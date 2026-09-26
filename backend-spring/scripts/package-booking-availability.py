#!/usr/bin/env python3
"""Build the booking-availability fix bundle from the verified local JAR."""
import hashlib
from pathlib import Path
import shutil
import tarfile
import tempfile

backend = Path(__file__).resolve().parents[1]
root = backend.parent
name = 'ynu-booking-availability-20260926'
package = root / 'dist' / f'{name}.tar.gz'
sources = {
    'backend.jar': backend / 'target/shotting-booking-1.0.0.jar',
    'upgrade-booking-availability.sh': backend / 'deploy/upgrade-booking-availability.sh',
    'migrate-wechat-reminders.sql': backend / 'scripts/migrate-wechat-reminders.sql',
    'README.md': root / 'docs/设备使用中仍可预约后续时段-2026-09-26.md',
}
with tempfile.TemporaryDirectory(prefix='ynu-booking-package-') as tmp:
    stage = Path(tmp) / name
    stage.mkdir()
    for target, source in sources.items():
        shutil.copyfile(source, stage / target)
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
