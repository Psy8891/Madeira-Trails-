# Working on this repo

- `docs/SPEC.md` is the source of truth. Change the spec first, then the code.
- Build one phase at a time (spec section *Build phases*). Don't start the next phase until every
  acceptance box of the current one is ticked.
- Designs: `docs/design/` (tokens in its README). Match colours and copy to them.
- Business logic (stats, off-route, status freshness, search, GPX) lives in plain Kotlin in
  `core/*` so it runs in fast JVM unit tests. Android code in `app/` stays thin.
- Every new piece of logic gets unit tests. Run `./gradlew test` before pushing; CI runs
  `./gradlew test :app:assembleDebug` on every push and pull request.
- Safety rule: never show a trail as open unless the status is fresh (under 24 h old). Stale or
  missing status is `UNKNOWN`.
- Toolchain: AGP 9.3 with built-in Kotlin, Gradle 9.5, Kotlin 2.4, compileSdk/targetSdk 36,
  minSdk 26. Versions live in `gradle/libs.versions.toml`.
- Not publishing to Google Play for now; no German or French strings in v1.
