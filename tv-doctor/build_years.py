import json
g=json.load(open('closed_tvs.json',encoding='utf-8'))
def Y(ver,it,en,priv,conf,nit="",nen=""):
    return {"os":ver,"reach":{"it":it,"en":en},"privacy":priv,"confidence":conf,"note":{"it":nit,"en":nen}}
OLD=["Impostazioni > Assistenza > Termini e policy > SyncPlus e Marketing: OFF (riconoscimento contenuti e pubblicità basata sulla visione)"]
MID=["Impostazioni > Assistenza > Termini e policy > Servizi di informazioni sulla visione: OFF (riconoscimento contenuti)","Impostazioni > Assistenza > Termini e policy > Pubblicità basata sugli interessi: OFF"]
NEW=["Impostazioni > Assistenza > Termini e privacy > Scelte sulla privacy > Servizi di informazioni sulla visione: OFF (riconoscimento contenuti)","Impostazioni > Assistenza > Termini e privacy > Scelte sulla privacy > Pubblicità basata sugli interessi: OFF"]
AUTO=["Impostazioni > Generale > Funzioni smart > Avvio automatico Smart Hub: OFF"]
C1="confermato da fonte (modello 2018) o da guide concordi"; C2="da verificare sulla tua TV: i menu cambiano per anno, firmware e paese"
sam={
"2015":Y("Tizen 2.3","Modalità sviluppatore possibile (Apps, poi 1-2-3-4-5). Le app vanno costruite a mano: gli installer comunitari non le supportano. Nessuna pulizia di sistema.","Developer mode possible (Apps, then 1-2-3-4-5). Apps must be hand-built: community installers do not support them. No system cleanup.",OLD,C2,"Un UE48JU6100 è stato segnalato funzionante con un pacchetto fatto a mano.","A UE48JU6100 was reported working with a hand-built package."),
"2016":Y("Tizen 2.4","Come il 2015, ma le installazioni falliscono spesso (errore 118); un pacchetto rimasto da un tentativo precedente può bloccare tutto.","As 2015, but installs often fail (error 118); a package left from an earlier attempt can block everything.",OLD,C2,"Nel cassetto app manca Impostazioni: usa il tasto Impostazioni del telecomando.","Settings is missing from the app drawer: use the remote's Settings button."),
"2017":Y("Tizen 3.0","Modalità sviluppatore possibile; le app firmate di terzi sono instabili e il menu Impostazioni delle app può bloccarsi.","Developer mode possible; third-party signed apps are unstable and the apps' Settings menu can freeze.",OLD,C2,"Usa il tasto Impostazioni del telecomando, non quello nel cassetto app.","Use the remote's Settings button, not the one in the app drawer."),
"2018":Y("Tizen 4.0","Modalità sviluppatore e sdb (porta 26101): si leggono e si disinstallano solo le app non di sistema. Provato su un UE55NU7400.","Developer mode and sdb (port 26101): only non-system apps can be listed and removed. Tested on a UE55NU7400.",MID+AUTO,C1,"Non rimuovere pacchetti org.tizen.* o com.samsung.*.","Never remove org.tizen.* or com.samsung.* packages."),
"2019":Y("Tizen 5.0","Come il 2018: modalità sviluppatore e sdb, app non di sistema.","As 2018: developer mode and sdb, non-system apps.",MID+AUTO,C1,"Autorun Last App spesso non riapre l'app sostitutiva.","Autorun Last App often does not reopen a replacement app."),
"2020":Y("Tizen 5.5","Modalità sviluppatore e sdb; da questo anno funzionano gli installer comunitari (TizenBrew) per le app web.","Developer mode and sdb; from this year community installers (TizenBrew) work for web apps.",MID+AUTO,C1),
"2021":Y("Tizen 6.0","Come il 2020. Il menu privacy può chiamarsi Termini e privacy > Scelte sulla privacy.","As 2020. The privacy menu may be called Terms & Privacy > Privacy Choices.",NEW,C2),
"2022":Y("Tizen 6.5","Come il 2021: modalità sviluppatore, app web firmate, nessuna pulizia di sistema.","As 2021: developer mode, signed web apps, no system cleanup.",NEW,C2),
"2023":Y("Tizen 7.0","Per firmare le app serve l'accesso con l'account Samsung. Nessuna pulizia di sistema.","Signing apps needs your Samsung account. No system cleanup.",NEW,C2),
"2024":Y("Tizen 8.0","Come il 2023; il certificato gratuito da riga di comando viene rifiutato (errori 3-15, 3-16): serve quello legato all'account Samsung. Si dice che Samsung prometta 7 anni di aggiornamenti dal 2024 (forum, non annuncio ufficiale).","As 2023; the free command-line certificate is rejected (errors 3-15, 3-16): you need the Samsung-account one. Samsung reportedly promises 7 years of updates from 2024 (forum, not official).",NEW,C2),
"2025":Y("versione non confermata","Nessuna fonte trovata sulle app di terzi per questo anno: solo impostazioni e DNS finché non è provato.","No source found on third-party apps for this year: settings and DNS only until proven.",NEW,C2),
"2026":Y("versione non confermata","Nessun dato: modelli troppo recenti. Solo impostazioni e DNS.","No data: models too recent. Settings and DNS only.",NEW,"nessun dato verificato"),
}
LIVE=["Impostazioni > Generali > Sistema > Impostazioni aggiuntive > Live Plus: OFF (riconoscimento contenuti)","Impostazioni > Generali > Sistema > Impostazioni aggiuntive > Pubblicità: attiva Limita il tracciamento degli annunci e Reimposta ID pubblicitario","Impostazioni > Generali > Sistema > Impostazioni aggiuntive > Impostazioni Home > Promozione Home: OFF","Accordi dell'utente (in Sistema o in Assistenza e privacy, a seconda della versione): togli le voci facoltative, come Informazioni vocali e Pubblicità basata sugli interessi"]
LOLD=["Impostazioni > Tutte > Generali > Informazioni su questa TV > Accordi dell'utente: togli Live Plus e la pubblicità basata sugli interessi, se presenti"]
LC="menu confermati da guide concordi (non da LG): controlla le voci sulla tua TV"
lg={
"2014":Y("webOS 1.0","Nessuna modalità sviluppatore utile per le app di oggi. Solo impostazioni e DNS.","No developer mode useful for today's apps. Settings and DNS only.",LOLD,"da verificare"),
"2015":Y("webOS 2.0","Modalità sviluppatore non confermata per questo anno; gli installer comunitari richiedono webOS 3 o più recente. Solo impostazioni e DNS.","Developer mode not confirmed for this year; community installers need webOS 3 or newer. Settings and DNS only.",LOLD,"da verificare"),
"2016":Y("webOS 3.0","Modalità sviluppatore sì (Content Store, app Developer Mode). L'elenco app per una Home sostitutiva va letto dal computer. Il root dipende dal firmware (webosbrew.org).","Developer mode yes (Content Store, Developer Mode app). The app list for a replacement home must be read from the computer. Root depends on firmware (webosbrew.org).",LOLD,"da verificare"),
"2017":Y("webOS 3.5","Come il 2016.","As 2016.",LOLD,"da verificare"),
"2018":Y("webOS 4.0","Modalità sviluppatore sì; Homebrew Channel dove il firmware è ancora rootabile.","Developer mode yes; Homebrew Channel where the firmware is still rootable.",LOLD+LIVE[:1],"da verificare"),
"2019":Y("webOS 4.5","Come il 2018.","As 2018.",LIVE,LC),
"2020":Y("webOS 5.0","Modalità sviluppatore sì; root solo su firmware vecchi.","Developer mode yes; root only on old firmware.",LIVE,LC),
"2021":Y("webOS 6.0","Modalità sviluppatore sì: le app installate così non vedono le altre app e chiedono accesso una volta. Home ridisegnata: i menu possono cambiare posto.","Developer mode yes: apps installed this way cannot see other apps and ask for access once. Home was redesigned: menus may move.",LIVE,LC,"Provato su una C1 europea da chi ha scritto webos-unclutter.","Tested on a European C1 by webos-unclutter's author."),
"2022":Y("webOS 22","Modalità sviluppatore sì (si rinnova ogni circa 40 giorni, tasto EXTEND). Root non garantito.","Developer mode yes (renew about every 40 days, EXTEND button). Root not guaranteed.",LIVE,LC),
"2023":Y("webOS 23","Come il 2022.","As 2022.",LIVE,LC),
"2024":Y("webOS 24","Come il 2022.","As 2022.",LIVE,LC),
"2025":Y("webOS 25","Come il 2022; l'aggiornamento webOS 25 è gratuito su molti modelli precedenti (da testata).","As 2022; webOS 25 is a free update on many earlier models (press report).",LIVE,LC),
"2026":Y("versione non confermata","Nessun dato: modelli troppo recenti. Solo impostazioni e DNS.","No data: models too recent. Settings and DNS only.",LIVE,"nessun dato verificato"),
}
g["tizen"]["years"]=sam; g["webos"]["years"]=lg; g["webos"]["settings"]=LIVE
g["webos"]["risks"]=["il firmware può ripristinare tutto dopo un aggiornamento","le regole DNS note sono tarate su una C1 europea (webOS 6): altri modelli e paesi chiamano altri server","se il DNS è un computer e si spegne, la TV non risolve più nulla","è impossibile togliere tutta la pubblicità da webOS con le impostazioni"]
with open('closed_tvs.json','w',encoding='utf-8') as f: json.dump(g,f,indent=1,ensure_ascii=False)
