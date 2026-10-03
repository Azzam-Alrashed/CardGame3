import SwiftUI

struct LobbyView: View {
    @Environment(Backend.self) private var backend
    /// AI player the host tapped, waiting for "Remove" to be confirmed.
    @State private var removing: Room.Player?

    var body: some View {
        // The room can disappear (leaving) a frame before the screen switches.
        if let room = backend.room {
        AdaptiveSplit {
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
                            isYou: player.uid == backend.uid,
                            isAI: room.isAI(player.uid)
                        )
                        .onTapGesture {
                            if backend.isHost && room.isAI(player.uid) { removing = player }
                        }
                    }
                }
                .padding(24)
            }
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
                    Button("Add AI player 🤖") { Task { await backend.addAiPlayer() } }
                        .buttonStyle(PillButtonStyle(primary: false))
                        .disabled(room.players.count >= TableSize.maxPlayers)
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

    var body: some View {
        VStack(spacing: 6) {
            Blob(color: color, size: 72, mood: isYou ? .wink : .happy, hair: isHost)
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
