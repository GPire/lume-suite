# Licences

This repository holds several parts. Each part has its own licence, kept in its folder.

| Folder | What it is | Licence | Notes |
|---|---|---|---|
| `lume/` | Home screen for Android TV, Google TV, Fire TV | Apache-2.0 (`lume/LICENSE`) | Derived from Hume Launcher by Petrobot; modified. See `lume/NOTICE`. |
| `ambient-cast/` | AirPlay and DLNA receiver | GPL-3.0 (`ambient-cast/LICENSE`) | Derived from CastBay 1.8.9 by weenas; modified. The full source of every build must be offered to whoever receives the app. Bundles UxPlay (GPL-3.0), OpenSSL and other libraries under their own licences in `ambient-cast/third_party`. |
| `ambient-gallery/` | Painting screensaver and gallery | Apache-2.0 (`ambient-gallery/LICENSE`) | New code. Paintings are fetched at run time from Wikimedia Commons; each keeps its own licence. |
| `tv-doctor/` | Automatic TV cleaner and guide | Apache-2.0 (`tv-doctor/LICENSE`) | New code. No GPL data is shipped (see below). |

TV Doctor does not ship any GPL data. The package list from Universal Android Debloater Next Generation (GPL-3.0) is downloaded by the user's own computer the first time it is needed, and is never stored in this repository.

## What is ours, and what we can and cannot claim

Measured on 2026-10-31 by comparing Lume's Java sources with Hume Launcher's (code lines only, no imports or comments):

- **Lume**: about 19,800 lines; about 12,400 of them (63%) are still identical to Hume. The big screens (`MainActivity` 87%, `SearchPanel` 96%, `SettingsActivity` 77%) are mostly Hume's structure with our changes on top. About 5,650 lines are in files that did not exist in Hume. Of those, two (`ControlPanelOverlay`, `RecentAppsOverlay`) are adapted from tvOS Launcher (Apache-2.0).
- **Written for this project, ours alone (Apache-2.0, copyright "Lume Suite contributors")**: all of `tv-doctor/` and `ambient-gallery/`; in Lume the files `AdbClient`, `AirPlayActivity`, `Appearance`, `Assistant`, `Brand`, `Gallery`, `HealthActivity`, `LumeDialog`, `Markets`, `MarketsActivity`, `NewsActivity`, `Optimizer`, `PhoneActivity`, `PhoneLinked`, `RemoteBridge`, `Region`, `SceneClock`, `Shot`, `TvOptimize`, `UiKit`, `UpdateCheck`, `WeatherCitiesActivity` and the phone remote page `assets/present/remote.html`; the web pages in `docs/`; the icons and banners in `branding/`.
- **Lume Cast** (GPL-3.0) is derived from CastBay and UxPlay; we added the lyrics, the Sing mode, the hints and the redesign on top.

What this means, plainly:

1. **We cannot call Lume "all ours".** Most of its code is still Hume's, under Apache-2.0. We may put our copyright on what we wrote and keep Lume under Apache-2.0 (or any terms for our own parts), but the Hume code and its credit stay as they are.
2. **Lume Cast must stay GPL-3.0.** It contains GPL code; only a rewrite from the AirPlay protocol up, with no GPL code, would free it.
3. **The way to make it fully ours is to rewrite the shared parts**: Lume's main screens (about 12,000 lines) without copying Hume. It is possible and can be done one screen at a time, replacing each file and checking it on the TV; it is weeks of work, not a trick. Until a file is rewritten it keeps the original notice.
4. **"Ours" and "open source" are different things.** Fully ours would let us choose any licence, including closed. A closed licence would also lose the main reason people trust and share a tool that sits on their TV, and the stars. The usual path for a project like this is: ours in authorship, open in licence.
