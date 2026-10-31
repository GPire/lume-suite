# Sharing photos, video and music over AirPlay: what exists, what is missing (2026-10-30)

Sources: the unofficial AirPlay protocol notes (nto.github.io/AirPlay.html), the UxPlay and RPiPlay READMEs, the jqssun Android receiver, and the receiver's own source. Only what was read is stated; nothing here was tested with an iPhone yet.

## What Lume Cast does today
- Music: AirPlay audio with cover, title, artist, progress, remote control of the phone, and now synced lyrics from LRCLIB.
- Screen mirroring and video (HLS) through UxPlay's native server.
- Unknown requests (everything else) end in HTTP 404.

## Photos: a gap nobody has filled
- The protocol has a photo path: `GET /slideshow-features`, `PUT /photo` (JPEG body, headers `X-Apple-AssetKey`, `X-Apple-Transition`, `X-Apple-AssetAction` = `cacheOnly` or `displayCached`, answer 412 when the picture is not cached), `PUT /slideshows/1` (slide duration, theme, playing/stopped), `POST /stop`, and plist `/event` messages.
- UxPlay and RPiPlay describe themselves as mirroring and audio receivers, and the Android receiver built on UxPlay lists mirroring and HLS video only: none documents photo support.
- Lume Cast advertises feature bits that include Photo and Slideshow (`0x5A7FFEE6`, inherited from UxPlay) while its server has no `/photo` route. If the Photos app lists the TV and then fails, that is a visible bug for a user. Needs a test with an iPhone; if confirmed, either clear those bits or implement the path.
- Doing it properly would be a real differentiator: a photo screen with a slow, gentle zoom, soft cross-dissolves between photos, the painting wall as the idle background, and the slide duration and theme the phone asks for.

## Video
- Protocol endpoints a receiver needs: `/play`, `/scrub` (GET and POST), `/rate`, `/playback-info`, `/setProperty`, `/getProperty`, `/stop`, plus `/event` states (loading, playing, paused, stopped). Lume Cast has `/play` and `/playback-info` through UxPlay; the rest was not audited.
- UxPlay documents no support for the AirPlay 2 video protocol and none planned: this is where the HLS player here already goes further than the base project.

## Music
- Queue and "Up next" are not part of the AirPlay audio stream a receiver gets; the phone keeps the queue. Skip, previous and play/pause already reach the phone (DACP). Showing the next song would need a metadata source the protocol does not give.
- Lyrics: LRCLIB (free, no account) gives time-synced lines. When a song has no synced version the screen says so. Synced lines cannot show word-by-word timing: the fill across each line follows an estimate of how long it takes to sing.

## Suggested order
1. Test the Photos app against the TV with an iPhone (does it list the TV, what does it send).
2. If it lists it: implement `/photo` and `/slideshows/1` in the native server, a Photo screen, and `/event`.
3. If it does not: stop advertising the photo bits, and say so in the help text.
