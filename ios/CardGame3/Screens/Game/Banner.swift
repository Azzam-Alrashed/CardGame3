import SwiftUI

/// Big headline for what is happening right now.
struct Banner: View {
    @Environment(Backend.self) private var backend
    var room: Room
    var round: PublicRound
    var me: String?
    /// The showdown this phone is staging, if any.
    var reveal: RevealClock?

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
                ResultSummary(room: room, round: round, me: me, reveal: reveal)
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
                .soundFeedback(.tick, trigger: left) { _, left in left <= 5 && left > 0 }
                .onChange(of: left == 0, initial: true) { _, isZero in
                    if isZero && !fired {
                        fired = true
                        onZero()
                    }
                }
        }
    }
}

/// The round's result. A showdown is staged: everyone's cards start face down, the challengers turn
/// theirs over one at a time, then the boss, and only then come the crown, the points and the headline.
/// Tapping skips to the verdict on this phone.
private struct ResultSummary: View {
    @Environment(Backend.self) private var backend
    var room: Room
    var round: PublicRound
    var me: String?
    var reveal: RevealClock?

    var body: some View {
        if let reveal {
            TimelineView(FramesUntil(end: reveal.end, fps: 30)) { context in
                summary(at: reveal.elapsed(at: context.date))
            }
            .contentShape(Rectangle())
            .onTapGesture { backend.skipReveal() }
        } else {
            summary(at: .infinity)
        }
    }

    private var result: RoundResult {
        round.result ?? RoundResult(outcome: .redeal, winnerId: nil, revealed: [], deltas: [:])
    }

    /// The showdown's order and timing; also used (fully played) when this phone didn't stage it.
    private var timeline: RevealTimeline? {
        reveal?.timeline ?? RevealTimeline(round: round, seatOrder: room.players.map(\.uid))
    }

    private func summary(at t: TimeInterval) -> some View {
        let verdict = t >= (timeline?.verdictAt ?? 0)
        let leader = bestSoFar(at: t)
        return VStack(spacing: 10) {
            if verdict {
                Text(headline).font(Theme.wordmark(32)).multilineTextAlignment(.center)
                    .transition(.scale(scale: 0.6).combined(with: .opacity))
                if let callout {
                    Text(callout).font(Theme.body(15, .heavy))
                        .padding(.horizontal, 12).padding(.vertical, 4)
                        .background(Capsule().fill(Theme.hotPink))
                        .foregroundStyle(.white)
                        .transition(.scale(scale: 0.4).combined(with: .opacity))
                }
                if let paid = dealsPaid {
                    Text("Paid \(paid.formatted()) in deals").font(Theme.body(16, .semibold)).opacity(0.75)
                }
            } else {
                let bossTurn = t >= (timeline?.drumrollAt ?? 0)
                Text(bossTurn ? "\(round.bossId == me ? "You reveal" : "\(room.name(of: round.bossId ?? "")) reveals")…" : "Showdown!")
                    .font(Theme.wordmark(32)).multilineTextAlignment(.center)
                    .id(bossTurn)
                    .transition(.push(from: .bottom).combined(with: .opacity))
            }
            ForEach(timeline?.order ?? result.revealed, id: \.self) { uid in
                row(uid, at: t, verdict: verdict, leading: uid == leader)
            }
            if verdict {
                let others = result.deltas.filter { !result.revealed.contains($0.key) }
                ForEach(others.sorted { $0.key < $1.key }, id: \.key) { uid, amount in
                    HStack {
                        Text(uid == me ? "You" : room.name(of: uid)).font(Theme.body(14, .bold))
                        Spacer()
                        RollingDelta(amount: amount)
                    }
                    .padding(.horizontal, 12)
                }
            }
        }
        // Keeps rows readable on iPad and leaves room for the winner's crown and lift.
        .frame(maxWidth: 560)
        .padding(.horizontal, 6)
        .animation(.spring(duration: 0.5, bounce: 0.4), value: verdict)
        .animation(.spring(duration: 0.35, bounce: 0.4), value: leader)
    }

