"""replace_google_home on a fake Google TV: Lume gets the role first, both Google packages go off, Home is checked;
nothing is turned off when the system does not make Lume the Home, and the old Home comes back if Home does not open Lume."""
import tvdoctor as T


def run(role_ok, home_ok, flag=True):
    calls = []

    def sh(serial, cmd):
        calls.append(cmd)
        if cmd.startswith("cmd package query-activities"):
            return "com.google.android.apps.tv.launcherx/.home.HomeActivity\norg.lumesuite.launcher/.MainActivity\n"
        if cmd == "pm list packages":
            return "package:com.google.android.apps.tv.launcherx\npackage:com.google.android.tungsten.setupwraith\n"
        if "get-role-holders" in cmd:
            return "org.lumesuite.launcher" if role_ok else "com.google.android.tvlauncher"
        if "resolve-activity" in cmd:
            return "org.lumesuite.launcher/.MainActivity" if home_ok else "com.google.android.tungsten.setupwraith/.RecoveryActivity"
        return ""
    T.sh = sh
    T.BACKUPS = "/tmp/tvd-test-backups"
    return T.replace_google_home("x", True, flag), calls


res, calls = run(True, True)
assert res, "should succeed"
assert calls.index(next(c for c in calls if "add-role-holder" in c)) < calls.index(next(c for c in calls if "disable-user" in c)), "role first"
assert any("disable-user --user 0 com.google.android.tungsten.setupwraith" in c for c in calls)
assert any("set-home-activity" in c for c in calls)

res, calls = run(False, True)
assert res == [] and not any("disable-user" in c for c in calls), "must not turn anything off without the role"

res, calls = run(True, False)
assert res == [] and any(c.startswith("pm enable --user 0 com.google.android.apps.tv.launcherx") for c in calls), "must roll back"

res, calls = run(True, True, flag=False)
assert res == [] and not any("disable-user" in c for c in calls), "needs the explicit flag"
print("TUTTO OK replace-home")
