---
业务域: 闸机出站扣费（后付费）
模块: gate-txn-pay-server
---

# 提示词：闸机出站扣费

## 何时读本文件
后付费乘车码出站扣款、扣费失败重试、APP 交易记录/详情（IF8A-05 / IF8A-34）、APP 用户账务信息（IF8A-35）、解约前的失败订单校验相关改动。

## 模块定位
`gate-txn-pay-server`，端口 **9106**（`gate-txn-pay-server/src/main/resources/application.properties`）。
职责：接收 fep-dev-server 转发的出站交易 → 生成扣费订单 → 异步调 pay-sign 免密扣款；
并为 ticket-server 提供交易查询、为 pay-sign 提供"是否存在扣费失败订单"判断、为运营端提供分页与退款。

## 接口清单

> **2026-09-14 / 2.0.67 起按调用方分了文件（ADR-D68），12 条 URL 逐字未变**：
> `controller/GateTxnPayController`（服务间 RPC）、`controller/app/GateTxnPayAppController`（APP 场景）、
> `controller/page/GateTxnPayPageController`（运营后台）、`controller/internal/ReconExportController`（对账）。
> 上游全是硬编码 URL，**NEVER 借重构改任何一个路径**，也 NEVER 把端点挪到别的前缀下。

`gate-txn-pay-server/.../controller/GateTxnPayController.java`（前缀 `/ci/gateTxnPay`，服务间 RPC）
- `/requestPay` — 出站扣费下单
- `/retryPay` — 扣费重试
- `/queryOrderByBizKey`
- `/hasFailedOrder` — 供 pay-sign 解约流程判断
- `/hasUnsettledOrderByCard` — 供 blacklist-server 判断黑名单可否解除
- `/syncDebitStatus` — pay-sign 支付结果回调后收敛扣费状态（补款单号在此分派给 `SupplementOrderService`）

`gate-txn-pay-server/.../controller/app/GateTxnPayAppController.java`（前缀 `/ci/gateTxnPay/app`，APP 场景）
- `/requestTransList`（IF8A-05）、`/countTransList`
- `/requestTransStatistics`（IF8A-41）— 账单统计
- `/queryByOrderNo`（IF8A-34）
- `/requestPayOrder`（IF8A-26）— 补款下单（本组唯一有写的端点）
- `/requestUserAccInfo`（IF8A-35）— APP 用户账务信息：未支付订单数 + 扣费失败订单数


`fep-app-server/.../controller/GateTxnPayController.java`（APP 入口，双别名）
- `{"/ci/app/requestPayOrder", "/app/requestPayOrder"}`（IF8A-26）
- `{"/ci/app/requestUserAccInfo", "/app/requestUserAccInfo"}`（IF8A-35）

⚠️ **IF8A-35 落在本模块而不是 account-server**：数据源就是 `GATE_TXN_PAY`，account-server 无任何账务表、也没注入 `GateTxnPayClient`，经它中转只是多一跳。`docs/ops/README.md` 早期方案曾把它列在 account-server，**以本文件为准**。

`gate-txn-pay-server/.../controller/page/GateTxnPayPageController.java`（前缀 `/page/gate-txn-pay`）
- `GET /` 分页、`POST /{orderNo}/refund`

⚠️ **本模块没有 `/internal/supplement/**` 接口**。曾实现过 `SupplementInternalController`（`queryForPrepay` / `applyPrepayResult`，供 collect-pay 在 IF8A-11 里回查补款单）并写进本文件，**2026-09-11 已整体删除**——用户裁决「不动 collect-pay」，补款单改为由本模块直接写一行 APP 订单（见下文「补款单的收款通道」）。**NEVER 重建这两个接口**，也不要按旧记载去 collect-pay 里找调用方。

## 核心类
- `service/impl/GateTxnPayServiceImpl.java` — 扣费主逻辑。**2.0.71（ADR-D77）起它不再 import 任何 `com.chinasofti.huateng.rpc` 下的类**：退款与人工重试整块搬到 `service/impl/GateTxnPayManualOpsService.java`（`retryPay` / `requestRefund` 在 Impl 里只剩一行委派，接口签名未变）。**NEVER 把退款搬回来** —— 出站扣费跟着闸机报文与算价规则变，退款跟着运营流程与支付中心退款接口变，两者只共用一张表。新类同样 **NEVER 加 `@Transactional`**（退款分支内有支付中心 RPC）。
- `paysign/PaySignInitiator.java` — 三入口收敛 + 渠道分派 + 状态回写，**不读任何 `@Value`**。两个渠道的报文组装在 `paysign/GatePayRequestFactory.java`（`gate.pay.*`）与 `paysign/AlipayTripPayRequestFactory.java`（`alipay.trip.*` + `pay.center.callback-url`）。**NEVER 把两套配置合并回一串构造参数**：`orderTimeOut` 在 pay-sign 分支是**秒**、支付宝分支是**分钟**，混用不报错、只在对端超时行为上表现。
- `constant/` 四个类是状态与码值的唯一来源（2.0.72）：`DebitStatus`（`DEBIT_STATUS` 五取值 + `isRetryable` / `isRefundable` 白名单，入库 MUST 用 `code()`、**NEVER 用 `name()`**）、`GateTxnPayRetCode`（`0000/8001/8002/8003/9002`，全模块硬编码已收口）、`GateTxnPayFieldCode`（`isExitTrxType` / `isWalletVendor` / `isBluetoothChannel` / `isCompanionOrThirdParty`）、`DiscountCalcStatus`（`DISCOUNT_CALC_STATUS` 四取值 `SUCCESS/SKIPPED/FALLBACK/OFFLINE_FARE_PENDING`，第三套状态词汇，列宽已加宽至 32；`SUPPLEMENT_ORDER.PAY_STATUS` 是**第四套**、**按用户 2026-09-14 裁决暂不收口**，详见 ADR-D79）。**收口 MUST 按「哪个字段」分组，NEVER 按「哪个字面量」分组** —— `FareCalculator` 的 `01/02/03` 是 `TRANSFER_FLAG` / `CUMULATIVE_TYPE`，与 `TRX_TYPE` 同形不同义，合并会让改 `TRX_TYPE` 连带改掉钱包减免标记。
- `writer/GateTxnPayWriter.java` — 事务边界与幂等落库（写操作 **MUST** 经此类）
- `mapper/GateTxnPayMapper.java` + `src/main/resources/mapper/GateTxnPayMapper.xml`
- **换乘推送链路**（本模块内独立子链路）：`entity/MetroTransferPushTask.java`、`mapper/MetroTransferPushTaskMapper.java`、`service/impl/MetroTransferPushTaskProcessor.java`、`service/impl/MetroTransferPushClient.java`。**投递的调度自 2.0.73 起不在本模块**：`processReadyTasks` 已摘掉 `@Scheduled`，改由 web-admin `sys_job`「公交换乘推送」cron `0 0/1 * * * ?` 经 `POST /internal/gate-txn-pay/metro-transfer/push` 触发，**轮询周期由 10 秒变成 60 秒**（用户 2026-09-15 裁决，公交卡系统那侧的推送时延上限随之变化）；`wallet.metro-transfer-poll-ms` 已废弃、无读取方。**NEVER 加回 `@Scheduled`**，见 ADR-D80。
- **钱包与优惠**：`service/impl/WalletAppGatewayClient.java`、`entity/DiscountLevel.java` + `mapper/DiscountLevelMapper.java`
- **三个出向 client 都对接外部系统、都继承 `ProxyWebClient`**（2026-09-14 / 2.0.67，ADR-D68）：`WalletAppGatewayClient`（ITP-App 网关）、`MetroTransferPushClient` + `OfflineMetroTransferClient`（`172.20.202.10:8885` 公交卡系统，后两个此前是裸 `WebClient`；**本行此前写 `8980` 是错的，真实端口以 `wallet.metro-transfer-url` / `wallet.metro-transfer-check-url` 为准，NEVER 回退**）。**NEVER 把它们搬进 `rpc` 模块** —— 那是 ITP 内部服务 Client 的位置（参照物是 pay-sign-server 的 `PayGatewayClient`，同样住业务模块）。两个换乘 client 的 3 秒超时靠**带 `responseTimeout` 参数的 `ProxyWebClient` 构造器**传入，**NEVER 改成覆写 `getResponseTimeout()` 读注入字段** —— 那个方法在父类构造期就被调用，那时子类字段还是 0，且编译与单测都发现不了。
- **IF8A-35 账务信息**：`GateTxnPayServiceImpl.requestUserAccInfo` + `GateTxnPayMapper.countUserAccInfo`（一条 SQL 出两个数量，避免同区间扫两遍）；DTO 在 `model` 的 `RequestUserAccInfoReqDTO` / `RequestUserAccInfoResult`；RPC 是 `rpc/.../pay/GateTxnPayClient.requestUserAccInfo`

## 关键业务规则（改动前必须遵守）
- 只处理 `trxType=02 出站` / `03 超时出站`；其余直接返回 **8001「非出站扣费交易」**
- 订单号规则：`GT + yyyyMMddHHmmssSSS + cardId 后 6 位`
- 金额单位为 **分**；`totalAmount = trxAmount + overtimeAmount`
- 日票（`CardTypeCodeEnum.isDailyTicket`）或 `totalAmount <= 0`：直接入库置 SUCCESS，**不调支付**
- 扣款走 pay-sign 免密代扣，失败订单会阻断用户解约（配合 `/hasFailedOrder`）
- **签约信息的唯一合法来源是 `NotifyVerifyResultRespDTO`**，即 ticket-server 的响应体，由 `GateTicketHandler.applyActualCardType` 查 account-server 后写入：`payChannelCode`（= `USER_ITP_REG_INFO.CHANNEL`，fep-dev 用它填 `paymentVendor`）、`requestSignSeq`、`payUserId`。响应体里**NEVER 再加 `paymentVendor` 字段**——它与 `payChannelCode` 同源，两个字段表达同一个值必然漂移。`fep-dev-server` **NEVER** 从自己的 `NotifyVerifyResultReqDTO` 读这几个字段——AGM 不上送、恒为 null；ticket-server 改的是它自己那份跨进程反序列化副本，改不回上游。已发生事故：2026-08-26 这条链路签约信息全程 null，免密扣款只能靠 pay-sign-server 回查 account-server 兜底，且 gate-txn-pay-server 内依赖 `paymentVendor='0B'` 的钱包优惠（`calculateWalletDiscount`）与地铁换乘推送（`shouldPushMetroTransfer`）分支一直不触发。根因还包括 `AccountApplicationServiceImpl.queryCardTypeByCardId` 当时未回填 `channel` / `reqContractNo` / `thirdPayId`（已一并修复）。

## 数据表
`GATE_TXN_PAY`
- Oracle 按 `TXN_DATE` **月分区**，序列 `SEQ_GATE_TXN_PAY`
- DDL：`gate-txn-pay-server/src/main/resources/sql/gate-txn-pay-schema.sql`（2026-09-14 从 `fep-dev-server` 迁入本模块，内容未改、行号仍有效）
- 幂等靠两个本地唯一索引：
  - `UK_GATE_TXN_PAY_ORDER_NO(ORDER_NO, TXN_DATE)`
  - `UK_GATE_TXN_PAY_BIZ(CARD_ID, TRX_TYPE, OUT_TIME, TICKET_TRANS_SEQ, DEVICE_ID, TXN_DATE)`

⚠️ 分区表约束：所有唯一索引 **MUST** 包含分区键 `TXN_DATE`。新增唯一约束时不带 `TXN_DATE` 会建表失败。

⚠️ **`TXN_DATE` 的列类型是 `VARCHAR2(8)`、存 `yyyyMMdd`，NEVER 当日期类型用**（2026-09-14 查 `USER_TAB_COLS.CHAR_LENGTH` 实测 = 8，与 `gate-txn-pay-schema.sql:28` 的 `VARCHAR2(8 CHAR)` 一致）。**本条此前记的「实际是 `VARCHAR2(16)`、DDL 写的是 `DATE`」两句都已作废**：前者是用 `DATA_LENGTH` 判长度（库字符集下 8 字符 = 16 字节，属 `docs/domain/decisions.md` 撤回记录里那个坑），后者是 DDL 早已修正过。因此涉及 `TXN_DATE` 的条件 **MUST** 用 `jdbcType=VARCHAR` 绑定 `yyyyMMdd` 字符串（口径见 `countTransList` / `countUserAccInfo`）。已发生的两次事故（IF8A-35 开发期，均只在联机调用时暴露，编译与单测发现不了）：

1. 传 `LocalDate` + `jdbcType=DATE` → 参数被转成 `'2026-06-08'` 交 Oracle 按会话 `NLS_DATE_FORMAT` 解析 → `ORA-01843 无效的月份`；
2. 改用 `TXN_DATE >= ADD_MONTHS(TRUNC(SYSDATE), ?)` → 变成「VARCHAR2 列 >= DATE」，Oracle 反向把列值 `'20260828'` 按 `NLS_DATE_FORMAT` 解析 → `ORA-01861 文字与格式字符串不匹配`。**该错误只在扫到实际数据行时触发**，没有匹配行的用户反而正常返回 `0000`，极易误判为已修复——验证 **MUST** 用有数据的 `thirdUserId`。

同理 `DEBIT_STATUS`、`THIRD_USER_ID` 也都是 `VARCHAR2`。改本表任何查询前 **MUST** 先核对生产库实际列类型，勿信仓库 DDL。

## 状态字典冲突
`GATE_TXN_PAY.TICKET_STATUS` 注释为"04 进站失败、05 已进站"，与 `QRCodeStatusEnum`（04 已进站、FF 进站失败）**不同源**。
跨表联查或迁移数据时 **MUST** 显式转换，**NEVER** 假设两处状态值可互换。

### `DEBIT_STATUS` 有三套并存口径，勿互相校验
取值 `INIT` / `PROCESSING` / `SUCCESS` / `FAIL` / `RETRY` / `CLOSED`（权威清单只有 DDL 注释 `gate-txn-pay-schema.sql:132`；无枚举类，全是字符串字面量；`CLOSED` 代码中无任何写入点）。

- **解约校验 `countFailedOrder`、黑名单 `countUnsettledOrderByCardId`**：`DEBIT_STATUS IS NULL OR != 'SUCCESS'` 即未结清（黑名单口径**不带渠道也不带时间下限**，覆盖该卡全部历史，只能用于批处理，NEVER 放联机链路）
- **IF8A-35 `countUserAccInfo`**：**白名单分档**——`unpaidCount` 只数 `INIT`/`PROCESSING`，`failureCount` 只数 `FAIL`/`RETRY`；`CLOSED` 与脏数据 `NULL` 两档都不落入，且只统计近 N 个月（`app.acc-info.query-months`，默认 3）

因此 **`unpaidCount + failureCount` ≠ `countFailedOrder` 的结果**，两者口径与时间范围都不同，**NEVER 拿来互相校验或推断对方**。白名单写法顺带避开了 Oracle 三值逻辑：`NULL` 不满足任何 `IN`，自然排除，不需要像 `countFailedOrder` 那样显式补 `IS NULL` 分支。

### 离线码金额待重算态：`DISCOUNT_CALC_STATUS='OFFLINE_FARE_PENDING'`（2026-09-11 新增）

离线码出站（`OFFLINE_FLAG='Y'`）的金额由服务端重算（`calculateOfflineFare`：同序列号首笔进站 → 票价 → 超时费 → 换乘减免 → 钱包折扣），依赖 ticket-server `/ci/app/queryFirstEntryTxn` 与 para-server 票价。**此前任一环节失败会直接返回 `8002` 且不落任何库**，闸机侧仍收到 `0000` 放行，结果是「乘客过闸无感 + APP 查不到订单 + 无任何补偿」（2026-09-11 因 `service.ticket.url` 键缺失实际发生，见 `docs/ops/生产环境清单.md` P1）。

现在改为**落单留痕 + 扫表补偿**：

- 失败时 `saveOfflineFarePendingOrder` 落 `DEBIT_STATUS='INIT'` + `DISCOUNT_CALC_STATUS='OFFLINE_FARE_PENDING'`，金额清零，**对上游仍返回 `8002`**（语义不变）。
- 补偿在本模块 `OfflineFareRecoveryProcessor`（**2.0.73 起已无 `@Scheduled`**，改由 web-admin `sys_job`「离线码金额补偿」cron `0 0/1 * * * ?` 经 `POST /internal/gate-txn-pay/offline-fare/recover` 触发；`-enabled` / `-batch-size` / `-lookback-days` 仍有效，**`gate.pay.offline-fare-recovery-poll-ms` 已废弃、无读取方**），调 `GateTxnPayService.recoverOfflineFarePendingOrders`：重算 → `updateOfflineFareRecalculated`（条件更新，**返回 1 才算抢到，才允许调 pay-sign**）→ `asyncPaySign`。抢占靠条件更新而非分布式锁，**支持多副本**。**NEVER 加回 `@Scheduled`** —— 两套调度源会并发发起扣款；`sys_job` 那行的 `concurrent` MUST 保持 `'1'`（禁止并发）。见 ADR-D80。
- 重建算价上下文用 `rebuildOfflineRequest`，钱包判定靠已持久化的 `PAYMENT_VENDOR`，因此补偿与出站当时口径一致；`requestSignSeq` 无法从订单行恢复，与 `retryPay` 传 `null` 的现状一致。

**三条红线**：
- 这类行 `TOTAL_AMOUNT=0` 但**不是免扣费交易**，**NEVER** 让它走 `requestPay` 里 `totalAmount <= 0` 的默认 SUCCESS 分支——那等于把该收的钱记成 0（资损）。
- 重算再次失败或重算出 0 金额时**只更新 `DISCOUNT_CALC_MSG`、保持 PENDING**，**NEVER** 置 `FAIL`（补偿再也捞不到）也 **NEVER** 置 `SUCCESS`。
- `OFFLINE_FARE_PENDING` 字面量在 `GateTxnPayServiceImpl` 与 `GateTxnPayMapper.xml` 三处条件里成对出现，改动 **MUST** 全局 grep，漏改即补偿静默失效。

