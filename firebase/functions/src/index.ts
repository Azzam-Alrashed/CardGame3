// Cloud Functions entry point. Every call requires a signed-in user.

import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { setGlobalOptions } from "firebase-functions/v2";
import { CallableRequest, HttpsError, onCall } from "firebase-functions/v2/https";
import * as play from "./play.js";
import * as rooms from "./rooms.js";

// Doha: closest Cloud Functions region to the Firestore database in Dammam (me-central2 has no Functions).
export const REGION = "me-central1";
// "public" lets phones reach the functions; each one still requires a signed-in user.
// Bots play their moves inside the call that triggered them, with pauses, so allow time for that.
setGlobalOptions({ region: REGION, maxInstances: 10, invoker: "public", timeoutSeconds: 300 });

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

export const bet = onCall((req) => play.bet(db, uidOf(req), req.data ?? {}));
export const withdraw = onCall((req) => play.withdraw(db, uidOf(req), req.data ?? {}));
export const makeOffer = onCall((req) => play.makeOffer(db, uidOf(req), req.data ?? {}));
export const answerOffer = onCall((req) => play.answerOffer(db, uidOf(req), req.data ?? {}));
export const reveal = onCall((req) => play.reveal(db, uidOf(req), req.data ?? {}));
export const timeUp = onCall((req) => play.timeUp(db, uidOf(req), req.data ?? {}));
export const nextRound = onCall((req) => play.nextRound(db, uidOf(req), req.data ?? {}));
export const setAway = onCall((req) => play.setAway(db, uidOf(req), req.data ?? {}));
