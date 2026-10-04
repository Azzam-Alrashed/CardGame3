import Foundation

/// Rules shared with the backend.
enum TableSize {
    static let minPlayers = 4
    static let maxPlayers = 13
}

enum Betting {
    static let step = 500
    static let minBet = 500
    /// Length of a betting turn before a bot takes the seat.
    static let turnSeconds = 45.0
}
