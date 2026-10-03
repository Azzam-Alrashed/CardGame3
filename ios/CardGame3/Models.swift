import Foundation

/// Mirrors the public room document written by Cloud Functions (`rooms/{code}`).
/// Extra fields (table, round, …) are ignored until the game screens need them.
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
    let players: [Player]
}

/// Room sizes enforced by the backend.
enum TableSize {
    static let minPlayers = 4
    static let maxPlayers = 13
}
