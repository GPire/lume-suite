# Licences

This repository holds several parts. Each part has its own licence, kept in its folder.

| Folder | What it is | Licence | Notes |
|---|---|---|---|
| `lume/` | Home screen for Android TV, Google TV, Fire TV | Apache-2.0 (`lume/LICENSE`) | Derived from Hume Launcher by Petrobot; modified. See `lume/NOTICE`. |
| `ambient-cast/` | AirPlay and DLNA receiver | GPL-3.0 (`ambient-cast/LICENSE`) | Derived from CastBay 1.8.9 by weenas; modified. The full source of every build must be offered to whoever receives the app. Bundles UxPlay (GPL-3.0), OpenSSL and other libraries under their own licences in `ambient-cast/third_party`. |
| `ambient-gallery/` | Painting screensaver and gallery | Apache-2.0 (`ambient-gallery/LICENSE`) | New code. Paintings are fetched at run time from Wikimedia Commons; each keeps its own licence. |
| `tv-doctor/` | Automatic TV cleaner and guide | Apache-2.0 (`tv-doctor/LICENSE`) | New code. No GPL data is shipped (see below). |

TV Doctor does not ship any GPL data. The package list from Universal Android Debloater Next Generation (GPL-3.0) is downloaded by the user's own computer the first time it is needed, and is never stored in this repository.
