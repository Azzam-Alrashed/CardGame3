import SwiftUI

/// Shows the right screen for where the player is: home, lobby, or game.
struct RootView: View {
    @Environment(Backend.self) private var backend

    var body: some View {
        ZStack {
            screenColor.ignoresSafeArea().animation(.easeInOut, value: backend.room?.status)
            switch backend.room?.status {
            case nil: HomeView()
            case .lobby: LobbyView()
            case .playing, .finished: GamePlaceholderView()
            }
        }
        .foregroundStyle(Theme.ink)
        .task { await backend.signIn() }
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

/// The controls at the bottom of a screen.
private struct BottomSheet<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        VStack(spacing: 14) { content }
            .padding(.horizontal, 24)
            .padding(.bottom, 12)
            .frame(maxWidth: .infinity)
    }
}

// MARK: - Home

struct HomeView: View {
    @Environment(Backend.self) private var backend
    @State private var code = ""
    @State private var busy = false
    @State private var bob = false

    private var hasName: Bool { !backend.playerName.trimmingCharacters(in: .whitespaces).isEmpty }
    private var ready: Bool { hasName && !busy && backend.uid != nil }

    var body: some View {
        @Bindable var backend = backend
        VStack(spacing: 0) {
            hero.frame(maxHeight: .infinity)

            VStack(spacing: 6) {
                Text("مداقش").font(Theme.wordmark(72))
                Text("Bet big. Make a deal. Reveal.").font(Theme.body(16, .medium)).opacity(0.7)
            }
            .padding(.bottom, 20)

            BottomSheet {
                PillField(placeholder: "Your name", text: $backend.playerName)
                    .textInputAutocapitalization(.words)

                Button("Create room") { act { await backend.createRoom() } }
                    .buttonStyle(PillButtonStyle())
                    .disabled(!ready)

                HStack(spacing: 12) {
                    PillField(placeholder: "Room code", text: $code)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .onChange(of: code) { code = String(code.uppercased().prefix(4)) }
                    Button("Join") { act { await backend.joinRoom(code: code) } }
                        .buttonStyle(PillButtonStyle(primary: false))
                        .frame(width: 110)
                        .disabled(!ready || code.count != 4)
                }
            }
        }
        .overlay { if busy { ProgressView().controlSize(.large) } }
        .onAppear { bob = true }
    }

    /// A fanned hand of four Aces, with blob friends peeking around it.
    private var hero: some View {
        GeometryReader { geo in
            let w = geo.size.width, h = geo.size.height
            let cardW = min(w * 0.3, h * 0.42)
            ZStack {
                Blob(color: Theme.blobColors[2], size: w * 0.34, mood: .happy, look: CGSize(width: 1, height: 0.5), hair: true)
                    .position(x: w * 0.13, y: h * 0.3)
                Blob(color: Theme.blobColors[4], size: w * 0.3, mood: .wink, look: CGSize(width: -1, height: 0.5))
                    .position(x: w * 0.88, y: h * 0.26)
                ForEach(Array(["C", "D", "H", "S"].enumerated()), id: \.offset) { i, suit in
                    PlayingCard(rank: 14, suit: suit, width: cardW)
                        .rotationEffect(.degrees(Double(i - 1) * 12 - 6), anchor: .bottom)
                        .offset(x: CGFloat(i) * cardW * 0.2 - cardW * 0.3)
                        .position(x: w * 0.5, y: h * 0.52)
                }
                Blob(color: Theme.blobColors[3], size: w * 0.26, mood: .surprised, look: CGSize(width: 0.5, height: -1))
                    .position(x: w * 0.22, y: h * 0.86)
                Blob(color: Theme.blobColors[1], size: w * 0.22, mood: .happy, look: CGSize(width: -1, height: -1))
                    .position(x: w * 0.8, y: h * 0.84)
                SpeechBubble(text: "Deal?")
                    .position(x: w * 0.74, y: h * 0.08)
            }
            .offset(y: bob ? -4 : 4)
            .animation(.easeInOut(duration: 2).repeatForever(), value: bob)
        }
        .clipped()
    }

    private func act(_ work: @escaping () async -> Void) {
        busy = true
        Task {
            await work()
            busy = false
        }
    }
}

// MARK: - Lobby

struct LobbyView: View {
    @Environment(Backend.self) private var backend

    var body: some View {
        let room = backend.room!
        VStack(spacing: 0) {
            VStack(spacing: 4) {
                Text("Share this code").font(Theme.body(15, .medium)).opacity(0.6)
                Text(room.code)
                    .font(Theme.wordmark(72))
                    .kerning(6)
                    .textSelection(.enabled)
                Text("\(room.players.count) / \(TableSize.maxPlayers) players")
                    .font(Theme.body(15, .medium)).opacity(0.6)
            }
            .padding(.top, 16)

            ScrollView {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: 12)], spacing: 18) {
                    ForEach(Array(room.players.enumerated()), id: \.element.id) { index, player in
                        PlayerBadge(
                            name: player.name,
                            color: Theme.color(forSeat: index),
                            isHost: player.uid == room.hostId,
                            isYou: player.uid == backend.uid
                        )
                    }
                }
                .padding(24)
            }
            .frame(maxHeight: .infinity)

            BottomSheet {
                if backend.isHost {
                    Button(startLabel(room)) { Task { await backend.startGame() } }
                        .buttonStyle(PillButtonStyle())
                        .disabled(room.players.count < TableSize.minPlayers)
                } else {
                    Text("Waiting for the host to start…").font(Theme.body(16, .medium)).opacity(0.6)
                        .padding(.vertical, 18)
                }
                Button("Leave room") { Task { await backend.leaveRoom() } }
                    .buttonStyle(PillButtonStyle(primary: false))
            }
        }
    }

    private func startLabel(_ room: Room) -> String {
        let missing = TableSize.minPlayers - room.players.count
        return missing > 0 ? "Need \(missing) more to start" : "Start game"
    }
}

private struct PlayerBadge: View {
    var name: String
    var color: Color
    var isHost: Bool
    var isYou: Bool

    var body: some View {
        VStack(spacing: 6) {
            Blob(color: color, size: 72, mood: isYou ? .wink : .happy, hair: isHost)
            Text(name).font(Theme.body(15, .bold)).lineLimit(1)
            if isHost || isYou {
                Text(isHost ? (isYou ? "Host · You" : "Host") : "You")
                    .font(Theme.body(12, .bold))
                    .padding(.horizontal, 8).padding(.vertical, 3)
                    .background(Capsule().fill(isHost ? Theme.ink : .white))
                    .foregroundStyle(isHost ? .white : Theme.ink)
            }
        }
    }
}

// MARK: - Game (next step)

struct GamePlaceholderView: View {
    var body: some View {
        VStack(spacing: 20) {
            Blob(color: Theme.blobColors[3], size: 140, mood: .surprised, hair: true)
            Text("The game has started!").font(Theme.body(28, .heavy))
            Text("The table screen is the next step.").font(Theme.body(16, .medium)).opacity(0.6)
        }
    }
}
