import SwiftUI

extension View {
    /// Long-press a player to report their name or hide it on this phone.
    /// Does nothing for yourself and for AI players.
    func playerMenu(_ uid: String, in room: Room) -> some View {
        modifier(PlayerMenu(uid: uid, room: room))
    }
}

private struct PlayerMenu: ViewModifier {
    @Environment(Backend.self) private var backend
    var uid: String
    var room: Room
    @State private var reporting = false
    @State private var thanked = false

    private var hidden: Bool { HiddenNames.shared.contains(uid) }

    func body(content: Content) -> some View {
        if uid == backend.uid || room.isAI(uid) {
            content
        } else {
            content
                .contextMenu {
                    Button("Report name…", systemImage: "exclamationmark.bubble") { reporting = true }
                    Button(hidden ? "Show name" : "Hide name", systemImage: hidden ? "eye" : "eye.slash") {
                        HiddenNames.shared.set(uid, hidden: !hidden)
                    }
                }
                .confirmationDialog("Report \(room.name(of: uid))?", isPresented: $reporting, titleVisibility: .visible) {
                    ForEach(ReportReason.allCases) { reason in
                        Button(reason.label) {
                            Task { thanked = await backend.report(uid, reason: reason) }
                        }
                    }
                } message: {
                    Text("We'll review this name.")
                }
                .alert("Thanks for telling us", isPresented: $thanked) {
                    if !hidden {
                        Button("Hide their name") { HiddenNames.shared.set(uid, hidden: true) }
                    }
                    Button("OK", role: .cancel) {}
                } message: {
                    Text("We'll review the report. You can also hide their name on this phone.")
                }
        }
    }
}
