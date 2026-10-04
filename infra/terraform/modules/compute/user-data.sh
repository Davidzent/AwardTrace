#!/bin/bash
# Prepares a fresh Amazon Linux 2023 host for the stack (doc 10). cloud-init runs it once, on first boot. The first
# deploy brings the Compose files and the start-up script; this installs only what they need.
set -euo pipefail

# Docker, and the Compose plugin, which Amazon Linux doesn't package. Pinned and checksummed like every dependency.
dnf install -y docker
compose_version=v5.6.0
compose_sha256=40343e21ca777173e69cff5dbafeb37c6f81f3b0d57d9e597f036e95eb63e76a
plugin=/usr/local/lib/docker/cli-plugins/docker-compose
mkdir -p "$(dirname "$plugin")"
curl -fsSL -o "$plugin" \
  "https://github.com/docker/compose/releases/download/${compose_version}/docker-compose-linux-x86_64"
echo "${compose_sha256}  ${plugin}" | sha256sum -c -
chmod 755 "$plugin"
systemctl enable --now docker

# A 2 GiB swap file as a safety net: with swappiness at 1, it is used only under real memory pressure.
if [ ! -f /swapfile ]; then
  dd if=/dev/zero of=/swapfile bs=1M count=2048
  chmod 600 /swapfile
  mkswap /swapfile
fi
swapon --show=NAME --noheadings | grep -qx /swapfile || swapon /swapfile
grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap defaults 0 0' >> /etc/fstab

cat > /etc/sysctl.d/90-awardtrace.conf <<'EOF'
vm.swappiness = 1
# Elasticsearch memory-maps its index files.
vm.max_map_count = 262144
EOF
sysctl --system
