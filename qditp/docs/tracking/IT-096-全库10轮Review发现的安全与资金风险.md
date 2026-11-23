# IT-096 全库 10 轮 Review 发现的安全与资金风险

> 本处理文档对应 `docs/tracking/问题清单.md` 索引表中 `IT-096` 一行。
> 重档判据：安全风险（验签绕过 / 密钥泄露 / 卡号明文）+ 资金一致性风险（事务吞异常）。
> **2026-11-22 复核更新**：经核查，`collect-ticket-server` 从未部署、无流量，已于 2026-11-22 归档清理（源码移入 `archive/abandonedservices/collect-ticket-server`，根 pom 移除 module）。原 5 项红线中 **2 项（验签 fail-open、支付预下单事务吞异常）位于该模块 → 降为「低·死代码」，不再需人工复核**。
> **2026-11-22 修复更新（用户要求修复）**：现存 3 项**均已修复并编译验证通过**——① SM2 私钥 d/x/y 明文日志已删除（`SM2.java:313`）；② 加密机超时日志卡号脱敏（前6后4）+ 保留域只记长度（CommonSaleAndRefund/CommonTac/CommonMac）；③ 签约回调 catch 增加 `setRollbackOnly()`（`SignResultCallbackHandler.java`）。修复涉及加密机模块日志与资金链路，**仍需人工复核安全合规性 + 发版验证**；未改任何加密/签名算法逻辑。

## 1. 背景 / 现象
- 发现时间：2026-11-21
- 发现渠道：全库 10 轮架构体检（只读代码审查，2432 个 Java 文件）
- 影响范围：acc-security-server（SM2 密钥 / 加密机日志）、pay-sign-server（签约回调）；collect-ticket-server 两项已随模块归档降级
- 现象描述：发现 5 项安全/资金风险（初评），其中 2 项因所属模块已归档降为死代码风险，**现存 3 项需人工复核**。明细与逐行证据见 [全库 10 轮 Review 报告](../reviews/2026-11-21-全库10轮代码Review.md) 第 2/3/9 轮。

## 2. 根因分析
- 排查过程：只读扫描 + 逐行核验（`PaySignUtils.java:52-54`、`SM2.java:313`、`CommonSaleAndRefundServiceImpl.java:72` 等均已 Read 工具确认属实）。
- 根因结论：
  1. **验签 fail-open**：`PaySignUtils.verify()` 公钥为空时 `return true`，而公钥注入默认空串 → 缺省配置下验签被绕过（资损风险）。
  2. **密钥明文日志**：SM2 密钥生成重试分支打印私钥 d / 公钥坐标 x/y，日志落持久化 PVC。
  3. **卡号明文日志**：加密机超时日志打印完整卡号 + 用户保留域。
  4. **事务吞异常**（2 处）：支付预下单 / 签约回调在 `@Transactional` 内 catch 异常后 return，事务照常提交，落库与远程/审计/事件可能不一致。

## 3. 方案对比
| 风险 | 方案 A | 方案 B | 推荐 |
|------|--------|--------|------|
| 验签 fail-open | 启动期强校验公钥，缺失 fail-fast | `verify()` 公钥空直接 `return false`（fail-closed） | B（最小改动）+ A（启动兜底） |
| 密钥/卡号明文日志 | 删除敏感字段打印，仅留索引/指纹 | 采样 + 脱敏调试开关 | A |
| 事务吞异常 | catch 显式重抛 / `setRollbackOnly()` | 事件改 `@TransactionalEventListener(AFTER_COMMIT)` | A + B |

## 4. 决策与结论
- 选定方案：见上表推荐列；**具体改动待人工复核后另行实施**。
- 决策理由：均触及 AGENTS.md §4.2 安全红线（支付 / 密钥 / 签名 / 资金一致性），AI 不擅自修改，须人工复核安全合规性。
- 影响范围 / 关联模块：collect-ticket-server、acc-security-server、pay-sign-server。
- 本轮已做的零风险修复：仅 `CertificateServiceImpl.java:57` 与 `TransformUtils.java:176/197` 的日志输出通道（不含上述红线项）。

## 5. 验证与回滚
- 验证方式 / 结果：本轮红线项未改，无验证；报告证据为逐行 Read 核验。零风险修复走 `mvn -o -pl acc-security-server -am clean test-compile`。
- 回滚预案：红线项改动前需在各环境确认公钥/密钥注入实况；日志脱敏改动随对应模块发版，保留回滚点。

## 6. 流程符合度自评
- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-096`）
- [x] 已建本处理文档并记录处理过程与决策
- [ ] 处理后回填索引「状态 / 下一步 / 更新」（待人工复核后）
- [x] 索引行未复制本文档叙述（索引只写一句话现象 + 状态 + 下一步 + 关联文档）
- [x] 本文档内行号均来自 2026-11-21 逐行核验，标注了来源
- 偏差说明：本项为"审查发现汇总"型重档，5 项风险合并一个 IT；红线项按规范只报告不修，故无"处理后回填"动作，待人工处置时再更新索引。
