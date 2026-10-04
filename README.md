# CardGame3

An app for playing مداقش, the poker-style card game we play with friends. Everyone joins a room from their own phone, bets, makes deals with the boss, and reveals.

<p align="center"><img src="assets/logo.svg" width="160" alt="CardGame3 logo"></p>

**Website:** [azzam-alrashed.github.io/CardGame3](https://azzam-alrashed.github.io/CardGame3/), a landing page served by GitHub Pages from [`docs/`](docs/).

## How the game works

- 4 to 13 players. The deck uses only the top ranks, one rank per player, and everyone gets 4 cards.
- Each player chooses to enter with a bet or sit the round out.
- The highest bettor becomes the **boss**. Other players can offer to withdraw in return for a share of his winnings.
- When the boss reveals, everyone without a deal reveals too, and the best hand wins. There is never a tie.

The full rules are in **[RULES.md](RULES.md)**.

## Features

- **Rooms with a 4-letter code.** Friends join from their own phones and see every move live.
- **AI players.** The host can fill empty seats with AI players (up to 13 seats), or play solo against 3 of them.
- **Step away anytime.** Leave a game in progress and a bot plays your seat until you tap **Back to game**.
- **A bot that plays fair.** It sees only its own cards, works out its odds by dealing imaginary hands from the cards it hasn't seen, and bluffs now and then.
- **First-launch onboarding** and a **How to play** sheet.
- **iPhone and iPad** in every orientation.

## Project layout

| Folder | What's inside |
| --- | --- |
| `firebase/functions/` | Backend in TypeScript: the rules engine and the bot (`src/engine/`), plus rooms, rounds and AI players as Cloud Functions |
| `firebase/firestore.rules` | Security rules: players see only their own room and their own cards |
| `ios/` | iOS app in SwiftUI (`CardGame3.xcodeproj`) |
| `assets/` | Logo and wordmark |
| `docs/` | Landing page for GitHub Pages: one static `index.html` plus images in `docs/img/` |
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

Bots (AI players and away players) also run on the server. After each move, the server plays any bot turns with a 2–4 second "thinking" pause. Firestore triggers can't be used because Cloud Functions isn't available in Dammam.

### Tests

You need Node.js. The emulator tests also need Java 21 (`brew install openjdk@21`).

```bash
cd firebase/functions
npm install
npm run test:emulator
```

`npm test` runs only the tests that don't need the emulators: the rules engine and the bot.

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
- [x] Onboarding and How to play
- [x] Leave mid-game, with a bot playing your seat
- [x] AI players added by the host
- [ ] Share with friends through TestFlight
- [ ] Android app

## License

See [LICENSE](LICENSE).
