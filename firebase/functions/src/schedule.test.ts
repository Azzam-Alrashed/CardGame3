import { afterEach, describe, expect, it } from "vitest";
import { Card } from "./engine/cards.js";
import { RoundState, placeBet, reveal, startRound, withdraw } from "./engine/round.js";
import type { Room } from "./rooms.js";
import {
  PrivateRound, botTiming, dueAction, nextBotAction, nextRoundAtFor, planBotMove, revealEndsAtFor, revealMs,
  turnDeadlineFor, wakeTime,
} from "./schedule.js";

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

  it("a person's betting turn gets 45 s; the clock keeps running until the turn passes", () => {
    const s = fresh(); // u1's turn
    expect(turnDeadlineFor(room(), null, s, 1000)).toBe(46_000);
    // Same turn, rewritten later (e.g. someone else stepped away): same deadline.
    expect(turnDeadlineFor(room(), priv(s, { turnDeadline: 46_000 }), s, 20_000)).toBe(46_000);
    // The turn passed to an AI player: no clock.
    const afterU1 = withdraw(s, "u1");
    expect(turnDeadlineFor(room(), priv(s, { turnDeadline: 46_000 }), afterU1, 20_000)).toBeNull();
    // u1 is away: their bot plays, no clock.
    expect(turnDeadlineFor(room({ away: ["u1"] }), null, s, 1000)).toBeNull();
  });

  it("the next round comes 8 s after a result (4 s after a redeal), only if someone is playing", () => {
    let s = fresh();
    for (const id of ["u1", "ai1", "ai2", "u0"]) s = withdraw(s, id);
    expect(s.result!.outcome).toBe("redeal");
    expect(nextRoundAtFor(room(), null, s, 1000)).toBe(5000);
    expect(nextRoundAtFor(room({ away: ["u0", "u1"] }), null, s, 1000)).toBeNull();

    let won = fresh();
    for (const id of ["u1", "ai1", "ai2"]) won = withdraw(won, id);
    won = placeBet(won, "u0", 500);
    expect(nextRoundAtFor(room(), null, won, 1000)).toBe(9000);
    // Rewritten later in the same finished round: keeps its time.
    expect(nextRoundAtFor(room(), priv(won, { nextRoundAt: 9000 }), won, 5000)).toBe(9000);
  });

  it("a showdown's reveal gets time to play on every phone before the next round", () => {
    const showdown = (revealed: number) =>
      ({ outcome: "showdown", winnerId: "u0", revealed: Array.from({ length: revealed }, (_, i) => `p${i}`), deltas: {} }) as const;
    expect(revealMs(showdown(2))).toBe(6300);
    expect(revealMs(showdown(4))).toBe(8300);
    expect(revealMs(showdown(13))).toBe(10_300); // challengers share 5 s at most
    expect(revealMs({ outcome: "uncontested", winnerId: "u0", revealed: [], deltas: {} })).toBe(0);

    // u1 and ai1 enter, ai1 is the boss and reveals: two hands to show.
    let s = fresh();
    s = placeBet(s, "u1", 500);
    s = placeBet(s, "ai1", 1000);
    s = withdraw(withdraw(s, "ai2"), "u0");
    s = reveal(s, "ai1");
    expect(s.result!.outcome).toBe("showdown");
    expect(revealEndsAtFor(null, s, 1000)).toBe(7300);
    expect(nextRoundAtFor(room(), null, s, 1000)).toBe(15_300);
    // Rewritten later in the same round: both keep their times.
    const later = priv(s, { revealEndsAt: 7300, nextRoundAt: 15_300 });
    expect(revealEndsAtFor(later, s, 5000)).toBe(7300);
    expect(nextRoundAtFor(room(), later, s, 5000)).toBe(15_300);
  });

  it("timers come in order: deals deadline, turn clock, next round, then bots", () => {
    const betting = fresh();
    expect(dueAction(priv(betting, { turnDeadline: 100, botMove: { action: bet, at: 50 } }), 100))
      .toEqual({ kind: "turnTimeout", id: "u1" });
    let done = fresh();
    for (const id of ["u1", "ai1", "ai2", "u0"]) done = withdraw(done, id);
    expect(dueAction(priv(done, { nextRoundAt: 100 }), 99)).toBeNull();
    expect(dueAction(priv(done, { nextRoundAt: 100 }), 100)).toEqual({ kind: "nextRound" });
    expect(wakeTime(priv(betting, { turnDeadline: 100, botMove: { action: bet, at: 50 } }))).toBe(50);
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
