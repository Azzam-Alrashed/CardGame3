import SwiftUI

/// The table: everyone's status on top, the round's state in the middle, your cards and actions below.
struct GameView: View {
    @Environment(Backend.self) private var backend

    var body: some View {
        let room = backend.room!
        if let over = room.gameOver {
            GameOverView(room: room, over: over)
        } else if let round = room.round {
            VStack(spacing: 0) {
                header(room, round)
                PlayersStrip(room: room, round: round, me: backend.uid)
                Spacer(minLength: 8)
                Banner(room: room, round: round, me: backend.uid)
                Spacer(minLength: 8)
                MyHand(cards: backend.myCards, revealed: round.revealedHands[backend.uid ?? ""] != nil)
                ActionPanel(room: room, round: round)
            }
        }
    }

    private func header(_ room: Room, _ round: PublicRound) -> some View {
        HStack {
            Text("Round \(round.roundNumber)").font(Theme.body(17, .heavy))
            Spacer()
            if let uid = backend.uid {
                Label("\(room.points(of: uid))", systemImage: "circle.hexagongrid.fill")
                    .font(Theme.body(17, .heavy))
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .background(Capsule().fill(.white))
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
    }
}
