import SwiftUI

/// The full house rules, opened from the "?" on the home screen.
struct HowToPlayView: View {
    var onReplayIntro: () -> Void
    @Environment(\.dismiss) private var dismiss

    private let sections: [(String, [String])] = [
        ("Setup", [
            "4 to 13 players. The deck has one rank per player: 4 players play with A, K, Q, J.",
            "Everyone gets 4 cards and starts with 5,000 points. Reaching 0 knocks you out.",
        ]),
        ("Betting", [
            "Starts with the player after the dealer, going right.",
            "Enter by betting, or withdraw for free and sit out this round.",
            "Bets go up in steps of 500, starting at 500. You must beat the highest bet so far, or go all in.",
            "The highest bettor is the boss. On a tie, the latest one to reach it.",
        ]),
        ("Deals", [
            "Before the reveal, others can offer to withdraw in return for some of the boss's winnings.",
            "Offers are in steps of 500 and everyone sees them. Bluffing is allowed.",
            "Accepted deals together can never be more than the boss's bet.",
            "The boss accepts or rejects. An acceptance can't be undone; a rejected player may offer again.",
        ]),
        ("Reveal", [
            "The boss chooses when to reveal, or the timer runs out.",
            "Everyone without an accepted deal reveals with the boss.",
            "Boss wins: keeps the bet, gains the same amount, then pays the accepted deals.",
            "Someone beats the boss: that player wins the boss's bet. Losers lose their own bets.",
            "Players with a deal get nothing if the boss loses, but lose nothing either.",
        ]),
        ("Automatic wins", [
            "Only one player enters: they win their bet.",
            "Everyone else takes a deal: the boss wins the bet and pays all deals.",
        ]),
        ("Between rounds", [
            "The dealer passes one seat to the right.",
            "As players are knocked out, the deck shrinks to match.",
            "If everyone withdraws, the cards are reshuffled and the next dealer deals.",
        ]),
        ("End of game", [
            "The game ends when fewer than 4 players remain. Most points wins.",
            "Equal points: whoever reached it first. Same round: closest to the dealer's right.",
        ]),
    ]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    card("Hands, best to worst") { HandRanking() }
                    ForEach(sections, id: \.0) { title, lines in
                        card(title) {
                            VStack(alignment: .leading, spacing: 8) {
                                ForEach(lines, id: \.self) { line in
                                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                                        Text("•").font(Theme.body(16, .black))
                                        Text(line).font(Theme.body(16, .medium))
                                            .fixedSize(horizontal: false, vertical: true)
                                    }
                                }
                            }
                        }
                    }
                    Button("Replay the intro") {
                        dismiss()
                        onReplayIntro()
                    }
                    .buttonStyle(PillButtonStyle(primary: false))
                    .padding(.top, 8)
                }
                .padding(20)
                .frame(maxWidth: 640)
                .frame(maxWidth: .infinity)
            }
            .background(Theme.sunny.ignoresSafeArea())
            .foregroundStyle(Theme.ink)
            .navigationTitle("How to play")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }.font(Theme.body(17, .bold))
                }
            }
        }
        .tint(Theme.ink)
    }

    private func card(_ title: String, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title).font(Theme.body(20, .heavy))
            content()
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 24).fill(.white))
    }
}
