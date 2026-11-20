#!/usr/bin/env python3
"""Run real map-layout/AI scenarios in an isolated dedicated-server fixture.

Optional local_human_module.cpp is a main-thread test entrant, not a retail client.
Stock/mod assets remain outside the repository and are never edited in place.
"""
import argparse
import ast
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time

PROBE = '''import bf2, host, sys, os
status = 6
nextSample = 0
human = -1
def changed(value):
    global status
    status = value
def init():
    global human
    host.registerGameStatusHandler(changed)
    if os.environ.get('BF2_LAYOUT_TEST_HUMAN') == '1':
        import layout_human
        human = layout_human.enter()
def shutdown():
    pass
def update():
    try:
        observe()
    except:
        f = open('layout_probe_error.log', 'a')
        f.write(str(sys.exc_info()[1]) + '\\n')
        f.close()
def observe():
    global nextSample, human
    now = host.timer_getWallTime()
    if now < nextSample: return
    nextSample = now + 1
    if os.environ.get('BF2_LAYOUT_TEST_HUMAN') == '1' and human < 0:
        import layout_human
        human = layout_human.enter()
    if human >= 0:
        import layout_human
        layout_human.spawn(human)
    players = []
    for player in bf2.playerManager.getPlayers():
        position = None
        vehicle = player.getVehicle()
        if vehicle: position = vehicle.getPosition()
        players.append((player.index, player.getTeam(), player.isAIPlayer(), player.isRemote(), player.isAlive(), position, player.score.kills))
    f = open('layout_observations.log', 'a')
    f.write(repr((now, status, bf2.gameLogic.isAIGame(), players)) + '\\n')
    f.close()
'''


def copy_asset(source, target):
    # Large immutable archives can share disk blocks; writable Python caches and
    # settings must have independent inodes in the disposable runtime tree.
    if str(source).lower().endswith('.zip'): return os.link(source, target)
    return shutil.copy2(source, target)


def fixture(base, target, authored=None):
    if not target.exists():
        shutil.copytree(base, target, copy_function=copy_asset)
    if authored:
        levels = target / 'mods/bf2/levels'
        for source in authored.glob('*/server.zip'):
            dest = levels / source.parent.name.lower() / 'server.zip'
            if dest.exists(): dest.unlink()
            shutil.copyfile(source, dest)
    (target / 'admin/layout_probe.py').write_text(PROBE)


