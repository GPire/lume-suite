#!/usr/bin/env python3
"""TV Doctor, the app: double-click, no terminal knowledge, no coding agent.

    python3 tvdoctor_app.py            opens the page in your browser (only on this computer)
    python3 tvdoctor_app.py --demo     same, on a pretend TV: nothing real is touched

Three steps: find the TV, see what will change in plain words, one button. Undo is always there.
"""
import contextlib, io, json, os, re, secrets, socket, subprocess, sys, threading, webbrowser
from concurrent.futures import ThreadPoolExecutor
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = getattr(sys, "_MEIPASS", None) or os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import tvdoctor as T
USER = T.USER
import discovery

TOKEN = secrets.token_urlsafe(16)
DEMO = "--demo" in sys.argv
STATE = {}


def local_prefix():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1)); ip = s.getsockname()[0]
    except OSError:
        ip = "192.168.1.2"
    finally:
        s.close()
    return ip.rsplit(".", 1)[0]


def port_open(ip, port):
    try:
        with socket.create_connection((ip, port), 0.35):
            return True
    except OSError:
        return False


def http_get(url, timeout=1.5):
    import urllib.request
    try:
        return urllib.request.urlopen(url, timeout=timeout).read().decode("utf-8", "replace")
    except Exception:
        return ""


SAMSUNG_YEAR = {"H": 2014, "J": 2015, "K": 2016, "M": 2017, "N": 2018, "R": 2019, "T": 2020, "A": 2021,
                "B": 2022, "C": 2023, "D": 2024, "F": 2025}


def samsung_year(model):
    """Year from the Samsung model code (UE55NU7400 -> N -> 2018). An estimate, shown as such."""
    m = re.match(r"^[A-Z]{2}\d{2}Q[A-Z]?\d{2}([A-Z])", model or "") or re.match(r"^[A-Z]{2}\d{2}([A-Z])", model or "")
    return SAMSUNG_YEAR.get(m.group(1)) if m else None


def classify(ip):
    """What kind of TV is at this address? Tries every family, cheapest probe first."""
    if port_open(ip, 5555):
        return {"kind": "adb", "serial": f"{ip}:5555", "name": ip}
    if port_open(ip, 8001):
        try:
            dev = json.loads(http_get(f"http://{ip}:8001/api/v2/") or "{}").get("device", {})
        except ValueError:
            dev = {}
        if dev:
            model = dev.get("modelName", "")
            return {"kind": "tizen", "serial": ip, "name": "Samsung " + (dev.get("name") or model or ip), "year": samsung_year(model)}
    if port_open(ip, 8060):
        x = http_get(f"http://{ip}:8060/query/device-info")
        if "<device-info" in x:
            g = lambda t: (re.search(f"<{t}>([^<]*)</{t}>", x) or [0, ""])[1]
            return {"kind": "roku", "serial": ip, "name": "Roku " + (g("user-device-name") or g("model-name") or ip), "year": None}
    if port_open(ip, 3000) and port_open(ip, 3001):
        return {"kind": "webos", "serial": ip, "name": "LG (webOS) " + ip, "year": None}
    return None


NAME_HINTS = [("amazon", "fire"), ("aftm", "fire"), ("firetv", "fire"), ("fire-tv", "fire"), ("samsung", "tizen"), ("tizen", "tizen"),
              ("webos", "webos"), ("lgwebostv", "webos"), ("roku", "roku"), ("vidaa", "vidaa"), ("hisense", "vidaa"),
              ("bravia", "android"), ("sony", "android"), ("chromecast", "android"), ("android", "android"), ("tcl", "android"),
              ("philips", "android"), ("xiaomi", "android"), ("shield", "android"), ("mibox", "android")]


def arp_names():
    """Hostnames the router/mDNS already told this computer about: a free hint of what each device is."""
    out = {}
    try:
        for line in subprocess.run(["arp", "-a"], capture_output=True, text=True, timeout=5).stdout.splitlines():
            m = re.search(r"^(\S+)\s+\(?(\d+\.\d+\.\d+\.\d+)\)?", line.strip()) or re.search(r"(\d+\.\d+\.\d+\.\d+)\s+\S+\s+\S+", line)
            if m and m.re.pattern.startswith("^"):
                out[m.group(2)] = m.group(1).lower()
    except Exception:
        pass
    return out


