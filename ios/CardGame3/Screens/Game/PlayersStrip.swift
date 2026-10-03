import SwiftUI

struct PlayersStrip: View {
    var room: Room
    var round: PublicRound
    var me: String?

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 14) {
                ForEach(room.players) { player in
                    PlayerChip(room: room, round: round, uid: player.uid, isMe: player.uid == me)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
        }
    }
}

private struct PlayerChip: View {
    var room: Room
    var round: PublicRound
    var uid: String
    var isMe: Bool

    var body: some View {
        let out = !room.isStillIn(uid)
        let isTurn = round.turnId == uid
        VStack(spacing: 4) {
            ZStack(alignment: .topTrailing) {
                Blob(
                    color: Theme.color(forSeat: room.seat(of: uid)),
                    size: 56,
                    mood: mood,
                    hair: round.bossId == uid
                )
                .overlay(Circle().strokeBorder(.white, lineWidth: isTurn ? 4 : 0))
                if room.isAway(uid) || room.isAI(uid) {
                    Text("🤖").font(.system(size: 16))
                        .frame(width: 26, height: 26)
                        .background(Circle().fill(.white))
                        .offset(x: -36, y: 34)
                }
                if round.dealerId == uid {
                    Text("D").font(Theme.body(12, .black))
                        .frame(width: 22, height: 22)
                        .background(Circle().fill(.white))
                        .offset(x: 4, y: -4)
                }
            }
            Text(isMe ? "You" : room.isAway(uid) ? "\(room.name(of: uid)) · away" : room.name(of: uid))
                .font(Theme.body(13, .bold)).lineLimit(1).minimumScaleFactor(0.7)
            Text(out ? "Out" : "\(room.points(of: uid))").font(Theme.body(12, .semibold)).opacity(0.7)
            status
        }
        .frame(width: 72)
        .opacity(out ? 0.35 : 1)
    }

    private var mood: Blob.Mood {
        if round.result?.winnerId == uid { return .wink }
        if round.withdrawn.contains(uid) { return .sleepy }
        if round.bossId == uid { return .surprised }
        return .happy
    }

    @ViewBuilder private var status: some View {
        if round.bossId == uid {
            tag("Boss \(short(round.bets[uid] ?? 0))", dark: true)
        } else if let deal = round.deals[uid] {
            tag("Deal \(short(deal))", dark: false)
        } else if let offer = round.offers[uid] {
            tag("Asks \(short(offer))", dark: false)
        } else if let bet = round.bets[uid] {
            tag("Bet \(short(bet))", dark: false)
        } else if round.withdrawn.contains(uid) {
            tag("Out", dark: false).opacity(0.6)
        } else {
            tag(" ", dark: false).hidden()
        }
    }

    private func tag(_ text: String, dark: Bool) -> some View {
        Text(text)
            .font(Theme.body(11, .heavy))
            .lineLimit(1)
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(Capsule().fill(dark ? Theme.ink : .white))
            .foregroundStyle(dark ? .white : Theme.ink)
    }
}
