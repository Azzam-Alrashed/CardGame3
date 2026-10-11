# Porting checklist: iOS → Android

Live checklist for the Android port. Each iOS file, the Kotlin file(s) it became, and where it stands.
Sources of truth: `ios/CardGame3/` (working tree), `firebase/functions/src/`, `RULES.md`, `README.md` (Features).
Decisions that aren't obvious from the iOS code are logged in [NOTES.md](NOTES.md).

Kotlin lives under `app/src/main/java/com/azzamalrashed/cardgame3/`.

| iOS | Android | Status |
| --- | --- | --- |
| App/CardGame3App.swift | CardGame3App.kt, MainActivity.kt | done |
| App/RootView.swift | RootScreen.kt | done |
| Models/Rules.swift | model/Rules.kt | done |
| Models/Round.swift | model/Round.kt | done |
| Models/Room.swift | model/Room.kt, model/HiddenNames.kt | done |
| (Codable) | model/Decoding.kt | done |
| Models/Hand.swift | model/Hand.kt | done |
| Models/Choreography.swift | model/Choreography.kt | done |
| Services/Backend.swift (working tree, with `landings`) | services/Backend.kt | done |
| Services/Sound.swift | services/Sound.kt, design/SoundToggle.kt | done |
| (UserDefaults / @AppStorage) | services/AppSettings.kt | done |
| Design/Theme.swift | design/Theme.kt | done |
| Design/Formatting.swift | design/Formatting.kt | done |
| Design/Blob.swift | design/Blob.kt | done |
| Design/PlayingCard.swift | design/PlayingCard.kt | done |
| Design/SpeechBubble.swift | design/SpeechBubble.kt | done |
| Design/AdaptiveSplit.swift | design/AdaptiveSplit.kt | done |
| Design/Controls.swift | design/Controls.kt, design/Dialogs.kt, design/Icons.kt | done |
| (SwiftUI animation, TimelineView, sensoryFeedback) | design/Motion.kt, design/Haptics.kt | done |
| Screens/Home/HomeView.swift | screens/home/HomeScreen.kt | todo |
| Screens/Onboarding/OnboardingView.swift | screens/onboarding/OnboardingScreen.kt | todo |
| Screens/Onboarding/HowToPlayView.swift | screens/onboarding/HowToPlaySheet.kt | todo |
| Screens/Lobby/LobbyView.swift | screens/lobby/LobbyScreen.kt | todo |
| Screens/Game/PlayerMenu.swift | screens/game/PlayerMenu.kt | todo |
| Screens/Game/GameView.swift | screens/game/GameScreen.kt | todo |
| Screens/Game/PlayersStrip.swift | screens/game/PlayersStrip.kt | todo |
| Screens/Game/DealLayer.swift | screens/game/DealSpots.kt (done), screens/game/DealLayer.kt | todo |
| Screens/Game/MyHand.swift | screens/game/MyHand.kt | todo |
| Screens/Game/ActionPanel.swift | screens/game/ActionPanel.kt | todo |
| Screens/Game/Banner.swift | screens/game/Banner.kt | todo |
| Screens/Game/GameOverView.swift | screens/game/GameOverScreen.kt | todo |
| Resources/Assets.xcassets/Sounds | res/raw/sfx_*.wav (tools/make_sounds.sh) | done |
| Resources/Assets.xcassets/AppIcon | res/mipmap-*/ic_launcher* (tools/make_icon.sh) | done |
| Resources/InfoPlist.xcstrings | res/values/strings.xml, res/values-ar/strings.xml | done |
| Resources/GoogleService-Info.plist | app/google-services.json | done |

Milestones: skeleton ✅ · models + backend ✅ · Home/Onboarding · Lobby · Game · deal + showdown · sounds · polish/tablet/RTL · unit tests · device checks.

## Conventions (read before porting a screen)

**Build.** `export JAVA_HOME=/opt/homebrew/opt/openjdk@21 ANDROID_HOME=~/Library/Android/sdk`, then from `android/`:
`./gradlew assembleDebug lint testDebugUnitTest`.

