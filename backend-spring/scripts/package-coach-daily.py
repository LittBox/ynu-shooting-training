#!/usr/bin/env python3
"""Build the coach daily results bundle from the verified local JAR."""
import hashlib
import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path
import shutil
import tarfile
import tempfile

backend = Path(__file__).resolve().parents[1]
root = backend.parent
name = 'ynu-coach-history-20260927'
package = root / 'dist' / f'{name}.tar.gz'
package.parent.mkdir(parents=True, exist_ok=True)
sources = {
    'backend.jar': backend / 'target/shotting-booking-1.0.0.jar',
    'upgrade-coach-daily.sh': backend / 'deploy/upgrade-coach-daily.sh',
    'migrate-wechat-reminders.sql': backend / 'scripts/migrate-wechat-reminders.sql',
    'migrate-historical-training.sql': backend / 'scripts/migrate-historical-training.sql',
    'README.md': root / 'docs/教员历史训练补录.md',
}
with tempfile.TemporaryDirectory(prefix='ynu-coach-daily-package-') as tmp:
    stage = Path(tmp) / name
    stage.mkdir()
    for target, source in sources.items():
        shutil.copyfile(source, stage / target)
    revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    dirty = bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=root, text=True).strip())
    build_info = {
        'source_commit': revision,
        'source_dirty': dirty,
        'packaged_at_utc': datetime.now(timezone.utc).isoformat(),
        'jar_sha256': hashlib.sha256((stage / 'backend.jar').read_bytes()).hexdigest(),
        'java_required': 21,
        'migrations': ['migrate-wechat-reminders.sql', 'migrate-historical-training.sql'],
        'note': 'Local package only; server deployment and WeChat upload are separate steps.',
    }
    (stage / 'BUILD-INFO.json').write_text(json.dumps(build_info, ensure_ascii=False, indent=2) + '\n')
    sums = ''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in sorted(stage.iterdir()))
    (stage / 'SHA256SUMS').write_text(sums)
    def metadata(info):
        info.uid = info.gid = 0
        info.uname = info.gname = 'root'
        info.mode = 0o755 if info.isdir() or info.name.endswith('.sh') else 0o644
        return info
    with tarfile.open(package, 'w:gz', format=tarfile.USTAR_FORMAT) as archive:
        archive.add(stage, arcname=name, filter=metadata)
digest = hashlib.sha256(package.read_bytes()).hexdigest()
package.with_name(package.name + '.sha256').write_text(f'{digest}  {package.name}\n')
print(f'{package}\nSHA-256 {digest}')
