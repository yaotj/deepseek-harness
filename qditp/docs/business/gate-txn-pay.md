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

## 附：gate-txn-pay-server 源码注释知识抽取（2026-09-16，阶段二）

阶段一只覆盖了 `src/main/java/**` 与 `src/main/resources/mapper/*.xml`；**本轮补的是它没进过的四类载体**：①`src/main/resources/application.properties`（**169 行里 85 行是注释，本模块注释知识密度最高的单个文件、阶段一一条没抽**）；②`src/main/resources/sql/*.sql`（7 个脚本、约 120 行 `--` 注释）；③`src/test/java/**`（11 个测试类、约 513 行注释，其中 `arch/GateTxnPayGuardTest` 是**把注释约束固化成构建失败**的唯一实现）；④阶段一按「样板」丢弃或列进「没有把握归类」的 controller 端点级 Javadoc、`entity` 字段行尾取值字典、`model/page` DTO 注释。`pom.xml` 只有 2 行注释。

本轮**不重复阶段一任何一条**，凡阶段一已收录的（`DebitStatus` / 两条 converge / 离线码补偿 / 换乘推送 / mapper 与分区表 / 对账 SQL 口径）此处只在「同一事实有新旁证」时以一行交叉引用出现。定位串格式同阶段一。

### 六、controller 层（端点级 Javadoc，阶段一只抽了类级）

**【契约】17 个端点的 URL 是冻结面，三个 controller 的类注释各写一遍**（`controller/GateTxnPayController.java:29~30`「拆分只动文件归属，17 个端点的 URL 一个字符都没改」；`controller/app/GateTxnPayAppController.java:25~27`「类上前缀是 `/ci/gateTxnPay/app`，与拆分前逐字相同 … 也 NEVER 把某个端点挪到别的前缀下 —— 上游是硬编码 URL 调过来的」）。**「17 个」这个数字只出现在注释里**，是拆分当时的总数（本类 6 + app 6 + page 若干 + internal），**MUST 以 `@PostMapping` 实数为准、NEVER 引用这个数**。

**【契约】本类 6 个端点里只有 3 个会改状态**（`controller/GateTxnPayController.java:32~33`「`requestPay` / `retryPay` / `syncDebitStatus` 会改状态，其余三个只读」）。这是给「加鉴权 / 加限流时先保护谁」用的分档，与 §三那条「`/page/**` 与 `/internal/**` 当前无鉴权」配套看。

**【契约】两个 service 并列注入不是两套实现**（`controller/GateTxnPayController.java:44~45`「同一张 `GATE_TXN_PAY`，按『有没有写』拆成两个接口，端点与报文一个都没变」）。读到 controller 里同时有 `gateTxnPayService` 与 `gateTxnPayQueryService` 时 **NEVER 以为是新旧并存**。

**【契约】IF8A-41 的票种白名单与 `cardType → cardTypeList` 展开在 ticket-server 侧完成**（`controller/app/GateTxnPayAppController.java:81~82`）：本模块只按已展开的 `cardTypeList` 查，**NEVER 在本模块补一份票种白名单** —— 那会与上游的展开规则各自演化。口径原文挂在 `model.app.TripDataDTO` 类注释上（跨模块，改口径 MUST 同时读那份）。

**【契约】IF8A-35 的结果「只用于展示」**（`controller/app/GateTxnPayAppController.java:112~113`）：「调用方 MUST 先判断 retCode 再用两个数量；本接口结果**只用于展示**，NEVER 拿它替代过闸或解约链路各自的欠费校验」。统计范围由 `app.acc-info.query-months`（默认 3）决定（`application.properties:153`）—— 也就是**超过 3 个月的欠费不进这两个数字**，而解约校验走的是 `hasFailedOrder`、没有月份窗，两者天生不等。

**【契约】`/page/**` 三个查询端点的日期窗都是必填，理由是分区裁剪**（`controller/page/GateTxnPayPageController.java:40`「服务层要求订单标识或完整日期范围防止全表扫描」、`:56` 与 `:63`「日期窗必填（yyyy-MM-dd，闭区间）」）。注意**同一模块里两种日期格式并存**：`/page/**` 入参是 `yyyy-MM-dd`，而 `GATE_TXN_PAY.TXN_DATE` 与原价补数入参是 `yyyyMMdd`（`model/page/OriginalFareBackfillRequest.java:9~10`），转换点在 service 层，**NEVER 把某个端点的格式「统一」掉**。

**【契约】批量退超时罚金：单批 ≤ 200、逐单走单笔链路、金额取各自 `OVERTIME_AMOUNT`**（`controller/page/GateTxnPayPageController.java:74`；`model/page/BatchRefundOvertimeRequest.java:8~9`「逐单各发起一次支付中心 RPC，笔数越多接口占用越长，200 是防长事务/超时的硬闸。运营要退更多需分批提交」，常量 `MAX_BATCH_SIZE`（`:12`）「超出直接拒绝」）。结果对象 `BatchRefundResult` 的 `total` / `success` / `fail` + 逐笔明细「长度恒等于 total」（`model/page/BatchRefundResult.java:14~20`），其中 `success` 的语义是「**仅指支付中心受理，不代表已退到账**」（`:16`）、`fail` 含「圈单后被单笔白名单/金额校验拦下的」（`:18`）—— 即**圈单查询与单笔校验是两道闸，圈进来不等于退得掉**。

**【契约】原价补数的 `dryRun` 默认 true，且回填只写空值行**（`controller/page/GateTxnPayPageController.java:94~95`「确认 `updatedList` 与 `suspectList` 后再传 `dryRun=false` 落库。回填只写空值行，重复调用幂等」；`model/page/OriginalFareBackfillRequest.java:19` 「单次最多处理条数，默认 500，上限 5000。每条都要调一次 para-server，NEVER 设过大」、`:25` 「可疑差额阈值（分），默认 300。实付大于 0 且『原价 - 实付』超过该值时跳过并列入 suspectList」、`:28` `force` 默认 false）。为什么需要这个接口：`ORIGINAL_FARE` 只在出站时写一次，**para-server 查不到票价时留空且没有补偿任务**（`model/page/OriginalFareBackfillRequest.java:6~7`）—— 这是本模块**唯一一条明确写着「没有补偿任务、只能靠人工接口补」**的数据缺口。

**【契约】离线码统计视图不含任何个人信息字段，汇总行复用同一类型**（`model/page/OfflineCodeStatView.java:4~7`「只读聚合 `OFFLINE_FLAG='Y'` 的行，不含任何个人信息字段」「`stationCode` / `stationName` 为空即汇总」；`:10` 车站编码「出站站，缺省时回落进站站」、`:12` 站名「由 `STATION_INFO` 补」）。**判「这行是明细还是汇总」MUST 看两个站字段是否为空，NEVER 靠行序或额外标志位**。
### 七、internal 层与调度归属（本轮只补阶段一没说清的三处）

**【契约·纠正】`CompensationInternalController` 现在是 3 个端点，`/internal/gate-txn-pay/**` 下没有补款「下单」端点、但有补款「收敛」端点**（`controller/internal/CompensationInternalController.java:47~52` 与 `:82~99`）。AGENTS.md §2.2.1 写的「该 Controller 只有这两个端点，没有补款端点」**在 2026-09-16 起只对一半**：补款**下单**（IF8A-26）确实整体迁到 face-pay-server、本模块没有（`controller/GateTxnPayController.java:110~113`、`controller/app/GateTxnPayAppController.java:30`），但同日新增了第三个端点 `POST /internal/gate-txn-pay/debit/converge`（补款支付成功后收敛原行程扣费状态，由 face-pay 同步调用）。**排查「谁能改 `DEBIT_STATUS`」MUST 按 3 个端点算**，见下面「矛盾与待裁决」#1。

**【陷阱】类注释开头「本模块两条补偿链路的外部调度入口」与类内第三个端点自相矛盾**（`controller/internal/CompensationInternalController.java:15` vs `:48~51`）。第三个端点的方法注释自己点明了这一点：「与上面两个 Processor 不同：这条**不是补偿批处理**，而是由 face-pay-server 在单笔补款支付成功后同步调用的收敛入口，**只是恰好共用本类的 `/internal/gate-txn-pay` 前缀**」。因此**按类注释估算「本模块有几条补偿链路」会少算，且会误以为第三个端点也恒返 `0000`**（它恰好相反）。

**【契约】调度归属的墓碑现在有构建期防线，不再只靠注释**（`src/test/java/com/chinasofti/huateng/gatetxnpay/arch/GateTxnPayGuardTest.java:18~34`）。该类把两条约束固化成会失败的测试：①**本模块不得有任何 `@Scheduled`**（「ADR-D80 起调度全部外移：补款任务连类整体迁到 face-pay-server，离线码补偿与公交换乘推送迁到 web-admin 的 `sys_job` **120 / 121**」，「加回一个既不编译失败也不告警，只会让同一件事在两处各跑一份」）；②**两条 converge 语句的状态白名单必须不同**（「补款专用那条含 `FAIL` … 支付回调那条不含 … 谁把两条合并成一条，这个测试就红」）。判定「只读源码与 mapper 文本，不起 Spring、不连库」，且**一律在剥离注释后的内容上做** —— 因为那些 NEVER 告示本身就写着被禁止的写法与取值（`:33~34`，`:50` 有 `commentLine` 的实现痕迹）。**注释里的 `sys_job` 号 120 / 121 与 AGENTS.md 一致，但 cron 不在代码里、MUST 查 `sys_job` 表。**

**【墓碑·反向】这个断言 NEVER 扩成连 `@EnableScheduling` 一起禁**（`arch/GateTxnPayGuardTest.java:24~27`）：「启动类上那个开关不是死代码 —— `resource/micro` 里有 3 个 `@Scheduled`（`SqlConfiguration` 与 `ResetMetersJob` 的 Prometheus 指标重置与打印），删掉开关会把那三个监控任务一并停掉。2026-09-16 本测试第一版就是这么写的，当场被自己抓到」。**这条是本模块唯一记录了「作者自己踩中并当场修正」的门禁设计细节**，改动该测试或清理启动类注解前 MUST 先读它。

**【契约】两个 Processor 类仍在本模块、但只剩「被 HTTP 调用的一轮」语义**（`service/impl/OfflineFareRecoveryProcessor.java`、`service/impl/MetroTransferPushTaskProcessor.java`，阶段一 §三 / §四已抽其返回值约定）。本轮补一条**配置侧证据**：两个原 `@Scheduled` 的周期配置键仍在 properties 里但**已无读取方**，见「墓碑清单」#1 / #2 —— 即「改了 `*-poll-ms` 却没生效」的原因写在配置文件本身，不在代码里。

### 八、entity 与列取值字典（阶段一列进「没有把握归类」，本轮按契约收）

**【契约】`GATE_TXN_PAY` 的六个状态/标志列取值域（实体行尾注释原文）**（`entity/GateTxnPay.java:34~59`）：`TICKET_STATUS` `01` 无交易 / `04` 进站失败 / `05` 已进站 / `06` 已出站 / `07` 超时出站 / `70` 异常（`:34`）；`ORDER_EXP_TYPE` `0` 正常 / `1` 单边 / `2` 补站（`:37`）；`COMPANION_FLAG` 「陪同票标志：`Y` 是,`N` 否（来自 `USER_ITP_REG_INFO`）」（`:38`）；`OFFLINE_FLAG` `Y`/`N`（`:39`）；`COUNTING_FLAG` 「计次票标志：`Y` 是,`N` 否」（`:43`）；`CHANNEL_TYPE` 「交易渠道类型，`01`=蓝牙」（`:48`）。**这三个取值域与库注释、与 `GateTxnPayFieldCode` 的白名单都不完全一致，逐条冲突见「矛盾与待裁决」#2~#6，改代码前 MUST 先看那一节、NEVER 只信实体注释。**

**【契约】钱包优惠六列的单位与语义**（`entity/GateTxnPay.java:52~58`）：`ORIGINAL_FARE` 「进出站地铁原价，单位分」、`WALLET_TOTAL_AMT` 「钱包当前累计金额，单位分」、`DISCOUNT_LEVEL_AMT` 「**命中的累计金额门槛**，单位分」（不是折后金额）、`DISCOUNT_RATE` 「命中的折扣率」（`BigDecimal`）、`EXPECTED_GATE_AMOUNT` 「按折扣公式计算的期望闸机金额，单位分」、`DISCOUNT_CALC_MSG` 「钱包优惠计算说明或降级原因」。**全部单位是分、`DISCOUNT_RATE` 是唯一非整型**；排查「优惠算错」MUST 先分清 `DISCOUNT_LEVEL_AMT`（门槛）与 `EXPECTED_GATE_AMOUNT`（期望实付）。

**【契约】商户与渠道四列的取值**（`entity/GateTxnPay.java:44~47`、`:49`）：`ATTRIBUTABLE_PARTY` / `RECEIVING_PARTY` 都是「`cjdsj`/`qddt`」两值（应收 / 实收商户，**两列可以不等**，那正是跨商户对账要查的）；`PAY_CHANNEL_CODE` 「如 `ALIPAY`、`WECHAT`，来自 `USER_ITP_REG_INFO.CHANNEL`」；`PAYMENT_VENDOR` 「支付厂商编码，**钱包为 `0B`**」；`PAY_USER_ID` 「钱包扣款用户标识」。注意 `PAY_USER_ID` 与钱包累计查询要送的 `thirdUserId` **不是一个东西**（`application.properties:88~91` 实测：送 `PAY_USER_ID` 返 `7001 找不到对应的数据`），见 §九。

**【契约】`INDUSTRY_DETAIL` 的落库理由在迁移脚本里写得比实体注释全**（`entity/GateTxnPay.java:59` 「支付宝出行行业明细JSON（21键），仅 `issueChannelCode=07` 有值，落单时整块存下、扣费与重试复用」；`sql/gate-txn-pay-industry-detail-migration.sql:3~13`）：21 键里 **9 项本表没有列**（`entryLineCode` / `entryLineName` / `exitLineCode` / `exitLineName` / `entryDeviceCode` / `entryId` / `exitId` / `cardNum` / `cardIssueCode`），由 fep-dev-server 在出站当次经**三路并行 RPC** 组装（para 查站线、ticket 查进站设备、按 `itpUserId`+时间戳算 `entryId`/`exitId`），「出站是唯一能拿到这些值的时点」。列长 `VARCHAR2(4000 CHAR)` 的定量依据：键名约 250 字符、仅 4 项中文，典型报文 800~1000 字节。**NEVER 改成 CLOB** —— Oracle `COUNT(<CLOB 列>)` 非法，且经 `mcp_database_qd` 查询时会报成通用 cast 错误、看不出真实成因。
### 九、config 与 application.properties（本模块注释知识密度最高的文件，阶段一未涉及）

