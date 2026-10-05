// When things happen on their own: bot moves and timers.
// Pure functions over the stored round, so they can be tested without Firestore.

import { randomInt } from "node:crypto";
import { BOT_STYLES, BotAction, botMove } from "./engine/bot.js";
import * as engine from "./engine/round.js";
import { RoundState } from "./engine/round.js";
import type { Room } from "./rooms.js";
import { secureRng } from "./util.js";

/** The current time. Tests replace it to move time forward. */
export const clock = { now: () => Date.now() };

/** Pause before each bot move so it feels like someone thinking. Tests shorten this. */
export const botTiming = { minMs: 2000, maxMs: 4000 };

/** How long a person has for a betting turn before a bot takes their seat. */
export const TURN_MS = 45_000;
/** How long the result stays up, after the reveal, before the next round is dealt (shorter when everyone withdrew). */
export const NEXT_ROUND_MS = 8_000;
export const REDEAL_MS = 4_000;

/** Full engine state of the current round; never readable by clients. Stored at rooms/{code}/private/round. */
export interface PrivateRound {
  state: RoundState;
  /** Epoch ms when the deals timer runs out (deals phase only). */
  deadline: number | null;
  /** How many offers each bot made this round. */
  botOffers?: Record<string, number>;
  /** The next bot move, decided when the round last changed and played after a short pause. */
  botMove?: PlannedBotMove | null;
  /** Epoch ms when the person whose betting turn it is runs out of time. */
  turnDeadline?: number | null;
  /** Epoch ms when the next round is dealt (finished rounds only). */
  nextRoundAt?: number | null;
  /** Epoch ms when phones finish staging the reveal (finished rounds only); no dealing before then. */
  revealEndsAt?: number | null;
}

export interface PlannedBotMove {
  action: BotAction;
  at: number;
}

/** Something that is due now, in the order it should happen. */
export type Due =
  | { kind: "timeUp" }
  | { kind: "turnTimeout"; id: string }
  | { kind: "nextRound" }
  | { kind: "bot"; action: BotAction };

/** A wake-up this close to its time counts as on time. */
const EARLY_SLACK_MS = 250;

/** The move the first bot with something to do (AI players, then away players) wants to make, or null. */
export function nextBotAction(room: Room, state: RoundState, botOffers: Record<string, number>): BotAction | null {
  if (state.phase === "finished") return null;
  for (const id of [...(room.aiPlayers ?? []), ...(room.away ?? [])]) {
    const style = room.botStyles?.[id] ?? "balanced";
    const action = botMove(state, id, style, botOffers[id] ?? 0, secureRng);
    if (action) return action;
  }
  return null;
}

/** Decides the next bot move now and schedules it after a thinking pause. */
export function planBotMove(
  room: Room, state: RoundState, botOffers: Record<string, number>, now: number,
): PlannedBotMove | null {
  const action = nextBotAction(room, state, botOffers);
  if (!action) return null;
  return { action, at: Math.round(now + botTiming.minMs + Math.random() * (botTiming.maxMs - botTiming.minMs)) };
}

const isPerson = (room: Room, id: string) => !(room.aiPlayers ?? []).includes(id) && !(room.away ?? []).includes(id);

/** Room fields for a player stepping away (a bot with its own style plays for them) or coming back. */
export function awayFields(room: Room, uid: string, away: boolean): Pick<Room, "away" | "botStyles"> {
  const ids = new Set(room.away ?? []);
  if (away) ids.add(uid);
  else ids.delete(uid);
  const botStyles = { ...room.botStyles };
  botStyles[uid] ??= BOT_STYLES[randomInt(BOT_STYLES.length)];
  return { away: [...ids], botStyles };
}

/**
 * When the person whose betting turn it is runs out of time, or null (not betting, or a bot's turn).
 * The clock keeps running while the turn stays the same, and restarts when it passes to someone new.
 */