留痕仅对**改动生效之后**的失败有效：2026-09-11 14:09 那笔丢单发生在旧代码下，库里没有任何行，补偿任务捞不到，只能重放 `/ci/gateTxnPay/requestPay` 补录。

### 补款单与免密代扣的双路径收款（2026-09-11 修复）

补款（IF8A-26）是**乘客主动支付**，免密代扣（`retryPay` / `requestPay`）是**我方发起**，同一笔欠费同时挂在两条路径上就会收两次钱。**2026-09-11 实测发生过**：卡 `0426090942000095` 的 5 笔离线码欠费在 15:39 被下成补款单 `SP20260911153940558000095`（1500 分，`INIT`），其中 `GT20260911140936000000095` 又在 15:43 被 `retryPay` 免密扣款成功（200 分）。

三处闭环，改动 **MUST** 三处一起看：
- `SupplementOrderServiceImpl.validateOrigOrders` 用**白名单** `SUPPLEMENTABLE_DEBIT_STATUS = INIT / RETRY / FAIL`。**NEVER 退回「非 SUCCESS 即可补款」**——`PROCESSING` 表示代扣在途、回调随时可能成功。
- `GateTxnPayManualOpsService.retryPay`（2.0.71 前在 `GateTxnPayServiceImpl`）前置调 `SupplementOrderService.isCoveredByPendingSupplement`，命中即回 `8001`。该方法**查表异常时返回 true（拒绝）**，与 `isSupplementOrder` 的「异常降级放行」方向刻意相反：那里放行可自愈，这里放行是重复扣款。
- `SupplementOrderCloseProcessor` 把超时的 `INIT` 单关成 `CLOSED`。⚠️ **该类自 2026-09-16 起已不在本模块**：已连同调度整体迁到 `face-pay-server`（`facepay/scheduler/SupplementOrderCloseProcessor.java`），是 face-pay **7 个 `@Scheduled` 中的两个** —— `converge()`（cron `0 */5 * * * ?`，`supplement.scheduler.converge-cron`）与 `closeTimeout()`（cron `0 */10 * * * ?`，`supplement.scheduler.close-cron`）；**本模块 `gate-txn-pay-server` 的 `@Scheduled` 现为 0 个**（2026-09-16 逐模块实测，已排除注释字样），**NEVER 在本模块加回该类或补款调度**。注意分工：**补款单的创建仍在本模块**（IF8A-26 `/requestPayOrder`），**只有收敛与关单的调度在 face-pay**。**这不是可选项**：`countPendingByOrigOrderNos` 把 `INIT` 也算作已覆盖，死单会让它覆盖的原订单**既不能再下补款单、也不能走免密重试**。关单白名单**只有 `INIT`**，`PROCESSING` 在渠道侧已挂待支付单，单方面关单会造成「乘客已付款、订单已关闭」的资损。**关单成功后 MUST 紧跟 `releaseItemActive` 释放独占**（2026-09-14 起），否则那批欠费单仍被 `UK_SUPPLEMENT_ITEM_ACTIVE` 占着、关单等于白做。

### 一键支付重下即作废旧单（2026-09-11 裁决）

用户原话：「一键支付即补款支付每次都需要创建订单，把之前的订单废掉」。因此 IF8A-26 **不再拒绝**「原订单已挂在未终结补款单上」的请求，改为先作废旧单再建新单（`SupplementOrderServiceImpl.revokePreviousOrders`）。**NEVER 退回「存在未终结补款单就拒绝下单」。**

- 顺序 **MUST 是「先 `closeUnpaidByOrderNo` 关 APP 订单行、再 `revokeInitOrder` 废补款单」**。APP 行才是乘客能付钱的入口，先关入口才能保证作废的单收不到钱；颠倒过来会留出「补款单已 CLOSED、收银台仍可付」的窗口。两条都是本地 SQL，与主表/明细同事务。
- 三条拒绝线（**都 MUST 保留**）：①旧单的 APP 行已 `PAY_STATUS='1'`（钱已到）→ 回 `8003 上一笔补款已支付成功，正在确认到账`，交给收敛任务；②旧单不是 `INIT`（如 `PROCESSING`，渠道侧已挂待支付单）→ 拒绝；③`revokeInitOrder` 影响 0 行（并发下状态已变）→ 拒绝让乘客重试。**这三条都是 check-then-act 的快速路径，NEVER 当成并发防线** —— 真正的互斥是第四道：明细的 `UK_SUPPLEMENT_ITEM_ACTIVE`（见下节）。
- **竞态兜底**：作废与乘客付款之间有毫秒级窗口。收敛任务因此额外回看 `supplement.converge-closed-lookback-hours`（默认 48）小时内的 `CLOSED` **与 `FAIL`** 单，一旦发现 APP 行已支付就走 `forceSuccessFromClosedOrFail` **强制销账**（同一条 UPDATE 一并回填渠道凭据）并落 ERROR 日志。**NEVER 把这个窗口设成 0**——那等于收了钱不销账。超时关单、支付失败同样受这条兜底保护。
- 实测（2026-09-11，2.0.59）：①对同 3 笔欠费重下，旧单 `SP20260911183920317135717` → `CLOSED`（`REMARK=乘客重新发起一键支付，本单作废`）、其 APP 行 → `PAY_STATUS='2'`，新单 `SP20260911185203559135717` 建成 `INIT` 且 APP 行 + 前置单齐备；②把旧单 APP 行改成已支付后重下，返回 `8003 上一笔补款已支付成功，正在确认到账，请稍后查看`，未建新单。

### 并发独占：`ACTIVE_ORIG_ORDER_NO` + `UK_SUPPLEMENT_ITEM_ACTIVE`（2026-09-14 修复，代码审查 P0）

**旧实现只有 `revokePreviousOrders` 这一层 check-then-act，没有任何 DB 级互斥**：两条并发 IF8A-26 都查不到旧单 → 都建单成功（`ORDER_NO` 带毫秒 + 卡号后 6 位，撞不上 `UK_SUPPLEMENT_ORDER_NO`），两行 `TBL_TVM_APP_ORDER` 都是 `PAY_STATUS='0'`，**收银台两张单都能付 ⇒ 同一笔欠费收两次钱**。`countPendingByOrigOrderNos` 的 Javadoc 当时写着「用于落单前拦截同一笔欠费被两张补款单同时覆盖」，但 `requestPayOrder` **根本没调它**。

- `SUPPLEMENT_ORDER_ITEM.ACTIVE_ORIG_ORDER_NO` + 唯一索引 `UK_SUPPLEMENT_ITEM_ACTIVE`：落单时该列等于 `ORIG_ORDER_NO`，Oracle 唯一索引不约束全 NULL 行，因此**置 NULL 即释放独占**。撞索引时 `insertItem` 抛完整性冲突 → 沿 cause 链判定（`isIntegrityViolation`，gate-txn-pay 是开了 tracing 的模块、异常会被切面换类型）→ `setRollbackOnly` 整单回滚 → 返 `8003 订单…已在另一笔补款中，请稍后重试`。**NEVER 吞掉继续建单**。
- 释放点**只有「非成功的终态」三处**：重下作废、超时关单、支付失败（`releaseItemActive`）。**`SUCCESS` 刻意不释放** —— 钱已收，无论 `settleOrigOrders` 是否命中，那些原订单都不该再被补款。
- `countPendingByOrigOrderNos` 口径随之改成**直接数 `ACTIVE_ORIG_ORDER_NO` 非空的明细**，不再 JOIN 主表判状态。与旧写法的差别是 **`SUCCESS` 也算独占**：钱已收但收敛可能没命中，放行免密重试会二次扣款。
- DDL 见 `gate-txn-pay-server/src/main/resources/sql/supplement-order-active-orig-migration.sql`，**2026-09-14 已在 `AFCITPDB` 执行并回查**（列 `VARCHAR2(128)`、索引 `UNIQUE/VALID`、回填 7 行且 `COUNT=COUNT(DISTINCT)=7`）。
- 另修两处同批缺陷：**①`selectPendingOrders` 的 `ROWNUM` 原先与 `ORDER BY` 同层** —— Oracle 的 `ROWNUM` 早于 `ORDER BY` 求值，等于「随便取 N 行再排序」，而回看窗口里的 `CLOSED` / `FAIL` 行在 48 小时内**不会退出结果集**，累积超过 `supplement.converge-batch-size`（默认 100）就把 `INIT` / `PROCESSING` 挤出扫描范围、乘客付的钱永不销账；现改为外层内联视图 + 「未终结优先」排序，并补索引 `IDX_SUPPLEMENT_ORDER_UPDT`。**②`FAIL` 原先是收敛盲区** —— 既不在回看窗口、`closeTimeoutOrders` 也只认 `INIT`，且判失败时不关 APP 订单行，于是「补款单 FAIL 而收银台仍可付」；现在 `FAIL` 分支同步 `closeUnpaidByOrderNo` + `releaseItemActive`，且 `FAIL` 纳入回看窗口。
- 回归钉子：`gate-txn-pay-server/src/test/java/.../SupplementOrderServiceTest.java` 六个用例，其中 `pendingScanSqlKeepsRownumOutsideAndPrioritisesOpenOrders` **直接读 mapper XML 断言 SQL 形状**（`ROWNUM` 在 `ORDER BY` 之后、回看含 `FAIL`、排序带状态优先级）—— 这三条是 SQL 语义，不跑真库验证不了行为，**NEVER 删该用例**。


`SP20260911153940558000095` 已于 2026-09-11 人工置 `CLOSED`（该单生成于支付链路尚未实现时、从未发起支付，且覆盖金额已因那笔代扣成功而不符）。回滚：`UPDATE SUPPLEMENT_ORDER SET PAY_STATUS='INIT', REMARK=NULL WHERE ORDER_NO='SP20260911153940558000095'`。

### 补款单的收款通道：调 collect-pay 的 `/internal/app-order/*`（2026-09-14 收回 owner）

**2026-09-14 起本模块不再碰 collect-pay 的任何表**。原实现（2026-09-11 落地）是本模块用自带的 `AppPayOrderMapper` 直插 `TBL_TVM_APP_ORDER` + `TBL_TVM_ORDER_PAY_PRE`，违反 `docs/domain/README.md` 判据三「热路径写入定 owner」；现已改为 **`CollectPayClient` → `POST /internal/app-order/{register,close-unpaid,pay-result}`**，两张表的写入回到唯一 owner collect-pay（`AppPayOrderInternalServiceImpl`）。**`mapper/AppPayOrderMapper.java` 与 `.xml` 已删除，NEVER 加回**。详见 ADR-D64。

- 三条资损口径（前置单 `TRANS_TYPE='03'`、`RSV2` MUST 非空、`ACTIVATE_FLAG='0'`）**现在只在 collect-pay 侧**，本模块 MUST NOT 复制一份：见 `collect-pay-server/.../AppPayOrderInternalService` 的类注释与 `docs/business/tvm-bom-pay.md`。本模块只保留 `APP_ORDER_SUPPLEMENT_FLAG = "SP"` 这一个入参常量（写进 `RSV2`，非空是挡住 collect-pay「购票未取票自动退款」的开关）。
- 乘客侧仍走 collect-pay 原有的 IF8A-11 下单 / `payNotice` / IF8A-18 回查，collect-pay 的对外行为一行未改。
- **一致性方向是「先落本地、后调远端」，与 AGENTS.md §5.2「MUST 先调远端后改本地」相反，这是 2026-09-14 用户裁决、NEVER 按通则改回**。理由是两个方向的代价不对称：远端优先时若本地落库失败，collect-pay 会留一行 `PAY_STATUS='0'` 的可付款孤儿单，乘客付了钱而我方没有补款单去收敛 ⇒ **资损方向**；本地优先的最坏情况只是「乘客暂时付不了」，且能自愈。
- 落地形态是 outbox：`SUPPLEMENT_ORDER.SALE_SYNC_STATUS/RETRY_COUNT/TIME/RESULT` 四列，`insert` 硬编码 `'PENDING'`，同步调 `deliverSaleSync` 成功即 `SUCCESS`；`RpcOutcome.Unreachable` → `markSaleSyncFailed`（+1）留给 `syncPendingSaleOrders` 扫表补推，`BizRejected` → `markSaleSyncRejected`（重试数直接顶到上限）+ ERROR 日志，**两者 NEVER 合并处置**。列形状与扫表 SQL 的四个坑见 `docs/domain/outbox.md`。
- **`localWriter`（`SupplementOrderLocalWriter`）存在的唯一理由是 Spring 事务的代理语义**：`requestPayOrder` 自身不带 `@Transactional`（内有 RPC，见 §5.2 铁律），事务只能靠跨 Bean 调用落在写库那一段。**NEVER 把它内联回 `SupplementOrderServiceImpl`**。
- **2026-09-14（2.0.70）起补款链路是五个类**：`SupplementOrderServiceImpl`（下单校验/构建 / 作废旧单 / 超时关单）+ `SupplementOrderLocalWriter`（事务边界）+ **`SupplementCollectPayGateway`**（对 collect-pay 的三处外呼：登记 / 回查 / 关单；四个报文常量 `TICKET_TYPE='SP'` / `RSV2='SP'` / `DEVICE_ID='GATE_TXN_PAY'` / `TRANS_TYPE='03'` 与「对端答复已付」的 `PAY_STATUS='1'` **全项目只有这一份**，判已付 MUST 走 `isPaid(...)`）+ **`SupplementSaleSyncService`**（outbox：同步投递 + 扫表补偿 + 三分支状态回写）+ **`SupplementConvergeService`**（**收敛 + 判结果 + 销账整块**：`convergePending` / `syncPayStatus` / `settleOrigOrders`）。改报文口径**只改网关那一处**；改 outbox 模板**只改 SaleSync 那一处**；改「补款到账后怎么把原订单改成 SUCCESS」**只改 Converge 那一处**。**NEVER 把这三块搬回 Impl**、**NEVER 在网关里写 mapper**、**NEVER 把 Converge 里的收敛 / 判结果 / 销账再拆成多个类**（它们是同一条资金链路，凭据与状态的写入顺序错了不报错、只丢钱），也 **NEVER 给 Converge 加 `@Transactional`**（链路内有 RPC，且 `convergePending` 对 `syncPayStatus` 是类内自调用，加了等于「有注解没事务」）。详见 ADR-D73、ADR-D76。
- **`found=false` 不等于调用失败**：`CollectPayClient.queryAppPayOrderResult` 网络失败会**抛异常**，`found` 只表达对端的业务答复。判「上一笔是否已支付」MUST 先接住异常并拒绝作废（否则可能把已付款的单子作废）。
- **收款后 collect-pay 不会通知本模块**，收口只有一条路：`SupplementOrderServiceImpl.convergePendingOrders`（2.0.70 起只是一行委派，真正实现在 `SupplementConvergeService.convergePending`）+ `SupplementOrderCloseProcessor.convergePaidSupplementOrders`（`supplement.converge-*`，默认 30s）扫 `INIT` / `PROCESSING` 补款单、经 `/internal/app-order/pay-result` 回查 APP 订单行，`PAY_STATUS='1'` 即回填渠道信息并把补款单与原订单收敛为 `SUCCESS`。**NEVER 关掉这个任务**，关掉等于乘客付了钱而欠费不清。`selectPendingOrders` **MUST 包含 `INIT`**——收银台流程下我方无从得知支付时点。
- 已知不精确：collect-pay 组装支付报文时把商品名硬编码为「APP单程票购票」/「地铁单程票」，补款单在收银台会显示成单程票。属现状，重构时一并改。
- **三个 `/internal/app-order/*` 端点当前无鉴权**（与本模块 IF8A-26、collect-pay 的 `/internal/recon` 同款临时降级，2026-09-14 用户裁决）。与 AGENTS.md §5.2 冲突，**上线前 MUST 补**。
- **端到端实测（2026-09-14，测试环境，`gate-txn-pay-server` 2.0.66 + `collect-pay-server` 1.1.83）**：三个端点 8/8 行为用例通过——`register` 幂等重入、`found` 语义（未建单返 `found=false` 而非报错）、`close-unpaid` 的 `PAY_STATUS 0→2` 白名单、重复关单 0 行按成功处理、`supplementFlag` 缺失时拒绝返 `8001`。回滚 tag：2.0.65 / 1.1.82。
- **端到端实测（2026-09-11，测试环境，直写时代，保留作背景）**：
  - 第一轮 `gate-txn-pay-server` 2.0.57，`SP20260911180915493000095`（200 分，覆盖 `GT20260911141953000000095`）：手工把 APP 订单行改 `PAY_STATUS='1'` 模拟收银台支付，32 秒内收敛完成。
  - 第二轮 2.0.58，`SP20260911183359768000095`（200 分，覆盖 `GT20260911142226000000095`）：**走 collect-pay 真实接口**——`/ci/app/requestPaymentInfo`（IF8A-11）返 `0000` 并回写 `REQUEST_PAY_FLAG='1'`，再 POST `/itptvm/ci/tvm/payNotice` 模拟支付中心成功回调（该接口无验签，`bizData` 是明文 JSON），collect-pay 自己把 `PAY_STATUS` 翻成 `'1'`、`MSG='支付成功'`，40 秒内本模块收敛出 `SUPPLEMENT_ORDER=SUCCESS` / `SUPPLEMENT_ORDER_ITEM=SETTLED` / `GATE_TXN_PAY=SUCCESS`。
  - 环境事实：测试环境支付中心（`dtcustomer.bestonepay.com/.../v1/payment/requestPay`）对所有订单都返 `code=600 操作失败`（同一时段 BOM 单也一样），所以 `PAYMENTINFO` / `MERCHANTORDERNO` 恒为空，**这不是 SP 单特有的问题**；IF8A-18 在订单未支付时正确返 `8999 支付中`。

