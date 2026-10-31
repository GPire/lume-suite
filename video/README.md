# The promo video

A 37-second vertical video (1080x1920, 30 fps, English on-screen text, synthesised score) built with [Remotion](https://www.remotion.dev). All the footage is real: it was recorded on a Fire TV Stick 4K Max with `adb shell screenrecord`, and the numbers on screen are the ones `dumpsys gfxinfo` measured with the same key presses on the stock home and on Lume (87% of frames dropped, median 28 ms, against 13% and 9 ms). TV Doctor's own page is recorded while it really cleans the stick.

## Rebuild it

1. Put the stick in its stock state (TV Doctor's *Undo everything*), then record `clipA.mp4` (stock home scrolling) with `adb shell screenrecord`; clean the stick with TV Doctor while `app/scripts/capture_tvdoctor.mjs` records its page (`node scripts/capture_tvdoctor.mjs <url> raw/tvd_frames`), then record `clipB.mp4` (Lume) and `clipC.mp4` (phone pairing and the remote).
2. Convert each clip to 30 fps (`ffmpeg -vf fps=30`) and put them, with `tvd_screen.mp4` made from the captured frames, in `app/public/`.
3. `python3 app/scripts/gen_audio.py app/public/score.wav` makes the score and effects (numpy only, nothing sampled).
4. `cd app && npm install && npx remotion render src/index.ts Promo out/promo-raw.mp4`, then normalise the loudness: `ffmpeg -i out/promo-raw.mp4 -af loudnorm=I=-14:TP=-1.5 -c:v copy out/promo.mp4`.

Raw footage and device snapshots are not in the repository (they hold the TV's own names and are large). The finished video is a release asset: `lume-suite-promo.mp4`.

Honest notes: the TV was set to Italian, so a few words on the TV itself are Italian; "Amazon" and "Fire TV" belong to Amazon and this project is not affiliated with them.