**【契约】异步扣费线程池的形状与拒绝策略**（`config/AsyncConfig.java:10~24`）：`paySignAsyncExecutor` core 4 / max 16 / queue 1000 / 线程名前缀 `pay-sign-async-` / 拒绝策略 `CallerRunsPolicy`。**`CallerRunsPolicy` 意味着队列满时出站扣费会退化成在请求线程上同步调 pay-sign** —— 与 AGENTS.md §5.2「NEVER 在请求线程上做长时间阻塞的 DB / IO」（虚拟线程 pin）撞在一起；改这里 MUST 连带评估那条。类注释只有一行「用于 pay-sign 异步补偿更新」，**这四个数字与拒绝策略在任何文档里都没有，只在这个文件里**。

**【契约·tracing】本模块打开 tracing 的三行是成组的，且理由写在注释里**（`application.properties:9~24`）：`management.tracing.enabled=true` + `sampling.probability=0` + `spring.autoconfigure.exclude=...OtlpAutoConfiguration`。注释给出三条**别处没有的判据**：①打开的直接动因是**对账链路**「recon-server 下发 `/internal/recon/export` 时带来的 W3C `traceparent` 才能被续接进 MDC，否则按 `sys_job_log`（job 109 日终对账）的 traceId 检索本模块的抽取日志会 0 条 —— 对账链路是 web-admin → recon-server → 本模块，少一环就断在这里」；②**本模块没有自带 log4j2 配置、走公共 `log4j2-linux.xml`**，其 pattern 已含 `%X{traceId}`，「只差这个开关」；③排除那行 **NEVER 删**，三个理由是「`sampling.probability=0` 只让本服务发起的 trace 不采样，采样器是 `parentBased(traceIdRatioBased(0))`，**上游带 `sampled=1` 的头进来时 span 仍会被采样并进导出队列**」「`micro/web` 的 `web.properties` 已把 endpoint 整行注释掉，本行是第二道保险 —— Deployment 只要注入 `MANAGEMENT_OTLP_TRACING_ENDPOINT` 就会重新激活 exporter」「Boot 3.2.6 **没有** `management.tracing.export.enabled` 这个开关，把 endpoint 置空也不行（`OtlpAutoConfiguration` 只判断键是否存在），只能排掉整个自动配置」。

**【契约】`service.*.url` 默认值的书写规则与三条「键缺失会怎样」的实测**（`application.properties:36~62`）：默认值一律用集群内网 Service 名，「**NEVER 再写 127.0.0.1** —— 在 K8s 里等于打到自己，且键缺失时 rpc 会退化成默认服务名 `*-service`、DNS 解析不到」；「各 Service 端口不统一：有的等于容器 `server.port`（account 9098 / ticket 9100），有的等于 NodePort 号（para 30026 / recon 30034），**照抄实测值、NEVER 按 `server.port` 推断**」。三条键缺失的**具体症状实测**：`service.ticket.url` 缺失 ⇒ 离线码出站整笔失败，2026-09-11 实测 `requestPay` 返 `8002`「Failed to resolve 'ticket-service'」、`GATE_TXN_PAY` 零订单、APP 乘车记录看不到行程（`:46~48`）；`service.alipay-pay-sign.url` 缺失 ⇒ 落到 `http://alipay-pay-sign-server:8080`、解析不到，**`ISSUE_CHANNEL_CODE=07` 的单全部收敛成 `RETRY`**（`:51~53`）；`service.collectPay.url` 缺失 ⇒ 落到 `collect-pay-service`，「每笔补款下单都返『补款单正在准备支付入口』而补偿轮轮失败 —— NEVER 删」（`:56~60`，**该注释是补款迁走前的现状，与 `:155` 那行「本服务不再读取任何 `supplement.*` 配置」并存**，见「矛盾与待裁决」#7）。

**【陷阱】`.properties` 里的中文 MUST 写 `\uXXXX` 转义**（`application.properties:136~141`）：「Spring Boot 的 `OriginTrackedPropertiesLoader` 按 ISO-8859-1 读 `.properties`，UTF-8 中文会被解成 mojibake 并**原样送到支付中心**」。落地形态是 `gate.pay.subject` / `gate.pay.body` 与 `alipay.trip.subject` / `alipay.trip.body` 四行都写转义（`:71~72`、`:140~141`，均为「地铁乘车扣费」「地铁乘车费用」）。**改支付主题文案时 NEVER 直接敲中文** —— 编译、启动、单测都不会报错，只有对端账单上能看出乱码。

**【契约】支付宝出行三个固定字段与「NEVER 与 gate.pay.\* 合并」的理由**（`application.properties:66~73`）：`alipay.trip.scene=TRIP` / `payment.vendor=05` / `industry.type=1` / `order.timeout.minutes=60`。理由原文：「`order.timeout.minutes` 是**分钟**（`gate.pay.order-timeout-seconds` 是**秒**），`payment.vendor` 也只对支付宝有意义」——与阶段一记的「两个报文工厂 NEVER 合并」是同一件事的配置侧证据。注意 `gate.pay.scene=AGM_GATE`、`gate.pay.order-timeout-seconds=60`（`:134`、`:142`）：**两个渠道的超时值都写 60，但单位差 60 倍**，看起来像「一致」实则不是。

**【陷阱】钱包累计金额查询的路径修正与「404 能当判据」的方法论**（`application.properties:76~95`）：原值 `/app/queryTotalAmt`「**从来没通过一次**」，恒返 404 ⇒ `FareCalculator.calculateWalletDiscount` 每笔掉进 catch、置 `DISCOUNT_CALC_STATUS='FALLBACK'` 按闸机原始金额扣款，**全库 8 笔 `PAYMENT_VENDOR='0B'` 单实测零条 `SUCCESS`、`DISCOUNT_RATE` / `WALLET_TOTAL_AMT` / `DISCOUNT_LEVEL_AMT` 全 NULL**；正确路径 **`/ci/app/v2/queryTotalAmt`**（与 `receiveSignResult` / `employeeCardSync` 同族前缀），实测 `{"thirdUserId":"00522943"}` → `{"totalAmt":0,"retCode":"0000"}`。方法论一条 **MUST 记住**：「该网关**不会对不存在的方法一律 404**（对照组 `/ngpayment-gateway/api/v1/contract/queryResult` 用 GET 返 200 + `code=1001`），因此**在这个网关上「GET 返 404」等于路径不存在**，可以拿来排除路径猜测」。另三条实测：**bizData 里只有 `thirdUserId` 是关键字段**（`cardType` / `msisdn` / `cardIssueCode` / `extend1` / `extend2` 送不送都返 `0000`）；`thirdUserId` **MUST 是 8 位 ITP 用户号**（`GATE_TXN_PAY.THIRD_USER_ID`），**NEVER 送支付宝 uid 或 `PAY_USER_ID`**（实测 `7001 找不到对应的数据`）；`multipart/form-data` 对端接受、不必改成 `x-www-form-urlencoded`。**仍未闭合：`totalAmt=0` 而该用户我方有 8 笔扣费，这个 0 是不是真值待接口方确认**（`:93~94`）。

**【陷阱】公交卡系统两个地址的端口与上下文路径修正 —— 「代码与对端文档一致」不能当判据**（`application.properties:100~121`）：两个 URL 原本都写 `8980`、推送那条还漏 `/buscard` 上下文。「**这不是我方笔误**：对端开发期给的接口文档原文写的就是 `8980` + `/busApi/2App/v1/pushMetroTran`，代码是照文档写的 —— 是**对端后来把服务挪到 8885 + `/buscard` 上下文、文档没同步**。因此『代码与对端文档一致』**不能当作地址正确的判据**，联调前 MUST 现探。」探路方法：**MUST 用 GET**，「GET 返 405『方法不允许』即路径存在、只收 POST」（8885 上两个路径都 405，8980 上全 404）；「**探路 NEVER 用 POST** —— POST 会真给公交卡系统推一条行程、可能真发换乘优惠」。改对后推送返 `{"retCode":"1002","retMsg":"接收地铁交易数据失败null"}`（HTTP 200，链路已通、对端业务侧仍在拒；`retMsg` 尾巴的 `null` 是对端拼错误消息时拼进去的空变量）。**同一份数据换三种报文形态（bizData 字符串 / 嵌套对象 / 平铺六字段）回的完全一样，因此 NEVER 靠改报文结构去试**。**查询那条的坑更隐蔽：路径一直对、只端口错，而失败后 `FareCalculator` 走「非钱包/算不出减免」分支、不像推送那样往 `LAST_ERROR` 留痕，于是一直静默失效、没人发现。**

**【契约】两个公交卡 client 的超时注入方式有反例警告**（`application.properties:100~103`）：两者 2026-09-14 起继承 `ProxyWebClient`（拿连接池与统一日志），「两个 `*-timeout-ms` 经**构造器**传给 `responseTimeout`（**NEVER 靠覆写 `getResponseTimeout()` 读注入字段**，那个方法在父类构造期就被调、那时子类字段还是 0）」。两个 URL 都是**完整地址**，「既当 baseUrl 又当请求 URL，WebClient 对带 host 的绝对地址直接用、不会二次拼接」。

**【契约】离线码计费的三个可调参数**（`application.properties:143~145`）：`offline.billing.timeout-seconds=1200`（20 分钟判超时）、`timeout-fee-cents=300`、`transfer-reduction-cents=100`。阶段一记过「`calculateOfflineFare` 的公式 `(票价 - 减免) * 折扣率`」，**这三个数值只在这里**；改它们等于改实收金额，MUST 与业务确认。另注意 `GateTxnPayServiceImpl.java:44` 的字段注释写着「不再读 `offline.billing.*` 三个配置项」（该类已把读取权交给 `FareCalculator`），**别据那句以为配置废弃了**。

**【契约】离线码补偿扫表的三个参数与开关**（`application.properties:146~151`）：`gate.pay.offline-fare-recovery-enabled`（默认 true，关掉即 Processor 返 `-1`）、`batch-size=50`、`lookback-days=7`。**`lookback-days=7` 是硬约束**：超过 7 天的待重算单**扫不到、也没有第二条补偿路径**（本模块没有任何按订单号手动重算的端点），因此「离线码金额长期为 0」的单**过了 7 天只能人工处理**。

**【契约】`InternalMicroHttp` 的日志级别被本模块单独压到 WARN，理由是令牌明文**（`application.properties:167~169`）：「`InternalMicroHttp` 会把整个请求头 Map 直接打进 INFO 日志，其中包含内部令牌 `X-Recon-Token` 的明文。公共构件 `resource/micro` 不改，改这里把该 logger 压到 WARN，避免令牌落进日志文件与日志采集。」**这行是「令牌鉴权恢复后仍然有效的防线」**，即便当前 `X-Recon-Token` 已删；**NEVER 因为「令牌都没了」把这行删掉**（恢复鉴权时会立刻又开始泄露）。

**【契约】数据库连接与分页走 `other.sql.*`，本模块单数据源**（`application.properties:26~32`）：`other.sql.double-datasource=false`、`type=oracle`、`page.type=oracle`，`DB_HOST` 默认 `172.20.222.3:1521`、`DB_NAME` 默认 `AFCITPDB`，账号口令走 `${DB_USERNAME:}` / `${DB_PASSWORD:}` 形态（**默认值是真值、属 AGENTS.md §5.2 敏感配置的例外现状，本轮只记录不改**）。`mybatis.mapper-locations=classpath*:mapper/*.xml`（`:34`）—— 新增 mapper XML **MUST** 放 `resources/mapper/` 下，放别处静默不加载。
### 十、sql 脚本与月分区表（7 个脚本，阶段一未涉及）

**【陷阱】`DISCOUNT_CALC_STATUS` 列宽 16 → 32 那次是「最后防线 100% 失效」**（`sql/gate-txn-pay-discount-calc-status-widen-migration.sql:1~20`）：`saveOfflineFarePendingOrder` 写入的 `'OFFLINE_FARE_PENDING'` 是 **20 字符**，而列原为 `VARCHAR2(16 CHAR)` ⇒ 整条 INSERT 被 Oracle 拒（`ORA-12899 ... 实际值: 20, 最大值: 16`），后果是「落单留痕待补偿」这条最后防线全废：**本次出站在 `GATE_TXN_PAY` 零痕迹**，而补偿靠 `DEBIT_STATUS='INIT' AND DISCOUNT_CALC_STATUS='OFFLINE_FARE_PENDING'` 扫表，行都没落进去、**永远扫不到、无法自愈**。实测样本：卡号 …095 在 18:28:44 与 18:30:23 两笔离线码出站，闸机侧已放行（ticket-server 返 `0000`、`QRCODE_STATUS` 已推进 `81 → 05`），但 `GATE_TXN_PAY` 当日 0 行。**为什么改列宽而不改常量**：那个字面量在 `GateTxnPayMapper.xml` 有 3 处硬编码 WHERE（`:585` / `:618` / `:632`；`:612` 的 SET 是参数、不含字面量），改常量要同步 3 处 WHERE + Java 写入点，「漏一处即『重算捞不到』或『重算完状态对不上』」；加宽列**不改代码、不重建镜像、不重启、对已有数据无损**。回查实测 `CHAR_LENGTH=32 / DATA_LENGTH=64`。**回退语句仅在本列无超过 16 字符的数据时才能成功，NEVER 在已产生 `OFFLINE_FARE_PENDING` 后回退。**

