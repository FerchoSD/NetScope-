#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

ANDROID_JAR="tools/platform33/android-13/android.jar"
KEYSTORE="keystore/netscope.jks"
KS_PASS="netscope"
KS_ALIAS="netscope"

OUT="build"
APK_NAME="NetScope-v1.0.apk"

[ -f "$ANDROID_JAR" ] || { echo "Falta $ANDROID_JAR"; exit 1; }
for tool in d8 apksigner aapt2 zipalign javac keytool; do
    command -v "$tool" >/dev/null || { echo "Falta $tool (pkg install $tool)"; exit 1; }
done

echo "==> Limpiando build"
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "==> Compilando recursos"
aapt2 compile --dir res -o "$OUT/res.zip"

echo "==> Enlazando recursos y manifest"
aapt2 link \
    -o "$OUT/base.apk" \
    -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    -R "$OUT/res.zip" \
    --java "$OUT/gen" \
    --min-sdk-version 26 \
    --target-sdk-version 33 \
    --version-code 1 \
    --version-name 1.0 \
    --auto-add-overlay

echo "==> Compilando Java"
find java "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac --release 11 \
    -classpath "$ANDROID_JAR" \
    -d "$OUT/classes" \
    -Xlint:-options \
    @"$OUT/sources.txt"

echo "==> Generando classes.dex"
jar cf "$OUT/classes.jar" -C "$OUT/classes" .
d8 --min-api 26 \
    --lib "$ANDROID_JAR" \
    --output "$OUT/dex" \
    "$OUT/classes.jar"

echo "==> Empaquetando"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -j "../unsigned.apk" classes.dex)

zipalign -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
    echo "==> Creando keystore"
    keytool -genkeypair \
        -keystore "$KEYSTORE" \
        -storepass "$KS_PASS" \
        -keypass "$KS_PASS" \
        -alias "$KS_ALIAS" \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=NetScope, O=NetScope, C=ES"
fi

echo "==> Firmando"
apksigner sign \
    --ks "$KEYSTORE" \
    --ks-pass "pass:$KS_PASS" \
    --key-pass "pass:$KS_PASS" \
    --ks-key-alias "$KS_ALIAS" \
    --min-sdk-version 26 \
    --out "$OUT/$APK_NAME" \
    "$OUT/aligned.apk"

mkdir -p dist
cp "$OUT/$APK_NAME" "dist/$APK_NAME"

echo "==> Verificando"
apksigner verify --verbose "dist/$APK_NAME"
aapt2 dump badging "dist/$APK_NAME" | sed -n '1,5p'

echo "APK listo: $ROOT/dist/$APK_NAME"
