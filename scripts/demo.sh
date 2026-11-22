#!/usr/bin/env bash
# Runs the demo questions against a local instance and prints readable output.
# Usage: set USER_KEY and ADMIN_KEY first, then ./scripts/demo.sh
set -euo pipefail
: "${USER_KEY:?set USER_KEY}"
: "${ADMIN_KEY:?set ADMIN_KEY}"
BASE=${BASE:-http://localhost:8080}

ask() {
  echo
  echo "Q: $1"
  body=$(python3 -c 'import json,sys; print(json.dumps({"question": sys.argv[1]}))' "$1")
  curl -sS -X POST "$BASE/ask" \
    -H "X-API-Key: $USER_KEY" -H "Content-Type: application/json" -d "$body" \
  | python3 -c '
import json, sys
d = json.load(sys.stdin)
print(d.get("answer") or d)
print()
print("sources:", "; ".join(sorted({s["source"] + " / " + s["section"] for s in d.get("sources", [])})))
'
}

echo "== Loading runbooks"
curl -sS -X POST "$BASE/ingest/runbooks" -H "X-API-Key: $ADMIN_KEY"
echo

ask "What command rolls back a bad payment-service deployment?"
ask "Is payment-service healthy right now, and what should I do about it?"
ask "What gateway latency threshold means the payment gateway is slow?"
ask "How do I rotate the Kubernetes API server certificate?"