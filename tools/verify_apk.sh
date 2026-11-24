#!/usr/bin/env bash
# Post-build verification: fails loudly if the APK is not launchable.
set -euo pipefail
BT=/var/tmp/aosdk/build-tools/34.0.0
APK="${1:-/home/user/nitrodrive/NitroDrive.apk}"
fail=0

echo "== signature"
"$BT/apksigner" verify "$APK" && echo "   signature OK" || { echo "   SIGNATURE FAILED"; fail=1; }

echo "== manifest launcher"
if "$BT/aapt2" dump badging "$APK" 2>/dev/null | grep -q "launchable-activity.*com.arena.nitrodrive.MainActivity"; then
  echo "   launcher OK"
else
  echo "   NO LAUNCHABLE ACTIVITY"; fail=1
fi

echo "== required classes in dex"
TMP=$(mktemp -d); (cd "$TMP" && unzip -o -q "$APK" classes.dex)
DUMP=$TMP/dex.txt
"$BT/dexdump" -f "$TMP/classes.dex" 2>/dev/null > "$DUMP"
for c in MainActivity Game GameView World Draw Store CarArt Sfx Music; do
  if grep -qi "class descriptor  : 'Lcom/arena/nitrodrive/$c;'" "$DUMP"; then
    echo "   OK   $c"
  else
    echo "   MISSING $c"; fail=1
  fi
done

echo "== assets"
if unzip -l "$APK" | grep -q "assets/fonts/Audiowide-Regular.ttf"; then echo "   fonts OK"; else echo "   FONTS MISSING"; fail=1; fi

rm -rf "$TMP"
if [ "$fail" = "1" ]; then echo "VERIFY_FAILED"; exit 1; fi
echo "VERIFY_OK"
