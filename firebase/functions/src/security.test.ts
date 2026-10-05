// Checks the Firestore security rules from a player's point of view.
// Runs against the Firestore emulator: npm run test:emulator
import { readFileSync } from "node:fs";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import {
  RulesTestEnvironment, assertFails, assertSucceeds, initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc } from "firebase/firestore";

const onEmulator = !!process.env.FIRESTORE_EMULATOR_HOST;

describe.skipIf(!onEmulator)("security rules", () => {
  let env: RulesTestEnvironment;

  beforeAll(async () => {
    const [host, port] = process.env.FIRESTORE_EMULATOR_HOST!.split(":");
    env = await initializeTestEnvironment({
      projectId: "cardgame-3",
      firestore: { rules: readFileSync("../firestore.rules", "utf8"), host, port: Number(port) },
    });
  });
  afterAll(() => env.cleanup());

  beforeEach(async () => {
    await env.clearFirestore();
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, "rooms/ABCD"), { playerIds: ["alice", "bob"] });
      await setDoc(doc(db, "rooms/ABCD/hands/alice"), { cards: [] });
      await setDoc(doc(db, "rooms/ABCD/hands/bob"), { cards: [] });
      await setDoc(doc(db, "rooms/ABCD/private/round"), { state: {} });
    });
  });

  const as = (uid: string | null) =>
    (uid ? env.authenticatedContext(uid) : env.unauthenticatedContext()).firestore();

  it("players can read their own room; outsiders and signed-out users cannot", async () => {
    await assertSucceeds(getDoc(doc(as("alice"), "rooms/ABCD")));
    await assertFails(getDoc(doc(as("eve"), "rooms/ABCD")));
    await assertFails(getDoc(doc(as(null), "rooms/ABCD")));
  });

  it("players can read only their own cards", async () => {
    await assertSucceeds(getDoc(doc(as("alice"), "rooms/ABCD/hands/alice")));
    await assertFails(getDoc(doc(as("alice"), "rooms/ABCD/hands/bob")));
  });

  it("nobody can read the private round state", async () => {
    await assertFails(getDoc(doc(as("alice"), "rooms/ABCD/private/round")));
  });

  it("reports are server-only: players can't read or file them directly", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "reports/alice_bob"), { reporter: "alice", reported: "bob" });
    });
    await assertFails(getDoc(doc(as("alice"), "reports/alice_bob")));
    await assertFails(getDoc(doc(as("bob"), "reports/alice_bob")));
    await assertFails(setDoc(doc(as("alice"), "reports/alice_eve"), { reporter: "alice", reported: "eve" }));
  });

  it("nobody can write anything directly", async () => {
    await assertFails(setDoc(doc(as("alice"), "rooms/ABCD"), { playerIds: ["alice"] }));
    await assertFails(setDoc(doc(as("alice"), "rooms/ABCD/hands/alice"), { cards: [] }));
    await assertFails(setDoc(doc(as("alice"), "rooms/WXYZ"), { playerIds: ["alice"] }));
  });
});
