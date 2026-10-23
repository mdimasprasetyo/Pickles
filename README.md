# Pickles

[![Android build](https://github.com/mdimasprasetyo/Pickles/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/mdimasprasetyo/Pickles/actions/workflows/android.yml)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)

An offline-first pickleball scorekeeper for Android (singles and doubles). After each rally you tap
**Team A Won Rally** or **Team B Won Rally**; Pickles works out the score, the server, the server
number, the serving side, the correct receiver and the score call, following the
**2026 USA Pickleball Official Rulebook**.

- Kotlin + Jetpack Compose, Material 3, portrait only, minSdk 26 (Android 8.0), targetSdk 35
- No network permission, no login, no ads, no analytics
- Match saved on the device after every rally; unfinished matches can be resumed
- Full undo of every rally (and of starting the next game)
- Free and open source (GPL-3.0)

## Project layout

```
Pickles/
├── engine/                     Pure Kotlin/JVM module: the scoring engine (no Android code)
│   ├── src/main/kotlin/app/pickles/engine/
│   │   ├── Model.kt            Immutable MatchState and related types (single source of truth)
│   │   ├── PicklesEngine.kt    newMatch, applyRallyResult, undo, startNextGame, situation(...)
│   │   └── MatchCodec.kt       JSON (de)serialisation with validation, used for persistence
│   └── src/test/kotlin/...     41 unit tests (rule fixtures, multi-rally traces, randomized
│                               cross-checks, undo, persistence, completion, tap safety)
└── app/                        Android app
    └── src/main/java/app/pickles/
        ├── MainActivity.kt     Entry point, screen routing, keep-screen-on
        ├── data/               MatchRepository (atomic JSON file), UiPrefs
        └── ui/
            ├── MatchViewModel.kt   Routes every intent through the engine; persists each commit
            ├── components/         CourtView (Canvas court + player markers), Scoreboard, common
            └── screens/            Home, Setup, Match, Help
```

### Architecture in one paragraph

`MatchState` is the only authoritative state. `PicklesEngine.applyRallyResult(state, winner)` is a
pure function that returns a complete new state (or a rejection), pushes the previous state onto the
undo stack and appends the rally to the history. `PicklesEngine.situation(state)` derives everything
the screen shows (server, server number, serving side, receiver, receiving court, each player's
score-based side and whether that position is required or flexible, and the score call). The court,
scoreboard and buttons all render from that one derived value, so they cannot disagree. The UI never
computes a score or a server.

## Rules implemented (2026 rulebook)

| Behaviour | Rule |
|---|---|
| Only the serving side scores (standard side-out scoring, default) | 4.A |
| First to the target score with the required margin wins | 4.B, 15.C |
| Singles: even score serves from right, odd from left; receiver diagonally opposite; side out on a lost rally | 5.A.1–5.A.3 |
| Doubles: both partners serve, except the starting server alone at the start of each game (0-0-2) | 5.B, 5.B.2 |
| After a side out, the player correctly on the right for the team score is server 1 | 5.B.2 |
| Correct sides: starting server right on even team score, left on odd; partner opposite | 5.B.3 |
| Only server and receiver have required positions; partners are unrestricted | 5.B.1, 5.B.4 |
| Server 1 → server 2 → side out | 5.B.5, 5.B.6 |
| Score calls: two numbers (singles), three numbers (doubles), games start 0-0-2 | 6.B.1, 6.B.2 |
| Serve to the diagonally opposite service court | 7.E |
| Optional, explicitly selected rally scoring (provisional) | 14.A.2–14.A.5 |
| End change at 6/8/11 in a single or deciding game; same server continues | 21.B.1–21.B.4 |
| Change ends and initial service each game; starting server may change | 21.F.2, 21.F.3 |

Rule numbers are also cited in KDoc throughout `engine/` and in the in-app Rules screen.

## Build

Requirements: JDK 17 and the Android SDK (platform 35, build-tools 35.0.0). Android Studio
Ladybug (2024.2) or newer works out of the box.

### Android Studio
1. *File → Open…* and choose the `Pickles` folder.
2. Let Gradle sync, then press **Run** with a device or emulator selected.

### Command line
```bash
cd Pickles
# point Gradle at your SDK once
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew :engine:test          # scoring-engine unit tests
./gradlew assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease       # app/build/outputs/apk/release/app-release.apk (minified)
```

### Release signing

The release build reads its signing key from environment variables or Gradle properties (for
example in `~/.gradle/gradle.properties`, or CI secrets), never from files in the repository:

| Variable | Meaning |
|---|---|
| `PICKLES_KEYSTORE_PATH` | Path to your keystore (`.jks`), absolute or relative to the repo root |
| `PICKLES_KEYSTORE_PASSWORD` | Keystore password |
| `PICKLES_KEY_ALIAS` | Key alias |
| `PICKLES_KEY_PASSWORD` | Key password |

```bash
PICKLES_KEYSTORE_PATH=~/keys/pickles.jks PICKLES_KEYSTORE_PASSWORD=... \
PICKLES_KEY_ALIAS=pickles PICKLES_KEY_PASSWORD=... ./gradlew assembleRelease
```

When they are not set (a normal local or contributor build), the release build falls back to the
debug key so it still builds and installs. Keystores, `keystore.properties` and `local.properties`
are git-ignored; never commit them.

### Signed releases on GitHub

`.github/workflows/release.yml` builds and signs the APK in CI whenever a release is published.
Add four repository secrets under **Settings → Secrets and variables → Actions**:
`PICKLES_KEYSTORE_BASE64` (the `.jks` file, base64-encoded), `PICKLES_KEYSTORE_PASSWORD`,
`PICKLES_KEY_ALIAS` and `PICKLES_KEY_PASSWORD`. Then bump `versionCode` / `versionName` in
`app/build.gradle.kts`, publish a release tagged `v<versionName>` (e.g. `v1.3.2`), and the
workflow attaches `Pickles-<version>.apk` to it. It fails rather than publish a debug-signed APK.

## Install on a phone

**From the APK file**
1. Copy `app-debug.apk` (or `app-release.apk`) to the phone.
2. Open it from the Files app. If asked, allow *Install unknown apps* for that app.
3. Tap **Install**, then open **Pickles**.

**With USB debugging**
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Using the app

1. Home → **Singles** or **Doubles**.
2. Enter names, pick each team's starting server (doubles), who serves first (or tap **Toss**),
   and the format. The card at the bottom says whether the format is an official tournament
   format (Rule 15.C) or recreational.
3. Tap the team that won each rally. The court shows the server in the highlighted serving area,
   an arrow to the highlighted receiving court, the receiver, and partners as faded *flexible*
   markers. The scoreboard shows the score call, the server's name and number, and the side.
4. **Undo** reverses the last rally exactly. **Restart** (with confirmation) starts the same
   match over. **Settings** shows the format, lets you rename players, flip the court view or keep
   the screen on. Tap a player's name on the court to rename them.
5. Close the app any time; Home offers **Resume match** next time.

## Tests

`./gradlew :engine:test` runs 41 JVM tests covering, among others:

1–3 singles start, side switching, side out · 4 doubles 0-0-2 · 5 server 1 → 2 · 6 side out after
server 2 · 7 serving-team scoring · 8–10 server, receiver and left/right court (13-rally rule fixture
trace) · 11 receivers never score · 12–14 undo after point / server change / side out · 15 restore
after restart · 16 game and match completion, deuce, win by 1 · 17 completed-match rejection and
duplicate-tap protection · 18 different starting servers and first-serving teams · best-of-3 flow ·
end changes · rally scoring · **3,000 random doubles games cross-checked against an independent
"physical movement" model** (partners swap only when their team scores while serving) and 2,000
random singles games.

`app/src/test/.../ScreenshotTest.kt` renders the real screens with Robolectric + Roborazzi
(`./gradlew :app:testDebugUnitTest`, images in `app/build/outputs/roborazzi`). It needs an x86_64
or macOS host for Robolectric's native graphics.

## Contributing

Issues and pull requests are welcome. Before opening a pull request, run the engine tests and a
debug build (`./gradlew :engine:test assembleDebug`); CI runs the same on every pull request.
Any change to scoring, serving, sides or end changes must cite the **2026 USA Pickleball Official
Rulebook** section it is based on. For a disputed call, open a *Rule question* issue with the
section and the score / server state. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Support the project

Pickles is free, with no ads and no tracking, and it will stay that way. If it helps on court and
you'd like to say thanks, you can support development on Trakteer (QRIS, e-wallets and Visa/Mastercard accepted):

<a href="https://trakteer.id/bukandimas" target="_blank"><img src="https://edge-cdn.trakteer.id/images/embed/trbtn-red-1.png?v=14-05-2025" height="40" alt="Trakteer Saya"></a>

## License

Copyright (C) 2026 Muhammad Dimas Prasetyo

This program is free software: you can redistribute it and/or modify it under the terms of the GNU
General Public License as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version. This program is distributed in the hope that it
will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE. See [LICENSE](LICENSE) for the full text.

The bundled fonts are not covered by the GPL: Plus Jakarta Sans and Barlow Condensed are licensed
under the SIL Open Font License 1.1 (see [`licenses/`](licenses/); the same texts ship in the app
under `app/src/main/assets/`).
