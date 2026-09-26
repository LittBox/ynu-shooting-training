#!/usr/bin/env bash
# Mixed-mode schema cannot be used by the old single-mode backend.
set -euo pipefail
if [ "$(id -u)" -ne 0 ]; then echo 'Run with sudo.' >&2; exit 1; fi
bundle_dir=$(cd -- "$(dirname -- "$0")" && pwd)
cd "$bundle_dir"
sha256sum -c SHA256SUMS
test -f /opt/ynu-shooting/backend.jar
test -f /etc/ynu-shooting/backend.env
test -f /etc/ynu-shooting/application-server.yml
install -d -m 700 /var/backups/ynu-shooting
backup_dir=$(mktemp -d /var/backups/ynu-shooting/mixed-training.XXXXXX)
cp -p /opt/ynu-shooting/backend.jar "$backup_dir/backend.jar"
# Stop writes so the database backup and migration represent one consistent upgrade boundary.
schema_changed=0
stop_attempted=0
recover_on_failure() {
    result=$?
    trap - EXIT
    if [ "$result" -ne 0 ] && [ "$stop_attempted" -eq 1 ]; then
        if [ "$schema_changed" -eq 0 ]; then
            systemctl start ynu-shooting || echo 'Old backend restart failed; inspect systemctl status.' >&2
        else
            systemctl stop ynu-shooting || true
            echo 'Upgrade failed after mixed-mode migration. Backend stopped; old JAR is incompatible with this schema.' >&2
            echo "JAR and database backups: $backup_dir. Keep them and inspect journalctl -u ynu-shooting before recovery." >&2
            echo 'Do not run an older upgrade script or restore the database over new scores.' >&2
        fi
    fi
    exit "$result"
}
trap recover_on_failure EXIT
stop_attempted=1
systemctl stop ynu-shooting
sudo -u postgres pg_dump -Fc -d ynu_shooting > "$backup_dir/database.dump"
chmod 600 "$backup_dir/database.dump"
test -s "$backup_dir/database.dump"
echo "JAR and database backup saved to: $backup_dir"
# Previous additive migrations are idempotent, allowing this package to include earlier fixes.
sudo -u postgres psql -X -d ynu_shooting -v ON_ERROR_STOP=1 < migrate-score-attempts.sql
sudo -u postgres psql -X -d ynu_shooting -v ON_ERROR_STOP=1 < migrate-training-resume.sql
# Conservative on connection loss: the transaction may have committed, so never restart old code blindly.
schema_changed=1
sudo -u postgres psql -X -d ynu_shooting -v ON_ERROR_STOP=1 < migrate-mixed-training-modes.sql
install -o root -g root -m 644 backend.jar /opt/ynu-shooting/backend.jar
systemctl start ynu-shooting
query_date=$(TZ=Asia/Shanghai date +%F)
for attempt in $(seq 1 30); do
    if systemctl is-active --quiet ynu-shooting &&
        curl --noproxy '*' --fail --silent --show-error --max-time 3 \
            "http://127.0.0.1:8080/api/bookings/slots?date=$query_date" 2>/dev/null | grep -q '"code":0'; then
        echo 'Upgrade successful: mixed-mode backend API returned code 0.'
        echo 'Next: compile and preview the updated mini program; test qualification and final rounds in the same session.'
        exit 0
    fi
    sleep 2
done
echo 'Backend health check failed.' >&2
exit 1