## 编码约束
- 新增写路径 **MUST** 走 `GateTxnPayWriter` 并依赖唯一索引 + `DuplicateKeyException` 兜底，**NEVER** 引入 Redis 锁
- **IF8A-26 目前没有身份认证**（2026-09-14 用户裁决：与 recon 的 `X-Recon-Token` 降级同款处理，本轮不接验签、记入本文件与上线核对清单）。`thirdUserId` 由客户端上送，`validateOrigOrders` 只做「列表内各单归属彼此一致」的自洽校验，**不是认证**：拿到别人的 `GATE_TXN_PAY.ORDER_NO` 即可给他人欠费建单，并经 `isCoveredByPendingSupplement` 阻断对方的免密扣款重试。这与 AGENTS.md §5.2「新增状态变更型接口 MUST 有鉴权与归属校验」冲突，**上线前 MUST 接入 `ItpRequestSignVerifier` 或同款验签，NEVER 自造签名逻辑**。
- **两个配置开关按 2026-09-14 裁决保持现状，改前 MUST 复核风险**：`supplement.strict-amount-check` 置 false 会跳过「`price` 与原订单合计相等」的校验（落库与收银台收的是 `sumAmount`，两者不符时乘客付的钱与展示金额不一致）；`supplement.max-order-count` 默认 200，意味着 `settleOrigOrders` 最多在请求线程上串行跑 400 条 SQL，在虚拟线程 + ojdbc8 `synchronized` 下有 pin 风险，要缓解 **MUST** 调小它或把收敛整体交给扫表任务，**NEVER** 在 `settleOrigOrders` 里加事务
- 涉及扣款金额与免密代扣的改动 **MUST** 提示人工复核资金安全
- 相关迁移脚本参考 `scripts/20260821_pay_txn_detail_data_migration.sql`
- **联机查询 MUST 带 `TXN_DATE` 下限**：不带就要扫全部月分区，而 `spring.threads.virtual.enabled=true` 下一条慢 SQL 会 pin 住载体线程（见 AGENTS.md §5.2）。`countUnsettledOrderByCardId` 是唯一例外，它是每天两次的批处理，**NEVER** 把它挪到联机链路
- **只读统计接口的兜底方向**：查询未执行时（参数缺失、异常）`retCode` 返回非 `0000`、数量给 0，调用方 **MUST** 先判 `retCode`；这与 `hasFailedOrder` / `hasUnsettledOrderByCard` 的「查不到就返回 true」相反——那两个是放行判定（宁可拦住），IF8A-35 只用于 APP 展示，**NEVER** 拿它替代过闸或解约链路各自的欠费校验
- **`GATE_TXN_PAY` 两个中文站名列的 owner 在本模块，谁改编码谁改名**（2026-09-15 / ADR-D83）。`ENTRY_STATION_NAME` / `EXIT_STATION_NAME` 由 `station/StationNameBackfiller.backfill(order)` 按**落库前最终的** `IN_STATION` / `OUT_STATION` 回填，取数走 `FareDataGateway.resolveStationNamesQuietly`（复用本模块已有的 `@EnableRpcPara` / `service.para.url`，没有新增接线）。**两个调用点 MUST 同批看齐**：`GateTxnPayServiceImpl.requestPay` 算价之后、`OfflineFareRecoveryServiceImpl` 重算之后且 `applyOfflineFareRecalculated` 之前；`updateOfflineFareRecalculated` 的 SET 里 **MUST** 含这两列。**NEVER 把回填挪到算价之前**——离线码的进站码是 `FareCalculator` 事后按 `cardId + ticketTransSeq` 重查覆盖的，早于它就是拿占位 `FFFF` 或上一趟行程的站码去查名。**NEVER 把查不到的站名写成空串或 null**（保留上游值即可，覆盖成空会让列表退回显示站点编码）。ticket-server 的 `GateTxnPayRequestAssembler.fillStationNames` **保留作兜底、NEVER 删**。
- **站名现在有两条路、读的却不是同一张表 —— 未裁决，NEVER 当成已收口**（2026-09-15）。写入侧是上一条的 `StationNameBackfiller`，经 para-server 读 **`TBL_STATION_INFO`**（带 `PARA_VER_NO` 版本维度，`para-server/src/main/resources/mapper/AppParaQueryMapper.xml:78`）；读出侧是 `GateTxnPayQueryServiceImpl.fillMissingStationNames`（r857），**本模块直查 `STATION_INFO`**（`GateTxnPayMapper.selectStationNames`），**只补内存里的展示值、不落库**，作用是让历史空站名行在运营列表页也能显示。两者职责互补（一个管新单落库、一个管历史行展示），**但 `AFCITPDB` 里 `STATION_INFO` 与 `TBL_STATION_INFO` 两张表都存在**（`USER_TABLES` 实测），同一个站码在两条路上**可能给出不同的中文名，且没有任何断言能发现**。要收口 **MUST 先定「谁是站名权威」**；另外「本模块直读 para 域的表」与 `docs/domain` 的 owner 判据冲突，同属待裁决项。

## 参考原始文档
- `docs/接口规范文档/青岛地铁-ITP与APP接口规范R6.docx`（IF8A-05、IF8A-26、IF8A-34、IF8A-35 §3.39）
- `docs/技术规范文档/城市轨道交通自动售检票系统技术规范-第9部分-互联网业务规范.docx`

⚠️ IF8A-35 在规格里位于 **§3.39**（不是 §3.35）。按章节号找接口会串位——`IF8A-26` 才在 §3.35。定位 **MUST** 以接口编号为准。

## 附：gate-txn-pay-server 源码注释知识抽取（2026-09-16，阶段一）

抽取范围：`gate-txn-pay-server/src/main/java/**/*.java`（45 个文件、约 5645 行）与 `gate-txn-pay-server/src/main/resources/mapper/*.xml`（4 个文件）的全部注释。本阶段**只读代码、不改任何源文件**。

归类口径：①契约与判据 ②决策理由 ③陷阱 —— 三类进正文；④墓碑注释单列文末一节、不进正文。复述方法名/参数名的普通 Javadoc、`{@inheritDoc}`、空 Javadoc 已丢弃。每条带定位串（`gate-txn-pay-server` + `类名.方法名` + `文件相对路径:行号`），所有 MUST / NEVER 原文保留。

### 一、出站扣费主链路

**【契约】`DEBIT_STATUS` 只有五个取值，`DebitStatus` 是唯一来源**（`gate-txn-pay-server` / `DebitStatus`，`gate-txn-pay-server/src/main/java/com/chinasofti/huateng/gatetxnpay/constant/DebitStatus.java:21~31`）：`INIT` 已落单尚未发起扣款（含离线码待重算态）、`PROCESSING` 已向支付域发起扣款等回调、`RETRY` 发起失败或无响应留给补偿重试、`SUCCESS` 终态已收到钱（免扣费与日票交易落单即此态）、`FAIL` 终态扣款失败。入库与比较 **MUST** 用 `code()` / `is(String)`，**NEVER** 用 `name()`：虽然当前两者字面相同，但 `name()` 一旦被重命名就会静默改掉落库值（`DebitStatus.java:17~19`、`:39`）。

**【契约】两条人工干预白名单**（`DebitStatus.isRetryable` / `isRefundable`，`constant/DebitStatus.java:54` / `:64`）：可重试 = `RETRY` 或 `INIT`；可退款 = `SUCCESS` 或 `PROCESSING`。`PROCESSING` 在退款白名单内是有意的 —— 支付中心可能已扣款成功但回调还没到，这种在途单也要能退（`:61~62`）。白名单而非黑名单是 AGENTS.md §5.2 的硬规则：写成「非终态即可重试」会把 `PROCESSING`（扣款在途）也放进来，等于对同一笔欠费同时开两条扣款路径（`:51~52`）。

**【契约】`DEBIT_STATUS` 与另两套状态词汇同形不同义，NEVER 混用**（`DebitStatus.java:6~10`、`DiscountCalcStatus`，`constant/DiscountCalcStatus.java:4~11`）。`DISCOUNT_CALC_STATUS` 取值是 `SUCCESS / SKIPPED / FALLBACK / OFFLINE_FARE_PENDING`，与 `DEBIT_STATUS` 只有 `SUCCESS` 同形；`SUPPLEMENT_ORDER.PAY_STATUS` 是另一台状态机（见 `docs/domain/state-machines.md`），**尚未收口**、仍是裸字面量（按用户 2026-09-14 裁决暂不动）。两列同形不同义，混用等于把两台状态机的白名单接到一起。一笔订单可以 `DISCOUNT_CALC_STATUS='FALLBACK'` 同时 `DEBIT_STATUS='SUCCESS'`。

**【契约】出站类交易白名单**（`GateTxnPayFieldCode.isExitTrxType`，`constant/GateTxnPayFieldCode.java:46`）：`TRX_TYPE ∈ {02, 03}`（02 出站普通、03 出站超时/特殊，两者都进扣费链路）。判定 **MUST** 用白名单而非「非进站即出站」：报文里还会出现其它交易类型，放行等于给非出站交易也扣一次钱（`:43~44`）。进站交易只更新票卡状态、不进入扣款链路（`GateTxnPayServiceImpl.validate`，`service/impl/GateTxnPayServiceImpl.java:246`）。

**【契约】`retCode` 五个值是对外契约，NEVER 改值**（`GateTxnPayRetCode`，`constant/GateTxnPayRetCode.java:11~12`）：上游按这几个码分支处理，改一个字面量等于悄悄改协议；本类只让「同一个码只有一处定义」，**不是**给它们改名或加新码的入口。`SUCCESS`（`0000`）**同时用于两个方向** —— 应答上游用它、判断下游（pay-sign / 支付宝 / 票价 / 公交换乘）答复是否成功用的也是它，全链路共用同一套码表；**但如果哪天某个下游改用别的成功码，MUST 为那个下游单独加一个常量、NEVER 改本常量的值**（`:14~17`）。

**【契约】支付结果回调只认 `SUCCESS`**（`GateTxnPayServiceImpl.syncDebitStatus`，`service/impl/GateTxnPayServiceImpl.java:357~358`）：只认 `SUCCESS` 为成功，其余一切支付状态（`FAIL` / `CLOSED` / 未知值）都收敛为 `FAIL`。**NEVER 反过来写成「非 FAIL 即成功」——未知状态被当成扣款成功等于放弃这笔应收。** 该端点只改状态、`NEVER` 触发扣款，`SUCCESS` / `FAIL` 终态订单不会被改写（`GateTxnPayController.syncDebitStatus`，`controller/GateTxnPayController.java:108`；`GateTxnPayService.syncDebitStatus`，`service/GateTxnPayService.java:48`）。

**【契约】出账口的状态收敛只有一条规则**（`PaySignInitiator`，`paysign/PaySignInitiator.java:28~30`）：`retCode=0000` 才 `PROCESSING`，其余一切（非 0000 / null / 抛异常）一律 `RETRY` 留给补偿。**NEVER** 把「没抛异常」当成扣款成功（AGENTS.md §5.2「返回 boolean 的 RPC 包装方法」同型陷阱）。渠道分派判据取**订单上的** `ISSUE_CHANNEL_CODE`（`07` 支付宝出行走 alipay-pay-sign，其余走 pay-sign）而不是入向 request —— `retryPay` 与离线补偿都没有 request，只能靠订单快照；两条路必须选出同一个渠道，否则重试会打到另一家（`PaySignInitiator.requestPay`，`paysign/PaySignInitiator.java:119~122`）。

**【契约】`COUNTING_TIMES` / `COUNTING_FLAG` MUST 入库即有值、NEVER 留 NULL**（用户 2026-09-10 裁定，`GateTxnPayServiceImpl.buildOrder`，`service/impl/GateTxnPayServiceImpl.java:308~313`）：`COUNTING_TIMES` 非日票恒 0、日票恒 1（本次行程消耗次数，**NEVER 是剩余次数**）；`COUNTING_FLAG` `Y`=日票（记期票+计次票）、`N`=非日票。正常链路由 ticket-server `GateTicketHandler` 透传，这里再按 `CARD_TYPE` 兜一次，覆盖上游未升级镜像、补录、离线补单等入口。**兜底 MUST 放在入库前而不是查询出口** —— 出口补值只能骗过 APP，库里仍是 NULL，报表与对账照样缺。

**【契约】免扣费与日票交易落单即 SUCCESS，且 MUST 显式写 `EXPECTED_GATE_AMOUNT=0`**（`GateTxnPayServiceImpl.requestPay`，`service/impl/GateTxnPayServiceImpl.java:140~141`）：供 APP 扣费详情展示「原价 X / 已省 X / 实付 0」，**NEVER 留 null** —— 留 null 时 APP 拿不到任何金额，扣费详情只能显示 0（2026-09-10 修复）。

**【契约】IF8A-35 与另两个欠费口径互不相等，NEVER 互相校验**（`GateTxnPayMapper.countUserAccInfo`，`mapper/GateTxnPayMapper.java:253~256`；`resources/mapper/GateTxnPayMapper.xml:585~592`）：`unpaidCount` 只数 `INIT` / `PROCESSING`，`failureCount` 只数 `FAIL` / `RETRY`；`CLOSED` 与脏数据 `NULL` 两档都不落入，因此两数之和 ≠ `countFailedOrder` 的「非 SUCCESS」总数，三者口径不同。白名单写法同时避免了 Oracle 三值逻辑坑（NULL 不满足任何 `IN`，自然被排除）。

**【契约】只读统计接口与放行判定的兜底方向相反**（`GateTxnPayQueryServiceImpl`，`service/impl/GateTxnPayQueryServiceImpl.java:191~192`、`:213~214`、`:418~421`）：`hasFailedOrder` / `hasUnsettledOrderByCard` 在参数缺失时 **NEVER 返回 false**，而是给错误码 + 标志置 `true`（调用方是解约流程 / 黑名单可解除性盘点，会把 false 当成「无欠费 / 已结清」而放行）；IF8A-35 查询未执行时 `retCode` 返非 `0000`、数量给 0，调用方 **MUST 先判 retCode**，**NEVER** 把 0/0 当成「该用户无欠费」。

**【决策】出账口 MUST 只有一处**（`PaySignInitiator`，`paysign/PaySignInitiator.java:21~26`、`service/impl/GateTxnPayServiceImpl.java:48~50`）：三条链路（`requestPay` 出站首次异步、`retryPay` 运营重试同步、离线码补偿抢占后同步）共用本类。**NEVER** 在任何调用方再拼一份 `GatePayRequestDTO` —— 报文里 `TXN_DATE` 与金额两项各有一条踩过的坑，复制一份就是复制两个缺陷。

**【决策】两个渠道的报文工厂拆成两个类，NEVER 合并成一组配置**（`GatePayRequestFactory`，`paysign/GatePayRequestFactory.java:14~17`；`AlipayTripPayRequestFactory`，`paysign/AlipayTripPayRequestFactory.java:11~15`）：拆的判据不是参数多，而是**两套配置的单位与语义不同却名字相似** —— pay-sign 的 `orderTimeOut` 是**秒**、支付宝的是**分钟**；支付宝的 `requestSignSeq` 取 `TICKET_TRANS_SEQ`（按票卡流水号找协议）、`notifyUrl` 必须显式带上（扣费结果只回调到这个地址）。混在同一串构造参数里时写错一个不报错、只在对端超时行为上表现出来（`PaySignInitiator.java:51~56`）。

**【决策】运营人工干预（重试 / 退款）与出站扣费分成两个类，理由是变化理由不同**（`GateTxnPayManualOpsService`，`service/impl/GateTxnPayManualOpsService.java:28~32`）：出站扣费跟着闸机报文（IF1A-01）与算价规则变，本类跟着运营流程与支付中心退款接口变，唯一共同点是读同一张表。合在一处时 `GateTxnPayServiceImpl` 因为退款而必须持有 `PaySignClient`，于是那个类同时握着「出账口」「退款口」两个出向 RPC；**拆开后 `GateTxnPayServiceImpl` 不再直接持有任何 rpc client**。退款只发起、不改本地 `DEBIT_STATUS`，**NEVER** 在这里顺手把订单改成某个「已退款」状态 —— 那个状态在 `DebitStatus` 里不存在，写进去等于给状态机加了一个没人认识的值（`GateTxnPayManualOpsService.requestRefund`，`:88~90`）。

**【决策】读写拆成两个 Service，分界线是「有没有写」**（`GateTxnPayQueryService`，`service/GateTxnPayQueryService.java:23~29`；`GateTxnPayService`，`service/GateTxnPayService.java:17~22`）：**NEVER 往查询接口加带写入或 RPC 的方法** —— 那条线一破，「查询侧可以随便重试、不必考虑幂等」这个前提就不再成立，而调用方（web-admin 分页、ticket-server、blacklist-server、pay-sign-server 解约校验）都是按只读语义在用它。**NEVER 往 `GateTxnPayQueryServiceImpl` 注入 writer / client 类协作者**，一旦注入就说明方法放错了地方，MUST 回到 `GateTxnPayServiceImpl`（`service/impl/GateTxnPayQueryServiceImpl.java:40~42`）。拆分只动 Java 类型，**HTTP 端点、URL 与报文一个都没变**。

**【决策】算价与取数拆成 `FareCalculator` + `FareDataGateway`（ADR-D69，2026-09-14）**（`fare/FareCalculator.java:27~30`、`fare/FareDataGateway.java:35~44`）：拆的理由不是行数，而是 `FareCalculator` 注入了 6 个协作者、其中 5 个是跨服务/跨系统调用 —— 一个「算钱」的类实际在做编排。**取数一律经 gateway，NEVER 在 `FareCalculator` 里再注入任何 Client 或 Mapper**；反过来**判定与措辞 MUST 留在 `FareCalculator`**，gateway 只返回「拿到的东西或 null」、**NEVER 在这里抛业务异常** —— 两条算价路径对同一个查询失败的措辞与后果都不同（一条降级、一条中断），在那里抛就把差异抹平了。

**【决策】三个字段级判定收口在 `GateTxnPayFieldCode`，但 NEVER 把同形常量搬进来**（`constant/GateTxnPayFieldCode.java:8~21`）：只收 `TRX_TYPE ∈ {02,03}`、`PAYMENT_VENDOR='0B'`、`COMPANION_FLAG ∈ {Y,C}` 这三个，因为只有它们是同一个字段上的同一个判定被抄了两遍以上；抄两份的后果是「加新出站码 / 新钱包渠道码时改一处、漏一处」，漏掉那处不报错 —— 表现为该笔既不算折扣也不推公交换乘，事后只能靠对账发现。**NEVER 把 `FareCalculator` 里的 `01`/`02`/`03` 搬进本类**：那些是 `TRANSFER_FLAG`（01 未减免 / 02 已减免）与 `CUMULATIVE_TYPE`（01/02/03 累计口径），与 `TRX_TYPE` **同形不同义**，一旦合并，日后改 `TRX_TYPE` 取值会连带改掉钱包折扣的减免标记。同理 **NEVER** 把 `COUNTING_FLAG` 的 `Y/N` 与 `COMPANION_FLAG` 的 `Y/C` 合并。

