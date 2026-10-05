import SwiftUI

/// The table: everyone's status on top, the round's state in the middle, your cards and actions below.
struct GameView: View {
    @Environment(Backend.self) private var backend
    @State private var confirmLeave = false
    @State private var showRules = false
    @State private var dealSpots = DealSpots()

    var body: some View {
        // The room can disappear (leaving, "Back home") a frame before the screen switches.
        if let room = backend.room, let over = room.gameOver {
            GameOverView(room: room, over: over)
        } else if let room = backend.room, let round = room.round {
            let me = backend.uid ?? ""
            let clock = backend.dealClock
            AdaptiveSplit {
                VStack(spacing: 0) {
                    header(room, round)
                    PlayersStrip(room: room, round: round, me: backend.uid, clock: clock, myFlips: backend.flipped.count)
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
                    MyHand(
                        cards: backend.myCards,
                        inRound: room.isStillIn(me),
                        ready: backend.myCardsRound.map { $0 == round.roundNumber } ?? !backend.myCards.isEmpty,
                        // A bot holding my seat looks at everything; after the round there's nothing to hide.
                        allFaceUp: round.phase == .finished || room.isAway(me),
                        clock: clock
                    )
                    ActionPanel(room: room, round: round)
                }
            }
            .environment(\.dealSpots, dealSpots)
            .overlay {
                if let clock { DealLayer(clock: clock, me: backend.uid, spots: dealSpots) }
            }
            .overlay {
                if round.phase == .finished, round.result?.winnerId == backend.uid {
                    Confetti().ignoresSafeArea().id(round.roundNumber)
                }
            }
            .sensoryFeedback(.impact(weight: .heavy), trigger: round.turnId) { (_: String?, turn: String?) in turn != nil && turn == backend.uid }
            .soundFeedback(.turn, trigger: round.turnId) { (_: String?, turn: String?) in turn != nil && turn == backend.uid }
            .onChange(of: round) { old, new in playTableSounds(old, new, room: room) }
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
            SoundToggle(size: 40)
            Button { showRules = true } label: { Image(systemName: "questionmark") }
                .buttonStyle(CircleButtonStyle(size: 40))
                .accessibilityLabel("How to play")
                .sheet(isPresented: $showRules) { HowToPlayView() }
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

    /// What everyone at the table hears when the round changes: chips, folds, offers, the boss, the result.
    private func playTableSounds(_ old: PublicRound, _ new: PublicRound, room: Room) {
        let sound = SoundPlayer.shared
        // A new deal sounds from DealLayer.
        guard old.roundNumber == new.roundNumber else { return }
        for (uid, amount) in new.bets where old.bets[uid] != amount {
            let points = room.table?.seats.first { $0.id == uid }?.points ?? 0
            sound.play(amount >= points ? .allIn : amount >= 2_000 ? .chipsBig : amount >= 1_000 ? .chips : .chip)
        }
        if new.withdrawn.count > old.withdrawn.count { sound.play(.fold) }
        if new.offers.contains(where: { old.offers[$0.key] != $0.value }) { sound.play(.offer) }
        if new.deals.count > old.deals.count {
            sound.play(.accept)
        } else if new.phase == .deals, old.offers.keys.contains(where: { new.offers[$0] == nil }) {
            sound.play(.reject)
        }
        if old.phase == .betting, new.phase == .deals { sound.play(.boss) }
        if old.phase != .finished, new.phase == .finished, let result = new.result {
            let me = backend.uid ?? ""
            switch result.outcome {
            case .redeal: sound.play(.fold)
            default: sound.play(result.winnerId == me ? .win : (result.deltas[me] ?? 0) < 0 ? .lose : .verdict)
            }
        }
    }
}
