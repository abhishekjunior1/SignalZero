#!/usr/bin/env bash
# Drives every screen and control on a running emulator.
#
# Each step asserts three things, because the weakest of them is worthless on
# its own:
#   1. the app is still the foreground activity   (a blind tap can leave it)
#   2. no FATAL EXCEPTION appeared in logcat
#   3. the screen shows the expected title        (so we know where we are)
#
# The first version of this script only checked for crashes. It navigated out of
# the app early on and kept tapping the Google launcher for thirty steps,
# reporting every one as a pass. Foreground and title assertions exist because
# of that.
set -uo pipefail

ANDROID_HOME=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
export PATH="$ANDROID_HOME/platform-tools:$PATH"
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$REPO/docs/screenshots/smoke"
PKG=com.medic.app
mkdir -p "$OUT"; rm -f "$OUT"/*.png "$OUT"/*.txt

NAV_Y=2255; NEARBY_X=108; LOCATION_X=324; ASSISTANT_X=540; MEDICAL_X=756; HOSPITAL_X=972
pass=0; fail=0; failed_steps=()

foreground() {
  # Android 15 reports topResumedActivity; mResumedActivity does not exist and
  # grepping for it silently returns empty, which reads as "left the app".
  adb shell dumpsys activity activities 2>/dev/null \
    | grep -m1 'topResumedActivity' \
    | sed -n 's/.* \([a-zA-Z0-9._]*\)\/.*/\1/p'
}

# Dump the visible text of the current screen via the accessibility tree.
screen_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml 2>/dev/null \
    | tr '>' '\n' | grep -o 'text="[^"]*"' | sed 's/text="//;s/"$//' | grep -v '^$'
}

step() { # name  expected-substring-in-screen-text  command...
  local name=$1 expect=$2; shift 2
  recover
  "$@" >/dev/null 2>&1
  sleep "${SLEEP:-4}"

  local fg crashes text problem=""
  fg=$(foreground)
  crashes=$(adb logcat -d 2>/dev/null | grep -c "FATAL EXCEPTION")
  text=$(screen_text)
  echo "$text" > "$OUT/$name.txt"
  adb exec-out screencap -p > "$OUT/$name.png" 2>/dev/null

  [ "$fg" = "$PKG" ] || problem="left the app (foreground=$fg)"
  [ "$crashes" -eq 0 ] || problem="${problem:+$problem; }CRASH in logcat"
  if [ -n "$expect" ] && ! grep -qiF "$expect" <<<"$text"; then
    problem="${problem:+$problem; }expected \"$expect\" on screen"
  fi

  if [ -n "$problem" ]; then
    echo "  ✗ $name — $problem"
    fail=$((fail+1)); failed_steps+=("$name: $problem")
  else
    echo "  ✓ $name"
    pass=$((pass+1))
  fi
}

# The in-app back arrow, top-left. `am start` will not do: MainActivity is a
# single instance, so re-launching resumes whatever screen was last open rather
# than returning Home. The system back key is worse -- from a top-level screen
# it exits the app entirely.
home() { adb shell input tap 72 178; }

# Some controls open a system picker (photo selection). If a step lands outside
# the app, back out until we are home again, so one stray tap does not poison
# every step after it.
recover() {
  local tries=0
  while [ "$(foreground)" != "$PKG" ] && [ $tries -lt 4 ]; do
    adb shell input keyevent 4; sleep 2; tries=$((tries+1))
  done
  if [ "$(foreground)" != "$PKG" ]; then
    adb shell am start -n $PKG/.MainActivity >/dev/null 2>&1; sleep 6
  fi
}

tap() { adb shell input tap "$1" "$2"; }
type_text() { adb shell input text "$1"; }

echo "=== reset ==="
adb shell pm clear $PKG >/dev/null 2>&1
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION RECORD_AUDIO CAMERA \
         READ_MEDIA_IMAGES POST_NOTIFICATIONS; do
  adb shell pm grant $PKG "android.permission.$p" 2>/dev/null
done
adb logcat -c
SLEEP=14 step 01-launch "" adb shell am start -n $PKG/.MainActivity

echo
echo "=== bottom navigation ==="
step 02-nav-nearby   "Nearby people"    tap $NEARBY_X   $NAV_Y
step 03-nav-location "My location"      tap $LOCATION_X $NAV_Y
step 04-nav-medical  "Medical help"     tap $MEDICAL_X  $NAV_Y
step 05-nav-hospital "Nearby hospital"  tap $HOSPITAL_X $NAV_Y
step 06-nav-assistant "Assistant"       tap $ASSISTANT_X $NAV_Y

echo
echo "=== home cards ==="
step 07-home          "How can I help you?"   home
step 08-card-mesh     "Nearby people"         tap 540 920
step 09-home          "How can I help you?"   home
step 10-tile-translate "Translate"            tap 280 1650
step 11-home          "How can I help you?"   home
step 12-tile-location  "My location"          tap 790 1650
step 13-home          "How can I help you?"   home
step 14-tile-medical   "Medical help"         tap 280 2010
step 15-home          "How can I help you?"   home
step 16-tile-hospital  "Nearby hospital"      tap 790 2010

echo
echo "=== assistant: triage a negation case ==="
step 17-assistant "Assistant" tap $ASSISTANT_X $NAV_Y
tap 430 1840; sleep 1
type_text "the%sbleeding%shasn%27t%sstopped"; sleep 1
step 18-typed "hasn't stopped" true
SLEEP=12 step 19-sent "" tap 992 1840

echo
echo "=== translate ==="
step 20-translate "Translate" tap $MEDICAL_X $NAV_Y
step 21-translate "Translate" home
step 22-translate "Translate" tap 280 1650
tap 450 780; sleep 1
type_text "where%sdoes%sit%shurt"; sleep 1
SLEEP=10 step 23-translated "" tap 540 1050

echo
echo "=== location: modes and sun sighting ==="
step 24-location "My location" tap $LOCATION_X $NAV_Y
step 25-night    "Night"       tap 780 1700
step 26-day      "Day"         tap 290 1700
step 27-sun      ""            tap 540 1880

echo
echo "=== hospital ==="
step 28-hospital "Nearby hospital" tap $HOSPITAL_X $NAV_Y
step 29-update   "Nearby hospital" tap 540 830

echo
echo "=== medical ==="
step 30-medical "Medical help" tap $MEDICAL_X $NAV_Y
step 31-scroll  "kit"          adb shell input swipe 540 1800 540 700 350

echo
echo "=== mesh: chat and SOS ==="
step 32-mesh "Nearby people" tap $NEARBY_X $NAV_Y
tap 430 1840; sleep 1
type_text "moving%sto%sthe%snorth%smarker"; sleep 1
step 33-typed "moving" true
step 34-sent  "moving" tap 992 1840
SLEEP=6 step 35-sos "SOS" tap 540 1990

echo
echo "════════════════════════════════════"
echo "  $pass passed, $fail failed"
if [ "$fail" -gt 0 ]; then
  printf '  - %s\n' "${failed_steps[@]}"
fi
echo "  evidence: $OUT (png + visible-text dump per step)"
[ "$fail" -eq 0 ]