**【决策】本类 NEVER 加 `@Transactional`（三处同源）**：`PaySignInitiator`（内部就是一次 RPC，`paysign/PaySignInitiator.java:32~34`）、`GateTxnPayManualOpsService`（退款分支内有支付中心 RPC，`service/impl/GateTxnPayManualOpsService.java:38~39`）、`OriginalFareBackfillServiceImpl`（循环内调 para-server，`service/impl/OriginalFareBackfillServiceImpl.java:24`、`:41~43`）。理由都是「事务包住网络调用已经出过生产事故（AGENTS.md §5.2 的行锁放大）」。`batchRefundOvertime` 同样含支付中心 RPC，**NEVER 加事务**（`service/GateTxnPayService.java:41`）。

**【决策】`ORIGINAL_FARE` 与钱包折扣解耦**（`GateTxnPayServiceImpl.requestPay`，`service/impl/GateTxnPayServiceImpl.java:113~117`；`FareCalculator.fillOriginalFare`，`fare/FareCalculator.java:70~77`）：`ORIGINAL_FARE` 是「本次行程的地铁原价」，与是否扣费、走哪个支付渠道无关，APP 扣费详情要靠它算「已省金额」。原先它只在 `calculateWalletDiscount` 的「`paymentVendor=0B` 且参与钱包累计」分支里赋值，于是日票 / 员工票 / 非钱包渠道全都拿不到 `ORIGINAL_FARE` 与 `EXPECTED_GATE_AMOUNT`（2026-09-10 定位：日票扣费详情全 null）。因此 **NEVER 把它放进钱包折扣计算的 if 里**。`fillOriginalFare` 自己吞异常，**NEVER 因票价查不到而影响出站放行**。

**【决策】两个「查票价」方法刻意成对存在，NEVER 合成一个**（`FareDataGateway.queryTicketPrice` / `queryTicketPriceQuietly`，`fare/FareDataGateway.java:74~78` / `:91~97`）：一个异常照原样抛出（给「查不到就中断本笔」的钱包补查、离线码重算用），一个吞掉所有异常只记日志（给「查不到只是展示降级、出站 MUST 放行」的 `ORIGINAL_FARE` 填充与历史补数用）。**吞不吞异常正是那两条路径唯一的区别，合一之后必有一条被改错，且编译与单测都发现不了。**

**【决策】站名的 owner 必须是本模块，谁改编码谁改名**（`StationNameBackfiller`，`station/StationNameBackfiller.java:17~27`）：站名原本唯一写入源是 ticket-server 的 `GateTxnPayRequestAssembler.fillStationNames`，它按 `lastHandleStationCode` 解析，而那个值在 `GateTicketHandler` 里被无条件覆盖成 `QRCODE_STATUS.LAST_TXN_STATION` —— 离线码进站报文没上传时它还是开卡占位值 `FFFF`（查不到站名），或者是**上一趟行程**的出站码（能查到，但是错的名）；随后 `FareCalculator` 才按 `cardId + ticketTransSeq` 重查首笔进站交易、覆盖 `IN_STATION`。**于是编码与站名不同源、时序还相反**，这就是「站名显示成 0622」的成因。ticket-server 那份 `fillStationNames` 保留作兜底，**NEVER 因为本类存在就把它删掉** —— 它覆盖的是本模块拿不到 para 应答时的降级路径。**NEVER 把查不到的站名写成空串或 null**：查不到就保留入参已有的值，覆盖成空等于把上游已填对的站名擦掉，比不回填更糟（`:29~30`；`FareDataGateway.resolveStationNamesQuietly`，`fare/FareDataGateway.java:161~164`：查不到某个码时该键**不出现在返回值里**而不是映射到 null，实测能查不到的真实取值是占位站码 `FFFF`）。

**【陷阱】事务内 RPC 的生产事故（2026-08-26 287 秒那次）**：`GateTxnPayServiceImpl` 的 `convergeDebitStatusForSupplement` 注释明确写「本方法**不带事务**、也**不调任何远端**」（`service/impl/GateTxnPayServiceImpl.java:394~395`），`PaySignInitiator` / `GateTxnPayManualOpsService` / `OriginalFareBackfillServiceImpl` / `OfflineFareRecoveryServiceImpl` 四处的类注释都指向同一条约束（分别在 `paysign/PaySignInitiator.java:32~33`、`service/impl/GateTxnPayManualOpsService.java:38~39`、`service/OriginalFareBackfillService.java:18~20`、`service/impl/OfflineFareRecoveryServiceImpl.java:35`）。`OriginalFareBackfillService` 的原话：「方法内要逐笔调 para-server，事务包住 RPC 会把行锁持有时长拉到对端响应时长，**是本项目已发生过的生产事故形态**。逐笔单条 UPDATE 自动提交，中途失败不影响已回填的行。」事故对端即 `syncDebitStatus`（`PaySignWorkflow.receivePayResult` 事务内调它，订单 `GT20260826210647653586419` 循环重推 8 分钟、单次请求 287233ms、`UPDATE` 等锁 44997ms，`PAY_CALLBACK_LOG` 零条落库）。

**【陷阱】站名回填的时序不能反**（`GateTxnPayServiceImpl.requestPay`，`service/impl/GateTxnPayServiceImpl.java:130~132`）：站名回填 **MUST 在算价之后** —— 离线码路径的 `FareCalculator` 会按 `cardId + ticketTransSeq` 重查首笔进站交易并覆盖 `IN_STATION`，放到算价之前等于拿旧编码（可能是占位 `FFFF` 或上一趟行程的站码）去查名，白做且会写进错的中文名。补偿链路同理，**MUST 夹在「重算已把 `IN_STATION` 改对」与「抢占写库」之间**（`OfflineFareRecoveryServiceImpl.recoverSingleOfflineFareOrder`，`service/impl/OfflineFareRecoveryServiceImpl.java:124~125`）。

**【陷阱】`industryDetail` NEVER 重算**（`GateTxnPayServiceImpl.buildOrder`，`service/impl/GateTxnPayServiceImpl.java:293~296`；`AlipayTripPayRequestFactory`，`paysign/AlipayTripPayRequestFactory.java:21~24`；`GateTxnPayQueryServiceImpl.toListDTO`，`service/impl/GateTxnPayQueryServiceImpl.java:349~350`）：支付宝出行行业明细由 fep-dev-server 在出站时整块组好透传，本服务只存不算。那 21 键里有 9 个（进出站线路码/名称、进站设备号、`entryId`/`exitId`、`cardNum`、`cardIssueCode`）在 `GATE_TXN_PAY` **没有对应列**，只有出站那一刻的三个并行 RPC 拿得到；用订单快照顶替会得到一份**键名完全不同**的 JSON，支付宝侧解析不出行程、扣费直接失败。查询侧也 **NEVER 重算**（那两个键只有出站那一刻拿得到）。注意 `GatePayRequestFactory` 那个「行业明细」是订单快照、**与支付宝那条不是一回事**，**NEVER 拿它顶替**（`paysign/GatePayRequestFactory.java:90~92`）。

**【陷阱】两条钱包算价路径口径不同，NEVER 擅自合并**（`FareCalculator`，`fare/FareCalculator.java:31~42`）：`calculateOfflineFare` 折扣基数是**换乘减免后**的票价，公式 `(票价 - 减免) * 折扣率`；`calculateWalletDiscount` 折扣基数是 `原价 - 1`、**不减换乘**，且 `TRANSFER_FLAG` 是拿算出来的期望值与闸机上报的 `TRX_AMOUNT` 比较反推的。那个 `-1` 与「减不减换乘」的差异**是搬迁前就存在的**，是业务规则还是历史遗留尚未裁决，历次搬迁均逐字保留；要动 **MUST 先与业务确认**，并同步改 `OfflineFareCalculationTest` 的期望值。

**【陷阱】钱包累计查询的 `extend1`/`extend2` 差异是对外报文差异**（`FareDataGateway.queryWalletTotalAmt`，`fare/FareDataGateway.java:134~137`）：在线钱包路径额外把 `extend1` / `extend2` 置空串，离线码路径不置。**这个差异是搬迁前就存在的、影响出向报文**，因此原样保留成一个开关，**NEVER 图省事统一成一种** —— 改它等于改对外报文，MUST 先与对端确认。

**【陷阱】`0000` 之外的成功码风险与 4 个私有 `RET_SUCCESS` 副本**（`constant/GateTxnPayRetCode.java:6~9`）：五个 retCode 此前在 8 个类里各写一份裸字面量，其中四个类还各自定义了同名私有常量 `RET_SUCCESS="0000"`。收口后仍保留的**有意副本**：`trimToNull` / `truncate` 三行私有方法在 `GateTxnPayServiceImpl` / `GateTxnPayQueryServiceImpl` / `FareCalculator` / `OriginalFareBackfillServiceImpl` 各一份，理由是项目规则禁止为此新建工具类（`service/impl/GateTxnPayQueryServiceImpl.java:44~45`、`fare/FareCalculator.java:44~46`、`service/impl/OriginalFareBackfillServiceImpl.java:140`）。

### 二、补款收敛

**【契约】补款收敛白名单含 `FAIL`，与支付回调收敛并存**（`GateTxnPayMapper.convergeDebitStatusForSupplement`，`mapper/GateTxnPayMapper.java:64~75`；SQL 在 `resources/mapper/GateTxnPayMapper.xml:194~215`）：允许 `INIT` / `PROCESSING` / `RETRY` / **`FAIL`** 更新为 `SUCCESS`。**与 `convergeDebitStatus` 并存、白名单多一个 `FAIL`，这是有意的**：补款下单校验放行的「欠费可补」口径包含 FAIL 单，能下单就必须能收敛，否则会出现「放行下单 + 补款支付成功 + 收敛不了」——钱已实收而行程仍挂欠费。目标状态**写死 `SUCCESS`**（不做入参）：补款成功是唯一走到这里的场景，少一个入参就少一处「传错状态把订单改成 FAIL」的可能。

**【契约】支付回调收敛的白名单是三个中间态**（`GateTxnPayMapper.convergeDebitStatus`，`mapper/GateTxnPayMapper.java:52~56`；SQL 在 `resources/mapper/GateTxnPayMapper.xml:177~192`）：仅允许 `INIT` / `PROCESSING` / `RETRY` 更新为 `SUCCESS` / `FAIL`，`SUCCESS` / `FAIL` 不在白名单内、重复回调不改写终态。不能只用 `updateStatusIfProcessing`（只覆盖 `PROCESSING`）：调 pay-sign 的同步响应失败时订单停在 `RETRY`、异步线程未及推进时停在 `INIT`，而支付平台侧仍可能扣款成功并发来回调，这两个状态被漏掉就会永久停在中间态（**2026-08-26 生产实测各出现 1 笔**）。

**【契约】补款收敛响应的三分支判据 = `retCode` + `converged` + `debitStatus`**（`CompensationInternalController.convergeDebitStatusForSupplement`，`controller/internal/CompensationInternalController.java:85~87`；`GateTxnPayService.convergeDebitStatusForSupplement`，`service/GateTxnPayService.java:56~63`；实现在 `service/impl/GateTxnPayServiceImpl.java:390~451`）：调用方要靠三者分出「已结清 / 重复支付待退款 / 需人工核对」。实际三支是 ——
- 改到行（`updated > 0`）：`retCode=0000`、`converged=true`、`debitStatus=SUCCESS`（`GateTxnPayServiceImpl.java:425~432`）；
- 0 行且原订单已是 `SUCCESS`：`retCode=0000`、`converged=false`、`retMsg=「原订单已结清，本次未改动」`，语义是**疑似已被先到的补款单结清 ⇒ 本单重复支付待退款**（`:439~443`）；
- 0 行且状态不在白名单：`retCode=STATUS_REJECT`、`converged=false`、回填当前 `debitStatus`，语义是**需人工核对**（`:444~449`）。
「0 行：**MUST 回填当前状态**，调用方靠它区分『已被先到的补款单收敛』与『状态不在白名单』。前者是重复支付待退款、后者是需人工核对，**两者都不该再重试**」（`:435~436`）。

**【契约】`syncDebitStatus` 的 0 行处置与补款收敛不同**（`GateTxnPayServiceImpl.syncDebitStatus`，`service/impl/GateTxnPayServiceImpl.java:363~378`）：0 行有两种含义 —— 订单已是终态（重复回调，正常）或状态值意外。**已是同一终态即视为幂等成功返 `0000`**，其余情形回非 `0000` 让调用方留痕。正因为它把「本次真改了行」与「早已被别人收敛」压成同一结果，补款链路才**不能复用它**（`service/GateTxnPayService.java:56~61`）。

**【决策】两条 converge 语句 NEVER 合并**（`mapper/GateTxnPayMapper.java:66~70`、`resources/mapper/GateTxnPayMapper.xml:198~205`、`writer/GateTxnPayWriter.java:107~110`、`service/GateTxnPayService.java:61`）：`convergeDebitStatus` 服务的是**支付结果回调**，语义是「中间态 到 终态」，在那条链路里 `FAIL` 是已到达的终态、不该被回调改写；`convergeDebitStatusForSupplement` 服务的是**补款支付成功**，必须能收 `FAIL` 单。原文：「**两条的白名单本就该不同，NEVER 合并成一条。**」「改任一条 MUST 想清楚是哪条链路在用。」

**【决策】补款收敛语句 2026-09-16 从 face-pay-server 迁入本模块**（`mapper/GateTxnPayMapper.java:74~75`、`resources/mapper/GateTxnPayMapper.xml:210~212`）：那边原先自己持有一份同形 UPDATE、**跨域直写 owner 不是它的表**，随本次收口迁入。白名单多一个 `FAIL` 与补款下单校验的「欠费可补」口径一致（**2026-09-16 裁决**，`service/GateTxnPayService.java:63`）。

**【决策】补款收敛端点可以带事务**（`service/GateTxnPayService.java:65`）：「本方法只改状态、NEVER 触发扣款，也 NEVER 调任何远端，因此**可以**带事务。」实现侧则说明它**不带事务**也成立：单条 UPDATE 自动提交即可，`selectByOrderNo` 只是为了拿 `TXN_DATE`（月分区表的 WHERE 组成）与状态（`service/impl/GateTxnPayServiceImpl.java:394~395`）。

**【决策】IF8A-26 补款下单已整体迁到 face-pay-server（2026-09-15）**（`controller/GateTxnPayController.java:110~113`、`controller/app/GateTxnPayAppController.java:30`）：补款单的支付结果由 face-pay 的 PayCenter 回调直接处理，**不再走 `syncDebitStatus` 的补款单分派分支**；pay-sign-server 侧已对补款单号的回调做拦截（补款单号不走 pay-sign）。本模块只留 `selectByOrderNos`（IF8A-26 校验待补款原订单）与 `/internal/gate-txn-pay/debit/converge` 收敛入口。

**【陷阱】`txnDate` 传错时 UPDATE 恒 0 行且不报错**（`GateTxnPayServiceImpl.convergeDebitStatusForSupplement`，`service/impl/GateTxnPayServiceImpl.java:420~421`）：调用方送的 `txnDate` 优先（补款明细里记的是下单当时的账期），缺失时退回订单自身的值。「**NEVER 用当天日期替代：`GATE_TXN_PAY` 按月分区，日期错了 UPDATE 恒 0 行且不报错。**」

**【陷阱】0 行不等于失败，MUST 回查区分两类**（`GateTxnPayMapper.convergeDebitStatusForSupplement` 的 `@return`，`mapper/GateTxnPayMapper.java:77~78`；`GateTxnPayWriter.convergeDebitStatusForSupplement`，`writer/GateTxnPayWriter.java:112~114`）：「影响行数；**0 行不等于失败**，调用方 MUST 回查当前状态区分『已被别人收敛（`SUCCESS`，属重复扣款）』与『状态不在白名单内（需人工）』」；「**NEVER 当成成功也 NEVER 一律当失败** —— 前者意味着重复扣款待退款，后者才是需人工核对」。`convergeDebitStatus` 侧同理：「返回 0 表示订单已是终态或不存在，调用方 MUST 自行区分并落日志，**NEVER 当成成功——重复回调与『订单号对不上』在这里是同一个返回值**」（`writer/GateTxnPayWriter.java:96~97`）。

**【陷阱】`selectByOrderNos` 走不到分区裁剪、且有 Oracle IN 上限**（`mapper/GateTxnPayMapper.java:283~284`；`resources/mapper/GateTxnPayMapper.xml:358~363`）：不带 `TXN_DATE`，只命中 `UK_GATE_TXN_PAY_ORDER_NO` 的前缀列；调用方 **MUST 限制列表长度**（Oracle IN 列表上限 1000，且列表越长扫描分区越多）。同一 `ORDER_NO` 理论上唯一（UK 含 `TXN_DATE`），这里不做 `ROWNUM` 去重，若出现同号多行由调用方按 `ORDER_NO` 归并后拒绝下单，**避免静默取其中一条**。

**【陷阱】两个配置开关的现状风险（沿用正文 §已知坑）**：`supplement.strict-amount-check` 与 `supplement.max-order-count` 按 2026-09-14 裁决保持现状；`SUPPLEMENT_ORDER.PAY_STATUS` 至今**未收口成枚举**，仍是 `SupplementOrderServiceImpl` / `SupplementConvergeService` 里的裸字面量（`constant/DiscountCalcStatus.java:6~8`）。

### 三、内部补偿端点

