#!/bin/bash
set -euxo pipefail

yum update -y
amazon-linux-extras install -y erlang
amazon-linux-extras install -y rabbitmq
yum install -y erlang rabbitmq-server

systemctl enable rabbitmq-server
systemctl start rabbitmq-server

rabbitmqctl add_user "${rabbitmq_user}" "${rabbitmq_password}" || true
rabbitmqctl set_user_tags "${rabbitmq_user}" administrator
rabbitmqctl set_permissions -p / "${rabbitmq_user}" ".*" ".*" ".*"
