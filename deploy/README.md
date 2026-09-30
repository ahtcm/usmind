# ============================================================
# USMIND Docker 一键部署
# 环境：任意支持 Docker 的 Linux 服务器（云服务器 / 内网机）
# 组成：MySQL 8 + Redis 7 + Spring Boot 后端 + Nginx（前端+反代）
# ============================================================

## 一、前置要求
- 服务器已安装 Docker 与 docker compose 插件（`docker compose version` 可验证）
- 服务器可访问外网（拉取基础镜像：mysql/redis/node/maven/nginx）
- 建议 2 核 4G 以上

## 二、部署步骤
```bash
# 1. 将项目上传到服务器（git clone 或 scp），进入 deploy 目录
cd usmind/deploy

# 2. 配置环境变量（必改：数据库密码 / JWT 密钥）
cp .env.example .env
#   vim .env 修改 USMIND_DB_PASSWORD、USMIND_JWT_SECRET、USMIND_OSS_CIPHER_KEY

# 3. 一键构建并启动（首次构建约 5-10 分钟，拉依赖+编译）
docker compose up -d --build

# 4. 查看状态
docker compose ps            # 4 个服务均应为 Up / healthy
docker compose logs -f backend   # 跟踪后端日志
```

## 三、访问入口
| 入口 | 地址 | 说明 |
|------|------|------|
| 用户端 | http://服务器IP/ | 脑图编辑、分享 |
| 管理后台 | http://服务器IP/admin/ | 系统管理（默认 admin/admin123，首次登录请修改） |
| 兼容端口 | http://服务器IP:8866 / :8867 | 与原本地端口一致 |

## 四、数据持久化与备份
- MySQL 数据卷 `mysql-data`、Redis `redis-data`、上传文件与日志 `app-data`，**容器删除不丢数据**
- 定时备份数据库（宿主机 crontab，每天 03:00）：
```bash
0 3 * * * bash /path/to/usmind/script/shell/backup-db.sh >> /path/to/usmind/logs/backup.log 2>&1
```
- 手动备份：`docker exec usmind-mysql mysqldump -uroot -p密码 --databases usmind_db > backup.sql`

## 五、探活与自愈
- compose 内置 healthcheck（MySQL/Redis/后端 TCP、Nginx HTTP），服务异常自动重启（restart: unless-stopped）
- 可选宿主机守护：`nohup bash script/shell/start-monitor.sh > logs/monitor.out 2>&1 &`
  （该脚本也适合非 Docker 的原生部署模式）

## 六、日常运维
```bash
docker compose ps            # 状态
docker compose logs -f nginx # 看 nginx 日志
docker compose restart backend
docker compose down          # 停止（保留数据卷）
docker compose down -v       # 停止并删除数据（慎用！）
```

## 七、升级版本
```bash
git pull                      # 拉最新代码
docker compose up -d --build  # 重建并滚动更新
```

## 八、常见问题
- **后端健康检查失败**：`docker compose logs backend` 查看；多半是数据库密码与 .env 不一致，或首次启动 40 秒内还没就绪（start_period 内不算失败）。
- **上传文件不生效**：确认 nginx 卷 `app-data:/data:ro` 与后端 `app-data:/data` 是同一卷（compose 已保证）。
- **80 端口被占**：修改 compose 中 `"80:80"` 左侧为其他端口（如 `"8080:80"`）。
- **国内拉镜像慢**：配置 Docker 镜像加速器（阿里云/腾讯云），或直接改用国内镜像源镜像。

## 九、非 Docker（原生）部署
与原 start-all 一致：`bash script/shell/start-all.sh` 或 Windows 下 `script\bat\start-all.bat`，
生产环境记得先 `export SPRING_PROFILES_ACTIVE=prod` 并配好 USMIND_* 环境变量。
