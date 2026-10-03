// Cloud Functions entry point. Every call requires a signed-in user.

import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { CallableRequest, HttpsError, onCall } from "firebase-functions/v2/https";
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
