# Android port: decisions and notes

Choices made where the iOS app didn't settle the question, or where Android works differently.
Newest at the bottom of each section.

## Setup

- **Toolchain.** AGP 9.4.1 (built-in Kotlin), Kotlin 2.4.20, Gradle 9.8.0, Compose BOM 2026.09.00, Firebase BOM
  34.19.0: the versions already cached on this Mac. `compileSdk`/`targetSdk` 37 (newest installed), `minSdk` 26.
- **Firebase.** Registered as the Android app "CardGame3 Android" (`1:628219046474:android:043440a17ece549e7154b3`)
  in `cardgame-3` with the Firebase CLI. `app/google-services.json` is committed, like iOS's
  `GoogleService-Info.plist`. No backend changes.
- **Emulators.** `./gradlew installDebug -Pcardgame3.emulators` builds a debug app that uses the local Firebase
  emulators at 10.0.2.2 (the host, seen from the Android emulator), like the iOS "Emulators" scheme. Plain HTTP is
  allowed only to 10.0.2.2 and 127.0.0.1 (`res/xml/network_security_config.xml`).
- **Configuration cache off.** A cached configuration kept replaying a one-off dependency download timeout on every
  later build, so `org.gradle.configuration-cache=false` in `gradle.properties`.

## Look

- **Font.** iOS uses SF Rounded, which Apple doesn't license for Android. The app uses the platform font (Roboto) at
  the same sizes and weights. `Theme.fontFamily` is the one place to change it: drop a rounded font such as Nunito
  (OFL) into `res/font/` and point it there.
- **Text size** follows the system font size (sp), as Android apps should; iOS uses fixed sizes. Card faces are the
  exception: their letters scale with the card.
- **Icons.** SF Symbols mapped to Material icons (table in `design/Icons.kt`). Two had no match and are drawn:
  the points badge (circle.hexagongrid.fill) and the crown. Share uses Android's share icon.
- **App icon.** Rendered from `assets/logo.svg` (`tools/make_icon.sh`, macOS Quick Look) as the adaptive icon's
  foreground, the logo filling 76 of 108 dp so the launcher's mask trims its corners as iOS does; the background
  layer is the logo's lime. There's no monochrome (themed icon) layer: the logo is a full-color scene.
- **Shadows** use Compose's `dropShadow` (blur radius and offset like SwiftUI's `.shadow`).

## Sound

- **Files.** The iOS sounds are IMA4 CAF, which Android can't play; `tools/make_sounds.sh` decodes them to 16-bit
  WAV (no further quality loss) in `res/raw`, `-` becoming `_` in the names. 1.7 MB.
- **Silent phone.** iOS's ambient session is muted by the silent switch. Android has no such switch for media, so
  sounds are skipped while the ringer is on silent or vibrate. No audio focus is taken, so music keeps playing
  under the game, as on iOS.
- **Player.** `SoundPool`, one random file per play, the same per-sound volumes and the ±6% pitch wobble.

## Behavior

- **State across rotation.** The activity handles orientation and size changes itself (`configChanges`), so
  rotating keeps every animation and half-typed field, like iOS. `Backend` lives in the `Application`.
- **Settings** are in SharedPreferences (`cardgame3`) under the iOS UserDefaults names (`playerName`, `roomCode`,
  `awayRoomCode`, `seenOnboarding`, `soundOn`, `hiddenNames`). The cards you flipped are two keys
  (`flippedRound` = "CODE:round", `flippedCards`) instead of iOS's one-entry dictionary.
- **Moves keep running** when the screen that started them goes away (`CardGame3App.scope`), like iOS `Task`s.
- **Not ported:** the `// TEMP timing` prints in the working-tree `Backend.swift`.
- **Reduce Motion** is Android's "Remove animations" (animator duration scale 0).
- **Haptics** map to `HapticFeedbackConstants` (light → KEYBOARD_TAP, medium → CONTEXT_CLICK, heavy → LONG_PRESS,
  success → CONFIRM, warning → REJECT, selection → SEGMENT_FREQUENT_TICK), so they follow the phone's
  touch-feedback setting.
- **Dialogs.** iOS confirmation dialogs become Material alert dialogs listing the choices plus Cancel; alerts
  become alert dialogs.

## Languages

- iOS translates only the app's name (InfoPlist.xcstrings: Muda / مداقش); the UI is English everywhere. Android does
  the same: `values-ar/strings.xml` has only `app_name`, and lint's MissingTranslation check is off (`app/lint.xml`).
- With `supportsRtl`, an Arabic phone lays the app out right to left, as iOS does when it runs in Arabic.
  `generateLocaleConfig` lists English and Arabic for Android 13's per-app language setting.
