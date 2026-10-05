// Runs against the Firestore emulator: npm run test:emulator
import { afterEach, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { getApps, initializeApp } from "firebase-admin/app";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import { createRoom, joinRoom, startGame } from "./rooms.js";
import * as play from "./play.js";
import { clock } from "./schedule.js";

const onEmulator = !!process.env.FIRESTORE_EMULATOR_HOST;

describe.skipIf(!onEmulator)("playing a round", () => {
  let db: Firestore;
  let code: string;

  beforeAll(() => {
    if (!getApps().length) initializeApp({ projectId: "cardgame-3" });
    db = getFirestore();
  });

  const room = async () => (await db.doc(`rooms/${code}`).get()).data()!;
  const round = async () => (await room()).round as play.PublicRound;
  const privateDoc = () => db.doc(`rooms/${code}/private/round`);

  /** 4 players u0..u3; u0 hosts and is the first dealer, so u1 bets first. */
  beforeEach(async () => {
    await db.recursiveDelete(db.collection("rooms"));
    ({ code } = await createRoom(db, "u0", { name: "Host" }));
    for (let i = 1; i < 4; i++) await joinRoom(db, `u${i}`, { code, name: `P${i}` });
    await startGame(db, "u0", { code });
  });

  afterEach(() => {
    clock.now = () => Date.now();
  });

  /** Jumps past the showdown's reveal, after which anyone may deal the next round. */
  const afterReveal = () => {
    clock.now = () => Date.now() + 60_000;
  };

  /** u1 bets 500, u2 bets 1000 (boss), u3 and u0 withdraw. */
  async function toDeals() {
    await play.bet(db, "u1", { code, amount: 500 });
    await play.bet(db, "u2", { code, amount: 1000 });
    await play.withdraw(db, "u3", { code });
    await play.withdraw(db, "u0", { code });
  }

  it("starting the game deals 4 private cards to each player", async () => {
    const r = await round();
    expect(r).toMatchObject({ roundNumber: 1, phase: "betting", dealerId: "u0", turnId: "u1" });
    const hands = await db.collection(`rooms/${code}/hands`).get();
    expect(hands.size).toBe(4);
    hands.forEach((h) => expect(h.data().cards).toHaveLength(4));
    // The public room document never contains anyone's cards.
    expect(JSON.stringify(await room())).not.toContain('"suit"');
  });

  it("enforces turns and bet rules with readable errors", async () => {
    await expect(play.bet(db, "u2", { code, amount: 500 })).rejects.toThrow(/not u2's turn/);
    await expect(play.bet(db, "u1", { code, amount: 750 })).rejects.toThrow(/steps of 500/);
    await expect(play.bet(db, "u1", { code, amount: "500" })).rejects.toThrow(/whole number/);
    await expect(play.bet(db, "stranger", { code, amount: 500 })).rejects.toThrow(/not in this room/);
  });

  it("closing the bets picks the boss and starts a 1 minute per entrant timer", async () => {
    const before = Date.now();
    await toDeals();
    const r = await round();
    expect(r).toMatchObject({ phase: "deals", bossId: "u2", turnId: null, bets: { u1: 500, u2: 1000 } });
    expect(r.deadline! - before).toBeGreaterThanOrEqual(2 * 60_000 - 1000);
    expect(r.deadline! - before).toBeLessThanOrEqual(2 * 60_000 + 5000);
  });

  it("on a tied highest bet, the latest to reach it is the boss, whatever order the bets are stored in", async () => {
    // Seats u0, u1, u3, u2, so betting goes u1, u3, u2, u0: u2 is later than u3 but sorts first.
    ({ code } = await createRoom(db, "u0", { name: "Host" }));
    for (const uid of ["u1", "u3", "u2"]) await joinRoom(db, uid, { code, name: uid });
    await startGame(db, "u0", { code });
    await play.withdraw(db, "u1", { code });
    await play.bet(db, "u3", { code, amount: 5000 }); // all in
    await play.bet(db, "u2", { code, amount: 5000 }); // all in, matching it
    // Firestore doesn't promise map key order (the emulator happens to keep it), so store the bets sorted.
    // Delete first: overwriting a doc in the emulator keeps its old key order.
    const priv = (await privateDoc().get()).data()!;
    priv.state.bets = Object.fromEntries(Object.entries(priv.state.bets).sort(([a], [b]) => a.localeCompare(b)));
    await privateDoc().delete();
    await privateDoc().set(priv);
    await play.withdraw(db, "u0", { code });
    expect((await round()).bossId).toBe("u2");
  });

  it("offers and deals: everyone else takes a deal, boss wins automatically", async () => {
    await toDeals();
    await play.makeOffer(db, "u1", { code, amount: 500 });
    expect((await round()).offers).toEqual({ u1: 500 });
    await play.answerOffer(db, "u2", { code, from: "u1", accept: true });
    const r = await round();
    expect(r.phase).toBe("finished");
    expect(r.result).toMatchObject({ outcome: "allDeals", winnerId: "u2", deltas: { u2: 500, u1: 500 } });
    expect(r.revealedHands).toEqual({});
  });

  it("boss reveals: revealed hands become public", async () => {
    await toDeals();
    await expect(play.reveal(db, "u1", { code })).rejects.toThrow(/Only the boss/);
    await play.reveal(db, "u2", { code });
    const r = await round();
    expect(r.result!.outcome).toBe("showdown");
    expect(Object.keys(r.revealedHands).sort()).toEqual(["u1", "u2"]);
  });

  it("after the timer runs out: no more offers, and anyone can force the reveal", async () => {
    await toDeals();
    await expect(play.timeUp(db, "u3", { code })).rejects.toThrow(/still running/);
    await privateDoc().update({ deadline: Date.now() - 1 });
    await expect(play.makeOffer(db, "u1", { code, amount: 500 })).rejects.toThrow(/Time is up/);
    await play.timeUp(db, "u3", { code });
    expect((await round()).result!.outcome).toBe("showdown");
  });

  it("next round passes the dealer right, and double calls are harmless", async () => {
    await toDeals();
    await play.reveal(db, "u2", { code });
    afterReveal();
    await expect(play.nextRound(db, "u0", { code, roundNumber: 2 })).resolves.toBeUndefined();
    await play.nextRound(db, "u0", { code, roundNumber: 1 });
    await play.nextRound(db, "u3", { code, roundNumber: 1 }); // late duplicate: ignored
    const r = await round();
    expect(r).toMatchObject({ roundNumber: 2, phase: "betting", dealerId: "u1", turnId: "u2" });
  });

  it("nobody can deal the next round while the reveal is still playing", async () => {
    await toDeals();
    await play.reveal(db, "u2", { code });
    const r = await round();
    // Two hands to show: 6.3 s of reveal, then the result stays up 8 s.
    expect(r.revealEndsAt! - Date.now()).toBeGreaterThan(5000);
    expect(r.nextRoundAt).toBe(r.revealEndsAt! + 8000);
    await play.nextRound(db, "u0", { code, roundNumber: 1 }); // too early: ignored
    expect((await round()).roundNumber).toBe(1);
    afterReveal();
    await play.nextRound(db, "u0", { code, roundNumber: 1 });
    expect((await round()).roundNumber).toBe(2);
  });

  it("peeks: everyone sees how many cards each player has looked at, until the next deal", async () => {
    expect((await db.doc(`rooms/${code}/hands/u1`).get()).data()).toMatchObject({ round: 1 });
    await play.peek(db, "u1", { code, roundNumber: 1, count: 2 });
    await play.peek(db, "u1", { code, roundNumber: 1, count: 1 }); // counts never go down
    await play.peek(db, "u2", { code, roundNumber: 1, count: 4 });
    await play.peek(db, "u3", { code, roundNumber: 0, count: 1 }); // late call from an earlier round
    expect((await room()).peeks).toEqual({ u1: 2, u2: 4 });
    await expect(play.peek(db, "u1", { code, roundNumber: 1, count: 5 })).rejects.toThrow(/1 to 4/);
    await expect(play.peek(db, "u1", { code, roundNumber: 1, count: "2" })).rejects.toThrow(/1 to 4/);
    await expect(play.peek(db, "stranger", { code, roundNumber: 1, count: 1 })).rejects.toThrow(/not in this room/);

    await toDeals();
    await play.reveal(db, "u2", { code });
    afterReveal();
    await play.nextRound(db, "u0", { code, roundNumber: 1 });
    expect((await room()).peeks).toEqual({});
    expect((await db.doc(`rooms/${code}/hands/u1`).get()).data()).toMatchObject({ round: 2 });
  });

  it("game ends when fewer than 4 players have points", async () => {
    await toDeals();
    await play.reveal(db, "u2", { code });
    // Knock u3 out directly in the stored state.
    const priv = (await privateDoc().get()).data()!;
    priv.state.players = priv.state.players.map((p: { id: string }) => (p.id === "u3" ? { ...p, points: 0 } : p));
    await privateDoc().set(priv);
    afterReveal();
    await play.nextRound(db, "u0", { code, roundNumber: 1 });
    const r = await room();
    expect(r.status).toBe("finished");
    expect(r.gameOver.standings).toHaveLength(4);
    expect((await db.collection(`rooms/${code}/hands`).get()).size).toBe(0);
  });
});
