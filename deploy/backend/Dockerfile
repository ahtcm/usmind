# ============================================================
# USMIND 后端镜像（多阶段构建）
# 阶段1：Maven 构建可执行 jar（使用阿里云镜像加速国内拉取）
# 阶段2：JRE 17 运行（不包含构建工具，镜像更小）
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS build
# 国内镜像加速（可选：若可直连 Maven Central 可删除 settings.xml）
COPY settings.xml /root/.m2/settings.xml
WORKDIR /build
COPY pom.xml .
RUN mvn -q dependency:go-offline || true
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai
COPY --from=build /build/target/usmind-backend.jar app.jar
EXPOSE 8899
ENTRYPOINT ["java", "-jar", "app.jar"]
