---
业务域: 支付签约 / 解约 / 免密扣款
模块: pay-sign-server
---

# 提示词：支付签约与免密扣款

## 何时读本文件
签约咨询、签约结果、解约（含 ACC 同意解约）、免密支付、退款、渠道回调、APP 异步通知补偿相关改动。

## 模块定位
`pay-sign-server`，端口 **9096**，`spring.application.name=pay-sign`
启动类 `pay-sign-server/.../PaySignServer.java`（`@EnableRpcRoute`、`@EnableRpcAccount`）
**本模块已无服务内定时任务**：2026-08-26 移除了唯一的 `@Scheduled`（签约通知补偿）与 `@EnableScheduling`，补偿统一改为外部定时任务调 `/internal/**` 无入参接口。新增周期性任务 **MUST** 沿用这一形态，**NEVER** 加回 `@Scheduled`——服务内调度隐含单副本假设、重启即丢、且无法按需人工重放。

## 接口清单

| Controller | 前缀 | 接口 |
|---|---|---|
| `PaySignAppController` | `/ci/app` | `requestContractAdvisory`(IF8A-21)、`requestContractResult`(IF8A-22)、`requestTermination`(IF8A-06)、`unbindAgreement`(IF8A-75，**直接解绑，立即调支付中心**，委托 `TerminationInternalService.unbindAgreement` → `executeTermination`；**APP 实际请求的路径是 `/userData/unbindAgreement`**，2026-09-09 已在 `fep-app-server/PaySignController.java:97` 加为兼容别名，与 `/ci/app/unbindAgreement`、`/app/unbindAgreement` 并存，规范正式路径待甲方确认后再收敛)、`requestAgreeRelease`(IF8A-36，**fep-app 未接线、暂不实现，见 §移除签约与解约不是一回事**)、`requestPay`(IF8A-19)、`requestRefund`、`receiveSignResult`、`receivePayResult`、`receiveTerminationResult`(IF8B-02)、`queryPayTxnBatch`(IF8A-05)、`updateDisplayAccount` |
| `PaySignController` | 无类级前缀 | `/requestSignInfo`(IF8A-16)、`/querySignInfoBySeq` |
| `PaySignAlipayTripController` | `/channel` | `requestContractAdvisory`、`requestContractResult`、`requestTermination`、`addContract` |
| `PaySignNotifyController` | `/app` | `/receiveSignResult` |
| `PaySignAlipayTripNotifyController` | `/notify` | `receiveSignResult`、`receiveTerminationResult` |
| `TerminationNotifyController` | `/ticket` | `/receiveTerminationResultFromItp` |
| `TerminationInternalController` | `/internal/termination` | `process`（**入参可省略**，扫表主线：不传 body 即历史行为全量扫 PENDING+SCANNING；传 `referenceTime` / `delayDays` 则只处理 `REQUEST_TIME <= referenceTime - delayDays` 的记录，DTO 在 `model` 模块的 `ProcessTerminationReqDTO` / `ProcessTerminationRespDTO`）、`compensateNotify`（无入参，解约通知补偿）、`checkFailedOrders`、`execute`、`notifyFailed` |
| `PaySignInternalController` | `/internal/paySign` | `compensateNotify`（无入参，签约结果通知补偿）、`resendNotify`（入参只有 `requestSignSeq`，**单条**签约结果通知重发：同步投递、不扫表、不递增 `NOTIFY_RETRY_COUNT`，DTO 在 `model` 的 `ResendSignNotifyReqDTO` / `ResendSignNotifyRespDTO`，Client 方法 `PaySignClient.resendSignNotify`） |
| `PaymentInternalController` | `/internal/payment` | `compensateRefundQuery`（无入参，**出网**回查支付中心 §3.2 退款查询并收口，扫 `PAY_REFUND_DETAIL` 停在 `PROCESSING` 的单，2026-09-15 / 批次 5B 随 `requestRefund` 摘事务同批新增，ADR-D8 的后半段，**NEVER 删**）、`compensateRefundSummary`（无入参，**不出网**，只重算 `PAY_TXN_DETAIL` 的退款汇总，A/B 二分见 §退款汇总跨表对账，ADR-D91） |

本模块 `/internal/**` 端点现共 **10 个**（termination 6 + paySign 2 + payment 2），
**全部无鉴权**（见 §待修问题 P0），**全部由 web-admin Quartz 触发、本模块无 `@Scheduled`**。
对应 `sys_job`：4 解约申请确认、6 签约结果通知补发、7 解约结果通知补发、
**122 退款回查补偿（`0 0/10 * * * ?`）**、**123 退款汇总跨表对账（`0 15 * * * ?`）**。
排查「pay-sign 的补偿有没有跑」**MUST 查 `SYS_JOB_LOG`**，NEVER 在本模块里找 `@Scheduled`。

代码中真实出现的编号：IF8A-05/06/16/19/21/22/36、IF8B-02。

## 核心类

**`PaySignWorkflow` 已删除**（2026-09-15，ADR-D87）。此前它是 2668 行的单一实现中心，现按 APP 入口的业务能力拆成四个领域服务 + 一个纯路由门面：

```
Controller → PaySignServiceImpl（202 行，纯路由，零业务逻辑）
               ├─ ContractDomainServiceImpl（785 行）签约 + 咨询 + 解约申请
               ├─ PaymentDomainServiceImpl（754 行）免密扣款 + 支付回调
               ├─ RefundDomainServiceImpl（505 行）退款 + 退款回查补偿 + 退款汇总跨表对账
               └─ CallbackDomainServiceImpl（594 行）签约回调 + 解约回调
```

**退款域是 2026-09-15 从支付域二次拆出的**（ADR-D91，`PaymentDomainServiceImpl` 1166 → 754 行）：
`requestRefund` / `compensateRefundQuery`（含 `settleRefundByQuery` / `resolveRefundQueryStatus`）
/ `compensateRefundSummary` 与只被退款用的私有方法、6 个常量全部迁入 `RefundDomainServiceImpl`。
`PaySignService` 门面签名**一字未动**，`PaymentInternalController` 改注入 `RefundDomainService`。
拆分动机记一条真实教训：**当天有一次编辑因为那个文件过大，`old_string` 匹配落到了方法内部、
误删了 `resolveRefundQueryStatus` 的方法体中段（已恢复）—— 文件大小本身就是缺陷密度。**

- `service/impl/AppNotifyServiceImpl.java`：异步通知 APP 签约/解约/解约失败结果 + 签约结果通知补偿（`compensateSignNotify`，由 `/internal/paySign/compensateNotify` 触发，只扫 `RECEIVE_SIGN_RESULT`）
- `service/impl/TerminationInternalServiceImpl.java`：解约内部流程（查扣费失败订单 → 执行解约 → 通知失败）；`service/impl/TerminationProcessor.java`：T+4 扫表收口
- `service/impl/ChannelSyncDeliverer.java`：解约后清理 account 侧支付通道的唯一投递实现（`CHANNEL_SYNC_*` outbox，ADR-D48）

### 排查支付链路问题的定位顺序（MUST 按此顺序，NEVER 从领域服务倒着猜）
1. 按 **URL** 找 Controller（本域 7 个 Controller 见上表，接口编号不唯一映射到一处代码）；
2. Controller 一律打 `PaySignServiceImpl`，在那里看这个方法被路由到哪个领域服务（**两个例外**：`receivePayResult` 直接路由到 `PaymentDomainServiceImpl`，**NEVER 改回经 `CallbackDomainService` 转发** —— 那会让回调组反向依赖支付组，而回调组一行支付逻辑都没有；`requestRefund` 直接路由到 `RefundDomainServiceImpl`，**NEVER 改回经 `PaymentDomainService` 转发**，理由同型）；
3. 再进对应领域服务读真实现。**`/internal/payment/**` 那两个补偿端点不经门面**，`PaymentInternalController` 直接注入 `RefundDomainService`。

### 四个 support 类 + 网关门面（`paysign/support/`）
拆分时为避免「三个领域服务各留一份逐字副本」抽出，属对 AGENTS.md §5.1「NEVER 主动创建新的工具类」的**有意破例**，先例是 `face-pay-server` 的 `F2fDuplicateKey`（ADR-D84）；破例理由写在各自类注释里，**NEVER 删那些注释**。

| 类 | 内容 | 硬约束 |
|---|---|---|
| `PaySignValidators` | 7 个入向报文必填校验（`validateRequestSignInfo` / `validateContractQuery` / `validateRequestTermination` / `validateRequestPay` / `validateRequestRefund` / `validateReceiveSignResult` / `validateReceiveTerminationResult`） | 返回的字符串**原样进 APP 应答的 `retMsg`**，**NEVER 改文案、NEVER 调整判断顺序**（顺序决定「同时缺两个字段时报哪一个」，联调方可能已按文案断言）。`validateRefundPayTxn` **刻意没搬**——它算「可退金额」，属退款业务规则，**NEVER 因为都叫 validate 就一起搬** |
| `PaySignResponses` | 17 个 `fillError` / `fillSuccess` 重载 | 只填壳、不判业务 |
| `PaySignGatewayMessages` | 6 个出向 bizData 组装（contract / creditQuery / queryResult / dismissal / requestPay / requestRefund） | 一律 `LinkedHashMap`，**因为键顺序参与签名，NEVER 换成 `HashMap` 或重排键**；`buildRequestRefundBizData` 的 `orderNo` **MUST 取 `payTxn.getPayCenterOrderNo()`**；`queryResult` 与 `dismissal` 键集相同但**NEVER 合并**——那是支付中心两个不同端点 |
| `PaySignValues` | 纯值转换：`normalizeVendor` / `parseDateTime`×2 / `stringValue` / `defaultString` / `convertPayStatus` / `resolveTxnDate`×2 | **NEVER 放需要注入 Bean 的方法**：一旦要注入，它就得变成 Spring Bean，三个领域服务对它的 `import static` 全部作废 |
| `PaySignGateway`（**Spring `@Component`，不是静态类**） | `request` / `isSuccess` / `errorMessage` / `isAlreadyPaidSuccess`，收口支付中心网关的调用与应答判读 | 包级方法 `isGatewaySuccess` 已删除，判读网关应答 **MUST 走 `PaySignGateway.isSuccess`**，**NEVER 在业务类里再写一份 code 比较** |

另有 `paysign/audit/PaySignAuditLogger`：审计流水写入的唯一实现。

**NEVER 往任何一个领域服务里堆跨组逻辑**：跨组共用的纯函数进 `PaySignValues`、出向报文进 `PaySignGatewayMessages`、网关调用与应答判读进 `PaySignGateway`。堆进去的后果不是「不美观」——三个领域服务互不引用是本次拆分唯一的结构保证，一旦 A 组调 B 组的私有方法，下一次改支付就必须同时读签约链路。

### 改本域代码后的验证
`mise exec -- mvn clean test -pl pay-sign-server -Djkube.skip=true`，本模块共 **100 个测试**（2026-09-15 现值；ADR-D91 那次退款域拆分是纯搬迁，**测试数未变、两份冻结清单字节未动**，这就是纯搬迁批次的等价判据）。其中 `arch/PaySignTransactionBoundaryArchTest` 冻结两份清单：`EXPECTED_TRANSACTIONAL_METHODS` **3 条**（`CallbackDomainServiceImpl#receiveSignResult`、`ContractDomainServiceImpl#alipayTripRequestSignInfo` / `#removeSignAgreement`）、`KNOWN_TRANSACTIONAL_OUTBOUND` **0 条（`new TreeSet<>()` 空集）**。**本行此前写「9 条 / 8 条」已过期**：批次 5A 由 9 减为 5、5B 减为 4、5C（ADR-D90）减为 3，出网清单则在 5C 收敛到空集。**那个空集是「已达标」而不是「还没填」——断言红了 MUST 去改代码，NEVER 往集合里补条目。****纯搬迁批次 MUST 不改动这两份清单**；确为有意修改事务边界时 **MUST 先在 `docs/domain/decisions.md` 立 ADR 再同步期望值**。特征断言**一律挂在 `PaySignServiceImpl` 门面上，NEVER 挪到某个领域服务的实现类上** —— 挂在被改的类上时，一搬家就得改测试，「绿」只证明代码和测试被一起改了。

## 状态取值（无枚举，String 常量）
- 签约状态（三个领域服务各自顶部常量，如 `ContractDomainServiceImpl:69~93`、`CallbackDomainServiceImpl:69~97`）：`NOT_SIGNED`、`SIGNED`、`UNSIGNED`、`FAILED`、`PENDING`、`SCANNING`、`SUCCESS`
- 钱包渠道 `0B` **MUST 走 `PaymentVendorEnum.WALLET.getCode()`**：原先三个类各硬编码一份、靠注释「三处同改」维持，2026-09-15 已收口，全模块 `= "0B"` 字面量 **0 处**，**NEVER 再写回字面量**
- 解约流程（`TerminationInternalServiceImpl`）：`PENDING`、`SCANNING`、`SUCCESS`、`FAILED`
- **MUST** 复用这些常量；新增状态值 **MUST** 全局 grep 字面量确认所有比较点。

真正的枚举只有两个，扩渠道时 **MUST** 改这里：
- `constant/PaymentVendorEnum.java`：`ALIPAY=03`、`WECHAT=04`、`ALIPAY_TRAVEL=05`、`LONG_PAY=06`、`CMB_BANK=0601`、`BOC_BANK=0602`、`CBDC_CONSTRUCTION=08`、`CBDC_BOC=0801`、`CBDC_PSBC=0802`、`CBDC_COMM=0803`、`WALLET=0B`、`CBDC_APP=0C`
- `constant/SignChannelEnum.java`：`METRO_APP` / `ALIPAY` / `WECHAT` / `UNION_PAY` / `LONG_PAY` / `WALLET`
- 错误码：`constant/PaySignErrorCodeEnum.java`

## 解约链路（改动前必读）

解约不是单次请求，是**三段式异步流程**，跨 `pay-sign-server` / `gate-txn-pay-server` / `account-server` 三个模块：

1. **IF8A-06 `requestTermination`** — APP 申请解约，插 `APP_TERMINATION_REQUEST`，`TERMINATION_STATUS=PENDING`，响应回填 `requestSignSeq`
2. **`/internal/termination/process`**（入参可省略，由 web-server 的 Quartz 任务 `terminationQuartzTask.confirmTermination()` 调用）— 扫表处理两类记录：
   - `PENDING`：调 `GateTxnPayClient.hasFailedOrder` 查该用户该渠道是否还有未结清欠费（结清的唯一标志是 `GATE_TXN_PAY.DEBIT_STATUS='SUCCESS'`）。有欠费 → 置 `FAILED` 并通知 APP；无欠费 → `PENDING→SCANNING` 并调支付平台解约
   - `SCANNING`：调支付平台 §2.4 `queryResult` 主动查协议状态，`status=UNSIGNED` 即复用 `receiveTerminationResult` 收口
   - 实现在 `TerminationProcessor`（独立 Bean，让每条记录跑在自己的事务里；与批处理入口同类会因 Spring 自调用不走代理而整批失效）
   - 旧的 `/internal/termination/check-failed-orders`、`/execute`、`/notify-failed` 三个接口仍在，但不是主线
3. **`receiveTerminationResult`** — 支付平台回调（代码内标号 `IPD03`，仅出现在 `CallbackDomainServiceImpl.java:234` 的注释/日志里，不是甲方编号），推进到 `SUCCESS` / `FAILED` 并异步通知 APP
   - **`cardId` / `cardType` MUST 从 `APP_TERMINATION_REQUEST` 补齐，NEVER 只依赖 `resolveCardInfoFromSignInfo`**（2026-09-09 事故 + 修复）。回调报文这两个字段为 null，而 `resolveCardInfoFromSignInfo` 的源表 `APP_PAY_SIGN_INFO.CARD_ID` / `CARD_TYPE` **全库为 NULL**（原因见 §签约结果查询不落库），补不出任何值；真值只在 `APP_TERMINATION_REQUEST` 里。补不齐会带 null 调 account `requestRemovePayChannel` → 参数校验失败 → 通道清理永远重推不成功。现由 `resolveCardInfoFromTerminationRequest` 兜底（实现 `CallbackDomainServiceImpl.java:562`，调用点 `:312`，**MUST 在幂等短路之后、状态白名单校验之前**）。实测案例 `requestSignSeq=0052294801523908`
4. **`/internal/termination/compensateNotify`**（无入参，供定时任务调用）— 扫 `APP_TERMINATION_REQUEST` 中 `TERMINATION_STATUS` 已是终态（`SUCCESS`/`FAILED`）、`NOTIFY_STATUS=FAILED`（或 `PENDING` 滞留超 10 分钟）且 `NVL(NOTIFY_RETRY_COUNT,0) < 10` 的记录重发通知，判定细节见 §幂等与重试。按 `TERMINATION_STATUS` 分流：`FAILED` 走 `doNotifyTerminationFailed`，其余走 `doNotifyTerminationResult`；解约时间取 `COMPLETE_TIME` 而非当前时间，保证重发报文与首次一致
   - **`/internal/paySign/compensateNotify` → `AppNotifyService.compensateSignNotify()` 只扫 `APP_PAY_SIGN_REQUEST`，覆盖不到解约表**，两者 **NEVER** 互相替代

`APP_TERMINATION_REQUEST` 有两个独立状态列（DDL 注释见 `pay-sign-schema.sql:180`、`:185`）：
- `TERMINATION_STATUS`：`PENDING` 待处理 → `SCANNING` 扫描中 → `SUCCESS` / `FAILED`
- `NOTIFY_STATUS`：`PENDING` / `SUCCESS` / `FAILED`，配合 `NOTIFY_RETRY_COUNT` 供 `compensateNotify` 扫表重试。**`PENDING` 是中间态而非「无需处理」**——补偿会把滞留超时的 `PENDING` 一并捞回重发，详见 §幂等与重试

### 解约申请满 4 天才确认（2026-09-07 新增）

业务规定：APP 申请解约支付方式后，**满 4 天才确认解约**。这个延迟是**扫描范围的筛选条件，不是调度频率**——由 `/internal/termination/process` 的入参表达，不是靠 Cron 少跑几次实现的。

- 截止点 `cutoff = referenceTime - delayDays`；只处理 `REQUEST_TIME <= cutoff` 的记录
- `referenceTime` 缺省取当前时间，格式 `yyyyMMdd`（当天 00:00:00）或 `yyyyMMddHHmmss`
- `delayDays` 缺省取 `pay-sign-server` 配置 `termination.confirm-delay-days`（默认 4）；**天数只在这一处配置**，`TerminationQuartzTask` 不再写一份
- **两个字段都不传 = 不按申请时间过滤**，与历史行为一致，老调用方不受影响
- 过滤**同时作用于 PENDING 与 SCANNING**，口径是「截止点之前仍未成功的都在扫描范围内」（用户 2026-09-07 确认）
- SQL 在 `AppTerminationRequestMapper.selectByStatusBefore`；`selectByStatusLimit`（不带 cutoff）保留给不传参的调用
- **筛选只有上界、没有下界**：`REQUEST_TIME <= cutoff`，所以每轮把 cutoff 之前所有仍未成功的申请都纳入，不是只取「正好第 N 天」那一批。**NEVER 改成固定时间窗**——`misfire_policy=3` 是「错过即放弃」，停机或滚动更新错过一轮后，固定窗口会让那一批申请永久没人处理；无下界才能自愈。重复扫描无害：状态查询是白名单（只有 PENDING / SCANNING），终态记录不在结果集里
- **调用方负责排空**：下游单轮对 PENDING 与 SCANNING 各只取 `BATCH_SIZE=200`（单轮上限 400 条）。`TerminationQuartzTask.invoke` 因此循环调用直到 `scanned == 0`，上限 `MAX_ROUNDS=20`；整个循环复用同一个 request 以固定 cutoff，**NEVER 每轮重新取当前时间**（边界会随耗时漂移，卡在边界上的申请会被漏掉）。若某轮 `scanned > 0` 但 `terminated + confirmed + rejected + expired == 0`，说明这批推不动，打 `warn` 提前结束等下一次调度，不刷满轮次

⚠️ **日级调度与 `SCANNING_TIMEOUT_MINUTES=1440` 的相互作用（已知风险，暂不处理）**：`TerminationProcessor` 的超时阈值注释写明是按「5 分钟一轮、重试约 288 次」标定的。当前只有每天 4 点一轮，一条 SCANNING 记录每 24 小时只被查一次，**下一轮时滞留已达阈值，一次 `queryResult` 查询失败就会 `expireScanning` 置 FAILED 并通知 APP 解约失败**，而此时本地签约记录与 account 支付通道都还没清。响应里 `expired > 0` 即属此类，`TerminationQuartzTask` 会打 `log.error`。

**处置结论：暂不处理（用户 2026-09-07 决定）**，带风险上线，靠 `expired > 0` 的 `log.error` 人工兜底。后续要修时**优先另建每 5 分钟只跑 SCANNING 收口的任务**，**NEVER 只是调大 `SCANNING_TIMEOUT_MINUTES`**——调大阈值只把误判时间往后推，SCANNING 记录依然一天只有一次收口机会。运维若收到「APP 提示解约失败、但支付中心协议已是 `UNSIGNED`」的投诉，先查这一条。


网关文档 §2.3 请求解约的 bizData **只有 `requestSignSeq`，没有 `notifyUrl`**；§五 说回调发往「商户配置的 `notifyUrl`」。支付（§1.1）、签约（§2.2）、退款（§3.1）都能逐次传回调地址，**解约是唯一一个只能靠支付中心侧商户配置的接口**——我方既看不到也改不了。
**Why**：只等回调，记录会永久卡在 `SCANNING`。生产已出现一例（`0052290701523020`：10:34 登记、11:53 置 `SCANNING`、13:33 人工补投回调才收口，用户在支付平台其实早已 `UNSIGNED`）。
**How to apply**：`SCANNING` 记录 **MUST** 由 `TerminationProcessor.confirmTermination` 主动查 §2.4 `queryResult` 收口。支付中心**没有独立的「解约结果查询」接口**，`queryResult` 的 `status` 同时承载签约态与解约态。回调保留作为快速路径，两条并存不冲突——`receiveTerminationResult` 对 `SUCCESS`/`FAILED` 有幂等短路（`CallbackDomainServiceImpl.java:301`）。

### 回调只接受 SCANNING
`CallbackDomainServiceImpl.java:315` 已实现状态白名单：**只有 `SCANNING` 允许继续处理**。
**Why**：`PENDING` 态尚未做未结清欠费校验，若放行会绕过前置校验直接删签约记录、清空默认支付通道并通知 APP 解约成功。
**How to apply**：新增任何解约回调入口 **MUST** 复用同一白名单，**NEVER** 只判「非终态即可处理」。

### 成功分支的操作顺序是强约束
`CallbackDomainServiceImpl.receiveTerminationResult` 成功分支（`:323` 起，方法本身**刻意不带 `@Transactional`**，理由写在 `:237~253` 的方法头注释里，**NEVER 删那段注释、NEVER 加回注解**），顺序 **MUST** 保持：

1. **阶段一 本地事务**（`transactionTemplate`，事务内 **NEVER 出网**）：查签约记录（通知报文要用）→ DELETE `APP_PAY_SIGN_INFO` → 写解约流水 → `markSuccess` 单条 CAS（`WHERE TERMINATION_STATUS='SCANNING'`）→ 仅当本次真的收口时 `initChannelSyncPending`
2. **阶段二 提交后**：先 `asyncNotifyTerminationResult` 通知 APP，**再** `syncChannelRemovalAfterCommit` 走 `ChannelSyncDeliverer` 删 account 侧支付通道

**Why**：`markSuccess` 与 `initChannelSyncPending` **MUST 同事务** —— 两者之间崩掉会留下 `TERMINATION_STATUS='SUCCESS'` 而 `CHANNEL_SYNC_STATUS=NULL`，而补偿扫表**刻意不捞 NULL**，这一笔的通道清理永久丢失。阶段二两步 **NEVER 反过来**：account-server 慢或不可达会把 APP 通知一起拖住，而通道清理已落 `PENDING`、补偿一定会重推。
**NEVER 退回「事务内先调 account 删通道、失败即整单回滚」**（ADR-D48 之前的形态）：那同时违反 AGENTS.md §5.2 的两条（事务内 RPC、事务内提交异步通知），且失败只能靠支付中心重推自愈。连带一条：包级方法 `removeAccountPayChannel`（「catch 全部异常后 return false」的旧样例）**已随 `PaySignWorkflow` 一并删除**（零调用方），通道清理的唯一实现是 `ChannelSyncDeliverer`；**NEVER 再写一个返回 boolean 的 RPC 包装方法**，新写的 MUST 返回 `RpcOutcome`（ADR-D45）。


### 待修问题（详见 `docs/reviews/解约链路代码审查记录-2026-08-25.md`）
- **P0：`/internal/**` 全部接口无鉴权、无归属校验**（`/internal/termination/**` 与 `/internal/paySign/compensateNotify`），`pay-sign-server` 无 spring-security、无拦截器兜底，枚举流水号即可强制解约任意用户；三个无入参批处理（`process` / 两个 `compensateNotify`）任何网络可达方都能反复触发批处理与对 APP 的通知重发。补鉴权 **MUST** 对齐现有验签实现，**NEVER** 自造签名逻辑。
- ~~**P1：事务内提交异步通知**~~ —— 2026-09-11 已修（pay-sign-server 2.0.75），仅限**签约成功分支** `CallbackDomainServiceImpl.receiveSignResult`（当时宿主是 `PaySignWorkflow`，2026-09-15 随拆分迁至此）：事务内改为 `eventPublisher.publishEvent(new SignResultCommittedEvent(requestSignSeq, paymentVendor, request))`，投递挪到 `paysign/event/SignResultCommittedListener`（`@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = false)`）。三条约束 **NEVER 改**：①`fallbackExecution` 保持 `false`，改 `true` 等于「无事务时立刻同步发 HTTP」，退回本次要消除的行为；②监听器**不加 `@Async`**（`asyncNotifySignResult` 本身已异步，再套一层异常彻底无声）；③监听器回查流水 **MUST 用 `selectLatestSignResultBySeq`**，用 `selectByRequestSignSeq` 会取到 `OPERATION_TYPE='SIGN'` 那行（`SIGN_STATUS` 为空、不在通知队列里）。事件是**进程内非持久**的，可靠性仍靠 `NOTIFY_STATUS='PENDING'` + `/internal/paySign/compensateNotify` 扫表补偿，监听器只是快速路径、内部 `catch` 全部异常只记日志（`AFTER_COMMIT` 里抛异常回滚不了已提交的事务，只会让本已成功的回调对支付平台报错、引来重推）。**摘掉 `receiveSignResult` 的 `@Transactional` 时 MUST 同步处理这个监听器**，否则通知静默不发（`fallbackExecution=false` 在无事务的类上等于静默不执行）。
  - ⚠️ **2.0.76 起该监听器做两件事**（ADR-D32）：APP 通知之后追加一次 `accountClient.syncPayAccountId`，把 `PAY_ACCOUNT_ID` 推给账户域 `APP_USER_PAY_CHANNEL`，补 ADR-D30 留下的「该列只有 IF8A-77 会写」缺口。**两者可靠性等级不同**：通知有 `NOTIFY_STATUS='PENDING'` + 扫表补偿兜底，回写**允许丢**（账户域那列是展示值、权威值在本域 `APP_PAY_SIGN_INFO`，且账户域 IF8A-77 仍会主动来取）。**NEVER 给回写加落库状态 + 扫表补偿**，也 **NEVER 让回写失败影响通知或签约结果**。之所以塞进同一个监听器而不新建一个，是因为本项目规定**一个事件类只挂一个监听器**。**NEVER 让这条链路去改 `USER_ITP_REG_INFO.THIRD_PAY_ID`** —— 那是 IF8A-77「更换默认支付方式」的业务动作产物。回写依赖账户域那一列的 DDL，**DDL 未执行时该调用恒返 9999**（不影响签约）。
- ~~**P1：`receiveTerminationResult` 事务内既提交通知又发 RPC**~~ —— 2026-09-12 已修（ADR-D48）：`@Transactional` 已摘掉，改成「本地短事务收口 + `CHANNEL_SYNC_*` outbox + 提交后出网」，形态见上面 §成功分支的操作顺序。现宿主 `CallbackDomainServiceImpl.receiveTerminationResult`（2026-09-15 随拆分迁入，注解仍然没有）。**NEVER 加回 `@Transactional`**，也 **NEVER 只照签约分支把通知挪到 `afterCommit` 而把 RPC 留在事务里** —— 那会造成「看起来已合规」的假象（2026-08-26 生产事故同一成因）。

- ~~**P1：`NOTIFY_RETRY_COUNT` 恒为 0**~~ —— 2026-08-26 已修：`AppNotifyServiceImpl.updateNotifyStatus(AppTerminationRequest,...)` 失败分支改为调用 `increaseNotifyRetryCount`，XML 用 `NVL(NOTIFY_RETRY_COUNT, 0) + 1` 兜住历史 NULL。**NEVER 去掉递增**——补偿接口按该字段设上限，不递增会无限重发。
- ~~**P1：解约通知无补偿入口**~~ —— 2026-08-26 已修：新增 `/internal/termination/compensateNotify`（`AppTerminationRequestMapper.selectCompensableNotify` + `AppNotifyService.asyncRetryTerminationNotify`），由外部定时任务调度。
- ~~**P1：补偿只扫 `FAILED`，滞留 `PENDING` 永不重发**~~ —— 2026-08-26 已修：两条补偿的扫描条件都加上「`PENDING` 且滞留超 10 分钟」，并在重发前先把该行落成 `FAILED` + 递增重试次数。同时修掉 `AppNotifyServiceImpl.updateNotifyStatus(PaySignRequest,...)` 对 `getNotifyRetryCount()` 的直接拆箱（NULL 会 NPE，导致状态回写整体失败、记录卡在 `PENDING`）与 `PaySignRequestMapper.increaseRetryCount` 缺失的 `NVL`。
- **P2：幂等是 select-then-update**，无行锁无 CAS，双回调并发会双跑。
- 内部接口 DTO 定义在模块本地，**未上移 `model`、`PaySignClient` 无对应方法**，调用方无法按项目既有方式复用。

### 同一签约流水的解约申请只能有一行，FAILED 靠 update 复活
`APP_TERMINATION_REQUEST.REQUEST_SIGN_SEQ` 上有唯一索引 `UK_ATR_REQUEST_SIGN_SEQ`。
`ContractDomainServiceImpl.requestTermination`（`:441`）在 insert 之前 **MUST** 先 `selectByRequestSignSeq`，按状态分流（2026-08-26 修复）：

- 不存在 → insert（`TERMINATION_STATUS=PENDING`，`NOTIFY_STATUS=PENDING`）
- `FAILED` → `reactivateFailed` 复活成 `PENDING`，清 `FAIL_REASON` / `SCAN_TIME` / `COMPLETE_TIME`，`NOTIFY_*` 一并复位，`REQUEST_TIME` 刷新为本次申请时间
- 其余（`SUCCESS` 等）→ 回 `8009`，**NEVER** 放行

**Why**：原来是无条件 insert。一次 `FAILED` 的解约（最常见是被未结清欠费拦下）之后，同一流水再申请必抛 `DuplicateKeyException`，被方法外层 catch 成 `9001 系统内部错误`——**用户从此永久无法解约，只能改库才能解开**。
**How to apply**：`reactivateFailed` 的 WHERE 带 `TERMINATION_STATUS = 'FAILED'`，是状态机白名单 + CAS，**MUST 检查返回行数**，0 行说明并发下状态已被改走，一律拒绝。`NOTIFY_*` 必须一起复位——上一轮已给 APP 发过解约失败通知，不复位则新一轮沿用旧的通知状态与重试轮次（可能已用尽重试预算），新一轮通知失败后补偿再也扫不到。`CREATE_TIME` 不动，审计口径是首次申请时间。

### SCANNING NEVER 无上限重试
`TerminationProcessor.confirmTermination` 里每个「本轮无法确认」的出口都走 `expireIfTimedOut`，滞留超过 `SCANNING_TIMEOUT_MINUTES`（24 小时）则 `expireScanning` 置 `FAILED` + 写明 `FAIL_REASON` + 通知 APP 解约失败，批处理响应新增 `expired` 计数（2026-08-26 新增）。

- 滞留起点取 `SCAN_TIME`（进入 `SCANNING`、调支付平台解约那一刻），`confirmTermination` 不刷新该列，所以它就是「在 SCANNING 待了多久」；为空退到 `REQUEST_TIME`；两列都空则只告警、**NEVER 凭空打 FAILED**
- `expireScanning` 一条语句落齐 4 个字段且 WHERE 带 `TERMINATION_STATUS = 'SCANNING'`。**MUST 用 CAS 且 MUST 单条**：`SCANNING` 是长期在途状态，解约回调随时可能把它收口成 `SUCCESS`，拆成 `updateFailReason` + `updateCompleteTime` 会把已收口的 `SUCCESS` 覆盖成 `FAILED`
- 阈值 **MUST 取得宽松**：打早了会在支付平台其实已解约、只是查询链路暂时不通时告诉 APP「解约失败」，而本地签约记录与 account 支付通道都还没清，用户以为还签着、免密扣款却已失效。超时日志用 `log.error`，`expired` 非 0 即需人工到支付中心核对协议真实状态
- 超时打成 `FAILED` 后，用户可从 APP 重新申请（走上面的 FAILED 复活），两个改动是配套的

## 移除签约（IF8A-36）与解约（IF8A-06）不是一回事 — 已登记，暂不实现

**业务口径（用户 2026-09-08 口述，唯一来源）**：解约 / 直接解约 **要** ITP 请求支付系统；**移除签约不请求支付系统**，直接把签约记录状态改成解约成功即可。调用场景是「用户协议在第三方已失效」或「钱包解绑」——协议在对端已不存在，再发解约请求没有意义。

**⚠️ 一句话结论，对外沟通时 MUST 原样引用：`requestAgreeRelease`（IF8A-36）只翻本地状态，它不是解约。** 它**不向支付中心发任何请求**，因此调用成功后 **ITP 侧 `SIGN_STATUS=UNSIGNED`、支付中心侧协议仍是 `SIGNED`（仍可被扣款）**。要真正在支付渠道解掉协议只有两条路：`requestTermination`（IF8A-06，走 `APP_TERMINATION_REQUEST` + 扫表推进）或 `unbindAgreement`（IF8A-75，立即调支付中心）。**NEVER 把它当成 IF8A-06 的同义词或「快捷解约」**，也 NEVER 用它替代解约做数据订正 —— 那会造出「本地已解约、渠道还能扣钱」的静默不一致，而这种不一致在我方任何表里都看不出来。2026-09-17 端到端实测已确认该行为（`docs/testing/pay-sign/e2e-2026-09-17.md` 发现③：调用后本地 `UNSIGNED` + `TERMINATION_TIME`，支付中心仍 `SIGNED`）。

现状与风险，恢复实现前 **MUST** 先看清：

- pay-sign 侧代码已存在（`PaySignAppController.java:61` → `PaySignServiceImpl` → `ContractDomainServiceImpl.removeSignAgreement`，DTO `RequestAgreeReleaseReqDTO` / `RequestAgreeReleaseResult` 在 `model`），但 **fep-app 无透传入口、`PaySignClient` 无对应方法，链路未接通**。
- APP 打 `/app/requestAgreeRelease` 落到的是 **account 侧「删支付通道」**（`AppAccountController.java:63` → `AccountClient.requestAgreeRelease` → `AccountApplicationServiceImpl:530` 直接委托 `requestRemovePayChannel`）。**同名不同义**，**NEVER** 在 fep-app 再加一个同路径路由。account 的 `/requestAgreeRelease` 还被 `ContractDomainServiceImpl.releaseWalletBinding`（钱包 `0B`，`:729`）内部调用，**NEVER 直接删**。
- 现有实现只写 `SIGN_STATUS=UNSIGNED` + `TERMINATION_TIME`，与解约成功分支（先调 account 清支付通道 → DELETE 签约记录 → 写流水 → 置 `APP_TERMINATION_REQUEST=SUCCESS` → 通知 APP）**行为不一致**；也没有归属校验，且不会收口同一流水已有的 `PENDING` 解约申请（留着会被扫表任务真的拿去调支付平台）。**2026-09-11 已补上其中两项**：状态白名单 + 幂等短路 —— 改走 CAS `markUnsigned`（`WHERE ... AND SIGN_STATUS='SIGNED'`），返 0 行时回查，已是 `UNSIGNED` 判幂等成功、其余状态返 `code=409`。**行为不一致、归属校验、`PENDING` 收口三项仍未做。**
- **改 `APP_PAY_SIGN_INFO.SIGN_STATUS` MUST 走 `PaySignInfoMapper` 的 4 条 CAS**（`markSigned` / `markSignFailed` / `markUnsigned` / `reactivateForResign`），迁移白名单与「为什么 `UNSIGNED -> SIGNED` 永久禁止」写在 mapper XML 注释里。`updateBySeq` **只用于回填 `PAY_ACCOUNT_ID` / `PAY_AGREEMENT_NO` 等非状态字段**，它的 WHERE 只有 `REQUEST_SIGN_SEQ`，拿它改状态会被并发回调互相覆盖。表上 2026-09-11 已加 `UK_APPSI_REQUEST_SIGN_SEQ`（唯一索引，此前该列无索引 ⇒ 全表扫描 UPDATE）。详见 `docs/domain/decisions.md` ADR-D12。
- 待裁决项与实施清单见 `docs/ops/README.md` 的 IF8A-36 章节。

## account-server 侧的解约配合
`AccountApplicationServiceImpl.requestRemovePayChannel`：
- 删支付渠道记录；**仅当该通道正好是注册信息里的默认通道**时，才清空 `thirdPayId`/`channel`/`reqContractNo` 并写 `USER_ITP_REG_LOG`（`OPER_TYPE=1`）。日志写在 `if` 块内，避免无效日志。
- 该方法 `@Transactional` 但整体 catch Exception 后返回 SYSTEM_ERROR、**异常不外抛**，日志插入失败时通道清空仍会提交（审查记录 P1-5）。改这里 **MUST** 让通道清空与日志写入同生共死。

## `APP_PAY_SIGN_INFO` 只有两个合法创建入口
签约主表的 INSERT **MUST** 只出现在这两处，要加第三处 **MUST** 先说清凭什么确定归属：

- `CallbackDomainServiceImpl.receiveSignResult` 成功分支（`:192`）——支付平台签约结果回调，报文自带 `payUserId` / `payAgreementNo` / `displayAccount`
- `ContractDomainServiceImpl.alipayTripRequestSignInfo` 同步确认分支（`:239`）——支付宝出行渠道侧同步确认，不调支付平台

**`requestContractResult`（IF8A-22）是查询接口，NEVER 在其中 INSERT**（2026-08-26 修复）：

- **Why**：本接口报文只有 `thirdUserId` / `requestSignSeq` / `paymentVendor`，拿不到 `CARD_ID` / `CARD_TYPE`，插出来的记录这两列恒为 NULL；解约链路要靠它们调 account `requestRemovePayChannel` 做卡信息比对（`AccountApplicationServiceImpl:481-487` 不匹配即 `INVALID_PARAM`）。更要紧的是本模块无鉴权，允许查询接口落库等于任何网络可达方带一个流水号、凭支付平台的返回就能给任意 `thirdUserId` 造出一条 `SIGNED` 记录。
- **已发生**：生产库 `00522908`(vendor 03)、`00522914`(03/04) 共 3 条 `SIGNED` 孤儿签约，`USER_ITP_REG_INFO` 里这两个 `thirdUserId` 一行都没有。这类记录**解约必然失败**（account 侧返 `NO_ACCOUNT_CARD` → `TerminationException` → 永久停在 `SCANNING`），只能改库清理。
- **How to apply**：支付平台返回 `SIGNED` 而本地无记录时，只 `log.warn` 并把状态回给调用方。这种「孤儿协议」要走人工核查，**NEVER 顺手补一条**。

**归属校验**：`selectBySeq(requestSignSeq, null)` 按主键查、不带用户维度，命中后 **MUST** 比对 `THIRD_USER_ID`，不匹配统一回 `8012 签约记录不存在`（**NEVER** 回「不属于你」，那等于给出存在性探测口）。所有按 `requestSignSeq` 单键查询的接口都适用同一口径。

## 数据表
`APP_PAY_SIGN_INFO`、`APP_PAY_SIGN_REQUEST`、`APP_PAY_SIGN_LOG`、`APP_TERMINATION_REQUEST`、`PAY_TXN_DETAIL`、`PAY_REFUND_DETAIL`、`PAY_CALLBACK_LOG`、`APP_USER_PAY_CHANNEL`

DDL 分两处，**改表结构前先确认改哪个脚本**：
- `pay-sign-server/src/main/resources/sql/pay-sign-schema.sql` — `APP_PAY_SIGN_INFO`、`APP_PAY_SIGN_LOG`、`APP_PAY_SIGN_REQUEST`、`APP_USER_PAY_CHANNEL`、`APP_TERMINATION_REQUEST`
- `pay-sign-server/src/main/resources/sql/pay-txn-schema.sql` — `PAY_TXN_DETAIL`(:6)、`PAY_REFUND_DETAIL`(:140)、`PAY_CALLBACK_LOG`(:225)（2026-09-14 从 `fep-dev-server` 迁入，内容未改、行号仍有效）

## 钱包渠道（`0B`）免密扣款的出向报文契约（2026-09-16，pay-sign-server 2.0.96，ADR-D105）
- **`requestSignSeq` 直接用钱包的 `THIRD_PAY_ID`，NEVER 给 `0B` 另建 §2.2 contract 签约**。接口方给出的「正确请求」样例里 `requestSignSeq` 就是钱包侧的支付账号标识，与 `APP_PAY_SIGN_INFO` 无关；`0B` 的签约关系在钱包侧、由账户域开户 / 加通道时拿到并落 `USER_ITP_REG_INFO.REQ_CONTRACT_NO`（= `THIRD_PAY_ID`）。**此前设计的「方案一：先给钱包补一次 contract 签约再扣款」整条作废、NEVER 复活** —— 那条路在支付中心侧没有对应的签约类型，做不出来。
- **`payUserId` NEVER 进 `requestPay` 报文**（2026-09-16 从 `PaySignGatewayMessages.buildRequestPayBizData` 删除）。网关文档 §1.1 的 16 字段表里**没有这个字段**，它只属于 §2.2 `contract`（原文注为「数字人民币子钱包推送专用」）；接口方的钱包扣款样例里也没有它。而 `PayGatewayClient.buildSignSource` 把 bizData 的**全部非空字段**按 `TreeMap` 升序拼待签串 —— **多送一个未定义字段就是另一个签名**。同批把 `PaymentDomainServiceImpl.validatePaySignInfo` 里「钱包必须有 payUserId」那条本地前置校验一并删除（字段既然不出网，拿它拦请求只是把能成的单子挡在本地）。**但 `payUserId` 仍照旧落 `PAY_TXN_DETAIL.PAY_USER_ID`**（对账与排查要用），`applyAccountUserView` 那处赋值 **NEVER 一起删**。
- **`0B` 扣款到 2026-09-16 仍未打通，且 `payUserId` 不是成因**。删掉它之后重试（2.0.96 已部署、日志证实报文不含该字段），网关照旧返**不带 `msg` 的裸 `{"code":9999}`**。这个改动是「契约对齐」而非「故障修复」，**NEVER 因为「改完还是 9999」就回头怀疑 payUserId 或重查签名拼串** —— 那一段已经取过硬证据。
- **剩余与样例的差异只有三个非必填字段**，都不在 §1.1 的必填 / 条件必填清单里，因此**文档已经解释不了这个 9999**，MUST 由接口方回答：①`notifyUrl`（我方送真值、样例送空串）；②`returnUrl`（我方不送、样例送空串）；③`industryDetail`（我方按行程明细送、样例不送）。三问是：裸 `9999` 无 `msg` 到底代表什么？`notifyUrl` 能否送我方公网地址？`industryDetail` / `returnUrl` 在 `0B` 场景是否实际必需？
- **`industryDetail` 在 §1.1 是「否」（非必填）**，核对过原文字段表；但文档给的 5 键示例结构与我方实际组装不一致，**NEVER 照那个示例改字段名** —— 它没有真实应答做证据，违反 ADR-D92（外部网关任何契约细节 MUST 有实测应答）。

## 幂等与重试（实际实现）
- 唯一索引：`UK_APP_PAY_SIGN_INFO_USER_VENDOR (THIRD_USER_ID, PAYMENT_VENDOR)`、`UK_ATR_REQUEST_SIGN_SEQ`
- `PAY_TXN_DETAIL` 建记录只有一处：`PaymentDomainServiceImpl.ensurePayTxn`（`:560`，先 `selectByOrderNo` 判存在再 insert，`DuplicateKeyException` 只兜真并发窗口，warn 不带堆栈）。**NEVER 在别处再调一次 insert**——`resolvePaySignInfoFromAccount`（原 `resolveAndCreatePayTxnFromAccount`，现 `:633`）原先也建记录，导致每笔交易插两次：第一次发生在 `pay.sign.test-force-amount` 覆盖金额之前（覆盖点现在是 `PaymentDomainServiceImpl.java:165~167`），`AMOUNT` 落原始金额与实际请求支付平台的金额不一致，第二次必然撞唯一索引刷一段 ORA-00001 堆栈（2026-08-26 修复）。该方法现在只负责从 account-server 补 `paymentVendor` / `requestSignSeq` / `payUserId`。
- MERGE 幂等写入：`PaySignLogMapper.xml`（`MERGE INTO APP_PAY_SIGN_LOG`）、`PaySignInfoMapper.xml`（`MERGE INTO APP_PAY_SIGN_INFO`）
- 通知补偿分两条，**扫的表不同、NEVER 混为一谈**，且都由外部定时任务调度：
  - `/internal/paySign/compensateNotify` → `compensateSignNotify()`，只扫 `APP_PAY_SIGN_REQUEST` 中 `OPERATION_TYPE='RECEIVE_SIGN_RESULT'` 的流水（**只管签约结果通知**）
  - `/internal/termination/compensateNotify` → `compensateTerminationNotify()`，只扫 `APP_TERMINATION_REQUEST`（解约结果通知）
    - **`selectCompensableNotify` 的 `TERMINATION_STATUS` 终态白名单 NEVER 去掉**：只取 `SUCCESS` / `FAILED`。通知内容完全由该列决定（`AppNotifyServiceImpl.asyncRetryTerminationNotify`：`FAILED` 发解约失败、**其余一律发解约成功**），`PENDING` / `SCANNING` 根本还没有可发的通知。而解约申请插入时 `NOTIFY_STATUS` 初值就是 `PENDING`、`NOTIFY_TIME` 为 NULL，一条卡在 `PENDING`/`SCANNING` 的申请（欠费查询一直失败、或支付平台迟迟不解约）滞留超 `staleMinutes` 就会被当成「成功通知丢了」，给 APP 发一条 `status=SUCCESS`、`dismissalTime` 为空的**假解约成功通知**——而 APP 侧实测没有按 `requestSignSeq` 幂等，污染的是对端状态、我方无法回滚（2026-08-26 修复）。
  - **一类通知只能有一个补偿队列**：`APP_PAY_SIGN_REQUEST` 是签约流水表，对签约结果通知它同时是「流水 + 队列」（签约没有独立申请表，这里是唯一队列）；对解约结果通知它**只是流水**，队列是 `APP_TERMINATION_REQUEST`。
    - **NEVER 把 `RECEIVE_TERMINATION_RESULT` 加回 `selectCompensableNotify` 的 `OPERATION_TYPE` 条件**，也 **NEVER** 在插入这类流水时写 `NOTIFY_STATUS` / `NOTIFY_RETRY_COUNT`。
    - **已发生（2026-08-26 修复）**：`PaySignWorkflow` 解约成功分支同时往两张表写 `NOTIFY_STATUS='PENDING'`，但只发一次通知、结果只回写 `APP_TERMINATION_REQUEST`（`AppNotifyServiceImpl.updateNotifyStatus(AppTerminationRequest…)`）。`APP_PAY_SIGN_REQUEST` 那行无人回写、永久停在 `PENDING`，过 `staleMinutes` 就被签约补偿扫到——**每成功解约一次就留一颗重复通知的雷**。生产库里查到 ID=745 / 749 两条即此类幽灵行。
    - 修复位置：`PaySignRequestMapper.xml`（白名单收窄为单值）、`PaySignWorkflow.java` 解约成功分支（不再写通知列；**该类已于 2026-09-15 删除，这段逻辑现在 `CallbackDomainServiceImpl.receiveTerminationResult`**）、`AppNotifyServiceImpl.compensateSignNotify`（删掉按 `OPERATION_TYPE` 分派的死分支与 `doNotifyTerminationResult(PaySignRequest,…)` 重载）、`PaySignInternalController` Javadoc。
    - **判断某类通知归谁补偿的口径**：谁持有该通知的状态与重试轮次、谁能收到结果回写，就归谁。**NEVER 用「两处都更新」来解决**——那只是把「漏更新」变成「必须手工同步维护两份真相」，还会再漏。
  - 两者扫描条件相同：`NOTIFY_STATUS='FAILED'`，**或** `NOTIFY_STATUS='PENDING'` 且滞留超过 10 分钟（`PENDING_STALE_MINUTES`）；再叠加 `NVL(NOTIFY_RETRY_COUNT,0) < 10`，单次上限 200 条、走同一个 `notifyExecutor` 线程池；依赖索引 `IDX_ATR_NOTIFY_STATUS(NOTIFY_STATUS, NOTIFY_RETRY_COUNT)`
  - **补偿 MUST 同时覆盖 `PENDING`**：两张表的流水插入时写的都是 `NOTIFY_STATUS='PENDING'`（`CallbackDomainServiceImpl.java:205` 签约结果 / `:352` 解约结果、`TerminationProcessor.java:197`），只有通知结果回写才会变成 `SUCCESS`/`FAILED`。若那次回写自身失败（进程被杀、DB 抖动、空值拆箱 NPE），记录永久停在 `PENDING`，只扫 `FAILED` 的补偿完全看不到它。**已发生事故**：2026-08-26 `APP_PAY_SIGN_REQUEST` ID=725 通知返回 `HTTP404`，其后三轮补偿全部 `fetchRowCount:0`，无人重发。
  - 滞留阈值 **MUST 显著大于外部调度周期**（建议调度 5 分钟 / 阈值 10 分钟）。阈值过小会把「刚提交、还没回」的正常在途通知判成滞留并重复发送。
  - 滞留判断与排序都用 `NVL(NOTIFY_TIME, CREATE_TMS|CREATE_TIME)`：`PENDING` 行 `NOTIFY_TIME` 为 NULL，只看该列时比较结果为 UNKNOWN，且 Oracle 升序把 NULL 排到最后，两处都会漏掉待补记录。
  - **重试计数只在「提交重发前」加一次**：补偿对扫到的每一条（`PENDING` 与 `FAILED` 一视同仁）先调 `increaseRetryCount` / `increaseNotifyRetryCount`（两条语句同时把 `NOTIFY_STATUS` 落成 `FAILED`），再提交重发；**通知结果回写只记 `NOTIFY_STATUS` / `NOTIFY_TIME` / `NOTIFY_RESULT`，NEVER 再动 `NOTIFY_RETRY_COUNT`**。
    - 为什么计数放在提交前：回写本身可能丢（进程被杀、DB 抖动），计数若依赖回写就可能永远不涨，`PENDING` 行会被下一轮反复扫到 —— 退化成无上限重复通知。先落库才有上限保证。
    - 为什么回写不能再加：两处都加会让一轮涨 2，预算按半速消耗，且与只走回写的路径口径不一致。**已发生**：2026-08-26 首次补偿的 21 条 `PENDING` 行日志显示 `retryCount=0` → 预翻转 1 → 回写 `NOTIFY_RETRY_COUNT='2'`（当轮已修）。
    - 口径：`NOTIFY_RETRY_COUNT` = 补偿重发轮次。首次通知（`CallbackDomainServiceImpl` / `TerminationProcessor` 触发）失败不计数，落 `FAILED` 后由补偿接管，共 1 + 10 次投递机会（上限来自 `app.notify.max-retry-count`，见下方「上限口径」）。
  - **重发对 APP 不是绝对不重复**：`PENDING` 也可能是「通知已到达、只是回写丢了」，补偿会再发一次。APP 侧按 `requestSignSeq` 幂等是前提。
  - **要人工重放某一笔签约通知，用 `/internal/paySign/resendNotify`（2026-09-08 新增），NEVER 打批量补偿**。该接口只按入参 `requestSignSeq` 取一条 `RECEIVE_SIGN_RESULT` 流水、**同步**投递并回写 `NOTIFY_STATUS` / `NOTIFY_RESULT`，返回体 `notified` 就是这次的真实结果；**不递增 `NOTIFY_RETRY_COUNT`**（那是补偿队列的预算，人工重放占用会让真实故障少一次自动重试机会）。前置校验是白名单：`APP_PAY_SIGN_INFO` 存在且 `SIGN_STATUS='SIGNED'`、且已有 `RECEIVE_SIGN_RESULT` 流水，缺任一条直接拒（`8012` / `8011`）——没有「签约已成功」的事实就不该有签约成功通知发出去。
  - ⚠️ **实测 APP 侧没有按 `requestSignSeq` 幂等**（2026-08-26）。补偿重发一条历史签约成功通知（`0052290701522998`）后，APP 把它当成新签约：回调我方开通支付通道，`APP_USER_PAY_CHANNEL` 多出一行 `REQ_CONTRACT_NO=0052290701522998`（`APP_PAY_SIGN_INFO` 里并无此流水），且 APP 自己的「当前签约号」也被改成了这个历史号，随后点解绑一律返 `8011 用户未签约`（`ContractDomainServiceImpl.requestTermination:465` 按流水号查签约信息，查不到即报此码）。
    **How to apply:** 在 APP 侧补齐幂等之前，**NEVER 为了「测补偿」去打 `/internal/*/compensateNotify`**——库里只要还有 `NVL(NOTIFY_RETRY_COUNT,0) < 10` 的历史流水，每打一次就往 APP 灌一条假签约成功，污染的是对端状态、我方无法回滚。要测补偿 MUST 先把历史测试流水的 `NOTIFY_STATUS` 处理成终态，或只用当轮新建的流水测。
- **APP 通知成功判定（`AppNotificationClient.notify`）三级，逐级都算失败**：
  1. HTTP 非 2xx → `HTTP<code>`；
  2. 响应体为空或解析后无 `retCode` → `响应体为空` / `响应无retCode`。**NEVER 把空体当成功**——APP 正常应答一定带 `retCode`，空体只可能是网关截断 / 502 被中间层改写成 200 / 应用抛异常返回空，判成功会让通知静默丢失且不再重试；
  3. `retCode` 不在 `app.notify.success-ret-codes`（逗号分隔，代码默认 `0000`，`application.properties:41` 配为 `0000,7004`）→ `业务失败:<retCode>:<retMsg>`，retCode 入 `NOTIFY_RESULT` 便于事后核查。
  - `7004` = APP 侧「已处理 / 重复通知」，属幂等码，按成功收口（用户 2026-08-26 确认）。**新增成功码 MUST 拿到 APP 侧码表或书面确认**：错误加码会把真实失败判成成功，从此不再重试、补偿扫不到、`NOTIFY_RESULT` 却记着成功，事后无从发现；反之判成失败最多重试到 `app.notify.max-retry-count` 上限后停在 `FAILED`，数据可见、可人工重放。加码只改配置，**NEVER 硬编码进代码**。
  - **上限口径（2026-09-12 按代码更正，此前本文档全篇写「3 次」是错的）**：`app.notify.max-retry-count`，**代码默认 10、`pay-sign-server/src/main/resources/application.properties:67` 也配的 10**，两处 `@Value("${app.notify.max-retry-count:10}")`（`TerminationInternalServiceImpl.java:83`、`AppNotifyServiceImpl.java:95`）。**上限只写在 SQL**（`AppTerminationRequestMapper.xml:121` 的 `NVL(NOTIFY_RETRY_COUNT,0) < #{maxRetryCount}`），Java 侧没有任何 `retryCount >= max` 判断；累加语句 `increaseNotifyRetryCount` 自身**不带上限**，刹车完全靠扫表语句过滤。**改上限只改配置即可，NEVER 去 mapper 里写死数字。** 注意 `APP_PAY_SIGN_REQUEST` 那条同型语句的参数名是 **`maxRetry`**（不是 `maxRetryCount`），两处勿混。
- **无 MQ、无 Redis、无 Spring Retry、无服务内 `@Scheduled`**。新增异步补偿 **MUST** 沿用「落库状态 + 无入参内部接口 + 外部定时任务扫表」模式。

### 支付结果回调的重推硬限次（2026-08-26 新增）
- `receivePayResult`（现宿主 `PaymentDomainServiceImpl.java:364`，2026-09-15 随支付组一起搬来，**它落在支付领域而非回调领域是有意的**：整个方法只读写 `PAY_CALLBACK_LOG` / `PAY_TXN_DETAIL`，并与 `requestPay` 共用 `resolveDebitRequestResult` 等支付组私有方法）**MUST 不带 `@Transactional`**：该方法内要调 `gate-txn-pay-server` 的 `syncDebitStatus`，事务包住网络调用会把行锁持有时长拉成对端响应时长，上游重推全部堆在同一行上，最终 Druid 强杀连接、`PAY_CALLBACK_LOG` 连同业务 UPDATE 一起回滚。见 `AGENTS.md` §5.2 与 `docs/ops/生产环境清单.md` §六 P0。
- 硬限次 `MAX_PAY_CALLBACK_PUSH = 2`（首推 1 次 + 重推 1 次）。计数用 `PayCallbackLogMapper.countByMerchantOrderNo(merchantOrderNo, 'PAY')`，**计数点 MUST 在 `PAY_CALLBACK_LOG` 的 insert 之后**——方法无事务，insert 已自动提交，这个 COUNT 才等于支付中心实际推送次数（含本次）。
- 达到上限后，无论本次处理成功与否都 `fillSuccess` 返回 `0000`，让支付中心停止重推。三条失败路径都走 `giveUpRetry`：`updatePayCallback` 命中 0 行、`syncGateTxnPayStatus` 失败、`catch (Exception)`。
- **代价：真实失败不再被上游重试。** 因此 `giveUpRetry` **MUST** 同时做两件事——打 ERROR 日志、把该笔最后一条 `PAY_CALLBACK_LOG` 的 `HANDLE_STATUS` 置为 `MANUAL`（`markManualByMerchantOrderNo`，`HANDLE_MSG` 记原因，截断 300 字）。**运维 MUST 例行巡检 `HANDLE_STATUS='MANUAL'`**，否则失败会静默沉底。
- ⚠️ **支付中心的重推次数 / 间隔 / 上限没有规格**：`docs/external/支付中心网关接口文档.md` §5（行 437~440）只写了「商户需返回通用响应表示接收成功 / 若未收到成功响应，系统会进行重试」，既无次数也无退避策略；生产实测间隔约 15~30s。**MUST 向供方索取重试规格**，拿到后再校准 `MAX_PAY_CALLBACK_PUSH`，**NEVER** 凭日志观察值当契约。

### 支付结果回调的优惠三字段（网关 V1.2，2026-09-18 新增，ADR-D136）
- `discountInfo`（渠道优惠详情，JSON 数组原文）落 **`PAY_CALLBACK_LOG.DISCOUNT_INFO`**（`VARCHAR2(2000 CHAR)`，已在 `AFCITPDB` 执行 `pay-sign-callback-discount-info-migration.sql` 并回查 `USER_TAB_COLS`）。写入点是 `PayTxnRules.buildPayCallbackLog`。落这张表而不是主表，是因为它是**回调事实**、随每次重推各存一份。**NEVER 写进 `PAY_TXN_DETAIL.DISCOUNT_INFO`** —— 那一列是闸机侧自算优惠（`RequestPayReqDTO.discountInfo`），两个口径混一列后无法区分。
- 该字段在 `ReceivePayResultReqDTO` 里是 `String`，**NEVER 为它建嵌套 DTO**：它是对外契约（`parseBizData` 可解析），且 Fastjson2 会把支付中心送的 JSON 数组自动归一成转义字符串赋给 `String` 字段（2026-09-18 端到端实测通过）。
- `PAY_TXN_DETAIL.CASH_AMOUNT` / `COUPON_AMOUNT` 当前是**无效值**：2026-09-18 实测 179/179 笔满足 `cashAmount == couponAmount == totalAmount`，`discountInfo` 下发 0 笔。**NEVER 用这两列做任何优惠口径的报表 / 对账 / 结算**（`docs/business/recon.md` 里「gate-txn-pay 无可靠优惠列」是同一类问题）。
- 按用户 2026-09-18 裁决：回调侧**不做任何金额关系校验**（`cash + coupon` 与 `total` 的加法关系未经供方确认），一律原样照写；**存量数据不处理**。**NEVER 加回「金额拆分自相矛盾则不回写」那类判定**。
- **原先列的三条「待向支付中心澄清」已按用户 2026-09-18 裁决关闭**（原话「你正确落库即可」），我方只对落库正确性负责，**NEVER 再把它们写成待办**：`cash + coupon` 加法关系与 `discountInfo` 下发时间都不影响落库（对方送什么存什么、列与写入点已就位，开始下发即自动落库、无需再改代码）；`payQuery` 口径见下条。
- **`payQuery`（`pay.sign.pay-query-url`）刻意不落库，NEVER 改成「顺手把优惠字段也存下来」。** 它在本模块的唯一用途是 `PaymentGatewayAdapter.queryPayStatus` → `PaymentDomainServiceImpl.queryGatewayPayStatus` → `addBlacklistForPaymentFailure` 的**拉黑前二次确认**，只在 `requestPay` 返 `Rejected` 且 `paymentVendor ∈ {03, 05}` 时出网，应答只读 `status` 一个键。三条判据：①它只在**支付失败**那一支被调用，那一支的优惠金额没有业务含义，状态收敛靠回调；②往 `PAY_CALLBACK_LOG` 插行会虚增 `countByMerchantOrderNo(merchantOrderNo, 'PAY')` 的计数，`MAX_PAY_CALLBACK_PUSH = 2` 提前触顶后支付中心停止重推、**真实失败静默沉底**；③往 `PAY_TXN_DETAIL` 回写等于把已知无效值灌进主表，且网关 §1.2 响应表里**没有 `discountInfo`**（只有 V1.2 回调才有）。要复盘某笔查询回来是什么，**MUST 搜日志「拉黑前查询支付中心支付状态」（应答全文已 INFO 打出），NEVER 去库里找**。详见 ADR-D136 决策三。
- **判断对方是否已改成有效值 MUST 现查 `PAY_CALLBACK_LOG.RAW_BODY`，NEVER 引用本节的数字。**

## 退款链路（改动前必读）

**宿主是 `RefundDomainServiceImpl`**（2026-09-15 从 `PaymentDomainServiceImpl` 拆出，ADR-D91）：
`requestRefund`（IF8A 侧入口，经 `PaySignServiceImpl` 路由）、`compensateRefundQuery`
（含 `settleRefundByQuery` / `resolveRefundQueryStatus`）、`compensateRefundSummary`。
**NEVER 再去 `PaymentDomainServiceImpl` 找退款代码**，那里只剩免密扣款与支付回调。

`requestRefund` **刻意不带 `@Transactional`**（批次 5B，ADR-D90 的兄弟批次）：它要出网调支付中心，
事务包住网络调用即复现 2026-08-26 那类事故。移出事务后「明细置终态」与「重算原单已退总额」
不再原子，配套补偿就是 `compensateRefundQuery`（`sys_job` **122**，`0 0/10 * * * ?`）。
**删掉那个补偿端点等于只做了 ADR-D8 的前一半，NEVER 删。**

### 退款汇总跨表对账：A 类可自愈、B 类不可自愈（NEVER 混为一谈）

`POST /internal/payment/compensateRefundSummary`（`sys_job` **123**，`0 15 * * * ?`）**不出网**，
只重算 `PAY_TXN_DETAIL` 与 `PAY_REFUND_DETAIL` 之间的汇总，分两类：

| 类别 | 判据 | 处置 |
|---|---|---|
| **A 可自愈** | `PAY_TXN_DETAIL` **有**该 `ORDER_NO`，但 `NVL(P.REFUND_AMOUNT,0)` ≠ 明细中该单 `REFUND_STATUS='SUCCESS'` 的金额合计 | 逐单调 `payTxnDetailMapper.updateRefundSummary(orderNo)`，**影响行数 > 0 才计 `submitted`** |
| **B 不可自愈** | 明细有 `REFUND_STATUS='SUCCESS'` 的行，但 `PAY_TXN_DETAIL` 里**没有**该 `ORDER_NO` | `updateRefundSummary` 必然影响 0 行，**MUST NOT 当成修好**：一行都不改，只记 WARN 计入 `skipped`，等人工 |

**核心判据：把「影响 0 行」当成功，会让扫表每一轮都报成功、不一致从此永远不可见。**
连带一条：**`compensateRefundSummary` 的 `skipped` 长期非 0 是预期**，因此 web-admin 侧的
`RefundCompensateQuartzTask` **MUST NOT** 照抄 `NotifyCompensateQuartzTask` 里
「`skipped > 0` 就 `log.error`」的写法（那会让调度日志每轮一条 ERROR、淹掉真实故障），
已改成 `log.warn` 并在消息里点明「其中含不可自愈记录，不代表本轮失败」。

两条扫表 SQL 都带 `TXN_DATE >= #{txnDateFrom}`（今天−7 天）做分区裁剪 + `ROWNUM <= #{limit}`；
**A / B 各自独立限流 200**（单轮 `scanned` 上限 400），**刻意不共享预算** ——
共享时 B 类常驻会把 A 类饿死。

## 已知坑（退款侧，2026-09-15 实测）

- **B 类不一致真实存在 7 条，A 类 0 条**。`PAY_REFUND_DETAIL` 状态分布 `PROCESSING` 1 / `RETRY` 6 / `SUCCESS` 43。
  43 个有成功退款的订单里 **7 个在 `PAY_TXN_DETAIL` 里根本没有对应行**：
  `280640445149511680`、`280642220672843776`、`GT20260825210458590586419`、
  `GT20260825212651982586419`、`GT20260825221334566586419`、`GT20260826192157073586419`
  （**后 4 条集中在 2026-08-26 20:33 同一分钟**，`GT` 前缀是免密扣款单，与那晚 21:07 的事务丢弃事故同一天）、
  `280638294097559552`（当天回查收口后新暴露）。
  反过来说，「第 8 步写 `SUCCESS`、第 9 步 `updateRefundSummary` 失败」这个 A 类中间态**至今没有真实发生过**，
  37 个能对上原单的订单汇总与明细完全一致。**排查退款汇总对不上 MUST 先分清是 A 还是 B。**
- **`RETRY` 状态的 6 条无人补偿**：`compensateRefundQuery` 的状态白名单**只认 `PROCESSING`**。
- **2 行畸形 `TXN_DATE`**：`28063829` / `28064044`，取的是 `ORDER_NO` 前 8 位而非 `yyyyMMdd`。
  `TXN_DATE` 是 `PAY_REFUND_DETAIL` 的**分区键**、也是 `UK_PAY_REFUND_DETAIL_NO` 的组成部分，
  这两行落在错误分区。**注意方向**：字符串比较下 `'28...' > '2026...'`，它们**恒大于任何 `2026xxxx`**，
  因此**永远落在 `TXN_DATE >= 今天−7天` 的窗口内、每轮必被扫到**。
  （**NEVER 写成「恒小于、永久扫不到」** —— 那个说法是错的，已被实测推翻。）
- **渠道退款号三列全为 NULL**：`REFUND_NO` / `CHANNEL_REFUND_NO` / `MERCHANT_REFUND_NO`
  在 43 条 `SUCCESS` 退款上**全是 NULL**（回查收口后仍然是），
  因此**按退款号与支付中心对账做不到**。MUST 向 bestonepay 索取完整字段清单（见下条）。

### 支付中心 refundQuery 的字段名与文档不一致（2026-09-15 实测，ADR-D92）

`docs/external/支付中心网关接口文档.md` §3.2 参数表写的是 `refundOrderNo`（退款订单号）与
`merchantRefundNo`（商户退款订单号）、注「至少填一个」。我方原先**只送 `refundOrderNo`**，
真实网关的应答是 `{"code":9999,"msg":"退款流水号或商户退款流水号必填"}`
（它认为两个都没填；且措辞是「**流水号**」而文档写「**订单号**」）。

**后果不是报错而是永久空转**：`resolveRefundQueryStatus` 拿不到终态 → `delayNextRefundQuery`
推 5 分钟 → 下轮再来，退款单永远收不了口，而端点每轮都返 `0000 成功`、调度日志一片绿。
**既不报警也不自愈。**

修法：`settleRefundByQuery` 改成**两个键都送、值都填我方的 `REFUND_ORDER_NO`**（文档明写「至少填一个」，多送不违规）。
2.0.88 部署后打同一条单**一次就通**：2.0.87 是 `scanned=1, submitted=0, skipped=1` + `code=9999`，
2.0.88 是 `scanned=1, submitted=1, skipped=0` 收口成功。那条卡了 **82 天**的退款
`RF2026062516090566197559552` 收口：`REFUND_STATUS` `PROCESSING` → `SUCCESS`，
`REFUND_TIME` = **2026-06-25T16:09:09**（距我方发起只隔 4 秒 —— **钱三个月前就退成功了，只是我方丢了那次应答**），
`NEXT_REQUEST_TIME` 被 `finishFromQuery` 清成 NULL，`REQUEST_COUNT` 仍为 1（回查不是发起退款，**NEVER 让它递增**）。

**判据**：这是「网关路径必须实测」那条教训的第二个变种 —— 出向 bizData 的**字段名**同样不能只看文档。
外部网关的**任何**契约细节（路径、字段名、必填性）都 **MUST 有一次真实应答做证据**，
**NEVER 只凭供方文档就认为对**。

## 关联依赖
- 解约前置校验调 `gate-txn-pay-server` 的 `/hasFailedOrder`：存在扣费失败订单时不得解约
- 签名工具 `util/RSASignUtils.java`（渠道侧 RSA）。**NEVER** 擅自修改签名/加密逻辑，**MUST** 提示人工复核

## 参考原始文档
- `docs/接口规范文档/青岛地铁-ITP与APP接口规范R6.docx`（IF8A-06/16/19/21/22/36、IF8B-02）
- `docs/技术规范文档/城市轨道交通自动售检票系统技术规范-第9部分-互联网业务规范.docx`

## 附：pay-sign-server 源码注释知识抽取（2026-09-16，阶段一）

> 抽取范围：`pay-sign-server/src/main/java/**/*.java`（92 个文件、约 11.1k 行、注释约 3.3k 行）与
> `pay-sign-server/src/main/resources/mapper/*.xml`（7 个文件、注释 54 行）的**全部注释**。
> 本节只做搬运与结构化；所有 MUST / NEVER 原文保留、不改写、不合并近似条款。
> **行号是 2026-09-16 抽取时刻的快照**：抽取期间该模块正被并发改动（ADR-D112 的
> `port/ContractGatewayPort` / `port/GatewayReply` / `port/ContractGatewayAdapter` 三个文件
> 是抽取中途才出现的，`ContractDomainServiceImpl` 同期被改过），因此**引用某条前 MUST 先 grep 现查行号**，
> NEVER 直接按本节行号跳转。
> 墓碑注释不进正文，单列在文末「墓碑注释清单（建议转为断言测试）」。

### 一、签约（IF8A-16 / 21 / 22 / 36、支付宝出行）

#### 契约与判据

1. **IF8A-22 的归属校验** —— `pay-sign-server` · `ContractDomainServiceImpl.requestContractResult` ·
   `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/impl/ContractDomainServiceImpl.java:438`：
   「REQUEST_SIGN_SEQ 是 APP_PAY_SIGN_INFO 的主键，只按它查等于任何网络可达方带一个流水号就能读到别人的
   payAccountId / payAgreementNo。命中的记录 MUST 属于报文里的 thirdUserId，不匹配一律按「签约记录不存在」回绝，
   NEVER 回具体原因（会变成存在性探测）」。
2. **孤儿协议不落库** —— `pay-sign-server` · `ContractResultOutcome.OrphanSigned` ·
   `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/support/ContractResultOutcome.java:42`：
   「调用方 MUST 只告警、NEVER 落库」；同一判据在 `ContractDomainServiceImpl.requestContractResult` ·
   `.../service/impl/ContractDomainServiceImpl.java:477`：「NEVER 在这里 insert APP_PAY_SIGN_INFO：本接口是
   IF8A-22 查询接口，报文里拿不到 CARD_ID / CARD_TYPE……签约记录只由 receiveSignResult 与支付宝出行
   requestSignInfo 创建」。已发生：生产库 00522908 / 00522914 共 3 条 SIGNED 孤儿签约，解约必然拿
   NO_ACCOUNT_CARD 卡死在 SCANNING，只能改库清理（`:482`）。
3. **SIGN_STATUS 只能走 4 条 CAS** —— `pay-sign-server` · `PaySignInfoMapper.markSigned` /
   `markSignFailed` / `markUnsigned` / `reactivateForResign` ·
   `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/mapper/PaySignInfoMapper.java:39`：
   「改 SIGN_STATUS **MUST** 走这 4 条，**NEVER** 再用 `updateBySeq`，后者 WHERE 只有 REQUEST_SIGN_SEQ，
   会让迟到的签约回调覆盖已解约状态」；`:42`「返回 0 行**不等于失败**……调用方 **MUST** 用
   `selectSignStatusBySeq` 回查后再决定」。白名单原文在
   `pay-sign-server/src/main/resources/mapper/PaySignInfoMapper.xml:103`：
   「NEVER 允许 UNSIGNED -> SIGNED：迟到的签约回调会把已解约通道改回已签约」，同处 `:105`
   「改状态 MUST 走这 4 条」、`:106`「返回 0 行不等于失败，MUST 由调用方回查当前状态判断「幂等」还是「真冲突」」。
4. **复位重签只清签约结果字段** —— `pay-sign-server` · `PaySignInfoMapper.reactivateForResign` ·
   `pay-sign-server/src/main/resources/mapper/PaySignInfoMapper.xml:135`：「NEVER 清 THIRD_USER_ID /
   PAYMENT_VENDOR / CARD_ID，它们是这一行的身份与解约时的卡信息比对依据」。
5. **CAS 结果三分，冲突不得静默** —— `pay-sign-server` · `SignStatusTransition.Outcome` ·
   `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/domain/SignStatusTransition.java:28`：
   「调用方 MUST 显式处理 CONFLICT，NEVER 静默忽略」；`.classify` · 同文件 `:64`：
   「解析不出来（NULL / 脏值 / 大小写不符）一律按 CONFLICT，NEVER 兜底成目标态：猜错方向会把「状态未知」
   当成「已经成功」，把不一致藏起来」；`:53`「「CAS 命中就不回查」是本规则的一部分，NEVER 改成先无条件回查再判断」。
6. **同一个 CONFLICT 在两个入口的处置刻意不同** —— `pay-sign-server` ·
   `ContractDomainServiceImpl.removeSignAgreement` · `.../service/impl/ContractDomainServiceImpl.java:662`：
   「本入口是状态变更型接口：冲突 MUST 拒绝并返 409，NEVER 降级成只告警放行」；
   `ContractDomainServiceImpl.applyGatewayStatus` · 同文件 `:756`：「本方法在 IF8A-22 查询接口内：
   冲突 MUST 只告警不落库、也不对上游报错，NEVER 照抄解约入口的 409 —— 那会让一次只读查询因状态不一致而失败」。
   两处共用同一条判定：`:657` / `:749`「CAS 返 0 行的解读统一走 SignStatusTransition（ADR-D40），
   NEVER 在这里重写判定」。同方法头 `:719`：「**NEVER** 用 `updateBySeq` 改 SIGN_STATUS」、
   `:722`「白名单不允许的迁移**只告警不落库**，留给对账与异常工单，**NEVER** 强改」。
7. **报文必填校验的文案与判断顺序即对外契约** —— `pay-sign-server` · `PaySignValidators`（类注释）·
   `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/support/PaySignValidators.java:18`：
   「**NEVER 改文案、NEVER 调整判断顺序** —— 顺序决定「同时缺两个字段时报哪一个」，而 APP 与联调方
   可能已按这些文案做了断言。要改 MUST 当成对外契约变更处理」。
8. **签约结果查询成功应答只有一处装配** —— `pay-sign-server` · `PaySignResponses.fillContractResult` ·
   `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/support/PaySignResponses.java:191`：
   「两处曾完全相同，**NEVER 再让它们分叉** —— 分叉的表现是「同一个用户，走缓存分支和走网关分支拿到的
   字段不一样」，而两条分支都返 `0000`，从应答码上完全看不出来」；`:194`「`defaultStatus` **由调用方传入、
   NEVER 在本类写死**」。另 `:52`「**NEVER 改 A 类的 `code` 取值**（失败 `-1` / 成功 `0`）：那是支付中心侧
   与旧客户端在读的字段」。
9. **出向签约报文的键序参与加签** —— `pay-sign-server` · `PaySignGatewayMessages`（类注释）·
   `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/support/PaySignGatewayMessages.java:26`：
   「键序影响待签串，**NEVER 换成 `HashMap`**」；`:17`「改任何一个键名或取值来源前 **MUST 先核对
   `docs/external/支付中心网关接口文档.md`**」；`putIfHasText` · 同文件 `:148`：「空值字段一律**不进报文**……
   两者对加签串与供方解析都不等价，**NEVER 改成无条件 put**」。
10. **签约与解约共用同一个查询报文，但两条 bizData NEVER 合并** —— `pay-sign-server` ·
    `PaySignGatewayMessages.buildDismissalBizData` · `.../support/PaySignGatewayMessages.java:89`：
    「与 `buildQueryResultBizData` 键集恰好相同，**但两者 NEVER 合并**：它们打的是两个不同的网关端点
    （`termination-url` / `contract-result-url`），供方任一侧加字段时只应改动其中一个」。
11. **支付宝出行同步确认分支不得写通知列** —— `pay-sign-server` ·
    `ContractDomainServiceImpl.alipayTripRequestSignInfo` · `.../service/impl/ContractDomainServiceImpl.java:281`：
    「NEVER 写 NOTIFY_STATUS / NOTIFY_RETRY_COUNT：本接口是「渠道侧已签好、我方同步确认」，整条链路没有
    APP 通知环节……写了就是一对永远没人回写的僵尸字段」。
12. **审计流水的 signStatus 只能是签约状态机的取值** —— `pay-sign-server` · `PaySignAuditLogger.write` ·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/audit/PaySignAuditLogger.java:60`：
    「取值来自 `SignStatus`，<b>NEVER 传解约流程的状态</b> —— 两台状态机取值撞车纯属巧合」；同类 `:97`
    「**NEVER 改成落细分名**（会让既有查询与统计全部错口径）」、`:114`「要新增类型 MUST 确认那个字段
    确实是「支付账号展示值」，NEVER 拿别的字段凑」。

#### 决策理由

13. **三个签约入口摘掉 `@Transactional`（批次 5A）** —— `pay-sign-server` ·
    `ContractDomainServiceImpl.requestSignInfo` / `requestContractAdvisory` / `requestContractResult` ·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/impl/ContractDomainServiceImpl.java:154`
    / `:314` / `:386`：「**本方法没有 `@Transactional`，2026-09-15 摘掉，NEVER 加回**」。三条理由逐字保留：
    ①「它包住的只有一次写」（一条 `APP_PAY_SIGN_REQUEST` INSERT）；②「它永远不会因业务失败回滚」
    （末尾 `catch (Exception e)` 吞掉一切，Spring 永远走 commit）；③「它反而把出网包了进来」——
    等待超过 Druid `remove-abandoned-timeout` 后连接被强杀、`commit` 抛 `connection closed`，
    「整个事务连那条「留证据」的审计 INSERT 一起被丢弃」。收尾判据：`:172` / `:324` / `:403`
    「NEVER 用「保持原子」换「可能整段丢失」」。
14. **ADR-D112：签约/解约方向的出向调用收进端口** —— `pay-sign-server` ·
    `ContractDomainServiceImpl`（字段注释）· `.../service/impl/ContractDomainServiceImpl.java:117`：
    「支付中心**签约/解约方向**出向调用的唯一出口（2026-09-16，ADR-D112）」、`:124`
    「**NEVER 把 `PaySignGateway` / `PaySignProperties` 加回本类**」；`requestGatewaySignInfo` 侧 `:857`
    「本方法只剩「把应答翻译成 SDK 参数」这一件事 —— 那才是签约用例自己的知识，NEVER 把它也搬进端口
    （端口不认识业务语义）」。端口侧理由在 `pay-sign-server` · `ContractGatewayPort`（类注释）·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/port/ContractGatewayPort.java:14`：
    「**本接口按用例一个方法，NEVER 退化成 `call(url, bizData)`**……**URL 只允许出现在
    `ContractGatewayAdapter` 里一处**（AGENTS.md §8「NEVER 退回拼接」的同源约束：路径带 `/v1`，
    散开后漏一处不报 404、极难定位）」、`:19`「**方法一律返回 `GatewayReply`**……实现方 **NEVER 抛异常**」；
    适配器侧 `ContractGatewayAdapter` · `.../port/ContractGatewayAdapter.java:23`
    「NEVER 让领域服务再注 `PaySignProperties` 只为取 URL」、`:27`「**NEVER 往里加业务判断**」、
    `:75`「**NEVER 在本类重写一份判定**」、`:88`「三级回落是对外契约的一部分，**顺序 NEVER 调整**」。
15. **`GatewayReply` 刻意只有两个变体** —— `pay-sign-server` · `GatewayReply`（类注释）·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/port/GatewayReply.java:15`：
    「**刻意只有两个变体，没有 `Unreachable`**：`PayGatewayClient.request` 现在把网络异常兜成一个
    `code!=0` 的应答（不抛），因此「不可达」在本层**不可观测**。硬造第三个变体会让调用点以为自己能区分，
    而实际区分不了 —— 那比没有更糟。要真正区分 MUST 先改 `PayGatewayClient`」；`:20`
    「**`raw()` 刻意保留**」（两个内部方法的签名仍是 `PaySignGatewayResponse`）；`:46`
    「**逐字复刻 `PayGatewayClient.errorMessage` 的语义**……NEVER 收成一句统一文案 ——
    联调方可能已按文案断言」。
16. **ADR-D107：签约结果查询的「决策」与「落库」分层** —— `pay-sign-server` ·
    `ContractResultOutcome`（类注释）· `.../support/ContractResultOutcome.java:14`：
    「①② 收进纯函数并返回一个 sealed 结果，③ 留在业务类用穷尽 `switch` 消费 —— 判断与副作用分层，
    而不是把判断切成两半」；`:20`「与 `RpcOutcome`（ADR-D45）/ `AccountQuery`（ADR-D94）同一手法：
    **新增一个分支时，业务类那个 `switch` 会直接编译失败**」。
17. **ADR-D107：审计流水按接口收口成六个薄壳** —— `pay-sign-server` ·
    `ContractDomainServiceImpl`（审计薄壳段）· `.../service/impl/ContractDomainServiceImpl.java:876`：
    原先 37 处裸调用里有三组「7 个实参**逐字相同**」，「漏改一处不会编译失败、不会告警，只会让审计流水
    少一个字段，而 APP_PAY_SIGN_REQUEST 是这条链路唯一的证据」；`:884`「NEVER 把这六个合并成一个
    「通用 writeAudit(action, ...)」：它们的第 4 个实参取法各不相同」。
18. **ADR-D96：协作者一律构造注入** —— `pay-sign-server` · `ContractDomainServiceImpl`（构造器注释）·
    `.../service/impl/ContractDomainServiceImpl.java:130`：「字段 `final` ⇒ 对象一建成即完备，
    且夹具漏注 / 多注一个协作者会**编译失败**，而不是运行时才报 `Could not find field`。
    **NEVER 退回 `@Autowired` 字段注入。**」同一段落在 `PaymentDomainServiceImpl:99`、
    `CallbackDomainServiceImpl:127`、`RefundDomainServiceImpl:86`、`AppNotifyServiceImpl:110`、
    `TerminationProcessor:128`、`TerminationExecutor:88`、`TerminationCompensationService:97`、
    `TerminationInternalServiceImpl:71`、`PaymentInternalController:34`、`PaySignInternalController:26`、
    `TerminationInternalController:31`（以及四个 APP / 渠道 Controller）各有一份。
19. **support 包是对「NEVER 主动创建新的工具类」的条件式破例** —— 判据统一写在
    `pay-sign-server` · `PaySignValues`（类注释）·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/support/PaySignValues.java:14`
    与 `:25`「**NEVER 往本类里塞需要注入 Bean 的方法**：一旦要注入，本类就得变成 Spring Bean，
    三个领域服务对它的静态导入全部作废、拆分收益归零」；同一破例的其余四处：`PaySignValidators:26`
    「**NEVER 往本类加任何 mapper / client 依赖**」、`PaySignResponses:46`「**NEVER 往本类加业务判断**」、
    `PaySignGatewayMessages:35`、`PaySignGateway:14`（后者要持有 `PayGatewayClient`、因此是 Spring Bean）。
20. **`PaySignResponses` 的 19 个重载消不掉** —— `pay-sign-server` · `PaySignResponses`（类注释）·
    `.../support/PaySignResponses.java:44`：「这 9 个 DTO 分属四个互不相干的家族，且多数在 `model` 模块 ——
    它们是能被 `parseBizData` 解析的**对外契约**，按 `docs/domain` 的判据 **NEVER 为了内部整洁给它们加
    共同父类或改继承**。因此重载数量是契约形状决定的，不是代码质量问题」；`:40`
    「真要抽 helper MUST 单独一版、并先补上覆盖三种行为的断言」；`:169`「那是从 `AppNotifyServiceImpl`
    搬来时的原样签名（2026-09-16，ADR-D98），**NEVER 为了「风格统一」改成 void**，那会连带改 11 个调用点」。
21. **钱包渠道的签约短路已删除（前提被实测推翻）** —— `pay-sign-server` ·
    `ContractDomainServiceImpl.requestSignInfo` · `.../service/impl/ContractDomainServiceImpl.java:181`：
    原实现的前提是「钱包扣款只靠 payUserId(thirdPayId)、不需要签约流水号」，
    「该前提已被实测推翻：支付中心 §1.1 requestPay 的 withholding 场景**强制要求 requestSignSeq**」，
    实测证据是「只送 payUserId 时网关返 code=9999「代扣签约请求流水号不能为空」」、
    「PAY_TXN_DETAIL 里 0B 渠道 16 笔全部 FAIL/RETRY、**零条 SUCCESS**，而同期 03/04 渠道分别有
    36/7 条 SUCCESS」；`:189`「NEVER 退回短路：短路等于让钱包渠道在支付中心侧永远没有可用的代扣协议，
    扣款必然失败」。
22. **ADR-D111：支付宝出行一次成功写两行流水，是现状不是缺陷** —— `pay-sign-server` ·
    `ContractDomainServiceImpl.alipayTripRequestSignInfo` · `.../service/impl/ContractDomainServiceImpl.java:291`：
    「与方法收尾的 auditAlipayTripSignInfo 一起构成「一次成功写两行流水」的现状（ADR-D111）……
    要不要合并成一行属于**改审计口径**，MUST 由人裁决；现状已被 AlipayTripSignCharacterizationTest 钉住」；
    同处 `:288`「NEVER 再把别的方法的论证复制到这里：注释写错会被后人当判据」。
23. **ADR-D101：解约申请的实体装配外提为纯函数** —— `pay-sign-server` · `TerminationRules`（类注释）·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/support/TerminationRules.java:13`：
    「**本类 MUST 保持纯函数、零状态、零依赖，NEVER 注入 mapper / client / properties。**」；`:17`
    「**NEVER 试图把该方法的状态机判断（FAILED 复活的 CAS、按流水号的重复申请拦截）也搬进来**，
    那些都要读写 mapper」；`buildTerminationRequest` · 同文件 `:35`「**`CREATE_TIME` / `UPDATE_TIME`
    MUST 显式赋值**：为 null 时不会走列默认值，直接触发 `ORA-01400`」、`:22`
    「取自枚举，**NEVER 退回字面量 `"PENDING"`**」。

#### 陷阱

24. **审计流水在事务内 INSERT 会随主事务一起丢** —— `pay-sign-server` · `PaySignAuditLogger`（类注释）·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/audit/PaySignAuditLogger.java:28`：
    「**异常一律吞掉、只记 ERROR 日志（NEVER 改成往外抛）**……①调用方多数带 `@Transactional`，
    这里的 INSERT 会随主事务回滚，所以「日志一定留得下」在事务内并不成立（已发生：2026-08-26 事务被
    Druid 强杀，`PAY_CALLBACK_LOG` 零条落库）；②`requestSignSeq` 为空时**直接不记**」。
25. **审计流水写通知列会凭空造出第二个补偿队列** —— 同类 `:34`：
    「本类**NEVER 写 `NOTIFY_STATUS` / `NOTIFY_RETRY_COUNT`**……审计流水多写一次通知列，就等于凭空造出
    第二个补偿队列且永远收不到结果回写 —— 生产上已因此留下 ID=745 / 749 两条幽灵行（2026-08-26 修复）」。
26. **改 `convertPayStatus` 的返回值必须全局 grep** —— `pay-sign-server` ·
    `PaySignValues.convertPayStatus` · `.../support/PaySignValues.java:92`：
    「全模块是按 String 字面量比较状态的（AGENTS.md §2.2.1），**改这里的任何一个返回值 MUST 全局 grep
    所有比较点**。末行故意「认不出就原样返回大写」而不是回落 PROCESSING —— 那样会把未知终态伪装成中间态、
    引来无休止重推」。
27. **未知渠道编码只 warn、照样原样返回** —— `pay-sign-server` · `PaySignValues.normalizeVendor` ·
    `.../support/PaySignValues.java:40`：「NEVER 改成抛异常或返 null —— 支付平台侧可能先于本枚举支持新渠道，
    拒绝会把能跑通的签约打死」。
28. **`resolveTxnDate(String)` 这个重载只准 `PAY_TXN_DETAIL` 用** —— 同类 `:120`：
    「**NEVER 把这个重载套到那两处**」（`PAY_REFUND_DETAIL` 与 `PAY_CALLBACK_LOG` 是各自独立的事件）。

### 二、解约（IF8A-06 / IF8A-75、T+4 扫表、通道清理 outbox）

#### 契约与判据

29. **解约状态机六条 CAS 的前置态即白名单** —— `pay-sign-server` · `AppTerminationRequestMapper` ·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/mapper/AppTerminationRequestMapper.java`：
    `markScanning`（`:65`「`/internal/termination/execute` 不带事务（事务内 NEVER 调 RPC），因此「判定 PENDING」
    与「置 SCANNING」不能再靠事务串起来，**MUST** 用本方法一次性抢占……调用方 **MUST** 检查返回值，
    **NEVER** 无条件继续去调支付中心——否则同一笔会重复发解约」）；
    `revertScanningToPending`（`:80`「**NEVER** 在「结果未知」（超时 / 连接异常 / 解析失败）时调用本方法……
    结果未知 **MUST** 留在 SCANNING」）；`rejectPending`（`:94`「**NEVER** 拆成 `updateFailReason` +
    `updateCompleteTime` + `updateNotifyStatus` 三条无 CAS 的语句……并给 APP 发一条与支付平台实际状态相反的
    解约失败通知。调用方 **MUST** 检查返回值」）；`markSuccess`（`:108`「**NEVER** 退回 `updateStatus` +
    `updateCompleteTime` + `updateNotifyStatus` 三条无 CAS 语句」、`:113`「调用方 **MUST** 检查返回值，
    0 行走 `TerminationStatusTransition` 判幂等还是冲突」）；`rejectScanning`（`:132`）；
    `expireScanning`（`:242`「**NEVER** 拆成多条 update——会把已收口的 SUCCESS 覆盖成 FAILED」）。
30. **`reactivateFailed` 是「重新申请解约」的唯一通道** —— 同 mapper `:224`：
    「因此「重新申请解约」只能走本方法，**NEVER 再 insert**——必抛 `DuplicateKeyException`，
    被 `requestTermination` 外层 catch 成 `SYSTEM_ERROR`，用户从此再也解不了约」；`:228`
    「调用方 **MUST** 检查返回值：返回 0 表示并发下状态已被改走，本次不能放行」。
    调用点判据在 `ContractDomainServiceImpl.requestTermination` ·
    `.../service/impl/ContractDomainServiceImpl.java:601`：「影响 0 行说明并发下状态已被改走，
    MUST 拒绝，NEVER 当成功继续」。
31. **解约收口以主动查 §2.4 为准，回调只是快速路径** —— `pay-sign-server` ·
    `ContractDomainServiceImpl.queryPayPlatformContractStatus` ·
    `.../service/impl/ContractDomainServiceImpl.java:704`：「支付中心没有独立的「解约结果查询」接口：
    网关文档 §2.4 查询签约结果的 status 同时承载签约态与解约态，`status=UNSIGNED` 即该协议已解约。
    解约回调地址只能由支付中心在商户侧配置（§2.3 请求解约的 bizData 没有 notifyUrl 字段），
    我方无法保证一定收到回调，因此解约收口 MUST 以主动查询为准，回调只作为快速路径」。
    同一判据在 `TerminationCompensationService.processTermination` ·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/impl/TerminationCompensationService.java:125`
    「SCANNING MUST 扫……只等回调会让记录永久卡在 SCANNING、用户实际已解约但 ITP 侧仍显示已签约」。
32. **`cardId` / `cardType` / `paymentVendor` 刻意非必填** —— `pay-sign-server` ·
    `PaySignValidators.validateRequestTermination` · `.../support/PaySignValidators.java:74`：
    「**NEVER 在这里加上它们的必填校验**，会把能正常受理的解约申请挡掉」（三列在
    `APP_TERMINATION_REQUEST` 是 NOT NULL，由 `requestTermination` 用签约记录回填）。
33. **IF8A-75 补建解约申请前必须先从账户域取票卡信息** —— `pay-sign-server` ·
    `TerminationExecutor.unbindAgreement` ·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/impl/TerminationExecutor.java:229`：
    「（2026-09-08 实测）。票卡信息的真实来源是 account 侧 APP_USER_PAY_CHANNEL，其 REQ_CONTRACT_NO
    即 requestSignSeq，MUST 先取回来塞进入参」；补建方法 `createTerminationRequest` · 同文件 `:287`
    「缺字段时 MUST 用签约记录回填，否则 INSERT 抛 ORA-01400」、`:290`「签约记录不存在即拒绝……
    **NEVER** 凭一个流水号凭空造申请，否则等于允许任意可达方在库里写入无主记录并驱动支付平台解约」。
    窄视图理由在 `AccountPayChannelView` ·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/port/AccountPayChannelView.java:10`
    「这一步一旦丢，补建解约申请必然 `ORA-01400`（2026-09-08 实测）」。
34. **未结清欠费查询失败时不得放行** —— `pay-sign-server` · `TerminationProcessor.processPending` ·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/impl/TerminationProcessor.java:168`：
    「查询未成功执行时 NEVER 继续解约：这里放行等于在用户可能仍欠费的情况下解约」。
    IF8A-75 则**不校验欠费**（用户 2026-09-08 裁决）—— `TerminationInternalService.unbindAgreement` ·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/TerminationInternalService.java:73`
    「规格 §3.59 的场景是「用户长时间未登录需强制解除绑定关系」，欠费由后续催收流程处理」，
    `:76`「`0000` 表示<b>已向支付渠道发起</b>，不代表已解绑完成」。
35. **失败通知只能在状态 CAS 成功之后发** —— `pay-sign-server` ·
    `TerminationProcessor.notifyTerminationFailed` · `.../service/impl/TerminationProcessor.java:334`：
    「MUST 在状态 CAS 成功之后才调用本方法，NEVER 在 CAS 之前或影响 0 行时调用」；
    `rejectByUnsettledOrder` · 同文件 `:275`「影响 0 行即 MUST 什么都不做，NEVER 覆盖别人的状态、
    更 NEVER 在支付平台已受理解约的情况下给 APP 发解约失败通知」；`expireIfTimedOut` · `:303`
    「NEVER 凭空打 FAILED——那会给 APP 发一条毫无依据的解约失败通知」、`:315`
    「影响 0 行说明这条已被解约回调收口成 SUCCESS，MUST 什么都不做，NEVER 覆盖终态」。
36. **`SCANNING` 超时阈值取 24 小时，且宽松是有意的** —— `pay-sign-server` ·
    `TerminationProcessor.SCANNING_TIMEOUT_MINUTES` · `.../service/impl/TerminationProcessor.java:78`：
    「**MUST 取得足够宽松**——打早了会在支付平台其实已经解约、只是查询链路暂时不通的情况下告诉 APP
    「解约失败」，而本地签约记录与 account 支付通道都还没清，用户以为还签着、免密扣款却已失效。
    反之留在 SCANNING 是无上限静默重试，没有任何人会知道」；`confirmTermination` · `:189`
    「每个「本轮无法确认」的出口都 MUST 走 expireIfTimedOut 而不是直接 return SKIPPED」。
37. **`CHANNEL_SYNC_*` 与 `NOTIFY_*` 管两件不同的事** —— `pay-sign-server` ·
    `AppTerminationRequest.channelSyncStatus` ·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/entity/AppTerminationRequest.java:25`：
    「NOTIFY_* 管「给 APP 发通知」，本组管「调 account-server 删通道」。NEVER 混用」；`:27`
    「**NULL 表示本行早于 ADR-D8 第一处的改造**……补偿扫表 NEVER 捞 NULL 行」。同一条在
    `pay-sign-server/src/main/resources/mapper/AppTerminationRequestMapper.xml:381`
    「与 NOTIFY_* 一组形状对称但语义无关，NEVER 混用」。
38. **通道清理三分支处置只有一处实现** —— `pay-sign-server` · `ChannelSyncDeliverer.deliver` ·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/impl/ChannelSyncDeliverer.java:50`：
    「三分支处置**互不相同、NEVER 合并**（口径同 ADR-D45 与 `outbox.md` §四）：`Ok` → 落 `SUCCESS`；
    `BizRejected` → 账户域明确拒绝，**重推一万次也不会成功**：先 +1 把状态推到 `FAILED`……
    再一次性转 `MANUAL`。**NEVER 让它留在补偿队列里。**；`Unreachable` → 只 +1……留给下一轮补偿」；
    `:59`「`false` 含「业务拒绝」与「不可达」两种，调用方 NEVER 据此判断可否重试」；`:22` / `:90`
    「**NEVER 让本类抛异常**」「NEVER 上抛：调用方都在本地已提交之后调本方法」。
39. **两条补偿端点不得互相替代、不得合并** —— `pay-sign-server` ·
    `TerminationInternalService.compensateChannelSync` · `.../service/TerminationInternalService.java:47`：
    「与 `compensateTerminationNotify()` 管的是**两件不同的事**……**NEVER 互相替代、NEVER 合并成一个端点**」；
    Controller 侧 `TerminationInternalController.compensateChannelSync` ·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/controller/TerminationInternalController.java:75`
    「NEVER 与 /compensateNotify 合并成一个端点：两者的重试上限、失败语义与人工介入口径都不同」；
    重试上限分开配的理由在 `TerminationCompensationService.MAX_CHANNEL_SYNC_RETRY` ·
    `.../service/impl/TerminationCompensationService.java:81`「通知失败只是 APP 少收一条消息，
    通道没删掉是跨域数据不一致，两者的容忍度不同，NEVER 复用同一个键」。
40. **`FAIL_REASON` 有两类读者，对外读者必须先剥标记** —— `pay-sign-server` ·
    `TerminationFailReason` ·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/domain/TerminationFailReason.java:11`：
    「因此写格式与读格式 **MUST 放在同一个类里**：拆开放两处就是这次缺陷的成因。新增任何 `FAIL_REASON`
    的读者前 **MUST 先判断它属于哪一类读者**：面向运维的直接读原值，面向外部的一律先过
    `stripManualMark(String)`」；`:21`「**NEVER 改这个字面量**」（同时是 mapper 里 `INSTR` 幂等判据的入参、
    运维检索关键字与测试钉住的常量）；`:29`「**MUST 保证内部说明本身不含这个串**」；`:62`
    「**面向外部的读者 MUST 调本方法**，NEVER 直接把 `FAIL_REASON` 原值发出去」；`:66`
    「宁可让下游回落到默认文案，也 NEVER 把内部说明发出去」。
41. **解约申请状态与签约日志状态字面量撞车纯属巧合** —— `pay-sign-server` ·
    `CallbackDomainServiceImpl.STATUS_FAILED` / `SIGN_LOG_STATUS_FAILED` ·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/impl/CallbackDomainServiceImpl.java:76`
    与 `:86`：「与 `STATUS_FAILED` 字面量相同、语义无关，**NEVER 合并**」；
    `ContractDomainServiceImpl.STATUS_FAILED` · `.../service/impl/ContractDomainServiceImpl.java:76`
    「**NEVER 用这个常量表达签约状态或流水日志的 SIGN_STATUS**……等于两台状态机共用一个常量 ——
    一旦某天要改其中一台的取值，另一台会被静默带走」。
42. **支付平台报文里的成功值不是我方状态机的取值** —— `pay-sign-server` ·
    `TerminationProcessor.CALLBACK_STATUS_SUCCESS` · `.../service/impl/TerminationProcessor.java:67`：
    「**NEVER 换成 `TerminationStatus.SUCCESS.name()`**：这是<b>支付平台报文</b>里的字段取值，
    与我方 `TERMINATION_STATUS` 列只是字面量恰好相同。对端改了报文取值时只应改这一行，
    不该牵动我方状态机」。

#### 决策理由

43. **ADR-D48：解约回调改成「本地短事务 + `CHANNEL_SYNC_*` outbox + 提交后出网」** —— `pay-sign-server` ·
    `CallbackDomainServiceImpl.receiveTerminationResult` ·
    `.../service/impl/CallbackDomainServiceImpl.java:256`：「本方法 NEVER 加 @Transactional
    （2026-09-12 / ADR-D48 摘掉，此前一直带着）……成功分支要调 account-server 删支付通道。包在事务里，
    APP_TERMINATION_REQUEST 那一行的排他锁就持满整个 RPC 往返……这正是 2026-08-26 生产事故
    （订单 GT20260826210647653586419 循环重推 8 分钟）的形态」；`:265`
    「本地事务先把 TERMINATION_STATUS 收成 SUCCESS 并同时置 CHANNEL_SYNC_STATUS='PENDING'
    （两者 MUST 同事务），提交后再出网删通道、按 RpcOutcome 落 CHANNEL_SYNC_*，失败留给
    /internal/termination/compensateChannelSync 重推。NEVER 在没有这套落库状态的前提下摘注解——
    那样本地会先提交成 SUCCESS，重推撞上幂等短路，账户域的通道就永远删不掉，比「粗暴回滚 + 靠上游重推」更糟」；
    同事务要求复述于 `:358`「markSuccess 与 initChannelSyncPending MUST 在同一个事务里：
    两者之间崩掉会留下 TERMINATION_STATUS='SUCCESS' 而 CHANNEL_SYNC_STATUS=NULL，
    而补偿扫表**刻意不捞 NULL**」。
44. **两个回调入口的事务边界刻意不同** —— `pay-sign-server` · `CallbackDomainServiceImpl`（类注释）·
    `.../service/impl/CallbackDomainServiceImpl.java:52`：「**两个入口的事务边界刻意不同，NEVER 对齐它们**：
    `receiveSignResult` 带 `@Transactional` 并靠 `AFTER_COMMIT` 事件投递通知；`receiveTerminationResult`
    **刻意不带**（ADR-D48），自己用 `transactionTemplate` 开短事务收口，事务外才出网」；`:118`
    「**NEVER 给 `receiveTerminationResult` 加回 `@Transactional`**」。
45. **ADR-D90（批次 5C）：`notifyTerminationFailed` 摘事务** —— `pay-sign-server` ·
    `TerminationInternalServiceImpl.notifyTerminationFailed` ·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/impl/TerminationInternalServiceImpl.java:147`：
    「**刻意不带 `@Transactional`，NEVER 加回**（批次 5C，ADR-D90）」。三条依据：①「整个方法只有一次写库……
    单语句本身就是原子的，外面套不套事务落库结果完全一样」；②「事务反而制造了两个真实窗口」——
    通知任务提交发生在 commit 之前，且 catch 分支 `throw new TerminationException(...)` 会真的回滚，
    「于是「APP 已收到解约失败通知、库里状态却回退成 PENDING」不是理论风险而是可达路径」；
    ③「ADR-D8 要求的「落同步状态 + 补偿」这里本来就齐了……**NEVER 因为「摘了事务怕丢通知」而把注解加回来** ——
    丢通知有人补，事务回滚把状态一起撤掉才是没人能补的那种」；`:174`
    「**NEVER 照抄那边的做法给本方法挂监听器**」（与 `receiveSignResult` 的差别：那条事务内有多张表多次写）。
46. **ADR-D95：解约域按「驱动方式」拆成两个类** —— `pay-sign-server` ·
    `TerminationCompensationService`（类注释）· `.../service/impl/TerminationCompensationService.java:31`：
    「那个类原有 7 个入口、10 个协作者，但它们分属**两个互不相干的驱动**……两组只共用
    `terminationRequestMapper` 一个协作者 —— 这正是「一个类里有两簇不相交的依赖」的低内聚信号」；
    `:41`「**本类 NEVER 加 `@Transactional`**：三个方法都会出网」。`TerminationExecutor`（类注释）·
    `.../service/impl/TerminationExecutor.java:38`「门面剩下的四个入口里，这两个共用一整套东西……
    而另外两个一条都不碰」、`:47`「**本类整体 NEVER 加 `@Transactional`** —— 逐条理由写在两个方法上方的注释里，
    那是 2026-08-26 生产事故换来的，NEVER 因为「看起来该有事务」加回去」；门面侧
    `TerminationInternalServiceImpl:47`「**NEVER 把那三个方法体搬回来**」、`:60`「**NEVER 把它们加回本类**」。
47. **无事务后靠 CAS 替代回滚** —— `pay-sign-server` · `TerminationProcessor`（类注释）·
    `.../service/impl/TerminationProcessor.java:39`：「本类所有方法 NEVER 加 @Transactional……
    去事务后每条 SQL 自动提交，没有回滚可用，因此状态流转全部走 mapper 里的 CAS 语句」，四条语句职责
    逐条列在 `:47`~`:50`；`:52`「结果未知（超时 / 连接异常）时刻意保持 SCANNING……NEVER 退回 PENDING ——
    支付平台可能已受理，退回会重复发解约」。同型段落在 `TerminationExecutor.executeTermination` ·
    `.../service/impl/TerminationExecutor.java:108` 与 `:118`。
48. **ADR-D8：人工件靠本表列过滤，有意不建工单表** —— `pay-sign-server` ·
    `AppTerminationRequestMapper.markChannelSyncManual` ·
    `.../mapper/AppTerminationRequestMapper.java:286`：「ADR-D8 有意**不建工单表**（pay-sign-server
    没有工单表，新建会引入一张只服务单条链路的表；调 account-server 开单则会新增一条跨域边，
    正是本次要消除的那类问题）。因此运维核对 **MUST** 用 `CHANNEL_SYNC_STATUS = 'MANUAL'` 过滤本表」；
    `markConflictForManualReview` · 同文件 `:171`「与 `markChannelSyncManual` 是同一套口径」、`:163`
    「本方法 **NEVER** 改 `TERMINATION_STATUS`、**NEVER** 动 `NOTIFY_*`：复位通知等于替业务决定
    「要不要给 APP 反悔」，那是业务裁决，不是并发处置」。
49. **两条「留痕」语句刻意不合并成一条** —— `pay-sign-server` ·
    `AppTerminationRequestMapper.markFailureConflictForManualReview` · 同文件 `:190`：
    「与 `markConflictForManualReview` 形态相同、**CAS 前置状态相反**，刻意分成两条语句而不是把状态做成
    绑定参数：状态字面量留在 SQL 里才能被离线渲染断言钉住，而两种矛盾的人工处置口径也不同」；`:196`
    「即便如此，写入格式仍 **MUST** 走 `TerminationFailReason` —— 依赖「当前某个分支恰好不读这一列」是脆的」。
    同判据在 XML `.../resources/mapper/AppTerminationRequestMapper.xml:368`。
50. **`rejectScanning` 与 `expireScanning` 刻意不合并** —— 同 mapper `:128`：
    「与 `expireScanning` 的差别只在语义（对方答复失败 vs 我方等超时），SQL 形态相同，因此两者<b>刻意不合并</b>：
    `FAIL_REASON` 的来源与运维处置口径不同，合并后无法从表里区分「支付平台说失败」和「我方查不动」」。
51. **`TerminationStatusTransition` 与 `SignStatusTransition` 刻意不合并** —— `pay-sign-server` ·
    `TerminationStatusTransition`（类注释）·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/domain/TerminationStatusTransition.java:11`：
    「<b>两者刻意不合并成一个泛型工具</b> —— ADR-D40 已裁决「NEVER 把它扩成通用状态机引擎」……
    这里重复的只是 3 个常量 + 一个 record 的结构，没有重复任何判定逻辑」；`:16`
    「**NEVER 在本类里加 CAS 前的白名单校验**……用过期值提前拦一道只会造成<b>误拦</b>，
    且比 CAS 返 0 行更难排查」（`SignStatusTransition:14` / `:19` 是同一条，且否决了
    归一成 `transit(key, from, to)` 的做法）。

#### 陷阱

52. **`APP_PAY_SIGN_INFO.CARD_ID` / `CARD_TYPE` 全库为 NULL，兜底不能删** —— `pay-sign-server` ·
    `CallbackDomainServiceImpl.resolveCardInfoFromTerminationRequest` ·
    `.../service/impl/CallbackDomainServiceImpl.java:588`：「`APP_PAY_SIGN_INFO.CARD_ID` / `CARD_TYPE`
    全库为 NULL……真值只在 `APP_TERMINATION_REQUEST` 里。**NEVER** 删掉本兜底」；对应事故在同文件 `:339`
    「已发生事故：2026-09-09 requestSignSeq=0052294801523908」（带 null 调 account-server 参数校验直接失败，
    进而抛 `TerminationException` 回滚，「支付渠道已解约、本地全部回退，且重试永远在同一处失败」）。
53. **CAS 撞车时补发通知会给 APP 两条相反结果** —— `pay-sign-server` ·
    `CallbackDomainServiceImpl.receiveTerminationResult` · `.../service/impl/CallbackDomainServiceImpl.java:411`：
    「NEVER 在这里补发成功通知：CONFLICT 多半是 expireScanning 抢先打成 FAILED、失败通知已发出，
    再发一条成功通知就是给 APP 两条相反结果」；`:415`「NEVER 改成抛异常回滚：账户域通道的 RPC 已经出网、
    回滚不掉，而本地一回滚签约记录就留着，下一轮重推在幂等短路直接返回成功，通道永远清不掉」；
    `:421`「仍 NEVER 自动订正状态：FAILED -> SUCCESS 已被明确禁止」；失败分支同型判据在 `:472`（原文见
    `:476`「留痕只陈述事实、NEVER 自动订正状态」）。
54. **`IDEMPOTENT` 不是异常，打成 ERROR 会造出假告警** —— 同文件 `:446`：
    「NEVER 把这条打成 ERROR / 「MUST 人工核对」：解约收口本就是「主动查 queryResult 为准 + 回调作快速路径」
    双路驱动（AGENTS.md §8），两路撞同一个 CAS 是设计内的常态而非异常。2026-09-14 实测
    requestSignSeq=0052294901523920，两路相差 46ms，库内三态全 SUCCESS、通道行也已删除，
    却报出一条要求人工核对的 ERROR」；`:413`「（IDEMPOTENT 是另一路已正常收口，走下面那个 else，
    NEVER 与本分支合并。）」。
55. **调用方不能假定「抛异常就全回滚」** —— `pay-sign-server` · `TerminationProcessor.confirmTermination` ·
    `.../service/impl/TerminationProcessor.java:231`：「receiveTerminationResult 自 ADR-D48（2026-09-12）
    摘掉 @Transactional 起**不再自带事务**：它内部用 transactionTemplate 起短事务、出网段在事务外，
    因此**调用方 NEVER 能假定「抛异常就全回滚」** —— 它可能已经清了账户支付通道、也可能已把状态推进过一段」。
56. **唯一索引竞态必须沿 cause 链判定** —— `pay-sign-server` · `TerminationExecutor.isDuplicateKeyViolation` ·
    `.../service/impl/TerminationExecutor.java:343`：「**MUST 逐层遍历 cause，NEVER 直接
    `catch (DuplicateKeyException)`**（ADR-D53）：本模块打开了 tracing，`MapperAspectToTrace` 会切到所有
    `@Mapper` 方法上；它此前把异常包成 `new RuntimeException(e)`，单层类型判断在本项目里捕不到，
    冲突会直接冒到全局处理器……**非唯一键冲突 MUST 原样上抛。**」；`:341`
    「<b>NEVER 把它当失败</b>——否则运维重试与批处理撞车时会假失败」。
57. **补偿重推前必须先把记录移出 `PENDING`** —— `pay-sign-server` ·
    `AppTerminationRequestMapper.increaseChannelSyncRetryCount` · `.../mapper/AppTerminationRequestMapper.java:277`：
    「补偿重推**前** MUST 先调本方法把记录移出 `PENDING`：否则重推后的状态回写若再失败，
    这行会被下一轮重复扫到且次数不涨，永远到不了上限、也永远开不出人工介入」。
58. **解约通知补偿的终态白名单不能去掉** —— `pay-sign-server` ·
    `AppTerminationRequestMapper.selectCompensableNotify` ·
    `pay-sign-server/src/main/resources/mapper/AppTerminationRequestMapper.xml:115`：
    「**NEVER 去掉这个终态白名单**——解约申请插入时 NOTIFY_STATUS 初值就是 PENDING，一条卡在
    PENDING/SCANNING 的申请（欠费查询一直失败、或支付平台迟迟不解约）滞留超 staleMinutes 就会被当成
    「成功通知丢了」，给 APP 发一条 status=SUCCESS、dismissalTime 为空的假解约成功通知，
    而 APP 侧实测没有按 requestSignSeq 幂等，污染的是对端状态、我方无法回滚（2026-08-26 修复）」。

### 三、免密扣款（支付 API 1.1 / 5.1）

#### 契约与判据

59. **入参校验按渠道类别分流：钱包看 `payUserId`、传统签约看 `requestSignSeq`（钱包现在两个都要）** ——
    `pay-sign-server` · `PayTxnRules.validatePaySignInfo` ·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/support/PayTxnRules.java:52`：
    「**钱包（`0B`）两个字段都要校验**：自 2026-09-15 起钱包也走支付中心签约，只校验 `payUserId`
    会掩盖「该用户其实没签约」」；`:61`「入口级路径选择 MUST 穷尽（ADR-D109）：两个类别要校验的字段本就不同，
    新增类别时编译器会在这里报错。NEVER 退回 if (isWallet(...)) + 尾部兜底 return」。字段装配侧
    `PaymentDomainServiceImpl.applyAccountUserView` ·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/impl/PaymentDomainServiceImpl.java:573`：
    「原实现是「互斥两支：钱包只填 payUserId」，注释写着 NEVER 两个都填……那条理由在支付中心的真实契约下
    站不住：§1.1 requestPay 的 withholding 场景**强制要求 requestSignSeq**（2026-09-15 实测，
    只送 payUserId 时网关返 `code=9999「代扣签约请求流水号不能为空」`），钱包因此永远扣不出去」。
60. **上游并不透传签约信息，本地补齐后才判「未签约」** —— `pay-sign-server` ·
    `PayTxnRules.validatePaySignInfo` · `.../support/PayTxnRules.java:46`：
    「现状（2026-08-26 生产实测）：调用方 gate-txn-pay-server 并**不**透传 paymentVendor / requestSignSeq……
    因此这里到达时字段为空只说明「本地和 account-server 都查不到签约信息」，可以判定未签约」。
61. **账户域必须答 `0000` 才采信** —— `pay-sign-server` ·
    `PaymentDomainServiceImpl.resolvePaySignInfoFromAccount` · `.../service/impl/PaymentDomainServiceImpl.java:529`：
    「**账户域必须答 `retCode=0000` 才采信**（ADR-D94）：业务失败应答里残留的 `channel` / `reqContractNo`
    曾被直接写进支付入参并真的发起免密扣款。不采信时**不抛异常、只是不补**，由 `validatePaySignInfo`
    收成 `8011`」；`:543`「NEVER 把两支合并成 default —— 合并后新增分支不会编译失败，就退回了靠人记规则」。
62. **支付回调的定位键只能是 `merchantOrderNo`** —— `pay-sign-server` ·
    `PaymentDomainServiceImpl.receivePayResult` · `.../service/impl/PaymentDomainServiceImpl.java:327`：
    「定位键 MUST 用 merchantOrderNo：回调报文里 orderNo 是**支付中心**的订单号……而 PAY_TXN_DETAIL.ORDER_NO
    存的是我方商户订单号……NEVER 用 request.getOrderNo() 当 WHERE 键，也 NEVER 拿它做兜底——那是一个必然匹配
    0 行的键，只会把「键传错」和「订单不存在」混成同一种现象」；已发生事故见 `:332`
    （「2026-08-26 免密扣款支付宝已扣款成功，回调 UPDATE 命中 0 行，PAY_STATUS 长期停在 PROCESSING，
    APP 显示扣费失败；生产库 4 笔受害（PAY_TXN_DETAIL 5103/5105/5107/5109）」）。
63. **`DEBIT_REQUEST_RESULT` 必须与 `PAY_STATUS` 同步回写** —— 同方法 `:346`：
    「APP 侧（IF8A-05 列表、IF8A-34 详情）展示的扣费结果读的是这一列，不是 PAY_STATUS」；
    事故见 `:349`。SQL 侧口径在 `pay-sign-server/src/main/resources/mapper/PayTxnDetailMapper.xml:141`
    与 `:146`「MUST 保持与 PAY_STATUS 同步」、`:151`「本列的值域是 PAY_STATUS 那一套……
    与对外契约字段名 debitRequestResult 的 0 / 1 值域是两件事，NEVER 混用」。
64. **回调 UPDATE 影响 0 行有两种含义，必须分开** —— 同方法 `:365`~`:373`：
    「WHERE 带列级状态白名单（INIT / PROCESSING / RETRY / FAIL），SUCCESS 是终态不在白名单内，
    因此重复的 SUCCESS 回调必然命中 0 行——这正是幂等出口，MUST 与「真的没落地」区分开……
    ①……本次是重推……但 MUST 继续往下走 syncGateTxnPayStatus……在这里 return 等于把唯一的重试机会掐掉，
    且 return 非 0000 会让支付中心继续推 —— 亲手造出新的死循环。②其余情况……NEVER 回 0000，
    静默吞掉等于放弃这笔的最后一次纠错机会」；SQL 侧同一条在 `PayTxnDetailMapper.xml:170`。
65. **`syncGateTxnPayStatus` 必须显式检查 retCode** —— 同类 `syncGateTxnPayStatus` ·
    `.../service/impl/PaymentDomainServiceImpl.java:636`：「MUST 显式检查 retCode：本项目的 RPC 包装方法
    不抛异常，「没抛异常」不等于远端已收敛（AGENTS.md §5.2 已记录过同类事故）」；失败时 `:398`
    「NEVER 抛异常回滚——本地状态与 PAY_CALLBACK_LOG 是支付中心结果的唯一凭据，回滚等于丢证据。
    改为回非 0000 让支付中心重推」。
66. **拉黑前必须用 §1.2 payQuery 二次确认** —— `pay-sign-server` ·
    `PaymentDomainServiceImpl.addBlacklistForPaymentFailure` · `.../service/impl/PaymentDomainServiceImpl.java:680`：
    「因此拉黑 MUST 先用只读接口 §1.2 payQuery 向支付中心确认这笔的真实状态，**只有支付中心明确回 FAIL
    才拉黑**。查不到、查询失败、状态是 SUCCESS 或任何中间态，一律不拉黑…… NEVER 把「查不到就当失败」
    写成兜底」；事故原文在 `:676`（「测试卡 0178606904586419 被以 REASON=操作失败 拉进黑名单，
    乘客直接过不了闸」）；`queryGatewayPayStatus` · `:730`「只用于「拉黑前二次确认」这一个判断，
    NEVER 拿它回写 PAY_TXN_DETAIL」。
67. **重推硬限次的计数点与留痕** —— 同类 `:320`「硬限次的计数点 MUST 在 insert 之后」；
    `shouldStopRetry` · `:443`「NEVER 因为拿不到计数就静默放行」；`giveUpRetry` · `:453`
    「MUST 同时做两件事：打 ERROR 日志、把 PAY_CALLBACK_LOG.HANDLE_STATUS 置为 MANUAL。
    NEVER 只回 0000 不留痕，那等于静默丢单」；计数 SQL 口径在
    `pay-sign-server/src/main/resources/mapper/PayCallbackLogMapper.xml:41`
    「MUST 按 MERCHANT_ORDER_NO + CALLBACK_TYPE 统计：一笔订单的支付回调与退款回调共用本表，
    只按订单号统计会把两类混算」。
68. **支付中心订单号必须单独落列** —— `pay-sign-server` ·
    `PaymentDomainServiceImpl.updatePayRequestResult` · `.../service/impl/PaymentDomainServiceImpl.java:615`：
    「支付中心订单号 MUST 单独落库：退款报文 §3.1 的「原支付订单号」只认它」；实体侧
    `PayTxnDetail.payCenterOrderNo` ·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/entity/PayTxnDetail.java:40`
    「退款报文 §3.1 的「原支付订单号 orderNo」MUST 用这个值，缺它退款必失败」；回调侧 `:354`
    「NEVER 塞进 MERCHANT_ORDER_NO——那一列是我方商户订单号」。

#### 决策理由

69. **`requestPay` NEVER 加 `@Transactional`：第二条理由是资损级** —— `pay-sign-server` ·
    `PaymentDomainServiceImpl.requestPay` · `.../service/impl/PaymentDomainServiceImpl.java:125`：
    「一、锁跨网络……上游 gate-txn-pay-server 对同一 orderNo 的重试会串行堆在同一行上……
    二、「我要去扣款了」这个标记直到支付中心返回后才提交。这是资损级缺陷……结果是「对方已扣款，
    我方连订单记录都没有」，事后既查不到也对不上账」；去事务后的三段形状与末句 `:148`
    「NEVER 用「保持原子」换「可能整段丢失」」。
70. **`receivePayResult` NEVER 加 `@Transactional`：事故与「回滚不丢证据」被推翻** —— 同类 `:271`：
    「一旦包在事务里，UPDATE PAY_TXN_DETAIL 拿到的行级排他锁会一直持到 RPC 返回并提交……
    自我放大，没有出口」；`:280`「已发生事故（2026-08-26 21:07~21:14 生产）：订单
    GT20260826210647653586419 循环重推 8 分钟，单次请求耗时 287233ms，UPDATE 实测等锁 44997ms，
    Oracle V$SESSION 持续 enq: TX - row lock contention。循环期间每一轮事务都被强杀回滚，
    连 PAY_CALLBACK_LOG 都没留下……所谓「回滚不会丢证据」在事务内并不成立」；重推策略取舍在 `:295`
    「NEVER 在放弃重推时不留痕」。
71. **`isAlreadyPaidSuccess` 不能散成副本** —— `pay-sign-server` · `PaymentDomainServiceImpl`（类注释）·
    `.../service/impl/PaymentDomainServiceImpl.java:68`：「**NEVER 在本类里重建 `isAlreadyPaidSuccess`
    的私有副本** —— 它是对支付中心应答措辞的硬编码约定，散成多份后改一处漏一处不会有编译错误，
    只会让已扣款成功的交易被判成失败并走到拉黑分支」；本体判据在 `PaySignGateway.isAlreadyPaidSuccess` ·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/support/PaySignGateway.java:50`
    「只按 msg 措辞识别，**NEVER 放宽成「code=9999 就算已支付」** —— 9999 是支付中心的通用失败码，
    放宽等于把所有失败都当成功」。
72. **ADR-D98：免密扣款的纯规则外提，且三个方法刻意没搬** —— `pay-sign-server` · `PayTxnRules`（类注释）·
    `.../support/PayTxnRules.java:21`：「**本类 MUST 保持纯函数、零状态、零依赖，NEVER 注入 mapper /
    client / properties**」；`:23`「**刻意留在 `PaymentDomainServiceImpl` 里没搬的三个**（NEVER 因为
    「看着也是纯的」补搬）」：`applyAccountUserView`（三处 `log.warn`，「按类名检索日志的排查路径会断」）、
    `shouldStopRetry`（与硬限次常量同源）、`resolvePaySignInfoFromAccount`（走端口、非纯函数）。
73. **ADR-D94：账户域读侧收进端口，「只收写不收读」被实证推翻** —— `pay-sign-server` ·
    `AccountDomainPort`（类注释）·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/port/AccountDomainPort.java:22`：
    「**读侧已于 2026-09-16 收进本接口（ADR-D94 续）**……**那个判断已被实证推翻，NEVER 回退**」，
    两条依据：「字段很多」不成立（三处调用点合起来只读 6 个字段）；「各调用点处置不同」**恰恰是缺陷本身** ——
    「于是一条 `8004` 应答里残留的 `channel` / `reqContractNo` 被送去**真实免密扣款**」。
    连带约束：`:18`「**本接口的方法一律返回 `RpcOutcome`、NEVER 返回 boolean**……实现方 **NEVER 向外抛异常**」、
    `:39`「**NEVER 让读方法返回 `Optional`** —— 会把「业务拒绝」和「不可达」压成同一个 `empty`」、
    `:9`「叫 `PayDomainPort` 等于指向自己。**NEVER 改回那个名字**」、`:72`
    「与 `removeChannel` <b>不是同一个 URL</b>，NEVER 合并成一个方法」。
74. **`AccountQuery` 三分类型与空响应归属** —— `pay-sign-server` · `AccountQuery`（类注释）·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/port/AccountQuery.java:13`：
    「**调用点 MUST 用穷尽 switch 模式匹配**，少写一个分支直接编译失败」；`:19`
    「NEVER 直接放 rpc 的查询 DTO —— 那等于把 rpc 契约换个地方再暴露一次，端口就白建了」；
    `NotFound` · `:31`「**MUST NOT 重试**」、`:38`「**NEVER 改成 Unreachable** —— 钱包绑定状态查询会因此
    把「查不到」从 `0000/NOT_SIGNED` 变成 `9001`，属对外行为变更」。窄视图约束在
    `AccountUserView` · `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/port/AccountUserView.java:8`
    「**NEVER 把字段加回来「以备将来」** —— 端口一旦变宽就退化成 rpc DTO 的别名，防腐层也就没了」、
    `:13`「把 `retCode` 塞进视图会诱导调用点再判一次，那正是 ADR-D94 里「两个调用点对同一个 DTO
    给出相反答案」的成因」。

#### 陷阱

75. **`ensurePayTxn` 的 `DuplicateKeyException` 兜底不得上抛** —— `pay-sign-server` ·
    `PaymentDomainServiceImpl.ensurePayTxn` · `.../service/impl/PaymentDomainServiceImpl.java:514`：
    「NEVER 抛出：抛出会让上游 gate-txn-pay-server 的重试拿到 9999，而库里其实是好的」；
    `:475`「这里 **NEVER** 再查一遍」（存在性由调用方传入）；`:477`「catch `DuplicateKeyException` 保留是因为
    「调用方查询」与 insert 之间仍有并发窗口……warn 不带堆栈」。
76. **补参方法只补字段、不建记录** —— 同类 `resolvePaySignInfoFromAccount` · `:524`：
    「本方法只补字段，NEVER 建 PAY_TXN_DETAIL……原先这里也调一次，导致每笔交易插两次：
    第一次发生在测试金额覆盖（test-force-amount）之前，AMOUNT 落的是原始金额，与实际请求支付平台的
    金额不一致，且第二次必然撞唯一索引刷 ORA-00001 堆栈」；`:559`「端口契约是「NEVER 抛异常」，
    这层只兜住装配阶段的意外……任何异常都 NEVER 放大成 requestPay 不可用」。
77. **`queryPayStatus` 返回 null 不得吞成「已收敛」** —— 同类 `queryPayStatus` · `:426`：
    「订单不存在或查询异常都返回 null，交由调用方按「未收敛」处理——NEVER 在这里吞成「已收敛」，
    那会把真实丢单伪装成幂等命中」。
78. **回调 UPDATE 的状态机只能比较「列与常量」** —— `pay-sign-server` ·
    `PayTxnDetailMapper.updatePayCallback` ·
    `pay-sign-server/src/main/resources/mapper/PayTxnDetailMapper.xml:158`：
    「WHERE 的状态机 MUST 只比较「列与常量」，NEVER 把入参写进状态判断。原先写成
    `PAY_STATUS != 'SUCCESS' OR (PAY_STATUS = 'SUCCESS' AND #{payStatus} = 'SUCCESS')`，
    而 `#{payStatus}` 是入参不是列：SUCCESS 回调进来时右分支退化成 `'SUCCESS' = 'SUCCESS'`，
    整个括号恒真，等于没有状态机……回调没有幂等出口（2026-08-26 事故的第 2 个成因）」；
    白名单含 FAIL 的理由在 `:166`~`:168`「若支付中心先推 FAIL 后推 SUCCESS，钱已扣就 MUST 让 SUCCESS 落地」。
79. **Druid WallFilter 与 XML 注释的孪生陷阱（本模块两处原文告示）** ——
    `pay-sign-server/src/main/resources/mapper/PayTxnDetailMapper.xml:153`：
    「注意：SQL 正文内 NEVER 使用行注释或块注释。Druid WallFilter 默认 commentAllow=false，
    SQL 正文带注释会被判定为注入并抛 SQLException（comment not allow），导致 UPDATE 静默失效。
    说明只能写在本 XML 注释里。同时本 XML 注释内也不得出现连续两个连字符，
    否则 XML 解析失败、mapper 加载不了、服务起不来」；同一告示在 `:214` 与
    `pay-sign-server/src/main/resources/mapper/PayCallbackLogMapper.xml:43`。
80. **虚拟线程 pin / 连接被强杀会让「本次调用失败」不等于「这笔钱没扣成」** —— `pay-sign-server` ·
    `PaymentDomainServiceImpl.addBlacklistForPaymentFailure` · `.../service/impl/PaymentDomainServiceImpl.java:674`：
    「入参 `gatewayResponse` 只代表**本次 HTTP 调用**没拿到成功应答，不等于「这笔钱没扣成」。
    网络超时、连接被 Druid 强杀、虚拟线程被 pin 住导致响应迟到，都会让一笔**已经在支付中心扣款成功**的交易
    在本端表现为失败」。

### 四、退款（支付 API 3.1 + 两套补偿）

#### 契约与判据

81. **退款查询必须同时送 `refundOrderNo` 与 `merchantRefundNo`** —— `pay-sign-server` ·
    `RefundDomainServiceImpl.settleRefundByQuery` ·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/impl/RefundDomainServiceImpl.java:265`：
    「两个键都送、值都填我方的 REFUND_ORDER_NO，这是 2026-09-15 实测逼出来的写法，NEVER 只送一个」；
    实测应答原文 `{"code":9999,"msg":"退款流水号或商户退款流水号必填"}`，
    「它认为两个都没填。注意它的措辞是「流水号」而文档写「订单号」，字段名与文档并不一致」；判据 `:273`
    「**出向 bizData 的字段名同样不能只看文档，MUST 拿真实应答验证**」；后果 `:275`
    「不是报错而是**永久空转**……每轮都返 0000「成功」，调度日志一片绿」。
82. **退款账本唯一真相是 `PAY_REFUND_DETAIL`，汇总只能全量重算** —— `pay-sign-server` ·
    `RefundDomainServiceImpl`（类注释）· `.../service/impl/RefundDomainServiceImpl.java:60`：
    「**退款账本的唯一真相是 `PAY_REFUND_DETAIL`**；`PAY_TXN_DETAIL` 的 `REFUND_AMOUNT` /
    `REFUND_STATUS` 是**派生汇总**，只能由 `PayTxnDetailMapper.updateRefundSummary` 按明细全量重算，
    **NEVER 累加式更新**」；mapper 侧 `PayTxnDetailMapper.updateRefundSummary` ·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/mapper/PayTxnDetailMapper.java:29`
    「**NEVER** 由调用方传入增量——入参没有幂等键，传增量就意味着重复执行会重复累加」；SQL 侧
    `pay-sign-server/src/main/resources/mapper/PayTxnDetailMapper.xml:196`
    「本语句 MUST 是「按 PAY_REFUND_DETAIL 重算」，NEVER 写成 `REFUND_AMOUNT = NVL(REFUND_AMOUNT, 0) +
    #{refundAmount}`……一旦虚高，后续真实退款会被误判为超额并拒绝，且账面无法自愈」。
83. **汇总必须在明细置终态之后、且回查里那次不能省** —— `pay-sign-server` ·
    `RefundDomainServiceImpl.requestRefund` · `.../service/impl/RefundDomainServiceImpl.java:181`：
    「汇总 MUST 在明细置为 SUCCESS 之后执行：SQL 是按 PAY_REFUND_DETAIL 重算的，顺序颠倒会漏掉本笔」；
    `settleRefundByQuery` · `:323`「汇总 MUST 在明细置终态之后执行，且这里 MUST 无条件执行一次 ——
    requestRefund 摘掉事务后存在「明细已 SUCCESS、汇总没重算」的中间态，只有这里能兜住它」；
    `:134`「NEVER 因为「这里已经算过一次」就把回查里那次省掉 —— 省掉后 ④ 成功、⑤ 失败的那一笔
    永远不会有人补」。
84. **退款回查的状态白名单只认 SUCCESS / FAIL** —— `pay-sign-server` ·
    `RefundDomainServiceImpl.resolveRefundQueryStatus` · `.../service/impl/RefundDomainServiceImpl.java:342`：
    「**NEVER 改成「不是 SUCCESS 就当 FAIL」** —— 那会把一笔仍在渠道处理中的退款写成失败，
    而 PAY_TXN_DETAIL 的已退总额是按本表重算的」。
85. **回查 CAS 影响 0 行 MUST 当失败** —— `pay-sign-server` · `PayRefundDetailMapper.finishFromQuery` ·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/mapper/PayRefundDetailMapper.java:42`：
    「<b>调用方 MUST NOT 把 0 行当成功继续</b>，尤其不能据此去重算汇总」；调用点 `:317`
    「NEVER 当成功继续：继续下去会拿本次回查结果去重算汇总，把别人刚写对的口径覆盖掉」；SQL 侧
    `pay-sign-server/src/main/resources/mapper/PayRefundDetailMapper.xml:133` / `:136` 同口径。
86. **回查 NEVER 动 `REQUEST_COUNT`** —— `pay-sign-server` ·
    `PayRefundDetailMapper.delayNextRefundQuery` · `.../mapper/PayRefundDetailMapper.java:49`：
    「**刻意不动 REQUEST_COUNT**：那一列的语义是「已发起退款请求次数」，回查不是发起退款，
    混进去会让运维分不清「我方重复退了几次」」；SQL 侧 `PayRefundDetailMapper.xml:158`
    「NEVER 在这里动 REQUEST_COUNT……回查不是发起退款，混进去会让运维误判成重复退款」。
87. **跨表对账 B 类不可自愈，MUST NOT 尝试修** —— `pay-sign-server` ·
    `PayRefundDetailMapper.selectOrphanRefundOrders` · `.../mapper/PayRefundDetailMapper.java:87`：
    「<b>这一类 MUST NOT 尝试修</b>：`updateRefundSummary` 对它们影响 0 行（没有行可 UPDATE），
    把 0 行当成「已修好」会让一批真正的坏账从告警里消失。调用方 MUST 只记 WARN + 计入 skipped，
    等人工核对「这笔退款退的到底是哪张原单」」；服务侧 `RefundDomainServiceImpl.compensateRefundSummary` ·
    `.../service/impl/RefundDomainServiceImpl.java:401`「调它既修不好、又会把一批真正的坏账混进
    「已扫过」的口径里。NEVER 在这里调它」；A 类判据 `:378`
    「**A 类的判据是「影响行数 &gt; 0」而不是「没抛异常」**……NEVER 计成已修」。
88. **退款明细 `ORDER_NO` 必须存我方商户订单号** —— `pay-sign-server` ·
    `PayRefundRules.buildPayRefundDetail` ·
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/support/PayRefundRules.java:73`：
    「ORDER_NO MUST 存我方商户订单号：退款汇总 updateRefundSummary 是按 PAY_REFUND_DETAIL.ORDER_NO =
    PAY_TXN_DETAIL.ORDER_NO 关联重算的，存别的号一律关联不上。生产已有 12 条 PAY_REFUND_DETAIL
    因历史上存了支付中心订单号而关联不到原支付订单」。
89. **退款可退金额判定的文案与顺序即契约** —— `pay-sign-server` · `PayRefundRules.validateRefundPayTxn` ·
    `.../support/PayRefundRules.java:40`：「返回的字符串会原样进 APP 应答的 `retMsg`，
    <b>NEVER 改文案、NEVER 调整判断顺序</b> —— 顺序决定「同时不满足两条时报哪一条」，
    联调方可能已按文案做断言」。

#### 决策理由

90. **`requestRefund` 摘 `@Transactional`（批次 5B / ADR-D8 三件套同批）** —— `pay-sign-server` ·
    `RefundDomainServiceImpl.requestRefund` · `.../service/impl/RefundDomainServiceImpl.java:108`：
    「带着它比摘掉更危险：① 与 ② 在 ③ 出网时都还没提交……结果不是「停在某个状态」，
    而是**本地连这一行都不存在**：REFUND_ORDER_NO 与 REQUEST_BODY 都查不到，事后既无从对账也无从补偿」；
    摘后最坏停在 `PROCESSING`「可查、可补偿」；末句 `:123`「NEVER 用「保持原子」换「可能整段丢失」」。
91. **两个补偿端点都不带事务，理由各不相同** —— 同类（类注释）`:55`：
    「**三个入口都不带 `@Transactional`，理由分别写在各自方法头，NEVER 加**」；
    `compensateRefundQuery` · `:212`「它逐行出网调支付中心，被事务包住就是把刚修掉的形状原地复现」；
    `compensateRefundSummary` · `:370`「① 每条 `updateRefundSummary` 都是按明细全量重算的单语句幂等
    UPDATE……② 整批包一个事务会把 200 行原支付订单的排他锁攒到批次末尾才放……③ 整批一个事务时任何一行
    抛异常都会连带回滚前面已经算对的那些，逐单独立收口严格更安全。**NEVER 加回。**」。
92. **退款从支付域拆出来的动机含一次真实教训** —— 同类（类注释）`:50`：
    「在 1166 行的原文件上做编辑时，`old_string` 匹配落到了方法内部，误删了 `resolveRefundQueryStatus`
    的方法体中段（已当场恢复）。同一段文字在超长文件里更容易出现多处近似匹配，于是
    **文件大小本身就是缺陷密度** —— 这是拆分的直接理由之一，记在这里以免后人把四件事再合回去」。
93. **扫描循环统一走 `OutboxScan`（ADR-D46）** —— 同类 `:236` / `:391`：
    「扫描循环走 OutboxScan（ADR-D46），NEVER 手写 for + try/catch：「单条失败不中断整批 /
    每行只计一次 / 兜住两侧异常」三条不变量已在骨架里固化并有测试」；解约侧同一条在
    `TerminationCompensationService.compensateChannelSync` ·
    `.../service/impl/TerminationCompensationService.java:256`。
94. **补偿扫表 SQL 刻意不捞 CLOB** —— `pay-sign-server` ·
    `PayRefundDetailMapper.selectCompensableRefundQuery` ·
    `pay-sign-server/src/main/resources/mapper/PayRefundDetailMapper.xml:95`：
    「真要看请求 / 应答原文时按主键单条查，NEVER 为了「顺手」把 CLOB 加进本清单」。
95. **A 类扫表 SQL 三层嵌套不能压平** —— 同 XML `:171`：「三层嵌套各有各的必要性，NEVER 压平：
    最内层只做「按 TXN_DATE 分区裁剪 + 只看 SUCCESS」拿候选订单号……中间层做金额比对，
    比对用的两个 SUM 都**不带 TXN_DATE 条件**……最外层套 ROWNUM，MUST 在已排序子查询之外」。
96. **只返回订单号、不建投影类** —— `pay-sign-server` · `PayRefundDetailMapper.selectDriftedRefundSummary` ·
    `.../mapper/PayRefundDetailMapper.java:65`：「<b>NEVER 为它新建投影类 / DTO</b> ——
    多带一个「我方算出的差额」字段就等于给了调用方一个「按差额补一下」的入口，而那条路没有幂等键」。

#### 陷阱

97. **`TXN_DATE` 畸形的行永远扫不到（已知盲区）** —— `pay-sign-server` ·
    `PayRefundDetailMapper.selectDriftedRefundSummary` · `.../mapper/PayRefundDetailMapper.java:69`：
    「**已知盲区：`TXN_DATE` 畸形的行永远扫不到。**……库里存在少量把 `ORDER_NO` 前 8 位当成日期写进去的
    历史行（形如 `28063829` / `28064044`，不是 `yyyyMMdd`）。字符串比较下这类值<b>恒小于</b>任何
    `2026xxxx`，因此它们被下界永久排除、本任务永远不会碰到，<b>只能人工处理</b>。
    <b>NEVER 为了捞它们去掉 TXN_DATE 下界</b>」；XML 侧同一条在 `PayRefundDetailMapper.xml:179`。
98. **`NEXT_REQUEST_TIME` 为 NULL 时的三值逻辑会让补偿永远 scanned=0** —— `pay-sign-server` ·
    `PayRefundDetailMapper.selectCompensableRefundQuery` ·
    `pay-sign-server/src/main/resources/mapper/PayRefundDetailMapper.xml:114`：
    「因此这里 MUST 写成「IS NULL OR 到点」—— 写成裸的 `NEXT_REQUEST_TIME &lt;= SYSTIMESTAMP`
    会因 NULL 比较为 UNKNOWN 而一条都捞不到，SQL 合法、日志上只表现为「补偿永远 scanned=0」」；
    同型 NVL 兜底告示在 `:109`（`LAST_REQUEST_TIME`）与 `:176`（`REFUND_AMOUNT`）。
99. **补偿扫表只认 `PROCESSING`（白名单而非「非终态即可」）** —— 同 XML `:104`：
    「这是「白名单」而不是「非终态即可处理」：只有 PROCESSING 才代表**已经把退款请求发出去了**……
    放行 INIT 等于去问支付中心一笔它根本没收到的退款单」。
100. **`refund-query-url` 尚未对真实网关实测** —— `pay-sign-server` ·
     `PaySignProperties.refundQueryUrl` ·
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/config/PaySignProperties.java:40`：
     「**该地址尚未对真实网关实测**……上线前 MUST 用一笔真实退款单打一次确认，
     **NEVER 因为「和别的 URL 长得一样」就当已验证** —— 2026-08-26 那次漏 `/v1` 返的是
     `code=600 操作失败`、不是 404，光看应答分不出是配错还是业务拒绝」。

### 五、回调与通知（IPD02 / IPD03、IF8B 出向通知、补偿队列）

#### 契约与判据

101. **签约通知的三个 `RpcOutcome` 分支处置** —— `pay-sign-server` ·
     `SignResultCommittedListener.syncPayAccountIdQuietly` ·
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/event/SignResultCommittedListener.java:116`：
     「按 AGENTS.md §5.2 <b>显式判 `RpcOutcome` 的每个分支</b>，不假定「没抛异常就是写成功」：
     账户域未命中通道行时返 `8004`，那是「签约先于加通道」的合法时序，记 info 即可。
     这里两个失败分支的日志级别<b>刻意不同</b>（业务拒绝 info / 不可达 warn），但<b>处置相同 —— 都是放弃</b>：
     本方法整体是「允许丢」的展示值同步，<b>NEVER 因为能区分出 `Unreachable` 就给它加重试或补偿</b>」；
     端口侧同一条在 `AccountDomainPort.syncPayAccountId` · `.../port/AccountDomainPort.java:81`
     「属 `RpcOutcome.BizRejected`，MUST 只记 info」。
102. **回查签约结果流水必须带 `OPERATION_TYPE` 过滤** —— `pay-sign-server` ·
     `SignResultCommittedListener.onSignResultCommitted` · `.../event/SignResultCommittedListener.java:43`：
     「回查 `APP_PAY_SIGN_REQUEST` <b>MUST 用 `PaySignRequestMapper.selectLatestSignResultBySeq`</b>，
     NEVER 用 `selectByRequestSignSeq`：后者不带 `OPERATION_TYPE` 过滤，同一流水号下还有
     `OPERATION_TYPE='SIGN'` 的记录，取错会拿到 `SIGN_STATUS` 为空、且不在通知队列里的那行」；
     mapper 侧 `PaySignRequestMapper.selectLatestSignResultBySeq` ·
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/mapper/PaySignRequestMapper.java:19`
     「这里 MUST 带 OPERATION_TYPE 过滤，NEVER 复用 `selectByRequestSignSeq`……拿它去重发会发出一条
     signResult 为空的报文」；SQL 侧 `pay-sign-server/src/main/resources/mapper/PaySignRequestMapper.xml:76`。
103. **签约通知队列与解约通知队列各管一类，不得互相替代** —— `pay-sign-server` ·
     `AppNotifyService.compensateSignNotify` / `asyncRetryTerminationNotify` ·
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/AppNotifyService.java:35`：
     「`compensateSignNotify()` 只扫 APP_PAY_SIGN_REQUEST，覆盖不到解约表，两者不可互相替代」；
     Controller 侧 `PaySignInternalController.compensateSignNotify` ·
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/controller/PaySignInternalController.java:40`
     「两者各自管一类通知，NEVER 让本接口再去扫 RECEIVE_TERMINATION_RESULT 流水——那会让同一条解约通知
     被两个队列各自重发一次」；SQL 侧的事故记录在
     `pay-sign-server/src/main/resources/mapper/PaySignRequestMapper.xml:91`
     「NEVER 把 RECEIVE_TERMINATION_RESULT 加回白名单……已发生：每成功解约一次就留一条幽灵 PENDING 行，
     过 staleMinutes 后给 APP 灌一条重复的解约成功通知（2026-08-26 修复）」。
104. **单条重发 NEVER 走批量补偿、且不占用重试预算** —— `pay-sign-server` ·
     `AppNotifyService.resendSignNotify` · `.../service/AppNotifyService.java:54`：
     「NEVER 为了重发一条而去打批量补偿——库里符合扫描条件的历史流水会被一起推给 APP，
     而 APP 侧实测没有按 requestSignSeq 幂等，污染的是对端状态、我方无法回滚」；实现侧
     `AppNotifyServiceImpl.resendSignNotify` ·
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/impl/AppNotifyServiceImpl.java:394`
     「同步投递：人工触发要立刻看到回执。NEVER 走 notifyExecutor……同时 NEVER 递增 NOTIFY_RETRY_COUNT：
     那是补偿队列的预算，人工重放不该占用」；两条白名单前置校验在 `:370`（必须有 SIGNED 记录）
     与 `:383`（必须已有签约结果流水）。
105. **通知重试计数「每轮补偿 +1」，回写侧不得再加** —— `pay-sign-server` ·
     `AppNotifyServiceImpl.compensateSignNotify` · `.../service/impl/AppNotifyServiceImpl.java:330`：
     「MUST 在提交前做，且 MUST 对 PENDING 与 FAILED 一视同仁……计数放在这里而不是结果回写里，
     是因为回写本身可能丢；计数先落库才有上限保证」；`:335`「与之配套：updateNotifyStatus 的失败分支
     NEVER 再递增计数，否则一轮涨 2、3 次预算 2 轮就用完。全链路口径是「每轮补偿 +1」」；
     回写侧 `:445`「只记状态与原因，NEVER 在这里动 NOTIFY_RETRY_COUNT」；解约侧同一条在
     `TerminationCompensationService.compensateTerminationNotify` ·
     `.../service/impl/TerminationCompensationService.java:224` 与 `:226`。
106. **通知成功码不得凭猜测扩充、空响应体按失败处理** —— `pay-sign-server` ·
     `AppNotificationClient.successRetCodes` ·
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/client/AppNotificationClient.java:36`：
     「**NEVER 凭猜测加码**：加错会把真实失败判成成功，从此不再重试、补偿也扫不到、`NOTIFY_RESULT`
     却记着成功，事后无从发现……加码前 MUST 拿到 APP 侧码表或书面确认」；`notify` · 同文件 `:60`
     「空响应体按**失败**处理……把空体当成功会让通知静默丢失且不再重试，宁可多发一次由 APP 幂等吸收」。
107. **出向通知加签的算法与源串一个字都不能改** —— `pay-sign-server` · `AppNotifySigner.buildItpSign` ·
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/support/AppNotifySigner.java:26`：
     「**算法与源串拼法一个字都不能改**（AGENTS.md §5.2 安全红线）：`signType=00` 或空 ⇒ 不签；
     `01` ⇒ SHA-1；`02` ⇒ MD5；其余 ⇒ `null`。源串是「7 个字段按 `key=value` 收集后**字典序排序**、
     `&` 连接，末尾再拼 `&key=<密钥>`」……**改动本类任何一行 MUST 人工复核安全合规性，并与对端重新联调验签**」；
     `:21`「**密钥 MUST 由调用方当参数传进来，本类 NEVER 自己持有 `itpSignKey`**」、`:24`
     「**NEVER 给本类加 `@Value` / `@Component`** —— 那等于把密钥的持有点又多开一处」、`:47`
     「<b>NEVER 把它写进日志。</b>」、`:34`「**NEVER 改成抛异常** —— 那会让本已落库的通知任务在补偿链路里
     反复失败」、`:35`「检索该 ERROR 日志 MUST 用类名 `AppNotifySigner`」。
108. **解约通知重发的报文必须与首次一致** —— `pay-sign-server` ·
     `AppNotifyServiceImpl.asyncRetryTerminationNotify` · `.../service/impl/AppNotifyServiceImpl.java:167`：
     「通知报文 MUST 与首次通知一致，因此解约时间取 COMPLETE_TIME 而不是当前时间」。
109. **签约与解约两条补偿链路读同一个重试上限配置键** —— `pay-sign-server` ·
     `AppNotifyServiceImpl.maxRetryCount` · `.../service/impl/AppNotifyServiceImpl.java:87`：
     「MUST 与 `TerminationInternalServiceImpl` 读同一个配置键，否则签约与解约两条补偿链路的重试预算会漂移。
     默认 10：上限 × 调度间隔就是「APP 侧最长可容忍故障时长」，原先写死的 3 配 10 分钟间隔只能兜住半小时」；
     对侧 `TerminationCompensationService:75` 是同一条。

#### 决策理由

110. **`@TransactionalEventListener(AFTER_COMMIT)` 是签约通知的唯一正确时机** —— `pay-sign-server` ·
     `SignResultCommittedEvent`（类注释）·
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/event/SignResultCommittedEvent.java:11`：
     「原实现在 `@Transactional` 方法内直接调 `appNotifyService.asyncNotifySignResult`，
     违反 AGENTS.md §5.2……事务若在提交阶段失败回滚，APP 已经收到「签约成功」而本地
     `APP_PAY_SIGN_INFO` 并没有这行。改为发布事件后，投递物理上位于 `afterCommit`，回滚则事件根本不投递」；
     字段取舍 `:17`「**NEVER 把 PaySignRequest / PaySignInfo 实体塞进来**：实体在事务内可能还是脏快照，
     监听器 MUST 自己按键回查已提交的数据」；`:23`「**本事件是进程内、非持久的**……本事件只是「快速路径」」。
111. **监听器注解三个参数都是有意选的** —— `pay-sign-server` ·
     `SignResultCommittedListener.onSignResultCommitted` · `.../event/SignResultCommittedListener.java:27`：
     「**注解的三个参数都是有意选的，NEVER 改**」：`phase = AFTER_COMMIT`「这是本类存在的全部理由」；
     `fallbackExecution = false`「**没有事务就不执行**……但若哪天那个注解被摘掉，本监听器会**静默不执行**……
     改成 `true` 更糟——那等于「无事务时立刻同步发 HTTP」……摘 `@Transactional` 的人 MUST 同步处理这里」；
     **不加 `@Async`**「再套一层线程池只会让异常彻底无声，且默认执行器在
     `spring.threads.virtual.enabled=true` 下有 pin 载体线程的风险（§5.2）」；`:25`
     「**NEVER 因为写在一起就给 ② 也加补偿、或让 ② 的失败影响 ①**」；`:110`
     「**NEVER 为它加落库状态 + 扫表补偿**——那是给「必须最终一致的业务写」用的，不该为一列展示值付这个复杂度」；
     `:113`「**NEVER 让它抛异常**」。
112. **事件只准用于这一件事，`publishEvent` 放在事务内是安全的** —— `pay-sign-server` ·
     `CallbackDomainServiceImpl.eventPublisher` ·
     `.../service/impl/CallbackDomainServiceImpl.java:107`：「publishEvent 本身是纯内存操作，不碰连接、
     不出网，放在事务内是安全的。NEVER 用它替代 appNotifyService 的直接调用——只有事务内的通知才需要绕这一层」；
     调用点 `:228`「NEVER 在这里直接调 appNotifyService：本方法带 `@Transactional`，事务若在提交阶段失败回滚，
     APP 已收到「签约成功」而 APP_PAY_SIGN_INFO 并没有这行（AGENTS.md §5.2）」。
113. **APP 通知先发、删通道后做** —— `pay-sign-server` ·
     `CallbackDomainServiceImpl.receiveTerminationResult` · `.../service/impl/CallbackDomainServiceImpl.java:405`：
     「阶段二：先提交 APP 通知（异步、立即返回），再走删通道的快速路径。NEVER 反过来：
     account-server 慢或不可达时会把 APP 通知一起拖住，而通道清理已经落成 PENDING、补偿一定会重推，
     这条通知不该等它」。
114. **`/internal/payment` 单独起前缀** —— `pay-sign-server` · `PaymentInternalController`（类注释）·
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/controller/PaymentInternalController.java:18`：
     「前缀是按**业务域**分的。退款属支付域，塞进 `/internal/paySign`……或 `/internal/termination` 都会让
     「按前缀就能看出归谁」这条约定作废。**NEVER 把支付域的补偿端点挂到那两个前缀下。**」；`:22`
     「**NEVER 在本模块新建同形副本**（AGENTS.md §5.1）」（应答复用 `model` 的 `CompensateNotifyRespDTO`）。
115. **本模块所有补偿由 web-admin Quartz 驱动** —— `pay-sign-server` ·
     `PaymentInternalController.compensateRefundQuery` / `compensateRefundSummary` ·
     `.../controller/PaymentInternalController.java:47` 与 `:74`：「**触发方是 web-admin 的 Quartz `sys_job`**，
     本模块 NEVER 自带 `@Scheduled`（全模块一个都没有，见 AGENTS.md §2.2.1；排查「退款回查有没有跑」
     MUST 查 `SYS_JOB_LOG`，NEVER 在本模块里找 `@Scheduled`）」；`:53`
     「**停用本任务等于让那批单子永久悬挂**，NEVER 只是「先关掉看看」」；`:78`
     「**它与 `/internal/payment/compensateRefundQuery` 是两件不同的事，NEVER 合并成一个端点**……
     合并后既没法分别调频……出网那半边一挂也会连带把纯本地的这半边一起拖停」；`:85`
     「返回的 `skipped` 里混着两种单，**看到非 0 不等于「下一轮会自己好」**……区分 MUST 看日志措辞，
     不要只看计数」。

#### 陷阱

116. **`/internal/**` 全部没有鉴权（已登记 P0）** —— `pay-sign-server` ·
     `PaymentInternalController.compensateRefundQuery` · `.../controller/PaymentInternalController.java:60`：
     「因此上线前 MUST 确认该端口不对外暴露；补鉴权时 MUST 与 `/internal/paySign/**`、
     `/internal/termination/**` 一起做，NEVER 在这里自造签名逻辑」（同一条在 `:89` 与
     `PaySignInternalController.resendSignNotify` ·
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/controller/PaySignInternalController.java:64`）。
117. **通知补偿扫表必须用 `NVL` 兜底 NULL 时间列** ——
     `pay-sign-server/src/main/resources/mapper/PaySignRequestMapper.xml:97`：
     「滞留时长基准用 NVL(NOTIFY_TIME, CREATE_TMS)：PENDING 行的 NOTIFY_TIME 为 NULL，
     只看 NOTIFY_TIME 会因三值逻辑漏掉全部待补记录」；`:99`「NOTIFY_RETRY_COUNT 用 NVL 兜底：
     历史数据可能为 NULL，直接比较同样会漏行」；`:130`「NOTIFY_RETRY_COUNT 用 NVL 兜底：
     NULL + 1 结果仍是 NULL，该行会被 selectCompensableNotify 的重试次数条件永久过滤掉、再也补不到」；
     解约侧同型三条在 `AppTerminationRequestMapper.xml:122`~`:124`。
118. **`ROWNUM` 必须套在已排序子查询外层** —— `pay-sign-server/src/main/resources/mapper/AppTerminationRequestMapper.xml:82`
     等六处：「ROWNUM 必须套在已排序的子查询外层，直接与 ORDER BY 同层会先截断再排序」
     （另见同文件 `:99` / `:125` / `:447`、`PaySignRequestMapper.xml:100`、`PayRefundDetailMapper.xml:117`）。

### 六、渠道判定（`0B` 钱包 vs 传统签约）

#### 契约与判据

119. **判定入口只有一个，且只回答「是哪个渠道」** —— `pay-sign-server` · `PaymentChannels`（类注释）·
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/support/PaymentChannels.java:24`：
     「因此本类只回答「是哪个渠道」，<b>NEVER 往里加「这个渠道该怎么处理」</b> —— 那属于业务分派，归领域服务」；
     `:27`「**NEVER 在业务类里再写 `PaymentVendorEnum.WALLET.getCode()` 或字面量 `"0B"`**，
     也 NEVER 再声明本模块第 6 份 `WALLET_PAYMENT_VENDOR` 常量」；`isWallet` · `:48`
     「`null` / 空串一律返回 `false`……<b>NEVER 改成抛异常</b>：渠道字段在多个 APP 报文里是非必填的」；
     `classify` · `:58`「**选择处理链路 MUST 用本方法 + 穷尽 `switch`**，只问「是不是钱包」的谓词才用前者」、
     `:62`「未知编码与 `null` 归到 `PaymentChannel.Contracted`，这是收口前的既有行为，NEVER 改成抛异常」。
120. **入口级路径选择必须穷尽 `switch`（当前 6 处）** —— `pay-sign-server` ·
     `ContractDomainServiceImpl.requestContractAdvisory` / `requestContractResult` / `requestTermination` ·
     `.../service/impl/ContractDomainServiceImpl.java:336` / `:415` / `:536`；
     `CallbackDomainServiceImpl.receiveTerminationResult` · `.../service/impl/CallbackDomainServiceImpl.java:281`；
     `PayTxnRules.validatePaySignInfo` · `.../support/PayTxnRules.java:61`；
     `TerminationExecutor.unbindAgreement` · `.../service/impl/TerminationExecutor.java:209`。原文：
     「入口级路径选择 MUST 穷尽（ADR-D109）：新增渠道类别时编译器会在这里报错 —— pattern switch
     语句与表达式**都**校验穷尽性（2026-09-16 实测）。NEVER 退回 if (isWallet(...))：那个 else 隐含
     「其余一切按传统签约处理」，漏改不报错」；空分支保留的理由：「空分支是刻意的：它是「这个类别
     已被考虑过」的证据，NEVER 删」（`:351` / `:423` / `:544` / `:297` / `TerminationExecutor:218`）。

#### 决策理由

121. **ADR-D109：为什么用 sealed `PaymentChannel` 而不是 `isWallet(...)` 布尔判断** —— `pay-sign-server` ·
     `PaymentChannel`（类注释）·
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/support/PaymentChannel.java:11`：
     「布尔判断的 else 隐含「其余一切都按传统签约处理」，因此**新增一个需要特殊处理的渠道时，
     漏改的那个分派点不会编译失败、不会告警，只会静默把新渠道当传统渠道处理**（去支付中心查一个不存在的协议）。
     而这不是假想：`PaymentVendorEnum` 里已有 `0C 数币APP` 与四个 `CBDC_*`，AGENTS.md §2.2.2 记着
     数字人民币硬钱包共 7 个接口「docs 有规格、代码无实现」—— 第三个类别是**已经在路上的**既定事实。
     改成 sealed 后，那天只需在这里加一个变体，**编译器会把每一个需要改的分派点逐个列出来**」；
     边界 `:20`「**只在「入口级路径选择」处用穷尽 `switch`**（当前 6 处）……这条边界 NEVER 模糊掉：
     一旦到处都是 `switch`，加一个变体要改的点又变成「改不完也看不出漏没漏」」；`:27`
     「**NEVER 往变体里加行为方法**（如 `handle(...)`）」；`Contracted` · `:43`
     「**未知编码与 `null` 也落这里**，这是**刻意保留的既有行为**」。
122. **ADR-D108：收口前的真实分布** —— `pay-sign-server` · `PaymentChannels`（类注释）·
     `.../support/PaymentChannels.java:10`：「收口前「是不是钱包」这个判断在 `src/main` 里被写了 <b>7 遍</b>、
     `WALLET_PAYMENT_VENDOR` 常量有 <b>5 份副本</b>（4 个业务类 + `PayTxnRules`），
     而且**归一化时机三种写法并存**……后果不是不好看：**新增一个渠道要改 7 处，漏一处既不编译失败也不告警**，
     只会让某一条链路把新渠道当成普通签约渠道处理（去支付中心查一个根本不存在的协议）。
     这与 AGENTS.md §2.2.1「多数模块用 String 字面量表达状态，改动状态值 MUST 全局 grep」
     是同一类风险的**活样例**」；`:33`「**对外只暴露 `walletCode()`**，字段本身私有，
     避免又被别处 import 成第 6 份副本」；`:71`「仅给「要把渠道号当值写出去」的地方用……
     判断请一律走 `isWallet(String)`」。
123. **钱包审计流水的第 3 个实参恒为 `walletCode()`** —— `pay-sign-server` ·
     `ContractDomainServiceImpl.auditWalletBindingResult` · `.../service/impl/ContractDomainServiceImpl.java:947`：
     「三处调用逐字相同。<b>NEVER 在这里改成送 request.getPaymentVendor()</b> —— 旧客户端可能送空或送错，
     而这条流水的语义就是「本次被判定为钱包渠道」」。
124. **钱包分支的 `NotFound` 与 `Unreachable` 映射刻意不同** —— `pay-sign-server` ·
     `ContractDomainServiceImpl.queryWalletBindingResult` · `.../service/impl/ContractDomainServiceImpl.java:780`：
     「NotFound 与 Unreachable 的映射刻意不同：前者是「查不到」，对 APP 就是未绑定（0000）；
     后者是「没问到」，MUST 报 9001 让调用方重试。NEVER 把两者合并」；
     `releaseWalletBinding` · `:826`「这里 <b>NEVER 把 `BizRejected` 的 retMsg 换成固定文案</b> ——
     旧客户端依赖它区分「没有这张卡」与「系统故障」」。

### 七、持久层与 mapper

#### 契约与判据

125. **补参 / 装配前的空白串一律收成 `null`** —— `pay-sign-server` · `AccountDomainRpcAdapter.trimToNull` ·
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/port/AccountDomainRpcAdapter.java:163`：
     「<b>NEVER 改成保留原样</b> —— 账户域返回过带空格的 `channel`，`normalizeVendor` 之前的比较会因此错判」；
     `:38`「账户域的成功码，与本模块对外应答复用同一个枚举值，NEVER 写成裸字面量 `"0000"`」；
     `:26`「**本类的每个方法都 NEVER 抛异常**……响应为空按 `RpcOutcome.BizRejected` 处理
     （HTTP 已 2xx，是对端契约问题，重推同一报文不会变好）」。
126. **`initChannelSyncPending` 必须与 `markSuccess` 同事务** —— `pay-sign-server` ·
     `AppTerminationRequestMapper.initChannelSyncPending` ·
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/mapper/AppTerminationRequestMapper.java:254`：
     「WHERE 带 `TERMINATION_STATUS = 'SUCCESS'` 是 CAS，**MUST 在同一个本地事务里、
     紧跟 `updateStatus(SUCCESS)` 之后调用**。返回 0 说明这条不是成功态，不该有通道待清理」；
     XML 侧 `pay-sign-server/src/main/resources/mapper/AppTerminationRequestMapper.xml:384`。
127. **`MANUAL` 是终态，自动流程不得覆盖** —— 同 mapper `updateChannelSyncStatus` · `:265`：
     「自动流程 **NEVER** 覆盖它。返回 0 的正常原因就是这行已被转人工，调用方只记日志、不当失败」；
     XML 侧 `AppTerminationRequestMapper.xml:400`「否则人工刚处理完，下一轮补偿又把它改回 FAILED 继续扫」、
     `:401`「NVL 兜底是必需的：历史行该列为 NULL，直接比较结果为 UNKNOWN，整条 UPDATE 影响 0 行」。
128. **`selectTerminationStatusBySeq` 只在 CAS 返 0 行时调** —— 同 mapper `:146`：
     「**MUST** 只在 CAS 影响 0 行时调用（见 `TerminationStatusTransition` 的约束），
     命中就回查等于给每次成功迁移加一次多余的 DB 往返」；XML 侧 `AppTerminationRequestMapper.xml:273`。
129. **通道清理补偿扫表与通知补偿扫表的三处差别** —— `pay-sign-server` ·
     `AppTerminationRequestMapper.selectCompensableChannelSync` ·
     `pay-sign-server/src/main/resources/mapper/AppTerminationRequestMapper.xml:440`：
     「与 selectCompensableNotify 的三处差别，改动前 MUST 逐条想清楚：①TERMINATION_STATUS 只取 'SUCCESS'
     （不含 FAILED）——解约失败的申请没有通道要删；②状态列显式列举 FAILED 与滞留 PENDING，
     因此 NULL 的历史行自然被排除……③MANUAL 不在白名单，达上限的行不再被捞取」。
130. **`FAIL_REASON` 前置标记 + `INSTR` 幂等闸门，三段 WHERE 缺一不可** —— 同 XML `:348`：
     「三个 WHERE 条件各自的作用，缺一不可：1. REQUEST_SIGN_SEQ 定位到行；2. TERMINATION_STATUS = 'FAILED'
     是 CAS……3. INSTR(...) = 0 是幂等闸门。支付中心会重推同一笔回调……没有这个条件时标记会被反复前置拼接，
     最终把 512 字符的 FAIL_REASON 挤满、原始原因反被截掉」；`:356`
     「MUST 前置标记而不是后置 —— 拼接结果超长时截掉的是尾部，标记本身不能被截」。
131. **`CHANNEL_SYNC_RESULT` 是 `VARCHAR2(1024 CHAR)`，超长直接 `ORA-12899`** —— `pay-sign-server` ·
     `ChannelSyncDeliverer.MAX_RESULT_LENGTH` ·
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/impl/ChannelSyncDeliverer.java:29`。
132. **`reactivateFailed` 的 `NOTIFY_*` 必须一并复位** ——
     `pay-sign-server/src/main/resources/mapper/AppTerminationRequestMapper.xml:250`：
     「NOTIFY_* 一并复位：上一轮已给 APP 发过解约失败通知，不复位则新一轮沿用旧的通知状态与重试轮次
     （可能已是 SUCCESS 或已用尽 3 次），新一轮通知失败后补偿再也扫不到」；`:252`
     「CREATE_TIME 不动（审计口径 = 首次申请时间）；REQUEST_TIME 更新为本次申请时间，
     扫表按 REQUEST_TIME 升序排队，复活的申请应排在队尾而不是插队」。同类「轮次归零」口径见
     `markSuccess`（`.../mapper/AppTerminationRequestMapper.java:115`）与 XML `:193` / `:287` / `:326`。
133. **`REQUEST_TIME` 为空的行本轮不参与是有意的** —— 同 XML `:97`：
     「REQUEST_TIME 理论上非空（申请插入时必填），因此不做 NVL 兜底；若为空则该行本轮不参与，
     这是有意的——凭空放行一条时间不明的申请去解约比漏扫一轮更糟」。

#### 决策理由

134. **审计流水的写入点收口，消掉一条反向依赖** —— `pay-sign-server` · `PaySignAuditLogger`（类注释）·
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/audit/PaySignAuditLogger.java:24`：
     「**NEVER 在本类里加业务判断**。它只做「把已经定好的一行审计流水落库」这一件事」；
     宿主侧 `ContractDomainServiceImpl.auditLogger` · `.../service/impl/ContractDomainServiceImpl.java:103`
     「那两处此前是**反向**调回本类的 `paySignWorkflow.writeLog(...)`，等于把业务类当公共工具库使唤。
     <b>NEVER 把审计流水的写入再搬回本类</b>」；`CallbackDomainServiceImpl:102`
     「<b>NEVER 在本类重建一份 writeLog</b>」。
135. **门面路由直连目标领域，不再层层转发** —— `pay-sign-server` · `PaySignServiceImpl.requestRefund` ·
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/impl/PaySignServiceImpl.java:112`：
     「NEVER 退回「经 PaymentDomainService 转发」—— 那会让支付组重新挂上退款入口，
     而支付组一行退款逻辑都没有了」；`receivePayResult` · `:125`
     「NEVER 退回「经 CallbackDomainService 转发」—— 那会让回调组反向依赖支付组」；
     接口侧同一条在 `CallbackDomainService` · `.../service/CallbackDomainService.java:13`
     与 `PaymentDomainService` · `.../service/PaymentDomainService.java:24`
     「**NEVER 把状态回写逻辑复制回回调领域。**」、`:12`「**NEVER 在本接口上加回退款方法**」。
136. **`pay.sign.*-url` 是 7 条完整 URL，不得退回拼接** —— `pay-sign-server` ·
     `PaySignProperties.contractUrl` 等 ·
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/config/PaySignProperties.java:15`：
     「NEVER 退回「基础地址 + 相对路径拼接」：路径本身带版本号（`/api/v1/...`），拆成两半后漏 `/v1`
     不会报 404、而是返回 `code=600 操作失败`，极难定位。2026-08-26 已因此导致免密扣款长期零成功。
     改动前 MUST 用只读接口 `/api/v1/contract/queryResult` 实测」；`:20`
     「不设默认值：地址随环境变化，缺配置时由 PayGatewayClient 直接抛异常暴露，比静默打到写死的默认域名安全」。

#### 陷阱

137. **Druid WallFilter：SQL 正文不得有注释；XML 注释不得有连续减号** —— 两处原文告示见
     `pay-sign-server/src/main/resources/mapper/PayTxnDetailMapper.xml:153` / `:214` 与
     `pay-sign-server/src/main/resources/mapper/PayCallbackLogMapper.xml:43`（条款原文见本节第 79 条）。
138. **`PAY_CALLBACK_LOG` 的定位取 `MAX(ID)`，依赖序列单调递增** ——
     `pay-sign-server/src/main/resources/mapper/PayCallbackLogMapper.xml:56`：
     「定位到最近一条：ID 取自 SEQ_PAY_CALLBACK_LOG，单调递增，MAX(ID) 即本次插入的那条。
     HANDLE_STATUS 为 VARCHAR2(32)、HANDLE_MSG 为 VARCHAR2(1024)，超长由调用方截断」。
139. **`updatePayCallback` 的 `NVL` 是重推防抹除的唯一防线** ——
     `pay-sign-server/src/main/resources/mapper/PayTxnDetailMapper.xml:138`：
     「优惠字段和金额字段在回调中通常为空，使用 NVL 保留入库时的原始值」；服务侧同一条在
     `.../service/impl/PaymentDomainServiceImpl.java:363`「所有会被稀疏报文覆盖的列在 SQL 里都套了 NVL，
     重推缺字段不会把首推写好的值抹成 NULL」。
140. **未知状态卡住的行是有意留的人工出口** —— `PayTxnDetailMapper.xml:172`：
     「若某行被历史数据或支付中心透传的未知状态卡住，会稳定命中 0 行并触发调用方的 ERROR 日志与
     MANUAL 标记，这是有意留的人工出口」。

### 墓碑注释清单（建议转为断言测试）

> 这些注释的正文价值是「禁止某件已被删掉的事情重新出现」，放在源码里只能靠人读。
> 下表给出每条想禁止的具体事情，以及能否写成 grep 类断言（`grep` 断言 MUST 排除注释行，
> 否则会被这些注释自身命中 —— 这正是 AGENTS.md §5.2 记过的「据 grep 计数判断已经回退」那类误判）。

| # | 文件:行号 | 想禁止的具体事情 | 可否 grep 断言 |
|---|---|---|---|
| T1 | `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/support/PayTxnRules.java:36` | 在 `PayTxnRules` 里重新声明 `WALLET_PAYMENT_VENDOR` | 可：`src/main` 下 `private static final String WALLET` 只允许出现在 `PaymentChannels.java`；`WALLET_PAYMENT_VENDOR` 的**声明**行数 = 0 |
| T2 | `.../service/impl/ContractDomainServiceImpl.java:87` | 同 T1（签约域）；也禁止直接写 `PaymentVendorEnum.WALLET.getCode()` 或字面量 `"0B"` | 可：`src/main` 非注释行里 `PaymentVendorEnum.WALLET.getCode()` 只允许出现在 `PaymentChannels.java`；字面量 `"0B"` 同理 |
| T3 | `.../service/impl/CallbackDomainServiceImpl.java:91` | 同 T2（回调域） | 可，同 T2 |
| T4 | `.../service/impl/TerminationExecutor.java:61` | 同 T2（解约执行） | 可，同 T2 |
| T5 | `.../service/impl/ContractDomainServiceImpl.java:111` | 把 `AccountClient` 加回签约域（新增账户域调用一律走 `AccountDomainPort`） | 可：`AccountClient` 只允许出现在 `port/AccountDomainRpcAdapter.java` |
| T6 | `.../service/impl/PaymentDomainServiceImpl.java:91` | 把 `AccountClient` 加回支付域 | 可，同 T5 |
| T7 | `.../service/impl/TerminationExecutor.java:83` | 在解约执行类注入 `AccountClient` | 可，同 T5 |
| T8 | `.../service/impl/ContractDomainServiceImpl.java:124` | 把 `PaySignGateway` / `PaySignProperties` 加回签约域（URL 只允许在 `ContractGatewayAdapter`） | 可：`PaySignProperties` 只允许出现在 `config/`、`client/PayGatewayClient.java`、`port/ContractGatewayAdapter.java` |
| T9 | `.../service/impl/ContractDomainServiceImpl.java:63`、`.../service/impl/TerminationProcessor.java:107` | 重新引用已删除的 `PaySignWorkflow`（含它的 `writeLog` / `isGatewaySuccess` 薄壳） | 部分：可断言 `src/main` 下不存在 `class PaySignWorkflow` 与标识符 `paySignWorkflow`；但注释里有 27 处该字样，**断言 MUST 排除注释** |
| T10 | `.../service/impl/PaymentDomainServiceImpl.java:67` | 重建 `requestGatewaySimple` / `isGatewaySuccess` / `isAlreadyPaidSuccess` / `gatewayErrorMsg` 四个私有薄壳 | 可：这四个名字的**方法声明**只允许出现在 `support/PaySignGateway.java` / `client/PayGatewayClient.java` |
| T11 | `.../service/impl/PaymentDomainServiceImpl.java:56`、`.../service/PaymentDomainService.java:12` | 把退款逻辑 / 退款方法加回支付域 | 可：`PaymentDomainServiceImpl.java` 与 `PaymentDomainService.java` 非注释行里不得出现 `Refund` |
| T12 | `.../service/impl/CallbackDomainServiceImpl.java:62`、`.../service/CallbackDomainService.java:13` | 在回调域加回 `receivePayResult` 转发层 | 可：两个文件非注释行里不得出现 `receivePayResult` |
| T13 | `.../service/AppNotifyService.java:42` | 在本模块重新引入服务内 `@Scheduled` 驱动 | 可：`src/main` 下行首锚定的 `@Scheduled` 计数 = 0（当前该字样只在注释里，共 9 处） |
| T14 | `.../service/impl/TerminationInternalServiceImpl.java:47`、`:60` | 把三个扫表补偿方法体、或 `paySignInfoMapper` / `contractDomainService` / `paySignGateway` / `auditLogger` / `accountDomainPort` 加回门面 | 可：该文件非注释行里不得出现这五个标识符；三个补偿方法体各只有一行委托 |
| T15 | `.../mapper/PaySignInfoMapper.java:61`、`pay-sign-server/src/main/resources/mapper/PaySignInfoMapper.xml:155` | 恢复被注释掉的 `upsert` / `MERGE INTO APP_PAY_SIGN_INFO`（状态迁移必须走 4 条 CAS） | 可：mapper 接口不得声明 `upsert(`；XML 里 `MERGE INTO` 不得出现在注释之外 |
| T16 | `.../service/impl/ContractDomainServiceImpl.java:181` | 恢复钱包渠道在 `requestSignInfo` 的短路返回 | 部分：可断言该方法内不出现旧文案「钱包支付不走 requestSignInfo」；行为回退更适合用特征测试钉 |
| T17 | `.../service/impl/ContractDomainServiceImpl.java:288` | 把别的方法（`requestTermination`）的论证复制回 `alipayTripRequestSignInfo` | 否：属注释卫生，建议改为评审检查项 |
| T18 | `.../support/PaySignResponses.java:37` | 退回「三种行为各收口成一个私有 helper」的说法（与代码不符，已改正） | 否：自我更正记录，建议随「真要抽 helper 单独一版」那条一起转为评审项 |
| T19 | `.../port/AccountDomainPort.java:22` | 回退成「端口只收写、不收读」 | 可：`AccountDomainPort` 必须声明 `queryUser` 与 `queryPayChannelByContract` 两个读方法 |
| T20 | `.../entity/PaySignRequest.java:19` | 让通知业务字段重新依赖已删除的 `PaySignInfo` | 可：`PaySignRequest.java` 不得 import `PaySignInfo` |
| T21 | `.../service/impl/CallbackDomainServiceImpl.java:230` | 在 `receiveSignResult` 里恢复那次 `selectByRequestSignSeq` 回填自增主键 | 否：同名方法在本类另有两处**正当**调用（`resolveThirdUserId` / `resolveDisplayAccount`），纯 grep 会误判，需按方法体断言 |
| T22 | `.../service/impl/PaySignServiceImpl.java:112`、`:125` | 让门面退回「经 `PaymentDomainService` / `CallbackDomainService` 转发」 | 可：`PaySignServiceImpl.requestRefund` 必须直接调 `refundDomainService`、`receivePayResult` 必须直接调 `paymentDomainService` |

（本节完。抽取口径与统计见本节开头说明；后续阶段若继续抽取其他模块，MUST 另起小节，NEVER 改写本节已归档条款。）

## 附：pay-sign-server 源码注释知识抽取（2026-09-16，阶段二）

> **本节只补漏。** 阶段一（上一节）已归档 140 条 + 22 条墓碑，本节**一条都不重复、不改写、不删除**；
> 编号从 **141** 起、墓碑从 **T23** 起，与阶段一连续。抽取范围是阶段一未覆盖的文件与段落：
> controller 全部 9 个类、`config/**`、`port/` 的扣款与退款方向（ADR-D113 续）、service 接口层、
> `util/RSASignUtils`、entity / model DTO，以及 `resources/` 下的
> `application.properties`（24 行注释）、`log4j2-paysign.xml`（含 7 个块注释）、`sql/*.sql`（56 行）。
> **行号是 2026-09-16 抽取时刻的快照，且该模块仍在被并发改动**：本轮就发现
> ADR-D115 / ADR-D115 续（二）已把阶段一第 22 条的结论改掉（见「矛盾与待裁决」M3），
> 因此引用某条前 **MUST 先 grep 现查行号**，NEVER 直接按本节行号跳转。
> 纯 `@param` / `@return` / getter-setter 样板不抽，只统计行数（见「本轮覆盖率自评」）。
> 涉及密钥的条目**只记键名与位置、NEVER 回显值**（AGENTS.md §5.2）。

### 一、controller 层（对外入口 / 内部补偿端点 / 全局异常）

#### 契约与判据

141. **10 个 `/internal/**` 端点的真实 URL 与前缀归属**（AGENTS.md §2.2.1 只给了数量，这里是逐条 URL）——
     解约 6 条在 `pay-sign-server` · `TerminationInternalController`（类级 `@RequestMapping("/internal/termination")`，
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/controller/TerminationInternalController.java:23`）：
     `/process`（`:49`）、`/compensateNotify`（`:63`）、`/compensateChannelSync`（`:80`）、
     `/checkFailedOrders`（`:89`）、`/execute`（`:101`）、`/notifyFailed`（`:110`）；
     签约 2 条在 `PaySignInternalController`（`.../controller/PaySignInternalController.java:18` 前缀 `/internal/paySign`）：
     `/compensateNotify`（`:46`）、`/resendNotify`（`:67`）；退款 2 条在 `PaymentInternalController`
     （`.../controller/PaymentInternalController.java:26` 前缀 `/internal/payment`）：
     `/compensateRefundQuery`（`:63`）、`/compensateRefundSummary`（`:92`）。
     **两个前缀下各有一个同名 `/compensateNotify`、只差前缀** —— 阶段一第 39 / 103 条那句「NEVER 合并」
     指的就是这两条，排查时 **MUST 带上前缀**，NEVER 只写方法名。
142. **`/internal/termination/process` 的入参可省略，`delayDays` 缺省 4** ——
     `TerminationInternalController.java:44`：「入参可省略（不传 body 即历史行为，不按申请时间过滤）。传
     `referenceTime` / `delayDays` 时只处理 `REQUEST_TIME <= referenceTime - delayDays` 的记录，
     用于「解约申请满 N 天才确认」的业务口径；delayDays 缺省取配置 `termination.confirm-delay-days`（默认 4）」。
     配置真值在 `pay-sign-server/src/main/resources/application.properties:57`（`termination.confirm-delay-days=4`），
     即 T+4，与本文件正文的解约链路一致。
143. **补偿端点返回的 `submitted` 不是投递结果** —— `TerminationInternalController.java:60`
     「重发是异步的，通知是否成功以 `APP_TERMINATION_REQUEST` 的 NOTIFY_STATUS / NOTIFY_RESULT 为准，
     不要用返回的 submitted 判断结果」；通道清理侧 `:77`「清理结果以 CHANNEL_SYNC_STATUS /
     CHANNEL_SYNC_RESULT 为准」；签约侧同一条在 `PaySignInternalController.java:43`（以
     `APP_PAY_SIGN_REQUEST` 的两列为准）。**唯一例外是单条重发**：`PaySignInternalController.java:60`
     「投递是同步的，返回体的 notified 即真实结果」。

144. **通道清理补偿刻意不捞 `NULL` 与 `MANUAL`** —— `TerminationInternalController.java:73`：
     「扫的是 `TERMINATION_STATUS='SUCCESS'` 且 `CHANNEL_SYNC_STATUS` 为 FAILED / 超时 PENDING 的记录；
     `CHANNEL_SYNC_STATUS` 为 NULL（历史数据）与 MANUAL（账户域已明确拒绝、需人工）刻意不捞」；
     接口侧 `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/TerminationInternalService.java:42`~`:44`
     「`NULL`（改造前的历史行）与 `MANUAL`（人工介入态）都**刻意扫不到**，理由见 `docs/domain/outbox.md` 的四个坑」，
     `:49`「调用方是 web-admin 的 Quartz 任务，**本模块 NEVER 加 `@Scheduled`**」。
145. **IF8A-75 与 IF8A-36 的分工、以及销户侧已有 `8023` 拦截** ——
     `service/TerminationInternalService.java:67`~`:74`：「IF8A-75 直接解绑支付方式：APP 侧入口，
     立即向支付渠道发起解绑，不等账期结束的定时任务」「与 IF8A-36 `requestTermination` 的区别是后者只登记
     PENDING、真正发起要等 web-admin 的 `TerminationQuartzTask` 扫表；本接口内部直接复用
     `executeTermination`（登记缺失时按签约记录补建 → CAS 置 SCANNING → 立即调支付渠道）」
     「**不校验未结清欠费**（用户 2026-09-08 裁决）……**IF8A-42 销户入口已有 `8023` 拦截，此处不重复拦**」。
     阶段一第 34 条只记了前半段，`8023` 与触发方类名 `TerminationQuartzTask` 是本轮新增。
146. **九个 Controller 的类级前缀与真实端点** —— `/ci/app`（`controller/ci/app/PaySignAppController.java:28`，
     12 个 POST）、同前缀下的 `PaySignController`（`requestSignInfo` `:31`、`querySignInfoBySeq` `:38`，
     **本模块唯一的 `@GetMapping`**）、`/app`（`controller/app/PaySignNotifyController.java:15`，
     只有 `/receiveSignResult`）、`/ticket`（`controller/ticket/TerminationNotifyController.java:15`，
     只有 `/receiveTerminationResultFromItp`）、`/channel`（`controller/channel/PaySignAlipayTripController.java:27`，
     `requestContractAdvisory` / `requestContractResult` / `requestTermination` / `addContract` 四条）、
     `/notify`（`controller/notify/PaySignAlipayTripNotifyController.java:22`，两条预留），
     外加三个 `/internal/**`（见第 141 条）。**同一个业务动作在三个前缀下各有一份入口**（如
     `requestTermination` 同时在 `/ci/app` 与 `/channel` 下），定位实现 MUST 带前缀。
147. **`queryPayTxnBatch` 的 `@PostMapping` 少一个前导斜杠** —— `PaySignAppController.java:154` 写的是
     `@PostMapping("queryPayTxnBatch")`，其余 11 个都带 `/`。Spring 会补齐、运行时行为一致，
     但**按 `"/queryPayTxnBatch"` 字符串 grep 会漏掉这一处**。
148. **支付宝出行的 `/notify/**` 两条是预留、当前不启用** ——
     `controller/notify/PaySignAlipayTripNotifyController.java:18`「当前支付宝采用同步确认模式，
     以下接口为预留，暂不启用」，`:40` / `:51`「若后续支付平台定义支付宝签约 / 解约结果回调，可启用此接口」。
     与阶段一第 11 条（同步确认分支 NEVER 写通知列）是同一事实的两面。
149. **签约渠道固定值写在 Controller 层，不在 service** —— `PaySignAppController.java:49` / `:56` / `:63` /
     `:75` / `:120` 五处逐字相同的「地铁APP专属入口，固定签约渠道为 METRO_APP」，`PaySignController.java:34` 同；
     `PaySignAlipayTripController.java:45` / `:52` / `:59` / `:73` 四处「支付宝出行专属入口，固定签约渠道为 ALIPAY」。
     取值定义在 `constant/SignChannelEnum.java`。**改渠道来源 MUST 同时扫这 10 处注释与赋值**。
150. **ADR-D96 的构造注入告示在 controller 层另有 8 份逐字副本** ——
     `PaySignAppController:37`、`PaySignController:23`、`PaySignNotifyController:22`、
     `TerminationNotifyController:22`、`PaySignAlipayTripController:34`、
     `PaySignAlipayTripNotifyController:29`、`PaySignInternalController:26`、`TerminationInternalController:31`：
     「字段 `final` ⇒ 对象一建成即完备，且夹具漏注 / 多注一个协作者会**编译失败**……
     <b>NEVER 退回 `@Autowired` 字段注入。</b>」（阶段一第 18 条列的是 service / mapper 侧那 15 处。）
151. **全局异常处理器只兜两类异常** —— `controller/PaySignExceptionHandler.java:15`
     「统一处理 `PayGatewayException`，避免调用方收到模糊的 500 错误」；`:38`「解约流程异常。
     触发事务回滚后，向调用方（含支付平台回调）返回 9999，便于重试」（**这句已与代码不符，见 M7**）。
     其余异常仍落到 `resource/micro/web` 的全局处理器 → UUID `retCode`（AGENTS.md §8 那条五成因）。

### 二、config 与 resources（配置 / 日志 / SQL 脚本）

#### 契约与判据

152. **`pay.sign.*-url` 在仓库里实际是 9 条网关 URL + 2 条通知地址** ——
     `pay-sign-server/src/main/resources/application.properties:81`~`:88` 八条
     （`contract-config-url` / `contract-url` / `contract-advisory-url` / `contract-result-url` /
     `termination-url` / `request-pay-url` / `pay-query-url` / `request-refund-url`）+ `:92`
     `refund-query-url`，另有 `:93` `default-notify-url` 与 `:94` `request-pay-notify-url`
     （两者都指向 `58.56.166.170:48000`，即公网入向网关，见 AGENTS.md §8 那条链路）。
     `PaySignProperties` 里还有 `wechatEntrustUrl`（`config/PaySignProperties.java:54`，硬编码微信
     `papay/entrustweb`）与 `alipayAppId` / `alipayMerchantAppId` / `wechatAppId` 三个硬编码默认值（`:51`~`:53`）。
     **「7 条」是 2026-08-26 那批的口径，已过期，见 M1。**
153. **`pay-query-url` 只读、只服务「拉黑前二次确认」** —— `config/PaySignProperties.java:29`
     「支付查询（网关文档 §1.2 payQuery），只读接口，用于在拉黑前二次确认支付中心侧的真实状态」
     （阶段一第 66 条记的是调用点判据，这里补配置侧的用途声明）。
154. **`refund-query-url` 的 bizData 描述与实现不一致，且该地址尚未实测** ——
     `config/PaySignProperties.java:33`~`:46`：「退款查询（网关文档 §3.2 refundQuery，
     `docs/external/支付中心网关接口文档.md:342~368`），只读接口」「bizData 按原文
     「refundOrderNo 和 merchantRefundNo 至少填一个」，我方填 `refundOrderNo`」
     「**该地址尚未对真实网关实测**：路径取自规格原文、域名与 `/ngpayment-gateway/api/v1` 前缀与其余 8 条同源」
     「不设默认值：缺配置时由 `compensateRefundQuery` 打 ERROR 并跳过本轮，比静默打到一个拼错的地址安全」。
     **「我方填 refundOrderNo」这句已过期**（实现是两个键都送，ADR-D92），见 M2。
     同一段告示的 properties 副本在 `application.properties:89`~`:91`。
155. **traceId 三行成组的**逐条理由**只写在本模块的 properties 里** ——
     `application.properties:15`~`:27`：「traceId 关联：micro/web 默认 `management.tracing.enabled=false`，
     此处仅为 pay-sign 打开」「只需要 MDC 里的 traceId/spanId 供 VictoriaLogs 日志检索，span 上报一律不要」
     「**NEVER 删除下面这行排除**：1) `sampling.probability=0` 只让本服务发起的 trace 不采样，
     采样器是 `parentBased(traceIdRatioBased(0))`，**上游带 `sampled=1` 的 traceparent / b3 头进来时
     span 仍会被采样并进导出队列**；2) micro/web 的 `web.properties` 已把
     `management.otlp.tracing.endpoint` 整行注释掉，本行是第二道保险 —— K8s Deployment 只要注入
     `MANAGEMENT_OTLP_TRACING_ENDPOINT` env 就会重新激活 exporter；3) **Boot 3.2.6 没有
     `management.tracing.export.enabled` 这个开关**（更高版本才有），把 endpoint 置成空值也不行
     （`OtlpAutoConfiguration` 只判断键是否存在），只能排掉整个自动配置」
     「排掉后 Tracer 与 MDC 的 traceId/spanId 照旧（`micrometer-tracing-bridge-otel` 提供），
     仅不再创建 SpanExporter」「不需要排 `ZipkinAutoConfiguration`：`opentelemetry-exporter-zipkin`
     在 micro/web 里是 optional，未传递到本模块」。**AGENTS.md §2.2.1 只说「三行成组」，
     这 5 条为什么是本轮唯一记载处。**
156. **本模块不走公共 log4j2，且该键非空会跳过运行期重载** —— `application.properties:9`~`:13`：
     「日志：使用本模块自带配置（含 VictoriaLogs appender），不走 micro/web 的 `log4j2-linux.xml`。
     该键非空会让 `CustomLoggingConfiguration` 跳过运行期重载，配置只影响 pay-sign」
     「VictoriaLogs 推送地址由环境变量 `VLOGS_URL` 注入（log4j2 读不到 Spring 配置项，只能读 env/sysprop）。
     未注入时 appender 自动禁用，文件与控制台日志不受影响」。
     这解释了 AGENTS.md §7 那条「`Tomcat started` 搜不到」在本模块的表现差异。

157. **VictoriaLogs appender 的端口是 30032、不是 9428** ——
     `pay-sign-server/src/main/resources/log4j2-paysign.xml:73`~`:87`：「url 为空时 appender 自动禁用
     （不起线程、不丢日志到别处），因此默认不配真实地址：由 K8s Deployment 注入 `VLOGS_URL`，
     当前测试集群实测可用的值是 `http://victoria-logs-pbi6a-svc.itp.svc:30032`。
     **注意端口是 30032 而不是 VictoriaLogs 默认的 9428**：该 Service 的 port 与 nodePort 都是 30032，
     targetPort 才是 9428（2026-09-07 实测 `kubectl get svc`），写 9428 会连不上」
     「只给 `scheme://host:port` 时代码会补 `/insert/jsonline?_stream_fields=app,host`。
     stream 字段只能是 app/host 这类低基数字段，traceId 走正文（LogsQL 仍可 `traceId:"xxx"` 检索）」
     「字段由 `victorialogs-event-template.json` 决定（在 micro/web 里）：`_time` / `_msg` / `level` /
     `logger` / `thread` / `traceId` / `spanId`。MDC 只取 traceId 与 spanId 两个白名单键 ——
     **`FirstFilter` 会把全部请求头塞进 MDC（含 `authorization`），NEVER 在 template 里改成全量输出**」。
158. **`X-Vlogs-Capture: 1` 是「按链路全量上报」的开关，键名 MUST 小写** ——
     `log4j2-paysign.xml:91`~`:97`：「两级过滤，`CompositeFilter` 遇到 ACCEPT / DENY 立即短路：
     1. 请求头带 `X-Vlogs-Capture: 1` 时（web-admin 的 `TerminationQuartzTask.traceHeaders` 加的），
     micro 的 `FirstFilter` 会把它小写后放进 MDC，**键是 `x-vlogs-capture`** —— 命中即 ACCEPT，
     该 traceId 链路上的日志**全量**上报，含 DEBUG。**NEVER 把 key 写成驼峰**。
     2. 未命中落到 `ThresholdFilter`：只有 WARN 及以上才上报，与改动前口径一致」。
159. **`VLOGS` 这个 logger 名是「关键链路埋点」的专用通道** —— `log4j2-paysign.xml:130`~`:135`：
     「关键链路埋点专用 logger：这里的日志**全量**进 VictoriaLogs（不受 Root 的 WARN 阈值限制）。
     用法：`private static final Logger vlogs = LoggerFactory.getLogger("VLOGS");`
     `vlogs.info("解约确认, orderNo={}, status={}", orderNo, status);`
     `additivity=false` 保证不再重复走 Root，避免同一条日志推两遍」。
160. **业务包提到 DEBUG 的唯一目的是「让 DEBUG 事件被生成」** —— `log4j2-paysign.xml:142`~`:150`：
     「业务包（含 mapper 包，MyBatis 的 SQL 日志 logger 名就是 mapper 接口全限定名）提到 DEBUG，
     目的只有一个：让 DEBUG 事件被**生成**，否则带 `X-Vlogs-Capture` 标记的链路也无 DEBUG 可采。
     Root 保持 INFO，不动 Spring / Druid / Netty 等框架日志……注意
     `com.chinasofti.huateng.log4j2.CheckLoggerHealth` 有更精确的 logger 配置（上面 INFO + `additivity=false`），
     不受本段影响，仍只写 tempFile」；Root 侧 `:160`~`:164`「Root 只把 WARN 及以上推给 VictoriaLogs：
     INFO 全量上报会把支付链路的报文日志（`other.web.enableLogRequestInFilter=true`、
     `logResponseMaxSize=20000`）整体搬进日志后端，量与敏感面都不可控。需要单条上报走上面的 VLOGS logger」。
     另两处：`:26`~`:31`「`ThresholdFilter` 保证本地文件只留 INFO 及以上……`ErrorInterceptorFilter`
     四个 `filter()` 重载全部 return NEUTRAL（只计数、不裁决），放在前面不会短路后面的 ThresholdFilter」；
     `:67`「micro 的 Console 原本不带 traceId，这里补上，便于 `kubectl logs` 与 VictoriaLogs 对照」。
161. **本文件由 Boot 在启动早期加载，所有 lookup MUST 自带默认值** —— `log4j2-paysign.xml:2`~`:11`：
     「为什么不改 `resource/micro/web` 的 `log4j2-linux.xml`：那份配置被 **21 个模块共用**，改一次全量生效。
     这里通过 `application.properties` 的 `logging.config` 指向本文件，`CustomLoggingConfiguration`
     会在第 38 行直接 return，micro 的默认配置不参与，改动范围收敛在 pay-sign 内」
     「注意：本文件由 Spring Boot 在启动早期加载，此时 `CustomLoggingConfiguration` 尚未设置
     `webLoggerLevel` / `logPath` / `appName` 等系统属性，因此**全部 lookup 都必须自带默认值**」。
162. **异步通知线程池必须手动透传 MDC** —— `config/PaySignExecutorConfig.java:55`~`:58`：
     「把提交线程的 MDC（含 traceId / spanId）透传到异步线程。线程池会复用线程，靠
     `InheritableThreadLocal` 只在建线程时继承一次，**必须在每个任务前后显式设置与清理**」。
     池参数在 `application.properties:62`~`:65`（core 4 / max 16 / queue 1000 / 前缀 `app-notify-`）；
     另有 `config/VisibleThreadPoolTaskExecutor.java:11`「显示线程池执行情况信息」（只打指标、无业务）。

#### 陷阱

163. **`pay-txn-schema.sql` 的四条前提写在文件头** ——
     `pay-sign-server/src/main/resources/sql/pay-txn-schema.sql:1`~`:4`：「支付交易明细表」
     「Oracle 月分区表，**`TXN_DATE` 使用字符串 `YYYYMMDD` 入库**」
     「本脚本保存支付侧完整明细，**`GATE_TXN_PAY.ORDER_NO = PAY_TXN_DETAIL.ORDER_NO`**」
     「**重试最大次数不入库**，后续由配置文件控制」。第二条正是阶段一第 97 条那个「`TXN_DATE` 畸形行
     永远扫不到」盲区的**成因**（字符串比较）；第三条是跨模块关联键的唯一权威声明。
     另两处表头：`:139`~`:140`（`PAY_REFUND_DETAIL`「一笔支付订单可以有多笔退款，退款结果回写
     `PAY_TXN_DETAIL.REFUND_STATUS` / `REFUND_AMOUNT`」）、`:224`~`:225`（`PAY_CALLBACK_LOG`
     「每次回调插入一条，避免重复回调覆盖原文」）。
164. **月分区维护与「待支付重试查询」是注释里的现成模板** ——
     `sql/pay-txn-schema.sql:296`~`:320` 给了三张表 `SPLIT PARTITION P_MAX AT ('20270201')` 的样例
     （**三张表都要各切一次**，分区名 `P202701`）；`:322`~`:329` 给了待重试扫表 SQL 样例
     （`PAY_STATUS IN ('FAIL','RETRY')` + `REQUEST_COUNT < :maxRequestCount` +
     `NEXT_REQUEST_TIME IS NULL OR <= SYSTIMESTAMP` + `ORDER BY NEXT_REQUEST_TIME NULLS FIRST, UPDATE_TIME`）。
     **注意这段 SQL 只是注释里的模板、不是在跑的代码**（本模块支付侧没有扫表重试任务，
     重试由支付中心重推 + 回调驱动，见阶段一第 67 条）；它与阶段一第 98 条那条「`IS NULL OR 到点`」
     的写法要求一致，可作为新写扫表 SQL 的起点。
165. **`pay-sign-channel-sync-migration.sql` 的头部注释是 ADR-D48 的「执行顺序」告示** ——
     `sql/pay-sign-channel-sync-migration.sql:1`~`:10`：「为 `APP_TERMINATION_REQUEST` 增加
     「解约成功 → 账户域清理支付通道」的可靠投递状态列」「列名与同表的 `NOTIFY_*` 一组对称，
     语义也照抄：状态 + 重试次数 + 时间 + 结果」「详见 `docs/domain/decisions.md` ADR-D8 第一处」
     「**NEVER 只执行本脚本就改 Java**：本组列是「拆事务边界 + 补偿端点 + 工单」三件事的前置，
     单独把 RPC 移出事务反而比现状更糟（本地提交后 `TERMINATION_STATUS=SUCCESS`，
     上游重推在幂等短路处返回成功，通道永远删不掉）。执行顺序见 ADR-D8」。
     **注意 `:2`~`:4` 描述的现状（「目前在 `@Transactional` 内调 `removeAccountPayChannel`」）
     早已被 ADR-D48 改掉，且那个类名已删除** —— 属脚本注释滞后，见 M7 同型。
166. **`pay-sign-txn-transin-migration.sql` 的「关联修改」清单点名了已删除的类** ——
     `sql/pay-sign-txn-transin-migration.sql:1`~`:7`：「为 `PAY_TXN_DETAIL` 表添加 `TRANS_IN` 字段，
     用于保存支付平台返回的入账账户/商户号」「关联修改：`PayTxnDetail.java`、`PayTxnDetailMapper.xml`、
     **`PaySignWorkflow.java`**」「添加列（允许为空，历史数据无需回刷）」。
     最后那个类名已于 2026-09-15 删除（阶段一 T9），**NEVER 据这行去找那个类**，见 T32。

### 三、port 层：扣款与退款方向（ADR-D113 续，阶段一只覆盖了签约方向）

#### 契约与判据

167. **扣款方向的端口刻意留着 `PaySignProperties`** —— `pay-sign-server` · `PaymentGatewayPort`（类注释）·
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/port/PaymentGatewayPort.java:6`~`:13`：
     「支付域看**支付中心免密扣款方向**的窄接口（防腐层，2026-09-16，ADR-D113 续）」
     「与 `ContractGatewayPort` 同一条路子：URL、报文装配、应答判读收进 `PaymentGatewayAdapter` 一处，
     领域服务不再注 `PaySignGateway`。`PaySignProperties` 仍留在 `PaymentDomainServiceImpl`，
     因为那里还要读支付回调地址（`getRequestPayNotifyUrl` / `getDefaultNotifyUrl`）——
     **这是预期的，NEVER 为了「凑齐 0 个 properties」把它也搬进端口**」
     「（原文写的理由是 `getTestForceAmount`，该测试开关已于 2026-09-16 删除，ADR-D115 续（二）。）」
     —— 最后这句是**注释自己记的自我更正**，可作为「注释与被删配置项同步维护」的样例。
168. **`payQuery` 的 URL 未配置时返回 `Rejected`、不抛异常** —— 同接口 `:25`~`:28`：
     「支付 API 查询支付状态，**只用于「拉黑前二次确认」**」「**URL 未配置时返回 `GatewayReply.Rejected`
     （并在实现内打 ERROR）**，调用点据此按「不拉黑」处理 —— 与收口前逐字同义」。
     这条与阶段一第 66 条（拉黑前 MUST 二次确认）是同一判据的端口侧落点。

169. **扣款方向的判定顺序与回调地址回落级数** —— `pay-sign-server` · `PaymentGatewayAdapter` ·
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/port/PaymentGatewayAdapter.java:47`：
     「判定顺序 **MUST 是「先看成功码、再看幂等措辞」**：`isAlreadyPaidSuccess` 只在非成功码下才有意义」；
     `:70`~`:75`「支付回调地址：报文透传值优先，其次支付专用配置……由
     `PaymentDomainServiceImpl.resolvePayNotifyUrl` 原样搬入。**注意它只有两级，与签约方向的三级回落不同，
     NEVER 互相看齐**」（签约方向那条三级回落见阶段一第 14 条 `ContractGatewayAdapter:88`）。
170. **`PaymentReply` 有三个变体、`GatewayReply` 只有两个，NEVER 合并** —— `pay-sign-server` ·
     `PaymentReply`（类注释）· `.../port/PaymentReply.java:10`~`:14`：
     「**为什么不复用 `GatewayReply`**：扣款方向有**第三种**结局 —— `AlreadyPaid`（网关答 `code=9999`
     但 msg 说「已支付成功 / 请勿重复支付」，业务上其实成功）。把它塞进 `GatewayReply` 的两分类，
     会让签约方向那 **4 处** `instanceof Rejected` 的语义**悄悄变化**：一笔已扣款成功的交易会落进 Rejected。
     **NEVER 合并这两个类型**；端口按用例设计，回复类型也按用例设计」；`:22`~`:28`
     「幂等成功：`code=9999` + 措辞命中「已支付成功」/「请勿重复支付」。判定口径由
     `PaySignGateway.isAlreadyPaidSuccess` 独占……**NEVER 在别处重写一份**」；
     `:48`「仅用于回填应答的 `success` 字段，**NEVER 拿它做分支判断**（分支 MUST 用模式匹配）」。
171. **退款方向的 bizData 刻意留在调用点组装** —— `pay-sign-server` · `RefundGatewayPort`（类注释）·
     `.../port/RefundGatewayPort.java:11`~`:16`：「**bizData 刻意仍由调用点组装并传入**，与签约方向不同。
     理由不是偷懒：`requestRefund` 的 bizData 要在出网**之前**落进 `PAY_REFUND_DETAIL.REQUEST_BODY`
     （「留痕 → 出网」那条不变量的物证），**它是业务证据链的一部分、不是出向细节**；退款回查那份 bizData
     同理带着「两个号都送」的实测结论注释（ADR-D92）。**NEVER 为了「对称好看」把它们搬进 adapter**——
     那会让落库的报文与真正发出的报文变成两处各自装配，正是本轮要消灭的形态」；
     `:26`~`:32`「退款查询地址是否已配置……补偿入口在扫表**之前**要据此整批短路（未配置时停在 PROCESSING
     的退款单本轮无人收口，MUST 打 ERROR 并返错，NEVER 静默继续）。**暴露一个布尔而不是 URL 本身**：
     让「URL 是什么」始终只有 adapter 知道」。
172. **退款 adapter 只做判读、不改报文** —— `.../port/RefundGatewayAdapter.java:14`~`:16`：
     「**退款查询的字段名有实测结论，改动 MUST 先读 ADR-D92**：网关只认 `merchantRefundNo`，
     只送 `refundOrderNo` 时返 9999「退款流水号或商户退款流水号必填」，表现是退款单永久空转而端点每轮返 0000。
     **那条约束落在调用点组装的 bizData 里，本类不改报文**」；`:44`「成功码判定的唯一入口，
     NEVER 在本类重写一份（同 `ContractGatewayAdapter#judge`）」。
173. **两个领域服务的协作者数量是「收口是否完成」的度量** ——
     `.../service/impl/PaymentDomainServiceImpl.java:98`~`:105`「本类此前直接注 `PaySignGateway`，
     于是「取哪个 URL、组哪份 bizData、怎么判成功（含 `isAlreadyPaidSuccess` 这条措辞约定）」散在两个出向点上。
     **NEVER 把 `PaySignGateway` 加回本类**」+「`paySignProperties` 仍留着：本类还要读
     **7 条 `pay.sign.*-url` 之外**的支付回调地址」；`.../service/impl/RefundDomainServiceImpl.java:82`~`:86`
     「本类此前同时注 `PaySignProperties` 与 `PaySignGateway`，而前者**只**为取 `requestRefundUrl` /
     `refundQueryUrl` 两个 URL 存在。两者一起换成本端口后，本类协作者 **4 → 3**。**NEVER 把那两个加回来。**」
     （前一句的「7 条」与仓库实际 9 条不符，见 M1。）

### 四、service 接口层与门面（ADR-D115 的两类删除）

#### 决策理由

174. **ADR-D115：两个「网关代理」转发壳已删除** —— `pay-sign-server` · `ContractDomainService`（接口尾注）·
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/ContractDomainService.java:39`~`:48`：
     「`requestPayPlatformTermination` / `queryPayPlatformContractStatus` 两个方法已于 2026-09-16 删除
     （ADR-D115）。它们不含任何领域逻辑，只是「取 URL + 组 bizData + 出网」的转发壳，
     被 `TerminationExecutor` / `TerminationProcessor` 当**网关代理**用 —— 那两个类因此依赖了一个
     6 协作者的领域服务，还得各自调 `paySignGateway.isSuccess` 判读应答。
     现在它们直接注 `port/ContractGatewayPort`（`requestDismissal` / `queryContractResult`），
     应答判读走 `GatewayReply` 模式匹配。**NEVER 在本接口加回任何「向支付中心发一次请求」的方法 ——
     出向调用一律加到 `ContractGatewayPort` 上**」；实现类侧同一条在
     `.../service/impl/ContractDomainServiceImpl.java:677`~`:683`（并注明「两者的方法体各只有一行
     `contractGatewayPort.xxx(...).raw()`，是 ADR-D112 留下的**过渡壳**」）。

175. **ADR-D115 续（二）：`ContractDomainServiceImpl` 不再持 `PaySignRequestMapper`（协作者 6 → 5）** ——
     `.../service/impl/ContractDomainServiceImpl.java:94`~`:99`：「唯一引用是 `alipayTripRequestSignInfo`
     里手写的那行流水，已与方法收尾的审计行合并成一行。**NEVER 在本类重新注入它** ——
     接口流水的唯一写入点是 `PaySignAuditLogger`，领域服务再持有 mapper 就等于给同一张表开第二个写入口，
     两个写法迟早分叉」。同类 `:102`~`:105` 补了一个可用于估量的事实：**本类调用 `auditLogger` 共 52 处**。
176. **支付宝出行的「两行流水」已合并成一行（口径变更，MUST 与阶段一第 22 条对读）** ——
     `.../service/impl/ContractDomainServiceImpl.java:273`~`:283`：「流水由方法收尾的
     `auditAlipayTripSignInfo` 一行写完（2026-09-16，ADR-D115 续（二））。此前这里另有一行手写的
     `PaySignRequest` INSERT，于是**一次成功写两行流水**：本行 `OPERATION_TYPE` 是原样字面量
     `ALIPAY_TRIP_REQUEST_SIGN_INFO` + `SIGN_STATUS='SIGNED'`、不带报文；审计那行被 `convertOperationType`
     归并成 `SIGN`、带报文。两行拼在一起才是完整证据，而**运营按 `OPERATION_TYPE` 筛流水的口径只有
     `SIGN` / `UNSIGN` 两个值** —— 那行字面量本身就在口径之外，谁按 `SIGN` 统计都会把它漏掉、
     按它统计又与别的接口不可比。合并后单行同时带 `SIGN_STATUS='SIGNED'`、`REQUEST_BODY` /
     `RESPONSE_BODY` 与 `RESULT_CODE`，信息只增不减，且 `APP_PAY_SIGN_REQUEST` 的写入点回到
     `PaySignAuditLogger` 一处。**NEVER 在本方法（或任何领域服务）里重新手写 `PaySignRequest`**」。
     这条**取代**了阶段一第 22 条（ADR-D111「两行是现状、要不要合并属改审计口径」），见 M3。
177. **回调领域与支付领域的边界墓碑（两侧各一条）** —— `pay-sign-server` · `CallbackDomainService`（类注释）·
     `.../service/CallbackDomainService.java:11`~`:13`：「**支付结果回调（`receivePayResult`）不在本接口**：
     它的真实现随支付组搬进了 `PaymentDomainServiceImpl`，`PaySignServiceImpl` 直接路由到
     `PaymentDomainService`。**NEVER 在这里加回一层转发** —— 那会让回调组反向依赖支付组」；
     `PaymentDomainService`（类注释）· `.../service/PaymentDomainService.java:11`~`:12`
     「退款（发起 + 两套补偿）已于 2026-09-15 拆出为 `RefundDomainService`（纯搬迁），
     **NEVER 在本接口上加回退款方法**」、`:20`~`:24`「它落在**支付**领域而不是回调领域：整个方法只读写
     `PAY_CALLBACK_LOG` / `PAY_TXN_DETAIL`，并与 `requestPay` 共用 `resolveDebitRequestResult`
     等支付组私有方法。对外入口仍是 `CallbackDomainService#receivePayResult`（`PaySignServiceImpl`
     的路由一行未动），那一层只做转发。**NEVER 把状态回写逻辑复制回回调领域。**」
     **注意最后这句与上一条自相矛盾**（一处说「路由一行未动、那一层只做转发」，另一处说「直接路由到
     `PaymentDomainService`、NEVER 加回转发」），见 M8-b。
178. **退款接口层把三个入口的定位、配套关系与 A/B 类处置写全了** —— `pay-sign-server` ·
     `RefundDomainService`（类注释与三个方法头）·
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/RefundDomainService.java:10`~`:58`：
     「2026-09-15 由 `PaymentDomainService` 拆出（纯搬迁）。**拆分前那个接口同时挂着支付发起、支付回调、
     退款发起与两个退款补偿五个入口，实现类 1166 行**」；`compensateRefundQuery` 是
     「`requestRefund` 摘掉 `@Transactional`（批次 5B）的**配套补偿**：那条链路最坏会停在
     「退款已发出、本地 PROCESSING」，**此处是唯一的收口出口**」；`compensateRefundSummary`
     「补的是 `requestRefund` 链路**第 9 步**（`updateRefundSummary`）失败或漏跑留下的窟窿：
     明细已 `SUCCESS`、汇总没跟上，账面上「可退金额 = 已付 - 已退」偏大。**此前没有任何补偿覆盖这一步**」
     「扫出的两类结果处置**相反**……B 类**一行都不改**……**MUST NOT 当成修好**」
     「触发方是 web-admin 的 Quartz `sys_job`……**本模块 NEVER 自带 `@Scheduled`**」。
179. **门面接口 `PaySignService` 记着支付宝出行与 IF8A-05 的对内用途** ——
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/PaySignService.java:29`~`:33`
     「支付宝出行-添加签约信息……接收支付宝 DTO，映射为内部 `RequestSignInfoReqDTO`，
     固定签约渠道为 ALIPAY，**同步确认签约成功**并写入 `APP_PAY_SIGN_INFO` 表和流水表」；
     `:44`~`:46` IF8A-36 的语义（同第 146 条那三处逐字相同的描述）；
     `:72`「IF8A-05 批量查询支付明细（**供 ticket-server 双源合并**）」——
     这是「谁在用 `queryPayTxnBatch`」的唯一记载处。
180. **`AppNotifyService` 的墓碑行同时是「外部调度形态」的说明** ——
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/AppNotifyService.java:42`~`:45`：
     「本方法**不再由服务内 `@Scheduled` 驱动**，改由外部定时任务调用
     `POST /internal/paySign/compensateNotify`，调用方可反复调用直到 `scanned` 为 0。
     重发是异步的，通知是否成功以 `APP_PAY_SIGN_REQUEST` 的 `NOTIFY_STATUS` / `NOTIFY_RESULT` 为准，
     不要用返回的 `submitted` 判断结果」（AGENTS.md §2.2.1 引的就是这一行，阶段一 T13 只登记了行号；
     此处补它的完整语境与「反复调用到 0」这条使用约定）。

### 五、support / domain 补漏（破例谱系、ADR-D99、ADR-D92 续）

181. **support 包那条「条件式破例」的谱系有 6 处交叉引用，且都点名 `F2fDuplicateKey`（ADR-D84）** ——
     `PaySignValues.java:14`~`:23`（「这几个方法原本是 `PaySignWorkflow` 的私有方法，而该类正在按
     「支付组 / 回调组 / 签约+解约组」拆成三个领域服务 —— 三个组都要用它们，不抽出来就等于**在三个类里
     各留一份逐字副本**」+ **准入判据**「只收「无字段依赖、无 IO、给同样输入必得同样输出」的函数。
     因此 `resolveNotifyUrl` / `resolvePayNotifyUrl` 都**不在**本类里 —— 它们要读 `PaySignProperties`，
     且签约与支付两条链路的回落顺序本就不同；同理 `PaySignGatewayMessages` 也是靠把 `notifyUrl`
     当参数传入来保持纯函数」）、`PaySignGatewayMessages.java:35`、`PaySignValidators.java:27`、
     `PayRefundRules.java:28`、`PaySignGateway.java:14`、`PaySignResponses.java:47`。
     阶段一第 19 条列了这批类名，**这里补的是「准入判据」与「哪两个方法因此被排除在外」**。
182. **`PaySignGateway` 为什么必须是 Bean、而不是静态工具** —— `.../support/PaySignGateway.java:16`~`:20`：
     「**为什么不直接让三个领域服务各注 `PayGatewayClient`**：`isSuccess` / `errorMessage` 本身就在 client 上，
     真正不能散的是 `isAlreadyPaidSuccess` —— 它按「`code=9999` 且 msg 含特定中文」判「其实已支付成功」，
     是**对支付中心应答措辞的硬编码约定**，措辞一变就要同步改。散成三份副本时，改一处漏两处不会有任何
     编译错误，只会让一笔已扣款成功的交易被判成失败、进而走到拉黑分支」。
183. **`PayRefundRules` 与 `PaySignValidators` 是两个类、两份职责** ——
     `.../support/PayRefundRules.java:24`~`:25`「报文校验（`PaySignValidators`）与退款业务规则（本类）
     在包里是两个类、两份职责。**NEVER 把本类的方法并进 `PaySignValidators`。**」；反向那条在
     `.../support/PaySignValidators.java:23`~`:24`「属**退款业务规则**而非报文校验。搬进本类等于把业务判断
     塞进 support 包，**NEVER 因为名字都叫 validate 就一起搬**」。
184. **ADR-D99：出向加签逐字搬出 `AppNotifyServiceImpl`，密钥收成入参** ——
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/support/AppNotifySigner.java:19`~`:24`：
     「ITP **出向通知**（IF8B）的加签（2026-09-16 由 `AppNotifyServiceImpl` 逐字搬出，ADR-D99）」
     「**密钥 MUST 由调用方当参数传进来，本类 NEVER 自己持有 `itpSignKey`**：原实现里 `buildItpSignSource`
     直接读 `@Value` 字段，**这是它此前不能当纯函数外提的唯一原因**。收成参数后本类仍满足 support 包那条
     条件式破例（纯函数、零状态、零依赖）。**NEVER 给本类加 `@Value` / `@Component`** —— 那等于把密钥的
     持有点又多开一处」（阶段一第 107 条记的是算法与源串本身，这里补「为什么此前搬不出来」）。
185. **ADR-D92 续：`requestPay` 的 bizData 里 NEVER 出现 `payUserId`（多送一个未定义字段就是另一个签名）** ——
     `.../support/PaySignGatewayMessages.java:102`~`:109`：「**NEVER 往本报文加 `payUserId`**
     （2026-09-16 删除，ADR-D92 续）。网关文档 §1.1 的字段表**没有这个字段**，它只属于 §2.2 contract
     （注为「数字人民币子钱包推送专用」）；接口方给出的钱包扣款「正确请求」样例里也没有它。而
     `PayGatewayClient.buildSignSource` 把 bizData 全部非空字段按 `TreeMap` 升序拼进待签串 ——
     **多送一个未定义字段就是另一个签名**，对端表现为**不带 `msg` 的裸 `code=9999`**
     （2026-09-16 实测 `orderNo=LHTEST202609160003`）」「钱包的 `payUserId` 仍然照旧落
     `PAY_TXN_DETAIL.PAY_USER_ID`（对账与排查要用），只是**不出网**；因此 `applyAccountUserView`
     那边的赋值 **NEVER 一起删**」。**这是「裸 9999 无 msg」这一症状在本模块的唯一成因记载。**
186. **退款报文的一个必填项曾因取值时机而必然为 null** —— 同类 `:29`~`:32`：
     「……由 `updateRefundRequestResult` 写入，构造报文时必然为 `null` —— **等于必填项漏传。NEVER 回退**」
     （上下文是退款 §3.1 的「原支付订单号」取值来源，与阶段一第 68 条 `payCenterOrderNo` 那条互为因果）。
187. **`PayTxnViews`（ADR-D98）：外提理由是「零协作者」，真正的收益是那个反射测试** ——
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/support/PayTxnViews.java:9`~`:20`：
     「`PAY_TXN_DETAIL` 实体到对内查询 DTO 的装配（2026-09-16 由 `PaySignServiceImpl` 外提，ADR-D98 判据）」
     「**外提理由是「零协作者」而不是行数**：这 26 行只读入参、不碰任何 mapper / client / properties」
     「**真正的风险是「漏一个 setter 不会有任何提示」**：`PayTxnDetailDTO` 有 **26 个字段**，手写逐字段拷贝时
     漏掉一个，编译通过、单测（**原先零覆盖**）也通过，只是调用方拿到的那一列恒为 `null`。收进本类之后由
     `PayTxnViewsTest` 用反射遍历 DTO 的全部 getter 守着 —— **新增字段但忘了在这里搬，那个用例立刻变红**」
     「**NEVER 往本类加过滤 / 脱敏 / 状态判断**：它只做字段搬运」；`:27`「`null` 入参返回空列表
     （**NEVER 返回 `null`**）」、`:39`「**26 个字段逐一对应，新增字段 MUST 同步加到这里**（有测试守）」。
188. **`TerminationFailReason` 的存在理由是 ADR-D47 当轮引入、同日发现的缺陷** ——
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/domain/TerminationFailReason.java:6`~`:9`：
     「**存在理由是一个已发生的缺陷**（2026-09-12，ADR-D47 当轮引入、同日发现）：该列**同时**服务两个
     互不相干的读者 —— 运维排查（要看内部说明）与 APP 解约失败通知的 `terminationResultMsg`
     （**绝不能看到内部说明**）。ADR-D47 把「需人工核对」标记前置拼进这一列时只考虑了前者，于是
     `AppNotifyServiceImpl.asyncRetryTerminationNotify` 的补偿重发会把运维文案发给终端用户」。
     阶段一第 40 条记的是读写约定本身，**这里补它的成因与时间线**。

### 六、event 与通知补偿补漏（ADR-D32、ADR-D47 的落点）

189. **ADR-D32：`AFTER_COMMIT` 里做的是**两件**派生动作，可靠性等级不同** —— `pay-sign-server` ·
     `SignResultCommittedListener`（类注释）·
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/event/SignResultCommittedListener.java:18`~`:25`：
     「本事件的**唯一**监听方：签约结果落库提交后做两件派生动作 —— ① 触发一次 APP 通知；
     ② 把 `PAY_ACCOUNT_ID` 推给账户域（**ADR-D32**）。两件事共享同一个前提：都**必须在事务提交之后**发 HTTP
     （AGENTS.md §5.2），所以放在同一个 `AFTER_COMMIT` 回调里、而不是各起一个监听器 ——
     **本项目规定一个事件类只挂一个监听器**。但两者**可靠性等级不同**：① 有 `NOTIFY_STATUS='PENDING'` +
     扫表补偿兜底，② 是允许丢的展示值同步。**NEVER 因为写在一起就给 ② 也加补偿、或让 ② 的失败影响 ①**」；
     字段侧 `:66`「ADR-D32：把 `PAY_ACCOUNT_ID` 推给账户域。**允许失败**」。
190. **解约失败通知 MUST 剥掉「需人工核对」标记（ADR-D47 的修复点）** ——
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/service/impl/AppNotifyServiceImpl.java:181`~`:183`：
     「`FAIL_REASON` 里可能带「需人工核对」的内部说明（ADR-D47），**MUST 剥掉再发给 APP**。
     2026-09-12 已发生：ADR-D47 当轮直接把原值发出去，运维文案会出现在用户的 `terminationResultMsg` 里。
     **NEVER 退回 `terminationRequest.getFailReason()`**」（调用的是 `TerminationFailReason.stripManualMark`）。

### 七、mapper 与实体补漏（持久层注释里的判据）

191. **`PAY_CALLBACK_LOG` 的 insert 脱离事务是刻意的，它是「支付中心实际推送次数」的唯一可靠计数器** ——
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/mapper/PayCallbackLogMapper.java:12`~`:15`：
     「统计同一商户订单号已收到的同类回调条数（**含本次，调用点在 insert 之后**）。用于「重推只做一次」的硬限次判定：
     本表**只追加不删除**，且 **insert 已脱离事务、立即提交**，因此这个计数就是支付中心实际推送次数的**可靠计数器**」。
     与 2026-08-26 事故（事务内回滚导致 `PAY_CALLBACK_LOG` 零条落库）互为因果：限次判定要成立，
     前提就是这张表的写入**不能被任何回滚带走**。**改动回调链路 MUST 先确认这条 insert 仍在事务外**。
192. **达到重推上限后回 `0000` 让上游停推，MUST 同时打人工标记，否则等于静默丢单** ——
     同文件 `:21`~`:24`：「把该商户订单号**最近一条**同类回调标记为需人工处理。调用点是「达到重推上限仍未处理成功」，
     此时**已决定回 `0000` 让上游停推**，必须留下**可检索的痕迹**，否则等于**静默丢单**」
     （方法名 `markManualByMerchantOrderNo`，第三参 `handleMsg`）。判据可复用：任何「主动让上游停止重推」的分支，
     MUST 有一条能 grep / 能 SQL 查出来的落库痕迹。
193. **退款汇总只准传 `orderNo`、NEVER 传增量金额** ——
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/mapper/PayTxnDetailMapper.java:27`~`:30`：
     「按 `PAY_REFUND_DETAIL` **重算**原支付订单的已退款金额与退款状态。**只传 `orderNo`**：金额与状态都由 SQL
     从明细表汇总，**NEVER 由调用方传入增量** —— **入参没有幂等键**，传增量就意味着**重复执行会重复累加**
     （详见 mapper XML 内注释）」。这条与 §12 的「退款回查补偿会重复触发」是一套：补偿端点按设计**允许重复调用**，
     所以下游 SQL MUST 写成幂等的「全量重算」而不是「累加」。
194. **`selectByOrderNos` 是为 IF8A-05 双源合并存在的，不是通用批查** ——
     同文件 `:16`：「批量按订单号查询支付明细（**用于 IF8A-05 双源合并**）」。
     即交易列表要把 `PAY_TXN_DETAIL` 与另一侧的数据按订单号合并，故需要 `IN` 批查；
     **NEVER 因为「看起来是通用工具方法」就在别处复用它做无上限的 `IN` 查询**（Oracle `IN` 有 1000 项上限）。
195. **`SIGN_STATUS` 只准走 4 条 CAS 方法，`updateBySeq` 会让迟到回调覆盖已解约状态** ——
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/mapper/PaySignInfoMapper.java:36`~`:42`：
     「`SIGN_STATUS` 状态机的 **CAS UPDATE，前置状态写在 WHERE 里**。迁移白名单见
     `docs/domain/state-machines.md` 与 mapper XML 注释。改 `SIGN_STATUS` **MUST 走这 4 条**，
     **NEVER 再用 `updateBySeq`**，后者 **WHERE 只有 `REQUEST_SIGN_SEQ`**，会让**迟到的签约回调覆盖已解约状态**。
     **返回 0 行不等于失败**：可能是幂等重放（当前已是目标态），也可能是真冲突，
     调用方 **MUST 用 `selectSignStatusBySeq` 回查后再决定**」。四条即 `markSigned`（`:44`）、
     `markSignFailed`（`:50`）、`markUnsigned`（`:52`）、`reactivateForResign`（`:56`）；
     `:55`「**复位重签**：`UNSIGNED` / `FAILED` -> `NOT_SIGNED`，并**清空上一轮的签约结果字段**」，
     `:58`「CAS 返回 0 行后回查当前状态用，**记录不存在时返回 null**」——`null` 与「状态不匹配」是两种分支，
     **NEVER 合并处理**。

196. **`PAY_CENTER_ORDER_NO` 与 `MERCHANT_ORDER_NO` 不是一回事，退款报文 §3.1 只认前者** ——
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/entity/PayTxnDetail.java:38`~`:40`：
     「支付中心侧的支付订单号（**回调报文的 `orderNo`**，形如 `286275496309587968`）。
     与 `MERCHANT_ORDER_NO` **不是一回事**：后者是**我方**商户订单号，**等同 `ORDER_NO`**。
     退款报文 **§3.1 的「原支付订单号 `orderNo`」MUST 用这个值，缺它退款必失败**」。
     即三个「订单号」在这张表里同时存在：`ORDER_NO`（=我方商户订单号）、`MERCHANT_ORDER_NO`（同上，冗余列）、
     `PAY_CENTER_ORDER_NO`（对端号）。**排查退款一直失败 MUST 先看这列有没有值**——它只能从支付回调报文里拿到，
     没收到过成功回调的单子退不了。同行还有 `:34`「优惠详情 JSON 数组」、`:35`「扣款结果：`PROCESSING`/`SUCCESS`/`FAIL`」、
     `:36`「优惠金额（**分**）」、`:37`「入账账户/商户号」。
197. **`PAY_SIGN_REQUEST` 冗余了一组通知业务字段，目的是「补偿通知不依赖已删除的 `PaySignInfo`」** ——
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/entity/PaySignRequest.java:19`：
     「通知业务字段（**补偿通知时使用，避免依赖已删除的 `PaySignInfo`**）」，覆盖 `payAccountId` / `payAgreementNo` /
     `cardId` / `cardType` / `terminationTime`。这条解释了一个反直觉的设计：解约成功后 `PAY_SIGN_INFO` 那行可能已被
     `deleteByUserAndVendor` 删掉（见 195 的方法清单），若通知补偿再去关联查询就会查不到、**补偿永远失败**，
     故解约申请表自带快照。**NEVER 以「字段重复、应该 JOIN 回主表」为由删这组列。**
     配套 `:25`~`:29`「通知相关字段」：`notifyStatus`「通知状态: `PENDING`/`SUCCESS`/`FAILED`」、
     `notifyRetryCount`「通知重试次数」、`notifyTime`「最后通知时间」、`notifyResult`「通知结果描述」——
     即 ADR-D46 outbox 四列形状在本表的落点。
198. **`PAY_CALLBACK_LOG` 只留原文、不承担最终态** ——
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/entity/PayCallbackLog.java:8`~`:9`：
     「每次支付平台回调都插入一条记录，**避免重复回调覆盖原文**。当前支付订单的**最终态**由
     `PAY_TXN_DETAIL` 或 `PAY_REFUND_DETAIL` 保存」。判据：查「这笔单现在什么状态」MUST 看后两张表，
     查「对端推过几次、推的什么内容」才看 `PAY_CALLBACK_LOG`，**NEVER 从回调流水表推断当前状态**。
199. **解约响应里的 `requestSignSeq` 是复用原签约流水号，不是新号** ——
     `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/model/response/RequestTerminationRespDTO.java:5`：
     「商户端签约流水号（**复用原签约流水号**）」。因此 APP 侧不能拿它当「本次解约请求的唯一标识」，
     同一签约多次申请解约拿到的是同一个值；本模块的解约唯一键是 `UK_ATR_REQUEST_SIGN_SEQ`（见阶段一第 118 条）。
200. **外部应答码速查（散落在 8 个文件的注释里，本轮汇总，便于反查）**：
     - `code=600 操作失败` = **URL 漏了 `/v1`**，不是 404、不是业务拒绝
       （`config/PaySignProperties.java:16`、`:43`，`resources/application.properties:91`）。
     - 裸 `code=9999`（**不带 `msg`**）= 我方送的签约流水号在支付中心侧不是有效协议
       （`support/PaySignGatewayMessages.java:106`「2026-09-16 实测 `orderNo=LHTEST202609160003`」、
       `service/impl/ContractDomainServiceImpl.java:185`~`:186`）。
     - `code=9999` + `msg` 含「已支付成功」/「请勿重复支付」= **幂等成功**，MUST 当成功处理
       （`support/PaySignGateway.java:48`~`:59`、`port/PaymentReply.java:11`、`:23`；
       `PaySignGateway.java:50`「只按 msg 措辞识别，**NEVER 放宽成「`code=9999` 就算已支付」**」）。
     - `code=9999「退款流水号或商户退款流水号必填」` = 只送了 `refundOrderNo`（ADR-D92，
       `port/RefundGatewayAdapter.java:15`、`service/impl/RefundDomainServiceImpl.java:274`）。
     - `code=9999「代扣签约请求流水号不能为空」` = 免密扣款只送了 `payUserId`
       （`service/impl/PaymentDomainServiceImpl.java:588`）。
     - 我方对上游的 `9999` = 通用失败（`constant/PaySignErrorCodeEnum.java:5` `FAIL("9999", "失败")`），
       全局异常处理器统一转（`exception/TerminationException.java:5`）。
     - `8023` = IF8A-42 销户入口的欠费拦截码，**解约链路刻意不重复拦**
       （`service/TerminationInternalService.java:74`「欠费由后续催收流程处理」）。
     - `app.notify.success-ret-codes=0000,7004`（`resources/application.properties:66`）：
       **`7004` 也算通知成功**，APP 侧用它表示「已收到过、无需再推」。

### 矛盾与待裁决

> 口径：每条按「注释说什么 / 代码实际是什么 / 证据 `file:line` / 建议裁决」四段写。
> **本轮只记录、一行代码都没改**（用户约束）。编号 M1 起，与阶段一的墓碑编号无关。

- **M1「7 条完整 URL」已过期，实际是 9 条网关 URL + 2 条通知 URL**
  - 注释说什么：`pay.sign.*` 收口成「**7 条**完整 URL」（`config/PaySignProperties.java:12`~`:22` 的类注释口径，
    阶段一第 136 条照抄了这个数字；`service/impl/PaymentDomainServiceImpl.java:104` 亦沿用）。
  - 代码实际是什么：`resources/application.properties:81`~`:88` 与 `:92` 共 **9 条** `pay.sign.*-url`
    （含后补的 `pay.sign.refund-query-url` 与 `wechatEntrustUrl`），另有 `:93` / `:94` 两条
    `app.notify.*-url` 出向通知地址（指向 `58.56.166.170:48000`）。
  - 证据：`pay-sign-server/src/main/resources/application.properties:81`~`:94`；
    `pay-sign-server/src/main/java/com/chinasofti/huateng/paysign/config/PaySignProperties.java:12`~`:22`。
  - 建议裁决：把「7 条」改成「按 `pay.sign.` 前缀 grep 得到的全部条目」这种**不带数字**的表述。
    根 `AGENTS.md` §8 也写着「改成 7 条完整 URL」，同属该数字的传播点，一并更新。
    **判据结论不变**（NEVER 退回 `-path` + `gateway-url` 拼接），只是计数不可引用。
- **M2 「我方只填 `refundOrderNo`」与「两个键都送」正面冲突**
  - 注释说什么：`config/PaySignProperties.java:37`~`:38` 仍写着退款查询「我方填 `refundOrderNo`」。
  - 代码实际是什么：`RefundDomainServiceImpl` 组装退款查询报文时 **`merchantRefundNo` 与 `refundOrderNo` 两个键都送**，
    并在注释里写明只送后者会返 `9999「退款流水号或商户退款流水号必填」`（ADR-D92）。
  - 证据：`.../config/PaySignProperties.java:37`；`.../service/impl/RefundDomainServiceImpl.java:272`~`:288`
    （`:274` 有实测应答原文 `{"code":9999,"msg":"退款流水号或商户退款流水号必填"}`）；
    `.../port/RefundGatewayAdapter.java:15`。
  - 建议裁决：以 ADR-D92 与实测应答为准，`PaySignProperties` 那两行注释已作废，
    应改为「两个键都 MUST 送」。**NEVER 因为供方文档只写一个键就删掉另一个。**
- **M3 阶段一第 22 条（ADR-D111「两行审计」）已被 ADR-D115 续（二）改掉**
  - 注释说什么：阶段一第 22 条按 ADR-D111 记录「支付宝出行签约落 **两行** 审计/签约记录」。
  - 代码实际是什么：`ContractDomainServiceImpl:273`~`:283` 声明支付宝出行链路已**合并为一行**
    （ADR-D115 续（二）），并把手写 `PaySignRequest` INSERT 一并删除。
  - 证据：`.../service/impl/ContractDomainServiceImpl.java:273`~`:283`；
    对照 `docs/business/pay-sign.md` 阶段一附录第 22 条。
  - 建议裁决：**阶段一那条 MUST 标注「已被 ADR-D115 续（二）取代」**（按用户约束本轮不改阶段一正文，
    故只在此登记）。同时 MUST 复核 `AlipayTripSignCharacterizationTest` 的断言是否仍按两行写
    —— 若是，那份特征测试与现实现不一致。

- **M4 「字段注入而非构造器注入是刻意的」与同文件下方的构造器注入自相矛盾**
  - 注释说什么：`service/impl/RefundDomainServiceImpl.java:71`~`:73` 写着依赖用**字段注入**而非构造器注入
    「是刻意的」。
  - 代码实际是什么：同文件 `:79`~`:88` 是一个显式构造器、依赖字段带 `final`，`:90`~`:95` 还标注了
    ADR-D96 对该构造器形状的要求。两种写法不可能同时成立。
  - 证据：`.../service/impl/RefundDomainServiceImpl.java:71`、`:79`~`:88`、`:90`~`:95`。
  - 建议裁决：以代码（构造器 + `final`）为准，`:71`~`:73` 那段是拆分过程中的残留说明，应删。
    **注意它可能指的是「某一个」依赖**（如为断循环依赖而单独字段注入的那一个）——
    裁决前 MUST 逐个字段核对，**NEVER 直接把整段注释删掉了事**。
- **M5 根 `AGENTS.md` §8 说「`pay-sign-server` 缺 `service.gateTxnPay.url`」，但该键已存在**
  - 注释说什么：根 `AGENTS.md` §8「`service.*.url` 有三类问题」把 `pay-sign-server` 缺
    `service.gateTxnPay.url` 列为「键缺失」的活例子。
  - 代码实际是什么：`pay-sign-server/src/main/resources/application.properties:54` 已有该键，
    与 `:48` / `:51` 另两条 `service.*.url` 并列。
  - 证据：`pay-sign-server/src/main/resources/application.properties:46`~`:54`；`AGENTS.md` §8 该行。
  - 建议裁决：按「`ticket-server` 缺 `service.paySign.url` 已补齐」「`fep-dev-server` 缺
    `service.key.url` 已补齐」的既有格式，把 `pay-sign-server` 这条也标为已补齐、NEVER 再列。
    **但 MUST 先查集群 env 确认线上值有效**（仓库有键 ≠ 线上指对了，属该 §同一条的第①/③类问题）。
- **M6 敏感项默认值：仓库里存在私钥真值默认值（安全红线，本轮只记位置）**
  - 注释说什么：`AGENTS.md` §5.2「敏感配置」要求私钥等 **MUST 写成 `${ENV_VAR:}`（空默认值）**，
    **NEVER 写默认真值**。
  - 代码实际是什么：`pay-sign-server/src/main/resources/application.properties:76`~`:80` 那组
    商户号 / 私钥键中，**私钥那一行带有非空默认值**；同一份密钥材料还出现在
    `util/RSASignUtils.java:78`~`:117` 那段被注释掉的 `main()` 里。
  - 证据：`pay-sign-server/src/main/resources/application.properties:80`（键名见该行，
    **值不在本文档回显**）；`.../util/RSASignUtils.java:80`。
  - 建议裁决：**P0**。MUST 改成 `${ENV:}` 空默认值 + K8s Secret 注入，并**连带删除**
    `RSASignUtils` 那段注释掉的 `main()`（注释掉不等于删除，仓库、镜像、`javap` 里都还在）。
    换钥同时 MUST 走人工安全复核（§5.2 安全红线）。本轮按约束**未改任何代码**。
- **M7 解约链路已无事务，但两处注释仍宣称「触发事务回滚」**
  - 注释说什么：`exception/TerminationException.java:4`~`:5`「由全局异常处理器统一转换为
    `resultCode=9999` 的 `BaseRespDTO`」并暗示回滚语义；`controller/PaySignExceptionHandler.java:38`
    明写「解约流程异常。**触发事务回滚后**，向调用方（含支付平台回调）返回 `9999`，便于重试」。
  - 代码实际是什么：解约收口按 ADR-D48 已改为「本地短事务 + `CHANNEL_SYNC_*` outbox + 提交后出网」，
    且承载 RPC 的方法**刻意不带 `@Transactional`**（ADR-D87 迁入 `CallbackDomainServiceImpl` 后依旧）。
    因此异常抛到处理器时**未必有事务可回滚**，`pay-sign-channel-sync-migration.sql:2`~`:4` 的脚本注释
    也是按 outbox 语义写的。
  - 证据：`.../exception/TerminationException.java:4`~`:5`；`.../controller/PaySignExceptionHandler.java:38`；
    `.../resources/sql/pay-sign-channel-sync-migration.sql:2`~`:4`；`AGENTS.md` §5.2 该条。
  - 建议裁决：把「触发事务回滚后」改为「不改变已提交的本地状态」。**这条是纯注释误导、不改行为**，
    但危害在于会诱导后来者「既然靠回滚兜底，那把 RPC 放回事务里也行」——正是 2026-08-26 事故的成因。

- **M8 IF8A 编号在注释、文档正文与另一份注释之间三方不一致**
  - 注释说什么：`controller/ci/app/PaySignAppController.java:82` 把 `requestTermination` 标为
    **IF8A-34**；`:68`~`:70` 把某端点标为 **IF8A-36**，而 `service/TerminationInternalService.java:69`
    对同一语义用的是另一个编号。
  - 代码实际是什么：本文档正文（§接口清单）把解约申请记为 **IF8A-06**；根 `AGENTS.md` §2.2.1 已明说
    「接口编号不唯一映射到一处代码」「IF8A-04/05/06 在 APP 域与 BOM 域含义不同」。
  - 证据：`.../controller/ci/app/PaySignAppController.java:68`~`:70`、`:80`~`:86`；
    `.../service/TerminationInternalService.java:69`；本文档 §接口清单对应行。
  - 建议裁决：**编号一律不作为定位依据**（§2.2.1 已是全局判据），代码注释里 MUST 同时写
    「模块 + URL」。不必强行统一编号，但 MUST 在本文档接口清单里标注「注释中的编号与此不一致」，
    避免下一轮又按编号去改代码。**NEVER 按编号相同就认为是同一个端点。**
  - 附带一条同源自相矛盾（M8-b）：回调 / 支付两个 controller 的注释一处写「**只做转发**」、
    另一处写「**NEVER 加回转发**」，指的其实是不同层（controller 转发给 domain service 是允许的，
    domain service 再转发给别的服务是禁止的），措辞需区分「层内委派」与「跨服务转发」。
- **M9 IF8A-36 端点在代码里已存在，本文档正文却写「fep-app 未接线、暂不实现」**
  - 注释说什么：`PaySignAppController.java:68`~`:70` 描述了 IF8A-36 的完整语义并有真实端点。
  - 代码实际是什么：本文档正文对应条目写的是「fep-app 未接线、暂不实现」。两者可同时为真
    （**支付域已实现、接入层未接线**），但正文的措辞会让人以为支付域也没做。
  - 证据：`.../controller/ci/app/PaySignAppController.java:68`~`:70`；本文档正文该行。
  - 建议裁决：正文改成「支付域端点已实现，`fep-app-server` 侧未接线」。
    **判断是否可用 MUST 分两段查**：本模块 URL 存在 + `fep-app-server` 有没有对应路由。
- **M10 `application.properties` 里注释掉的备用库地址与「唯一目标库」判据冲突**
  - 注释说什么：`resources/application.properties:33`~`:35` 留了一个被注释掉的备用数据库主机地址。
  - 代码实际是什么：根 `AGENTS.md` §8 已裁决 **`172.20.222.3:1521 / AFCITPDB` 是唯一目标业务库**，
    并要求「凡是『测试库已执行、生产库仍未执行』的表述都是把同一个库当成两个」。
  - 证据：`pay-sign-server/src/main/resources/application.properties:33`~`:35`；`AGENTS.md` §8 该条。
  - 建议裁决：若那个地址已无效，MUST 删掉（留着会让后来者以为存在第二个环境）；
    若确实是另一个真实库，则 MUST 先拿到它的角色定义再写进 `docs/ops/生产环境清单.md`，
    **NEVER 以注释形态留在业务模块配置里**。线上真实值一律以 Deployment env 为准。

### 墓碑清单（T23 起，接阶段一 T1~T22）

> 口径：本节只记「已被删除 / 已被否决，且注释里明确要求不要再回来」的东西。
> 每条给出**可 grep 的字样**与**可否 grep 到**的评估 —— 只存在于注释里的墓碑，
> grep 代码是搜不到的，这正是本节存在的理由。

| 编号 | 墓碑（NEVER 回来的东西） | 出处（`路径:行号`） | 可 grep 的字样 | 可 grep 性 |
|---|---|---|---|---|
| T23 | `pay.sign.test-force-amount` / `testForceAmount` 强制金额开关 | `config/PaySignProperties.java:55`~`:60` | `testForceAmount` | 仅注释可见；配置文件里已无该键 |
| T24 | `ContractDomainService` 上那两个「网关代理」方法（拆分时确认零调用方） | `service/ContractDomainService.java:39`~`:48` | `已删除` + 方法名 | 仅注释；接口里已无声明 |
| T25 | 把 `PaySignRequestMapper` 重新注回 `ContractDomainServiceImpl` | `service/impl/ContractDomainServiceImpl.java:87`~`:99` | `PaySignRequestMapper` | 可 grep（该类仍存在，别处仍在用），**易误判** |
| T26 | 支付宝出行链路里手写的 `PaySignRequest` INSERT（ADR-D115 续（二）合并为一行） | `service/impl/ContractDomainServiceImpl.java:273`~`:283` | `ADR-D115` | 仅注释 |
| T27 | 给 payment / refund 两个 impl 重新注入 `PaySignGateway` 或 `PaySignProperties`（MUST 走 port） | `service/impl/PaymentDomainServiceImpl.java:96`~`:105`、`service/impl/RefundDomainServiceImpl.java:60`~`:73` | `PaySignGateway` | 可 grep（support 类仍在），**易误判** |
| T28 | `requestPay` 的 bizData 里只送 `payUserId`（会返 `9999「代扣签约请求流水号不能为空」`） | `service/impl/PaymentDomainServiceImpl.java:588`、`service/impl/ContractDomainServiceImpl.java:185`~`:186` | `payUserId` | 可 grep |
| T29 | `AppNotifySigner` 自己持有 `itpSignKey`（密钥 MUST 由外部传入） | `support/AppNotifySigner.java:18`~`:27` | `itpSignKey` | 可 grep（配置键仍在） |
| T30 | `PaySignInfoMapper.upsert` / `MERGE INTO` 写法（补 T15：Java 侧声明也已注释掉） | `mapper/PaySignInfoMapper.java:61`~`:62` | `废弃：原有的 upsert/MERGE` | 仅注释（**语句已注释掉，grep `upsert` 会命中注释行**） |
| T31 | 把原始 `FAIL_REASON`（含「需人工核对」）直接发给 APP | `service/impl/AppNotifyServiceImpl.java:181`~`:185` | `stripManualMark` | 可 grep（现行调用点即该方法） |
| T32 | `PaySignWorkflow`（god class，ADR-D87 已删；迁移脚本注释里还留着这个类名） | `resources/sql/pay-sign-txn-transin-migration.sql:2` | `PaySignWorkflow` | **仅在注释 / 脚本里能 grep 到，`src/main` 的 Java 代码里为 0** |

> ⚠️ T25 / T27 / T28 / T29 / T31 的字样在代码里**仍能 grep 到**（那些类、键、字段本身没被删，
> 被删的是「某个位置上的用法」）。**因此 NEVER 用「grep 得到 ⇒ 已经回退了」来判断**，
> MUST 打开对应行确认上下文；反过来 T23 / T24 / T26 / T30 / T32 **grep 代码一定为 0**，
> 它们只活在注释里 —— 这也是把它们搬进本文档的唯一理由。

### 本轮覆盖率自评

**分母（口径沿用用户实测，本轮未重测）**：`pay-sign-server/src/main` 共 107 个文件 / 13189 行，
其中 **Java 注释行 3607**、**XML / properties 注释行 219**，合计 **3826 行注释**。
mapper XML 里的那部分注释阶段一已抽（本轮只补了 Java mapper 接口侧的判据，见第 191~195 条）。

**分子**：
- 阶段一：140 条 + 墓碑 22 条（T1~T22）。
- **本轮：抽取 60 条（第 141~200 条）+ 矛盾 10 条（M1~M10）+ 墓碑 10 条（T23~T32）**，
  分布为 controller 11 条、config 与 resources 15 条、port 7 条、service 接口与门面 7 条、
  support / domain 8 条、event 与通知 2 条、mapper 与实体 10 条。
- 两轮合计 **200 条正文 + 32 条墓碑 + 10 条矛盾**。

**样板跳过（有意不抽，但计入分母）约 156 行**：
- 纯 `@param` / `@return` / getter-setter 说明 **76 行**（`util/RSASignUtils.java:24`~`:54` 最集中）。
- `util/RSASignUtils.java:78`~`:117` 那段**被注释掉的 `main()` 与造报文样例 40 行**
  （知识量为零，但**含密钥材料**，已作为 M6 的证据点记录，**值未回显**）。
- 一行式 DTO / 实体类注释约 **40 行**（如「XX 请求 DTO」这类与类名同义的说明）。

**零知识注释文件清单（读过、确认无可抽内容）**：
`PaySignServer.java`、`constant/PaySignErrorCodeEnum.java`、`entity/PaySignInfo.java`、
`entity/PaySignLog.java`、`mapper/PaySignLogMapper.java`、`model/request/PaySignGatewayRequest.java`、
`model/request/RequestContractAdvisoryReqDTO.java`、`model/response/BaseRespDTO.java`、
`model/response/PaySignGatewayResponse.java`、`model/response/RequestContractAdvisoryRespDTO.java`、
`model/response/RequestContractResultRespDTO.java`、`model/response/RequestSignInfoRespDTO.java`、
`resources/sql/pay-sign-schema.sql`、`resources/mapper/PaySignLogMapper.xml`（无真实注释）。
**仅有一行式类注释、无判据可抽**：`exception/PayGatewayException.java`、`entity/PayRefundDetail.java`、
4 个 model DTO、`config/VisibleThreadPoolTaskExecutor.java`。

**仍未 100% 覆盖的部分（明确留白）**：
- 五个大文件的**逐行**覆盖仍不足：`ContractDomainServiceImpl`（324 注释行）、
  `PaymentDomainServiceImpl`（297）、`CallbackDomainServiceImpl`（212）、
  `AppTerminationRequestMapper`（207）、`RefundDomainServiceImpl`（161）。
  阶段一按主题抽了契约与判据，本轮补齐了阶段一完全缺失的六个 ADR（**D32 / D47 / D84 / D92 / D99 / D113**）
  与 ADR-D115 的两类删除，但这些文件里**「逐步骤流程说明」类注释未逐条搬**
  —— 那类注释与代码结构一一对应，搬过来等于抄代码，判据密度低。
- `AppTerminationRequestMapper` 的 XML 侧注释（扫表 SQL 的四个坑）阶段一已覆盖，
  Java 接口侧的**逐方法说明**本轮未逐条抽。
- 测试目录（`src/test`）**不在本轮口径内**，但 M3 已指出 `AlipayTripSignCharacterizationTest`
  可能与现实现不一致，**下一轮 MUST 覆盖 `src/test` 的注释与断言**。

（本节完。**抽取口径与统计见本节开头说明；阶段一附录 NEVER 改写**，本轮所有新增均在本小节内；
若继续抽取其他模块，MUST 另起小节、编号从 201 / T33 / M11 起。）

## 附：pay-sign-server 测试断言与 DDL 补漏（2026-09-16，阶段三）

> **本轮覆盖前两轮完全没纳入口径的两块载体**：①`pay-sign-server/src/test`
> （**40 个文件 / 6563 行 / 注释 1486 行，全仓最多**）；②`src/main/resources/sql` 四个脚本
> （**129 条 `COMMENT ON` + 56 行 `--` 注释**）。编号接阶段二：正文 **201** 起、矛盾 **M11** 起、墓碑 **T33** 起。
>
> ⚠️ **本节所有 `路径:行号` 都是「阶段三删注释之前」的行号**。同一批作业的第二步已把 `src/main` /
> `src/test` 的多行叙述型注释删除，因此**照这些行号打开文件看到的是代码、不是那段注释** ——
> 本节即那些注释的唯一存档，「可 grep 原文短语」用于对照历史版本（`svn cat -r BASE <path>`，**NEVER 用 git**）。
> 保留下来的一行式护栏见本节末「保留的护栏」。

### 一、ArchUnit 门禁与事务边界冻结（`src/test/java/.../arch/`，4 个类）

201. **`arch/PaySignTransactionBoundaryArchTest.java:25`~`:204`** — 把「哪些方法带 `@Transactional`」与
     「其中哪些会出网」冻结成两份清单。**为什么冻结而不写成禁止规则**（`:28`~`:34`）：本模块历史上有
     6 个方法违反 AGENTS.md §5.2（7 条出口），写成禁止规则会立刻全红、随后被 `@Disabled` 掉、**护栏归零**；
     改成「冻结现状 + 逐条注明」后，纯搬迁批次 MUST 让两份清单一字不变，修边界的批次才让它变短。
     可 grep：`护栏归零`、`NEVER 靠放宽本类来让构建变绿`。
202. 同类 `:37`~`:64` 记 5A/5B/5C 三批的收敛与**每批判据不同型**：5A 四个方法摘 `@Transactional`
     **不需要**补偿，判据是「注解净效果为负」（三条返回路径末尾 `catch (Exception)` 吞一切、事务从不因业务失败回滚、
     事务内只有一次审计 INSERT）；5B `requestRefund` **适用** ADR-D8，同批补了 `compensateRefundQuery`
     + `POST /internal/payment/compensateRefundQuery`，`:54`「删掉那个补偿端点等于只做了 ADR-D8 的前一半，NEVER 删」；
     5C `notifyTerminationFailed` 是第三型（事务只包一条 CAS UPDATE、保护不了任何东西，却把异步通知的提交
     圈在 commit 之前），属**纯摘注解**。可 grep：`ADR-D8 的前一半`、`纯摘注解`。
203. 同类 `:142`~`:172`：`KNOWN_TRANSACTIONAL_OUTBOUND` **自 5C 起为空集**，`:167` 明写
     「**本集合为空是「已达标」，不是「还没填」**」；三个补偿端点本身出网但不带事务、**不该出现在这里**，
     出现即说明有人给补偿方法加了事务；`:172`「断言红了 MUST 去改代码，NEVER 往这个集合里补条目」。
     另 `:105`~`:119`：带事务的方法**实测 3 个**（`receiveSignResult` / `alipayTripRequestSignInfo` /
     `removeSignAgreement`），`requestPay` / `receivePayResult` / 两个补偿 / `receiveTerminationResult`
     **刻意不在此列**。可 grep：`不是「还没填」`、`NEVER 直接改期望值`。
204. 同类 `:151`~`:156` 是一条**反直觉的计数解释**：`releaseWalletBinding` 只调一次
     `accountDomainPort.agreeRelease`，但 `rejected.retMsg()` / `unreachable.cause()` 的 owner 是
     `RpcOutcome$*`、命中 `OUTBOUND_SINKS` 的 `com.chinasofti.huateng.rpc.` 前缀，于是**同一次出网被同时记成
     「RPC」与「账户域端口」** —— 摘事务后两条一起消失**不是漏改**。出网判定是**保守近似**（`:80`~`:83`）：
     BFS 命中五类出口即记一笔、同名重载合并、宁可多报，`NEVER 把某个出口从 OUTBOUND_SINKS 里删掉来消警`。
205. **`arch/SignStatusArchTest.java:16`~`:73`** — `APP_PAY_SIGN_INFO.SIGN_STATUS` 门禁三条：
     ①`updateBySeq`（WHERE 只有 `REQUEST_SIGN_SEQ`，是「迟到回调覆盖已解约状态」的来源）**唯一允许调用方**
     是 `ContractDomainServiceImpl.applyGatewayStatus`，只用于回填 `PAY_ACCOUNT_ID` 等非状态字段；
     ②4 条 CAS **MUST 只在 service 层调**（controller 直调等于把「返 0 行则回查再分流」漏写）；
     ③`javax` 只禁 Java EE 那几个包、**NEVER 写成禁整个 `javax..`**（`javax.crypto` / `javax.net` / `javax.sql`
     是 JDK 自带包、签名链路正当使用，一刀切会把门禁做成**永久红灯**）。可 grep：`永久红灯`。
206. **`arch/TerminationStatusArchTest.java:15`~`:70`** — 同形、语义独立：`updateStatus` / `updateFailReason`
     / `updateCompleteTime` 三条无 CAS 语句 **MUST 零调用方**，XML 里保留只为不动历史结构，
     `:37`「NEVER 因为「方法还在」就重新拿来写状态」；`:55` 记拆开写的后果 ——
     「状态与完成时间不再同生共死，中间崩一次就留下 `FAILED` 但没有完成时间的行」。
207. **`arch/WalletChannelGuardTest.java:17`~`:65`** — 把 ADR-D108（渠道码常量只准 `support/PaymentChannels`
     声明一次，收口前 **5 份副本**）与 ADR-D109（入口只准 `classify(...)` + 穷尽 `switch`，NEVER 退回布尔谓词
     加 `else`）固化成红灯。**与本轮删注释直接相关**：`:28` 明写「判定一律在**剥离注释后**的代码上做，
     因为那些 NEVER 告示本身就写着被禁止的写法」—— 这类门禁天然免受删注释影响。可 grep：`剥离注释后`。

### 二、状态机与白名单的行为锁（`src/test/java/.../domain/`，5 个类）

208. **`domain/SignStatusTransitionTest.java:14`~`:82`** — `SignStatusTransition` 四条：CAS 命中即 `DONE`
     且 **MUST 不回查**（用计数器锁死，防后人改成「先无条件回查再判断」多加一次 DB 往返）；0 行 + 库里已是目标态
     = 上游重放按成功处理；0 行 + 非目标态 = `CONFLICT`（**这一条就是「已解约通道被迟到的签约回调覆盖」那个必须挡住的场景**）；
     NULL / 脏值 / 大小写不符一律 `CONFLICT`、**NEVER 兜底成目标态**（「猜错方向会把「状态未知」当成「已经成功」」），
     `observedStatus` 仍返原始值供日志如实呈现；目标态本身解析不出来时也是 `CONFLICT`。
     存在理由：此前 `removeSignAgreement` 与 `applyGatewayStatus` 各写一遍该判定、一个正向一个取反（ADR-D40）。
209. **`domain/TerminationStatusTransitionTest.java:14`~`:80`** — 与上一条同形，且 `:16`~`:17` 记这些断言
     **不是恒真的**：做过**变异验证**（把 `CONFLICT` 兜底成 `IDEMPOTENT`、把回查改成无条件执行，逐条确认变红）。
     真实场景是 `expireScanning` 抢先打成 `FAILED`、迟到的解约成功回调不得改成 `SUCCESS`。可 grep：`变异验证`。
210. **`domain/StatusWhitelistTest.java:13`~`:69`** — 迁移白名单与 `PaySignInfoMapper.xml` 的 4 条 CAS、
     `UserPhoneChangeLogMapper.xml` 的扫表条件**逐条对齐**（`SIGN_SYNC_STATUS IN ('PENDING','FAILED')`；
     `SUCCESS` 唯一终态；历史行该列 NULL 语义是「不参与补偿」、**NEVER 被解析成 PENDING**）。
     `:31`~`:32` 点名整个改造要挡的那一个迁移：`UNSIGNED -> SIGNED`（APP 显示通道有效而渠道侧协议已注销），**NEVER 放开**。
     `:19` 记该组断言 2026-09-11 已在库上用合成数据实跑过（ADR-D12），本类是它的代码化留存。
211. **`domain/TerminationStatusWhitelistTest.java:12`~`:76`** — 与 `AppTerminationRequestMapper.xml` 的 6 条 CAS
     一一对应：`PENDING -> SCANNING | FAILED`；`SCANNING` 三个出口；**`FAILED` 只能复活成 `PENDING`、NEVER 直接转
     `SUCCESS`**（`:39` 标为「本轮的核心裁决（用户 2026-09-12）」，删掉即允许「已告知 APP 解约失败后、迟到的成功回调
     再改成成功」，对端会收到两条相反通知而补通知口径没有业务定义）；`SUCCESS` 唯一终态；
     `isNotifiable` MUST 与 `selectCompensableNotify` 的 `TERMINATION_STATUS in ('SUCCESS','FAILED')` 一致
     （放宽到 PENDING / SCANNING 会给 APP 发**假解约成功通知**，2026-08-26 修过一次）。
212. **`domain/TerminationFailReasonTest.java:11`~`:78`** — 锁死 `FAIL_REASON` 的写 / 读格式互逆。
     存在理由是一个**已发生的缺陷**（2026-09-12，ADR-D47 引入、同日发现）：写入方把「需人工核对」的内部说明
     前置拼进该列，而 `asyncRetryTerminationNotify` 的补偿重发会把该列原值当成 `terminationResultMsg`
     **发给终端用户**。四条断言：写读一字不差；剥离结果 **NEVER 残留标记**（用 `contains` 而非 `startsWith`，
     将来改成后置拼接同样要红）；无标记原样返回；带标记但分隔符缺失（历史脏数据 / 手工改库）MUST 返空串
     （「宁可让下游回落到「存在扣费失败订单」这种默认文案，也 NEVER 把内部说明整段发出去」）；
     标记字面量同时是 mapper `INSTR` 幂等判据的入参与运维检索关键字，改动 MUST 同步 mapper Javadoc 与 ADR-D47。

### 三、mapper 离线 SQL 断言（不连库，`XMLMapperBuilder` 渲染后取文本，2 个类）

213. **`mapper/AppTerminationRequestMapperSqlTest.java:18`~`:267`** — 存在理由：**解约状态机的并发保证只在
     那些 WHERE 里**，Java 侧白名单枚举只做解析与文档化；某条 CAS 的前置条件被删掉后 SQL 依然语法合法、
     编译与其它单测全绿，只会在生产上表现为「已超时打成 FAILED 的申请被迟到回调改成 SUCCESS，APP 收到两条相反通知」
     —— 2026-09-12 之前主收口路径就是那样。`:28`「**NEVER 把断言放宽成「只判断包含 TERMINATION_STATUS」**」。
214. 同类的五组不变量：①6 条 CAS 的 WHERE **MUST 同时带主键与前置状态**（只带主键 = 无条件覆盖、只带状态 = 全表更新）；
     ②三条「产生新通知」的语句 **MUST 把通知轮次归零**（沿用残留轮次会让新通知一上来就接近
     `app.notify.max-retry-count`，首投失败后 `selectCompensableNotify` 的 `NVL(NOTIFY_RETRY_COUNT,0) < max`
     再也扫不到它）；③`markSuccess` / `rejectScanning` **MUST 落完成时间**（2026-09-12 前由无 CAS 的
     `updateCompleteTime` 单独写，合成一条后 `COMPLETE_TIME` 与状态同生共死，**NEVER 再拆开**）；
     ④通知补偿的终态白名单 NEVER 去掉；⑤`where and` 语法保护（防 `include` / 条件标签改动渲染出语法错误）。
215. 同类 `:134`~`:143`：整份 XML 里 `TERMINATION_STATUS` 的**字面量赋值次数固定为 7 处**
     （`markScanning` / `revertScanningToPending` / `rejectPending` / `markSuccess` / `rejectScanning` /
     `expireScanning` / `reactivateFailed`），用来挡「悄悄新增一条不带 CAS 的状态写语句」——
     那种语句加进来编译与按名单的断言都不会红。`updateStatus` / `updateFailReason` 用 `#{status}` 占位符、不计入。
     **数字变了 MUST 先确认新语句带 CAS，NEVER 直接改这个期望值。**
216. 同类 `:159`~`:167`：两条「解约结果矛盾」留痕语句的三条不变量（CAS / `INSTR` 幂等闸门 / 截断到 512）
     以及**前置状态 MUST 分别是 `FAILED` 与 `SUCCESS`**（写反等于在错误的那一半留痕）。少了 CAS 会把任意状态的行
     都标成需人工；少了 `INSTR` 闸门则支付中心每次重推都再前置拼一遍标记，512 字符很快挤满、原始原因反被截掉；
     一旦它们写了 `TERMINATION_STATUS` 或 `NOTIFY_`，就等于替业务决定「要不要反悔」。
217. 同类 `:198`~`:235`：`selectCompensableChannelSync` 的**四个坑**（`docs/domain/outbox.md` §二）——
     `ROWNUM` 与 `ORDER BY` 同层会先截断再排序（最旧记录可能永远排不进这一批）；重试次数不套 `NVL` 时
     该列为 NULL 的行比较结果 UNKNOWN、一条都捞不到；状态改成「不等于 SUCCESS」的黑名单会把 NULL 历史行
     （凭空再删一次通道）与 `MANUAL`（覆盖人工结论）一起捞进来；`TERMINATION_STATUS` 放宽到含 `FAILED`
     会给没有通道要删的申请发起清理。外加：三条会写 `CHANNEL_SYNC_STATUS` 的 update **MUST 带 MANUAL 闸门**，
     而 `markChannelSyncManual` 自己是**进入** MANUAL 那一条、前置条件是 `FAILED`、不在此列。
     另 `:200` 记一条事实：这条 SQL **2026-09-12 才接上调用方，此前 5 条 CHANNEL_SYNC 语句全是死代码**。
218. **`mapper/PayRefundDetailMapperSqlTest.java:15`~`:213`** — 存在理由：批次 5B 摘掉 `requestRefund` 的
     `@Transactional` 后，「退款已发出、本地停在 `PROCESSING`」这类单子**只能靠这条扫表 SQL 被捞出来**；
     写错不报错、只会让补偿永远 `scanned=0`，那批单子永久悬挂（对账时才发现，且已无法区分是我方漏收口还是对方真没退）。
219. 同类的四组不变量：①回查扫表四条（`PROCESSING` 白名单 —— 只有它代表「退款请求确实已出网」，放宽成「非终态」
     会去问支付中心一笔它根本没收到的退款单；`NEXT_REQUEST_TIME` MUST 允许为空；滞留判断与排序 MUST 套
     `NVL(LAST_REQUEST_TIME, CREATE_TIME)`；`ROWNUM` MUST 在已排序子查询外层）；②两条写回状态 MUST 是 CAS 且
     **主键是「退款单号 + 交易日期」两列**（分区表 + 本地唯一索引 `UK_PAY_REFUND_DETAIL_NO`，
     **少写 `TXN_DATE` 就等于跨分区更新**）；③退避语句 **NEVER 动 `REQUEST_COUNT` 与 `REFUND_STATUS`**
     （前者语义是「我方发起退款的次数」，回查不是发起退款；退避只是「等下次再问」不是订正）；
     ④收口语句 MUST 用 `NVL` 兜住三个单号与退款时间（§3.2 应答可能缺项，直接覆盖会把 §3.1 受理时拿到的值擦空，
     而那几个号是事后对账的唯一线索）。
220. 同类 `:135`~`:179`：跨表对账两条扫表 SQL 的三条不变量（带 `TXN_DATE >=` 下界 —— 缺它就是全分区扫，
     代价是「畸形 `TXN_DATE` 的行永远扫不到」，那是**已登记的已知盲区**、**NEVER 靠去掉下界来覆盖**；
     `ROWNUM` 限流且在排序子查询外层；只统计 `REFUND_STATUS = 'SUCCESS'` —— 把 `PROCESSING` / `RETRY` 算进来
     会把在途退款当成已退、汇总反而被这条「对账」改错），以及**两条 SQL 的差别 MUST 恰好是 `EXISTS` 与 `NOT EXISTS`**：
     写反了没有任何编译或运行错误（`updateRefundSummary` 对 B 类影响 0 行、对 A 类 1 行，两者都「没抛异常」），
     唯一后果是**一批真正的坏账从告警里消失**。`:147` 顺带守「SQL 正文里没有行注释」（Druid WallFilter 静默失效）。

### 四、签约 / 咨询 / 查询侧特征测试（`service/impl/`，5 个类）

221. **`service/impl/ContractDomainCharacterizationTest.java:31`~`:49`** 给出全模块「特征测试」的定义：
     **断言的是当前行为、不是「应该怎样」**；用途只有一个 —— 拆分时凡断言变红就说明这次搬迁改了对外可见行为、
     不是重构而是改需求。因此 **NEVER 为了让某次改动通过而放宽这里的断言**；真要改行为 MUST 先立 ADR、再连同断言一起改。
     可 grep：`不是重构而是改需求`。
222. 同类钉住三条口径 + 两条**已作废的旧契约**：钱包（`0B`）的 `requestSignInfo` **现在必须能走到支付中心**
     并拿回 SDK 参数（`:58`~`:65`；用例此前叫 `walletRequestSignInfoIsRejectedAndNeverReachesGateway`、
     断言 8001 + 永不到网关，该契约 2026-09-15 作废：支付中心 §1.1 withholding 场景强制要求 `requestSignSeq`，
     实测 `code=9999「代扣签约请求流水号不能为空」`，钱包不在支付中心签约就永远扣不出去，
     `PAY_TXN_DETAIL` 里 `0B` 渠道零条 SUCCESS）。**NEVER 回退。**
223. 同类其余：网关成功且 `data.data` 有值才原样回 SDK 参数、未成功统一收成 9001（**NEVER 退化成「成功但参数为空」**）；
     签约侧流水 `OPERATION_TYPE` 恒为 `SIGN`（13 个内部细分名由 `convertOperationType` 归并，抽 `writeLog` 时
     **MUST 不变**，否则运营查流水的口径静默变了）；咨询打的是 `pay.sign.contract-advisory-url`
     （`:144`~`:147`：URL 错配在生产上表现为 `code=600 操作失败`、**不是 404**，极难定位）。
224. 同类 `:185`~`:217`：解约申请首次落 `PENDING` 三件套（`TERMINATION_STATUS=PENDING` +
     `NOTIFY_STATUS=PENDING` + `NOTIFY_RETRY_COUNT=0`）且**不碰支付平台** —— 「不碰」是业务口径（T+4 才确认）
     而非性能优化，**在这里提前解约，用户满 4 天前的欠费就再也扣不到了**；同一流水已有非 `FAILED` 申请时拒 8009，
     白名单只放 `FAILED` 复活，**无条件 insert 会撞 `UK_ATR_REQUEST_SIGN_SEQ` 并被外层 catch 成 9001，
     那条流水从此永久无法再申请解约**。
225. **`service/impl/ContractResultCharacterizationTest.java:21`~`:134`**（IF8A-22 非钱包主干，2026-09-16 新增）
     — 存在理由写得很直白：该方法是 `ContractDomainServiceImpl` 里最复杂的（109 行、4 个协作者），而**非钱包主干
     此前零覆盖** —— 唯一相关的 4 条断言传的是 `walletQuery()`，在方法第 3 行的钱包分支就 `return` 了、
     **从未进入主干**；补上本类之前，任何对该方法的重构都是「无护栏改控制流」。可 grep：`无护栏改控制流`。
226. 同类六条口径：①本地已签约且数据完整 ⇒ 直接返回、**绝不出网**；②流水号命中的记录不属于报文里的 `thirdUserId`
     ⇒ 按「签约记录不存在」回绝且不出网（**防「凭流水号探测他人协议」的归属校验**）；③网关业务失败 ⇒ `SYSTEM_ERROR`
     且不改本地状态；④本地有记录 + 平台返 SIGNED ⇒ 走 `markSigned`；⑤**孤儿协议**（平台说已签约、本地无记录）
     ⇒ 照实返回但 **NEVER insert**（本接口报文里拿不到 `CARD_ID` / `CARD_TYPE`，凭空造记录会让解约链路拿
     `NO_ACCOUNT_CARD` **卡死在 SCANNING**，生产已发生 3 条、只能改库清理）；⑥网关 `data` 为空 + 本地无记录
     ⇒ 归一成 `NOT_SIGNED` 且仍返 `0000`。
227. **`service/impl/AccountReadCharacterizationTest.java:24`~`:233`** — 覆盖 `AccountDomainPort` Javadoc 那条
     硬前置条件（「要收读 MUST 先给这三处补单测，NEVER 顺手一起改」）里走 `queryUserInfo` 的两处。
     **核心是「retCode 要不要看」两个调用点原先答案不一致**：`queryWalletBindingResult` 要求 `retCode=0000`
     才认（active 三条件之一），而 `resolvePaySignInfoFromAccount` **完全不看 retCode**、只要 `channel` 非空
     就往支付入参上写 —— 后者是缺陷而非设计：账户域返 `8004`（该用户无此通道）时 DTO 里残留的 `channel` /
     `reqContractNo` 会被当成有效签约信息**送去真实扣款**。已按 **ADR-D94** 对齐成「必须 SUCCESS 才采信」，
     修复后在 `validatePaySignInfo` 拦成 `8011`、**出网次数 0**。`:165`「NEVER 把断言放宽成「只要不报错就行」：
     它守的是资金安全，不是返回码美观」。
228. 同类两条易误改的实测口径：`atLeastOnce()` **不能改成 `times(1)`**（网关返失败后 `requestPay` 还会再查一次
     支付状态，单笔请求**出网 2 次**，改了会在无关改动上假红）；夹具的 `NotFound` **刻意没有数据槽位**
     （「拒绝了还能读到残留字段」正是 ADR-D94 那个缺陷的形状，类型上就不该表达得出来）；
     账户域**不可达**收成 9001、与「业务拒绝」不同码（不可达可重试、业务拒绝重试一万次也不会变）。
229. **`service/impl/TerminationInternalReadCharacterizationTest.java:24`~`:183`**（第三处读调用，IF8A-75 直接解绑）
     — 钉住 `CARD_ID` / `CARD_TYPE` 的**真实来源是账户域、不是签约表**：`APP_PAY_SIGN_INFO` 这两列**全库为 NULL**
     （签约链路从不写它们），而 `APP_TERMINATION_REQUEST` 两列 **NOT NULL** —— 「从账户域取回来塞进入参」这一步
     一旦丢，补建解约申请必然 `ORA-01400`（2026-09-08 实测），`unbindAgreementFillsCardInfoFromAccount()`
     是这条链路的判据、**NEVER 删**。另一条同等重要：账户域**取不到时不中断**（既不抛也不改码），
     `:74`~`:76` 把这条同时登记成**缺陷存证**（线上该列全 NULL，故这条路径在真库上会 `ORA-01400`，
     是「取不到辅助信息不放大成接口不可用」这个取舍的代价，真要改 MUST 先立 ADR）。
     `:112`~`:115`：「端口万一真抛了也不外溢」单独立一条用例，**NEVER 因为「端口不该抛」删掉它**。
230. **`service/impl/AlipayTripSignCharacterizationTest.java:23`~`:138`**（上一轮 M3 点名的疑点，本轮复核结论
     见 §矛盾 M11） — 它是全模块唯一**不经支付中心、直接把 `APP_PAY_SIGN_INFO` 写成 `SIGNED`** 的入口
     （渠道侧已签好、我方只做同步确认），因此没有网关调用也没有回调收口，**一旦放宽已签约校验就会出现
     同一用户同渠道两行签约记录**。四条断言：直接落 SIGNED 且 `NEVER 调支付中心`；不返 SDK 参数；已签约即拒（8013）；
     成功**只写一行**审计流水且该行同时带 `OPERATION_TYPE='SIGN'` + `SIGN_STATUS='SIGNED'` + 报文 + `RESULT_CODE`。

### 五、支付与退款侧特征测试（`service/impl/`，5 个类）

231. **`service/impl/PayResultCallbackCharacterizationTest.java:22`~`:190`** — 该方法此前**零覆盖**
     （逐方法量覆盖时确认：全模块 129 个测试没有一个调用它），而它正是 **2026-08-26 生产事故的那条链路**
     （订单 `GT20260826210647653586419` 循环重推 8 分钟；同批 4 笔已扣款成功的订单 `PAY_STATUS` 长期卡在
     `PROCESSING`）。事故后立的每条规则此前**只写在方法体注释里、没有任何自动化断言守着**。
232. 同类六条：①**定位键 MUST 是 `merchantOrderNo`**（回调报文的 `orderNo` 是支付中心的订单号、只能落
     `PAY_CENTER_ORDER_NO`；用错键必然命中 0 行，把「键传错」与「订单不存在」混成同一种现象 —— **那 4 笔的成因**）；
     ②`DEBIT_REQUEST_RESULT` MUST 与 `PAY_STATUS` 同步回写（APP 读的是前者，只改后者会让已扣款成功的交易
     在 APP 上一直显示失败）；③影响 0 行且本地已是目标状态时 **MUST 继续走 `syncDebitStatus`、NEVER 提前 return**
     （重推是收敛 `GATE_TXN_PAY` 的唯一机会，在这里 return 非 `0000` 等于亲手造死循环）；④影响 0 行且非目标状态
     **NEVER 回 `0000`**；⑤达到推送上限则回 `0000` 止推但 **MUST 同时把回调标成 `MANUAL`**（NEVER 只止推不留痕）；
     ⑥远端同步失败 **NEVER 抛异常回滚**（本地状态与 `PAY_CALLBACK_LOG` 是支付中心结果的唯一凭据，
     回滚等于丢证据 —— 事故当天循环期间该表**零条落库**）。
233. **`service/impl/RefundRequestCharacterizationTest.java:22`~`:139`** — `requestRefund` 此前**零覆盖**，
     而它是本模块唯一「碰钱 + 三条写 + 一次出网」的入口，方法头逐条论证过的三条不变量此前**没有一行代码守着**：
     ①**留痕 MUST 先于出网**（`insert` → `markRequesting` → 调支付中心；该方法刻意不带 `@Transactional`，
     包成事务后网关超时会把「留证据」的 INSERT 一起回滚，结果是本地连这一行都不存在而对方可能已受理甚至已退款成功，
     事后既无从对账也无从补偿；摘掉后最坏停在 `PROCESSING`，由 `compensateRefundQuery` 回查收口）；
     ②**汇总 MUST 在明细置 SUCCESS 之后**（`updateRefundSummary` 按 `PAY_REFUND_DETAIL` 全量重算，顺序颠倒会漏掉本笔）；
     ③网关失败 MUST 落 `RETRY` 且 **NEVER 重算汇总**（这一笔还没成功，算进已退总额会让可退金额上界偏小、把合法退款拒掉）。
     另：汇总回写命中 0 行时只打 ERROR、**对上游仍答 `0000`**（回非 0000 会让上游以为没受理而重复申请，
     该中间态由 `compensateRefundSummary` 兜住）。
234. **`service/impl/PayTxnDateResolutionTest.java:11`~`:84`** — `PAY_TXN_DETAIL.TXN_DATE` 的取值口径：
     **优先用发起方透传的行程日、NEVER 默认本地当日**。`GATE_TXN_PAY` 与 `PAY_TXN_DETAIL` 按 `(ORDER_NO, TXN_DATE)`
     一一对应且两表都以该列月分区，支付侧一取「本地当日」，跨零点那批订单就落在与行程侧不同的日期上 ——
     **两表再也 join 不上、对账取不到、补偿也找不回来**；已实测出站到落库滞后可达 **94 分钟**，22:26 之后出站随时能踩。
     这个缺陷**编译、启动、单笔手工验证全都发现不了**（白天跑一整天都对），因此断言 MUST 用「明显不是今天」的日期
     （用例里是 `2025-01-01`），**NEVER 拿 `LocalDate.now()` 当输入 —— 那样把 bug 写进期望值里，网就是空的**。
     无参重载给 `PAY_REFUND_DETAIL` / `PAY_CALLBACK_LOG` 用（各自独立事件），两个口径 **NEVER 混用**；
     `:81`~`:83` 另记「**NEVER 退回反射调用**」（反射版在方法搬家后抛的是「反射调用失败」，看起来像测试坏了而不是行为变了）。
235. **`service/impl/PaymentRefundQueryCompensationTest.java:30`~`:199`** — 只盯三件「改错了不会有编译错误、
     也不会有别的测试变红」的事：①收口 CAS 命中 0 行时 **NEVER 当成功、且 NEVER 继续重算汇总**
     （0 行意味着这一行已被退款回调或另一副本收口，继续算就是用本次回查结果覆盖别人写对的口径）；
     ②真收口成功时 **MUST 无条件重算一次汇总**（摘事务后「明细已 SUCCESS、汇总没重算」这个中间态只有这里能兜住，
     省掉那一次等于 ADR-D8 只做了一半）；③回查没拿到终态时只推退避时间、**NEVER 落终态**。
     另两条后补的：退避 CAS 也命中 0 行时仍计 `skipped` 且 NEVER 落终态（此前那条 `if (delayed == 0)` 分支零覆盖）；
     单条抛异常 **NEVER 中断整批**（守 `OutboxScan.run` 的 `onError` 回调 —— 一行的网关超时能把整批掀掉时，
     表现是「每轮都只处理到第一条坏数据为止」而端点仍返 `0000`）；未配置 `pay.sign.refund-query-url`
     MUST 直接返错且一条都不扫。
236. **`service/impl/PaymentRefundSummaryCompensationTest.java:19`~`:122`** — 补的是 `requestRefund` 第 9 步
     （`updateRefundSummary`）失败或漏跑留下的窟窿：`PAY_REFUND_DETAIL` 是唯一账本、已经对了，
     `PAY_TXN_DETAIL` 的两列汇总没跟上，账面上「可退金额 = 已付 − 已退」偏大。两条口径：**A 类 MUST 按影响行数
     判成败**（返 0 行却计 `submitted` 等于对外宣称「这批账已经对齐了」而实际一行都没动，判据 MUST 是
     「影响行数 > 0」、**NEVER 是「没抛异常」**）；**B 类 MUST 一行都不改**（明细已 SUCCESS 而 `PAY_TXN_DETAIL`
     里根本没有该 `ORDER_NO`，调它没有任何修复效果、却会把一批真正的坏账混进「已处理」口径）。
     `:35`~`:41` 两条工程判断：**刻意用 mock 而不连库**（A 类在目标库里当前 **0 行**、B 类 **6 行**，
     靠真实数据构造不出 A 类分支）；**刻意不注 `PaySignProperties` / `PaySignGateway`**（这条补偿不出网，
     谁把出向调用混进汇总路径会立刻 NPE 而不是静默走通；那两件事 **NEVER 合并**）。
     `:47` 记 B 类样本取自目标库实测的 6 条之一（**2026-08-26 那次生产事故当晚留下的**）。

### 六、解约簇特征测试与护栏（`service/impl/`，6 个类）

237. **`service/impl/TerminationCallbackTrunkCharacterizationTest.java:24`~`:188`** — 为什么单独建类：
     `CallbackDomainCharacterizationTest` 里那四条解约用例**全部在入口段就返回**（钱包分流 / 申请不存在 /
     终态幂等 / 非 SCANNING 拒绝），四条断言清一色是 `verify(deleteByUserAndVendor, never())`，也就是说
     **「进了主干之后会发生什么」一行都没被执行过**；逐方法量覆盖确认 `:339`~`:489` 那 **148 行**
     （本地事务收口 + CAS 三分支 + 通知 + 通道清理投递）零覆盖。`:31`「**「grep 到方法名」NEVER 等于「被覆盖」**」
     —— 这个类就是那次量化的产物。可 grep：`NEVER 等于「被覆盖」`。
238. 同类四条不变量：①**APP 通知 MUST 在通道清理投递之前**（account-server 慢或不可达时不该把 APP 通知拖住，
     而通道清理已落 `PENDING`、补偿一定会重推；顺序写反编译照样通过）；②`markSuccess` 与
     `initChannelSyncPending` **只在本次调用真收口时成对发生**（`IDEMPOTENT` 也去置 PENDING 会把已 SUCCESS 的
     通道同步打回待投递、通道被重复删一次）；③`CONFLICT` / `IDEMPOTENT` 两条都 **NEVER 补发成功通知**，
     但**只有 `CONFLICT` 落人工核对留痕** —— 两者合并会把「双路撞同一个 CAS」这种设计内常态报成需人工核对
     （2026-09-14 实测）；④失败分支 **NEVER 删签约记录**且流水 `SIGN_STATUS` 落 `FAILED`。
     另 `:188`：`cardId` / `cardType` **刻意不塞进回调报文**、主干 MUST 从解约申请表补齐（2026-09-09 事故）。
239. **`service/impl/TerminationExecutorGuardTest.java:21`~`:118`** — `executeTermination` 的三条异常路径，
     **状态归属不对称**：①网关**抛异常 = 结果未知** ⇒ MUST 保持 `SCANNING` 等 `processTermination` 主动查协议状态收口，
     **NEVER 退回 `PENDING`**（支付平台可能已受理，退回会让扫表任务再发一次解约）；②网关**明确答失败 = 没发出去**
     ⇒ 这时才 MUST `revertScanningToPending` 把执行权交还扫表；③回退的 CAS 命中 0 行（已被回调收口）⇒
     只告警、**仍然抛异常**、NEVER 吞成成功。两条路径都以抛 `TerminationException` 收尾，差别只在**有没有回退状态**
     —— 「这个差别没有任何编译期保护，改错了也不会有人发现，直到某天重复解约」。
240. **`service/impl/TerminationProcessorGuardTest.java:34`~`:258`** — `TerminationProcessor`（解约扫表主处理器）
     **此前整个类从未被实例化过**（夹具里 `terminationCompensationService` 是 mock，扫表链路够不到它）。
     两支各带一条不对称规则：**SCANNING 支**查不通 / 查到「仍已签约」都 MUST 走 `expireIfTimedOut` 而不是直接
     `SKIPPED`（否则查不通的记录就是**无上限静默重试**，既没有终态也没有人知道），确认 `UNSIGNED` 才复用回调收口
     （**NEVER 在这里重写那套写操作**）；**PENDING 支**欠费查询未成功 **MUST NOT 继续解约**（放行等于在用户可能
     仍欠费时解约）、CAS 未抢到 PENDING MUST 直接放弃（否则同一笔重复发解约）、只有网关明确失败才回退 PENDING。
241. 同类 `:155`~`:156` 记一条**测试写法陷阱**：`rejectPending` **MUST 显式打桩返 1** —— 它的 WHERE 带
     `TERMINATION_STATUS='PENDING'` 是 CAS，而 Mockito 对 `int` 的默认返回 0 会让用例静默落进下面那条
     「已被别人接手」的分支。另 `:173`~`:175`：判定欠费与写 FAILED 之间隔着一次查欠费 RPC，
     这条可能已被 execute 接口抢成 SCANNING 或被回调收口成 SUCCESS，此时 **NEVER 给 APP 发解约失败通知**。
242. **`service/impl/TerminationRejectGuardTest.java:28`~`:248`** — `/internal/termination/notifyFailed` 与
     `/internal/termination/checkFailedOrders` 此前**零覆盖**，而前者恰好是「把 PENDING 打成 FAILED 并对 APP
     发失败通知」的唯一入口 —— 「一旦这里判错，用户会收到与支付平台实际状态相反的通知，且没有第二条路径能纠正」。
     三条口径：已是 `FAILED` 是**幂等成功**（`0000`）、不再发第二条通知（Quartz 重推是常态：返错会让任务日志一片红、
     重发会让 APP 收到重复的解约失败）；CAS 落空 ⇒ **CONFLICT 返错且 NEVER 发通知**（此刻库里那条可能已被 execute
     抢成 SCANNING、甚至被回调收口成 SUCCESS）；`failReason` 空缺时回落成「存在扣费失败订单」，
     且**写进 CAS 的是回落后的值**（断言落在「CAS 收到的第二个参数」上 —— 回落值只有这一处能验，
     而它是 APP 侧展示给用户的失败原因文案）。
243. 同类另两条**处置刻意相反**的分支：库层抛异常时 **MUST 包成 `TerminationException` 往外抛、NEVER catch 后返错**
     （写库失败意味着「状态到底改没改」未知，对 Quartz 返 `9001` 会让那条任务被记成「已处理、失败」，
     抛出去才会留下堆栈并让任务重试）；`checkFailedOrders` 里**闸机域答 `null` ⇒ 9001，NEVER 当成「没有失败订单」**
     （`hasFailedOrder` 默认值是 `false`，把「查不到」翻译成 `false` 并返 `0000`，调用方会据此**放行解约** ——
     而那名用户可能正欠着扣费失败的钱）。
244. **`service/impl/WalletTerminationCharacterizationTest.java:18`~`:112`** — 补的是「层 3 动工前的最后一块空白」：
     逐条核对三个入口的六条渠道分支后，只有 `requestTermination` 的钱包支（`releaseWalletBinding`）零覆盖，
     按 **ADR-D108 续（二）「零覆盖的方法 NEVER 先拆」**，这个类必须先存在。四条不变量：钱包解绑**不建解约申请、
     不出网调支付中心**（由账户域 `requestAgreeRelease` 同步完成，与 T+4 扫描链路完全无关；误接进解约申请流程
     会给钱包用户凭空造一条**永远等不到回调的 SCANNING 记录**）；渠道号 **MUST 用 `PaymentChannels.walletCode()`**、
     NEVER 透传请求原值（可能带空白或大小写差异）；`BizRejected` 与 `Unreachable` 分开处置（ADR-D45：前者把对端文案
     带出来、后者只给通用文案并把栈打进日志，两者都 NEVER 报成成功）；缺 `cardId` / `cardType` 时 **NEVER 调账户域**
     （那个端点的参数校验会失败，等于把一次必然失败的出网当成业务分支）。

### 七、回调、通知与加签（3 个类 + 1 组事件断言）

245. **`service/impl/CallbackDomainCharacterizationTest.java:25`~`:146`** 四条都对应**已发生过的生产事故或已立 ADR
     的决定**：①签约成功回调 **NEVER 直接调 `appNotifyService`**（ADR-D32 / 2.0.75：该方法带 `@Transactional`，
     事务内投递通知一旦随后回滚，APP 已收到「签约成功」而 `APP_PAY_SIGN_INFO` 并没有那行；现在只发
     `SignResultCommittedEvent`、由 `SignResultCommittedListener` 在 `AFTER_COMMIT` 投递）——
     `:32`「**本类是这条不变量的唯一自动化断言点**：把通知调回来编译照样通过、单跑接口也「看起来正常」」；
     ②签约失败分支既不落签约主表也不发事件、只留流水且 `APP_PAY_SIGN_LOG.SIGN_STATUS` 落 `FAILED`；
     ③解约回调**只接受 `SCANNING`**（白名单、不是「非终态即可」：`PENDING` 尚未做未结清欠费校验，放行等于绕过前置校验
     直接删签约记录并通知 APP 解约成功）；④终态幂等短路返成功（支付中心会重推同一笔，第二次 MUST 不再删一遍通道、
     也 MUST NOT 对上游报错引来更多重推）。
246. 同类 `:89`~`:96` 是另一条**已作废契约**：钱包（`0B`）签约成功回调**必须被接受并落 `APP_PAY_SIGN_INFO`**
     （用例原名 `walletSignResultCallbackIsRejected`、断言 8001「钱包支付不支持签约结果回调」，2026-09-15 作废：
     钱包也在支付中心建代扣签约，而 `APP_PAY_SIGN_INFO` 是 `requestPay` 取 `requestSignSeq` 的权威来源 ——
     拒掉回调等于「支付中心侧已签约、我方表里零行」的静默不一致）。**NEVER 回退。**
247. **`service/impl/AppNotifyCompensationCharacterizationTest.java:34`~`:202`** — `AppNotifyServiceImpl`
     此前**整类零实例化**（在全仓 21 个测试文件里只以 mock 身份出现，473 行、6 个 public 一行没跑），
     而这两个入口是通知投递链路上**唯一带预算与白名单**的地方。四条：①`compensateSignNotify` 的重试预算
     **每轮 MUST 只 +1**，且计数刻意放在**提交重发之前**（回写本身可能丢，先落库才有上限保证），
     并对 `PENDING` 与 `FAILED` 一视同仁（`PENDING` 不计数就会被下一轮反复扫到、退化成无上限重复通知），
     配套口径是「`updateNotifyStatus` 的失败分支 NEVER 再递增」，否则一轮涨 2、3 次预算 2 轮用完；
     ②单条失败 **NEVER 中断整批**；③`resendSignNotify` 的**双白名单**（必须有 `SIGNED` 签约记录**且**必须有
     `RECEIVE_SIGN_RESULT` 流水 —— 放宽任一条 = 凭一个流水号给 APP 造一条假通知，而 APP 侧无幂等、污染无法回滚）；
     ④人工重放**同步投递且 NEVER 占用重试预算**（走异步后返回值只剩「已提交」，与本接口「告诉调用方这次到底通没通」
     相悖；占用预算会让真实故障少一次自动重试机会）。
248. **`support/AppNotifySignerTest.java:12`~`:102`** — 出向加签此前**零测试覆盖**（`buildItpSign` / `itpSignKey`
     / `setSign(` 在 test 下零命中），`:16`「那意味着「112 个用例全绿」对这次搬迁**什么都没证明** ——
     源串少拼一个字段、排序换个口径、大小写变一下，测试照样全绿，而对端会开始验签失败」。
     **期望值是独立算出来的**（7 个字段 `key=value` 收集 → 字典序排序 → `&` 连接 → 末尾 `&key=<密钥>`，
     在 Java 之外单独算 SHA-1 / MD5），`:21`「**NEVER 用「跑一遍把输出粘进来」的方式更新这两个常量**」。
249. 同类 `:26`~`:33` 记一条**本类第一次运行就查出的既存缺陷**：独立算出的期望值与实现不一致，逐个变体反推源串后确认
     —— `bizData` 那一段用的是 `Map` 的**插入顺序**而不是字典序，即 `JSONWriter.Feature.MapSortField`
     **在这条调用上没有生效**；后果是同一份 `bizData` 只要构造顺序不同算出的 `sign` 就不同，对端按自己的顺序
     重建 Map 验签会失败。**本批刻意不修**（§5.2 安全红线：改加签 MUST 人工复核 + 与对端重新联调），
     因此那两个摘要常量钉的是**今天的真实行为、不是应然**；修复那天它们会变红，
     `:33`「**那时 MUST 把它当成一次有意的契约变更，NEVER 直接把新摘要粘进来了事**」。
     另三条：`signType=00`（免签）/ 未约定值 / 空值一律返 `null`（**NEVER 退化成默认用某个算法**）；
     `:97`~`:101` 密钥为空时**仍然出签**（源串末尾是 `&key=`）是搬迁前的原样行为、本条不是赞成它而是把它钉住，
     要改成「没密钥就不签」MUST 当成有意的契约变更并同步对端。
250. **`service/impl/ChannelSyncDelivererTest.java:25`~`:145`** — ADR-D48 整套 outbox 的**执行点**，此前**一行未跑**
     （`TerminationCallbackTrunkCharacterizationTest` 只 `verify` 了它被调用、而夹具里它是 mock）。五条：
     `Ok` ⇒ 落 `SUCCESS` 且 **NEVER 递增重试次数**（那是失败侧的计数）；`BizRejected` ⇒ **MUST 先
     `increaseChannelSyncRetryCount` 再 `markChannelSyncManual`**（后者的 CAS 要求前置态已是 `FAILED`，
     顺序写反 CAS 命中 0 行 —— 表现不是报错，而是**这一笔永久留在补偿队列里无限重推**；本类用 `InOrder` 钉住，
     因为它是纯顺序耦合、编译器与单条 `verify` 都发现不了）；`Unreachable` ⇒ 只 +1 并落 `FAILED`、**NEVER 转人工**；
     落库自身异常 **NEVER 上抛**（两个调用方都在「本地已提交」之后调它，抛出去只会让上游误判失败）；
     `CHANNEL_SYNC_RESULT` 是 `VARCHAR2(1024)`、写入 **MUST 截断**否则 `ORA-12899`。
     另：转人工的原因里 MUST 带上对端 `retCode` / `retMsg`，否则运维拿不到可操作信息。

### 八、夹具与 support / 纯函数护栏（10 个类）

251. **`service/impl/PaySignFacadeFixture.java:40`~`:267`** — 本模块所有特征测试的地基，四条**工程约束**：
     ①**刻意不起 Spring 上下文**（起上下文会拉起 Druid 与 mybatis-adaptor，本机没 Oracle 就跑不了，
     「测试也就永远不会被人真的执行」）；②**装配 MUST 走构造器、NEVER 退回 `ReflectionTestUtils.setField`**
     （ADR-D96：四个领域服务的协作者已全部 `final` + 构造注入，于是「夹具与被测类的协作者集合不一致」
     由**编译器**拦住；原先按字符串字段名注的四组 `injectXxx` 少注一个不会有任何提示、只留一个 `null` 字段等着踩，已全删）；
     ③**与 `service.impl` 同包是有意的**（`writeLog` / `isGatewaySuccess` 等包级方法要能直接断言，
     **NEVER 挪到别的包再靠反射调私有方法** —— 那样重命名方法时测试不会编译失败，只会在运行期抛
     `NoSuchMethodException`，等于把护栏做成纸糊的）；④网关的 `isSuccess` / `errorMessage` 用 `thenCallRealMethod`，
     **NEVER 改成 `thenReturn(true)`**，否则成功码规则一改测试仍然全绿。
252. 同类 `:84`~`:168` 的等价性论证（**本轮最值得留存的一条方法论**）：`auditLogger` 与三个出向 port
     **一律用真实现 + 共用同一批 mock 协作者**，于是拆分前写的断言「一条都不用改就继续通过，这本身就是等价性证明」；
     断言**一律经 `PaySignServiceImpl` 门面下钻、NEVER 直接调某个领域服务实现** —— 「若断言挂在被拆的那个类上，
     一旦把方法搬走就必须同时改测试，那时「全绿」只证明**代码和测试被一致地改了**，证不了行为等价，
     而这里是支付链路，等价性是唯一的验收依据」。该字段 2026-09-15 建立时**没有动一行业务代码、19 个既有断言原样全绿**；
     当天 `PaySignWorkflow` 被删、八个入口搬进两个领域服务，**这些断言一条都没改过**；
     ADR-D112 / D113 收口 port 后**那 205 个既有断言同样一条未改**。可 grep：`证不了行为等价`。
253. **`service/impl/TerminationInternalFixture.java:19`~`:88`** — 与门面夹具**分开**的理由：那条链是
     「APP 入口门面 → 四个领域服务」，本类装的是**解约内部端点**（`/internal/termination/**`，全部由 web-admin
     Quartz 触发、不经 APP 门面），两条链的协作者只有 4 个重叠，「硬合成一个夹具会让任一侧的依赖变动都惊动另一侧」。
     另三条：被测对象**以接口类型暴露**（防测试顺手调到实现细节）；`TerminationExecutor` **用真实现、刻意不 mock**
     （门面现在只剩一行委托，换 mock 就只能证明「委托调用发生了」，证不出状态机、CAS、票卡回填 ——
     「那等于把特征测试变成空转」）；`channelSyncDeliverer` / `terminationProcessor` / 两个重试上限
     **已不再是被测类的协作者**（扫表补偿三兄弟 2026-09-16 拆到 `TerminationCompensationService`），
     **NEVER 因为「以前注过」把它们加回来**；`:52`「**NEVER 加回 `contractDomainService` mock** —— 解约链路已不经它」。
254. **`support/PayRefundRulesTest.java:15`~`:125`** — 四个 public 此前**全部零覆盖**（只能由 `requestRefund` 到达，
     而那个入口零调用点），其中两条规则各有代价：**可退金额上界**（`refundAmount > 已付 − 已退`）是全模块唯一实现点、
     放宽即允许超额退款；**`ORDER_NO` MUST 存我方商户订单号**（退款汇总按 `PAY_REFUND_DETAIL.ORDER_NO =
     PAY_TXN_DETAIL.ORDER_NO` 关联重算，存支付中心号一律关联不上 —— **生产已有 12 条坏账正是这么来的**）。
     另：**判断顺序也被钉住**（顺序决定「同时不满足两条时报哪一条」，联调方可能已按文案断言）；
     已付金额取 `TOTAL_AMOUNT` 优先、缺失或非正回落 `AMOUNT`、两者都空按 0；`REFUND_AMOUNT` 为 null 视作已退 0
     （**NEVER 因为 null 就拒掉整笔退款**）；退款单号形状是 `RF` + 17 位时间戳 + 商户订单号后 8 位。
255. **`support/PaySignGatewayMessagesTest.java:20`~`:29`** — 出网报文固化三件事，每件都对应真实踩过或可能踩的坑：
     **键集与取值来源**（退款的 `orderNo` 曾误取 `merchantRefundNo`、构造时恒 `null`，导致必填项漏传，故逐字段钉死来源）、
     **键序**（报文参与加签，`LinkedHashMap` 的插入序不能变）、**空值不进报文**（不是「进报文但取值 null」——
     两者对加签串不等价）。
256. **`support/PaySignGatewayTest.java:12`~`:37`** — 补的是 `isAlreadyPaidSuccess` 的 **true 支**
     （既有用例的网关失败码一律是 `600`，这一支此前零执行）；散掉的后果是**把一笔已扣款成功的交易判成失败、
     进而走到拉黑分支**。反面同样钉住：**NEVER 放宽成「`code=9999` 就算已支付」**（9999 是支付中心的通用失败码，
     放宽等于把所有失败都当成功），措辞与码**两个条件 MUST 同时成立**。
257. **`support/PaySignValidatorsTest.java:22`~`:28`** — 断言的是**文案与判断顺序、不是「有没有报错」**：
     这些字符串会原样进 APP 应答的 `retMsg`，联调方可能已按文案断言；因此每个「缺字段」用例只留一个字段为空、
     另有一组「同时缺两个」用例钉死**先报哪一个**。改这些断言等于改对外契约，
     **MUST 当成契约变更走确认、NEVER 顺手改成期望新文案**。
258. **`support/PayTxnRulesTest.java:11`~`:63`** — 补的是 `validatePaySignInfo` 的**钱包支**
     （`requestPay` 既有用例全走传统渠道，钱包分支此前零执行 —— 而那正是 2026-09-15「钱包扣款零 SUCCESS」
     那次改动的落点）。两支校验的字段本就不同：**钱包认 `payUserId`、传统渠道认 `requestSignSeq`**，
     **NEVER 把两者合并成一套校验**；带空白的钱包渠道号也 MUST 认出来（归一化在 `PaymentChannels` 内部完成）；
     另钉 `PAY_STATUS` → 扣费请求结果的归一：**只有 `SUCCESS` / `PROCESSING` 原样，其余一律 `FAIL`**。
259. **`support/PayTxnViewsTest.java:15`~`:81`** — `:17`「**这个类的价值不在「转换对不对」，而在「有没有漏字段」**」：
     `PayTxnDetailDTO` 有 26 个字段，收口前那 26 行逐字段拷贝写在 `PaySignServiceImpl.queryPayTxnBatch` 里且零覆盖
     —— 漏一个 setter 编译通过、单测通过，只是调用方拿到的那一列**恒为 null**。因此主用例用**反射**：
     把实体每个可写字段都填非空值，转换后遍历 DTO 全部 getter，任一为 null 就报出字段名；
     **NEVER 改成逐字段 `assertEquals`** —— 「那种写法本身也会漏，等于用同一个缺陷守自己」。
260. **`support/PaymentChannelsTest.java:8`~`:85`** 与 **`audit/PaySignAuditResultCodeTest.java:22`~`:29`** —
     前者钉 `0B` → true、大小写与前后空格归一后仍匹配、`null` / 空串 → false 且**不抛异常**、`walletCode()`
     返回值必须让 `isWallet` 为 true、未知编码与 `null` 归到 `Contracted`（**保留收口前既有行为**，
     改成抛异常或另立变体都会改变对外行为）、**`classify` 与 `isWallet` MUST 永远同口径**（两者分叉就等于
     「同一个问题两个答案」）；后者钉 `APP_PAY_SIGN_REQUEST` 的 `RESULT_CODE` / `RESULT_MSG` 取值口径
     —— 此前这两列**只在应答是 `BaseRespDTO` 时才有值、其余应答类型恒为 NULL**，
     `:26`「那是最难发现的一类缺陷：不报错、不告警，运营按结果码筛流水时只表现为「这批单子没有结果」」，
     故**每种字段形状各钉一条**并把「取不到」与「超长」两个边界也钉住（`NEVER 只测 happy path`）。
     实现侧对应 `audit/PaySignAuditLogger.java:62` 的 `CODE_KEYS = {"retCode","resultCode","code"}`
     与 `:46` 的 `RESULT_CODE_MAX = 64`（对齐 schema 的 `VARCHAR2(64 CHAR)`）。

### 九、DDL 侧：129 条 `COMMENT ON` 与 56 行 `--` 注释（`src/main/resources/sql/`，4 个文件）

> 分布（实测）：`pay-sign-schema.sql` **78 条 `COMMENT ON` / 0 行 `--`**；`pay-txn-schema.sql` **46 / 42**；
> `pay-sign-channel-sync-migration.sql` **4 / 10**；`pay-sign-txn-transin-migration.sql` **1 / 4**。
> **这四个文件里的 `--` 注释是安全的**（它们不进 Druid），但 **NEVER 把这些片段整段拷进 mapper XML 的 SQL 正文**。

261. **`pay-sign-schema.sql:1`~`:35`（`APP_PAY_SIGN_INFO`）** — 唯一约束
     `UK_APP_PAY_SIGN_INFO_USER_VENDOR (THIRD_USER_ID, PAYMENT_VENDOR)` 是「同一用户同渠道只能一行签约」的
     库侧依据（第 230 条那句「放宽已签约校验会出现两行」由它兜底）；另有 `UK_APPSI_REQUEST_SIGN_SEQ` 单列唯一索引。
     `SIGN_STATUS` 列注释给出四值 `NOT_SIGNED / SIGNING / SIGNED / UNSIGNED`（`:26`）；
     **`PAYMENT_VENDOR` 的列注释是库侧唯一的渠道码字典**（`:28`：`03` 支付宝 / `04` 微信 / `06` 建行龙支付 /
     `07` 云闪付 / `0B` 钱包），与 `support/PaymentChannels` 的 `0B` 一致。可 grep：`0B钱包`。
262. 同文件 `:37`~`:62`（`APP_PAY_SIGN_LOG`）与 `:64`~`:122`（`APP_PAY_SIGN_REQUEST`）**主键形状不同、
     决定了「一条流水号能留几行」**：前者 `PK_APP_PAY_SIGN_LOG PRIMARY KEY (REQUEST_SIGN_SEQ)` —— 单列主键，
     **一个流水号只能有一行签约日志**；后者是 `ID` + `SEQ_APP_PAY_SIGN_REQUEST` 序列主键、**可多行**，
     因此第 230 条「审计流水合并成一行」讲的是**后者**。`OPERATION_TYPE` 列注释写「SIGN-签约，UNSIGN-解约」（`:57`），
     与第 223 条那条归并口径一致。
263. 同文件 `:83`~`:86` + `:119`~`:122`（`APP_PAY_SIGN_REQUEST.NOTIFY_*` 四列）是**本项目 outbox 四列形状的第一处落地**：
     `NOTIFY_STATUS`（注释：`PENDING/SUCCESS/FAILED`）+ `NOTIFY_RETRY_COUNT` + `NOTIFY_TIME` + `NOTIFY_RESULT`，
     配套索引 `IDX_APP_PAY_SIGN_REQUEST_NOTIFY_STATUS (NOTIFY_STATUS, NOTIFY_RETRY_COUNT)` —— **两列正好是扫表谓词**。
     另注意 `CARD_ID` / `CARD_TYPE` / `DISPLAY_ACCOUNT` 在该表是 **NOT NULL** 且列注释都写「补偿通知时使用」。
264. 同文件 `:152`~`:203`（`APP_TERMINATION_REQUEST`）— `TERMINATION_STATUS` 注释四值
     `PENDING/SCANNING/SUCCESS/FAILED`（`:189`）；三个索引的语义分别是
     `UK_ATR_REQUEST_SIGN_SEQ`（唯一 ⇒ **同一签约流水只能一行、FAILED 只能 update 复活**，第 224 条的库侧依据）、
     `IDX_ATR_USER_STATUS (THIRD_USER_ID, TERMINATION_STATUS, REQUEST_TIME)`、
     `IDX_ATR_NOTIFY_STATUS (NOTIFY_STATUS, NOTIFY_RETRY_COUNT)`、
     `IDX_ATR_CHANNEL_SYNC (CHANNEL_SYNC_STATUS, CHANNEL_SYNC_RETRY_COUNT)`。
265. **`CHANNEL_SYNC_*` 四列（`pay-sign-schema.sql:168`~`:171` + 注释 `:198`~`:201`；
     `pay-sign-channel-sync-migration.sql:11`~`:24`）= ADR-D48 的 outbox 四列形状**，
     两份文件的列注释**逐字相同**：状态（`PENDING待投递、SUCCESS已清理、FAILED待重试`；
     **`NULL` 表示本行早于改造，NEVER 被补偿扫描捞取**）+ 重试次数（`达上限后不再扫描、转人工处理`）+
     时间（`配合滞留判定`）+ 结果（`超长由调用方截断`，即第 250 条那条 `VARCHAR2(1024)` 截断要求）。
     可 grep：`NEVER被补偿扫描捞取`（**原文无空格，按空格 grep 会漏**）。
266. **`pay-sign-channel-sync-migration.sql:1`~`:10` 那 10 行 `--` 是 ADR-D8 的执行顺序约束**：
     `:8`~`:10`「**NEVER 只执行本脚本就改 Java**：本组列是「拆事务边界 + 补偿端点 + 工单」三件事的前置，
     单独把 RPC 移出事务反而比现状更糟（本地提交后 `TERMINATION_STATUS=SUCCESS`，上游重推在幂等短路处返回成功，
     **通道永远删不掉**）」。`:5` 另记设计口径：「列名与同表的 `NOTIFY_*` 一组对称，语义也照抄」。
267. **`pay-txn-schema.sql:6`~`:138`（`PAY_TXN_DETAIL`）的四组取值域**（库侧唯一权威）：
     `PAY_TYPE`＝`PAY/REFUND`；`PAY_STATUS`＝`INIT/PROCESSING/SUCCESS/FAIL/RETRY/CLOSED`；
     `REFUND_STATUS`＝`NONE/PROCESSING/PARTIAL/SUCCESS/FAIL`；`DEBIT_REQUEST_RESULT`＝`PROCESSING/SUCCESS/FAIL`
     （第 232 条那条「两列同步回写」的另一半就是它）。另两条易错的类型事实：`PAY_TIME` 是
     **`VARCHAR2(28 CHAR)` 存 `YYYYMMDDHH24MISS`**（不是 TIMESTAMP）；`TXN_DATE` 是 `VARCHAR2(8 CHAR)`
     且为**月分区键**（第 234 条的库侧依据）。`:124` 还给了一条链路事实：
     **`PAY_CENTER_ORDER_NO`「退款报文的原支付订单号取此列」**。
268. 同表五个索引的语义：`UK_PAY_TXN_DETAIL_ORDER (ORDER_NO, TXN_DATE) LOCAL` —— **主键是两列**，
     即第 219 条「CAS 少写 `TXN_DATE` 等于跨分区更新」的同型依据；
     `IDX_PAY_TXN_DETAIL_STATUS (PAY_STATUS, TXN_DATE, NEXT_REQUEST_TIME)` **三列正好是重试扫表的三个谓词**；
     余下 `IDX_..._CARD_DATE` / `IDX_..._USER_DATE` / `IDX_..._CHANNEL_ORDER` 都是「业务键 + 分区键」两列形态。
269. **`pay-txn-schema.sql:142`~`:222`（`PAY_REFUND_DETAIL`）** — `UK_PAY_REFUND_DETAIL_NO (REFUND_ORDER_NO,
     TXN_DATE) LOCAL` 就是第 219 条那句「本表主键是「退款单号 + 交易日期」两列」的原文依据；
     `IDX_PAY_REFUND_DETAIL_STATUS (REFUND_STATUS, TXN_DATE, NEXT_REQUEST_TIME)` 是回查扫表索引。
     **注释覆盖度是本文件最低的一处：20 列只注释了 7 列**（`REFUND_ORDER_NO` / `ORDER_NO` / `REFUND_STATUS` /
     `REFUND_AMOUNT` / `REQUEST_COUNT` / `NEXT_REQUEST_TIME` / `LAST_REQUEST_TIME`），
     **三个单号列 `MERCHANT_REFUND_NO` / `REFUND_NO` / `CHANNEL_REFUND_NO` 一条注释都没有**
     —— 这正是 M2 无法用库侧注释裁决的原因（见 §矛盾）。另记一条类型事实：该表 `REFUND_AMOUNT` 是
     `NUMBER(16)` 而 `PAY_TXN_DETAIL.REFUND_AMOUNT` 是 `NUMBER(22)`（汇总列比明细列宽，方向安全）；
     `REFUND_NO` 是 **NOT NULL**。
270. **`pay-txn-schema.sql:227`~`:294`（`PAY_CALLBACK_LOG`）** — 表注释与 `PayCallbackLogMapper.xml:4` 一致：
     **只追加、不参与订单最终态覆盖**；`CALLBACK_TYPE`＝`PAY/REFUND`；`RAW_BODY` 是 CLOB 存回调原文；
     两个索引 `(ORDER_NO, TXN_DATE)` / `(REFUND_ORDER_NO, TXN_DATE)`。
     **`HANDLE_STATUS` 的注释只写了 `SUCCESS成功，FAIL失败`**（`:294`），而代码会写第三个值 `MANUAL`（见 §矛盾 M13）。
271. **三张分区表的分区只建到 `P202612` + `P_MAX`**（`:53`~`:62`、`:176`~`:185`、`:259`~`:268`），
     因此 2027-01 起全部落进 `P_MAX`；`pay-txn-schema.sql:296`~`:329` 那 **42 行 `--`** 正是为此留的运维样例
     （三张表各一段 `ALTER TABLE ... SPLIT PARTITION P_MAX AT ('20270201')`，外加一段「待支付重试查询」样例
     `WHERE PAY_STATUS IN ('FAIL','RETRY') AND REQUEST_COUNT < :maxRequestCount AND (NEXT_REQUEST_TIME IS NULL
     OR NEXT_REQUEST_TIME <= SYSTIMESTAMP)`，与 `IDX_PAY_TXN_DETAIL_STATUS` 三列完全对应）。
     `:4` 另记一条设计口径：**「重试最大次数不入库，后续由配置文件控制」**。
272. **`pay-sign-txn-transin-migration.sql`（9 行，1 条 `COMMENT ON` + 4 行 `--`）** — 给 `PAY_TXN_DETAIL` 补
     `TRANS_IN VARCHAR2(64 CHAR)`（列注释：`入账账户/商户号（支付平台 data.transIn）`），
     `:1`~`:2` 写着「关联修改：`PayTxnDetail.java`、`PayTxnDetailMapper.xml`、**`PaySignWorkflow.java`**」
     —— 最后那个类 ADR-D87 已删（墓碑 T32 记的就是这一行），`:4`「允许为空，历史数据无需回刷」。
273. **`APP_USER_PAY_CHANNEL`（`pay-sign-schema.sql:124`~`:150`）** — 主键三列
     `(THIRD_USER_ID, CARD_TYPE, CHANNEL)`（**不含 `CARD_ID`**，这也是 `docs/domain` 撤回记录里那条
     「按 `CARD_TYPE` 关联」讨论的库侧形状）；`STATUS` 注释三值 `ACTIVE有效、INACTIVE无效、UNBOUND已解绑`；
     虚拟列 `THIRD_USER_ID_SUFFIX VARCHAR2(4 CHAR) GENERATED ALWAYS AS (SUBSTR(THIRD_USER_ID, -2)) VIRTUAL`
     注释写「第三方用户ID后两位，**分区字段**」—— 但该表**没有 `PARTITION BY`**（见 §矛盾 M15）。
274. **mapper XML 侧本轮补的四条**（阶段一已抽扫表 SQL 四个坑，这里补此前漏掉的语句级判据）：
     `PayCallbackLogMapper.xml:39`~`:42` 重推硬限次的计数 **MUST 按 `MERCHANT_ORDER_NO` + `CALLBACK_TYPE` 统计**
     （一笔订单的支付回调与退款回调共用本表，只按订单号统计会把两类混算）；同文件 `:54`~`:57` 放弃重推时的留痕
     **定位到 `MAX(ID)`**（ID 取自 `SEQ_PAY_CALLBACK_LOG` 单调递增，即本次插入那条），并明写
     `HANDLE_STATUS VARCHAR2(32)` / `HANDLE_MSG VARCHAR2(1024)`、**超长由调用方截断**；
     `AppTerminationRequestMapper.xml:94`~`:97` 记 T+4 的 `cutoff` 由 service 层算出、**PENDING 与 SCANNING
     共用同一个 cutoff**、且 `REQUEST_TIME` 理论非空故**不做 `NVL` 兜底**（为空则该行本轮不参与）；
     同文件 `:302`~`:305`：`rejectScanning` 与 `expireScanning` **SQL 形态相同、语义不同**（对方答复失败 vs 我方等超时），
     **刻意不合并** —— 「`FAIL_REASON` 来源与运维处置口径不同，合并后无法从表里区分两者」。
275. **`AppTerminationRequestMapper.xml:380`~`:382` 是 `CHANNEL_SYNC_*` 一组的总说明**：
     「与 `NOTIFY_*` 一组**形状对称但语义无关，NEVER 混用**。状态取值 `PENDING / SUCCESS / FAILED / MANUAL`。
     `NULL` 是改造前的历史行，四条语句都不会碰到它」；`:398`~`:401` 记 `NVL(CHANNEL_SYNC_STATUS,'X') != 'MANUAL'`
     这个闸门的两个理由（人工处理完 NEVER 被自动流程覆盖；**`NVL` 兜底是必需的** —— 历史行该列为 NULL，
     直接比较结果 UNKNOWN、整条 UPDATE 影响 0 行）；`:424`~`:427` 记 `markChannelSyncManual` 的 CAS 前置态
     必须是 `FAILED`（缺这个 CAS 时，补偿与主链路交错的极端时序下会把刚置成 SUCCESS 的行误标成 MANUAL，
     **等于凭空造一条「需要人工」的假告警**）。

### 矛盾与待裁决

> 口径同阶段二：「注释说什么 / 代码或库实际是什么 / 证据 `file:line` / 建议裁决」四段。
> 前两条是**用本轮新载体回头裁决上一轮登记的 M1、M2**，其余 M11 起为本轮新增。

- **M1 的本轮裁决：库侧对它无裁决力，精确口径是「12 条 `pay.sign.*-url`，其中 9 条是我方主动调用的网关地址」**
  - 上一轮怎么记：「实际是 9 条网关 URL + 2 条通知 URL」，并把 `:93` / `:94` 记成 `app.notify.*-url`。
  - 库侧证据：四个 SQL 脚本里**没有任何一条 URL**，`COMMENT ON` 也不含地址 —— **DDL 对这条矛盾无裁决力**，
    只能按 `application.properties` 实测。
  - 本轮实测（`pay-sign-server/src/main/resources/application.properties`）：以 `-url` 结尾的 `pay.sign.*` 键共 **12 条**
    = **9 条我方主动调用的网关地址**（`:81` contract-config、`:82` contract、`:83` contract-advisory、
    `:84` contract-result、`:85` termination、`:86` request-pay、`:87` pay-query、`:88` request-refund、
    `:92` refund-query）+ **2 条我方回调地址**（`:93` `pay.sign.default-notify-url`、
    `:94` `pay.sign.request-pay-notify-url`，都指向 `58.56.166.170:48000`，是**塞进出向 bizData 交给支付中心回调的**，
    不是我方去调的）+ **1 条微信委托代扣页**（`:99` `pay.sign.wechat.entrust-url`）。
    真正的 `app.notify.*-url` 是**另外两条**（`:59` sign-result、`:61` termination-result，**均为 `testngbackV2` 地址**）。
  - 建议裁决：**上一轮那句「9 条含 `wechatEntrustUrl`」不准确**（`wechat.entrust-url` 不在那 9 条里），
    且 `:93` / `:94` 的前缀是 `pay.sign.` 而非 `app.notify.`。结论仍是**表述里 NEVER 带数字**，
    改成「按 `pay.sign.` 前缀 grep 得到的全部条目」；`AGENTS.md` §8 那句「7 条完整 URL」同步改。
    **判据本身不变：NEVER 退回 `-path` + `gateway-url` 拼接。**
- **M2 的本轮裁决：库侧注释同样无裁决力（那三列根本没有注释），以代码 + ADR-D92 实测为准；另发现一条新的口径分叉**
  - 库侧证据：`PAY_REFUND_DETAIL` 的 `MERCHANT_REFUND_NO` / `REFUND_NO` / `CHANNEL_REFUND_NO`
    **在 `pay-txn-schema.sql` 里一条 `COMMENT ON` 都没有**（该表 20 列只注释 7 列，见第 269 条），
    因此**无法用库侧注释裁决 M2**。
  - 代码实际是什么：`service/impl/RefundDomainServiceImpl.java:287`~`:288` 两个键都送，
    **且两个键送的都是我方 `REFUND_ORDER_NO`**（`bizData.put("refundOrderNo", row.getRefundOrderNo())` +
    `bizData.put("merchantRefundNo", row.getRefundOrderNo())`）；而库里 `MERCHANT_REFUND_NO` 列存的是
    **网关回执里的 `merchantRefundNo`**（`support/PayRefundRules.java:95` 回填）。
  - 建议裁决：①`config/PaySignProperties.java:37` 那行「我方填 `refundOrderNo`」**判作废**，
    改为「两个键都 MUST 送」（ADR-D92 有实测应答做证据）；②**新增一条待澄清**：
    「回查上送的 `merchantRefundNo`」与「库列 `MERCHANT_REFUND_NO`」**不是同一个东西**，
    文档与注释里 MUST 区分表述，NEVER 让后人以为该键取自那一列；③`REFUND_NO` 是 **NOT NULL**，
    但本轮未核实它在哪一步被回填 —— 留给下一轮实测。
- **M11 上一轮 M3 点名的疑点已复核：`AlipayTripSignCharacterizationTest` 的断言与实现一致，
  但它自己的类注释与自己的断言矛盾**
  - 注释说什么：该类 `:30`~`:42` 仍整段描述「一次成功请求会往 `APP_PAY_SIGN_REQUEST` 写**两行**流水」，
    并点名「方法体内 `:275`~`:288` 手写的一行」，还写着「要不要合并成一行属于改审计口径，MUST 由人裁决，
    本类先把现状钉住」。
  - 实际是什么：**同一个类 `:96`~`:122` 的断言要求「只有一行」**（`assertEquals(1, logs.size(),
    "合并后 MUST 只有一行；两行是分叉前的旧现状")`，并断言那一行同时带 `OPERATION_TYPE='SIGN'` +
    `SIGN_STATUS='SIGNED'` + 报文 + `RESULT_CODE='0000'`，`:100`「**NEVER 回退成两行**」）；
    实现侧 `ContractDomainServiceImpl:273` 明写「流水由方法收尾的 `auditAlipayTripSignInfo` **一行写完**
    （2026-09-16，ADR-D115 续（二））」，`paySignRequestMapper` 在该类里**已无手写 INSERT**
    （现存引用只有构造注入与 `auditAlipayTripSignInfo` 的调用点 `:248` / `:258` / `:292` / `:297` / `:886`）。
  - 证据：`src/test/.../AlipayTripSignCharacterizationTest.java:30`~`:42`（旧）对 `:96`~`:122`（新）；
    `src/main/.../service/impl/ContractDomainServiceImpl.java:273`。
  - 建议裁决：**上一轮 M3 担心的「断言仍按两行写」不成立** —— 断言已是一行、且是 ADR-D115 续（二）的判据。
    真正过期的是**该类的类注释**（连同它引用的 `:275~288` 行号），本轮删注释已把那段整体移除、
    只保留一行式护栏。**NEVER 据那段旧注释再去「恢复两行」。**
- **M12 `CHANNEL_SYNC_STATUS` 的列注释取值域缺 `MANUAL`**
  - 注释说什么：`pay-sign-schema.sql:198` 与 `pay-sign-channel-sync-migration.sql:18` 都只写
    `PENDING待投递、SUCCESS已清理、FAILED待重试`。
  - 实际是什么：**代码会写第四个值 `MANUAL`**（`AppTerminationRequestMapper.xml:382` 明列四值、
    `:424` 的 `markChannelSyncManual` 就是写它，`ChannelSyncDeliverer` 的 `BizRejected` 分支必然落到它）。
  - 建议裁决：以代码为准，**两处列注释都 MUST 补 `MANUAL（重试耗尽/业务拒绝，已转人工，自动流程 NEVER 覆盖）`**。
    这条不改也不会报错，但运营按注释理解取值域时会漏掉终态。
- **M13 `PAY_CALLBACK_LOG.HANDLE_STATUS` 的列注释同样缺 `MANUAL`**
  - 注释说什么：`pay-txn-schema.sql:294`「本地处理状态：SUCCESS成功，FAIL失败」。
  - 实际是什么：达到重推上限时**止推并把回调标成 `MANUAL`**（`PayCallbackLogMapper.xml:54`~`:57` 那条留痕语句、
    断言在 `PayResultCallbackCharacterizationTest:147`）。
  - 建议裁决：补 `MANUAL`。与 M12 同型 —— **本模块两组「转人工」终态都没进列注释**，
    排查时 NEVER 以列注释的取值域为全集。
- **M14 `schema` 与 `migration` 两个方向都不同步**
  - 实际是什么：①**`TRANS_IN` 只在 `pay-sign-txn-transin-migration.sql:5`，`pay-txn-schema.sql` 的
    `CREATE TABLE PAY_TXN_DETAIL` 里没有它** ⇒ 拿 schema 建新库会缺该列，而代码在用（`PayTxnDetail` 有该字段）；
    ②反过来 `CHANNEL_SYNC_*` 四列与 `IDX_ATR_CHANNEL_SYNC` **在 `pay-sign-schema.sql:168`~`:171` / `:180`
    与 migration `:11`~`:24` 重复** ⇒ 对已有该组列的库重复执行 migration 会报 `ORA-01430` / `ORA-00955`。
  - 建议裁决：按 `AGENTS.md` §8 那条口径，**`*-schema.sql` MUST 是「当前全量形状」**：把 `TRANS_IN` 补进 schema；
    migration 保留原样（它是执行历史的物证）。**NEVER 反向删 schema 里的 `CHANNEL_SYNC_*` 去消重** ——
    那会让新建库缺列。**本轮只登记、未改任何 SQL 文件。**
- **M15 `APP_USER_PAY_CHANNEL.THIRD_USER_ID_SUFFIX` 注释写「分区字段」，而该表没有分区**
  - 证据：`pay-sign-schema.sql:135`~`:136` 定义虚拟列、`:150` 注释「第三方用户ID后两位，分区字段」；
    同文件 `:124`~`:138` 的 `CREATE TABLE` **没有任何 `PARTITION BY`**（对比同库 `PAY_TXN_DETAIL` 等三张表都有）。
  - 建议裁决：要么补分区（属结构变更，MUST 先评估在线重定义），要么把注释改成
    「预留的分区键（当前表未分区）」。**NEVER 让后人据这条注释推断「按后两位分区已生效」。**
- **M16 「该模块没有能装配 `AppNotifyServiceImpl` 的 mock 脚手架」已被推翻**
  - 注释说什么：`domain/TerminationFailReasonTest.java:15`~`:16`「**这组用例是那条链路唯一的护栏** ——
    该模块没有能装配 `AppNotifyServiceImpl` 的 mock 脚手架」。
  - 实际是什么：2026-09-16 新增的 `service/impl/AppNotifyCompensationCharacterizationTest.java:53`~`:54`
    **直接 new 了被测类**（并说明理由：这两个入口不在 `PaySignService` 门面上）。
  - 建议裁决：那句「唯一的护栏 / 没有脚手架」判过期。`FAIL_REASON` 的**格式互逆**仍只有那组用例守着
    （新类守的是**预算与白名单**，不是文案格式），因此**结论「NEVER 删那组用例」不变**，只是理由要换。
- **M17 `PAY_TXN_DETAIL` 有三列优惠语义重叠，其中一列类型也不同**
  - 证据：`pay-txn-schema.sql:24` `COUPON_AMOUNT NUMBER(22)`（注释「优惠金额，单位分」）、
    `:44` `DISCOUNT_INFO VARCHAR2(512)`（「优惠详情JSON数组」）、
    `:46` `DISCOUNT_FEE NUMERIC(10)`（「优惠金额（分）」）—— **后者是全表唯一的 `NUMERIC` 类型金额列**。
  - 建议裁决：`COUPON_AMOUNT` 与 `DISCOUNT_FEE` **二者只应留一个作为权威**；
    在裁决前，**读写这两列的代码 MUST 在注释里写明取的是哪一列、来源是哪个报文字段**，
    NEVER 假设它们恒等。本轮只登记。

### 墓碑清单（T33 起，接阶段二 T23~T32）

> 口径同阶段二。本轮的墓碑**大半只活在测试注释与 SQL 脚本注释里**，而这一批注释在第二步已被删除，
> 因此这张表现在是它们的**唯一去处**；「可 grep 性」一列按**删注释之后**的仓库状态评估。

| 编号 | 墓碑（NEVER 回来的东西） | 出处（删除前 `路径:行号`） | 可 grep 的字样 | 删注释后的可 grep 性 |
|---|---|---|---|---|
| T33 | `KNOWN_TRANSACTIONAL_OUTBOUND` 里被 5A/5B/5C 删掉的 8 条「事务包住出网」（`ContractDomainServiceImpl` 6 条 + `PaymentDomainServiceImpl#requestRefund` + `TerminationInternalServiceImpl#notifyTerminationFailed`） | `arch/PaySignTransactionBoundaryArchTest.java:142`~`:172` | `KNOWN_TRANSACTIONAL_OUTBOUND` | **可 grep（常量仍在、且仍是空集）**，这是本轮唯一「墓碑本身有断言守着」的一条 |
| T34 | `updateStatus` / `updateFailReason` / `updateCompleteTime` 三条无 CAS 的解约状态写语句 | `arch/TerminationStatusArchTest.java:31`~`:56`；`AppTerminationRequestMapper.xml` 语句本体仍在 | 方法名 | 可 grep（**XML 语句故意保留**），**易误判成「还在用」** |
| T35 | `PaySignInfoMapper.updateBySeq` 的「写状态」用法（现只准回填非状态字段） | `arch/SignStatusArchTest.java:34`~`:38` | `updateBySeq` | 可 grep（方法仍在），**易误判** |
| T36 | 「钱包不签约 / 钱包签约回调应拒绝」两条旧契约（2026-09-15 作废）与两个旧用例名 `walletRequestSignInfoIsRejectedAndNeverReachesGateway` / `walletSignResultCallbackIsRejected` | `ContractDomainCharacterizationTest.java:60`~`:64`；`CallbackDomainCharacterizationTest.java:91`~`:95` | 旧用例名 | **删注释后为 0**（用例已改名，旧名只存在于被删的注释里）⇒ 只能查本表或 SVN 历史 |
| T37 | `PaySignFacadeFixture` / `TerminationInternalFixture` 里那四组按字段名反射注入的 `injectXxx` 与 `ReflectionTestUtils.setField`（ADR-D96 已删） | `PaySignFacadeFixture.java:49`~`:52`；`TerminationInternalFixture.java:34`~`:37` | `ReflectionTestUtils` | **删注释后为 0**（`src/test` 已无该 import） |
| T38 | `PayTxnDateResolutionTest` 曾用**反射**调 `PaySignWorkflow` 的私有 `resolveTxnDate`（现为 `PaySignValues` 的公开方法） | `PayTxnDateResolutionTest.java:81`~`:83` | `NEVER 退回反射` | **删注释后为 0** |
| T39 | `TerminationInternalFixture` 里的 `contractDomainService` mock（ADR-D115 删）与 `channelSyncDeliverer` / `terminationProcessor` / 两个重试上限的注入（2026-09-16 拆到 `TerminationCompensationService`） | `TerminationInternalFixture.java:48`~`:62` | `contractDomainService` | 可 grep（类仍在、别处仍用），**易误判** |
| T40 | `pay-sign-channel-sync-migration.sql` 注释里的两个已删符号 `PaySignWorkflow.receiveTerminationResult` 与 `removeAccountPayChannel`（补 T32 —— 那条只记了 `pay-sign-txn-transin-migration.sql:2`） | `pay-sign-channel-sync-migration.sql:2`~`:3` | `removeAccountPayChannel` | **仍可 grep（SQL 脚本注释未删）**，但 `src/main` 的 Java 里为 0 |

> ⚠️ 本轮**新增一类风险**：T36 / T37 / T38 在删注释后**代码里 grep 不到任何痕迹**，
> 而它们恰好是「曾经这样写过、且明确不许回去」的三条。**判断「有没有回退」MUST 用本表 + `svn diff` /
> `svn cat -r BASE`，NEVER 只在当前工作副本里 grep。**

### 保留的护栏（阶段三删注释后，代码里仍在的一行式告示）

> 删除的判据是「多行叙述 / MUST-NEVER 长论证 / 事故史 / 墓碑」，**保留的判据是「一行就能拦住一次回归」**。
> 下面六类是本轮**刻意留在代码里**的，**NEVER 因为「注释都清了」把它们一并删掉**。

| # | 位置 | 保留的一行 | 它拦的是什么 |
|---|---|---|---|
| ① | `service/impl/PaymentDomainServiceImpl.java`（`receivePayResult` 上方） | 「无 `@Transactional` 是刻意的、NEVER 加回（2026-08-26 生产事故：事务内调 `syncDebitStatus`，单请求 287 秒、行锁 45 秒、`PAY_CALLBACK_LOG` 零条落库）」 | 有人「顺手补上事务」把资损级形状原地复现 |
| ② | `event/SignResultCommittedListener.java`（`@TransactionalEventListener` 上方） | 「`AFTER_COMMIT` + `fallbackExecution=false`；NEVER 加 `@Async`，内部 MUST catch 全部异常只记日志」 | 事务内投递通知 / 异常彻底无声 / 虚拟线程下的 pin |
| ③ | `service/impl/RefundDomainServiceImpl.java`（退款回查组装 `bizData` 处） | 「MUST 同时送 `merchantRefundNo` 与 `refundOrderNo`（ADR-D92 实测：只送 `refundOrderNo` 返 9999）」 | 退款单永久空转、而端点每轮返 `0000` |
| ④ | `resources/application.properties`（`pay.sign.*-url` 组上方） | 「以下 `pay.sign.*-url` 均为完整 URL：NEVER 退回「路径 + `gateway-url`」拼接（漏 `/v1` 不报 404、极难定位）」 | 拼接写法漏版本号，表现为 `code=600` 而非 404 |
| ⑤ | `src/test` 里 **38 个**守卫 / 特征化测试类的类 Javadoc（两个 `*Fixture` 是「测试夹具：…」，不计入 38） | 每类一行「护栏：…」，说明**这组断言在防什么** | 断言被放宽 / 被 `@Disabled` 时，读者仍知道代价 |
| ⑥ | 7 个 mapper XML 各一行 | 「注释只能写在这里：SQL 正文禁写行注释或块注释，Druid WallFilter 会判定为注入并让该语句静默失效」 | 把说明写回 SQL 正文导致语句**静默失效**（只在 Oracle 生产炸） |

> ⑥ 那行**刻意不出现两个连续减号**（XML 注释里出现即 MyBatis 解析失败、服务启动即挂）。
> 本轮已用 `xmllint --noout` 逐个校验 7 个 mapper，并另跑了一次「注释体内是否含连续减号」的扫描（0 命中）。

### 本轮覆盖率自评

**分母（本轮实测，用 Java 词法剥离统计、字符串/字符/文本块受保护）**：
- `src/test`：**40 个文件 / 6563 行 / 注释 1486 行**（去掉纯装饰行 `/**`、`*/`、`<ul>` 等后可抽内容 1156 行）。
- `src/main/resources/sql`：4 个文件 / **129 条 `COMMENT ON`** + **56 行 `--`**
  （`pay-sign-schema.sql` 78 / 0；`pay-txn-schema.sql` 46 / 42；`channel-sync-migration` 4 / 10；`txn-transin-migration` 1 / 4）。
- 顺带补抽的 mapper XML 语句级判据来自 `src/main/resources/mapper` 的 **283 行**注释（阶段一已覆盖扫表 SQL 四个坑那部分）。

**分子**：本轮 **75 条正文（第 201~275 条）+ 矛盾 7 条（M11~M17，另含对 M1、M2 的库侧裁决）+ 墓碑 8 条（T33~T40）**。
分布：arch 门禁 7 条、domain 状态机 5 条、mapper 离线 SQL 断言 8 条、签约与查询侧特征测试 10 条、
支付与退款侧 6 条、解约簇 8 条、回调通知与加签 6 条、夹具与 support 10 条、DDL 与 mapper 15 条。
三轮合计 **275 条正文 + 40 条墓碑 + 17 条矛盾**。

**有意跳过（计入分母）约 330 行**：`src/test` 里的 `// ---------` 分隔行、`import` 说明、
与用例方法名同义的一行式注释（如「/** 成功路径。 */」）、以及 `PaySignFacadeFixture` 里逐 mock 字段的
「哪个 mapper 归谁」说明（那部分是装配细节、判据密度低，且已被第 251~252 条的方法论覆盖）。

**明确留白**：
- `src/test` 的**断言正文**未逐条搬（本节只搬「钉住什么 + 为什么会退化」，具体 `assertEquals` 参数以代码为准）。
- `pay-sign-schema.sql` 的 78 条列注释里，**纯字段名同义的约 40 条未逐条列**（如「第三方用户ID」「创建时间」），
  本节只列取值域、约束、索引语义与矛盾点。
- **`REFUND_NO NOT NULL` 在哪一步回填**未实测（M2 第③点）。
- 四个 SQL 脚本**是否与 `AFCITPDB` 现状逐列一致**本轮未回查（M14 的两个方向都只在仓库口径上确认）。

### 阶段三第二步：删除结果与不变量自证

- **删除量**：Java **4169 行**注释（`src/main` + `src/test` 合计 5101 → 932，触及 **125 个文件**）；
  mapper XML **276 行**（283 → 7，7 即新加的一行式护栏，触及 7 个文件）；
  `application.properties` **14 行**（24 → 10，含新加的护栏 ④）。**合计删除 4459 行注释 / 133 个文件。**
- **保留下来的 932 行 Java 注释**＝标准 Javadoc 摘要行 + `@param` / `@return` / `@throws` +
  上表六类一行式护栏；**多行叙述、MUST-NEVER 长论证、事故史与墓碑注释已全部移出代码、只存在于本文档与 SVN 历史**。
- **不变量自证（逐文件、词法级）**：对 138 个 Java 文件分别取 `svn cat -r BASE`（**NEVER 用 git**，AGENTS.md §7）
  与当前工作副本，各自剥离注释并归一化空白后逐字比对 —— **code mismatch: 0**；
  mapper XML 同法（剥 `<!-- -->` 后归一化）**0 处不一致**；`application.properties` 的**非注释行逐行相同**，
  其中 `pay.sign.merchant-private-key=` 那一行**字节级未变**（值未在任何地方回显）。
  另在 `/tmp/paysign-baseline` 留了改前快照，用同一脚本二次比对同样 0 不一致。
- **XML 校验**：7 个 mapper 逐个 `xmllint --noout` 通过；并额外扫描「注释体内是否含两个连续减号」**0 命中**。
- **测试数**：删除前 `Tests run: 254, Failures: 0, Errors: 0, Skipped: 0` + `BUILD SUCCESS`；
  删除后**同样是 254 / 0 / 0 / 0 + `BUILD SUCCESS`**（命令 `mise exec -- mvn -o clean test -pl pay-sign-server
  -am -Djkube.skip=true`；**该模块 `package` 阶段会推镜像，所以 MUST 带 `-Djkube.skip=true`**，
  判成败看 `BUILD SUCCESS` / `[ERROR]`、**NEVER 看退出码**）。
- **未做的**：一行代码逻辑都没改，SQL 脚本一个字都没动，**未提交 SVN**。
- ⚠️ **一处不属于本轮作业的改动**：`src/main/resources/log4j2-paysign.xml` 在本次会话期间（文件 mtime 21:31）
  被**另一路改动**把三段多行注释压成了三行一行式注释（含「详见 `docs/ops/生产环境清单.md` 附.三」这类引用），
  **形态与本轮脚本的产物不同**（本轮对 XML 的处理是删注释 + 补一行护栏，不会保留压缩后的原文）。
  已核实：该文件**剥掉注释后的配置内容与会话开始时的快照逐字相同**（`xmllint --noout` 也通过），
  因此无功能风险；**本轮未回退它，也未把它计入上面的删除量**（上面的 7 个 XML 只含 mapper）。
  MUST 由人确认这处改动的归属后再决定保留或回退。

（本节完。**编号与统计口径见本节开头；阶段一、阶段二附录 NEVER 改写**。若继续抽取其他模块，
MUST 另起小节、编号从 276 / T41 / M18 起。）









