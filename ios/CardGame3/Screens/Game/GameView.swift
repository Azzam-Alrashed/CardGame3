import SwiftUI
import UIKit

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
            let reveal = backend.revealClock
            AdaptiveSplit {
                VStack(spacing: 0) {
                    header(room, round, reveal: reveal)
                    PlayersStrip(room: room, round: round, me: backend.uid, clock: clock, myFlips: backend.flipped.count, reveal: reveal)
                    Spacer(minLength: 8)
                    // Results can be tall; scroll them when the screen is short (landscape).
                    ViewThatFits(in: .vertical) {
                        Banner(room: room, round: round, me: backend.uid, reveal: reveal)
                        ScrollView { Banner(room: room, round: round, me: backend.uid, reveal: reveal) }
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
                // After the showdown has played out.
                if round.phase == .finished, round.result?.winnerId == backend.uid {
                    TimelineView(FramesUntil(end: reveal?.verdict ?? .distantPast, fps: 10)) { context in
                        if context.date >= reveal?.verdict ?? .distantPast { Confetti().ignoresSafeArea() }
                    }
                    .id(round.roundNumber)
                }
            }
            .task(id: reveal?.start) {
                if let reveal { await playRevealSounds(reveal) }
            }
            .sensoryFeedback(.impact(weight: .heavy), trigger: round.turnId) { (_: String?, turn: String?) in turn != nil && turn == backend.uid }
            .soundFeedback(.turn, trigger: round.turnId) { (_: String?, turn: String?) in turn != nil && turn == backend.uid }
            .onChange(of: round) { old, new in playTableSounds(old, new, room: room) }
            .sensoryFeedback(trigger: round.phase) { _, phase in
                // A staged showdown buzzes at its verdict instead (playRevealSounds).
                guard phase == .finished, backend.revealClock == nil, let winner = round.result?.winnerId else { return nil }
                return winner == backend.uid ? .success : .impact(weight: .medium)
            }
        }
    }

    private func header(_ room: Room, _ round: PublicRound, reveal: RevealClock?) -> some View {
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
                // Points move at the showdown's verdict, not before: no spoilers.
                TimelineView(FramesUntil(end: reveal?.verdict ?? .distantPast, fps: 4)) { context in
                    let points = room.points(of: uid, settled: context.date >= reveal?.verdict ?? .distantPast)
                    Label(points.formatted(), systemImage: "circle.hexagongrid.fill")
                        .font(Theme.body(17, .heavy).monospacedDigit())
                        .contentTransition(.numericText(value: Double(points)))
                        .animation(.smooth(duration: 0.8), value: points)
                        .padding(.horizontal, 14).padding(.vertical, 8)
                        .background(Capsule().fill(.white))
                }
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
        // A staged showdown plays its own sounds (playRevealSounds).
        if old.phase != .finished, new.phase == .finished, let result = new.result, backend.revealClock == nil {
            sound.play(verdictSound(result))
        }
    }

    private func verdictSound(_ result: RoundResult) -> Sound {
        let me = backend.uid ?? ""
        if result.outcome == .redeal { return .fold }
        return result.winnerId == me ? .win : (result.deltas[me] ?? 0) < 0 ? .lose : .verdict
    }

    /// The showdown's sounds, in time with the cards: a snap for each one turned over, a darbuka roll
    /// before the boss, a deep doum on the boss's last card, then the verdict (with a buzz).
    private func playRevealSounds(_ reveal: RevealClock) async {
        let sound = SoundPlayer.shared
        // Restarted by a skip: cut the drumroll short.
        sound.stop(.roll)
        let timeline = reveal.timeline
        var events: [(at: TimeInterval, play: () -> Void)] = [(timeline.drumrollAt, { sound.play(.roll) })]
        for uid in timeline.order {
            for card in 0..<DealTimeline.handSize {
                let last = uid == timeline.boss && card == DealTimeline.handSize - 1
                events.append((timeline.flipTime(uid, card: card), {
                    sound.play(.flip, volume: last ? 1 : 0.7)
                    if last { sound.play(.doum) }
                }))
            }
        }
        events.append((timeline.verdictAt, { [backend] in
            sound.stop(.roll)
            guard let result = backend.room?.round?.result else { return }
            sound.play(verdictSound(result))
            let won = result.winnerId == backend.uid
            UIImpactFeedbackGenerator(style: won ? .heavy : .medium).impactOccurred()
        }))
        for event in events.sorted(by: { $0.at < $1.at }) {
            let wait = event.at - reveal.elapsed(at: .now)
            if wait < -0.1 { continue }
            if wait > 0 { try? await Task.sleep(for: .seconds(wait)) }
            if Task.isCancelled { return }
            event.play()
        }
    }
}
