"""Host-binding fixture for the Android career admin script. Runs on Python 2.3 and 3.x.

Pass an empty scratch directory. The fixture creates private test receipts inside it.
"""
import posix
import sys


class Host:
    def registerGameStatusHandler(self, callback):
        self.callback = callback

    def unregisterGameStatusHandler(self, callback):
        assert callback == self.callback
        self.callback = None

    def pmgr_getPlayers(self):
        return list(self.players.keys())

    def pmgr_p_get(self, key, player):
        return self.players[player][key]

    def pmgr_getScore(self, player, key):
        return self.players[player][key]


def save(path, data):
    stream = open(path, 'w')
    stream.write(data)
    stream.close()


def read_receipts():
    result = []
    for item in posix.listdir(receipts):
        assert item.endswith('.round') and len(item) == 38
        data = open(receipts + '/' + item, 'r')
        try:
            result.append(data.read().strip())
        finally:
            data.close()
    result.sort()
    return result


store = sys.argv[1]
receipts = store + '/receipts'
posix.mkdir(receipts)
host = Host()
host.players = {
    0: {'ai': 1, 'remote': 1, 'team': 1, 'score': 500, 'kills': 60, 'deaths': 0},
    1: {'ai': 0, 'remote': 1, 'team': 1, 'score': 700, 'kills': 90, 'deaths': 0},
    2: {'ai': 0, 'remote': 0, 'team': 2, 'score': -3, 'kills': 1, 'deaths': 4},
}
sys.modules['host'] = host
sys.path.insert(0, sys.argv[2])
import bf2_offline_career as career
career.CAREER_HOME = store
save(store + '/active', 'first')
career.init()
registered = open(store + '/registered', 'r')
assert registered.read().strip() == 'first'
registered.close()
for status in (3, 1, 1, 4, 1, 2, 2):
    host.callback(status)
assert read_receipts() == ['1 -3 1 4']

# No credit for an interrupted round, a spectator, a bot, or a remote human.
for status in (3, 1, 5, 2):
    host.callback(status)
host.players[2]['team'] = 0
for status in (3, 1, 2):
    host.callback(status)
assert read_receipts() == ['1 -3 1 4']

# Final write failure leaves the same round pending and is retried after the folder returns.
host.players[2].update({'team': 1, 'score': 162, 'kills': 8, 'deaths': 1})
host.callback(1)
posix.rename(receipts, receipts + '.offline')
host.callback(2)
posix.rename(receipts + '.offline', receipts)
career.update()
career.update()
assert read_receipts() == ['1 -3 1 4', '1 162 8 1']

# A new (or cancelled) launch cannot inherit a pending local player's result.
host.callback(1)
save(store + '/active', 'second')
host.callback(2)
assert len(read_receipts()) == 2
career.shutdown()
assert host.callback is None
sys.stdout.write('OK: local identity, signed score, completed rounds, retry, duplicate, restart scope\n')
