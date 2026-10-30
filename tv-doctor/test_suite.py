"""Installing Lume Suite: checksum checks, a download from a (faked) release, and which TVs are allowed. No network, no TV."""
import hashlib, json, os, sys, tempfile
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.argv = ["x", "--demo"]
import tvdoctor as T
import tvdoctor_app as A
import test_scenarios_data as D

NAMES = [n for n, _, _ in A.SUITE]
body = {n: (n + " bytes").encode() * 50 for n in NAMES}
sha = lambda b: hashlib.sha256(b).hexdigest()

# 1) a local dist folder with good sums
with tempfile.TemporaryDirectory() as d:
    for n in NAMES: open(os.path.join(d, n), "wb").write(body[n])
    open(os.path.join(d, "SHA256SUMS"), "w").write("".join(f"{sha(body[n])}  {n}\n" for n in NAMES))
    r = A.suite_files(local_dir=d)
    assert set(r["files"]) == set(NAMES) and r["source"] == "questo computer", r
    # tampered file is refused
    open(os.path.join(d, NAMES[1]), "wb").write(b"evil")
    try:
        A.suite_files(local_dir=d); raise SystemExit("file manomesso accettato!")
    except RuntimeError as e:
        assert "SHA-256" in str(e), e

# 2) a release on "GitHub" (faked): downloaded, checked, cached
with tempfile.TemporaryDirectory() as home:
    A.DOWNLOADS = os.path.join(home, "dl")
    urls = {f"https://x/{n}": body[n] for n in NAMES}
    urls["https://x/SHA256SUMS"] = "".join(f"{sha(body[n])}  {n}\n" for n in NAMES).encode()
    rel = {"tag_name": "v0.1.0", "assets": [{"name": n, "browser_download_url": f"https://x/{n}"} for n in NAMES + ["SHA256SUMS"]]}
    calls = []
    def fetch(u): calls.append(u); return urls[u]
    r = A.suite_files(local_dir="/nonexistent", api=lambda: json.dumps(rel).encode(), fetch=fetch)
    assert r["source"] == "GitHub" and r["version"] == "v0.1.0" and all(os.path.exists(p) for p in r["files"].values()), r
    first = len(calls)
    A.suite_files(local_dir="/nonexistent", api=lambda: json.dumps(rel).encode(), fetch=fetch)
    assert len(calls) == first + 1, "the apps must not be downloaded twice"            # only SHA256SUMS again
    # a release whose file does not match its sum
    urls["https://x/" + NAMES[0]] = b"swapped"
    for p in os.listdir(os.path.join(A.DOWNLOADS, "v0.1.0")): os.remove(os.path.join(A.DOWNLOADS, "v0.1.0", p))
    try:
        A.suite_files(local_dir="/nonexistent", api=lambda: json.dumps(rel).encode(), fetch=fetch); raise SystemExit("download manomesso accettato!")
    except RuntimeError as e:
        assert "SHA-256" in str(e)
    # incomplete release
    rel2 = {"tag_name": "v0", "assets": rel["assets"][:2]}
    try:
        A.suite_files(local_dir="/nonexistent", api=lambda: json.dumps(rel2).encode(), fetch=fetch); raise SystemExit("release incompleta accettata!")
    except RuntimeError:
        pass

# 3) which TVs: Android 7+ only
fake = {"files": {n: "/tmp/" + n for n in NAMES}}
for name, scen in D.SCEN.items():
    T.FIXTURE = D.fixture(scen)
    d = T.detect("fixture")
    res = A.install_suite("fixture", d, fake)
    sdk = int(d.get("sdk") or 0)
    allowed = d["family"] in ("fireos", "google-tv", "android-tv", "android-other") and sdk >= 24
    assert all(x["ok"] for x in res) == allowed, (name, d["family"], sdk, res)
    assert (len(T.FIXTURE.get("installed", [])) == 3) == allowed, name
print("suite OK: %d scenari, controllo SHA-256, cache, rifiuto di file manomessi" % len(D.SCEN))
