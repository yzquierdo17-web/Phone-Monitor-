#!/usr/bin/env bash
# Emulator smoke test: install, grant permissions, visit every tab, start the HUD,
# save screenshots + logcat to qa-out/.
set -u
PKG=com.vitals.monitor
OUT=qa-out
mkdir -p "$OUT"

shot() { adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }

tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1
  local xy
  xy=$(python3 - "$1" "$OUT/ui.xml" <<'EOF'
import re, sys
label, path = sys.argv[1], sys.argv[2]
xml = open(path, encoding="utf-8", errors="ignore").read()
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    t = re.search(r' text="([^"]*)"', n); d = re.search(r'content-desc="([^"]*)"', n)
    if (t and t.group(1) == label) or (d and d.group(1) == label):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        x1, y1, x2, y2 = map(int, b.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2); break
EOF
)
  if [ -n "$xy" ]; then adb shell input tap $xy; echo "tapped $1 at $xy"; else echo "NOT FOUND: $1"; fi
}

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb shell appops set $PKG GET_USAGE_STATS allow || true
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow || true
adb logcat -c

adb shell am start -n $PKG/.MainActivity
sleep 8
shot 1-overview
adb shell input swipe 500 1800 500 600 300; sleep 2; shot 1b-overview-scrolled
adb shell input swipe 500 1800 500 600 300; sleep 2; shot 1c-overview-scrolled

for tab in Network Apps Sensors System; do
  tap_text "$tab"; sleep 6; shot "2-$tab"
done

tap_text Overview; sleep 3
tap_text "Start HUD"; sleep 5; shot 3-hud-in-app
adb shell input keyevent KEYCODE_HOME; sleep 3; shot 4-hud-on-home

adb shell dumpsys activity services $PKG | grep -i "ServiceRecord" > "$OUT/services.txt" || true
adb logcat -d > "$OUT/logcat.txt"
grep -nE "FATAL EXCEPTION|AndroidRuntime|$PKG.*(Exception|Error)" "$OUT/logcat.txt" > "$OUT/crashes.txt" || true
echo "=== crashes ==="; cat "$OUT/crashes.txt" | head -60
echo "=== services ==="; cat "$OUT/services.txt"
exit 0