def predict_off(ip, names, hint=None):
    """The TV is there but debugging is off: say which family it probably is, so the right guide opens.
    What the TV announced about itself (maker, model, services) comes first; the name from the router is the last guess."""
    if hint and hint.get("family"):
        label = " ".join(x for x in (hint.get("maker"), hint.get("model")) if x) or hint.get("name") or ip
        return {"kind": "off", "family": hint["family"], "serial": ip, "name": hint.get("name") or label, "model": hint.get("model", "")}
    if port_open(ip, 8008):
        try:
            nm = json.loads(http_get(f"http://{ip}:8008/setup/eureka_info") or "{}").get("name", "")
        except ValueError:
            nm = ""
        if nm:
            return {"kind": "off", "family": "android", "serial": ip, "name": nm}
    h = names.get(ip, "")
    for key, fam in NAME_HINTS:
        if key in h:
            return {"kind": "off", "family": fam, "serial": ip, "name": h.split(".")[0] or ip}
    return None


def adb_ready():
    """Always true: with no adb program the built-in client (pyadb) is used. The download stays as an option."""
    return True


def install_adb():
    """Official Google platform-tools into this folder. Called only from the button the user presses."""
    import platform, urllib.request, zipfile
    osn = {"Darwin": "darwin", "Windows": "windows", "Linux": "linux"}.get(platform.system())
    if not osn: return {"error": "sistema non riconosciuto"}
    dest = os.path.join(USER, "tools"); os.makedirs(dest, exist_ok=True)
    z = os.path.join(dest, "platform-tools.zip")
    urllib.request.urlretrieve(f"https://dl.google.com/android/repository/platform-tools-latest-{osn}.zip", z)
    zipfile.ZipFile(z).extractall(dest)
    os.chmod(os.path.join(dest, "platform-tools", "adb"), 0o755) if osn != "windows" else None
    os.environ["PATH"] = os.path.join(dest, "platform-tools") + os.pathsep + os.environ["PATH"]
    return {"ok": adb_ready()}


_local_tools = os.path.join(USER, "tools", "platform-tools")
if os.path.isdir(_local_tools):
    os.environ["PATH"] = _local_tools + os.pathsep + os.environ["PATH"]

BRAND_FAMILY = {"amazon": "fire", "android": "android", "samsung": "tizen", "lg": "webos", "roku": "roku", "vidaa": "vidaa"}


def enable_guide(brand, year, lang):
    g = json.load(open(os.path.join(HERE, "enable_guides.json"), encoding="utf-8"))
    fam = BRAND_FAMILY.get(brand, brand)
    if fam == "android": fam = "googletv" if year >= 2021 else "androidtv"
    e = g[fam]; pick = lambda d: d.get(lang) or d["en"]
    notes = [pick(n) for n in e["notes"]]
    if fam == "tizen" and year <= 2014: notes.insert(0, pick(g["tizen"]["notes"][0]))
    if fam in ("tizen", "webos"):
        cg = json.load(open(T.GUIDES, encoding="utf-8"))[fam].get("years", {}).get(str(year))
        if cg:
            notes.insert(0, f"{cg['os']}: " + (cg["reach"].get(lang) or cg["reach"]["en"]))
            if fam == "webos" and year <= 2015: e = dict(e, can="none")
            if fam == "tizen" and year <= 2014: e = dict(e, can="none")
    return {"family": fam, "can": e["can"], "title": pick(e["title"]), "intro": pick(e["intro"]),
            "steps": [pick(x) for x in e["steps"]], "notes": notes, "os": {"tizen": "tizen", "webos": "webos", "roku": "roku", "vidaa": "vidaa"}.get(fam)}


def find_tvs():
    if DEMO:
        return [{"kind": "adb", "serial": "demo:5555", "name": "Demo TV"}]
    pre = local_prefix()
    names = arp_names()
    try:
        hints = discovery.discover(2.5)   # what the TVs announce about themselves (mDNS + SSDP)
    except Exception:
        hints = {}
    def one(ip):
        found = classify(ip)
        hint = hints.get(ip)
        if found and hint and found["kind"] == "adb":
            found["name"] = hint.get("name") or hint.get("model") or found["name"]
        return found or predict_off(ip, names, hint)
    addresses = sorted({f"{pre}.{i}" for i in range(1, 255)} | set(hints))
    with ThreadPoolExecutor(64) as ex:
        res = [r for r in ex.map(one, addresses) if r]
    for r in res:
        if r["kind"] == "adb":
            T.adb(None, "connect", r["serial"])
    return res


def demo_fixture():
    import test_scenarios_data as D
    f = D.fixture(D.SCEN["Sony Bravia (Google TV, 3 GB)"])
    f["props"]["sys.boot_completed"] = "1"
    return f


