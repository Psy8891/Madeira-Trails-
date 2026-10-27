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
| `core/geo/` | Plain Kotlin geo logic: distances, track stats, off-route detection, UTM, GPX, elevation grid and height profiles, sunrise and sunset, scale bar |
| `core/route/` | Plain Kotlin: reads the walking network and plans routes (A*) |
| `core/catalog/` | Plain Kotlin: reads the trail catalog and live status, search over trails and places, where you are on a trail, GPX import, map GeoJSON, IPMA weather warnings, the pre-hike checklist |
| `core/maps/` | Plain Kotlin: the offline map package (manifest, status, resume, SHA-256) and the Vereda Topo style |
| `data/` | Python data jobs (with pytest tests): trail catalog from OpenStreetMap (names, road access, points of interest), official status from Visit Madeira, offline map building (terrain, contours, elevation grid, manifest) |
| `.github/workflows/android-ci.yml` | CI: runs the unit and data tests, builds a debug APK and publishes it on every push |
| `.github/workflows/update-catalog.yml` | Weekly: rebuilds `app/src/main/assets/catalog.json` and `data/catalog-report.md` |
| `.github/workflows/update-status.yml` | 09:00 and 14:00 Madeira time: publishes `status.json` on the `status` branch |
| `.github/workflows/build-maps.yml` | Monthly + manual: builds the offline map package (release `maps-YYYY.MM.DD`, manifest on `maps-latest`) and the elevation grid, fonts and overview map in the app |

## Status

Phases 0.5–0.9 done, Phase 1 (offline map) and the first part of Phase 4 (safety) built; see the
spec. The app opens on a map of Madeira with every trail drawn: official trails coloured by their
live official status, community trails from OpenStreetMap dashed, and peaks, viewpoints,
waterfalls, water, shelters, parking and tunnels as icons. Download the offline topo map (contours,
terrain shading, levadas and paths) in *Me → Offline map* or from the map banner; without it the
map is online, or a small overview map when there is no connection. Trail and route cards show the
climb with a small height graph, time including the climb, sunset and daylight left, IPMA weather
warnings and a *Before you go* checklist. *Plan a route* (or a long-press on the map) plans your
own walk on the phone, offline; *Directions* shows the whole hike in the app or opens Google Maps.
The red *SOS* button opens the emergency card (call 112, your coordinates, send them by SMS).
**Import GPX** adds your own files; they stay on the phone. Record is still a placeholder.

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
