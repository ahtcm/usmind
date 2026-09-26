# USMIND 部署与运维文档

## 架构概览

```
┌─────────────────────────────────────────────────────┐
│                    Nginx (8866/8867)                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────┐ │
│  │ 用户端 8866  │  │ 管理后台 8867│  │ /api 代理│ │
│  │ release/     │  │ release/     │  │ → 8899   │ │
│  │   usmind/    │  │   admin/     │  │          │ │
│  └──────────────┘  └──────────────┘  └────┬─────┘ │
└──────────────────────────────────────────────┼───────┘
                                                │
                    ┌───────────────────────────▼──────────┐
                    │   Spring Boot 后端 (8899)             │
                    │   Java 17 + MyBatis-Plus + JWT       │
                    └──────┬──────────────┬────────────────┘
                           │              │
                    ┌──────▼────┐  ┌─────▼──────┐
                    │  MySQL 8  │  │   Redis 5  │
                    │  (3306)   │  │   (6379)   │
                    └───────────┘  └────────────┘
```

## 目录结构

```
usmind/
├── code/
│   ├── backend/          # Spring Boot 后端
│   ├── web-user/         # Vue 3 用户端（思维导图）
│   ├── web-admin/        # Vue 3 管理后台
│   └── usmind/           # 旧 Python 版本（保留参考）
├── sql/
│   └── init.sql          # 数据库初始化脚本
├── release/
│   ├── usmind/           # 用户端构建产物（nginx托管）
│   └── admin/            # 管理后台构建产物（nginx托管）
├── uploads/              # 文件上传兜底存储目录
├── script/               # 启动/构建脚本
├── docs/                 # 文档
├── logs/                 # 日志
└── tools/                # 工具链（JDK/Maven/MySQL/Redis/Nginx/Node）
```

## 快速启动

### 方式一：一键启动（推荐）

```bat
cd C:\Users\25750\Desktop\usmind\script
start-all.bat
```

自动完成：启动 MySQL → Redis → Nginx → 后端 → 构建部署前端

### 方式二：分步启动

1. **启动 MySQL**
   ```bat
   script\start-mysql.bat
   ```

2. **启动 Redis**
   ```bat
   script\start-redis.bat
   ```

3. **初始化数据库**（仅首次）
   ```bat
   script\init-db.bat
   ```

4. **启动后端**
   ```bat
   script\start-backend.bat
   ```

5. **构建并部署前端**
   ```bat
   script\build-web-user.bat
   script\build-web-admin.bat
   ```

6. **启动 Nginx**
   ```bat
   script\start-nginx.bat
   ```

## 访问地址

| 服务 | 地址 | 说明 |
|------|------|------|
| 用户端 | http://127.0.0.1:8866 | 思维导图编辑器 |
| 管理后台 | http://127.0.0.1:8867 | 系统管理 |
| 后端API | http://127.0.0.1:8899/api | REST API |
| 分享链接 | http://127.0.0.1:8866/share/{token} | 免登录查看 |

## 默认账号

- **管理员**: admin / admin123
- 首次登录后请及时修改密码

## 开发模式

### 后端开发
```bat
cd code\backend
..\..\tools\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run -Dspring.profiles.active=dev
```

### 用户端开发（热更新）
```bat
script\start-web-user.bat
# 访问 http://127.0.0.1:5173
```

### 管理后台开发（热更新）
```bat
script\start-web-admin.bat
# 访问 http://127.0.0.1:5174
```

## OSS 配置说明

系统支持多种对象存储，可在管理后台 → OSS配置 中管理：

| 类型 | 说明 |
|------|------|
| local | 本地存储（默认兜底，存 uploads/ 目录） |
| aliyun | 阿里云 OSS |
| tencent | 腾讯云 COS |
| qiniu | 七牛云 |
| minio | MinIO 私有存储 |

**热插拔**：在管理后台点击「激活」按钮即可切换存储，无需重启服务。新上传的文件自动使用新存储，已有文件不受影响。

## 停止服务

```bat
script\stop-all.bat
```

## 常见问题

### Q: 后端启动失败，提示数据库连接失败
A: 确认 MySQL 已启动（`start-mysql.bat`），并已执行 `init-db.bat` 初始化数据库。

### Q: 前端页面空白
A: 检查浏览器控制台，确认 API 请求是否正常。开发模式下需要后端同时运行。

### Q: 文件上传失败
A: 检查 `uploads/` 目录是否存在且有写入权限。如配置了 OSS，确认密钥和 Bucket 配置正确。

### Q: 分享链接无法访问
A: 确认导图已开启分享，分享链接格式为 `http://127.0.0.1:8866/share/{token}`。
