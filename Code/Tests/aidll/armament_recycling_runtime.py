#!/usr/bin/env python3
"""Link Android armament and nav regressions against the actual built AI object manifest."""
import argparse
import importlib.util
from pathlib import Path
import subprocess

here = Path(__file__).resolve().parent
p = argparse.ArgumentParser()
p.add_argument('--ai-out', type=Path, required=True)
p.add_argument('--ndk', type=Path, required=True)
p.add_argument('--out', type=Path, required=True)
p.add_argument('--source-root', type=Path, default=here.parents[2])
a = p.parse_args()
a.out.mkdir(parents=True, exist_ok=True)
bf = a.source_root / 'Code/BF2'
spec = importlib.util.spec_from_file_location('observers', here / 'information_frontiers_runtime.py')
o = importlib.util.module_from_spec(spec)
spec.loader.exec_module(o)
o.CODE = a.source_root / 'Code'
base = [('IBase', 'Core/IBase.h'), ('IObjectComponent', 'Game/Objects/IObjectComponent.h')]
fk = 'AI/AIInterface/BFKnowledge/'
lines = ['namespace dice { namespace hfe { namespace ai {']
o.observer(lines, 'WeaponObserver', 'IWeapon', base + [('IWeapon', fk+'IWeapon.h')],
           {'int getWeaponType() const', 'int getWeaponActivate() const', 'int getWeaponFire() const'}, [])
o.observer(lines, 'EngineObject', 'IAIObject', base + [('IAIObject', fk+'IAIObject.h')],
           {'IAIObjectArmament* getArmament() const', 'void update()'},
           ['IAIObjectArmament* armament = nullptr;', 'unsigned int updates = 0;'])
o.observer(lines, 'EngineArmament', 'IAIObjectArmament',
           [('IAIObjectPlugIn', fk+'IAIObjectPlugIn.h'), ('IAIObjectArmament', fk+'IAIObjectPlugIn.h')],
           {'void update()', 'const std::vector<IWeapon*>& getWeapons() const',
            'IWeapon* getLastFiredWeapon() const', 'int getWeaponIdFromActivationId(int activation, int fire) const'},
           ['EngineArmament() : IAIObjectArmament(nullptr) {}',
            'std::vector<IWeapon*> weapons;', 'IPIArmament* view = nullptr;', 'IWeapon* last = nullptr;'])
lines.append('} } }')
(a.out / 'armament_observers.inl').write_text('\n'.join(lines)+'\n')
objects = [a.ai_out / (s.strip()[:-4]+'.o') for s in
           (a.source_root / 'Code/Build/aidll_sources.txt').read_text().splitlines()
           if s.strip() and not s.strip().startswith('#')]
assert all(path.exists() for path in objects), 'Build the full Android AI manifest first'
cxx = a.ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android28-clang++'
command = [str(cxx), '-std=c++14', '-O1', '-g', '-UNDEBUG', '-DDEDICATED=1', '-DBF2_IMAGE_AIDLL=1',
           '-DBF2_PLATFORM_POSIX=1', '-include', str(bf/'Core/BuildConfig.h'),
           '-include', str(bf/'Core/PlatformCompat.h'), '-I'+str(bf), '-I'+str(a.out),
           str(here/'armament_recycling_runtime.cpp'), str(bf/'Game/Objects/IObjectComponent.cpp'),
           *map(str, objects), '-Wl,-z,max-page-size=16384', '-o', str(a.out/'armament-check')]
subprocess.run(command, check=True)
print(a.out/'armament-check')
nav_command = command[:]
nav_command.remove(str(here/'armament_recycling_runtime.cpp'))
nav_command.remove(str(bf/'Game/Objects/IObjectComponent.cpp'))
nav_command[-1] = str(a.out/'nav-recycling-check')
nav_command.insert(-2, str(here/'nav_recycling_runtime.cpp'))
subprocess.run(nav_command, check=True)
print(a.out/'nav-recycling-check')
