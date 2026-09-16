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
