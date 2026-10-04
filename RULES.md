# مداقش — House Rules

## Setup
- Players: 4 to 13
- Deck: take the top ranks, one rank per player (4 players = A, K, Q, J)
- Each player gets 4 cards
- Everyone starts with 5,000 points; reaching 0 knocks you out of the game

## Betting (one round)
- Starts with the player after the dealer, going right
- Each player chooses to enter (bet) or withdraw (no cost, out for this round only)
- Bets come in steps of 500, and the minimum is 500
- To enter you must beat the highest bet so far, or go all in
- Everyone can see who entered and how much they bet
- If someone goes all in with exactly the highest bet, that later player becomes the boss
- Each betting turn has 45 seconds; when it runs out, a bot plays your seat until you take it back

## Deals (before reveal)
- The highest bettor is the boss of the round (if tied, the latest one to reach it)
- Other players offer to withdraw in return for an amount of the boss's winnings if he wins
- An offer is just an amount of points, in steps of 500; it can be higher or lower than a previous offer (bluffing allowed)
- All accepted deals together can never be more than the boss's bet: an offer can be at most what is left of his bet after the deals he already accepted
- Offers are visible to everyone at the table, as if said out loud
- The boss accepts or rejects; an acceptance can never be undone
- A rejected player may make another offer
- Timer: 1 minute per player in the betting round

## Reveal
- The boss chooses when to reveal (or the timer runs out)
- Every player without an accepted deal must reveal with him
- The pot is only the highest bet:
  - If the boss wins, he keeps his bet and gains the same amount (it comes from no one), then pays his accepted deals from it
  - Players who revealed and lost each lose their own bet
  - If a revealing player beats him, that player wins the boss's bet
  - Players with a deal get nothing if the boss loses, but they lose nothing either
- Total points at the table grow over time (by design)
- There is never a tie. Tiebreak order:
  1. Compare the main group (a pair of Aces beats a pair of Kings)
  2. Compare the leftover cards from highest to lowest
  3. Suit of the highest card: ♠ > ♥ > ♦ > ♣
- Hand ranking (high to low): four of a kind, three of a kind, two pairs, one pair, high card

## Automatic wins
- If only one player enters, he wins his bet amount (no deals, no reveal)
- If every other player takes a deal, the boss wins his bet amount and pays all deals (no reveal)

## Between rounds
- The dealer role passes one seat to the right each round
- When players are knocked out, the deck shrinks to match the players who are left
- If everyone withdraws, the cards are reshuffled and the next dealer deals
- The next round is dealt automatically a few seconds after the result

## End of game
- The game ends when fewer than 4 players remain
- The player with the most points wins; if points are equal, whoever reached that total first wins; if they reached it in the same round, the player closest to the dealer's right wins
