#!/usr/bin/env bash
# Run after upgrade-wechat-reminders.sh. AppSecret is reused from the existing environment file.
set -euo pipefail
if [ "$(id -u)" -ne 0 ]; then echo 'Run with sudo.' >&2; exit 1; fi
state=${1:-trial}
case "$state" in trial|developer|formal) ;; *) echo 'Use trial, developer or formal.' >&2; exit 1;; esac
test -f /etc/ynu-shooting/backend.env
install -d -m 700 /var/backups/ynu-shooting
config_backup=$(mktemp /var/backups/ynu-shooting/reminders-env.XXXXXX)
cp -p /etc/ynu-shooting/backend.env "$config_backup"
chmod 600 "$config_backup"
restore_on_failure() {
    result=$?
    trap - EXIT
    if [ "$result" -ne 0 ]; then
        cp -p "$config_backup" /etc/ynu-shooting/backend.env
        systemctl restart ynu-shooting || true
        echo "Reminder configuration failed; previous environment restored. Backup: $config_backup" >&2
    fi
    exit "$result"
}
trap restore_on_failure EXIT
python3 - "$state" <<'PY'
import os, re, sys, tempfile
from pathlib import Path
path=Path('/etc/ynu-shooting/backend.env')
original=path.read_text()
# Credentials stay in their existing protected file; neither read their values into logs nor print the file.
for key in ['WECHAT_APP_ID','WECHAT_APP_SECRET']:
    if not re.search(r'^'+key+r'\s*=\s*\S+',original,re.M):
        raise SystemExit('Existing WeChat login credentials are missing; configuration not changed.')
fields='{"thing4":"title","date3":"start","time16":"end","thing25":"location"}'
values={
    'WECHAT_REMINDERS_ENABLED':'true',
    'WECHAT_REMINDERS_STATE':sys.argv[1],
    'WECHAT_REMINDERS_LOCATION':'楠院二栋C307',
    'WECHAT_TRAINING_TEMPLATE_ID':'tG676ieJiCyCYOqkxXA2wS2IkZX_ruvoK1atgECpLmE',
    'WECHAT_TRAINING_FIELDS':fields,
    'WECHAT_DUTY_TEMPLATE_ID':'tG676ieJiCyCYOqkxXA2wS2IkZX_ruvoK1atgECpLmE',
    'WECHAT_DUTY_FIELDS':fields,
}
lines=[line for line in original.splitlines() if line.partition('=')[0].strip() not in values]
lines += [key+"='"+value+"'" for key,value in values.items()]
fd,name=tempfile.mkstemp(prefix='.reminder-env-',dir=path.parent)
try:
    with os.fdopen(fd,'w') as f:
        f.write('\n'.join(lines)+'\n');f.flush();os.fsync(f.fileno())
    os.chmod(name,0o600);os.chown(name,0,0);os.replace(name,path)
finally:
    if os.path.exists(name):os.unlink(name)
PY
systemctl restart ynu-shooting
query_date=$(TZ=Asia/Shanghai date +%F)
for attempt in $(seq 1 30); do
    if systemctl is-active --quiet ynu-shooting && curl --noproxy '*' --fail --silent --max-time 3 \
        "http://127.0.0.1:8080/api/bookings/slots?date=$query_date" | grep -q '"code":0'; then
        echo "Reminders enabled: 15 minutes before training/duty; mini program state=$state."
        echo 'Each member must tap and accept the subscription for their own booking or shift.'
        echo 'If WeChat rejects token requests, add the server public outbound IP to the WeChat IP whitelist.'
        exit 0
    fi
    sleep 2
done
echo 'Backend health check failed after reminder configuration.' >&2
exit 1
