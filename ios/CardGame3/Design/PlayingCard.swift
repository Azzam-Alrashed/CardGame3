import SwiftUI

/// A white playing card face; rank 11-14 = J, Q, K, A. Suits: S, H, D, C.
struct PlayingCard: View {
    var rank: Int
    var suit: String
    var width: CGFloat = 90

    private var rankText: String {
        switch rank {
        case 14: "A"
        case 13: "K"
        case 12: "Q"
        case 11: "J"
        default: "\(rank)"
        }
    }
    private var suitSymbol: String { ["S": "♠", "H": "♥", "D": "♦", "C": "♣"][suit] ?? "?" }
    private var ink: Color { suit == "H" || suit == "D" ? Theme.hotPink : Theme.ink }

    var body: some View {
        RoundedRectangle(cornerRadius: width * 0.14)
            .fill(.white)
            .overlay(RoundedRectangle(cornerRadius: width * 0.14).strokeBorder(Theme.ink, lineWidth: 2.5))
            .overlay(alignment: .topLeading) {
                VStack(spacing: -2) {
                    Text(rankText).font(Theme.body(width * 0.26, .black))
                    Text(suitSymbol).font(.system(size: width * 0.2))
                }
                .padding(width * 0.09)
            }
            .overlay { Text(suitSymbol).font(.system(size: width * 0.5)) }
            .foregroundStyle(ink)
            .frame(width: width, height: width * 1.4)
            .shadow(color: .black.opacity(0.18), radius: 10, y: 6)
    }
}
