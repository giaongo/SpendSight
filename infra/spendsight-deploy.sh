#!/usr/bin/env bash
# OPS-09 · The only thing the CI deploy key can run on the VM.
#
# Installed as /usr/local/bin/spendsight-deploy (outside the repo, so a rollback
# to an older commit cannot remove it). The key's authorized_keys line forces it:
#
#   command="/usr/local/bin/spendsight-deploy",no-pty,no-port-forwarding,no-agent-forwarding,no-X11-forwarding ssh-ed25519 AAAA... github-actions-deploy
#
# SSH puts the requested command in $SSH_ORIGINAL_COMMAND. Accepted:
#   status               current commit and container state
#   deploy <40-hex sha>  check out that commit (must be on origin/master) and run Compose
#   rollback             go back to the commit saved before the last deploy
# Anything else is refused. Run by hand as root with the same words as arguments.
#
# Install or update on the VM:
#   install -m 0755 /opt/spendsight/infra/spendsight-deploy.sh /usr/local/bin/spendsight-deploy
set -euo pipefail

REPO=/opt/spendsight
PREV_FILE=/opt/spendsight-previous-commit
LOCK=/run/spendsight-deploy.lock

read -r -a ARGS <<< "${SSH_ORIGINAL_COMMAND:-$*}"
ACTION=${ARGS[0]:-}

refuse() { echo "refused: $*" >&2; exit 2; }
[ "${#ARGS[@]}" -le 2 ] || refuse "too many arguments"

# Chained with && on purpose: set -e is off when this runs inside an `if`.
compose_up() {
  cd "$REPO/infra" \
    && docker compose up -d --build --remove-orphans \
    && { docker image prune -f >/dev/null || true; }
}

case "$ACTION" in
  status)
    cd "$REPO"
    echo "commit $(git rev-parse HEAD)"
    [ -f "$PREV_FILE" ] && echo "previous $(cat "$PREV_FILE")"
    cd infra && docker compose ps --format '{{.Service}}: {{.Status}}'
    ;;

  deploy)
    sha=${ARGS[1]:-}
    [[ "$sha" =~ ^[0-9a-f]{40}$ ]] || refuse "deploy needs a full 40-character commit sha"
    exec 9>"$LOCK"; flock -n 9 || refuse "another deploy is running"
    cd "$REPO"
    git fetch --quiet origin master
    git merge-base --is-ancestor "$sha" origin/master || refuse "$sha is not on origin/master"
    git rev-parse HEAD > "$PREV_FILE"
    git checkout --quiet --force --detach "$sha"
    if ! (compose_up); then
      # The new commit did not build or start: put the previous one back before failing.
      prev=$(cat "$PREV_FILE")
      echo "compose failed for ${sha:0:7}; restoring ${prev:0:7}" >&2
      git checkout --quiet --force --detach "$prev"
      compose_up
      exit 1
    fi
    echo "deployed ${sha:0:7}, previous $(cut -c1-7 "$PREV_FILE")"
    ;;

  rollback)
    [ "${#ARGS[@]}" -eq 1 ] || refuse "rollback takes no arguments"
    [ -s "$PREV_FILE" ] || refuse "no previous commit recorded"
    exec 9>"$LOCK"; flock -n 9 || refuse "another deploy is running"
    prev=$(cat "$PREV_FILE")
    cd "$REPO"
    git checkout --quiet --force --detach "$prev"
    compose_up
    echo "rolled back to ${prev:0:7}"
    ;;

  *)
    refuse "unknown command '${ACTION}'. Use: status | deploy <sha> | rollback"
    ;;
esac
