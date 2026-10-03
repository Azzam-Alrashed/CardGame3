import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    // Emulator tests share one Firestore, so test files must not run at the same time.
    fileParallelism: false,
  },
});
