import FirebaseAuth
import FirebaseFirestore
import FirebaseFunctions
import Foundation
import Observation

/// Talks to Firebase: signs in, calls the room functions, and keeps the current room live.
@MainActor
@Observable
final class Backend {
    private(set) var uid: String?
    private(set) var room: Room?
    /// This player's 4 cards for the current round (readable only by them).
    private(set) var myCards: [Card] = []
    /// The round `myCards` were dealt for: the room and the cards arrive separately after a deal.
    private(set) var myCardsRound: Int?
    /// When this phone saw the current round dealt, to animate the deal. Nil when the round was
    /// already dealt as we started listening (opening the app mid-round): nothing to animate.
    private(set) var dealtAt: (round: Int, date: Date)?
    /// Which of my cards (by position) I've turned over this round. Kept across launches.
    private(set) var flipped: Set<Int> = []
    var errorMessage: String?

    var playerName: String {
        didSet { UserDefaults.standard.set(playerName, forKey: "playerName") }
    }

    /// Same region as the Cloud Functions (Doha).
    private let functions = Functions.functions(region: "me-central1")
    private var roomListener: ListenerRegistration?
    private var handListener: ListenerRegistration?
    /// Round number we already asked the server to force-reveal, so we only ask once.
    private var timeUpSent: Int?
    /// True while this player is leaving a room on purpose (so losing access isn't news).
    private var leaving = false
    /// Waits for flips to settle before telling the table, so flipping all four is one call.
    private var peekTask: Task<Void, Never>?

    init() {
        playerName = UserDefaults.standard.string(forKey: "playerName") ?? ""
        uid = Auth.auth().currentUser?.uid
    }

    var isHost: Bool { room != nil && room?.hostId == uid }

    // MARK: Sign-in

    /// Anonymous sign-in for now; Apple and Google sign-in come before release.
    func signIn() async {
        // A saved session from another backend (e.g. emulators vs live) can't refresh: start over.
        if let user = Auth.auth().currentUser {
            do {
                _ = try await user.getIDToken(forcingRefresh: true)
            } catch let error as NSError {
                // Being offline must not cost a player their seat; any other failure means the
                // session is no good here (expired, or from the other backend), so start fresh.
                if AuthErrorCode(rawValue: error.code) != .networkError {
                    try? Auth.auth().signOut()
                    uid = nil
                }
            }
        }
        if uid == nil {
            await run {
                let result = try await Auth.auth().signInAnonymously()
                uid = result.user.uid
            }
        }
        // Back to the table after the app was closed mid-game.
        if room == nil, let saved = UserDefaults.standard.string(forKey: "roomCode") {
            listen(to: saved)
        }
    }

    // MARK: Rooms

    func createRoom() async {
        await run {
            let code = try await callForCode("createRoom", ["name": playerName])
            listen(to: code)
        }
    }

    func joinRoom(code: String) async {
        await run {
            let joined = try await callForCode("joinRoom", ["code": code, "name": playerName])
            listen(to: joined)
        }
    }

    func leaveRoom() async {
        guard let code = room?.code else { return }
        leaving = true
        defer { leaving = false }
        await run {
            _ = try await functions.httpsCallable("leaveRoom").call(["code": code])
            stopListening()
        }
    }

    func addAiPlayer() async {
        guard let code = room?.code else { return }
        await run {
            _ = try await functions.httpsCallable("addAiPlayer").call(["code": code])
        }
    }

    /// Host only, in the lobby: removes another player, person or AI.
    func removePlayer(_ uid: String) async {
        guard let code = room?.code else { return }
        await run {
            _ = try await functions.httpsCallable("removePlayer").call(["code": code, "playerId": uid])
        }
    }

    /// After a game: opens a new lobby with the same AI players, or joins the one someone already opened.
    func rematch() async {
        guard let code = room?.code else { return }
        await run {
            let newCode = try await callForCode("rematch", ["code": code])
            listen(to: newCode)
        }
    }

    func startGame() async {
        guard let code = room?.code else { return }
        await run {
            _ = try await functions.httpsCallable("startGame").call(["code": code])
        }
    }

    /// Leaves the screen of a finished game (the server keeps the room as a record).
    func goHome() { stopListening() }

    /// Reports another player's name for review. Returns whether the report was sent.
    func report(_ uid: String, reason: ReportReason) async -> Bool {
        guard let code = room?.code else { return false }
        var sent = false
        await run {
            _ = try await functions.httpsCallable("reportPlayer")
                .call(["code": code, "playerId": uid, "reason": reason.rawValue])
            sent = true
        }
        return sent
    }

    // MARK: Away

    /// Room code of a game this player stepped away from (shown on Home as "Back to game").
    private(set) var awayRoomCode: String? = UserDefaults.standard.string(forKey: "awayRoomCode") {
        didSet { UserDefaults.standard.set(awayRoomCode, forKey: "awayRoomCode") }
    }

    /// Step away from a game in progress: a bot plays for you until you come back.
    func leaveGame() async {
        guard let code = room?.code else { return }
        await run {
            _ = try await functions.httpsCallable("setAway").call(["code": code, "away": true])
            stopListening()
            awayRoomCode = code
        }
    }

    /// At the table while a bot plays your seat (your turn ran out): take it back.
    func takeSeatBack() async {
        guard let code = room?.code else { return }
        await run {
            _ = try await functions.httpsCallable("setAway").call(["code": code, "away": false])
        }
    }

    /// Back from Home: take your seat back from the bot.
    func returnToGame() async {
        guard let code = awayRoomCode else { return }
        await run {
            do {
                _ = try await functions.httpsCallable("setAway").call(["code": code, "away": false])
                listen(to: code)
            } catch {
                // The game ended or the room is gone: nothing to go back to.
                awayRoomCode = nil
                throw error
            }
            awayRoomCode = nil
        }
    }

