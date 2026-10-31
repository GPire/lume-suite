#!/usr/bin/env python3
"""tvdoctor: recognises an Android-family TV, plans a cleanup by risk tier, applies it reversibly.

    tvdoctor.py detect                 what TV is this (brand, model, Android/Fire OS, RAM, storage, launcher)
    tvdoctor.py plan  [--tier T]       dry run: what would change, with why. Changes NOTHING.
    tvdoctor.py apply --tier T --yes   backs up first, then disables (never uninstalls) the planned packages
    tvdoctor.py undo  [--file F]       turns back on everything a backup disabled (default: the latest)

Tiers: safe (recommended, no known side effect), caution (advanced: privacy/telemetry, may touch features).
Everything marked Expert/Unsafe by the community list is never touched. Packages the TV needs to boot, show
its Home, take remote input or update itself are never touched, whatever any list says.
Package data: Universal Android Debloater Next Generation (GPL-3.0), plus a curated Fire OS set.
Works on the Android family (Android TV, Google TV, Fire OS, Shield...). LG webOS, Samsung Tizen, Roku and
VIDAA have no ADB: see the note printed by `detect` on those.
"""
import argparse, json, os, socket, subprocess, sys, time, urllib.request, re

FROZEN = getattr(sys, "frozen", False)   # the double-click app (PyInstaller): read-only files inside, the person's data outside
HERE = getattr(sys, "_MEIPASS", None) or os.path.dirname(os.path.abspath(__file__))
USER = os.path.join(os.path.expanduser("~"), ".tvdoctor") if FROZEN else HERE
os.makedirs(USER, exist_ok=True)
CACHE = os.path.join(USER, "uad_lists.json")
BUNDLED_UAD = os.path.join(HERE, "uad_lists.json")   # shipped inside the app, so the first run works offline
BACKUPS = os.path.join(USER, "backups")
UAD_URL = ("https://raw.githubusercontent.com/Universal-Debloater-Alliance/"
           "universal-android-debloater-next-generation/main/resources/assets/uad_lists.json")

# Never touched, whatever any list says: boot, Home, input, settings, updates, our own apps.
NEVER_EXACT = {
    "android", "com.android.systemui", "com.android.settings", "com.android.shell", "com.android.bluetooth",
    "com.android.phone", "com.android.vending", "com.google.android.gms", "com.google.android.gsf",
    "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx", "com.google.android.leanbacklauncher",
    "com.google.android.tungsten.setupwraith", "com.google.android.tv.remote.service", "com.google.android.inputmethod.latin",
    "com.amazon.tv.launcher", "com.amazon.platform", "com.amazon.firelauncher", "com.amazon.firehomestarter",
    "amazon.fireos", "com.amazon.fireinputdevices", "com.amazon.device.software.ota", "com.amazon.tv.settings.v2",
    "com.amazon.vizzini", "com.amazon.whisperlink.core.android", "com.amazon.tcomm",
    "com.amazon.venezia", "com.amazon.dcp", "com.android.onetimeinitializer", "com.amazon.tv.livetv",
    "com.tcl.tv", "com.tcl.setupwizard", "org.droidtv.homeintentresolver", "org.droidtv.tvsystem",
    "com.google.android.katniss.setup", "com.google.android.tv.provision",
    "com.petrobot.hume", "io.github.toolicious.homeonfire", "com.weenas.castbay", "org.lumesuite.gallery", "org.lumesuite.cast",
}
NEVER_PREFIX = ("com.android.providers.", "com.android.inputmethod", "com.android.server.", "com.android.networkstack",
                "com.android.permissioncontroller", "com.android.packageinstaller", "com.google.android.gms.",
                "com.mediatek.", "com.qualcomm.", "vendor.", "android.ext.")

# Fire OS: tested conservative set (ads, shopping, preloaded apps) = safe; telemetry/ACR/ad id = caution.
FIRE_SAFE = {
    "com.amazon.shoptv.firetv.client": "Amazon Shopping on the TV (ads and shopping rows)",
    "com.amazon.shoptv.client": "Amazon Shopping client",
    "com.amazon.ods.kindleconnect": "Kindle connect service",
    "com.amazon.bueller.music": "Amazon Music app",
    "com.amazon.dummy.music": "Amazon Music placeholder",
    "com.amazon.bueller.photos": "Amazon Photos app",
    "com.amazon.minitv.android.app": "Amazon miniTV app",
    "com.amazon.tahoe": "Amazon Kids (FreeTime)",
    "com.amazon.gamehub": "Luna / Game Hub",
    "com.amazon.tv.website_launcher": "Website launcher tiles",
    "com.amazon.smarthomemapviewapp": "Smart Home map app",
    "com.amazon.storm.lightning.tutorial": "Remote tutorial",
    "com.amazon.tmm.tutorial": "Tutorial",
    "com.amazon.tv.releasenotes": "Release notes screen",
    "com.amazon.sneakpeek": "Promotional previews",
    "com.android.printservice.recommendation": "Print service recommendations",
}
FIRE_CAUTION = {
    "com.amazon.tv.acr": "Automatic content recognition: what you watch is reported",
    "com.amazon.hybridadidservice": "Advertising ID service",
    "com.amazon.wifilocker": "Saves Wi-Fi passwords to the Amazon cloud",
    "com.amazon.device.metrics": "Device metrics",
    "com.amazon.client.metrics.api": "Metrics API",
    "com.amazon.wirelessmetrics.service": "Wireless metrics",
    "com.amazon.device.logmanager": "Log manager (uploads logs)",
    "com.amazon.dp.logger": "Data platform logger",
    "com.amazon.perfcollection": "Performance data collection",
    "com.fireos.usagestats.proxy": "Usage statistics proxy",
    "com.amazon.privacypassservice": "Privacy pass service",
}


