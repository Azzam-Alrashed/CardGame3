import { describe, expect, it } from "vitest";
import { Card, Suit, buildDeck, deal } from "./cards.js";
import { HandCategory, compareHands, evaluateHand } from "./hands.js";
import {
  RoundState, Seat, acceptOffer, makeOffer, placeBet, rejectOffer, reveal, startRound, timeUp, withdraw,
} from "./round.js";
import { isGameOver, newTable, nextTable } from "./game.js";

const R: Record<string, number> = { A: 14, K: 13, Q: 12, J: 11, T: 10, "9": 9 };
/** h("AS KH QD JC") */
const h = (s: string): Card[] => s.split(" ").map((c) => ({ rank: R[c[0]], suit: c[1] as Suit }));
const fixedRng = () => 0.5;

function seats(...points: number[]): Seat[] {
  return points.map((p, i) => ({ id: `p${i}`, points: p }));
}

/** A round with chosen hands. Dealer is p0, so p1 bets first. */
function round(hands: string[], points = hands.map(() => 5000)): RoundState {
  const s = startRound(seats(...points), 0, fixedRng);
  return { ...s, players: s.players.map((p, i) => ({ ...p, hand: h(hands[i]) })) };
}

describe("deck", () => {
  it("uses the top ranks, one per player", () => {
    const ranks = new Set(buildDeck(5).map((c) => c.rank));
    expect([...ranks]).toEqual([14, 13, 12, 11, 10]);
    expect(buildDeck(4)).toHaveLength(16);
    expect(buildDeck(13)).toHaveLength(52);
  });
  it("allows 4 to 13 players", () => {
    expect(() => buildDeck(3)).toThrow();
    expect(() => buildDeck(14)).toThrow();
  });
  it("deals 4 unique cards to everyone", () => {
    const hands = deal(6, Math.random);
    expect(hands.every((x) => x.length === 4)).toBe(true);
    expect(new Set(hands.flat().map((c) => c.rank + c.suit)).size).toBe(24);
  });
});

describe("hands", () => {
  it("ranks categories", () => {
    expect(evaluateHand(h("AS AH AD AC")).category).toBe(HandCategory.FourOfAKind);
    expect(evaluateHand(h("KS KH KD AC")).category).toBe(HandCategory.ThreeOfAKind);
    expect(evaluateHand(h("KS KH QD QC")).category).toBe(HandCategory.TwoPairs);
    expect(evaluateHand(h("KS KH QD AC")).category).toBe(HandCategory.OnePair);
    expect(evaluateHand(h("AS KH QD JC")).category).toBe(HandCategory.HighCard);
  });
  it("higher category wins", () => {
    expect(compareHands(h("JS JH QD KC"), h("AS KH QD TC"))).toBeGreaterThan(0);
    expect(compareHands(h("KS KH QD QC"), h("AS AH KD JC"))).toBeGreaterThan(0);
  });
  it("bigger group wins within a category", () => {
    expect(compareHands(h("AS AH QD JC"), h("KS KH AD QC"))).toBeGreaterThan(0);
  });
  it("then leftover cards, highest first", () => {
    expect(compareHands(h("KS KH AD JC"), h("KD KC QS JH"))).toBeGreaterThan(0);
  });
  it("then suit of the highest card: ♠ > ♥ > ♦ > ♣", () => {
    expect(compareHands(h("AS KD QD JC"), h("AH KS QS JS"))).toBeGreaterThan(0);
    expect(compareHands(h("KD KC AH QS"), h("KS KH AD QC"))).toBeGreaterThan(0);
  });
  it("never ties for any two different hands", () => {
    for (let t = 0; t < 2000; t++) {
      const [a, b] = deal(4 + (t % 10), Math.random);
      expect(compareHands(a, b)).not.toBe(0);
    }
  });
});

