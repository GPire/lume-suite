"""Scenario tests: fake TVs from many families, built with real package names from the community list.
Checks the safety invariants of the planner. This tests the LOGIC, not real firmware: package sets are synthetic."""
import json, re, sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tvdoctor as T

uad = T.load_uad()
def pick(words, n=14):
    """Real package names from the community list that mention the brand."""
    out = [p for p, e in uad.items() if any(w in (p + " " + e.get("description", "")).lower() for w in words)]
    return out[:n]

BASE_SYSTEM = ["android", "com.android.systemui", "com.android.settings", "com.android.providers.settings", "com.android.providers.media",
               "com.android.shell", "com.android.bluetooth", "com.google.android.gms", "com.google.android.gsf", "com.android.vending",
               "com.android.permissioncontroller", "com.android.packageinstaller"]
GOOGLE_TV = BASE_SYSTEM + ["com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher", "com.google.android.tungsten.setupwraith", "com.google.android.tv.remote.service"]
ANDROID_TV = BASE_SYSTEM + ["com.google.android.tvlauncher", "com.google.android.leanbacklauncher", "com.google.android.tv.remote.service"]

SCEN = {
 "Sony Bravia (Google TV, 3 GB)": dict(maker="Sony", model="BRAVIA 4K", sdk="31", ram=3072, pk=GOOGLE_TV + pick(["sony"]), home="com.google.android.apps.tv.launcherx", fam="google-tv"),
 "TCL (Google TV, 2 GB)": dict(maker="TCL", model="55C645", sdk="30", ram=2048, pk=GOOGLE_TV + pick(["tcl"]), home="com.google.android.apps.tv.launcherx", fam="google-tv"),
 "Philips (Android TV 9, 2 GB)": dict(maker="Philips", model="55PUS", sdk="28", ram=2048, pk=ANDROID_TV + pick(["philips", "tp vision"]), home="com.google.android.tvlauncher", fam="android-tv"),
 "Xiaomi Mi Box (Android TV, 2 GB)": dict(maker="Xiaomi", model="MIBOX4", sdk="28", ram=2048, pk=ANDROID_TV + pick(["xiaomi", "miui", "mi box"]), home="com.google.android.tvlauncher", fam="android-tv"),
 "Hisense (Android TV 9, 1 GB)": dict(maker="Hisense", model="H55", sdk="28", ram=1024, pk=ANDROID_TV + pick(["hisense"]), home="com.google.android.tvlauncher", fam="android-tv"),
 "Nvidia Shield (Android TV 11, 3 GB)": dict(maker="NVIDIA", model="SHIELD Android TV", sdk="30", ram=3072, pk=ANDROID_TV + pick(["nvidia"]), home="com.google.android.tvlauncher", fam="android-tv"),
 "Android TV 2015 (Android 6, 1.5 GB)": dict(maker="Sony", model="KDL-W", sdk="23", ram=1536, pk=ANDROID_TV + pick(["sony"], 6), home="com.google.android.leanbacklauncher", fam="android-tv"),
 "Fire TV Stick 4K Max (Fire OS 8)": dict(maker="Amazon", model="AFTKM", sdk="30", ram=1669, pk=BASE_SYSTEM + ["com.amazon.tv.launcher", "com.amazon.platform", "com.amazon.venezia", "com.amazon.tv.acr", "com.amazon.tv.livetv", "com.amazon.shoptv.client", "com.amazon.gamehub", "com.amazon.device.software.ota"] + pick(["amazon", "fire tv", "fireos"], 20), home="com.amazon.tv.launcher", fam="fireos", fp="Amazon/karat/karat:11"),
}

def fixture(s):
    pk = list(dict.fromkeys(s["pk"]))
    # heavy enough to be proposed: 30 MB each, except the launcher
    ram = {p: 30_000 for p in pk}
    return {"props": {"ro.product.manufacturer": s["maker"], "ro.product.brand": s["maker"], "ro.product.model": s["model"],
                      "ro.product.device": "dev", "ro.hardware": "soc", "ro.build.version.release": "x", "ro.build.version.sdk": s["sdk"],
                      "ro.build.fingerprint": s.get("fp", ""), "ro.build.characteristics": "tv"},
            "ram_mb": s["ram"], "packages": pk, "system": pk, "disabled": [], "ram_kb": ram, "home": s["home"]}

