// When things happen on their own: bot moves and timers.
// Pure functions over the stored round, so they can be tested without Firestore.

import { BotAction, botMove } from "./engine/bot.js";
import * as engine from "./engine/round.js";
import { RoundState } from "./engine/round.js";
import type { Room } from "./rooms.js";
import { secureRng } from "./util.js";

/** The current time. Tests replace it to move time forward. */
export const clock = { now: () => Date.now() };

/** Pause before each bot move so it feels like someone thinking. Tests shorten this. */
export const botTiming = { minMs: 2000, maxMs: 4000 };

/** Full engine state of the current round; never readable by clients. Stored at rooms/{code}/private/round. */
export interface PrivateRound {
  state: RoundState;
  /** Epoch ms when the deals timer runs out (deals phase only). */
  deadline: number | null;
  /** How many offers each bot made this round. */
  botOffers?: Record<string, number>;
  /** The next bot move, decided when the round last changed and played after a short pause. */
  botMove?: PlannedBotMove | null;
}

export interface PlannedBotMove {
  action: BotAction;
  at: number;
}

/** Something that is due now, in the order it should happen. */
export type Due = { kind: "timeUp" } | { kind: "bot"; action: BotAction };

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

/** When this round next needs attention, or null if it is waiting on people. */
export function wakeTime(priv: PrivateRound): number | null {
  const times = [
    priv.state.phase === "deals" ? priv.deadline : null,
    priv.botMove?.at ?? null,
  ].filter((t): t is number => t !== null);
  return times.length ? Math.min(...times) : null;
}

/** What should happen now, if anything. Timers come before bots. */
export function dueAction(priv: PrivateRound, now: number): Due | null {
  if (priv.state.phase === "deals" && priv.deadline !== null && priv.deadline <= now) return { kind: "timeUp" };
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