FIXTURE = None   # a dict describing a fake TV, for tests: props, packages, system, disabled, ram_kb, home


def fake_sh(command):
    f = FIXTURE
    m = re.match(r"getprop (\S+)", command)
    if m: return f["props"].get(m.group(1), "")
    if "grep MemTotal" in command: return "MemTotal: %d kB" % (f["ram_mb"] * 1024)
    if command.startswith("df /data"): return "x x x %d" % (f.get("free_mb", 4000) * 1024)
    if command.startswith("wm size"): return "Physical size: 1920x1080"
    if command.startswith("pm list packages -s"): return "\n".join("package:" + p for p in f["system"])
    if command.startswith("pm list packages -d"): return "\n".join("package:" + p for p in f.get("disabled", []))
    if command.startswith("pm list packages"): return "\n".join("package:" + p for p in f["packages"])
    if command.startswith("pm disable-user"): return "Package %s new state: disabled-user" % command.split()[3]
    if "resolve-activity" in command: return f["home"] + "/.Main"
    if command.startswith("dumpsys meminfo"):
        return "Total PSS by process:\n" + "\n".join("    %s: %s (pid 1 / activities)" % (format(kb, ",") + "K", p) for p, kb in f["ram_kb"].items()) + "\nTotal PSS by OOM adj:"
    return ""


def _builtin_adb(serial, args):
    """No adb program installed: use the built-in pure-Python client (pyadb) for what we need."""
    import pyadb
    host, _, port = (serial or "").partition(":")
    port = int(port or 5555)
    try:
        if args and args[0] == "shell":
            return pyadb.shell(host, args[1], port)
        if args and args[0] == "connect":
            pyadb.connect(args[1].split(":")[0], int(args[1].split(":")[1]) if ":" in args[1] else 5555).close()
            return "connected to " + args[1]
    except (OSError, ConnectionError) as e:
        raise SystemExit(f"la TV non risponde: {e}")
    return ""


def probe(serial, wait=4, wireless=False):
    """Is this TV ready? 'ok', 'auth' (it shows the "Allow debugging?" box and waits for the person) or 'fail'.
    Never blocks for long: the box is shown to the person by the app, not hidden behind a 45 s wait."""
    import shutil
    host, _, port = serial.partition(":")
    port = int(port or 5555)
    if wireless and not shutil.which("adb"):
        return "tools"   # Android 11+ wireless debugging is encrypted: the built-in client cannot, Google's adb can
    if shutil.which("adb"):
        try:
            subprocess.run(["adb", "connect", serial], capture_output=True, text=True, timeout=15)
            st = subprocess.run(["adb", "-s", serial, "get-state"], capture_output=True, text=True, timeout=10)
            out = (st.stdout + st.stderr).lower()
        except Exception:
            return "fail"
        return "ok" if out.strip().startswith("device") else "auth" if "unauthorized" in out else "pair" if wireless else "fail"
    import pyadb
    try:
        pyadb.connect(host, port, wait).close()
        return "ok"
    except socket.timeout:
        return "auth"
    except (OSError, ConnectionError):
        return "fail"


def adb(serial, *args, check=False):
    import shutil
    if not shutil.which("adb"):
        return _builtin_adb(serial, args)
    cmd = ["adb"] + (["-s", serial] if serial else []) + list(args)
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
    if check and r.returncode:
        raise SystemExit(f"adb non risponde: {r.stderr.strip() or r.stdout.strip()}")
    return r.stdout.strip()


def install_apk_text(serial, apk, progress=None):
    """Put an app on the TV (or update it). Uses adb when it is installed, else the built-in client. Returns what the TV answered."""
    import shutil
    if FIXTURE is not None:
        FIXTURE.setdefault("installed", []).append(apk)
        return "Success"
    if shutil.which("adb"):
        r = subprocess.run(["adb"] + (["-s", serial] if serial else []) + ["install", "-r", apk], capture_output=True, text=True, timeout=300)
        return r.stdout + r.stderr
    import pyadb
    host = (serial or "").split(":")[0]
    return pyadb.install(host, apk, int(serial.split(":")[1]) if serial and ":" in serial else 5555, progress)


def install_apk(serial, apk, progress=None):
    return "Success" in install_apk_text(serial, apk, progress)


# What an install failure means, in words for the person (the code is kept after it for a report).
INSTALL_WHY = {
    "UPDATE_INCOMPATIBLE": "c'è già una versione firmata in modo diverso (una versione di prova): toglila dalle Impostazioni > App della TV e riprova",
    "VERSION_DOWNGRADE": "c'è già una versione più recente",
    "INSUFFICIENT_STORAGE": "la TV non ha abbastanza spazio libero",
    "OLDER_SDK": "questa TV ha un Android troppo vecchio",
    "NO_MATCHING_ABIS": "questa TV ha un processore non supportato",
}


def install_why(text):
    m = re.search(r"INSTALL_[A-Z_]*FAILED_([A-Z_]+)|FAILED_([A-Z_]+)", text)
    code = (m.group(1) or m.group(2)) if m else ""
    for k, v in INSTALL_WHY.items():
        if k in text:
            return v
    return ("non riuscita (%s)" % (code or text.strip()[:60]))


