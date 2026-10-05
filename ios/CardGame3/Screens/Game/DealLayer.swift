import SwiftUI
import UIKit

/// Places the deal starts from and lands on, reported by the views that own them.
enum DealSpot: Hashable {
    /// The dealer's blob, where the deck sits.
    case dealer
    /// The small cards in front of a player.
    case seat(String)
    /// One of my cards, by position.
    case hand(Int)
    /// The visible part of the players strip (players scrolled out of view get cards at its edge).
    case strip
}

/// Where the deal starts and lands, in screen coordinates: written as those views lay out (and scroll),
/// read by `DealLayer` on every frame. Anchor preferences can't carry this: they don't come out of
/// the players' ScrollView.
final class DealSpots {
    var frames: [DealSpot: CGRect] = [:]
}

private struct DealSpotsKey: EnvironmentKey {
    static let defaultValue = DealSpots()
}

extension EnvironmentValues {
    var dealSpots: DealSpots {
        get { self[DealSpotsKey.self] }
        set { self[DealSpotsKey.self] = newValue }
    }
}

extension View {
    /// Marks this view as a place the deal starts from or lands on.
    func dealSpot(_ spot: DealSpot?) -> some View {
        modifier(DealSpotMarker(spot: spot))
    }
}

private struct DealSpotMarker: ViewModifier {
    @Environment(\.dealSpots) private var spots
    var spot: DealSpot?

    private struct Placed: Equatable {
        var spot: DealSpot?
        var frame: CGRect
    }

    func body(content: Content) -> some View {
        // The spot is part of the value, so a chip that becomes the dealer reports at once.
        content.onGeometryChange(for: Placed.self) { Placed(spot: spot, frame: $0.frame(in: .global)) } action: { placed in
            if let spot = placed.spot { spots.frames[spot] = placed.frame }
        }
    }
}

/// The dealer shuffling, then cards flying face down to every player, one at a time, with sound.
/// Drawn over the whole table; the small cards and my hand show each card once it has landed.
struct DealLayer: View {
    var clock: DealClock
    var me: String?
    var spots: DealSpots
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// Size of the small cards in front of each player (`MiniCards`).
    static let miniWidth: CGFloat = 12

