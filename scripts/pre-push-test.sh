#!/usr/bin/env bash
# pre-push-test.sh — wrapper invoked by the pre-push hook for the "test" stage.
#
# Detection logic (either condition is sufficient):
#   1. /.dockerenv exists  (set by Docker for ALL Docker containers — including
#      act local CI runs; those get the flag too, which is an accepted trade-off)
#   2. REMOTE_CONTAINERS or DEVCONTAINER env var is non-empty
#      (set by VS Code Dev Containers / GitHub Codespaces)
#
# When IN the container:
#   - Resolve JAVA_HOME by asking the JVM itself (immune to symlinks and wrapper scripts)
#   - Run ./gradlew test -PdisableKoverInstrumentation (Kover↔MockK conflict, ADR-1)
#
# When NOT in the container:
#   - Run plain ./gradlew test (Kover instrumentation on; JAVA_HOME untouched)

set -euo pipefail

# Detect dev-container environment FIRST, before any JAVA_HOME manipulation.
if [ -f "/.dockerenv" ] || [ -n "${REMOTE_CONTAINERS:-}" ] || [ -n "${DEVCONTAINER:-}" ]; then
  # In-container: derive JAVA_HOME from the JVM itself — immune to symlinks and wrapper scripts.
  if command -v java >/dev/null 2>&1; then
    _jh="$(java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/[[:space:]]java\.home[[:space:]]*=/{print $2; exit}')"
    if [ -n "$_jh" ] && [ -x "$_jh/bin/java" ]; then
      export JAVA_HOME="$_jh"
    else
      echo "pre-push-test.sh: WARNING: could not derive JAVA_HOME from java; using existing JAVA_HOME=${JAVA_HOME:-<unset>}" >&2
    fi
  else
    echo "pre-push-test.sh: WARNING: java not found on PATH; using existing JAVA_HOME=${JAVA_HOME:-<unset>}" >&2
  fi
  # Gradle uses JAVA_HOME directly; no need to prepend to PATH.
  exec ./gradlew test -PdisableKoverInstrumentation "$@"
else
  # Outside container: run with full Kover instrumentation; leave JAVA_HOME untouched.
  exec ./gradlew test "$@"
fi
