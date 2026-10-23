# Contributing to Pickles

Thanks for helping. Issues and pull requests are welcome.

## Before you open a pull request

1. Run the scoring-engine tests and build the app:
   ```bash
   ./gradlew :engine:test assembleDebug
   ```
   CI runs the same commands on every pull request.
2. Keep scoring logic in `engine/` (pure Kotlin, no Android code). The UI must never compute a
   score or a server itself; it renders `PicklesEngine.situation(state)`.
3. Add or update engine tests for any behaviour change.

## Scoring rule changes

Pickles follows the **2026 USA Pickleball Official Rulebook**. A pull request or issue that
changes how scoring, serving, sides or end changes work **must cite the rulebook section** it is
based on (for example "Rule 5.B.3"), and should update the KDoc rule reference in `engine/` and the
matching topic in the in-app Rules screen. For a disputed call, open a *Rule question* issue with
the section and the exact score / server state.

## Licence

Pickles is licensed under the GNU GPL v3 (see `LICENSE`). By contributing you agree that your
contribution is licensed under the same terms.

Never commit keystores, `keystore.properties`, `local.properties` or any password or key.
