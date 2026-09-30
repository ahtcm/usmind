# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\deploy\nginx\nginx.conf'
conf = '''# ============================================================
# USMIND Nginx 容器内配置
#   - 80   统一入口：/       用户端静态；/admin/ 管理后台静态
#               /api/ /ws /uploads/ 反代 backend:8899
#   - 8866 兼容直连：用户端（原端口）
#   - 8867 兼容直连：管理后台（原端口）
# 上传目录 /data/uploads 由 backend 卷共享（只读挂载）
# 注意：正则 location 只能出现在 server 级（不可嵌套），
#       静态缓存规则统一提级到 server 级（正则优先于前缀）。
# ============================================================
server_tokens off;
client_max_body_size 500m;

map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}

gzip on;
gzip_types text/css application/javascript application/json image/svg+xml;
gzip_min_length 1k;

# ---------- 统一入口 80 ----------
server {
    listen 80;
    server_name _;

    # 精确匹配优先：SPA 入口不做长缓存（防更新后取旧资源）
    location = /admin { return 301 /admin/; }
    location = /admin/index.html {
        add_header Cache-Control "no-cache, no-store, must-revalidate";
    }
    location = /index.html {
        add_header Cache-Control "no-cache, no-store, must-revalidate";
    }

    # 静态资源长缓存（正则优先于前缀 location，对所有站点生效）
    location ~* \\.(js|css|png|jpe?g|gif|svg|webp|woff2?|ico)$ {
        expires 30d;
        add_header Cache-Control "public, max-age=2592000, immutable";
    }

    # 管理后台（静态，base=/admin/）
    location /admin/ {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /admin/index.html;
    }

    # 后端 API
    location /api/ {
        proxy_pass http://backend:8899;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 300s;
    }

    # WebSocket 实时协作
    location /ws {
        proxy_pass http://backend:8899;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_read_timeout 3600s;
    }

    # 上传文件（共享卷直出，走 Nginx 高效静态）
    location /uploads/ {
        alias /data/uploads/;
        access_log off;
        expires 30d;
        add_header Cache-Control "public, max-age=2592000, immutable";
    }

    # 用户端静态（SPA）
    location / {
        root /usr/share/nginx/html/user;
        index index.html;
        try_files $uri $uri/ /index.html;
    }
}

# ---------- 兼容直连 8866（用户端） ----------
server {
    listen 8866;
    server_name _;

    location = /index.html {
        add_header Cache-Control "no-cache, no-store, must-revalidate";
    }
    location ~* \\.(js|css|png|jpe?g|gif|svg|webp|woff2?|ico)$ {
        expires 30d;
        add_header Cache-Control "public, max-age=2592000, immutable";
    }
    location /api/ {
        proxy_pass http://backend:8899;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_read_timeout 300s;
    }
    location /ws {
        proxy_pass http://backend:8899;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_read_timeout 3600s;
    }
    location /uploads/ {
        alias /data/uploads/;
        access_log off;
        expires 30d;
    }
    location / {
        root /usr/share/nginx/html/user;
        index index.html;
        try_files $uri $uri/ /index.html;
    }
}

# ---------- 兼容直连 8867（管理后台） ----------
server {
    listen 8867;
    server_name _;

    location = / { return 301 /admin/; }
    location = /admin/index.html {
        add_header Cache-Control "no-cache, no-store, must-revalidate";
    }
    location ~* \\.(js|css|png|jpe?g|gif|svg|webp|woff2?|ico)$ {
        expires 30d;
        add_header Cache-Control "public, max-age=2592000, immutable";
    }
    location /api/ {
        proxy_pass http://backend:8899;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_read_timeout 300s;
    }
    location /uploads/ {
        alias /data/uploads/;
        access_log off;
        expires 30d;
    }
    location /admin/ {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /admin/index.html;
    }
    location / {
        root /usr/share/nginx/html/admin;
        index index.html;
        try_files $uri $uri/ /admin/index.html;
    }
}
'''
io.open(p, 'w', encoding='utf-8', newline='').write(conf)
print('NGINX CONF REWRITTEN')
