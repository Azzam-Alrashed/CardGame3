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

    init() {
        playerName = UserDefaults.standard.string(forKey: "playerName") ?? ""
        uid = Auth.auth().currentUser?.uid
    }

    var isHost: Bool { room != nil && room?.hostId == uid }

    // MARK: Sign-in

    /// Anonymous sign-in for now; Apple and Google sign-in come before release.
    func signIn() async {
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
        await run {
            _ = try await functions.httpsCallable("leaveRoom").call(["code": code])
            stopListening()
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
                        // No longer allowed to read this room (e.g. left it): forget it quietly.
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
