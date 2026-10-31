"""SmartTube is installed only when the file matches GitHub's digest AND is signed with the pinned certificate."""
import hashlib, json, os, sys, tempfile
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tvdoctor as T, tvdoctor_app as A
real = "/tmp/st_latest.apk"
if not os.path.exists(real):
    print("skip: no SmartTube sample"); sys.exit(0)
data = open(real, "rb").read()
T.FIXTURE = {"props": {}, "packages": [], "system": [], "disabled": [], "ram_kb": {}, "ram_mb": 1024, "home": "x"}
T.sh = lambda s, c: "armeabi-v7a,armeabi" if "abilist" in c else ""
A.DOWNLOADS = tempfile.mkdtemp()
rel = lambda dg: json.dumps({"tag_name": "32.68", "assets": [{"name": "SmartTube_stable_32.68_armeabi-v7a.apk", "browser_download_url": "x", "digest": "sha256:" + dg}]}).encode()
ok = A.install_smarttube("s", fetch=lambda u: rel(hashlib.sha256(data).hexdigest()) if u == A.SMARTTUBE_API else data)
assert ok["ok"], ok
bad = A.install_smarttube("s", fetch=lambda u: rel("0" * 64) if u == A.SMARTTUBE_API else data)
assert not bad["ok"] and "non corrisponde" in bad["why"], bad
forged = bytearray(data); forged[-3] ^= 1
other = A.install_smarttube("s", fetch=lambda u: rel(hashlib.sha256(bytes(forged)).hexdigest()) if u == A.SMARTTUBE_API else bytes(forged))
assert not other["ok"], other     # tampered file: its digest is "right" but the signature no longer verifies
A.SMARTTUBE_CERT = "00" * 32
wrongkey = A.install_smarttube("s", fetch=lambda u: rel(hashlib.sha256(data).hexdigest()) if u == A.SMARTTUBE_API else data)
assert not wrongkey["ok"] and "firma" in wrongkey["why"], wrongkey
print("TUTTO OK smarttube")
