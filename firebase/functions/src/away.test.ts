// Runs against the Firestore emulator: npm run test:emulator
import { beforeAll, beforeEach, describe, expect, it } from "vitest";
import { getApps, initializeApp } from "firebase-admin/app";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import { createRoom, joinRoom, startGame } from "./rooms.js";
import * as play from "./play.js";

const onEmulator = !!process.env.FIRESTORE_EMULATOR_HOST;

describe.skipIf(!onEmulator)("away players and bots", () => {
  let db: Firestore;
  let code: string;

  beforeAll(() => {
    if (!getApps().length) initializeApp({ projectId: "cardgame-3" });
    db = getFirestore();
  });

  const room = async () => (await db.doc(`rooms/${code}`).get()).data()!;

  /** u0 hosts and deals, so u1 bets first. */
  beforeEach(async () => {
    await db.recursiveDelete(db.collection("rooms"));
    ({ code } = await createRoom(db, "u0", { name: "Host" }));
    for (let i = 1; i < 4; i++) await joinRoom(db, `u${i}`, { code, name: `P${i}` });
    await startGame(db, "u0", { code });
  });

  it("stepping away marks you away, gives your bot a style, and the bot plays your turn", async () => {
    await play.setAway(db, "u1", { code, away: true });
    const r = await room();
    expect(r.away).toEqual(["u1"]);
    expect(["careful", "balanced", "wild"]).toContain(r.botStyles.u1);
    expect(r.round.turnId).toBe("u2"); // u1's bot already bet or withdrew
  });

  it("coming back stops the bot", async () => {
    await play.setAway(db, "u2", { code, away: true });
    await play.setAway(db, "u2", { code, away: false });
    await play.bet(db, "u1", { code, amount: 500 });
    const r = await room();
    expect(r.away).toEqual([]);
    expect(r.round.turnId).toBe("u2"); // waits for the human again
  });

  it("a table where everyone else is away plays on after each of your moves", async () => {
    for (const id of ["u1", "u2", "u3"]) await play.setAway(db, id, { code, away: true });
    // Bots u1..u3 have bet or withdrawn; it's the human dealer's turn (or the round already ended).
    const r = await room();
    expect(r.round.phase === "finished" || r.round.turnId === "u0").toBe(true);
  });

  it("only players in the room can step away, and only during a game", async () => {
    await expect(play.setAway(db, "stranger", { code, away: true })).rejects.toThrow(/not in this room/);
    const lobby = await createRoom(db, "x", { name: "X" });
    await expect(play.setAway(db, "x", { code: lobby.code, away: true })).rejects.toThrow(/No game/);
  });
});
