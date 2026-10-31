# Press kit

For journalists, bloggers and video makers. Everything here can be used freely with a link back; nothing needs asking.

## In one sentence

**TV Doctor finds your smart TV, installs a calm home screen and an AirPlay receiver, and switches off, safely and reversibly, what slows it down or watches you. One app, no command line, no coding assistant, no account.**

## Three paragraphs

*The problem.* Smart TVs and streaming sticks get slower with age, fill their home screen with sponsored rows and send a stream of data about what you watch. Most people never find the settings, and the common fixes need a command line or an AI coding assistant.

*The project.* Lume Suite is three free, open-source programs: **TV Doctor** (a desktop app that recognises the TV and cleans it in small, checked groups, with an undo), **Lume** (a tvOS-style home screen for Fire TV, Google TV and Android TV, with a phone remote) and **Lume Cast** (an AirPlay receiver with synced lyrics). Nothing is uninstalled; every change can be undone.

*The honest part.* Tested end to end on one real Fire TV Stick 4K Max and an Android TV 14 system image; Samsung, LG and Roku are closed systems and get guides only. The project says what is verified and what is not, in [docs/SUPPORTED_TVS.md](SUPPORTED_TVS.md).

## Facts you can quote

- Free, no account, no analytics; source on GitHub; Apache-2.0 (Lume, TV Doctor, Gallery) and GPL-3.0 (Lume Cast). Licence map: [LICENSE.md](../LICENSE.md).
- In one test on a Fire TV Stick 4K Max, the stock home screen dropped frames 69 to 81 percent of the time against 28 percent for Lume, and about 25 MB more memory was free after cleaning. One TV, one test.
- Apps are only switched off, never uninstalled; every change is backed up and undoable.
- Downloads are signed with the project's key and listed with SHA-256 checksums.
- Interface and README in 14 languages; website in 6.

## Assets

Banner: `docs/img/banner.png` (1280x640). Screens: `docs/img/lume-home.jpg`, `lume-control_center.jpg`, `lume-search.jpg`, `lume-markets.jpg`, `cast-settings.jpg`, `phone-remote.jpg`. Icons: `branding/*.svg`. All taken from a real Fire TV Stick 4K Max.

## Contact

Maintainer: [@GPire](https://github.com/GPire). Questions, a review unit to try, or a correction: open a [discussion](https://github.com/GPire/lume-suite/discussions) or write to the address in [SECURITY.md](../SECURITY.md).
