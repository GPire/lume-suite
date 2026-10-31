# TV Doctor and Lume next to the projects that clean TVs

What the well-known projects do, what we took from them (credited in `tv-doctor/NOTICE`), and what is still missing. Based on their public READMEs and guides, read on 2026-10-30; star counts are not quoted because they change.

| | CleanYourTV | debloat-tv (rcland12) | android-tv-debloat-toolkit | TV Doctor + Lume |
|---|---|---|---|---|
| Needs a terminal / coding agent | scripts, or one prompt to an agent | bash scripts | Windows GUI | No: one link, one window, Mac / Windows / Linux |
| Finds the TV on the network | partly | no | pairing helper | Yes (mDNS + SSDP), brand, model and year |
| Fire TV | debloat only | no | no | Yes (tried on a real Fire TV Stick 4K Max) |
| Google TV / Android TV | yes | Chromecast list (real device) | TCL, Onn, Shield | Lists from UAD-NG + debloat-tv + brand sets (TCL, Sony, Xiaomi, Nvidia); tried on an Android TV 14 image, not yet on a physical Google TV |
| Samsung, LG, Roku, VIDAA | no | no | no | Honest per-year guides (no ADB exists on those) |
| Small batches + health check + automatic undo | restore script | undo script | backup | Yes: 4 at a time, rolls the batch back when the TV stops answering |
| Never-touch rules | yes | yes | risk ratings | Fixed list + any name with input / HDMI / live TV, checked by tests |
| Replaces the Home | own 280 KB launcher | Projectivy | no | Lume, opt-in, role first, checked, reversible, reboot-tested on Android TV |
| Notices apps switched back on by an update | no | "run again" | no | Yes, counts them and offers the fix |
| Private DNS against ads | no | no | no | Optional, reversible (AdGuard) |
| Languages in the app | English | English | English | 14 |

## Still missing
- A physical Google TV run (the recipe is from a real Chromecast, ours ran on an emulator image).
- A "keep Assistant / Cast / updates" question before cleaning (the packages are already in the careful tier).
- Lume's memory on a real 1 GB TV. At start, on the Android TV 14 image, it uses about 48 MB (PSS); CleanYourTV quotes 39 MB for its launcher. The emulator draws without a GPU, so this is a guide, not a measurement.

## CleanYourTV (the project that grew fastest in this space)

Facts read from its public page on 2026-10-31: created 2026-10-17, about 47,000 stars and 5,900 forks, a website of its own, a community phone app, and a large press list (its own count). It asks you to run an AI coding assistant (Claude Code, Codex, Gemini CLI) with a prompt, or to follow commands by hand; the computer needs Python, a JDK and the Android SDK, because it builds the launcher there. It covers Android TV and Google TV; other systems get guides. GitHub shows no recognised licence file for it.

| | CleanYourTV | Lume Suite |
|---|---|---|
| Who it is for | People with a coding assistant or who are happy with commands | People who want to press one button |
| Needs a coding assistant | Yes (or manual commands) | No |
| Needs Python, JDK, Android SDK | Yes | No (apps for Mac, Windows, Linux; or from the TV alone) |
| Fire TV tested on a real device | Not stated | Yes (Fire TV Stick 4K Max) |
| Phone as a remote, scenes, profiles | No | Yes |
| AirPlay receiver with lyrics | No | Yes (Lume Cast) |
| Undo | Restore script | Undo in the app, per group, with health check |
| Licence | None recognised by GitHub | Apache-2.0 and GPL-3.0, with a licence map |
| Reach | Far ahead: press, website, languages, models | Starting |
| Launcher size | Very small (about 570 KB, its claim) | About 1.6 MB |

We are not competing for the same user as much as for the user it leaves behind: the person who does not have, or does not want, a coding assistant. Where it leads (reach, languages, number of models) we say so and learn from it.

