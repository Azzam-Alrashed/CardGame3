// A simple bot that plays for players who stepped away from the table.
// It only sees its own hand plus what is public, and mixes in randomness and bluffs.

import { Card, HAND_SIZE, Rng, buildDeck, shuffle } from "./cards.js";
import { HandCategory, compareHands, evaluateHand } from "./hands.js";
import { BET_STEP, MIN_BET, RoundState, dealRoom, highestBet } from "./round.js";

export type BotStyle = "careful" | "balanced" | "wild";
export const BOT_STYLES: BotStyle[] = ["careful", "balanced", "wild"];

const STYLE: Record<BotStyle, { aggression: number; bluff: number }> = {
  careful: { aggression: 0.7, bluff: 0.08 },
  balanced: { aggression: 1.0, bluff: 1 / 6 },
  wild: { aggression: 1.4, bluff: 0.3 },
};

/** Most offers a bot makes in one round, so bots can't haggle forever. */
export const MAX_BOT_OFFERS = 2;

export type BotAction =
  | { kind: "bet"; id: string; amount: number }
  | { kind: "withdraw"; id: string }
  | { kind: "offer"; id: string; amount: number }
  | { kind: "answer"; bossId: string; from: string; accept: boolean }
  | { kind: "reveal"; bossId: string };

/** Imaginary deals per decision when estimating the odds. */
const SAMPLES = 600;

/**
 * Chance that `hand` beats `opponents` random hands, by dealing them imaginary hands from the cards
 * the bot hasn't seen. It never looks at anyone's real cards.
 */
export function winChance(hand: readonly Card[], playerCount: number, opponents: number, rng: Rng): number {
  if (opponents <= 0) return 1;
  const unseen = buildDeck(playerCount).filter((c) => !hand.some((h) => h.rank === c.rank && h.suit === c.suit));
  let wins = 0;
  for (let i = 0; i < SAMPLES; i++) {
    const deck = shuffle(unseen, rng);
    let beatsAll = true;
    for (let o = 0; o < opponents && beatsAll; o++) {
      beatsAll = compareHands(hand, deck.slice(o * HAND_SIZE, (o + 1) * HAND_SIZE)) > 0;
    }
    if (beatsAll) wins++;
  }
  return wins / SAMPLES;
}

/** 0 (worst) to 1 (best) for a 4-card hand. `lowestRank` is the smallest rank in this table's deck. */
export function handStrength(hand: readonly Card[], lowestRank: number): number {
  const v = evaluateHand(hand);
  const base = [0.1, 0.4, 0.62, 0.8, 0.97][v.category];
  const top = v.category === HandCategory.HighCard ? v.kickers[0] : v.groups[0];
  const rankShare = (top - lowestRank) / Math.max(1, 14 - lowestRank);
  return Math.min(1, base + 0.12 * rankShare);
}

const roundDown = (n: number) => Math.floor(n / BET_STEP) * BET_STEP;

/** What the bot playing `id` would do now, or null if it has nothing to do. */
export function botMove(
  state: RoundState,
  id: string,
  style: BotStyle,
  offersMade: number,
  rng: Rng,
): BotAction | null {
  const me = state.players.find((p) => p.id === id);
  if (!me || state.phase === "finished") return null;
  const n = state.players.length;
  const { aggression, bluff } = STYLE[style];
  const bluffing = rng() < bluff;
  const chance = (opponents: number) => winChance(me.hand, n, opponents, rng);

  if (state.phase === "betting") {
    if (state.players[state.turnIndex].id !== id) return null;
    // Opponents: those already in, plus about half of those still to decide.
    const toDecide = n - state.decidedCount - 1;
    const strength = chance(Math.max(1, Object.keys(state.bets).length + Math.ceil(toDecide / 2)));
    // Next step of 500 above the highest bet (which may be an odd all-in amount).
    const minBet = Math.max(MIN_BET, (Math.floor(highestBet(state) / BET_STEP) + 1) * BET_STEP);
    const wantsIn = bluffing || rng() < strength * aggression;
    if (!wantsIn) return { kind: "withdraw", id };
    if (minBet > me.points) {
      // Can only go all in: only with a strong hand or a bluff.
      return bluffing || strength > 0.7 ? { kind: "bet", id, amount: me.points } : { kind: "withdraw", id };
    }
    const share = bluffing ? 0.3 + rng() * 0.3 : strength * aggression * (0.2 + rng() * 0.3);
    const amount = Math.min(roundDown(me.points), Math.max(minBet, roundDown(me.points * share)));
    return { kind: "bet", id, amount };
  }

  // Deals phase. Revealers are everyone who entered and has no deal.
  const revealers = Object.keys(state.bets).filter((p) => !(p in state.deals));
  if (state.bossId === id) {
    const bet = state.bets[id];
    const left = dealRoom(state); // what the boss keeps if he wins now
    const opponents = revealers.length - 1;
    const now = chance(opponents);
    // Expected points: win what's left of the bet, or lose the whole bet.
    const valueNow = now * left - (1 - now) * bet;
    const [from, offer] = Object.entries(state.offers)[0] ?? [];
    if (from !== undefined) {
      if (offer > left) return { kind: "answer", bossId: id, from, accept: false };
      const after = chance(opponents - 1);
      const valueAfter = after * (left - offer) - (1 - after) * bet;
      // A little randomness so the bot isn't perfectly predictable.
      const accept = valueAfter + (rng() - 0.5) * bet * 0.05 > valueNow;
      return { kind: "answer", bossId: id, from, accept };
    }
    return now > 0.6 ? { kind: "reveal", bossId: id } : null;
  }
  if (!(id in state.bets) || id in state.deals || id in state.offers || offersMade >= MAX_BOT_OFFERS) return null;
  const room = dealRoom(state);
  if (room < BET_STEP) return null;
  // Odds against the boss and every other revealer.
  if (!bluffing && chance(revealers.length - 1) >= 0.4) return null; // confident: stay in and reveal
  const share = bluffing ? 0.75 + rng() * 0.25 : 0.25 + rng() * 0.35;
  return { kind: "offer", id, amount: Math.min(room, Math.max(BET_STEP, roundDown(room * share))) };
}
