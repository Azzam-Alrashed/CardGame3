import SwiftUI

/// A soft glowing ball with a little face.
struct Blob: View {
    enum Mood { case happy, wink, surprised, sleepy }

    var color: Color
    var size: CGFloat
    var mood: Mood = .happy
    /// Where the eyes look, from -1 to 1.
    var look: CGSize = .zero
    var hair = false

    var body: some View {
        ZStack {
            Circle()
                .fill(color)
                .overlay(
                    // Soft highlight top-left, slightly darker edge.
                    Circle().fill(RadialGradient(
                        colors: [.white.opacity(0.6), .white.opacity(0), .black.opacity(0.08)],
                        center: UnitPoint(x: 0.35, y: 0.3),
                        startRadius: 0,
                        endRadius: size * 0.7
                    ))
                )
                .shadow(color: color.opacity(0.45), radius: size * 0.12, y: size * 0.06)

            face.frame(width: size * 0.6, height: size * 0.45).offset(y: size * 0.02)

            if hair {
                HairCurl()
                    .stroke(Theme.ink, style: StrokeStyle(lineWidth: size * 0.05, lineCap: .round))
                    .frame(width: size * 0.7, height: size * 0.3)
                    .offset(x: size * 0.1, y: -size * 0.42)
            }
        }
        .frame(width: size, height: size)
    }

    @ViewBuilder private var face: some View {
        let eye = size * 0.17
        VStack(spacing: size * 0.06) {
            HStack(spacing: size * 0.08) {
                eyeView(eye, closed: mood == .sleepy)
                eyeView(eye, closed: mood == .wink || mood == .sleepy)
            }
            mouth
        }
    }

    private func eyeView(_ d: CGFloat, closed: Bool) -> some View {
        ZStack {
            if closed {
                Smile()
                    .stroke(Theme.ink, style: StrokeStyle(lineWidth: d * 0.22, lineCap: .round))
                    .frame(width: d * 0.8, height: d * 0.35)
            } else {
                Circle().fill(.white).frame(width: d, height: d)
                Circle().fill(Theme.ink).frame(width: d * 0.55, height: d * 0.55)
                    .offset(x: look.width * d * 0.18, y: look.height * d * 0.18)
            }
        }
        .frame(width: d, height: d)
    }

    @ViewBuilder private var mouth: some View {
        switch mood {
        case .surprised:
            Circle().stroke(Theme.ink, lineWidth: size * 0.035).frame(width: size * 0.1, height: size * 0.1)
        default:
            Smile()
                .stroke(Theme.ink, style: StrokeStyle(lineWidth: size * 0.045, lineCap: .round))
                .frame(width: size * 0.22, height: size * 0.08)
        }
    }
}

private struct Smile: Shape {
    func path(in r: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: r.minX, y: r.minY))
        p.addQuadCurve(to: CGPoint(x: r.maxX, y: r.minY), control: CGPoint(x: r.midX, y: r.maxY * 2))
        return p
    }
}

private struct HairCurl: Shape {
    func path(in r: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: r.minX, y: r.maxY))
        p.addQuadCurve(to: CGPoint(x: r.maxX * 0.75, y: r.minY), control: CGPoint(x: r.width * 0.2, y: r.minY))
        p.addArc(center: CGPoint(x: r.maxX * 0.85, y: r.minY + r.height * 0.2), radius: r.height * 0.2,
                 startAngle: .degrees(180), endAngle: .degrees(520), clockwise: false)
        return p
    }
}
