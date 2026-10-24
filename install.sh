#!/usr/bin/env bash
# Installs Hearth on a Fire TV over the network and makes the Home
# button open it.
#
#   ./install.sh <TV_IP>              install
#   ./install.sh <TV_IP> --uninstall  put the Amazon home screen back and remove the app
#
# Needs adb (Android platform-tools) on this computer and "ADB debugging" turned
# on in the TV's Developer options.
set -euo pipefail

PKG=app.hearth.launcher
GUARD=$PKG/$PKG.HomeGuardService

die() { echo "Error: $*" >&2; exit 1; }

[ $# -ge 1 ] || die "usage: ./install.sh <TV_IP> [--uninstall]"
command -v adb >/dev/null || die "adb not found. Install Android platform-tools first (see README)."

TV="$1"
[[ "$TV" == *:* ]] || TV="$TV:5555"

echo "Connecting to $TV ..."
adb connect "$TV" >/dev/null || true
for i in $(seq 1 30); do
    state=$(adb -s "$TV" get-state 2>/dev/null || true)
    [ "$state" = "device" ] && break
    [ "$i" = 1 ] && echo "If the TV shows \"Allow USB debugging?\", tick \"Always allow\" and choose OK."
    sleep 2
    adb connect "$TV" >/dev/null 2>&1 || true
done
[ "$(adb -s "$TV" get-state 2>/dev/null || true)" = "device" ] \
    || die "could not connect to $TV. Check the IP address and that ADB debugging is on."

sh() { adb -s "$TV" shell "$@" | tr -d '\r'; }

# The secure setting is a colon-separated list; keep any other services the user has.
services=$(sh settings get secure enabled_accessibility_services)
[ "$services" = "null" ] && services=""
others=$(printf '%s' "$services" | tr ':' '\n' | grep -v "^$GUARD$" | paste -sd: - || true)

if [ "${2:-}" = "--uninstall" ]; then
    if [ -n "$others" ]; then
        sh settings put secure enabled_accessibility_services "$others" >/dev/null
    else
        sh settings delete secure enabled_accessibility_services >/dev/null
        sh settings put secure accessibility_enabled 0 >/dev/null
    fi
    adb -s "$TV" uninstall "$PKG" >/dev/null || true
    sh input keyevent KEYCODE_HOME >/dev/null
    echo "Removed. The TV is back on the Amazon home screen."
    exit 0
fi

model=$(sh getprop ro.product.model)
os=$(sh getprop ro.build.version.name)
echo "Found: $model ($os)"
[ "$(sh getprop ro.product.manufacturer)" = "Amazon" ] \
    || echo "Warning: this doesn't look like a Fire TV. The app will install, but the Home button takeover only works on Fire OS."

# Use the newest of: a downloaded hearth.apk next to this script, or a local build.
here=$(cd "$(dirname "$0")" && pwd)
apk=$(ls -t "$here/hearth.apk" "$here/app/build/outputs/apk/release/app-release.apk" \
            "$here/app/build/outputs/apk/debug/app-debug.apk" 2>/dev/null | head -1 || true)
[ -n "$apk" ] || die "no APK found. Download hearth.apk from the Releases page into this folder."

echo "Installing $(basename "$apk") ..."
if ! out=$(adb -s "$TV" install -r "$apk" 2>&1); then
    echo "$out" >&2
    if printf '%s' "$out" | grep -q INSTALL_FAILED_UPDATE_INCOMPATIBLE; then
        die "a copy signed by someone else is already on the TV. Run ./install.sh $1 --uninstall first, then install again."
    fi
    die "install failed (see the message above)."
fi

echo "Taking over the Home button ..."
sh settings put secure enabled_accessibility_services "${others:+$others:}$GUARD" >/dev/null
sh settings put secure accessibility_enabled 1 >/dev/null

sh am start -n "$PKG/.MainActivity" >/dev/null
echo "Done. Press Home on the remote: Hearth should open."
echo "Your laptop isn't needed after this; the launcher starts by itself after a reboot."
