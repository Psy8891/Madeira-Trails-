# Madeira Trails App — Build Spec

Oct 26, 2026 · @André

## Summary

**Vereda** (working name, rename freely) is an offline-first Android hiking app for Madeira and Porto Santo: a Gaia GPS-style topo map carrying every official PR trail plus the best community-mapped levadas and veredas, GPS recording and navigation, one-tap offline maps, and a local profile with Done / To-do / Wishlist lists. No account and no login.

Why it beats the global apps on this island:

- **Official status and booking on every PR trail** — open / partial / closed with a "checked X min ago" time, the 2026 fee, and a button to book on SIMplifica.
- **Sharper topo** — contours and hillshade built from Madeira's own 10 m terrain model, not global 30 m data.
- **The whole island offline in one tap** — Madeira and Porto Santo each ship as one package, so nobody has to draw download boxes before a hike.
- **Levadas as a first-class map feature** — channel, path, tunnels, exposed sections and shelters drawn distinctly.

**Decided:** Kotlin + Jetpack Compose · MapLibre Native · Room · free self-hosted map data (OpenStreetMap + DROTe 10 m DTM) published as static files on GitHub · PT-PT and EN at launch.

**How to use this with Claude Code**

1. Export this doc as Markdown and commit it as `docs/SPEC.md` in a new repo; save the UI design screens to `docs/design/`.
2. Ask Claude Code to build one phase at a time (see *Build phases*), and don't start the next phase until every acceptance box of the current one is ticked.
3. Treat `docs/SPEC.md` as the source of truth: change the spec first, then the code.

## Goals and principles

Version 1 succeeds if a first-time visitor can pick a trail, confirm it is open, book it, and walk it offline without getting lost — and a local can log every hike they do.

**Goals**

1. **Find a trail in under 30 seconds** — search by name or code ("pr6", "25 fontes"), filter by status, difficulty, length and area, or browse the map.
2. **Navigate safely with no signal** — trail line on a topo map, live position, distance and climb remaining, off-route alert.
3. **Record any hike and keep a personal log** — Done, To-do, Wishlist and custom lists, plus stats such as "official trails completed: 17 of 42".
4. **Never mislead about access** — official status with its check time and source, booking and fee shown before the user commits.

**Who it is for**

| User | Needs most |
| --- | --- |
| Visitors (most users) | Open/closed status, booking and fee, English, offline maps, simple safety info |
| Residents | Lesser-known trails and levadas, logging hikes, free entry but booking still required |
| Trail runners | Recording, pace, climb, exporting GPX to other tools |

**Principles**

- **No login.** The profile lives on the phone; backup is a file the user owns plus Android's built-in device backup.
- **Offline first.** Every screen works in airplane mode; only status, weather warnings and package updates need a connection.
- **Battery budget.** Target: a 6-hour hike with recording on uses under 25% of a Pixel 8-class battery.
- **Free to run.** No paid APIs and no server code; only static files on GitHub.
- **Honest data.** Every trail shows its source: Official (IFCN), Community (OpenStreetMap) or Imported.

**Not in version 1:** a Google Play release, accounts or cloud sync, reviews and social feed, photo sharing, road routing to the trailhead (hand off to Google Maps), iOS, Wear OS, payments or ads.

## Madeira rules the app must reflect

Since 1 January 2026 every classified (PR) trail needs a timed booking on SIMplifica before you start; visitors pay €4.50 per trail (PR 1: €10.50), while residents and children up to 12 walk free but must still book. Trails close often, and walking a closed one is an offence, so status is a core feature, not a nice-to-have.