    private func row(_ uid: String, at t: TimeInterval, verdict: Bool, leading: Bool) -> some View {
        let cards = round.revealedHands[uid] ?? []
        let shown = t >= (timeline?.shownTime(uid) ?? 0)
        let won = verdict && uid == result.winnerId
        let lost = verdict && uid != result.winnerId
        return HStack(spacing: 4) {
            VStack(alignment: .leading, spacing: 1) {
                Text(uid == me ? "You" : room.name(of: uid)).font(Theme.body(14, .bold))
                Text(shown ? HandValue(cards)?.name ?? "" : uid == round.bossId ? "Boss" : " ")
                    .font(Theme.body(11, .semibold)).opacity(0.7)
                    .contentTransition(.opacity)
            }
            .lineLimit(1).minimumScaleFactor(0.6)
            .frame(width: 76, alignment: .leading)
            ForEach(Array(cards.enumerated()), id: \.element) { i, card in
                let up = t >= (timeline?.flipTime(uid, card: i) ?? 0)
                FlipCard(card: card, faceUp: up, width: 34)
                    .animation(.spring(duration: 0.35, bounce: 0.3), value: up)
            }
            Spacer(minLength: 4)
            if verdict {
                RollingDelta(amount: result.deltas[uid] ?? 0).transition(.scale.combined(with: .opacity))
            } else if leading {
                Text("Best so far").font(Theme.body(11, .heavy))
                    .padding(.horizontal, 8).padding(.vertical, 3)
                    .background(Capsule().fill(Theme.ink)).foregroundStyle(.white)
                    .transition(.scale.combined(with: .opacity))
            }
        }
        .padding(8)
        .background(RoundedRectangle(cornerRadius: 16).fill(.white.opacity(won ? 0.9 : 0.4)))
        .overlay(alignment: .topLeading) {
            if won {
                Text("👑").font(.system(size: 22)).rotationEffect(.degrees(-20)).offset(x: -6, y: -12)
                    .transition(.offset(y: -60).combined(with: .opacity))
            }
        }
        .scaleEffect(won ? 1.03 : 1)
        .opacity(lost ? 0.6 : 1)
        .offset(y: lost ? 3 : 0)
    }

    /// The strongest challenger shown so far, while there's more than one to compare.
    private func bestSoFar(at t: TimeInterval) -> String? {
        guard let timeline, t < timeline.verdictAt else { return nil }
        let hands = timeline.challengers
            .filter { t >= timeline.shownTime($0) }
            .compactMap { uid in HandValue(round.revealedHands[uid] ?? []).map { (uid, $0) } }
        guard hands.count > 1 else { return nil }
        return hands.max { $1.1.beats($0.1) }?.0
    }

    private var headline: String {
        switch result.outcome {
        case .redeal: return "Everyone folded.\nRedeal!"
        default:
            let who = result.winnerId == me ? "You win" : "\(room.name(of: result.winnerId ?? "")) wins"
            return "\(who) \(winAmount.formatted())!"
        }
    }

    /// The moments people argue about: "The boss falls!", "Won on the suit! ♠ beats ♥".
    private var callout: String? {
        guard result.outcome == .showdown, let winner = result.winnerId,
              let best = HandValue(round.revealedHands[winner] ?? []) else { return nil }
        let rivals = result.revealed.filter { $0 != winner }.compactMap { HandValue(round.revealedHands[$0] ?? []) }
        guard let runnerUp = rivals.max(by: { $1.beats($0) }) else { return nil }
        let close: String? = switch best.decider(against: runnerUp) {
        case .kicker: "Won on the kicker!"
        case .suit:
            "Won on the suit! \(HandValue.suitSymbol[best.topSuit] ?? "") beats \(HandValue.suitSymbol[runnerUp.topSuit] ?? "")"
        default: nil
        }
        let parts = [winner != round.bossId ? "The boss falls!" : nil, close].compactMap { $0 }
        return parts.isEmpty ? nil : parts.joined(separator: " ")
    }

    /// What the winner won before paying deals: the boss's bet (or the lone entrant's bet).
    private var winAmount: Int {
        guard let winner = result.winnerId else { return 0 }
        if let boss = round.bossId { return round.bets[boss] ?? 0 }
        return round.bets[winner] ?? result.deltas[winner] ?? 0
    }

    /// Deals the winning boss paid out, if any.
    private var dealsPaid: Int? {
        guard result.winnerId != nil, result.winnerId == round.bossId else { return nil }
        let total = round.deals.values.reduce(0, +)
        return total > 0 ? total : nil
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
