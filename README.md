# Vereda — Madeira Trails

[![Android CI](https://github.com/Psy8891/Madeira-Trails-/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Psy8891/Madeira-Trails-/actions/workflows/android-ci.yml)

An offline-first Android hiking app for Madeira and Porto Santo: a detailed topo map with every
official PR trail and the best community-mapped levadas, GPS recording and navigation, one-tap
offline maps, and a local profile with Done / To-do / Wishlist lists. No account, no login.

*Vereda* is a working name.

## Where things are

| Path | What |
| --- | --- |
| [`docs/SPEC.md`](docs/SPEC.md) | The build spec: features, data sources, architecture, data model, build phases with acceptance criteria |
| [`docs/design/`](docs/design/) | Screen designs and design tokens |
| `app/` | The Android app (Kotlin, Jetpack Compose, MapLibre) |
| `core/model/` | Plain Kotlin models: trails, status, the official trail list, search |
| `core/geo/` | Plain Kotlin geo logic: distances, track stats, off-route detection, UTM, GPX |
| `.github/workflows/android-ci.yml` | CI: runs the unit tests and builds a debug APK on every push |

## Status

Phase 1 skeleton. The app opens on a map of Madeira (a temporary online base map until the
Phase 0 offline packages exist), lists the 42 official trails with accent-insensitive search,
and has placeholder Record and Me tabs. The core logic for later phases already has unit tests.

## Build and test

Requirements: JDK 21 (or 17+) and the Android SDK with platform 36. Android Studio handles both.

```bash
./gradlew test               # all unit tests
./gradlew :app:assembleDebug # debug APK in app/build/outputs/apk/debug/
```

Every push to `main` and every pull request runs the same commands on GitHub Actions. Each run
shows a test summary table on its page (and fails if no tests ran), and attaches the debug APK and
the test reports as downloadable artifacts.

## Data and licences

Map data © OpenStreetMap contributors (ODbL). Terrain © DROTe – Direção Regional do Ordenamento
do Território (CC BY 4.0). Trail status and fees from IFCN and Visit Madeira. See `docs/SPEC.md`.
