import SwiftUI

struct MyHand: View {
    var cards: [Card]
    var revealed: Bool

    var body: some View {
        HStack(spacing: -24) {
            ForEach(Array(cards.enumerated()), id: \.element) { i, card in
                PlayingCard(rank: card.rank, suit: card.suit, width: 84)
                    .rotationEffect(.degrees(Double(i) * 6 - 9), anchor: .bottom)
                    .offset(y: abs(Double(i) - 1.5) * 6)
            }
        }
        .padding(.bottom, 16)
        .animation(.spring, value: cards)
    }
}
