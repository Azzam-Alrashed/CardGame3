import SwiftUI

/// Shows the right screen for where the player is: intro, home, lobby, or game.
struct RootView: View {
    @Environment(Backend.self) private var backend
    @AppStorage("seenOnboarding") private var seenOnboarding = false

    var body: some View {
        ZStack {
            screenColor.ignoresSafeArea().animation(.easeInOut, value: backend.room?.status)
            Group {
                switch backend.room?.status {
                case nil where !seenOnboarding: OnboardingView { withAnimation { seenOnboarding = true } }
                case nil: HomeView()
                case .lobby: LobbyView()
                case .playing, .finished: GameView()
                }
            }
            .transition(.blurReplace)
        }
        .animation(.smooth(duration: 0.45), value: backend.room?.status)
        .foregroundStyle(Theme.ink)
        .task {
            SoundPlayer.shared.preload()
            await backend.signIn()
        }
        .alert("Something went wrong", isPresented: errorShown) {
            Button("OK") { backend.errorMessage = nil }
        } message: {
            Text(backend.errorMessage ?? "")
        }
    }

    /// Each screen gets its own bold color.
    private var screenColor: Color {
        switch backend.room?.status {
        case nil: Theme.lime
        case .lobby: Theme.sunny
        case .playing, .finished: Theme.grape
        }
    }

    private var errorShown: Binding<Bool> {
        Binding(get: { backend.errorMessage != nil }, set: { if !$0 { backend.errorMessage = nil } })
    }
}
