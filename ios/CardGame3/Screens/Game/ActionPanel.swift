import SwiftUI

struct ActionPanel: View {
    @Environment(Backend.self) private var backend
    var room: Room
    var round: PublicRound
    @State private var amount = Betting.minBet

    private var me: String { backend.uid ?? "" }
    private var myPoints: Int { room.points(of: me) }

    /// A bot is playing this player's seat (they stepped away, or their turn ran out).
    private var botHasMySeat: Bool { room.isAway(me) && room.isStillIn(me) }

    var body: some View {
        VStack(spacing: 12) {
            if botHasMySeat {
                botPlaying
            } else {
                switch round.phase {
                case .betting: betting
                case .deals: deals
                case .finished: afterRound
                }
            }
        }
        .blocksWhileBusy()
        // The boss feels each new offer arrive.
        .sensoryFeedback(.impact(weight: .medium), trigger: round.offers) { old, new in
            round.bossId == me && new.count > old.count
        }
        .sensoryFeedback(.warning, trigger: botHasMySeat) { _, now in now }
        .animation(.spring(duration: 0.4, bounce: 0.3), value: round)
        .padding(20)
        .background(
            UnevenRoundedRectangle(topLeadingRadius: 32, topTrailingRadius: 32)
                .fill(.white.opacity(0.25))
                .ignoresSafeArea(edges: .bottom)
        )
    }

    // Away

    private var botPlaying: some View {
        VStack(spacing: 12) {
            HStack(spacing: 10) {
                Text("🤖").font(.system(size: 28))
                Text("A bot is playing your seat").font(Theme.body(17, .bold))
            }
            AsyncButton("Take my seat back") { await backend.takeSeatBack() }
                .buttonStyle(PillButtonStyle())
        }
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }

    // After the round

    /// While the showdown plays: Skip (on this phone). Then the next round, which nobody can deal
    /// before everyone's reveal has played.
    private var afterRound: some View {
        let reveal = backend.revealClock
        let revealEnds = round.revealEndsDate ?? .distantPast
        return TimelineView(.periodic(from: .now, by: 0.25)) { context in
            if let reveal, context.date < reveal.verdict {
                Button("Skip") { backend.skipReveal() }
                    .buttonStyle(PillButtonStyle(primary: false))
                    .transition(.opacity)
            } else {
                AsyncButton { await backend.nextRound() } label: { NextRoundLabel(at: round.nextRoundDate) }
                    .buttonStyle(PillButtonStyle())
                    .disabled(context.date < revealEnds)
                    .transition(.opacity)
            }
        }
    }

    // Betting

    /// Smallest bet that beats the highest one: the next step of 500 above it (it may be an odd all-in amount).
    private var minBet: Int { max(Betting.minBet, (round.highestBet / Betting.step + 1) * Betting.step) }

    @ViewBuilder private var betting: some View {
        if round.turnId == me {
            if let deadline = round.turnDeadlineDate {
                Countdown(deadline: deadline, size: 20)
            }
            if minBet <= myPoints {
                AmountPicker(amount: $amount, range: minBet...roundDown(myPoints))
                AsyncButton("Bet \(amount.formatted())") { await backend.bet(amount) }
                    .buttonStyle(PillButtonStyle())
            }
            HStack(spacing: 12) {
                AsyncButton("All in \(myPoints.formatted())") { await backend.bet(myPoints) }
                    .buttonStyle(PillButtonStyle(primary: false))
                AsyncButton("Withdraw") { await backend.withdraw() }
                    .buttonStyle(PillButtonStyle(primary: false))
            }
            .onAppear { amount = minBet }
            .onChange(of: round.highestBet) { amount = minBet }
        } else if round.bets[me] != nil {
            waiting("You're in with \((round.bets[me] ?? 0).formatted()). Waiting for the others…", icon: "hourglass")
        } else if round.withdrawn.contains(me) {
            waiting("You sat this one out.", icon: "moon.zzz.fill")
        } else {
            waiting("Look at your cards… your turn is coming.", icon: "eye.fill")
        }
    }

    // Deals

