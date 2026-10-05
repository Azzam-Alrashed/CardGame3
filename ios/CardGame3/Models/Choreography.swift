import SwiftUI

// When each part of the deal happens. Every view that draws a piece of it (the flying cards, the
// small cards in front of each player, your hand) reads the same timeline, so they move together.
// Times are seconds since this phone saw the round dealt (see `Backend.dealtAt`).

/// The deal: the dealer shuffles, then gives one card at a time to each player, four times around.
struct DealTimeline: Equatable {
    static let shuffle: TimeInterval = 0.6
    static let flight: TimeInterval = 0.35
    static let handSize = 4

    /// Players in the order they get cards: starting after the dealer, going right.
    let order: [String]
    /// Time between two cards leaving the dealer: shorter on big tables, so a deal takes about 2 s.
    let interval: TimeInterval

    init(seats: [String], dealerId: String) {
        let first = (seats.firstIndex(of: dealerId) ?? -1) + 1
        order = seats.indices.map { seats[(first + $0) % seats.count] }
        interval = min(0.1, 1.4 / Double(max(1, seats.count * Self.handSize)))
    }

    var total: TimeInterval { Self.shuffle + Double(order.count * Self.handSize - 1) * interval + Self.flight }

    /// When card `index` (0–3) of `uid`'s hand leaves the dealer, or nil if they aren't dealt in.
    func departure(_ uid: String, card index: Int) -> TimeInterval? {
        guard let seat = order.firstIndex(of: uid) else { return nil }
        return Self.shuffle + Double(index * order.count + seat) * interval
    }

    /// Cards in the air at `t`: who each is for, which of their cards, and how far along (0–1).
    func flights(at t: TimeInterval) -> [Flight] {
        guard t >= Self.shuffle else { return [] }
        var flights: [Flight] = []
        for (seat, uid) in order.enumerated() {
            for card in 0..<Self.handSize {
                let leaves = Self.shuffle + Double(card * order.count + seat) * interval
                let progress = (t - leaves) / Self.flight
                if progress >= 0, progress < 1 { flights.append(Flight(uid: uid, card: card, progress: progress)) }
            }
        }
        return flights
    }

    struct Flight: Hashable {
        let uid: String
        let card: Int
        let progress: Double
        var id: String { "\(uid)#\(card)" }
    }

    // MARK: Bots' tells

    /// AI and away players look at their cards too: soon after the deal, one card at a time.
    /// Seeded by player and round, so every phone shows the same thing. `t` is infinite when
    /// this phone didn't see the deal: they've long since looked.
    static func botPeeks(_ uid: String, round: Int, sinceDealEnd t: TimeInterval) -> Int {
        let start = 0.4 + Double(stableHash("\(uid):\(round)") % 1600) / 1000
        guard t >= start else { return 0 }
        guard t < start + botPeeksWindow else { return handSize }
        return min(handSize, 1 + Int((t - start) / 0.35))
    }

    /// Every bot has looked at all its cards this long after the deal ends.
    static let botPeeksWindow: TimeInterval = 0.4 + 1.6 + 0.35 * 4

    /// FNV-1a: the same on every phone and every launch (Swift's `hashValue` is not).
    private static func stableHash(_ s: String) -> UInt64 {
        s.utf8.reduce(14_695_981_039_346_656_037) { ($0 ^ UInt64($1)) &* 1_099_511_628_211 }
    }
}

/// A deal this phone is showing: its timeline and when it started.
struct DealClock: Equatable {
    let timeline: DealTimeline
    let start: Date
    let round: Int

    var end: Date { start.addingTimeInterval(timeline.total) }
    /// When the last bot has finished looking at its cards.
    var peeksEnd: Date { end.addingTimeInterval(DealTimeline.botPeeksWindow) }

    /// Whether card `index` of `uid`'s hand has reached them by `date`.
    func landed(_ uid: String, card index: Int, at date: Date) -> Bool {
        guard let leaves = timeline.departure(uid, card: index) else { return true }
        return date.timeIntervalSince(start) >= leaves + DealTimeline.flight
    }

    func landedCount(_ uid: String, at date: Date) -> Int {
        (0..<DealTimeline.handSize).filter { landed(uid, card: $0, at: date) }.count
    }
}

/// Animation frames from now until `end`, then none: a `TimelineView` using it goes quiet when it's done.
struct FramesUntil: TimelineSchedule {
    var end: Date
    var fps: Double = 60

    func entries(from start: Date, mode: TimelineScheduleMode) -> AnySequence<Date> {
        let step = 1 / (mode == .lowFrequency ? 1 : fps)
        return AnySequence(sequence(first: start) { $0 < end ? $0.addingTimeInterval(step) : nil })
    }
}
