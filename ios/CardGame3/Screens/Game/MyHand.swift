import SwiftUI

struct MyHand: View {
    var cards: [Card]
    var revealed: Bool
    /// The card lifted by a tap, to read it more easily.
    @State private var lifted: Card?

    var body: some View {
        VStack(spacing: 6) {
            fan
            // What you're holding, so nobody has to work out "two pairs" under pressure.
            if let hand = HandValue(cards) {
                Text(hand.name)
                    .font(Theme.body(14, .heavy))
                    .padding(.horizontal, 12).padding(.vertical, 5)
                    .background(Capsule().fill(.white.opacity(0.85)))
                    .contentTransition(.opacity)
                    .transition(.scale.combined(with: .opacity))
            }
        }
        .padding(.bottom, 12)
        .animation(.spring(duration: 0.4).delay(0.4), value: cards)
    }

    private var fan: some View {
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
        .padding(.bottom, 4)
        .frame(minHeight: 84 * 1.4 + 4)
        .animation(.spring, value: cards)
        .sensoryFeedback(.impact(weight: .medium), trigger: cards) { old, new in old.isEmpty && !new.isEmpty }
        .onChange(of: cards) { lifted = nil }
    }
}
