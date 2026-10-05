import AVFoundation
import SwiftUI

/// Every sound in the app. Files are data sets in Assets.xcassets/Sounds, made by assets/make_sounds.py.
enum Sound: CaseIterable {
    case shuffle, deal, land, flip, fold
    case chip, chips, chipsBig, allIn
    case offer, accept, reject
    case turn, tick, match, join, toggle
    case boss, win, lose, verdict, quads, fanfare
    /// Darbuka: the drumroll before the boss reveals, and a deep center stroke.
    case roll, doum

    /// One is picked at random each time, so repeated sounds don't feel mechanical.
    fileprivate var assets: [String] {
        switch self {
        case .shuffle: ["sfx-shuffle"]
        case .deal: ["sfx-deal-1", "sfx-deal-2", "sfx-deal-3", "sfx-deal-4"]
        case .land: ["sfx-land-1", "sfx-land-2"]
        case .flip: ["sfx-flip-1", "sfx-flip-2"]
        case .fold: ["sfx-fold-1", "sfx-fold-2"]
        case .chip: ["sfx-chip-1", "sfx-chip-2", "sfx-chip-3"]
        case .chips: ["sfx-chips-1", "sfx-chips-2", "sfx-chips-3"]
        case .chipsBig: ["sfx-chips-big"]
        case .allIn: ["sfx-all-in"]
        case .offer: ["sfx-offer"]
        case .accept: ["sfx-accept"]
        case .reject: ["sfx-reject"]
        case .turn: ["sfx-turn"]
        case .tick: ["sfx-tick"]
        case .match: ["sfx-match"]
        case .join: ["sfx-join"]
        case .toggle: ["sfx-toggle"]
        case .boss: ["sfx-boss"]
        case .win: ["sfx-win"]
        case .lose: ["sfx-lose"]
        case .verdict: ["sfx-verdict"]
        case .quads: ["sfx-quads"]
        case .fanfare: ["sfx-fanfare"]
        case .roll: ["sfx-roll"]
        case .doum: ["sfx-doum"]
        }
    }

    /// Every file is normalized to the same peak, so loudness is set here.
    fileprivate var volume: Float {
        switch self {
        case .deal, .tick: 0.45
        case .fold, .toggle, .join: 0.6
        case .chip, .land, .reject: 0.7
        case .flip, .chips, .offer, .accept, .turn, .boss, .lose, .verdict: 0.8
        case .chipsBig, .match, .win, .fanfare, .roll: 0.9
        case .shuffle: 0.75
        case .allIn, .quads, .doum: 1
        }
    }

    /// Slight random pitch for sounds that repeat a lot; music stays in tune.
    fileprivate var varies: Bool {
        switch self {
        case .boss, .win, .lose, .verdict, .quads, .fanfare, .match, .roll: false
        default: true
        }
    }
}

/// Plays sounds over whatever else is playing, and stays quiet when the phone is on silent.
@MainActor
final class SoundPlayer {
    static let shared = SoundPlayer()

    /// Idle players per file, so the same sound can overlap itself (cards dealt quickly).
    private var pools: [String: [AVAudioPlayer]] = [:]
    private let maxPerFile = 6

    private init() {
        // Ambient: muted by the silent switch, and mixes with the player's music.
        try? AVAudioSession.sharedInstance().setCategory(.ambient)
    }

    /// Turned off with the speaker button; on by default.
    static var isOn: Bool { UserDefaults.standard.object(forKey: "soundOn") as? Bool ?? true }

    /// Loads every file once so the first play of each sound isn't late.
    func preload() {
        for asset in Sound.allCases.flatMap(\.assets) where pools[asset] == nil {
            if let player = makePlayer(asset) { pools[asset] = [player] }
        }
    }

    /// `rate` above 1 plays higher and faster (used for rising "match" pops).
    func play(_ sound: Sound, rate: Float = 1, volume: Float = 1) {
        guard Self.isOn, let asset = sound.assets.randomElement(), let player = idlePlayer(asset) else { return }
        player.volume = sound.volume * volume
        player.rate = rate * (sound.varies ? .random(in: 0.94...1.06) : 1)
        player.currentTime = 0
        player.play()
    }

    /// Stops a long sound early (the drumroll, when a reveal is skipped).
    func stop(_ sound: Sound) {
        for asset in sound.assets { pools[asset]?.forEach { $0.stop() } }
    }

    private func idlePlayer(_ asset: String) -> AVAudioPlayer? {
        let pool = pools[asset] ?? []
        if let idle = pool.first(where: { !$0.isPlaying }) { return idle }
        guard pool.count < maxPerFile, let player = makePlayer(asset) else { return nil }
        pools[asset] = pool + [player]
        return player
    }

    private func makePlayer(_ asset: String) -> AVAudioPlayer? {
        guard let data = NSDataAsset(name: asset)?.data,
              let player = try? AVAudioPlayer(data: data, fileTypeHint: AVFileType.caf.rawValue)
        else { return nil }
        player.enableRate = true
        player.prepareToPlay()
        return player
    }
}

extension View {
    /// Plays a sound when `trigger` changes and `condition` agrees, like `sensoryFeedback`.
    func soundFeedback<T: Equatable>(
        _ sound: Sound, trigger: T, condition: @escaping (T, T) -> Bool = { _, _ in true }
    ) -> some View {
        onChange(of: trigger) { old, new in
            if condition(old, new) { SoundPlayer.shared.play(sound) }
        }
    }
}

/// Speaker button that turns all sounds on or off.
struct SoundToggle: View {
    var size: CGFloat = 44
    @AppStorage("soundOn") private var soundOn = true

    var body: some View {
        Button {
            soundOn.toggle()
            SoundPlayer.shared.play(.toggle)
        } label: {
            Image(systemName: soundOn ? "speaker.wave.2.fill" : "speaker.slash.fill")
                .contentTransition(.symbolEffect(.replace))
        }
        .buttonStyle(CircleButtonStyle(size: size))
        .accessibilityLabel(soundOn ? "Turn sound off" : "Turn sound on")
    }
}
