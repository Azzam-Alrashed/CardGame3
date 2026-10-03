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
            .background(Capsule().fill(primary ? Theme.ink : .white.opacity(0.35)))
            .overlay(Capsule().strokeBorder(Theme.ink, lineWidth: primary ? 0 : 2))
            .contentShape(Capsule())
            .opacity(isEnabled ? 1 : 0.35)
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.spring(duration: 0.25, bounce: 0.5), value: configuration.isPressed)
            .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
    }
}

/// White circle icon button, like the "?" and leave buttons.
struct CircleButtonStyle: ButtonStyle {
    var size: CGFloat = 44
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: size * 0.4, weight: .heavy))
            .foregroundStyle(Theme.ink)
            .frame(width: size, height: size)
            .background(Circle().fill(.white))
            .shadow(color: .black.opacity(0.08), radius: 10, y: 4)
            .opacity(isEnabled ? 1 : 0.4)
            .scaleEffect(configuration.isPressed ? 0.9 : 1)
            .animation(.spring(duration: 0.25, bounce: 0.5), value: configuration.isPressed)
            .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
    }
}

/// A button that runs server work: shows a spinner and ignores taps until it finishes.
/// Reports `BusyKey` so a parent can block its other buttons meanwhile.
struct AsyncButton<Label: View>: View {
    var action: () async -> Void
    @ViewBuilder var label: Label
    @State private var running = false

    var body: some View {
        Button {
            running = true
            Task {
                await action()
                running = false
            }
        } label: {
            label
                .opacity(running ? 0 : 1)
                .overlay { if running { Spinner() } }
        }
        .allowsHitTesting(!running)
        .preference(key: BusyKey.self, value: running)
    }
}

extension AsyncButton where Label == Text {
    init(_ title: String, action: @escaping () async -> Void) {
        self.init(action: action) { Text(title) }
    }
}

/// A small spinning arc in the current text color.
struct Spinner: View {
    @State private var spinning = false
    var body: some View {
        Circle()
            .trim(from: 0, to: 0.7)
            .stroke(style: StrokeStyle(lineWidth: 3, lineCap: .round))
            .frame(width: 20, height: 20)
            .rotationEffect(.degrees(spinning ? 360 : 0))
            .animation(.linear(duration: 0.8).repeatForever(autoreverses: false), value: spinning)
            .onAppear { spinning = true }
    }
}

/// True while any `AsyncButton` inside is running.
struct BusyKey: PreferenceKey {
    static let defaultValue = false
    static func reduce(value: inout Bool, nextValue: () -> Bool) { value = value || nextValue() }
}

extension View {
    /// Blocks taps on everything inside while one of its `AsyncButton`s is running.
    func blocksWhileBusy() -> some View { modifier(BlocksWhileBusy()) }
}

private struct BlocksWhileBusy: ViewModifier {
    @State private var busy = false
    func body(content: Content) -> some View {
        content
            .allowsHitTesting(!busy)
            .onPreferenceChange(BusyKey.self) { busy = $0 }
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
            .tint(Theme.ink)
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