def inspect(serial):
    if DEMO and T.FIXTURE is None:
        T.FIXTURE = demo_fixture()
    d = T.detect(serial)
    plan = T.build_plan(serial, d, "safe")
    todo = [x for x in plan if not x[4]]
    mb = 0
    for p, t, why, src, off in todo:
        m = re.search(r"\[(\d+) MB\]", why)
        mb += int(m.group(1)) if m else 0
    STATE[serial] = {"d": d}
    # After a system update: apps this TV Doctor turned off earlier that are on again (what TV Slim calls drift).
    reactivated = 0
    try:
        files = sorted(f for f in os.listdir(T.BACKUPS) if f.startswith(serial.replace(":", "_")) and "-tuning-" not in f and "-launchers-" not in f) if os.path.isdir(T.BACKUPS) else []
        before = set()
        for f in files: before |= set(json.load(open(os.path.join(T.BACKUPS, f), encoding="utf-8")).get("disabled", []))
        reactivated = len(before & {x[0] for x in todo})
    except Exception:
        pass
    return {
        "reactivated": reactivated,
        "serial": serial, "maker": d["maker"], "model": d["model"], "family": d["family"], "android": d["android"],
        "ram": d["ram_mb"], "profile": d["tier"], "free": d["free_mb"], "notes": T.pitfalls(d), "wireless": T.wireless_note(d),
        "count": len(todo), "mb": mb,
        "replaceHome": d["family"] in ("google-tv", "android-tv") and bool({"com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx"} & set(T.home_activities(serial))),
        "suite": d["family"] in ("fireos", "google-tv", "android-tv", "android-other") and int(d.get("sdk") or 0) >= 24,
        "items": [{"pkg": p, "why": re.sub(r"\s*\[\d+ MB\]", "", why), "tier": t, "group": T.group_of(p)} for p, t, why, src, off in todo],
        "groups": [{"id": g, "count": sum(1 for x in todo if T.group_of(x[0]) == g), "keep": keep}
                   for g, _, keep in T.KEEP_GROUPS if any(T.group_of(x[0]) == g for x in todo)],
    }


def report_url(serial, outcome=""):
    """A prefilled GitHub issue for a TV that did something odd. Brand, model, system and what happened only: no address, no serial
    number, no account. GitHub gets nothing until the person reads it there and presses Submit."""
    import urllib.parse, platform
    d = STATE.get(serial, {}).get("d") or {}
    body = "\n".join([
        "**TV:** %s %s" % (d.get("maker", "?"), d.get("model", "?")),
        "**System:** %s (%s), %s MB RAM, profile %s" % (d.get("android", "?"), d.get("family", "?"), d.get("ram_mb", "?"), d.get("tier", "?")),
        "**Computer:** %s" % platform.system(),
        "**What TV Doctor did / showed:** %s" % (outcome or "(write here)"),
        "", "**What went wrong or what you expected:**", "", "(write here, in your language)"])
    q = urllib.parse.urlencode({"template": "tv_report.md", "title": "TV report: %s %s" % (d.get("maker", "?"), d.get("model", "?")), "body": body[:1800]})
    return f"https://github.com/{REPO}/issues/new?{q}"


REPO = "GPire/lume-suite"
SUITE = [("lume-launcher.apk", "Lume", "org.lumesuite.launcher"), ("ambient-cast.apk", "Ambient Cast", "org.lumesuite.cast"),
         ("ambient-gallery.apk", "Galleria", "org.lumesuite.gallery")]
DOWNLOADS = os.path.join(os.path.expanduser("~"), ".tvdoctor", "downloads")


def _sha256(path):
    import hashlib
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""): h.update(block)
    return h.hexdigest()


