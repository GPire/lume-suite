# How it fits together

**One rule for all device sizes.** Memory up to 2.2 GB is *essential*, up to 3.3 GB *balanced*, above that *full*. TV Doctor (animation scale), Lume (motion level) and Lume Cast (profile) use the same thresholds, so a small stick gets calm motion everywhere and a big TV gets everything.

**TV Doctor** (`tv-doctor/`): a small local server (127.0.0.1, random port, token) with a one-page interface. Steps: find the TV on the network, recognise it, plan, apply in groups of four apps with a health check after each and automatic put-back, verify, undo. Backups are JSON files. Closed systems get a guide for their year instead of a cleanup. If no `adb` program is present it uses its own client (`pyadb.py`).

**Phone remote** (`lume/assets/present/remote.html`, `RemoteBridge.java`): the page is served by the TV itself on port 8080 after a six-digit pairing; the phone keeps a token. Actions go through the same Home the person uses (keys, search, apps), plus volume, media keys, profiles, scenes and the TV's own voice assistant. Android aborts activity starts from a background app, so opening apps while something else is on screen needs the *Display over other apps* permission (TV Doctor and *Optimize this TV* grant it); typing into other apps and Back from them need Lume's accessibility switch. The page translates itself (it, en, es, de, fr, pt) and keeps its list of TVs in the phone's own storage.

**Updates**: `docs/latest.json` names the current versions; Lume reads it only while its About page is open and never installs anything by itself.

**Lume** (`lume/`): plain Java, no Gradle (`./build.sh`). Region follows the TV's locale (`Region.java`): news sources, markets and film catalogues change with the country; a country without its own pack gets BBC and Guardian.

**Lume Cast** (`ambient-cast/`): Kotlin and native code. Its home screen shows the Wi-Fi band and signal and warns about guest networks, the usual reason an iPhone cannot see the TV.

## Privacy

No account, no analytics. Network requests: weather and city search (Open-Meteo), markets (Yahoo Finance), news (the newsrooms' RSS), film catalogues (JustWatch/TMDB), paintings (Wikimedia), lyrics (lrclib.net, only if turned on).
