# Device support (honest)

"Tested" means run on real hardware by the author. Everything else is marked as such.

| System | TV Doctor | Lume | Lume Cast |
|---|---|---|---|
| Fire TV Stick 4K Max, Fire OS 8.1 | Tested: find, plan, apply in groups of 4 with health check and undo, tune animations | Tested | Tested (AirPlay receiver starts; home and settings) |
| Other Fire TV | Same method, not tested | Not tested | Not tested |
| Google TV, Android TV (Sony, TCL, Philips, Xiaomi, Hisense, Shield) | 8 simulated scenarios pass; real Android TV 14 system image (emulator): detect, plan, apply, undo and the three release APKs verified; `replace-home` (Lume replaces the stock Android TV Home, reversible, checked after the change, survives a reboot) verified on the emulator; Google TV with `launcherx` uses the recipe tried on a real Chromecast with Google TV by rcland12/debloat-tv (turn off launcherx and the setup app; opt-in, verified, reversible) but is not run here; no physical Google TV yet | Not tested | Not tested |
| Samsung Tizen 2015-2026 | Per-year guide only (developer mode, what is and is not possible). Closed system: no apps are removed | n/a | n/a |
| LG webOS 2014-2026 | Per-year guide only | n/a | n/a |
| Roku, VIDAA | Settings and DNS guide only | n/a | n/a |

## Known limits

- Fire OS 8.1 does not let an app run commands on its own TV (the TV's debugging service ignores connections from itself), so the cleanup always runs from a computer. Lume's health page can read the state and point to TV Doctor.
- AirPlay video from Netflix, Disney+ and similar apps is protected by Apple and cannot be shown by third-party receivers.
- Home-screen advertising on Samsung, Hisense and Roku cannot be removed from outside; TV Doctor lists what can be switched off (tracking, interest ads) and what cannot.

## Languages

| Part | Languages |
|---|---|
| TV Doctor screens | English, Italian, Spanish, German, French, Portuguese, Chinese (simplified), Hindi, Japanese, Korean, Arabic (right to left), Russian, Turkish, Indonesian |
| TV Doctor guides per TV year and system | English and Italian; the other languages show the English guide |
| Lume | English, Italian, Spanish, German, French, Portuguese (a few settings texts are still English) |
| Lume Cast | English, Italian, Spanish, German, French, Portuguese |

Translations were written by the author with machine help and have not been reviewed by native speakers. Corrections are welcome.
