#!/usr/bin/env bash
# pre-push-test.sh — wrapper invoked by the pre-push hook for the "test" stage.
#
# JaCoCo is used as the coverage backend unconditionally (no flags needed). The ByteBuddy agent
# is preloaded automatically via build.gradle.kts jvmArgumentProviders. No container detection
# or coverage-flag logic is needed here.
#
# JAVA_HOME: resolved robustly from the running JVM when java is on PATH (immune to dangling
# symlinks in the Docker Desktop dev-container). No-op when java is absent (native devs who
# already have a correct JAVA_HOME set).

set -euo pipefail

# Resolve JAVA_HOME from the running JVM — immune to symlinks and wrapper scripts.
# Guarded so it is a no-op when java is not on PATH (native developers already have JAVA_HOME).
if command -v java >/dev/null 2>&1; then
  _jh="$(java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/[[:space:]]java\.home[[:space:]]*=/{print $2; exit}')"
  if [ -n "$_jh" ] && [ -x "$_jh/bin/java" ]; then
    export JAVA_HOME="$_jh"
  else
    echo "pre-push-test.sh: WARNING: could not derive JAVA_HOME from java; using existing JAVA_HOME=${JAVA_HOME:-<unset>}" >&2
  fi
fi

exec ./gradlew test "$@"
