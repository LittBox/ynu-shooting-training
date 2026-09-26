#!/usr/bin/env bash
# 由 systemd 传入环境变量；仅检查，不输出密钥。
set -euo pipefail

for name in DB_URL DB_USER DB_PASSWORD WECHAT_APP_ID WECHAT_APP_SECRET JWT_SECRET; do
  if [[ -z "${!name:-}" ]]; then
    printf '缺少配置 %s，请在 /etc/ynu-shooting/backend.env 中填写。\n' "$name" >&2
    exit 1
  fi
done
if [[ "$WECHAT_APP_ID" != 'wxdeef956f530d27c6' ]]; then
  printf 'WECHAT_APP_ID 与当前小程序正式 AppID 不一致。\n' >&2
  exit 1
fi
if [[ ${#JWT_SECRET} -lt 32 ]]; then
  printf 'JWT_SECRET 至少需要 32 个字符，建议填写 openssl rand -hex 32 的结果。\n' >&2
  exit 1
fi
case "$DB_URL" in
  jdbc:postgresql://127.0.0.1:5432/ynu_shooting\?*) ;;
  *) printf '此部署包预期使用本机 ynu_shooting 数据库，请核对 DB_URL。\n' >&2; exit 1 ;;
esac
