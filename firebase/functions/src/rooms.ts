// Rooms: create, join by code, leave, start.
// Handlers take the Firestore instance and caller's uid so they can be tested against the emulator.

import { Firestore, FieldValue } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { MAX_PLAYERS, MIN_PLAYERS } from "./engine/cards.js";
import { Table, newTable } from "./engine/game.js";
import { dealRound } from "./play.js";
import type { BotStyle } from "./engine/bot.js";

export type RoomStatus = "lobby" | "playing" | "finished";

export interface RoomPlayer {
  uid: string;
  name: string;
}

export interface Room {
  code: string;
  hostId: string;
  status: RoomStatus;
  /** Seat order around the table. */
  players: RoomPlayer[];
  /** Same uids as players; lets security rules check membership. */
  playerIds: string[];
  table: Table | null;
  createdAt: FieldValue;
  /** Players who stepped away; a bot plays for them until they come back. */
  away?: string[];
  /** Each away player's bot personality, kept for the whole game. */
  botStyles?: Record<string, BotStyle>;
}

/** No I or O, so codes can't be confused with 1 and 0. */
const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ";
const CODE_LENGTH = 4;
const MAX_NAME_LENGTH = 20;

export function randomCode(rng: () => number = Math.random): string {
  let code = "";
  for (let i = 0; i < CODE_LENGTH; i++) code += CODE_ALPHABET[Math.floor(rng() * CODE_ALPHABET.length)];
  return code;
}

export function cleanName(name: unknown): string {
  const trimmed = typeof name === "string" ? name.trim() : "";
  if (trimmed.length === 0 || trimmed.length > MAX_NAME_LENGTH) {
    throw new HttpsError("invalid-argument", `Name must be 1-${MAX_NAME_LENGTH} characters`);
  }
  return trimmed;
}

function cleanCode(code: unknown): string {
  const c = typeof code === "string" ? code.trim().toUpperCase() : "";
  if (c.length !== CODE_LENGTH) throw new HttpsError("invalid-argument", "Room code must be 4 letters");
  return c;
}

const rooms = (db: Firestore) => db.collection("rooms");

export async function createRoom(db: Firestore, uid: string, data: { name?: unknown }): Promise<{ code: string }> {
  const name = cleanName(data.name);
  for (let attempt = 0; attempt < 10; attempt++) {
    const code = randomCode();
    const room: Room = {
      code,
      hostId: uid,
      status: "lobby",
      players: [{ uid, name }],
      playerIds: [uid],
      table: null,
      createdAt: FieldValue.serverTimestamp(),
    };
    try {
      await rooms(db).doc(code).create(room); // fails if the code is taken
      return { code };
    } catch (e: unknown) {
      if ((e as { code?: number }).code !== 6 /* ALREADY_EXISTS */) throw e;
    }
  }
  throw new HttpsError("resource-exhausted", "Could not find a free room code, try again");
}

export async function joinRoom(
  db: Firestore, uid: string, data: { code?: unknown; name?: unknown },
): Promise<{ code: string }> {
  const code = cleanCode(data.code);
  const name = cleanName(data.name);
  await db.runTransaction(async (tx) => {
    const ref = rooms(db).doc(code);
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "No room with that code");
    const room = snap.data() as Room;
    const existing = room.players.find((p) => p.uid === uid);
    if (existing) {
      // Re-joining just updates the name.
      tx.update(ref, { players: room.players.map((p) => (p.uid === uid ? { uid, name } : p)) });
      return;
    }
    if (room.status !== "lobby") throw new HttpsError("failed-precondition", "This game has already started");
    if (room.players.length >= MAX_PLAYERS) throw new HttpsError("failed-precondition", "This room is full");
    tx.update(ref, { players: [...room.players, { uid, name }], playerIds: [...room.playerIds, uid] });
  });
  return { code };
}

export async function leaveRoom(db: Firestore, uid: string, data: { code?: unknown }): Promise<void> {
  const code = cleanCode(data.code);
  await db.runTransaction(async (tx) => {
    const ref = rooms(db).doc(code);
    const snap = await tx.get(ref);
    if (!snap.exists) return;
    const room = snap.data() as Room;
    if (room.status !== "lobby") throw new HttpsError("failed-precondition", "You can't leave a game in progress");
    const players = room.players.filter((p) => p.uid !== uid);
    if (players.length === 0) {
      tx.delete(ref);
      return;
    }
    // If the host leaves, the next player in the room becomes host.
    const hostId = room.hostId === uid ? players[0].uid : room.hostId;
    tx.update(ref, { players, playerIds: players.map((p) => p.uid), hostId });
  });
}

export async function startGame(db: Firestore, uid: string, data: { code?: unknown }): Promise<void> {
  const code = cleanCode(data.code);
  await db.runTransaction(async (tx) => {
    const ref = rooms(db).doc(code);
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "No room with that code");
    const room = snap.data() as Room;
    if (room.hostId !== uid) throw new HttpsError("permission-denied", "Only the host can start the game");
    if (room.status !== "lobby") throw new HttpsError("failed-precondition", "The game has already started");
    if (room.players.length < MIN_PLAYERS) {
      throw new HttpsError("failed-precondition", `You need at least ${MIN_PLAYERS} players`);
    }
    dealRound(tx, ref, newTable(room.players.map((p) => p.uid)), Date.now(), { status: "playing" });
  });
}
