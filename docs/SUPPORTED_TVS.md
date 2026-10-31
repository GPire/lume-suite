# Supported TVs

The honest list. It starts short on purpose and grows with every report: see the [threads per brand](https://github.com/GPire/lume-suite/discussions) and *Report your TV* in the app.

| Level | Meaning |
|---|---|
| **Verified on a real TV** | Run end to end on the real device by the maintainer. |
| **Verified on a system image** | Run on the Android TV 14 emulator image, not on a physical TV. |
| **Simulated** | The logic is tested against real package names of that family, not against the firmware. |
| **Guide only** | The system is closed: TV Doctor explains what you can switch off, per year. |
| **Reported by users** | A person told us it worked (link to their thread). None yet. |

| TV / family | Level | Notes |
|---|---|---|
| Amazon Fire TV Stick 4K Max (Fire OS 8.1) | **Verified on a real TV** | The whole cycle: find, clean in groups, restart twice, Lume, Lume Cast, phone remote. Wake from sleep and a very long session are not yet tested. |
| Other Fire TV sticks and Fire TV Edition TVs | Simulated | Same system family; start with the safe group and restart once. |
| Google TV / Android TV (Android 14 image) | Verified on a system image | Detect, clean, undo, Home replacement, reboot, Lume screens. No physical Google TV yet. |
| Sony Bravia, TCL, Xiaomi / Mi TV, NVIDIA Shield, Philips, Hisense (Android), Chromecast with Google TV | Simulated | Brand packs and known pitfalls from community sources; the never-list protects system packages. |
| Samsung (Tizen 2015 to 2026), LG (webOS 2.0 to 25) | Guide only | Menu names per year from the sources listed in [docs/GOOGLE_TV_AND_TIZEN.md](GOOGLE_TV_AND_TIZEN.md); some years are unconfirmed and marked so. |
| Roku, Hisense / VIDAA | Guide only | Settings that reduce ads and tracking; network DNS as the other lever. |

Android 11 and newer TVs that only offer *Wireless debugging* need a computer once for the pairing code; the app finds the TV and asks for it.

**Your TV is not here, or is here as "simulated"?** That is the most useful thing you can tell us: [open the thread for your brand](https://github.com/GPire/lume-suite/discussions).
