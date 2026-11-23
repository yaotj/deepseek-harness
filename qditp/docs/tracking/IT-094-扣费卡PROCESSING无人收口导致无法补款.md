# IT-094 扣费单卡 `PROCESSING` 无人收口，乘客「一键补扣」永久被拒

> 本处理文档对应 `docs/tracking/问题清单.md` 索引表中 `IT-094` 一行。

## 1. 背景 / 现象

- 发现时间：2026-11-20 15:19（用户提供 APP 截图）
- 发现渠道：用户反馈（测试环境真机 APP）
- 影响范围：`PAYMENT_VENDOR=0B`（钱包免密）扣费单；测试环境当前全库 4 笔 `PROCESSING` 全部集中在一张卡（`0426090949000126` / 用户 `00523003`）
- 现象描述：APP「地铁未扣费订单」列出 6 笔，点「一键补扣」弹 `部分订单已结清或状态不可补款`（IF8A-26 返 `8003`）。

## 2. 根因分析

**排查过程**

1. face-pay 日志（Pod 日志，15:18:59，trace `a6c8f7d56d9cb7b3d736018ceea9efcf`）：

   ```
   补款下单请求, request=SupplementOrderReqDTO{... orderNoList=[GT20261120140204397000126,
     GT20261119134942691000126, GT20261119134448240000126, GT20261117152315081000126,
     GT20261116114803555000126, GT20261116114324978000126] ...}
   补款下单校验失败：部分原订单状态不可补款（非 INIT/RETRY/FAIL）, notPendingCount=1
   补款下单结果, orderNo=null, payStatus=null   → 8003 部分订单已结清或状态不可补款
   ```

   与截图时间（15:19）完全对上，`notPendingCount=1` 说明只有 1 笔不合格。

2. 查 `QDITP.GATE_TXN_PAY` 这 6 笔：5 笔 `FAIL`（可补款），唯 `GT20261120140204397000126`（7 元，王家港→苗岭路 14:01–14:02）是 **`PROCESSING`**。

3. 查该笔的支付域流水 `QDITP.PAY_TXN_DETAIL`：`PAY_STATUS=PROCESSING`、`PAY_CENTER_ORDER_NO=294040074557751296`（支付中心已建单）、`CHANNEL_ORDER_NO` 为空、`PAY_TIME` 为空、`REQUEST_COUNT=1`。即：**pay-sign 发起后支付中心返回「处理中」，此后没有任何成功/失败回调**。

4. 查 `QDITP.GATE_TXN_PAY.REMARK` = `扣费已被pay-sign受理`，`UPDATE_TIME` 停在 14:02:07——**106 分钟没有任何状态推进**。

**根因结论**

`PROCESSING`（已受理、等支付结果）在本系统里**没有任何兜底收口通道**，三个可能推进它的入口全部把它排除在外：

| 入口 | 判定 | 对 `PROCESSING` 的结论 |
|---|---|---|
| 补款 IF8A-26 | `DEBIT_PENDING = INIT/RETRY/FAIL` | 拒绝（本次 8003） |
| 综管台手工重试 `retryPay` | `DebitStatus.isRetryable` = `INIT/RETRY` | 拒绝「当前扣费状态不允许重试支付」 |
| 批量重试队列 | `DebitStatus.isBatchRetryable` = `RETRY/FAIL` | 不进队列（实测 `GATE_RETRY_QUEUE` 该单 0 行） |
| 主动查询收口 | `GateTxnPayMapper.updateStatusIfProcessing`（`PROCESSING → SUCCESS/FAIL`） | **全仓零调用方（死代码）**；`gate-txn-pay-server` 与 `pay-sign-server` 均无 `@Scheduled` |

即 `PROCESSING` 只能靠支付中心回调离开该状态，回调不来就永久卡死。叠加 IT-090（测试支付中心未配置 `0B` 渠道），钱包免密扣费基本「受理即卡死」。

**口径不一致（同一笔单两套结论）**：IF8A-35 的 `unpaidCount` 白名单是 `INIT/PROCESSING`（APP 因此把它列为欠费、允许展示在未扣费订单里），而 IF8A-26 的补款白名单是 `INIT/RETRY/FAIL`。APP 说「可以补」，接口说「不可补」——乘客点一次失败一次，且 8003 文案无法告诉他卡在哪一笔。

