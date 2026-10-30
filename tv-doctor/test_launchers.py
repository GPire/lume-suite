"""launchers(): never touches system or our home, only turns off extras, and only when Lume is installed (fixture TV, no adb)."""
import os, sys, io, contextlib
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tvdoctor as T
calls = []
def fake(homes):
    def sh(serial, cmd):
        calls.append(cmd)
        if cmd.startswith("cmd package query-activities"):
            return "\n".join(f"    {h}/.Main" for h in homes) + "\npriority=0 x\n"
        return "Package new state: disabled-user" if cmd.startswith("pm disable-user") else ""
    return sh
def run(homes, apply):
    calls.clear(); T.sh = fake(homes)
    with contextlib.redirect_stdout(io.StringIO()): res = T.launchers("t:5555", apply)
    return res, [c for c in calls if c.startswith("pm disable-user")]
AMZ = ["com.amazon.tv.launcher", "com.amazon.firehomestarter"]
r, d = run(AMZ + ["org.lumesuite.launcher", "com.petrobot.hume"], False)
assert r == ["com.petrobot.hume"] and d == [], (r, d)                      # lists, does nothing without --yes
r, d = run(AMZ + ["org.lumesuite.launcher", "com.petrobot.hume", "me.efesser.flauncher"], True)
assert sorted(x.split()[-1] for x in d) == ["com.petrobot.hume", "me.efesser.flauncher"], d
assert not any("amazon" in x or "lumesuite" in x for x in d), d             # system and ours untouched
r, d = run(AMZ + ["com.petrobot.hume"], True)
assert r == [] and d == [], (r, d)                                         # no Lume installed: nothing is turned off
print("launchers OK")
