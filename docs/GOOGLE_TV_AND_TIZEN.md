# What really works on Google TV and Samsung Tizen (checked October 2026)

Read from the projects' own pages and from community reports; where a claim was not confirmed, it says so. Nothing here was run on a Samsung or a Google TV by us: the only real device so far is a Fire TV Stick 4K Max.

## Google TV and Android TV

| What | Works? | Notes |
|---|---|---|
| Turn off preinstalled apps (ads, telemetry) | Yes, over ADB, without root | Apps are turned off, never removed, so everything can be undone. TV Doctor does it in small groups with a health check after each. |
| Replace the Home screen | Yes, on most sets | Install the new launcher first, then turn off the old one, or the TV can show a black screen. TV Doctor never turns off the Home. TCL, Philips and Chromecast with Google TV each have a known trap (see `tv-doctor/known_issues.json`). |
| Install Lume, Ambient Cast and the Gallery | Yes (Android 7 or newer) | TV Doctor installs them from this repository's release, after checking each file's SHA-256. |
| Keep ADB open | **Not by itself on Android 14** | Wireless debugging picks a random port and closes it on restart: a security measure, not a fault. Switch it back on after a restart (the pairing stays), or run `adb tcpip 5555` from a paired computer. A third-party app (adb-auto-enable) automates this, but says it was tested only on Chromecast with Google TV. |

Reference projects: CleanYourTV (MIT, phone-only, lists for 47 brands), TV Slim (Apache-2.0), Free My TV (MIT), tvOS Launcher (Apache-2.0), Aerial (MIT).

## Samsung Tizen (2015 and later)

| What | Works? | Notes |
|---|---|---|
| Turn off or remove Samsung's own apps | **No reliable method found** | The TV's debug port (26101) exists in developer mode, but no trustworthy guide says which system apps are safe to remove. TV Doctor does not try. |
| Privacy and ad settings, DNS blocking | Yes | Per year, in `tv-doctor/closed_tvs.json`. DNS blocking cannot remove rows from the Home screen, and apps with fixed addresses bypass it. |
| Ads inside YouTube | Yes, in developer mode | **TizenBrew** (GPL-3.0, about 1.7k stars) installs web modules without Tizen Studio; its **TizenTube** module (GPL-3.0, about 2.4k stars) removes YouTube ads and skips sponsored segments. Third-party projects, not ours. |
| Another Home screen | Yes on many sets | **Free My TV** (MIT): developer mode, then its installer. It runs as an app, not as the real Home, and 2015-2016 sets cannot use the TizenBrew installer. |
| Run Lume, Ambient Cast | **No** | They are Android apps. Tizen needs web apps: a "Lume for Samsung" would be a separate project, and does not exist yet. An Android streaming stick on HDMI gives the full Lume experience on any TV. |

## So, for a TV that is closed

TV Doctor says plainly what is and is not possible for the model and year, guides the settings that matter, and points to the third-party tools above. The cleanest answer for a closed TV is an inexpensive Fire TV or Google TV stick on HDMI: Lume then runs on the stick, and the TV's own Home is never shown.