def sh(serial, command):
    if FIXTURE is not None:
        return fake_sh(command)
    return adb(serial, "shell", command)


def pick_serial(arg):
    if arg:
        return arg
    lines = [l.split()[0] for l in adb(None, "devices").splitlines()[1:] if l.strip().endswith("device")]
    if len(lines) == 1:
        return lines[0]
    raise SystemExit("serve un solo dispositivo collegato, oppure indicalo con -s IP:5555 (trovati: %s)" % (lines or "nessuno"))


def prop(serial, key):
    return sh(serial, f"getprop {key}")


def detect(serial):
    d = {
        "maker": prop(serial, "ro.product.manufacturer"), "brand": prop(serial, "ro.product.brand"),
        "model": prop(serial, "ro.product.model"), "device": prop(serial, "ro.product.device"),
        "soc": prop(serial, "ro.hardware"), "android": prop(serial, "ro.build.version.release"),
        "sdk": prop(serial, "ro.build.version.sdk"), "fingerprint": prop(serial, "ro.build.fingerprint"),
        "characteristics": prop(serial, "ro.build.characteristics"),
        "fire_os": prop(serial, "ro.build.version.name"),
    }
    mem = sh(serial, "grep MemTotal /proc/meminfo")
    d["ram_mb"] = int(re.sub(r"\D", "", mem) or 0) // 1024
    d["free_mb"] = int((sh(serial, "df /data | tail -1").split() + ["0"] * 4)[3] or 0) // 1024
    d["size"] = sh(serial, "wm size").replace("Physical size: ", "")
    pk = set(l.replace("package:", "") for l in sh(serial, "pm list packages").splitlines())
    d["_pkgs"] = pk
    if d["maker"].lower() == "amazon" or d["fingerprint"].startswith("Amazon/"):
        d["family"] = "fireos"
    elif "com.google.android.apps.tv.launcherx" in pk:
        d["family"] = "google-tv"
    elif "com.google.android.tvlauncher" in pk or "com.google.android.leanbacklauncher" in pk:
        d["family"] = "android-tv"
    else:
        d["family"] = "android-other"
    d["tier"] = "essenziale" if d["ram_mb"] <= 2200 else "equilibrato" if d["ram_mb"] <= 3300 else "pieno"
    return d


def show_detect(d):
    print(f"Marca/modello : {d['maker']} {d['model']}  ({d['device']}, SoC {d['soc']})")
    print(f"Sistema       : Android {d['android']} (API {d['sdk']})" + (f", Fire OS {d['fire_os']}" if d["family"] == "fireos" else ""))
    print(f"Famiglia      : {d['family']}")
    print(f"Memoria       : {d['ram_mb']} MB di RAM -> profilo {d['tier']}; spazio libero {d['free_mb']} MB; schermo {d['size']}")
    notes = {
        "fireos": "Fire OS non cede la Home a un altro launcher: si pulisce e si redirige il tasto Home (Hume / Home on Fire).",
        "google-tv": "Google TV: si possono disattivare i pacchetti e sostituire il launcher.",
        "android-tv": "Android TV: si possono disattivare i pacchetti e impostare un altro launcher.",
        "android-other": "Android generico: la lista della comunita' si applica, ma verifica che sia davvero una TV.",
    }
    print("Nota          : " + notes[d["family"]])
    for n in pitfalls(d): print("Attenzione    : " + n)
    if wireless_note(d): print("Connessione   : " + wireless_note(d))


def load_uad():
    if not os.path.exists(CACHE):
        if os.path.exists(BUNDLED_UAD) and BUNDLED_UAD != CACHE:
            return json.load(open(BUNDLED_UAD, encoding="utf-8"))
        print("scarico la lista UAD-NG (una volta)...", file=sys.stderr)
        urllib.request.urlretrieve(UAD_URL, CACHE)
    return json.load(open(CACHE, encoding="utf-8"))


NO_BENEFIT_PREFIX = ("com.android.theme.", "com.android.traceur", "com.android.protips", "com.android.carrierconfig",
                     "com.android.calllogbackup", "com.android.bluetoothmidiservice")
MIN_RAM_KB = 10_000   # generic list entries are proposed only when they use at least ~10 MB right now


def ram_by_package(serial):
    """Resident memory (PSS, KB) per package from dumpsys meminfo: what each app really costs this TV."""
    out, ram, on = sh(serial, "dumpsys meminfo"), {}, False
    for line in out.splitlines():
        if "Total PSS by process" in line: on = True; continue
        if on and "Total PSS by OOM" in line: break
        m = re.match(r"\s*([\d,]+)K: ([\w.\-]+)", line)
        if on and m:
            pkg = m.group(2).split(":")[0]
            ram[pkg] = max(ram.get(pkg, 0), int(m.group(1).replace(",", "")))
    return ram


