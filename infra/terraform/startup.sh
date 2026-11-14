#!/usr/bin/env bash
# SpendSight VM bootstrap (OPS-01). Rendered by Terraform templatefile().
# Safe to run more than once: every step checks before it changes anything.
#
# Does: SSH by key only, firewall (22 to the team; 80/443 to all or the team),
# Docker Engine with the Compose plugin, /data, and a clone of the repo.
set -euo pipefail
exec > >(tee -a /var/log/spendsight-bootstrap.log) 2>&1
echo "== spendsight bootstrap $(date -Is)"

TEAM_CIDRS="${team_cidrs}"
WEB_OPEN_TO_ALL="${web_open_to_all}"
REPO_URL="${repo_url}"
REPO_BRANCH="${repo_branch}"
REPO_DIR=/opt/spendsight

export DEBIAN_FRONTEND=noninteractive

# --- SSH: key only -----------------------------------------------------------
install -d -m 0755 /etc/ssh/sshd_config.d
cat > /etc/ssh/sshd_config.d/10-spendsight.conf <<'EOF'
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin prohibit-password
EOF
systemctl reload ssh 2>/dev/null || systemctl reload sshd 2>/dev/null || true

# --- Packages ----------------------------------------------------------------
apt-get update -y
apt-get install -y ca-certificates curl git gnupg ufw rsync

# --- Docker Engine + Compose plugin (official apt repo) ----------------------
if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
  install -d -m 0755 /etc/apt/keyrings
  if [ ! -s /etc/apt/keyrings/docker.asc ]; then
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc
  fi
  . /etc/os-release
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $VERSION_CODENAME stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -y
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
systemctl enable --now docker

# --- /data (OPS-03 mounts these; OPS-04 rsyncs into them) --------------------
install -d -m 0755 /data /data/junction /data/cache
if [ ! -e /data/.env ]; then
  install -m 0600 /dev/null /data/.env   # secrets go here by hand, never in Git
fi

# --- Repo --------------------------------------------------------------------
if [ -d "$REPO_DIR/.git" ]; then
  echo "repo already at $REPO_DIR; leaving it for the deploy job (OPS-09)"
else
  git clone --branch "$REPO_BRANCH" "$REPO_URL" "$REPO_DIR"
fi

# --- Firewall ----------------------------------------------------------------
# Verda has no security groups, so ufw does it. Docker-published ports skip
# ufw's INPUT rules, so 80/443 for Caddy are also filtered in DOCKER-USER.
WAN_IF=$(ip -4 route show default | awk '{print $5; exit}')

ufw --force reset >/dev/null
ufw default deny incoming
ufw default allow outgoing
for cidr in $TEAM_CIDRS; do
  ufw allow from "$cidr" to any port 22 proto tcp comment 'ssh team'
done

AFTER_RULES=/etc/ufw/after.rules
sed -i '/^# BEGIN spendsight$/,/^# END spendsight$/d' "$AFTER_RULES"
if [ "$WEB_OPEN_TO_ALL" = "true" ]; then
  ufw allow 80/tcp comment 'http'
  ufw allow 443/tcp comment 'https'
  ufw allow 443/udp comment 'http3'
else
  for cidr in $TEAM_CIDRS; do
    ufw allow from "$cidr" to any port 80 proto tcp comment 'http team'
    ufw allow from "$cidr" to any port 443 comment 'https team'
  done
  {
    echo "# BEGIN spendsight"
    echo "*filter"
    echo ":DOCKER-USER - [0:0]"
    echo "-A DOCKER-USER -m conntrack --ctstate RELATED,ESTABLISHED -j RETURN"
    for cidr in $TEAM_CIDRS; do
      echo "-A DOCKER-USER -i $WAN_IF -s $cidr -p tcp -m multiport --dports 80,443 -j RETURN"
      echo "-A DOCKER-USER -i $WAN_IF -s $cidr -p udp --dport 443 -j RETURN"
    done
    echo "-A DOCKER-USER -i $WAN_IF -p tcp -m multiport --dports 80,443 -j DROP"
    echo "-A DOCKER-USER -i $WAN_IF -p udp --dport 443 -j DROP"
    echo "COMMIT"
    echo "# END spendsight"
  } >> "$AFTER_RULES"
fi
ufw --force enable
ufw reload

echo "== spendsight bootstrap done $(date -Is)"
