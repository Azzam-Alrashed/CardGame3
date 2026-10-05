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

/// The back of a card: hot pink with a cream frame, and a pair of blob eyes on bigger cards.
struct CardBack: View {
    var width: CGFloat = 90

    var body: some View {
        let corner = width * 0.14
        let big = width >= 40
        RoundedRectangle(cornerRadius: corner)
            .fill(Theme.hotPink)
            .overlay {
                RoundedRectangle(cornerRadius: corner * 0.6)
                    .strokeBorder(Theme.cream.opacity(0.9), lineWidth: max(1, width * 0.04))
                    .padding(width * 0.1)
            }
            .overlay { if big { eyes } }
            .overlay(RoundedRectangle(cornerRadius: corner).strokeBorder(Theme.ink, lineWidth: big ? 2.5 : 1))
            .frame(width: width, height: width * 1.4)
            .shadow(color: .black.opacity(0.18), radius: min(10, width * 0.11), y: min(6, width * 0.07))
    }

    private var eyes: some View {
        HStack(spacing: width * 0.05) {
            ForEach(0..<2, id: \.self) { _ in
                Circle().fill(.white).frame(width: width * 0.2, height: width * 0.2)
                    .overlay(Circle().fill(Theme.ink).frame(width: width * 0.11, height: width * 0.11).offset(y: width * 0.02))
            }
        }
    }
}

/// A card that turns over, around its vertical axis, when `faceUp` changes (animate the change).
struct FlipCard: View {
    var card: Card
    var faceUp: Bool
    var width: CGFloat = 90
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Flipping(angle: faceUp ? 0 : 180, flat: reduceMotion) {
            PlayingCard(rank: card.rank, suit: card.suit, width: width)
        } back: {
            CardBack(width: width)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(faceUp ? card.spokenName : "Face-down card")
    }
}

/// The face until the turn passes 90°, then the back. With Reduce Motion it cross-fades instead of turning.
private struct Flipping<Face: View, Back: View>: View, Animatable {
    var angle: Double
    var flat: Bool
    @ViewBuilder var face: Face
    @ViewBuilder var back: Back

    var animatableData: Double {
        get { angle }
        set { angle = newValue }
    }

    var body: some View {
        let showFace = angle < 90
        ZStack {
            face.opacity(flat ? 1 - angle / 180 : showFace ? 1 : 0)
            back.opacity(flat ? angle / 180 : showFace ? 0 : 1)
        }
        .rotation3DEffect(.degrees(flat ? 0 : showFace ? angle : angle - 180), axis: (x: 0, y: 1, z: 0), perspective: 0.5)
    }
}

extension Card {
    /// "King of hearts", for VoiceOver.
    var spokenName: String {
        let name = [14: "Ace", 13: "King", 12: "Queen", 11: "Jack"][rank] ?? "\(rank)"
        let suitName = ["S": "spades", "H": "hearts", "D": "diamonds", "C": "clubs"][suit] ?? ""
        return "\(name) of \(suitName)"
    }
}
