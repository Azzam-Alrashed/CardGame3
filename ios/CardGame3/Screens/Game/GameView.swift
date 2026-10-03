import SwiftUI

/// The table: everyone's status on top, the round's state in the middle, your cards and actions below.
struct GameView: View {
    @Environment(Backend.self) private var backend
    @State private var confirmLeave = false

    var body: some View {
        // The room can disappear (leaving, "Back home") a frame before the screen switches.
        if let room = backend.room, let over = room.gameOver {
            GameOverView(room: room, over: over)
        } else if let room = backend.room, let round = room.round {
            AdaptiveSplit {
                VStack(spacing: 0) {
                    header(room, round)
                    PlayersStrip(room: room, round: round, me: backend.uid)
                    Spacer(minLength: 8)
                    // Results can be tall; scroll them when the screen is short (landscape).
                    ViewThatFits(in: .vertical) {
                        Banner(room: room, round: round, me: backend.uid)
                        ScrollView { Banner(room: room, round: round, me: backend.uid) }
                    }
                    Spacer(minLength: 8)
                }
            } side: {
                VStack(spacing: 0) {
                    Spacer(minLength: 0)
                    MyHand(cards: backend.myCards, revealed: round.revealedHands[backend.uid ?? ""] != nil)
                    ActionPanel(room: room, round: round)
                }
            }
            .overlay {
                if round.phase == .finished, round.result?.winnerId == backend.uid {
                    Confetti().ignoresSafeArea().id(round.roundNumber)
                }
            }
            .sensoryFeedback(.impact(weight: .heavy), trigger: round.turnId) { (_: String?, turn: String?) in turn != nil && turn == backend.uid }
            .sensoryFeedback(trigger: round.phase) { _, phase in
                guard phase == .finished, let winner = round.result?.winnerId else { return nil }
                return winner == backend.uid ? .success : .impact(weight: .medium)
            }
        }
    }

    private func header(_ room: Room, _ round: PublicRound) -> some View {
        HStack {
            Button {
                confirmLeave = true
            } label: {
                Image(systemName: "door.left.hand.open")
            }
            .buttonStyle(CircleButtonStyle(size: 40))
            .accessibilityLabel("Leave the table")
            .confirmationDialog("Leave the table?", isPresented: $confirmLeave, titleVisibility: .visible) {
                Button("Leave · a bot plays for me") { Task { await backend.leaveGame() } }
            } message: {
                Text("A bot plays your seat with your points until you come back.")
            }
            Text("Round \(round.roundNumber)").font(Theme.body(17, .heavy))
                .contentTransition(.numericText())
                .animation(.snappy, value: round.roundNumber)
            Spacer()
            if let uid = backend.uid {
                Label(room.points(of: uid).formatted(), systemImage: "circle.hexagongrid.fill")
                    .font(Theme.body(17, .heavy).monospacedDigit())
                    .contentTransition(.numericText(value: Double(room.points(of: uid))))
                    .animation(.smooth(duration: 0.8), value: room.points(of: uid))
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .background(Capsule().fill(.white))
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
    }
}
