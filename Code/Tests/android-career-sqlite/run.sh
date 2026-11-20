#!/usr/bin/env bash
# Exercise production OfflineCareer with Android 13's actual native SQLite via Robolectric.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../.." && pwd)
: "${ANDROID_HOME:?Set ANDROID_HOME to an SDK with android-35}"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17 or newer}"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/lib" "$work/src/main/java/com/mackillergr/bf2" "$work/src/test/java/com/mackillergr/bf2"
cp "$here/pom.xml" "$work/pom.xml"
cp "$here/OfflineCareerTest.java" "$work/src/test/java/com/mackillergr/bf2/"
cp "$root/android/offline-career/java/OfflineCareer.java" "$work/src/main/java/com/mackillergr/bf2/"
curl -fsSL https://dl.google.com/dl/android/maven2/androidx/test/monitor/1.7.2/monitor-1.7.2.aar -o "$work/lib/monitor.aar"
curl -fsSL https://dl.google.com/dl/android/maven2/androidx/test/espresso/espresso-idling-resource/3.6.1/espresso-idling-resource-3.6.1.aar -o "$work/lib/idling.aar"
python3 - "$work/lib" <<'PYCODE'
from pathlib import Path
from zipfile import ZipFile
import hashlib,sys
folder=Path(sys.argv[1])
for name,wanted in [('monitor','868cc120d10d024b886fa157e1e1eaee0e6a8e5d55e7f765ef41d8fc0fea775b'),
                    ('idling','bc63a8780f1c707724e389f530cb2e1a6847da97f6a8114604c002aba2fd86ca')]:
    archive=folder/(name+'.aar')
    if hashlib.sha256(archive.read_bytes()).hexdigest()!=wanted:
        raise SystemExit('Unexpected AndroidX artifact digest: '+name)
    with ZipFile(archive) as package:
        (folder/(name+'.jar')).write_bytes(package.read('classes.jar'))
PYCODE
"${MVN:-mvn}" -B -f "$work/pom.xml" -Dandroid.jar="$ANDROID_HOME/platforms/android-35/android.jar" "$@" test
