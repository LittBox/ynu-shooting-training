#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

# Maven and the interactive shell may otherwise select different JDKs on macOS.
if [[ -x /usr/libexec/java_home ]]; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
elif [[ -z "${JAVA_HOME:-}" ]]; then
  echo '请先设置 JAVA_HOME，指向 JDK 21。' >&2
  exit 1
fi
export PATH="$JAVA_HOME/bin:$PATH"

export PORT="${PORT:-8080}"
if command -v lsof >/dev/null 2>&1 && lsof -nP "-iTCP:${PORT}" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "端口 ${PORT} 已被占用，后端可能已经启动，无需重复运行。" >&2
  echo '如需重启，请先在原运行终端按 Ctrl+C，再执行本脚本。' >&2
  exit 1
fi
export AUTH_MODE=mock
export DB_DDL_AUTO=validate
if [[ -z "${JWT_SECRET:-}" ]]; then
  export JWT_SECRET="$(openssl rand -hex 32)"
  echo '已生成临时本地登录密钥；重启后请重新登录。'
fi

echo "启动本地后端：http://127.0.0.1:${PORT}（PostgreSQL / ynu-shooting，开发模拟登录）"
exec mvn spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments=--server.address=127.0.0.1
