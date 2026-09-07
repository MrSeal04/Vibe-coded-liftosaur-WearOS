#!/usr/bin/env bash
# PHASE 0: capture real API responses as DTO source-of-truth + test fixtures.
# STRICTLY READ-ONLY. Every call here is a GET; nothing mutates the account.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
load_key

OUT="${REPO_ROOT}/fixtures"
mkdir -p "$OUT"

# name:path pairs - GET only, by construction.
ENDPOINTS=(
  "settings:/settings"
  "programs:/programs"
  "program-current:/programs/current"
  "workout-next:/workout/next"
  "workout-current:/workout/current"
  "history-page1:/history?limit=5"
  "gyms:/gyms"
  "measurements:/measurements"
  "exercise-data:/exercise-data"
)

echo "==> Capturing schema (read-only) into fixtures/"
fail=0
for pair in "${ENDPOINTS[@]}"; do
  name="${pair%%:*}"; path="${pair#*:}"
  code=$(curl -sS -o "$OUT/${name}.json" -w '%{http_code}' \
    -H "Authorization: Bearer ${API_KEY}" -H "Accept: application/json" \
    "${API_BASE}${path}" || echo 000)
  if [[ "$code" == "200" ]]; then
    size=$(wc -c < "$OUT/${name}.json" | tr -d ' ')
    echo "  OK   ${name}  (${size}B)  ${path}"
    jq . "$OUT/${name}.json" > "$OUT/${name}.pretty.json" 2>/dev/null || echo "       WARN: not valid JSON"
  else
    echo "  FAIL ${name}  $(explain_http "$code")  ${path}"
    cat "$OUT/${name}.json" 2>/dev/null | head -c 300; echo
    fail=1
  fi
done

echo
echo "==> Key unknowns to resolve from these fixtures (see plan Phase 0):"
echo "    1. Are setIds server-assigned or client-generated? -> decides offline-start support"
jq -r '.data.workout.entries[0].sets[0].setId // "n/a"' "$OUT/workout-next.pretty.json" 2>/dev/null \
  | sed 's/^/       workout-next first setId: /'
echo "    2. Shape of superset / promptedVars / warmupSets:"
jq -r '.data.workout.entries[0] | {superset, promptedVars, warmupSets}' "$OUT/workout-next.pretty.json" 2>/dev/null | sed 's/^/       /'
echo "    3. History pagination envelope:"
jq -r '.data | {hasMore, nextCursor} // "n/a"' "$OUT/history-page1.pretty.json" 2>/dev/null | sed 's/^/       /'

exit $fail
