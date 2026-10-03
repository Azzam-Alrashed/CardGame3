# CardGame3

An app for playing مداقش, the poker-style card game we play with friends. Everyone joins a room from their own phone, bets, makes deals with the boss, and reveals.

<p align="center"><img src="assets/logo.svg" width="160" alt="CardGame3 logo"></p>

## How the game works

- 4 to 13 players. The deck uses only the top ranks, one rank per player, and everyone gets 4 cards.
- Each player chooses to enter with a bet or sit the round out.
- The highest bettor becomes the **boss**. Other players can offer to withdraw in return for a share of his winnings.
- When the boss reveals, everyone without a deal reveals too, and the best hand wins. There is never a tie.

The full rules are in **[RULES.md](RULES.md)**.

## Project layout

| Folder | What's inside |
| --- | --- |
| `firebase/functions/` | Backend in TypeScript: the rules engine (`src/engine/`), rooms and rounds as Cloud Functions |
| `firebase/firestore.rules` | Security rules: players see only their own room and their own cards |
| `ios/` | iOS app in SwiftUI (`CardGame3.xcodeproj`) |
| `assets/` | Logo and wordmark |
| `android/` | Android app in Kotlin *(coming soon)* |

Tech choices are explained in **[TECH_STACK.md](TECH_STACK.md)**.

## Backend

| | |
| --- | --- |
| Firebase project | `cardgame-3` |
| Firestore | `me-central2` (Dammam) |
| Cloud Functions | `me-central1` (Doha). Dammam has no Cloud Functions. |
| Sign-in | Anonymous, plus a display name |

All game logic runs on the server. Phones only call functions and listen to their room, so nobody can peek at other players' cards.

### Tests

You need Node.js. The emulator tests also need Java 21 (`brew install openjdk@21`).

```bash
cd firebase/functions
npm install
npm run test:emulator
```

`npm test` runs only the rules engine tests, without the emulators.

### Deploy

```bash
firebase deploy --only firestore:rules,functions
```

## iOS app

You need Xcode. Open `ios/CardGame3.xcodeproj`.

The Xcode project is committed and is the source of truth, including the signing team and other settings. Add new files through Xcode.

| Scheme | Backend |
| --- | --- |
| **CardGame3** | Live Firebase. Use this on real iPhones. |
| **CardGame3 (Emulators)** | Local emulators. Start them first with `firebase emulators:start --only auth,functions,firestore`. |

To install on your iPhone, select it as the device and set your **Team** under Signing & Capabilities.

## Status

- [x] House rules written down
- [x] Rules engine, with tests for every rule
- [x] Backend: rooms, rounds, deals, timer, security rules
- [x] Backend deployed to Firebase
- [x] iOS app: home, lobby, table, results, game over
- [x] iPhone and iPad, every orientation
- [x] Uploaded to App Store Connect
- [ ] Share with friends through TestFlight
- [ ] Android app

## License

See [LICENSE](LICENSE).
