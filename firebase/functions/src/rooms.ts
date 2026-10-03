// Rooms: create, join by code, leave, start.
// Handlers take the Firestore instance and caller's uid so they can be tested against the emulator.

import { DocumentReference, FieldValue, Firestore, Transaction } from "firebase-admin/firestore";
import { randomInt } from "node:crypto";
import { HttpsError } from "firebase-functions/v2/https";
import { MAX_PLAYERS, MIN_PLAYERS } from "./engine/cards.js";
import { Table, newTable } from "./engine/game.js";
import { dealRound, runBots } from "./play.js";
import { BOT_STYLES, BotStyle } from "./engine/bot.js";

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
  /** AI players added by the host; always played by a bot. */
  aiPlayers?: string[];
  /** Each away player's or AI player's bot personality, kept for the whole game. */
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
    const ai = new Set(room.aiPlayers ?? []);
    const humans = players.filter((p) => !ai.has(p.uid));
    // AI players never keep a room alive on their own.
    if (humans.length === 0) {
      tx.delete(ref);
      return;
    }
    // If the host leaves, the next human in the room becomes host.
    const hostId = room.hostId === uid ? humans[0].uid : room.hostId;
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
  await runBots(db, code); // an AI player may bet first
}

// MARK: AI players

export const AI_NAMES = [
  "Lucky Lulu", "Bot Fahd", "Sneaky Sami", "Captain Kings", "Bluffy Bader", "Queenie",
  "Jack Jr.", "Ace Abdullah", "Dealer Dana", "Mister Maybe", "Wild Waleed", "Calm Khalid", "Noor Nerves",
].map((n) => `${n} 🤖`);

/** Host only, in the lobby: adds an AI player with a fun name and a random style. */
export async function addAiPlayer(db: Firestore, uid: string, data: { code?: unknown }): Promise<{ id: string }> {
  const code = cleanCode(data.code);
  return db.runTransaction(async (tx) => {
    const ref = rooms(db).doc(code);
    const room = await hostLobby(tx, ref, uid);
    if (room.players.length >= MAX_PLAYERS) throw new HttpsError("failed-precondition", "This room is full");
    const taken = new Set(room.players.map((p) => p.name));
    const name = AI_NAMES.find((n) => !taken.has(n)) ?? `AI ${room.players.length + 1} 🤖`;
    const id = `ai_${randomInt(2 ** 40).toString(36)}`;
    tx.update(ref, {
      players: [...room.players, { uid: id, name }],
      playerIds: [...room.playerIds, id],
      aiPlayers: [...(room.aiPlayers ?? []), id],
      botStyles: { ...room.botStyles, [id]: BOT_STYLES[randomInt(BOT_STYLES.length)] },
    });
    return { id };
  });
}

/** Host only, in the lobby: removes one of the AI players. */
export async function removeAiPlayer(
  db: Firestore, uid: string, data: { code?: unknown; aiId?: unknown },
): Promise<void> {
  const code = cleanCode(data.code);
  await db.runTransaction(async (tx) => {
    const ref = rooms(db).doc(code);
    const room = await hostLobby(tx, ref, uid);
    const aiId = data.aiId;
    if (typeof aiId !== "string" || !(room.aiPlayers ?? []).includes(aiId)) {
      throw new HttpsError("invalid-argument", "That player isn't an AI player in this room");
    }
    const { [aiId]: _, ...botStyles } = room.botStyles ?? {};
    tx.update(ref, {
      players: room.players.filter((p) => p.uid !== aiId),
      playerIds: room.playerIds.filter((p) => p !== aiId),
      aiPlayers: (room.aiPlayers ?? []).filter((p) => p !== aiId),
      botStyles,
    });
  });
}

/** Loads the room and checks the caller is its host and the game hasn't started. */
async function hostLobby(tx: Transaction, ref: DocumentReference, uid: string): Promise<Room> {
  const snap = await tx.get(ref);
  if (!snap.exists) throw new HttpsError("not-found", "No room with that code");
  const room = snap.data() as Room;
  if (room.hostId !== uid) throw new HttpsError("permission-denied", "Only the host can change AI players");
  if (room.status !== "lobby") throw new HttpsError("failed-precondition", "The game has already started");
  return room;
}
