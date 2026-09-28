#!/usr/bin/env bash
# ============================================================
#  USMIND 构建后端 jar（Linux）
#  作用同 script/bat/build-backend.bat
#  产物: code/backend/target/usmind-backend.jar
# ============================================================
set -e
SELF_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SELF_DIR/../.." && pwd)"
TOOLS="$ROOT/tools"

JAVA_BIN="$(ls -d "$TOOLS"/jdk-17.0.2/bin/java 2>/dev/null | head -1)"
[ -z "$JAVA_BIN" ] && JAVA_BIN="$(ls -d "$TOOLS"/jdk*/bin/java 2>/dev/null | head -1)"
MAVEN_CMD="$(ls -d "$TOOLS"/apache-maven-*/bin/mvn 2>/dev/null | head -1)"

if [ -z "$JAVA_BIN" ]; then echo "[错误] 未找到 tools/jdk-17.0.2（Linux 版 JDK17）"; exit 1; fi
if [ -z "$MAVEN_CMD" ]; then echo "[错误] 未找到 tools/apache-maven-3.9.6（Linux 版 Maven）"; exit 1; fi

export JAVA_HOME="$(dirname "$(dirname "$JAVA_BIN")")"
export PATH="$JAVA_HOME/bin:$PATH"

echo "[Env] JAVA_HOME=$JAVA_HOME"
echo "[Env] Maven=$MAVEN_CMD"
echo "[Build] mvn clean package -DskipTests ..."

cd "$ROOT/code/backend"
"$MAVEN_CMD" clean package -DskipTests

if [ $? -ne 0 ]; then
  echo "[Error] Build failed"
  echo "[Hint] 若 jar 被占用，先执行 stop-all.sh 停止后端再重试"
  exit 1
fi

echo "[Done] jar: $ROOT/code/backend/target/usmind-backend.jar"