**【契约】`/internal/gate-txn-pay/**` 只有三个端点**（`CompensationInternalController`，`controller/internal/CompensationInternalController.java:41~42`）：`POST /offline-fare/recover`（离线码金额补偿，`:69~71`）、`POST /metro-transfer/push`（公交换乘推送，`:77~79`）、`POST /debit/converge`（补款收敛，2026-09-16 新增，`:96~99`）。前两个由 web-admin Quartz `sys_job` 驱动（`gateTxnPayQuartzTask.recoverOfflineFare()` / `pushMetroTransfer()`，cron 均 `0 0/1 * * * ?`），第三个「**不是补偿批处理**，而是由 face-pay-server 在单笔补款支付成功后同步调用的收敛入口，只是恰好共用本类的 `/internal/gate-txn-pay` 前缀」（`:47~51`）。

**【契约】前两个端点恒返 `0000` 且吞异常，第三个都不**：
- `recoverOfflineFare` —— 「**恒返 `0000`**：本轮扫表异常属可自愈（下一分钟再来一轮），返非 0 会让 `sys_job_log` 记一次失败、淹没真正需要人看的失败。本轮结论在 `retMsg` 里，排查 MUST 看 `retMsg` 与模块日志」（`controller/internal/CompensationInternalController.java:66~67`）。异常在 Processor 内兜住、以 `-2` 表达（`service/impl/OfflineFareRecoveryProcessor.java:57~59`：本方法现在由 HTTP 入口调用，抛出去会让 web-admin 侧 `sys_job_log` 记失败——那是对的，但**本轮扫表异常属于可自愈**，记成调度失败会淹没真正需要人看的失败）。
- `pushMetroTransfer` —— 同一约定（`:74~79`），扫表异常返 `-2`（`service/impl/MetroTransferPushTaskProcessor.processReadyTasks`，`service/impl/MetroTransferPushTaskProcessor.java:75~78`）。
- `convergeDebitStatusForSupplement` —— 「本端点**与上面两个不同：NEVER 恒返 `0000`**。上面两个是『一轮扫表』，失败可自愈；这条是单笔资金收敛，调用方要靠 `retCode` + `converged` + `debitStatus` 三者分出『已结清 / 重复支付待退款 / 需人工核对』，把失败压成 `0000` 会让重复支付无声通过。」并且「本方法**不吞异常**：抛出去让调用方收到非 2xx，等价于 `RpcOutcome.Unreachable`，由 face-pay 的 `SupplementOrderCloseProcessor.converge`（cron `0 */5 * * * ?`）下一轮重入。**NEVER 在这里 catch 后返一个假的业务码** —— 那会把『可重试』误判成『业务拒绝』。」（`controller/internal/CompensationInternalController.java:85~91`）

**【契约】`-1` / `-2` 是两个 Processor 共用的返回约定**（`CompensationInternalController.describe`，`controller/internal/CompensationInternalController.java:105~106`、`:111~115`）：`-1` 开关未开启、`-2` 本轮扫表异常，**改一处 MUST 改另一处**；「这里 NEVER 把负值折叠成『本轮 0 笔』——那正是当初用负值区分的原因」。用负值而不是 0 的理由：`enabled=false` 时返 `-1` 而不是 0，是为了区分「开关关了」与「本轮没单子」，否则调度日志里两种情况长得一样、排查「补偿为什么不动」时分不清（`service/impl/OfflineFareRecoveryProcessor.java:54~55`；`MetroTransferPushTaskProcessor.processReadyTasks` 是三态：`-1` 开关关、`-2` 扫表炸、`0` 本轮没任务，`service/impl/MetroTransferPushTaskProcessor.java:60~61`）。`processReadyTasks` 的正数返回值是**捞到并逐条处理过**的任务数，**不等于成功数**（`:58`）；`recoverOfflineFarePendingOrders` 的返回值**只计成功推进的笔数**，「**NEVER** 把跳过或失败也算进去 —— 运维靠这个数判断补偿有没有在推进」（`service/OfflineFareRecoveryService.java:10`）。

**【契约】两个补偿端点都同步执行完一整轮才返回**（`controller/internal/CompensationInternalController.java:37~39`）：「同步是刻意的：web-admin 侧 `sys_job.concurrent='1'`（禁止并发）靠的就是『上一次调用没返回就不发下一次』，这里改成异步受理即返回会让禁并发失效。两轮耗时都在秒级，远小于 `GateTxnPayClient` 的默认响应超时。」

**【契约】对账抽取端点相反：立即返回、不等抽取完成**（`ReconExportController.export`，`controller/internal/ReconExportController.java:35~40`）：「抽取是分钟级批处理，同步等待会让请求线程长时间阻塞在 ojdbc8 的 `synchronized` 方法里、pin 住虚拟线程的载体线程。收齐判定由 recon-server 侧的批次状态负责，本响应只表示『已排入队列』。」受理返回 `true` 已受理 / `false` 同批次上一轮抽取仍在执行（**限流，不是失败**，`service/ReconExportService.java:100~102`）。

**【契约】本源标识 `gate-txn-pay` 是跨服务契约字段值，NEVER 改**（`ReconExportService.SOURCE`，`service/ReconExportService.java:52`）。

**【陷阱 / 待恢复】三个 `/internal/**` 端点当前无鉴权，上线前 MUST 恢复**（`controller/internal/CompensationInternalController.java:23~35`）：用户 2026-09-15 明确要求「不需要鉴权」。本模块没有 spring-security、没有全局拦截器兜底，因此**现状等于允许任何网络可达方触发扫表级别的补偿批处理（其中离线码那条会真的发起扣款）**，与 AGENTS.md §5.2 冲突，属**有意为之的临时降级**。补款收敛那条尤其要紧 —— 「它能把任意订单号的 `DEBIT_STATUS` 直接改成 `SUCCESS`，等于免单」（`:93~94`）。恢复做法：引入共享令牌配置 + 请求头比对，比较 **MUST 用 `MessageDigest.isEqual` 做定长时间比较，NEVER 用 `String.equals`**（后者短路返回，可被逐字节计时探测出令牌内容）；令牌值由 K8s Secret 注入，**NEVER 在仓库里写默认真值**。恢复时 MUST 同批给 `GateTxnPayClient.recoverOfflineFare` / `pushMetroTransfer` 补请求头 —— 这两个方法**已预留 `Map<String,String> headers` 形参**（当前只用来传 traceId），加令牌不需要改签名。**注意 `ReconExportController` 当前也没有鉴权**（`X-Recon-Token` 已按用户 2026-09-11 要求整段删除），两者是「同样待恢复」，不是「照着一份已有实现抄」（`:28~29`；`controller/internal/ReconExportController.java:14~23`）。

**【陷阱 / 待恢复】`/page/**` 写接口同样没有鉴权**（`GateTxnPayPageController`，`controller/page/GateTxnPayPageController.java:76~77`、`:97~99`）：批量退超时罚金是**资金操作**、原价补数是**写接口**，「与本模块其它 `/page/**` 接口一样，**当前没有鉴权**：模块内无 spring-security、无全局拦截器，网络可达方即可调用。上线前 MUST 由运维在入口侧限制该路径只对运营网段开放，或补齐与 `AccountRequestVerifier` 对齐的验签。」

### 四、离线码与换乘

**【契约】离线码补偿的对象是「金额未定」而不是「免扣费」**（`OfflineFareRecoveryProcessor`，`service/impl/OfflineFareRecoveryProcessor.java:12~15`；`OfflineFareRecoveryServiceImpl`，`service/impl/OfflineFareRecoveryServiceImpl.java:25~33`）：判据是 `DEBIT_STATUS='INIT'` + `TOTAL_AMOUNT=0` + `DISCOUNT_CALC_STATUS='OFFLINE_FARE_PENDING'`。离线码出站金额由服务端重算（同序列号首笔进站 + 票价 + 超时费 + 换乘减免 + 钱包折扣），依赖 ticket-server 与 para-server，任一不可达即落痕。**「这是『金额未定』而非『免扣费』，NEVER 按 0 金额收口。」** 由此派生本类全部 NEVER：重算仍失败 → 只标回待重算、**NEVER** 置 FAIL（置了补偿再也捞不到它）；重算出 0 元 → 同上、**NEVER** 按 0 元收口成 SUCCESS（账面正常、车费永久收不回来）；抢占（`applyOfflineFareRecalculated` 返回 1）之前 **NEVER** 扣款，否则多副本重复扣；单笔异常 **NEVER** 冲出批次，本轮剩下的每一行都是资损口。

**【契约】出站首次落痕的语义：返 8002、不建换乘任务**（`GateTxnPayServiceImpl.saveOfflineFarePendingOrder`，`service/impl/GateTxnPayServiceImpl.java:198~205`）：金额清零并显式打上 `OFFLINE_FARE_PENDING`；这行 `TOTAL_AMOUNT=0` 但**不是免扣费交易**，因此 **NEVER** 让它走 `totalAmount <= 0` 的默认 SUCCESS 分支，也 **NEVER** 在此建换乘推送任务（金额与换乘减免都还没算出来）。对上游仍返回 `8002`（`ORDER_PERSIST_FAILED`），保持「闸机照常放行、ITP 侧不认账」的既有语义不变（`constant/GateTxnPayRetCode.java:24` 亦记同一语义）。算价失败时的日志原文：「离线码出站金额计算失败，落单留痕待补偿，**NEVER 按闸机金额扣款**」（`:126`）。

**【契约】离线码补偿的单笔顺序不可调换**（`OfflineFareRecoveryServiceImpl.recoverSingleOfflineFareOrder`，`service/impl/OfflineFareRecoveryServiceImpl.java:101~102`）：重算 → 抢占式回写 → 发起扣款。`applyOfflineFareRecalculated` 返回 1 才代表本副本抢到该笔，**只有此时才允许调 pay-sign，NEVER 先扣款后回写，也 NEVER 忽略返回值**。

**【契约】公交换乘推送的五个条件**（`MetroTransferPushTaskProcessor.shouldPushMetroTransfer`，`service/impl/MetroTransferPushTaskProcessor.java:228~234`）：出站 / 钱包渠道 / 非蓝牙 / 非同行 / 非第三方（`isExitTrxType` + `isWalletVendor` + `!isBluetoothChannel` + `!isCompanionOrThirdParty`）。三个字段级判定收口在 `GateTxnPayFieldCode`，与 `FareCalculator`、`GateTxnPayServiceImpl` 共用同一份，**NEVER 在这里复制字面量**（加新的出站码或钱包码时会漏掉这一处）。

**【契约】换乘推送落状态按 `RpcOutcome` 三分支**（`MetroTransferPushTaskProcessor.landOutcome`，`service/impl/MetroTransferPushTaskProcessor.java:124~161`）：`Ok` → `markSuccess`；`BizRejected` → **一次即终态 `FAILED` + 人工**（「对端答复了、且业务上拒绝：重推一万次也不会成功，MUST 一次即终态 + 人工」，`:139`；日志带「已置 FAILED 终态待人工核对（NEVER 重推）」，`:142`）；`Unreachable` → 退避重试，`retry >= maxRetry` 时置 `FAILED`。每条 mapper 的影响行数 **MUST 接住**：0 行意味着这行已被人工或其它副本改过，只能记日志，**NEVER 当成写成功**（`docs/domain/outbox.md` §二，`:125~126`）。

**【契约】`push` 返回三态而不是 void + 抛异常**（`MetroTransferPushClient.push`，`service/impl/MetroTransferPushClient.java:42~48`，AGENTS.md §5.2 / ADR-D45）：改造前对「网络不可达 / 超时 / 对端明确答复非 `0000` 的业务拒绝」一律抛 `IllegalStateException`，调用方只能笼统 catch，于是「这笔数据对端不收」被当成网络抖动**退避重推满 10 次才转 `FAILED`——白等约 1 小时，且期间对端被反复打**。`enabled=false` 仍抛异常：调用方进入循环前已判过同一个开关，走到这里说明有新调用方漏判，属编程错误，「**NEVER 悄悄返回某个 outcome**——那会把一批任务按『投递结论』落库，而实际上一个请求都没发出去」（`:50~52`）。

**【契约】离线码换乘资格查询失败 MUST 抛异常、NEVER 降级成 false**（`FareDataGateway.isTransferReduction`，`fare/FareDataGateway.java:198~203`；`OfflineMetroTransferClient`，`service/impl/OfflineMetroTransferClient.java:25~27`）：「对端不可达或答非 `0000` 时**照原样抛 `IllegalStateException`**：换乘资格影响实收金额，**NEVER 降级成 false** —— 那是静默少收换乘减免、属资损方向。」调用方 `FareCalculator` 依赖「抛异常即算不出减免」这个语义，改成返回默认值等于静默少收费。

**【决策】换乘任务的判定与构建放在 `MetroTransferPushTaskProcessor`，两个调用方共用一份**（`service/impl/MetroTransferPushTaskProcessor.java:180~186`、`service/impl/GateTxnPayServiceImpl.java:53~58`）：让 `MetroTransferPushTask` 的**首尾在同一处** —— 这里决定「生不生」，`processReadyTasks` 决定「怎么送、送不成怎么办」。出站首次落单与离线码金额补偿两条链路共用，**NEVER 各自复制一份判定** —— 五个条件里后三个是**排除**语义，复制一次写反一个，表现是「少推」或「多推」，两边都不报错：少推乘客拿不到公交换乘优惠，多推给了不该给的（蓝牙 / 同行 / 第三方票）。该类只依赖任务表与推送客户端、不反向引用 Service，因此不构成循环依赖。

**【决策】`wallet.metro-transfer-enabled=false` 时连任务都不建（2.0.77 起，用户 2026-09-15 明确要求）**（`MetroTransferPushTaskProcessor.buildMetroTransferPushTask`，`service/impl/MetroTransferPushTaskProcessor.java:188~202`）：此前该开关只拦投递、不拦生成，关闭期间行程仍逐条落 `PENDING` 堆在表里。那样做有个**看着像优点、实际是缺陷**的后果：一旦开关打开，**几天前的陈旧行程会一次性涌向公交卡系统**，而换乘优惠是有时效的业务语义，补推一批过期行程比不推更糟；`batch-size=50` 还会让积压按分钟慢慢吐、期间对端被持续打。现在语义收敛成一句：**这个开关等于「本功能是否启用」**。敢这么做的前提是**关闭期间的数据并没有不可恢复** —— 任务表六个业务列（`THIRD_USER_ID` / `TRANS_DATE` / `TRANS_TIME` / `PAY_CHANNEL_TYPE` / `TRANSFER_FLAG` / `CARD_TYPE`）**全部取自 `GATE_TXN_PAY` 同一行**，没有一个字段是凭空生成的，任何时候都能用 `INSERT ... SELECT` 按 `shouldPush` 的同四个条件把某个时间窗的任务补建回来；**改动这里的字段映射时 MUST 同步核对那条补建 SQL 的口径**。**NEVER 把这个开关判断挪到两个调用方里**（等于把「本功能是否启用」复制成两份）。开关判断 **MUST 放在 `shouldPush` 之后**：放前面每笔非钱包出站都要打一行日志，而那些单本来就不推、纯噪音（`:208~209`）。

**【决策】换乘推送轮询周期 10 秒 → 60 秒（用户 2026-09-15 裁决），这是外部可感知的行为变化**（`service/impl/MetroTransferPushTaskProcessor.java:25~28`）：公交卡系统那侧收到换乘推送的时延上限从 10 秒变为 60 秒。要调回更密 **MUST 改 `sys_job` 的 cron，NEVER 改回模块内 `@Scheduled`**；同时注意 `SYS_JOB_LOG` 是「开始即入库」，cron 每打密一档那张表的日增行数就翻一档。

**【决策】调度语义从 `fixedDelay` 变成 cron 不是等价替换**（`service/impl/OfflineFareRecoveryProcessor.java:25~29`、`service/impl/MetroTransferPushTaskProcessor.java:30~31`）：原来是「上一轮跑完再等 60 秒」、保证两轮间隔 ≥60s；现在是墙上时钟每分钟触发，**只靠 `sys_job.concurrent='1'`（禁止并发）保证不重叠**。上一轮耗时接近 60 秒时下一轮会比原行为**早最多 60 秒**开始 —— 这在离线码链路无害（扣款前必须先过 `applyOfflineFareRecalculated` 的条件更新，早跑一轮最坏只是多一次空扫），**但 `concurrent` 那一列 NEVER 改成 `'0'`**（`'0'` 是允许并发），一改就是并发重复扣款。同理 **NEVER 删 `sys_job` 那行**，删了补偿就彻底停摆（`OfflineFareRecoveryProcessor.java:23`）。

**【决策】借 `DISCOUNT_CALC_STATUS` 表达待重算，是为了零 DDL**（`GateTxnPayServiceImpl.OFFLINE_FARE_PENDING`，`service/impl/GateTxnPayServiceImpl.java:191~194`）：「借这一列而不是新增列，是为了零 DDL —— `GATE_TXN_PAY` 是月分区表，加列成本高。改动该字面量 **MUST 同步改 `GateTxnPayMapper.xml` 里三处同名条件**，否则补偿静默失效。」`DiscountCalcStatus` 类注释同样点名那三处（离线码待重算的扫表判据 + 两条 CAS 的 WHERE），「漏改即『补偿任务扫不到、金额永远是 0』，且编译与单测都发现不了」（`constant/DiscountCalcStatus.java:17~20`）。

**【决策】补偿循环骨架统一走 `model.domain.OutboxScan`，NEVER 退回裸 for**（`OfflineFareRecoveryServiceImpl.recoverOfflineFarePendingOrders`，`service/impl/OfflineFareRecoveryServiceImpl.java:80~87`）：它把三条不变量固化下来（单条失败 NEVER 中断整批、每行只计一次、投递方法抛异常时外层兜一层），不再依赖每个补偿任务各自记得写 try/catch。「**NEVER 退回裸 for** —— 漏一个 catch，`markOfflineFarePending` / `applyOfflineFareRecalculated` / `asyncPaySign` 任一抛异常都会冲出循环，本轮剩余待重算订单全部不处理，而它们是资损口（`TOTAL_AMOUNT=0` 却不是免扣费交易）。」语义映射：`deliver` 返回 `false` 表示「本轮没推进」（没抢到 / 重算仍为 0），不是投递失败，该落的痕已由 `recoverSingleOfflineFareOrder` 自己落完，因此 `onFailure` 无事可做。

