# Ready-to-paste posts

Written in the first person for the maintainer. Read each community's rules first, say it is your own project, post one thing per community, and stay to answer for the first day. No hashtags, no emoji. Change the numbers only if you have newer ones.

## Show HN (Hacker News)

**Title:** Show HN: TV Doctor – switch off what slows and watches you on a Fire TV or Android TV (no command line)

**Text:**
I got tired of a Fire TV Stick that got slower every month and a home screen full of sponsored rows. The fixes I found needed a command line or an AI coding assistant, so I built an app that does it with one button.

TV Doctor finds the TV on your network, recognises it, switches off the packages that slow it or send data out in groups of four, checks the TV still answers after each group (and puts the group back if not), and keeps a backup so everything can be undone. Nothing is uninstalled. It also installs Lume (a tvOS-style home screen with a phone remote) and Lume Cast (an AirPlay receiver with synced lyrics).

Honest status: end to end on one real Fire TV Stick 4K Max; an Android TV 14 system image; Samsung, LG and Roku are closed systems and only get guides. In one test the stock home screen dropped frames 69 to 81 percent of the time against 28 percent for Lume (one TV, one test). Apache-2.0 and GPL-3.0, signed releases with checksums. The status page is here: https://github.com/GPire/lume-suite/blob/main/docs/SUPPORTED_TVS.md

What I would like from you: tell us your TV model and what happened, in the threads per brand: https://github.com/GPire/lume-suite/discussions

Repo: https://github.com/GPire/lume-suite

## Reddit: r/fireTV

**Title:** I made a one-button cleaner for Fire TV sticks (no ADB commands, nothing uninstalled, undo included)

**Text:**
My Fire TV Stick 4K Max had become slow and the home screen was mostly ads. I wrote an app for Mac, Windows and Linux that finds the stick, switches off the packages that slow it or send data out (in small groups, with a health check after each, and an undo), and installs a calmer home screen. You can also do it from the TV alone with Downloader.

It is free and open source. It is tested on one real stick, so I am looking for people with other models to tell me what happened. Details and the honest list of what works: https://github.com/GPire/lume-suite. Questions welcome; I will answer today.

## Reddit: r/AndroidTV and r/googletv

**Title:** TV Doctor: clean a Google TV / Android TV without a coding assistant or a command line

**Text:**
There are good projects for debloating Android TVs, but most want you to run an AI coding assistant or paste commands. This is for people who would rather press a button: an app finds the TV (Wi-Fi debugging on Android 11 and newer needs a computer once for the pairing code), plans the clean-up per brand, applies it in checked groups, and can undo. There is also a home screen and a phone remote. Brand packs for Sony, TCL, Xiaomi, Nvidia. I only have a Fire Stick, so I need real reports for Google TV models: https://github.com/GPire/lume-suite/discussions

## Reddit: r/privacy (and r/degoogle)

**Title:** Smart TVs track what you watch. This switches the trackers off on Fire TV and Android TV, reversibly

**Text:**
Smart TV makers report viewing data and show ads in the home screen. For Fire TV and Android TV there is a way to switch the trackers and ad services off without root; it is usually a command-line job. TV Doctor does it in small checked groups with an undo, never uninstalls, and says what it did. For Samsung, LG and Roku, which are closed, it lists the settings that reduce tracking per model year and shows how to block ad domains at the router. Source and licence map: https://github.com/GPire/lume-suite

## X, Threads and Mastodon (one post)

Your TV got slower and keeps showing ads. I made a free app that finds it, switches off what slows it and watches you, and installs a calm home screen with an Apple TV-style phone remote. One button, nothing deleted, undo included. Open source. https://github.com/GPire/lume-suite

## LinkedIn

I built and open-sourced a tool that cleans smart TVs (Fire TV, Google TV, Android TV) without a command line: it recognises the model, switches off what slows it or sends data out in checked groups, and can undo everything. The interesting part was making something safe enough for non-technical people, and being honest about what is verified: https://github.com/GPire/lume-suite/blob/main/docs/SUPPORTED_TVS.md

## Italiano (forum, gruppi Telegram e Facebook su Fire TV e Android TV, Reddit r/Italia, r/ItaliaPersonalFinance no)

**Titolo:** Ho fatto un'app che pulisce Fire TV e Android TV con un solo pulsante (senza comandi, con annulla)

La mia Fire TV Stick era diventata lenta e piena di pubblicità. Ho scritto un'app per Mac, Windows e Linux che trova la chiavetta, spegne ciò che la rallenta o manda dati fuori (a gruppi, controllando la TV dopo ogni gruppo, con annulla) e installa una Home più calma con telecomando sul telefono. Si può fare anche dalla sola TV con Downloader. Gratis e open source. L'ho provata su una sola chiavetta vera: cerco chi ha altri modelli per dirmi com'è andata. https://github.com/GPire/lume-suite

## Español

**Título:** Hice una app que limpia Fire TV y Android TV con un solo botón (sin comandos, con deshacer)

Mi Fire TV Stick se había vuelto lenta y llena de publicidad. Escribí una app para Mac, Windows y Linux que encuentra el dispositivo, apaga lo que lo ralentiza o envía datos (por grupos, comprobando la TV tras cada uno, con deshacer) e instala un inicio más tranquilo con mando en el móvil. Gratis y de código abierto. Solo la he probado en un stick real: busco gente con otros modelos que me cuente qué pasó. https://github.com/GPire/lume-suite

## Português

**Título:** Fiz uma app que limpa Fire TV e Android TV com um só botão (sem comandos, com anular)

A minha Fire TV Stick ficou lenta e cheia de publicidade. Escrevi uma app para Mac, Windows e Linux que encontra o dispositivo, desliga o que o abranda ou envia dados (em grupos, verificando a TV após cada um, com anular) e instala um ecrã inicial mais calmo com comando no telemóvel. Grátis e de código aberto. Só a testei num stick real: procuro quem tenha outros modelos para me contar como correu. https://github.com/GPire/lume-suite

## Press e-mail (10 lines)

Subject: A smart-TV cleaner for people who do not use a command line (open source)

Hello, you covered CleanYourTV, so I thought you might like the other side of the same problem: people who do not have, or do not want, a coding assistant. TV Doctor finds the TV, switches off what slows it and sends data out in checked, reversible groups, and installs a calm home screen with a phone remote and an AirPlay receiver. It is free, open source, and honest about what is tested (one real Fire TV Stick, an Android TV image; closed systems get guides). The press kit has pictures and facts: https://github.com/GPire/lume-suite/blob/main/docs/PRESS.md. I can send a review unit setup or a 5-minute walkthrough. Thank you for your time.

## After each post

1. Reply to every comment within the hour for the first day.
2. Copy useful replies into the matching discussion thread so the next person finds them.
3. Add a row to SUPPORTED_TVS.md for every real report and thank the person.
4. Do not argue, do not delete, thank for bugs. A bad report handled well travels better than a good one ignored.