**【契约】`gate-txn-pay-schema.sql` 已按库内实际结构整体对齐，且长度一律以 `CHAR_LENGTH` 为准**（`sql/gate-txn-pay-schema.sql:6~11`）：2026-09-14 用 `USER_TAB_COLS` + `USER_COL_COMMENTS` 实测回填，「**NEVER 用 `DATA_LENGTH` 判长度**，库字符集下 8 字符 = 16 字节，曾据此误记过 `TXN_DATE` 是 `VARCHAR2(16)`」。对齐动作：补进 13 列钱包优惠 / 换乘列（`PAYMENT_VENDOR` ~ `DISCOUNT_CALC_MSG`），**删掉库里并不存在的 3 列**（`PAY_CHANNEL_CODE` / `DISCOUNT_FEE` / `DISCOUNT_INFO`，全仓零代码引用），约 18 列长度改小、`CARD_TYPE` 由 NOT NULL 改可空；索引与分区实测与文件一致。**注意 `PAY_CHANNEL_CODE` 在实体里有字段（`entity/GateTxnPay.java:46`）而 schema 说库里没这列**，见「矛盾与待裁决」#8。

**【契约】月分区维护的现成语句与「P_MAX 只是兜底」**（`sql/gate-txn-pay-schema.sql:1~4`、`:207~259`）：`TXN_DATE` 用字符串 `YYYYMMDD` 入库、按出站交易日**手动**维护月分区，`P_MAX` 仅兜底。脚本尾部给了 6 段现成 SQL：`SPLIT PARTITION P_MAX AT ('20270201') INTO (P202701, P_MAX)` 形态的新增分区、`USER_TAB_PARTITIONS` 查分区、`DROP PARTITION P202606 UPDATE INDEXES` 删历史（「执行前务必确认历史数据已归档」）、以及两段**推荐查询写法**（按卡号 / 按 `DEBIT_STATUS IN ('FAIL','RETRY')` 都带 `TXN_DATE >= :beginDate AND TXN_DATE < :endDate`）。**「手动维护」意味着不加新分区时未来数据全落 `P_MAX`** —— 不报错、只是分区裁剪失效，`P_MAX` 越滚越大。**这条运维动作在 `docs/ops/` 里没有，只写在这个脚本尾部。**

**【契约】五个索引的用途注释**（`sql/gate-txn-pay-schema.sql:100~149`）：`ORDER_NO` 唯一索引「同时作为调用支付接口的 `orderNo`」且**「Oracle 本地唯一索引必须包含分区键，因此包含 `TXN_DATE`」**（这解释了阶段一那条「`selectByOrderNos` 不带 `TXN_DATE` 只命中前缀列」）；过闸交易幂等唯一索引「同一笔出站/超时出站交易只允许生成一笔扣费订单」；另三个按卡号 / 用户 / 扣费状态 / 设备（排查「设备重复上送、漏传」）。**按卡号那条注释明确「查询时应带 `TXN_DATE` 范围以触发分区裁剪」。**

**【陷阱】站名存量补齐脚本：只补空值、名与码不同源的行只盘点不改**（`sql/gate-txn-pay-station-name-backfill.sql:1~28`、`:53~68`）：历史行有两种坏形态 ——「①站名为空（列表侧回落显示站点编码，即用户看到的 `0622`）；②站名非空但与本行编码不同源（名是 `FFFF` 时代的，码是重查后的）」。站名唯一权威来源是 `TBL_STATION_INFO.STATION_NM WHERE PARA_VER_NO = (SELECT CURRENT_VER_NO FROM TBL_PARA_VERSION WHERE PARA_TYPE='0001')`，**列名是 `STATION_NM` 不是 `STATION_NAME`**（写错会被 MCP 报成通用 cast 错误、看不出是列不存在）。执行顺序 **MUST 1 → 2 → 3，第 4 步是独立决策、NEVER 与第 2 步一起跑**；两条 UPDATE 都带 `IS NULL` 前置故可重复执行，「**NEVER 去掉 `IS NULL`** —— 那会把非空站名一起覆盖，而原值无处可查、不可回滚」「**NEVER 用 `NVL(..., IN_STATION)` 之类的兜底把编码写进名字列** —— 列表侧本来就有回落逻辑，把编码写进名字列等于让『有没有真站名』这件事再也分辨不出来」。2026-09-15 在 `AFCITPDB` 实跑：第 1 步与第 4 步盘点都 0 行（64 行全部两列齐备且与 v41 一致），第 2 步当时是 no-op —— 「**这不代表脚本没用**，换库或换账期后 MUST 重新跑第 1 步再判断」。

**【契约】`SUPPLEMENT_ORDER_ITEM.ACTIVE_ORIG_ORDER_NO` 的独占语义与「SUCCESS 刻意不释放」**（`sql/supplement-order-active-orig-migration.sql:1~16`、`:36~45`）：非 NULL 表示「本明细仍在独占该原订单」，**Oracle 唯一索引不约束全 NULL 行**，故置 NULL 即释放。释放点只有**非成功的三处终态**（重下作废 CLOSED / 超时关单 CLOSED / 支付失败 FAIL）；「**SUCCESS 刻意不释放**：钱已收，无论 `settleOrigOrders` 是否命中，该原订单都 NEVER 允许再被补款」。成因是 2026-09-14 代码审查 P0：两条并发请求都查不到旧单、都建单成功（`ORDER_NO` 带毫秒 + 卡号后 6 位撞不上），两行 `TBL_TVM_APP_ORDER` 都 `PAY_STATUS='0'`、**收银台两张单都能付 ⇒ 同一笔欠费收两次钱**；「Java 侧的 `selectPendingOrderNosByOrigOrderNos` 是 check-then-act，挡不住真并发」。同批补 `IDX_SUPPLEMENT_ORDER_UPDT`（收敛任务改内联视图后按 `UPDATE_TIME` 回看 CLOSED/FAIL 单，原 `IDX_SUPPLEMENT_ORDER_STATUS` 帮不上）。回查实测：列 `VARCHAR2(128) NULLABLE=Y`、两索引 VALID、回填 7 行且 `COUNT=COUNT(DISTINCT)=7`（ORA-01452 防线通过）。**这三个脚本连同 `SUPPLEMENT_ORDER` 的 outbox 四列仍在本模块，但补款功能已迁 face-pay**，见「矛盾与待裁决」#7。

**【决策】补款登记 collect-pay 那步「有意偏离 §5.2 先远端后本地」，裁决人与理由都在脚本注释里**（`sql/supplement-order-sale-sync-migration.sql:1~15`、`:24~26`）：两种失败方向代价不对称 —— 先远端后本地时「collect-pay 里留下一行 `PAY_STATUS='0'` 的**可支付孤儿订单**，乘客付得进去、我方却没有补款单去收敛这笔钱 —— **资损方向**」；先本地后远端时「乘客暂时付不了款，扫表补偿重推即可自愈，**无资损**」。故改用 outbox，前缀 `SALE_SYNC_`，列形状与状态取值「全部照 `docs/domain/outbox.md` 模板，**NEVER 另起一套列名或状态值**」。**扫表谓词 `SALE_SYNC_STATUS IN ('PENDING','FAILED')`，历史行为 NULL 天然扫不到 —— 这是模板要求的行为：NEVER 把 NULL 兜底成 PENDING，否则本功能上线那一刻会把改造前的全部补款单一次性推给 collect-pay。**

### 十一、测试侧防线（阶段一完全未涉及；本模块 11 个测试类共约 513 行注释）

**【契约】`MapperSqlWallCompatibilityTest` 守的是「Oracle 合法、Druid WallFilter 却判成注入」这一类静默失效**（`src/test/java/com/chinasofti/huateng/gatetxnpay/MapperSqlWallCompatibilityTest.java:19~32`）：被拦下的语句抛 `SQLException: sql injection violation`，「而编译、`xmllint` 与普通单测都发现不了 —— 只在 Oracle 运行时炸，且**往往被上层 catch 成业务降级（如钱包优惠落 `FALLBACK`），表面看起来一切正常**。因此这类约束 MUST 有静态防线」。2026-09-15 实测到的第一例：**`FETCH FIRST 1 ROW ONLY`（单数）被判注入，复数 `ROWS ONLY` 才通过**（`:27~28`、`:32` 「Oracle 接受、druid 1.2.23 的 Oracle 解析器不接受」）。**这就是阶段一 `DiscountLevelMapper.xml` 那条「MUST 写复数」的构建期防线所在，改 mapper 后 MUST 跑这个测试、NEVER 只跑 `xmllint`。**

**【契约】守卫测试对「注释即约束」的处理方式**（`arch/GateTxnPayGuardTest.java:33~34`、`:50`、`:80`、`:90`）：判定一律**在剥离注释后的内容**上做（因为 NEVER 告示本身就写着被禁止的写法与取值）；取 mapper 语句体时「**id 必须精确匹配，避免前缀包含关系误取**」（`convergeDebitStatus` 是 `convergeDebitStatusForSupplement` 的前缀，按 `contains` 取会取错那条 —— 这正是两条 converge 断言最容易写错的地方）；目标状态从 `SET DEBIT_STATUS = 'X'` 文本里取。**新增同族语句（如再来一条 `convergeDebitStatusForXxx`）MUST 同步这个测试，NEVER 只加 SQL。**
### 矛盾与待裁决

> 一律**只记录、不修改代码**（本轮硬约束）。「代码实际」列为本轮实读结论；同一行涉及多份注释时以「注释 A / 注释 B」并列。

| # | 注释说什么 | 代码 / 库实际是什么 | 证据（文件:行） | 建议裁决 |
|---|---|---|---|---|
| 1 | AGENTS.md §2.2.1：「`CompensationInternalController` **只有这两个端点，没有补款端点**」 | 该类现有 **3 个** `@PostMapping`：`/offline-fare/recover`、`/metro-transfer/push`、**`/debit/converge`（2026-09-16 新增，补款支付成功后收敛，face-pay 同步调用）**。没有的是补款**下单**端点（IF8A-26 已迁 face-pay） | `controller/internal/CompensationInternalController.java:47~52`、`:82~99`；`controller/GateTxnPayController.java:110~113` | 把 AGENTS.md 那句改成「没有补款**下单**端点，但有补款**收敛**端点 `/debit/converge`」。这条不是文档洁癖：该端点**能把任意订单号的 `DEBIT_STATUS` 直接改成 `SUCCESS`，等于免单**（`:93~94`），漏记它等于漏掉一个资金写入面 |
| 2 | 类注释：「本模块**两条**补偿链路的外部调度入口」 | 类内 3 个端点，第三个自称「**不是补偿批处理**」且**不恒返 `0000`、不吞异常** | `CompensationInternalController.java:15` vs `:48~51`、`:85~91` | 改类注释首行为「两条补偿链路 + 一条单笔资金收敛入口」。保持三个端点在同一类可以，但注释 MUST 说清第三个的语义相反 |
| 3 | 类注释：「与 pay-sign-server 的 **7 个** `/internal/**` 端点、recon-server 的 `/internal/recon/daily/run` 同一形态」 | pay-sign-server 现为 **10 个**（`/internal/termination/**` 6 + `/internal/paySign/**` 2 + `/internal/payment/**` 2，后两个 2026-09-15 新增） | `CompensationInternalController.java:19`；AGENTS.md §2.2.1 | 把跨模块计数从注释里删掉（改成「与 pay-sign-server / recon-server 的 `/internal/**` 同一形态」）。**跨模块数量写进注释必然过期**，这是第二次踩 |
| 4 | 实体：`COUNTING_TIMES` 「计次票**剩余**次数（扣减后）」 | 库注释「计次票**剩余可用**次数」；而 AGENTS 侧与阶段一记的是「**本次行程消耗次数**，非日票恒 0、日票恒 1，NEVER 是剩余次数」；代码实际是「取上游上送值，日票缺省 1」 | `entity/GateTxnPay.java:42`；`sql/gate-txn-pay-schema.sql:14~17`；`service/impl/GateTxnPayServiceImpl.java:308~313`、`:372` | **MUST 与业务确认后再定稿**，schema 注释已明确写「两种解读都能套上，NEVER 据本行注释直接改写入逻辑」。定稿前 NEVER 依赖这一列做次数对账 |
| 5 | 库注释：`COUNTING_FLAG` 「`1`=计时,`2`=计次」 | 代码走 `Y`/`N`（查询侧兜底 `'N'`），实体注释也是 `Y`/`N` | `sql/gate-txn-pay-schema.sql:18~19`；`entity/GateTxnPay.java:43`；`service/impl/GateTxnPayQueryServiceImpl.java:264` | **取值域以代码为准，库注释疑似过期**（schema 文件已如此判定）。建议改库列注释，NEVER 反过来改代码 |
| 6 | 实体：`TICKET_STATUS` 六值（`01/04/05/06/07/70`）、`ORDER_EXP_TYPE` 三值（`0/1/2`）、`COMPANION_FLAG` 「陪同票 `Y`/`N`」 | 库：`TICKET_STATUS` 还有 `02` 进站 / `03` 进站超时 / `80` 自助补出站；`ORDER_EXP_TYPE` 是 `0~5` 六个值；`COMPANION_FLAG` 库叫「**同行票**」，且 `GateTxnPayFieldCode` 的白名单是 **`{Y,C}`**（不含 `N`） | `entity/GateTxnPay.java:34`、`:37`、`:38`；`sql/gate-txn-pay-schema.sql:20~22`；`constant/GateTxnPayFieldCode.java:8~21` | 三条都以**库 + `GateTxnPayFieldCode`** 为准，实体行尾注释**不完整**。`COMPANION_FLAG` 尤其要紧：按实体注释以为「非 `Y` 即 `N`」会漏掉 `C`，而 `C` 在白名单内、影响换乘推送的排除判定 |
| 7 | properties：「IF8A-26 在线补款单已整体迁移到 face-pay-server（2026-09-15），**本服务不再读取任何 `supplement.*` 配置**」 | 同一文件 `:56~61` 仍在解释 `service.collectPay.url` 「每笔补款下单都返『补款单正在准备支付入口』…… NEVER 删」；`sql/` 下仍有 3 个 `supplement-order-*.sql`（含 `SUPPLEMENT_ORDER` 建表与两组迁移）；`DiscountCalcStatus` 注释还指向本模块已不存在的 `SupplementOrderServiceImpl` / `SupplementConvergeService` | `application.properties:155` vs `:56~61`；`sql/supplement-order-{schema,active-orig-migration,sale-sync-migration}.sql`；`constant/DiscountCalcStatus.java:6~8` | 裁决「`SUPPLEMENT_ORDER*` 两张表与其 DDL 的 owner 模块是谁」。**NEVER 直接删这三个脚本**（表在库里、face-pay 在用），但 MUST 在文件头写清 owner 已变，否则下一个人会在本模块继续加列 |
| 8 | `gate-txn-pay-schema.sql`：库里**不存在** `PAY_CHANNEL_CODE` 列（与 `DISCOUNT_FEE` / `DISCOUNT_INFO` 一并删除，理由「全仓零代码引用」） | 实体仍有 `payChannelCode` 字段且带注释「来自 `USER_ITP_REG_INFO.CHANNEL`」 | `sql/gate-txn-pay-schema.sql:10`；`entity/GateTxnPay.java:46` | 现查一次 `USER_TAB_COLS`：若库里真没有，实体那个字段属**死字段**（resultMap 不映射即恒 null），MUST 删或补列；**NEVER 在不确认的情况下往 SQL 里加它**（会 `ORA-00904`） |
| 9 | properties：`wallet.metro-transfer-enabled` 默认 **true**（2026-09-17 / 2.0.90 起，ADR-D134；**此前是 `false`，NEVER 回退**） | 阶段一记「2.0.77 起该开关等于『本功能是否启用』，关闭期间连任务都不建」 | `application.properties:57`；`service/impl/MetroTransferPushTaskProcessor.java:145~157` | 两条合起来才是完整事实：**开启是常态、关闭需要显式注入 `WALLET_METRO_TRANSFER_ENABLED=false`**。之所以翻默认值：此前线上仅靠手工 `kubectl set env` 兜着（2026-09-17 现查 Deployment 里根本没这个 env、跑的是 jar 内 `false`），**一次 apply Deployment 就会静默回落、只留一行 INFO「本单不建推送任务」**。判断线上是否开启仍 **MUST 查 Deployment env**，但**默认值已站在「更容易被发现」那一侧** |
| 10 | properties：钱包累计查询实测 `{"thirdUserId":"00522943"}` → `totalAmt=0` | 该用户我方有 8 笔扣费，**`0` 是不是真值未确认** | `application.properties:93~94` | 向接口方确认口径（是否只统计某类交易 / 是否按渠道隔离）。**未确认前 NEVER 把 `totalAmt` 当作对账依据**，它只喂折扣档位命中 |
| 11 | properties：公交卡推送改对地址后仍返 `retCode=1002`「接收地铁交易数据失败null」 | 链路已通、**对端业务侧仍在拒**；三种报文形态回的完全一样 | `application.properties:113~116` | 属**外部依赖未闭合**，已请接口方查服务端异常栈。**NEVER 在我方改报文结构去试**（阶段一同款结论）；换乘推送上线判据 MUST 是对端返 `0000` |
| 12 | `DB_USERNAME` / `DB_PASSWORD` 写了**默认真值** | AGENTS.md §5.2 要求敏感项写 `${ENV:}` 空默认值由 Secret 注入 | `application.properties:31~32` | 上线前 MUST 清空默认值。本轮只记录（改配置属改运行时行为，且与本轮「只写 docs」约束冲突） |
### 墓碑清单（阶段二新增，与阶段一那 25 条不重复）

