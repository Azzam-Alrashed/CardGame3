import Foundation

/// What a hand is, for showing its name ("Pair of Kings"). Also works on the 1–3 cards turned over so far.
/// Mirrors `evaluateHand` in firebase/functions/src/engine/hands.ts; the server decides who wins.
struct HandValue: Equatable {
    enum Category: Int, Comparable {
        case highCard, onePair, twoPairs, threeOfAKind, fourOfAKind
        static func < (a: Self, b: Self) -> Bool { a.rawValue < b.rawValue }
    }

    let category: Category
    /// Ranks of the paired, tripled or quad groups, strongest first.
    let groups: [Int]
    /// Ranks of the leftover single cards, highest first.
    let kickers: [Int]

    init?(_ cards: [Card]) {
        guard (1...4).contains(cards.count) else { return nil }
        let counts = Dictionary(grouping: cards, by: \.rank).mapValues(\.count)
        // Bigger groups first, then higher rank.
        let byGroup = counts.sorted { $0.value != $1.value ? $0.value > $1.value : $0.key > $1.key }
        groups = byGroup.filter { $0.value > 1 }.map(\.key)
        kickers = byGroup.filter { $0.value == 1 }.map(\.key)
        switch byGroup[0].value {
        case 4: category = .fourOfAKind
        case 3: category = .threeOfAKind
        case 2: category = groups.count == 2 ? .twoPairs : .onePair
        default: category = .highCard
        }
    }

    var name: String {
        switch category {
        case .fourOfAKind: "Four \(Self.plural(groups[0]))"
        case .threeOfAKind: "Three \(Self.plural(groups[0]))"
        case .twoPairs: "Two pairs · \(Self.short(groups[0])) & \(Self.short(groups[1]))"
        case .onePair: "Pair of \(Self.plural(groups[0]))"
        case .highCard: "\(Self.singular(kickers[0])) high"
        }
    }

    private static func singular(_ rank: Int) -> String {
        [14: "Ace", 13: "King", 12: "Queen", 11: "Jack"][rank] ?? "\(rank)"
    }

    private static func plural(_ rank: Int) -> String {
        rank == 6 ? "Sixes" : rank > 10 ? singular(rank) + "s" : "\(rank)s"
    }

    private static func short(_ rank: Int) -> String {
        [14: "A", 13: "K", 12: "Q", 11: "J"][rank] ?? "\(rank)"
    }
}
