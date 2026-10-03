// Hand ranking and comparison. There is never a tie.

import { Card, HAND_SIZE, suitStrength } from "./cards.js";

export enum HandCategory {
  HighCard = 0,
  OnePair = 1,
  TwoPairs = 2,
  ThreeOfAKind = 3,
  FourOfAKind = 4,
}

export interface HandValue {
  category: HandCategory;
  /** Ranks of the paired/tripled/quad groups, strongest first. */
  groups: number[];
  /** Ranks of the leftover single cards, highest first. */
  kickers: number[];
  /** Suit strength of the highest card (best suit among cards of the top rank). */
  topSuit: number;
}

export function evaluateHand(hand: readonly Card[]): HandValue {
  if (hand.length !== HAND_SIZE) {
    throw new Error(`A hand must have ${HAND_SIZE} cards, got ${hand.length}`);
  }
  const counts = new Map<number, number>();
  for (const c of hand) counts.set(c.rank, (counts.get(c.rank) ?? 0) + 1);

  // Bigger groups first, then higher rank.
  const byGroup = [...counts.entries()].sort((a, b) => b[1] - a[1] || b[0] - a[0]);
  const groups = byGroup.filter(([, n]) => n > 1).map(([r]) => r);
  const kickers = byGroup.filter(([, n]) => n === 1).map(([r]) => r);

  const topCount = byGroup[0][1];
  let category: HandCategory;
  if (topCount === 4) category = HandCategory.FourOfAKind;
  else if (topCount === 3) category = HandCategory.ThreeOfAKind;
  else if (groups.length === 2) category = HandCategory.TwoPairs;
  else if (groups.length === 1) category = HandCategory.OnePair;
  else category = HandCategory.HighCard;

  const topRank = Math.max(...hand.map((c) => c.rank));
  const topSuit = Math.max(...hand.filter((c) => c.rank === topRank).map((c) => suitStrength(c.suit)));

  return { category, groups, kickers, topSuit };
}

/** Positive if a beats b, negative if b beats a. Never 0 for two hands from the same deck. */
export function compareHands(a: readonly Card[], b: readonly Card[]): number {
  const va = evaluateHand(a);
  const vb = evaluateHand(b);
  if (va.category !== vb.category) return va.category - vb.category;
  for (let i = 0; i < va.groups.length; i++) {
    if (va.groups[i] !== vb.groups[i]) return va.groups[i] - vb.groups[i];
  }
  for (let i = 0; i < va.kickers.length; i++) {
    if (va.kickers[i] !== vb.kickers[i]) return va.kickers[i] - vb.kickers[i];
  }
  return va.topSuit - vb.topSuit;
}
