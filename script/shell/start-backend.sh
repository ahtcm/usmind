#!/usr/bin/env bash
# ============================================================
#  USMIND 启动后端（Linux）：Maven 构建 + 启动 Spring Boot (8899)
#  作用同 script/bat/start-backend.bat
# ============================================================
set -e
SELF_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SELF_DIR/../.." && pwd)"
TOOLS="$ROOT/tools"

JAVA_BIN="$(ls -d "$TOOLS"/jdk-17.0.2/bin/java 2>/dev/null | head -1)"
[ -z "$JAVA_BIN" ] && JAVA_BIN="$(ls -d "$TOOLS"/jdk*/bin/java 2>/dev/null | head -1)"
MAVEN_CMD="$(ls -d "$TOOLS"/apache-maven-*/bin/mvn 2>/dev/null | head -1)"
PROJECT_DIR="$ROOT/code/backend"

if [ -z "$JAVA_BIN" ]; then echo "[错误] 未找到 tools/jdk-17.0.2（Linux 版 JDK17）"; exit 1; fi
if [ -z "$MAVEN_CMD" ]; then echo "[错误] 未找到 tools/apache-maven-3.9.6（Linux 版 Maven）"; exit 1; fi

export JAVA_HOME="$(dirname "$(dirname "$JAVA_BIN")")"
export PATH="$JAVA_HOME/bin:$PATH"

cd "$PROJECT_DIR"
echo "[Build] Maven packaging..."
"$MAVEN_CMD" clean package -DskipTests -q

if [ $? -ne 0 ]; then
  echo "[Error] Build failed"
  exit 1
fi

echo "[Start] Backend on port 8899..."
exec "$JAVA_BIN" -jar "target/usmind-backend.jar" \
     --spring.profiles.active=dev \
     --file.upload-dir="$ROOT/uploads"
