#!/usr/bin/env bash
# OPS-04 · Copy the Junction data to the instance.
#
#   infra/sync-data.sh            # host from `terraform output public_ip`
#   infra/sync-data.sh 1.2.3.4    # or name it
#   SSH_KEY=~/.ssh/other infra/sync-data.sh   # key; default ~/.ssh/verda if present
#
# Sends the four CSVs to /data/junction and the local index to /data/cache.
# rsync -t keeps modification times, so on the instance the index stays newer
# than tx_junction_transactions.csv and the service opens it instead of
# re-indexing on first boot. Unchanged files are skipped, so it is safe to re-run.
#
# The CSVs never go into the image or into Git. If the index on the instance is
# lost, the service rebuilds it from the CSVs on start; the CSVs are the backup.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
SRC_DIR=${JUNCTION_DIR:-$ROOT/Junction-2026-S-pankki}
INDEX=${SPENDSIGHT_CACHE_DIR:-$ROOT/backend/.cache}/junction.sqlite
SSH_USER=${SSH_USER:-root}
CSVS=(
  tx_customers_junction.csv
  tx_junction_transactions.csv
  mtg_tx_customer_link_and_final_mtg_infio.csv
  mtg_negotiation_history.csv
)

HOST=${1:-$(terraform -chdir="$ROOT/infra/terraform" output -raw public_ip)}
TARGET="$SSH_USER@$HOST"
SSH_KEY=${SSH_KEY:-$HOME/.ssh/verda}
SSH_CMD=(ssh -o ConnectTimeout=15)
[ -f "$SSH_KEY" ] && SSH_CMD+=(-i "$SSH_KEY" -o IdentitiesOnly=yes)
ssh() { command "${SSH_CMD[@]}" "$@"; }
echo "== syncing Junction data to $TARGET"

# --- Local checks -------------------------------------------------------------
mtime() { stat -f %m "$1" 2>/dev/null || stat -c %Y "$1"; }   # macOS, then Linux

files=()
for name in "${CSVS[@]}"; do
  path="$SRC_DIR/$name"
  [ -f "$path" ] || { echo "missing $path" >&2; exit 1; }
  if head -c 40 "$path" | grep -q '^version https://git-lfs'; then
    echo "$name is a Git LFS pointer, not the CSV. Download the raw file first." >&2
    exit 1
  fi
  files+=("$path")
done

send_index=true
if [ ! -f "$INDEX" ]; then
  echo "no local index at $INDEX; the instance will build it on first start (several minutes)"
  send_index=false
elif [ "$(mtime "$INDEX")" -lt "$(mtime "$SRC_DIR/tx_junction_transactions.csv")" ]; then
  echo "local index is older than the transaction CSV, so the service would rebuild it anyway; not sending it"
  echo "(start the backend locally once to refresh it, then re-run)"
  send_index=false
fi

# --- Copy ---------------------------------------------------------------------
ssh "$TARGET" 'install -d -m 0755 /data/junction /data/cache'

rsync -e "${SSH_CMD[*]}" -t -h --partial --progress "${files[@]}" "$TARGET:/data/junction/"
if $send_index; then
  rsync -e "${SSH_CMD[*]}" -t -h --partial --progress "$INDEX" "$TARGET:/data/cache/"
fi

# --- Verify on the instance ---------------------------------------------------
# The api container runs as UID 1000 and must be able to rebuild the index.
ssh "$TARGET" bash -s <<'EOF'
set -euo pipefail
chown 1000:1000 /data/cache
csv=/data/junction/tx_junction_transactions.csv
idx=/data/cache/junction.sqlite
ls -lh /data/junction /data/cache
if [ -f "$idx" ] && [ "$(stat -c %Y "$idx")" -ge "$(stat -c %Y "$csv")" ]; then
  echo "== index is newer than the CSV: the service will open it without re-indexing"
else
  echo "== no fresh index: the service will build it on start (several minutes)"
fi
EOF

echo "== done. If the stack is already up: ${SSH_CMD[*]} $TARGET 'cd /opt/spendsight/infra && docker compose restart api'"
