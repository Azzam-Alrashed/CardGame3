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
                Button("Next round") { Task { await backend.nextRound() } }
                    .buttonStyle(PillButtonStyle())
            }
        }
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
                Button("Bet \(amount)") { Task { await backend.bet(amount) } }
                    .buttonStyle(PillButtonStyle())
            }
            HStack(spacing: 12) {
                Button("All in \(myPoints)") { Task { await backend.bet(myPoints) } }
                    .buttonStyle(PillButtonStyle(primary: false))
                Button("Withdraw") { Task { await backend.withdraw() } }
                    .buttonStyle(PillButtonStyle(primary: false))
            }
            .onAppear { amount = minBet }
            .onChange(of: round.highestBet) { amount = minBet }
        } else if round.bets[me] != nil {
            waiting("You're in with \(round.bets[me] ?? 0). Waiting for the others…")
        } else if round.withdrawn.contains(me) {
            waiting("You sat this one out.")
        } else {
            waiting("Look at your cards… your turn is coming.")
        }
    }

    // Deals

    @ViewBuilder private var deals: some View {
        if round.bossId == me {
            if round.offers.isEmpty {
                waiting("You're the boss. Wait for offers, or reveal now.")
            }
            ForEach(round.offers.sorted { $0.key < $1.key }, id: \.key) { uid, offer in
                HStack {
                    Text("\(room.name(of: uid)) asks \(offer)").font(Theme.body(16, .bold))
                    Spacer()
                    Button("No") { Task { await backend.answerOffer(from: uid, accept: false) } }
                        .buttonStyle(PillButtonStyle(primary: false)).frame(width: 70)
                    Button("Deal") { Task { await backend.answerOffer(from: uid, accept: true) } }
                        .buttonStyle(PillButtonStyle()).frame(width: 84)
                        .disabled(offer > round.dealRoom)
                }
            }
            Button("Reveal!") { Task { await backend.reveal() } }
                .buttonStyle(PillButtonStyle())
        } else if let deal = round.deals[me] {
            waiting("Deal locked: you get \(deal) if \(room.name(of: round.bossId ?? "")) wins.")
        } else if round.bets[me] != nil {
            if round.dealRoom >= Betting.step {
                AmountPicker(amount: $amount, range: Betting.step...roundDown(round.dealRoom))
                Button(round.offers[me] == nil ? "Offer to withdraw for \(amount)" : "Change offer to \(amount)") {
                    Task { await backend.makeOffer(amount) }
                }
                .buttonStyle(PillButtonStyle())
                .onAppear { amount = min(max(amount, Betting.step), roundDown(round.dealRoom)) }
            } else {
                waiting("No room left for deals. Get ready to reveal!")
            }
        } else {
            waiting("Watching the deals…")
        }
    }

    private func roundDown(_ n: Int) -> Int { max(Betting.step, n / Betting.step * Betting.step) }

    private func waiting(_ text: String) -> some View {
        Text(text).font(Theme.body(16, .semibold)).multilineTextAlignment(.center)
            .frame(maxWidth: .infinity).padding(.vertical, 10)
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
            Text("\(amount)")
                .font(.system(size: 34, weight: .black, design: .rounded).monospacedDigit())
                .frame(maxWidth: .infinity)
                .contentTransition(.numericText())
            stepButton("plus") { amount = min(range.upperBound, amount + Betting.step) }
                .disabled(amount >= range.upperBound)
        }
        .onAppear { amount = min(max(amount, range.lowerBound), range.upperBound) }
        .animation(.snappy, value: amount)
    }

    private func stepButton(_ icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 22, weight: .heavy))
                .frame(width: 56, height: 56)
                .background(Circle().fill(.white))
        }
        .foregroundStyle(Theme.ink)
    }
}