> 「墓碑」= 唯一作用是**禁止把某个已迁走 / 已废弃 / 已实测错误的东西加回来**，不承载正向知识。

| # | 文件:行 | 想拦住的事 | 载体 |
|---|---|---|---|
| 1 | `src/main/resources/application.properties:126~129` | 「【2.0.73 起已废弃、无读取方】原为 `MetroTransferPushTaskProcessor` 的 `@Scheduled(fixedDelayString)` …… **保留本行只为让『改了这个值却没生效』的人看到原因，NEVER 靠改它调频率**」 | 配置注释（`wallet.metro-transfer-poll-ms` 仍在文件里、值 10000） |
| 2 | `src/main/resources/application.properties:148~149` | 同上，`gate.pay.offline-fare-recovery-poll-ms`「已废弃、无读取方，改由 `sys_job`「离线码金额补偿」cron `0 0/1 * * * ?` 控制」 | 配置注释（值仍是 60000） |
| 3 | `src/main/resources/application.properties:155` | 「IF8A-26 在线补款单已整体迁移到 face-pay-server（2026-09-15），**本服务不再读取任何 `supplement.*` 配置**」——整行是墓碑，下面没有任何键 | 配置注释（**空占位**，防止有人再往本模块加 `supplement.*`） |
| 4 | `src/main/resources/application.properties:161~163` | `recon.internal-token`「2026-09-11 起 `X-Recon-Token` 校验已按用户要求整段删除，本键**已无任何读取方**，不需要注入。保留只为便于日后恢复鉴权（上线前 MUST 恢复）」 | 配置注释 + 留空的键 |
| 5 | `src/test/java/.../arch/GateTxnPayGuardTest.java:24~27` | **反向墓碑**：「本断言只管 `@Scheduled`，**NEVER 扩成连 `@EnableScheduling` 一起禁**」（`resource/micro` 里有 3 个 `@Scheduled` 靠那个开关，删了会停掉 Prometheus 指标重置与打印）。附「2026-09-16 本测试第一版就是这么写的，当场被自己抓到」 | 测试类 Javadoc（**唯一有构建期效力的墓碑**） |
| 6 | `src/test/java/.../arch/GateTxnPayGuardTest.java:21~23` | 「本模块不得有任何 `@Scheduled`」+「加回一个既不编译失败也不告警，只会让同一件事在两处各跑一份」 | 测试断言（把阶段一 #1/#4/#5 三条注释墓碑变成了红灯） |
| 7 | `src/main/resources/sql/gate-txn-pay-discount-calc-status-widen-migration.sql:19~20` | 「回退语句仅在本列无超过 16 字符的数据时才能成功，**NEVER 在已产生 `OFFLINE_FARE_PENDING` 后回退**」 | SQL 注释（回退语句被注释掉、不可直接执行） |
| 8 | `src/main/resources/sql/gate-txn-pay-industry-detail-migration.sql:12~13` | 「**NEVER 改成 CLOB**」——Oracle `COUNT(<CLOB>)` 非法 + MCP 会报成通用 cast 错误 | SQL 注释 |
| 9 | `src/main/resources/sql/gate-txn-pay-station-name-backfill.sql:26~28`、`:68` | 「NEVER 去掉 `IS NULL` 条件」「NEVER 用 `NVL(..., IN_STATION)` 把编码写进名字列」「**NEVER 把下面这条盘点 SQL 直接改成 UPDATE**」 | SQL 注释（第 4 步刻意只留 SELECT） |
| 10 | `src/main/resources/sql/supplement-order-sale-sync-migration.sql:25~26` | 「历史行该列为 NULL、天然扫不到，这是 outbox 模板要求的行为：**NEVER 把 NULL 兜底成 PENDING**，否则本功能上线那一刻会把改造前的全部补款单一次性推给 collect-pay」 | SQL 注释 |
| 11 | `src/main/resources/sql/supplement-order-active-orig-migration.sql:13` | 「**SUCCESS 刻意不释放**：钱已收 …… 该原订单都 NEVER 允许再被补款」 | SQL 注释（语义型墓碑，防止有人「顺手」把 SUCCESS 也加进释放点） |
| 12 | `pom.xml:102`、`pom.xml:124` | `<goal>deploy</goal>` 与 `<outputDirectory>target</outputDirectory>` 被整行注释掉 —— 即**本模块 `mvn package` 会 `build` + `push` 但不会 `deploy`**，jar 落在镜像根目录 `/app.jar` | XML 注释（`pom.xml` 全文仅这 2 行注释；与 AGENTS.md §7「`build-image-after-package`」一致，`<version>` 现为 **2.0.88**，**线上跑哪版 MUST 现查 Deployment**） |

### 本轮覆盖率自评

**分母（实测口径，2026-09-16）**：`src/main` 46 个文件 / 6913 行中，**Java 注释行 1682**（按「行首 `*` / `/*` 或行内含 `//`」计，含 26 行实体行尾注释）、**XML/properties 注释行 179**（其中 `application.properties` **85**、四个 mapper XML 合计 92、`pom.xml` **2**）；另有 `src/main/resources/sql/*.sql` **约 120 行 `--` 注释**（7 个脚本，阶段一与本轮统计口径此前都未含它，本轮补计）与 `src/test` **约 513 行注释**（11 个类）。

**本轮抽取**：正文新增条目 **34 条**（§六 controller 9 / §七 internal 与调度 5 / §八 entity 4 / §九 config+properties 12 / §十 sql 7 / §十一 tests 3 —— 其中 §十 与 §十一 有 2 条互为交叉引用，去重后按 34 计）；**矛盾与待裁决 12 条**；**墓碑 12 条**。合计 **58 条**。

**样板跳过（统计但不抽）**：纯 `@param` / `@return` / `{@inheritDoc}` 约 **95 行**（集中在 `mapper/GateTxnPayMapper.java`、`service/ReconExportService.java`、`controller/internal/ReconExportController.java:42~43`）；getter/setter 分隔注释 **2 行**（`entity/GateTxnPay.java:33`、`:106`）；`sql/gate-txn-pay-schema.sql` 里**逐列 `COMMENT ON` 之外的示例 SQL 注释约 55 行**（分区维护与推荐查询，已按「运维动作」整段收进 §十一条，不再逐行抽）。

**零知识注释文件清单**（无注释或仅一行类名重复）：`GateTxnPayServer.java`（**0 行注释**，`@EnableRpc*` 装配靠注解自解释）、`mapper/DiscountLevelMapper.java`（0）、`mapper/MetroTransferPushTaskMapper.java`（0）、`resources/mapper/MetroTransferPushTaskMapper.xml`（0）、`sql/supplement-order-schema.sql`（**0 行 `--` 注释**，全靠 `COMMENT ON`）、`entity/DiscountLevel.java`（1）、`entity/MetroTransferPushTask.java`（1）、`service/impl/WalletAppGatewayClient.java`（1）、`model/page/GateTxnPayRefundRequest.java`（3，仅字段说明）、`config/AsyncConfig.java`（3，正文已按契约收其数值）。

**覆盖率结论**：按「带知识量的注释块」估算，阶段一 + 阶段二合计覆盖 **约 90%**；剩余未覆盖集中在三处 —— ①`src/test` 那 513 行里的**用例级注释**（每条断言在守什么，如 `PaySignInitiationTest` 104 行、`OfflineFareRecoveryTest` 68 行、`WalletTransferFlagInferenceTest` 61 行，本轮只抽了两个门禁类的类级注释）；②`mapper/GateTxnPayMapper.java` 189 行注释里被阶段一按「已收录同源」跳过的**逐方法 `@param` 说明中夹带的口径**（如各查询要求带 `TXN_DATE` 的具体理由）；③`sql/gate-txn-pay-schema.sql` 的**逐列 `COMMENT ON` 原文**（约 60 列，是列取值域的**库侧权威**，与实体行尾注释的冲突已在「矛盾」#4~#6 记录，但未逐列比对）。

## 附：gate-txn-pay-server 测试断言与 mapper 口径补漏（2026-09-16，阶段三）

本轮只补阶段二自评点名的**三处空白**，不重复阶段一 / 阶段二任何一条：①`src/test` 11 个文件的**用例级**口径（阶段二只抽了两个门禁类的类级注释）；②`mapper/GateTxnPayMapper.java` 夹在 `@param` / `@return` 中间的**口径说明**（不是 `@param x 参数x` 那种样板）；③`sql/gate-txn-pay-schema.sql` 约 60 行 `COMMENT ON` 原文，并用它**裁决阶段二「矛盾」#4~#6、#8 记下的取值域冲突**。

> ⚠️ **本节的行号是「抽取当时（删除前）」的坐标**。同一天随后已按「代码只留标准 Javadoc」把 `src/main` 与 `src/test` 的多行叙述型 / MUST-NEVER / 事故史 / 墓碑注释整体删除（只保留六组一行式护栏，清单见本节末「删除后仍在代码里的护栏」）。因此**本节是这些知识此后的唯一载体**，按行号回源码 MUST 用 `svn cat -r <删除前版本>`，**NEVER 因为现在源码里搜不到某句原文就判定本节记错**。

### 十二、`src/test` 逐类：每个测试钉住什么不变量（11 个文件）

**【门禁】`arch/GateTxnPayGuardTest`（116 行）是本模块唯一有构建期效力的约束**，两条断言：
- `moduleHasNoScheduledAnnotation`（`src/test/java/com/chinasofti/huateng/gatetxnpay/arch/GateTxnPayGuardTest.java:42~61`）遍历 `src/main/java` 全部 `.java`，**行首 trim 后 `startsWith("@Scheduled")` 且该行不是注释行**才算命中（`:50~52` 的 `commentLine` 判定）—— 正因为先排除注释行，Javadoc 里写「NEVER 加回 `@Scheduled`」不会让门禁自伤。失败信息原文「本模块的调度已全部外移（补款迁 face-pay、离线码与换乘迁 web-admin sys_job 120/121），NEVER 加回模块内定时任务」。
- `supplementConvergeWhitelistKeepsFailWhileCallbackOneDoesNot`（`:63~78`）三段断言：补款侧 `convergeDebitStatusForSupplement` 的语句体 MUST 含 `FAIL`（否则「放行下单 + 补款支付成功 + 收敛不了」，钱已实收而行程仍挂欠费）；回调侧 `convergeDebitStatus` MUST NOT 含 `FAIL`（FAIL 在那条链路里是已到达的终态、不该被回调改写）；补款侧 `targetStatus(...)` MUST 等于 `SUCCESS`（「目标状态写死 SUCCESS、不做入参，少一个入参就少一处传错状态的可能」）。
- **作者第一版把第一条断言扩成连 `@EnableScheduling` 一起禁，那是错的**（`:24~27`）：启动类上那个开关不是死代码 —— `resource/micro` 里有 **3 个 `@Scheduled`** 靠它（`SqlConfiguration` 与 `ResetMetersJob` 的 Prometheus 指标重置与打印），删掉开关会把这三个监控任务一并停掉。原文即「2026-09-16 本测试第一版就是这么写的，当场被自己抓到」。**NEVER 回退成禁 `@EnableScheduling`。**
- 两个技术前提：判定一律在**剥离注释后**的文本上做（`:33~34` 与 `stripXmlComments` `:99~115`，因为那些 NEVER 告示本身就写着被禁止的写法与取值）；`statementBody` 取语句体时 **`id` 必须精确匹配 `id="..."`**（`:80~88`）—— `convergeDebitStatus` 是 `convergeDebitStatusForSupplement` 的前缀，按 `contains` 取会取错那条，这是两条 converge 断言最容易写错的地方。

