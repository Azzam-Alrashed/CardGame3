import SwiftUI

struct PlayersStrip: View {
    var room: Room
    var round: PublicRound
    var me: String?
    /// The deal this phone is animating, if any.
    var clock: DealClock?
    /// How many of my cards I've turned over (shown before the server has it).
    var myFlips = 0
    /// The showdown this phone is staging, if any.
    var reveal: RevealClock?

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    ForEach(room.players) { player in
                        PlayerChip(room: room, round: round, uid: player.uid, isMe: player.uid == me,
                                   clock: clock, myFlips: myFlips, reveal: reveal)
                            .playerMenu(player.uid, in: room)
                            .id(player.uid)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
                .animation(.spring(duration: 0.4, bounce: 0.4), value: round)
            }
            .dealSpot(.strip)
            // Keep whoever is acting in view on a crowded table.
            .onChange(of: round.turnId ?? round.bossId, initial: true) { _, uid in
                guard let uid else { return }
                withAnimation(.smooth) { proxy.scrollTo(uid, anchor: .center) }
            }
        }
    }
}

private struct PlayerChip: View {
    var room: Room
    var round: PublicRound
    var uid: String
    var isMe: Bool
    var clock: DealClock?
    var myFlips: Int
    var reveal: RevealClock?

    var body: some View {
        let out = !room.isStillIn(uid)
        // Frames while cards land here, bots look at theirs, and the showdown plays.
        TimelineView(FramesUntil(end: max(clock?.peeksEnd ?? .distantPast, reveal?.end ?? .distantPast), fps: 30)) { context in
            // Points move at the showdown's verdict, not before.
            let settled = reveal.map { context.date >= $0.verdict } ?? true
            VStack(spacing: 4) {
                avatar(at: context.date)
                Text(isMe ? "You" : room.isAway(uid) ? "\(room.name(of: uid)) · away" : room.name(of: uid))
                    .font(Theme.body(13, .bold)).lineLimit(1).minimumScaleFactor(0.7)
                Text(out ? "Out" : room.points(of: uid, settled: settled).formatted()).font(Theme.body(12, .semibold)).opacity(0.7)
                    .contentTransition(.numericText())
                    .animation(.smooth(duration: 0.8), value: settled)
                status.transition(.scale.combined(with: .opacity))
            }
        }
        .frame(width: 72)
        .opacity(out ? 0.35 : 1)
    }

