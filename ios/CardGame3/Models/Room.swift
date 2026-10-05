import Foundation
import Observation

/// Names this player hid on this phone (they show as "Player 3" everywhere). Kept across launches.
@Observable
final class HiddenNames {
    static let shared = HiddenNames()
    private(set) var ids = Set(UserDefaults.standard.stringArray(forKey: "hiddenNames") ?? [])

    func contains(_ uid: String) -> Bool { ids.contains(uid) }

    func set(_ uid: String, hidden: Bool) {
        if hidden { ids.insert(uid) } else { ids.remove(uid) }
        UserDefaults.standard.set(Array(ids), forKey: "hiddenNames")
    }
}

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
    /// Players who stepped away; a bot plays for them.
    let away: [String]?
    /// AI players added by the host; always played by a bot.
    let aiPlayers: [String]?
    /// How many of their cards each player has looked at this round (a tell everyone can see).
    let peeks: [String: Int]?
    /// After the game: the new lobby someone opened to play again, and who opened it.
    let rematchCode: String?
    let rematchBy: String?

    /// A player's name, or "Player 3" if it is hidden on this phone (see `HiddenNames`).
    func name(of uid: String) -> String {
        if HiddenNames.shared.contains(uid) { return "Player \(seat(of: uid) + 1)" }
        return players.first { $0.uid == uid }?.name ?? "?"
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

    func isAI(_ uid: String) -> Bool {
        aiPlayers?.contains(uid) ?? false
    }

    func isAway(_ uid: String) -> Bool {
        away?.contains(uid) ?? false
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

extension Seat: Identifiable {}
