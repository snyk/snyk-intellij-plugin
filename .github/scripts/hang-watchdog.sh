#!/usr/bin/env bash
# Hang watchdog for the Gradle `test` task.
#
# Sleeps past the normal run time, then repeatedly captures thread dumps of the
# Gradle test-worker JVM(s) so an intermittent CI hang can be diagnosed from the
# run's uploaded artifacts alone, with no local reproduction. It never triggers on
# a healthy run: that finishes before the threshold and the caller kills this
# watchdog while it is still sleeping.
#
# Both capture mechanisms are used:
#   1. `jstack -l` -> a file per dump (primary; `-l` includes lock ownership).
#   2. `kill -3` (SIGQUIT) -> the JVM's own stdout (Unix best-effort; on Windows a
#      JVM dumps on Ctrl-Break rather than SIGQUIT, so this is a no-op there and
#      jstack remains the source of truth).
#
# See .local/test-hang-capture-plan.md and the IDE-2237 investigation.
set -uo pipefail

THRESHOLD="${HANG_THRESHOLD_SECONDS:-1200}"      # 20m: above the ~16m real-run max, below the 25m Gradle test timeout
INTERVAL="${HANG_DUMP_INTERVAL_SECONDS:-120}"    # dump every 2m thereafter
DUMP_DIR="${HANG_DUMP_DIR:-build/hang-dumps}"

JAVA_BIN="${JAVA_HOME:+${JAVA_HOME}/bin/}"

capture() { # $1 = pid, $2 = outfile
  "${JAVA_BIN}jstack" -l "$1" >"$2" 2>&1 || "${JAVA_BIN}jcmd" "$1" Thread.print >"$2" 2>&1
}

sleep "$THRESHOLD"

iter=0
while true; do
  iter=$((iter + 1))
  ts="$(date +%Y%m%d-%H%M%S)"
  # (Re)create the dump dir every iteration: `./gradlew clean` deletes build/ at the start of the
  # step, so a dir created once up-front is gone by the time we dump.
  mkdir -p "$DUMP_DIR"
  pids="$("${JAVA_BIN}jps" -l 2>/dev/null | grep 'GradleWorkerMain' | awk '{print $1}')"
  if [ -z "$pids" ]; then
    echo "[hang-watchdog] iter ${iter} @ ${ts}: no GradleWorkerMain process found"
  else
    for pid in $pids; do
      out="${DUMP_DIR}/threaddump-${ts}-pid${pid}-${iter}.txt"
      if capture "$pid" "$out"; then
        echo "[hang-watchdog] iter ${iter} @ ${ts}: wrote ${out} for pid ${pid}"
      else
        echo "[hang-watchdog] iter ${iter} @ ${ts}: FAILED to capture pid ${pid}"
      fi
      kill -3 "$pid" 2>/dev/null || true
    done
  fi
  sleep "$INTERVAL"
done
