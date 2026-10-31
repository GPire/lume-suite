"""Known-issues base: structure and matching checks (logic only, not real firmware)."""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tvdoctor as T
data = json.load(open(T.KNOWN, encoding="utf-8"))["issues"]
ids = [i["id"] for i in data]
assert len(ids) == len(set(ids)), "id duplicati"
FAM = {"android-tv", "google-tv", "fireos", "tizen", "webos", "roku", "vidaa"}
for i in data:
    for k in ("symptom", "cause", "fix"):
        assert i[k]["it"] and i[k]["en"], (i["id"], k)
    assert set(i["match"].get("family", [])) <= FAM, i["id"]
    assert i["confidence"] in ("reported", "verified") and i["source"], i["id"]
    for c in i.get("adb", []):
        assert "uninstall" not in c and "rm " not in c and "wipe" not in c, (i["id"], c)
def ids_of(**k): return {i["id"] for i in T.match_issues(**k)}
assert "fire-os-home-locked" in ids_of(family="fireos", brand="amazon", sdk=30)
assert "fire-os-home-locked" not in ids_of(family="google-tv", brand="tcl", sdk=34)
assert "tcl-hdmi-break" in ids_of(family="google-tv", brand="tcl", sdk=30)
assert "adb-port-closed-android11" in ids_of(family="google-tv", sdk=34) and "adb-port-closed-android11" not in ids_of(family="android-tv", sdk=23)
assert "tizen-dev-install-118" in ids_of(family="tizen", year=2016) and "tizen-dev-install-118" not in ids_of(family="tizen", year=2021)
assert "webos-dev-expires" in ids_of(family="webos", year=2020)
assert "vidaa-ads" in ids_of(family="vidaa", year=2024)
assert "macos-adb-blocked" in ids_of(family="roku")
print(f"known_issues OK: {len(data)} problemi, {sum(i['confidence']=='verified' for i in data)} verificati da noi")
