import SwiftUI

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

/// The controls at the bottom of a screen.
struct BottomSheet<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        VStack(spacing: 14) { content }
            .padding(.horizontal, 24)
            .padding(.bottom, 12)
            .frame(maxWidth: .infinity)
    }
}
