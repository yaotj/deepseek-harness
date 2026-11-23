# IT-097 盲区 Review 发现的安全红线与确定性缺陷

> 本处理文档对应 `docs/tracking/问题清单.md` 索引表中 `IT-097` 一行。
> 重档判据：安全风险（弱口令 / 监控台暴露 / TLS 关闭 / token 明文 / 网关无鉴权 fail-open）+ 确定性功能缺陷（分页恒空）。
> 来源：[2026-11-22 盲区 10 轮 Review 报告](../reviews/2026-11-22-盲区10轮代码Review.md) R4/R7/R8/R9/R10。
>
> **2026-11-22 修复更新（用户要求修复）**：
> **已修复并编译验证**：① route-sentinel 5 项（写端点鉴权 fail-closed、断言失败 fail-safe、traceparent/b3 透传、bizData 日志脱敏、熔断器半开态，57 测试全绿）；② resource：ApiErrorResponse NPE 判空、ProxyOkHttp b3 空值不拼、FirstFilter token 日志脱敏 + MDC finally 清理、分页方言漏配告警、CORS 冲突消除；③ web-server：SecurityConfig 移除 `/druid/**` permitAll；④ 前端发行渠道 07 译名对齐真源；⑤ BLACKLIST DROP 脚本加备份守卫、13 个 job 脚本改动态取号+NOT EXISTS（`debit-retry` 的 cron/backoff 夹带已回退，保持 HEAD 原值）。
> **按项目口径未改**：web-server `token.secret` 弱口令、`statViewServlet.login-password` —— 仓库明文口令是唯一真源（真源表 `docs/ops/environments/跨环境共性.md` §三），改成 Secret 注入空默认违反硬约束，**仅报告不擅改**。
> **需人工复核/现查**：ProxyOkHttp TrustAll TLS（改严格模式会让自签证书的 HTTPS 出向调用挂掉，保持现状待决策）；COUNTING_END 列类型、BLACKLIST 脚本执行状态、job_id 号段占用。
> **发版义务**：resource/micro（web + mybatis-adaptor）是全业务模块基础构件，改动后 MUST 单独 install 并重打包依赖服务镜像才生效。

## 1. 背景 / 现象
- 发现时间：2026-11-22
- 发现渠道：承接 11-21 全库 Review 未覆盖段，补做前端 / web-server / resource / SQL 的 10 轮只读审查
- 影响范围：web-server（综管台后台）、resource/micro（全业务公共构件）、route-sentinel（网关）、alipay-pay-sign-server（分页）
- 现象描述：6 项安全红线（未改）+ 1 处确定性分页 bug（已修）+ 3 处需现查库的 SQL 风险。明细与逐行证据见 Review 报告。

## 2. 根因分析
- 排查过程：4 组并行只读扫描 + 关键项 Read 逐行核验（`/para/operator` 初评高→核验为 mock 死代码降级；分页 bug 含 Controller 传参链已确认；渠道 07 译名与 model 真源冲突已确认）。
- 根因结论：
  1. web-server `token.secret` 弱口令硬编码、`/druid/**` permitAll + allow 空 → 监控台外网可访问。
  2. resource 公共构件 `ApiErrorResponse` 未判空致全局异常处理 NPE 失效；`ProxyOkHttp` 无条件 TrustAll；`FirstFilter` token 明文 + MDC 未清理。
  3. route-sentinel 路由写端点无鉴权、断言解析失败 fail-open（`req -> true`）。
  4. `AlipayPayLogMapper.xml` 分页上界写 `rn <= #{limit}` 而非 `offsetEnd`，第 2 页起恒空。

## 3. 方案对比
| 风险 | 方案 A | 方案 B | 推荐 |
|------|--------|--------|------|
| token.secret | Secret 注入 `${WEB_TOKEN_SECRET:}` | 强口令硬编码 | A（注意存量 JWT 失效，低峰执行） |
| Druid 暴露 | 关闭 statViewServlet | allow 限内网 + 口令 Secret + 移除 permitAll | A 或 B（B 更稳） |
| TrustAll TLS | 用 JVM 默认信任链 | 注入企业 CA SSLContext | B（生产需 CA） |
| token 明文日志 | 白名单头 + try/finally MDC.clear() | 仅删日志打印 | A |
| 网关 fail-open | 解析失败跳过该路由 + 保留上次 RouterFunction | 启动 fail-fast | A（可用性优先） |
| 分页 bug | 加 bind offsetEnd（已修） | 改 PageHelper | A（已实施） |

## 4. 决策与结论
- 选定方案：见上表推荐列；安全红线项**待人工复核后实施**，本轮不擅自改。
- 决策理由：均触及 AGENTS.md §4.2 安全红线（鉴权 / 密钥 / 网关流量）或全业务公共构件影响面，须人工复核。
- 本轮已做的零风险修复（4 处）：前端扣费状态裸码×2（走 `debitStatusLabel`）、`LogAspect` 审计日志堆栈、支付宝日志分页上界。

## 5. 验证与回滚
- 验证方式 / 结果：前端 `node syntax-check.mjs` ERROR 0 + `node query-ui-check.mjs` ERROR 0；`mvn -o -pl web-server/web-framework -am clean test-compile` exit 0；mapper XML 良构 OK；`lint-prompts.py` ERROR 0。分页修复待真库 smoke 验第 2 页返回。
- 回滚预案：4 处修复均为显示/日志/分页边界改动，随所属模块发版，保留回滚点；安全红线项改动前须低峰期 + 各环境配置实况确认（token 更换会使存量 JWT 失效）。

## 6. 流程符合度自评
- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-097`）
- [x] 已建本处理文档并记录处理过程与决策
- [ ] 处理后回填索引「状态 / 下一步」（待人工复核 + 真库 smoke 后）
- [x] 索引行未复制本文档叙述
- [x] 行号均来自 2026-11-22 逐行核验，标注了来源
- 偏差说明：本项为「审查发现汇总」型重档，多项风险合并一个 IT；红线项按规范只报告不修，待人工处置时再更新索引。
