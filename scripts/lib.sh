#!/usr/bin/env bash
# Shared helpers for LiftWear API scripts.
# The API key lives OUTSIDE the repo, in ~/.config/liftwear/api_key (mode 600).
set -euo pipefail

API_BASE="https://www.liftosaur.com/api/v1"
KEY_FILE="${LIFTWEAR_KEY_FILE:-$HOME/.config/liftwear/api_key}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

load_key() {
  if [[ ! -f "$KEY_FILE" ]]; then
    echo "ERROR: no API key at $KEY_FILE" >&2
    echo "Create it with:" >&2
    echo "  mkdir -p ~/.config/liftwear && install -m600 /dev/null ~/.config/liftwear/api_key" >&2
    echo "  printf '%s' 'lftsk_YOURKEY' > ~/.config/liftwear/api_key" >&2
    exit 1
  fi
  API_KEY="$(tr -d '[:space:]' < "$KEY_FILE")"
  if [[ "$API_KEY" != lftsk_* ]]; then
    echo "ERROR: key in $KEY_FILE does not start with 'lftsk_'" >&2
    exit 1
  fi
}

# Read-only GET. Never mutates.
api_get() {
  local path="$1"
  curl -sS --fail-with-body \
    -H "Authorization: Bearer ${API_KEY}" \
    -H "Accept: application/json" \
    "${API_BASE}${path}"
}

# Explain an HTTP failure in the API's own terms rather than a bare curl error.
explain_http() {
  case "$1" in
    401) echo "401 - API key invalid or revoked." ;;
    403) echo "403 - no active Liftosaur Premium subscription. The v1 API requires it." ;;
    404) echo "404 - not found." ;;
    409) echo "409 - conflict (workout_already_active / start_time_taken / mismatch / ambiguous_entry)." ;;
    422) echo "422 - Liftoscript parse or runtime error." ;;
    *)   echo "HTTP $1" ;;
  esac
}