def suite_files(local_dir=None, api=None, fetch=None):
    """The three apps, each checked against the release's SHA256SUMS before it goes near the TV.
    From a `dist` folder next to this one (a build on this computer) or from the newest release on GitHub.
    api/fetch are replaceable so this can be tested without a network."""
    import urllib.request
    local = local_dir if local_dir is not None else os.path.join(os.path.dirname(HERE), "dist")
    names = [n for n, _, _ in SUITE]
    if all(os.path.exists(os.path.join(local, n)) for n in names):
        sums = os.path.join(local, "SHA256SUMS")
        want = dict(l.split()[::-1] for l in open(sums, encoding="utf-8").read().splitlines() if l.strip()) if os.path.exists(sums) else {}
        for n in names:
            if n in want and want[n].lstrip("*") != _sha256(os.path.join(local, n)):
                raise RuntimeError(f"{n}: la firma SHA-256 non corrisponde, non lo installo")
        return {"files": {n: os.path.join(local, n) for n in names}, "source": "questo computer", "version": "locale"}
    get = fetch or (lambda url: urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "tv-doctor"}), timeout=30).read())
    rel = json.loads((api or (lambda: get(f"https://api.github.com/repos/{REPO}/releases/latest")))())
    assets = {a["name"]: a["browser_download_url"] for a in rel.get("assets", [])}
    if "SHA256SUMS" not in assets or any(n not in assets for n in names):
        raise RuntimeError("la versione su GitHub non ha tutti i file")
    want = {}
    for line in get(assets["SHA256SUMS"]).decode().splitlines():
        if line.strip():
            digest, name = line.split(None, 1)
            want[name.strip().lstrip("*")] = digest
    folder = os.path.join(DOWNLOADS, re.sub(r"[^A-Za-z0-9._-]", "_", rel.get("tag_name", "latest")))
    os.makedirs(folder, exist_ok=True)
    out = {}
    for n in names:
        path = os.path.join(folder, n)
        if not (os.path.exists(path) and _sha256(path) == want.get(n)):
            open(path, "wb").write(get(assets[n]))
        if _sha256(path) != want.get(n):
            os.remove(path)
            raise RuntimeError(f"{n}: la firma SHA-256 non corrisponde, non lo installo")
        out[n] = path
    return {"files": out, "source": "GitHub", "version": rel.get("tag_name", "")}


def install_suite(serial, d, files=None):
    """Lume, Ambient Cast and the Gallery onto an Android-based TV (Android 7+); one answer per app."""
    if d["family"] not in ("fireos", "google-tv", "android-tv", "android-other") or int(d.get("sdk") or 0) < 24:
        return [{"app": n, "ok": False, "why": "questa TV non lo permette"} for _, n, _ in SUITE]
    got = files or suite_files()
    res = []
    for apk, name, pkg in SUITE:
        try:
            res.append({"app": name, "ok": bool(T.install_apk(serial, got["files"][apk]))})
        except Exception as e:
            res.append({"app": name, "ok": False, "why": str(e)[:120]})
    return res


def mem_avail(serial):
    m = re.search(r"MemAvailable:\s+(\d+)", T.sh(serial, "grep MemAvailable /proc/meminfo"))
    return int(m.group(1)) // 1024 if m else 0


def healthy(serial, home_pkg):
    """The TV answers, finished booting and still has its Home. Checked after every small batch."""
    if T.sh(serial, "getprop sys.boot_completed").strip() != "1":
        return False
    h = T.sh(serial, "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1")
    return bool(h.strip()) and (not home_pkg or home_pkg in h)


def run_apply(serial, batch=4, install=False, replace_home=False, dns=False, keep=(), anim=True):
    """Small batches, a health check after each; on the first doubt the batch is undone and we stop."""
    if DEMO and T.FIXTURE is None:
        T.FIXTURE = demo_fixture()
    d = STATE.get(serial, {}).get("d") or T.detect(serial)
    suite = None
    if install:   # the apps first: if the download or the install fails, nothing on the TV has been touched yet
        try:
            suite = install_suite(serial, d)
        except Exception as e:
            suite = [{"app": n, "ok": False, "why": str(e)[:160]} for _, n, _ in SUITE]
    plan = T.build_plan(serial, d, "safe")
    todo = [x[0] for x in plan if not x[4] and T.group_of(x[0]) not in set(keep)]   # what the owner chose to keep stays on
    home = T.sh(serial, "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1").split("/")[0]
    before, done, rolled = mem_avail(serial), 0, False
    for k in range(0, len(todo), batch):
        part = set(todo[k:k + batch])
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            T.apply(serial, d, plan, "safe", part)
        out = buf.getvalue()
        ok_now = len(re.findall(r"^\s+ok ", out, re.M))
        if not healthy(serial, home):
            m = re.search(r"backup scritto: (.*)", out)
            if m:
                with contextlib.redirect_stdout(io.StringIO()):
                    T.undo(serial, m.group(1).strip())
            rolled = True
            break
        done += ok_now
    with contextlib.redirect_stdout(io.StringIO()):
        T.tune(serial, d.get("tier", "essenziale") if anim else "pieno", dns)
    T.sh(serial, "sync")   # Android writes package states a little later: a reboot right now would lose them (seen on Fire OS 8.1)
    homed = None
    if replace_home and not rolled:   # Lume must be there; replace_google_home checks Home opens Lume and puts Google back if not
        with contextlib.redirect_stdout(io.StringIO()):
            homed = bool(T.replace_google_home(serial, True, True))
    return {"ok": not rolled, "done": done, "rolledBack": rolled, "before": before, "after": mem_avail(serial), "suite": suite, "home": homed}


