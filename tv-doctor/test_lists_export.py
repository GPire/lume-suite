"""Lume's on-TV lists (lume/assets/optimize.json) must be the ones TV Doctor uses: run tools/export_lists.py after editing a list."""
import json, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import tvdoctor as T
path = os.path.join(HERE, "..", "lume", "assets", "optimize.json")
if not os.path.exists(path):
    print("skip: no lume folder next to tv-doctor"); sys.exit(0)
have = json.load(open(path, encoding="utf-8"))
assert have["fire"]["safe"] == T.FIRE_SAFE and have["google"]["safe"] == T.GOOGLE_TV_SAFE, "run tools/export_lists.py"
assert set(have["never"]["exact"]) == set(T.NEVER_EXACT) and have["never"]["words"] == list(T.NEVER_WORDS), "run tools/export_lists.py"
assert [g["id"] for g in have["groups"]] == [g for g, _, _ in T.KEEP_GROUPS], "run tools/export_lists.py"
print("TUTTO OK liste uguali")
