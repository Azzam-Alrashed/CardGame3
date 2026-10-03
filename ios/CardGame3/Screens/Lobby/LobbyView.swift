import SwiftUI

struct LobbyView: View {
    @Environment(Backend.self) private var backend
    /// AI player the host tapped, waiting for "Remove" to be confirmed.
    @State private var removing: Room.Player?
    @State private var copied = false

    var body: some View {
        // The room can disappear (leaving) a frame before the screen switches.
        if let room = backend.room {
        AdaptiveSplit {
          VStack(spacing: 0) {
            VStack(spacing: 4) {
                Text(copied ? "Copied!" : "Tap to copy the code")
                    .font(Theme.body(15, .medium)).opacity(0.6)
                    .contentTransition(.opacity)
                Button {
                    UIPasteboard.general.string = room.code
                    withAnimation(.snappy) { copied = true }
                    Task {
                        try? await Task.sleep(for: .seconds(1.5))
                        withAnimation(.snappy) { copied = false }
                    }
                } label: {
                    Text(room.code)
                        .font(Theme.wordmark(72))
                        .kerning(6)
                }
                .buttonStyle(.plain)
                .sensoryFeedback(.success, trigger: copied) { _, now in now }
                .scaleEffect(copied ? 1.06 : 1)
                HStack(spacing: 10) {
                    Text("\(room.players.count) / \(TableSize.maxPlayers) players")
                        .font(Theme.body(15, .medium)).opacity(0.6)
                        .contentTransition(.numericText())
                    ShareLink(item: "Join my مداقش table! Room code: \(room.code)") {
                        Label("Invite", systemImage: "square.and.arrow.up")
                            .font(Theme.body(14, .bold))
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .background(Capsule().fill(.white))
                    }
                    .foregroundStyle(Theme.ink)
                }
            }
            .padding(.top, 16)

            ScrollView {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: 12)], spacing: 18) {
                    ForEach(Array(room.players.enumerated()), id: \.element.id) { index, player in
                        PlayerBadge(
                            name: player.name,
                            color: Theme.color(forSeat: index),
                            isHost: player.uid == room.hostId,
                            isYou: player.uid == backend.uid,
                            isAI: room.isAI(player.uid),
                            onRemove: backend.isHost && room.isAI(player.uid) ? { removing = player } : nil
                        )
                        .transition(.scale(scale: 0.3).combined(with: .opacity))
                    }
                }
                .padding(24)
                .animation(.spring(duration: 0.5, bounce: 0.45), value: room.players)
            }
            .sensoryFeedback(.impact(weight: .light), trigger: room.players.count)
            .frame(maxHeight: .infinity)
          }
          .confirmationDialog(
              "Remove \(removing?.name ?? "")?",
              isPresented: Binding(get: { removing != nil }, set: { if !$0 { removing = nil } }),
              titleVisibility: .visible
          ) {
              Button("Remove", role: .destructive) {
                  if let id = removing?.uid { Task { await backend.removeAiPlayer(id) } }
              }
          }
        } side: {
          VStack {
            Spacer(minLength: 0)
            BottomSheet {
                if backend.isHost {
                    AsyncButton("Add AI player 🤖") { await backend.addAiPlayer() }
                        .buttonStyle(PillButtonStyle(primary: false))
                        .disabled(room.players.count >= TableSize.maxPlayers)
                    AsyncButton(startLabel(room)) { await backend.startGame() }
                        .buttonStyle(PillButtonStyle())
                        .disabled(room.players.count < TableSize.minPlayers)
                        .animation(.snappy, value: room.players.count)
                } else {
                    HStack(spacing: 8) {
                        Text("Waiting for the host to start")
                        Image(systemName: "ellipsis").symbolEffect(.variableColor.iterative, options: .repeating)
                    }
                    .font(Theme.body(16, .semibold)).opacity(0.6)
                    .padding(.vertical, 18)
                }
                AsyncButton("Leave room") { await backend.leaveRoom() }
                    .buttonStyle(PillButtonStyle(primary: false))
            }
            .blocksWhileBusy()
          }
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
    var isAI: Bool
    /// Set for AI players the host may remove.
    var onRemove: (() -> Void)?

    var body: some View {
        VStack(spacing: 6) {
            Blob(color: color, size: 72, mood: isYou ? .wink : .happy, hair: isHost)
                .overlay(alignment: .topTrailing) {
                    if let onRemove {
                        Button(action: onRemove) { Image(systemName: "xmark") }
                            .buttonStyle(CircleButtonStyle(size: 26))
                            .accessibilityLabel("Remove \(name)")
                            .offset(x: 4, y: -4)
                    }
                }
            Text(name).font(Theme.body(15, .bold)).lineLimit(1).minimumScaleFactor(0.7)
            if isHost || isYou || isAI {
                Text(isAI ? "AI" : isHost ? (isYou ? "Host · You" : "Host") : "You")
                    .font(Theme.body(12, .bold))
                    .padding(.horizontal, 8).padding(.vertical, 3)
                    .background(Capsule().fill(isHost ? Theme.ink : .white))
                    .foregroundStyle(isHost ? .white : Theme.ink)
            }
        }
    }
}
