import { describe, expect, it } from "vitest";
import { Card, Suit } from "./cards.js";
import { BotStyle, MAX_BOT_OFFERS, botMove, handStrength, winChance } from "./bot.js";
import { RoundState, acceptOffer, makeOffer, placeBet, startRound, withdraw } from "./round.js";

const R: Record<string, number> = { A: 14, K: 13, Q: 12, J: 11 };
const h = (s: string): Card[] => s.split(" ").map((c) => ({ rank: R[c[0]], suit: c[1] as Suit }));
const always = (v: number) => () => v;

/** 4 players, dealer p0, so p1 bets first. */
function round(hands: string[], points = [5000, 5000, 5000, 5000]): RoundState {
  const s = startRound(points.map((p, i) => ({ id: `p${i}`, points: p })), 0, always(0.5));
  return { ...s, players: s.players.map((p, i) => ({ ...p, hand: h(hands[i]) })) };
}
const STRONG = "AS AH AD KC", WEAK = "JS QH KD AC";

describe("bot", () => {
  it("rates four of a kind above a pair above high card", () => {
    expect(handStrength(h("AS AH AD AC"), 11)).toBeGreaterThan(handStrength(h("AS AH KD QC"), 11));
    expect(handStrength(h("AS AH KD QC"), 11)).toBeGreaterThan(handStrength(h("JS QH KD AC"), 11));
  });

  it("only acts on its own turn", () => {
    const s = round([WEAK, STRONG, WEAK, WEAK]);
    expect(botMove(s, "p2", "balanced", 0, always(0.5))).toBeNull();
  });

  it("bets legally with a strong hand", () => {
    const s = placeBet(round([WEAK, WEAK, STRONG, WEAK]), "p1", 1000);
    const move = botMove(s, "p2", "balanced", 0, always(0.5))!;
    expect(move.kind).toBe("bet");
    if (move.kind === "bet") {
      expect(move.amount % 500).toBe(0);
      expect(move.amount).toBeGreaterThanOrEqual(1500);
      expect(() => placeBet(s, "p2", move.amount)).not.toThrow();
    }
  });

  it("withdraws a weak hand unless it bluffs", () => {
    const s = round([WEAK, WEAK, WEAK, WEAK]);
    expect(botMove(s, "p1", "careful", 0, always(0.99))!.kind).toBe("withdraw");
    expect(botMove(s, "p1", "wild", 0, always(0.01))!.kind).toBe("bet"); // bluff
  });

  it("every style always makes a legal betting move", () => {
    for (const style of ["careful", "balanced", "wild"] as BotStyle[]) {
      for (let t = 0; t < 300; t++) {
        let s = round([WEAK, STRONG, WEAK, "KS KH QD QC"], [5000, 700, 5000, 2600]);
        for (const id of ["p1", "p2", "p3", "p0"]) {
          const m = botMove(s, id, style, 0, Math.random)!;
          s = m.kind === "bet" ? placeBet(s, id, m.amount) : withdraw(s, id);
        }
        expect(s.phase).not.toBe("betting");
      }
    }
  });

  function dealsRound(bossHand: string, otherHand: string): RoundState {
    let s = round([WEAK, otherHand, bossHand, WEAK]);
    s = placeBet(s, "p1", 500);
    s = placeBet(s, "p2", 2000); // boss
    s = withdraw(s, "p3");
    return withdraw(s, "p0");
  }

  it("weak entrant offers to withdraw within what's left of the boss's bet, at most twice", () => {
    const s = dealsRound(STRONG, WEAK);
    const m = botMove(s, "p1", "balanced", 0, always(0.5))!;
    expect(m.kind).toBe("offer");
    if (m.kind === "offer") expect(m.amount).toBeLessThanOrEqual(2000);
    expect(botMove(s, "p1", "balanced", MAX_BOT_OFFERS, always(0.5))).toBeNull();
  });

  it("boss answers offers and reveals a strong hand", () => {
    const s = dealsRound(STRONG, WEAK);
    expect(botMove({ ...s, offers: { p1: 500 } }, "p2", "balanced", 0, always(0.5))!.kind).toBe("answer");
    expect(botMove(s, "p2", "balanced", 0, always(0.5))).toEqual({ kind: "reveal", bossId: "p2" });
  });

  it("weak boss waits for the timer instead of revealing", () => {
    expect(botMove(dealsRound(WEAK, STRONG), "p2", "balanced", 0, always(0.5))).toBeNull();
  });

  it("estimates real odds: K-K-J-J beats two players about half the time, high card rarely", () => {
    const twoPairs = winChance(h("JC KD JD KH"), 4, 2, Math.random);
    expect(twoPairs).toBeGreaterThan(0.4);
    expect(twoPairs).toBeLessThan(0.7);
    expect(winChance(h("JS QH KD AC"), 4, 2, Math.random)).toBeLessThan(0.2);
    expect(winChance(h("AS AH AD AC"), 4, 2, Math.random)).toBe(1);
  });

  it("the K-K-J-J boss keeps his win instead of dealing it away (the round from the screenshot)", () => {
    // Dealer p0 = boss with two pairs; p2 (Fahad) and p1 (Sara) entered, p3 withdrew.
    let s = round(["JC KD JD KH", "AS QS AH QC", "AC QH AD KS", "JS JH KC QD"]);
    s = placeBet(s, "p1", 500);
    s = placeBet(s, "p2", 1500);
    s = withdraw(s, "p3");
    s = placeBet(s, "p0", 2000);
    for (let t = 0; t < 20; t++) {
      const fahad = botMove(makeOffer(s, "p2", 1500), "p0", "balanced", 0, Math.random)!;
      expect(fahad).toMatchObject({ kind: "answer", from: "p2", accept: false });
    }
    // Even after one deal, he never gives away the rest of his win.
    const oneDeal = acceptOffer(makeOffer(s, "p1", 500), "p0", "p1");
    for (let t = 0; t < 20; t++) {
      expect(botMove(makeOffer(oneDeal, "p2", 1500), "p0", "balanced", 0, Math.random)).toMatchObject({ accept: false });
    }
    // ~55% to win is borderline: it either reveals or waits for the timer, but never deals its win away.
    const next = botMove(s, "p0", "balanced", 0, Math.random);
    expect(next === null || next.kind === "reveal").toBe(true);
  });

  it("a boss with four of a kind reveals right away", () => {
    let s = round(["AS AH AD AC", "KS KH QD QC", "KD KC JD JH", "QS QH JS JC"]);
    s = placeBet(s, "p1", 500);
    s = placeBet(s, "p2", 1000);
    s = withdraw(s, "p3");
    s = placeBet(s, "p0", 2000);
    expect(botMove(s, "p0", "balanced", 0, Math.random)).toEqual({ kind: "reveal", bossId: "p0" });
  });

  it("a weak boss is happy to buy out a strong-looking opponent cheaply", () => {
    let s = round(["JS QH KD AC", "AS AH KS KC", "QS QC JD JH", "AD KH QD JC"]);
    s = placeBet(s, "p1", 500);
    s = withdraw(s, "p2");
    s = withdraw(s, "p3");
    s = placeBet(s, "p0", 2000);
    const accepted = Array.from({ length: 20 }, () =>
      botMove(makeOffer(s, "p1", 500), "p0", "balanced", 0, Math.random)).filter((m) => m && "accept" in m && m.accept);
    expect(accepted.length).toBeGreaterThan(15);
  });
});
