// One round: betting → deals → reveal → result.
// Every function is pure: it takes a state and returns a new one, throwing on illegal moves.

import { Card, Rng, deal } from "./cards.js";
import { compareHands } from "./hands.js";

export const BET_STEP = 500;
export const MIN_BET = 500;
export const TIMER_MS_PER_PLAYER = 60_000;

export interface Seat {
  id: string;
  points: number;
  /** Round number when this player's points last changed (breaks end-of-game ties). */
  changedAt?: number;
}

export interface RoundPlayer extends Seat {
  hand: Card[];
}

export type Phase = "betting" | "deals" | "finished";

export type Outcome =
  | "redeal" // everyone withdrew
  | "uncontested" // only one player entered
  | "allDeals" // every other entrant took a deal
  | "showdown";

export interface RoundResult {
  outcome: Outcome;
  winnerId: string | null;
  /** Players whose cards were revealed. */
  revealed: string[];
  /** Points change per player id (missing = 0). */
  deltas: Record<string, number>;
}

export interface RoundState {
  roundNumber: number;
  /** Seats in table order. "Going right" means index + 1. */
  players: RoundPlayer[];
  dealerIndex: number;
  phase: Phase;
  /** Index into players of whoever must decide next (betting phase only). */
  turnIndex: number;
  decidedCount: number;
  /** Entrants and their bets, in the order they entered. */
  bets: Record<string, number>;
  withdrawn: string[];
  bossId: string | null;
  /** Latest unanswered offer per player. */
  offers: Record<string, number>;
  /** Accepted deals; can never be undone. */
  deals: Record<string, number>;
  /** Length of the deals timer, set when the deals phase starts. */
  timerMs: number | null;
  result: RoundResult | null;
}

export function startRound(seats: readonly Seat[], dealerIndex: number, rng: Rng, roundNumber = 0): RoundState {
  const hands = deal(seats.length, rng);
  return {
    roundNumber,
    players: seats.map((s, i) => ({ ...s, hand: hands[i] })),
    dealerIndex,
    phase: "betting",
    turnIndex: (dealerIndex + 1) % seats.length,
    decidedCount: 0,
    bets: {},
    withdrawn: [],
    bossId: null,
    offers: {},
    deals: {},
    timerMs: null,
    result: null,
  };
}

/** The highest bet placed so far (0 if nobody has entered). */
export function highestBet(state: RoundState): number {
  return Math.max(0, ...Object.values(state.bets));
}

function player(state: RoundState, id: string): RoundPlayer {
  const p = state.players.find((x) => x.id === id);
  if (!p) throw new Error(`Unknown player ${id}`);
  return p;
}

function assertTurn(state: RoundState, id: string): void {
  if (state.phase !== "betting") throw new Error("Betting is closed");
  if (state.players[state.turnIndex].id !== id) throw new Error(`It is not ${id}'s turn`);
}

/** Enter the round with a bet. Must beat the highest bet in steps of 500, or be all in. */
export function placeBet(state: RoundState, id: string, amount: number): RoundState {
  assertTurn(state, id);
  const me = player(state, id);
  const allIn = amount === me.points;
  if (amount <= 0 || amount > me.points) throw new Error("Bet must be between 1 and your points");
  if (!allIn) {
    if (amount < MIN_BET) throw new Error(`Minimum bet is ${MIN_BET}`);
    if (amount % BET_STEP !== 0) throw new Error(`Bets go in steps of ${BET_STEP}`);
    if (amount <= highestBet(state)) throw new Error("You must beat the highest bet or go all in");
  }
  return advanceTurn({ ...state, bets: { ...state.bets, [id]: amount } });
}

/** Sit this round out. Costs nothing. */
export function withdraw(state: RoundState, id: string): RoundState {
  assertTurn(state, id);
  return advanceTurn({ ...state, withdrawn: [...state.withdrawn, id] });
}

function advanceTurn(state: RoundState): RoundState {
  const decidedCount = state.decidedCount + 1;
  const n = state.players.length;
  if (decidedCount < n) {
    return { ...state, decidedCount, turnIndex: (state.turnIndex + 1) % n };
  }
  return closeBetting({ ...state, decidedCount });
}

function closeBetting(state: RoundState): RoundState {
  const entrants = Object.keys(state.bets);
  if (entrants.length === 0) {
    return finish(state, { outcome: "redeal", winnerId: null, revealed: [], deltas: {} });
  }
  if (entrants.length === 1) {
    const [only] = entrants;
    return finish(state, {
      outcome: "uncontested",
      winnerId: only,
      revealed: [],
      deltas: { [only]: state.bets[only] },
    });
  }
  // The latest player to reach the highest bet is the boss (a matching all-in takes it).
  const top = highestBet(state);
  const bossId = entrants.filter((id) => state.bets[id] === top).pop()!;
  return {
    ...state,
    phase: "deals",
    bossId,
    timerMs: TIMER_MS_PER_PLAYER * entrants.length,
  };
}

