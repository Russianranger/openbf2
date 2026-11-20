# Offline career ledger regression

Run `Code/Tests/android-career-sqlite/run.sh` with `JAVA_HOME` pointing to JDK 17 or newer,
`ANDROID_HOME` to an SDK with Android 35, and Maven on PATH (or set `MVN`). Requires curl,
Python 3 and access to Maven Central and Google's Maven repository. Extra arguments are
forwarded to Maven, for example `-s /path/to/settings.xml` for your proxy configuration.
The first run downloads Robolectric 4.14.1 and its Android runtime into Maven's cache.
AndroidX AAR inputs are SHA-256 checked; no downloaded files are added to the APK.

The fixture compiles the unchanged production `OfflineCareer.java` and runs it under
Android 13 with Robolectric's **NATIVE** SQLite mode. It verifies earned rank/score,
closing and reopening the database, duplicate receipt replay with changed contents,
signed score adjustments, malformed receipt rejection, and launch registration/disarm.
The production SQLite connections really close between `summary` calls. This tests the
ledger and filesystem lifecycle; it is not a physical-device install or a played round.
Pair it with `Code/Tests/android_stock_career_registration.py` against the final APK for
the actual packaged ARM64 interpreter/stock-script registration proof.
