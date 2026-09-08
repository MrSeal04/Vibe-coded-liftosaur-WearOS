#!/usr/bin/env bash
# PHASE 0 SAFEGUARD 2: the disposable program all write testing runs against.
#
# Deliberately NOT made current. The API has no endpoint to set a current program
# anyway (checked 2026-09-08), and POST /workout/start takes an explicit programId
# that overrides the current one - so the real program is never touched.
#
# The exercise is one this account does not train: /workout/finish runs progressions
# and updates 1RMs, and an overlap would alter real training state.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
load_key

IDS_FILE="${REPO_ROOT}/.liftwear-testids"

api_post() {
  curl -sS --fail-with-body -X POST \
    -H "Authorization: Bearer ${API_KEY}" \
    -H "Content-Type: application/json" \
    -H "X-Liftosaur-Device-Id: liftwear-devbox" \
    -H "X-Liftosaur-Client: liftwear-test/0.1.0" \
    -d "$2" "${API_BASE}$1"
}

api_delete() {
  curl -sS --fail-with-body -X DELETE \
    -H "Authorization: Bearer ${API_KEY}" \
    -H "Content-Type: application/json" \
    -H "X-Liftosaur-Device-Id: liftwear-devbox" \
    -H "X-Liftosaur-Client: liftwear-test/0.1.0" \
    ${3:+-d "$3"} "${API_BASE}$1"
}

case "${1:-}" in
  create)
    # No progress block: nothing to progress, nothing to carry into the account.
    TEXT='# Week 1
## Test Day
Zercher Squat, Barbell / 3x5 / 100lb 60s
'
    body=$(jq -n --arg name "LiftWear TEST - delete me" --arg text "$TEXT" '{name:$name, text:$text}')
    resp=$(api_post "/programs" "$body")
    echo "$resp" | jq .
    pid=$(echo "$resp" | jq -r '.data.program.id // .data.id // empty')
    [[ -n "$pid" ]] && { echo "program:$pid" >> "$IDS_FILE"; echo "RECORDED program:$pid"; }
    ;;
  delete-program)
    # Explicit ID only, never a range. Refuses anything not in the scratch file.
    pid="$2"
    grep -qx "program:$pid" "$IDS_FILE" || { echo "REFUSING: $pid is not in $IDS_FILE" >&2; exit 1; }
    api_delete "/programs/${pid}" | jq .
    ;;
  delete-history)
    hid="$2"
    grep -qx "history:$hid" "$IDS_FILE" || { echo "REFUSING: $hid is not in $IDS_FILE" >&2; exit 1; }
    api_delete "/history/${hid}" | jq .
    ;;
  record-history)
    echo "history:$2" >> "$IDS_FILE"; echo "RECORDED history:$2"
    ;;
  list)
    cat "$IDS_FILE" 2>/dev/null || echo "(nothing recorded)"
    ;;
  *)
    echo "usage: testprog.sh {create|delete-program <id>|record-history <id>|delete-history <id>|list}" >&2
    exit 1
    ;;
esac
