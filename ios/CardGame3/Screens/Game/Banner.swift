import SwiftUI

/// Big headline for what is happening right now.
struct Banner: View {
    @Environment(Backend.self) private var backend
    var room: Room
    var round: PublicRound
    var me: String?

    var body: some View {
        VStack(spacing: 8) {
            switch round.phase {
            case .betting:
                Text(round.turnId == me ? "Your turn" : "\(room.name(of: round.turnId ?? ""))'s turn")
                    .font(Theme.wordmark(36))
                    .lineLimit(1).minimumScaleFactor(0.5)
                    .id(round.turnId)
                    .transition(.push(from: .trailing).combined(with: .opacity))
                Text(round.highestBet > 0 ? "Highest bet \(round.highestBet.formatted())" : "No bets yet")
                    .font(Theme.body(16, .medium)).opacity(0.7)
                    .contentTransition(.numericText())
            case .deals:
                Text("Deal time").font(Theme.wordmark(36))
                if let deadline = round.deadlineDate {
                    Countdown(deadline: deadline) { Task { await backend.timerRanOut() } }
                }
                Text("\(round.bossId == me ? "You're" : "\(room.name(of: round.bossId ?? "")) is") the boss · \(round.dealRoom.formatted()) left for deals")
                    .font(Theme.body(15, .medium)).opacity(0.7)
                    .multilineTextAlignment(.center)
                    .contentTransition(.numericText())
            case .finished:
                ResultSummary(room: room, round: round, me: me)
            }
        }
        .padding(.horizontal, 20)
        .id(round.phase)
        .transition(.scale(scale: 0.85).combined(with: .opacity))
        .animation(.spring(duration: 0.45, bounce: 0.35), value: round)
    }
}

/// "1:32" in a pill that ticks every second and turns pink near the end; calls `onZero` once when time runs out.
struct Countdown: View {
    var deadline: Date
    var size: CGFloat = 44
    var onZero: () -> Void = {}
    @State private var fired = false

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let left = max(0, Int(deadline.timeIntervalSince(context.date).rounded(.up)))
            let hurry = left <= 10
            Text(String(format: "%d:%02d", left / 60, left % 60))
                .font(.system(size: size, weight: .black, design: .rounded).monospacedDigit())
                .contentTransition(.numericText(countsDown: true))
                .padding(.horizontal, size / 2).padding(.vertical, size / 7)
                .background(Capsule().fill(hurry ? Theme.hotPink : .white))
                .foregroundStyle(hurry ? .white : Theme.ink)
                // A little heartbeat each second near the end.
                .keyframeAnimator(initialValue: 1.0, trigger: hurry ? left : -1) { view, scale in
                    view.scaleEffect(scale)
                } keyframes: { _ in
                    SpringKeyframe(1.12, duration: 0.12)
                    SpringKeyframe(1.0, duration: 0.3)
                }
                .animation(.snappy, value: left)
                .sensoryFeedback(.impact(weight: .light), trigger: left) { _, left in left <= 5 && left > 0 }
                .onChange(of: left == 0, initial: true) { _, isZero in
                    if isZero && !fired {
                        fired = true
                        onZero()
                    }
                }
        }
    }
}

private struct ResultSummary: View {
    var room: Room
    var round: PublicRound
    var me: String?

