# Launch readiness (candid)

Status on 2026-10-30. "Done" means built and checked by the author. Nothing here is marketing: it is the list of what could go wrong on a global launch.

## 1. Decisions (taken on 2026-10-30, with reasons)

| Decision | Choice | Why |
|---|---|---|
| Licence of TV Doctor | Apache-2.0 | Open, with a patent grant, same as Lume: anyone can try, translate and port it. No GPL data is shipped, so nothing forces another licence. |
| Application ids | `org.lumesuite.launcher`, `org.lumesuite.cast`, `org.lumesuite.gallery` | Tied to the project, not to a person. They cannot change after a store release, so they were fixed before the first public build. |
| Repository name | `lume-suite` | Says what it is; the three parts live in one place. |
| Credits | Kept as in `NOTICE.md` | They are licence obligations (Apache-2.0 and GPL-3.0), not a choice. |

Still open: a trademark search for "Lume" and "Ambient Cast" (only a quick web search was done; a minimal launcher called "Lumen" exists); the release signing key (the APKs here use a throw-away debug key, and users cannot update across signatures); a monitored e-mail for security and legal requests.

## 2. Legal and intellectual property

- **Apple names.** AirPlay, Apple Music, Apple TV and tvOS are trademarks of Apple Inc. This project is not affiliated with or endorsed by Apple. The look is inspired by tvOS (glass, focus lift, large type); no Apple logo, icon, font or asset is used (the typeface is Inter). Do not use these names in product or repository names.
- **GPL-3.0 (Ambient Cast, built on CastBay and UxPlay).** The full source of every published build must be offered, and the licence must be reachable inside the app (it is, under Open-Source Licenses). Keep `ambient-cast/` in sync with every release.
- **Apache-2.0 (Lume, from Hume Launcher).** The NOTICE file must keep the original copyright and state the changes (done).
- **Data providers, each with its own terms.**
  - Yahoo Finance chart endpoints are public but *unofficial*; they can change or be blocked, and their terms do not clearly allow redistribution. The Markets page says so ("may be delayed, not financial advice"). For a large launch, offer a second provider (for example one with a user-supplied key).
  - JustWatch and TMDB: unofficial GraphQL for JustWatch; TMDB requires an attribution notice and has its own commercial terms. Review before a commercial launch.
  - Open-Meteo: free for non-commercial use with attribution (CC BY 4.0). The source name is shown in the weather panel.
  - News: only headline, summary and the picture the newsroom publishes in its own RSS feed, with the source and age shown. Some publishers restrict commercial reuse of feeds.
  - Paintings from Wikimedia Commons: each work has its own licence; only public-domain works should ship in the catalogue (not yet audited, see section 4).
- **IP-based location.** Automatic weather location asks a public IP-geolocation service (see PRIVACY.md).

## 3. Safety of TV Doctor

Done: apps are only turned off, never uninstalled; a never-list of system packages; groups of four with a health check and automatic put-back; every change undoable from a stored backup; a local server bound to 127.0.0.1 with a token and host check.

Open: only a Fire TV Stick 4K Max was tested on real hardware; Fire OS 8.1 has reports of boot loops after aggressive disabling, so every other model must start with the most conservative group and a restart check. Add a plain warranty disclaimer in the licence.

## 4. Quality and testing

- Automated: 8 simulated TV scenarios and a protocol test for the built-in ADB client. None for Lume or Ambient Cast.
- Not done: a test matrix across Google TV and Android TV models; AirPlay tests with several iPhone and iOS versions; the CI workflows have never run on GitHub.
- Not audited: which paintings the gallery can fetch and their licences; right-to-left layout in Lume (only TV Doctor has it); screen-reader support.

## 5. Distribution reality

- GitHub releases (sideload) work today. Amazon Appstore and Google Play each have launcher and accessibility-service policies; Lume as the Home app on Fire OS relies on an accessibility switch the person must turn on themselves, which stores may question.
- Lume has no update mechanism yet (Ambient Cast's was removed on purpose because it pointed at the original project). Plan one that checks this repository's releases and shows the checksum.

## 6. Support

README in 14 languages; issue template for TV reports; no website, no triage process, no translation review by native speakers. Decide who answers issues and how fast before announcing.

## 7. Screen checks (added 2026-10-30)

Fire OS blanks the system screenshot, so `lume/tools/screens.sh` (and the `--es walk` hook in `Shot.java`) draws each screen from inside the app. Screens checked this way so far: Home (Scene look), Control Center, Recent Apps, Health, Markets, News, Weather cities, forecast panel, AirPlay, Search, Settings (7 pages), Setup (2 pages). Defects found and fixed this way: a squeezed last button, an overflowing panel, clipped list rows and text, a label touching its value, unequal cards, a keyboard row beyond the safe margin, untranslated labels. Not yet checked: profile picker, long-press menus, folders, the Gallery, Ambient Cast's own screens, right-to-left layout.
