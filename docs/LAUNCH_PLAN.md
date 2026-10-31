# How people will find this (and how to help them)

No one can promise a number of stars. What can be done is to make the project easy to try, easy to trust and easy to share, and to put it in front of the people who already have the problem. This is the plan, with what is done and what is for the maintainer.

## What makes a tool like this spread

1. **A promise in one line, with proof.** The README opens with the problem (slow, noisy, watching TV), the result (two minutes, no command line) and one measured number, labelled as one TV, one test.
2. **Nothing to be afraid of.** Nothing is deleted, everything can be undone, the source is open, the files are signed and checksummed, and the privacy page says what leaves the TV (almost nothing).
3. **A first minute that works.** One link on the TV (`gpire.github.io/lume-suite/tv`), one app on the computer, one command. TV Doctor tells the person what it did.
4. **Pictures that move.** A short screen recording of the Home, the phone remote swiping and the "Phone connected" capsule does more than a paragraph. *(For the maintainer: record 20 to 30 seconds on a phone pointing at the TV, put it in `docs/img/` and at the top of the README.)*
5. **A reason to come back.** Releases with short, human notes; the phone remote and scenes; TV reports that make the lists better; a visible list of supported TVs.

## Done in the repository

Clear README in 14 languages with a start-here table; topics and description; Discussions; issue form for TV reports; SECURITY with a real contact; signed release with checksums; one-line installers; pages for phone and TV; honest status and comparison pages.

## For the maintainer, in this order

1. **Set the social preview** (Settings > General > Social preview): upload `docs/img/banner.png`. Links shared on social networks then show it. The API cannot do this.
2. **Record the demo video** (point 4 above), upload it to YouTube and Shorts/Reels/TikTok with the title "Fix a slow Fire TV Stick in 2 minutes (no command line)".
3. **Post where the problem lives** (read each community's rules; say it is your own project; answer every comment the first day):
   - Hacker News, *Show HN: TV Doctor – switch off the spyware and ads on your smart TV (open source)*. Weekday morning US time.
   - Reddit: r/fireTV, r/AndroidTV, r/googletv, r/Chromecast, r/degoogle, r/privacy, r/selfhosted, r/Italia and national TV/tech subreddits. One post per community, written for it, never the same text.
   - XDA Forums (Fire TV and Android TV sections), AFTVnews comments or tips, Italian and Spanish tech forums.
4. **Get listed in "awesome" lists** (open a pull request to each; search GitHub for `awesome android tv`, `awesome fire tv`, `awesome privacy`, `awesome-selfhosted`-style lists and the TV debloat lists that already exist).
5. **Reply to every issue within a day** for the first month. Early responsiveness is what turns a visitor into a contributor.
6. **Translate the posts** into the languages already in the README; a native speaker should read each one.
7. **Add a "works on my TV" list** from the reports, with model and year: people search for their model.

## What would hurt

Overpromising (say "tested on" and list it), hiding the cost of anything, spamming communities, and ignoring a bad report. A TV that breaks because of us is the one story that travels faster than any launch.

## What the fastest-growing projects of 2026 have in common (read from GitHub on 2026-10-31)

Looked at: CleanYourTV (about 47,000 stars in 14 days), AnyPS5 (50,000 since August), Strata (37,000 since September), several AI-tool repositories, and the all-time list (the most starred repository ever has about 558,000; none has reached a million, so a target of "millions" cannot be met by anyone; "tens of thousands" can).

1. **One sentence that names the pain and the result** (*Debloat any Android or Google TV and get an Apple TV-style home*). Ours: *Your TV got slower, louder and nosier. This puts it right in two minutes.*
2. **A hot wave to ride.** CleanYourTV rode vibe-coding and coding agents. Our wave is the opposite and just as strong: *no agent, no command line, no account*, and privacy against smart-TV ads. Say it in the first line.
3. **"Any hardware", "one click", "free", "no account".** The same four promises appear in every fast riser.
4. **A start-here table by situation** ("You have a... go to..."). Done in the README.
5. **README in the languages where the problem is common** (Chinese, Russian, Portuguese (Brazil), Spanish, Japanese). Done for 14; the website for 6 (translations welcome, see the issue).
6. **A page that shows progress and compatibility** (AnyPS5 has a live progress map and one discussion thread per game). Done: [SUPPORTED_TVS.md](SUPPORTED_TVS.md) and one thread per brand.
7. **Social proof, truthfully.** Press count, trending badges and stars come only after they exist; until then the proof is the measured result and the honest status page.
8. **A press kit and a website of its own.** Done: [PRESS.md](PRESS.md). A short own domain (a name people can say and type, like `cleanyour.tv`) is the next step and costs a few euros: the maintainer's call.
9. **A community engine**: discussion threads, good-first-issues, a Discord or similar. Threads and issues are seeded; a chat server is optional.
10. **Moments.** CleanYourTV ran a Halloween edition. Ours: Black Friday (when TV ads and deals peak), "new TV for Christmas", the start of the football season. A small themed wallpaper pack or a one-page guide per moment gives a reason to post again.
11. **One viral piece of content.** The reel that started CleanYourTV was a before/after on a TV. Ours, in three 20-second cuts: (shot lists, texts and titles are in [VIDEO_KIT.md](VIDEO_KIT.md)) (a) a Fire Stick home screen stuttering, then Lume, same stick; (b) an iPhone sending a song to the TV with lyrics, then the phone swiping the TV; (c) a TV settings screen with the "ads and tracking" switches, and the one-button clean. Hooks to test: *"This stick was dying. Two minutes later."* / *"Your TV is watching you. Here is the off switch."* / *"Use your phone as the Apple TV remote on any Fire TV."*

## 90-day attack plan (competitor method)

| Weeks | Move | Done by |
|---|---|---|
| 1 | Record the three videos; set the social preview; buy a short domain and point it to the Pages site | maintainer |
| 1 | Show HN and the TV communities (see above); reply to every comment the same day | maintainer |
| 2 | Offer a review unit or a walkthrough to 10 tech sites and TV channels that covered CleanYourTV (use the press kit) | maintainer |
| 2 to 4 | Turn the first 30 reports into SUPPORTED_TVS.md rows; thank each person in the release notes | project |
| 4 | Black Friday guide: "turn off the ads before the new year" (one page, one video) | project |
| 4 to 8 | Brand packs from reports (Hisense, Philips, Panasonic...); Google TV real-device pass | contributors |
| 8 to 12 | Rewrite Lume's shared screens so the code is fully ours; second language wave for the website | project |

Watch: stars per day, forks, issues opened by strangers, downloads of each file (`gh release view`), search impressions for "debloat fire tv" and "fire tv slow". Review every Monday and do more of what moved.

