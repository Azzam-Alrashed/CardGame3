import SwiftUI

struct HomeView: View {
    @Environment(Backend.self) private var backend
    @State private var code = ""
    @State private var busy = false
    @State private var bob = false
    @State private var showRules = false
    @AppStorage("seenOnboarding") private var seenOnboarding = true

    private var hasName: Bool { !backend.playerName.trimmingCharacters(in: .whitespaces).isEmpty }
    private var ready: Bool { hasName && !busy && backend.uid != nil }

    var body: some View {
        @Bindable var backend = backend
        AdaptiveSplit {
            hero
        } side: {
            VStack(spacing: 0) {
            Spacer(minLength: 0)
            VStack(spacing: 6) {
                Text("مداقش").font(Theme.wordmark(72))
                Text("Bet big. Make a deal. Reveal.").font(Theme.body(16, .medium)).opacity(0.7)
            }
            .padding(.bottom, 20)

            BottomSheet {
                if let away = backend.awayRoomCode {
                    Button("Back to game \(away)") { act { await backend.returnToGame() } }
                        .buttonStyle(PillButtonStyle())
                        .disabled(busy || backend.uid == nil)
                }
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
        }
        .overlay { if busy { ProgressView().controlSize(.large) } }
        .overlay(alignment: .topTrailing) {
            Button { showRules = true } label: {
                Image(systemName: "questionmark")
                    .font(.system(size: 18, weight: .heavy))
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(.white))
                    .shadow(color: .black.opacity(0.06), radius: 12, y: 4)
            }
            .accessibilityLabel("How to play")
            .padding(.trailing, 20)
        }
        .sheet(isPresented: $showRules) {
            HowToPlayView { withAnimation { seenOnboarding = false } }
        }
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
                    .position(x: w * 0.6, y: h * 0.08)
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
