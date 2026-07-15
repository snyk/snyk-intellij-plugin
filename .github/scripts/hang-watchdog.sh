#!/usr/bin/env bash
# Hang watchdog for the Gradle `test` task.
#
# Sleeps past the normal run time, then repeatedly captures thread dumps of the
# Gradle test-worker JVM(s) so an intermittent CI hang can be diagnosed from the
# run's uploaded artifacts alone, with no local reproduction. It never triggers on
# a healthy run: that finishes before the threshold and the caller kills this
# watchdog while it is still sleeping.
#
# Scope: this targets the Gradle *test worker* (`GradleWorkerMain`), which is where
# the known hang lives (IDE-2237: an EDT-dispatch deadlock in the test JVM). A hang
# that instead wedges the Gradle daemon or a compile/lint phase has no test worker,
# so this logs "no test-worker JVM found" and captures nothing — that means "not a
# test-worker hang", NOT "nothing wrong"; such a run is still bounded by the job
# `timeout-minutes` backstop, just without a thread dump.
#
# Capture uses two mechanisms:
#   1. `jstack -l` -> a file per dump (primary; `-l` includes lock ownership).
#   2. `kill -3` (SIGQUIT) -> the JVM's own stdout (Unix best-effort; on Windows a
#      JVM dumps on Ctrl-Break rather than SIGQUIT, so this is a no-op there and
#      jstack remains the source of truth).
# Each capture is time-bounded: jstack/jcmd attach via the JVM dynamic-attach
# socket, which can itself hang if the target is wedged in native code or a GC
# stall. Bounding it keeps the watchdog loop alive so later iterations still fire.
#
# See .local/test-hang-capture-plan.md and the IDE-2237 investigation.
set -uo pipefail

THRESHOLD="${HANG_THRESHOLD_SECONDS:-1200}"          # 20m: above the ~16m real-run max, below the 25m Gradle test timeout
INTERVAL="${HANG_DUMP_INTERVAL_SECONDS:-120}"        # dump every 2m thereafter
CAPTURE_TIMEOUT="${HANG_CAPTURE_TIMEOUT_SECONDS:-60}" # bound a single jstack/jcmd attach
DUMP_DIR="${HANG_DUMP_DIR:-build/hang-dumps}"

JAVA_BIN="${JAVA_HOME:+${JAVA_HOME}/bin/}"

# Run a command with its stdout+stderr to $out, killed if it exceeds CAPTURE_TIMEOUT.
# Portable (no coreutils `timeout`, which is absent on macOS runners). Returns 124 on timeout.
run_bounded() { # $1 = outfile; rest = command...
  local out="$1"; shift
  "$@" >"$out" 2>&1 &
  local p=$! waited=0
  while kill -0 "$p" 2>/dev/null; do
    if [ "$waited" -ge "$CAPTURE_TIMEOUT" ]; then
      kill "$p" 2>/dev/null || true
      return 124
    fi
    sleep 2
    waited=$((waited + 2))
  done
  wait "$p"
}

capture() { # $1 = pid, $2 = outfile
  run_bounded "$2" "${JAVA_BIN}jstack" -l "$1" && return 0
  # jstack failed or timed out. If it left a partial dump, keep it — do NOT let the jcmd fallback
  # truncate it (both use `>` on the same file). Only fall back when jstack produced nothing.
  [ -s "$2" ] && return 1
  run_bounded "$2" "${JAVA_BIN}jcmd" "$1" Thread.print
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
    echo "[hang-watchdog] iter ${iter} @ ${ts}: no test-worker JVM found (a daemon/compile-phase hang is not captured here)"
  else
    for pid in $pids; do
      out="${DUMP_DIR}/threaddump-${ts}-pid${pid}-${iter}.txt"
      if capture "$pid" "$out"; then
        echo "[hang-watchdog] iter ${iter} @ ${ts}: wrote ${out} for pid ${pid}"
      else
        echo "[hang-watchdog] iter ${iter} @ ${ts}: capture FAILED/timed out for pid ${pid} (kept partial ${out})"
      fi
      kill -3 "$pid" 2>/dev/null || true
    done
  fi
  sleep "$INTERVAL"
done
