import SwiftUI
import UIKit

/// My cards. They're dealt face down and I turn them over by tapping (or swiping across them);
/// the table sees how many I've looked at. Betting without looking is allowed.
struct MyHand: View {
    @Environment(Backend.self) private var backend
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var cards: [Card]
    /// I'm dealt into this round (not knocked out).
    var inRound: Bool
    /// False until my cards for this round have arrived: the room and the cards update separately.
    var ready: Bool
    /// Everything shows face up: the round is over, or a bot holds my seat.
    var allFaceUp: Bool
    /// The deal this phone is animating; cards appear as they land.
    var clock: DealClock?
    /// The card lifted by a tap once it's face up, to read it more easily.
    @State private var lifted: Int?
    @State private var showHint = false

    static let cardWidth: CGFloat = 84
    /// How much each card leans in the fan; the flying cards land at the same angle.
    static func angle(_ index: Int) -> Double { Double(index) * 6 - 9 }

    private func isUp(_ index: Int) -> Bool { ready && (allFaceUp || backend.flipped.contains(index)) }
    /// The cards I can see, for the hand name.
    private var seen: [Card] { cards.indices.filter(isUp).map { cards[$0] } }

    var body: some View {
        VStack(spacing: 6) {
            if inRound {
                // Frames while cards are landing, and while they wiggle for attention.
                TimelineView(FramesUntil(end: showHint ? .distantFuture : clock?.end ?? .distantPast, fps: 30)) { context in
                    fan(at: context.date)
                }
            }
            label.frame(height: 28)
        }
        .frame(minHeight: Self.cardWidth * 1.4 + 38, alignment: .bottom)
        .padding(.bottom, 12)
        .onChange(of: cards) { lifted = nil }
        .task(id: hintKey) { await hintIfUntouched() }
    }

    private func fan(at date: Date) -> some View {
        HStack(spacing: -24) {
            ForEach(0..<DealTimeline.handSize, id: \.self) { i in
                let landed = clock.map { $0.landed(backend.uid ?? "", card: i, at: date) } ?? true
                card(i)
                    .dealSpot(.hand(i))
                    .rotationEffect(.degrees(Self.angle(i) + (showHint ? wiggle(i, date) : 0)), anchor: .bottom)
                    .offset(y: abs(Double(i) - 1.5) * 6 - (lifted == i ? 22 : 0))
                    .zIndex(lifted == i ? 1 : 0)
                    .opacity(landed ? 1 : 0)
                    .animation(reduceMotion ? .easeIn(duration: 0.25) : nil, value: landed)
                    .onTapGesture { tap(i) }
                    .accessibilityAddTraits(.isButton)
                    .accessibilityHint(isUp(i) ? "" : "Double-tap to turn it over")
                    .accessibilityAction { tap(i) }
            }
        }
        .padding(.bottom, 4)
        // Swipe across the cards to turn over the rest, one after another.
        .gesture(DragGesture(minimumDistance: 24).onEnded { drag in
            if abs(drag.translation.width) > 40 { turnOverRest(rightward: drag.translation.width > 0) }
        })
    }

    @ViewBuilder private func card(_ i: Int) -> some View {
        if ready, i < cards.count {
            FlipCard(card: cards[i], faceUp: isUp(i), width: Self.cardWidth)
        } else {
            CardBack(width: Self.cardWidth)
        }
    }

    @ViewBuilder private var label: some View {
        if let hand = HandValue(seen) {
            pill(hand.name)
                .keyframeAnimator(initialValue: 1.0, trigger: hand.category) { view, scale in
                    view.scaleEffect(scale)
                } keyframes: { _ in
                    SpringKeyframe(1.25, duration: 0.15)
                    SpringKeyframe(1.0, duration: 0.35)
                }
                .transition(.scale.combined(with: .opacity))
        } else if showHint {
            pill("Tap your cards to look").transition(.scale.combined(with: .opacity))
        }
    }

    private func pill(_ text: String) -> some View {
        Text(text)
            .font(Theme.body(14, .heavy))
            .padding(.horizontal, 12).padding(.vertical, 5)
            .background(Capsule().fill(.white.opacity(0.85)))
            .contentTransition(.opacity)
    }

    // MARK: Turning cards over

    private func tap(_ i: Int) {
        if isUp(i) {
            withAnimation(.spring(duration: 0.3, bounce: 0.5)) { lifted = lifted == i ? nil : i }
        } else if ready {
            turnOver(i)
        }
    }

    private func turnOver(_ i: Int) {
        let before = HandValue(seen)?.category
        withAnimation(.spring(duration: 0.45, bounce: 0.25)) {
            showHint = false
            backend.flip([i])
        }
        SoundPlayer.shared.play(.flip)
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        if let after = HandValue(seen)?.category, after >= .onePair, after > (before ?? .highCard) {
            celebrate(after)
        }
    }

    /// A pair, then trips: each better hand pops a little higher. Four of a kind gets the fanfare.
    private func celebrate(_ category: HandValue.Category) {
        if category == .fourOfAKind {
            SoundPlayer.shared.play(.quads)
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        } else {
            let rate: Float = category == .threeOfAKind ? 1.26 : category == .twoPairs ? 1.12 : 1
            SoundPlayer.shared.play(.match, rate: rate)
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        }
    }

    private func turnOverRest(rightward: Bool) {
        guard ready else { return }
        let rest = (0..<DealTimeline.handSize).filter { !isUp($0) }
        Task {
            for i in rightward ? rest : rest.reversed() {
                turnOver(i)
                try? await Task.sleep(for: .milliseconds(90))
            }
        }
    }

    // MARK: Nudge

    private var hintKey: String { "\(clock?.round ?? 0) \(backend.flipped.isEmpty) \(allFaceUp) \(ready)" }

    /// New players may not know the cards can be turned over: after a moment, wiggle them and say so.
    private func hintIfUntouched() async {
        showHint = false
        guard ready, inRound, !allFaceUp, backend.flipped.isEmpty else { return }
        let landed = clock?.end ?? .now
        try? await Task.sleep(for: .seconds(max(0, landed.timeIntervalSinceNow) + 2))
        guard !Task.isCancelled else { return }
        withAnimation(.spring) { showHint = true }
    }

    /// A gentle side-to-side rock, each card a little out of step with the next.
    private func wiggle(_ i: Int, _ date: Date) -> Double {
        sin(date.timeIntervalSinceReferenceDate * 9 + Double(i)) * 2.5
    }
}
