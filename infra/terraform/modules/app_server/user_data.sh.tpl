#!/bin/bash
set -euxo pipefail

yum update -y
yum install -y java-11-amazon-corretto-headless tomcat

systemctl enable tomcat

install -d -o tomcat -g tomcat /opt/skier-app

cat <<'ENVVARS' >/opt/skier-app/.env
%{ for key, value in environment_variables ~}
${key}=${value}
%{ endfor ~}
ENVVARS

cp /opt/skier-app/.env /etc/default/skier-app
chown tomcat:tomcat /opt/skier-app/.env
chmod 640 /opt/skier-app/.env

curl --fail --silent --show-error --location "${artifact_url}" --output /tmp/app.war
cp /tmp/app.war /usr/share/tomcat/webapps/ROOT.war
chown tomcat:tomcat /usr/share/tomcat/webapps/ROOT.war

install -d /etc/systemd/system/tomcat.service.d
cat <<'DROPIN' >/etc/systemd/system/tomcat.service.d/env.conf
[Service]
EnvironmentFile=/etc/default/skier-app
DROPIN

systemctl daemon-reload
systemctl restart tomcat
