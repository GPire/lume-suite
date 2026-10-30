"""replace_google_home on a fake Google TV: both packages off, set-home-activity called, rolled back when Home is not Lume."""
import tvdoctor as T
calls = []
def fake(ok):
    def sh(serial, cmd):
        calls.append(cmd)
        if cmd.startswith("cmd package query-activities"):
            return "com.google.android.apps.tv.launcherx/.home.HomeActivity\\norg.lumesuite.launcher/.MainActivity\\n".replace("\\\\n", "\\n")
        if cmd == "pm list packages":
            return "package:com.google.android.apps.tv.launcherx\\npackage:com.google.android.tungsten.setupwraith\\n".replace("\\\\n", "\\n")
        if "resolve-activity" in cmd:
            return "org.lumesuite.launcher/.MainActivity" if ok else "com.google.android.tungsten.setupwraith/.RecoveryActivity"
        return ""
    return sh
T.BACKUPS = "/tmp/tvd-test-backups"
T.sh = fake(True); calls.clear()
assert T.replace_google_home("x", True, True)
assert any("disable-user --user 0 com.google.android.tungsten.setupwraith" in c for c in calls)
assert any("set-home-activity" in c for c in calls)
T.sh = fake(False); calls.clear()
assert T.replace_google_home("x", True, True) == []
assert any(c.startswith("pm enable --user 0 com.google.android.apps.tv.launcherx") for c in calls), "no rollback"
T.sh = fake(True); calls.clear()
assert T.replace_google_home("x", True, False) == [] and not any("disable-user" in c for c in calls), "must need the explicit flag"
print("TUTTO OK replace-home")
