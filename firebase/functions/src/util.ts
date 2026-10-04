// Small helpers shared by the Cloud Functions handlers.

import { randomInt } from "node:crypto";
import { HttpsError } from "firebase-functions/v2/https";

/** Doha: closest Cloud Functions region to the Firestore database in Dammam (me-central2 has no Functions). */
export const REGION = "me-central1";

export const CODE_LENGTH = 4;

export function cleanCode(code: unknown): string {
  const c = typeof code === "string" ? code.trim().toUpperCase() : "";
  if (c.length !== CODE_LENGTH) throw new HttpsError("invalid-argument", "Room code must be 4 letters");
  return c;
}

export function toPoints(n: unknown): number {
  if (typeof n !== "number" || !Number.isInteger(n)) throw new HttpsError("invalid-argument", "Amount must be a whole number");
  return n;
}

/** Crypto-strong randomness for shuffling and bots. */
export const secureRng = () => randomInt(2 ** 32) / 2 ** 32;
