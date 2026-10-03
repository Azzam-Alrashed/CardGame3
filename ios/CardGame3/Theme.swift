import SwiftUI

// Playful look: bold solid screen colors, heavy rounded headlines, black pill buttons,
// tilted playing cards, and soft gradient blob characters with faces.

enum Theme {
    static let ink = Color(red: 0.07, green: 0.07, blue: 0.08)
    static let cream = Color(red: 0.97, green: 0.95, blue: 0.93)

    /// Bold screen backgrounds.
    static let lime = Color(red: 0.49, green: 0.89, blue: 0.10)
    static let sunny = Color(red: 1.00, green: 0.93, blue: 0.10)
    static let hotPink = Color(red: 1.00, green: 0.38, blue: 0.56)
    static let grape = Color(red: 0.55, green: 0.32, blue: 0.98)

    /// Blob colors, also used to give each player their own avatar color.
    static let blobColors: [Color] = [
        Color(red: 0.55, green: 0.89, blue: 0.42), // green
        Color(red: 1.00, green: 0.48, blue: 0.43), // coral
        Color(red: 0.44, green: 0.70, blue: 0.97), // blue
        Color(red: 1.00, green: 0.79, blue: 0.30), // yellow
        Color(red: 0.96, green: 0.60, blue: 0.79), // pink
        Color(red: 1.00, green: 0.62, blue: 0.35), // orange
        Color(red: 0.70, green: 0.58, blue: 0.97), // lilac
    ]

    static func color(forSeat index: Int) -> Color { blobColors[index % blobColors.count] }

    static func wordmark(_ size: CGFloat) -> Font { .system(size: size, weight: .black, design: .rounded) }
    static func body(_ size: CGFloat, _ weight: Font.Weight = .semibold) -> Font {
        .system(size: size, weight: weight, design: .rounded)
    }
}

// MARK: - Blob character

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

// MARK: - Controls

/// Full-width pill buttons: solid black (primary) or black outline (secondary).
struct PillButtonStyle: ButtonStyle {
    var primary = true
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(Theme.body(18, .bold))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 18)
            .foregroundStyle(primary ? .white : Theme.ink)
            .background(Capsule().fill(primary ? Theme.ink : .clear))
            .overlay(Capsule().strokeBorder(Theme.ink, lineWidth: primary ? 0 : 2))
            .opacity(isEnabled ? 1 : 0.35)
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(duration: 0.2), value: configuration.isPressed)
    }
}

/// White pill text field.
struct PillField: View {
    var placeholder: String
    @Binding var text: String

    var body: some View {
        TextField(placeholder, text: $text)
            .font(Theme.body(18))
            .foregroundStyle(Theme.ink)
            .padding(.horizontal, 22)
            .padding(.vertical, 16)
            .background(Capsule().fill(.white))
            .shadow(color: .black.opacity(0.06), radius: 12, y: 4)
    }
}

// MARK: - Playing card

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
