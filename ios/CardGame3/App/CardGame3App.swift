import FirebaseAuth
import FirebaseCore
import FirebaseFirestore
import FirebaseFunctions
import SwiftUI

@main
struct CardGame3App: App {
    @State private var backend: Backend

    init() {
        Self.configureFirebase()
        _backend = State(initialValue: Backend())
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(backend)
        }
    }

    /// Uses the live Firebase project by default. The "CardGame3 (Emulators)" scheme sets
    /// USE_EMULATORS=1 to use the local emulators instead (simulator only).
    private static func configureFirebase() {
        FirebaseApp.configure()
        guard ProcessInfo.processInfo.environment["USE_EMULATORS"] == "1" else { return }

        let host = "127.0.0.1"
        Auth.auth().useEmulator(withHost: host, port: 9099)
        Functions.functions(region: "me-central1").useEmulator(withHost: host, port: 5001)
        let settings = Firestore.firestore().settings
        settings.host = "\(host):8080"
        settings.isSSLEnabled = false
        settings.cacheSettings = MemoryCacheSettings()
        Firestore.firestore().settings = settings
    }
}
