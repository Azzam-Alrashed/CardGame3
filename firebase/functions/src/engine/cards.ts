// Cards, deck building and dealing.

/** Suits ordered from strongest to weakest: ♠ > ♥ > ♦ > ♣ */
export const SUITS = ["S", "H", "D", "C"] as const;
export type Suit = (typeof SUITS)[number];

/** 2..14, where 11 = J, 12 = Q, 13 = K, 14 = A */
export type Rank = number;

export interface Card {
  rank: Rank;
  suit: Suit;
}

export const HAND_SIZE = 4;
export const MIN_PLAYERS = 4;
export const MAX_PLAYERS = 13;

/** Higher number = stronger suit. */
export function suitStrength(suit: Suit): number {
  return SUITS.length - SUITS.indexOf(suit);
}

/** One rank per player, always the top ranks (4 players = A, K, Q, J). */
export function buildDeck(playerCount: number): Card[] {
  if (playerCount < MIN_PLAYERS || playerCount > MAX_PLAYERS) {
    throw new Error(`Player count must be ${MIN_PLAYERS}-${MAX_PLAYERS}, got ${playerCount}`);
  }
  const deck: Card[] = [];
  for (let rank = 14; rank > 14 - playerCount; rank--) {
    for (const suit of SUITS) deck.push({ rank, suit });
  }
  return deck;
}

/** Returns a value in [0, 1). Injected so tests can be deterministic. */
export type Rng = () => number;

/** Fisher–Yates shuffle; returns a new array. */
export function shuffle<T>(items: readonly T[], rng: Rng): T[] {
  const out = [...items];
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out;
}

/** Deals HAND_SIZE cards to each of playerCount players from a fresh shuffled deck. */
export function deal(playerCount: number, rng: Rng): Card[][] {
  const deck = shuffle(buildDeck(playerCount), rng);
  const hands: Card[][] = [];
  for (let p = 0; p < playerCount; p++) {
    hands.push(deck.slice(p * HAND_SIZE, (p + 1) * HAND_SIZE));
  }
  return hands;
}
