import SwiftUI

struct GameOverView: View {
    @Environment(Backend.self) private var backend
    var room: Room
    var over: GameOver

    var body: some View {
        VStack(spacing: 18) {
            Spacer()
            Blob(color: Theme.color(forSeat: room.seat(of: over.winnerId)), size: 150, mood: .wink, hair: true)
            Text(over.winnerId == backend.uid ? "You win!" : "\(room.name(of: over.winnerId)) wins!")
                .font(Theme.wordmark(44))
            VStack(spacing: 8) {
                ForEach(Array(over.standings.enumerated()), id: \.element.id) { place, seat in
                    HStack {
                        Text("\(place + 1).").font(Theme.body(17, .heavy)).frame(width: 30)
                        Text(room.name(of: seat.id)).font(Theme.body(17, .bold))
                        Spacer()
                        Text("\(seat.points)").font(Theme.body(17, .heavy))
                    }
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(Capsule().fill(.white.opacity(place == 0 ? 0.9 : 0.4)))
                }
            }
            .padding(.horizontal, 24)
            Spacer()
            Button("Back home") { backend.goHome() }
                .buttonStyle(PillButtonStyle())
                .padding(24)
        }
    }
}
