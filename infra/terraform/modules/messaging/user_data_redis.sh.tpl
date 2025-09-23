#!/bin/bash
set -euxo pipefail

yum update -y
amazon-linux-extras install -y redis6

CONF_FILE="/etc/redis6.conf"
if [ ! -f "$CONF_FILE" ]; then
  CONF_FILE="/etc/redis/redis.conf"
fi

sed -i 's/^bind .*/bind 0.0.0.0/' "$CONF_FILE"
sed -i 's/^protected-mode yes/protected-mode no/' "$CONF_FILE"

systemctl enable redis6
systemctl start redis6
