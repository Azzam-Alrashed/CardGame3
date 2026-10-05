import SwiftUI

struct HomeView: View {
    @Environment(Backend.self) private var backend
    @State private var code = ""
    @State private var bob = false
    @State private var appeared = false
    @State private var showRules = false
    @FocusState private var focus: Field?
    @AppStorage("seenOnboarding") private var seenOnboarding = true

    private enum Field { case name, code }

    private var hasName: Bool { !backend.playerName.trimmingCharacters(in: .whitespaces).isEmpty }
    private var ready: Bool { hasName && backend.uid != nil }

    var body: some View {
        @Bindable var backend = backend
        AdaptiveSplit {
            hero
                .contentShape(Rectangle())
                .onTapGesture { focus = nil }
        } side: {
            VStack(spacing: 0) {
                Spacer(minLength: 0)
                VStack(spacing: 6) {
                    Text("مداقش").font(Theme.wordmark(72))
                    Text("Bet big. Make a deal. Reveal.").font(Theme.body(16, .medium)).opacity(0.7)
                }
                .padding(.bottom, 20)
                .scaleEffect(appeared ? 1 : 0.8)
                .opacity(appeared ? 1 : 0)

                BottomSheet {
                    if let away = backend.awayRoomCode {
                        AsyncButton("Back to game \(away)") { await backend.returnToGame() }
                            .buttonStyle(PillButtonStyle())
                            .disabled(backend.uid == nil)
                    }
                    PillField(placeholder: "Your name", text: $backend.playerName)
                        .textInputAutocapitalization(.words)
                        .submitLabel(.done)
                        .focused($focus, equals: .name)

                    AsyncButton("Create room") { await backend.createRoom() }
                        .buttonStyle(PillButtonStyle())
                        .disabled(!ready)

                    HStack(spacing: 12) {
                        PillField(placeholder: "Room code", text: $code)
                            .font(Theme.body(18, .heavy))
                            .textInputAutocapitalization(.characters)
                            .autocorrectionDisabled()
                            .submitLabel(.join)
                            .focused($focus, equals: .code)
                            .onSubmit { if ready && code.count == 4 { Task { await backend.joinRoom(code: code) } } }
                            .onChange(of: code) { code = String(code.uppercased().filter(\.isLetter).prefix(4)) }
                        AsyncButton("Join") { await backend.joinRoom(code: code) }
                            .buttonStyle(PillButtonStyle(primary: code.count == 4))
                            .frame(width: 110)
                            .disabled(!ready || code.count != 4)
                            .animation(.snappy, value: code.count == 4)
                    }
                }
                .blocksWhileBusy()
                .offset(y: appeared ? 0 : 40)
                .opacity(appeared ? 1 : 0)
            }
        }
        .overlay(alignment: .topTrailing) {
            HStack(spacing: 10) {
                SoundToggle()
                Button { showRules = true } label: { Image(systemName: "questionmark") }
                    .buttonStyle(CircleButtonStyle())
                    .accessibilityLabel("How to play")
            }
            .padding(.trailing, 20)
        }
        .sheet(isPresented: $showRules) {
            HowToPlayView { withAnimation { seenOnboarding = false } }
        }
        .onAppear {
            bob = true
            withAnimation(.spring(duration: 0.6, bounce: 0.35).delay(0.1)) { appeared = true }
        }
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
                        // Fans out from a neat stack when the screen appears.
                        .rotationEffect(.degrees(appeared ? Double(i - 1) * 12 - 6 : 0), anchor: .bottom)
                        .offset(x: appeared ? CGFloat(i) * cardW * 0.2 - cardW * 0.3 : 0)
                        .animation(.spring(duration: 0.7, bounce: 0.4).delay(0.2 + Double(i) * 0.07), value: appeared)
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
}
