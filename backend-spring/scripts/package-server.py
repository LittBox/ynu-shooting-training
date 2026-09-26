#!/usr/bin/env python3
"""Package an already verified Maven JAR using an explicit file allowlist."""
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
import tarfile
import tempfile
import zipfile


def main():
    backend = Path(__file__).resolve().parents[1]
    root = backend.parent
    jar = backend / "target/shotting-booking-1.0.0.jar"
    if not jar.is_file():
        raise SystemExit("先在 backend-spring 执行 mvn verify，再生成上传包。")
    with zipfile.ZipFile(jar) as archive:
        manifest = archive.read("META-INF/MANIFEST.MF").decode()
        if "Main-Class: org.springframework.boot.loader.launch.JarLauncher" not in manifest:
            raise SystemExit("JAR 不是可执行的 Spring Boot 包。")

    sources = {
        "backend.jar": jar,
        "README.md": backend / "deploy/README.md",
        "sql/schema-postgresql.sql": backend / "scripts/schema-postgresql.sql",
        "config/application-server.yml": backend / "deploy/application-server.yml",
        "config/backend.env.example": backend / "deploy/backend.env.example",
        "systemd/ynu-shooting.service": backend / "deploy/ynu-shooting.service",
        "scripts/check-config.sh": backend / "deploy/check-config.sh",
    }
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    name = f"ynu-server-{stamp}"
    output = root / "dist"
    output.mkdir(exist_ok=True)
    package = output / f"{name}.tar.gz"
    if package.exists():
        raise SystemExit(f"文件已存在：{package}")
    with tempfile.TemporaryDirectory(prefix="ynu-server-package-") as temporary:
        stage = Path(temporary) / name
        stage.mkdir()
        for relative, source in sources.items():
            target = stage / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
            target.chmod(0o755 if relative.endswith(".sh") else 0o644)
        info = {
            "built_at_utc": datetime.now(timezone.utc).isoformat(),
            "java_required": 21,
            "database": "ynu_shooting",
            "schema": "ynu-shooting",
            "wechat_app_id": "wxdeef956f530d27c6",
            "jar_sha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
            "note": "Local packaging only; server deployment and WeChat login not verified.",
        }
        (stage / "BUILD-INFO.json").write_text(json.dumps(info, ensure_ascii=False, indent=2) + "\n")
        files = sorted(path for path in stage.rglob("*") if path.is_file())
        sums = [f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.relative_to(stage).as_posix()}\n" for path in files]
        (stage / "SHA256SUMS").write_text("".join(sums))

        def clean_metadata(member):
            member.uid = member.gid = 0
            member.uname = member.gname = "root"
            member.mode = 0o755 if member.isdir() or member.name.endswith(".sh") else 0o644
            return member

        with tarfile.open(package, "w:gz", format=tarfile.USTAR_FORMAT) as archive:
            archive.add(stage, arcname=name, filter=clean_metadata)
    digest = hashlib.sha256(package.read_bytes()).hexdigest()
    package.with_name(package.name + ".sha256").write_text(f"{digest}  {package.name}\n")
    print(package)
    print(f"{package.stat().st_size:,} bytes; SHA-256 {digest}")


if __name__ == "__main__":
    main()