# Google TV / Android TV: from rcland12/debloat-tv (MIT), tried on a real Chromecast with Google TV 4K (Android 14).
# Safe = nothing visible depends on them. Caution = a feature goes away (named in the text), always reversible.
GOOGLE_TV_SAFE = {
    "com.google.android.play.games": "Play Games (unused on a TV)",
    "com.google.android.feedback": "Sends feedback and crash reports to Google",
    "com.android.tv.feedbackconsent": "Consent screen for the feedback reports",
    "com.google.android.tv.remotecontrol.logging": "Remote-control usage logging",
    "com.android.printspooler": "Printing (a TV does not print)",
}
GOOGLE_TV_SAFE.update({
    "com.google.android.partnersetup": "Partner setup (one-shot, already done)",
    "com.google.android.onetimeinitializer": "One-time initialiser (already done)",
    "com.google.android.syncadapters.calendar": "Calendar sync (a TV has no calendar)",
    "com.google.android.backdrop": "Old Daydream backdrop screensaver",
    "com.android.adservices.api": "Android ad-services API (ad profiling)",
    "com.android.ondevicepersonalization.services": "On-device ad personalisation",
})
GOOGLE_TV_CAUTION = {
    "com.google.android.tvrecommendations": "Recommendation rows on Google's own Home go empty",
    "com.google.android.leanbacklauncher.recommendations": "Recommendation rows on the old Android TV Home",
    "com.android.dreams.basic": "The basic screensaver",
    "com.google.android.apps.tv.dreamx": "Ambient-mode screensaver with recommendations (falls back to the basic one)",
    "com.google.android.videos": "Google TV store and rentals: the Shop tab and past purchases stop working",
    "com.google.android.katniss": "Google Assistant: the remote's microphone button stops working",
    "com.google.android.youtube.tvmusic": "YouTube Music app",
    "com.google.android.youtube.tvunplugged": "YouTube TV app",
    "com.google.android.marvin.talkback": "TalkBack screen reader (turn back on if anyone needs it)",
}
# Brand extras (promotions, boot ads, telemetry) found on real sets: techpp "debloat Android TV with ADB", TCL/Sony/Xiaomi/Nvidia.
# safe = ads, promotions, telemetry; caution = apps the owner may still use. Inputs, updates and HDMI packages are never listed.
BRAND_SAFE = {
    "tcl": {"com.tcl.bootadservice": "Ads at start-up", "com.tcl.overseasappshow": "App ads", "com.tcl.notereminder": "Reminder pop-ups",
            "com.tcl.messagebox": "Promotional message box", "com.tcl.esticker": "Electronic sticker ads"},
    "sony": {"com.sony.dtv.promos": "Sony promotions", "com.sony.dtv.sonyselect": "Sony Select promo row", "com.sony.dtv.multiscreendemo": "Demo screen",
             "com.sony.dtv.ecodashboard": "Eco dashboard", "com.sony.dtv.smarthelp": "Smart help pop-ups"},
    "xiaomi": {"com.mitv.tvhome.mitvplus": "Mi TV+ rows (PatchWall)", "com.mitv.tvhome.michannel": "Mi channel rows (PatchWall)"},
    "nvidia": {"com.nvidia.stats": "Usage statistics", "com.nvidia.enhancedlogging": "Extra logging", "com.nvidia.feedback": "Feedback reports",
               "com.nvidia.diagtools": "Diagnostic tools", "com.nvidia.shield.remotediagnostic": "Remote diagnostics",
               "com.nvidia.SHIELD.Platform.Analyser": "Platform analyser", "com.nvidia.factorybundling": "Factory bundling"},
}
BRAND_CAUTION = {
    "tcl": {"com.tcl.dashboard": "TCL dashboard", "com.tcl.appmarket2": "TCL app market", "com.tcl.browser": "TCL browser",
            "com.tcl.guard": "TCL guard", "com.tcl.usercenter": "TCL user centre", "com.tcl.tvweishi": "TCL tvweishi service"},
    "sony": {"com.sony.dtv.helpguidecontents": "Help guide contents", "com.sony.dtv.sonytab": "Sony tab"},
    "xiaomi": {"com.mitv.tvhome.atv": "Mi TV home (PatchWall)"},
}
# Needed by the brand even if a list says otherwise (Inputs button, updates, system service).
NEVER_EXACT |= {"com.tcl.suspension", "com.tcl.tvinput", "com.tcl.ttvs", "com.sony.dtv.sonysystemservice", "com.sony.dtv.airplay"}
# Needed by Google TV even if a list says otherwise: DRM keys (HD/4K), the Cast receiver, firmware updates, sign-in pages.
NEVER_EXACT |= {"com.google.android.rkpdapp", "com.google.android.apps.mediashell", "com.google.android.tv.dfuservice",
                "com.google.android.webview", "com.google.android.configupdater"}


# Whatever a list says: anything whose name says it handles inputs, HDMI or live TV breaks the remote or the picture sources.
NEVER_WORDS = ("tvinput", "hdmi", "livetv", "inputmethod", "tvcenter", "inputservice")


# What the owner may want to keep. Each rule is (group, words in the package name); kept groups are left out of the plan.
# keep_default: True where switching it off removes something people use daily (the voice button, casting).
KEEP_GROUPS = [
    ("assistant", ("katniss", "alexa", "vizzini", "voice", "assistant"), True),
    ("cast", ("mediashell", "chromecast", "cast.receiver", "airplay"), True),
    ("games", ("games", "gamehub", "luna"), False),
    ("music", ("music",), False),
    ("shopping", ("shoptv", "shopping"), False),
    ("video", ("youtube.tvunplugged", "videos", "minitv", "tubitv", "netflix", "disney", "amazonvideo"), False),
    ("screensaver", ("dreamx", "dreams", "backdrop", "screensaver"), False),
    ("kids", ("tahoe", "kids"), False),
    ("smarthome", ("smarthome", "homekit"), False),
    ("recommendations", ("recommendations", "tvrecommendations", "sonyselect", "promos", "mitvplus", "michannel"), False),
    ("privacy", ("acr", "feedback", "logging", "adservices", "ondevicepersonalization", "stats", "diag", "analyser", "bootad", "adshow"), False),
]


