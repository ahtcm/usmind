# ============================================================
# USMIND Nginx 镜像（多阶段）
# 阶段1：Node 20 构建 web-user（用户端）
# 阶段2：Node 20 构建 web-admin（管理后台，base=/admin/）
# 阶段3：Nginx Alpine 提供静态托管
# 构建上下文 = ../code（docker-compose.yml 已指定），
# 无需提交 node_modules/target/dist（见 code/.dockerignore）
# nginx.conf 由 docker-compose 运行时挂载（改配置无需重建镜像）
# ============================================================
FROM node:20-alpine AS build-user
RUN npm config set registry https://registry.npmmirror.com
WORKDIR /build/user
COPY web-user/package.json web-user/package-lock.json* ./
RUN npm install --registry=https://registry.npmmirror.com
COPY web-user/ ./
RUN npm run build

FROM node:20-alpine AS build-admin
RUN npm config set registry https://registry.npmmirror.com
WORKDIR /build/admin
COPY web-admin/package.json web-admin/package-lock.json* ./
RUN npm install --registry=https://registry.npmmirror.com
COPY web-admin/ ./
RUN npm run build

FROM nginx:alpine
# 时区与日志软链（nginx 默认日志到 stdout 便于 docker logs）
RUN ln -sf /dev/stdout /var/log/nginx/access.log && ln -sf /dev/stderr /var/log/nginx/error.log
COPY --from=build-user /build/user/dist /usr/share/nginx/html/user
COPY --from=build-admin /build/admin/dist /usr/share/nginx/html/admin
EXPOSE 80 8866 8867
