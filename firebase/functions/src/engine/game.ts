// Between rounds: knockouts, dealer rotation, end of game.

import { MIN_PLAYERS } from "./cards.js";
import { RoundState, Seat } from "./round.js";

export const STARTING_POINTS = 5000;

export interface Table {
  seats: Seat[];
  dealerIndex: number;
  roundNumber: number;
}

export interface GameOver {
  winnerId: string;
  standings: Seat[];
}

export function newTable(playerIds: readonly string[]): Table {
  return {
    seats: playerIds.map((id) => ({ id, points: STARTING_POINTS, changedAt: 0 })),
    dealerIndex: 0,
    roundNumber: 1,
  };
}

/**
 * Applies a finished round: knocks out players at 0 and passes the dealer one seat to the right
 * (skipping knocked-out players). Returns the next table, or GameOver if fewer than 4 remain.
 */
export function nextTable(round: RoundState): Table | GameOver {
  if (round.phase !== "finished") throw new Error("Round is not finished");
  const all: Seat[] = round.players.map(({ id, points, changedAt }) => ({ id, points, changedAt }));
  const seats = all.filter((s) => s.points > 0);

  if (seats.length < MIN_PLAYERS) {
    // Equal points: whoever reached that total first wins; same round: closest to the dealer's right.
    const n = all.length;
    const fromDealer = (s: Seat) => (all.indexOf(s) - round.dealerIndex - 1 + n) % n;
    const standings = [...all].sort(
      (a, b) =>
        b.points - a.points || (a.changedAt ?? 0) - (b.changedAt ?? 0) || fromDealer(a) - fromDealer(b),
    );
    return { winnerId: standings[0].id, standings };
  }

  const n = all.length;
  let i = (round.dealerIndex + 1) % n;
  while (all[i].points <= 0) i = (i + 1) % n;
  const dealerIndex = seats.findIndex((s) => s.id === all[i].id);
  return { seats, dealerIndex, roundNumber: round.roundNumber + 1 };
}

export function isGameOver(x: Table | GameOver): x is GameOver {
  return "winnerId" in x;
}
