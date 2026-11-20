#!/usr/bin/env python3
"""Query real Karkand co-op rosters over GameSpy on an ARM64/Bionic server.

Requires a built server, Android runner/runtime and legally supplied BF2 server
content. No mocks or asset downloads; each case uses private config files/ports.
"""
import argparse
import collections
import hashlib
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import time


def query(sock, port):
    keys = (21, 22, 25, 26)  # name, score, team, deaths
    sock.sendto(b"\xfe\xfd\x00BOTS" + bytes((0, len(keys), *keys, 0)), ("127.0.0.1", port))
    try:
        data = sock.recv(16384)
    except (socket.timeout, ConnectionError):
        return None
    if data[:5] != b"\x00BOTS" or len(data) < 7:
        return None
    count = int.from_bytes(data[5:7], "big")
    values = data[7:].split(b"\0")
    if count > 64 or len(values) < count * len(keys):
        raise AssertionError("Malformed roster query")
    return [tuple(x.decode("latin1") for x in values[i:i + len(keys)])
            for i in range(0, count * len(keys), len(keys))]


def check(args, first, second, index):
    # Never use the production/default 16567 listener.
    port = args.port + index * 2
    query_port = port + 1
    config_name = "thor_roster_%d" % port
    config = args.game / config_name
    config.mkdir(exist_ok=False)
    values = dict(serverPort=port, gameSpyPort=query_port, internet=0,
                  serverIP='"127.0.0.1"', interfaceIP='"127.0.0.1"',
                  maxPlayers=1, voipEnabled=0, useGlobalRank=0, useGlobalUnlocks=0,
                  autoRecord=0, numPlayersNeededToStart=0, autoBalanceTeam=0,
                  coopBotCount=first + second, coopBotRatio=0 if not first + second else 100.0 * first / (first + second),
                  coopBotDifficulty=50, startDelay=1, endDelay=1, spawnTime=3,
                  ticketRatio=100, roundsPerMap=3, notEnoughPlayersRestartDelay=1)
    text = (args.game / "mods/bf2/settings/serversettings.con").read_text(encoding="latin1")
    for key, value in values.items():
        pattern = r"(?mi)^sv\." + key + r"\s+[^\r\n]*"
        line = "sv.%s %s" % (key, value)
        text = re.sub(pattern, lambda _: line, text) if re.search(pattern, text) else text + "\n" + line + "\n"
    (config / "serversettings.con").write_text(text)
    (config / "maplist.con").write_text("mapList.append strike_at_karkand gpm_coop 16\n")
    environment = dict(os.environ, BF2_PORT_LOCAL_COOP="1",
                       BF2_PORT_COOP_TEAM1_BOTS=str(first), BF2_PORT_COOP_TEAM2_BOTS=str(second),
                       BF2_AI_SPAWN_WITHOUT_HUMAN="1", BF2_ROUND_TRACE="1",
                       BFU_LIBS=str(args.libs), BF2_GAMESPY_DOMAIN="localhost", TERM="xterm")
    command = [str(args.runner), str(args.binary), "+modPath", "mods/bf2", "+config",
               config_name + "/serversettings.con", "+mapList", config_name + "/maplist.con",
               "+port", str(port), "+maxPlayers", "1"]
    path = args.out / ("roster-%d-%d.log" % (first, second))
    samples = []
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.settimeout(0.3)
    ai_path = None
    with path.open("wb") as log:
        process = subprocess.Popen(command, cwd=args.game, env=environment, stdin=subprocess.DEVNULL,
                                   stdout=log, stderr=log)
        try:
            deadline = time.monotonic() + args.timeout
            stable_since = None
            while process.poll() is None and time.monotonic() < deadline:
                time.sleep(1)
                data = path.read_bytes()
                if ai_path is None:
                    mapped = [line.split()[-1] for line in Path("/proc/%d/maps" % process.pid).read_text().splitlines()
                              if "libaidll.so" in line]
                    if mapped:
                        ai_path = Path(mapped[0]).resolve()
                        if ai_path != (args.libs / "libaidll.so").resolve():
                            raise AssertionError("Unexpected AI plugin: " + str(ai_path))
                rows = query(sock, query_port)
                live = re.search(rb"\[round\] state 2 -> 3[^\n]* players ([0-9]+) ", data)
                if rows is None or live is None:
                    continue
                # The query table is cached and initially empty even with bots.
                # Verify the real live-round population as well, especially 0/0.
                if int(live.group(1)) != first + second:
                    raise AssertionError("Engine live player count %s differs from requested %d" %
                                         (live.group(1).decode(), first + second))
                counts = collections.Counter(row[2] for row in rows)
                samples.append(dict(elapsed=round(args.timeout - (deadline - time.monotonic()), 2),
                                    counts=dict(counts), players=rows))
                if counts.get("1", 0) == first and counts.get("2", 0) == second and len(rows) == first + second:
                    if stable_since is None:
                        stable_since = time.monotonic()
                    if time.monotonic() - stable_since >= args.hold:
                        if ai_path is None:
                            raise AssertionError("Roster passed without verifying the AI plugin")
                        result = dict(team1=first, team2=second, engine_players=int(live.group(1)), samples=samples,
                                      ai=str(ai_path), ai_sha256=hashlib.sha256(ai_path.read_bytes()).hexdigest())
                        (args.out / ("roster-%d-%d.json" % (first, second))).write_text(json.dumps(result, indent=2) + "\n")
                        print("PASS exact bots %d/%d, %d live samples" % (first, second, len(samples)), flush=True)
                        return result
                else:
                    stable_since = None
            raise AssertionError("Roster %d/%d failed: exit=%s last=%s; see %s" %
                                 (first, second, process.poll(), samples[-1:] or "no live query", path))
        finally:
            if process.poll() is None:
                process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
            sock.close()
            for child in config.iterdir():
                child.unlink()
            config.rmdir()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("runner", "binary", "game", "libs", "out"):
        parser.add_argument("--" + name, type=lambda s: Path(s).resolve(), required=True)
    parser.add_argument("--port", type=int, default=18667)
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument("--hold", type=int, default=12)
    parser.add_argument("--cases", nargs="+", default=["0,0", "0,5", "7,0", "3,5", "31,32", "63,0"])
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    cases = [tuple(map(int, value.split(","))) for value in args.cases]
    assert all(len(pair) == 2 and min(pair) >= 0 and sum(pair) <= 63 for pair in cases)
    assert 1024 <= args.port and args.port + len(cases) * 2 < 65536
    assert not args.port <= 16567 < args.port + len(cases) * 2
    result = [check(args, first, second, i) for i, (first, second) in enumerate(cases)]
    (args.out / "summary.json").write_text(json.dumps(dict(binary=str(args.binary),
        sha256=hashlib.sha256(args.binary.read_bytes()).hexdigest(), cases=result), indent=2) + "\n")


if __name__ == "__main__":
    main()
