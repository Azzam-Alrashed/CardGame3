// Runs against the Firestore emulator: npm run test:emulator
import { beforeAll, beforeEach, describe, expect, it } from "vitest";
import { initializeApp } from "firebase-admin/app";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import {
  Room, addAiPlayer, createRoom, joinRoom, leaveRoom, randomCode, rematch, removeAiPlayer, removePlayer, reportPlayer,
  startGame,
} from "./rooms.js";

const onEmulator = !!process.env.FIRESTORE_EMULATOR_HOST;

describe.skipIf(!onEmulator)("rooms", () => {
  let db: Firestore;

  beforeAll(() => {
    initializeApp({ projectId: "cardgame-3" });
    db = getFirestore();
  });

  beforeEach(async () => {
    await db.recursiveDelete(db.collection("rooms"));
  });

  const read = async (code: string) => (await db.doc(`rooms/${code}`).get()).data() as Room | undefined;

  async function roomWith(count: number): Promise<string> {
    const { code } = await createRoom(db, "u0", { name: "Host" });
    for (let i = 1; i < count; i++) await joinRoom(db, `u${i}`, { code, name: `P${i}` });
    return code;
  }

  it("creates a room with a 4-letter code and the creator as host", async () => {
    const { code } = await createRoom(db, "u0", { name: " Azzam " });
    expect(code).toMatch(/^[A-HJ-NP-Z]{4}$/);
    const room = await read(code);
    expect(room).toMatchObject({ hostId: "u0", status: "lobby", players: [{ uid: "u0", name: "Azzam" }] });
  });

  it("rejects empty or too long names", async () => {
    await expect(createRoom(db, "u0", { name: "  " })).rejects.toThrow();
    await expect(createRoom(db, "u0", { name: "x".repeat(21) })).rejects.toThrow();
  });

  it("joins by code, case-insensitive, in seat order", async () => {
    const { code } = await createRoom(db, "u0", { name: "Host" });
    await joinRoom(db, "u1", { code: code.toLowerCase(), name: "Sara" });
    const room = await read(code);
    expect(room!.players.map((p) => p.name)).toEqual(["Host", "Sara"]);
    expect(room!.playerIds).toEqual(["u0", "u1"]);
  });

  it("re-joining only updates the name", async () => {
    const code = await roomWith(2);
    await joinRoom(db, "u1", { code, name: "New name" });
    const room = await read(code);
    expect(room!.players).toHaveLength(2);
    expect(room!.players[1].name).toBe("New name");
  });

  it("rejects unknown codes and full rooms", async () => {
    await expect(joinRoom(db, "u1", { code: "ZZZZ", name: "x" })).rejects.toThrow(/No room/);
    const code = await roomWith(13);
    await expect(joinRoom(db, "u13", { code, name: "x" })).rejects.toThrow(/full/);
  });

  it("host leaving passes host to the next player; last one out deletes the room", async () => {
    const code = await roomWith(2);
    await leaveRoom(db, "u0", { code });
    expect((await read(code))!.hostId).toBe("u1");
    await leaveRoom(db, "u1", { code });
    expect(await read(code)).toBeUndefined();
  });

  it("only the host can start, and only with 4 to 13 players", async () => {
    const code = await roomWith(3);
    await expect(startGame(db, "u0", { code })).rejects.toThrow(/at least 4/);
    await joinRoom(db, "u3", { code, name: "P3" });
    await expect(startGame(db, "u1", { code })).rejects.toThrow(/Only the host/);
    await startGame(db, "u0", { code });
    const room = await read(code);
    expect(room!.status).toBe("playing");
    expect(room!.table!.seats.map((s) => s.points)).toEqual([5000, 5000, 5000, 5000]);
  });

  it("nobody can join or leave once the game has started", async () => {
    const code = await roomWith(4);
    await startGame(db, "u0", { code });
    await expect(joinRoom(db, "u9", { code, name: "Late" })).rejects.toThrow(/already started/);
    await expect(leaveRoom(db, "u1", { code })).rejects.toThrow(/in progress/);
  });
});

