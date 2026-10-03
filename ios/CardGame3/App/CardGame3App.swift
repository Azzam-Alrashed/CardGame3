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

    /// Debug builds talk to the local Firebase emulators, so no GoogleService-Info.plist is needed yet.
    private static func configureFirebase() {
        #if DEBUG
        let options = FirebaseOptions(googleAppID: "1:628219046474:ios:0000000000000000", gcmSenderID: "628219046474")
        options.apiKey = "emulator-api-key"
        options.projectID = "cardgame-3"
        FirebaseApp.configure(options: options)

        let host = "127.0.0.1"
        Auth.auth().useEmulator(withHost: host, port: 9099)
        Functions.functions(region: "me-central1").useEmulator(withHost: host, port: 5001)
        let settings = Firestore.firestore().settings
        settings.host = "\(host):8080"
        settings.isSSLEnabled = false
        settings.cacheSettings = MemoryCacheSettings()
        Firestore.firestore().settings = settings
        #else
        FirebaseApp.configure()
        #endif
    }
}
