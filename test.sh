#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

ANDROID_JAR="tools/platform33/android-13/android.jar"
JUNIT="tools/junit/junit-4.13.2.jar"
HAMCREST="tools/junit/hamcrest-core-1.3.jar"

OUT="build/test-classes"
rm -rf "$OUT"
mkdir -p "$OUT"

echo "==> Compilando lógica pura y tests"
# ScanController depende de service/ui y R generado; no es lógica pura y lo cubre build.sh.
find java/com/netscope/lan/core java/com/netscope/lan/util tests -name '*.java' \
    ! -path '*/core/net/ScanController.java' > build/test-sources.txt
javac --release 11 \
    -classpath "$ANDROID_JAR:$JUNIT:$HAMCREST" \
    -d "$OUT" \
    -Xlint:-options \
    @"build/test-sources.txt"

echo "==> Ejecutando tests"
java -cp "$OUT:$JUNIT:$HAMCREST:$ANDROID_JAR" org.junit.runner.JUnitCore \
    com.netscope.lan.test.SubnetMathTest \
    com.netscope.lan.test.ServiceCatalogTest \
    com.netscope.lan.test.CsvExporterTest \
    com.netscope.lan.test.JsonExporterTest \
    com.netscope.lan.test.ScanDiffEngineTest \
    com.netscope.lan.test.PortScannerLocalTest
