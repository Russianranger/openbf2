# OpenBF2 Android local career admin module. Compatible with embedded Python 2.3.
# [CHOSEN, not recovered] See docs/android-thor/DESIGN.md, Durable offline career.
import posix
import sys
import host

# The installer supplies CAREER_HOME in the first line of the installed module.
_session = None
_round = None
_pending = {}
_retry_frames = 0


def _active_session():
    try:
        reader = open(CAREER_HOME + '/active', 'r')
        try:
            return reader.readline(80).strip()
        finally:
            reader.close()
    except IOError:
        return None


def _round_id():
    random = open('/dev/urandom', 'rb')
    try:
        raw = random.read(16)
        if len(raw) != 16:
            raise IOError('could not allocate a career round ID')
        return ''.join(['%02x' % ord(raw[i:i+1]) for i in range(16)])
    finally:
        random.close()


def _human_score():
    # Only the listen server's local human earns this device's single career.
    local = []
    for player in host.pmgr_getPlayers():
        if (not host.pmgr_p_get('ai', player) and
            not host.pmgr_p_get('remote', player) and
            host.pmgr_p_get('team', player) in (1, 2)):
            local.append(player)
    if len(local) != 1:
        return None
    player = local[0]
    return (host.pmgr_getScore(player, 'score'), host.pmgr_getScore(player, 'kills'),
            host.pmgr_getScore(player, 'deaths'))


def _save_pending():
    # Each complete receipt is a single atomic rename. A launcher restart can import it.
    for key in list(_pending.keys()):
        score, kills, deaths = _pending[key]
        base = CAREER_HOME + '/receipts/' + key
        writer = open(base + '.tmp', 'w')
        try:
            writer.write('1 %d %d %d\n' % (score, kills, deaths))
            writer.flush()
            posix.fsync(writer.fileno())
        finally:
            writer.close()
        posix.rename(base + '.tmp', base + '.round')
        del _pending[key]


def _status(status):
    global _round
    if not _session or _session != _active_session():
        return
    try:
        if status == 1 and _round is None:  # GameStatus_Playing
            _round = _round_id()
        elif status == 2 and _round is not None:  # GameStatus_EndGame
            values = _human_score()
            if values is not None:
                _pending[_round] = values
            _round = None
            _save_pending()
        elif status == 3 or status == 5:  # PreGame, Restart (discard interrupted round)
            _round = None
    except Exception:
        sys.stderr.write('Offline career status: %s\n' % (sys.exc_info()[1],))


def init():
    global _session
    _session = _active_session()
    if _session:
        host.registerGameStatusHandler(_status)


def update():
    # Called every frame. No filesystem access while the queue is empty.
    global _retry_frames
    if _pending:
        if _retry_frames:
            _retry_frames = _retry_frames - 1
            return
        try:
            _save_pending()
        except (IOError, OSError):
            _retry_frames = 300


def shutdown():
    global _round
    if _session:
        host.unregisterGameStatusHandler(_status)
    _round = None
    if _pending:
        _save_pending()
