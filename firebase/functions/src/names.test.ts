import { describe, expect, it } from "vitest";
import { isOffensive } from "./names.js";

describe("player names", () => {
  it("allows ordinary names, including ones that contain a short bad word", () => {
    for (const name of ["Azzam", "Sara", "Hassan", "Bassam", "Dickens", "Scunthorpe", "عزام", "سارة", "Bot Fahd 🤖", "Ace 4"]) {
      expect(isOffensive(name), name).toBe(false);
    }
  });

  it("blocks offensive words, in English and Arabic", () => {
    for (const name of ["fuck you", "Big Dick", "shit", "قحبة", "يا شرموطة", "كس"]) {
      expect(isOffensive(name), name).toBe(true);
    }
  });

  it("sees through common disguises", () => {
    for (const name of ["F.U.C.K", "sh1t", "b1tch", "f u c k e r", "N1GGA", "قَحبة", "motherfucker"]) {
      expect(isOffensive(name), name).toBe(true);
    }
  });
});
