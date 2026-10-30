"""Builds the double-click app (no Python needed on the TV owner's computer) with PyInstaller.
usage: python3 build_app.py NAME     e.g. TV-Doctor-mac-arm64  ->  dist-app/NAME(.exe)   plus NAME.sha256
Run on each operating system it should run on (the GitHub workflow .github/workflows/app.yml does that)."""
import hashlib, os, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
name = sys.argv[1] if len(sys.argv) > 1 else "TV-Doctor"
data = ["app_page.html", "known_issues.json", "closed_tvs.json", "enable_guides.json", "uad_lists.json", "LICENSE", "NOTICE"]
for f in data:
    if not os.path.exists(os.path.join(HERE, f)):
        sys.exit("missing " + f + (" (run any tvdoctor command once to download the UAD-NG list)" if f == "uad_lists.json" else ""))
cmd = [sys.executable, "-m", "PyInstaller", "--noconfirm", "--clean", "--onefile", "--name", name,
       "--distpath", os.path.join(HERE, "dist-app"), "--workpath", os.path.join(HERE, "build-app"), "--specpath", os.path.join(HERE, "build-app"),
       "--paths", HERE, "--hidden-import", "tvdoctor", "--hidden-import", "pyadb", "--hidden-import", "discovery"]
for f in data:
    cmd += ["--add-data", os.path.join(HERE, f) + os.pathsep + "."]
cmd.append(os.path.join(HERE, "tvdoctor_app.py"))
subprocess.run(cmd, check=True)
out = os.path.join(HERE, "dist-app", name + (".exe" if sys.platform == "win32" else ""))
h = hashlib.sha256(open(out, "rb").read()).hexdigest()
open(out + ".sha256", "w").write(f"{h}  {os.path.basename(out)}\n")
print(out, h)
