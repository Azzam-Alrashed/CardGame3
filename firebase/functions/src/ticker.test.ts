// Runs against the Firestore emulator: npm run test:emulator
import { afterEach, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { getApps, initializeApp } from "firebase-admin/app";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import { addAiPlayer, createRoom, joinRoom, startGame } from "./rooms.js";
import * as play from "./play.js";
import { PrivateRound, botTiming, clock } from "./schedule.js";
import { cleanup, runDue, sweep, tick } from "./ticker.js";

const onEmulator = !!process.env.FIRESTORE_EMULATOR_HOST;

describe.skipIf(!onEmulator)("ticker", () => {
  let db: Firestore;
  let code: string;
  let now: number;

  beforeAll(() => {
    if (!getApps().length) initializeApp({ projectId: "cardgame-3" });
    db = getFirestore();
  });

  const room = async () => (await db.doc(`rooms/${code}`).get()).data()!;
  const priv = async () => (await db.doc(`rooms/${code}/private/round`).get()).data() as PrivateRound;
  const later = (ms: number) => { now += ms; };

  /** Humans u0 (host, dealer) and u1, then AI players, so u1 bets first and the AI players after. */
  beforeEach(async () => {
    now = 1_000_000_000_000;
    clock.now = () => now;
    botTiming.minMs = 3000;
    botTiming.maxMs = 3000;
    await db.recursiveDelete(db.collection("rooms"));
    ({ code } = await createRoom(db, "u0", { name: "Host" }));
    await joinRoom(db, "u1", { code, name: "P1" });
    await addAiPlayer(db, "u0", { code });
    await addAiPlayer(db, "u0", { code });
    await startGame(db, "u0", { code });
  });

  afterEach(() => {
    clock.now = () => Date.now();
    botTiming.minMs = 0;
    botTiming.maxMs = 0;
  });

  it("a human's move returns at once; the bot plays after its pause", async () => {
    await play.bet(db, "u1", { code, amount: 500 });
    const ai = (await room()).aiPlayers[0];
    expect((await room()).round.turnId).toBe(ai); // not played yet
    expect((await room()).wakeAt).toBe(now + 3000);

    expect(await tick(db, code)).toBe(now + 3000); // too early: nothing happens
    expect((await room()).round.turnId).toBe(ai);

    later(3000);
    await tick(db, code);
    const r = await room();
    expect(r.round.bets[ai] !== undefined || r.round.withdrawn.includes(ai)).toBe(true);
  });

  it("bots play one move per wake-up until it's a human's turn again", async () => {
    await play.withdraw(db, "u1", { code });
    for (let i = 0; i < 2; i++) {
      later(3000);
      await tick(db, code);
    }
    const r = await room();
    // Both AI players decided; the human dealer is next (or the round already ended).
    expect(r.round.phase === "finished" || r.round.turnId === "u0").toBe(true);
    if (r.round.phase === "betting") expect(r.wakeAt).toBeNull(); // waiting on a person
  });

  it("the deals timer reveals on its own, with no phone asking", async () => {
    // Make it a human showdown: the AI players step aside.
    const [ai1, ai2] = (await room()).aiPlayers;
    await play.bet(db, "u1", { code, amount: 500 });
    await db.doc(`rooms/${code}/private/round`).update({
      "botMove.action": { kind: "withdraw", id: ai1 },
    });
    later(3000);
    await tick(db, code);
    await db.doc(`rooms/${code}/private/round`).update({ "botMove.action": { kind: "withdraw", id: ai2 } });
    later(3000);
    await tick(db, code);
    await play.bet(db, "u0", { code, amount: 1000 });
    expect((await room()).round.phase).toBe("deals");

    const { deadline } = await priv();
    expect((await room()).wakeAt).toBe(deadline);
    now = deadline!;
    await tick(db, code);
    expect((await room()).round.result.outcome).toBe("showdown");
    expect((await room()).wakeAt).toBeNull();
  });

  it("a planned move that no longer fits is dropped, not retried forever", async () => {
    await play.bet(db, "u1", { code, amount: 500 });
    await db.doc(`rooms/${code}/private/round`).update({ "botMove.action": { kind: "bet", id: "u0", amount: 500 } });
    later(3000);
    expect(await tick(db, code)).toBeNull();
    expect((await priv()).botMove).toBeNull();
  });

  it("the sweeper picks up rooms whose wake-up was missed", async () => {
    await play.bet(db, "u1", { code, amount: 500 });
    const before = (await room()).round.turnId;
    later(10_000); // the task never came
    expect(await sweep(db)).toBe(1);
    expect((await room()).round.turnId).not.toBe(before);
  });

  it("runDue stops once nothing is due now", async () => {
    await play.withdraw(db, "u1", { code });
    const next = await runDue(db, code, (await room()).wakeAt);
    expect(next).toBe(now + 3000); // the AI's move is still in the future
  });

  it("cleanup deletes idle rooms and keeps active ones", async () => {
    const day = 24 * 60 * 60 * 1000;
    const lobby = (await createRoom(db, "x", { name: "X" })).code;
    const freshLobby = (await createRoom(db, "y", { name: "Y" })).code;
    await db.doc(`rooms/${lobby}`).update({ updatedAt: now - 2 * day });
    await db.doc(`rooms/${code}`).update({ updatedAt: now - 3 * day }); // a game paused for 3 days: kept
    expect(await cleanup(db)).toBe(1);
    expect((await db.doc(`rooms/${lobby}`).get()).exists).toBe(false);
    expect((await db.doc(`rooms/${freshLobby}`).get()).exists).toBe(true);
    expect((await db.doc(`rooms/${code}`).get()).exists).toBe(true);

    await db.doc(`rooms/${code}`).update({ updatedAt: now - 8 * day });
    expect(await cleanup(db)).toBe(1);
    expect((await db.doc(`rooms/${code}/private/round`).get()).exists).toBe(false); // subcollections too
  });
});
