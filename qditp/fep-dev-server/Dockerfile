FROM os-harbor-svc.default.svc.cloudos:443/itpb/jdk21:1.0.0

# 红岛生产基础镜像：itpb 项目公共 JDK 21 镜像（与 route-sentinel 同一基座，
# 2026-10-26 实测 itpb ns 内拉取成功，java version 21.0.1，完整 JDK）。
#
# 【COPY 文件名约定】
#   云管台镜像构建（itp-ops.sh buildx）上传的构建上下文中 jar 固定命名为 app.jar，
#   故此处 MUST 写 COPY app.jar，不能写 target/fep-dev-server-<版本>.jar —— 平台构建
#   上下文中没有 target/ 目录，会 COPY 失败。

ENV JAVA_TOOL_OPTIONS="-Djdk.virtualThreadScheduler.parallelism=8 -Djdk.virtualThreadScheduler.maxPoolSize=256"

COPY app.jar /app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
