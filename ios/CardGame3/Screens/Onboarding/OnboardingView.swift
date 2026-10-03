import SwiftUI

/// First-launch intro: the game in four swipeable pages, then the player's name.
struct OnboardingView: View {
    @Environment(Backend.self) private var backend
    var onFinish: () -> Void

    @State private var page = 0
    @State private var bob = false
    @FocusState private var nameFocused: Bool

    private let colors = [Theme.lime, Theme.sunny, Theme.hotPink, Theme.grape, Theme.lime]
    private var lastPage: Int { colors.count - 1 }
    private var hasName: Bool { !backend.playerName.trimmingCharacters(in: .whitespaces).isEmpty }

    var body: some View {
        TabView(selection: $page) {
            welcome.tag(0)
            bet.tag(1)
            deal.tag(2)
            reveal.tag(3)
            name.tag(4)
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
        .background(colors[page].ignoresSafeArea().animation(.easeInOut, value: page))
        .overlay(alignment: .topTrailing) {
            if page < lastPage {
                Button("Skip", action: onFinish)
                    .font(Theme.body(16, .bold))
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
            }
        }
        .onAppear { bob = true }
        .onChange(of: page) { nameFocused = page == lastPage }
    }

    // MARK: Pages

    private var welcome: some View {
        IntroPage(title: "مداقش", titleFont: Theme.wordmark(64),
                  text: "A betting card game for 4 to 13 friends. Everyone starts with 5,000 points. Hit zero and you're out.") {
            GeometryReader { geo in
                let w = geo.size.width, h = geo.size.height, r = min(w, h) * 0.36
                ZStack {
                    VStack(spacing: 0) {
                        Text("5,000").font(Theme.wordmark(min(w, h) * 0.16))
                        Text("points each").font(Theme.body(15, .medium)).opacity(0.7)
                    }
                    ForEach(0..<6) { i in
                        let angle = Double(i) / 6 * 2 * .pi - .pi / 2
                        Blob(color: Theme.color(forSeat: i), size: r * 0.55,
                             mood: [.happy, .wink, .surprised, .happy, .sleepy, .wink][i],
                             look: CGSize(width: -cos(angle), height: -sin(angle)), hair: i == 3)
                            .position(x: w / 2 + cos(angle) * r, y: h / 2 + sin(angle) * r)
                    }
                    SpeechBubble(text: "Hi!").position(x: w / 2 + r * 0.75, y: h / 2 - r * 1.1)
                }
            }
        } controls: { nextControls }
    }

    private var bet: some View {
        IntroPage(title: "Bet or sit out",
                  text: "You get 4 cards. To enter, bet more than the highest bet so far, or go all in. Sitting out is free. The highest bet makes you the boss.") {
            GeometryReader { geo in
                let w = geo.size.width, h = geo.size.height, cardW = min(w * 0.22, h * 0.3)
                ZStack {
                    hand([(13, "S"), (13, "H"), (12, "D"), (14, "C")], width: cardW)
                        .position(x: w / 2, y: h * 0.6)
                    Blob(color: Theme.blobColors[1], size: cardW * 0.9, mood: .happy, look: CGSize(width: 1, height: 0.5))
                        .position(x: w * 0.16, y: h * 0.3)
                    SpeechBubble(text: "1,500!").position(x: w * 0.3, y: h * 0.1)
                    Blob(color: Theme.blobColors[6], size: cardW * 0.75, mood: .sleepy)
                        .position(x: w * 0.85, y: h * 0.28)
                    SpeechBubble(text: "I'm out").position(x: w * 0.76, y: h * 0.08)
                }
            }
        } controls: { nextControls }
    }

    private var deal: some View {
        IntroPage(title: "Make a deal",
                  text: "Scared of the boss? Offer to step aside for a cut of the boss's winnings. The boss accepts or rejects. Bluffing is allowed.") {
            GeometryReader { geo in
                let w = geo.size.width, h = geo.size.height, s = min(w, h)
                ZStack {
                    Blob(color: Theme.blobColors[3], size: s * 0.42, mood: .wink, look: CGSize(width: -0.6, height: 0.3), hair: true)
                        .position(x: w * 0.66, y: h * 0.58)
                    Text("BOSS").font(Theme.body(14, .black)).kerning(2)
                        .padding(.horizontal, 10).padding(.vertical, 4)
                        .background(Capsule().fill(Theme.ink)).foregroundStyle(.white)
                        .position(x: w * 0.66, y: h * 0.58 + s * 0.26)
                    Blob(color: Theme.blobColors[2], size: s * 0.26, mood: .surprised, look: CGSize(width: 1, height: -0.3))
                        .position(x: w * 0.22, y: h * 0.62)
                    SpeechBubble(text: "Deal for 500?").position(x: w * 0.3, y: h * 0.3)
                    SpeechBubble(text: "Deal!").position(x: w * 0.78, y: h * 0.14)
                }
            }
        } controls: { nextControls }
    }

    private var reveal: some View {
        IntroPage(title: "Reveal!",
                  text: "Everyone without a deal shows their hand with the boss. Beat the boss and you win the boss's bet. Lose and you lose your own.") {
            HandRanking()
                .padding(.horizontal, 24)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } controls: { nextControls }
    }

    private var name: some View {
        @Bindable var backend = backend
        return IntroPage(title: "What should we call you?",
                         text: "Your friends will see this name at the table.") {
            GeometryReader { geo in
                let w = geo.size.width, h = geo.size.height, s = min(w, h)
                ZStack {
                    Blob(color: Theme.blobColors[4], size: s * 0.5, mood: .happy, look: CGSize(width: 0, height: 1))
                        .position(x: w / 2, y: h * 0.58)
                    SpeechBubble(text: hasName ? "Hi, \(backend.playerName)!" : "Who are you?")
                        .position(x: w / 2 + s * 0.12, y: h * 0.58 - s * 0.38)
                }
            }
        } controls: {
            PillField(placeholder: "Your name", text: $backend.playerName)
                .textInputAutocapitalization(.words)
                .submitLabel(.go)
                .focused($nameFocused)
                .onSubmit { if hasName { onFinish() } }
            Button("Let's play", action: onFinish)
                .buttonStyle(PillButtonStyle())
                .disabled(!hasName)
        }
    }

    // MARK: Pieces

    @ViewBuilder private var nextControls: some View {
        PageDots(count: colors.count, current: page)
        Button("Next") { withAnimation { page += 1 } }
            .buttonStyle(PillButtonStyle())
    }

    private func hand(_ cards: [(Int, String)], width: CGFloat) -> some View {
        ZStack {
            ForEach(Array(cards.enumerated()), id: \.offset) { i, card in
                PlayingCard(rank: card.0, suit: card.1, width: width)
                    .rotationEffect(.degrees(Double(i) * 10 - 15), anchor: .bottom)
                    .offset(x: (CGFloat(i) - 1.5) * width * 0.35)
            }
        }
        .offset(y: bob ? -4 : 4)
        .animation(.easeInOut(duration: 2).repeatForever(), value: bob)
    }
}

/// One intro page: art on top (or left in landscape), words and buttons below.
private struct IntroPage<Art: View, Controls: View>: View {
    var title: String
    var titleFont = Theme.wordmark(36)
    var text: String
    @ViewBuilder var art: Art
    @ViewBuilder var controls: Controls