**【自反用例】`MapperSqlWallCompatibilityTest`（63 行）的两条**（类级已在阶段二 §十一收录，这里只补用例级）：`wallFilterRejectsSingularRowOnly`（`:36~43`）不扫代码、直接对 druid 本身断言 —— 单数 `FETCH FIRST 1 ROW ONLY` MUST 被 `WallUtils.isValidateOracle` 判非法、复数 `ROWS ONLY` MUST 通过；失败信息是「若本断言失败说明 druid 已能解析单数形式，可放宽下面的 mapper 扫描」，**这是本模块唯一一条「断言失败等于好消息」的用例，NEVER 因为看不懂就删**。`noMapperUsesSingularRowOnly`（`:45~62`）才是扫 `src/main/resources/mapper/*.xml` 的那条，正则见 `:32~34`。

**【SQL 口径】`mapper/GateTxnPayMapperSqlTest`（305 行）用 MyBatis `XMLMapperBuilder` 解析 mapper 并取渲染后的 SQL 文本、不连库**（`mapper/GateTxnPayMapperSqlTest.java:37~38`，因此本机与 CI 都能无条件运行），钉四组：
- ①**`DEBIT_STATUS` 三套并存判据，各有各的理由，NEVER 顺手统一**（`:25~29`）：解约与黑名单解除用**黑名单** `IS NULL OR != 'SUCCESS'`（Oracle 三值逻辑下漏掉 `IS NULL` 会把脏数据判成已结清、**放行解约**），两条 MUST 一字不差、否则同一笔订单两处结论相反（`:79~87`）；IF8A-35 用**白名单分档**、两档互斥且都不含 `SUCCESS` 与 `CLOSED`（`:89~99`）；状态推进用**显式前置白名单**、终态 NEVER 进白名单（`:125~140`）。
- ②**OGNL 里 `'0'` 是 char 字面量、与 String 比较恒为 false**（`:112~123`）：IF8A-05 的 `debitRequestResult` 过滤片段一旦写成单引号，**整段过滤静默失效**（`0` 走 SUCCESS、`1` 走带 NULL 的黑名单两支都渲染不出来），只有把 SQL 渲染出来比对才发现得了。
- ③**`OFFLINE_FARE_PENDING` 与 `DEBIT_STATUS='INIT'` 在三条语句里 MUST 成对**（捞单 / 抢占回写 / 失败留痕，`:31~32`、`:142~152`）：任一处掉了另一半，补偿要么捞不到、要么并发重复扣款。并且**剥离 XML 注释后该字面量只允许出现 3 次**（`:63~73`、`:165~175`，注释里为讲清语义会引用状态字面量，故计数前 MUST 先剥）—— 多一次即说明有人又抄了一份判据，改口径时必然漏改。配套两条：两条 UPDATE NEVER 改 `DEBIT_STATUS`（`:154~163`，置 FAIL 补偿再也捞不到、置 SUCCESS 是资损）；抢占语义靠 WHERE 里的状态条件、返回 1 才代表抢到，去掉即退化成无条件覆盖（`:186~195`）。
- ④三条全局形状：按主键定位的写语句 MUST 带分区键 `TXN_DATE`（`:34~35`、`:176~185`，漏掉即扫全部分区且跨月重名时改错行）；渲染出 `WHERE AND` 只在运行时抛语法异常，故对**全部**语句扫一遍（`:198~207`）；**渲染后的 SQL 正文里 NEVER 出现注释**（`:208~220`，Druid `commentAllow=false` 判成注入后语句静默失效，且达梦环境关了 WallFilter，只在 Oracle 暴露）。另有一条**测试自身的坑**：IF8A-41 统计语句以 `request.*` 取值，**缺这个键会渲染成不带任何过滤的全表扫描**（`:277~287`），断言前 MUST 先塞 `statisticsRequest()`。

**【只读侧】`service/impl/GateTxnPayQueryTest`（204 行）钉三处「参数被静默改写」**（`service/impl/GateTxnPayQueryTest.java:27~32`，共同特征是**出错不报错**：分页钳制失效只是查得多或查得少、窗口算错只是统计口径变了、DTO 补齐漏掉只是前端显示 null）：
- 四个搜索维度全空 MUST 在碰库之前拒绝，NEVER 落成全表分页扫描（`:44~52`）；只给开始日期不给结束日期同样算「没有范围」，两者 MUST 成对（`:53~61`）。
- 分页钳制：`pageSize` 上限 100、非法值回落 10、`pageNum` 下限 1 —— 「少了 `Math.min` 那一层，前台传 100000 就是一次百万行结果集」（`:62~67`）。
- IF8A-35 缺 `thirdUserId` MUST 返 `9002` 且不查库（`:94~101`）；统计窗口下限 MUST 是 `yyyyMMdd` 字符串且等于「今天减 N 个月」，配置非法（`<=0`）时回落 3 个月，**NEVER 退化成不加下限**（那会扫全部月分区，`:102~107`）。
- **聚合查询返回 null 时 MUST 返 `9002`**（`:133~150`）：`unpaidCount` / `failureCount` 是 **primitive int**，报错分支里它们照样是 0 —— 「**唯一**能让 APP 区分『无欠费』与『查询失败』的就是 retCode」，改成返 `0000` 会让 APP 把查询失败当无欠费、放行欠费乘客，而两个计数字段看不出差别。
- 列表 DTO 对历史行补齐 `COUNTING_TIMES=0` / `COUNTING_FLAG=N`（2026-09-10 之前落库的行这两列是 NULL，去掉补齐前台显示 null，`:151~173`）；订单号为空直接返 null、不查库（`:174~180`）。
- 另有一条**结构性断言**：构造器只收 `GateTxnPayMapper` 一个协作者（`:185~193`），「一旦有人把写入、算价或 pay-sign 调用挪进查询实现，构造器就得多收协作者、本方法立刻编译不过 —— 那正是要暴露的耦合」；`accInfoQueryMonths` 是 `@Value` 字段而非构造参数，单测只能反射注入（`:194~199`）。

**【换乘推送】`MetroTransferPushDecisionTest`（198 行）钉五个判定条件 + 一道功能开关**（`service/impl/MetroTransferPushDecisionTest.java:12~32`；建网的直接原因是这段逻辑同时被 `requestPay` 与离线码补偿调用而**此前一条测试都没有**，写反则「两条链路同时错」：少推乘客拿不到公交换乘优惠、多推给了不该给的，两边都不报错）：
- 五条件缺一不可（`:22~24`）：出站交易（`trxType` 02/03）、钱包渠道（`PAYMENT_VENDOR=0B`）、非蓝牙（`CHANNEL_TYPE != 01`）、非同行票（`!= Y`）、非第三方票（`!= C`）。**后三个是排除语义，写成 `equals` 正好反掉。**
- 渠道判定是 `equalsIgnoreCase(trimToNull(...))`、**NEVER 退回 `equals`**（`:68~88`）：渠道值来自闸机上送、经多层转发，实测存在大小写与首尾空格不一致。
- 功能开关 `wallet.metro-transfer-enabled` 关闭时**连任务都不建**（`:141~157`，2.0.77 起、用户 2026-09-15 要求）：此前它只拦投递不拦生成，关闭期间行程逐条堆成 `PENDING`，开关一开几天前的陈旧行程会一次性涌向公交卡系统，而**换乘优惠有时效、补推过期行程比不推更糟**。**NEVER 退回「只在 `processReadyTasks` 判开关」**；它与五个条件性质不同（五条件是「这笔该不该推」、开关是「本功能是否启用」），**NEVER 混成一个判断**。**2026-09-17 / 2.0.90 起该开关默认值已翻成 `true`**（含两处 `@Value` 的内联兜底，ADR-D134）：公交侧接收自 2026-09-15 17:40 前后已修好、端到端推送已实测通过（任务 `ID=1006` → `STATUS=SUCCESS`），且从 Pod 内实测 `172.20.202.10:8885` **TCP 可达**（**`decisions.md:2197` / `:3224` 那条「公交卡系统在测试环境不可达、外呼没验」已作废，NEVER 回退**）。连带一条：**关闭期间不建任务意味着开关打开后历史单不会被补推**，`ID=4~8` / `1002` / `1003` 那批 `FAILED`（对端当时返 `1002`）走的是 `BizRejected` **一次即终态**、**不会自愈**，要补只能人工。
- `OUT_TIME` 为 14 位时日期取前 8 位、时间取第 9~14 位（`:114~124`）；`OUT_TIME` 缺失时日期**回落 `TXN_DATE`、NEVER 改成取当日**（`:125~140`，与支付域按 `(ORDER_NO, TXN_DATE)` 关联的口径一致）。
- 可测性两条：被测类名只写在 `TARGET` 一行（`:35~37`），「搬家后只改 `TARGET` 与实例构造这两处，下面的断言值一行不动」；**`enabled` 是唯一必须传真值的构造参数**（`:172~178`），传 false 会让本类所有 `assertNotNull` 用例集体转红、而失败原因看起来像「判定条件写反了」——**排查本类集体转红 MUST 先看这里传的是什么**。

**【算价顺序】`OfflineFareCalculationTest`（329 行）钉离线码重算的四步顺序与超时费隔离**（`service/impl/OfflineFareCalculationTest.java:38~51`）：四步是**票价 → 超时费 → 换乘减免 → 钱包折扣**，「这个顺序无法从签名或类型看出来，改错也照样编译通过、照样落库，只是每一笔的金额都算错，属直接资损」。
- 用例特意选「减免 100 分、折扣 0.8」：**先减免后打折得 240、先打折后减免得 220、把超时费并进基数得 480** —— 三种写法结果互不相同，因此断言能真正区分顺序，**NEVER 把期望值改成「大于 0」之类的宽松判断**。
- 超时费只进 `OVERTIME_AMOUNT`，既不参与折扣基数也不并入 `TRX_AMOUNT`（`:102~106`，并进基数会算成 `(300+300)*0.8=480`、比正确值多收 240 分）。
- 同行票**不参与钱包累计折扣、但换乘减免仍然生效**（`:142~146`，两件事共用一个 wallet 分支、容易被一起跳过）；未命中减免时 `transferFlag` 保持 01、金额不减（`:165~172`）。
- 出站早于进站是脏数据 MUST 直接拒绝、NEVER 算出负数秒后当未超时放过；该用例 **MUST 连票价一起 stub** —— 票价查询排在时间校验**之前**，不 stub 会先抛「离线码地铁票价查询失败」，**用例看着通过其实没走到被测分支**（`:181~187`）。缺 `ticketTransSeq` 时无法定位首笔进站、MUST 早失败（`:198~207`）。
- 订单号规则 `GT` + 17 位时间戳 + 卡号后 6 位（`:208~212`）：「长度与后缀取法进了 `UK_GATE_TXN_PAY_ORDER_NO`，改一处就是幂等口径变更」。`buildOrderNo` 仍留在 `GateTxnPayServiceImpl`（`:308~312`，理由「订单号是订单聚合的身份，不属算价」），因此**这一条仍需反射**，其余用例在 `FareCalculator` 拆出后已改直调、断言值与拆分前逐字一致。

**【资损防线】`OfflineFareRecoveryTest`（268 行）四条断言各对应一条 NEVER**（`service/impl/OfflineFareRecoveryTest.java:36~49`）：待重算行是 `DEBIT_STATUS='INIT'` + `TOTAL_AMOUNT=0` 但**并非免扣费交易**，错置 SUCCESS 则车费永久收不回、置 FAIL 则补偿再也捞不到。
- 空结果集直接返 0、NEVER 在没有待重算行时还去写库（`:61~70`）。
- 单轮上限钳制：`limit<=0` 落 50、超 200 收到 200、回溯天数 `<=0` 落 7 天（`:72~81`）；**回溯天数只能从「起止日期相差几天」反推 —— 它不是参数，而是被算进扫描区间的**（`:180~191`）。
- 重算仍失败 → 只调 `markOfflineFarePending` 保持待重算态，不置 FAIL 也不置 SUCCESS、更不发起扣款（`:83~99`）。
- **重算出 0 元是最隐蔽的一条**（`:101~116`）：票价参数异常时重算会「成功」返回 0，若按 0 元收口成 SUCCESS，「账面完全正常、对账也不报错，**车费永久收不回来**」。
- CAS 抢占失败（`applyOfflineFareRecalculated != 1`）立即收手（`:118~132`）：返回 0 意味着另一副本已处理，继续调 pay-sign 就是**重复扣款**，所以顺序 MUST 是「先抢占、再扣款」且返回值 MUST 被检查；抢占成功才扣款、`TOTAL_AMOUNT` MUST 等于实扣 + 超时费（`:134~145`）。
- 单笔异常 **NEVER 中断整批**（`:147~165`）：改造前循环体是裸调用，中间那笔一抛就冲出 for、本轮剩余待重算订单全部不处理，「而它们每一笔都是 `TOTAL_AMOUNT=0` 的资损口」；用例让第 2 笔在抢占时抛 `ORA-00060 死锁`，断言第 3 笔照样被扣款、返回值只计成功笔数。

