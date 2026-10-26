FROM os-harbor-svc.default.svc.cloudos:443/itpb/jdk21:1.0.0

# 红岛生产基础镜像：itpb 项目公共 JDK 21 镜像（2026-10-26 实测 itpb ns 内拉取成功，
# java version 21.0.1，完整 JDK 含 jar 工具，PATH=/home/javaapp/soft/jdk-21.0.1/bin）。
#
# 【为什么用 JDK 21 而不是 JDK 25】
#   route-sentinel 原为 Java 25（字节码 major 69），而 qditp 主体基线是 Java 21，
#   红岛 itpb 只有 jdk21:1.0.0 这一个公共基础镜像（Harbor 25 个仓库里唯一的 JDK 镜像）。
#   降级已实测：pom 改 java.version=21 → 编译通过 → 22 个测试全过 → 字节码 major 65；
#   依赖侧无需改动（spring-boot 4.0.7 / spring-core 7.0.8 / gateway-webmvc 5.0.2
#   全部是 Java 17 字节码 major 61，Java 21 可正常加载）。
#
# 【COPY 文件名约定】
#   云管台镜像构建（itp-ops.sh cloudos image build）上传 jar 时 filename 固定为 app.jar，
#   故此处 MUST 写 COPY app.jar，不能写 target/route-sentinel-<版本>.jar —— 平台构建
#   上下文中没有 target/ 目录，会 COPY 失败。
#
# 虚拟线程载体线程数加固：与 qditp 仓库基线 docker/Dockerfile 保持一致
# （route-sentinel 未开启 spring.threads.virtual.enabled，此处为预留，保留无害）。
ENV JAVA_TOOL_OPTIONS="-Djdk.virtualThreadScheduler.parallelism=8 -Djdk.virtualThreadScheduler.maxPoolSize=256"

COPY app.jar /app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
