// Cloud Functions entry point. Every call requires a signed-in user.

import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { CallableRequest, HttpsError, onCall } from "firebase-functions/v2/https";
import * as play from "./play.js";
import * as rooms from "./rooms.js";

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

export const bet = onCall((req) => play.bet(db, uidOf(req), req.data ?? {}));
export const withdraw = onCall((req) => play.withdraw(db, uidOf(req), req.data ?? {}));
export const makeOffer = onCall((req) => play.makeOffer(db, uidOf(req), req.data ?? {}));
export const answerOffer = onCall((req) => play.answerOffer(db, uidOf(req), req.data ?? {}));
export const reveal = onCall((req) => play.reveal(db, uidOf(req), req.data ?? {}));
export const timeUp = onCall((req) => play.timeUp(db, uidOf(req), req.data ?? {}));
export const nextRound = onCall((req) => play.nextRound(db, uidOf(req), req.data ?? {}));
