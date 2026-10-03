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
