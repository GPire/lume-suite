# Lume

The home screen your TV should have shipped with: calm, glassy, one thing in focus, and tidy by itself.
Lume replaces the stock launcher on Android TV, Google TV and Fire TV. Plain Java, no Gradle, no third-party
runtime libraries, about 1 MB.

Lume is derived from Hume Launcher by Petrobot (Apache-2.0). See NOTICE.

## What it does

- **One focus per screen.** A full-screen carousel with Continue Watching first, a glass dock, a six-across app grid.
- **Adapts to the TV.** The same three profiles as Ambient Cast and TV Doctor: essential (up to 2.2 GB), balanced
  (up to 3.3 GB), full. Calm motion on a stick, everything where there is room. Nothing runs at rest.
- **Tidy by default.** Vendor tools nobody needs on a home screen (early access, profile pickers, system monitors)
  are tucked away on the first run, one step from coming back.
- **Six languages**: Italian, English, Spanish, German, French, Portuguese.
- **Works with** Ambient Cast for AirPlay and with TV Doctor for cleaning a TV safely.

## Build

```sh
./build.sh      # needs JDK 17+, Android SDK build-tools 34.0.0 and platform android-34
```

Licence: Apache-2.0. Inter typeface under the SIL OFL, QR code library by Project Nayuki (MIT).
