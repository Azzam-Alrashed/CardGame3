// Playing rounds on top of rooms.
//
// Storage per room:
//   rooms/{code}                 public: room + `round` (everything players may see)
//   rooms/{code}/hands/{uid}     one player's 4 cards, readable only by that player
//   rooms/{code}/private/round   full engine state including every hand; never readable by clients

import { randomInt } from "node:crypto";
import { DocumentReference, Firestore, Transaction } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { BOT_STYLES, BotAction, botMove } from "./engine/bot.js";
import { Card } from "./engine/cards.js";
import { Table, isGameOver, nextTable } from "./engine/game.js";
import * as engine from "./engine/round.js";
import { RoundState } from "./engine/round.js";
import type { Room } from "./rooms.js";

/** Crypto-strong randomness for shuffling. */
const secureRng = () => randomInt(2 ** 32) / 2 ** 32;

/** What everyone at the table can see about the current round. */
export interface PublicRound {
  roundNumber: number;
  phase: engine.Phase;
  dealerId: string;
  /** Whose turn it is to bet (betting phase only). */
  turnId: string | null;
  bets: Record<string, number>;
  withdrawn: string[];
  bossId: string | null;
  offers: Record<string, number>;
  deals: Record<string, number>;
  /** Epoch ms when the deals timer runs out (deals phase only). */
  deadline: number | null;
  result: engine.RoundResult | null;
  /** Cards of players who revealed (finished rounds only). */
  revealedHands: Record<string, Card[]>;
}

interface PrivateRound {
  state: RoundState;
  deadline: number | null;
  /** How many offers each bot made this round. */
  botOffers?: Record<string, number>;
}

export function publicView(state: RoundState, deadline: number | null): PublicRound {
  const revealed = state.result?.revealed ?? [];
  return {
    roundNumber: state.roundNumber,
    phase: state.phase,
    dealerId: state.players[state.dealerIndex].id,
    turnId: state.phase === "betting" ? state.players[state.turnIndex].id : null,
    bets: state.bets,
    withdrawn: state.withdrawn,
    bossId: state.bossId,
    offers: state.offers,
    deals: state.deals,
    deadline,
    result: state.result,
    revealedHands: Object.fromEntries(
      state.players.filter((p) => revealed.includes(p.id)).map((p) => [p.id, p.hand]),
    ),
  };
}

const roomRef = (db: Firestore, code: string) => db.collection("rooms").doc(code);
const privateRef = (room: DocumentReference) => room.collection("private").doc("round");
const handRef = (room: DocumentReference, uid: string) => room.collection("hands").doc(uid);

function cleanCode(code: unknown): string {
  const c = typeof code === "string" ? code.trim().toUpperCase() : "";
  if (c.length !== 4) throw new HttpsError("invalid-argument", "Room code must be 4 letters");
  return c;
}

function toPoints(n: unknown): number {
  if (typeof n !== "number" || !Number.isInteger(n)) throw new HttpsError("invalid-argument", "Amount must be a whole number");
  return n;
}

/**
 * Deals a new round for the table and writes all docs. Call inside a transaction.
 * `roomFields` are extra room fields to save in the same write.
 */
export function dealRound(
  tx: Transaction, ref: DocumentReference, table: Table, now: number, roomFields: Record<string, unknown> = {},
): void {
  const state = engine.startRound(table.seats, table.dealerIndex, secureRng, table.roundNumber);
  for (const p of state.players) tx.set(handRef(ref, p.id), { cards: p.hand });
  writeRound(tx, ref, state, now, null, {}, { table, ...roomFields });
}

function writeRound(
  tx: Transaction, ref: DocumentReference, state: RoundState, now: number, deadline: number | null,
  botOffers: Record<string, number> = {}, roomFields: Record<string, unknown> = {},
): void {
  // Start the timer when the round enters the deals phase.
  const d = state.phase === "deals" ? (deadline ?? now + state.timerMs!) : null;
  const priv: PrivateRound = { state, deadline: d, botOffers };
  tx.set(privateRef(ref), priv);
  tx.update(ref, { ...roomFields, round: publicView(state, d) });
}

