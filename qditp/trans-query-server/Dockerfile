# trans-query-server 模块内 Dockerfile —— 兼容「不传 --dockerfile」的 buildx 调用。
#
# 【真源在别处，本文件 MUST 与它同步】
#   全仓统一构建用的 Dockerfile 是 docs/ops/environments/scripts/buildx.Dockerfile，
#   `flow backend` 默认显式传 --dockerfile 指向它，不会用到本文件。
#   本文件只解决「单独执行 itp-ops.sh buildx trans-query-server <版本>」时，
#   buildx 默认到 <repo>/<module>/Dockerfile 取文件、找不到就报错的问题。
#   修改 FROM / ENV / COPY 时 MUST 两处同改，NEVER 只改一边。
#
# 【NEVER 写 ENTRYPOINT / CMD —— 最要紧的一条】
#   基础镜像默认入口是 `sh /startapp.sh`：它按容器 env 算 JVM 堆参数
#   （max_memory - reserved_memory - max_direct_memory）、落 GC 日志与 OOM heapdump。
#   一旦写 ENTRYPOINT ["java","-jar","/app.jar"]，这一整套全部失效——
#   fep-dev-server/Dockerfile 就是反面案例，详见
#   docs/tracking/IT-031-红岛镜像ENTRYPOINT绕过startapp致JVM日志契约失效.md。
#   本文件刻意不写 ENTRYPOINT，也不要从 fep-dev-server 复制。
#
# 【首行 FROM 会按环境替换】buildx 上传前按 --env 把 FROM 换成对应项目的基础镜像
#   （test=itp/jdk:25、hongdao=itpb/jdk21），所以本行写什么都会被覆盖，保持与真源同形即可。
# 【jar 路径 /app.jar】平台构建上下文里 jar 恒命名为 app.jar，与 startapp.sh 写死的一致。

FROM os-harbor-svc.default.svc.cloudos:443/itpb/jdk21:1.0.0

# 虚拟线程载体线程数加固（与统一真源同源，见 buildx.Dockerfile 注释）。
ENV JAVA_TOOL_OPTIONS="-Djdk.virtualThreadScheduler.parallelism=8 -Djdk.virtualThreadScheduler.maxPoolSize=256"

COPY app.jar /app.jar
