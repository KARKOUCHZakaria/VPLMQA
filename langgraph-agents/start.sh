#!/bin/sh
set -eu

export DISPLAY="${DISPLAY:-:99}"
rm -f "/tmp/.X${DISPLAY#:}-lock"
Xvfb "$DISPLAY" -screen 0 "${E2E_SCREEN_SIZE:-1366x768x24}" -ac +extension RANDR &
openbox >/tmp/openbox.log 2>&1 &
x11vnc -display "$DISPLAY" -forever -shared -nopw -listen 0.0.0.0 -rfbport 5900 >/tmp/x11vnc.log 2>&1 &
websockify --web=/usr/share/novnc 0.0.0.0:7900 localhost:5900 >/tmp/novnc.log 2>&1 &

exec uvicorn main:app --host 0.0.0.0 --port 8090
