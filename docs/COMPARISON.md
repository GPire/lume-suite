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
