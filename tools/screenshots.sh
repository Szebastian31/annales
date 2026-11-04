#!/bin/bash
# Makes the README screenshots from tools/screens.html (mockups of the app's screens with a made-up month in Gliwice)
# and a banner with five of them in phone frames. Nothing on your phone or in your browser is touched.
#
#   tools/screenshots.sh
cd "$(dirname "$0")/.."
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
[ -x "$CHROME" ] || { echo "Google Chrome isn't in /Applications. Install it, or change CHROME in this file."; exit 1; }
ROOT="$(pwd)"
mkdir -p screenshots

shoot() { # url, file, width, height, pixel scale
  rm -f "$ROOT/screenshots/$2"
  "$CHROME" --headless --disable-gpu --hide-scrollbars --no-first-run --no-default-browser-check --allow-file-access-from-files \
    --user-data-dir="$(mktemp -d)" --force-device-scale-factor="$5" --window-size="$3,$4" \
    --virtual-time-budget=6000 --screenshot="$ROOT/screenshots/$2" "$1" >/dev/null 2>&1 || true
  if [ -s "$ROOT/screenshots/$2" ]; then echo "  screenshots/$2"; else echo "  screenshots/$2 FAILED"; fi
}

# each phone screen is 390 × 844 points at 3× (1170 × 2532 px), via tools/phone.html
echo "Phone screens:"
for s in today journal collections postmark insights; do
  shoot "file://$ROOT/tools/phone.html?screen=$s" "$s.png" 780 1688 1.5
done
echo "Banner:"
shoot "file://$ROOT/tools/banner.html" "banner.png" 1280 720 2
echo "Done. Open the folder with: open screenshots"
