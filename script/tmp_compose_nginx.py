# -*- coding: utf-8 -*-
import io
p = r'C:\Users\25750\Desktop\usmind\deploy\docker-compose.yml'
s = io.open(p, encoding='utf-8').read()
old = '''  nginx:
    build: ./nginx
    container_name: usmind-nginx
    restart: unless-stopped
    ports:
      - "80:80"        # 统一入口（用户端 / + 管理后台 /admin/）
      - "8866:8866"    # 兼容：用户端直连
      - "8867:8867"    # 兼容：管理后台直连
    volumes:
      - app-data:/data:ro   # 共享后端上传目录
    depends_on:
      - backend'''
new = '''  nginx:
    # 构建上下文为 code/（包含两个前端源码），Dockerfile 在 deploy/nginx/
    build:
      context: ../code
      dockerfile: ../deploy/nginx/Dockerfile
    container_name: usmind-nginx
    restart: unless-stopped
    ports:
      - "80:80"        # 统一入口（用户端 / + 管理后台 /admin/）
      - "8866:8866"    # 兼容：用户端直连
      - "8867:8867"    # 兼容：管理后台直连
    volumes:
      - app-data:/data:ro                 # 共享后端上传目录
      - ../deploy/nginx/nginx.conf:/etc/nginx/conf.d/default.conf:ro
    depends_on:
      - backend'''
assert s.count(old) == 1, 'cnt=' + str(s.count(old))
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('COMPOSE OK')
