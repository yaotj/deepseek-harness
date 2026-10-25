# ⚠️ 2026-10-26：本快照是 jdk:25，但仓库基线 docker/Dockerfile 与 跨环境共性.md 都是 **jdk:21**，
#   runtime-observability-traps.md 也记「基线是 JDK 21」。远端已与基线漂移，**待裁断**，
#   NEVER 默默拿本文件当基线用。另：本快照缺 docker/Dockerfile 里的 JAVA_TOOL_OPTIONS
#   （虚拟线程 pin 缓解参数），且 COPY 写的是 *.jar（基线是 app.jar）。
FROM os-harbor-svc.default.svc.cloudos:443/itp/jdk:25

COPY *.jar /app.jar

