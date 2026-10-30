#!/bin/sh
# Runs TV Doctor against a REAL Android TV system image (no TV needed): detect, plan, apply, check, undo,
# and installs the release APKs. Needs the Android SDK command-line tools; downloads ~1.5 GB once.
# usage: tools/test-android-tv-emulator.sh [api-level, default 34]
set -e
API="${1:-34}"
SDK="${ANDROID_HOME:?set ANDROID_HOME to an Android SDK folder}"
export ANDROID_SDK_ROOT="$SDK" ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$SDK/avd}"
mkdir -p "$ANDROID_AVD_HOME"
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" "emulator" "system-images;android-$API;android-tv;x86" >/dev/null
echo no | "$SDK/cmdline-tools/latest/bin/avdmanager" create avd -n "atv$API" -k "system-images;android-$API;android-tv;x86" -d tv_1080p --force
"$SDK/emulator/emulator" -avd "atv$API" -no-window -no-audio -no-snapshot -gpu swiftshader_indirect -memory 2048 >/dev/null 2>&1 &
export PATH="$SDK/platform-tools:$PATH"
until [ "$(adb -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 5; done
cd "$(dirname "$0")/../tv-doctor"
python3 tvdoctor.py -s emulator-5554 detect
python3 tvdoctor.py -s emulator-5554 apply --tier safe --yes
python3 tvdoctor.py -s emulator-5554 undo
for a in ../dist/*.apk; do [ -f "$a" ] && python3 tvdoctor.py -s emulator-5554 install --apk "$a"; done
adb -s emulator-5554 emu kill
