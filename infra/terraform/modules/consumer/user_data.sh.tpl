#!/bin/bash
set -euxo pipefail

yum update -y
yum install -y java-11-amazon-corretto-headless

install -d -m 0755 /opt/skier-consumer

cat <<'ENVVARS' >/opt/skier-consumer/.env
%{ for key, value in environment_variables ~}
${key}=${value}
%{ endfor ~}
ENVVARS

curl --fail --silent --show-error --location "${artifact_url}" --output /opt/skier-consumer/consumer.jar
cat <<'SERVICE' >/etc/systemd/system/skier-consumer.service
[Unit]
Description=Skier Lift RabbitMQ Consumer
After=network.target

[Service]
Type=simple
ExecStart=/usr/bin/java -jar /opt/skier-consumer/consumer.jar
EnvironmentFile=/opt/skier-consumer/.env
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
SERVICE

systemctl daemon-reload
systemctl enable skier-consumer.service
systemctl start skier-consumer.service