def run_undo(serial):
    buf = io.StringIO()
    files = sorted(f for f in os.listdir(T.BACKUPS) if f.startswith(serial.replace(":", "_"))) if os.path.isdir(T.BACKUPS) else []
    with contextlib.redirect_stdout(buf):
        for f in reversed(files):
            T.undo(serial, os.path.join(T.BACKUPS, f))
    return {"ok": True, "restored": len(files)}


def guide(os_name, year, lang="it"):
    g = json.load(open(T.GUIDES, encoding="utf-8"))[os_name]
    yr = g.get("years", {}).get(str(year))
    if yr:
        return {"name": f"{g['name']} {year} - {yr['os']}", "reach": yr["reach"].get(lang) or yr["reach"]["en"], "settings": yr["privacy"],
                "dns": g["dns"], "risks": g["risks"] + ([yr["note"].get(lang) or yr["note"]["en"]] if yr["note"]["it"] else []),
                "confidence": yr["confidence"]}
    band = next((b for b in g["bands"] if b["from"] <= year <= b["to"]), g["bands"][-1])
    return {"name": g["name"], "reach": band["reach"], "settings": g["settings"], "dns": g["dns"], "risks": g["risks"]}


class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass

    def _send(self, code, body, ctype="application/json"):
        b = body if isinstance(body, bytes) else body.encode()
        self.send_response(code); self.send_header("Content-Type", ctype + "; charset=utf-8")
        self.send_header("Content-Length", str(len(b))); self.send_header("Cache-Control", "no-store")
        self.end_headers(); self.wfile.write(b)

    def _host_ok(self):
        return self.headers.get("Host", "").split(":")[0] in ("127.0.0.1", "localhost")

    def do_GET(self):
        if not self._host_ok(): return self._send(403, "{}")
        if self.path.split("?")[0] == "/":
            return self._send(200, PAGE.replace("__TOKEN__", TOKEN), "text/html")
        self._send(404, "{}")

    def do_POST(self):
        if not self._host_ok() or self.headers.get("X-Token") != TOKEN: return self._send(403, "{}")
        n = int(self.headers.get("Content-Length") or 0)
        q = json.loads(self.rfile.read(n) or b"{}")
        try:
            if self.path == "/api/find": r = {"tvs": find_tvs()}
            elif self.path == "/api/connect":
                ip = re.sub(r"[^0-9.]", "", q.get("ip", ""))
                r = classify(ip) or {"error": "nessuna TV riconosciuta a questo indirizzo"}
                if r.get("kind") == "adb": T.adb(None, "connect", r["serial"])
            elif self.path == "/api/inspect": r = inspect(q["serial"])
            elif self.path == "/api/report": r = {"url": report_url(q.get("serial", ""), str(q.get("outcome", ""))[:400])}
            elif self.path == "/api/apply": r = run_apply(q["serial"], install=bool(q.get("install")), replace_home=bool(q.get("replaceHome")), dns=bool(q.get("dns")), keep=q.get("keep") or [], anim=q.get("anim", True))
            elif self.path == "/api/undo": r = run_undo(q["serial"])
            elif self.path == "/api/enable": r = enable_guide(q["brand"], int(q.get("year", 2022)), q.get("lang", "en"))
            elif self.path == "/api/adbstatus": r = {"ready": DEMO or adb_ready()}
            elif self.path == "/api/install_adb": r = install_adb()
            elif self.path == "/api/probe":
                ip = re.sub(r"[^0-9.]", "", q.get("ip", "")); r = classify(ip) or {"kind": "off"}
                if r.get("kind") == "adb": T.adb(None, "connect", r["serial"])
            elif self.path == "/api/guide": r = guide(q["os"], int(q.get("year", 2022)), q.get("lang", "it"))
            else: return self._send(404, "{}")
            self._send(200, json.dumps(r))
        except SystemExit as e:
            self._send(200, json.dumps({"error": str(e)}))
        except Exception as e:
            self._send(200, json.dumps({"error": str(e)[:200]}))


PAGE = open(os.path.join(HERE, "app_page.html"), encoding="utf-8").read()


def main():
    srv = ThreadingHTTPServer(("127.0.0.1", 0), H)
    url = f"http://127.0.0.1:{srv.server_port}/" + ("?auto=1" if "--auto" in sys.argv else "")
    print("TV Doctor è aperto su", url, "(solo su questo computer). Ctrl+C per chiudere.")
    threading.Timer(0.6, lambda: webbrowser.open(url)).start()
    try: srv.serve_forever()
    except KeyboardInterrupt: pass


if __name__ == "__main__":
    main()