function assertDealsPhase(state: RoundState): void {
  if (state.phase !== "deals") throw new Error("Not in the deals phase");
}

/** Offer to withdraw in return for `amount` of the boss's winnings if he wins. Replaces any earlier offer. */
export function makeOffer(state: RoundState, id: string, amount: number): RoundState {
  assertDealsPhase(state);
  if (id === state.bossId) throw new Error("The boss cannot make offers");
  if (!(id in state.bets)) throw new Error("Only players in the round can make offers");
  if (id in state.deals) throw new Error("You already have a deal");
  if (amount <= 0 || amount % BET_STEP !== 0) throw new Error(`Offers go in steps of ${BET_STEP}`);
  if (amount > dealRoom(state)) throw new Error("An offer cannot exceed what is left of the boss's bet");
  return { ...state, offers: { ...state.offers, [id]: amount } };
}

export function rejectOffer(state: RoundState, bossId: string, fromId: string): RoundState {
  assertBossWithOffer(state, bossId, fromId);
  const { [fromId]: _, ...offers } = state.offers;
  return { ...state, offers };
}

export function acceptOffer(state: RoundState, bossId: string, fromId: string): RoundState {
  assertBossWithOffer(state, bossId, fromId);
  const { [fromId]: amount, ...offers } = state.offers;
  if (amount > dealRoom(state)) throw new Error("Not enough left of the boss's bet for this deal");
  const next = { ...state, offers, deals: { ...state.deals, [fromId]: amount } };
  const others = Object.keys(next.bets).filter((id) => id !== next.bossId);
  if (others.every((id) => id in next.deals)) {
    return settle(next, "allDeals", next.bossId!, []);
  }
  return next;
}

/** What is left of the boss's bet after the deals he already accepted. */
export function dealRoom(state: RoundState): number {
  const taken = Object.values(state.deals).reduce((a, b) => a + b, 0);
  return state.bets[state.bossId!] - taken;
}

function assertBossWithOffer(state: RoundState, bossId: string, fromId: string): void {
  assertDealsPhase(state);
  if (bossId !== state.bossId) throw new Error("Only the boss can answer offers");
  if (!(fromId in state.offers)) throw new Error(`No open offer from ${fromId}`);
}

/** The boss chooses to reveal. */
export function reveal(state: RoundState, bossId: string): RoundState {
  assertDealsPhase(state);
  if (bossId !== state.bossId) throw new Error("Only the boss can reveal");
  return showdown(state);
}

/** The deals timer ran out: forced reveal. */
export function timeUp(state: RoundState): RoundState {
  assertDealsPhase(state);
  return showdown(state);
}

function showdown(state: RoundState): RoundState {
  const revealed = Object.keys(state.bets).filter((id) => !(id in state.deals));
  const winnerId = revealed.reduce((best, id) =>
    compareHands(player(state, id).hand, player(state, best).hand) > 0 ? id : best,
  );
  return settle(state, "showdown", winnerId, revealed);
}

function settle(state: RoundState, outcome: Outcome, winnerId: string, revealed: string[]): RoundState {
  const bossId = state.bossId!;
  const bossBet = state.bets[bossId];
  const deltas: Record<string, number> = {};

  if (winnerId === bossId) {
    // Boss gains his bet amount (from no one) and pays his deals from it.
    const dealTotal = Object.values(state.deals).reduce((a, b) => a + b, 0);
    deltas[bossId] = bossBet - dealTotal;
    for (const [id, amount] of Object.entries(state.deals)) deltas[id] = amount;
  } else {
    // A revealing player beat the boss and wins the boss's bet. Deal holders get nothing.
    deltas[winnerId] = bossBet;
    deltas[bossId] = -bossBet;
  }
  // Every other revealing player loses their own bet.
  for (const id of revealed) {
    if (id !== winnerId && id !== bossId) deltas[id] = -state.bets[id];
  }
  return finish(state, { outcome, winnerId, revealed, deltas });
}

function finish(state: RoundState, result: RoundResult): RoundState {
  const players = state.players.map((p) => ({
    ...p,
    points: p.points + (result.deltas[p.id] ?? 0),
    changedAt: result.deltas[p.id] ? state.roundNumber : p.changedAt,
  }));
  return { ...state, players, offers: {}, phase: "finished", result };
}