def group_of(pkg):
    low = pkg.lower()
    for g, words, _ in KEEP_GROUPS:
        if any(w in low for w in words):
            return g
    return None


def is_never(p):
    return p in NEVER_EXACT or p.startswith(NEVER_PREFIX) or any(w in p for w in NEVER_WORDS)


def build_plan(serial, d, tier):
    uad = load_uad()
    system = set(l.replace("package:", "") for l in sh(serial, "pm list packages -s").splitlines())
    disabled = set(l.replace("package:", "") for l in sh(serial, "pm list packages -d").splitlines())
    home = sh(serial, "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1")
    home_pkg = home.split("/")[0]
    plan = []
    ram = ram_by_package(serial)
    def add(p, t, why, src):
        mb = ram.get(p, 0) // 1024
        tag = f" [{mb} MB]" if mb else ""
        if p in disabled:
            plan.append((p, t, "già disattivato", src, True))
        else:
            plan.append((p, t, why + tag, src, False))
    seen = set()
    if d["family"] == "fireos":
        for p, why in FIRE_SAFE.items():
            if p in d["_pkgs"] and not is_never(p): add(p, "safe", why, "Fire OS"); seen.add(p)
        if tier == "caution":
            for p, why in FIRE_CAUTION.items():
                if p in d["_pkgs"] and not is_never(p): add(p, "caution", why, "Fire OS"); seen.add(p)
    if d["family"] in ("google-tv", "android-tv"):
        for p, why in GOOGLE_TV_SAFE.items():
            if p in d["_pkgs"] and not is_never(p) and p != home_pkg: add(p, "safe", why, "debloat-tv"); seen.add(p)
        if tier == "caution":
            for p, why in GOOGLE_TV_CAUTION.items():
                if p in d["_pkgs"] and not is_never(p) and p != home_pkg: add(p, "caution", why, "debloat-tv"); seen.add(p)
    maker = (d["maker"] + " " + d["brand"]).lower()
    for key, safe_set in BRAND_SAFE.items():
        if key not in maker: continue
        for p, why in safe_set.items():
            if p in d["_pkgs"] and not is_never(p) and p != home_pkg: add(p, "safe", why, key.upper()); seen.add(p)
        if tier == "caution":
            for p, why in BRAND_CAUTION.get(key, {}).items():
                if p in d["_pkgs"] and not is_never(p) and p != home_pkg: add(p, "caution", why, key.upper()); seen.add(p)
    # What we deliberately file under "caution" (it takes a feature away) never slips into the safe tier through the generic list.
    held_back = set(FIRE_CAUTION) | set(GOOGLE_TV_CAUTION) | {p for v in BRAND_CAUTION.values() for p in v}
    for p in sorted(system):
        if p in held_back and tier == "safe":
            continue
        if p in seen or is_never(p) or p == home_pkg or p not in uad or p.startswith(NO_BENEFIT_PREFIX):
            continue
        if ram.get(p, 0) < MIN_RAM_KB:
            continue   # nothing to gain: it is not using memory now
        e = uad[p]; lvl = e.get("removal")
        if lvl not in ("Recommended", "Advanced"):
            continue
        t = "safe" if lvl == "Recommended" else "caution"
        needed = [n for n in e.get("neededBy", []) if n in d["_pkgs"] and n not in disabled]
        if t == "safe" and needed:
            t = "caution"
        if t == "caution" and tier == "safe":
            continue
        why = (e.get("description") or "").split("\n")[0][:90]
        if needed: why += f"  [serve a: {', '.join(needed[:2])}]"
        add(p, t, why, "UAD-NG " + e.get("list", ""))
    return plan


def show_plan(plan, tier):
    todo = [x for x in plan if not x[4]]
    print(f"Livello: {tier}.  Da disattivare: {len(todo)}   già disattivati: {len(plan) - len(todo)}")
    for t in ("safe", "caution"):
        rows = [x for x in plan if x[1] == t]
        if not rows: continue
        print(f"\n== {t.upper()} ({len(rows)})")
        for p, _, why, src, off in rows:
            print(f"  {'[gia off]' if off else '[da fare]'} {p:<52} {why}  <{src}>")
    print("\nNiente è stato modificato. Per applicare: apply --tier %s --yes" % tier)


def apply(serial, d, plan, tier, only):
    todo = [x for x in plan if not x[4] and (not only or x[0] in only)]
    if not todo:
        print("niente da fare"); return
    os.makedirs(BACKUPS, exist_ok=True)
    path = os.path.join(BACKUPS, f"{serial.replace(':','_')}-{time.strftime('%Y%m%d-%H%M%S')}.json")
    json.dump({"serial": serial, "model": d["model"], "tier": tier, "disabled": [x[0] for x in todo]}, open(path, "w", encoding="utf-8"), indent=1)
    print(f"backup scritto: {path}")
    done, failed = [], []
    for p, *_ in todo:
        out = sh(serial, f"pm disable-user --user 0 {p} 2>&1 | tail -1")
        (done if "disabled-user" in out else failed).append((p, out))
        print(("  ok    " if "disabled-user" in out else "  salta ") + p + ("" if "disabled-user" in out else "  (" + out[:70] + ")"))
    print(f"\nfatto: {len(done)}  saltati (protetti dal sistema): {len(failed)}.  Per annullare: undo --file {path}")


