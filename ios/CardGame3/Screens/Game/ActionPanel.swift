import SwiftUI

struct ActionPanel: View {
    @Environment(Backend.self) private var backend
    var room: Room
    var round: PublicRound
    @State private var amount = Betting.minBet

    private var me: String { backend.uid ?? "" }
    private var myPoints: Int { room.points(of: me) }

    var body: some View {
        VStack(spacing: 12) {
            switch round.phase {
            case .betting: betting
            case .deals: deals
            case .finished:
                AsyncButton("Next round") { await backend.nextRound() }
                    .buttonStyle(PillButtonStyle())
            }
        }
        .blocksWhileBusy()
        // The boss feels each new offer arrive.
        .sensoryFeedback(.impact(weight: .medium), trigger: round.offers) { old, new in
            round.bossId == me && new.count > old.count
        }
        .animation(.spring(duration: 0.4, bounce: 0.3), value: round)
        .padding(20)
        .background(
            UnevenRoundedRectangle(topLeadingRadius: 32, topTrailingRadius: 32)
                .fill(.white.opacity(0.25))
                .ignoresSafeArea(edges: .bottom)
        )
    }

    // Betting

    /// Smallest bet that beats the highest one.
    /// Next step of 500 above the highest bet (which may be an odd all-in amount).
    private var minBet: Int { max(Betting.minBet, (round.highestBet / Betting.step + 1) * Betting.step) }

    @ViewBuilder private var betting: some View {
        if round.turnId == me {
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
                    Blob(color: Theme.color(forSeat: room.seat(of: uid)), size: 36, mood: .surprised)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(room.name(of: uid)).font(Theme.body(15, .bold)).lineLimit(1)
                        Text("asks \(offer.formatted())").font(Theme.body(14, .heavy)).opacity(0.7)
                            .contentTransition(.numericText())
                    }
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