**【无鉴权写接口】`OriginalFareBackfillTest`（226 行）钉运营补数 `backfillOriginalFare` 的四类「出错不报错」**（`service/impl/OriginalFareBackfillTest.java:24~33`）。危险性的根在「**它是写接口且没有鉴权**」（见 `GateTxnPayPageController` 的 Javadoc）：一旦 `dryRun` 默认值被改成 false 或差额阈值判断被简化，运营点一下就把脏数据写进历史订单的 `ORIGINAL_FARE`，而 **APP 账单里的「优惠」是拿它减出来的、错了没有任何报警**。
- 入参护栏三条（请求体为空、日期非 `yyyyMMdd`、start 晚于 end）MUST 都在碰库之前挡掉（`:42~52`）。
- **`dryRun` 为 null MUST 当 true**（`:53~57`）：「这是本接口唯一的安全默认值 —— 反过来意味着运营只填日期就直接落库，且没有鉴权拦着」。
- `limit`：null 落 500、超上限收到 5000、小于 1 抬到 1（`:68~76`），「每行都要调一次 para-server，钳制是保护对端」。
- 可疑差额：实付 > 0 且「原价 − 实付」超阈值的行 MUST 只进 `suspectList`、**不落库**（`:77~95`）—— 挡的是**已发生过的脏数据**：「设备 206377 的 5 笔把 `TRX_AMOUNT` 按元上送、原价按分，回填后 APP 的优惠虚高 4~7 元」。反向那条同样要钉：**实付为 0 的行 NEVER 进 `suspectList`**（`:96~112`，免扣费与日票的实付本来就是 0，「差额等于原价」是正常形态，判反会让**整批日票永远补不上原价**）。`force=true` 明确越过阈值，落库仍走同一条带 `IS NULL` 的 UPDATE（`:113~127`）。
- UPDATE 影响 0 行是**幂等结果、不是失败**（`:128~144`，并发下已被别的调用填过；计进 `failedCount` 会让运营以为出错并反复重跑）；单笔抛异常只落 `failedList` 并带 `error`、**不中断后续行**（`:145~175`，「本方法不带事务、逐笔自动提交，中途 return 会让剩下的行白扫一遍 para-server」），`queryOrderByBizKey` 查不到票价的行只累加 `noFareCount`、也不算失败。
- 结构性断言同 `GateTxnPayQueryTest`：构造器只收 `gateTxnPayMapper` 与 `FareCalculator` 两个协作者，「谁把 pay-sign 调用或写入器牵进来，构造器就得加参数、本方法立刻编译不过」（`:219~222`）。

**【唯一出账口】`PaySignInitiationTest`（392 行）是三条链路共用的报文与状态收敛网**（`requestPay` / `retryPay` / 离线码补偿，`service/impl/PaySignInitiationTest.java:41~57`），断言分两类：
- **报文口径**：`TXN_DATE` MUST 取订单快照的交易日（出站日）、**NEVER 取当日**（`:81~98`）—— 「两表按 `(ORDER_NO, TXN_DATE)` 关联且都以它做月分区，**出站到落库实测滞后达 94 分钟**，22:26 之后出站时若支付域自己取 `now()` 就跨日、关联即落空」；这是本文件里**唯一「错了不报错、只是对不上账」的不变量**（报文照样发、pay-sign 照样返 0000，只有跨日那一小时的订单在 `PAY_TXN_DETAIL` 上关联不到，事后只能人工核对发现），常量 `OUT_TXN_DATE="20260101"` 就是为此刻意取远离今天的值（`:61`）。金额 MUST 是 `TOTAL_AMOUNT`（实扣 + 超时费），漏掉超时费就是少收钱（`:100~109`）。场景 / 行业类型 / 商品标题 / 订单超时全部来自配置项、报文里一个都不能少（`:111~126`，断言值 `AGM_GATE` / `1` / `地铁乘车扣费` / `地铁乘车费用` / `60`）。
- **状态收敛**：只有 `retCode=0000` 才推进 `PROCESSING`，其余一切（非 0000 / null / 抛异常）一律落 `RETRY`（`:128~177`）。「**NEVER 把「没抛异常」当成扣款成功**」（AGENTS.md §5.2「返回 boolean 的 RPC 包装方法」同型陷阱）；null 响应（连接不上 / 报文解析失败）落 RETRY 且原因 MUST 留痕（备注含「重试调用pay-sign失败」）；补偿链路上 pay-sign 抛异常 → 落 RETRY 且异常信息进备注，**NEVER 让异常冲出去**（那会让已抢占成功的这笔既没扣款、也没留下重试标记）。
- **渠道分派双向都钉**：`ISSUE_CHANNEL_CODE=07` MUST 走 alipay-pay-sign 且 `verifyNoInteractions(paySignClient)`（`:179~195`，「分派错了不会报错：pay-sign 收到一笔它查不到签约的订单，返非 0000、订单落 RETRY，看起来只是『扣费失败』，实际是整条支付宝出行链路全部打不通」）；反向 —— 非支付宝渠道 NEVER 碰 alipay（`:235~244`，「分派是双向的，反向错了同样打不通」）；支付宝返非 0000 同样落 RETRY（`:224~233`）。
- 支付宝报文的 `industryDetail` MUST 是落单时存下的那份 **21 键 JSON 原样透传**（`:197~222`）：其中 **9 个键**（进出站线路码 / 名称、进站设备号、`entryId` / `exitId`、`cardNum`、`cardIssueCode`）在 `GATE_TXN_PAY` **没有列**，只有出站那一刻 fep-dev-server 的三个并行 RPC 拿得到，「谁把这里改成按订单快照重算，得到的是键名完全不同的 JSON，支付宝解析不出行程、扣费必失败」；同批钉住 `orderNo` 取 `GATE_TXN_PAY.ORDER_NO`、`requestSignSeq` 取 `TICKET_TRANS_SEQ`（支付宝按票卡流水号找协议）、金额取 `TOTAL_AMOUNT`。
- **装配陷阱两处，且都表现成「断言值对不上」而不是 NPE**（`:330~365`）：构造器第 6 位 `metroTransferPushTaskProcessor` 与 `StationNameBackfiller` **都不能传 null** —— 补偿入口抢占成功后会先建换乘推送任务、再回填站名，传 null 在那一步 NPE、被单笔 catch 吞掉，于是「本该断言的『落 RETRY』根本没执行到」，实测表现为 `expected: <1> but was: <0>`。`PaySignInitiator` 与两个报文工厂传**真实实例**（「mock 掉就什么都没测到」），且两组配置参数单位不同（pay-sign 的 60 是**秒**、支付宝的 60 是**分钟**），**NEVER 把两组合并成一串**（`:367~378`）。异步执行器传 null 是**有意的**：本文件走的两条入口都是同步扣款，「谁把它改成异步这里立刻 NPE」。
- 另记一条：`recoveryService()`（`:380~391`）装配的是补偿服务而不是订单服务，因为「pay-sign 抛异常 → 落 RETRY」那条路只有补偿链路会同步走到；两者共用同一个真实 `PaySignInitiator`，所以搬家后**断言值一行没改**。

**【只记录现状的网】`WalletTransferFlagInferenceTest`（266 行）钉在线路径 `TRANSFER_FLAG` 的反推判定**（`service/impl/WalletTransferFlagInferenceTest.java:28~46`）：`calculateWalletDiscount` 不去问「这位乘客到底有没有公交换乘」，而是自算 `expected = round((原价 − 1) * 折扣率)` 再与闸机上报的 `TRX_AMOUNT` 比 —— 相等判 02、不等判 01。三条 MUST 钉住：
- 减的是**硬编码 1 分**，不是 `offline.billing.transfer-reduction-cents`（默认 100 分）——**两条路径对同一个「换乘减免」差 100 倍**。
- 权威数据源 `OfflineMetroTransferClient.isReduction` 就在同一个类的字段里，在线路径**一次都不调**（`:123~136`，用 `verifyNoInteractions` 钉住）。
- 算出的 `expected` **不参与扣款**，只写进 `EXPECTED_GATE_AMOUNT` 当观测值（`:138~157`）；这与线下路径口径**相反**（那边 `calculateOfflineFare` 会 `setTrxAmount`、算出来的就是真扣的钱），「两处 `EXPECTED_GATE_AMOUNT` 语义不同，NEVER 合并」。
- **决定性用例** `offlinePricedTransferTripIsClassifiedAsNoTransfer`（`:102~121`）：一笔**真按线下换乘规则定价**的行程（减 100 分再打 0.8 = 240）在在线路径上被判成「无换乘」。「只要减免真的是 1 元，在线路径的 02 就永远推不出来；反之若真的是 1 分，线下路径每笔多减 99 分。**二者 NEVER 可能同时正确 —— 这就是需要业务裁决的那个点**。」
- 取整是 **HALF_UP**：`(201-1)*0.9225=184.5` MUST 进位到 185，截断或 HALF_DOWN 得 184（`:159~171`）。差一分即翻转成 01（`:88~100`，一分不减的折后价 320 被判无换乘）。
- 钱包累计查询失败即**整段降级** `FALLBACK` + 01，折扣率与期望值全留空、「降级时 MUST 不留半成品期望值」（`:173~195`）——**而这正是 `AFCITPDB` 里全部 8 行钱包订单的实际状态**（2026-09-14 实测 `DISCOUNT_CALC_STATUS` 无一行 SUCCESS、`DISCOUNT_RATE` 46 行全空），也就是说**线上那个 01 是本分支写的、不是反推出来的**。非钱包渠道整段跳过、连 01 都不写，`transferFlag` 保持入库前的空值（`:197~209`）。
- **本文件的元规则**：只记录现状、不主张现状正确（`FareCalculator` 类注释已声明那个 `-1` 与「减不减换乘」的差异是搬迁前就存在的、尚未裁决）。裁决后改口径 MUST 同批改这些期望值并在注释里记依据，「**NEVER 把断言放宽成『非空』之类看不出口径的判断 —— 那样就白建这张网了**」。

**【站名回填】`station/StationNameBackfillerTest`（125 行）三条语义**（`station/StationNameBackfillerTest.java:14~19`，改坏后编译与启动都不报错、只在「列表站名显示成编码」或「站名被擦成空」时才被发现）：两个码都查得到 → 两列都覆盖（`:23~36`）；进站码查不到（占位 `FFFF` 不在 `TBL_STATION_INFO`）时进站名保持原值、**出站名照常回填**，NEVER 因为进站查不到就整体放弃（`:37~52`）；**一个都查不到（para 不可达 / 返非 0000）时 NEVER 把上游已填对的站名擦成空**（`:53~66`，「本类最关键的不变量：覆盖成空比不回填更糟，列表会退回显示编码」）。另两条：进出站编码都为空时直接短路、不发 RPC（**用抛异常的桩证明没被调用**，`:67~83`）；进出同站 MUST 能正常回填且去重后只查一个码（`:84~105`）。不用 Mockito —— `FareDataGateway` 的取数方法已是「吞异常返空 Map」形态，匿名子类覆写那一个方法即可，构造参数传 null 不会被触达。

**两处逐字相同的副本注释**（删注释时同批处理）：`PaySignInitiationTest:77~78` 与 `OfflineFareRecoveryTest:57~59` 都写着「本文件的用例全是非支付宝渠道…这个 mock 只为满足构造器；断言支付宝分派 MUST 另写用例，NEVER 靠这里的 mock 冒充覆盖」；`noopStationNameBackfiller()` 那段桩也是两份（`PaySignInitiationTest:352~365`、`OfflineFareRecoveryTest:253~267`）。

### 十三、`GateTxnPayMapper.java` 夹在 `@param` / `@return` 中间的口径（189 行注释，逐条抽，非样板）

阶段一按「样板」跳过了这个文件的 `@param` / `@return`，但**它们不是样板** —— 参数说明里夹着分区裁剪要求、口径必须同步的对偶方法、返回值的判空与「0 行不等于失败」这类只能靠人记的约定。以下逐条（`mapper/GateTxnPayMapper.java`）：

