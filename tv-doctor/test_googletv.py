"""Google TV: the curated list is proposed, the packages Google TV needs never are, and the Home stays."""
from test_scenarios_data import *
T.FIXTURE = fixture(SCEN["Sony Bravia (Google TV, 3 GB)"])
T.FIXTURE["packages"] += list(T.GOOGLE_TV_SAFE) + list(T.GOOGLE_TV_CAUTION) + ["com.google.android.rkpdapp", "com.google.android.apps.mediashell"]
T.FIXTURE["system"] = T.FIXTURE["packages"]; T.FIXTURE["ram_kb"] = {p: 30_000 for p in T.FIXTURE["packages"]}
d = T.detect("fixture")
safe = [x[0] for x in T.build_plan("fixture", d, "safe") if not x[4]]
full = [x[0] for x in T.build_plan("fixture", d, "caution") if not x[4]]
assert all(p in safe for p in T.GOOGLE_TV_SAFE), "safe set missing"
leak = [p for p in T.GOOGLE_TV_CAUTION if p in safe]
assert not leak, "caution set in safe tier: %s" % leak
assert all(p in full for p in T.GOOGLE_TV_CAUTION), "caution set missing"
for never in ("com.google.android.rkpdapp", "com.google.android.apps.mediashell", "com.google.android.apps.tv.launcherx",
              "com.google.android.tungsten.setupwraith", "com.google.android.gms"):
    assert never not in full, never
print("TUTTO OK google tv")

# brands: ads/promotions proposed, inputs/updates never, and any name with hdmi/tvinput/livetv is refused
T.FIXTURE = fixture(SCEN["TCL (Google TV, 2 GB)"])
extra = ["com.tcl.bootadservice", "com.tcl.overseasappshow", "com.tcl.tvinput", "com.tcl.ttvs", "com.tcl.suspension", "com.acme.hdmi.service", "com.acme.livetv"]
T.FIXTURE["packages"] += extra; T.FIXTURE["system"] = T.FIXTURE["packages"]; T.FIXTURE["ram_kb"] = {p: 30_000 for p in T.FIXTURE["packages"]}
d = T.detect("fixture")
plan = [x[0] for x in T.build_plan("fixture", d, "caution") if not x[4]]
assert "com.tcl.bootadservice" in plan and "com.tcl.overseasappshow" in plan
for never in ("com.tcl.tvinput", "com.tcl.ttvs", "com.tcl.suspension", "com.acme.hdmi.service", "com.acme.livetv"):
    assert never not in plan, never
print("TUTTO OK marche")