    private func avatar(at date: Date) -> some View {
        let isTurn = round.turnId == uid
        let dealing = clock.map { date < $0.end } ?? false
        let verdict = round.phase == .finished && (reveal.map { date >= $0.verdict } ?? true)
        return ZStack(alignment: .topTrailing) {
            Blob(
                color: Theme.color(forSeat: room.seat(of: uid)),
                size: 56,
                mood: mood(verdict: verdict),
                look: dealing ? lookAtDealer : .zero,
                hair: round.bossId == uid
            )
            .animation(.easeInOut(duration: 0.3), value: dealing)
            .background { if isTurn { TurnPulse() } }
            .overlay {
                if isTurn, let deadline = round.turnDeadlineDate {
                    TurnClockRing(deadline: deadline)
                } else {
                    Circle().strokeBorder(.white, lineWidth: isTurn ? 4 : 0)
                }
            }
            .scaleEffect(isTurn ? 1.08 : 1)
            .dealSpot(round.dealerId == uid ? .dealer : nil)
            .overlay(alignment: .bottomTrailing) {
                if room.isStillIn(uid), !round.withdrawn.contains(uid) {
                    MiniCards(
                        landed: clock?.landedCount(uid, at: date) ?? DealTimeline.handSize,
                        peeked: peeked(at: date),
                        faces: round.revealedHands[uid] ?? [],
                        faceUp: faceUp(at: date)
                    )
                        .dealSpot(.seat(uid))
                        .offset(x: 14, y: -2)
                        .transition(.offset(y: 14).combined(with: .opacity))
                }
            }
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
    }

    /// While the cards are dealt, everyone watches the dealer (who looks down at the deck).
    private var lookAtDealer: CGSize {
        if round.dealerId == uid { return CGSize(width: 0, height: 1) }
        return CGSize(width: room.seat(of: round.dealerId) < room.seat(of: uid) ? -1 : 1, height: 0.2)
    }

    /// How many cards this player has looked at. Bots (AI and away players) look on their own schedule.
    private func peeked(at date: Date) -> Int {
        let told = room.peeks?[uid] ?? 0
        if room.isAI(uid) || room.isAway(uid) {
            let sinceDeal = clock.map { date.timeIntervalSince($0.end) } ?? .infinity
            return max(told, DealTimeline.botPeeks(uid, round: round.roundNumber, sinceDealEnd: sinceDeal))
        }
        return isMe ? max(told, myFlips) : told
    }

    /// How many of this player's cards have turned over in the showdown.
    private func faceUp(at date: Date) -> Int {
        guard round.revealedHands[uid] != nil else { return 0 }
        guard let reveal else { return DealTimeline.handSize }
        let t = reveal.elapsed(at: date)
        return (0..<DealTimeline.handSize).filter { t >= reveal.timeline.flipTime(uid, card: $0) }.count
    }

    /// Once the result is in (after the showdown plays): the winner winks, the other revealed hands are sad.
    private func mood(verdict: Bool) -> Blob.Mood {
        if verdict, round.result?.winnerId == uid { return .wink }
        if verdict, round.result?.revealed.contains(uid) == true { return .sad }
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
            .minimumScaleFactor(0.7) // "Boss 3.5K" fits the chip
            .padding(.horizontal, 8).padding(.vertical, 3)
            .background(Capsule().fill(dark ? Theme.ink : .white))
            .foregroundStyle(dark ? .white : Theme.ink)
            .contentTransition(.numericText())
    }
}

/// Four small face-down cards in front of a player. The ones they've looked at stand up a little:
/// a tell everyone can read ("he bet 2,000 without even looking!").
private struct MiniCards: View {
    var landed: Int
    var peeked: Int
    /// Turned face up in a showdown, one card at a time.
    var faces: [Card] = []
    var faceUp = 0

    var body: some View {
        // 7 pt apart; DealLayer lands each card in the same spot.
        HStack(spacing: 5 - DealLayer.miniWidth) {
            ForEach(0..<DealTimeline.handSize, id: \.self) { i in
                // Unseen cards lie flat on the table; a card that's been looked at stands up.
                let standing = i < peeked || i < faceUp
                Group {
                    if i < faces.count {
                        FlipCard(card: faces[i], faceUp: i < faceUp, width: DealLayer.miniWidth)
                    } else {
                        CardBack(width: DealLayer.miniWidth)
                    }
                }
                .scaleEffect(x: 1, y: standing ? 1 : 0.6, anchor: .bottom)
                .rotationEffect(.degrees(Double(i) * 8 - 12 + (standing ? -8 : 0)), anchor: .bottom)
                .offset(y: standing ? -7 : 0)
                .opacity(i < landed ? 1 : 0)
            }
        }
        .animation(.spring(duration: 0.3, bounce: 0.55), value: peeked)
        .animation(.spring(duration: 0.35, bounce: 0.3), value: faceUp)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(peeked == 0 ? "Hasn't looked at their cards" : "Looked at \(peeked) of 4 cards")
    }
}

/// The turn's time left, as a ring that empties clockwise and turns pink for the last 10 seconds.
private struct TurnClockRing: View {
    var deadline: Date

    var body: some View {
        TimelineView(.animation(minimumInterval: 0.1)) { context in
            let left = max(0, deadline.timeIntervalSince(context.date))
            Circle()
                .trim(from: 0, to: min(1, left / Betting.turnSeconds))
                .stroke(left <= 10 ? Theme.hotPink : .white, style: StrokeStyle(lineWidth: 4, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .padding(2)
        }
    }
}

/// A white ring that keeps growing out of the blob whose turn it is.
private struct TurnPulse: View {
    var body: some View {
        Circle()
            .stroke(.white, lineWidth: 3)
            .phaseAnimator([false, true]) { ring, on in
                ring.scaleEffect(on ? 1.45 : 1).opacity(on ? 0 : 0.9)
            } animation: { on in on ? .easeOut(duration: 1.1) : nil }
    }
}
