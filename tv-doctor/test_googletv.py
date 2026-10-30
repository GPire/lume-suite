"""Google TV: the curated list is proposed, the packages Google TV needs never are, and the Home stays."""
from test_scenarios_data import *
T.FIXTURE = fixture(SCEN["Sony Bravia (Google TV, 3 GB)"])
T.FIXTURE["packages"] += list(T.GOOGLE_TV_SAFE) + list(T.GOOGLE_TV_CAUTION) + ["com.google.android.rkpdapp", "com.google.android.apps.mediashell"]
T.FIXTURE["system"] = T.FIXTURE["packages"]; T.FIXTURE["ram_kb"] = {p: 30_000 for p in T.FIXTURE["packages"]}
d = T.detect("fixture")
safe = [x[0] for x in T.build_plan("fixture", d, "safe") if not x[4]]
full = [x[0] for x in T.build_plan("fixture", d, "caution") if not x[4]]
assert all(p in safe for p in T.GOOGLE_TV_SAFE), "safe set missing"
assert not any(p in safe for p in T.GOOGLE_TV_CAUTION), "caution set in safe tier"
assert all(p in full for p in T.GOOGLE_TV_CAUTION), "caution set missing"
for never in ("com.google.android.rkpdapp", "com.google.android.apps.mediashell", "com.google.android.apps.tv.launcherx",
              "com.google.android.tungsten.setupwraith", "com.google.android.gms"):
    assert never not in full, never
print("TUTTO OK google tv")