describe("betting", () => {
  const hands = ["AS AH KD QC", "KS KH QD JC", "QS QH JD AC", "JS JH AD KC"];

  it("starts with the player after the dealer and goes right", () => {
    let s = round(hands);
    expect(() => placeBet(s, "p0", 500)).toThrow();
    s = placeBet(s, "p1", 500);
    expect(s.players[s.turnIndex].id).toBe("p2");
  });
  it("needs 500 minimum, steps of 500, and must beat the highest", () => {
    const s = round(hands);
    expect(() => placeBet(s, "p1", 400)).toThrow();
    expect(() => placeBet(s, "p1", 750)).toThrow();
    const s2 = placeBet(s, "p1", 1000);
    expect(() => placeBet(s2, "p2", 1000)).toThrow();
    expect(placeBet(s2, "p2", 1500).bets.p2).toBe(1500);
  });
  it("allows all in below the highest bet", () => {
    let s = round(hands, [5000, 5000, 300, 5000]);
    s = placeBet(s, "p1", 2000);
    s = placeBet(s, "p2", 300);
    expect(s.bets.p2).toBe(300);
  });
  it("an all in matching the highest bet makes that later player the boss", () => {
    let s = round(hands, [5000, 5000, 2000, 5000]);
    s = placeBet(s, "p1", 2000);
    s = placeBet(s, "p2", 2000); // all in
    s = withdraw(s, "p3");
    s = withdraw(s, "p0");
    expect(s.bossId).toBe("p2");
  });
  it("highest bettor becomes boss, timer is 1 min per entrant", () => {
    let s = round(hands);
    s = placeBet(s, "p1", 500);
    s = placeBet(s, "p2", 1500);
    s = withdraw(s, "p3");
    s = withdraw(s, "p0");
    expect(s.phase).toBe("deals");
    expect(s.bossId).toBe("p2");
    expect(s.timerMs).toBe(2 * 60_000);
  });
  it("everyone withdraws: redeal, nobody loses", () => {
    let s = round(hands);
    for (const id of ["p1", "p2", "p3", "p0"]) s = withdraw(s, id);
    expect(s.result!.outcome).toBe("redeal");
    expect(s.players.every((p) => p.points === 5000)).toBe(true);
  });
  it("one entrant wins his bet automatically", () => {
    let s = round(hands);
    s = withdraw(s, "p1");
    s = placeBet(s, "p2", 1000);
    s = withdraw(s, "p3");
    s = withdraw(s, "p0");
    expect(s.result!.outcome).toBe("uncontested");
    expect(s.players.find((p) => p.id === "p2")!.points).toBe(6000);
  });
});

