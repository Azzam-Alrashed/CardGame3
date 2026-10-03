# CardGame3

An app for playing مداقش, the poker-style card game we play with friends. Everyone joins a room from their own phone, bets, makes deals with the boss, and reveals.

## How the game works

- 4 to 13 players. The deck uses only the top ranks, one rank per player, and everyone gets 4 cards.
- Each player chooses to enter with a bet or sit the round out.
- The highest bettor becomes the **boss**. Other players can offer to withdraw in return for a share of his winnings.
- When the boss reveals, everyone without a deal reveals too, and the best hand wins. There is never a tie.

The full rules are in **[RULES.md](RULES.md)**.

## Project layout

| Folder | What's inside |
| --- | --- |
| `firebase/functions/` | Backend game logic in TypeScript. The rules engine lives in `src/engine/`. |
| `ios/` | iOS app in Swift *(coming soon)* |
| `android/` | Android app in Kotlin *(coming soon)* |

Tech choices are explained in **[TECH_STACK.md](TECH_STACK.md)**.

## Running the tests

You need Node.js.

```bash
cd firebase/functions
npm install
npm test
```

## Status

- [x] House rules written down
- [x] Rules engine, with tests for every rule
- [ ] Firebase: rooms, join by code, live updates
- [ ] iOS app
- [ ] Android app

## License

See [LICENSE](LICENSE).
