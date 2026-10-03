import Foundation

/// Mirrors the public room document written by Cloud Functions (`rooms/{code}`).
struct Room: Decodable, Equatable {
    enum Status: String, Decodable {
        case lobby, playing, finished
    }

    struct Player: Decodable, Equatable, Identifiable {
        let uid: String
        let name: String
        var id: String { uid }
    }

    let code: String
    let hostId: String
    let status: Status
    /// Everyone who joined, in seat order (knocked-out players stay listed).
    let players: [Player]
    let table: Table?
    let round: PublicRound?
    let gameOver: GameOver?

    func name(of uid: String) -> String {
        players.first { $0.uid == uid }?.name ?? "?"
    }

    /// Seat index in the joined order, used for each player's blob color.
    func seat(of uid: String) -> Int {
        players.firstIndex { $0.uid == uid } ?? 0
    }

    /// Points including the result of a just-finished round (the table updates at the next deal).
    func points(of uid: String) -> Int {
        let base = table?.seats.first { $0.id == uid }?.points ?? 0
        return base + (round?.result?.deltas[uid] ?? 0)
    }

    func isStillIn(_ uid: String) -> Bool {
        table?.seats.contains { $0.id == uid } ?? false
    }
}

struct Seat: Decodable, Equatable {
    let id: String
    let points: Int
}

/// Players still in the game, plus whose deal it is.
struct Table: Decodable, Equatable {
    let seats: [Seat]
    let dealerIndex: Int
    let roundNumber: Int
}

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
    let result: RoundResult?
    let revealedHands: [String: [Card]]

    var highestBet: Int { bets.values.max() ?? 0 }

    /// What is left of the boss's bet for new deals.
    var dealRoom: Int {
        guard let bossId, let bossBet = bets[bossId] else { return 0 }
        return bossBet - deals.values.reduce(0, +)
    }

    var deadlineDate: Date? { deadline.map { Date(timeIntervalSince1970: $0 / 1000) } }
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

/// Rules shared with the backend.
enum TableSize {
    static let minPlayers = 4
    static let maxPlayers = 13
}

enum Betting {
    static let step = 500
    static let minBet = 500
}
