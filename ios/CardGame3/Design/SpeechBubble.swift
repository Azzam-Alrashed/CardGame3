import SwiftUI

/// Black speech bubble, like "Hello~".
struct SpeechBubble: View {
    var text: String
    var body: some View {
        Text(text)
            .font(Theme.body(20, .heavy))
            .foregroundStyle(.white)
            .padding(.horizontal, 18)
            .padding(.vertical, 10)
            .background(Capsule().fill(Theme.ink))
            .overlay(alignment: .bottomTrailing) {
                Triangle().fill(Theme.ink).frame(width: 14, height: 12).offset(x: -18, y: 9)
            }
            .rotationEffect(.degrees(-8))
    }
}

private struct Triangle: Shape {
    func path(in r: CGRect) -> Path {
        Path { p in
            p.move(to: CGPoint(x: r.minX, y: r.minY))
            p.addLine(to: CGPoint(x: r.maxX, y: r.minY))
            p.addLine(to: CGPoint(x: r.midX, y: r.maxY))
            p.closeSubpath()
        }
    }
}