**State.** `Backend` is the iOS `Backend`: its fields (`room`, `uid`, `myCards`, `flipped`, `dealClock`, `revealClock`, …)
are Compose state, so reading them in a composable redraws on change. Moves are `suspend` functions. Time is epoch
milliseconds (`Long`, `System.currentTimeMillis()`); choreography timelines are seconds (`Double`), see `model/Choreography.kt`.

**Strings.** All UI text comes from resources (`stringResource`), English copied word for word from iOS. Each screen
has its own `res/values/strings_<screen>.xml` with names prefixed by the screen (`home_create_room`). Interpolations
become `%1$s` args; numbers are passed already formatted (`amount.formatted()`, `short(n)`). Hand names
(`HandValue.name`) and "Player 3" stay in Kotlin, as on iOS.

**Type.** `Theme.body(size, weight)` / `Theme.wordmark(size)` take iOS point sizes. Weights: `.medium` → Medium,
`.semibold` → SemiBold, `.bold` → Bold, `.heavy` → ExtraBold, `.black` → Black. `.monospacedDigit()` →
`with(Theme) { style.monospacedDigits() }`. Text color is `Theme.ink` unless iOS says otherwise.

**Layout.** Screens draw their backgrounds edge to edge and pad their content with `Modifier.safeDrawingPadding()`
(includes the keyboard). `AdaptiveSplit(main = {…}) { side }` is iOS's `AdaptiveSplit`; its side is a column that
sits at the bottom (iOS's `Spacer(minLength: 0)` on top). `BottomSheet { }` is iOS's `BottomSheet`.
SwiftUI `.offset(x:)` is physical: use `Modifier.offset` for things that should mirror in Arabic (RTL) and
`Modifier.absoluteOffset` for physical things (cards, blobs' parts). `rotationEffect(anchor: .bottom)` →
`graphicsLayer { rotationZ = deg; transformOrigin = TransformOrigin(0.5f, 1f) }`.

**Motion.** `.spring(duration: d, bounce: b)` → `swiftSpring(d, b)`; `.smooth` = `swiftSpring(0.5)`,
`.snappy` = `swiftSpring(0.5, 0.15)`, `.bouncy` = `swiftSpring(0.5, 0.3)`, `.easeInOut` = `tween(350, easing = EaseInOut)`.
`TimelineView(FramesUntil(end:, fps:))` → `frameTime(until, fps)` (or `frameTicks` inside draw code);
`TimelineView(.periodic(by:))` → `periodicTime(ms)`; `.onChange(of:)` → `OnChange(value) { old, new -> }`;
Reduce Motion → `reduceMotion()`. `withAnimation` + state → `animate*AsState` / `Animatable` with the same spec.

**Feedback.** `sensoryFeedback` / `UIImpactFeedbackGenerator` → `rememberHaptics().play(Haptic.Light|Medium|Heavy|Success|Warning|Selection)`.
`.soundFeedback(sound, trigger:)` → `OnChange(trigger) { old, new -> if (…) SoundPlayer.play(Sound.X) }`.

**Controls.** `PillButtonStyle` → `PillButton` / `AsyncPillButton` (spinner, busy) ; `CircleButtonStyle` →
`CircleButton` / `CircleIconButton`; `.blocksWhileBusy()` → wrap in `BlocksWhileBusy { }`; `PillField`;
`SoundToggle`; `confirmationDialog` → `ChoiceDialog`; `.alert` → `MessageDialog`; `.sheet` → Material3
`ModalBottomSheet`; `contextMenu` → long-press + `DropdownMenu` (see `PlayerMenu`). SF Symbols → Material icons
per the table in `design/Icons.kt`.

**Pieces.** `Blob(color, size, mood = Mood.Happy, look = Offset(x, y), hair)`, `PlayingCard`, `CardBack`,
`FlipCard(card, faceUp, width, animation)`, `drawCardBack` (Canvas), `SpeechBubble`, `Theme.color(forSeat)`.
Deal positions: `Modifier.dealSpot(DealSpot.X)` and `LocalDealSpots`; sizes in `TableLayout`.
