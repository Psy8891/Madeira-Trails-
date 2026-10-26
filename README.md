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
| `core/catalog/` | Plain Kotlin: reads the trail catalog and live status, search over trails and places, where you are on a trail, GPX import, map GeoJSON |
| `data/` | Python data jobs (with pytest tests): trail catalog from OpenStreetMap (names, road access, points of interest), official status from Visit Madeira |
| `.github/workflows/android-ci.yml` | CI: runs the unit and data tests, builds a debug APK and publishes it on every push |
| `.github/workflows/update-catalog.yml` | Weekly: rebuilds `app/src/main/assets/catalog.json` and `data/catalog-report.md` |
| `.github/workflows/update-status.yml` | Every 30 min: publishes `status.json` on the `status` branch |

## Status

Phase 0.6 (see the spec). The app opens on a map of Madeira (a temporary online base map until
the offline packages exist) with every trail drawn: official trails coloured by their live
official status, community trails from OpenStreetMap dashed, and peaks, viewpoints, waterfalls,
water, shelters, parking and tunnels as icons. The locate button shows where you are (follow, then
follow with compass). Explore searches trails, peaks and named places (accent-insensitive, partial
names work), filters by source, and opens a trail on the map with its start, end and the walk from
the nearest road, plus a card with its status age, how to get to the start and where you are on
it. **Import GPX** adds your own files (from a guide, Wikiloc or a friend); they stay on the
phone. Record and Me are still placeholders.

Live status: <https://raw.githubusercontent.com/Psy8891/Madeira-Trails-/status/status.json>
(what the app reads), parsed rows in
[`rows.txt`](https://github.com/Psy8891/Madeira-Trails-/blob/status/rows.txt).
Trail coverage: [`data/catalog-report.md`](data/catalog-report.md).

## Install on your phone

Every push to `main` that passes CI replaces the **Latest debug build** release with a fresh APK:

1. On the phone, signed in to GitHub, open
   <https://github.com/Psy8891/Madeira-Trails-/releases/download/latest-debug/vereda-debug.apk>
   (or the repo's *Releases* page).
2. Open the downloaded `vereda-debug.apk` and allow installs from your browser when Android asks.
3. Later builds install over this one; your data stays.

All builds are signed with the shared debug key in `app/debug.keystore`, so CI and local builds
are interchangeable on the same phone.

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
