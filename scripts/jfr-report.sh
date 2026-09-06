#!/usr/bin/env bash
# Summarise a JFR recording down to the parts that matter for this mod.
#
#   scripts/jfr-report.sh run/teslamaps.jfr
#
# Covers the three things spark's render-thread sampler cannot see:
# allocation pressure by call site, GC/safepoint pauses, and work on
# threads other than the render thread (netty, workers).
set -euo pipefail

JFR_FILE="${1:-run/teslamaps.jfr}"
JFR_BIN="${JAVA_HOME:-}/bin/jfr"
[ -x "$JFR_BIN" ] || JFR_BIN=jfr

if [ ! -f "$JFR_FILE" ]; then
    echo "no recording at $JFR_FILE" >&2
    echo "record one with:  ./gradlew.bat runClient -Pjfr=120" >&2
    exit 1
fi

echo "=== event counts ==="
"$JFR_BIN" summary "$JFR_FILE"

echo
echo "=== allocation by teslamaps call site (top 25) ==="
"$JFR_BIN" print --events ObjectAllocationSample --stack-depth 40 "$JFR_FILE" \
    | grep -oE 'com\.teslamaps\.[A-Za-z0-9_.$]+\.[a-zA-Z0-9_]+' \
    | sort | uniq -c | sort -rn | head -25

echo
echo "=== allocated types (top 15) ==="
"$JFR_BIN" print --events ObjectAllocationSample "$JFR_FILE" \
    | grep -E '^\s+objectClass' | sort | uniq -c | sort -rn | head -15

echo
echo "=== GC pauses over 5ms ==="
"$JFR_BIN" print --events GCPhasePause "$JFR_FILE" \
    | grep -E 'duration = [0-9]{2,}' | head -20

echo
echo "=== cpu samples off the render thread, teslamaps frames (top 25) ==="
"$JFR_BIN" print --events ExecutionSample --stack-depth 40 "$JFR_FILE" \
    | awk '/^jdk.ExecutionSample/{t=""} /sampledThread/{t=$0} {print t"\t"$0}' \
    | grep -v 'Render thread' \
    | grep -oE 'com\.teslamaps\.[A-Za-z0-9_.$]+\.[a-zA-Z0-9_]+' \
    | sort | uniq -c | sort -rn | head -25
