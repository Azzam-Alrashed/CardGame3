// Cloud Functions entry point. Every call requires a signed-in user.

import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { setGlobalOptions } from "firebase-functions/v2";
import { CallableRequest, HttpsError, onCall } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { onTaskDispatched } from "firebase-functions/v2/tasks";
import * as play from "./play.js";
import * as rooms from "./rooms.js";
import * as ticker from "./ticker.js";
import { REGION } from "./util.js";

// "public" lets phones reach the callable functions; each one still requires a signed-in user.
setGlobalOptions({ region: REGION, maxInstances: 10, invoker: "public" });

initializeApp();
const db = getFirestore();

function uidOf(req: CallableRequest): string {
  if (!req.auth) throw new HttpsError("unauthenticated", "Sign in first");
  return req.auth.uid;
}

export const createRoom = onCall((req) => rooms.createRoom(db, uidOf(req), req.data ?? {}));
export const joinRoom = onCall((req) => rooms.joinRoom(db, uidOf(req), req.data ?? {}));
export const leaveRoom = onCall((req) => rooms.leaveRoom(db, uidOf(req), req.data ?? {}));
export const startGame = onCall((req) => rooms.startGame(db, uidOf(req), req.data ?? {}));
export const addAiPlayer = onCall((req) => rooms.addAiPlayer(db, uidOf(req), req.data ?? {}));
export const removeAiPlayer = onCall((req) => rooms.removeAiPlayer(db, uidOf(req), req.data ?? {}));
export const removePlayer = onCall((req) => rooms.removePlayer(db, uidOf(req), req.data ?? {}));
export const rematch = onCall((req) => rooms.rematch(db, uidOf(req), req.data ?? {}));

export const bet = onCall((req) => play.bet(db, uidOf(req), req.data ?? {}));
export const withdraw = onCall((req) => play.withdraw(db, uidOf(req), req.data ?? {}));
export const makeOffer = onCall((req) => play.makeOffer(db, uidOf(req), req.data ?? {}));
export const answerOffer = onCall((req) => play.answerOffer(db, uidOf(req), req.data ?? {}));
export const reveal = onCall((req) => play.reveal(db, uidOf(req), req.data ?? {}));
export const timeUp = onCall((req) => play.timeUp(db, uidOf(req), req.data ?? {}));
export const nextRound = onCall((req) => play.nextRound(db, uidOf(req), req.data ?? {}));
export const setAway = onCall((req) => play.setAway(db, uidOf(req), req.data ?? {}));

// MARK: Background work (see ticker.ts)

/** Wakes a room at its `wakeAt`: plays a bot move or a timer, then schedules the next wake-up. */
export const tickTask = onTaskDispatched(
  {
    invoker: "private", // only Cloud Tasks
    retryConfig: { maxAttempts: 3, minBackoffSeconds: 5 },
    rateLimits: { maxConcurrentDispatches: 50 },
  },
  async (req) => {
    const data = (req.data ?? {}) as { code?: unknown; at?: unknown };
    await ticker.onWake(db, String(data.code ?? ""), typeof data.at === "number" ? data.at : Date.now());
  },
);

/** Backstop for wake-ups that were lost. */
export const sweep = onSchedule({ schedule: "every 1 minutes", timeZone: "Asia/Riyadh" }, async () => {
  await ticker.sweep(db);
});

/** Deletes idle rooms once a day. */
export const cleanup = onSchedule({ schedule: "every day 04:00", timeZone: "Asia/Riyadh" }, async () => {
  await ticker.cleanup(db);
});
