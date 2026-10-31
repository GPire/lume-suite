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

## Phone remote and Lume Cast screens (2026-10-31)

| Part | Tested on | Not yet |
|---|---|---|
| Phone remote: pairing, apps, search, volume, media keys, scenes, profiles list | Fire TV Stick 4K Max, driven from a computer over the network | A real phone swipe session; several TVs; Google TV |
| Phone remote: voice assistant button | Starts Alexa's screen on Fire OS, but Alexa only listens from the remote's own button | Google Assistant / Gemini on Google TV |
| Phone remote: typing into other apps | Needs Lume's accessibility switch; the check that it refuses cleanly without it passed | Typing with the switch on, in a real search box |
| Lume Cast: connected banner, options hint, redesigned Settings, Help, Health, About | Fire TV Stick 4K Max (pictures taken on the TV) | Mirroring and music with the new screens in use |

## Advertising and memory, on every Android TV (2026-10-31)

| What | Fire TV (Stick 4K Max) | Google TV / Android TV |
|---|---|---|
| Personalised ads off (`secure limit_ad_tracking = 1`), set by TV Doctor and by Lume's guided cleaning after the install | **Verified**: it is exactly what the *Interest-based ads* switch in Fire OS changes (found by comparing all system settings before and after pressing it) | The standard Android key that apps are asked to respect; **not verified on a real Google TV**; Google's own ad-ID switch lives in Google's apps |
| Other privacy switches in Fire OS (usage data, app usage data, Amazon Photos) | Off here; they are stored by Amazon's apps, not in system settings, so no script can set them: TV Doctor switches off the apps behind them | Not applicable |
| Memory freed automatically by Lume when it is low and nothing is playing (Settings > Optimize this TV, or the first-run page) | Runs; **no measurable gain seen** in a short test on a 1.7 GB stick (Android already manages cached apps) | Same code; not tested |

