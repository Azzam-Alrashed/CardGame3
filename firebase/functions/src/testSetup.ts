// Test setup: bots move without pausing, and wake-ups run right away instead of through Cloud Tasks,
// so each call returns once every bot move it triggered has been played.
import { getFirestore } from "firebase-admin/firestore";
import { botTiming } from "./schedule.js";
import { runDue, waker } from "./ticker.js";

botTiming.minMs = 0;
botTiming.maxMs = 0;
waker.wake = async (code, at) => {
  await runDue(getFirestore(), code, at);
};