- **`@param debitRequestResult` 的三值口径**（`:181~182`）：「null 或空=全部，`"0"`=已扣款成功，`"1"`=未扣款成功。**口径落在 `DEBIT_STATUS` 上，与 `countFailedOrder` 的结清判定一致**」——即 IF8A-05 的「未扣款成功」与解约校验的「未结清」是同一条判据，改一处 MUST 同步另一处。
- **`@param cardTypeList` 为什么不能用单值**（`:183~185`）：「非空时按 `CARD_TYPE IN (...)` 过滤并**忽略** `cardType`。**APP 日票聚合码 05 会展开成 0445~0448**，因此不能用单值」。
- **`selectTransStatistics` 的 `@return` 判空约定**（`:224~226`）：「**永不为 null**（`COUNT(1)` 保证有一行），但 `SUM()` 在零行时返回 NULL，**调用方 MUST 逐字段判空补 `"0.00"`**」。同一段还写清了**数据源选择的理由**（`:216~219`）：用 `GATE_TXN_PAY` 而非 `QRCODE_TXN_DETAIL`，因为本表同时有 `ORIGINAL_FARE`（地铁原价）/ `TRX_AMOUNT`（票价）/ `OVERTIME_AMOUNT`（超时加收）/ `TOTAL_AMOUNT`（实付 = 票价 + 超时费）四个量、单表算完不需跨服务合并；「两表的 `TRX_AMOUNT` / `OVERTIME_AMOUNT` **逐行相等**（2026-09-10 LEFT JOIN 8 行核对）」。过滤条件 MUST 与 `countTransList` 同口径，否则统计数与列表条数对不上。
- **`convergeDebitStatusForSupplement` 的 `@return`：0 行不等于失败**（`:77~79`）——「调用方 MUST 回查当前状态区分『已被别人收敛（SUCCESS，属重复扣款）』与『状态不在白名单内（需人工）』」。同一处还记着**它是 2026-09-16 从 face-pay-server 迁入的**（`:74~75`）：「那边原先自己持有一份同形 UPDATE，直写本模块 owner 的表。**NEVER 在 face-pay 侧加回任何对 `GATE_TXN_PAY` 的写语句**」，以及白名单多一个 `FAIL` 的完整理由与「目标状态写死 `SUCCESS`、不做入参」（`:66~72`）。
- **`updateStatusFromPending` 刻意排除 `PROCESSING`**（`:33~36`）：「已受理订单若被迟到的失败结果降级回 `RETRY`，会被 `retryPay` 当作可重试订单**再次发起扣款**」。
- **`convergeDebitStatus` 为什么不能只用 `updateStatusIfProcessing`**（`:52~56`）：后者只覆盖 `PROCESSING`，「漏掉了『同步响应失败停在 `RETRY`』与『异步线程未及推进停在 `INIT`』两种订单 —— 这两种订单在支付平台侧**仍可能扣款成功**，只等回调收口，漏掉即永久停在中间态」；`SUCCESS` / `FAIL` 不在白名单内，重复回调不改写终态。
- **`countUnsettledOrderByCardId` 与 `countFailedOrder` 是一对，口径 MUST 同步**（`:239~241`）：结清口径完全一致（`DEBIT_STATUS` 非 SUCCESS），「区别只是**不带渠道、不带时间下限**：`BLACKLIST` 表没有渠道字段、拉黑也不区分渠道，因此判定必须覆盖该卡的**全部历史欠费**」。代价说明同样在注释里（`:243~244`）：「该查询不带 `TXN_DATE`，走不到月分区裁剪，会扫全部分区。调用方是**每天两次、单次只查一张卡**的批处理，代价可接受；**NEVER 把它放进过闸等联机链路**」。
- **`countUserAccInfo` 的三段口径**（`:250~273`）：①分档是**白名单** —— `unpaidCount` 只数 `INIT`/`PROCESSING`、`failureCount` 只数 `FAIL`/`RETRY`，「`CLOSED` 与脏数据 `NULL` 两档都不落入，因此**两数之和 ≠ `countFailedOrder` 的『非 SUCCESS』总数**，三者口径不同，**NEVER 拿来互相校验**」；②两个数量合并成**一次**扫描，避免同一用户区间被扫两遍；③踩过的两个坑「NEVER 重犯」：传 `LocalDate` + `jdbcType=DATE` 抛 `ORA-01843`，改成 `ADD_MONTHS(TRUNC(SYSDATE), ?)` 抛 `ORA-01861`，「且后者**只在扫到实际数据行时触发** —— 无欠费记录的用户反而返回成功，极易误判为已修复」；④时间下限**同时是分区裁剪条件**，本方法在 APP 联机链路上（不同于 `countUnsettledOrderByCardId` 那条批处理专用的全历史查询），全分区扫描会造成长时间阻塞的 DB 调用、在 `spring.threads.virtual.enabled=true` 下 pin 住载体线程。**但这一段里关于列类型的两句已过期**，见本节「矛盾」#13。
- **`selectByOrderNos` 的两条限制**（`:283~284`）：「不带 `TXN_DATE` 因此走不到分区裁剪，只命中 `UK_GATE_TXN_PAY_ORDER_NO` 的**前缀列**。调用方 MUST 限制列表长度：**Oracle 的 IN 列表上限 1000**，且列表越长扫描的分区越多」。
- **`selectOvertimeRefundablePage` 的圈单口径与它的边界**（`:144~153`）：近似圈定五条件 `OVERTIME_AMOUNT > 0` + `ORDER_EXP_TYPE = '1'`（单边）+ `TICKET_STATUS = '07'`（超时出站）+ `DEBIT_STATUS IN ('SUCCESS','PROCESSING')`（即 `DebitStatus#isRefundable` 白名单）+ `NVL(OUT_STATION, IN_STATION) = stationCode`；「该口径是『圈出候选』，真正的可退校验（日票拒退、金额上限、状态白名单）仍由单笔 `requestRefund` 逐单把关，因此本查询只做粗筛，**NEVER 用它直接判定能否退款**」。`countOvertimeRefundable` 的过滤条件 MUST 与它**完全一致**（`:161`）。
- **两条离线码统计的车站取法与去重**（`:120~136`）：分组统计的车站取 `NVL(OUT_STATION, IN_STATION)`（「离线码以出站交易落库，个别异常行可能只有进站站」）；汇总条**跨车站去重，因此不能对分组行求和**，且返回行的 `stationCode` / `stationName` 为空。两者的 `startDate` / `endDate`（`yyyyMMdd`）**MUST 非空，是 `TXN_DATE` 月分区的裁剪条件**。
- **`updateOriginalFareIfNull` 的 `IS NULL` 是幂等条件、NEVER 去掉**（`:304~306`）：「出站时已写好的原价快照是**当时参数版本**的值，回填用的是**当前版本**，覆盖等于篡改历史账单口径」。配套 `selectMissingOriginalFare` 在 SQL 里就排除进出站为空的行（`:293~295`，「进出站为空的行查不出票价…避免调用方为它们白跑一次 para-server 调用」）。
- **离线码补偿三条语句的口径**（`:314~343`）：`selectOfflineFarePending` 的判据是 `DISCOUNT_CALC_STATUS='OFFLINE_FARE_PENDING'` 且仍处 `INIT`，「这类行的 `TOTAL_AMOUNT` 是 0 但**不是免扣费交易**，NEVER 按 0 金额收口成 SUCCESS」；`updateOfflineFareRecalculated` 的 WHERE **既是幂等条件也是多副本下的抢占条件**，「返回 1 才代表本副本拿到了这笔，返回 0 说明别的副本已重算或订单已被人工干预。调用方 MUST 据此决定是否继续调 pay-sign，**NEVER 忽略返回值** —— 否则同一笔会被重复发起扣款」；`updateOfflineFarePendingMsg` **NEVER 改 `DEBIT_STATUS`**（「置 FAIL 会让订单进终态、补偿再也捞不到，置 SUCCESS 则是资损」）。
- 两条易被忽略的小口径：`selectStationNames` 的**行映射键是 `STATION_CODE` / `STATION_NAME`**（`:114`，改 SQL 别名会让 Map 取值全空）；`selectOperationPage` 与 `countOperationPage` 的参数 MUST 保持一致（`:92`、`:104`）。

### 十四、`gate-txn-pay-schema.sql` 逐列 `COMMENT ON`：列取值域的**库侧权威**

`sql/gate-txn-pay-schema.sql:158~205` 是 **46 行 `COMMENT ON`**（1 行表注释 + 45 行列注释），且**整份文件已于 2026-09-14 按 `AFCITPDB` 库内实际结构对齐**（`:6~11`：列清单 / 长度 / 可空性 / 默认值 / 列注释全部取自 `USER_TAB_COLS` + `USER_COL_COMMENTS` 实测，长度一律以 `CHAR_LENGTH` 为准）。因此**这 46 行就是列取值域的库侧权威**，用它裁决阶段二「矛盾」#4~#6、#8 那五组冲突：

| # | 列 | 库侧原文（`COMMENT ON` 行号） | 另外两方说什么 | **库侧结论** |
|---|---|---|---|---|
| 1 | `COUNTING_TIMES` | 「计次票**剩余可用**次数」（`:186`） | 实体「计次票剩余次数（扣减后）」（`entity/GateTxnPay.java:42`）；此前文档「本次行程**消耗**次数（恒为 1）」；代码「取上游上送值，日票缺省 1」（`GateTxnPayServiceImpl.java:372`） | **「消耗次数」那一支在库侧没有任何依据，MUST 作废**（库与实体都站在「剩余」一侧）。但「剩余可用」与代码的「取上游上送值」**仍不等价** —— 上游送的是什么没有任何约束，因此库侧只能裁掉一支，**「剩余 / 扣减后」仍需业务确认**；定稿前 NEVER 依赖这一列做次数对账，也 NEVER 据库注释直接改写入逻辑（`:13~17` 原文即如此告示） |
| 2 | `COUNTING_FLAG` | 「计次/计时标识：**1=计时,2=计次**」（`:187`） | 代码与实体都是 `Y`/`N`（查询侧兜底 `'N'`，`GateTxnPayQueryServiceImpl.java:264`、`entity:43`） | **取值域以代码为准：库注释过期，MUST 改注释、NEVER 改代码。** 两侧不是「写法不同」而是**取值域无交集**（列宽 `VARCHAR2(2 CHAR)` 两种都装得下，所以不会报错），且**语义也不同**：`Y/N` 表达「是不是计次票」、`1/2` 表达「计时还是计次」。代码这一侧还多一道构建期证据 —— `GateTxnPayQueryTest` 断言历史行补 `N`（本节 §十二），库注释那一侧一条证据都没有 |
| 3 | `TICKET_STATUS` | **八值**「01无交易,02进站,03进站超时,04进站失败,05**出站**,06**出站超时**,70异常,80自助补出站」+「与 `QRCodeStatusEnum` 不同源，跨表 NEVER 互换」（`:170`） | 实体**六值**「01无交易,04进站失败,05**已进站**,06**已出站**,07超时出站,70异常」（`entity:34`）；mapper 圈单用 `TICKET_STATUS = '07'`（`GateTxnPayMapper.java:149`） | **以库侧八值为准，实体那行三处都错**：少 `02`/`03`/`80`、且 `05`/`06` 的语义与库**正好错位**（库 05=出站、实体 05=已进站）。**更要紧的是库侧八值里没有 `07`**，而「批量退超时罚金」的圈单 SQL 正是按 `'07'` 过滤 —— 若库注释是全集，**那条圈单恒空、运营永远圈不到单**。**MUST 现查 `SELECT TICKET_STATUS, COUNT(*) ... GROUP BY TICKET_STATUS`** 再决定是改 SQL 还是补库注释，NEVER 只按其中一方改 |
| 4 | `ORDER_EXP_TYPE` | **六值**「0正常,1单边账(入),2单边账(出),3单边入站人工,4单边出站人工,5双段计费超时」（`:173`） | 实体**三值**「0正常,1单边,2补站」（`entity:37`）；mapper 圈单注为 `'1'`（单边） | **以库侧六值为准**：实体不仅少三个值，`2` 的语义还写错了（库是「单边账(出)」，不是「补站」）。连带两条 —— 退超时罚金按 `'1'`（库义「单边账**(入)**」）圈单**是否符合业务未见依据**；日终对账 EXP 段也吃这一列，甲方那份 1~15 的异常类型与本列六值的冲突已记在 AGENTS.md §2.2.2 |
| 5 | `COMPANION_FLAG` | 「**同行票**标识：**Y/N**」（`:183`，列默认 `'N'`） | 实体「**陪同票**标志：Y是,N否」（`entity:38`）；`GateTxnPayFieldCode` 的排除白名单是 **`{Y,C}`**（`constant/GateTxnPayFieldCode.java:32~35`，`C`=第三方票，判定 `isCompanionOrThirdParty` 大小写不敏感） | **`C` 在库侧也没有依据** —— 库注释同样只写 `Y/N`。三方里**只有代码认识 `C`**，而它真实影响换乘推送的排除判定（`MetroTransferPushDecisionTest` 有断言保护）。因此裁决：**取值域取代码的 `{Y,N,C}` 并集，MUST 补库列注释、并把名称统一为「同行票」**；**NEVER 依赖实体那行的「非 `Y` 即 `N`」**——按它写判定会漏掉 `C`，漏掉的那笔会被错误地推给公交卡系统 |
| 6 | `PAY_CHANNEL_CODE` | **库里不存在该列**：列清单无、`COMMENT ON` 无，且 `:10` 明确记「删掉库里并不存在的 3 列（`PAY_CHANNEL_CODE` / `DISCOUNT_FEE` / `DISCOUNT_INFO`，全仓零代码引用）」 | 实体仍有 `payChannelCode` 字段并注「来自 `USER_ITP_REG_INFO.CHANNEL`」（`entity:46`） | **库侧结论明确：没有这一列。** 该实体字段是**死字段**（resultMap 不映射即恒 `null`），MUST 删字段或补列，二选一；**NEVER 在未确认的情况下把它写进任何 SQL**（必 `ORA-00904`）。注意实体注释里那句「来自 `USER_ITP_REG_INFO.CHANNEL`」描述的其实是 `SIGN_CHANNEL_CODE`（库注「签约通道代码」，`:182`）的来源 —— 两个字段被混着记过 |

**库侧权威还确认 / 澄清了这些取值域**（阶段一、二未逐列记）：`DEBIT_STATUS` 六值「INIT初始化，PROCESSING处理中，SUCCESS成功，FAIL失败，RETRY待补扣，**CLOSED关闭**」（`:162`）；`TRX_TYPE` 只有「02出站，03超时出站」（`:167`，与 `GateTxnPayFieldCode.isExitTrxType` 一致）；`PAYMENT_VENDOR`「支付厂商编码，**钱包为0B**」（`:190`）；`CHANNEL_TYPE`「**01为蓝牙**」（`:191`）；`TRANSFER_FLAG`「01无换乘，02有换乘」（`:193`）；`CUMULATIVE_TYPE`「01正常出站，02超时出站，03不累计」（`:194`）；`DISCOUNT_CALC_STATUS` 直接写成「SUCCESS/FALLBACK/SKIPPED/OFFLINE_FARE_PENDING」（`:200`，**与 `DiscountCalcStatus` 四取值完全一致，这一列没有冲突**）；`OFFLINE_FLAG`「离线码标识：Y/N」（`:184`）；`TXN_DATE`「出站交易日期，取 `OUT_TIME` 前 8 位，格式 YYYYMMDD，用于月分区」（`:169`，列定义 `VARCHAR2(8 CHAR)`，`:41`）；`INDUSTRY_DETAIL`「支付宝出行行业明细JSON，**21键**，出站落单时由 fep-dev-server 组装后整块透传，**仅 `ISSUE_CHANNEL_CODE=07` 有值**」（`:203`，与 `PaySignInitiationTest` 那条「原样透传 NEVER 重算」互为印证）；`ATTRIBUTABLE_PARTY` / `RECEIVING_PARTY` 是「订单应收商户 / 订单实收商户」（`:188~189`，实体补了取值样例 `cjdsj/qddt`）；四个金额列一律「单位分」（`:178~180`、`:195~199`）；`TICKET_TRANS_SEQ`「二维码交易计数器，**文档字段名为 `tikcetTransSeq`**」（`:168`，甲方文档里的拼写错误，对接时 NEVER 自行纠正成 `ticket...`）。

**`COMMENT ON` 之外，本文件另有两块不属于列字典、但同样只此一处的知识**：①分区维护与推荐查询的 6 段示例 SQL（`:207~259`，全部注释掉，含新增月分区的 `SPLIT PARTITION P_MAX AT ('2027xxxx')` 模板、`USER_TAB_PARTITIONS` 查法、`DROP PARTITION ... UPDATE INDEXES` 与「执行前务必确认历史数据已归档」）；②三个非唯一索引的用途（`:122~156`：`IDX_..._CARD_DATE` / `USER_DATE` / `STATUS_DATE` / `DEVICE_DATE`，其中「按扣费状态查待补扣、失败、处理中订单」那条正是补偿扫表的支撑索引）。