**【决策】离线码补偿类 NEVER 加 `@Transactional`，且「落痕」不在这里**（`service/impl/OfflineFareRecoveryServiceImpl.java:35~37`）：内部要调 pay-sign（AGENTS.md §5.2 事务内禁 RPC）。「落待重算痕」那一步属于出站首次落单、留在 `GateTxnPayServiceImpl.saveOfflineFarePendingOrder`；本类只负责把痕**推进**掉。

**【决策】补偿重建请求上下文而非只按订单号重试**（`OfflineFareRecoveryServiceImpl.rebuildOfflineRequest`，`service/impl/OfflineFareRecoveryServiceImpl.java:148~152`）：钱包判定依赖 `PAYMENT_VENDOR`，该列下单时已持久化，因此补偿时的换乘减免与钱包折扣口径与出站当时一致。**NEVER 改成只按订单号重试而不重建上下文** —— `calculateOfflineFare` 读的是 request 的 `cardId` / `ticketTransSeq` / `paymentVendor`。`requestSignSeq` 无法从订单行恢复，与 `retryPay` 传 `null` 的现状一致，由 pay-sign 侧回查 account 兜底。

**【决策】两个外部公交卡系统的 Client 留在业务模块，NEVER 搬进 `rpc`**（`MetroTransferPushClient`，`service/impl/MetroTransferPushClient.java:20~23`；`OfflineMetroTransferClient`，`service/impl/OfflineMetroTransferClient.java:19~21`）：对接的是**外部公交卡系统**（不是 ITP 内部服务），按 pay-sign-server 的 `PayGatewayClient` 定位留在业务模块里 —— 「`rpc` 是 ITP 内部服务的 Client 集合，`service.*.url` 与 `@EnableRpcXxx` 都是为内部调用准备的」。2026-09-14 从裸 `WebClient.Builder#build()` 改为继承 `ProxyWebClient`（换来连接池 500 连接 / 1000 排队 / 20s 空闲回收、3s 连接超时、出入报文日志开关、`authorization` 的 MDC 透传与统一失败日志），**三态返回语义 / 抛异常语义一行未改**。

**【陷阱】换乘推送的两个缺陷是叠加的，单改一个仍然是 1002**（`MetroTransferPushClient.push`，`service/impl/MetroTransferPushClient.java:58~81`）：
- **MUST 用 `application/x-www-form-urlencoded`，NEVER 改回 JSON body。** 对端 `com.bestone.buscard` 用 `RequestHandlerVO` 按表单字段绑定，发 JSON body 时那六个字段**全部为 `null`**、随后在取 `bizData` 处 NPE，对外表现是 **HTTP 200 + `{"retCode":"1002","retMsg":"接收地铁交易数据失败null"}`** —— `retMsg` 尾部那个字面量 `null` 就是「参数一个都没绑上」的指纹，**NEVER 把它当成我方数据内容有问题去改 bizData 字段**。2026-09-15 用九种报文形态 + 四组数据实测：只要是 JSON body，回的字节完全一样；拿到对端日志 `RequestHandlerVO@61810503[providerId=<null>,...,bizData=<null>]` 才定性。
- **bizData 只有五个字段，NEVER 往里加 `cardType`。** 2026-09-15 单变量对照实测：带 `cardType` → **1002**；键名换成 `reserve` → **0000**；**两者同时给 → 又变回 1002**；**整个键都不给 → 0000**。第三条说明对端**见到 `cardType` 这个键就炸**，第四条说明这个位置对端根本不需要，因此按接口方要求**直接不发**，**NEVER 为了「留个位置」塞 `reserve` 空串或卡类型**。卡类型在 `METRO_TRANSFER_PUSH_TASK.CARD_TYPE` 已有留痕、不依赖出向报文。
- 排查经验：「**Content-Type 与 bizData 键名这两个缺陷是叠加的，单改任何一个都仍然是 1002**」，于是「换一个变量试一次、没好就否掉这个方向」的排查法在这里必然误判，前后共试了十几种形态都回同样的字节就是这个原因。

**【陷阱】离线码换乘查询自接入以来一次都没成功过（2026-09-15 修）**（`OfflineMetroTransferClient`，`service/impl/OfflineMetroTransferClient.java:29~39`）：**报文形态 MUST 是 form-urlencoded + 五个公共参数 + `bizData` 外壳，NEVER 回退成 JSON body 平铺四个字段。** 此前发的是 `postJsonAndGetResponse` + 平铺 `thirdUserId`/`handleDateTime`/`cardId`/`ticketTransSeq`，实测对端回 **HTTP 200 + `{"retCode":"1002","retMsg":"bizData 解析异常"}`**。而失败语义是抛 `IllegalStateException`、`FareCalculator` 据此判「算不出减免」，于是表现为**静默少给优惠、不报错、无告警** —— 这类缺陷 MUST 靠真实应答体检出，编译与单测都看不到。换成现在的形态后同一组数据实测回 `{"retCode":"0000","retMsg":"成功","isReduction":"01"}`；bizData 字段清单**就是原来那四个、一个不多一个不少**。**NEVER 因为两个端点的 retMsg 不同就以为是两类问题** —— 该端点直接点名 `bizData`，`pushMetroTran` 回的是「接收地铁交易数据失败null」，**同一个骨架、同一个成因**。

**【陷阱】「投递」与「落状态」两段的 catch 语义不同，合在一个 try 里会重复推送**（`MetroTransferPushTaskProcessor.processOne`，`service/impl/MetroTransferPushTaskProcessor.java:86~97`、`:100~101`、`:119`）：**整个方法体 MUST 兜住全部异常**（抛出去会终止 for、本轮剩余任务全部不处理）。投递段的异常 = 没拿到对端答复 ⇒ 归 `Unreachable`、可重试；**落状态段的异常 NEVER 再改判成重试** —— 改造前两段合在一个 try 里，`markSuccess` 抛异常会掉进同一个 catch 继续执行 `markRetry`，而那条 CAS 的前置 `STATUS='PROCESSING'` 此时仍然成立，**于是一笔已推送成功的任务被重新排入重推、对端收到重复行程**；现在这种情况让行留在 `PROCESSING`，交给 `recoverStuckProcessing` 在租约（`wallet.metro-transfer-processing-lease-seconds`）到期后退回 `RETRY`。抢占段也单独兜异常：**抢不到（别的副本先到）或抢占本身报错都只能跳过这条，NEVER 归到下面的投递结论里** —— 没抢到就等于一个请求都没发，落任何状态都是错的。

**【陷阱】`LAST_ERROR` 超长直接抛 ORA-12899**（`MetroTransferPushTaskProcessor.truncate`，`service/impl/MetroTransferPushTaskProcessor.java:171`，上限 512）。

### 五、持久层与月分区表

**【契约】`GATE_TXN_PAY` 按 `TXN_DATE` 月分区，`TXN_DATE` 是 `VARCHAR2` 存 `yyyyMMdd`**（`GateTxnPayMapper.countUserAccInfo`，`mapper/GateTxnPayMapper.java:260~264`；`resources/mapper/GateTxnPayMapper.xml:594~603`）：**生产库 `TXN_DATE` 实际是 `VARCHAR2(16)` 存 `yyyyMMdd`，不是 `DATE`**（2026-09-08 实测 `ALL_TAB_COLUMNS`；**仓库 DDL `gate-txn-pay-schema.sql:28` 写的 `DATE` 与生产库不一致，勿照 DDL 设计绑定类型**）。绑定 **MUST** `jdbcType=VARCHAR`，字符串比较对 `yyyyMMdd` 定长格式是正确的字典序、且能命中 `TXN_DATE` 上的索引。校验只做长度与数字，**NEVER 转成 `LocalDate` 再绑定**（`OriginalFareBackfillServiceImpl`，`service/impl/OriginalFareBackfillServiceImpl.java:127`）。

**【契约】哪些查询 MUST 带 `TXN_DATE` 区间（分区裁剪条件）**：`countOfflineByStationGroup` / `countOfflineSummary`（`mapper/GateTxnPayMapper.java:123~124`、`:132~133`）、`selectOvertimeRefundablePage` / `countOvertimeRefundable`（`:153`、`:161`）、`selectMissingOriginalFare`（`:293~295`）、`selectOfflineFarePending`（`:319`）、`countUserAccInfo`（`:260~261`、`:270~273`）、对账四类的 `Recon_Window`（`resources/mapper/ReconExportMapper.xml:12~19`）。反面清单（走不到分区裁剪）：`countUnsettledOrderByCardId`（不带 `TXN_DATE`、会扫全部分区，「调用方是每天两次、单次只查一张卡的批处理，代价可接受；**NEVER** 把它放进过闸等联机链路」，`mapper/GateTxnPayMapper.java:243~244`）、`selectByOrderNos`（`:283~284`）。

**【契约】三组 CAS 白名单在 mapper 层的落点**（`resources/mapper/GateTxnPayMapper.xml`）：`updateStatusFromPending` = `INIT` / `RETRY`（`:152~165`，**刻意排除 `PROCESSING`**：已受理订单若被迟到的失败结果降级回 `RETRY`，会被 `retryPay` 当作可重试订单再次发起扣款，`mapper/GateTxnPayMapper.java:33~36`）；`updateStatusIfProcessing` = 仅 `PROCESSING`（`:166~176`）；`convergeDebitStatus` = `INIT` / `PROCESSING` / `RETRY`（`:177~192`）；`convergeDebitStatusForSupplement` = 前三个 + `FAIL`（`:194~215`）。四条语句的 WHERE **都带 `TXN_DATE`**。

**【契约】离线码重算回写的 WHERE 是幂等条件也是抢占条件**（`GateTxnPayMapper.updateOfflineFareRecalculated`，`mapper/GateTxnPayMapper.java:328~331`；`resources/mapper/GateTxnPayMapper.xml:702~707`）：`DEBIT_STATUS='INIT' AND DISCOUNT_CALC_STATUS='OFFLINE_FARE_PENDING'` —— 返回 1 才代表本副本拿到这笔，返回 0 说明别的副本已重算或订单已被人工干预。调用方 **MUST 据此决定是否继续调 pay-sign，NEVER 忽略返回值** —— 否则同一笔会被重复发起扣款。SET 里 **MUST 含 `ENTRY_STATION_NAME` / `EXIT_STATION_NAME`**：谁改编码谁改名，漏掉这两列时列表侧站名永久为空、只能回落显示站点编码，**NEVER 删**。

**【契约】原价回填的 `ORIGINAL_FARE IS NULL` 是幂等 + 保护条件**（`GateTxnPayMapper.updateOriginalFareIfNull`，`mapper/GateTxnPayMapper.java:304~306`；`resources/mapper/GateTxnPayMapper.xml:638~641`）：「并发重复调用或人工重跑都只会写第一次，**NEVER 去掉它** —— 出站时已写好的原价快照是当时参数版本的值，回填用的是当前版本，覆盖等于篡改历史账单口径。」补数三条设计约束：不带事务 / 只写空值行 / **回填用的是当前参数版本的票价**（费率矩阵只保留少数历史版本，无法还原当时版本，因此结果是近似口径；若期间调过价 **MUST 先与业务确认可接受**，`service/OriginalFareBackfillService.java:16~25`）。`affected == 0` 是并发下的**幂等结果**，既不计 updated 也不计 failed（`:39`）。

**【契约】圈单查询只是粗筛，NEVER 当成可退款结论**（`GateTxnPayMapper.selectOvertimeRefundablePage`，`mapper/GateTxnPayMapper.java:144~151`；`resources/mapper/GateTxnPayMapper.xml:306~313`）：口径四要素 `OVERTIME_AMOUNT > 0` + `ORDER_EXP_TYPE='1'`（单边）+ `TICKET_STATUS='07'`（超时出站）+ `DEBIT_STATUS IN ('SUCCESS','PROCESSING')`（`DebitStatus.isRefundable` 白名单）+ `NVL(OUT_STATION, IN_STATION)=stationCode`。「该口径是『圈出候选』，真正的可退校验（日票拒退、金额上限、状态白名单）仍由单笔 `requestRefund` 逐单把关，因此本查询只做粗筛，**NEVER** 用它直接判定能否退款。」`countOvertimeRefundable` 的过滤条件 **MUST 与分页查询完全一致**（`:161`）；同理 `countTransList` 与 `selectTransList`（`:200`）、`selectTransStatistics` 与 `countTransList`（`:221~222`）、`countOperationPage` 与 `selectOperationPage`（`:92`）。

**【契约】IF8A-05 的 `cardTypeList` 与 `cardType` 互斥**（`mapper/GateTxnPayMapper.java:183~184`；`resources/mapper/GateTxnPayMapper.xml:425~426`、`:468`）：`cardTypeList` 非空时按 `CARD_TYPE IN (...)` 过滤并**忽略** `cardType` —— APP 日票聚合码 `05` 会展开成 `0445~0448`，因此不能用单值；「两个条件互斥，**NEVER 同时拼**（AND 起来必然命中 0 行）」。

**【契约】IF8A-41 四个金额的口径（2026-09-10 校准）**（`resources/mapper/GateTxnPayMapper.xml:502~514`；`mapper/GateTxnPayMapper.java:214~226`）：① `totalPrice` 用 `NVL(ORIGINAL_FARE, TRX_AMOUNT)` —— `ORIGINAL_FARE` 是 2026-09-10 才开始稳定落库的，历史行为空，缺失时退化成票价（优惠显示 0），**NEVER 直接 `NVL(...,0)`，那会把原价显示成 0 元**；② `totalDebit` 用 `TOTAL_AMOUNT`（= `TRX_AMOUNT` + `OVERTIME_AMOUNT`），**NEVER 只 `SUM(TRX_AMOUNT)`** —— 旧实现如此、把超时费漏掉、**少报 12 元（实测用户 00522943）**；③ `totalDiscount` 的减数 **MUST 是 `TRX_AMOUNT` 而非 `TOTAL_AMOUNT`** —— 后者含超时加收，会让加收抵掉优惠并算出负数（**实测那笔 540/1200 的补票会得到 0-1740**），逐笔 `GREATEST(...,0)` 下取零；④ `totalOvertime` 单列出超时加收，**NEVER 把 `OVERTIME_AMOUNT` 映射到 `totalDiscount`** —— 加收与优惠语义相反，旧实现把 12 元超时费显示成「已优惠 12 元」。数据源取 `GATE_TXN_PAY` 而非 `QRCODE_TXN_DETAIL`：本表同时拥有四个量、单表即可算完（两表的 `TRX_AMOUNT` / `OVERTIME_AMOUNT` 逐行相等，2026-09-10 LEFT JOIN 8 行核对）。零行时 `COUNT(1)` 保证有一行、但 `SUM()` 返 NULL，**调用方 MUST 逐字段判空补 "0.00"、NEVER 只判 `tripData == null`**（`service/impl/GateTxnPayQueryServiceImpl.java:139~142`）。

**【契约】对账四类文件口径互不套用**（`ReconExportMapper`，`mapper/ReconExportMapper.java:20~27`）：`ITP.EXP` 只取异常/单边订单（`ORDER_EXP_TYPE` 非空非空格**且非 `'0'`**）、`ITP.PAY` 过闸组取 `DEBIT_STATUS='SUCCESS'` / 单边组取异常订单、`ITP.BUS` 只取 `DEBIT_STATUS='SUCCESS'` 按交易日汇总、`ITP.DETAIL` 只取日票/计次票且产生超时费的行程。**NEVER 互相套用 WHERE**（`:68~70`、`:83~84`）。行格式段数与顺序即甲方顺序、**NEVER 调整**：EXP 13 段（`service/ReconExportService.java:162~164`）、PAY 21 段 = 5 键 + 16 度量（`:238~242`、常量 `PAY_SEGMENT_COUNT` 见 `:55`「甲方 §一(2) 明确列出，NEVER 增减」）、BUS **只有 4 段**（`:318~320`，「这里没有线路、车站、设备、支付方式段，**NEVER 照搬 PAY 的 5 段键**」）、DETAIL 7 段（`:350~351`）。DETAIL 交易类型固定「出站」、金额取 `OVERTIME_AMOUNT` 而非 `TOTAL_AMOUNT`（对账的是超时费本身，报全额等于重复计账），「发售」类型的行由 daily-ticket-server 负责、**NEVER 在本模块产出**（`:353~358`）。卡类型判定用 `CARD_TYPE IN ('0445','0446','0447','0448')`；甲方 §二 说 ACC 侧「根据 `SIGN_CHANNEL_CODE` 区分票种」那是 ACC 侧做法，本表该列是签约支付通道、不是票种，**NEVER 拿它判日票**（`:360~364`）。

**【决策】月分区表设计取舍（加列成本高 / 键必须带 `TXN_DATE`）**：离线码待重算标记**借 `DISCOUNT_CALC_STATUS` 而不是新增列，就是为了零 DDL——`GATE_TXN_PAY` 是月分区表，加列成本高**（`service/impl/GateTxnPayServiceImpl.java:193`）；`DISCOUNT_CALC_STATUS` 列宽已从 16 加宽到 32 字符（`sql/gate-txn-pay-discount-calc-status-widen-migration.sql`）—— 原 16 字符装不下 `OFFLINE_FARE_PENDING` 的 20 字符、会在运行时报 `ORA-12899`，**新增取值 MUST 先核对列宽**（`constant/DiscountCalcStatus.java:13~15`）；`GatePayRequestFactory` 里 `TXN_DATE` **MUST 取行程侧订单的交易日期（出站日）、NEVER 让支付域自己取当日**：两表按 `(ORDER_NO, TXN_DATE)` 关联且都以它做月分区，**出站到落库最大滞后实测 94 分钟**，22:26 之后出站时支付域取 `now()` 会跨日、关联即落空；补单重试走同一条路，值仍取订单快照（`paysign/GatePayRequestFactory.java:68~70`）。

**【决策】对账 mapper 刻意独立、返回 `Map` 而非实体**（`mapper/ReconExportMapper.java:12~18`）：对账抽取是批处理口径（全量扫时间窗口、按 keyset 翻页、库内 `GROUP BY`），与联机查询的口径和索引策略完全不同，混在一起后续任何一方调整过滤条件都会误伤另一方。返回 `Map<String,Object>` 是因为抽取每类文件只需要 7~10 列，映射成 40 余列的实体等于让每行多背 30 个 null 字段，百万行量级下是可观开销。