    var body: some View {
        AdaptiveSplit {
            art.padding(.top, 48)
        } side: {
            VStack(spacing: 0) {
                Spacer(minLength: 0)
                VStack(spacing: 10) {
                    Text(title).font(titleFont).multilineTextAlignment(.center)
                    Text(text).font(Theme.body(17, .medium)).opacity(0.75)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.horizontal, 28)
                .padding(.bottom, 24)
                BottomSheet { controls }
            }
        }
    }
}

private struct PageDots: View {
    var count: Int
    var current: Int

    var body: some View {
        HStack(spacing: 8) {
            ForEach(0..<count, id: \.self) { i in
                Capsule()
                    .fill(Theme.ink.opacity(i == current ? 1 : 0.25))
                    .frame(width: i == current ? 22 : 8, height: 8)
            }
        }
        .animation(.spring(duration: 0.3), value: current)
        .padding(.bottom, 4)
    }
}

/// The five hands from best to worst, with a small example of each.
struct HandRanking: View {
    private let rows: [(String, [(Int, String)])] = [
        ("Four of a kind", [(14, "S"), (14, "H"), (14, "D"), (14, "C")]),
        ("Three of a kind", [(13, "S"), (13, "H"), (13, "C"), (12, "D")]),
        ("Two pairs", [(12, "S"), (12, "H"), (11, "D"), (11, "C")]),
        ("One pair", [(14, "H"), (14, "C"), (13, "S"), (11, "D")]),
        ("High card", [(14, "S"), (13, "D"), (12, "C"), (11, "H")]),
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(Array(rows.enumerated()), id: \.offset) { i, row in
                HStack(spacing: 12) {
                    Text("\(i + 1)").font(Theme.body(15, .black)).frame(width: 18)
                    Text(row.0).font(Theme.body(16, .bold))
                    Spacer(minLength: 8)
                    HStack(spacing: -12) {
                        ForEach(Array(row.1.enumerated()), id: \.offset) { _, card in
                            PlayingCard(rank: card.0, suit: card.1, width: 30)
                        }
                    }
                }
            }
            Text("No ties: suits break them, ♠ > ♥ > ♦ > ♣")
                .font(Theme.body(14, .medium)).opacity(0.7)
                .padding(.top, 4)
        }
        .frame(maxWidth: 420)
    }
}
