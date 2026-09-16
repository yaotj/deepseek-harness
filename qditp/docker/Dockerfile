FROM os-harbor-svc.default.svc.cloudos:443/itp/jdk:21

# 虚拟线程载体线程数加固。
#
# 背景：全部服务共用 resource/micro/web/src/main/resources/web.properties:6 的
# spring.threads.virtual.enabled=true，Tomcat 请求跑在虚拟线程上（日志里的
# tomcat-handler-N 即虚拟线程命名）。JDK 21 尚未落地 JEP 491，虚拟线程在
# synchronized 块内阻塞会 pin 住载体线程；Oracle JDBC(ojdbc8) 的
# PhysicalConnection / OracleStatement 大量方法是 synchronized，一条慢 SQL
# 就等于把一个载体线程 pin 住同样长的时间。
#
# 载体线程池默认 parallelism = 容器可见 CPU 数。K8s 里 CPU limit 偏小时
# （极端情况 1 核）只需一两条慢 SQL 就能 pin 满，导致全 JVM 虚拟线程停止调度——
# 包括本该唤醒 WebClient 响应的那个续体。已发生事故（2026-08-26 生产
# pay-sign-server）：gate-txn-pay 17ms 就答完的响应，本端 287 秒后才处理。
#
# 这里把 parallelism 与 maxPoolSize 显式抬高，与容器 CPU 数解耦，
# 把「必然全局停摆」降级为「局部变慢」。这是缓解，不是治本；
# 治本是不要在持锁/长事务里做阻塞调用。
#
# 需要现场取证 pinning 时，在 Deployment 上追加环境变量即可，
# NEVER 常态开启（pin 频繁时会刷爆日志）：
#   JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Djdk.tracePinnedThreads=full"
# 或用 JFR 事件 jdk.VirtualThreadPinned。
# 虚拟线程栈 MUST 用 jcmd <pid> Thread.dump_to_file -format=json 抓，jstack 看不到。
ENV JAVA_TOOL_OPTIONS="-Djdk.virtualThreadScheduler.parallelism=8 -Djdk.virtualThreadScheduler.maxPoolSize=256"

COPY app.jar /app.jar
