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
                    self.room = try? snapshot?.data(as: Room.self)
                }
            }
        guard let uid else { return }
        handListener = Firestore.firestore().collection("rooms").document(code)
            .collection("hands").document(uid)
            .addSnapshotListener { [weak self] snapshot, _ in
                Task { @MainActor in
                    let cards = snapshot?.data()?["cards"] as? [[String: Any]] ?? []
                    self?.myCards = cards.compactMap { c in
                        guard let rank = c["rank"] as? Int, let suit = c["suit"] as? String else { return nil }
                        return Card(rank: rank, suit: suit)
                    }
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