**【决策】PAY 汇总在库内 GROUP BY、同键出两行、线路段留空**（`service/ReconExportService.exportPay`，`service/ReconExportService.java:244~263`；`mapper/ReconExportMapper.java:61~70`）：不做分页（四键聚合后只有几百到几千行，分页反而要把同一个聚合跑多遍），**NEVER 改成把明细拉回 Java 再聚合**——那等于把百万行搬进堆内存，本方法存在的唯一理由就是避免这件事。过闸组与单边组各输出一行、彼此把对方那两段写 0；recon-server 侧对汇总文件按键二次累加会合并同键行，**NEVER 为了「一行一键」改成 Java 侧先按键 join 两个结果集**。**线路段（第 2 段）本模块一律留空**，2026-09-16 起由 recon-server 按车站码统一补齐（`ReconStationMapper` + `recon.line-backfill.*`），本模块两条 PAY 汇总 SQL 已删掉 `LEFT JOIN STATION_INFO S ON S.STATION_CODE = T.OUT_STATION` 与 `S.LINE_CODE`。收口理由：线路是车站的函数，原先四个源各写一遍这个 join，等于「线路怎么取」有四份副本；而 `STATION_INFO` 属车站/参数域、owner 不是本模块，少一处跨域直连就少一处破例。**NEVER 把 join 加回来**（recon-server 侧是无条件覆盖，加回来不改变产出、只让口径重新分叉）；**NEVER 改成用车站代码前 2 位推线路** —— 实测前 2 位恰好等于线路号是编码巧合、不是契约。

**【决策】对账抽取 NEVER 跑在请求线程上、且刻意不加 `@Transactional`**（`service/ReconExportService.java:27~45`）：全服务默认 `spring.threads.virtual.enabled=true`，JDK 21 未落地 JEP 491，虚拟线程在 `synchronized` 内阻塞会 **pin 住载体线程**，而 ojdbc8 的 `PhysicalConnection` / `OracleStatement` 大量方法是 `synchronized` —— 一条 60s 慢 SQL 就是 60s 的 pin；载体线程池 parallelism 默认等于容器可见 CPU 数，CPU limit 偏小时一两条慢 SQL 即可 pin 满，**全 JVM 虚拟线程停止调度**，连 WebClient 响应的续体都唤不醒。因此把任务派给**固定大小的平台线程池**，控制器只受理与立即返回。并发控制不依赖任何中间件：同一 batchId 的在途标记放 `ConcurrentHashMap`、`putIfAbsent` 成功才受理、`finally` 移除；单副本内足够，多副本由 recon-server 侧按批次分发保证只下发一次。**所有导出方法及其调用链刻意不加 `@Transactional`**：若把整轮抽取包进一个事务，事务时长等于抽取时长（分钟级），期间还夹着 `sink.write` 触发的分片上送（HTTP 网络调用）—— 事务内发起 RPC 是本项目明令禁止的：连接被 Druid `remove-abandoned-timeout` 判定为泄漏后强杀、`commit` 抛 connection closed、整轮白跑。抽取是纯只读、本来也不需要事务。抽取线程数 `recon.export.worker` 默认 1 即串行，**刻意不放大**——抽取是重 IO 的全表扫描，并发只会互相抢 Oracle 的 IO 与 Druid 连接、反而拖慢联机链路（`:79~82`）。一类文件失败时 **NEVER 让它中断整轮**（四类是四份独立文件，一类挂掉不该拖累另三类，否则重跑成本翻倍，`:132~134`）。

**【决策】`GateTxnPayWriter` 单独拆成组件**（`writer/GateTxnPayWriter.java:13~19`）：用数据库唯一索引保证幂等、无需分布式锁；单独拆分是为了**避免 `GateTxnPayServiceImpl` 内部自调用导致 `@Transactional` 失效**。订单与公交 outbox 在同一事务中写入，避免订单成功但漏建推送任务（`:60`）；公交推送任务以订单号唯一约束保证同一笔过闸交易只产生一个任务（`:133`）。

**【陷阱】幂等兜底 MUST 沿 cause 链判定，NEVER 直接 `catch (DuplicateKeyException)`**（`GateTxnPayWriter.isDuplicateKeyViolation`，`writer/GateTxnPayWriter.java:176~185`）：`MapperAspectToTrace`（`resource/micro/web/.../trace/MapperAspectToTrace.java:51`）在 `management.tracing.enabled=true` 时把 mapper 抛出的任何异常统一包成 `RuntimeException`，单层类型判断就捕不到，本类赖以实现幂等的「唯一索引 + `DuplicateKeyException` 兜底」会整体失效。「本模块当前未注入该 env（判断走 `web.properties:78` 的默认 `false`），**但那是配置巧合而非代码保证**——2026-09-08 已在 account-server 与 para-server 上实测到失效后果，此处按防御写法统一收口。」（注：AGENTS.md §2.2.1 记 `gate-txn-pay-server` 自 2.0.60 起**已打开** tracing，与本注释「当前未注入」表述存疑，见文末未定条目。）

**【陷阱】SQL 正文 NEVER 写注释（Druid WallFilter `commentAllow=false`）**（`resources/mapper/ReconExportMapper.xml:7~10`）：「**NEVER 把这些说明搬进 SQL 正文**：Druid WallFilter 的 `commentAllow=false`，SQL 正文里出现行注释或块注释会被判定为注入，语句静默失效。另注意本段是 XML 注释，**内部不能出现连续两个半角减号**，否则 XML 解析直接报错。」

**【陷阱】Druid WallFilter 只认复数 `ROWS ONLY`**（`resources/mapper/DiscountLevelMapper.xml:23~35`）：「末行 **MUST 写复数 `ROWS ONLY`，NEVER 写单数 `ROW ONLY`**。两者在 Oracle 里都合法，但 druid 1.2.23 自带的 Oracle 语法解析器只认复数形式；单数形式被判成注入，抛 `sql injection violation ... syntax error, expect ONLY, actual ROW`，该语句静默失效 —— 编译、单测、`xmllint` 全都发现不了，只在 Oracle 运行时炸。」**已发生：2026-09-15 钱包优惠端到端验证时，`queryTotalAmt` 已成功返回 `WALLET_TOTAL_AMT`，却在这条 SQL 上抛出上述异常，`DISCOUNT_CALC_STATUS` 落 `FALLBACK`、`DISCOUNT_RATE` 恒 NULL。** 判据已用 `WallUtils.isValidateOracle` 实测：`ROW ONLY` 返 false、`ROWS ONLY` 返 true；回归防线见 `MapperSqlWallCompatibilityTest`。

**【陷阱】本模块未开 `map-underscore-to-camel-case`，MUST 用显式 resultMap**（`resources/mapper/DiscountLevelMapper.xml:4~15`）：全仓只有 acc-es-server 开了该配置，因此 `LEVEL_AMT` 映不到 `levelAmt` —— **查询会正常返回一行、对象也非 null，但四个字段全是 null**，于是 `FareCalculator` 的 `level.getLevelDiscount() == null` 判定成立、抛「无有效折扣档位」，看起来像档位数据缺失，实际上库里那一行一直都在。2026-09-15 实测：`DISCOUNT_LEVEL` 有 `LEVEL_AMT=0` / `DISCOUNT_TYPE='01'` / `DISCOUNT_STATUS='00'` 的可用行，同一条 SQL 在库里返 1 行、服务侧却报无档位。本模块另外三个实体 mapper 一律显式 resultMap，本文件此前是唯一例外。**NEVER 退回裸 resultType 自动映射。**

**【陷阱】OGNL 的 `'0'` 是 char 字面量，与 String 比较恒 false**（`resources/mapper/GateTxnPayMapper.xml:387~396`）：「`test` 里字符串 **MUST 用双引号**：OGNL 的 `'0'` 是 char 字面量，与 String 比较恒为 false，写成 `debitRequestResult == '0'` 会让整段过滤静默失效（**alipay-pay-sign-server 的 `AlipayPayLogMapper.xml` 就是这种写法，需另行核实**）。」同一段还记：`NULL` 必须显式列出 —— Oracle 三值逻辑下 `DEBIT_STATUS != 'SUCCESS'` 对 NULL 不成立，漏掉会让脏数据在「失败」页签里凭空消失；口径与 `countFailedOrder` 的「结清」判定完全一致，避免同一笔订单在 APP「未支付」页签和解约校验里结论相反。

**【陷阱】IF8A-35 时间下限踩过的两个坑，NEVER 重犯**（`mapper/GateTxnPayMapper.java:266~268`；`resources/mapper/GateTxnPayMapper.xml:596~602`）：① 传 `LocalDate` + `jdbcType=DATE` → 参数被转成 `'2026-06-08'` 交 Oracle 按 `NLS_DATE_FORMAT` 解析 → **`ORA-01843` 无效的月份**；② 改用 `ADD_MONTHS(TRUNC(SYSDATE), ?)` → 变成「`VARCHAR2` 列 >= `DATE`」，Oracle 反向把列值 `'20260828'` 按 `NLS_DATE_FORMAT` 解析 → **`ORA-01861` 文字与格式字符串不匹配**；「该错误**只在扫到实际数据行时触发**，无匹配行的用户反而返回成功，极易误判为已修好」。查询侧同一约束：**NEVER 改成传日期对象或 `ADD_MONTHS`**，月数非法（<=0）时退回默认 3、**NEVER 让它变成「不加下限」**（`service/impl/GateTxnPayQueryServiceImpl.java:408~412`）。聚合查询必然返回一行，映射为 null 时按「查询未执行」处理，**NEVER 落成 0/0 + `0000`，那会被 APP 当成「无欠费」**（`:418~419`）。

**【陷阱】Oracle `ROWNUM` 在 `ORDER BY` 之前生效**（`resources/mapper/GateTxnPayMapper.xml:615~622`）：`ROWNUM` 限流 **MUST 放在外层** —— 写在内层会先截断再排序，拿到的不是最早的 N 条。

**【陷阱】keyset 游标 NEVER 换成 OFFSET 大页码**（`mapper/ReconExportMapper.java:35~37`；`resources/mapper/ReconExportMapper.xml:39~45`）：Oracle 的 `OFFSET n ROWS` 需要先产出并丢弃前 n 行，第 800 页的代价是第 1 页的 800 倍、全量扫完是 O(n²)。用 `(OUT_TIME, ID)` 复合游标，每批都是索引区间的第一批、代价恒定；`ORDER BY OUT_TIME, ID` 与游标条件严格对应 —— 同一 `OUT_TIME` 内可能有多行（同秒多笔），**只比 `OUT_TIME` 会漏行或死循环**，因此第二段用 `ID` 破平。

**【陷阱】对账窗口横跨两个 `yyyyMMdd`，只传一个日期会丢掉窗口后半段**（`mapper/ReconExportMapper.java:39~41`；`resources/mapper/ReconExportMapper.xml:16~19`）：窗口是 T-2 02:00 到 T-1 02:00；`TXN_DATE` 区间两端闭负责分区裁剪、`OUT_TIME` 区间**左闭右开**保证相邻两天既不漏也不重。`OUT_TIME` 是 `VARCHAR2` 存 `yyyyMMddHHmmss`，直接做字符串比较，**NEVER 套 `TO_DATE` 之类的函数**，那会让索引的 `OUT_TIME` 前缀失效。

**【陷阱】EXP 口径必须排除 `'0'`，否则汇总与明细对不平**（`resources/mapper/ReconExportMapper.xml:86~111`）：`ORDER_EXP_TYPE` 的 `'0'` 就是「正常」（`scripts/20260818_if8a_schema_migration.sql`），而甲方「订单异常类型」取值域是 1~15、不含 0。`'0'` 既不是 NULL 也不是空格，原来的 `IS NOT NULL AND <> ' '` 会把**全部正常订单**捞进单边账文件，同时让第 18/19 段与第 12/13 段「过闸笔数/金额」逐字重复，ACC 侧把同一笔既算过闸收入又算单边账、必然对不平 —— **2026-09-11 端到端实测确认（EXP 3 行全是误报）**，故新增 `'0'` 排除。空格判定也是必须的：Oracle 里长度为 0 的字符串等于 NULL，但历史数据可能写入单个空格表示「无异常」，只判 `IS NOT NULL` 会让 EXP 文件虚增。本查询 **NEVER 加 `DEBIT_STATUS` 条件**：单边账的本质就是扣费链路没走完，按 `SUCCESS` 过滤等于把要报的行全部滤掉。两处口径抽成公共片段 `Recon_Exp_Filter` 是为了让它们**无法漂移**。**我方列注释取值域（0 正常 … 5 双段计费超时）与甲方 1~15 并不一致，映射关系待甲方澄清，NEVER 自行折算**（`mapper` 侧同一约束见 `service/ReconExportService.java:180~190`：猜错会把超时行程报成自主补站、账目性质完全变了）。

**【陷阱】对账三处「本表无对应数据」按空值/0 输出，NEVER 拿别的列顶替**（`service/ReconExportService.exportExp`，`service/ReconExportService.java:166~178`）：**进站设备编码** —— 本表只有一个 `DEVICE_ID`、语义是出站设备，**NEVER 拿它同时填进站设备**，那会让 ACC 侧把出站闸机当成进站闸机对账；**出站处理设备类型** —— 本表无该列；**优惠金额** —— 固定 `0`，本表唯一形似优惠的列 `DISCOUNT_LEVEL_AMT` 语义是「命中的累计金额门槛」（`entity/GateTxnPay.java:54`）、不是优惠额本身，且生产数据大面积为 NULL，属不可靠列、取值口径待甲方确认（BUS 优惠段同理，`:322~324`）。**实际扣款金额同样取 `TOTAL_AMOUNT`**：本表没有独立的实收列，「应扣」与「实扣」在库里是同一个值，**两段同值是有意的、不是复制粘贴错误**。

**【陷阱】`resultType=Map` 下 NUMBER 列一律是 `BigDecimal`**（`mapper/ReconExportMapper.java:18`；`ReconExportService.toLong`，`service/ReconExportService.java:404~408`）：直接强转 `(Long)` 会抛 `ClassCastException`；本表金额列单位是分、都是整数，直接取 `longValue()`。该私有方法只服务本类，**不是新建公共工具类**。

**【陷阱】对账 SQL 的表别名约定 MUST 保留**（`resources/mapper/ReconExportMapper.xml:22~31`）：全部 select 把主表写成 `FROM GATE_TXN_PAY T`，三个公共片段（`Recon_Window` / `Recon_Keyset` / `Recon_Exp_Filter`）里的列也一律带 `T.` 前缀。2026-09-16 之前的理由是那两处 `LEFT JOIN STATION_INFO`，join 已随线路段收口删除、现在是单表查询，**但这条约定 MUST 保留**：它让公共片段与「FROM 后面有几张表」解耦，下次任何一条 select 再引入第二张表时不必回头改片段、也不会踩 `ORA-00918`。**新增 select MUST 同样把主表别名写成 `T`**，否则 include 进来的片段解析不到该别名。

**【陷阱】站名回填的历史行只补内存、NEVER UPDATE 回填历史数据**（`GateTxnPayQueryServiceImpl.toListDTO`，`service/impl/GateTxnPayQueryServiceImpl.java:342~344`）：2026-09-10 之前落库的历史行 `COUNTING_TIMES` / `COUNTING_FLAG` 是 NULL，展示时按「非日票」补 0 / N，只覆盖历史行。「**NEVER 改成 UPDATE 回填历史数据**——无法区分『当时是非日票』与『当时漏写』。」站名同理：查不到的保持 null、由前端回退显示编码（`:363~365`、`:278`）。

**【陷阱】离线码汇总的独立卡数 MUST 单独查**（`GateTxnPayMapper.countOfflineSummary`，`mapper/GateTxnPayMapper.java:130`；`resources/mapper/GateTxnPayMapper.xml:689~692`）：跨车站去重，**NEVER 对分组行的 `cardCount` 求和**（同一张卡可能在多个车站刷过离线码）；`stationCode` / `stationName` 恒为空表示汇总行。车站取 `NVL(OUT_STATION, IN_STATION)`：离线码以出站交易落库，个别异常行只有进站站。

**【陷阱】重算再次失败时 NEVER 改 `DEBIT_STATUS`**（`GateTxnPayMapper.updateOfflineFarePendingMsg`，`mapper/GateTxnPayMapper.java:338~339`；`resources/mapper/GateTxnPayMapper.xml:734~737`）：「置 FAIL 会让订单进终态、补偿再也捞不到，置 SUCCESS 则是资损。」`markOfflineFarePending` 返回 0 行时 **MUST 记日志、NEVER 把 0 行当成「原因已留痕」** —— 改造前本方法返回 void、0 行被静默吞掉，运维会以为库里能查到失败原因（`docs/domain/outbox.md` §二，`writer/GateTxnPayWriter.java:162~164`）。

**【陷阱】批量退款结果 NEVER 退化成二值**（`BatchRefundResult`，`model/page/BatchRefundResult.java:9~11`）：批量语义是「逐单走单笔退款链路、单票失败不阻断整批」，结果必须能表达部分成功（汇总计数 + 逐笔明细含失败原因），**NEVER 退化成「整批成功/失败」的二值** —— 运营需要知道哪几笔没退成、为什么，再决定是否补退。单批上限 `MAX_BATCH_SIZE=200` 是防长事务/超时的硬闸（逐单各发起一次支付中心 RPC，`model/page/BatchRefundOvertimeRequest.java:8~12`）。补数结果行**只回显定位与金额字段、NEVER 回显整行订单快照**（`service/impl/OriginalFareBackfillServiceImpl.java:115`）。