    // MARK: Playing

    func bet(_ amount: Int) async { await roundCall("bet", ["amount": amount]) }
    func withdraw() async { await roundCall("withdraw") }
    func makeOffer(_ amount: Int) async { await roundCall("makeOffer", ["amount": amount]) }
    func answerOffer(from uid: String, accept: Bool) async {
        await roundCall("answerOffer", ["from": uid, "accept": accept])
    }
    func reveal() async { await roundCall("reveal") }

    /// Called by every phone when its countdown hits zero; the server checks the real deadline.
    func timerRanOut() async {
        guard let round = room?.round, round.phase == .deals, timeUpSent != round.roundNumber else { return }
        timeUpSent = round.roundNumber
        await roundCall("timeUp", quiet: true)
    }

    func nextRound() async {
        guard let number = room?.round?.roundNumber else { return }
        await roundCall("nextRound", ["roundNumber": number])
    }

    // MARK: Looking at cards

    /// Turns over some of my cards. The table sees how many I've looked at (their small cards lift).
    func flip(_ cards: [Int]) {
        guard let code = room?.code, let round = room?.round?.roundNumber else { return }
        let before = flipped.count
        flipped.formUnion(cards)
        guard flipped.count > before else { return }
        UserDefaults.standard.set(["\(code):\(round)": Array(flipped)], forKey: "flipped")
        peekTask?.cancel()
        peekTask = Task {
            try? await Task.sleep(for: .milliseconds(400))
            guard !Task.isCancelled else { return }
            await roundCall("peek", ["roundNumber": round, "count": flipped.count], quiet: true)
        }
    }

    /// The deal this phone is animating for the current round, if any.
    var dealClock: DealClock? {
        guard let round = room?.round, let dealt = dealtAt, dealt.round == round.roundNumber,
              let seats = room?.table?.seats.map(\.id) else { return nil }
        return DealClock(timeline: DealTimeline(seats: seats, dealerId: round.dealerId), start: dealt.date, round: dealt.round)
    }

    /// Notes when a new round is dealt (to animate it) and picks up which cards I'd already turned over.
    private func roomChanged(from old: Room?, to new: Room?) {
        guard let new, let round = new.round else { return }
        if round.roundNumber != old?.round?.roundNumber {
            // The first snapshot after listening shows a round already in progress: nothing to animate.
            if old != nil { dealtAt = (round.roundNumber, .now) }
            let saved = UserDefaults.standard.dictionary(forKey: "flipped")?["\(new.code):\(round.roundNumber)"] as? [Int]
            flipped = Set(saved ?? [])
        }
    }

    private func roundCall(_ name: String, _ extra: [String: Any] = [:], quiet: Bool = false) async {
        guard let code = room?.code else { return }
        var data = extra
        data["code"] = code
        do {
            _ = try await functions.httpsCallable(name).call(data)
        } catch {
            if !quiet { errorMessage = (error as NSError).localizedDescription }
        }
    }

    // MARK: Helpers

    private func callForCode(_ name: String, _ data: [String: Any]) async throws -> String {
        let result = try await functions.httpsCallable(name).call(data)
        guard let code = (result.data as? [String: Any])?["code"] as? String else {
            throw BackendError.badResponse
        }
        return code
    }

    private func listen(to code: String) {
        stopListening()
        UserDefaults.standard.set(code, forKey: "roomCode")
        roomListener = Firestore.firestore().collection("rooms").document(code)
            .addSnapshotListener { [weak self] snapshot, error in
                Task { @MainActor in
                    guard let self else { return }
                    if error != nil {
                        // No longer allowed to read this room: we left it, or the host removed us.
                        if !self.leaving, self.room?.status == .lobby {
                            self.errorMessage = "The host removed you from the room."
                        }
                        self.stopListening()
                        return
                    }
                    // A missing document means the room was deleted.
                    let room = try? snapshot?.data(as: Room.self)
                    self.roomChanged(from: self.room, to: room)
                    self.room = room
                }
            }
        guard let uid else { return }
        handListener = Firestore.firestore().collection("rooms").document(code)
            .collection("hands").document(uid)
            .addSnapshotListener { [weak self] snapshot, _ in
                Task { @MainActor in
                    let data = snapshot?.data()
                    let cards = data?["cards"] as? [[String: Any]] ?? []
                    self?.myCards = cards.compactMap { c in
                        guard let rank = c["rank"] as? Int, let suit = c["suit"] as? String else { return nil }
                        return Card(rank: rank, suit: suit)
                    }
                    self?.myCardsRound = data?["round"] as? Int
                }
            }
    }

    private func stopListening() {
        roomListener?.remove()
        roomListener = nil
        handListener?.remove()
        handListener = nil
        room = nil
        myCards = []
        myCardsRound = nil
        dealtAt = nil
        flipped = []
        peekTask?.cancel()
        UserDefaults.standard.removeObject(forKey: "roomCode")
    }

    /// Runs a backend call and turns any failure into a message the UI can show.
    private func run(_ work: () async throws -> Void) async {
        errorMessage = nil
        do {
            try await work()
        } catch {
            errorMessage = (error as NSError).localizedDescription
        }
    }
}

enum BackendError: LocalizedError {
    case badResponse
    var errorDescription: String? { "Unexpected response from the server" }
}

/// Why a player's name was reported (matches the server's `REPORT_REASONS`).
enum ReportReason: String, CaseIterable, Identifiable {
    case offensive, impersonation, other

    var id: String { rawValue }
    var label: String {
        switch self {
        case .offensive: "Offensive name"
        case .impersonation: "Pretending to be someone"
        case .other: "Something else"
        }
    }
}
