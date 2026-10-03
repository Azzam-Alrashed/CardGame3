// Playing rounds on top of rooms.
//
// Storage per room:
//   rooms/{code}                 public: room + `round` (everything players may see)
//   rooms/{code}/hands/{uid}     one player's 4 cards, readable only by that player
//   rooms/{code}/private/round   full engine state including every hand; never readable by clients

import { randomInt } from "node:crypto";
import { DocumentReference, Firestore, Transaction } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
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
  writeRound(tx, ref, state, now, null, { table, ...roomFields });
}

function writeRound(
  tx: Transaction, ref: DocumentReference, state: RoundState, now: number, deadline: number | null,
  roomFields: Record<string, unknown> = {},
): void {
  // Start the timer when the round enters the deals phase.
  const d = state.phase === "deals" ? (deadline ?? now + state.timerMs!) : null;
  const priv: PrivateRound = { state, deadline: d };
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
    const { state, deadline } = privSnap.data() as PrivateRound;
    const now = Date.now();
    let next: RoundState;
    try {
      next = apply(state, { now, deadline });
    } catch (e) {
      if (e instanceof HttpsError) throw e;
      throw new HttpsError("failed-precondition", (e as Error).message);
    }
    writeRound(tx, ref, next, now, deadline);
  });
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
}
