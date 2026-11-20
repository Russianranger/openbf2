#!/usr/bin/env python3
"""Run the production handheld decoder against Robolectric Android 13 objects."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--out", type=Path, required=True, help="isolated Maven working directory")
parser.add_argument("--android-sdk", type=Path, default=os.environ.get("ANDROID_HOME"))
parser.add_argument("--androidx-jars", type=Path, help="optional directory containing monitor.jar and idling.jar")
parser.add_argument("--mvn", default="mvn")
parser.add_argument("--maven-arg", action="append", default=[])
args = parser.parse_args()
sdk = args.android_sdk
if sdk is None or not (sdk / "platforms/android-35/android.jar").is_file():
    parser.error("ANDROID_HOME/--android-sdk must contain platforms/android-35/android.jar")
out = args.out.resolve()
out.mkdir(parents=True, exist_ok=True)
tests = Path(__file__).resolve().parent
thor = tests.parent
for name in ("HandheldInput.java", "PadState.java", "PadMenuFrame.java", "ControllerBindings.java",
             "ControllerBindingsActivity.java", "GameplayActions.java", "TapHoldBindings.java"):
    target = out / "src/main/java/com/mackillergr/bf2" / name
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(thor / "src/com/mackillergr/bf2" / name, target)
for source, target in [(tests / "HandheldInputRuntimeTest.java", out / "src/test/java/com/mackillergr/bf2/HandheldInputRuntimeTest.java"),
                       (tests / "ControllerBindingsAndroidTest.java", out / "src/test/java/com/mackillergr/bf2/ControllerBindingsAndroidTest.java"),
                       (tests / "PadMenuFrameAndroidTest.java", out / "src/test/java/com/mackillergr/bf2/PadMenuFrameAndroidTest.java"),
                       (tests / "fixtures/SDLActivity.java", out / "src/main/java/org/libsdl/app/SDLActivity.java")]:
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, target)
libs = out / "lib"
libs.mkdir(exist_ok=True)
for name, artifact in [("monitor", "androidx/test/monitor/1.7.2/monitor-1.7.2.aar"),
                       ("idling", "androidx/test/espresso/espresso-idling-resource/3.6.1/espresso-idling-resource-3.6.1.aar")]:
    target = libs / (name + ".jar")
    if args.androidx_jars:
        shutil.copyfile(args.androidx_jars / target.name, target)
    elif not target.is_file():
        archive = libs / (name + ".aar")
        urllib.request.urlretrieve("https://dl.google.com/dl/android/maven2/" + artifact, archive)
        with zipfile.ZipFile(archive) as aar:
            target.write_bytes(aar.read("classes.jar"))
from xml.sax.saxutils import escape
android = escape(str((sdk / "platforms/android-35/android.jar").resolve()))
(out / "pom.xml").write_text('''<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
<groupId>openbf2.qa</groupId><artifactId>controller</artifactId><version>1</version>
<properties><maven.compiler.release>8</maven.compiler.release></properties><dependencies>
<dependency><groupId>android</groupId><artifactId>android</artifactId><version>35</version><scope>system</scope><systemPath>''' + android + '''</systemPath></dependency>
<dependency><groupId>junit</groupId><artifactId>junit</artifactId><version>4.13.2</version><scope>test</scope></dependency>
<dependency><groupId>org.robolectric</groupId><artifactId>robolectric</artifactId><version>4.14.1</version><scope>test</scope><exclusions>
<exclusion><groupId>androidx.test</groupId><artifactId>monitor</artifactId></exclusion>
<exclusion><groupId>androidx.test.espresso</groupId><artifactId>espresso-idling-resource</artifactId></exclusion></exclusions></dependency>
<dependency><groupId>local.androidx</groupId><artifactId>monitor</artifactId><version>1.7.2</version><scope>system</scope><systemPath>${project.basedir}/lib/monitor.jar</systemPath></dependency>
<dependency><groupId>local.androidx</groupId><artifactId>idling</artifactId><version>3.6.1</version><scope>system</scope><systemPath>${project.basedir}/lib/idling.jar</systemPath></dependency>
</dependencies><build><plugins><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId><version>3.5.2</version>
<configuration><systemPropertyVariables><robolectric.dependency.repo.url>https://repo.maven.apache.org/maven2</robolectric.dependency.repo.url></systemPropertyVariables></configuration>
</plugin></plugins></build></project>''')
subprocess.run([args.mvn, "-f", str(out / "pom.xml"), *args.maven_arg, "test"], check=True)
