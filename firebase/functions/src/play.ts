// Playing rounds on top of rooms. Storage is described in store.ts.

import { randomInt } from "node:crypto";
import { Firestore } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { BOT_STYLES } from "./engine/bot.js";
import { isGameOver, nextTable } from "./engine/game.js";
import * as engine from "./engine/round.js";
import { RoundState } from "./engine/round.js";
import type { Room } from "./rooms.js";
import { PrivateRound, clock } from "./schedule.js";
import { dealRound, handRef, privateRef, roomRef, writeRound } from "./store.js";
import { waker } from "./ticker.js";
import { cleanCode, toPoints } from "./util.js";

export type { PublicRound } from "./store.js";

/** Loads the round, applies a move, and saves it — all in one transaction. Then wakes the room for bots. */
async function move(
  db: Firestore,
  codeIn: unknown,
  uid: string,
  apply: (state: RoundState, ctx: { now: number; deadline: number | null }) => RoundState,
): Promise<void> {
  const code = cleanCode(codeIn);
  const ref = roomRef(db, code);
  const wakeAt = await db.runTransaction(async (tx) => {
    const [roomSnap, privSnap] = await Promise.all([tx.get(ref), tx.get(privateRef(ref))]);
    if (!roomSnap.exists) throw new HttpsError("not-found", "No room with that code");
    const room = roomSnap.data() as Room;
    if (!room.playerIds.includes(uid)) throw new HttpsError("permission-denied", "You are not in this room");
    if (room.status !== "playing" || !privSnap.exists) throw new HttpsError("failed-precondition", "No round in progress");
    const priv = privSnap.data() as PrivateRound;
    const now = clock.now();
    let next: RoundState;
    try {
      next = apply(priv.state, { now, deadline: priv.deadline });
    } catch (e) {
      if (e instanceof HttpsError) throw e;
      throw new HttpsError("failed-precondition", (e as Error).message);
    }
    return writeRound(tx, ref, room, priv, next, now);
  });
  await waker.wake(code, wakeAt);
}

export const bet = (db: Firestore, uid: string, d: { code?: unknown; amount?: unknown }) =>
  move(db, d.code, uid, (s) => engine.placeBet(s, uid, toPoints(d.amount)));

export const withdraw = (db: Firestore, uid: string, d: { code?: unknown }) =>
  move(db, d.code, uid, (s) => engine.withdraw(s, uid));

export const makeOffer = (db: Firestore, uid: string, d: { code?: unknown; amount?: unknown }) =>
  move(db, d.code, uid, (s, { now, deadline }) => {
    assertBeforeDeadline(now, deadline);
    return engine.makeOffer(s, uid, toPoints(d.amount));
  });

export const answerOffer = (db: Firestore, uid: string, d: { code?: unknown; from?: unknown; accept?: unknown }) =>
  move(db, d.code, uid, (s, { now, deadline }) => {
    assertBeforeDeadline(now, deadline);
    if (typeof d.from !== "string") throw new HttpsError("invalid-argument", "Missing offer sender");
    return d.accept === true ? engine.acceptOffer(s, uid, d.from) : engine.rejectOffer(s, uid, d.from);
  });

export const reveal = (db: Firestore, uid: string, d: { code?: unknown }) =>
  move(db, d.code, uid, (s) => engine.reveal(s, uid));

/**
 * Forces the reveal once the deals timer has run out. The server does this on its own now;
 * version 1.0 of the app still calls it from every phone, so it stays.
 */
export const timeUp = (db: Firestore, uid: string, d: { code?: unknown }) =>
  move(db, d.code, uid, (s, { now, deadline }) => {
    if (deadline === null || now < deadline) throw new HttpsError("failed-precondition", "The timer is still running");
    return engine.timeUp(s);
  });

function assertBeforeDeadline(now: number, deadline: number | null): void {
  if (deadline !== null && now >= deadline) throw new HttpsError("deadline-exceeded", "Time is up");
}

/**
 * Any player may call this after a round finishes: applies knockouts, passes the dealer right,
 * and deals the next round — or ends the game. `roundNumber` makes double calls harmless.
 */
export async function nextRound(db: Firestore, uid: string, d: { code?: unknown; roundNumber?: unknown }): Promise<void> {
  const code = cleanCode(d.code);
  const ref = roomRef(db, code);
  const wakeAt = await db.runTransaction(async (tx) => {
    const [roomSnap, privSnap] = await Promise.all([tx.get(ref), tx.get(privateRef(ref))]);
    if (!roomSnap.exists) throw new HttpsError("not-found", "No room with that code");
    const room = roomSnap.data() as Room;
    if (!room.playerIds.includes(uid)) throw new HttpsError("permission-denied", "You are not in this room");
    if (room.status !== "playing" || !privSnap.exists) return null;
    const { state } = privSnap.data() as PrivateRound;
    if (state.roundNumber !== d.roundNumber) return null; // someone already moved on
    if (state.phase !== "finished") throw new HttpsError("failed-precondition", "The round is not finished");

    const outcome = nextTable(state);
    // Knocked-out players' old hands are deleted; everyone else's are overwritten by the new deal.
    const stillIn = isGameOver(outcome) ? [] : outcome.seats.map((s) => s.id);
    for (const p of state.players) if (!stillIn.includes(p.id)) tx.delete(handRef(ref, p.id));
    if (isGameOver(outcome)) {
      tx.update(ref, { status: "finished", gameOver: outcome, wakeAt: null, updatedAt: clock.now() });
      return null;
    }
    return dealRound(tx, ref, room, outcome, clock.now());
  });
  await waker.wake(code, wakeAt);
}

// MARK: Away players

/**
 * Step away from the table (a bot plays for you) or come back.
 * Only during a game; in the lobby, players leave the room instead.
 */
export async function setAway(db: Firestore, uid: string, d: { code?: unknown; away?: unknown }): Promise<void> {
  const code = cleanCode(d.code);
  const ref = roomRef(db, code);
  const wakeAt = await db.runTransaction(async (tx) => {
    const [snap, privSnap] = await Promise.all([tx.get(ref), tx.get(privateRef(ref))]);
    if (!snap.exists) throw new HttpsError("not-found", "No room with that code");
    const room = snap.data() as Room;
    if (!room.playerIds.includes(uid)) throw new HttpsError("permission-denied", "You are not in this room");
    if (room.status !== "playing" || !privSnap.exists) throw new HttpsError("failed-precondition", "No game in progress");
    const away = new Set(room.away ?? []);
    if (d.away === true) away.add(uid);
    else away.delete(uid);
    const botStyles = { ...room.botStyles };
    botStyles[uid] ??= BOT_STYLES[randomInt(BOT_STYLES.length)];
    // Rewriting the round replans bot moves: a player who stepped away gets one, one who came back loses it.
    const priv = privSnap.data() as PrivateRound;
    return writeRound(tx, ref, room, priv, priv.state, clock.now(), { away: [...away], botStyles });
  });
  await waker.wake(code, wakeAt);
}
