# CloudOS「镜像构建」（itp-ops.sh buildx）统一构建用 Dockerfile —— 红岛/测试共用，FROM 按环境替换。
#
# 【为什么只有一份、NEVER 给每个模块复制一份】
#   上下文里 jar 恒为 app.jar、基础镜像恒为 itpb/jdk21:1.0.0，模块之间没有差异；
#   复制 21 份只会让「改一处忘改其余」成为常态。
#
# 【NEVER 写 ENTRYPOINT / CMD——这是本文件最要紧的一条】
#   基础镜像的默认入口是 `sh /startapp.sh`，它按容器 env 算 JVM 参数并落日志：
#       堆 = max_memory - reserved_memory - max_direct_memory（红岛现值 8000/2000/100 → -Xmx5900m）
#       + -XX:MaxDirectMemorySize、+HeapDumpOnOutOfMemoryError、
#       + -Xlog:gc*=trace:file=/home/javaapp/app/logs/${HOSTNAME}/gctrace.txt
#       + -Djava.io.tmpdir=/home/javaapp/app/logs
#   一旦在本文件写 ENTRYPOINT ["java","-jar","/app.jar"]，以上全部失效。2026-10-28 现查对照：
#     acc-es-server  PID1=`sh /startapp.sh`     → cmdline 带 -Xmx5900m 等一整套
#     fep-dev-server PID1=`java -jar /app.jar`  → jcmd VM.flags 实测 MaxHeapSize=2147483648（2 GiB）
#   实际丢的是：堆 5900m→2 GiB、MaxDirectMemorySize、OOM heapdump、GC 日志
#   （该 Pod 目录下没有 gctrace.txt，而 acc-es 目录下有 3 个）、java.io.tmpdir。
#   **不影响应用 `<app>.log`**：fep-dev.log 仍正常落盘（log4j2 自己按 ${HOSTNAME} 建目录），
#   pvc-logship 的 applogs.sh 照采——别把这两件事混为一谈（详见 IT-031 §2）。
#   契约细节与取值见 docs/tracking/IT-031-红岛镜像ENTRYPOINT绕过startapp致JVM日志契约失效.md
#   与 docs/tracking/IT-020-JVM参数被平台回滚.md。
#
# 【jar 路径 /app.jar】startapp.sh 里写死 `java -jar ... /app.jar`，与本文件 COPY 目标一致。

FROM os-harbor-svc.default.svc.cloudos:443/itpb/jdk21:1.0.0

# 虚拟线程载体线程数加固：与仓库基线 docker/Dockerfile 同源（全部服务共用
# resource/micro/web 的 spring.threads.virtual.enabled=true，ojdbc 持锁阻塞会 pin 载体线程）。
ENV JAVA_TOOL_OPTIONS="-Djdk.virtualThreadScheduler.parallelism=8 -Djdk.virtualThreadScheduler.maxPoolSize=256"

COPY app.jar /app.jar