def run(args, game, name, mode, size, team1, team2, human, index):
    tag = '%s-%s-%d-%d-%d-human%d' % (name, mode, size, team1, team2, human)
    out = args.out / tag
    out.mkdir(exist_ok=True)
    config = game / ('layout_config_%d' % index)
    config.mkdir(exist_ok=True)
    port = args.port + index * 2
    values = dict(serverPort=port, gameSpyPort=port+1, serverIP='"127.0.0.1"', interfaceIP='"127.0.0.1"',
        internet=0, maxPlayers=1, voipEnabled=0, useGlobalRank=0, useGlobalUnlocks=0, autoRecord=0,
        adminScript='"layout_probe"', numPlayersNeededToStart=1 if human else 0, autoBalanceTeam=0,
        coopBotCount=team1+team2, coopBotRatio=100.0*team1/(team1+team2) if team1+team2 else 0,
        coopBotDifficulty=50, startDelay=1, endDelay=1, spawnTime=1, ticketRatio=100, timeLimit=0)
    text = (game/'mods/bf2/settings/serversettings.con').read_text(encoding='latin1')
    for key,value in values.items():
        pattern = r'(?mi)^sv\.'+key+r'\s+[^\r\n]*'; line='sv.%s %s'%(key,value)
        text = re.sub(pattern,lambda _:line,text) if re.search(pattern,text) else text+'\n'+line+'\n'
    (config/'server.con').write_text(text)
    (config/'maps.con').write_text('mapList.append %s %s %d\n'%(name,mode,size))
    observations = game/'layout_observations.log'
    errors = game/'layout_probe_error.log'
    for old in (observations, errors):
        if old.exists(): old.unlink()
    env=dict(os.environ, LD_LIBRARY_PATH=str(args.libs or args.binary.parent), BF2_PORT_LOCAL_COOP='1',
        BF2_PORT_COOP_TEAM1_BOTS=str(team1), BF2_PORT_COOP_TEAM2_BOTS=str(team2),
        BF2_PORT_HOST_LEVEL=name, BF2_PORT_HOST_MODE=mode, BF2_PORT_HOST_SIZE=str(size),
        BF2_LAYOUT_TEST_HUMAN=str(human), TERM='xterm')
    if args.map_pack:
        env.update(BF2_PORT_MAP_PACK=args.map_pack.name, BF2_PORT_DIRECT_START='1',
            BF2_ANDROID_GAME_ROOT=str(game))
    command=[str(args.binary),'+modPath','mods/bf2','+config',str(config.relative_to(game)/'server.con'),
        '+mapList',str(config.relative_to(game)/'maps.con'),'+port',str(port),'+maxPlayers','1',
        '+loadLevel',name,'+gameMode',mode,'+ai','0' if mode=='gpm_cq' else '1']
    logpath=out/'server.log'; good=None; samples=[]
    with logpath.open('wb') as log:
        process=subprocess.Popen(command,cwd=game,env=env,stdin=subprocess.DEVNULL,stdout=log,stderr=log)
        try:
            deadline=time.monotonic()+args.timeout
            while process.poll() is None and time.monotonic()<deadline:
                time.sleep(1)
                if errors.exists(): raise AssertionError(errors.read_text())
                if args.case == 'missing' and 'refusing fallback' in logpath.read_text(errors='replace'):
                    print('PASS exact missing layout rejected', flush=True)
                    return
                if not observations.exists(): continue
                samples=[ast.literal_eval(line) for line in observations.read_text().splitlines() if line]
                if not samples: continue
                stamp,status,ai,players=samples[-1]
                first=sum(p[2] and p[1]==1 for p in players);second=sum(p[2] and p[1]==2 for p in players)
                humans=sum(not p[2] and not p[3] for p in players)
                expected=('[host-layout] Loaded exact %s/%d for %s'%(mode,size,name)).lower()
                valid=status==1 and first==team1 and second==team2 and humans==human and bool(ai)==(mode!='gpm_cq')
                valid=valid and expected in logpath.read_text(errors='replace').lower()
                if human: valid=valid and any(not p[2] and p[4] for p in players)
                if valid:
                    if good is None: good=time.monotonic()
                    if time.monotonic()-good>=args.hold: break
                else: good=None
            else:
                if args.case == 'missing' and 'refusing fallback' in logpath.read_text(errors='replace'):
                    print('PASS exact missing layout rejected', flush=True)
                    return
                raise AssertionError('%s failed exit=%s last=%s; see %s'%(tag,process.poll(),samples[-1:] or None,logpath))
        finally:
            if process.poll() is None: process.terminate()
            try: process.wait(timeout=5)
            except subprocess.TimeoutExpired: process.kill();process.wait()
    if observations.exists(): shutil.copyfile(observations,out/'observations.log')
    positions={}
    for _,_,_,players in samples:
        for p in players:
            if p[2] and p[5] is not None: positions.setdefault(p[0],set()).add(tuple(round(x,2) for x in p[5]))
    moved=sum(len(v)>1 for v in positions.values())
    result=dict(map=name,map_pack=args.map_pack.name if args.map_pack else None,mode=mode,size=size,team1=team1,team2=team2,humans=human,samples=len(samples),
        moving_bots=moved,kills=max(sum(p[6] for p in sample[3]) for sample in samples),final_players=samples[-1][3],binary_sha256=hashlib.sha256(args.binary.read_bytes()).hexdigest())
    (out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    if human and team1+team2: assert moved > 0, 'Bot roster never moved in the test round'
    print('PASS',tag,'moving bots',moved,flush=True)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('binary','base_game','authored_levels','out'):
        parser.add_argument('--'+name.replace('_','-'),type=lambda s:Path(s).resolve(),required=True)
    parser.add_argument('--libs',type=lambda s:Path(s).resolve(),help='Matching native side-library directory')
    parser.add_argument('--human-module',type=lambda s:Path(s).resolve())
    parser.add_argument('--map-pack',type=lambda s:Path(s).resolve(),help='Installed openbf2_maps_<id> generation')
    parser.add_argument('--mixed-case',action='store_true',help='Launch using a mixed-case map identity over canonical content paths')
    parser.add_argument('--local-human',action='store_true',help='Enter a QA local human in bot scenarios too')
    parser.add_argument('--port',type=int,default=19740)
    parser.add_argument('--timeout',type=int,default=150)
    parser.add_argument('--hold',type=int,default=25)
    parser.add_argument('--case',default='gulf32',choices=['gulf32','gulf64','karkand64','sp1','cq64','missing'])
    args=parser.parse_args();args.out.mkdir(parents=True,exist_ok=True)
    modded=args.case in ('gulf32','gulf64','karkand64')
    game=args.out/('authored-game' if modded else 'stock-game')
    fixture(args.base_game,game,args.authored_levels if modded and not args.map_pack else None)
    if args.map_pack:
        assert re.fullmatch(r'openbf2_maps_[0-9a-f]{32}', args.map_pack.name)
        dest=game/'mods'/args.map_pack.name
        if not dest.exists(): shutil.copytree(args.map_pack,dest,copy_function=copy_asset)
    if args.human_module: shutil.copyfile(args.human_module,game/'admin/layout_human.so')
    cases=dict(gulf32=('gulf_of_oman','gpm_coop',32,8,8,0),gulf64=('gulf_of_oman','gpm_coop',64,8,8,0),
        karkand64=('strike_at_karkand','gpm_coop',64,8,8,0),sp1=('strike_at_karkand','sp1',16,3,5,0),
        cq64=('gulf_of_oman','gpm_cq',64,0,0,1),missing=('gulf_of_oman','gpm_coop',64,0,0,0))
    assert args.port!=16567 and 1024<=args.port<=65534
    selected=list(cases[args.case])
    if args.local_human: selected[-1]=1
    if args.mixed_case:
        selected[0]='_'.join(x.capitalize() for x in selected[0].split('_'))
    if selected[-1] and not args.human_module: parser.error('--human-module required for local human')
    run(args,game,*selected,index=0)


if __name__=='__main__': main()