def undo(serial, path):
    if not path:
        files = sorted(f for f in os.listdir(BACKUPS) if f.startswith(serial.replace(":", "_"))) if os.path.isdir(BACKUPS) else []
        if not files: raise SystemExit("nessun backup trovato")
        path = os.path.join(BACKUPS, files[-1])
    b = json.load(open(path, encoding="utf-8"))
    for p in b["disabled"]:
        print(sh(serial, f"pm enable {p} 2>&1 | tail -1"))
    for k, v in b.get("settings", {}).items():
        ns, key = k.split(":")
        sh(serial, f"settings put {ns} {key} {v}" if v not in ("", "null") else f"settings delete {ns} {key}")


# ---- Lessons from the community (TV Detox, dumbtv, free-my-tv): known pitfalls per brand -------------------
BRAND_PITFALLS = {
    "tcl": ["Non disattivare com.tcl.tv: rompe tutti gli ingressi HDMI.",
            "L'app di setup possiede il Wi-Fi: disattivarla fa dimenticare la rete.",
            "Dopo l'installazione di un launcher serve il permesso di esecuzione in background (appops RUN_IN_BACKGROUND allow)."],
    "philips": ["La Home passa da org.droidtv.homeintentresolver: non toccarlo o il nuovo launcher non si apre."],
    "xiaomi": ["Con la Home di Google spenta il tasto YouTube del telecomando può non fare niente."],
    "vu": ["Con la Home di Google spenta il tasto YouTube del telecomando può non fare niente."],
    "oneplus": ["Con la Home spenta i tasti Guida e Tutte le app non rispondono."],
    "google": ["Google TV: la Home di Google si sostituisce solo con l'opzione dedicata (spegne anche l'app di configurazione, alcune pagine Impostazioni risultano non disponibili finché non annulli). Altrimenti resta sempre accesa."],
    "amazon": ["Fire OS 8.1: dopo disattivazioni aggressive è segnalato qualche bootloop. Riavvio di controllo dopo ogni gruppo."],
}
GOOGLE_TV_KEEP = ["com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx", "com.google.android.tungsten.setupwraith"]

# Cheap settings that help every Android-family TV (reversible: the old value is saved in the backup).
TUNING = [("global", "window_animation_scale", "0.5"), ("global", "transition_animation_scale", "0.5"),
          ("global", "animator_duration_scale", "0.5")]


def pitfalls(d):
    keys = [d["maker"].lower(), d["brand"].lower()]
    out = []
    for k, notes in BRAND_PITFALLS.items():
        if any(k in x for x in keys):
            out += notes
    if d["family"] == "google-tv":
        out += BRAND_PITFALLS["google"]
    return out


def wireless_note(d):
    """Android 11+ TVs and Chromecast with Google TV have no fixed port 5555: they need adb pair."""
    if int(d.get("sdk") or 0) >= 30:
        return "Android 11+: se la porta 5555 non risponde usa Debug wireless: adb pair IP:porta, poi adb connect IP:porta."
    return ""


def check(serial):
    """Drift check after a firmware update: finds packages a backup disabled that are enabled again."""
    files = sorted(os.listdir(BACKUPS)) if os.path.isdir(BACKUPS) else []
    files = [f for f in files if f.startswith(serial.replace(":", "_"))]
    if not files: raise SystemExit("nessun backup: non c'è nulla da controllare")
    want = set()
    for f in files: want |= set(json.load(open(os.path.join(BACKUPS, f), encoding="utf-8"))["disabled"])
    off = set(l.replace("package:", "") for l in sh(serial, "pm list packages -d").splitlines())
    back = sorted(want - off)
    print(f"Controllati {len(want)} pacchetti. Tornati attivi dopo un aggiornamento: {len(back)}")
    for p in back: print("  riattivato  " + p)
    return back


PRIVATE_DNS = "dns.adguard-dns.com"   # blocks ads and trackers for every app (not YouTube's own ads); off again with undo


def tune(serial, tier="essenziale", dns=False, ultra=False):
    """Apply the animation tuning (and, if asked, the private DNS), saving old values so undo can restore them."""
    old = {}
    sh(serial, "pm trim-caches 999G")   # app caches only: logins and downloads stay
    if dns:
        for key in ("private_dns_mode", "private_dns_specifier"):
            old[f"global:{key}"] = sh(serial, f"settings get global {key}").strip()
        sh(serial, "settings put global private_dns_mode hostname")
        sh(serial, f"settings put global private_dns_specifier {PRIVATE_DNS}")
    scale = {"essenziale": "0.5", "equilibrato": "0.7", "pieno": "1.0"}.get(tier, "0.5")
    if ultra:
        scale = "0.3"   # ultra: the shortest animations that still look like animations
        sh(serial, "cmd package bg-dexopt-job")   # let Android finish optimising the apps' code now, not in the night (Android 8+; harmless if unknown)
    if scale == "1.0" and not dns:
        print("TV potente: animazioni lasciate com'è"); return
    if scale == "1.0":
        scale = None
    for ns, key, _ in (TUNING if scale else []):
        val = scale
        old[f"{ns}:{key}"] = sh(serial, f"settings get {ns} {key}").strip()
        sh(serial, f"settings put {ns} {key} {val}")
    os.makedirs(BACKUPS, exist_ok=True)
    path = os.path.join(BACKUPS, f"{serial.replace(':','_')}-tuning-{time.strftime('%Y%m%d-%H%M%S')}.json")
    json.dump({"serial": serial, "disabled": [], "settings": old}, open(path, "w", encoding="utf-8"), indent=1)
    print("animazioni ridotte (valori precedenti salvati in %s)" % path)


