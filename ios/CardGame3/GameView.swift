import SwiftUI

/// The table: everyone's status on top, the round's state in the middle, your cards and actions below.
struct GameView: View {
    @Environment(Backend.self) private var backend

    var body: some View {
        let room = backend.room!
        if let over = room.gameOver {
            GameOverView(room: room, over: over)
        } else if let round = room.round {
            VStack(spacing: 0) {
                header(room, round)
                PlayersStrip(room: room, round: round, me: backend.uid)
                Spacer(minLength: 8)
                Banner(room: room, round: round, me: backend.uid)
                Spacer(minLength: 8)
                MyHand(cards: backend.myCards, revealed: round.revealedHands[backend.uid ?? ""] != nil)
                ActionPanel(room: room, round: round)
            }
        }
    }

    private func header(_ room: Room, _ round: PublicRound) -> some View {
        HStack {
            Text("Round \(round.roundNumber)").font(Theme.body(17, .heavy))
            Spacer()
            if let uid = backend.uid {
                Label("\(room.points(of: uid))", systemImage: "circle.hexagongrid.fill")
                    .font(Theme.body(17, .heavy))
                    .padding(.horizontal, 14).padding(.vertical, 8)
                    .background(Capsule().fill(.white))
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
    }
}

// MARK: - Players

private struct PlayersStrip: View {
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
                if round.dealerId == uid {
                    Text("D").font(Theme.body(12, .black))
                        .frame(width: 22, height: 22)
                        .background(Circle().fill(.white))
                        .offset(x: 4, y: -4)
                }
            }
            Text(isMe ? "You" : room.name(of: uid)).font(Theme.body(13, .bold)).lineLimit(1)
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

// MARK: - Banner

/// Big headline for what is happening right now.
private struct Banner: View {
    @Environment(Backend.self) private var backend
    var room: Room
    var round: PublicRound
    var me: String?

    var body: some View {
        VStack(spacing: 8) {
            switch round.phase {
            case .betting:
                Text(round.turnId == me ? "Your turn" : "\(room.name(of: round.turnId ?? ""))'s turn")
                    .font(Theme.wordmark(36))
                Text(round.highestBet > 0 ? "Highest bet \(round.highestBet)" : "No bets yet")
                    .font(Theme.body(16, .medium)).opacity(0.7)
            case .deals:
                Text("Deal time").font(Theme.wordmark(36))
                if let deadline = round.deadlineDate {
                    Countdown(deadline: deadline) { Task { await backend.timerRanOut() } }
                }
                Text("\(round.bossId == me ? "You're" : "\(room.name(of: round.bossId ?? "")) is") the boss · \(round.dealRoom.formatted()) left for deals")
                    .font(Theme.body(15, .medium)).opacity(0.7)
                    .multilineTextAlignment(.center)
            case .finished:
                ResultSummary(room: room, round: round, me: me)
            }
        }
        .padding(.horizontal, 20)
    }
}

/// Ticks every second; calls `onZero` once when time runs out.
private struct Countdown: View {
    var deadline: Date
    var onZero: () -> Void
    @State private var fired = false

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let left = max(0, Int(deadline.timeIntervalSince(context.date).rounded(.up)))
            Text(String(format: "%d:%02d", left / 60, left % 60))
                .font(.system(size: 44, weight: .black, design: .rounded).monospacedDigit())
                .padding(.horizontal, 22).padding(.vertical, 6)
                .background(Capsule().fill(left <= 10 ? Theme.hotPink : .white))
                .onChange(of: left == 0, initial: true) { _, isZero in
                    if isZero && !fired {
                        fired = true
                        onZero()
                    }
                }
        }
    }
}

private struct ResultSummary: View {
    var room: Room
    var round: PublicRound
    var me: String?

    var body: some View {
        let result = round.result!
        VStack(spacing: 10) {
            Text(headline(result)).font(Theme.wordmark(32)).multilineTextAlignment(.center)
            ForEach(result.revealed, id: \.self) { uid in
                HStack(spacing: 4) {
                    Text(uid == me ? "You" : room.name(of: uid))
                        .font(Theme.body(14, .bold)).frame(width: 64, alignment: .leading).lineLimit(1)
                    ForEach(round.revealedHands[uid] ?? [], id: \.self) { card in
                        PlayingCard(rank: card.rank, suit: card.suit, width: 34)
                    }
                    Spacer()
                    delta(result.deltas[uid] ?? 0)
                }
                .padding(8)
                .background(RoundedRectangle(cornerRadius: 16).fill(.white.opacity(uid == result.winnerId ? 0.9 : 0.4)))
            }
            let others = result.deltas.filter { !result.revealed.contains($0.key) }
            ForEach(others.sorted { $0.key < $1.key }, id: \.key) { uid, amount in
                HStack {
                    Text(uid == me ? "You" : room.name(of: uid)).font(Theme.body(14, .bold))
                    Spacer()
                    delta(amount)
                }
                .padding(.horizontal, 12)
            }
        }
    }

    private func headline(_ result: RoundResult) -> String {
        switch result.outcome {
        case .redeal: return "Everyone folded.\nRedeal!"
        default:
            let who = result.winnerId == me ? "You win" : "\(room.name(of: result.winnerId ?? "")) wins"
            return "\(who) \((result.deltas[result.winnerId ?? ""] ?? 0).formatted())!"
        }
    }

    private func delta(_ amount: Int) -> some View {
        Text(amount > 0 ? "+\(amount)" : "\(amount)")
            .font(Theme.body(15, .heavy))
            .foregroundStyle(amount >= 0 ? Theme.ink : Theme.hotPink)
    }
}

// MARK: - My hand

private struct MyHand: View {
    var cards: [Card]
    var revealed: Bool

