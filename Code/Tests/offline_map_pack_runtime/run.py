#!/usr/bin/env python3
"""Run real Android FileManager/ZIP/ModInfo against synthetic private map data.

By default links changed FileManager and ModInfo with a matching built engine.
--linked-only calls the exact supplied library, with no production replacements.
No game assets, gameplay or filesystem mocks.
"""
import argparse, hashlib, os, pathlib, subprocess, tempfile, zipfile
p=argparse.ArgumentParser(description=__doc__)
for name in ('source','out','ndk','library','sdl_include','runner'):
    p.add_argument('--'+name.replace('_','-'),type=pathlib.Path,required=True)
p.add_argument('--linked-only',action='store_true',help='Call the supplied libmain.so exports without rebuilding production code')
a=p.parse_args();a.source=a.source.resolve();a.out=a.out.resolve();a.out.mkdir(parents=True,exist_ok=True)
bf=a.source/'Code/BF2';root=pathlib.Path(tempfile.mkdtemp(prefix='game-',dir=a.out));pack='openbf2_maps_0123456789abcdef0123456789abcdef'
def write(path,text):
    path=root/path.as_posix().lower();path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text)
def desc(name,size):
    return f'<map><name>{name}</name><modes><mode type="gpm_coop"><maptype ai="1" players="{size}" type="assault">fixture</maptype></mode></modes></map>'
write(pathlib.Path('mods/bf2/mod.desc'),'<mod><title>Base</title></mod>')
for level in ('stockmap','stockmap_extra'):
    write(pathlib.Path(f'mods/bf2/levels/{level}/info/{level}.desc'),desc('Base StockMap',16))
    write(pathlib.Path(f'mods/bf2/levels/{level}/GameModes/gpm_coop/16/GamePlayObjects.con'),'BASE16')
    write(pathlib.Path(f'mods/bf2/levels/{level}/GameModes/gpm_coop/64/GamePlayObjects.con'),'BASE64')
    for name in ('server.zip','client.zip'):
        with zipfile.ZipFile(root/f'mods/bf2/levels/{level}/{name}','w') as z:
            z.writestr('GameModes/gpm_coop/16/GamePlayObjects.con','BASE16')
for level,title in [('stockmap','Pack StockMap'),('newmap','Pack NewMap'),('otherpackmap','Unselected')]:
    write(pathlib.Path(f'mods/{pack}/levels/{level}/info/{level}.desc'),desc(title,64))
    for archive,entries in [('server.zip',{'GameModes/gpm_coop/64/GamePlayObjects.con':'PACK64'}),('client.zip',{'client.marker':'PACK CLIENT'})]:
        with zipfile.ZipFile(root/f'mods/{pack}/levels/{level}/{archive}','w') as z:
            for name,text in entries.items():z.writestr(name,text)
for name,text in [('Common/shared.txt','BASE ART'),('Python/private.txt','BASE PYTHON')]:
    write(pathlib.Path('mods/bf2')/name,text);write(pathlib.Path('mods')/pack/name,'PACK MUST NOT SHADOW')
write(pathlib.Path(f'mods/{pack}/mod.desc'),'<mod><title>Must not be a mod</title></mod>')
def hashes():
    return {str(path.relative_to(root)):hashlib.sha256(path.read_bytes()).hexdigest()
            for path in (root/'mods'/pack).rglob('*') if path.is_file()}
before=hashes()
cxx=a.ndk/'toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang++'
flags=['-std=c++14','-O1','-g','-fPIC','-DDEDICATED=0','-D_LINUX','-DBF2_PLUGIN_ABI_NATIVE=1',
       '-DBF2_EDX_PARAM=','-D_ITERATOR_DEBUG_LEVEL=0','-fms-extensions','-fno-strict-aliasing','-fwrapv',
       '-include',str(bf/'Core/BuildConfig.h'),'-include',str(bf/'Core/PlatformCompat.h'),
       '-include',str(bf/'Core/PortableClient.h')]
if a.linked_only:flags+=['-fvisibility-inlines-hidden']
for inc in [bf,bf/'PortShim',bf/'PortShim/android',bf/'External/zlib-1.2.3',bf/'External/zlib-1.2.3/contrib/minizip',a.sdl_include]:
    flags+=['-I'+str(inc)]
sources=[] if a.linked_only else [bf/'IO/File/FileManager.cpp',bf/'Game/ModInfo.cpp']
sources += [a.source/'Code/Tests/offline_map_pack_runtime/probe.cpp']
objects=[]
for source in sources:
    obj=a.out/(source.stem+'.o');objects.append(str(obj))
    subprocess.run([str(cxx),*flags,'-c',str(source),'-o',str(obj)],check=True)
binary=a.out/'map-pack-runtime'
export_flags=[] if a.linked_only else ['-Wl,--export-dynamic']
subprocess.run([str(cxx),*objects,str(a.library.resolve()),*export_flags,
    '-Wl,-rpath-link,'+str(a.library.resolve().parent),'-ldl','-o',str(binary)],check=True)
run_env=os.environ.copy()
run_env['BFU_LIBS']=str(a.library.resolve().parent)+':'+run_env.get('BFU_LIBS','')
command=[str(a.runner.resolve()),str(binary),str(root)]
if a.linked_only:
    assert a.library.name=='libmain.so','Exact-library mode expects the packaged libmain.so filename'
    symbols=subprocess.check_output([str(cxx.parent/'llvm-nm'),'-D','--defined-only',str(binary)],text=True)
    assert not any(name in symbols for name in ('11FileManager','7ModInfo','12offline_maps')),'Probe must not interpose production map-pack symbols'
    command.append(str(a.library.resolve()))
    print('Exact libmain.so SHA256 '+hashlib.sha256(a.library.read_bytes()).hexdigest(),flush=True)
subprocess.run(command,env=run_env,check=True)
assert before==hashes(),'Read-only pack content changed'
assert (root/'mods/bf2/settings/career.ini').read_text()=='save'
print('PASS immutable pack hashes and base-mod save destination')