## 3. 方案对比

| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| 方案A 补款白名单加 `PROCESSING` | 让补款直接吃下卡住的单 | 改动最小、一行 SQL | 挡不住双付：该笔其实可能正在支付中心扣款中，补款成功 = 同一笔欠费收两次 | **高（资金）** |
| 方案B 加 `PROCESSING` 超时收口任务 | `gate-txn-pay` 新增 `@Scheduled`：`PROCESSING` 超 N 分钟主动查支付中心，查到终态就收敛、查不到（超时未支付）落 `RETRY`，让补款/重试自然接上 | 从根上解决「卡死」；补款白名单不用动；与既有状态机一致 | 需要支付域查询能力与超时阈值；新增调度要复核单副本约束（`F2fGuardTest` 只锁 face-pay，gate-txn-pay 是新调度） | 中（查询语义需与支付中心确认，见 IT-091） |
| 方案C 仅数据修复 | 手工把这 4 笔改 `RETRY` | 零代码 | 治标：下一笔 0B 扣费照样卡死 | 低，但问题不闭环 |
| 方案D 补款前对账 | IF8A-26 遇 `PROCESSING` 先同步查一次支付中心，得终态就继续补款 | 用户体验最好（点一次就成） | 在补款链路里夹支付域查询，链路变长、且 `PROCESSING` 查询语义未确认 | 中 |
| **方案E 列表侧收口（已采纳）** | `Debit_Result_Filter` 的「未扣款成功」分支由黑名单 `IS NULL OR != 'SUCCESS'` 收窄为白名单 `RETRY/FAIL`，「地铁未扣费订单」不再列出支付中的单 | 一行 SQL 解决「点了必失败」；白名单与补款侧 `DEBIT_PENDING` 同源，列表里每一笔都真的可补款；顺带排除 `NULL` 脏数据 | 治标：`PROCESSING` 单本身仍卡着、钱仍收不上来，只是不再把乘客引到必败的补扣上 | 低（不涉资金语义） |
| **方案F 补款白名单去掉 `INIT`（已采纳）** | `DEBIT_PENDING` 由 `INIT/RETRY/FAIL` 收窄为 `RETRY/FAIL`（列表侧 `Debit_Result_Filter` 同步） | 堵住「发起中」窗口的双付口子：`INIT` 是「已落单、异步任务正在发往支付中心」，出站接口对外已回 `PROCESSING` 而库里仍是 `INIT`，此时补款与原免密扣费可能各扣一次 | 卡 `INIT` 的单不能靠补款自救（但 `DebitRetryProcessor` 每轮扫 `INIT`、必然接管，不是死路）；`DISCOUNT_CALC_STATUS='OFFLINE_FARE_PENDING'` 也一并被挡（本就是 0 元待重算，应挡） | 低 |

## 4. 决策与结论

