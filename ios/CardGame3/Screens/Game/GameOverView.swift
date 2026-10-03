import SwiftUI

struct GameOverView: View {
    @Environment(Backend.self) private var backend
    var room: Room
    var over: GameOver
    @State private var shown = false

    var body: some View {
        VStack(spacing: 18) {
            Spacer()
            Blob(color: Theme.color(forSeat: room.seat(of: over.winnerId)), size: 150, mood: .wink, hair: true)
                .overlay(alignment: .top) { Text("👑").font(.system(size: 56)).offset(y: -44).rotationEffect(.degrees(-10)) }
                .scaleEffect(shown ? 1 : 0.2)
                .phaseAnimator([false, true]) { blob, up in blob.offset(y: up ? -8 : 4) } animation: { _ in .easeInOut(duration: 1.2) }
            Text(over.winnerId == backend.uid ? "You win!" : "\(room.name(of: over.winnerId)) wins!")
                .font(Theme.wordmark(44))
                .multilineTextAlignment(.center)
                .opacity(shown ? 1 : 0)
            VStack(spacing: 8) {
                ForEach(Array(over.standings.enumerated()), id: \.element.id) { place, seat in
                    HStack(spacing: 10) {
                        Text(place < 3 ? ["🥇", "🥈", "🥉"][place] : "\(place + 1).")
                            .font(Theme.body(place < 3 ? 22 : 17, .heavy)).frame(width: 32)
                        Blob(color: Theme.color(forSeat: room.seat(of: seat.id)), size: 28, mood: place == 0 ? .wink : .happy)
                        Text(seat.id == backend.uid ? "You" : room.name(of: seat.id)).font(Theme.body(17, .bold)).lineLimit(1)
                        Spacer()
                        Text(seat.points.formatted()).font(Theme.body(17, .heavy).monospacedDigit())
                    }
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(Capsule().fill(.white.opacity(place == 0 ? 0.9 : 0.4)))
                    .offset(y: shown ? 0 : 30)
                    .opacity(shown ? 1 : 0)
                    .animation(.spring(duration: 0.5, bounce: 0.35).delay(0.4 + Double(place) * 0.08), value: shown)
                }
            }
            .padding(.horizontal, 24)
            .frame(maxWidth: 520)
            Spacer()
            Button("Back home") { backend.goHome() }
                .buttonStyle(PillButtonStyle())
                .padding(24)
                .frame(maxWidth: 520)
        }
        .overlay { if over.winnerId == backend.uid { Confetti().ignoresSafeArea() } }
        .sensoryFeedback(over.winnerId == backend.uid ? .success : .impact(weight: .medium), trigger: shown) { _, now in now }
        .onAppear { withAnimation(.spring(duration: 0.6, bounce: 0.45)) { shown = true } }
    }
}

/// Bits of colored paper falling from the top for a few seconds, for wins.
struct Confetti: View {
    private struct Piece {
        var x = Double.random(in: 0...1)
        var delay = Double.random(in: 0...0.8)
        var speed = Double.random(in: 280...480)
        var sway = Double.random(in: 1.5...4)
        var spin = Double.random(in: -6...6)
        var size = CGSize(width: .random(in: 7...12), height: .random(in: 10...18))
        var color = (Theme.blobColors + [.white, Theme.hotPink]).randomElement()!
    }

    @State private var pieces = (0..<90).map { _ in Piece() }
    @State private var start = Date.now
    @State private var done = false

    var body: some View {
        TimelineView(.animation(paused: done)) { context in
            Canvas { gc, size in
                let t = context.date.timeIntervalSince(start)
                for p in pieces {
                    let life = t - p.delay
                    guard life > 0 else { continue }
                    let y = -30 + life * p.speed
                    guard y < size.height + 30 else { continue }
                    let x = p.x * size.width + sin(life * p.sway) * 24
                    var piece = gc
                    piece.translateBy(x: x, y: y)
                    piece.rotate(by: .radians(life * p.spin))
                    // Squash horizontally as it tumbles.
                    piece.scaleBy(x: cos(life * p.sway * 2), y: 1)
                    let rect = CGRect(origin: CGPoint(x: -p.size.width / 2, y: -p.size.height / 2), size: p.size)
                    piece.fill(Path(roundedRect: rect, cornerRadius: 2), with: .color(p.color))
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        // Every piece has fallen off screen by then.
        .task {
            try? await Task.sleep(for: .seconds(4))
            done = true
        }
    }
}
