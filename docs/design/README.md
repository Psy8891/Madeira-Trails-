# UI design

The live, editable designs are on the **Vereda UI design canvas**:
https://claude.ai/artifact/Cr57XLNKvr6EDJ9Pr7B77g (private to the owner).

The files in this folder are the source of each screen, kept here so code can be checked
against them. They are HTML with inline styles, so colours, sizes, spacing and copy can be read
directly. They need the canvas runtime to render, so open the link above to see them.

| File | Screen |
| --- | --- |
| `Main.dc.html` | Map — home, with the trail peek sheet |
| `Layers.dc.html` | Map layers sheet and status legend |
| `Explore.dc.html` | Explore — trail list, chips, filters |
| `Trail.dc.html` | Trail page (PR 6 example) |
| `Navigate.dc.html` | Navigate and record |
| `SaveTrack.dc.html` | Save a recording, mark trail as done |
| `Me.dc.html` | Profile, progress and lists |
| `Todo.dc.html` | To-do list with reminder |
| `Offline.dc.html` | Offline map packages |
| `TopoMap.dc.html` | Shared topo map illustration used by the other screens |

## Design tokens

| Token | Value | Use |
| --- | --- | --- |
| Primary (laurel) | `#1F5F3F` | Main buttons, selected tab, switches |
| Primary tint | `#E4EFE7` | Selected tab pill |
| Ink | `#17201B` | Main text |
| Ink secondary | `#4E5A53` | Secondary text |
| Background | `#F6F4EE` | Screen background |
| Card border | `#ECE9E1` | Cards and dividers |
| Record | `#C2410C` | Record button |
| Open | `#2E9E5B` line · `#1B7A44` text on `#E3F3EA` | Status |
| Partly open | `#E3A008` line · `#8A5300` text on `#FDF0D5` | Status |
| Closed | `#C62828` dashed line · `#B42318` text on `#FDE7E5` | Status |
| Unknown | `#8A8A8A` line · `#5F6368` text on `#EEEFF1` | Status |
| Levada | `#1A8FD6` | Map |
| Community trail | `#5B6B9A` dashed | Map |
| Active route | `#D81B60` | Navigation |
| My recording | `#E4572E` | Map |

Fonts: **Barlow Semi Condensed** (titles, numbers) and **Inter** (body text).
The PR badge uses the yellow-over-red waymark (`#F2C200` / `#D32F2F`) of Portuguese PR trails.