export function turnDeadlineFor(room: Room, prev: PrivateRound | null, state: RoundState, now: number): number | null {
  if (state.phase !== "betting" || !isPerson(room, state.players[state.turnIndex].id)) return null;
  const sameTurn = prev !== null && prev.state.phase === "betting"
    && prev.state.roundNumber === state.roundNumber && prev.state.decidedCount === state.decidedCount;
  return (sameTurn ? prev.turnDeadline : null) ?? now + TURN_MS;
}

/**
 * How long phones take to stage a showdown: the challengers turn their cards over one at a time,
 * a drumroll, the boss card by card, then the verdict. Other outcomes have nothing to reveal.
 * Mirrored by `RevealTimeline` in the iOS app (Models/Choreography.swift); keep them in step.
 */
export function revealMs(result: engine.RoundResult | null): number {
  if (result?.outcome !== "showdown") return 0;
  const challengers = result.revealed.length - 1;
  return 5300 + Math.min(1000 * challengers, 5000);
}

/** When a finished round's reveal is over on every phone: the earliest the next round may be dealt. */
export function revealEndsAtFor(prev: PrivateRound | null, state: RoundState, now: number): number | null {
  if (state.phase !== "finished") return null;
  const sameRound = prev !== null && prev.state.phase === "finished" && prev.state.roundNumber === state.roundNumber;
  return (sameRound ? prev.revealEndsAt : null) ?? now + revealMs(state.result);
}

/**
 * When to deal the next round (a few seconds after the reveal), or null. Only while someone seated
 * is actually at the table: a table of bots and away players waits for a person to come back.
 */
export function nextRoundAtFor(room: Room, prev: PrivateRound | null, state: RoundState, now: number): number | null {
  if (state.phase !== "finished" || !state.players.some((p) => isPerson(room, p.id))) return null;
  const sameRound = prev !== null && prev.state.phase === "finished" && prev.state.roundNumber === state.roundNumber;
  const wait = state.result?.outcome === "redeal" ? REDEAL_MS : NEXT_ROUND_MS;
  const revealEnds = revealEndsAtFor(prev, state, now) ?? now;
  return (sameRound ? prev.nextRoundAt : null) ?? Math.max(now, revealEnds) + wait;
}

/** When this round next needs attention, or null if it is waiting on people. */
export function wakeTime(priv: PrivateRound): number | null {
  const times = [
    priv.state.phase === "deals" ? priv.deadline : null,
    priv.turnDeadline ?? null,
    priv.nextRoundAt ?? null,
    priv.botMove?.at ?? null,
  ].filter((t): t is number => t !== null);
  return times.length ? Math.min(...times) : null;
}

/** What should happen now, if anything. Timers come before bots. */
export function dueAction(priv: PrivateRound, now: number): Due | null {
  const { state } = priv;
  if (state.phase === "deals" && priv.deadline !== null && priv.deadline <= now) return { kind: "timeUp" };
  if (state.phase === "betting" && priv.turnDeadline != null && priv.turnDeadline <= now) {
    return { kind: "turnTimeout", id: state.players[state.turnIndex].id };
  }
  if (state.phase === "finished" && priv.nextRoundAt != null && priv.nextRoundAt <= now) return { kind: "nextRound" };
  if (priv.botMove && priv.botMove.at <= now + EARLY_SLACK_MS) return { kind: "bot", action: priv.botMove.action };
  return null;
}

export function applyBotAction(state: RoundState, action: BotAction): RoundState {
  switch (action.kind) {
    case "bet": return engine.placeBet(state, action.id, action.amount);
    case "withdraw": return engine.withdraw(state, action.id);
    case "offer": return engine.makeOffer(state, action.id, action.amount);
    case "answer":
      return action.accept
        ? engine.acceptOffer(state, action.bossId, action.from)
        : engine.rejectOffer(state, action.bossId, action.from);
    case "reveal": return engine.reveal(state, action.bossId);
  }
}