- **选定方案：E + F（2026-11-20 用户两次裁决）**：16:11「这个列表中不应该包含支付中的数据」→ E（列表不再列 `PROCESSING`）；16:19「`INIT` 是什么状态…我觉得这个状态应该不能补款」→ F（补款白名单去掉 `INIT`，列表同步）。两者已发测试验证。
- 决策理由：`PROCESSING` = 已发往支付中心等结果，`INIT` = 已落单、异步任务正在发往支付中心（出站接口对外已回 `PROCESSING`，库里仍是 `INIT`）——两者钱都可能已在路上，让它们可补款等于开双付口子。列表口径与补款白名单对齐后，「能看到的都能补」。
- **`INIT` 去掉后不会产生死信箱**：`sys_job 345`（`DebitRetryProcessor.retryRecentUnpaidDebits`）专门扫卡在 `INIT` 的单、带 `minAgeSeconds=60` 观察期，把它转 `RETRY` 重发；`RETRY/FAIL` 归 220/255。这也正是 `minAgeSeconds` 存在的意义 —— 给「发起中」窗口留观察期。
- **2026-11-20 追加裁决（ADR-D176，已发测试）**：用户「改为：定时任务每天重试两次，上午 9 点一次，下午 15 点一次」，220 / 255 / 345 三条 cron 统一为 `0 0 9,15 * * ?`（此前 220/255 为每天 1 点、345 为每分钟）；345 同步收窄为只捞 `INIT`（避免与 220/255 抢 `RETRY/FAIL` 双发扣款），删 `windowMinutes`，`backoffMinutes` 720→300。因此 `INIT` 单的最长自救等待由「最多 1 分钟」变为「最多 6 小时」——**这是对齐旧 ITP 的有意取舍，NEVER 报成回归**。gate-txn-pay 2.1.28 已发测试（3 Pod Ready、新日志 `卡在INIT的单补发本轮无候选`），`SYS_JOB` 三条已 UPDATE 并重启 web-admin 生效（RAMJobStore 重载）。
- **A 明确否决**：补款直接吃 `PROCESSING` 会双付。
- **B/C/D 仍未落地**：`PROCESSING` 卡死本身（钱收不上来）依然存在，本次只是不再误导乘客、不再给双付口子。后续仍建议 B（超时收口任务）+ C（存量卡单人工转 `RETRY`）。
- 影响范围 / 关联模块：`gate-txn-pay-server`（`GateTxnPayMapper.xml` 的 `Debit_Result_Filter`，被 `selectTransList` / `countTransList` 共用）；IF8A-05 交易列表经 `trans-query-server` → `gate-txn-pay` 同一路径受影响；关联 IT-090（0B 渠道未配置）、IT-091（回查语义）、IT-082 阶段三（响应侧四态）。

## 5. 验证与回滚

- **改动**：① 列表侧 `Debit_Result_Filter` 非成功分支 `AND (DEBIT_STATUS IS NULL OR DEBIT_STATUS != 'SUCCESS')` → `AND DEBIT_STATUS IN ('RETRY', 'FAIL')`；② 补款侧 `SupplementOrderServiceImpl.DEBIT_PENDING` `INIT/RETRY/FAIL` → `RETRY/FAIL`，拒绝文案改为「部分订单已结清或正在扣费中，暂不可补款」。
- **单测**：`GateTxnPayMapperSqlTest.debitResultFilterNeverIncludesProcessingOrInit`（断言白名单、`NEVER` 含 `PROCESSING`/`INIT`、`NEVER` 退回黑名单写法）+ `recentRetryOnlyClaimsStuckInitOrders`（345 只捞 INIT、无窗口上界、不记账）共 18 例全绿；face-pay `SupplementOrderServiceImplTest` 新增 `initDebitOrderIsNotSupplementable` / `processingDebitOrderIsNotSupplementable`，21 例全绿。
- **E2E（gate-txn-pay 2.1.25）**：`POST /ci/app/requestTransList`（经 fep-app，用户 `00523003` / 卡 `0426090949000126`，`debitRequestResult=1`）返回 9 条，**其中 `GT20261120154401159000126`（800 元、`PROCESSING`）不在列表**，而同批已收敛为 `FAIL` 的 `GT20261120140204397000126`(700) / `GT20261120152340275000126`(600) / `GT20261120154827755000126`(600) 均在列表 —— 唯一差异就是状态。
- 回滚：`cloudos deploy gate-txn-pay-server os-harbor-svc.default.svc.cloudos:443/itp/gate-txn-pay-server:2.1.24 --app-version 2.1.24 --yes`；face-pay 回滚到 `1.1.0`。

## 6. 流程符合度自评

- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-094`）
- [x] 已建本处理文档并记录处理过程与决策
- [x] 处理后已回填索引「状态 / 下一步 / 更新」
- [x] 索引行未复制本文档的叙述（索引只写「一句话现象 + 状态 + 下一步 + 关联文档」）
- [x] 本文档内的版本号 / 端口 / 行号等数字是否已标注来源或「MUST 现查」
- 偏差说明：`@Scheduled` 数量在 `gate-txn-pay-server` 当前为 0（本次现查 `grep -rn "@Scheduled" gate-txn-pay-server/src/main` 零命中），新增调度前 MUST 复核单副本约束。**方案B 未实施，本问题残留「卡单本身无人收口」这一半，索引状态标为「部分收口」。**