/** Loads the round, applies a move, and saves it — all in one transaction. */
async function move(
  db: Firestore,
  codeIn: unknown,
  uid: string,
  apply: (state: RoundState, ctx: { now: number; deadline: number | null }) => RoundState,
): Promise<void> {
  const code = cleanCode(codeIn);
  const ref = roomRef(db, code);
  await db.runTransaction(async (tx) => {
    const [roomSnap, privSnap] = await Promise.all([tx.get(ref), tx.get(privateRef(ref))]);
    if (!roomSnap.exists) throw new HttpsError("not-found", "No room with that code");
    const room = roomSnap.data() as Room;
    if (!room.playerIds.includes(uid)) throw new HttpsError("permission-denied", "You are not in this room");
    if (room.status !== "playing" || !privSnap.exists) throw new HttpsError("failed-precondition", "No round in progress");
    const { state, deadline, botOffers } = privSnap.data() as PrivateRound;
    const now = Date.now();
    let next: RoundState;
    try {
      next = apply(state, { now, deadline });
    } catch (e) {
      if (e instanceof HttpsError) throw e;
      throw new HttpsError("failed-precondition", (e as Error).message);
    }
    writeRound(tx, ref, next, now, deadline, botOffers);
  });
  await runBots(db, code);
}

export const bet = (db: Firestore, uid: string, d: { code?: unknown; amount?: unknown }) =>
  move(db, d.code, uid, (s) => engine.placeBet(s, uid, toPoints(d.amount)));

export const withdraw = (db: Firestore, uid: string, d: { code?: unknown }) =>
  move(db, d.code, uid, (s) => engine.withdraw(s, uid));

export const makeOffer = (db: Firestore, uid: string, d: { code?: unknown; amount?: unknown }) =>
  move(db, d.code, uid, (s, { now, deadline }) => {
    assertBeforeDeadline(now, deadline);
    return engine.makeOffer(s, uid, toPoints(d.amount));
  });

export const answerOffer = (db: Firestore, uid: string, d: { code?: unknown; from?: unknown; accept?: unknown }) =>
  move(db, d.code, uid, (s, { now, deadline }) => {
    assertBeforeDeadline(now, deadline);
    if (typeof d.from !== "string") throw new HttpsError("invalid-argument", "Missing offer sender");
    return d.accept === true ? engine.acceptOffer(s, uid, d.from) : engine.rejectOffer(s, uid, d.from);
  });

export const reveal = (db: Firestore, uid: string, d: { code?: unknown }) =>
  move(db, d.code, uid, (s) => engine.reveal(s, uid));

/** Any player may call this once the deals timer has run out; it forces the reveal. */
export const timeUp = (db: Firestore, uid: string, d: { code?: unknown }) =>
  move(db, d.code, uid, (s, { now, deadline }) => {
    if (deadline === null || now < deadline) throw new HttpsError("failed-precondition", "The timer is still running");
    return engine.timeUp(s);
  });

function assertBeforeDeadline(now: number, deadline: number | null): void {
  if (deadline !== null && now >= deadline) throw new HttpsError("deadline-exceeded", "Time is up");
}

/**
 * Any player may call this after a round finishes: applies knockouts, passes the dealer right,
 * and deals the next round — or ends the game. `roundNumber` makes double calls harmless.
 */
export async function nextRound(db: Firestore, uid: string, d: { code?: unknown; roundNumber?: unknown }): Promise<void> {
  const code = cleanCode(d.code);
  const ref = roomRef(db, code);
  await db.runTransaction(async (tx) => {
    const [roomSnap, privSnap] = await Promise.all([tx.get(ref), tx.get(privateRef(ref))]);
    if (!roomSnap.exists) throw new HttpsError("not-found", "No room with that code");
    const room = roomSnap.data() as Room;
    if (!room.playerIds.includes(uid)) throw new HttpsError("permission-denied", "You are not in this room");
    if (room.status !== "playing" || !privSnap.exists) return;
    const { state } = privSnap.data() as PrivateRound;
    if (state.roundNumber !== d.roundNumber) return; // someone already moved on
    if (state.phase !== "finished") throw new HttpsError("failed-precondition", "The round is not finished");

    const outcome = nextTable(state);
    // Knocked-out players' old hands are deleted; everyone else's are overwritten by the new deal.
    const stillIn = isGameOver(outcome) ? [] : outcome.seats.map((s) => s.id);
    for (const p of state.players) if (!stillIn.includes(p.id)) tx.delete(handRef(ref, p.id));
    if (isGameOver(outcome)) {
      tx.update(ref, { status: "finished", gameOver: outcome });
      return;
    }
    dealRound(tx, ref, outcome, Date.now());
  });
  await runBots(db, code);
}

