#!/usr/bin/env bash
# Run with sudo from an extracted upgrade bundle containing SHA256SUMS and backend.jar.
set -euo pipefail
if [ "$(id -u)" -ne 0 ]; then
    echo 'Please run: sudo bash upgrade-coach-daily.sh' >&2
    exit 1
fi
bundle_dir=$(cd -- "$(dirname -- "$0")" && pwd)
cd "$bundle_dir"
sha256sum -c SHA256SUMS
test -f /opt/ynu-shooting/backend.jar
test -f /etc/ynu-shooting/backend.env
test -f /etc/ynu-shooting/application-server.yml

# Existing mixed-mode schema is required; the reminder migration is additive and safe to reapply.
mixed_ready=$(sudo -u postgres psql -X -A -t -d ynu_shooting -v ON_ERROR_STOP=1 <<'SQL'
SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='ynu-shooting' AND table_name='score_attempts' AND column_name='mode')
AND EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='"ynu-shooting".scores'::regclass AND conname='uk_score_session_mode');
SQL
)
if [ "$mixed_ready" != 't' ]; then echo 'Install the mixed-training release first. No files changed.' >&2; exit 1; fi
install -d -m 700 /var/backups/ynu-shooting
backup_dir=$(mktemp -d /var/backups/ynu-shooting/coach-daily.XXXXXX)
cp -p /opt/ynu-shooting/backend.jar "$backup_dir/backend.jar"
echo "Previous JAR saved to: $backup_dir/backend.jar"

restore_on_failure() {
    result=$?
    trap - EXIT
    if [ "$result" -ne 0 ]; then
        echo 'Upgrade failed. Restoring previous JAR.' >&2
        systemctl stop ynu-shooting || true
        if cp -p "$backup_dir/backend.jar" /opt/ynu-shooting/backend.jar; then
            systemctl start ynu-shooting || echo 'Old JAR restored, but service start failed; inspect systemctl status.' >&2
        else
            echo "RESTORE FAILED. Backup remains at $backup_dir/backend.jar" >&2
        fi
    fi
    exit "$result"
}
trap restore_on_failure EXIT
systemctl stop ynu-shooting
sudo -u postgres pg_dump -Fc -d ynu_shooting > "$backup_dir/database.dump"
chmod 600 "$backup_dir/database.dump"
test -s "$backup_dir/database.dump"
echo "Database backup saved to: $backup_dir/database.dump"
sudo -u postgres psql -X -d ynu_shooting -v ON_ERROR_STOP=1 < migrate-wechat-reminders.sql
install -o root -g root -m 644 backend.jar /opt/ynu-shooting/backend.jar
systemctl start ynu-shooting

query_date=$(TZ=Asia/Shanghai date +%F)
for attempt in $(seq 1 30); do
    if systemctl is-active --quiet ynu-shooting &&
        curl --noproxy '*' --fail --silent --show-error --max-time 3 \
            "http://127.0.0.1:8080/api/bookings/slots?date=$query_date" 2>/dev/null |
            grep -q '"code":0'; then
        echo 'Upgrade successful: backend API returned code 0.'
        echo 'Next: update the mini program and review date cards, daily rankings and session details. Reminder configuration is unchanged.'
        exit 0
    fi
    sleep 2
done
echo 'Backend health check failed.' >&2
exit 1