    var body: some View {
        HStack(spacing: -24) {
            ForEach(Array(cards.enumerated()), id: \.element) { i, card in
                PlayingCard(rank: card.rank, suit: card.suit, width: 84)
                    .rotationEffect(.degrees(Double(i) * 6 - 9), anchor: .bottom)
                    .offset(y: abs(Double(i) - 1.5) * 6)
            }
        }
        .padding(.bottom, 16)
        .animation(.spring, value: cards)
    }
}

// MARK: - Actions

private struct ActionPanel: View {
    @Environment(Backend.self) private var backend
    var room: Room
    var round: PublicRound
    @State private var amount = Betting.minBet

    private var me: String { backend.uid ?? "" }
    private var myPoints: Int { room.points(of: me) }

    var body: some View {
        VStack(spacing: 12) {
            switch round.phase {
            case .betting: betting
            case .deals: deals
            case .finished:
                Button("Next round") { Task { await backend.nextRound() } }
                    .buttonStyle(PillButtonStyle())
            }
        }
        .padding(20)
        .background(
            UnevenRoundedRectangle(topLeadingRadius: 32, topTrailingRadius: 32)
                .fill(.white.opacity(0.25))
                .ignoresSafeArea(edges: .bottom)
        )
    }

    // Betting

    /// Smallest bet that beats the highest one.
    private var minBet: Int { max(Betting.minBet, round.highestBet + Betting.step) }

    @ViewBuilder private var betting: some View {
        if round.turnId == me {
            if minBet <= myPoints {
                AmountPicker(amount: $amount, range: minBet...roundDown(myPoints))
                Button("Bet \(amount)") { Task { await backend.bet(amount) } }
                    .buttonStyle(PillButtonStyle())
            }
            HStack(spacing: 12) {
                Button("All in \(myPoints)") { Task { await backend.bet(myPoints) } }
                    .buttonStyle(PillButtonStyle(primary: false))
                Button("Withdraw") { Task { await backend.withdraw() } }
                    .buttonStyle(PillButtonStyle(primary: false))
            }
            .onAppear { amount = minBet }
            .onChange(of: round.highestBet) { amount = minBet }
        } else if round.bets[me] != nil {
            waiting("You're in with \(round.bets[me]!). Waiting for the others…")
        } else if round.withdrawn.contains(me) {
            waiting("You sat this one out.")
        } else {
            waiting("Look at your cards… your turn is coming.")
        }
    }

    // Deals

    @ViewBuilder private var deals: some View {
        if round.bossId == me {
            if round.offers.isEmpty {
                waiting("You're the boss. Wait for offers, or reveal now.")
            }
            ForEach(round.offers.sorted { $0.key < $1.key }, id: \.key) { uid, offer in
                HStack {
                    Text("\(room.name(of: uid)) asks \(offer)").font(Theme.body(16, .bold))
                    Spacer()
                    Button("No") { Task { await backend.answerOffer(from: uid, accept: false) } }
                        .buttonStyle(PillButtonStyle(primary: false)).frame(width: 70)
                    Button("Deal") { Task { await backend.answerOffer(from: uid, accept: true) } }
                        .buttonStyle(PillButtonStyle()).frame(width: 84)
                        .disabled(offer > round.dealRoom)
                }
            }
            Button("Reveal!") { Task { await backend.reveal() } }
                .buttonStyle(PillButtonStyle())
        } else if let deal = round.deals[me] {
            waiting("Deal locked: you get \(deal) if \(room.name(of: round.bossId ?? "")) wins.")
        } else if round.bets[me] != nil {
            if round.dealRoom >= Betting.step {
                AmountPicker(amount: $amount, range: Betting.step...roundDown(round.dealRoom))
                Button(round.offers[me] == nil ? "Offer to withdraw for \(amount)" : "Change offer to \(amount)") {
                    Task { await backend.makeOffer(amount) }
                }
                .buttonStyle(PillButtonStyle())
                .onAppear { amount = min(max(amount, Betting.step), roundDown(round.dealRoom)) }
            } else {
                waiting("No room left for deals. Get ready to reveal!")
            }
        } else {
            waiting("Watching the deals…")
        }
    }

    private func roundDown(_ n: Int) -> Int { max(Betting.step, n / Betting.step * Betting.step) }

    private func waiting(_ text: String) -> some View {
        Text(text).font(Theme.body(16, .semibold)).multilineTextAlignment(.center)
            .frame(maxWidth: .infinity).padding(.vertical, 10)
    }
}

/// − amount + in steps of 500.
private struct AmountPicker: View {
    @Binding var amount: Int
    var range: ClosedRange<Int>

    var body: some View {
        HStack {
            stepButton("minus") { amount = max(range.lowerBound, amount - Betting.step) }
                .disabled(amount <= range.lowerBound)
            Text("\(amount)")
                .font(.system(size: 34, weight: .black, design: .rounded).monospacedDigit())
                .frame(maxWidth: .infinity)
                .contentTransition(.numericText())
            stepButton("plus") { amount = min(range.upperBound, amount + Betting.step) }
                .disabled(amount >= range.upperBound)
        }
        .onAppear { amount = min(max(amount, range.lowerBound), range.upperBound) }
        .animation(.snappy, value: amount)
    }

    private func stepButton(_ icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 22, weight: .heavy))
                .frame(width: 56, height: 56)
                .background(Circle().fill(.white))
        }
        .foregroundStyle(Theme.ink)
    }
}

// MARK: - Game over

private struct GameOverView: View {
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

extension Seat: Identifiable {}

/// 500 → "500", 1500 → "1.5K", 12000 → "12K".
func short(_ n: Int) -> String {
    n < 1000 ? "\(n)" : (Double(n) / 1000).formatted(.number.precision(.fractionLength(0...1))) + "K"
}
