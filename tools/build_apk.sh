#!/usr/bin/env bash
set -euo pipefail
# --- portable paths: override with env vars, defaults work in this sandbox -----
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/var/tmp/aosdk}}"
BT="${BUILD_TOOLS:-$SDK/build-tools/34.0.0}"
PLAT="$SDK/platforms/android-34/android.jar"
JAVA="${JAVA_BIN:-${JAVA_HOME:+$JAVA_HOME/bin/java}}"; JAVA="${JAVA:-/var/tmp/jdk17/bin/java}"
JAVAC="${JAVAC_BIN:-${JAVA_HOME:+$JAVA_HOME/bin/javac}}"; JAVAC="${JAVAC:-/var/tmp/jdk17/bin/javac}"
KEYTOOL="${KEYTOOL_BIN:-/var/tmp/jdk17/bin/keytool}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# fall back to PATH when the sandbox JDK isn't present (e.g. on GitHub Actions)
command -v "$JAVA"   >/dev/null 2>&1 || JAVA="$(command -v java)"
command -v "$JAVAC"  >/dev/null 2>&1 || JAVAC="$(command -v javac)"
command -v "$KEYTOOL">/dev/null 2>&1 || KEYTOOL="$(command -v keytool)"
BUILD=$ROOT/build
APP=$ROOT/app
PKG=com.arena.nitrodrive
OUT=$ROOT/NitroDrive.apk

rm -rf "$BUILD/classes" "$BUILD/dex" "$BUILD/apk" "$BUILD/gen" "$BUILD"/*.apk "$BUILD"/*.zip
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$BUILD/apk"

echo "== 1. aapt2 compile resources"
"$BT/aapt2" compile --dir "$APP/res" -o "$BUILD/res.zip"

echo "== 2. aapt2 link"
"$BT/aapt2" link -o "$BUILD/app.unsigned.apk" -I "$PLAT" \
  --manifest "$APP/AndroidManifest.xml" \
  "$BUILD/res.zip" \
  --java "$BUILD/gen" \
  --min-sdk-version 23 --target-sdk-version 34 \
  --version-code 2 --version-name 1.0.1

echo "== 3. javac"
find "$APP/src" -name '*.java' > "$BUILD/sources.txt"
if ! "$JAVAC" -source 11 -target 11 -Xlint:-options -nowarn \
  -classpath "$PLAT" -sourcepath "$APP/src" -encoding UTF-8 \
  -d "$BUILD/classes" @"$BUILD/sources.txt"; then
  echo "JAVAC FAILED -- aborting build"; exit 1
fi
# every source file must produce a class, otherwise the APK is missing code
MISSING=0
for f in $(cat "$BUILD/sources.txt"); do
  cls=$(basename "$f" .java)
  found=$(find "$BUILD/classes" -name "$cls.class" | head -1)
  if [ -z "$found" ]; then echo "MISSING CLASS: $cls"; MISSING=1; fi
done
if [ "$MISSING" = "1" ]; then echo "COMPILE INCOMPLETE -- aborting build"; exit 1; fi
echo "   $(find $BUILD/classes -name '*.class' | wc -l) classes compiled"

echo "== 4. d8 dex"
"$BT/d8" --release --min-api 23 \
  --lib "$PLAT" \
  --output "$BUILD/dex" \
  $(find "$BUILD/classes" -name '*.class')

echo "== 5. package"
cp "$BUILD/app.unsigned.apk" "$BUILD/app.dexed.apk"
cd "$BUILD/dex"
cp "$BUILD/dex/classes.dex" "$BUILD/apk/classes.dex"
# add assets + dex into the apk
cd "$BUILD"
cp app.unsigned.apk stage.apk
cd "$APP"
zip -q -r "$BUILD/stage.apk" assets
cd "$BUILD"
(cd "$BUILD/dex" && zip -q "$BUILD/stage.apk" classes.dex)

echo "== 6. zipalign"
"$BT/zipalign" -f 4 "$BUILD/stage.apk" "$BUILD/aligned.apk"

echo "== 7. sign"
if [ ! -f "$ROOT/keystore.jks" ]; then
  "$KEYTOOL" -genkeypair -storetype JKS -keystore "$ROOT/keystore.jks" -storepass nitrodrive -keypass nitrodrive \
    -alias nitro -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Nitro Drive, OU=Games, O=Arena, L=Hyderabad, ST=Telangana, C=IN" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$ROOT/keystore.jks" --ks-pass pass:nitrodrive --key-pass pass:nitrodrive \
  --ks-key-alias nitro --out "$OUT" "$BUILD/aligned.apk"
"$BT/apksigner" verify --print-certs "$OUT" | head -5

ls -la "$OUT"

echo "== 8. verify launchability"
bash "$ROOT/tools/verify_apk.sh" "$OUT"
echo "APK_BUILD_OK"
