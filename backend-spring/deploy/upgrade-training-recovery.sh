#!/usr/bin/env bash
set -euo pipefail
if [ "$(id -u)" -ne 0 ]; then echo 'Run with sudo.' >&2; exit 1; fi
bundle_dir=$(cd -- "$(dirname -- "$0")" && pwd)
cd "$bundle_dir"
sha256sum -c SHA256SUMS
install -d -m 700 /var/backups/ynu-shooting
backup_dir=$(mktemp -d /var/backups/ynu-shooting/training-recovery.XXXXXX)
sudo -u postgres pg_dump -Fc -d ynu_shooting > "$backup_dir/database.dump"
chmod 600 "$backup_dir/database.dump"
test -s "$backup_dir/database.dump"
echo "Database backup saved to: $backup_dir/database.dump"
# Add the new empty table before restarting; older code ignores this table.
sudo -u postgres psql -X -d ynu_shooting -v ON_ERROR_STOP=1 < migrate-score-attempts.sql
sudo -u postgres psql -X -d ynu_shooting -v ON_ERROR_STOP=1 < migrate-training-resume.sql
bash upgrade-backend.sh
