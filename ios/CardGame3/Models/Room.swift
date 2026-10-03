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

extension Seat: Identifiable {}