### 矛盾与待裁决

> 承接阶段二编号（阶段二用到 #12），本轮新增 #13~#17。一律**只记录、不改代码**；上面「十四」那张表已给出五组取值域的**库侧结论**，不在此处重复。

| # | 注释说什么 | 代码 / 库实际是什么 | 证据（文件:行） | 建议裁决 |
|---|---|---|---|---|
| 13 | mapper Javadoc：「**生产库 `TXN_DATE` 实际是 `VARCHAR2(16)`** 存 `yyyyMMdd`，不是 `DATE`（2026-09-08 实测 `ALL_TAB_COLUMNS`；仓库 DDL `gate-txn-pay-schema.sql:28` 写的 `DATE` 与生产库不一致）」 | **两句都已过期**：schema 现为 `TXN_DATE VARCHAR2(8 CHAR)`（`:41`），且 2026-09-14 已用 `CHAR_LENGTH` 实测 = 8；`VARCHAR2(16)` 正是「用 `DATA_LENGTH` 判长度」那个已被 `docs/domain/decisions.md` 撤回记录收录的坑（库字符集下 8 字符 = 16 字节），DDL 写 `DATE` 也早已修正 | `mapper/GateTxnPayMapper.java:261~264` vs `sql/gate-txn-pay-schema.sql:41`；AGENTS.md「`TXN_DATE` 的列类型是 `VARCHAR2(8)`」 | 把那段改成只留「`VARCHAR2` 存 `yyyyMMdd`，绑定 MUST 用 `jdbcType=VARCHAR`」，**删掉 16 与 `DATE` 两个数字**。留着它等于在代码里立了一块把人带回旧坑的路牌 —— 而下一个人查列类型时最可能读到的就是这段 |
| 14 | 库注释 `TICKET_STATUS` 八值**不含 `07`**；实体那行有 `07 超时出站`；mapper 圈单按 `TICKET_STATUS='07'` 过滤 | 三方互不相同（详见「十四」#3）。若库注释是全集，则「批量退超时罚金」的圈单**恒空** | `sql/gate-txn-pay-schema.sql:170`；`entity/GateTxnPay.java:34`；`mapper/GateTxnPayMapper.java:149` | **MUST 先 `GROUP BY TICKET_STATUS` 现查库内实际分布**（含 `06` 与 `07` 各多少行），再定是改 SQL 还是补库注释。**NEVER 直接把 `'07'` 改成 `'06'`** —— 那会把口径从「超时出站」悄悄换成库义的「出站超时」，两者是不是同一件事本身也没有依据 |
| 15 | 库注释 `DEBIT_STATUS` 有 **6 个**值（含 `CLOSED关闭`）；`DebitStatus` 枚举只有 **5 个**（`INIT/PROCESSING/RETRY/SUCCESS/FAIL`，**无 `CLOSED`**） | 代码**承认库里有 `CLOSED`**：`countUserAccInfo` 的 Javadoc 明确写「`CLOSED` 与脏数据 `NULL` 两档都不落入」。也就是说枚举不是该列的全集，而枚举类注释自称「**唯一取值来源**」 | `sql/gate-txn-pay-schema.sql:162`；`constant/DebitStatus.java:4`、`:21~31`；`mapper/GateTxnPayMapper.java:253~256` | 二选一：把 `CLOSED` 补进枚举（**只用于读**，且 NEVER 进 `isRetryable` / `isRefundable` 白名单），或确认库里从无该值后删库注释里那一项。**在裁决前 NEVER 用 `DebitStatus.values()` 当作 `DEBIT_STATUS` 的全集**（例如据它生成前台下拉或写「其余状态一律非法」的校验） |
| 16 | 实体行尾把 `COMPANION_FLAG` 叫「陪同票」、把 `payChannelCode` 注为「来自 `USER_ITP_REG_INFO.CHANNEL`」 | 库分别叫「同行票」、且 `PAY_CHANNEL_CODE` **列不存在**；「来自 `USER_ITP_REG_INFO.CHANNEL`」实际描述的是 `SIGN_CHANNEL_CODE`（库注「签约通道代码」） | `entity/GateTxnPay.java:38`、`:46`；`sql/gate-txn-pay-schema.sql:183`、`:182`、`:10` | 实体那 26 行行尾注释**整体已不可信**（本轮逐列比对命中 5 处偏差），建议**整批改为以库注释为准的一行**或直接删除、只留库侧字典。**NEVER 再据实体行尾注释判断任何取值域** |
| 17 | mapper 自称退超时罚金的圈单口径「**近似圈定口径（已确认）**」 | 五个条件里 `ORDER_EXP_TYPE='1'` 注为「单边」，而库义是「单边账**(入)**」；「已确认」指的是哪一次、由谁确认，注释里没有出处 | `mapper/GateTxnPayMapper.java:146~151`；`sql/gate-txn-pay-schema.sql:173` | 要么补上确认出处（谁、哪天、依据哪份文档），要么把「已确认」删掉。**注释里的「已确认」没有出处时等于没有确认** —— 而这条口径决定运营能圈出哪些单来退钱 |

### 墓碑清单（阶段三新增，与阶段一 25 条、阶段二 12 条不重复）

> 「墓碑」= 唯一作用是**禁止把某个已迁走 / 已废弃 / 已实测错误的东西加回来**，不承载正向知识。本轮的墓碑集中在**测试装配**上 —— 它们拦的不是业务写法，而是「照着改会让整批用例集体转红、且失败信息指向错误方向」。

| # | 文件:行 | 想拦住的事 | 载体 |
|---|---|---|---|
| 1 | `src/test/.../service/impl/PaySignInitiationTest.java:337~341` | 构造器第 6 位 `metroTransferPushTaskProcessor` **不能传 null**：补偿入口抢占成功后先建换乘任务，NPE 被单笔 catch 吞掉后表现为 `expected: <1> but was: <0>`「而非直接报 NPE —— 已实测」 | 测试 Javadoc（**唯一记录这条实测现象的地方**） |
| 2 | `src/test/.../service/impl/PaySignInitiationTest.java:352~357`、`OfflineFareRecoveryTest.java:253~259` | 站名回填 **NEVER 传 null**，同款陷阱、同样表现成断言值对不上；桩恒返空 Map 是为了「本文件的报文断言值一行不用改」 | 测试 Javadoc（两份逐字近似的副本） |
| 3 | `src/test/.../service/impl/MetroTransferPushDecisionTest.java:172~178` | `enabled` 传 false 会让本类所有 `assertNotNull` 用例集体转红，「而失败原因看起来像『判定条件写反了』——**排查本类用例集体转红 MUST 先看这里传的是什么**」 | 测试 Javadoc |
| 4 | `src/test/.../MapperSqlWallCompatibilityTest.java:40` | 「若本断言失败说明 druid 已能解析单数形式，**可放宽下面的 mapper 扫描**」——反向墓碑：这条断言失败是好消息，NEVER 当缺陷修 | 断言失败信息 |
| 5 | `src/test/.../mapper/GateTxnPayMapperSqlTest.java:63~64`、`:69~73` | 计数前 MUST 先剥 XML 注释（「注释里为讲清语义会引用状态字面量」）；`OFFLINE_FARE_PENDING` 剥注释后**只允许出现 3 次**，多出来即「有人又抄了一份判据，改口径时必然漏改」 | 测试常量注释 + 断言 |
| 6 | `src/test/.../service/impl/OfflineFareCalculationTest.java:181~187` | 「这里 MUST 连票价一起 stub：票价查询排在时间校验**之前**，不 stub 会先抛『离线码地铁票价查询失败』，**用例看着通过其实没走到被测分支**」 | 测试 Javadoc（假绿用例的唯一记录） |
| 7 | `src/main/java/.../mapper/GateTxnPayMapper.java:74~75` | 「本方法是 2026-09-16 从 face-pay-server 迁入的 —— 那边原先自己持有一份同形 UPDATE，**直写本模块 owner 的表**。NEVER 在 face-pay 侧加回任何对 `GATE_TXN_PAY` 的写语句」 | Java Javadoc（跨模块 owner 边界的唯一告示） |
| 8 | `src/main/java/.../mapper/GateTxnPayMapper.java:35~36` | 「**刻意排除 `PROCESSING`**：已受理订单若被迟到的失败结果降级回 `RETRY`，会被 `retryPay` 当作可重试订单再次发起扣款」 | Java Javadoc |
| 9 | `src/main/resources/sql/gate-txn-pay-schema.sql:13~22` | 五条「遗留待业务确认的注释语义冲突（本文件现按库内原文写，与改动前的说法不同，**NEVER 当成笔误改回**）」 | SQL 注释（本轮「十四」那张表的原始出处） |
| 10 | `src/main/resources/sql/gate-txn-pay-schema.sql:6~11` | 「长度一律以 `CHAR_LENGTH` 为准，**NEVER 用 `DATA_LENGTH` 判长度**，库字符集下 8 字符 = 16 字节，曾据此误记过 `TXN_DATE` 是 `VARCHAR2(16)`」+ 删掉三列（`PAY_CHANNEL_CODE` / `DISCOUNT_FEE` / `DISCOUNT_INFO`）的记录 | SQL 注释（与「矛盾」#13 互为对照：**同一个坑在 mapper 里还留着旧结论**） |
| 11 | `src/test/.../service/impl/GateTxnPayQueryTest.java:185~193`、`OriginalFareBackfillTest.java:219~222` | 「构造器只收 N 个协作者，谁把写入 / 算价 / pay-sign 牵进来，本方法立刻编译不过 —— **那正是要暴露的耦合**」：把分层约束固化成**编译期**失败 | 测试 Javadoc（两处同型） |
| 12 | `src/test/.../service/impl/OfflineFareRecoveryTest.java:180~181`、`PaySignInitiationTest.java:56~57`、`OfflineFareCalculationTest.java:49~51` 等 6 处 | 「抽成独立协作者后**断言值 NEVER 改** —— 断言不变才是行为没变的证据」（同一句在 6 个测试类里各写一遍，是本模块四轮拆分共用的验收判据） | 测试 Javadoc（跨文件重复，删注释时按一条处理） |

### 本轮覆盖率自评

**分母（本轮口径）**：`src/test` **11 个文件 2586 行**、注释约 **510 行**（`PaySignInitiationTest` 392 行文件里 104 行注释、`OfflineFareCalculationTest` 329/约 70、`GateTxnPayMapperSqlTest` 305/约 60、`OfflineFareRecoveryTest` 268/68、`WalletTransferFlagInferenceTest` 266/61、`OriginalFareBackfillTest` 226/约 50、`GateTxnPayQueryTest` 204/约 45、`MetroTransferPushDecisionTest` 198/约 55、`station/StationNameBackfillerTest` 125/约 25、`arch/GateTxnPayGuardTest` 116/约 25、`MapperSqlWallCompatibilityTest` 63/约 16）；`mapper/GateTxnPayMapper.java` **345 行里 189 行注释**（其中约 95 行是纯 `@param` 样板、本轮抽的是剩下那部分）；`sql/gate-txn-pay-schema.sql` **46 行 `COMMENT ON`** + 分区维护示例约 55 行。

**本轮抽取**：§十二 逐类 **11 条**（含约 70 个用例级子条目）、§十三 **14 条**、§十四 **6 组库侧裁决 + 1 组库侧确认清单（14 列）**；**矛盾新增 5 条**（#13~#17）、**墓碑新增 12 条**。合计正文 **31 条 + 17 条附录条目**。

**三处空白已全部闭合**：①`src/test` 用例级 —— 11 个类逐类落地，含此前只报了行数的 `PaySignInitiationTest` / `OfflineFareRecoveryTest` / `WalletTransferFlagInferenceTest` 三个；②`GateTxnPayMapper.java` 的 `@param` 夹带口径 —— 14 条；③`COMMENT ON` 逐列 —— 46 行全部过一遍，并**用它裁决了阶段二挂着的五组取值域冲突**。

**样板跳过**：`@param cardId 卡号` 这类纯参数名重复约 95 行；测试里的 `private` 工厂方法（`order()` / `walletRequest()` / `payResult()`）的一行说明约 20 行；`import` 静态方法块无注释。

**阶段一 + 二 + 三合计覆盖率结论**：按「带知识量的注释块」估算 **约 98%**。剩余 2% 是三类**有意不抽**的：①`FareCalculator` / `ReconExportService` 那两个大类里**已被阶段一整段收录、只是措辞不同**的重复段；②`model/page` 五个 DTO 的字段级一行说明（`BatchRefundResult` 的 `ItemResult` 等，属结构自解释）；③`pom.xml` 那 2 行 XML 注释（阶段二已收）。**至此本模块注释知识抽取收尾**，随后执行的注释删除以本文件为唯一去处。

### 删除后仍在代码里的护栏（2026-09-16 收尾，共 6 组）

删注释时**刻意保留**下面这几处一行式护栏 —— 判据是「删掉它，下一个改动者会在没有任何提示的情况下做出一次不报错的错误改动」：
1. `service/impl/OfflineFareRecoveryProcessor.java`、`service/impl/MetroTransferPushTaskProcessor.java` 类上各一行：**NEVER 加回 `@Scheduled`，已改由 web-admin `sys_job` 120 / 121 触发（ADR-D80）**。
2. `controller/internal/CompensationInternalController.java` 的 `debit/converge` 端点上一行：**该端点能把任意订单号的 `DEBIT_STATUS` 直接改成 `SUCCESS`（等于免单），与另两个端点语义相反 —— NEVER 恒返 `0000`、NEVER 吞异常**。
3. `resources/mapper/GateTxnPayMapper.xml` 的 `convergeDebitStatus` 与 `convergeDebitStatusForSupplement` 上各一行：**补款侧白名单含 `FAIL`、回调侧不含，差异是有意的，改一侧会打挂另一侧（有守卫测试）**。
4. `arch/GateTxnPayGuardTest` 类与两个测试方法上各一行「防什么」。
5. `resources/mapper/*.xml` 里「SQL 正文禁写注释」那条（Druid WallFilter）。
6. `application.properties` 里 `wall` / `tracing` / `wallet.app-gateway-url` 三处一行式判据（最后那个是**全仓唯一带 `${ENV:}` 包装的 `testngbackV2` 残留**）。
