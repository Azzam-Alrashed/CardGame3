// Runs against the Firestore emulator: npm run test:emulator
import { beforeAll, beforeEach, describe, expect, it } from "vitest";
import { initializeApp } from "firebase-admin/app";
import { Firestore, getFirestore } from "firebase-admin/firestore";
import { Room, addAiPlayer, createRoom, joinRoom, leaveRoom, randomCode, removeAiPlayer, startGame } from "./rooms.js";

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

describe("room codes", () => {
  it("never use I or O", () => {
    for (let i = 0; i < 500; i++) expect(randomCode()).not.toMatch(/[IO]/);
  });
});
