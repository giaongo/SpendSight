#!/usr/bin/env bash
# Smoke test for the deployed backend (run after a CD deploy).
#
#   infra/smoke.sh                                   # default URL below
#   infra/smoke.sh https://api.<ip-dashes>.sslip.io
#
# Read-only: it never calls POST or PUT routes, so the in-memory design-sample
# ledger (effort setting, refiled payments) is left as the judges will see it.
set -uo pipefail

URL=${1:-${DEPLOY_URL:-https://api.95-133-254-29.sslip.io}}
URL=${URL%/}
HOST=${URL#https://}
fail=0

pass() { printf '  \033[32mok\033[0m   %s\n' "$1"; }
bad()  { printf '  \033[31mFAIL\033[0m %s\n' "$1"; fail=1; }

# get <path> -> body on stdout; non-2xx counts as failure
get() { curl -fsS --max-time 15 "$URL$1"; }

# expect <label> <path> <python expression on `d`> <expected>
expect() {
  local got
  got=$(get "$2" | python3 -c "import json,sys; d=json.load(sys.stdin); print($3)" 2>/dev/null) || got="(request failed)"
  if [ "$got" = "$4" ]; then pass "$1 = $4"; else bad "$1: expected $4, got $got"; fi
}

echo "== $URL"

echo "-- edge"
cert_end=$(echo | openssl s_client -connect "$HOST:443" -servername "$HOST" 2>/dev/null | openssl x509 -noout -enddate 2>/dev/null | cut -d= -f2)
if [ -n "$cert_end" ] && curl -fsS --max-time 15 -o /dev/null "$URL/healthz"; then
  pass "trusted TLS certificate (expires $cert_end)"
else
  bad "TLS certificate not trusted or site unreachable"
fi
code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 "http://$HOST/healthz")
if [ "$code" = 308 ]; then pass "http redirects to https"; else bad "http -> https redirect: got $code"; fi

echo "-- health"
expect "healthz ok"           /healthz        'd["ok"]'                     True
expect "index status"         /healthz        'd["index"]["status"]'        ready

echo "-- design sample (BACKLOG oracle table)"
expect "safe_to_spend_cents"  /ledger/summary 'd["safe_to_spend_cents"]'    102830

echo "-- Junction data"
expect "CUST00001 balance"    /customers/CUST00001/today 'd["balance_cents"]' 472358
expect "CUST00002 balance"    /customers/CUST00002 'd["balance_cents"]'     153916
expect "transactions indexed" /health         'd["transactions_rows"]'      3626755
code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 "$URL/customers/CUST99999")
if [ "$code" = 404 ]; then pass "unknown customer -> 404"; else bad "unknown customer: got $code"; fi

echo "-- screens the app opens with"
for path in /customers/CUST00007 /customers/CUST00007/today /customers/CUST00007/spending \
            /customers/CUST00007/save /campaigns /settings/effort /payments /charts /bonus /tools/week; do
  if get "$path" >/dev/null; then pass "GET $path"; else bad "GET $path"; fi
done

echo
if [ "$fail" = 0 ]; then echo "all checks passed"; else echo "some checks FAILED"; fi
exit "$fail"
