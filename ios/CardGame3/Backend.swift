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
    var errorMessage: String?

    var playerName: String {
        didSet { UserDefaults.standard.set(playerName, forKey: "playerName") }
    }

    private let functions = Functions.functions()
    private var roomListener: ListenerRegistration?

    init() {
        playerName = UserDefaults.standard.string(forKey: "playerName") ?? ""
        uid = Auth.auth().currentUser?.uid
    }

    var isHost: Bool { room != nil && room?.hostId == uid }

    // MARK: Sign-in

    /// Anonymous sign-in for now; Apple and Google sign-in come before release.
    func signIn() async {
        if uid != nil { return }
        await run {
            let result = try await Auth.auth().signInAnonymously()
            uid = result.user.uid
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
        roomListener = Firestore.firestore().collection("rooms").document(code)
            .addSnapshotListener { [weak self] snapshot, error in
                Task { @MainActor in
                    guard let self else { return }
                    if let error {
                        self.errorMessage = error.localizedDescription
                        return
                    }
                    // A missing document means the room was deleted.
                    self.room = try? snapshot?.data(as: Room.self)
                }
            }
    }

    private func stopListening() {
        roomListener?.remove()
        roomListener = nil
        room = nil
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
