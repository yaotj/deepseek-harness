# IT-049 测试环境 accEs（es-server）健康 503 被综管台监控误报 DOWN

> 本处理文档对应 `docs/tracking/问题清单.md` 索引表中 `IT-049` 一行。
> 发现于 2026-11-03 综管台全量真实浏览器 E2E（`docs/testing/admin-web/08-执行记录-2026-11-03.md` §五）。

## 1. 背景 / 现象
- 发现时间：2026-11-03
- 发现渠道（线上告警 / 测试 / 用户反馈 / 自查）：测试 —— 综管台「服务状态监控」页 22 服务中唯一异常项。
- 影响范围：仅测试环境观测面——监控页把 `accEs` 恒报 `DOWN（HTTP 503）`，与服务真实健康度（db/diskSpace/ping 全 UP、`/monitor/health` 恒 200）不符，造成误报噪声；**不影响任何业务流量**（本服务无 k8s 应用容器探针，路由不受 actuator 状态影响）。
- 现象描述：`GET http://es-server-38bzh-svc.itp.svc:8080/actuator/health` 恒 503，payload 唯一拖累项 `readinessState=OUT_OF_SERVICE`，其余组件（`db`/`diskSpace`/`livenessState`/`ping`）全 UP；重启 Pod 可复现（fresh-boot 即 503）。

## 2. 根因分析
- 排查过程（traceId / 关键日志 / 复现步骤）：
  1. Pod `2/2 Running`、Service 端点正常、`/monitor/health` 200 `{"msg":"ok"}` ⇒ 服务本体健康。
  2. 主端点 503 payload 定位到 `readinessState=OUT_OF_SERVICE` 为唯一非 UP 组件。
  3. 现查 k8s-master：es-server / para-server 等**应用容器均无 readiness/liveness 探针**（仓库 `deploy/k8s/10-deployments/*.yaml` 的 `/monitor/health` 探针模板未在线上生效，Pod spec 里只有 istio-proxy 自身 `/healthz/ready`）。
  4. 对照实验：重启 para-server Pod 后其主端点仍 200（`readinessState=UP`），重启 es-server 后仍 503 ⇒ 差异在 es-server 应用侧。
  5. 读 Spring Boot 3.2.6 字节码（`spring-boot-actuator-autoconfigure`）：`AvailabilityProbesAutoConfiguration`（`ProbesCondition`：`management.endpoint.health.probes.enabled` ≠ false **且** 检测到运行于 K8s）把 `livenessState`/`readinessState` 指示器挂进**主** `/actuator/health`；k8s 探针感知下 readiness 默认 `REFUSING_TRAFFIC`，等 k8s readiness 探针命中 `/actuator/health/readiness` 才翻转——本服务无探针 ⇒ 永远 OUT_OF_SERVICE。
- 根因结论：es-server 跑在 K8s 下触发了 Spring 的探针感知装配，但应用容器没有配置探针驱动 readiness 翻转，主端点被 k8s 感知的 `readinessState` 恒拖成 503；综管台监控探主端点 ⇒ 误报 DOWN。

## 3. 方案对比
| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| A（采用） | `acc-es-server` `application.properties` 加 `management.endpoint.health.probes.enabled=false`（关闭探针感知；本服务本无探针，语义与事实一致） | 一行配置；主端点回归真实业务健康（db/diskSpace/ping）；与其它 21 服务监控口径对齐 | 主端点 JSON 不再含 liveness/readiness 组件（形态与 para 略异，功能无差） | 极低；日后给本服务加 k8s 探针 MUST 移除该行 |
| B | 给 es-server 应用容器加 k8s readinessProbe（仓库基线 `/monitor/health` 形态） | 贴合仓库 deploy 模板 | 不解决 Spring 应用内 readiness 状态；改变 k8s 路由语义（探针挂 → 摘流量） | 中 |
| C | 代码显式 `AvailabilityChangeEvent.publish(ACCEPTING_TRAFFIC)`（ApplicationReadyEvent 监听 / CommandLineRunner 内发布） | 主端点形态与其它服务一致 | **实测两版（2.0.27 / 2.0.28）均无效**——探针感知路径与事件机制不符，原因未完全定位 | 已试错 2 个版本，成本高 |
| D | 改综管台监控探活目标（accEs 单独探 `/monitor/health` 或 liveness 组） | 不动业务服务 | 监控口径 22 个服务不一致；主端点 503 仍会被其它工具误判 | 低 |

## 4. 决策与结论
- 选定方案：**A**。
- 决策理由：根因即「探针感知装配」与「无探针」的事实错配；关掉装配最贴近事实、最小改动（1 行 + 版本 bump）、可逆。
- 影响范围 / 关联模块：仅 `acc-es-server`。工作区改动：`acc-es-server/src/main/resources/application.properties`（+1 有效行，含 5 行注释）、`acc-es-server/pom.xml` 2.0.26→**2.0.29**（中间无效方案已试 2.0.27/2.0.28，均已被取代；代码改动 `EsServerApplication` 已还原，git 工作区无残留）。
- 红岛生产：本缺陷为测试环境观测面问题；生产 es-server 下次发该模块时 MUST 顺带验证其主端点状态（同框架行为大概率一致，但生产操作红线：二次确认 + 灰度）。

## 5. 验证与回滚
- 验证方式 / 结果（2026-11-03，测试环境）：
  - `acc-es-server:2.0.29` 经 `itp-ops.legacy.sh k8s deploy` 远端构建推送，digest `sha256:c3bfb873a71c17f561f9dfde091844d76c1fb2084e4b2cb6f59625b44fab6974`；
  - `kubectl set image` 滚更成功，新 Pod `es-server-79f7888447-flbs2` **imageID digest 与推送 digest 一致**、重启 0；
  - Pod IP 直连 `GET /actuator/health` → **HTTP 200 `{"status":"UP","components":{"db":"UP","diskSpace":"UP","ping":"UP"}}`**（liveness/readiness 组件已消失，符合预期）；
  - 综管台「服务状态监控」页：**22/22 UP、0 DOWN**，accEs 行 `UP 17ms`（修复前 `DOWN 503`）。
- 回滚预案：`ssh k8s-master "kubectl -n itp set image deployment/es-server es-server=os-harbor-svc.default.svc.cloudos:443/itp/acc-es-server:2.0.26 && kubectl -n itp rollout status deployment/es-server"`（2.0.26 为缺陷前基线，镜像仍在 Harbor）。

## 6. 流程符合度自评
- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-049`）
- [x] 已建本处理文档并记录处理过程与决策
- [x] 处理后已回填索引「状态 / 下一步 / 更新」
- [x] 索引行未复制本文档的叙述（索引只写「一句话现象 + 状态 + 下一步 + 关联文档」）
- [x] 本文档内的版本号 / 端口 / 行号等数字已标注来源或「MUST 现查」（镜像 tag 2.0.26→2.0.29、digest 为 2026-11-03 发版实值，随版本推进过期）
- 偏差说明：方案 C 两次试错（2.0.27 @EventListener、2.0.28 run() 发布）均无效后转方案 A；试错版本保留在 Harbor 未清理（不占发布序列，无害）。
