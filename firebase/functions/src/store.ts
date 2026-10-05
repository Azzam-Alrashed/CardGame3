// Where a round is stored, and the one place that writes it.
//
// Storage per room:
//   rooms/{code}                 public: room + `round` (everything players may see)
//   rooms/{code}/hands/{uid}     one player's 4 cards and the round they're for, readable only by that player
//   rooms/{code}/private/round   full engine state including every hand; never readable by clients

import { DocumentReference, Firestore, Transaction } from "firebase-admin/firestore";
import { Card } from "./engine/cards.js";
import { Table, isGameOver, nextTable } from "./engine/game.js";
import * as engine from "./engine/round.js";
import { RoundState } from "./engine/round.js";
import type { Room } from "./rooms.js";
import { PrivateRound, nextRoundAtFor, planBotMove, revealEndsAtFor, turnDeadlineFor, wakeTime } from "./schedule.js";
import { secureRng } from "./util.js";

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
  /** Epoch ms when the person whose betting turn it is runs out of time (a bot then takes their seat). */
  turnDeadline: number | null;
  /** Epoch ms when the next round is dealt (finished rounds only). */
  nextRoundAt: number | null;
  /** Epoch ms when phones finish staging the showdown (finished rounds only); no dealing before then. */
  revealEndsAt: number | null;
  result: engine.RoundResult | null;
  /** Cards of players who revealed (finished rounds only). */
  revealedHands: Record<string, Card[]>;
}

export function publicView(
  state: RoundState, timers: Pick<PrivateRound, "deadline" | "turnDeadline" | "nextRoundAt" | "revealEndsAt">,
): PublicRound {
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
    deadline: timers.deadline,
    turnDeadline: timers.turnDeadline ?? null,
    nextRoundAt: timers.nextRoundAt ?? null,
    revealEndsAt: timers.revealEndsAt ?? null,
    result: state.result,
    revealedHands: Object.fromEntries(
      state.players.filter((p) => revealed.includes(p.id)).map((p) => [p.id, p.hand]),
    ),
  };
}

export const roomRef = (db: Firestore, code: string) => db.collection("rooms").doc(code);
export const privateRef = (room: DocumentReference) => room.collection("private").doc("round");
export const handRef = (room: DocumentReference, uid: string) => room.collection("hands").doc(uid);

/**
 * Saves a round: the private state, the public view, and when the room next needs attention.
 * Call inside a transaction, after all reads. `room` is the room as read; `roomFields` are changes
 * to save with it (they count when deciding bot moves, e.g. a player who just stepped away).
 * Returns the room's new `wakeAt`.
 */
export function writeRound(
  tx: Transaction,
  ref: DocumentReference,
  room: Room,
  prev: PrivateRound | null,
  state: RoundState,
  now: number,
  roomFields: Record<string, unknown> = {},
  overrides: { botOffers?: Record<string, number> } = {},
): number | null {
  const sameRound = prev !== null && prev.state.roundNumber === state.roundNumber;
  // The deals timer starts when the round enters the deals phase.
  const deadline = state.phase === "deals" ? ((sameRound ? prev.deadline : null) ?? now + state.timerMs!) : null;
  const botOffers = overrides.botOffers ?? (sameRound ? (prev.botOffers ?? {}) : {});
  const effective = { ...room, ...roomFields } as Room;
  const priv: PrivateRound = {
    state,
    deadline,
    botOffers,
    botMove: planBotMove(effective, state, botOffers, now),
    turnDeadline: turnDeadlineFor(effective, prev, state, now),
    nextRoundAt: nextRoundAtFor(effective, prev, state, now),
    revealEndsAt: revealEndsAtFor(prev, state, now),
  };
  const wakeAt = wakeTime(priv);
  tx.set(privateRef(ref), priv);
  tx.update(ref, { ...roomFields, round: publicView(state, priv), wakeAt, updatedAt: now });
  return wakeAt;
}

/**
 * Deals a new round for the table and writes all docs. Call inside a transaction, after all reads.
 * Everyone's cards start face down again (`peeks` is cleared).
 */
export function dealRound(
  tx: Transaction, ref: DocumentReference, room: Room, table: Table, now: number,
  roomFields: Record<string, unknown> = {},
): number | null {
  const state = engine.startRound(table.seats, table.dealerIndex, secureRng, table.roundNumber);
  for (const p of state.players) tx.set(handRef(ref, p.id), { cards: p.hand, round: state.roundNumber });
  return writeRound(tx, ref, room, null, state, now, { table, peeks: {}, ...roomFields });
}

/**
 * After a finished round: applies knockouts, passes the dealer right, and deals the next round —
 * or ends the game. Call inside a transaction, after all reads. Returns the room's new `wakeAt`.
 */
export function advanceTable(
  tx: Transaction, ref: DocumentReference, room: Room, state: RoundState, now: number,
): number | null {
  const outcome = nextTable(state);
  // Knocked-out players' old hands are deleted; everyone else's are overwritten by the new deal.
  const stillIn = isGameOver(outcome) ? [] : outcome.seats.map((s) => s.id);
  for (const p of state.players) if (!stillIn.includes(p.id)) tx.delete(handRef(ref, p.id));
  if (isGameOver(outcome)) {
    tx.update(ref, { status: "finished", gameOver: outcome, wakeAt: null, updatedAt: now });
    return null;
  }
  return dealRound(tx, ref, room, outcome, now);
}
