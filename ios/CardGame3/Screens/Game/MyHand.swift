import SwiftUI

struct MyHand: View {
    var cards: [Card]
    var revealed: Bool
    /// The card lifted by a tap, to read it more easily.
    @State private var lifted: Card?

    var body: some View {
        HStack(spacing: -24) {
            ForEach(Array(cards.enumerated()), id: \.element) { i, card in
                PlayingCard(rank: card.rank, suit: card.suit, width: 84)
                    .rotationEffect(.degrees(Double(i) * 6 - 9), anchor: .bottom)
                    .offset(y: abs(Double(i) - 1.5) * 6 - (lifted == card ? 22 : 0))
                    .zIndex(lifted == card ? 1 : 0)
                    .onTapGesture { withAnimation(.spring(duration: 0.3, bounce: 0.5)) { lifted = lifted == card ? nil : card } }
                    // Dealt in one at a time, flying up from below.
                    .transition(
                        .offset(y: 260).combined(with: .scale(scale: 0.6)).combined(with: .opacity)
                            .animation(.spring(duration: 0.55, bounce: 0.3).delay(Double(i) * 0.09))
                    )
            }
        }
        .padding(.bottom, 16)
        .frame(minHeight: 84 * 1.4 + 16)
        .animation(.spring, value: cards)
        .sensoryFeedback(.impact(weight: .medium), trigger: cards) { old, new in old.isEmpty && !new.isEmpty }
        .onChange(of: cards) { lifted = nil }
    }
}
