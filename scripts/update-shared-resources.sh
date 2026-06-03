#!/usr/bin/env bash
#
# Updates (or verifies) the static HTML resources that are vendored from the
# Snyk Language Server (snyk-ls) repository.
#
# These files are copies of the canonical versions in snyk-ls under
# shared_ide_resources/ui/html/. The CI "Static Resource Checking" workflow
# (.github/workflows/resource-check.yml) fails if a local copy drifts from its
# reference, so run this script to pull the latest versions into place.
#
# Usage:
#   scripts/update-shared-resources.sh            Download references into place
#   scripts/update-shared-resources.sh --check    Verify local files match references (no writes)
#
# The RESOURCES list below is the single source of truth for the mapping and is
# also consumed by CI via --check. Add new shared resources here.

set -euo pipefail

# Each entry maps a local file path (relative to the repo root) to its reference
# URL in snyk-ls, separated by a "|". URLs do not contain "|".
RESOURCES=(
  "src/main/resources/html/ScanSummaryInit.html|https://raw.githubusercontent.com/snyk/snyk-ls/refs/heads/main/shared_ide_resources/ui/html/ScanSummaryInit.html"
  "src/main/resources/html/settings-fallback.html|https://raw.githubusercontent.com/snyk/snyk-ls/refs/heads/main/shared_ide_resources/ui/html/settings-fallback.html"
)

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

# Compute a sha512 hex digest from stdin, using whichever tool is available
# (sha512sum on Linux/CI, shasum on macOS).
sha512() {
  if command -v sha512sum >/dev/null 2>&1; then
    sha512sum | awk '{print $1}'
  else
    shasum -a 512 | awk '{print $1}'
  fi
}

check_mode=false
case "${1:-}" in
  --check) check_mode=true ;;
  "") ;;
  *)
    echo "Unknown argument: $1" >&2
    echo "Usage: $0 [--check]" >&2
    exit 2
    ;;
esac

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

failures=0
for entry in "${RESOURCES[@]}"; do
  local_path="${entry%%|*}"
  url="${entry#*|}"
  tmp="$tmpdir/$(basename "$local_path")"

  # Download to a temp file so we preserve the exact bytes (including any
  # trailing newline) that CI hashes; command substitution would strip them.
  if ! curl -fsSL "$url" -o "$tmp"; then
    echo "ERROR:    failed to download $url" >&2
    failures=$((failures + 1))
    continue
  fi

  if $check_mode; then
    if [[ ! -f "$local_path" ]]; then
      echo "MISSING:  $local_path (no local file)"
      failures=$((failures + 1))
      continue
    fi
    candidate="$(sha512 <"$local_path")"
    reference="$(sha512 <"$tmp")"
    if [[ "$candidate" == "$reference" ]]; then
      echo "OK:       $local_path"
    else
      echo "DRIFT:    $local_path does not match $url"
      failures=$((failures + 1))
    fi
  else
    mkdir -p "$(dirname "$local_path")"
    cp "$tmp" "$local_path"
    echo "UPDATED:  $local_path"
  fi
done

if [[ $failures -gt 0 ]]; then
  if $check_mode; then
    echo "" >&2
    echo "$failures resource(s) out of sync. Run scripts/update-shared-resources.sh to update them." >&2
  fi
  exit 1
fi

if $check_mode; then
  echo "All shared resources are up to date."
else
  echo "Done. Review the changes and commit them."
fi