describe("deals and reveal", () => {
  // p1 = pair of Aces (best), p2 = pair of Kings, p3 = pair of Queens
  const hands = ["JS JH JD JC", "AS AH KD QC", "KS KH QD AC", "QS QH AD KC"];

  /** p1 bets 500, p2 1000, p3 2000 (boss); p0 withdraws. */
  function dealsPhase(): RoundState {
    let s = round(hands);
    s = placeBet(s, "p1", 500);
    s = placeBet(s, "p2", 1000);
    s = placeBet(s, "p3", 2000); // boss
    s = withdraw(s, "p0");
    return s;
  }

  it("only non-boss entrants can offer, in steps of 500", () => {
    const s = dealsPhase();
    expect(s.bossId).toBe("p3");
    expect(() => makeOffer(s, "p3", 500)).toThrow();
    expect(() => makeOffer(s, "p0", 500)).toThrow();
    expect(() => makeOffer(s, "p1", 250)).toThrow();
  });
  it("after a rejection the player can offer again, higher or lower", () => {
    let s = makeOffer(dealsPhase(), "p1", 1000);
    s = rejectOffer(s, "p3", "p1");
    s = makeOffer(s, "p1", 1500);
    expect(s.offers.p1).toBe(1500);
  });
  it("deals can never add up to more than the boss's bet", () => {
    let s = dealsPhase(); // boss bet 2000
    expect(() => makeOffer(s, "p1", 2500)).toThrow();
    s = acceptOffer(makeOffer(s, "p1", 1500), "p3", "p1");
    expect(() => makeOffer(s, "p2", 1000)).toThrow();
    // An offer made earlier cannot be accepted once the room is gone.
    let t = makeOffer(makeOffer(dealsPhase(), "p1", 1500), "p2", 1000);
    t = acceptOffer(t, "p3", "p1");
    expect(() => acceptOffer(t, "p3", "p2")).toThrow();
  });
  it("an accepted deal can never be undone", () => {
    let s = makeOffer(dealsPhase(), "p1", 500);
    s = acceptOffer(s, "p3", "p1");
    expect(() => makeOffer(s, "p1", 1000)).toThrow();
    expect(s.deals.p1).toBe(500);
  });
  it("boss wins showdown: gains his bet, pays deals, revealing losers lose their bet", () => {
    let s = makeOffer(dealsPhase(), "p1", 500); // p1 holds the best hand but takes a deal
    s = acceptOffer(s, "p3", "p1");
    s = reveal(s, "p3");
    // Revealed: p2 (Kings) vs p3 (Queens). Kings beat Queens, so the boss loses here.
    expect(s.result!.revealed.sort()).toEqual(["p2", "p3"]);
    expect(s.result!.winnerId).toBe("p2");
    expect(s.result!.deltas).toEqual({ p2: 2000, p3: -2000 });
  });
  it("boss wins: deals are paid from his winnings", () => {
    let s = round(["JS JH JD JC", "QS QH AD KC", "KS KH QD AC", "AS AH KD QC"]);
    s = placeBet(s, "p1", 500);
    s = placeBet(s, "p2", 1000);
    s = placeBet(s, "p3", 2000); // boss with Aces
    s = withdraw(s, "p0");
    s = acceptOffer(makeOffer(s, "p1", 500), "p3", "p1");
    s = timeUp(s);
    expect(s.result!.winnerId).toBe("p3");
    expect(s.result!.deltas).toEqual({ p3: 1500, p1: 500, p2: -1000 });
    expect(s.players.find((p) => p.id === "p3")!.points).toBe(6500);
  });
  it("everyone else takes a deal: boss wins automatically, no reveal", () => {
    let s = dealsPhase();
    s = acceptOffer(makeOffer(s, "p1", 500), "p3", "p1");
    s = acceptOffer(makeOffer(s, "p2", 1000), "p3", "p2");
    expect(s.result!.outcome).toBe("allDeals");
    expect(s.result!.revealed).toEqual([]);
    expect(s.result!.deltas).toEqual({ p3: 500, p1: 500, p2: 1000 });
  });
});

describe("between rounds", () => {
  it("knocks out players at 0 and passes the dealer right, skipping them", () => {
    const table = newTable(["a", "b", "c", "d", "e"]);
    let s = startRound(table.seats, 0, fixedRng);
    s = {
      ...s,
      phase: "finished",
      players: s.players.map((p) => (p.id === "b" ? { ...p, points: 0 } : p)),
    };
    const next = nextTable(s);
    if (isGameOver(next)) throw new Error("should continue");
    expect(next.seats.map((x) => x.id)).toEqual(["a", "c", "d", "e"]);
    expect(next.seats[next.dealerIndex].id).toBe("c");
  });
  it("equal points at the end: whoever reached that total first wins", () => {
    let s = startRound(
      [
        { id: "late", points: 7000, changedAt: 5 },
        { id: "early", points: 7000, changedAt: 2 },
        { id: "x", points: 0 },
        { id: "y", points: 3000 },
      ],
      0, fixedRng, 6,
    );
    s = { ...s, phase: "finished" };
    const next = nextTable(s);
    expect(isGameOver(next) && next.winnerId).toBe("early");
  });
  it("same total in the same round: closest to the dealer's right wins", () => {
    let s = startRound(
      [
        { id: "a", points: 7000, changedAt: 3 },
        { id: "dealer", points: 0 },
        { id: "b", points: 3000 },
        { id: "c", points: 7000, changedAt: 3 },
      ],
      1, fixedRng, 4,
    );
    s = { ...s, phase: "finished" };
    const next = nextTable(s);
    expect(isGameOver(next) && next.winnerId).toBe("c");
  });
  it("ends when fewer than 4 remain; most points wins", () => {
    let s = startRound(seats(0, 9000, 4000, 7000), 0, fixedRng);
    s = { ...s, phase: "finished" };
    const next = nextTable(s);
    expect(isGameOver(next) && next.winnerId).toBe("p1");
  });
});