    @ViewBuilder private var deals: some View {
        if round.bossId == me {
            if round.offers.isEmpty {
                waiting("You're the boss. Wait for offers, or reveal now.", icon: "crown.fill")
            }
            ForEach(round.offers.sorted { $0.key < $1.key }, id: \.key) { uid, offer in
                HStack(spacing: 10) {
                    HStack(spacing: 10) {
                        Blob(color: Theme.color(forSeat: room.seat(of: uid)), size: 36, mood: .surprised)
                        VStack(alignment: .leading, spacing: 0) {
                            Text(room.name(of: uid)).font(Theme.body(15, .bold)).lineLimit(1)
                            Text("asks \(offer.formatted())").font(Theme.body(14, .heavy)).opacity(0.7)
                                .contentTransition(.numericText())
                        }
                    }
                    .playerMenu(uid, in: room)
                    Spacer(minLength: 4)
                    AsyncButton("No") { await backend.answerOffer(from: uid, accept: false) }
                        .buttonStyle(PillButtonStyle(primary: false)).frame(width: 70)
                    AsyncButton("Deal") { await backend.answerOffer(from: uid, accept: true) }
                        .buttonStyle(PillButtonStyle()).frame(width: 84)
                        .disabled(offer > round.dealRoom)
                }
                .padding(.leading, 8)
                .transition(.move(edge: .leading).combined(with: .opacity))
            }
            AsyncButton("Reveal!") { await backend.reveal() }
                .buttonStyle(PillButtonStyle())
        } else if let deal = round.deals[me] {
            waiting("Deal locked: you get \(deal.formatted()) if \(room.name(of: round.bossId ?? "")) wins.", icon: "lock.fill")
        } else if round.bets[me] != nil {
            if round.dealRoom >= Betting.step {
                AmountPicker(amount: $amount, range: Betting.step...roundDown(round.dealRoom))
                AsyncButton(round.offers[me] == nil ? "Offer to withdraw for \(amount.formatted())" : "Change offer to \(amount.formatted())") {
                    await backend.makeOffer(amount)
                }
                .buttonStyle(PillButtonStyle())
                .onAppear { amount = min(max(amount, Betting.step), roundDown(round.dealRoom)) }
            } else {
                waiting("No room left for deals. Get ready to reveal!", icon: "bolt.fill")
            }
        } else {
            waiting("Watching the deals…", icon: "eye.fill")
        }
    }

    private func roundDown(_ n: Int) -> Int { max(Betting.step, n / Betting.step * Betting.step) }

    private func waiting(_ text: String, icon: String) -> some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 22, weight: .bold))
                .symbolEffect(.pulse, options: .repeating)
            Text(text).font(Theme.body(16, .semibold)).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 10)
        .transition(.opacity)
    }
}

/// − amount + in steps of 500.
private struct AmountPicker: View {
    @Binding var amount: Int
    var range: ClosedRange<Int>

    var body: some View {
        HStack {
            stepButton("minus") { amount = max(range.lowerBound, amount - Betting.step) }
                .disabled(amount <= range.lowerBound)
            Text(amount.formatted())
                .font(.system(size: 34, weight: .black, design: .rounded).monospacedDigit())
                .frame(maxWidth: .infinity)
                .contentTransition(.numericText())
            stepButton("plus") { amount = min(range.upperBound, amount + Betting.step) }
                .disabled(amount >= range.upperBound)
        }
        .onAppear { amount = min(max(amount, range.lowerBound), range.upperBound) }
        .animation(.snappy, value: amount)
        .sensoryFeedback(.selection, trigger: amount)
    }

    private func stepButton(_ icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
        }
        .buttonStyle(CircleButtonStyle(size: 56))
        .buttonRepeatBehavior(.enabled)
    }
}

/// "Next round · 6": counts down to the server dealing it; tapping deals it now.
private struct NextRoundLabel: View {
    var at: Date?

    var body: some View {
        if let at {
            TimelineView(.periodic(from: .now, by: 1)) { context in
                let left = max(0, Int(at.timeIntervalSince(context.date).rounded(.up)))
                Text(left > 0 ? "Next round · \(left)" : "Dealing…")
                    .contentTransition(.numericText(countsDown: true))
                    .animation(.snappy, value: left)
            }
        } else {
            Text("Next round")
        }
    }
}