**【陷阱】原价补数的可疑差额阈值挡的是「金额按元上送」脏数据**（`service/OriginalFareBackfillService.java:27~30`；`model/page/OriginalFareBackfillRequest.java:25`）：实付 > 0 且「原价 - 实付」超过 `suspectDiffCents`（默认 300 分）的行跳到 `suspectList` 不回填 —— **已发生：设备 206377 的 5 笔测试数据 `TRX_AMOUNT` 是元、原价是分，回填后优惠虚高 4~7 元**。实付为 0 时不比对差额（免扣费与日票的实付本来就是 0，差额等于原价、不是脏数据，`service/impl/OriginalFareBackfillServiceImpl.java:75`）。单次条数默认 500、上限 5000，**每条都要调一次 para-server，NEVER 设过大**（`model/page/OriginalFareBackfillRequest.java:19`）。

**【陷阱】`countFailedOrder` 与 `countUnsettledOrderByCardId` 的结清口径 MUST 同步改**（`mapper/GateTxnPayMapper.java:239~241`）：两者口径完全一致（`DEBIT_STATUS` 非 `SUCCESS`），区别只是后者**不带渠道、不带时间下限** —— `BLACKLIST` 表没有渠道字段、拉黑也不区分渠道，因此判定必须覆盖该卡的全部历史欠费。**改动其中任一处的结清口径 MUST 同步另一处。**

### 墓碑注释清单（建议转为断言测试）

下列注释的唯一作用是**禁止把某个已迁走 / 已删除的东西加回来**，不承载正向知识，故不进正文。

| # | 文件:行号 | 想禁止的事 | 能否写成断言测试 |
|---|---|---|---|
| 1 | `gate-txn-pay-server/src/main/java/com/chinasofti/huateng/gatetxnpay/service/impl/OfflineFareRecoveryProcessor.java:22~23` | 「**NEVER 在这里加回 `@Scheduled`**」——2.0.73 起本类调度移到 web-admin `sys_job`（`gateTxnPayQuartzTask.recoverOfflineFare()`，cron `0 0/1 * * * ?`），两套调度源互不知情会并发发起扣款 | **能**。反射扫本模块 `src/main` 全部类的方法，断言 `@Scheduled` 注解数为 0；或字节码级扫 jar。稳定、无需数据库 |
| 2 | `.../service/impl/OfflineFareRecoveryProcessor.java:23` | 「**NEVER 删 `sys_job` 那行**」——删了离线码补偿彻底停摆 | **不能**（纯断言层面）。`sys_job` 是运行库数据、且 Quartz 用内存 JobStore；只能做运维巡检脚本（查 `SYS_JOB_LOG` 有无该 job 的近期记录），不适合单测 |
| 3 | `.../service/impl/OfflineFareRecoveryProcessor.java:29` | 「**`concurrent` 那一列 NEVER 改成 `'0'`**」——一改就是并发重复扣款 | **不能**在本模块内断言（该列在 web-admin 的 `sys_job` 表里）。可在 web-admin 侧加一条启动校验或库内巡检 SQL |
| 4 | `.../service/impl/MetroTransferPushTaskProcessor.java:22~23` | 「**NEVER 在这里加回 `@Scheduled`**」——两套调度源会把同一条任务推两次 | **能**，与 #1 同一个断言覆盖 |
| 5 | `.../service/impl/MetroTransferPushTaskProcessor.java:27` | 「要调回更密 MUST 改 `sys_job` 的 cron，**NEVER 改回模块内 `@Scheduled`**」 | **能**（同 #1 的断言即可覆盖「模块内没有 `@Scheduled`」这一半） |
| 6 | `.../service/impl/MetroTransferPushTaskProcessor.java:31` | 「不重叠只靠 `sys_job.concurrent='1'` 保证，**那一列 NEVER 改成 `'0'`**」 | **不能**，同 #3 |
| 7 | `.../mapper/GateTxnPayMapper.java:74~75` | 「本方法是 2026-09-16 从 face-pay-server 迁入的 —— 那边原先自己持有一份同形 UPDATE，直写本模块 owner 的表。**NEVER 在 face-pay 侧加回任何对 `GATE_TXN_PAY` 的写语句**」 | **能**（跨模块静态检查）：断言 `face-pay-server/src/main/resources/mapper/**` 与 `src/main/java/**` 中不出现 `UPDATE GATE_TXN_PAY` / `INSERT INTO GATE_TXN_PAY` / `GATE_TXN_PAY` 写语句。宿主放在 face-pay 侧更自然 |
| 8 | `.../resources/mapper/GateTxnPayMapper.xml:210~212` | 同 #7 的 SQL 侧原文：「本语句原先是 face-pay 自己持有的一份副本（跨域直写 owner 不是它的表），随本次收口迁入本模块，**NEVER 在 face-pay 侧加回**」 | **能**，与 #7 同一个断言 |
| 9 | `.../mapper/GateTxnPayMapper.java:66~70`、`.../writer/GateTxnPayWriter.java:107~110`、`.../resources/mapper/GateTxnPayMapper.xml:198~205`、`.../service/GateTxnPayService.java:61` | 「**两条的白名单本就该不同，NEVER 合并成一条**」/「**NEVER 把两个方法合并**」/「**NEVER 把两条合并成一条**」 | **能**：断言 `GateTxnPayMapper.xml` 中 `convergeDebitStatus` 的 `DEBIT_STATUS IN` 列表**不含** `'FAIL'`、`convergeDebitStatusForSupplement` 的**含** `'FAIL'`（解析 XML 文本即可，无需数据库）。已有先例 `MapperSqlWallCompatibilityTest` 就是这种纯文本/解析器级断言 |
| 10 | `.../fare/FareDataGateway.java:95~96` | 「**NEVER 把这两个方法合成一个**」（`queryTicketPrice` 抛异常 vs `queryTicketPriceQuietly` 吞异常） | **能**：断言 `FareDataGateway` 上同时存在两个方法，且用 mock gateway 依赖验证「一个抛、一个返 null」的行为差异 |
| 11 | `.../paysign/GatePayRequestFactory.java:17`、`.../paysign/AlipayTripPayRequestFactory.java:15`、`.../paysign/PaySignInitiator.java:56` | 「**NEVER 把两套配置合并成一组**」/「**NEVER 把它们合并回来**」（`orderTimeOut` 一边秒一边分钟） | **部分能**：可断言两个工厂类各自存在、且 `PaySignInitiator` 的构造参数里没有 `@Value`；单位语义本身无法断言 |
| 12 | `.../service/impl/MetroTransferPushTaskProcessor.java:184~186`、`:201~202`、`.../service/impl/GateTxnPayServiceImpl.java:56` | 「**NEVER 各自复制一份判定**」/「**NEVER 把这个开关判断挪到两个调用方里**」 | **部分能**：可断言两个调用方（`GateTxnPayServiceImpl`、`OfflineFareRecoveryServiceImpl`）源码中不出现 `isWalletVendor` / `isBluetoothChannel` / `isCompanionOrThirdParty` 的组合判定与 `wallet.metro-transfer-enabled` 字面量 |
| 13 | `.../station/StationNameBackfiller.java:27` | 「ticket-server 那份 `fillStationNames` 保留作为兜底，**NEVER 因为本类存在就把它删掉**」 | **能**（跨模块）：断言 `ticket-server` 中 `GateTxnPayRequestAssembler.fillStationNames` 方法仍存在 |
| 14 | `.../controller/GateTxnPayController.java:29~30`、`.../controller/app/GateTxnPayAppController.java:25~27` | 「**拆分只动文件归属，17 个端点的 URL 一个字符都没改**，上游全是硬编码 URL，**NEVER 借后续重构改路径**」/「**NEVER 借重构改任何一个字符**，也 NEVER 把某个端点挪到别的前缀下」 | **能**：写一份「端点 URL 黄金清单」测试，反射扫全部 `@RestController` 的 `@RequestMapping` + `@PostMapping` 拼出全集，与固定清单逐字比对。这是本清单里**收益最高**的一条 |
| 15 | `.../service/GateTxnPayQueryService.java:27~29`、`.../service/impl/GateTxnPayQueryServiceImpl.java:41~42`、`.../service/GateTxnPayService.java:20` | 「**NEVER 往本接口加带写入或 RPC 的方法**」/「**NEVER 往本类注入 writer / client 类协作者**」/「**NEVER 把只读查询加回本接口**」 | **能**：断言 `GateTxnPayQueryServiceImpl` 的构造参数只有 `GateTxnPayMapper` 一个，且其字段类型集合中不含任何 `*Client` / `*Writer` |
| 16 | `.../service/impl/OriginalFareBackfillServiceImpl.java:24~26`、`.../service/impl/GateTxnPayManualOpsService.java:38`、`.../paysign/PaySignInitiator.java:32`、`.../service/impl/OfflineFareRecoveryServiceImpl.java:35`、`.../service/GateTxnPayService.java:41` | 「**NEVER 给本类加 `@Transactional`**」（五处同源，理由都是事务内禁 RPC）/「**NEVER 注入 `GateTxnPayWriter` 或 `PaySignClient`**」 | **能**：断言这五个类（及其方法）上没有 `@Transactional` 注解；`OriginalFareBackfillServiceImpl` 额外断言其字段类型不含 `GateTxnPayWriter` / `PaySignClient` |
| 17 | `.../service/impl/OfflineFareRecoveryServiceImpl.java:82~84` | 「**NEVER 退回裸 for**」（补偿循环 MUST 走 `OutboxScan`） | **部分能**：可断言该方法源码中出现 `OutboxScan.run`；「不许写裸 for」本身不易断言 |
| 18 | `.../service/impl/MetroTransferPushClient.java:58`、`:68`、`.../service/impl/OfflineMetroTransferClient.java:29` | 「**MUST 用 `application/x-www-form-urlencoded`，NEVER 改回 JSON body**」/「**NEVER 往 bizData 里加 `cardType`**」/「**NEVER 回退成 JSON body 平铺四个字段**」 | **能**：用 mock 的 `ProxyWebClient` 断言两个 client 调的是 `postFormAndGetResponse`（不是 `postJsonAndGetResponse`），并断言 bizData 的键集合恰好等于约定的五个 / 四个、**不含 `cardType`** |
| 19 | `.../service/ReconExportService.java:52`、`:55`、`:261~263`、`:358`、`:364`、`.../resources/mapper/ReconExportMapper.xml:26~31` | 「本源标识 **NEVER 改**」/「21 段 **NEVER 增减**」/「**NEVER 把 join 加回来**」「**NEVER 改成用车站代码前 2 位推线路**」/「『发售』类型的行 **NEVER 在本模块产出**」/「**NEVER 拿 `SIGN_CHANNEL_CODE` 判日票**」/「新增 select **MUST 把主表别名写成 `T`**」 | **能**（大多为文本级断言）：断言 `SOURCE == "gate-txn-pay"`、`PAY_SEGMENT_COUNT == 21`、`ReconExportMapper.xml` 中不出现 `STATION_INFO` / `LINE_CODE`、每条 `<select>` 的 `FROM GATE_TXN_PAY T` 形态 |
| 20 | `.../resources/mapper/DiscountLevelMapper.xml:23~35`、`:4~15` | 「**NEVER 写单数 `ROW ONLY`**」/「**NEVER 退回裸 resultType 自动映射**」 | **已有**：注释自己点名 `MapperSqlWallCompatibilityTest` 即该断言；resultMap 那条可断言该 mapper 的 `<select>` 均用 `resultMap` 而非 `resultType` |
| 21 | `.../constant/GateTxnPayFieldCode.java:15~21`、`.../constant/DebitStatus.java:6~10`、`.../constant/DiscountCalcStatus.java:9` | 「**NEVER 把 `FareCalculator` 里的 `01`/`02`/`03` 搬进本类**」/「**NEVER** 把 `COUNTING_FLAG` 与 `COMPANION_FLAG` 合并」/「**NEVER** 拿 `DebitStatus` 表示 `DISCOUNT_CALC_STATUS`」/「**NEVER 混用**」 | **部分能**：可断言 `GateTxnPayFieldCode` 的常量集合恰好是那 6 个、`DebitStatus` / `DiscountCalcStatus` 的枚举常量集合固定；「不许混用」需靠调用点静态扫描，成本较高 |
| 22 | `.../mapper/GateTxnPayMapper.java:305~306`、`.../resources/mapper/GateTxnPayMapper.xml:638~641`、`:702~707`、`:734~737` | 「`ORIGINAL_FARE IS NULL` **NEVER 去掉**」/「两个状态条件 **NEVER 去掉**」/「两个站名列 **NEVER 删**」/「**NEVER 在此改 `DEBIT_STATUS`**」 | **能**（XML 文本断言）：断言 `updateOriginalFareIfNull` 的 WHERE 含 `ORIGINAL_FARE IS NULL`、`updateOfflineFareRecalculated` 的 WHERE 含两个状态条件且 SET 含两个站名列、`updateOfflineFarePendingMsg` 的 SET **不含** `DEBIT_STATUS` |
| 23 | `.../resources/mapper/GateTxnPayMapper.xml:425~426`、`:468` | 「两个条件互斥，**NEVER 同时拼**」 | **能**：以 `cardType` + `cardTypeList` 同时非空构造入参，断言生成的 SQL 只出现 `IN (...)`、不出现 `CARD_TYPE =` |
| 24 | `.../resources/mapper/GateTxnPayMapper.xml:502~514` | IF8A-41 四条「**NEVER**」（`NVL(...,0)` / 只 `SUM(TRX_AMOUNT)` / 减数用 `TOTAL_AMOUNT` / `OVERTIME_AMOUNT` 映到 `totalDiscount`） | **能**：XML 文本断言四个表达式的确切形态；更好的是接一个内存库/黄金数据集做数值回归 |
| 25 | `.../resources/mapper/GateTxnPayMapper.xml:387~396` | 「`test` 里字符串 **MUST 用双引号**」（OGNL char 字面量坑） | **能**：断言该 mapper 全部 `test` 属性中不出现单引号包裹的多字符/数字字面量比较；顺带可加一条**跨模块**巡检覆盖注释点名的 `alipay-pay-sign-server/AlipayPayLogMapper.xml`（该处**尚未核实**） |

补充说明：#1 / #4 / #5 三条其实是**同一个断言**（本模块 `@Scheduled` 数为 0），落一条即可；#14 的「端点 URL 黄金清单」与 #9 的「两条 converge 白名单差异」是本清单里**投入产出比最高的两条**，都不需要数据库。

### 没有把握归类的注释

| 文件:行号 | 为什么拿不准 |
|---|---|
| `gate-txn-pay-server/src/main/java/com/chinasofti/huateng/gatetxnpay/writer/GateTxnPayWriter.java:183~185` | 注释称「本模块当前未注入 `management.tracing.enabled`（走 `web.properties:78` 默认 `false`）」，而 AGENTS.md §2.2.1 记 `gate-txn-pay-server` **自 2.0.60 起已打开 tracing**。二者至少有一方过期 —— 若 tracing 确已打开，这段就从「防御性冗余」变成「**唯一在生效的防线**」，归类应从「决策理由」改为「陷阱」。本阶段只读代码、未查 `application.properties` 与集群 env，故不归类 |
| `.../service/impl/GateTxnPayServiceImpl.java:44`（`FareCalculator` 字段注释「不再读 `offline.billing.*` 三个配置项」）、`:62~71`（`GateTxnPayManualOpsService` 字段注释）、`:74~77`（`StationNameBackfiller` 字段注释） | 形态是「某段已搬到某类」的迁移说明，既像决策理由（为什么拆）又像墓碑（禁止搬回）。多数没有显式 NEVER，因此未列入墓碑清单；正文只摘了带 NEVER / MUST 的部分 |
| `.../service/impl/GateTxnPayQueryServiceImpl.java:38`、`.../service/impl/OriginalFareBackfillServiceImpl.java:21~22` | 「方法体逐行搬来、**行为一字未改**」「`OriginalFareBackfillTest` 的 8 条断言在搬动前后完全相同，那就是证据」—— 是重构留证，不是契约也不是陷阱，且已无对照物可验 |
| `.../constant/DiscountCalcStatus.java:6~8` | 提到 `SUPPLEMENT_ORDER.PAY_STATUS`「**尚未收口**，仍是 `SupplementOrderServiceImpl` / `SupplementConvergeService` 里的裸字面量（按用户 2026-09-14 裁决暂不动）」。但这两个类**在本次扫描的 45 个文件里不存在**（补款已于 2026-09-15 迁往 face-pay-server），因此这段注释指向的对象可能已不在本模块 —— 是「过期注释」还是「跨模块指路」无法判定 |
| `.../resources/mapper/GateTxnPayMapper.xml:387~396` 末句 | 「alipay-pay-sign-server 的 `AlipayPayLogMapper.xml` 就是这种写法，**需另行核实**」—— 注释自身声明未核实，既是陷阱线索也是待办项，未单列 |
| `.../config/AsyncConfig.java:10`、`.../entity/DiscountLevel.java:5`、`.../entity/MetroTransferPushTask.java:5`、`.../entity/GateTxnPay.java:7`、`.../service/impl/WalletAppGatewayClient.java:17`、`.../mapper/DiscountLevelMapper.java`、`.../mapper/MetroTransferPushTaskMapper.java`（无注释） | 单行类说明，介于「普通 Javadoc」与「弱契约」之间（如 `WalletAppGatewayClient` 的「按 ITP-App 网关协议调用，**不是内部 RPC 调用**」含一点边界信息）。按丢弃规则处理，此处仅登记 |
| `.../entity/GateTxnPay.java:34~59` | 26 行字段行尾注释是**列取值字典**（`TICKET_STATUS` 01/04/05/06/07/70、`ORDER_EXP_TYPE` 0/1/2、`PAYMENT_VENDOR` 0B、`CHANNEL_TYPE` 01=蓝牙、`TRANSFER_FLAG` 01/02、`CUMULATIVE_TYPE` 01/02/03 等）。既是契约（取值域）又像字段说明；正文中只在被 NEVER 引用处摘取（如 `DISCOUNT_LEVEL_AMT` 语义），未整表搬入 |
| `.../service/ReconExportService.java:180~190`、`.../resources/mapper/ReconExportMapper.xml:97~111` | 「甲方 1~15 与我方 0~5 的映射待澄清」既是陷阱（猜错会改变账目性质）也是**对甲方的待澄清项**（AGENTS.md §2.2.2 已记同一条）。正文按陷阱收录，但它本质是外部依赖未闭合，归类可争议 |
