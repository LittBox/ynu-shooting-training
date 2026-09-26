#!/usr/bin/env bash
# Server operator grants coach duties to a confirmed existing account.
# A student number identifies the account; it is never a qualification rule.
set -euo pipefail
if [ "$(id -u)" -ne 0 ] || [ "$#" -ne 1 ] || [ -z "$1" ]; then
    echo 'Usage on the Ubuntu server: sudo bash grant-coach.sh STUDENT_NUMBER' >&2
    exit 1
fi
sudo -u postgres psql -X -d ynu_shooting -v ON_ERROR_STOP=1 -v student_no="$1" <<'SQL'
BEGIN;
SET LOCAL search_path TO "ynu-shooting";
CREATE TEMP TABLE coach_target ON COMMIT DROP AS
SELECT u.id FROM users u JOIN profiles p ON p.user_id = u.id
WHERE p.student_no = :'student_no';
DO $$
BEGIN
    IF (SELECT count(*) FROM coach_target) <> 1 THEN
        RAISE EXCEPTION 'Expected exactly one registered account; no role changed.';
    END IF;
END $$;
SELECT u.id AS account_id, p.real_name, u.role AS previous_role
FROM users u JOIN coach_target t ON t.id = u.id JOIN profiles p ON p.user_id = u.id
FOR UPDATE OF u;
WITH promoted AS (
    UPDATE users SET role = 'admin', updated_at = CURRENT_TIMESTAMP
    WHERE id IN (SELECT id FROM coach_target) AND role = 'student'
    RETURNING id
)
INSERT INTO audit_log (admin_user_id, action, target_user_id, detail, created_at)
SELECT NULL, 'GRANT_COACH', id, 'Confirmed by server operator; student -> admin', CURRENT_TIMESTAMP
FROM promoted;
SELECT u.id AS account_id, p.real_name, u.role AS current_role
FROM users u JOIN coach_target t ON t.id = u.id JOIN profiles p ON p.user_id = u.id;
COMMIT;
SQL
echo 'Coach authorization checked. Refresh account identity in the mini-program.'
