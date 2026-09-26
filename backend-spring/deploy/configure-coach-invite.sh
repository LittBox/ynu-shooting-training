#!/usr/bin/env bash
# Initialize a server-only invitation before running upgrade-backend.sh.
set -euo pipefail
if [ "$(id -u)" -ne 0 ]; then
    echo 'Run with sudo.' >&2
    exit 1
fi
python3 - <<'PY'
from pathlib import Path
import os, re, secrets, shutil, tempfile

path = Path('/etc/ynu-shooting/backend.env')
text = path.read_text()
matches = list(re.finditer(r'^[ \t]*COACH_INVITE_CODE[ \t]*=([^\r\n]*)$', text, re.MULTILINE))
if len(matches) > 1:
    raise SystemExit('Multiple COACH_INVITE_CODE entries; resolve duplicates first. No changes made.')
if matches and matches[0].group(1).strip().strip('\"\''):
    existing = matches[0].group(1).strip().strip('\"\'')
    if not 16 <= len(existing) <= 128:
        raise SystemExit('Existing COACH_INVITE_CODE must contain 16–128 characters. No changes made.')
    print('COACH_INVITE_CODE is already configured; existing invitation preserved.')
    raise SystemExit(0)
backups = Path('/var/backups/ynu-shooting')
backups.mkdir(mode=0o700, parents=True, exist_ok=True)
backup_dir = Path(tempfile.mkdtemp(prefix='invitation-config.', dir=backups))
shutil.copy2(path, backup_dir / 'backend.env')
code = secrets.token_hex(12)
entry = f'COACH_INVITE_CODE={code}'
updated = text[:matches[0].start()] + entry + text[matches[0].end():] if matches else text.rstrip('\n') + '\n' + entry + '\n'
fd, temporary = tempfile.mkstemp(prefix='.backend.env.', dir=path.parent)
try:
    with os.fdopen(fd, 'w') as out:
        out.write(updated)
    os.chmod(temporary, 0o600)
    os.replace(temporary, path)
finally:
    if os.path.exists(temporary):
        os.unlink(temporary)
print('Coach invitation created (save it and share only with confirmed coaches):')
print(code)
print('Now run upgrade-backend.sh to load the new setting.')
PY
