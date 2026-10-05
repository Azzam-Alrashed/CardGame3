// Keeps offensive words out of player names (they are shown to everyone at the table).
//
// Matching is by whole word, after undoing common disguises (l33t letters, Arabic letter variants),
// so ordinary names that merely contain a short bad word (Hassan, Bassam) are fine. A few long
// words are also caught inside other words.

/** Whole words, in their normalized form (see `normalize`). */
const WORDS = new Set([
  // English
  "fuck", "fucker", "fucking", "fuk", "shit", "bitch", "cunt", "dick", "cock", "pussy", "whore", "slut",
  "nigger", "nigga", "fag", "faggot", "retard", "rape", "rapist", "nazi", "hitler", "porn", "sex", "penis",
  "vagina", "asshole", "bastard",
  // Arabic
  "كس", "زب", "زبي", "طيز", "شرموط", "شرموطه", "قحبه", "منيوك", "منيوكه", "متناك", "نيك", "نياك", "خول",
  "عرص", "عاهره", "زانيه", "لوطي", "منيك", "كسمك", "كسختك",
]);

/** Caught even inside other words. */
const FRAGMENTS = ["fuck", "nigg", "faggot", "whore", "شرموط", "قحب", "منيوك", "متناك"];

const LEET: Record<string, string> = { "0": "o", "1": "i", "3": "e", "4": "a", "5": "s", "7": "t", "@": "a", "$": "s" };

/** Lowercase, l33t letters undone, Arabic diacritics and letter variants unified. */
export function normalize(text: string): string {
  return text
    .toLowerCase()
    .replace(/[013457@$]/g, (c) => LEET[c])
    .replace(/[ً-ْـ]/g, "") // harakat and tatweel
    .replace(/[أإآ]/g, "ا")
    .replace(/ة/g, "ه")
    .replace(/ى/g, "ي");
}

export function isOffensive(name: string): boolean {
  const clean = normalize(name);
  const words = clean.split(/[^\p{L}]+/u).filter(Boolean);
  if (words.some((w) => WORDS.has(w))) return true;
  // "f u c k" or "f.u.c.k": also check the name with everything but letters removed.
  const squashed = words.join("");
  return FRAGMENTS.some((f) => squashed.includes(f));
}
