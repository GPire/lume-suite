"""What the owner keeps is not switched off; the groups offered are only those with something to switch off."""
from test_scenarios_data import *
T.FIXTURE = fixture(SCEN["Sony Bravia (Google TV, 3 GB)"])
T.FIXTURE["packages"] += ["com.google.android.katniss", "com.google.android.play.games", "com.google.android.feedback"]
T.FIXTURE["system"] = T.FIXTURE["packages"]; T.FIXTURE["ram_kb"] = {p: 30_000 for p in T.FIXTURE["packages"]}
import tvdoctor_app as A
A.STATE.clear()
info = A.inspect("fixture")
ids = {g["id"]: g for g in info["groups"]}
assert "games" in ids and "privacy" in ids and ids["games"]["keep"] is False
assert "assistant" in ids and ids["assistant"]["keep"] is True, "assistant is kept by default"
assert all(i["group"] for i in info["items"] if i["pkg"] in ("com.google.android.play.games", "com.google.android.feedback"))
print("TUTTO OK keep")

# run_apply leaves the kept group alone
seen = []
A.T.apply = lambda serial, d, plan, tier, part: seen.extend(part)
A.healthy = lambda *a, **k: True
A.mem_avail = lambda *a: 0
A.T.tune = lambda *a, **k: None
A.run_apply("fixture", keep=["games"])
assert "com.google.android.play.games" not in seen and "com.google.android.feedback" in seen, seen
seen.clear(); A.run_apply("fixture", keep=[])
assert "com.google.android.play.games" in seen
print("TUTTO OK keep apply")