    var body: some View {
        let result = round.result ?? RoundResult(outcome: .redeal, winnerId: nil, revealed: [], deltas: [:])
        VStack(spacing: 10) {
            Text(headline(result)).font(Theme.wordmark(32)).multilineTextAlignment(.center)
            if let paid = dealsPaid(result) {
                Text("Paid \(paid.formatted()) in deals").font(Theme.body(16, .semibold)).opacity(0.75)
            }
            ForEach(Array(result.revealed.enumerated()), id: \.element) { row, uid in
                HStack(spacing: 4) {
                    VStack(alignment: .leading, spacing: 1) {
                        Text(uid == me ? "You" : room.name(of: uid))
                            .font(Theme.body(14, .bold))
                        if let hand = HandValue(round.revealedHands[uid] ?? []) {
                            Text(hand.name).font(Theme.body(11, .semibold)).opacity(0.7)
                        }
                    }
                    .lineLimit(1).minimumScaleFactor(0.6)
                    .frame(width: 76, alignment: .leading)
                    ForEach(Array((round.revealedHands[uid] ?? []).enumerated()), id: \.element) { i, card in
                        PlayingCard(rank: card.rank, suit: card.suit, width: 34)
                            .flipIn(delay: Double(row) * 0.25 + Double(i) * 0.06)
                    }
                    Spacer()
                    delta(result.deltas[uid] ?? 0)
                }
                .padding(8)
                .background(RoundedRectangle(cornerRadius: 16).fill(.white.opacity(uid == result.winnerId ? 0.9 : 0.4)))
                .overlay(alignment: .topLeading) {
                    if uid == result.winnerId {
                        Text("👑").font(.system(size: 22)).rotationEffect(.degrees(-20)).offset(x: -6, y: -12)
                    }
                }
                .scaleEffect(uid == result.winnerId ? 1.03 : 1)
            }
            let others = result.deltas.filter { !result.revealed.contains($0.key) }
            ForEach(others.sorted { $0.key < $1.key }, id: \.key) { uid, amount in
                HStack {
                    Text(uid == me ? "You" : room.name(of: uid)).font(Theme.body(14, .bold))
                    Spacer()
                    delta(amount)
                }
                .padding(.horizontal, 12)
            }
        }
        // Keeps rows readable on iPad and leaves room for the winner's crown and lift.
        .frame(maxWidth: 560)
        .padding(.horizontal, 6)
    }

    private func headline(_ result: RoundResult) -> String {
        switch result.outcome {
        case .redeal: return "Everyone folded.\nRedeal!"
        default:
            let who = result.winnerId == me ? "You win" : "\(room.name(of: result.winnerId ?? "")) wins"
            return "\(who) \(winAmount(result).formatted())!"
        }
    }

    /// What the winner won before paying deals: the boss's bet (or the lone entrant's bet).
    private func winAmount(_ result: RoundResult) -> Int {
        guard let winner = result.winnerId else { return 0 }
        if let boss = round.bossId { return round.bets[boss] ?? 0 }
        return round.bets[winner] ?? result.deltas[winner] ?? 0
    }

    /// Deals the winning boss paid out, if any.
    private func dealsPaid(_ result: RoundResult) -> Int? {
        guard result.winnerId != nil, result.winnerId == round.bossId else { return nil }
        let total = round.deals.values.reduce(0, +)
        return total > 0 ? total : nil
    }

    private func delta(_ amount: Int) -> some View {
        RollingDelta(amount: amount)
    }
}

/// "+1500" / "-500", counting up from zero when it appears.
private struct RollingDelta: View {
    var amount: Int
    @State private var shown = 0

    var body: some View {
        Text(shown > 0 ? "+\(shown.formatted())" : shown.formatted())
            .font(Theme.body(14, .heavy).monospacedDigit())
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(Capsule().fill(amount > 0 ? Theme.ink : .white))
            .foregroundStyle(amount > 0 ? .white : amount < 0 ? Theme.hotPink : Theme.ink)
            .contentTransition(.numericText(value: Double(shown)))
            .onAppear { withAnimation(.smooth(duration: 0.8).delay(0.5)) { shown = amount } }
            .onChange(of: amount) { withAnimation(.smooth) { shown = amount } }
    }
}

extension View {
    /// Flips a card face up after `delay` when it first appears.
    func flipIn(delay: Double) -> some View { modifier(FlipIn(delay: delay)) }
}

private struct FlipIn: ViewModifier {
    var delay: Double
    @State private var up = false

    func body(content: Content) -> some View {
        content
            .rotation3DEffect(.degrees(up ? 0 : 90), axis: (x: 0, y: 1, z: 0), perspective: 0.5)
            .opacity(up ? 1 : 0)
            .onAppear { withAnimation(.spring(duration: 0.4, bounce: 0.3).delay(delay)) { up = true } }
    }
}
