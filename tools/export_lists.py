"""Writes lume/assets/optimize.json from the lists in tv-doctor/tvdoctor.py, so TV Doctor (computer) and Lume (on the TV) never disagree.
Run it after editing a list:  python3 tools/export_lists.py"""
import json, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "tv-doctor"))
import tvdoctor as T
out = {
    "fire": {"safe": T.FIRE_SAFE, "caution": T.FIRE_CAUTION},
    "google": {"safe": T.GOOGLE_TV_SAFE, "caution": T.GOOGLE_TV_CAUTION},
    "brand": {k: {"safe": T.BRAND_SAFE.get(k, {}), "caution": T.BRAND_CAUTION.get(k, {})} for k in set(T.BRAND_SAFE) | set(T.BRAND_CAUTION)},
    "groups": [{"id": g, "words": list(w), "keep": k} for g, w, k in T.KEEP_GROUPS],
    "never": {"exact": sorted(T.NEVER_EXACT), "prefix": list(T.NEVER_PREFIX), "words": list(T.NEVER_WORDS)},
}
path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "lume", "assets", "optimize.json")
json.dump(out, open(path, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
print("written", os.path.relpath(path), sum(len(v["safe"]) + len(v["caution"]) for v in (out["fire"], out["google"])), "curated packages")
