import { afterEach, describe, expect, it } from "vitest";
import { Card } from "./engine/cards.js";
import { RoundState, placeBet, startRound, withdraw } from "./engine/round.js";
import type { Room } from "./rooms.js";
import { PrivateRound, botTiming, dueAction, nextBotAction, planBotMove, wakeTime } from "./schedule.js";

const seats = ["u0", "u1", "ai1", "ai2"].map((id) => ({ id, points: 5000 }));
const fixedRng = () => 0.5;
/** Dealer u0, so u1 bets first, then ai1, ai2, u0. */
const fresh = (): RoundState => startRound(seats, 0, fixedRng, 1);
const room = (extra: Partial<Room> = {}) => ({ aiPlayers: ["ai1", "ai2"], ...extra }) as Room;
const priv = (state: RoundState, extra: Partial<PrivateRound> = {}): PrivateRound =>
  ({ state, deadline: null, botOffers: {}, botMove: null, ...extra });
const bet = { kind: "bet", id: "ai1", amount: 1000 } as const;

describe("schedule", () => {
  afterEach(() => {
    botTiming.minMs = 0;
    botTiming.maxMs = 0;
  });

  it("only plans a bot move when it is a bot's turn", () => {
    const s = fresh();
    expect(nextBotAction(room(), s, {})).toBeNull(); // u1 (human) to bet
    const afterU1 = withdraw(s, "u1");
    expect(nextBotAction(room(), afterU1, {})).toMatchObject({ id: "ai1" });
  });

  it("an away player's seat is played by a bot", () => {
    expect(nextBotAction(room({ away: ["u1"] }), fresh(), {})).toMatchObject({ id: "u1" });
  });

  it("plans the move after a thinking pause", () => {
    botTiming.minMs = 2000;
    botTiming.maxMs = 4000;
    const planned = planBotMove(room(), withdraw(fresh(), "u1"), {}, 10_000)!;
    expect(planned.at).toBeGreaterThanOrEqual(12_000);
    expect(planned.at).toBeLessThanOrEqual(14_000);
  });

  it("finished rounds have nothing for bots to do", () => {
    let s = fresh();
    s = withdraw(s, "u1");
    s = withdraw(s, "ai1");
    s = withdraw(s, "ai2");
    s = placeBet(s, "u0", 500); // only one entrant: round over
    expect(s.phase).toBe("finished");
    expect(nextBotAction(room(), s, {})).toBeNull();
  });

  it("wakes at the earlier of the deals timer and the next bot move", () => {
    const deals = { ...fresh(), phase: "deals" as const };
    expect(wakeTime(priv(fresh()))).toBeNull();
    expect(wakeTime(priv(fresh(), { botMove: { action: bet, at: 500 } }))).toBe(500);
    expect(wakeTime(priv(deals, { deadline: 300, botMove: { action: bet, at: 500 } }))).toBe(300);
    // The deals deadline only counts in the deals phase.
    expect(wakeTime(priv(fresh(), { deadline: 300 }))).toBeNull();
  });

  it("the timer comes before bots, and nothing is due early", () => {
    const deals = { ...fresh(), phase: "deals" as const };
    const p = priv(deals, { deadline: 1000, botMove: { action: bet, at: 900 } });
    expect(dueAction(p, 600)).toBeNull(); // more than the early-wake slack before the move
    expect(dueAction(p, 900)).toEqual({ kind: "bot", action: bet });
    expect(dueAction(p, 1000)).toEqual({ kind: "timeUp" });
  });

  it("never looks at anyone's cards to plan", () => {
    // Planning only reads the bot's own hand: swapping the humans' cards changes nothing.
    const s = withdraw(fresh(), "u1");
    const swapped: RoundState = {
      ...s,
      players: s.players.map((p) => (p.id === "u1" ? { ...p, hand: [] as Card[] } : p)),
    };
    expect(nextBotAction(room(), swapped, {})).toMatchObject({ id: "ai1" });
  });
});
