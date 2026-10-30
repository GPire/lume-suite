# Lume Suite

[English](README.md) · [Italiano](README.it.md) · [Español](README.es.md) · [Deutsch](README.de.md) · [Français](README.fr.md) · [Português](README.pt.md) · [简体中文](README.zh.md) · [हिन्दी](README.hi.md) · [日本語](README.ja.md) · [한국어](README.ko.md) · [العربية](README.ar.md) · [Русский](README.ru.md) · [Türkçe](README.tr.md) · [Bahasa Indonesia](README.id.md)

A calmer TV. Three parts that work as one:

- **TV Doctor**: plug it in, press one button, and your TV is cleaned and tuned. It recognises the brand, model, year and system, removes what slows or watches you (reversibly), and tells you, step by step, how to switch on what your TV needs. No coding agent, no command line.
- **Lume**: a home screen for Android TV, Google TV and Fire TV. A big glass clock, a dock of sharp app cards, Control Center, Recent Apps, markets, news, weather in several cities, a health page, three looks (Liquid Glass, Graphite, High contrast), and full control from a phone. Adapts to the power of the TV and to the country it is in.
- **Ambient Cast**: an AirPlay receiver with Apple Music-style lyrics and screen mirroring, so an iPhone can show music and video on the TV.

## Install: no coding agent, no command line

**One link for everyone: https://gpire.github.io/lume-suite/** (it picks your computer, shows the TV address to copy).

**From a computer (any TV on your Wi-Fi)**
1. Open the [latest release](https://github.com/GPire/lume-suite/releases/latest) and download **TV-Doctor.zip**. Unzip it.
2. Double-click **TV Doctor.command** (Mac), **TV Doctor.bat** (Windows) or run `./tv-doctor.sh` (Linux). Your browser opens.
3. Press **Find my TV**. It recognises the brand, model, year and system, installs **Lume, Ambient Cast and the Gallery** on Android-based TVs (each file checked against its SHA-256 first), shows what it would turn off, and waits for your OK. Every change can be undone from the same page.

**From the TV alone (Fire TV, Android TV, Google TV)**
1. Install the free **Downloader** app from your TV's store.
2. In Downloader enter `github.com/GPire/lume-suite/releases/latest/download/lume-launcher.apk` and install. Repeat with `ambient-cast.apk` if you want AirPlay.
3. Open **Lume** and follow its setup. Cleaning the TV (turning off apps that slow it) needs a computer once: on a Fire TV, the TV cannot do it to itself.

**Samsung, LG, Roku, VIDAA** are closed systems: TV Doctor tells you what is and is not possible for your model and year. See [docs/GOOGLE_TV_AND_TIZEN.md](docs/GOOGLE_TV_AND_TIZEN.md). An HDMI stick gives these TVs the full experience.

Nothing is installed on your computer, and nothing is uninstalled from your TV: apps are only turned off, never removed.

Release files are signed with this project's key (certificate SHA-256 `09:9C:A1:B2:91:12:0A:BF:2C:B2:EB:79:9B:76:81:58:3D:3C:52:E4:00:91:1B:A0:83:3F:2A:CA:A1:92:A7:B6`) and listed with their checksums in `SHA256SUMS`.

## What works today (honest status)

| Part | Tested on real hardware | Notes |
|---|---|---|
| TV Doctor on Fire TV | Fire TV Stick 4K Max (Fire OS 8.1) | Full cycle: find, plan, apply in safe groups, check, undo |
| TV Doctor on Google/Android TV | Simulated scenarios only | 8 simulated brands pass; no real device yet |
| TV Doctor on Samsung, LG, Roku, VIDAA | Guides only | These systems are closed: TV Doctor explains what is and is not possible, per year |
| Lume | Fire TV Stick 4K Max | Also meant for Android and Google TV; not yet tested there |
| Ambient Cast | Fire TV Stick 4K Max | AirPlay music and mirroring need testing with more iPhones |

Details in [docs/DEVICE_SUPPORT.md](docs/DEVICE_SUPPORT.md).

## Languages

The interface is available in English, Italian, Spanish, German, French and Portuguese. More are on the way; see [CONTRIBUTING.md](CONTRIBUTING.md) to help translate.

## Before you rely on it

This is early software, tested on one real device. Read [docs/LAUNCH_READINESS.md](docs/LAUNCH_READINESS.md) for the honest list of risks and [docs/PRIVACY.md](docs/PRIVACY.md) for what leaves your network. TV Doctor only turns apps off and can undo everything, but it is provided without warranty.

## Licences

Each part has its own licence, listed in [LICENSE.md](LICENSE.md). Lume is derived from Hume Launcher by Petrobot (Apache-2.0) and Ambient Cast from CastBay by weenas (GPL-3.0); credits in [NOTICE.md](NOTICE.md).