| Rule (2026) | What the app does | Source |
| --- | --- | --- |
| Booking is mandatory for all PR trails, online only via SIMplifica; walking without one is an infraction | "Book on SIMplifica" button on every PR trail page and in the To-do reminder | [Visit Madeira FAQ](https://visitmadeira.com/en/travel-info/faq/) |
| Standard fee €4.50 per person; PR 1 €10.50; €3 when booked through protocol tour operators | Fee chip on cards and trail page, stored per trail in the catalog | [Visit Madeira FAQ](https://visitmadeira.com/en/travel-info/faq/), [Visit Madeira hiking](https://www.visitmadeira.com/en/what-to-do/nature-seekers/activities/hiking/) |
| Visitors 12 and under are exempt | "Free for 12 and under" line on the fee card | [PR 6 trail page](https://visitmadeira.com/en/what-to-do/nature-seekers/activities/hiking/pr-6-levada-das-25-fontes/) |
| Residents are free but must register and book | "I'm a Madeira resident" switch in the profile changes the fee card to "Free — booking still required" | [IFCN FAQ](https://ifcn.madeira.gov.pt/en/atividades-de-natureza/percursos-pedestres-recomendados/faq-s-taxas-percursos-pedestres-classificados/frequently-asked-questions-fees-classified-walking-routes.html) |
| Daily and 3- or 7-day passes are applied automatically at checkout when cheaper | Note on the fee card; no price maths in the app | [IFCN FAQ](https://ifcn.madeira.gov.pt/en/atividades-de-natureza/percursos-pedestres-recomendados/faq-s-taxas-percursos-pedestres-classificados/frequently-asked-questions-fees-classified-walking-routes.html) |
| Closed trails: access is a misdemeanour with fines of €250–€2,500 for individuals | Closed trails drawn red-dashed, Navigate blocked behind a warning | [DN Madeira, 2023](https://www.dnoticias.pt/2023/10/25/380569-turistas-identificados-pela-policia-florestal-na-vereda-do-areeiro/) |
| Status is published as an official PDF and on each Visit Madeira trail page, often with partial openings ("open to km 1.2") and detours | Status model has open / partial / closed / unknown plus a note and km range | [Status PDF](https://visitmadeira.com/media/rcradgkh/percursos-pedestres-classificados.pdf) |

Fees and rules change yearly, so they live in the downloadable catalog, never hard-coded in the app.

### Official trail list (catalog seed)

The IFCN list has 42 classified trails: 39 on Madeira and 3 on Porto Santo ([official list PDF](https://visitmadeira.com/media/rcradgkh/percursos-pedestres-classificados.pdf)). The data pipeline must match every code below to a geometry and fail loudly if one is missing.

| Code | Name (start – end) | Municipality |
| --- | --- | --- |
| PR 1 | Vereda do Pico do Areeiro (Pico do Areeiro – Pico Ruivo) | Santana |
| PR 1.1 | Vereda da Ilha (Pico Ruivo – Ilha) | Santana |
| PR 1.2 | Vereda do Pico Ruivo (Achada do Teixeira – Pico Ruivo) | Santana |
| PR 1.3 | Vereda da Encumeada (Pico Ruivo – Encumeada) | Ribeira Brava |
| PR 2 | Vereda do Urzal (Curral das Freiras – Boaventura) | Câmara de Lobos |
| PR 3 | Vereda do Burro (Pico do Areeiro – Ribeira das Cales) | Funchal |
| PR 3.1 | Caminho Real do Monte (Ribeira das Cales – Monte) | Funchal |
| PR 4 | Levada do Barreiro (Poço da Neve – Casa do Barreiro) | Funchal |
| PR 5 | Vereda das Funduras (Portela – Maroços) | Machico |
| PR 6 | Levada das 25 Fontes (Rabaçal – 25 Fontes) | Calheta |
| PR 6.1 | Levada do Risco (Rabaçal – Risco) | Calheta |
| PR 6.2 | Levada do Alecrim (Rabaçal – Nascente da Levada do Alecrim) | Calheta |
| PR 6.3 | Vereda da Lagoa do Vento (Rabaçal – Lagoa do Vento) | Calheta |
| PR 6.4 | Levada Velha do Rabaçal (Pico Gordo – Casa do Rabaçal) | Calheta |
| PR 6.5 | Vereda do Pico Fernandes (Paul da Serra – 25 Fontes) | Calheta |
| PR 6.6 | Vereda do Túnel do Cavalo (Zona de Lazer da Caldeira – Casa do Rabaçal) | Calheta |
| PR 6.7 | Vereda da Câmara de Carga do Rabaçal (Rabaçal – Túnel do Cavalo) | Calheta |
| PR 6.8 | Levada do Paul II – Um Caminho Para Todos (Rabaçal – Estrada do Rochão) | Calheta |
| PR 7 | Levada do Moinho (Ribeira da Cruz – Lamaceiros) | Porto Moniz |
| PR 8 | Vereda da Ponta de São Lourenço (Baía d'Abra – Cais do Sardinha) | Machico |
| PR 9 | Levada do Caldeirão Verde (Queimadas – Caldeirão Verde) | Santana |
| PR 9.1 | Levada do Caldeirão Verde – Um Caminho para Todos (Queimadas – Pico das Pedras) | Santana |
| PR 10 | Levada do Furado (Ribeiro Frio – Portela) | Machico |
| PR 11 | Vereda dos Balcões (Ribeiro Frio – Balcões) | Santana |
| PR 12 | Caminho Real da Encumeada (Boca da Corrida – Encumeada – Ribeira Grande) | Ribeira Brava |
| PR 13 | Vereda do Fanal (Assobiadores – Paul da Serra – Fanal) | Porto Moniz |
| PR 13.1 | Vereda da Palha Carga (Louro da Gota – 25 Fontes) | Porto Moniz |
| PR 14 | Levada dos Cedros (Fanal – Curral Falso) | Porto Moniz |
| PR 15 | Vereda da Ribeira da Janela (Curral Falso – Ribeira da Janela) | Porto Moniz |
| PR 16 | Levada Fajã do Rodrigues (Fajã da Amã – Ribeira da Janela) | São Vicente |
| PR 17 | Caminho do Pináculo e Folhadal (Lombo do Mouro – Folhadal – Encumeada) | Ribeira Brava |
| PR 18 | Levada do Rei (Quebradas – Ribeiro Bonito) | Santana |
| PR 19 | Caminho Real do Paul do Mar (Prazeres – Paul do Mar) | Calheta |
| PR 20 | Vereda do Jardim do Mar (Prazeres – Jardim do Mar) | Calheta |
| PR 21 | Caminho do Norte (Encumeada – Ribeira Grande) | São Vicente |
| PR 22 | Vereda do Chão dos Louros | São Vicente |
| PR 23 | Levada da Azenha (Azenha – Caminho Velho do Castelo) | Santa Cruz |
| PR 27 | Glaciar do Planalto (Bica da Cana – ER105) | Calheta |
| PR 28 | Levada da Rocha Vermelha (Fanal – Lombo do Atouguia) | Calheta |
| PS PR 1 | Vereda do Pico Branco e Terra Chã (ER 111 – Terra Chã) | Porto Santo |
| PS PR 2 | Vereda do Pico do Castelo (Moledo – Pico do Castelo) | Porto Santo |
| PS PR 3 | Levada do Pico do Castelo (Camacha – Ribeiro Formoso – Capela da Graça) | Porto Santo |

### Beyond the famous trails

You asked for lesser-known official trails and anything with a usable GPX. The catalog takes these in, in this order of trust:

1. **The quieter official PRs** — PR 6.3 to 6.8, 13.1, 16, 22, 23, 27, 28 and the Porto Santo trails get the same status, fee and booking treatment as the famous ones. PR 6.8 and PR 9.1 are signed "Um Caminho Para Todos" and get an *Accessible* tag and filter.
2. **Other official routes** — the Parque Ecológico do Funchal 18.8 km loop that [Visit Madeira lists](https://visitmadeira.com/pt/o-que-fazer/exploradores-da-natureza/atividades/trail-running/parque-ecologico-do-funchal/) as a trail-running route, and the planned 93 km Grande Rota from Porto Moniz to Machico ([DN Madeira](https://www.dnoticias.pt/2023/4/14/356039-governo-lanca-empreitada-pedestre-grande-rota/)), which is not on the 2026 list yet. The data model supports GR routes so it can be added the day it is published.
3. **Community trails from OpenStreetMap** — unclassified but popular paths such as Levada Nova, Levada do Moinho (Ponta do Sol), Levada da Ribeira da Janela, Vereda do Galhano and Levada do Brasileiro ([DN Madeira](https://www.dnoticias.pt/2023/10/16/379314-percursos-pedestres-dos-concelhos-afectados-pelos-incendios-reabertos/)). These have no official status or maintenance, so they carry a "Not officially maintained" banner.
4. **GPX you bring yourself** — files from guidebooks, Wikiloc or friends are imported privately on the device. They can't be bundled in the app unless their licence allows redistribution (see *Data sources*).

## Features

Version 1 ships nine feature areas; items tagged **(later)** wait for version 1.x. Everything not tagged is in scope for v1.

### 1. Map (home tab)

- Full-screen topo map, opening on the whole island or the last viewed area.
- **Layers sheet** (stack icon, as in Gaia GPS): base map Topo (default) or Satellite **(later, online only)**; overlays switched on and off individually — official trails, community trails, levadas, contour lines, hillshade (with intensity slider), points of interest, my tracks.
- Official trails drawn by status: open green, partial amber, closed red dashed, unknown grey. Community trails blue-grey dashed.
- **Tap a trail** → bottom sheet peek: code badge, name, distance, climb, time, status chip, fee chip, buttons *Details* and *Navigate*.
- Controls: locate me (cycles follow → follow with heading → free), compass reset, scale bar.
- **Long-press** a point → coordinates (decimal and UTM zone 28N), terrain elevation, *Save waypoint*, *Share location*.
- Measure-distance tool **(later)**.
- Search bar on top; red record button bottom-right.

### 2. Explore (trail catalog)

- AllTrails-style list of cards with a map toggle. Card: elevation sparkline or photo, code badge, name, area, distance, time, climb, difficulty, route type, status chip, fee chip.
- **Quick chips:** Open now · Under 2 h · Levadas · Peaks · Accessible · Porto Santo · Near me.
- **Filters sheet:** source (official / community / my imports), status, difficulty, distance range, climb range, duration, route type (loop, out and back, one way), municipality, features (waterfall, viewpoint, tunnel, laurel forest, accessible, family friendly).
- **Sort:** nearest, shortest, longest, most climb, code order, A–Z.
- Default: official trails first, "Open now" on.

### 3. Trail page

- Map preview with the line, start and end markers and km ticks; tap to open full map.
- **Status banner:** state, note ("open to km 1.2"), "checked 18 min ago", link to the official page. Stale data (over 24 h) shows as *Unknown — check the official page*, never as open.
- **Access card:** fee, "Free for residents and 12 and under", *Book on SIMplifica* (opens browser), booking reminder.
- **Stats:** distance, estimated time, climb and descent, highest and lowest point, route type, difficulty. Official values win when published; otherwise computed (see *Data model*).
- **Elevation profile:** drag along it to show the matching point on the map.
- **About:** a short original summary (2–4 sentences, written by us, not copied) and a link to the official description.
- **On the trail:** tunnels (bring a torch), exposed or vertigo sections, shelters, water, toilets, parking, nearest bus stop.
- **Actions:** Save (Done / To-do / Wishlist / custom list), Navigate, Download offline map, Export GPX, Share, Directions to start (opens Google Maps).
- **Your history** on this trail: dates done and linked recordings.

### 4. Navigate and record

- **Navigate a trail:** line highlighted, progress along it, distance, climb and time remaining, next feature ("tunnel in 400 m").
- **Off-route alert:** vibration and notification when more than 40 m from the line for over 30 s (both adjustable); no alerts during GPS gaps such as tunnels.
- **Record:** start, pause, resume, stop; live time (moving and total), distance, pace, altitude, climb and descent.
- Runs with the screen off in a foreground service; notification shows live stats with Pause and Stop.
- Add a waypoint while recording; photo and note on waypoints **(later)**.
- **On stop:** name it, trim start and end, save. If the track overlaps an official trail by 70% or more, offer "Mark PR 6 as done".
- Crash-safe: points are saved every 10 s and recording resumes after the app is killed.

### 5. Offline maps

- **One-tap packages:** *Madeira* and *Porto Santo*, each holding base map, contours and hillshade. Size shown before download, Wi-Fi-only switch, progress, pause and resume, checksum check, "update available" badge.
- A small overview map (zoom 0–11) ships inside the app so the map is never blank.
- The trail catalog and status are small and refresh in the background.
- **Storage screen:** packages with size, version date, update and delete.
- Gaia-style draw-a-box downloads **(later)**, only needed for the satellite layer.

### 6. Profile and lists (no login)

- **Profile:** display name, photo or initials, "I'm a Madeira resident" switch, units, language.
- **Done** = trails completed, each with date, optional linked recording, rating (1–5) and private note.
- **To-do** = planned trails with an optional date; the day before, a reminder says "Check status and book".
- **Wishlist** = someday trails. **Custom lists** such as "With kids" or "Winter walks".
- **Stats:** km walked, metres climbed, hikes, official trails done (progress ring, x of 42), per year. Heatmap of my tracks and badges **(later)**.
- **Saved:** my recordings, waypoints and imports, sortable into folders (as in Gaia GPS).
- **Backup:** export everything to one .zip (JSON plus GPX files) and import it back; Android device backup enabled for the database and settings (map packages excluded).

### 7. Search

- One box for trails (name, code like "pr6" or "6.1", aliases like "25 fontes"), places (peaks, villages, viewpoints) and coordinates (decimal, DMS, UTM).
- Accent-insensitive ("caldeirao" finds "Caldeirão"), works offline, keeps recent searches.

### 8. GPX import and export

- Import GPX, KML or GeoJSON from the file picker or Android's share / "Open with".
- Imported trails get stats computed from the terrain model and can join any list.
- Export any trail or recording as GPX 1.1 with attribution in the metadata.

### 9. Safety

- **Emergency card:** call 112, large coordinates, "Share my location" by SMS with a Google Maps link (works with no data).
- **Weather warnings** from IPMA for the trail's area, shown on the trail page and before starting.
- Sunset time and "daylight left" while recording.
- Trail-specific checklist before starting (torch for tunnels, water, layers, booking done).
- Low-battery warning at 20% while recording.
- First-launch disclaimer: conditions change; official closures always win over the app.

## Data sources and licensing

Every map and trail byte in the app comes from open data we are allowed to redistribute; official status and fees are facts we link to, and descriptions are written by us.

| Data | Source | Licence | Used for |
| --- | --- | --- | --- |
| Roads, paths, levadas, places, POIs | OpenStreetMap (Geofabrik Portugal extract, clipped to Madeira and Porto Santo) | ODbL 1.0 | Base map, POIs, search, community trails |
| Official trail lines | OSM hiking route relations tagged `ref=PR…`, e.g. [PR14 Levada dos Cedros](https://osm.org/relation/2239199), which already carry from/to, distance and the Visit Madeira link | ODbL 1.0 | Trail geometry |
| Terrain, Madeira | [DROTe 10 m Digital Terrain Model](https://dados.gov.pt/en/datasets/modelo-digital-do-terreno-mdt-da-ilha-da-madeira/) on dados.gov.pt (also a [5 m 2018/19 model](https://snig.dgterritorio.gov.pt/rndg/srv/api/records/cb85978f-afa9-4b9a-b5ed-ae7628372e11) with unclear terms) | CC BY 4.0 | Contours, hillshade, elevation profiles, climb |
| Terrain, Porto Santo | [DROTe 10 m DTM service](https://snig.dgterritorio.gov.pt/rndg/srv/api/records/7241ad17-d659-4c95-9101-770fbe345904) | To verify; fallback Copernicus GLO-30 | Same as above |
| Official list, status, fees | Visit Madeira trail pages and status PDF; IFCN | Government publication — store facts and links only, never copy text or photos | Catalog metadata, status |
| Weather warnings | IPMA open data API | Free, attribution to IPMA | Warnings card |
| Fonts and icons | Noto Sans (glyph PBFs), Material Symbols | OFL / Apache 2.0 | Labels, UI |

**Attribution line** (map corner and About screen): "© OpenStreetMap contributors · Terrain © DROTe · Status: IFCN / Visit Madeira · Warnings © IPMA". DROTe asks for the credit "Informação geográfica cedida pela Direção Regional do Ordenamento do Território" or "© DROTe" ([terms](https://snig.dgterritorio.gov.pt/rndg/srv/api/records/0bcce93f-12e4-44ec-90b8-4fb42fe49b15)).

**ODbL obligation:** the trail catalog is a derived database, so it is published under ODbL in the public data repo with attribution. That costs nothing and keeps the app clean.

**The GPX rule:** a GPX is bundled only if its licence allows redistribution (OSM, open-data portals, Creative Commons without NC for a free app is fine). GPX files from AllTrails, Wikiloc, komoot or guidebooks stay private imports on the user's phone.

**Don't use** the public tile servers of OpenStreetMap or OpenTopoMap: the OSM policy forbids bulk downloads for offline use and heavy app traffic ([OSM tile policy summary](https://b4x.com/android/forum/threads/open-street-maps-not-working.143697/?amp=1)). Self-hosted files avoid that entirely.

## Data pipeline

A separate public repo, `vereda-data`, turns open data into static files with three scheduled GitHub Actions jobs; the app only ever downloads files, so there is no server to run or pay for.

```mermaid
flowchart LR
  A["OSM extract + DROTe 10 m DTM"] --> B["build-maps (monthly)"] --> C["PMTiles packages (GitHub Release)"] --> APP["Vereda app"]
  D["OSM routes + official.yaml"] --> E["build-catalog (daily, 42/42 check)"] --> F["catalog.json (GitHub Pages, ODbL)"] --> APP
  G["Visit Madeira status pages + PDF"] --> H["status (every 30 min)"] --> I["status.json (GitHub Pages)"] --> APP
  J["IPMA API (weather warnings)"] -- "live, on demand" --> APP
```

Only the IPMA weather warnings are fetched live; everything else is a static file the phone keeps for offline use.

| Job | Runs | Does | Publishes |
| --- | --- | --- | --- |
| `build-maps` | Monthly + manual | Clips the OSM extract with osmium; Planetiler builds base vector tiles (z0–15); GDAL `gdal_contour` (10 m interval, 50 m index) + tippecanoe builds contour tiles (z11–15); the DTM is encoded as Terrarium RGB WebP tiles (z8–14) for MapLibre hillshade | `madeira-base.pmtiles`, `madeira-contours.pmtiles`, `madeira-dem.pmtiles` and the same for Porto Santo, as a versioned GitHub Release (e.g. `maps-2026.11`) |
| `build-catalog` | Daily | Pulls OSM hiking relations and named levada/vereda paths; joins them with the hand-kept `official.yaml`; computes stats from the DTM; validates that all 42 official codes matched | `catalog.json`, `trails.geojson` on GitHub Pages + a match report |
| `status` | Every 30 min | Reads each official trail page and the status PDF; parses ABERTO / PARCIALMENTE / ENCERRADO plus notes; applies `status-overrides.yaml` | `status.json` on GitHub Pages |

**`official.yaml` is the human-curated heart of the catalog.** One entry per official trail: code, official name, municipality, island, official URL, fee, official distance and duration, difficulty, accessible flag, OSM relation id, aliases ("25 fontes"), feature tags. It is the place to fix anything OSM gets wrong.

**Status rules (safety-critical):**

- A parse failure keeps the last good value and sets `stale: true`; nothing is ever guessed as open.
- Every entry carries `checkedAt` and `sourceUrl`; the app shows the age and treats anything older than 24 h as unknown.
- `status-overrides.yaml` lets you set a status by hand (with expiry) when the official page lags behind the news.
- Be polite to the source: one request per page per run, a clear User-Agent with contact email, honour errors.

**Elevation stats:** sample the DTM every 10 m along the line, smooth with a 3-point median, count climb only on changes of 3 m or more. Estimated time when no official figure exists: 4 km/h on the flat plus 1 h per 600 m of climb (Naismith-style), rounded to 15 min.

**Package manifest** — the app reads `manifest.json` to know what exists:

```json
{
  "schema": 1,
  "packages": [{
    "id": "madeira",
    "version": "2026.11",
    "minApp": 1,
    "files": [
      {"role": "base", "url": "https://github.com/<you>/vereda-data/releases/download/maps-2026.11/madeira-base.pmtiles", "bytes": 0, "sha256": "…"},
      {"role": "contours", "url": "…/madeira-contours.pmtiles", "bytes": 0, "sha256": "…"},
      {"role": "dem", "url": "…/madeira-dem.pmtiles", "bytes": 0, "sha256": "…"}
    ]
  }],
  "catalog": {"url": "https://<you>.github.io/vereda-data/catalog.json", "version": "2026-10-26"},
  "status": {"url": "https://<you>.github.io/vereda-data/status.json"}
}
```

Package sizes are unknown until the first build; Madeira is about 800 km², so expect tens of MB per file. Phase 0 measures them and writes the real numbers into the manifest.

## App architecture

A native, single-activity Kotlin app: Compose for UI, MapLibre for the map, Room as the single source of truth, and a location foreground service for recording.

| Concern | Choice | Notes |
| --- | --- | --- |
| Language / UI | Kotlin 2.x, Jetpack Compose, Material 3, Navigation Compose | MVVM with unidirectional data flow: `UiState` in a `StateFlow` per screen |
| SDK levels | `compileSdk 36`, `targetSdk 36`, `minSdk 26` | Google Play has required API 36 for new apps and updates since 31 Aug 2026 ([Google Play](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-419)) |
| Map | MapLibre Native Android 13.x (`org.maplibre.gl:android-sdk`) wrapped in `AndroidView` | 13.0 made Vulkan the default; `android-sdk-opengl` is the fallback artifact if a device misrenders ([MapLibre](https://maplibre.org/news/2026-03-03-maplibre-newsletter-february-2026/)) |
| Offline tiles | PMTiles read with `pmtiles://file://<absolute path>` from app storage | `pmtiles://asset://` is not supported, so the bundled overview pack is copied from assets to `filesDir` on first launch; PMTiles sources don't use MapLibre's offline packs ([MapLibre PMTiles](https://maplibre.org/maplibre-native/android/examples/data/PMTiles/)) |
| Database | Room with an FTS4 table for search | Single source of truth; repositories expose `Flow` |
| Settings | DataStore (Preferences) | Profile, units, language, recording options |
| DI / async | Hilt, Coroutines and Flow |  |
| Background | WorkManager for catalog/status refresh and package downloads; a foreground service (type `location`) for recording and navigation |  |
| Location | Fused Location Provider; plain `LocationManager` GPS as fallback on devices without Google services |  |
| Network | OkHttp + kotlinx.serialization; resumable Range downloads with SHA-256 check |  |
| GPX | Own GPX 1.1 reader/writer on `XmlPullParser` | Small, fully unit-tested |
| Charts | Elevation profile drawn on a Compose `Canvas` | No chart library |

**Modules**

| Module | Owns |
| --- | --- |
| `:app` | Activity, navigation graph, theme, DI setup |
| `:core:model` | Plain Kotlin data classes shared everywhere |
| `:core:data` | Room database, DAOs, repositories, catalog/status sync, backup export/import |
| `:core:map` | MapLibre wrapper, style JSON, layer and source management, camera helpers |
| `:core:location` | Recording service, location filters, track statistics, off-route detection |
| `:core:geo` | GPX/KML/GeoJSON parsing, polyline maths, DEM sampling, coordinate formats (UTM 28N) |
| `:core:download` | Package manifest, downloads, checksum, storage management |
| `:feature:map`, `:feature:explore`, `:feature:trail`, `:feature:record`, `:feature:offline`, `:feature:me`, `:feature:search`, `:feature:settings` | One screen group each: Composables + ViewModel |

Keep business logic (stats, off-route, status freshness, search normalisation) in plain Kotlin inside `:core:*` so it runs in fast JVM unit tests.

## Data model

Trails and status arrive from the catalog and are replaced on each sync; everything the user creates lives in separate tables that a sync never touches.

**Room entities**

| Entity | Key fields | Notes |
| --- | --- | --- |
| `Trail` | `id` ("pr-6", "osm-r2239199", "imp-\<uuid>"), `source` (OFFICIAL, COMMUNITY, IMPORT), `code`, `name`, `island`, `municipality`, `lengthM`, `ascentM`, `descentM`, `minAltM`, `maxAltM`, `durationMin`, `difficulty`, `routeType` (LOOP, OUT\_AND\_BACK, ONE\_WAY), `start`/`end` lat-lon, `bbox`, `featureTags`, `feeEur`, `officialUrl`, `catalogVersion` | Imports are never deleted by a sync |
| `TrailGeometry` | `trailId`, `polyline` (encoded, 1e6 precision), `profile` (distance/elevation pairs) | Separate so lists load fast |
| `TrailStatus` | `trailId`, `state` (OPEN, PARTIAL, CLOSED, UNKNOWN), `noteEn`, `notePt`, `sourceUrl`, `checkedAt`, `fetchedAt` | UI computes freshness from `checkedAt` |
| `TrailFts` | name, code variants ("pr6", "pr 6", "6"), aliases, municipality, all accent-folded | FTS4 |
| `UserList` | `id`, `name`, `type` (TODO, WISHLIST, CUSTOM), `sortOrder` | TODO and WISHLIST created on first launch |
| `ListEntry` | `listId`, `trailId`, `addedAt`, `plannedDate?`, `note?` |  |
| `Completion` | `id`, `trailId`, `date`, `trackId?`, `rating?`, `note?` | "Done" = trails with at least one completion; marking Done removes the trail from To-do |
| `Track` | `id`, `name`, `startedAt`, `endedAt`, `distanceM`, `movingS`, `totalS`, `ascentM`, `descentM`, `maxAltM`, `trailId?`, `state` (RECORDING, PAUSED, DONE), `folderId?` |  |
| `TrackPoint` | `trackId`, `seq`, `time`, `lat`, `lon`, `altGps?`, `altDem?`, `accuracyM`, `segment` | Index on (`trackId`, `seq`); a new `segment` after each pause |
| `Waypoint` | `id`, `trackId?`, `name`, `lat`, `lon`, `alt?`, `note?`, `createdAt`, `folderId?` |  |
| `Folder` | `id`, `name`, `parentId?` | Saved items, Gaia-style |
| `MapPackage` | `id`, `version`, `state`, `bytesTotal`, `bytesDone`, `path`, `sha256` |  |

**catalog.json** (one entry shown):

```json
{
  "version": "2026-10-26",
  "trails": [{
    "id": "pr-6",
    "source": "official",
    "code": "PR 6",
    "name": "Levada das 25 Fontes",
    "from": "Rabaçal", "to": "25 Fontes",
    "island": "madeira", "municipality": "Calheta",
    "lengthM": 4300, "durationMin": 180,
    "ascentM": 0, "descentM": 0, "minAltM": 0, "maxAltM": 0,
    "difficulty": "moderate", "routeType": "out_and_back",
    "feeEur": 4.5, "accessible": false,
    "tags": ["levada", "waterfall", "laurel_forest", "tunnel"],
    "aliases": ["25 fontes", "25 springs"],
    "officialUrl": "https://visitmadeira.com/en/what-to-do/nature-seekers/activities/hiking/pr-6-levada-das-25-fontes/",
    "osmRelation": 0,
    "polyline": "…",
    "profile": [[0, 1274], [10, 1273]]
  }]
}
```

The zeros are placeholders the pipeline fills. The 4.3 km and 3 h come from the [official PR 6 page](https://visitmadeira.com/en/what-to-do/nature-seekers/activities/hiking/pr-6-levada-das-25-fontes/).

**status.json:**

```json
{
  "generatedAt": "2026-10-26T12:00:00Z",
  "trails": {
    "pr-1": {"state": "partial", "noteEn": "Open only between Pico do Areeiro and Pedra Rija viewpoint (km 1.2).", "notePt": "…", "sourceUrl": "…", "checkedAt": "2026-10-26T11:48:00Z", "stale": false}
  }
}
```

The PR 1 note mirrors the wording in the official status PDF read for this spec; the live value comes from the job.

**Backup .zip:** `backup.json` (lists, entries, completions, tracks without points, waypoints, folders, profile) + `tracks/<id>.gpx` + `imports/<id>.gpx`. Versioned with `"schema": 1`; import merges by id and never deletes.

## Screens and navigation

Four bottom tabs — **Map · Explore · Record · Me** — with search on top of Map and Explore. The visual designs are in the [Vereda UI design canvas](https://claude.ai/artifact/Cr57XLNKvr6EDJ9Pr7B77g) (nine phone screens plus the shared map style); this table is the contract they follow.

| Screen | Reached from | Must show |
| --- | --- | --- |
| Onboarding (3 steps) | First launch | Language; why location is needed ("only while you use the app or record"); "Download Madeira map now" with size and Wi-Fi switch |
| Map | Tab 1 | Topo map, search bar, layers button, locate, compass, record button, trail lines by status |
| Layers sheet | Map → stack icon | Base map choice, overlay switches, hillshade slider, link to Offline maps |
| Trail peek | Tap a trail line or map pin | Code, name, distance, time, climb, status, fee; Details and Navigate |
| Search | Search bar | Recent searches; results grouped Trails / Places / Coordinates |
| Explore | Tab 2 | Quick chips, sort, trail cards, list/map toggle |
| Filters sheet | Explore → Filters | All filters from *Features §2*, result count on the apply button |
| Trail page | Card, peek, list | Status banner, access card, stats, elevation profile, about, on-the-trail features, actions, your history |
| Navigate | Trail page → Navigate | Map with route, progress bar, remaining distance/climb/time, next feature, off-route banner |
| Record | Tab 3 or red button | Big stats, pause/stop, add waypoint, GPS accuracy, battery, daylight left |
| Save recording | Record → Stop | Name, trim, match to trail ("Mark PR 6 as done?"), folder |
| Me | Tab 4 | Profile header, stats with x of 42 ring, Done / To-do / Wishlist / custom lists, Saved (tracks, waypoints, imports) |
| List detail | Me → a list | Trail cards with planned date or done date; reorder; share list as text |
| Offline maps | Me → Offline maps, Layers sheet | Packages with size, version, progress, update, delete; free space |
| Settings | Me → gear | Units, language, resident switch, recording options (accuracy filter, auto-pause, off-route distance), backup export/import, attributions, privacy |
| Emergency | Shield icon on Map and Record | 112 button, big coordinates, share location by SMS |

**Back behaviour:** Android 16 predictive back on every screen; bottom sheets close before leaving the screen. **Deep links:** `vereda://trail/pr-6` and the GPX "Open with" intent.

## Map style

The style is one MapLibre style JSON in `:core:map/assets/style/vereda-topo.json`, layered so the trail you care about always sits on top of terrain, and levadas read as water, not as paths.

**Layer order (bottom to top)**

1. Background and landcover — land `#F3F0E6`, laurel forest and wood `#CFE3BE`, scrub and heath `#E3E8C8`, meadow `#E8EDCF`, bare rock and scree `#E6DFD3`, built-up `#E9E4DE`, sea `#AFD5EA`.
2. Hillshade — raster-dem source (Terrarium), exaggeration 0.4, shadow `#3E3428`, highlight `#FFFFFF`, light from 315°; user slider scales the exaggeration 0–1.
3. Contours — minor 10 m `#B08A5E` at 35% opacity, 0.6 px, from zoom 13.5; index 50 m `#9A744A` at 60%, 1 px, from zoom 12; labels every 100 m, 10 px, from zoom 14.
4. Water lines and **levadas** — levada channel `#1A8FD6`, 1.4 px with 0.8 px white casing; dashed at 50% opacity in tunnels. Match `waterway=canal` and any way whose name starts with "Levada".
5. Roads — expressway (VR) `#F2C46D` with `#C9A24F` casing; regional (ER) white with `#BDB5A8` casing; tracks `#8C7B6A` dashed.
6. Footpaths by difficulty (`sac_scale`) — easy `#6B4F3A` dashed 3-2; mountain `#6B4F3A` dashed 2-2; demanding and above `#B23A2E` dotted.
7. **Official trail overlay** — width 3 px at zoom 10 rising to 8 px at zoom 16, 85% opacity, colour by status: open `#2E9E5B`, partial `#E3A008`, closed `#C62828` dashed, unknown `#8A8A8A`. Code shields ("PR 6") along the line.
8. Community trails — `#5B6B9A`, 4 px, dashed, 70% opacity.
9. User layers — recorded track `#E4572E` 4 px; active navigation route `#D81B60` 7 px with white casing; waypoints.
10. POIs from zoom 14 — peak (name + height), viewpoint, mountain shelter, drinking water, toilets, parking, bus stop, tunnel entrance, waterfall, picnic site.
11. Labels — Noto Sans; peaks bold with height; water names italic.

A dark/night variant of the style is **(later)**. The same colour values are used in the UI designs so status colours mean the same thing everywhere.

## Tracking and navigation engine

Recording and navigation share one foreground service that collects filtered fixes, stores them in batches, and derives every number on screen from the stored points.

**Location requests**

| Mode | Interval | Min. distance | Batching |
| --- | --- | --- | --- |
| Navigating, screen on | 1 s | 2 m | none |
| Recording, screen on | 2 s | 3 m | none |
| Recording or navigating, screen off | 3 s | 5 m | deliver every 15 s (`maxUpdateDelay`) |

**Fix filtering**

- Drop fixes with accuracy worse than 25 m (setting: 15–50 m).
- Drop jumps that imply more than 15 km/h over two fixes (a hiker, not a car).
- Auto-pause (optional): speed under 0.5 m/s for 20 s; resume on 10 m of movement.
- Each pause starts a new `segment`; distance never bridges segments.

**Statistics**

- Distance: haversine sum of accepted points.
- Climb: terrain elevation (DTM) at each point when the map package is installed, otherwise GPS altitude; count only changes of 3 m or more (hysteresis). Using the phone barometer is **(later)**.
- Moving time excludes auto-paused time; pace = moving time / distance.

**Persistence and recovery**

- Points are buffered in memory and written to Room every 10 s or every 20 points.
- The recording state (track id, status) lives in DataStore; on restart the service resumes the same track.

**Off-route and progress**

- Precompute the route's segments with cumulative distance; snap the position to the nearest segment (spatial grid of 100 m cells).
- Off-route when the snapped distance exceeds the threshold (40 m) for 30 s; back on route below 25 m. No alerts when the last fix is older than 15 s (tunnels, deep valleys).
- Progress = cumulative distance at the snapped point; detect walking the wrong way when progress drops for 60 s.

**Battery and OEM limits**

- Foreground service only; no wakelocks beyond what the service needs.
- For aggressive battery savers (Xiaomi, Samsung, Huawei), show a one-time tip linking to the vendor's settings; don't request unrestricted battery as a permission.

## Permissions, privacy and Play compliance

The app needs location only while it is in use or recording, never in the background, which keeps the Play review simple and the privacy story honest.

**Permissions**

| Permission | Why | When asked |
| --- | --- | --- |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Show position, record, navigate | First tap on locate, Record or Navigate, with a one-line reason first |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` | Keep recording with the screen off | Manifest only |
| `POST_NOTIFICATIONS` | Recording notification, off-route alerts, To-do reminders | When starting the first recording |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Downloads, status, warnings; Wi-Fi-only option | Manifest only |
| `VIBRATE` | Off-route alert | Manifest only |

**Not requested:** background location, storage (use app storage and the system file picker), contacts, camera (later, via the system camera intent), battery-optimisation exemption.

**Privacy**

- No account, no analytics, no ads. Optional crash reporting is off by default.
- Location and tracks never leave the phone unless the user exports or shares them.
- Network calls go only to the GitHub-hosted data files and the IPMA API; none carry user data.
- A short privacy policy page is hosted on GitHub Pages and linked from Settings and the Play listing.

**Google Play checklist (only if you publish later)**

- [ ] `targetSdk 36` and Android 16 behaviour: edge-to-edge layouts, predictive back.
- [ ] Native libraries support 16 KB memory pages (MapLibre fixed its 16 KB alignment in [11.6.1](https://github.com/maplibre/maplibre-native/releases)); check with APK Analyzer.
- [ ] Foreground service type `location` declared and justified in the Play Console declaration form.
- [ ] Data safety form: location collected, not shared, processed on device, user can delete.
- [ ] Closed test before production (new personal developer accounts must run one; check the current tester count and duration in Play Console).
- [ ] Accessibility: TalkBack labels on all map controls, 48 dp touch targets, AA contrast, text up to 200%.

## Build phases

Five phases, each ending in something you can install and test on your Pixel; give Claude Code one phase at a time and tick every box before the next.

### Phase 0 — Data (repo `vereda-data`)

Goal: the map and catalog files exist and are trustworthy.

- [ ] `build-maps` produces base, contours and DEM PMTiles for Madeira and Porto Santo; they open correctly in the PMTiles web viewer.
- [ ] Contours at 10 m match the DTM (spot-check Pico Ruivo 1,862 m and Pico do Areeiro).
- [ ] `build-catalog` matches 42 of 42 official codes, or the job fails listing the missing ones.
- [ ] `status.json` refreshes every 30 min with `checkedAt`; a broken source page yields `stale: true`, not a wrong state.
- [ ] `manifest.json` lists real file sizes and SHA-256 hashes.

### Phase 1 — Map that works offline

Goal: open the app in airplane mode and see Madeira in detail.

- [ ] Project skeleton with the modules from *App architecture*, CI building a debug APK on every push.
- [ ] Onboarding downloads the Madeira package with progress, pause/resume and checksum.
- [ ] Vereda Topo style renders contours, hillshade, levadas, paths and POIs as in *Map style*.
- [ ] Official and community trails drawn by status; tap opens the peek sheet.
- [ ] Locate-me, compass, scale bar, long-press coordinates (decimal + UTM 28N + elevation).
- [ ] Layers sheet toggles every overlay; Offline maps screen can update and delete packages.

### Phase 2 — Find and plan

Goal: pick a trail, check it, save it.

- [ ] Explore list with chips, filters, sort and map toggle.
- [ ] Trail page with status banner (freshness rule), access card, stats, interactive elevation profile, actions.
- [ ] Search across trails, places and coordinates, accent-insensitive, offline.
- [ ] Me tab: profile, resident switch, Done / To-do / Wishlist / custom lists, x-of-42 progress.
- [ ] To-do reminder the day before a planned date.

### Phase 3 — Walk it

Goal: record and navigate a real hike with the screen off.

- [ ] Recording service with notification controls, filters, auto-pause, crash recovery.
- [ ] Navigate mode with progress, remaining numbers, off-route alert, no false alerts in tunnels.
- [ ] Save screen with trim and "Mark as done" matching.
- [ ] GPX/KML/GeoJSON import and GPX export; backup .zip export and import.
- [ ] Field test passed (see *Testing*).

### Phase 4 — Polish and install

Goal: a finished release build on your own phone. Google Play is not planned for now.

- [ ] Emergency card, IPMA warnings, sunset and daylight-left, pre-hike checklist, disclaimer.
- [ ] All strings in PT-PT and EN; app follows the phone language.
- [ ] Accessibility pass.
- [ ] Signed release APK installed and used on your Pixel for a full hike.

**Later (v1.x):** satellite layer, photos on waypoints, badges and heatmap, German and French, night map style, barometer climb, home-screen widget, Wear OS.

## Testing

Most risk sits in GPS logic, status parsing and offline behaviour, so those get automated tests with recorded fixtures plus three real hikes before release.

| Level | What | Tooling |
| --- | --- | --- |
| Unit (JVM) | GPX round-trip, distance and climb maths, auto-pause, off-route and wrong-way logic, status freshness, search normalisation ("Caldeirão" = "caldeirao"), UTM conversion | JUnit 5, Kotest assertions, Turbine for Flows |
| Pipeline | Status parser against saved copies of the official PDF and pages (open, partial, closed, broken HTML); catalog JSON Schema validation; 42/42 match check | pytest or Kotlin script tests in `vereda-data` CI |
| Database | Migrations, sync never deletes user data, backup export → import gives identical lists | Room testing, instrumented |
| UI | Screens render each `UiState` (loading, empty, error, stale status) | Compose UI tests, screenshot tests (Roborazzi or Paparazzi) |
| End-to-end | Onboarding → download → find PR 6 → save to To-do → navigate with a replayed GPX → stop → marked Done | Maestro flows on an emulator in GitHub Actions |
| Location replay | Debug-only "GPX replay" feeding mock locations at 1× to 20× speed, including a GPS gap for tunnels | Fused provider mock mode |
| Offline | Airplane mode on every screen; interrupted download resumes; corrupted file fails checksum; low storage message | Manual checklist + instrumented |

**Field test before release** — record the full trail, compare with the official distance, and note battery used:

- [ ] PR 8 Ponta de São Lourenço — open sky, exposed coast.
- [ ] PR 6 Levada das 25 Fontes — deep valley and tunnel, GPS gaps.
- [ ] PR 1.2 Achada do Teixeira to Pico Ruivo — altitude and climb accuracy.

Pass mark: distance within 5% of the official figure, no false off-route alerts, under 25% battery for six hours.

## Risks and open questions

The biggest risk is the trail status feed, because it has no official API; everything else has a known fallback.

| Risk or question | Impact | Plan |
| --- | --- | --- |
| Status comes from a PDF and web pages that can change layout or go down (the IFCN site was reported down during a November 2025 storm, per [This Is Madeira Island](https://thisismadeiraisland.com/news/official-warnings-ignored-madeiras-closed-trails-flooded-by-tourists-as-police-move-in/)) | Wrong or missing status | Stale → unknown rule, manual overrides, parser fixtures in CI; ask IFCN / Visit Madeira for a JSON feed or permission |
| Per-trail SIMplifica booking links are unknown | Extra taps for the user | Link the portal for v1; collect deep links in `official.yaml` if they exist |
| Porto Santo terrain licence not confirmed | Can't ship Porto Santo contours | Ask DROTe; fall back to Copernicus GLO-30 |
| Some newer PRs (6.6–6.8, 22, 23, 27, 28) may be missing or incomplete in OSM | Trails without lines | Match report lists them; map them in OSM yourself from your own GPS tracks (also improves OSM for everyone) |
| Package sizes unknown | Download friction on mobile data | Measure in Phase 0; split contours from base so users can skip them if needed |
| Users trusting the app on dangerous terrain | Safety and liability | Disclaimer, official status first, closed trails block Navigate, community trails labelled "not maintained" |
| Official descriptions and photos are copyrighted | Can't copy them | Write short original summaries; use own photos or none in v1 |
| Name "Vereda" may clash with existing apps or marks | Rebrand later | Check Play Store and EUIPO before the listing |

**Decisions (26 Oct 2026)**

- **Name and package id:** working name *Vereda*, package id `pt.vereda.app`. Both can be changed freely until the app is published.
- **Google Play:** not publishing for now. Phase 4 ends with a release build installed on your own phone.
- **Languages:** Portuguese (PT-PT) and English only; German and French are not planned for v1.
- **Official status feed from IFCN:** not now. The app reads the public status list; revisit only if you decide to publish.

## Sources

Pages opened or read for this spec, as of 26 October 2026.

- [Visit Madeira — FAQ (booking, fees, PR 1 price)](https://visitmadeira.com/en/travel-info/faq/)
- [Visit Madeira — Hiking (rules, operator fee, status)](https://www.visitmadeira.com/en/what-to-do/nature-seekers/activities/hiking/)
- [Visit Madeira — PR 6 Levada das 25 Fontes](https://visitmadeira.com/en/what-to-do/nature-seekers/activities/hiking/pr-6-levada-das-25-fontes/)
- [Visit Madeira — Classified trails list and status (PDF)](https://visitmadeira.com/media/rcradgkh/percursos-pedestres-classificados.pdf)
- [Visit Madeira — Parque Ecológico do Funchal trail-running route](https://visitmadeira.com/pt/o-que-fazer/exploradores-da-natureza/atividades/trail-running/parque-ecologico-do-funchal/)
- [IFCN — FAQ on 2026 fees and reservations](https://ifcn.madeira.gov.pt/en/atividades-de-natureza/percursos-pedestres-recomendados/faq-s-taxas-percursos-pedestres-classificados/frequently-asked-questions-fees-classified-walking-routes.html)
- [DN Madeira — Fines for walking closed trails (2023)](https://www.dnoticias.pt/2023/10/25/380569-turistas-identificados-pela-policia-florestal-na-vereda-do-areeiro/)
- [DN Madeira — Grande Rota tender (2023)](https://www.dnoticias.pt/2023/4/14/356039-governo-lanca-empreitada-pedestre-grande-rota/)
- [DN Madeira — Popular unclassified trails (2023)](https://www.dnoticias.pt/2023/10/16/379314-percursos-pedestres-dos-concelhos-afectados-pelos-incendios-reabertos/)
- [dados.gov.pt — DTM of Madeira, 10 m, CC BY 4.0](https://dados.gov.pt/en/datasets/modelo-digital-do-terreno-mdt-da-ilha-da-madeira/)
- [SNIG — DTM Madeira 5 m (2018/2019)](https://snig.dgterritorio.gov.pt/rndg/srv/api/records/cb85978f-afa9-4b9a-b5ed-ae7628372e11)
- [SNIG — DTM Porto Santo service](https://snig.dgterritorio.gov.pt/rndg/srv/api/records/7241ad17-d659-4c95-9101-770fbe345904)
- [SNIG — DROTe attribution terms](https://snig.dgterritorio.gov.pt/rndg/srv/api/records/0bcce93f-12e4-44ec-90b8-4fb42fe49b15)
- [OpenStreetMap — PR14 Levada dos Cedros relation](https://osm.org/relation/2239199)
- [MapLibre Android — PMTiles (local file support)](https://maplibre.org/maplibre-native/android/examples/data/PMTiles/)
- [MapLibre newsletter Feb 2026 — Android 13.0, Vulkan default](https://maplibre.org/news/2026-03-03-maplibre-newsletter-february-2026/)
- [MapLibre Native releases — 16 KB alignment fix](https://github.com/maplibre/maplibre-native/releases)
- [Google Play — Target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-419)
- [Gaia GPS — product site](https://www.gaiagps.com/) (AllTrails blocks automated reading; its patterns are described from the public app)
