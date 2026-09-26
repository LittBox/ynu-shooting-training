#!/usr/bin/env bash
# 只读检查：在目标 Linux 服务器执行，不安装软件、不读取应用密钥、不修改配置。
set -u

section() { printf '\n--- %s ---\n' "$1"; }

section '操作系统 / 架构 / 当前用户'
if [[ -r /etc/os-release ]]; then cat /etc/os-release; fi
uname -m
id

section '内存 / 磁盘'
if command -v free >/dev/null 2>&1; then free -h; fi
df -h /

section 'Java（项目需要 Java 21）'
if command -v java >/dev/null 2>&1; then
  java -version 2>&1
else
  printf 'PATH 中未发现 Java；仍需检查宝塔 Java 管理器。\n'
fi

section 'PostgreSQL 客户端 / 服务端版本'
for binary in psql postgres; do
  if command -v "$binary" >/dev/null 2>&1; then
    "$binary" --version
  else
    printf 'PATH 中未发现 %s（不代表服务器一定未安装）。\n' "$binary"
  fi
done

section 'Nginx'
if command -v nginx >/dev/null 2>&1; then
  nginx -v 2>&1
elif [[ -x /www/server/nginx/sbin/nginx ]]; then
  /www/server/nginx/sbin/nginx -v 2>&1
else
  printf '常见路径未发现 Nginx。\n'
fi

section '宝塔目录'
for directory in /www/server/panel /www/server/nginx /www/server/pgsql /www/server/java; do
  if [[ -d "$directory" ]]; then printf '存在 %s\n' "$directory"; fi
done

section '监听中的 TCP 端口（不显示进程参数）'
if command -v ss >/dev/null 2>&1; then
  ss -lnt
else
  printf '未找到 ss，可在宝塔面板查看端口和服务。\n'
fi
