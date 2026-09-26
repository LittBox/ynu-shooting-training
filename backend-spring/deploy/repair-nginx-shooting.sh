#!/usr/bin/env bash
# Run with sudo on the Ubuntu server after confirming the current site only returns 444.
set -eu

site=$(readlink -f /etc/nginx/sites-enabled/patern-library)
test -f "$site"
# Refuse to overwrite a site that has changed since the diagnosis.
current=$(sed 's/#.*//' "$site" | tr -d '[:space:]')
if [ "$current" != 'server{listen80;server_name_;return444;}' ]; then
    printf '%s\n' 'Site differs from the inspected configuration; no changes made.' >&2
    exit 1
fi

install -d -m 700 /var/backups/ynu-shooting
backup=$(mktemp /var/backups/ynu-shooting/nginx-before-fix.XXXXXX)
cp -p "$site" "$backup"
printf 'Backup: %s\n' "$backup"

cat > "$site" <<'NGINX'
server {
    listen 80;
    server_name _;

    location ^~ /ynu-shooting/ {
        proxy_pass http://127.0.0.1:8080/;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        return 444;
    }
}
NGINX

if nginx -t && systemctl reload nginx; then
    printf '%s\n' 'Nginx reloaded. Verify the /ynu-shooting/ endpoint next.'
else
    cp -p "$backup" "$site"
    printf '%s\n' 'Repair failed; original configuration restored.' >&2
    nginx -t && systemctl reload nginx
    exit 1
fi
