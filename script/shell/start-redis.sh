#!/usr/bin/env bash
# ============================================================
#  USMIND 启动 Redis（Linux）
#  作用同 script/bat/start-redis.bat
# ============================================================
SELF_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SELF_DIR/../.." && pwd)"
TOOLS="$ROOT/tools"
REDIS_DIR="$(ls -d "$TOOLS"/redis-*/ 2>/dev/null | head -1)"

if [ -z "$REDIS_DIR" ]; then
  echo "[错误] 未找到 tools/redis-* 目录（请放 Linux 版 Redis）"
  exit 1
fi

if pgrep -f "redis-server" >/dev/null 2>&1; then
  echo "[Info] Redis 已在运行"
  exit 0
fi

# 优先使用 redis.conf，其次 redis.windows.conf（若误放 Windows 配置）
CONF=""
for c in "$REDIS_DIR/redis.conf" "$REDIS_DIR/redis.windows.conf"; do
  [ -f "$c" ] && CONF="$c" && break
done

echo "[Start] Redis (6379)..."
nohup "$REDIS_DIR/bin/redis-server" ${CONF:+"$CONF"} --daemonize no \
      > "$REDIS_DIR/redis.log" 2>&1 &

for i in $(seq 1 10); do
  if (echo > /dev/tcp/127.0.0.1/6379) >/dev/null 2>&1; then
    echo "[Done] Redis started (port 6379)"
    exit 0
  fi
  sleep 1
done
echo "[警告] Redis 可能未完全就绪，请查看 $REDIS_DIR/redis.log"
