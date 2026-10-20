# face-pay-server 日志配置现状

> 调查时间：2026-10-14
> 来源：k8s-master 当前运行 Pod + 仓库源码 + jar 包

## 一、结论

`face-pay-server` 当前存在两类日志输出：

1. **本地文件日志**：写入 Pod 内 `emptyDir` 卷 `/home/javaapp/app/logs/<HOSTNAME>/`
2. **远程日志推送**：通过自定义 `VictoriaLogs` appender 推送到 `victoria-logs` StatefulSet（`http://victoria-logs-pbi6a-svc.itp.svc:30032`）

这就是用户提到的“日志写入另外的机器上”的配置。`victoria-logs` 是独立的 Pod/StatefulSet，运行在集群内其他节点上。

## 二、本地文件日志

### 2.1 启动脚本

容器启动命令来自 `/startapp.sh`：

```sh
HOSTNAME=$(hostname)
LOG_DIR=/home/javaapp/app/logs/${HOSTNAME}
mkdir -p ${LOG_DIR}
exec java -jar \
  -Djava.io.tmpdir=/home/javaapp/app/logs \
  -Xmx500m -Xms500m \
  -XX:MaxDirectMemorySize=100m \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=${LOG_DIR}/java.dump \
  -Xlog:gc*=trace:file=${LOG_DIR}/gctrace.txt:uptimemillis,pid:filecount=5,filesize=20m \
  /app.jar
```

### 2.2 Deployment 挂载

```yaml
volumeMounts:
  - mountPath: /home/javaapp/app/logs
    name: local-logs
volumes:
  - emptyDir: {}
    name: local-logs
```

### 2.3 日志路径

`CustomLoggingConfiguration` 在 `ApplicationReadyEvent` 时重载 log4j2 配置，设置：

```java
String moreLogPath = logPath + "/" + System.getenv().get("HOSTNAME");
System.setProperty("logPath", moreLogPath);
```

实际路径示例：

```
/home/javaapp/app/logs/face-pay-server-6b66d5df9f-dr7fc/
├── check-itpagm.log
├── gctrace.txt
├── gctrace.txt.0
├── gctrace.txt.1
├── gctrace.txt.2
└── itpagm.log
```

### 2.4 配置键

| 键 | 说明 |
|---|---|
| `other.logging.logPath` | 日志根目录，默认 `/home/javaapp/app/logs` |
| `other.logging.logBakDays` | 日志保留天数 |
| `other.logging.appName` | 应用名，face-pay 为 `itpagm` |
| `HOSTNAME` | Pod 名，作为子目录 |

## 三、远程日志推送（VictoriaLogs）

### 3.1 配置位置

公共配置文件：`resource/micro/web/src/main/resources/log4j2-linux.xml`

face-pay 没有独立的 log4j2 配置，使用上述公共配置。

### 3.2 关键 Appender

```xml
<VictoriaLogs name="VictoriaLogs" url="${env:VLOGS_URL:-}"
    queueCapacity="8192" batchSize="512" flushIntervalMillis="2000"
    connectTimeoutMillis="2000" requestTimeoutMillis="5000">
    <Filters>
        <ThreadContextMapFilter onMatch="ACCEPT" onMismatch="NEUTRAL">
            <KeyValuePair key="x-vlogs-capture" value="1" />
        </ThreadContextMapFilter>
        <ThresholdFilter level="DEBUG" onMatch="ACCEPT" onMismatch="DENY" />
    </Filters>
    <JsonTemplateLayout eventTemplateUri="classpath:victorialogs-event-template.json">
        <EventTemplateAdditionalField key="app" value="${appName}" />
        <EventTemplateAdditionalField key="host" value="${env:HOSTNAME:-unknown}" />
    </JsonTemplateLayout>
</VictoriaLogs>
```

### 3.3 face-pay Deployment 注入的环境变量

```yaml
- name: VLOGS_URL
  value: http://victoria-logs-pbi6a-svc.itp.svc:30032
- name: management.tracing.enabled
  value: "true"
- name: management.tracing.sampling.probability
  value: "0"
- name: spring.autoconfigure.exclude
  value: org.springframework.boot.actuate.autoconfigure.tracing.otlp.OtlpAutoConfiguration
```

### 3.4 运行状态确认

在 face-pay Pod 内确认存在 `vlogs-sender` 线程：

```bash
cat /proc/7/task/*/comm | grep vlogs-sender
# 输出：vlogs-sender
```

说明远程日志推送线程正在运行。

### 3.5 日志过滤规则

- MDC 中存在 `x-vlogs-capture=1` 的日志：全量上报（不受级别限制）
- 否则：DEBUG 及以上级别才上报
- Root logger 只上报 WARN 及以上
- `com.chinasofti.huateng` 和 `VLOGS` logger 上报 DEBUG 及以上

> 注：`log4j2-linux.xml` 注释显示 2026-10-14 刚将阈值从 INFO 下调为 DEBUG。

## 四、迁移到生产时的注意

1. **VLOGS_URL 必须带 `http://` 前缀**，且端口必须是 VictoriaLogs Service 的 NodePort（当前为 30032），不是默认 9428
2. 生产环境需确认新的 VictoriaLogs 地址，并更新 `VLOGS_URL`
3. `management.tracing.enabled=true` 与 `spring.autoconfigure.exclude=...OtlpAutoConfiguration` 必须**同时存在**，否则可能误起 OTLP exporter
4. 本地日志使用 `emptyDir`，Pod 重启即丢失；如需保留，应改为 PVC 或确保日志收集程序在 Pod 存活期间及时处理
5. 如生产不部署 VictoriaLogs，需移除或禁用 `VictoriaLogs` appender，否则 appender 会静默禁用（`url` 为空时），不影响业务

## 五、未发现的其他远程日志配置

经检查，face-pay 未发现以下配置：

- 独立的 syslog appender
- SocketAppender / JMSAppender / KafkaAppender
- Fluentd / Filebeat / Logstash sidecar
- NFS / PVC 日志卷
- rsyslog 远程转发规则

唯一远程日志通道就是上述 `VictoriaLogs` appender。
