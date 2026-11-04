#!/usr/bin/env bash
# Copies GA credentials onto a connected Wear OS watch for openAnalytics.
#
#   scripts/provision.sh <service-account.json> <ga4-property-id>
#   scripts/provision.sh --clear
#
# Uses the first device `adb` sees; set ANDROID_SERIAL to choose one.
# Debug builds: files go to the app's private storage (via run-as).
# Release builds: falls back to /sdcard/Android/data/<pkg>/files.
set -euo pipefail

PKG="dev.openanalytics.wear"
ADB="${ADB:-adb}"
EXT_DIR="/sdcard/Android/data/$PKG/files"

die() { echo "error: $*" >&2; exit 1; }
command -v "$ADB" >/dev/null || die "adb not found (set ADB=/path/to/adb)"
"$ADB" shell pm path "$PKG" >/dev/null 2>&1 || die "$PKG is not installed on the device. Install the APK first."

if [[ "${1:-}" == "--clear" ]]; then
  "$ADB" shell run-as "$PKG" rm -f files/service-account.json files/property-id.txt 2>/dev/null || true
  "$ADB" shell rm -f "$EXT_DIR/service-account.json" "$EXT_DIR/property-id.txt" 2>/dev/null || true
  echo "Credentials removed."
  exit 0
fi

[[ $# -eq 2 ]] || { sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 1; }
SA="$1"
PROP="${2#properties/}"
[[ -f "$SA" ]] || die "no such file: $SA"
[[ "$PROP" =~ ^[0-9]+$ ]] || die "property ID must be numeric (Admin > Property settings), not a G-XXXX measurement ID"
grep -q '"service_account"' "$SA" || die "$SA doesn't look like a service-account key"

if "$ADB" shell run-as "$PKG" true >/dev/null 2>&1; then
  "$ADB" shell run-as "$PKG" mkdir -p files
  "$ADB" exec-in run-as "$PKG" sh -c 'cat > files/service-account.json' < "$SA"
  printf '%s' "$PROP" | "$ADB" exec-in run-as "$PKG" sh -c 'cat > files/property-id.txt'
  "$ADB" shell run-as "$PKG" chmod 600 files/service-account.json files/property-id.txt
  echo "Installed credentials to private app storage."
else
  tmp="$(mktemp)"; trap 'rm -f "$tmp"' EXIT
  printf '%s' "$PROP" > "$tmp"
  "$ADB" shell mkdir -p "$EXT_DIR"
  "$ADB" push "$SA" "$EXT_DIR/service-account.json" >/dev/null
  "$ADB" push "$tmp" "$EXT_DIR/property-id.txt" >/dev/null
  echo "Installed credentials to $EXT_DIR (non-debuggable build)."
fi

"$ADB" shell am force-stop "$PKG"
"$ADB" shell am start -n "$PKG/.ui.MainActivity" >/dev/null
echo "Launched openAnalytics."
