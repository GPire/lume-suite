from test_scenarios_data import *
fails, rows = 0, []
for name, s in SCEN.items():
    T.FIXTURE = fixture(s)
    d = T.detect("fixture")
    plan = T.build_plan("fixture", d, "caution")
    todo = [p for p, t, why, src, off in plan if not off]
    problems = []
    if d["family"] != s["fam"]: problems.append(f"famiglia {d['family']} != {s['fam']}")
    for p in todo:
        if T.is_never(p): problems.append(f"proposto un pacchetto intoccabile: {p}")
        if p == s["home"]: problems.append(f"proposta la Home: {p}")
        e = uad.get(p)
        if e and e.get("removal") in ("Expert", "Unsafe") and p not in T.FIRE_SAFE and p not in T.FIRE_CAUTION:
            problems.append(f"proposto {p} marcato {e['removal']}")
    safe = sum(1 for _, t, _, _, off in plan if t == "safe" and not off)
    caut = sum(1 for _, t, _, _, off in plan if t == "caution" and not off)
    rows.append((name, d["family"], d["tier"], len(T.FIXTURE["packages"]), safe, caut, problems))
    fails += bool(problems)

print(f"{'scenario':<38}{'famiglia':<14}{'profilo':<12}{'pacchetti':>9}{'safe':>6}{'cautela':>9}  esito")
for name, fam, tier, n, safe, caut, pr in rows:
    print(f"{name:<38}{fam:<14}{tier:<12}{n:>9}{safe:>6}{caut:>9}  " + ("OK" if not pr else "PROBLEMI: " + "; ".join(pr)))
print("\nTUTTI GLI SCENARI OK" if not fails else f"\n{fails} SCENARI CON PROBLEMI")
sys.exit(1 if fails else 0)
