import Foundation

struct Card: Decodable, Equatable, Hashable {
    let rank: Int
    let suit: String
}

/// Everything players may see about the current round.
struct PublicRound: Decodable, Equatable {
    enum Phase: String, Decodable {
        case betting, deals, finished
    }

    let roundNumber: Int
    let phase: Phase
    let dealerId: String
    let turnId: String?
    let bets: [String: Int]
    let withdrawn: [String]
    let bossId: String?
    let offers: [String: Int]
    let deals: [String: Int]
    /// Epoch milliseconds when the deals timer runs out.
    let deadline: Double?
    /// Epoch milliseconds when the person whose betting turn it is runs out of time (then a bot plays).
    let turnDeadline: Double?
    /// Epoch milliseconds when the server deals the next round.
    let nextRoundAt: Double?
    let result: RoundResult?
    let revealedHands: [String: [Card]]

    var highestBet: Int { bets.values.max() ?? 0 }

    /// What is left of the boss's bet for new deals.
    var dealRoom: Int {
        guard let bossId, let bossBet = bets[bossId] else { return 0 }
        return bossBet - deals.values.reduce(0, +)
    }

    var deadlineDate: Date? { deadline.map(Date.init(epochMs:)) }
    var turnDeadlineDate: Date? { turnDeadline.map(Date.init(epochMs:)) }
    var nextRoundDate: Date? { nextRoundAt.map(Date.init(epochMs:)) }
}

extension Date {
    /// Dates from the server are epoch milliseconds.
    init(epochMs: Double) { self.init(timeIntervalSince1970: epochMs / 1000) }
}

struct RoundResult: Decodable, Equatable {
    enum Outcome: String, Decodable {
        case redeal, uncontested, allDeals, showdown
    }

    let outcome: Outcome
    let winnerId: String?
    let revealed: [String]
    let deltas: [String: Int]
}

struct GameOver: Decodable, Equatable {
    let winnerId: String
    let standings: [Seat]
}
