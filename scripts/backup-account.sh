#!/usr/bin/env bash
# PHASE 0 SAFEGUARD 1 of 3: full read-only export of the live account.
# Run this BEFORE the first write of the project. Read-only; makes no changes.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
load_key

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="${REPO_ROOT}/backups/${STAMP}"
mkdir -p "$OUT"

echo "==> Backing up live Liftosaur account to backups/${STAMP}/"

echo "  settings"; api_get "/settings"          > "$OUT/settings.json"
echo "  programs"; api_get "/programs"          > "$OUT/programs.json"
echo "  gyms";     api_get "/gyms"              > "$OUT/gyms.json"
echo "  measurements"; api_get "/measurements"  > "$OUT/measurements.json"
echo "  exercise-data"; api_get "/exercise-data" > "$OUT/exercise-data.json"

# Page history to exhaustion via the documented cursor.
echo "  history (paging)"
cursor=""; page=0
: > "$OUT/history-all.ndjson"
while :; do
  page=$((page+1))
  if [[ -n "$cursor" ]]; then q="/history?limit=100&cursor=${cursor}"; else q="/history?limit=100"; fi
  resp="$(api_get "$q")"
  echo "$resp" > "$OUT/history-page-$(printf '%03d' "$page").json"
  echo "$resp" | jq -c '.data.records[]? // .data[]? // empty' >> "$OUT/history-all.ndjson" 2>/dev/null || true
  more="$(echo "$resp" | jq -r '.data.hasMore // false' 2>/dev/null || echo false)"
  cursor="$(echo "$resp" | jq -r '.data.nextCursor // empty' 2>/dev/null || echo '')"
  echo "    page $page (hasMore=$more)"
  [[ "$more" == "true" && -n "$cursor" ]] || break
  [[ $page -ge 200 ]] && { echo "    stopping at 200 pages (safety cap)"; break; }
done

# Fetch each program's full Liftoscript source, so a program is restorable.
mkdir -p "$OUT/programs"
jq -r '.data.programs[]?.id // empty' "$OUT/programs.json" 2>/dev/null | while read -r pid; do
  [[ -n "$pid" ]] || continue
  echo "  program source: $pid"
  api_get "/programs/${pid}" > "$OUT/programs/${pid}.json"
done

records=$(wc -l < "$OUT/history-all.ndjson" | tr -d ' ')
echo
echo "==> Backup complete: $OUT"
echo "    history records: $records"
du -sh "$OUT"
