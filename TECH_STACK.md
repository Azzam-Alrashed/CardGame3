# CardGame3 — Tech Stack

How the app is built. For how the game is played, see [RULES.md](RULES.md).

- Apps: native Swift (iOS) and Kotlin (Android)
- Backend: Firebase. Cloud Functions in TypeScript own all game logic (dealing, bets, deals, timer, winner); Firestore for live room updates; each phone sees only its own cards
- Sign-in: Apple / Google plus a display name
- Repo: `ios/`, `android/`, `firebase/`
- Build order: backend with rule tests, then iOS, then Android
