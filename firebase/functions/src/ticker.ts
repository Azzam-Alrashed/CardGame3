// Keeps games moving without anyone tapping: bot moves, timers, the next round, and stalled rooms.
//
// Every write saves `wakeAt` on the room: when it next needs attention. `waker.wake` asks Cloud Tasks
// to run `tick` at that time; `tick` does one due thing and schedules the next. A sweeper runs every
// minute for rooms whose wake-up was missed.

import { Firestore, Timestamp } from "firebase-admin/firestore";
import { getFunctions } from "firebase-admin/functions";
import { logger } from "firebase-functions/v2";
import * as engine from "./engine/round.js";
import type { Room } from "./rooms.js";
import { PrivateRound, applyBotAction, awayFields, clock, dueAction, nextBotAction, wakeTime } from "./schedule.js";
import { advanceTable, privateRef, roomRef, writeRound } from "./store.js";
import { REGION } from "./util.js";

/** A missed wake-up older than this is picked up by the sweeper. */
const SWEEP_GRACE_MS = 5_000;
/** Longest a wake-up waits when it arrives early, before asking to be woken again. */
const MAX_EARLY_WAIT_MS = 30_000;
const DAY_MS = 24 * 60 * 60 * 1000;

export const waker = {
  /**
   * Runs `tick` for this room at `at` (epoch ms). Never fails the caller: the sweeper is the backstop.
   * `again` asks for a fresh task when the one for this time already ran too early.
   */
  async wake(code: string, at: number | null, again = false): Promise<void> {
    if (at === null) return;
    try {
      // The id makes repeated requests for the same wake-up harmless.
      const id = again ? `${code}-${at}-${Date.now()}` : `${code}-${at}`;
      await getFunctions()
        .taskQueue(`locations/${REGION}/functions/tickTask`)
        .enqueue({ code, at }, { scheduleTime: new Date(Math.max(at, Date.now())), id });
    } catch (e) {
      if ((e as { code?: string }).code === "functions/task-already-exists") return;
      logger.warn("Could not schedule a wake-up; the sweeper will pick it up", { code, at, error: String(e) });
    }
  },
};

/**
 * Does the one thing that is due in this room (a timer, the next round, or a bot move), if any.
 * Returns when the room next needs attention, or null.
 */
export async function tick(db: Firestore, code: string): Promise<number | null> {
  const ref = roomRef(db, code);
  return db.runTransaction(async (tx) => {
    const [roomSnap, privSnap] = await Promise.all([tx.get(ref), tx.get(privateRef(ref))]);
    const room = roomSnap.data() as Room | undefined;
    if (!room) return null;
    if (room.status !== "playing" || !privSnap.exists) {
      if (room.wakeAt != null) tx.update(ref, { wakeAt: null });
      return null;
    }
    const priv = privSnap.data() as PrivateRound;
    const now = clock.now();
    const due = dueAction(priv, now);
    if (!due) {
      // Woken early, or the schedule changed since: just make sure `wakeAt` is right.
      const at = wakeTime(priv);
      if (at !== (room.wakeAt ?? null)) tx.update(ref, { wakeAt: at });
      return at;
    }

    if (due.kind === "nextRound") return advanceTable(tx, ref, room, priv.state, now);

    let botOffers = priv.botOffers ?? {};
    let state;
    let roomFields: Partial<Room> = {};
    try {
      if (due.kind === "timeUp") {
        state = engine.timeUp(priv.state);
      } else if (due.kind === "turnTimeout") {
        // Out of time: a bot takes the seat (like stepping away) and plays this turn right away.
        roomFields = awayFields(room, due.id, true);
        const action = nextBotAction({ ...room, ...roomFields }, priv.state, botOffers);
        state = action ? applyBotAction(priv.state, action) : priv.state;
      } else {
        state = applyBotAction(priv.state, due.action);
        if (due.action.kind === "offer") botOffers = { ...botOffers, [due.action.id]: (botOffers[due.action.id] ?? 0) + 1 };
      }
    } catch (e) {
      // A planned move that no longer fits should never happen (every change replans), but don't retry it forever.
      logger.error("Dropping a bot move that no longer applies", { code, due, error: String(e) });
      const cleared: PrivateRound = { ...priv, botMove: null };
      const at = wakeTime(cleared);
      tx.set(privateRef(ref), cleared);
      tx.update(ref, { wakeAt: at });
      return at;
    }
    return writeRound(tx, ref, room, priv, state, now, roomFields, { botOffers });
  });
}

/**
 * Handles a Cloud Tasks wake-up for `at`: plays what is due, then schedules the next wake-up.
 * Tasks can arrive early (the emulator ignores the schedule entirely), so wait for the time first.
 */
export async function onWake(db: Firestore, code: string, at: number): Promise<void> {
  const early = at - clock.now();
  if (early > 0) await sleep(Math.min(early, MAX_EARLY_WAIT_MS));
  const next = await tick(db, code);
  await waker.wake(code, next, next === at);
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/** Keeps ticking while something is due right now (tests use it in place of Cloud Tasks). */
export async function runDue(db: Firestore, code: string, at: number | null): Promise<number | null> {
  let next = at;
  for (let step = 0; next !== null && next <= clock.now() && step < 200; step++) next = await tick(db, code);
  return next;
}

/** Picks up rooms whose wake-up was missed (a lost task, a deploy, a cold start). */
export async function sweep(db: Firestore): Promise<number> {
  const stuck = await db.collection("rooms").where("wakeAt", "<=", clock.now() - SWEEP_GRACE_MS).limit(50).get();
  await Promise.all(stuck.docs.map(async (doc) => {
    try {
      await waker.wake(doc.id, await tick(db, doc.id));
    } catch (e) {
      logger.error("Sweep could not tick a room", { code: doc.id, error: String(e) });
    }
  }));
  return stuck.size;
}

/**
 * Deletes rooms nobody uses any more: lobbies idle for a day, anything else idle for a week.
 * Rooms are only kept to run the game (see PRIVACY.md).
 */
export async function cleanup(db: Firestore): Promise<number> {
  const now = clock.now();
  let deleted = 0;
  const idle = await db.collection("rooms").where("updatedAt", "<", now - DAY_MS).limit(200).get();
  for (const doc of idle.docs) {
    const room = doc.data() as Room;
    if (room.status === "lobby" || (room.updatedAt ?? 0) < now - 7 * DAY_MS) {
      await db.recursiveDelete(doc.ref);
      deleted++;
    }
  }
  // Rooms written by older versions have no `updatedAt`.
  const old = await db.collection("rooms").where("createdAt", "<", Timestamp.fromMillis(now - 7 * DAY_MS)).limit(200).get();
  for (const doc of old.docs) {
    if (doc.data().updatedAt === undefined) {
      await db.recursiveDelete(doc.ref);
      deleted++;
    }
  }
  return deleted;
}
