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
                Text(round.highestBet > 0 ? "Highest bet \(round.highestBet)" : "No bets yet")
                    .font(Theme.body(16, .medium)).opacity(0.7)
            case .deals:
                Text("Deal time").font(Theme.wordmark(36))
                if let deadline = round.deadlineDate {
                    Countdown(deadline: deadline) { Task { await backend.timerRanOut() } }
                }
                Text("\(round.bossId == me ? "You're" : "\(room.name(of: round.bossId ?? "")) is") the boss · \(round.dealRoom.formatted()) left for deals")
                    .font(Theme.body(15, .medium)).opacity(0.7)
                    .multilineTextAlignment(.center)
            case .finished:
                ResultSummary(room: room, round: round, me: me)
            }
        }
        .padding(.horizontal, 20)
    }
}

/// Ticks every second; calls `onZero` once when time runs out.
private struct Countdown: View {
    var deadline: Date
    var onZero: () -> Void
    @State private var fired = false

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let left = max(0, Int(deadline.timeIntervalSince(context.date).rounded(.up)))
            Text(String(format: "%d:%02d", left / 60, left % 60))
                .font(.system(size: 44, weight: .black, design: .rounded).monospacedDigit())
                .padding(.horizontal, 22).padding(.vertical, 6)
                .background(Capsule().fill(left <= 10 ? Theme.hotPink : .white))
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
        let result = round.result!
        VStack(spacing: 10) {
            Text(headline(result)).font(Theme.wordmark(32)).multilineTextAlignment(.center)
            ForEach(result.revealed, id: \.self) { uid in
                HStack(spacing: 4) {
                    Text(uid == me ? "You" : room.name(of: uid))
                        .font(Theme.body(14, .bold)).frame(width: 64, alignment: .leading).lineLimit(1)
                    ForEach(round.revealedHands[uid] ?? [], id: \.self) { card in
                        PlayingCard(rank: card.rank, suit: card.suit, width: 34)
                    }
                    Spacer()
                    delta(result.deltas[uid] ?? 0)
                }
                .padding(8)
                .background(RoundedRectangle(cornerRadius: 16).fill(.white.opacity(uid == result.winnerId ? 0.9 : 0.4)))
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
    }

    private func headline(_ result: RoundResult) -> String {
        switch result.outcome {
        case .redeal: return "Everyone folded.\nRedeal!"
        default:
            let who = result.winnerId == me ? "You win" : "\(room.name(of: result.winnerId ?? "")) wins"
            return "\(who) \((result.deltas[result.winnerId ?? ""] ?? 0).formatted())!"
        }
    }

    private func delta(_ amount: Int) -> some View {
        Text(amount > 0 ? "+\(amount)" : "\(amount)")
            .font(Theme.body(15, .heavy))
            .foregroundStyle(amount >= 0 ? Theme.ink : Theme.hotPink)
    }
}
