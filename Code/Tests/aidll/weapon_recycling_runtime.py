#!/usr/bin/env python3
"""Exercise real production weapon lifetimes under ASan and UBSan."""
import argparse
import importlib.util
from pathlib import Path
import subprocess

here = Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument('--out', type=Path, required=True)
args = parser.parse_args()
root = here.parents[2]
bf = root / 'Code/BF2'
out = args.out.resolve()
out.mkdir(parents=True, exist_ok=True)
spec = importlib.util.spec_from_file_location('observers', here / 'information_frontiers_runtime.py')
observers = importlib.util.module_from_spec(spec)
spec.loader.exec_module(observers)
observers.CODE = root / 'Code'
lines = ['namespace dice { namespace hfe { namespace ai {']
observers.observer(lines, 'WeaponObserver', 'IWeapon', [
    ('IBase', 'Core/IBase.h'), ('IObjectComponent', 'Game/Objects/IObjectComponent.h'),
    ('IWeapon', 'AI/AIInterface/BFKnowledge/IWeapon.h')], {'int getWeaponType() const'}, [])
lines.append('} } }')
(out / 'weapon_observer.inl').write_text('\n'.join(lines) + '\n')
ai = bf / 'AI/AIDLL/AI'
sources = [ai / 'AIUtils/WeaponInfoManager.cpp', ai / 'AIKnowledge/WeaponInfo.cpp',
           ai / 'AIKnowledge/WeaponInfoReal.cpp', ai / 'AIPlanInterpreter/BAPContext.cpp',
           bf / 'Game/Objects/IObjectComponent.cpp',
           bf / 'Core/Debug.cpp', bf / 'Core/StringUtil.cpp']
command = ['g++', '-std=c++14', '-O1', '-g', '-fsanitize=address,undefined',
           '-fno-omit-frame-pointer', '-DDEDICATED=1', '-DBF2_IMAGE_AIDLL=1',
           '-DBF2_PLATFORM_POSIX=1', '-include', str(bf / 'Core/BuildConfig.h'),
           '-include', str(bf / 'Core/PlatformCompat.h'), '-I' + str(bf), '-I' + str(out)]
command += [str(here / 'weapon_recycling_runtime.cpp'), *map(str, sources), '-o', str(out / 'weapon-test')]
subprocess.run(command, check=True)
result = subprocess.run([str(out / 'weapon-test')], text=True, capture_output=True)
(out / 'result.log').write_text(result.stdout + result.stderr)
print(result.stdout, end='')
if result.returncode:
    print(result.stderr)
result.check_returncode()
