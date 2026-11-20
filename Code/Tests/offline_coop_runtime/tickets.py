#!/usr/bin/env python3
"""Real stock-script ticket initialization, death, bleed and round reset.

Requires a compatible built server, QA local-human entrant and ticket_actions module.
The probe observes actual Python/engine ticket state; it does not replace game rules.
It triggers real local suicide and sets a controlled rate for the native bleed timer.
Only disposable copied content/configuration is written. No graphical client is modeled.
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
from layouts import copy_asset

PROBE = r'''import bf2, host, sys, layout_human, ticket_actions
status = 6
round = 0
phase = ''
started = 0
stamp = 0
nextSample = 0
victim = -1
deathCount = 0
def emit(kind, data):
    f = open('ticket_observations.log', 'a')
    f.write(repr((host.timer_getWallTime(), round, kind, data)) + '\n')
    f.close()
def changed(value):
    global status, round, phase, started
    status = value
    emit('status', value)
    if value == 1:
        phase = 'initial'
        started = host.timer_getWallTime()
def died(player, vehicle):
    global deathCount
    deathCount += 1
    emit('death', (player.index, player.getTeam(), bf2.gameLogic.getTickets(player.getTeam())))
def init():
    host.registerGameStatusHandler(changed)
    host.registerHandler('PlayerDeath', died, 1)
def shutdown(): pass
def update():
    try: observe()
    except:
        emit('error', str(sys.exc_info()[1]))
def observe():
    global phase, stamp, nextSample, victim, round
    now = host.timer_getWallTime()
    if now < nextSample: return
    nextSample = now + .1
    if status != 1: return
    # Stock load/reset can publish transient Playing/PreGame pairs on adjacent frames.
    if phase == 'initial' and now-started < .3: return
    human = layout_human.enter()
    if human >= 0 and phase != 'death_wait': layout_human.spawn(human)
    tickets = (bf2.gameLogic.getTickets(1), bf2.gameLogic.getTickets(2))
    if phase == 'initial':
        round += 1
        limits = [(bf2.gameLogic.getTicketLimit(t, 1), bf2.gameLogic.getTicketLimit(t, 2),
                   bf2.gameLogic.getTicketLimit(t, 3), bf2.gameLogic.getTicketLimit(t, 4)) for t in (1,2)]
        emit('initial', (tickets, (bf2.gameLogic.getDefaultTickets(1), bf2.gameLogic.getDefaultTickets(2)),
                         bf2.serverSettings.getTicketRatio(), limits))
        phase = 'ready'
    elif phase == 'ready' and now-started >= 1:
        if round == 1 and min(tickets) > 10:
            player = bf2.playerManager.getPlayerByIndex(human)
            if player and player.isAlive():
                victim = human
                emit('suicide', (human, player.getTeam(), tickets, deathCount))
                ticket_actions.suicide()
                phase = 'death_wait'
                stamp = now
        elif round == 1:
            emit('finish', tickets)
            bf2.gameLogic.setTickets(1, 0)
            phase = 'finish_wait'
        elif round == 2:
            emit('restart', tickets)
            host.rcon_invoke('admin.restartMap')
            phase = 'restart_wait'
        else:
            emit('done', tickets)
            phase = 'done'
    elif phase == 'death_wait' and now-stamp >= 3:
        player = bf2.playerManager.getPlayerByIndex(victim)
        emit('after_death', (tickets, deathCount, player.isAlive()))
        bf2.gameLogic.setTicketChangePerSecond(1, -3.0)
        bf2.gameLogic.setTicketChangePerSecond(2, 0.0)
        emit('bleed_start', tickets)
        stamp = now
        phase = 'bleed_wait'
    elif phase == 'bleed_wait' and now-stamp >= 1.5:
        emit('bleed_end', tickets)
        bf2.gameLogic.setTicketChangePerSecond(1, 0.0)
        emit('finish', tickets)
        bf2.gameLogic.setTickets(1, 0)
        phase = 'finish_wait'
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('binary', 'libs', 'base_game', 'human_module', 'actions_module', 'out'):
        parser.add_argument('--'+name.replace('_','-'), type=lambda x: Path(x).resolve(), required=True)
    parser.add_argument('--authored-levels', type=lambda x: Path(x).resolve())
    parser.add_argument('--map', default='strike_at_karkand')
    parser.add_argument('--mode', default='gpm_coop', choices=('gpm_coop','sp1','gpm_cq'))
    parser.add_argument('--size', type=int, default=16)
    parser.add_argument('--tickets', default='default,default')
    parser.add_argument('--expect', required=True, help='Expected actual start values, team1,team2')
    parser.add_argument('--port', type=int, default=22800)
    parser.add_argument('--timeout', type=int, default=150)
    parser.add_argument('--scope', choices=('offline','lan','wrongmap'), default='offline')
    args = parser.parse_args()
    assert 1024 <= args.port <= 65533 and args.port not in (16566,16567)
    selected = args.tickets.split(','); expected = tuple(map(int,args.expect.split(',')))
    assert len(selected) == len(expected) == 2
    args.out.mkdir(parents=True, exist_ok=True)
    game = args.out/'game'
    shutil.copytree(args.base_game, game, copy_function=copy_asset)
    if args.authored_levels:
        source = args.authored_levels/args.map/'server.zip'
        dest = game/'mods/bf2/levels'/args.map/'server.zip'
        dest.unlink(); shutil.copyfile(source,dest)
    shutil.copyfile(args.human_module,game/'admin/layout_human.so')
    shutil.copyfile(args.actions_module,game/'admin/ticket_actions.so')
    (game/'admin/ticket_probe.py').write_text(PROBE)
    values = dict(serverPort=args.port,gameSpyPort=args.port+1,serverIP='"127.0.0.1"',
        interfaceIP='"127.0.0.1"',internet=0,maxPlayers=1,voipEnabled=0,useGlobalRank=0,
        useGlobalUnlocks=0,autoRecord=0,adminScript='"ticket_probe"',numPlayersNeededToStart=1,
        autoBalanceTeam=0,coopBotCount=4,coopBotRatio=50,coopBotDifficulty=50,startDelay=1,
        endDelay=1,spawnTime=1,manDownTime=1,endOfRoundDelay=1,ticketRatio=100,timeLimit=0,roundsPerMap=3,swapTeamsOnMapChange=0)
    text = (game/'mods/bf2/settings/serversettings.con').read_text(encoding='latin1')
    for key,value in values.items():
        pattern=r'(?mi)^sv\.'+key+r'\s+[^\r\n]*';line='sv.%s %s'%(key,value)
        text=re.sub(pattern,lambda _:line,text) if re.search(pattern,text) else text+'\n'+line+'\n'
    (game/'ticket_config.con').write_text(text)
    (game/'ticket_maps.con').write_text('mapList.append %s %s %d\n'%(args.map,args.mode,args.size))
    env=dict(os.environ,LD_LIBRARY_PATH=str(args.libs),BF2_LAYOUT_TEST_HUMAN='1',TERM='xterm',
        BF2_PORT_LOCAL_COOP='1',BF2_PORT_DIRECT_START='1',BF2_PORT_HOST_LEVEL=args.map,
        BF2_PORT_HOST_MODE=args.mode,BF2_PORT_HOST_SIZE=str(args.size),
        BF2_PORT_COOP_TEAM1_BOTS='2',BF2_PORT_COOP_TEAM2_BOTS='2',BF2_PORT=str(args.port))
    if args.scope == 'lan': env['BF2_PORT_LOCAL_COOP']='0'
    if args.scope == 'wrongmap': env['BF2_PORT_HOST_LEVEL']='not_this_map'
    for i,value in enumerate(selected):
        key='BF2_PORT_TEAM%d_TICKETS'%(i+1)
        env.pop(key,None)
        if value != 'default': env[key]=value
    command=[str(args.binary),'+modPath','mods/bf2','+config','ticket_config.con','+mapList',
        'ticket_maps.con','+port',str(args.port),'+maxPlayers','1','+loadLevel',args.map,
        '+gameMode',args.mode,'+ai','0' if args.mode=='gpm_cq' else '1']
    observations=game/'ticket_observations.log'; samples=[]
    with (args.out/'server.log').open('wb') as log:
        process=subprocess.Popen(command,cwd=game,env=env,stdin=subprocess.DEVNULL,stdout=log,stderr=log)
        try:
            deadline=time.monotonic()+args.timeout
            while process.poll() is None and time.monotonic()<deadline:
                time.sleep(.3)
                if not observations.exists(): continue
                samples=[ast.literal_eval(line) for line in observations.read_text().splitlines() if line]
                errors=[r for r in samples if r[2]=='error']
                assert not errors,errors
                if any(r[2]=='done' for r in samples): break
            else: raise AssertionError('No completion: exit=%s samples=%s'%(process.poll(),samples[-5:]))
        finally:
            if process.poll() is None: process.terminate()
            try:process.wait(timeout=5)
            except subprocess.TimeoutExpired:process.kill();process.wait()
            if observations.exists():shutil.copyfile(observations,args.out/'observations.log')
    starts=[r for r in samples if r[2]=='initial']
    assert len(starts)==3,starts
    for row in starts:
        tickets,defaults,ratio,limits=row[3]
        assert tickets==expected and defaults==expected and ratio==100,row
        # Preserve the shipped team2 20% warning's team1 dependency.
        assert limits==[(0,10,int(expected[0]*.1),int(expected[0]*.2)),
                        (0,10,int(expected[1]*.1),int(expected[0]*.2))],row
    if min(expected)>10:
        before=next(r for r in samples if r[2]=='suicide')[3]
        after=next(r for r in samples if r[2]=='after_death')[3]
        assert after[1]>before[3] and after[0][before[1]-1]<before[2][before[1]-1],(before,after)
        bleed1=next(r for r in samples if r[2]=='bleed_start')[3]
        bleed2=next(r for r in samples if r[2]=='bleed_end')[3]
        assert bleed2[0]<=bleed1[0]-3,(bleed1,bleed2)
    assert any(r[2]=='status' and r[3]==2 for r in samples),'No real EndGame transition'
    assert any(r[2]=='restart' for r in samples),'No explicit restart exercised'
    loaded='[host-layout] Loaded exact %s/%d for %s'%(args.mode,args.size,args.map)
    assert loaded.lower() in (args.out/'server.log').read_text(errors='replace').lower()
    inputs=[game/'mods/bf2/python/game/gamemodes/gpm_coop.py',
            game/'mods/bf2/python/game/gamemodes/gpm_cq.py',
            game/'mods/bf2/levels'/args.map/'server.zip',
            game/'admin/layout_human.so',game/'admin/ticket_actions.so']
    result=dict(map=args.map,mode=args.mode,size=args.size,tickets=selected,expected=expected,
        scope=args.scope,authored_levels=str(args.authored_levels),starts=starts,observations=samples,
        inputs_sha256={str(p.relative_to(game)):hashlib.sha256(p.read_bytes()).hexdigest() for p in inputs},
        binary_sha256=hashlib.sha256(args.binary.read_bytes()).hexdigest())
    (args.out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    print('PASS tickets',args.map,args.size,selected,'->',expected,
          'death/bleed/end/restart' if min(expected)>10 else 'lower-bound/end/restart')


if __name__=='__main__':main()
