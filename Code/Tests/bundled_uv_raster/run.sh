#!/usr/bin/env bash
# Start Xvfb and the renderer in one process namespace.
set -euo pipefail
qa=$(cd "${1:?QA dependency root}" && pwd)
shift
export LD_LIBRARY_PATH="$qa/vkd3d-1.17/.libs:$qa/dxvk-build/src/d3d9:$qa/sysroot/usr/lib/x86_64-linux-gnu${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
display_num=${BFU_DISPLAY_NUMBER:-$(python3 -c 'import random; print(random.randrange(220,800))')}
"$qa/sysroot/usr/bin/Xvfb" ":$display_num" -screen 0 1024x768x24 \
 -nolisten local -nolisten unix -listen tcp -ac > "$qa/xvfb${display_num}.log" 2>&1 &
xserver_pid=$!
trap 'kill "$xserver_pid" 2>/dev/null || true; wait "$xserver_pid" 2>/dev/null || true' EXIT
export DISPLAY="127.0.0.1:$display_num" SDL_VIDEODRIVER=x11 DXVK_WSI_DRIVER=SDL2
export VK_ICD_FILENAMES="$qa/sysroot/usr/share/vulkan/icd.d/lvp_icd.json"
python3 - "$display_num" <<'PY'
import socket,sys,time
for attempt in range(100):
    try:
        connection=socket.create_connection(('127.0.0.1',6000+int(sys.argv[1])),timeout=.5)
        connection.close()
        break
    except OSError:
        time.sleep(.1)
else:
    raise RuntimeError('Xvfb TCP did not become ready')
PY
"$@"