// MARK: Away players and bots

/**
 * Step away from the table (a bot plays for you) or come back.
 * Only during a game; in the lobby, players leave the room instead.
 */
export async function setAway(db: Firestore, uid: string, d: { code?: unknown; away?: unknown }): Promise<void> {
  const code = cleanCode(d.code);
  const ref = roomRef(db, code);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "No room with that code");
    const room = snap.data() as Room;
    if (!room.playerIds.includes(uid)) throw new HttpsError("permission-denied", "You are not in this room");
    if (room.status !== "playing") throw new HttpsError("failed-precondition", "No game in progress");
    const away = new Set(room.away ?? []);
    if (d.away === true) away.add(uid);
    else away.delete(uid);
    const botStyles = { ...room.botStyles };
    botStyles[uid] ??= BOT_STYLES[randomInt(BOT_STYLES.length)];
    tx.update(ref, { away: [...away], botStyles });
  });
  if (d.away === true) await runBots(db, code);
}

/** Pause before each bot move so it feels like someone thinking. Tests shorten this. */
export const botTiming = { minMs: 2000, maxMs: 4000 };

/** The next thing a bot (or the expired timer) should do, or null. */
function pendingBotAction(room: Room, priv: PrivateRound, now: number): BotAction | { kind: "timeUp" } | null {
  const { state, deadline } = priv;
  if (state.phase === "deals" && deadline !== null && now >= deadline) return { kind: "timeUp" };
  for (const id of [...(room.aiPlayers ?? []), ...(room.away ?? [])]) {
    const style = room.botStyles?.[id] ?? "balanced";
    const action = botMove(state, id, style, priv.botOffers?.[id] ?? 0, secureRng);
    if (action) return action;
  }
  return null;
}

function applyBotAction(state: RoundState, action: BotAction | { kind: "timeUp" }): RoundState {
  switch (action.kind) {
    case "bet": return engine.placeBet(state, action.id, action.amount);
    case "withdraw": return engine.withdraw(state, action.id);
    case "offer": return engine.makeOffer(state, action.id, action.amount);
    case "answer":
      return action.accept
        ? engine.acceptOffer(state, action.bossId, action.from)
        : engine.rejectOffer(state, action.bossId, action.from);
    case "reveal": return engine.reveal(state, action.bossId);
    case "timeUp": return engine.timeUp(state);
  }
}

/**
 * Plays bot moves one at a time, with a short pause before each, until no bot has anything to do.
 * Every step re-reads the latest state, so a player coming back stops their bot right away.
 */
export async function runBots(db: Firestore, code: string): Promise<void> {
  const ref = roomRef(db, code);
  for (let step = 0; step < 100; step++) {
    const [roomSnap, privSnap] = await Promise.all([ref.get(), privateRef(ref).get()]);
    const room = roomSnap.data() as Room | undefined;
    if (!room || room.status !== "playing" || !privSnap.exists) return;
    if (!pendingBotAction(room, privSnap.data() as PrivateRound, Date.now())) return;

    await sleep(botTiming.minMs + Math.random() * (botTiming.maxMs - botTiming.minMs));

    const acted = await db.runTransaction(async (tx) => {
      const [rs, ps] = await Promise.all([tx.get(ref), tx.get(privateRef(ref))]);
      const r = rs.data() as Room | undefined;
      if (!r || r.status !== "playing" || !ps.exists) return false;
      const priv = ps.data() as PrivateRound;
      const now = Date.now();
      const action = pendingBotAction(r, priv, now);
      if (!action) return false;
      const botOffers = { ...priv.botOffers };
      if (action.kind === "offer") botOffers[action.id] = (botOffers[action.id] ?? 0) + 1;
      writeRound(tx, ref, applyBotAction(priv.state, action), now, priv.deadline, botOffers);
      return true;
    });
    if (!acted) return;
  }
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));