describe.skipIf(!onEmulator)("AI players", () => {
  let db: Firestore;
  beforeAll(() => {
    db = getFirestore();
  });
  beforeEach(async () => {
    await db.recursiveDelete(db.collection("rooms"));
  });
  const read = async (code: string) => (await db.doc(`rooms/${code}`).get()).data() as Room | undefined;

  it("only the host can add or remove AI players, and only in the lobby", async () => {
    const { code } = await createRoom(db, "u0", { name: "Host" });
    await joinRoom(db, "u1", { code, name: "Guest" });
    await expect(addAiPlayer(db, "u1", { code })).rejects.toThrow(/Only the host/);
    const { id } = await addAiPlayer(db, "u0", { code });
    await expect(removeAiPlayer(db, "u1", { code, aiId: id })).rejects.toThrow(/Only the host/);
    await expect(removeAiPlayer(db, "u0", { code, aiId: "u1" })).rejects.toThrow(/isn't an AI/);
    await removeAiPlayer(db, "u0", { code, aiId: id });
    const room = (await read(code))!;
    expect(room.players.map((p) => p.uid)).toEqual(["u0", "u1"]);
    expect(room.aiPlayers).toEqual([]);
    expect(room.botStyles).toEqual({});
  });

  it("AI players get unique names, a style, and fill up to 13 seats", async () => {
    const { code } = await createRoom(db, "u0", { name: "Host" });
    for (let i = 0; i < 12; i++) await addAiPlayer(db, "u0", { code });
    await expect(addAiPlayer(db, "u0", { code })).rejects.toThrow(/full/);
    const room = (await read(code))!;
    expect(new Set(room.players.map((p) => p.name)).size).toBe(13);
    expect(room.aiPlayers).toHaveLength(12);
    for (const id of room.aiPlayers!) expect(["careful", "balanced", "wild"]).toContain(room.botStyles![id]);
  });

  it("a room with only AI players left is deleted; host passes to a human", async () => {
    const { code } = await createRoom(db, "u0", { name: "Host" });
    await addAiPlayer(db, "u0", { code });
    await joinRoom(db, "u1", { code, name: "Guest" });
    await leaveRoom(db, "u0", { code });
    expect((await read(code))!.hostId).toBe("u1");
    await leaveRoom(db, "u1", { code });
    expect(await read(code)).toBeUndefined();
  });

  it("solo: 1 human + 3 AI players can start, and the AIs play until it's the human's turn", async () => {
    const { code } = await createRoom(db, "u0", { name: "Host" });
    for (let i = 0; i < 3; i++) await addAiPlayer(db, "u0", { code });
    await startGame(db, "u0", { code });
    const room = (await read(code)) as Room & { round: { phase: string; turnId: string | null } };
    expect(room.status).toBe("playing");
    // u0 deals, so the three AIs bet first; the round now waits for the human (or already ended).
    expect(room.round.phase === "finished" || room.round.turnId === "u0").toBe(true);
  });
});

describe.skipIf(!onEmulator)("lobby control and rematch", () => {
  let db: Firestore;
  beforeAll(() => {
    db = getFirestore();
  });
  beforeEach(async () => {
    await db.recursiveDelete(db.collection("rooms"));
  });
  const read = async (code: string) => (await db.doc(`rooms/${code}`).get()).data() as Room | undefined;

  it("the host can remove anyone else from the lobby, people included", async () => {
    const { code } = await createRoom(db, "u0", { name: "Host" });
    await joinRoom(db, "u1", { code, name: "Guest" });
    await joinRoom(db, "u2", { code, name: "Other" });
    await expect(removePlayer(db, "u1", { code, playerId: "u2" })).rejects.toThrow(/Only the host/);
    await expect(removePlayer(db, "u0", { code, playerId: "u0" })).rejects.toThrow(/Leave room/);
    await expect(removePlayer(db, "u0", { code, playerId: "nobody" })).rejects.toThrow(/isn't in this room/);
    await removePlayer(db, "u0", { code, playerId: "u1" });
    expect((await read(code))!.playerIds).toEqual(["u0", "u2"]);
  });

  it("nobody can be removed once the game started", async () => {
    const { code } = await createRoom(db, "u0", { name: "Host" });
    for (let i = 1; i < 4; i++) await joinRoom(db, `u${i}`, { code, name: `P${i}` });
    await startGame(db, "u0", { code });
    await expect(removePlayer(db, "u0", { code, playerId: "u1" })).rejects.toThrow(/already started/);
  });

  /** A finished game: host u0, guest u1, two AI players. */
  async function finishedGame(): Promise<string> {
    const { code } = await createRoom(db, "u0", { name: "Host" });
    await joinRoom(db, "u1", { code, name: "Sara" });
    await addAiPlayer(db, "u0", { code });
    await addAiPlayer(db, "u0", { code });
    await startGame(db, "u0", { code });
    await db.doc(`rooms/${code}`).update({ status: "finished", gameOver: { winnerId: "u1", standings: [] } });
    return code;
  }

  it("play again opens a new lobby with the same AI players, hosted by whoever asked", async () => {
    const old = await finishedGame();
    const before = (await read(old))!;
    const { code } = await rematch(db, "u1", { code: old });
    expect(code).not.toBe(old);

    const next = (await read(code))!;
    expect(next).toMatchObject({ status: "lobby", hostId: "u1", table: null });
    expect(next.players[0]).toEqual({ uid: "u1", name: "Sara" });
    expect(next.aiPlayers).toEqual(before.aiPlayers);
    expect(next.players.slice(1).map((p) => p.name)).toEqual(before.players.slice(2).map((p) => p.name));
    for (const id of next.aiPlayers!) expect(next.botStyles![id]).toBe(before.botStyles![id]);

    // The finished game stays, pointing at the rematch.
    expect(await read(old)).toMatchObject({ status: "finished", rematchCode: code, rematchBy: "Sara" });
  });

  it("everyone else who asks joins the same rematch", async () => {
    const old = await finishedGame();
    const { code } = await rematch(db, "u1", { code: old });
    expect(await rematch(db, "u0", { code: old })).toEqual({ code });
    expect((await read(code))!.players.map((p) => p.uid)).toEqual(["u1", ...(await read(code))!.aiPlayers!, "u0"]);
  });

  it("only players of a finished game can ask for a rematch", async () => {
    const old = await finishedGame();
    await expect(rematch(db, "stranger", { code: old })).rejects.toThrow(/not in this room/);
    const { code: lobby } = await createRoom(db, "x", { name: "X" });
    await expect(rematch(db, "x", { code: lobby })).rejects.toThrow(/isn't over/);
  });
});

describe.skipIf(!onEmulator)("names and reports", () => {
  let db: Firestore;
  beforeAll(() => {
    db = getFirestore();
  });
  beforeEach(async () => {
    await db.recursiveDelete(db.collection("rooms"));
    await db.recursiveDelete(db.collection("reports"));
  });

  it("offensive names can't create or join a room", async () => {
    await expect(createRoom(db, "u0", { name: "sh1t head" })).rejects.toThrow(/different name/);
    const { code } = await createRoom(db, "u0", { name: "Host" });
    await expect(joinRoom(db, "u1", { code, name: "قحبة" })).rejects.toThrow(/different name/);
  });

  it("players can report another person's name; reporting again updates the same report", async () => {
    const { code } = await createRoom(db, "u0", { name: "Host" });
    await joinRoom(db, "u1", { code, name: "Meanie" });
    await reportPlayer(db, "u0", { code, playerId: "u1", reason: "offensive" });
    await reportPlayer(db, "u0", { code, playerId: "u1", reason: "impersonation" });
    const reports = await db.collection("reports").get();
    expect(reports.size).toBe(1);
    expect(reports.docs[0].id).toBe("u0_u1");
    expect(reports.docs[0].data()).toMatchObject({ code, reporter: "u0", reported: "u1", name: "Meanie", reason: "impersonation" });
  });

  it("you can't report yourself, an AI player, or anyone from outside the room", async () => {
    const { code } = await createRoom(db, "u0", { name: "Host" });
    const { id: ai } = await addAiPlayer(db, "u0", { code });
    await joinRoom(db, "u1", { code, name: "Guest" });
    await expect(reportPlayer(db, "u0", { code, playerId: "u0", reason: "other" })).rejects.toThrow(/can't be reported/);
    await expect(reportPlayer(db, "u0", { code, playerId: ai, reason: "other" })).rejects.toThrow(/can't be reported/);
    await expect(reportPlayer(db, "x", { code, playerId: "u1", reason: "other" })).rejects.toThrow(/not in this room/);
    await expect(reportPlayer(db, "u0", { code, playerId: "u1", reason: "rude" })).rejects.toThrow(/reason/);
  });
});

describe("room codes", () => {
  it("never use I or O", () => {
    for (let i = 0; i < 500; i++) expect(randomCode()).not.toMatch(/[IO]/);
  });
});
