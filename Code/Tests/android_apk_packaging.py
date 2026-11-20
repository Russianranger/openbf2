"""Regression checks for rebuilt plugin selection and update-compatible APK identities."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("thor_apk", ROOT / "android/thor/tools/build_apk.py")
APK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(APK)


class ApkPackaging(unittest.TestCase):
    def test_all_engine_plugins_are_required(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            for name in APK.REBUILT_LIBRARIES:
                (folder / name).write_bytes(b"fixture --coop\0")
            for name in APK.REBUILT_LIBRARIES:
                path = folder / name
                path.unlink()
                with self.assertRaisesRegex(ValueError, name):
                    APK.native_libraries(folder)
                path.write_bytes(b"fixture --coop\0")
            self.assertEqual(set(APK.native_libraries(folder)), set(APK.REBUILT_LIBRARIES))

    def test_update_identity(self):
        previous = ("io.github.russianranger.openbf2", 1, ["original-certificate"])
        APK.verify_update(previous, (previous[0], 2, previous[2]))
        for current in (("another.package", 2, previous[2]),
                        (previous[0], 1, previous[2]),
                        (previous[0], 2, ["substitute-certificate"])):
            with self.subTest(current=current), self.assertRaises(ValueError):
                APK.verify_update(previous, current)


if __name__ == "__main__":
    unittest.main()