    var body: some View {
        GeometryReader { geo in
            if !reduceMotion {
                TimelineView(FramesUntil(end: clock.end)) { context in
                    let t = context.date.timeIntervalSince(clock.start)
                    ZStack(alignment: .topLeading) {
                        if t < DealTimeline.shuffle, let deck = dealerPoint(geo) {
                            shuffling(t).position(deck)
                        }
                        ForEach(clock.timeline.flights(at: t), id: \.id) { flight in
                            if let from = dealerPoint(geo), let target = target(flight, geo) {
                                flying(flight.progress, from: from, to: target)
                            }
                        }
                    }
                    .frame(width: geo.size.width, height: geo.size.height, alignment: .topLeading)
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .task(id: clock.start) { await playSounds() }
    }

    // MARK: Drawing

    /// Two half-decks riffling into each other above the dealer.
    private func shuffling(_ t: TimeInterval) -> some View {
        let split = sin(t / DealTimeline.shuffle * .pi) * 9
        let riffle = sin(t * 40) * 1.5
        return ZStack {
            CardBack(width: 16).rotationEffect(.degrees(-8)).offset(x: -split, y: riffle)
            CardBack(width: 16).rotationEffect(.degrees(8)).offset(x: split, y: -riffle)
        }
    }

    private struct Target {
        var point: CGPoint
        var width: CGFloat
        var angle: Double
    }

    /// One card on its way: an arc from the deck, spinning, growing to its landing size, squashing as it lands.
    private func flying(_ progress: Double, from: CGPoint, to target: Target) -> some View {
        let p = 1 - pow(1 - progress, 2.2) // fast out of the hand, settling at the end
        let lift = min(90, hypot(target.point.x - from.x, target.point.y - from.y) * 0.35)
        let control = CGPoint(x: (from.x + target.point.x) / 2, y: min(from.y, target.point.y) - lift)
        let point = CGPoint(
            x: pow(1 - p, 2) * from.x + 2 * (1 - p) * p * control.x + p * p * target.point.x,
            y: pow(1 - p, 2) * from.y + 2 * (1 - p) * p * control.y + p * p * target.point.y
        )
        let width = 16 + (target.width - 16) * p
        let squash = progress > 0.85 ? 1 - (progress - 0.85) * 0.8 : 1
        return CardBack(width: width)
            .scaleEffect(x: 2 - squash, y: squash)
            .rotationEffect(.degrees((1 - p) * 300 + target.angle * p))
            .position(point)
    }

    // MARK: Where

    /// A spot's frame in this layer's coordinates.
    private func rect(_ spot: DealSpot, _ geo: GeometryProxy) -> CGRect? {
        let origin = geo.frame(in: .global).origin
        return spots.frames[spot]?.offsetBy(dx: -origin.x, dy: -origin.y)
    }

    /// The deck: on the dealer's blob, kept inside the visible strip if they're scrolled out of view.
    private func dealerPoint(_ geo: GeometryProxy) -> CGPoint? {
        guard let dealer = rect(.dealer, geo) else { return nil }
        return clampedToStrip(CGPoint(x: dealer.midX, y: dealer.midY), geo)
    }

    private func target(_ flight: DealTimeline.Flight, _ geo: GeometryProxy) -> Target? {
        if flight.uid == me, let slot = rect(.hand(flight.card), geo) {
            // The slot may be the tilted card's bounding box: its center is right, its width isn't.
            return Target(point: CGPoint(x: slot.midX, y: slot.midY), width: MyHand.cardWidth, angle: MyHand.angle(flight.card))
        }
        guard let seat = rect(.seat(flight.uid), geo) else { return nil }
        // Fanned like MiniCards: about 7 pt apart.
        let x = seat.midX + (CGFloat(flight.card) - 1.5) * (Self.miniWidth - 5)
        return Target(point: clampedToStrip(CGPoint(x: x, y: seat.midY), geo), width: Self.miniWidth,
                      angle: Double(flight.card) * 8 - 12)
    }

    private func clampedToStrip(_ point: CGPoint, _ geo: GeometryProxy) -> CGPoint {
        guard let strip = rect(.strip, geo) else { return point }
        return CGPoint(x: min(max(point.x, strip.minX + 12), strip.maxX - 12), y: point.y)
    }

    // MARK: Sound

    /// The shuffle, a swish for each card, and a soft landing (with a tap) for each of mine.
    private func playSounds() async {
        let sound = SoundPlayer.shared
        let timeline = clock.timeline
        var events: [(at: TimeInterval, play: () -> Void)] = [(0, { sound.play(.shuffle) })]
        // On a big table cards leave every few hundredths of a second: a swish for every other one is plenty.
        let every = timeline.interval < 0.05 ? 2 : 1
        for (seat, uid) in timeline.order.enumerated() {
            for card in 0..<DealTimeline.handSize {
                guard let leaves = timeline.departure(uid, card: card) else { continue }
                if (card * timeline.order.count + seat) % every == 0 {
                    events.append((leaves, { sound.play(.deal, volume: 0.7) }))
                }
                if uid == me {
                    events.append((leaves + DealTimeline.flight, {
                        sound.play(.land, volume: 0.6)
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    }))
                }
            }
        }
        for event in events.sorted(by: { $0.at < $1.at }) {
            let wait = event.at - Date.now.timeIntervalSince(clock.start)
            // Already past (the deal started a while ago): stay quiet rather than play a burst.
            if wait < -0.1 { continue }
            if wait > 0 { try? await Task.sleep(for: .seconds(wait)) }
            if Task.isCancelled { return }
            event.play()
        }
    }
}
