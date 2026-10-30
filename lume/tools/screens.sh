#!/bin/bash
# Takes a real picture of every Lume screen from a TV connected with adb and saves them in ./screens/.
# Fire OS blocks the system screenshot, so Lume draws its own (see Shot.java). Look at the pictures for clipped
# text, overlapping pieces, buttons squeezed to a sliver and anything pushed off the screen.
# Usage: tools/screens.sh [ip:port]     (default: the only connected device)
set -euo pipefail
TV=${1:-}
ADB=(adb); [ -n "$TV" ] && ADB=(adb -s "$TV")
OUT=screens; mkdir -p "$OUT"
shot() {  # name, extra am start args...
  local n=$1; shift
  "${ADB[@]}" shell input keyevent KEYCODE_BACK >/dev/null; sleep 1
  "${ADB[@]}" shell "rm -f /sdcard/Download/lume-shots/shot_$n.png"
  "${ADB[@]}" shell am start -n org.lumesuite.launcher/.MainActivity "$@" --es shot "$n" >/dev/null
  sleep 9
  "${ADB[@]}" pull "/sdcard/Download/lume-shots/shot_$n.png" "$OUT/" >/dev/null && echo "ok  $n" || echo "MANCA $n"
}
"${ADB[@]}" shell am start -n org.lumesuite.launcher/.MainActivity >/dev/null; sleep 4
shot home
shot control_center --es panel cc
shot recent_apps    --es panel recents
for s in health markets news weather airplay; do shot "$s" --es open "$s"; done
echo "Pictures in $OUT/. Settings and Setup open from the Home (not reachable from adb)."
