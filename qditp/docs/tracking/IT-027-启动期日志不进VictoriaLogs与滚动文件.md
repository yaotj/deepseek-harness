# IT-027 启动期日志不进 VictoriaLogs、也不落滚动文件

## 1. 背景 / 现象

- 发现时间：2026-10-28（日志配置统一改造收尾时确认）。
- 发现渠道：自查；IT-023 排查中被间接印证。
- 影响范围：全部依赖 `micro/web` 的 Java 服务（当前 21 个）。
- 现象：进程从启动到 `ApplicationReadyEvent` 之间的日志只进 stdout（`kubectl logs` 可见），
  **既不写 `${logPath}/${appName}.log`，也不进 VictoriaLogs**。若启动在 ready 之前失败
  （例如 IT-023 的 DB 认证被 RST），Pod 内连日志文件都不会产生：
  `itp-ops.sh log tail` 全空、VL 查该 app 为 0 条，看起来像「服务从未启动 / 没日志」。
- **本次统一改造放大了该盲区**：2026-10-27 删除 `pay-sign-server` / `collect-pay-server` / `web-admin`
  的模块专属 log4j2 配置后，这三个服务由 `logging.config` 在启动早期加载（启动期即有文件与 VL）
  退回公共路径（只在 `ApplicationReadyEvent` 重载），盲区从「部分模块」扩展到「全部模块」。

## 2. 根因分析

1. `resource/micro/web/src/main/resources/log4j2-test.xml` 随 `micro/web` 进入所有模块的 classpath；
   Spring Boot 3.2.6 `Log4J2LoggingSystem.getStandardConfigLocations()` 的查找顺序中
   `log4j2-test.xml` 排在 `log4j2.xml` 之前（2026-10-28 对 `spring-boot-3.2.6.jar` 执行 `javap -c` 实测），
   因此启动早期实际生效的是这份「测试命名」的配置。
2. `log4j2-test.xml` 只有 `Console` appender、Root INFO，**没有 `RollingFile`、没有 `VictoriaLogs`**。
3. 带文件与 VL appender 的 `log4j2-linux.xml` 只由 `CustomLoggingConfiguration` 在
   `ApplicationReadyEvent` 重载；启动失败就永远到不了 ready，重载不会发生。
4. 启动早期 `%X{traceId}` 也是空值（`FirstFilter` 尚未工作），即便把启动日志接到 VL 也没有 traceId。

## 3. 方案对比

| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| A | 在 `log4j2-test.xml` 里补 `RollingFile` + `VictoriaLogs`，lookup 全部带默认值 | 启动即落盘、即上报；只改 XML | 与运行期配置两处维护；启动期 `appName` / `logPath` 只能取 env 默认值，可能与运行期目录不一致；需防两份配置重复初始化 appender | 中 |
| B | 把重载时机提前到 `ApplicationStartedEvent` 或更早 | 单份配置 | `webLoggerLevel` / `logPath` / `appName` 系统属性由同一个 Listener 赋值，提前后 MUST 重排赋值顺序；更早事件里 Spring 环境尚未完全就绪 | 中 |
| C | 维持现状，运维侧明确「启动问题只看 `kubectl logs` / `log watch`」 | 零改动、零风险 | 启动失败场景在 VL 与日志盘上仍全盲 | 低 |

## 4. 决策与结论

- 选定：**C（本次不动，只记录）** —— 2026-10-28 用户裁决。
- 运维判据（立即生效）：启动期问题 MUST 先看 `kubectl logs`（或 `itp-ops.sh log watch`），
  **NEVER 以「VL 0 条 / 日志盘无文件」推断服务没启动过**；反过来，日志盘无文件本身就是
  「进程未到 ready」的判据之一（见 IT-023）。
- 关联模块：全部依赖 `micro/web` 的服务；配置落点 `resource/micro/web/src/main/resources/log4j2-test.xml`。

## 5. 验证与回滚

- 本次未改动，无验证与回滚动作。
- 后续若实施方案 A / B，验证方式：重启服务后除 `kubectl logs` 外，在 VL 按 `app` 查启动窗口记录；
  回滚 = 还原 `log4j2-test.xml` / 重载时机，并重新出 `resource/micro` + 受影响模块的镜像。

## 6. 流程符合度自评

- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-027`）
- [x] 已建本处理文档并记录处理过程与决策
- [x] 处理后已回填索引「状态 / 下一步 / 更新」
- [x] 索引行未复制本文档的叙述（索引只写「一句话现象 + 状态 + 下一步 + 关联文档」）
- [x] 本文档内的版本号 / 端口 / 行号等数字是否已标注来源或「MUST 现查」
- 偏差说明：本轮按用户裁决只记录、不改代码；「2026-10-27 统一改造放大盲区」与本次改造同批生效，尚无线上实测样本。
