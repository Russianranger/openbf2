"""Run the APK's actual ARM64 Python and career hook with owner-supplied BF2 1.50 scripts.

Usage: python3 android_stock_career_registration.py APK SERVER_DIR NDK ANDROID_ROOT QEMU
ANDROID_ROOT is an extracted Android arm64 system with /system and /apex. QEMU is a
qemu-aarch64 executable. The native host callbacks model players; no game round is simulated.
"""
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import zipfile


DRIVER = r'''
extern void Py_Initialize(void);
extern int PyRun_SimpleString(const char *);
extern void Py_Finalize(void);
extern int Py_NoSiteFlag;
int main(void) {
    Py_NoSiteFlag = 1;
    Py_Initialize();
    int result = PyRun_SimpleString("import sys\nsys.path = ['pylib-2.3.4.zip','python','mods/bf2/python','admin']\nexecfile('probe.py')");
    Py_Finalize();
    return result != 0;
}
'''

PROBE = '''import sys
class Host:
    def __init__(self):
        self.status = []
        self.events = []
        self.players = []
    def registerGameStatusHandler(self, function): self.status.append(function)
    def unregisterGameStatusHandler(self, function): self.status.remove(function)
    def registerHandler(self, name, *args): self.events.append(name)
    def log(self, msg): sys.__stderr__.write(msg + '\\n')
    def sgl_setParam(self, *args): pass
    def sh_setEnableCommander(self, *args): pass
    def sgl_getIsAIGame(self): return 1
    def ss_getParam(self, key): return 0
    def pmgr_getPlayers(self): return self.players
    def pmgr_p_get(self, key, player):
        if key == 'team': return 1
        if key == 'ai': return player == 0
        if key == 'remote': return player != 1
        raise KeyError(key)
    def pmgr_getScore(self, player, key):
        assert player == 1
        return {'score':162, 'kills':8, 'deaths':1}[key]
host = Host()
sys.modules['host'] = host
import bf2
bf2.init_module()
import bf2_offline_career as career
career.init()
import game.gamemodes.gpm_coop as coop
coop.init()
assert career._status in host.status and coop.onGameStatusChanged in host.status
assert 'TimeLimitReached' in host.events and 'PlayerConnect' in host.events
host.players = [0, 1, 2]
career._status(bf2.GameStatus.Playing)
career._status(bf2.GameStatus.EndGame)
career._status(bf2.GameStatus.EndGame)
career.shutdown()
'''


def main(apk, server, ndk, android, qemu):
    with tempfile.TemporaryDirectory(prefix="bf2-stock-career-") as scratch:
        work = Path(scratch)
        for folder in ("python", "mods/bf2/python"):
            shutil.copytree(server / folder, work / folder, ignore=shutil.ignore_patterns("*.pyc", "*.pyo"))
        shutil.copyfile(server / "pylib-2.3.4.zip", work / "pylib-2.3.4.zip")
        (work / "admin").mkdir()
        (work / "lib").mkdir()
        receipts = work / "career/receipts"
        receipts.mkdir(parents=True)
        (work / "career/active").write_text("stock-probe")
        with zipfile.ZipFile(apk) as package:
            (work / "lib/libdice_py.so").write_bytes(package.read("lib/arm64-v8a/libdice_py.so"))
            asset = package.read("assets/bf2_offline_career.py")
            (work / "admin/bf2_offline_career.py").write_bytes(b"CAREER_HOME = 'career'\n" + asset)
        (work / "driver.c").write_text(DRIVER)
        (work / "probe.py").write_text(PROBE)
        compiler = ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang"
        subprocess.run([compiler, "driver.c", "-Llib", "-ldice_py", "-Wl,--allow-shlib-undefined",
                        "-o", "driver"], cwd=work, check=True)
        paths = [work / "lib", android / "apex/com.android.runtime/lib64/bionic",
                 android / "apex/com.android.runtime/lib64", android / "system/lib64"]
        subprocess.run([qemu, "-L", android, "-E", "LD_LIBRARY_PATH=" + ":".join(map(str, paths)),
                        "./driver"], cwd=work, check=True, timeout=30)
        assert (work / "career/registered").read_text().strip() == "stock-probe"
        assert [p.read_text().strip() for p in receipts.glob("*.round")] == ["1 162 8 1"]
        print("APK career hook, stock scoring/co-op registration, local-only result and dedup passed.")


if __name__ == "__main__":
    if len(sys.argv) != 6:
        raise SystemExit(__doc__)
    main(*(Path(arg).resolve() for arg in sys.argv[1:]))