KNOWN = os.path.join(HERE, "known_issues.json")


def match_issues(family=None, brand="", maker="", sdk=0, year=0):
    """Known community problems that apply to this device (read-only: nothing is run)."""
    out = []
    for i in json.load(open(KNOWN, encoding="utf-8"))["issues"]:
        m = i["match"]
        if m.get("family") and family not in m["family"]: continue
        if m.get("brand") and not any(b in (brand + " " + maker).lower() for b in m["brand"]): continue
        if m.get("sdk_min") and int(sdk or 0) < m["sdk_min"]: continue
        if m.get("sdk_max") and int(sdk or 0) > m["sdk_max"]: continue
        if m.get("year_from") and year < m["year_from"]: continue
        if m.get("year_to") and year > m["year_to"]: continue
        out.append(i)
    return out


def show_issues(issues, lang="it"):
    for i in issues:
        t = lambda k: i[k].get(lang) or i[k]["en"]
        print(f"[{i['confidence']}] {t('symptom')}\n    causa: {t('cause')}\n    cosa fare: {t('fix')}\n    fonte: {i['source']}")
        if i.get("adb"): print("    comandi: " + " ; ".join(i["adb"]))


OURS = ("org.lumesuite.launcher",)
# Home screens that belong to the system or the maker: they are never touched (Fire OS and Google TV need their own).
SYSTEM_HOME_PREFIXES = ("com.amazon.", "com.google.android.", "com.android.", "android", "com.sony.", "com.samsung.", "com.lge.",
                        "com.tcl.", "com.mediatek.", "com.nvidia.", "org.droidtv.", "com.xiaomi.", "com.mitv.", "com.hisense.", "com.philips.")


def home_activities(serial):
    """Package names of every app that offers itself as a Home screen."""
    out = sh(serial, "cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME --brief")
    return sorted({l.strip().split("/")[0] for l in out.splitlines() if "/" in l and not l.strip().startswith(("priority", "Activity"))})


def launchers(serial, apply=False):
    """Extra launchers installed by hand (Hume, FLauncher, ATV Launcher, an old tvOS clone...): turned off, never uninstalled,
    and only when Lume is there to take their place. The list of what was switched off is saved so undo can bring them back."""
    homes = home_activities(serial)
    ours = [h for h in homes if h in OURS]
    system = [h for h in homes if h not in OURS and h.startswith(SYSTEM_HOME_PREFIXES)]
    extra = [h for h in homes if h not in OURS and h not in system]
    print("Home screens on this TV:")
    for h in homes:
        print("  " + ("nostro   " if h in ours else "di sistema" if h in system else "extra    ") + "  " + h)
    if not ours:
        print("\nLume non è installato: non spengo nessun altro launcher (resterebbe la Home senza ricambio).")
        return []
    if not extra:
        print("\nNessun launcher in più da spegnere."); return []
    if not apply:
        print("\nSi possono spegnere (sempre ripristinabili): " + ", ".join(extra) + "\nAggiungi --yes per farlo."); return extra
    for p in extra:
        sh(serial, f"am force-stop {p}")
        print(sh(serial, f"pm disable-user --user 0 {p}").strip())
    sh(serial, "sync")
    os.makedirs(BACKUPS, exist_ok=True)
    path = os.path.join(BACKUPS, f"{serial.replace(':','_')}-launchers-{time.strftime('%Y%m%d-%H%M%S')}.json")
    json.dump({"serial": serial, "disabled": extra, "settings": {}}, open(path, "w", encoding="utf-8"), indent=1)
    print("salvato in " + path)
    return extra


