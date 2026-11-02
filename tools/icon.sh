#!/bin/bash
# Renders the Annales icon (tools/icon.html) everywhere the app, the Play Store and the README use it:
#   icons/icon-1024.png, icon-512.png, icon-192.png   README and banner: rounded, transparent corners
#   icons/icon-playstore-512.png                      Play Store listing: full square, Google rounds it
#   res/mipmap-nodpi/ic_launcher_background.png       Android 8+ adaptive icon, back layer: the kraft envelope, full bleed
#   res/mipmap-nodpi/ic_launcher_foreground.png       Android 8+ adaptive icon, front layer: the stamp and its inks, inside the safe zone
#   res/mipmap-nodpi/ic_launcher.png                  older Android: rounded
# The themed icon and the status-bar icon are vectors (ic_launcher_monochrome.xml, ic_stat_annales.xml).
cd "$(dirname "$0")/.."
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
[ -x "$CHROME" ] || { echo "Google Chrome isn't in /Applications. Install it, or change CHROME in this file."; exit 1; }
RES=app/src/main/res
TMP="$(mktemp -d)"
mkdir -p icons "$RES/mipmap-nodpi" "$RES/drawable"
shoot() { # mode, file (always 1024 px; sips makes the smaller sizes)
  rm -f "$2"
  "$CHROME" --headless --disable-gpu --hide-scrollbars --no-first-run --no-default-browser-check --allow-file-access-from-files \
    --user-data-dir="$(mktemp -d)" --default-background-color=00000000 --force-device-scale-factor=1 --window-size=1024,1024 \
    --virtual-time-budget=2000 --screenshot="$2" "file://$PWD/tools/icon.html?mode=$1" >/dev/null 2>&1 || true
  [ -s "$2" ] || { echo "  $2 FAILED"; exit 1; }
}
size() { sips -Z "$1" "$2" --out "$3" >/dev/null && echo "  $3"; }
echo "Icon:"
shoot round "$TMP/round.png"
shoot full  "$TMP/full.png"
shoot bg    "$TMP/bg.png"
shoot fg    "$TMP/fg.png"
size 1024 "$TMP/round.png" icons/icon-1024.png
size 512  "$TMP/round.png" icons/icon-512.png
size 192  "$TMP/round.png" icons/icon-192.png
size 512  "$TMP/full.png"  icons/icon-playstore-512.png
size 432  "$TMP/bg.png"    "$RES/mipmap-nodpi/ic_launcher_background.png"
size 432  "$TMP/fg.png"    "$RES/mipmap-nodpi/ic_launcher_foreground.png"
size 192  "$TMP/round.png" "$RES/mipmap-nodpi/ic_launcher.png"