def replace_google_home(serial, apply=False, allow_google_tv=False):
    """Opt-in: Lume takes the place of the stock Android TV / Google TV Home. The stock launcher is turned off (never
    uninstalled) and saved in a backup so `undo` turns it back on. Checked right after: if Lume is not what Home opens, it is
    turned back on at once.
    Android TV: turn off com.google.android.tvlauncher. Google TV (launcherx): the recipe tried on a real Chromecast with
    Google TV 4K (rcland12/debloat-tv, MIT): turn off launcherx AND the setup app (its RecoveryActivity takes the Home key
    when launcherx is off), then make the other launcher the default. Cost: a few Settings pages (account > Apps & services)
    show "Unavailable" until undo."""
    homes = home_activities(serial)
    if not any(h in OURS for h in homes):
        print("Lume non è installato: la Home di Google resta com'è (non resterebbe nessuna Home)."); return []
    stock = [h for h in homes if h in ("com.google.android.tvlauncher", "com.google.android.leanbacklauncher")]
    gtv = "com.google.android.apps.tv.launcherx" in homes
    if gtv and not allow_google_tv:
        print("Questa è una Google TV (launcherx). Si può sostituire (provato su un Chromecast con Google TV vero), ma spegne anche\n"
              "l'app di configurazione: alcune pagine delle Impostazioni (Account > App e servizi) risultano 'non disponibili' finché\n"
              "non annulli con undo. Se vuoi: --google-tv."); return []
    targets = stock + (["com.google.android.apps.tv.launcherx", "com.google.android.tungsten.setupwraith"] if gtv else [])
    if "sony" in sh(serial, "getprop ro.product.manufacturer").lower() and "com.sony.dtv.applicationdisabler" in sh(serial, "pm list packages"):
        targets.append("com.sony.dtv.applicationdisabler")   # it turns disabled apps back on after a restart
    targets = [t for t in targets if t in sh(serial, "pm list packages")]
    if not targets:
        print("Nessuna Home di Google da sostituire."); return []
    if not apply:
        print("Lume può sostituire: " + ", ".join(targets) + "\nAnnullabile con undo. Aggiungi --yes per farlo."); return targets
    # Order matters (a black screen otherwise): Lume becomes the Home role holder first, and only then the old Home goes off.
    sh(serial, "cmd role add-role-holder --user 0 android.app.role.HOME org.lumesuite.launcher")
    if "org.lumesuite.launcher" not in sh(serial, "cmd role get-role-holders --user 0 android.app.role.HOME"):
        print("Lume non risulta la Home scelta dal sistema: non spengo niente."); return []
    for p in targets:
        print(sh(serial, f"pm disable-user --user 0 {p}").strip())
    sh(serial, "cmd package set-home-activity --user 0 org.lumesuite.launcher/.MainActivity")
    sh(serial, "sync"); time.sleep(2)
    now = sh(serial, "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
    if "org.lumesuite.launcher" not in now:
        for p in targets:
            sh(serial, f"pm enable --user 0 {p}")
        print("Con la Home di Google spenta, Home non apre Lume: ho rimesso tutto come prima."); return []
    os.makedirs(BACKUPS, exist_ok=True)
    path = os.path.join(BACKUPS, f"{serial.replace(':','_')}-launchers-{time.strftime('%Y%m%d-%H%M%S')}.json")
    json.dump({"serial": serial, "disabled": targets, "settings": {}}, open(path, "w", encoding="utf-8"), indent=1)
    print("Fatto: Home apre Lume. Per tornare a Google: undo --file " + path)
    return targets


GUIDES = os.path.join(HERE, "closed_tvs.json")


def guide(os_name, year):
    """Closed platforms (no ADB): honest, per-year checklist from guides.json."""
    g = json.load(open(GUIDES, encoding="utf-8"))
    p = g.get(os_name)
    if not p: raise SystemExit("piattaforme: " + ", ".join(g))
    yr = p.get("years", {}).get(str(year))
    if yr:
        print(f"{p['name']} {year} - {yr['os']}  [{yr['confidence']}]\n"); print("Cosa si puo' fare davvero:  " + yr["reach"]["it"])
        print("\nImpostazioni da spegnere (sul telecomando):")
        for s_ in yr["privacy"]: print("  - " + s_)
        if yr["note"]["it"]: print("\nNota: " + yr["note"]["it"])
        print("\nDomini DNS: " + ", ".join(p["dns"])); return
    band = next((b for b in p["bands"] if b["from"] <= year <= b["to"]), None)
    print(f"{p['name']} - modello {year}\n")
    if not band: print("anno fuori dalle fasce note: uso solo la lista impostazioni generale\n"); band = p["bands"][-1]
    print("Cosa si può fare davvero:  " + band["reach"])
    print("\nImpostazioni da spegnere (sul telecomando):")
    for s in p["settings"]: print("  - " + s)
    print("\nDomini da bloccare nel DNS (uno alla volta, controlla che app e aggiornamenti funzionino):")
    for dom in p["dns"]: print("  - " + dom)
    print("\nAttenzione: " + "; ".join(p["risks"]))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("cmd", choices=["detect", "plan", "apply", "undo", "check", "tune", "guide", "issues", "launchers", "replace-home", "install"])
    ap.add_argument("-s", "--serial"); ap.add_argument("--tier", choices=["safe", "caution"], default="safe")
    ap.add_argument("--yes", action="store_true"); ap.add_argument("--os"); ap.add_argument("--year", type=int, default=2022); ap.add_argument("--only"); ap.add_argument("--file"); ap.add_argument("--apk"); ap.add_argument("--google-tv", action="store_true"); ap.add_argument("--dns", action="store_true")
    a = ap.parse_args()
    if a.cmd == "guide":
        guide(a.os, a.year); return
    if a.cmd == "issues" and a.os:
        show_issues(match_issues(family=a.os, year=a.year)); return
    serial = pick_serial(a.serial)
    if a.cmd == "check":
        check(serial); return
    if a.cmd == "tune":
        tune(serial, dns=a.dns); return
    if a.cmd == "launchers":
        launchers(serial, a.yes); return
    if a.cmd == "replace-home":
        replace_google_home(serial, a.yes, a.google_tv); return
    if a.cmd == "install":
        print("installato" if install_apk(serial, a.apk, lambda d, t: print(f"\r{d * 100 // t}%", end="", flush=True)) else "NON installato"); return
    if a.cmd == "undo":
        undo(serial, a.file); return
    d = detect(serial)
    if a.cmd == "issues":
        show_issues(match_issues(d["family"], d["brand"], d["maker"], d["sdk"])); return
    if a.cmd == "detect":
        show_detect(d); return
    plan = build_plan(serial, d, a.tier)
    if a.cmd == "plan" or not a.yes:
        show_detect(d); print(); show_plan(plan, a.tier); return
    apply(serial, d, plan, a.tier, set(a.only.split(",")) if a.only else None)


if __name__ == "__main__":
    main()
