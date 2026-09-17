---
业务域: 支付宝渠道（出行 / 碰一下）
模块: fep-alipay-server, alipay-pay-sign-server, alipay-account-server
---

# 提示词：支付宝渠道对接

## 何时读本文件
支付宝出行渠道的签约/解约、渠道侧支付与退款、行程记录查询、渠道开户、黑名单变更通知相关改动。

## 三个模块的分工（勿混淆）
- **fep-alipay-server**：端口 8080，`spring.application.name=fep-alipay`。**薄网关，无 mapper / 无表 / 无事务 / 无定时任务**，入参 `@ModelAttribute ItpCommonFormRequest`（**2026-09-11 收口后的全项目唯一实现，在 `model` 模块**；旧名 `CommonFormRequest` 及本模块自有副本已一并删除，见 `app-gateway.md`「报文契约」），全部经 RPC 下发。
- **alipay-pay-sign-server**：端口 8080，`spring.application.name=alipay-pay-sign`。渠道签约与支付的**实际落库方**。
- **alipay-account-server**：端口 8080，`spring.application.name=alipay-account`。渠道用户开户与信息维护，较薄。

⚠️ 三者默认端口都是 8080，同机部署 **MUST** 显式区分端口。

## 接口清单

**fep-alipay-server**
- `FepAlipayTripController`（`/channel`）：`addContract`、`terminateContract`、`requestApplication`、`requestIndustryData`、`findTravelList`、`findTravelDetail`
- `FepAlipayTripPaymentController`（`/admin/payment`）：`payQuery`、`requestRefund`、`addBlackList`、`executeTermination`
- `FepAlipayTripNotifyController`（`/notify`）：`payment/payNotify`、`closeResultForAlipay`
- `FepAlipayTripMemberContractController`（`/memberContract/channel`）：`requestIndustryData`
- 下发客户端：`AlipayPaySignClient`、`AlipayAccountClient`、`TicketClient`、`IndustryDataClient`、`BlacklistClient`

**alipay-pay-sign-server**
- `AlipayPaySignController`（`/channel`）：`addContract`、`terminateContract`、`selectSignInfo`、`executeTermination`、`findTravelDetail`、`notify/blackListChange`
- `AlipayTripPaymentController`（`/api/payment`）：`requestPay`、`payQuery`、`requestRefund`、`payNotify`
- `AlipayPayLogController`（`/api/payment`）：`payLog/list`(GET+POST)、`payLog/detail`、`payLog/entryId`、`payLog/exitId`、`payLog/queryByTravelRecord`、`payLog/travelList`(GET+POST)
- `AlipayTerminationInternalController`（`/internal/alipay/termination`）：`process`（销卡批处理，仅供 web-admin 的 Quartz 任务调用，`AlipayPaySignClient.processAlipayTermination`）
- `AlipayChannelSyncInternalController`（`/internal/alipay/channelSync`）：`compensate`（支付通道同步补偿扫描，2026-09-17 新增，ADR-D132）。**上线前 MUST 在 web-admin 建对应 `sys_job`**，否则端点零调用、`CHANNEL_SYNC_*` outbox 依旧没人扫。**当前无鉴权**（与同模块另两个 `/internal/**` 同现状）

**alipay-account-server**
- `FepAlipayTripRequestApplicationController`（`/channel`）：`requestApplication`、`queryUserInfo`、`updatePaymentChannel`、`updatePhone`
- `AlipayUserPageController`（`/page/user/alipay`）：`/search`

> 本域代码中**没有任何 IF 编号标注**。需要对应 IF 编号时 **MUST** 回查 `docs/接口规范文档/`，**NEVER** 自行编造编号。

## 核心类
- 签约登记：`alipay-pay-sign-server/.../service/impl/AlipayContractServiceImpl.java`、`TerminationRegistrationService`、`TerminationNotifier`
- 销卡批处理（2026-09-07 新增）：`AlipayTerminationInternalServiceImpl`（扫 PENDING → 逐条 `TerminationNotifier.execute`）+ web-admin 侧 `AlipayTerminationQuartzTask`
- 支付：`AlipayTripPaymentServiceImpl` + 协作类 `PaymentRequestService`、`PaymentQueryService`、`PaymentRefundService`、`PaymentNotifyAdapter`、`RefundAmountCalculator`、`PayLogBuilder`、`SignLogRecorder`、`BizDataBuilder`、`IndustryDetailEnricher`
- 查询：`AlipayPayLogQueryServiceImpl`
- **出网端口（2026-09-17 / ADR-D131，`paysign/port/` 平铺四个方向，每方向 1 Port + 1 Adapter）**：`AccountChannelPort`（account 域支付通道）、`DebitSyncPort`（gate-txn-pay 扣费状态）、`BlacklistPort`（加黑）、`PayCenterPort`（支付中心支付 / 退款 / 查询，返自建 sealed `PayCenterReply`）。**新写出网 MUST 走这四个之一，NEVER 在 service 里直接注 `*Client` / `PayCenterClient`**
- **通道同步 outbox（2026-09-17 / ADR-D132）**：`ChannelSyncDeliverer`（出网 + 三态回写的**唯一一份**，包私有）、`ChannelSyncCompensationService`（扫一批重推）、`AlipaySignInfoMapper.selectCompensableChannelSync`

## 支付宝渠道重构后形态（2026-09-17，ADR-D129 ~ ADR-D133、ADR-D135）

读本模块代码前 **MUST** 先按这几条对齐现状，**NEVER** 照旧文件形态推断：

- **`addContract` 已不在事务里出网**（ADR-D129）：改成「本地短事务落签约行 → 提交后出网 → 按 `CHANNEL_SYNC_*` 四列回写」。**NEVER 把 RPC 挪回 `@Transactional` 内**（AGENTS.md §5.2 那条事故）。
- **两台状态机改「枚举 + 白名单 + CAS」**（ADR-D130）：`ALIPAY_SIGN_INFO.SIGN_STATUS` 的唯一写入口是 mapper 里那条 CAS 语句（`SIGNED -> TERMINATED`），**NEVER 再加第二条改该列的 UPDATE**。
- **`CHANNEL_SYNC_STATUS='FAILED'` 的行靠结果文案前缀分流**（ADR-D132）：`BIZ_REJECTED:` 只等人工、`UNREACHABLE:` 才重推。**改前缀 MUST 同时改 `selectCompensableChannelSync` 的 `NOT LIKE`**。
- **`AlipayContractServiceImpl` 刻意没拆**（ADR-D133）：依赖簇确实不相交（签约查询 4 字段 / 解约 3 字段、交集为空），但 230 行 + 4 个入口不足以让拆分产生收益。**拆分要两条同时成立：簇不相交 + 规模或变更频率造成真实成本。NEVER 只凭「簇不相交」启动拆分。**
- **`ALIPAY_SIGN_INFO` 的主键是 `THIRD_USER_ID` 单列**（2026-09-17 实测 `ALIPAY_SIGN_INFO_PK`，`AGREEMENT_CODE` **没有任何唯一约束**）。连带三条 **NEVER 忘**：①一个用户全表最多一行，**解约不删行 ⇒ 重签只能就地 UPDATE**（`reactivateSign`，CAS `TERMINATED -> SIGNED`），NEVER 再 INSERT；②「同一用户只有一条生效签约」已由主键保证，**NEVER 再加唯一索引**（ADR-D135 建过一个又删了）；③`selectByAgreementCode` 理论上可能多行。
- **`addContract` 落库分三支 + 换号一律拒绝**（ADR-D135）：有生效签约 → 同号幂等返成功 / **异号拒绝**（`CHANNEL_AGREEMENT_CODE` 要发给支付中心销卡，覆盖旧号等于旧协议永远解不了约）；有历史行 → `reactivateSign`；都没有 → INSERT。冲突（主键 / CAS 0 行）统一回查生效行后按幂等处理。
- **`selectByThirdUserIdAndChannel` 带 `SIGN_STATUS='SIGNED'` 谓词，NEVER 去掉**（ADR-D135）：三个调用方（`addContract` 判生效 / `selectSignInfo` / `PaymentRequestService.requestPay` 取协议号扣款）要的都是「生效中」，去掉谓词时**已解约用户会被继续扣费**。要判「表里有没有这一行」用 `selectAnyByThirdUserIdAndChannel`。
- **`TerminationNotifier` 不再直调 `PayCenterClient`**，销卡结果通知走 `PaymentNotifyAdapter`。通知方向那两条判据（`blacklistNotify` 三者任一 / `closeResultNotify` 只认 `code==200`）**刻意留在 adapter 内、NEVER 并进 `PayCenterPort`**。
- 单测基线：`mise exec -- mvn -o clean test -pl alipay-pay-sign-server -Djkube.skip=true` → **74 tests / 0 failures**（2026-09-17 ADR-D135 后；此前是 69）。特征测试（`AlipayContractCharacterizationTest` 24 / `AlipayPaymentCharacterizationTest` 18 / `TerminationSweepCharacterizationTest` 14 等）钉的是**对外行为**，改动后变红 **MUST 先确认是不是有意的契约变更**。

## 支付宝出行销卡链路（2026-09-07 落地）
登记（`terminateContract`）→ `ALIPAY_TERMINATION_REQUEST` 落 `PENDING` → 每天 2:00 Quartz「支付宝出行销卡」调 `/internal/alipay/termination/process` → 逐条执行销卡。

- **执行顺序**：`TerminationNotifier.execute` **先**通知支付中心业务关闭（`closeResultNotify`，成功判定 `code == "200"`），**后**改本地（`ALIPAY_SIGN_INFO` 置 `TERMINATED` + 登记表 CAS `PENDING → COMPLETED`）。顺序 **NEVER** 颠倒（AGENTS.md §5.2）。
- **状态收口**：`COMPLETED`（成功）/ `FAIL`（仅「签约信息不存在」这一终态失败）；通知失败、异常、CAS 未命中一律保持 `PENDING` 等下一轮，**NEVER 置 FAIL**。
- **排空**：服务端单批 200 条不循环，`AlipayTerminationQuartzTask` 按 `scanned==0` 结束，`MAX_ROUNDS=20` 兜底，cutoff 全程固定。
- **登记校验**：`TerminationRegistrationService` 现在回填不到 `THIRD_USER_ID` 就**拒绝登记**（返回 `8011 用户未签约`），并用 `countByAgreementCode` 做存在性判断（该表无唯一索引，`selectByAgreementCode` 有 `TooManyResultsException` 风险）；重复登记幂等返回成功。
- **不加列的代价**（用户 2026-09-07 决定不做 DDL）：没有重试计数、没有失败原因列、`TERMINATION_TIME` 仍不写。卡在 `PENDING` 的记录**只能靠容器日志定位**，排查 MUST 搜 `销卡执行异常` / `通知支付中心销卡结果未成功`。
- **未做欠费校验**：实测 `GATE_TXN_PAY` 与 `ALIPAY_PAY_LOG` 订单号交集为 0、`THIRD_USER_ID` 体系与 `PAYMENT_VENDOR` 都不同，支付宝出行的「未结清」语义在现有数据里没有落点。这一步留成显式扩展点，**NEVER** 补一个恒返回「无欠费」的假校验；补齐前 MUST 先向业务确认欠费真源。
- **与 `executeTermination` 的关系**：`/channel/executeTermination`（`AlipayContractServiceImpl:142-183`）只改 `ALIPAY_SIGN_INFO`、**从不碰登记表**，且先本地后远端、远端失败只 warn。它与本批处理是两条独立路径，**NEVER** 假定二者会互相收口。

### 销卡通知的协议号（2026-09-07 生产实测，未跑通）
- `closeResultNotify` 的 `agreementNo` **MUST 传渠道协议号 `ALIPAY_SIGN_INFO.CHANNEL_AGREEMENT_CODE`**，不是我方 `AGREEMENT_CODE`。传我方号支付中心返回 `code=600 未查询到协议信息: 070000144735309128`——**不是 404、不是签名错**，极易误判成业务原因（`TerminationNotifier` 已按此修正，alipay-pay-sign 1.1.13）。
- 但改用渠道号 `2088302232551032` 后**仍返回 `600 未查询到协议信息`**。即当前库里两个号支付中心都不认，销卡通知这一步在存量数据上跑不通。
- 根因指向 `CHANNEL_AGREEMENT_CODE` 存的值不对：生产 `ALIPAY_SIGN_INFO` 3 行里**有 2 行该字段同为 `2088302232551032`**（协议号应唯一，`2088` 开头更像支付宝 uid）。该字段不由我方生成，直接取签约入参 `AlipayTripAddContractReqDTO.channelAgreementCode`（`AlipayContractServiceImpl:89`），所以问题在上游传值或字段语义约定。
- 因此排查销卡通知失败 **MUST** 先确认「支付中心认的协议号到底存在我方哪个字段」，**NEVER** 再换一个号盲试生产网关。批处理对这种情况保持 `PENDING`（不置 FAIL）是刻意设计，靠后续重试自愈。

## 查询乘车记录详情的 `debitRequestResult`（2026-09-14 修复，alipay-pay-sign 1.1.19）

- **值域是对外契约的 `"0"`（扣费成功）/ `"1"`（未成功），NEVER 放渠道文案**。修复前 `PaymentQueryService.findTravelDetail` 填的是 `ALIPAY_PAY_LOG.RESULT_MSG`（如「支付成功」「渠道协议号不能为空,」），支付宝侧按 0/1 解析，**任何文案都被判成非 0 即失败，成功单也显示扣费未成功**。
- 现在统一走 `payLog.getPayStatus()` → `mapPayStatusToDebitResult`（`SUCCESS` 忽略大小写 → `"0"`，其余含 null → `"1"`）。**同一套映射有三份**：`alipay-pay-sign-server/PaymentQueryService`、`fep-alipay-server/AlipayQueryServiceImpl:236` 与 `:416`。跨模块无法复用（AGENTS.md §5.1 禁止新建工具类），**改一处 MUST 看齐其余两处**。
- 该值域与 `PAY_TXN_DETAIL.DEBIT_REQUEST_RESULT` 列的值域（`SUCCESS`/`FAIL`/`PROCESSING`）**同名不同义**，NEVER 混用。闸机后付费侧的权威列是 `GATE_TXN_PAY.DEBIT_STATUS`，`PAY_TXN_DETAIL` 那一列只在前者为空时兜底（`ticket-server/TransRecordAssembler.toAppDebitResult`）。
- 回归判据（2026-09-14 在 1.1.19 上实测，只读）：`POST /channel/findTravelDetail`，`GT20260727173242368542741`（`PAY_STATUS=SUCCESS`）返 `debitRequestResult="0"`，`GT20260911163450147542741`（`PAY_STATUS=FAIL`）返 `"1"`；单测见 `TravelDetailDebitResultTest`（6 例，含大小写与 null）。
- ⚠️ **`ALIPAY_PAY_LOG` 没有 `CREATE_TIME` / `UPDATE_TIME` 列**（2026-09-14 查 `USER_TAB_COLS` 实测 31 列）。按时间排序 MUST 用 `TRANS_TIME` 或主键 `PAY_SEQ`；写 `ORDER BY CREATE_TIME` 会被 MCP 报成通用的 `McpToolException cannot be cast to java.util.Map`，看不出是列不存在。另注意 `TRANS_TIME` 是 VARCHAR 且**格式不统一**（存量里既有 `2026-07-28 16:59:55` 也有毫秒时间戳 `1785229085685`），**NEVER 直接当日期比较**。

## 付款链路加固（2026-09-14，alipay-pay-sign 1.1.21，对齐 pay-sign-server）

改动集中在 `PaymentRequestService` / `PaymentQueryService` / `PaymentRefundService` 三个类，**每一条都 NEVER 回退**：

- **`payQuery` 不再把「查不通」写成本地 FAIL**。原实现三个分支无条件 `updatePayNotify`，于是支付中心不可达 / 网关返 `600` 时会把一笔已 `SUCCESS` 的订单改成 `FAIL`，而 `PAY_STATUS` 正是 `countUnsettledByCardId` 判欠费的依据（等于把乘客算成欠费）。现在只有「拿到业务应答且给出终态」才回写，且走新语句 `updatePayQueryResultIfNotSuccess`（WHERE 带 `PAY_STATUS IS NULL OR != 'SUCCESS'` 白名单 + 影响 0 行回读区分幂等 / 口径冲突）。查询链路失败一律只打 ERROR、不动库。
- **黑名单只在「支付中心业务明确拒绝」时加**（解密 data 后 `retCode != SUCCESS`）。传输层失败、网关路径错、响应为空**不再加黑**——那些与乘客付款能力无关，加黑直接拦住过闸。同时 `blacklistClient.addBlackList` 的 `retCode` 现在会显式判（此前丢弃返回值，属 AGENTS.md §5.2 那条）。
- **`payNotify` 补齐回调凭据 + 重推硬限次**，形态照抄 pay-sign-server 的 `receivePayResult`：回调即写 `ALIPAY_PAY_CALLBACK_LOG`（`HANDLE_STATUS=PROCESSING`）→ 按 `countByOrderNo` 判累计推送次数 → 同步 `GATE_TXN_PAY` → 失败且 `pushCount < 2` 返非 0000 让重推、达上限则返 0000 停推并 `markManualByOrderNo` 置 `MANUAL`。落库 / 计数 / 回写全部 catch 只记日志，**表缺失时自动退化成原行为**。`transStatus` 改白名单（只认 `1` 成功 / `2` 失败），其余取值拒绝 + 置 `MANUAL`，**NEVER 回退成「非 1 即 FAIL」**。
  - ⚠️ **`MAX_PAY_CALLBACK_PUSH=2` 是与 pay-sign 对齐的经验值**，支付中心重推规格仍缺（`docs/external/支付中心网关接口文档.md` §5 只写「会重试」），**MUST 索取规格后校准**。
  - **运维 MUST 例行巡检 `ALIPAY_PAY_CALLBACK_LOG.HANDLE_STATUS='MANUAL'`**，否则放弃重推的那笔会静默沉底。
- **`requestRefund` 摘掉 `@Transactional`**：方法体内两次调支付中心（OkHttp readTimeout 30s），事务包住网络调用与 AGENTS.md §5.2 记的 2026-08-26 生产事故同一成因，且回滚会把刚插入的退款明细一起丢弃——而远端可能已受理。`AlipayPayLogMapper.xml` 的 `updateRefundSummary` 注释里早就写明了这个风险，这次才真正拆掉。
- **退款收口改三分支**（`Ok` / `BizRejected` / `Unreachable`）：初始状态由自相矛盾的 `FAIL` + 「退款处理中」改为 `PROCESSING`；业务成功 → `SUCCESS` + 重算汇总；业务明确拒绝 → `FAIL`（可重新申请）；**拿不到业务应答 → 保持 `PROCESSING`，NEVER 置 FAIL**（置 FAIL 会让汇总少记已退金额，同一笔随后能再退一次）。
- **退款成功判定补上业务码**：此前只判 `code==200`，「网关受理、业务拒绝」会被写成 `SUCCESS` 并进汇总，账面凭空多一笔已退。现在与 `requestPay` / `payQuery` 一致，还要解 data 里的 `retCode`。
- **退款幂等**：`countByOrderNoAndStatus(orderNo,'PROCESSING') > 0` 即拒绝新申请（本表没有调用方提供的幂等键，`REFUND_ORDER_NO` 是我方每次新生成的，这条状态短路是唯一防线）；插入侧另有 `isIntegrityViolation` 沿 `getCause()` 链兜底（照 `card-pool-server` 样板，**NEVER 只 catch 最外层 `DuplicateKeyException`**）。

### 仍未闭合（本轮有意不做）

- **`ALIPAY_PAY_LOG` 已无写入方**：`PaymentRequestService.requestPay` 按注释把落单收口到 gate-txn-pay-server 后不再写本表，`PayLogBuilder` 成为**死代码**（全模块无调用），而 **5 条读路径仍依赖它**：`payQuery`（返「订单不存在」）、`requestRefund`（「原支付记录不存在」）、`findTravelDetail`（「未查询到支付流水」）、`AlipayPayLogQueryServiceImpl` 后台列表 / 详情、`AlipayArrearsQueryService.countUnsettledByCardId`（**欠费恒 0**）。用户 2026-09-14 决定**本轮不动**，迁移另立项：要么读路径改走 gate-txn-pay，要么恢复本地落单（后者与 `requestPay` 的「NEVER 恢复双写」注释冲突，需先推翻那条）。**NEVER 因为 `PayLogBuilder` 没人用就删它** —— 它是「恢复本地落单」这一选项的现成材料。
- **`UK_ARL_REFUND_ORDER_NO` 未建成**：存量 `ALIPAY_REFUND_LOG` 33 行 / 去重 26，7 行重复全部集中在联调造数 `REFUND20260715120000001`（固定号，代码生成的是 `R` + 毫秒 + 8 位 UUID；`REFUND_STATUS` 全 FAIL）。**不是真实重复退款**，但 `ORA-01452` 拦住了索引。清理归属后再执行，脚本与判据见 `alipay-pay-sign-server/src/main/resources/sql/alipay-pay-sign-callback-log-migration.sql`。
- **`PayCenterClient.signRequest` 仍是 `request.setSign("test")` 占位**，`signWithRsa` / `buildSignData` 写好了但没人调，`pay.center.merchant-private-key` 为空。属安全红线，**本次未动，上生产前 MUST 人工复核并接真实 RSA 加签**。
- **`/api/payment/**` 四个端点（含 `payNotify`、`requestRefund`）无鉴权无归属校验**，与 §5.2 冲突。
- **支付中心的幂等拒答（如「订单已支付成功，请勿重复支付」）仍被当成扣款失败**（`AlipayPayLogMapper.countUnsettledByCardId` 的 javadoc 已记）。修它需要支付中心的 retCode 码表，**NEVER 靠匹配中文文案兜**。

## 状态取值（2026-09-17 起签约状态已有枚举，其余仍是 String 常量）
- `AlipayContractServiceImpl`：`SIGNED`、`PENDING`，渠道常量 `CHANNEL_ALIPAY="ALIPAY"`
- **签约状态迁移走枚举 + 白名单 + CAS**（ADR-D130）：`AlipaySignStatusTransition` 表达三态结果，白名单只有 `SIGNED -> TERMINATED`；CAS 返 0 且库里既非 `SIGNED` 也非 `TERMINATED` 判 `CONFLICT`、**只告警不硬改**
- **通道同步 outbox 用 `model.domain.SyncStatus`**（`PENDING` / `SUCCESS` / `FAILED`，`isCompensable() = PENDING || FAILED`）；`FAILED` 内部再按 `BIZ_REJECTED:` / `UNREACHABLE:` 前缀分流（ADR-D132）
- `TerminationRegistrationService`：`PENDING`、`COMPLETED`
- `TerminationNotifier`：`TERMINATED`（写 `ALIPAY_SIGN_INFO`）、`COMPLETED`、`FAIL`（写 `ALIPAY_TERMINATION_REQUEST`）；返回值 `Outcome{TERMINATED, FAILED, RETRY_LATER}`

⚠️ 与 pay-sign-server 的状态词表**不完全一致**（如 pay-sign 用 `SUCCESS`/`UNSIGNED`，此处用 `COMPLETED`/`TERMINATED`）。跨模块对接 **MUST** 显式映射。

## 数据表
`ALIPAY_SIGN_INFO`、`ALIPAY_SIGN_LOG`、`ALIPAY_PAY_LOG`、`ALIPAY_REFUND_LOG`、`ALIPAY_TERMINATION_REQUEST`、`ALIPAY_PAY_CALLBACK_LOG`
- 逻辑删除标记 `DELETE_FLAG='0'`，查询 **MUST** 带此条件（见 `AlipaySignInfoMapper.xml`、`AlipayRefundLogMapper.xml`）
- ⚠️ 本模块**无 `*-schema.sql` 建表脚本**，前五张表的 DDL 在仓库内没有权威副本，唯一索引无法从仓库确认。新增幂等约束前 **MUST** 让用户确认线上 DDL。
- **`ALIPAY_PAY_CALLBACK_LOG` 是例外**：它的权威 DDL 就是 `alipay-pay-sign-server/src/main/resources/sql/alipay-pay-sign-callback-log-migration.sql`，2026-09-14 已在 `AFCITPDB` 执行并回查（`USER_TABLES` 命中、14 列、`PK_ALIPAY_PAY_CALLBACK_LOG` + 两条 NONUNIQUE 索引齐全）。
- **`ALIPAY_SIGN_INFO` 的 `CHANNEL_SYNC_STATUS` / `CHANNEL_SYNC_RETRY_COUNT` / `CHANNEL_SYNC_TIME` / `CHANNEL_SYNC_RESULT` 四列与 `IDX_ASI_CHANNEL_SYNC` 已在库**（2026-09-17 在 `AFCITPDB` 回查，与 `alipay-sign-channel-sync-migration.sql` 逐字一致：`VARCHAR2(16) NOT NULL DEFAULT 'PENDING'` / `NUMBER(10) NOT NULL DEFAULT 0` / `DATE` / `VARCHAR2(500)`；索引 `VALID`、`NONUNIQUE`、列序 STATUS(1)+TIME(2)），见 ADR-D129。**`CHANNEL_SYNC_RESULT` 是 500 而不是 pay-sign 侧的 1024**，落长文案按 500 算。**脚本存在既推不出已执行、也推不出未执行，两个方向都 MUST 先查数据字典。**

## 幂等
`@Transactional(rollbackFor = Exception.class)` + 查询已存在记录短路；销卡链路额外用 `updateStatusCas`（按 `TERMINATION_SEQ` + 原状态）保证状态只被推进一次。签约链路仍**无唯一索引可依赖、无 MQ**；定时任务只有 web-admin 侧的「支付宝出行销卡」（本模块内部**无 `@Scheduled`**）。
**付款链路已在 1.1.21 补齐三处**（详见上文「付款链路加固」）：`payQuery` 的非终态白名单回写、`payNotify` 的回调凭据 + 硬限次、`requestRefund` 的 `PROCESSING` 状态短路 + cause 链兜底。**`requestRefund` 已不带 `@Transactional`，NEVER 加回。**
签约与销卡链路仍是本仓幂等最薄的部分，新增写入 **MUST** 主动补状态短路判断。

## 编码约束
- fep-alipay-server **NEVER** 新增 mapper / 事务，只做转发与报文适配
- 黑名单变更通知：blacklist-server 侧 `alipayPaySignClient.notifyBlackListChange` 已启用；`notifyAppBlacklistAsync`、`notifyAlipayBlacklistAsync` 在 `BlacklistServiceImpl` 中**已被注释**，勿误认为在用
- **支付中心有两种应答形态，`PayCenterResponse` 必须同时接住 `retCode/retMsg` 与 `code/success/data`**。通知类接口 `receiveBlackListFromItp` 实测返回 `{"retCode":"0000","retMsg":"成功"}`，支付 / 退款 / 查询类返回 `code` + `success` + `data`。2026-09-11 修复：原 `PayCenterResponse` 只有 `code/msg/data/success` 四个字段，Fastjson2 **静默丢弃** `retCode/retMsg`，`PaymentNotifyAdapter.notifyBlackListChange` 按 `Boolean.TRUE.equals(getSuccess())` 判定 ⇒ 恒为 `null` ⇒ 支付中心已答成功却回 `9999 黑名单变更通知失败`，blacklist-server 侧打出 `异步通知支付宝外部黑名单变更失败` 的**假告警**（加入与解除两个方向都中）。现已补 `retCode/retMsg` 字段并抽出 `isPayCenterNotifySuccess`（`retCode=0000` / `success=true` / `code=200` 三者任一即成功），`alipay-pay-sign-server` 1.1.17 起生效，端到端复测 ADD / DELETE 两向均为 `黑名单变更通知成功`。**排查「对端说成功、我方记失败」MUST 先比对应答字段名与 DTO 字段名**，`success=null` 就是字段没接住的信号。
  - ⚠️ 同文件 `notifyCloseResult` 仍只按 `code==200` 判定（`PaymentNotifyAdapter:95` 附近），若支付中心的业务关闭结果通知也返回 `retCode`，会踩同一个坑，**实测前 NEVER 认定它是对的**。
- **`AlipayTripTravelRecordDTO` / `AlipayTripFindTravelDetailRespDTO` 的 `entry*` / `exit*` 字段名与语义相反，取值 MUST 用「本次 / 上一次」理解**。ticket-server 的三个查询（`selectAlipayTravelList` / `selectAlipayTravelDetail` / `selectAlipayTravelDetailByUserAndDateTime`，`QRCodeTxnDetailMapper.xml:164` 的 `alipayTripTravelRecordResultMap`）统一映射 `entryStationName ← HANDLE_STATION_CODE`、`entryDate ← HANDLE_DATE_TIME`、`exitStationName ← LAST_HANDLE_STATION_CODE`、`exitDate ← LAST_HANDLE_DATE_TIME`，即 `entry*` 是**这条记录本次**的处理站与时间、`exit*` 是**上一次**的。对出站记录（`TRX_TYPE='02'`）来说本次就是出站、上一次就是进站，与字段名正好反。因此 fep-alipay 拿出站记录的出站信息 **MUST 取 `exitDetail.getEntryStationName()` / `getEntryDate()`**。已发生（2026-09-11）：`AlipayQueryServiceImpl` 详情分支取了 `getExitStationName()` / `getExitDate()`，拿到的是出站记录的 `LAST_HANDLE_*`（= 进站），与 entryDetail 的进站信息重合，APP 侧「查询乘车记录详情」进出站站点与时间**完全一样**；列表分支（同文件 `record.setExitStationName(exitDetail.getEntryStationName())`）一直是对的、有注释，所以**列表正确、详情错误**，两个接口自相矛盾。已修，`fep-alipay:1.0.59` 起生效，复测同一笔 `GT20260911163450147542741` 得到 entry 团岛/`20260911163436`、exit 安子/`20260911163449`，与 `QRCODE_TXN_DETAIL` 两行（进站 `0133@163436`、出站 `0128@163449`）一致。
  - ⚠️ 集群里 `fep-alipay` 与 `fep-alipay-server` 是**两个 Deployment 共用同一镜像 `itp/fep-alipay`**，2026-09-11 实测在跑的是 `fep-alipay`（1/1，NodePort **30020**），`fep-alipay-server` 副本数为 0（NodePort 30023 无端点）。**改镜像 MUST 改 `fep-alipay`，NEVER 只改名字更像的那个**。
- 渠道签名逻辑 **NEVER** 擅自修改，**MUST** 提示人工复核

- **`ALIPAY_USER_INFO.CHANNEL` 是「2 位十六进制签约渠道码」，支付宝固定 `07`（`IssueChannelCodeEnum.ALIPAY`），NEVER 写英文字面量**。该列的唯一读取方是 `AlipayApplicationServiceImpl#requestIndustryData`，经 `SignChannelUtils.resolve`（取前 2 位）落进行业码体的签约渠道段，而码体整串必须是 hex。历史实现写的是 `"ALIPAY"`，截出 `AL` ⇒ `acc-security-server` 的 `ItpHexUtils.toByte('L')` 抛 `NumberFormatException` ⇒ 被映射成**误导性**的 `retCode=500 / retMsg=hexString length odd`（长度其实是偶数 64，问题在字符不是 hex）。2026-09-11 定位并修复：`AlipayAccountServiceImpl` 两处改用枚举、存量 `ALIPAY_USER_INFO`(1 行) 与 `ALIPAY_REG_LOG`(21 行) 已刷成 `07`，端到端复测 `retCode=0000`、码体渠道段 `0707`（发行 07 + 签约 07）。
  - **NEVER 顺手改 alipay-pay-sign-server 的 `CHANNEL_ALIPAY = "ALIPAY"`**：那是 `ALIPAY_SIGN_INFO` / `ALIPAY_SIGN_LOG` / `ALIPAY_TERMINATION_REQUEST` 三张表的 CHANNEL，与码体无关且被 `selectByThirdUserIdAndChannel` / `selectByCardIdAndChannel` 当查询条件用，改了会查不到签约记录。
  - 还原 SQL：`UPDATE ALIPAY_USER_INFO SET CHANNEL='ALIPAY' WHERE CHANNEL='07';` / `UPDATE ALIPAY_REG_LOG SET CHANNEL='ALIPAY' WHERE CHANNEL='07';`
- **`industry-data-server` 出本服务前已自检码体为 `[0-9A-F]{64}`**（`IndustryCardDataServiceImpl.HEX_BODY`），不合法直接返 `8001` 并把整串打进 ERROR 日志。因此再遇到码体类问题 **MUST 先看 industry-data-server 有没有这条 8001**，`hexString length odd` 只应出现在真正的奇数长度场景。
- **`application.properties` 里 NEVER 直接写中文，MUST 写 `\uXXXX` 转义**。Spring Boot 的 `OriginTrackedPropertiesLoader` 按 **ISO-8859-1** 读 `.properties`（`java.util.Properties` 规范），文件本身是 UTF-8，于是中文值被逐字节解成 mojibake 并原样送到支付中心。2026-09-11 实测：出站扣费 `body=å°éä¹è½¦è´¹ç¨`、`subject=å°éä¹è½¦æ£è´¹`，正是 `地铁乘车费用` / `地铁乘车扣费` 的 UTF-8 字节按 Latin-1 解码结果。**注意 Java 源码里的 `@Value("${k:中文}")` 默认值是对的**（class 文件按 UTF-8 编译），坑只在 properties 覆盖了该键时暴露——因此「代码里看着没问题」不能作为判据。已修三处同类：`fep-dev-server`（`alipay.trip.subject/body`）、`gate-txn-pay-server`（`gate.pay.subject/body`）、`daily-ticket-server`（`daily-ticket.pay.subject/body`），全部改为 `\uXXXX` 并用 `java.util.Properties` 回读校验。
- **`industryDetail` 由 fep-dev-server 组装，字段值一律用空串兜底、NEVER 留 `null`**（`GateTransactionHandler.blankIfNull`，2026-09-11 按用户要求从「null 占位」改为「空串占位」）。理由：Fastjson2 默认丢弃 null 值字段，支付中心侧会**整个 key 都看不到**，排查时极易误判成对端丢字段。因此「某字段在支付中心侧缺失」等价于「我方 put 进去的是 null」，**NEVER 当成对端问题**。2026-09-11 修三处：①`entryDeviceCode` 原先 `put(..., null)` 且注释写「由调用方注入」，但 `requestAlipayTripPay` 拿到 `entryDeviceFuture.get()` 后**从未回填**，字段恒缺失，现由 `applyEntryDeviceCode` 注入、取不到时置空串并打 warn；②`cardIssueCode` 从未写入，现取 `alipay.trip.card-issue-code`（默认 `0007`，与 alipay-pay-sign-server 的 `PaymentQueryService` / `PaymentRefundService` 硬编码值一致）；③`channelAgreementNo` 及其余 15 个字段全部走 `blankIfNull`。注意 `channelAgreementNo` 的真值由 alipay-pay-sign-server 的 `BizDataBuilder.enrichIndustryDetail` 从 `signInfo.channelAgreementCode` 覆盖，**属正常设计、勿在 fep-dev 侧重复填**；该方法在 `channelAgreementCode` 为空时原样返回，此时留在报文里的就是空串。

## 参考原始文档
- `docs/接口规范文档/青岛地铁-ITP与APP接口规范R6.docx`

## 附：alipay-pay-sign-server / alipay-account-server / fep-alipay-server 源码注释知识抽取（2026-09-16，阶段一）

> **本节所有 `路径:行号` 都是 2026-09-16 抽取当时的快照。这三个模块有并发写入者，引用行号前 MUST 先 grep 现查那一行还在不在**，NEVER 直接照抄本节行号去改代码或写下一份文档。
> 抽取范围：三模块 `src/main/java/**/*.java`、`src/main/resources/mapper/*.xml`、`src/main/resources/application.properties`，以及 `alipay-pay-sign-server/src/main/resources/sql/*.sql` 的头注释。只收录「契约与判据 / 决策理由 / 陷阱」三类；复述方法名参数名的普通 Javadoc、`{@inheritDoc}`、空 Javadoc 一律丢弃。**「墓碑注释」单列文末，不进正文。**
> 每条前缀标注类别：【契约】=契约与判据，【决策】=决策理由，【陷阱】=只在运行时暴露。MUST / NEVER 保留注释原文措辞、不合并。

### 附一、fep-alipay 网关与报文

- 【决策】`fep-alipay-server` 只做转发与报文适配、不落库：`src/main` 下无 mapper 无实体，`AlipayTripServiceImpl` 类注释即「委托给领域特定服务实现类处理具体业务逻辑」（`fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/service/impl/AlipayTripServiceImpl.java:30~35`）。
- 【契约】通知入口的方向：`FepAlipayTripNotifyController` 类注释「统一承接支付宝出行推送的业务通知，包括行程数据推送等。注意：**此处为 ITP 内部触发接口，由 ticket-server / online-server 在行程完成后调用，再由 fep-alipay-server 主动推送给支付宝出行侧**」（`fep-alipay-server/.../controller/FepAlipayTripNotifyController.java:21~28`）。同类 `payNotify` 补「兼容 application/x-www-form-urlencoded 格式，业务字段放在 bizData 中」（`:40~45`）；`closeResult` 补「接收支付宝方销卡结果通知，更新解约记录状态」（`:57~62`）。
- 【契约】`FepAlipayTripPaymentController.executeTermination` 注释「web管理控制台调用，执行待处理的解约登记」（`fep-alipay-server/.../controller/FepAlipayTripPaymentController.java:81~85`）；`AlipayPaymentService.executeTermination` 的入参约定是「协议号，为空时执行所有待处理解约」（`fep-alipay-server/.../service/AlipayPaymentService.java:31~36`）。
- 【契约+陷阱】`findTravelDetail` / `findTravelList` 的 `entry*` / `exit*` 语义反转，注释原文：「出站查询返回的 response 中，entryStationName/entryDate 实际对应本次出站站点和时间；其 exitStationName/exitDate 取自 QRCODE_TXN_DETAIL 的 LAST_HANDLE_\*，是上一次处理（即进站），直接用会与 entryDetail 的进站信息重合，表现为进出站站点与时间完全一样」（`fep-alipay-server/.../service/impl/AlipayQueryServiceImpl.java:237~239`；简版复述在同文件 `:421`）。
- 【契约】`payTradeOrderNo` / `invoice` 恒返 null 是裁决结果、不是遗漏：「payTradeOrderNo（支付宝渠道流水号）与 invoice 在 GATE_TXN_PAY 里没有对应列：前者按用户 2026-09-14 的明确裁决『暂时返 null，先让链路通，渠道流水后补』——核账走支付中心 payQuery；后者在 ALIPAY_PAY_LOG 33 行里实测全为 null，从未被写过，返 null 零损失」（`fep-alipay-server/.../service/impl/AlipayQueryServiceImpl.java:252~254`）。
- 【陷阱】`debitRequestResult` 的 `0`/`1` 映射在本仓有**三份同名私有方法**，注释原文：「同一套映射在 fep-alipay-server 的 AlipayQueryServiceImpl:236 / :416 各有一份（私有方法同名），三处 MUST 保持一致；跨模块无法直接复用，见 AGENTS.md §5.1『NEVER 主动创建新的工具类』」（`alipay-pay-sign-server/.../service/impl/PaymentQueryService.java:410~417`）。
- 【陷阱】入参已收口为 `model` 的 `ItpCommonFormRequest`（本模块旧副本 `CommonFormRequest` 已删），而该类 `toString` 会打**全部字段**：`sign` 恒定脱敏、但 `bizData` 完整进日志。抽取时实测**有 9 处**直接 `log.info("...{}", request)` 打 `ItpCommonFormRequest`：`FepAlipayTripController.java:47 / :66 / :85 / :104 / :123 / :142`、`FepAlipayTripMemberContractController.java:36`、`FepAlipayTripPaymentController.java:37 / :56`。这几处**源码里没有任何注释说明这件事**，属注释缺口（见附八）。

### 附二、开户与用户信息（alipay-account-server）

- 【决策】`requestApplication`（开卡申请，规范 3.69）**故意不带 `@Transactional`**，注释原文：「卡池预占、ticket-server 注册乘车状态、卡池确认 / 释放全是 RPC。改造前这里是 `@Transactional` 包住 3 次 RPC，与 2026-08-26 生产事故（`PaySignWorkflow.receivePayResult` 事务内调远端，行锁持有时长等于对端响应时长，连接被 Druid 的 `remove-abandoned-timeout` 强杀后整个事务连同证据一起回滚）**同型**」（`alipay-account-server/src/main/java/com/chinasofti/huateng/alipay/account/service/impl/AlipayAccountServiceImpl.java:66~78`）。
- 【契约】同处给出开户编排的**固定顺序**：「按 AGENTS.md §5.2『先调远端、后改本地』：预占卡号 → 注册乘车状态 → 短事务落 `ALIPAY_USER_INFO` + `ALIPAY_REG_LOG` → 确认预占。落库失败时乘车状态留在远端等重推（卡池按 businessId 幂等发号，重推拿到同一卡号、不会产生第二条乘车状态），预占显式 release；release 本身失败由卡池的预占超时回收兜底」（同上 `:74~77`）。
- 【决策】落库那两条 INSERT 用显式 `TransactionTemplate` 而非注解：「`requestApplication` 要『预占（RPC）→ 注册乘车状态（RPC）→ 落本地 → 确认预占（RPC）』，而 AGENTS.md §5.2 禁止在 `@Transactional` 方法内发起 RPC；同类内自调用又绕不过 Spring 代理，因此只能把落库那两条 INSERT 收进 TransactionTemplate。**NEVER** 给 `requestApplication` 重新加上 `@Transactional`」（`AlipayAccountServiceImpl.java:55~62`）。
- 【契约】预占失败分三类、处置不同：「POOL_EMPTY 属正常业务结果（WARN）；REJECTED 说明票种不走卡池或归属冲突，属程序 / 配置缺陷、重试无用（ERROR）；CALL_FAILED 是 card-pool-server 不可达或响应无法解析，可重试（ERROR）」（`AlipayAccountServiceImpl.java:289~299`）。释放分支「失败只记 WARN……卡号会由 card-pool-server 的预占超时回收兜底，若在此处抛异常反而会掩盖真正的失败原因」（`:324~333`），且 `releaseReservation` 的 `businessId` **须与预占时完全一致**（`:331`）。
- 【决策】支付宝渠道换号**故意不向支付域同步显示账号**：「用户 2026-09-11 裁定：不需要。ITP 侧的 `PhoneChangeServiceImpl.updatePhone` 会调 pay-sign 的 `updatePaySignDisplayAccount` 并带 `SIGN_SYNC_*` 补偿，**NEVER 照抄到这里** —— 支付宝走自有代扣，用户在支付域没有签约行，推过去只会命中『`APP_PAY_SIGN_INFO` UPDATE 影响 0 行 ⇒ 返回 FAIL』，白造一批永远重推不成功的 `FAILED` 记录和异常工单。同理 `ALIPAY_PHONE_CHANGE_LOG` **NEVER 加 `SIGN_SYNC_*` 列**」（`AlipayAccountServiceImpl.java:219~229`）。
- 【决策】`ALIPAY_PHONE_CHANGE_LOG` 的归属：「本表由 alipay-account-server 独占写入，与 account-server 的 `USER_PHONE_CHANGE_LOG` 是两条互不相干的链路（用户 2026-09-11 裁定）。NEVER 再把两者合表或共用序列」（`alipay-account-server/.../entity/AlipayPhoneChangeLog.java:5~10`；DDL 侧同义表注释在 `alipay-account-server/src/main/resources/sql/alipay-account-server-schema.sql:22`）。
- 【契约】按逻辑卡号查支付宝用户是**过闸链路的回落读**：「只读，不改任何状态；account 域按 cardId 查不到支付宝用户时由 ticket-server 调本接口，用于取码体的签约渠道位与真实卡种。见 `GateTicketHandler#applyActualCardType`」（`alipay-account-server/.../controller/FepAlipayTripRequestApplicationController.java:57~62`，接口侧同义注释在 `.../service/AlipayAccountService.java:28~35`）。
- 【契约】`ALIPAY_USER_INFO.CARD_ISSUE_CODE` 字段注释写死「发卡渠道代码，固定 0007」（`alipay-account-server/.../entity/AlipayUserInfo.java:25~27`）；`ALIPAY_CARD_POOL.STATUS` 值域是「UNALLOCATED:未分配/ALLOCATED:已分配/USED:已使用」（`.../entity/AlipayCardPool.java:20~22`）。
- 【契约】运营查询侧：`AlipayUserPageController` 类注释「支付宝注册用户运营查询，仅查询 `ALIPAY_USER_INFO`」，且「手机号和第三方支付标识均按运营展示要求脱敏」（`alipay-account-server/.../controller/AlipayUserPageController.java:16` / `:52`）；展示对象 `AlipayUserSearchView` 注释「不包含签约请求号等敏感字段」（`.../page/AlipayUserSearchView.java:5`）。

### 附三、签约与解约（alipay-pay-sign-server）

- 【契约】销卡批处理与 pay-sign 同形、**不在服务端循环**：「与 pay-sign 的 `/internal/termination/process` 同形：调用方反复调用直到 `scanned` 为 0 完成排空，服务端单次只处理一批」（`alipay-pay-sign-server/.../controller/AlipayTerminationInternalController.java:14~19`）；单批上限 200，「排空靠调用方多轮调用，不在这里循环」（`.../service/impl/AlipayTerminationInternalServiceImpl.java:39`，接口侧 `.../service/AlipayTerminationInternalService.java:11~18`）。触发方是 web-admin 的 Quartz（「内部接口，供 web-admin 的 Quartz 任务调用」，同接口类注释 `:6~8`）。
- 【决策】销卡批处理**不加事务**：「本方法 NEVER 加 `@Transactional`：逐条执行时会调支付中心（HTTP），事务包住等于按对端响应时长持有行锁（AGENTS.md §5.2 已有生产事故）。每条记录各自独立收口，一条失败不影响其余」（`AlipayTerminationInternalServiceImpl.java:20~25`）。
- 【契约+决策】销卡**当前未做欠费校验，且这是有意留的扩展点**：「2026-09-07 实测 `GATE_TXN_PAY` 与 `ALIPAY_PAY_LOG` 订单号交集为 0、`THIRD_USER_ID` 体系与 `PAYMENT_VENDOR` 均不同，支付宝出行的『未结清』语义在现有数据里找不到落点。因此这一步留成显式扩展点，**NEVER** 补一个恒返回『无欠费』的假校验冒充已校验」（`AlipayTerminationInternalServiceImpl.java:27~30`）。基准时间解析失败「返回 null，由调用方转成 INVALID_PARAM，**NEVER** 静默退化成全表扫描」（`:114~118`）；历史脏数据分支「登记时未回填 thirdUserId，做不了用户维度校验，跳过并留日志等人工处理」（`:89`）。
- 【契约】`TerminationNotifier` 是**唯一**回写登记表状态的地方：「本类曾长期是死代码（被注入、无调用点），2026-09-07 由销卡批处理 `AlipayTerminationInternalServiceImpl` 启用。它是**唯一**会回写 `ALIPAY_TERMINATION_REQUEST.STATUS` 的地方——`AlipayContractServiceImpl.executeTermination` 只改 `ALIPAY_SIGN_INFO`，从不碰登记表」（`alipay-pay-sign-server/.../service/impl/TerminationNotifier.java:19~25`）。
- 【契约】销卡执行顺序与失败处置：「**执行顺序 MUST 是『先通知支付中心、后改本地』**（AGENTS.md §5.2）：顺序颠倒会留下『ITP 已置 TERMINATED、支付中心仍认为签约中』的不一致，且无法自愈。通知失败时 **NEVER 置 FAIL**——FAIL 是终态，会让这条登记永久卡死；保持 PENDING 等下一轮重试」（`TerminationNotifier.java:27~29`）；「本方法 **NEVER 加 `@Transactional`**：内部有 HTTP 调用……每条 SQL 自动提交，状态机靠 CAS 保证」（`:31~32`）。三种结果「COMPLETED / FAIL（明确失败且不再重试）/ 本轮未完成保持 PENDING」（`:47~53`），调用方「**NEVER** 假定『没抛异常就是成功』」（`:66~71`）。分支注释：签约信息不存在「是明确的终态失败：重试多少次都不会变出一条签约记录」（`:78`）；CAS 未命中「说明这条已被别的执行流收口，本轮不重复计数」（`:96`）；未知异常「（可能是网络抖动），**NEVER 置 FAIL**：保持 PENDING 让下一轮自愈」（`:105`）。
- 【陷阱】**通知支付中心要传渠道协议号，不是我方协议号**：「**agreementNo 传的是渠道协议号 `CHANNEL_AGREEMENT_CODE`，不是我方 `AGREEMENT_CODE`。** 2026-09-07 实测：传我方协议号 `070000144735309128` 支付中心返回 `code=600 未查询到协议信息`（不是 404、不是签名错，极易被误判成业务原因）。我方号是 ITP 内部号，支付中心只认签约时它返回的那个号。**NEVER** 把两者混用」（`TerminationNotifier.java:124~133`）；退化分支「签约记录没存渠道号属于数据缺陷，退回我方号只为保留旧行为，支付中心大概率仍查不到」（`:139`）。
- 【陷阱】登记表**没有协议号唯一约束**，存在性判断只能用 COUNT：「表上没有 AGREEMENT_CODE 唯一约束，重复登记时本方法会抛 TooManyResultsException，只在能确定单条的场景使用；判断『是否已登记』MUST 用 `countByAgreementCode`」（`alipay-pay-sign-server/.../mapper/AlipayTerminationRequestMapper.java:13~20`，XML 侧同义注释 `mapper/AlipayTerminationRequestMapper.xml:53`）；登记侧复述「存在性判断用 COUNT：该表无唯一约束，历史脏数据会让 selectByAgreementCode 抛 TooManyResultsException」（`.../service/impl/TerminationRegistrationService.java:45`）。
- 【契约】改状态必须走 CAS：「按协议号改状态，无状态 CAS。批处理 MUST 用 `updateStatusCas`：本方法会把同一协议号的所有行一起改掉，且不校验原状态，并发下会把已收口的记录覆盖回去」「按主键 + 原状态改状态（CAS）。返回 0 表示状态已被别的执行流改走，调用方 MUST 放弃本次处理」（`AlipayTerminationRequestMapper.java:25~35`，XML 侧 `mapper/AlipayTerminationRequestMapper.xml:44`）。
- 【契约】登记时 `THIRD_USER_ID` 回填不到就不登记：「销卡批处理按 `THIRD_USER_ID` 做用户维度校验，回填不到就 NEVER 登记：一条三字段全 NULL 的 PENDING 记录既跑不通批处理，也无法人工追溯（生产已产生过一条）」（`TerminationRegistrationService.java:54~55`）。
- 【契约】登记表**没有专用申请时间列**：「`CREATE_TIME` 即申请时间：该表没有专用的申请时间列」（`mapper/AlipayTerminationRequestMapper.xml:78`）；批量取记录「按状态取一批，最老优先；Oracle 用 ROWNUM 包一层做限量」（`:65`）。

### 附四、支付与退款落库（alipay-pay-sign-server）

- 【决策】扣费申请**不再写 `ALIPAY_PAY_LOG`**：「过闸扣费已收口到 gate-txn-pay-server，订单与状态的唯一权威是 `GATE_TXN_PAY`（落单在 `PaySignInitiator` 之前完成，幂等靠该表的唯一键 + 状态白名单）。这里再落一份日志表就是双写，两边状态一旦分叉无法判定谁对；**NEVER 恢复双写**。『订单已存在直接返回』的短路也随之下沉到 gate-txn-pay-server，本方法只负责调支付中心」（`alipay-pay-sign-server/.../service/impl/PaymentRequestService.java:50~58`）。
- 【契约】扣款失败加黑名单**只允许一条分支**：「**只允许在『支付中心已给出业务应答且判定为扣款失败』这一条分支调用**（即解密 data 后 `retCode != SUCCESS`）。传输层失败、网关路径错（实测形态是 `code=600 操作失败`，见 AGENTS.md §8）、对端 5xx、响应为空都 **NEVER 加黑名单**——那些与乘客的付款能力无关，加黑会直接拦住其过闸」；且「`blacklistClient.addBlackList` 是『返回结果对象、不抛异常』的 RPC 包装，因此 **MUST 显式判 retCode**（AGENTS.md §5.2）；判不过只打 ERROR，不影响本次支付申请对上游的应答」（`PaymentRequestService.java:141~152`）。
- 【契约】支付回调链路形态与硬限次：「形态对齐 pay-sign-server 的 `PaySignWorkflow.receivePayResult`：**回调即入库**当凭据 → 按累计推送次数判是否已到上限 → 同步下游 → 失败且未到上限就返非 0000 让支付中心重推、到上限则返 0000 停推并把该行置 MANUAL。**NEVER 回退成『只返非 0000、库里不留痕』**：那样支付中心会无限重推，而我方除容器日志外没有任何可查的证据」（`alipay-pay-sign-server/.../service/impl/PaymentQueryService.java:211~215`）；同方法「扣费订单已收口到 gate-txn-pay-server，因此本方法 **NEVER 再读写 ALIPAY_PAY_LOG**，而是把结果同步给 `GATE_TXN_PAY.DEBIT_STATUS`（唯一权威）。业务幂等由下游按订单号 + 状态白名单保证」（`:203~205`），「**NEVER 给本方法加 `@Transactional`**：方法体内有 RPC……无事务时每条 SQL 自动提交，`ALIPAY_PAY_CALLBACK_LOG` 那行凭据不会被回滚掉」（`:207~209`）。
- 【契约+陷阱】重推次数上限 `2` 是**经验值、不是契约**：「支付结果回调允许支付中心推送的总次数（首推 1 次 + 重推 1 次），与 pay-sign-server 的 `MAX_PAY_CALLBACK_PUSH` 同口径。⚠️ 支付中心的重推次数 / 间隔 / 上限**没有规格**（`docs/external/支付中心网关接口文档.md` §5 只写「未收到成功响应会重试」）。这个 2 是与 pay-sign 对齐的经验值，**MUST 向供方索取重试规格后再校准，NEVER 把日志观察值当契约**」（`PaymentQueryService.java:37~44`）。
- 【陷阱】**回调计数点必须在 insert 之后**：「计数点 MUST 在 insert 之后：`handlePayNotify` 无事务，insert 已自动提交，这个 COUNT 才等于支付中心实际推送次数」（`alipay-pay-sign-server/.../mapper/AlipayPayCallbackLogMapper.java:12~17`；服务侧同义 `PaymentQueryService.java:311~315`，并补「取不到计数时返回 0，等于『不启用硬限次、保持原来的让上游重推』，**NEVER 因为计数失败就直接放行返 0000**」）。
- 【契约】达上限的人工出口 MUST 留痕：「达到重推上限仍未处理成功时调用：那一刻我方主动回 0000 让支付中心停推，代价是真实失败不再被上游重试，因此 **MUST 留痕**，否则等于静默丢单」（`AlipayPayCallbackLogMapper.java:24~29`）；服务侧补「这是『不再自动重试』换『不再自我放大』的取舍，因此 MUST 同时打 ERROR 并留痕。**运维 MUST 例行巡检 `HANDLE_STATUS='MANUAL'`**，否则失败会静默沉底」（`PaymentQueryService.java:336~341`）。XML 侧限定只标最后一条：「一笔订单可能有多次推送，需要人工看的是最终仍未收口的那次。用子查询取 `CREATE_TIME` 最大的一行，同秒并列时按 `CALLBACK_SEQ` 兜底定序，保证本语句恒定只影响一行」（`mapper/AlipayPayCallbackLogMapper.xml:32~36`）。
- 【契约】回调凭据表「回调一到就落库，是支付中心推送结果的唯一凭据；后续处理失败 **NEVER 回滚这一行**，回滚等于丢证据」（`.../entity/AlipayPayCallbackLog.java:5~12`）；服务侧「落库自身失败只记日志、不打断处理：留痕失败不该让一笔已扣款的回调收不了口」（`PaymentQueryService.java:281~283`）。
- 【契约】`transStatus` 白名单映射：「只认契约里的 `1` 成功 / `2` 失败，其余返回 null。**NEVER 写成『等于 1 就成功、否则失败』**——那是黑名单式判定，会把新增取值、空值、乱码统统当成扣款失败，从而把一笔可能已成功的扣费同步成 FAIL（AGENTS.md §5.2『状态机校验用白名单』）」（`PaymentQueryService.java:264~270`）。DDL 侧列注释同口径：`TRANS_STATUS IS '报文原始交易状态，1 成功 2 失败'`、`HANDLE_STATUS IS '本次处理结果 SUCCESS / FAIL / MANUAL，MANUAL 需运维巡检'`（`alipay-pay-sign-server/src/main/resources/sql/alipay-pay-sign-callback-log-migration.sql:40` / `:42`）。
- 【契约】收敛下游状态的判定：「与 pay-sign-server 的 `PaySignWorkflow.syncGateTxnPayStatus` 保持同一形态：显式判 `retCode=0000`，失败只记日志并交回调用方决定是否让上游重推。@return true 表示远端已确认收敛（含幂等命中），false 表示需要支付中心重推」（`PaymentQueryService.java:359~366`）。
- 【契约】`debitRequestResult` 是**对外契约字段、值域只有 `0`/`1`**：「NEVER 往里塞渠道文案 —— 本方法上线前该字段填的是 `ALIPAY_PAY_LOG.RESULT_MSG`，支付宝侧按 0/1 解析，任何文案都被判成非 0 即失败，成功单也显示扣费未成功」（`PaymentQueryService.java:410~417`）。与之同名的**库内列**值域不同：`ALIPAY_PAY_TXN_DETAIL.DEBIT_REQUEST_RESULT` 是 `PROCESSING/SUCCESS/FAIL`，实体注释明确「与对外契约字段 `debitRequestResult` 的 `0`/`1` 值域**同名不同义，NEVER 混用**」（`.../entity/AlipayPayTxnDetail.java:30~31` 与 `:101`）。
- 【陷阱】`payQuery` 的回写**必须带非终态白名单**：「`payQuery` 是查询接口，**NEVER 用 `updatePayNotify` 无条件覆盖**——那会把已经 SUCCESS 的订单按一次查不通的结果改成 FAIL，而 `PAY_STATUS` 正是 `countUnsettledByCardId` 判断欠费的依据」；「返回 0 行有两种含义，调用方 **MUST 回读区分**：本地已是目标状态（幂等命中），或本地已 SUCCESS 而支付中心给出失败（口径冲突，只告警、等人工，NEVER 强行覆盖）」（`.../mapper/AlipayPayLogMapper.java:18~27`；新表侧同义 `.../mapper/AlipayPayTxnDetailMapper.java` 的 `updatePayQueryResultIfNotSuccess`、`mapper/AlipayPayLogMapper.xml:109~118`、`mapper/AlipayPayTxnDetailMapper.xml:250~256`、`PaymentQueryService.java:173~179`）。XML 里记了历史错法：「已发生的错法是无条件 UPDATE，于是『支付中心暂时查不通』这种链路问题会把一笔已扣款成功的订单打成 FAIL」（`mapper/AlipayPayLogMapper.xml:112~114`）。
- 【陷阱】欠费口径已被污染，**不能直接驱动解黑**：「⚠️ 已知口径污染：`PaymentRequestService` 把支付中心的幂等拒答（如『订单已支付成功，请勿重复支付』）也当成扣款失败，连带把 PAY_STATUS 写成 FAIL。因此本表的 FAIL **不完全等于真欠费**，在该缺陷修复前，盘点结果只能人工复核，**NEVER** 直接拿它驱动删除黑名单」（`.../mapper/AlipayPayLogMapper.java:73~84`）。
- 【契约】欠费判定 MUST 同时问两个模块：「支付宝出行链路的欠费只落 `ALIPAY_PAY_LOG`，`GATE_TXN_PAY` 里没有对应行，所以判定『该卡欠费是否结清』MUST 同时问两个模块，缺一个就会漏判。本类只读，NEVER 加任何写操作」（`.../service/AlipayArrearsQueryService.java:10~18`）；分流点在「`fep-dev-server` 的 GateTransactionHandler：issueChannelCode=07 走支付宝、其余走闸机扣费」（`AlipayPayLogMapper.java:76~78`）。查询未执行时的兜底：「查询未执行时 NEVER 返回 false：调用方会把 false 当成『已结清』，一旦后续接上自动解除黑名单就等于放行仍欠费的乘客。与 gate-txn-pay 侧同一套兜底」（`AlipayArrearsQueryService.java:43~44`），入口只读、「不暴露任何状态变更能力，因此无需鉴权与归属校验。后续若在本前缀下新增写接口，MUST 先补验签（对齐现有实现，NEVER 自造签名逻辑）」（`.../controller/AlipayArrearsInternalController.java:13~17`）。
- 【陷阱】未结清统计 MUST 显式兼容 NULL：「Oracle 三值逻辑下 `PAY_STATUS != 'SUCCESS'` 对 NULL 不成立，漏掉会把脏数据判成已结清」（`mapper/AlipayPayLogMapper.xml:251~255`、`mapper/AlipayPayTxnDetailMapper.xml:374~377`、`.../mapper/AlipayPayTxnDetailMapper.java:99~104`）。

#### 附四续、退款

- 【决策】退款方法**已于 2026-09-14 移除 `@Transactional`**：「方法体内要调支付中心（OkHttp readTimeout 30s），事务包住网络调用会把行锁持有时长拉成对端响应时长，上游对同一笔的重试全部堆在同一行上串行等待，超过 Druid `remove-abandoned-timeout` 后连接被强杀、`commit` 抛 `connection closed`，**整个事务连同刚插入的退款明细一起丢弃** —— 而支付中心那边可能已经受理了退款。这与 AGENTS.md §5.2 记录的 2026-08-26 生产事故同一成因。去掉事务后每条 SQL 自动提交，明细行在调远端之前就已落地，是这笔退款存在过的唯一凭据」（`alipay-pay-sign-server/.../service/impl/PaymentRefundService.java:59~78`）。
- 【契约】退款三分支收口，对应 `Ok` / `BizRejected` / `Unreachable`：「顺序 MUST 保持『先落 PROCESSING 明细 → 再调支付中心 → 按业务应答收口』：业务成功 → 明细置 SUCCESS，再按明细重算原订单退款汇总；业务明确拒绝 → 明细置 FAIL（终态，允许重新申请）；**拿不到业务应答（响应为空 / 传输层失败）→ 明细保持 PROCESSING**，返非 0000 并打 ERROR。**NEVER 在这个分支置 FAIL**：远端可能已受理，置 FAIL 会让汇总少记一笔已退金额，随后同一笔还能再退一次，直接造成重复退款」（`PaymentRefundService.java:69~78`，代码内同义注释在 `:172~173`；明细实体侧 `.../entity/AlipayRefundTxnDetail.java:33~38`）。
- 【决策】`PROCESSING` 是本次新增的中间态：「明细在调支付中心**之前**就以该状态落地，业务应答到达后才收口成 SUCCESS / FAIL。此前初始状态直接写 `FAIL` 而 `RESULT_MSG` 写『退款处理中』，两者自相矛盾，且『远端不可达』与『业务拒绝』都落成同一个 FAIL，无法区分」（`PaymentRefundService.java:32~38`）。
- 【契约】退款幂等**只有状态短路一条防线**：「同一原订单只要还有未收口（PROCESSING）的退款明细，就拒绝新的退款申请。本表没有调用方提供的幂等键（REFUND_ORDER_NO 每次新生成），这条状态判断是唯一能挡住『远端已受理、本地结果未回写』时重复退款的防线，NEVER 去掉」（`PaymentRefundService.java:101~103`；mapper 侧 `.../mapper/AlipayRefundLogMapper.java:13~20`、`mapper/AlipayRefundTxnDetailMapper.xml:106~107`、`.../mapper/AlipayRefundTxnDetailMapper.java:37~40`）。
- 【陷阱】唯一约束竞态兜底 MUST 沿 `getCause()` 链判：「`UK_ARL_REFUND_ORDER_NO` 竞态兜底。沿 getCause 链判定，NEVER 只看最外层类名：本模块虽然当前没开 tracing，但一旦打开，观测切面会把异常重新包一层（AGENTS.md §5.2 记录的 ADR-D53），只 catch DuplicateKeyException 会静默落空」（`PaymentRefundService.java:143~145`；辅助方法注释 `:241~247`，样板取自 `card-pool-server` 的 `CardPoolServiceImpl`）。**注意该注释的「当前没开 tracing」与本模块 `application.properties:12~20` 的排除注释所描述的现状相反**（见附八「矛盾」）。
- 【陷阱】只判 `code==200` 会把「网关受理、业务拒绝」写成成功：「与 requestPay / payQuery 同一套判定：HTTP 与网关层通了之后，还要解开 data 看业务 retCode。此前只判 code==200 就当退款成功，于是『网关受理、业务拒绝』会被写成 SUCCESS 并进汇总，账面凭空多出一笔已退金额」（`PaymentRefundService.java:191~193`）。
- 【契约】退款汇总的执行时机与算法：「汇总 MUST 在明细置为 SUCCESS 之后执行：SQL 是按 `ALIPAY_REFUND_LOG` 重算的，顺序颠倒会漏掉本笔。金额与状态都不再由这里算，避免用 UPDATE 前的旧快照做加法、以及重复执行重复累加」（`PaymentRefundService.java:224~226`）。
- 【契约】**汇总 NEVER 写成累加**：「本语句 MUST 是『按 ALIPAY_REFUND_LOG 重算』，NEVER 写成 `REFUND_AMOUNT = NVL(REFUND_AMOUNT, 0) + #{refundAmount}`。原因：入参里没有任何幂等键，累加式写法执行两次就多记一笔退款金额，而这一列又是 `RefundAmountCalculator` 判断『可退金额 = 已付 - 已退』的依据；一旦虚高，后续真实退款会被误判为超额并拒绝，且账面无法自愈……重算写法天然幂等：结果只取决于 `ALIPAY_REFUND_LOG` 里 `REFUND_STATUS = 'SUCCESS' AND DELETE_FLAG = '0'` 的行，执行 1 次与 N 次落库值相同」（`mapper/AlipayPayLogMapper.xml:131~150`；新表侧同义 `mapper/AlipayPayTxnDetailMapper.xml:273~292`，并注明「明细表才是唯一账本」、「已退总额为 0 时保留原 REFUND_STATUS：那说明还没有任何一笔退款成功，此时既不该写 PARTIAL 也不该写 SUCCESS」、「可退金额口径 `NVL(NULLIF(TOTAL_AMOUNT, 0), AMOUNT)` 与 pay-sign 侧一致」）。
- 【契约】「已退成功金额合计」只能做校验、不能回写主表：「只给 Java 侧做『可退金额 = 已付 - 已退』校验用，**NEVER 拿它的返回值回写主表的 REFUND_AMOUNT** —— 那等于退回『读旧值再相加』的非幂等写法，并发两笔退款时两边都会算偏小。回写主表只能走 `AlipayPayTxnDetailMapper.updateRefundSummary`（SQL 内自行重算）」（`mapper/AlipayRefundTxnDetailMapper.xml:95~98`；实体/接口侧 `.../mapper/AlipayRefundTxnDetailMapper.java:30~34`、`.../entity/AlipayRefundTxnDetail.java:10~13`）。
- 【陷阱】旧表金额是字符串，汇总要来回转型：「金额列在本表是字符串，所以要 TO_NUMBER 求和后再 TO_CHAR 回写；明细金额写库前都过了 `RefundAmountCalculator.parseAmount`，非数字串会在那里就抛出」（`mapper/AlipayPayLogMapper.xml:145~147`）。新表已改 `NUMBER`：「与旧 `ALIPAY_PAY_LOG.updateRefundSummary` 的差异：本表金额是 NUMBER，不再需要 TO_NUMBER 求和后 TO_CHAR 回写」（`mapper/AlipayPayTxnDetailMapper.xml:291~292`）。
- 【契约】退款明细两条 UPDATE 的 WHERE 形状固定：「两条 UPDATE 的 WHERE 都是 `REFUND_ORDER_NO + TXN_DATE`：既命中唯一索引 `UK_ARTD_REFUND_ORDER`，又能做分区裁剪。**NEVER 只按 `REFUND_ORDER_NO`**」（`.../mapper/AlipayRefundTxnDetailMapper.java:9~13`；XML 侧 `mapper/AlipayRefundTxnDetailMapper.xml:115~116`）。
- 【契约】我方应答码与支付中心应答码 MUST 分列：「`RET_CODE / RET_MSG` 是我方对外应答，`PAY_CENTER_CODE / PAY_CENTER_MSG` 是支付中心原始应答，两组 MUST 分列存放：合成一列后就分不清『我方判失败』与『对端说失败』，排查退款差异时无从下手」（`mapper/AlipayRefundTxnDetailMapper.xml:128~134`；实体 `.../entity/AlipayRefundTxnDetail.java:62` / `:65`）。
- 【契约】支付中心有**两种应答形态**，同一个类都要接住：「支付 / 退款 / 查询类返回 `code` + `success` + `data`；通知类（如 receiveBlackListFromItp）返回 `retCode` + `retMsg`（2026-09-11 实测 `{"retCode":"0000","retMsg":"成功"}`）。少了 retCode / retMsg 时 Fastjson2 会静默丢弃这两个字段，success 恒为 null，成功应答会被判成失败」（`.../model/response/PayCenterResponse.java:3~12`；判定方法侧「两种形态都要认，否则成功应答会被判成失败并触发无意义的重试与告警」，`.../service/impl/PaymentNotifyAdapter.java:79~85`，另 `:96~98` 说明「组装支付中心失败原因，优先取通知类接口的 retMsg」）。`TerminationNotifier` 的成功码常量注释「与 PaymentNotifyAdapter 保持一致，勿再引入第二套判定」（`TerminationNotifier.java:44`）。
- 【陷阱】分页参数语义曾写错、**NEVER 回退**：「`offset` 是**已跳过的行数**、`pageSize` 是**每页行数**。旧 `AlipayPayLogMapper.selectAlipayPayLogList` 的谓词是 `rn > offset AND rn <= limit`，把 limit 当成了行号上界，于是第 2 页 `offset=10, limit=10` 恒返 0 行（只有第 1 页对）。本方法的 SQL 用 `rn > offset AND rn <= offset + pageSize`，**NEVER 回退成传行号上界**」（`.../mapper/AlipayPayTxnDetailMapper.java:73~82`；XML 侧 `mapper/AlipayPayTxnDetailMapper.xml:350~355`，并补「排序按 CREATE_TIME 降序 + ID 降序兜同一时刻：旧表只能 ORDER BY PAY_SEQ DESC，而 PAY_SEQ 是 UUID，等于随机序、翻页时同一行会重复出现或被跳过」）。
- 【陷阱】列表时间过滤 **NEVER 打在字符串列上**：「时间过滤打在 `CREATE_TIME`（TIMESTAMP 列）上，能走索引；NEVER 退回旧表那种 `TO_DATE(TRANS_TIME, ...)` 的写法 —— 那是对字符串列做函数转换，既走不到索引，又因存量格式不统一（有的是 yyyy-MM-dd HH:mm:ss、有的是毫秒时间戳）而随时抛 ORA-01861。endDate 用『次日零点之前』而不是 `<= endDate`，否则会漏掉当天有时分秒的行」（`mapper/AlipayPayTxnDetailMapper.xml:315~320`）。
- 【契约】支付回调回写 SQL 的**四条约束**（逐条照抄 pay-sign 的 `PayTxnDetailMapper.updatePayCallback`，`mapper/AlipayPayTxnDetailMapper.xml:202~227`）：（一）「SET 段除 PAY_STATUS / DEBIT_REQUEST_RESULT 外全部套 NVL。支付中心的重推报文字段是稀疏的，不套 NVL 时一次重推就能把首推写好的 PAY_TIME / CHANNEL_ORDER_NO 覆盖成 NULL」；（二）「DEBIT_REQUEST_RESULT MUST 与 PAY_STATUS 同步写。漏写会出现『PAY_STATUS 已 SUCCESS、扣款结果还停在 PROCESSING』，APP 侧长期显示扣费未成功（pay-sign 侧 2026-08-26 已发生过，生产 4 笔手工修数）」；（三）「WHERE 的状态白名单 MUST 只比较『列与常量』，NEVER 把入参写进状态判断。pay-sign 侧曾写成 `PAY_STATUS != 'SUCCESS' OR (PAY_STATUS = 'SUCCESS' AND 入参 = 'SUCCESS')`，而入参不是列：SUCCESS 回调进来时右分支退化成恒真，整个括号恒真，等于没有状态机」；（四）「白名单含 FAIL、不含 SUCCESS。含 FAIL 是因为支付中心可能先推 FAIL 后推 SUCCESS，钱已扣就 MUST 让 SUCCESS 落地；不含 SUCCESS 使得重复成功回调稳定命中 0 行 —— 这就是回调的幂等出口，调用方 MUST 把『0 行 + 当前已是目标状态』判为重推并返成功。被未知状态卡住的行也会稳定 0 行并触发人工出口，这是有意留的」。Java 侧同义描述在 `.../mapper/AlipayPayTxnDetailMapper.java:43~48`（「影响 0 行即『该单已是 SUCCESS 终态』，调用方 MUST 把它判为重推并返成功，**NEVER 直接回非 0000**，否则状态机自己会造出一个新的重推循环」）。
- 【契约】出网前留痕的三步顺序（新表链路，**当前无业务调用方**）：「本接口目前**没有业务调用方**：新表与链路接线分两轮做，本轮只落表与数据访问层。接线时的调用顺序 MUST 是『① insert + markRequesting 落库并提交 → ② 无事务调支付中心 → ③ updateRequestResult 回写』，即先留痕、再出网、后回写；**NEVER 把这三步包进同一个 `@Transactional`**」（`.../mapper/AlipayPayTxnDetailMapper.java:16~19`；退款侧 `.../mapper/AlipayRefundTxnDetailMapper.java:15~19` 同义并补「幂等靠 `countByOrderNoAndStatus` 的状态短路 + 插入侧沿 `getCause()` 链判完整性冲突两道」）。留痕语句本身「请求次数 +1、状态压回 `PROCESSING`、记首次与最近请求时间。MUST 在调支付中心**之前**执行并提交，否则远端已受理、本地无任何凭据」（`AlipayPayTxnDetailMapper.java:34~37`；XML 侧「PAY_STATUS 无条件压成 PROCESSING 是有意的：重试路径要允许把 FAIL / RETRY 的单子重新置为在途」，`mapper/AlipayPayTxnDetailMapper.xml:173~174`）。
- 【契约】同步应答回写只覆盖部分列：「`PAY_CENTER_ORDER_NO` 套 NVL 不覆盖已有值（后续退款要用它）；其余列直接覆盖，因为本语句是这一步的唯一写入者」（`mapper/AlipayPayTxnDetailMapper.xml:185~186`）。
- 【陷阱】进出站关联查询**不能去掉 ORDER BY**：「同一个 ENTRY_ID 理论上只对应一笔扣费，但库里没有该列的唯一约束，用 `FETCH FIRST 1 ROWS ONLY` 避免 TooManyResultsException。排序按 ID 降序取最新一笔，NEVER 去掉 ORDER BY：不带排序时『取哪一行』由执行计划决定，同一份数据在换了索引后会返回不同的行」（`mapper/AlipayPayTxnDetailMapper.xml:153~156`）。
- 【契约】新旧实体三处**刻意**不同（`.../entity/AlipayPayTxnDetail.java:7~32`）：①「金额一律 `Integer`、单位分。旧实体 31 个字段全是 String，退款汇总因此要在 SQL 里 `TO_NUMBER` 求和再 `TO_CHAR` 回写。**NEVER 把这里改回 String**」；②「时间分两类：`createTime` / `updateTime` / `*RequestTime` 等是 `LocalDateTime`（库里 TIMESTAMP）；而 `payTime` 保持 String，因为它是**支付中心回调原文**（`YYYYMMDDHH24MISS`），落库即证据、不做解析。旧实体的 `transTime` 是 String 且存量格式不统一……本实体不保留该字段，按时间过滤 MUST 用 `createTime`」；③「主键是序列生成的 `id`，业务唯一键是 `(orderNo, txnDate)` 对应唯一索引 `UK_APTD_ORDER`。旧实体的 UUID 主键 `paySeq` 不再保留 —— 它既排不出时序，也拦不住重复落单」。另「`payCenterOrderNo` 与 `merchantOrderNo` 不是一回事：后者是我方商户订单号、等同 `orderNo`；前者是支付中心侧的支付订单号，**退款报文的『原支付订单号』MUST 用它**，缺它退款必失败」（`:26~28`，同义列注释在 `:68`）。
- 【契约】退款明细实体与旧表差异及 `txnDate` 独立性（`.../entity/AlipayRefundTxnDetail.java:7~23`）：「本表是**退款账本的唯一真源**：`ALIPAY_PAY_TXN_DETAIL.REFUND_AMOUNT / REFUND_STATUS` 由 `AlipayPayTxnDetailMapper.updateRefundSummary` 按本表重算得出，**NEVER 由调用方传增量累加**（入参没有幂等键，执行两次就多记一笔）」；「**不再有 `deleteFlag`** —— 退款明细不该被逻辑删除，且旧表每条查询都得记着带 `DELETE_FLAG='0'`，漏一次就把已删行算进汇总」；「`txnDate` 是**本次退款的发起日期**，与原支付单的 `txnDate` 各自独立、**NEVER 复用原单日期**：两者是各自独立的事件，跨零点退款时会分叉」。
- 【契约】主表/明细表状态词表：`ALIPAY_PAY_TXN_DETAIL.PAY_STATUS` = `INIT / PROCESSING / SUCCESS / FAIL / RETRY / CLOSED`；`REFUND_STATUS`（主表）= `NONE / PROCESSING / PARTIAL / SUCCESS / FAIL`；退款明细 `REFUND_STATUS` = `INIT / PROCESSING / SUCCESS / FAIL / RETRY / CLOSED`（`.../entity/AlipayPayTxnDetail.java:41` / `:61`、`.../entity/AlipayRefundTxnDetail.java:33`，DDL 侧汇总在 `sql/alipay-pay-txn-schema.sql:42~45`，并注明「与 pay-sign-server 保持逐字一致，NEVER 换成 ALIPAY_* 旧表那套」）。旧表 `ALIPAY_PAY_LOG` 的对外字段口径另有一套：`PAY_STATUS`（SUCCESS/FAIL）、`REFUND_STATUS`（`SUCCESS-全额退款完成/PARTIAL-部分退款/NONE-无退款`）、`INDUSTRY_TYPE`（`1-地铁 2-公交 3-打车 4-购物`）（`.../entity/AlipayPayLog.java:41~43` / `:141~143` / `:61~63`）。
- 【契约】新表数据访问「语句形态逐条对齐 pay-sign-server 的 `PayTxnDetailMapper`，外加本模块 1.1.21 在 `payQuery` 上修过的那条非终态白名单。每条语句的设计约束写在 `AlipayPayTxnDetailMapper.xml` 的注释里，改 SQL 前 MUST 先读」（`.../mapper/AlipayPayTxnDetailMapper.java:9~14`）。

### 附五、出行销卡与定时任务

- 【契约】销卡的驱动方在**服务外**：入口 Controller 注释「支付宝出行销卡内部接口控制器……调用方反复调用直到 `scanned` 为 0 完成排空」（`alipay-pay-sign-server/.../controller/AlipayTerminationInternalController.java:14~19`），服务接口注释点名「内部接口，供 web-admin 的 Quartz 任务调用」（`.../service/AlipayTerminationInternalService.java:6~8`）。抽取范围内**三个模块都没有任何 `@Scheduled`**，销卡这条也不例外 —— 补偿节奏由 web-admin 侧的 `sys_job` 决定，本仓这三个模块的注释里**没有记录对应的 job_id 与 cron**（缺口，见附八）。
- 【契约】`fep-alipay-server` 侧的同名入口是给综管台用的：「web管理控制台调用，执行待处理的解约执行」（`fep-alipay-server/.../controller/FepAlipayTripPaymentController.java:81~85`），入参「协议号，为空时执行所有待处理解约」（`fep-alipay-server/.../service/AlipayPaymentService.java:31~36`）。
- 【契约】`ALIPAY_SIGN_INFO` 与登记表的写入方**分离**（同附三那条）：`AlipayContractServiceImpl.executeTermination` 只改签约表、不碰登记表；登记表状态只由 `TerminationNotifier` 写（`.../service/impl/TerminationNotifier.java:19~25`）。
- 【契约】支付中心 HTTP 客户端的职责与签名现状：「封装与支付中心的所有交互，包括支付、退款、查询、通知等。**当前签名采用测试占位，生产环境应替换为真实 RSA 签名流程**」（`alipay-pay-sign-server/.../util/PayCenterClient.java:34~39`），内部另有「测试阶段统一使用测试签名」（`:224~226`）。bizData 有两种组装：「采用 Base64 编码」与「保持明文」两个 builder 并存（`:102~118`，统一入口 `:123~128` 的 `encrypted` 开关）。**签名相关改动 MUST 提示人工复核（AGENTS.md §5.2 安全红线）。**
- 【契约】`AlipayPaySignServer` 启动类注释列出本模块的四条 RPC 依赖与用途：`@EnableRpcAlipayAccount`（查询/更新支付宝账户信息）、`@EnableRpcPara`（查询车站、线路等公共参数）、`@EnableRpcTicket`（票务相关能力）、`@EnableRpcGateTxnPay`（「支付结果回调把 `GATE_TXN_PAY.DEBIT_STATUS` 收敛到终态」）（`alipay-pay-sign-server/src/main/java/com/chinasofti/huateng/alipay/paysign/AlipayPaySignServer.java:13~24`）。

### 附六、分区表与序列（alipay-pay-sign-server）

- 【决策】为什么另起 `ALIPAY_PAY_TXN_DETAIL` / `ALIPAY_REFUND_TXN_DETAIL` 两张新表、而不继续用 `ALIPAY_PAY_LOG` / `ALIPAY_REFUND_LOG`（`alipay-pay-sign-server/src/main/resources/sql/alipay-pay-txn-schema.sql:7~13`，四条原文）：「1) 旧两表 31 / 20 列**全部是 VARCHAR2**，`PAY_AMOUNT` / `REFUND_AMOUNT` / `TRANS_TIME` 都是字符串，查询侧只能 `TO_NUMBER` / `TO_DATE(TRANS_TIME,...)`，既走不到索引，又因存量 `TRANS_TIME` 格式不统一（既有 `'2026-07-28 16:59:55'` 也有毫秒时间戳 `'1785229085685'`）而无法安全比较。2) `ALIPAY_PAY_LOG` 没有 `CREATE_TIME` / `UPDATE_TIME` 列，排序只能落到 UUID 主键 `PAY_SEQ`，等于随机序。3) 旧表没有任何唯一索引，『唯一索引 + DuplicateKeyException 兜底』这条项目主幂等写法无处落地。4) 旧表没有 `REQUEST_COUNT` / `NEXT_REQUEST_TIME`，扫表补偿无列可依。」
- 【契约】形态对齐 pay-sign 的 `pay-txn-schema.sql`：「金额一律 NUMBER 且单位为分，时间一律 TIMESTAMP，`TXN_DATE` 为 yyyyMMdd 字符串并作月分区键，主键取序列，业务唯一键是 `(ORDER_NO, TXN_DATE)` 的 LOCAL 唯一索引」（`sql/alipay-pay-txn-schema.sql:3~5`）。唯一索引即幂等地基：「并发 requestPay 时两条请求可能都看不到已存在的行，第二条 INSERT 撞这条索引即可被 catch 成『重推』而不是错误（写法照 card-pool-server 的 `isIntegrityViolation` 沿 getCause 链判定，NEVER 只 catch 最外层 DuplicateKeyException）」（`:132~134`；mapper 侧同义 `mapper/AlipayPayTxnDetailMapper.xml:75~78`，并注明「本模块已开 tracing，观测切面会换异常类型，裸 catch 会静默落空」）。
- 【决策】本脚本**不是迁移脚本**：「只服务『新建这两张表』。**它不是迁移脚本**：存量 `ALIPAY_PAY_LOG` / `ALIPAY_REFUND_LOG` 的处置（迁移 / 双读 / 冻结）尚未裁决，选定后 MUST 另出 `*-migration.sql`」（`sql/alipay-pay-txn-schema.sql:15~16`）。
- 【陷阱】**最早分区 NEVER 改成 `P202609`**：「分区列表从 P202606 起、共 8 个分区，与 AFCITPDB 里 `PAY_TXN_DETAIL` / `PAY_REFUND_DETAIL` 实测的分区布局逐个对齐（2026-09-15 查 `USER_TAB_PARTITIONS`：两表都是 P202606~P202612 + P_MAX）。NEVER 把最早分区改成 P202609：存量 `ALIPAY_PAY_LOG` 33 行的 `TRANS_TIME` 最大值是 2026-07-28，若后续裁决为『迁移存量』，那些 `TXN_DATE=202607` 的行会全部挤进 P202609 这个九月分区，分区裁剪与将来按月归档都会失准，而且**建表时看不出任何异常**」（`sql/alipay-pay-txn-schema.sql:18~22`）。后续月分区维护示例（`SPLIT PARTITION P_MAX AT ('20270201')`）留在文件末尾注释里（`:283~291`）。
- 【契约】执行状态与重跑纪律：「**本脚本已于 2026-09-16 在 AFCITPDB 全量执行并回查通过**（2 张表 × 8 分区 P202606~P202612 + P_MAX、2 个序列、10 个 LOCAL 索引 `PARTITIONED=YES`、41 条列注释）。重复执行会报对象已存在，MUST 先确认差异再补单条，NEVER 整段重跑」（`sql/alipay-pay-txn-schema.sql:24~26`）。
- 【陷阱】**MCP 执行分区表的限制已消除，但入参名与 PL/SQL 绕法有坑**：「现在可以经 `mcp_database_qd` 执行。原先『MCP 跑不了分区表』的限制已于 2026-09-16 消除 —— 那是 MCP 服务端 SQL 校验器的两个缺陷，已在 database-mcp-server 侧修好并部署（镜像 `itp/database-server:1.0.7 -> 1.0.8`，Deployment `database` / ns `itp`）：1) jsqlparser 5.3 -> 5.4，`PARTITION BY RANGE` 从此能解析（5.3 报「Encountered unexpected token: "RANGE"」）；2) 新增 `SqlTailNormalizer` 兜底钩子，仅在 jsqlparser 解析失败后触发，只摘除 `CREATE INDEX` 末尾的 LOCAL / GLOBAL 关键字尾巴（纯关键字、必须是严格前缀，带堆叠语句 / 注释 / 标点的尾巴一律拒绝），成功路径零变化。注意 `executeDdl` / `validateDdl` 的入参名不同：`executeDdl` 用 `sql`（单条），`validateDdl` 与 `executeDdlBatch` 用 `statements`（数组）。AGENTS.md §8 记的 `BEGIN EXECUTE IMMEDIATE` 绕法在这里依然不行（返「DDL statement not allowed: Block」），不需要它了。**NEVER 为了让 MCP 能跑就去掉分区或 LOCAL** —— 参照表在同一个库里就是分区的」（`sql/alipay-pay-txn-schema.sql:28~40`）。
- 【契约】索引用途逐条留注：欠费盘点支撑索引「旧表这条查询是全表扫」（`sql/alipay-pay-txn-schema.sql:141`）；进出站关联索引「这两列在旧表上无索引，`FETCH FIRST 1 ROWS ONLY` 靠全表扫」（`:151~152`）；退款汇总索引「`updateRefundSummary` 的相关子查询按 `(ORDER_NO, REFUND_STATUS)` 求和，两列都进索引」（`:266`）。
- 【契约】退款明细表与旧表的三处关键差异（DDL 侧口径，`sql/alipay-pay-txn-schema.sql:194~202`）：「1) `REFUND_AMOUNT` 是 NUMBER（旧表 VARCHAR2，汇总要 TO_NUMBER 再 TO_CHAR 回写）；2) 有 `(REFUND_ORDER_NO, TXN_DATE)` 唯一索引 —— 旧表的 `UK_ARL_REFUND_ORDER_NO` 因存量 7 行重复（联调造数固定号 `REFUND20260715120000001`）撞 `ORA-01452`，至今没建成；3) 有 `REQUEST_COUNT` / `NEXT_REQUEST_TIME`，退款回查补偿才有列可依。本表**不带 `DELETE_FLAG`**」。
- 【契约】回调凭据表的迁移脚本是**该表的唯一权威 DDL**：「本模块无 `*-schema.sql`，因此本文件是这两个对象的唯一权威 DDL」，执行记录「2026-09-14：`ALIPAY_PAY_CALLBACK_LOG` + `IDX_APCL_ORDER_NO_TYPE` + `IDX_APCL_HANDLE_STATUS` **已执行并回查通过**（`USER_TABLES` 命中 1、`USER_TAB_COLS` 14 列、`PK_ALIPAY_PAY_CALLBACK_LOG` 为 UNIQUE，两条 NONUNIQUE 索引都在）。文件末尾那条 `UK_ARL_REFUND_ORDER_NO` **未执行**」（`sql/alipay-pay-sign-callback-log-migration.sql:8~16`）。建这张表的理由：「`handlePayNotify` 此前完全不落库，同步 `GATE_TXN_PAY` 失败就回非 0000 让支付中心无限重推，而库里一条记录都没有，排查只能翻容器日志」（`:3~6`）。
- 【陷阱】`UK_ARL_REFUND_ORDER_NO` **至今未建成，退款并发双提交挡不住**：「⚠️ 2026-09-14 执行时**未建成**，原因是存量有重复：`ALIPAY_REFUND_LOG` 共 33 行，`REFUND_ORDER_NO` 去重 26（空值 0），差 7 行；全部集中在一个号 `REFUND20260715120000001`（8 行，`ORDER_NO` 同为 `GT20260715120000001`，`REFUND_STATUS` 全 FAIL，金额同为 10000，时间 2026-07-15 18:25 ~ 2026-07-16 11:17）。该号是**固定写死的联调造数**（代码生成的形态是 R + 毫秒时间戳 + 8 位 UUID），不是真实重复退款。因此本条留待『这批造数按业务确认归属后』再执行，**NEVER 为了建索引直接删行**。重跑前 MUST 先复核 `SELECT COUNT(*), COUNT(DISTINCT REFUND_ORDER_NO) FROM ALIPAY_REFUND_LOG;` 两者相等再执行。**索引缺位期间，退款幂等只靠 `PaymentRefundService` 的『同一 ORDER_NO 存在 PROCESSING 即拒绝』状态短路，挡不住真正的并发双提交**」（`sql/alipay-pay-sign-callback-log-migration.sql:49~65`）。

### 附七、配置与可观测性

- 【陷阱】`alipay-pay-sign-server` 的 OTLP 排除行 **NEVER 删**，理由整段写在配置里：「本模块 K8s Deployment 注入了 env `management.tracing.enabled=true`（同族的 alipay-account-server / fep-alipay 都没有），配合 1.1.15 镜像里旧版 micro/web 仍打开的 `management.otlp.tracing.endpoint`，`OtlpAutoConfiguration` 会建出 SpanExporter，导出线程每批抛 `UnknownHostException: collector-istio-traces-service.opentelemetry`（该 collector 在集群里不存在）刷 ERROR。Boot 3.2.6 没有 `management.tracing.export.enabled` 开关，endpoint 置空也不行（只判断键是否存在），只能排掉整个自动配置；排掉后 Tracer 与 MDC 的 traceId/spanId 照旧，仅不再创建 SpanExporter。与 `pay-sign-server/application.properties:28` 同一处理方式」（`alipay-pay-sign-server/src/main/resources/application.properties:12~20`）。**判据：本模块开了 tracing，但仓库 properties 里只有这条排除、没有 `management.tracing.enabled=true` —— 那一行由 Deployment env 注入，NEVER 据仓库 properties 判断本模块没开 tracing。**
- 【陷阱】三个模块的 `service.*.url` 默认值都带同一段告示：「服务间调用地址：默认值一律用集群内网 Service 名（`kubectl get svc -n itp` 实测，2026-09-11）。NEVER 写 127.0.0.1（等于打到自己），也 NEVER 写 `http://<模块名>:8080` 这种裸名——集群里的 Service 名都带随机后缀，例如支付宝开户是 `alipay-account-server-2n6kc-svc`，裸名 `alipay-account-server` 解析不到」（`fep-alipay-server/src/main/resources/application.properties:10~12`、`alipay-pay-sign-server/src/main/resources/application.properties:28~29`）。
- 【契约】**`service.account` 在这两个模块里指的是支付宝开户服务**：「本模块的 `service.account` 指的是**支付宝开户服务**（alipay-account-server），不是 account-server」（`fep-alipay-server/.../application.properties:13`、`alipay-pay-sign-server/.../application.properties:30`），对应键 `service.account.url` 的 env 名是 `SERVICE_ALIPAY_ACCOUNT_URL`。
- 【契约】`alipay-account-server` 的 `service.ticket.url` 留了原值错因注释（**NEVER 删**）：「原值 `http://ticket-server:9103` 名和端口都不对：Service 名带随机后缀、端口是 9100（9103 才是容器 `server.port`）」（`alipay-account-server/src/main/resources/application.properties:19~20`）。
- 【契约】`fep-alipay-server` 四条出向通知地址均注明「ITP 主动推送给支付宝的外部接口地址」：`alipay.notify.push-trans-data-url`（行程数据推送）、`alipay.notify.close-result-url`（业务关闭结果通知）、`alipay.notify.card-data-url`（行业数据推送）、`alipay.notify.blacklist-url`（黑名单状态变更通知）；另 `alipay.notify.pay-callback-url` 注明「支付完成后支付宝回调该地址」（`fep-alipay-server/src/main/resources/application.properties:43~56`）。
- 【契约】`alipay-pay-sign-server` 侧两条通知地址键的注释是「支付中心转发给支付宝」：`pay.center.close-result-notify-url`（业务关闭结果通知地址）、`pay.center.blacklist-notify-url`（黑名单变更通知地址），当前值为空（`alipay-pay-sign-server/src/main/resources/application.properties:72~75`）。
- 【契约】`PayCenterProperties` 逐字段注释给出支付中心配置的完整键集：商户号 / API 版本 / 签名类型 / 字符集 / 网关地址 / **商户私钥** / **支付中心公钥** / 支付回调地址 + 支付、退款、支付查询、退款查询、业务关闭结果通知、黑名单变更通知六条「固定地址」（`alipay-pay-sign-server/.../config/PayCenterProperties.java:12~66`）。**敏感项只列键名：`pay.center.merchant-private-key`、`pay.center.paycenter-public-key`（抽取时两者在 properties 里都是空值，由环境注入）。**

### 附八、注释缺口与自相矛盾之处（照实记录，不代笔补写）

- **缺口 1**：`fep-alipay-server` 那 9 处 `log.info("...{}", request)` 打的是收口后的 `ItpCommonFormRequest`，`bizData` 会完整进日志 —— **源码里没有任何注释提示这件事**，唯一记载在 AGENTS.md §5.1。
- **缺口 2**：三个模块的注释里**没有记销卡批处理对应的 web-admin `sys_job` job_id 与 cron**，只说「供 web-admin 的 Quartz 任务调用」。
- **缺口 3**：`AlipayPayLog` / `AlipayRefundLog` / `AlipayRegLog` / `AlipayUserInfo` / `AlipayCardPool` 的字段注释是逐字段中文名，**没有任何一条写出旧表与新表的取舍关系**（那部分只在 `AlipayPayTxnDetail` / `AlipayRefundTxnDetail` 与 schema SQL 里）。
- **缺口 4**：`AlipaySignInfoMapper` / `AlipaySignLogMapper` / `AlipayRefundLogMapper.xml` / `AlipaySignInfoMapper.xml` / `AlipaySignLogMapper.xml`、以及 `BizDataBuilder` / `IndustryDetailEnricher` / `PayLogBuilder` / `SignLogRecorder` / `RefundAmountCalculator` / `AlipayTripPaymentServiceImpl` / `AlipayPayLogQueryServiceImpl` **一条有效注释都没有**（签约主表 `ALIPAY_SIGN_INFO` 的状态词表因此无处可查）。
- **缺口 5**：`alipay-account-server/src/main/resources/sql/alipay-account-server-schema.sql` **没有头注释**（只有 `COMMENT ON` 语句），文件内也没有执行状态记录 —— 与 `alipay-pay-sign-server` 两个 SQL 的做法不一致。
- **矛盾 1（注释与代码）**：`TerminationNotifier` 类注释要求「**执行顺序 MUST 是『先通知支付中心、后改本地』**」，而 `AlipayContractServiceImpl.executeTermination` 的编号注释是「3. 更新签约状态为已解约」（`alipay-pay-sign-server/.../service/impl/AlipayContractServiceImpl.java:168~169`）在前、「4. 调用支付平台解约接口」（`:171~172`）在后，**且第 4 步失败只 `log.warn` 不回滚第 3 步**。两处对同一件事的顺序要求相反，本节不做裁决、只记录。
- **矛盾 2（注释与注释）**：`mapper/AlipayPayLogMapper.xml:138~139` 仍写「`PaymentRefundService.requestRefund` 带 `@Transactional` 且事务内调支付中心」，而 `PaymentRefundService.java:62` 的方法注释是「**NEVER 给本方法加 `@Transactional`**（2026-09-14 移除）」。XML 那句已过期。
- **矛盾 3（注释与配置）**：`PaymentRefundService.java:144` 写「本模块虽然当前没开 tracing」，而 `alipay-pay-sign-server/src/main/resources/application.properties:12~20` 的排除注释与 `mapper/AlipayPayTxnDetailMapper.xml:77` 都写「本模块已开 tracing」（靠 Deployment env 注入）。前者已过期。
- **矛盾 4（与 AGENTS.md 计数）**：AGENTS.md §5.1 写 `fep-alipay-server` 有「8 处 `log.info("...{}", request)`」，抽取时实测直接打 `ItpCommonFormRequest` 的是 **9 处**（清单见附一末条）。

### 墓碑注释清单（建议转为断言测试）

> 「墓碑注释」= 明确禁止回退到某个历史错法的注释。每条给 `文件:行号` + 禁止的事 + 能否断言化。**这些注释 NEVER 删。**

| # | 文件:行号 | 禁止的事 | 能否断言化 |
|---|---|---|---|
| 1 | `alipay-account-server/.../service/impl/AlipayAccountServiceImpl.java:60~61` | NEVER 给 `requestApplication` 重新加 `@Transactional` | 能：反射读方法与类上的 `@Transactional`，断言不存在 |
| 2 | `alipay-account-server/.../service/impl/AlipayAccountServiceImpl.java:225~228` | NEVER 把 ITP 侧「同步显示账号到支付域」照抄进支付宝换号；`ALIPAY_PHONE_CHANGE_LOG` NEVER 加 `SIGN_SYNC_*` 列 | 部分：可断言实体无 `signSync*` 字段、mapper XML 不含 `SIGN_SYNC`；「不调 pay-sign」需用 mock 校验零交互 |
| 3 | `alipay-account-server/.../entity/AlipayPhoneChangeLog.java:9` | NEVER 把 `ALIPAY_PHONE_CHANGE_LOG` 与 `USER_PHONE_CHANGE_LOG` 合表或共用序列 | 难：属库结构约定，只能做 DDL 文本断言（脚本内序列名唯一） |
| 4 | `alipay-pay-sign-server/.../service/impl/PaymentRequestService.java:53~57` | NEVER 恢复「扣费同时写 `ALIPAY_PAY_LOG`」的双写 | 能：mock `AlipayPayLogMapper`，断言 `requestPay` 路径零写入 |
| 5 | `alipay-pay-sign-server/.../service/impl/PaymentRequestService.java:144~148` | 传输层失败 / `code=600` / 5xx / 空响应 NEVER 加黑名单 | 能：参数化用例喂四种失败形态，断言 `blacklistClient` 零调用 |
| 6 | `alipay-pay-sign-server/.../service/impl/PaymentQueryService.java:203~215` | NEVER 再读写 `ALIPAY_PAY_LOG`；NEVER 加 `@Transactional`；NEVER 回退成「只返非 0000、库里不留痕」 | 能：前两条同上；第三条断言每次回调都有一行 `ALIPAY_PAY_CALLBACK_LOG` |
| 7 | `alipay-pay-sign-server/.../service/impl/PaymentQueryService.java:267~269` | `transStatus` NEVER 写成「等于 1 就成功、否则失败」 | 能：喂 `1` / `2` / `3` / `null` / 乱码，断言只有 1、2 有映射，其余为 null |
| 8 | `alipay-pay-sign-server/.../service/impl/PaymentQueryService.java:411~413` | 对外 `debitRequestResult` NEVER 塞渠道文案（只能 `0`/`1`） | 能：断言返回值 ∈ {"0","1"} |
| 9 | `alipay-pay-sign-server/.../service/impl/PaymentQueryService.java:313~314` | NEVER 因为计数失败就直接放行返 0000 | 能：让计数抛异常，断言退化为 0 且仍走「让上游重推」分支 |
| 10 | `alipay-pay-sign-server/.../service/impl/PaymentRefundService.java:62~67` | NEVER 给退款方法加 `@Transactional` | 能：反射断言无注解 |
| 11 | `alipay-pay-sign-server/.../service/impl/PaymentRefundService.java:75~76`、`:173` | 拿不到业务应答时 NEVER 把退款明细置 FAIL（保持 PROCESSING） | 能：mock 空响应 / IO 异常，断言明细状态仍为 PROCESSING |
| 12 | `alipay-pay-sign-server/.../service/impl/PaymentRefundService.java:103` | NEVER 去掉「同一原订单存在 PROCESSING 即拒绝」的状态短路 | 能：先造一条 PROCESSING 明细，断言第二次申请被拒 |
| 13 | `alipay-pay-sign-server/.../service/impl/PaymentRefundService.java:143~145`、`:245~246` | NEVER 只 catch 最外层 `DuplicateKeyException` | 能：抛「被包了两层的」完整性冲突异常，断言仍走幂等分支 |
| 14 | `alipay-pay-sign-server/.../service/impl/AlipayTerminationInternalServiceImpl.java:23`、`TerminationNotifier.java:31` | 销卡批处理与销卡执行 NEVER 加 `@Transactional` | 能：反射断言无注解 |
| 15 | `alipay-pay-sign-server/.../service/impl/AlipayTerminationInternalServiceImpl.java:29~30` | NEVER 补一个恒返回「无欠费」的假校验冒充已校验 | 难：属设计意图，只能靠评审；可退化为「该方法内不出现 `return true` 形式的欠费桩」的静态检查 |
| 16 | `alipay-pay-sign-server/.../service/impl/AlipayTerminationInternalServiceImpl.java:117` | 基准时间解析失败 NEVER 静默退化成全表扫描 | 能：喂坏时间串，断言返 INVALID_PARAM 且 mapper 未被调用 |
| 17 | `alipay-pay-sign-server/.../service/impl/TerminationNotifier.java:29`、`:105` | 通知失败 / 未知异常 NEVER 置 FAIL（保持 PENDING） | 能：mock 通知失败与抛异常两路，断言状态仍 PENDING |
| 18 | `alipay-pay-sign-server/.../service/impl/TerminationNotifier.java:130` | NEVER 把我方 `AGREEMENT_CODE` 与渠道 `CHANNEL_AGREEMENT_CODE` 混用 | 能：断言出向报文 `agreementNo` 取自 `channelAgreementCode` |
| 19 | `alipay-pay-sign-server/.../service/impl/TerminationRegistrationService.java:54~55` | `thirdUserId` 回填不到时 NEVER 登记 | 能：断言该分支不产生 INSERT |
| 20 | `alipay-pay-sign-server/.../mapper/AlipayPayLogMapper.java:21`、`mapper/AlipayPayLogMapper.xml:111`、`mapper/AlipayPayTxnDetailMapper.xml:252` | `payQuery` NEVER 把已 SUCCESS 的订单改写成 FAIL | 能：集成测试造 SUCCESS 行，跑 payQuery 回写断言影响 0 行、状态不变 |
| 21 | `alipay-pay-sign-server/.../mapper/AlipayPayLogMapper.java:38`、`mapper/AlipayPayLogMapper.xml:133~134`、`mapper/AlipayPayTxnDetailMapper.xml:275~276` | 退款汇总 NEVER 写成 `REFUND_AMOUNT = NVL(...) + 增量` | 能：连跑两次 `updateRefundSummary`，断言金额不变（幂等） |
| 22 | `alipay-pay-sign-server/.../mapper/AlipayRefundTxnDetailMapper.java:33`、`mapper/AlipayRefundTxnDetailMapper.xml:96` | NEVER 拿「已退成功金额合计」的返回值回写主表 | 部分：可静态检查该方法返回值不流向 `updateRefundSummary` 入参 |
| 23 | `alipay-pay-sign-server/.../mapper/AlipayRefundTxnDetailMapper.java:13`、`mapper/AlipayRefundTxnDetailMapper.xml:116` | 两条 UPDATE 的 WHERE NEVER 只按 `REFUND_ORDER_NO`（须带 `TXN_DATE`） | 能：断言 XML 文本含 `TXN_DATE` 谓词（或用不同 txnDate 造两行验证只命中一行） |
| 24 | `alipay-pay-sign-server/.../mapper/AlipayPayTxnDetailMapper.java:79~81`、`mapper/AlipayPayTxnDetailMapper.xml:353` | 分页 NEVER 回退成让调用方传行号上界 | 能：造 15 行取第 2 页，断言返 5 行（旧写法返 0 行） |
| 25 | `mapper/AlipayPayTxnDetailMapper.xml:155`、`:215~217` | 关联查询 NEVER 去掉 `ORDER BY`；状态白名单 NEVER 把入参写进判断 | 能：前者靠 XML 文本断言；后者断言重复 SUCCESS 回调影响 0 行 |
| 26 | `mapper/AlipayPayTxnDetailMapper.xml:317`、`sql/alipay-pay-txn-schema.sql:20` | 时间过滤 NEVER 退回 `TO_DATE(TRANS_TIME,...)`；最早分区 NEVER 改成 `P202609` | 部分：前者 XML 文本断言；后者只能做 DDL 文本 / `USER_TAB_PARTITIONS` 巡检 |
| 27 | `mapper/AlipayPayCallbackLogMapper.xml:35~36` 等 5 处 XML 注释 | SQL 正文 NEVER 写行/块注释；XML 注释内 NEVER 出现连续两个减号 | 能：CI 里跑 `xmllint --noout` + 正则扫 `<select|update|insert>` 正文内的注释符 |
| 28 | `alipay-pay-sign-server/.../entity/AlipayPayTxnDetail.java:15`、`:31` | 新表金额 NEVER 改回 String；库内 `DEBIT_REQUEST_RESULT` 与对外 `debitRequestResult` NEVER 混用 | 能：反射断言字段类型为 `Integer`；值域断言分别 ∈ {PROCESSING,SUCCESS,FAIL} 与 {"0","1"} |
| 29 | `alipay-pay-sign-server/.../entity/AlipayRefundTxnDetail.java:22`、`:36~37` | 退款 `txnDate` NEVER 复用原支付单日期；拿不到应答 NEVER 置 FAIL | 能：跨零点用例断言两个 txnDate 可不同；后者同第 11 条 |
| 30 | `alipay-pay-sign-server/src/main/resources/application.properties:12` | NEVER 删 `OtlpAutoConfiguration` 排除行 | 能：断言该键值包含 `OtlpAutoConfiguration` |
| 31 | `alipay-pay-sign-server/src/main/resources/sql/alipay-pay-txn-schema.sql:26`、`:40` | NEVER 整段重跑本脚本；NEVER 为迁就 MCP 去掉分区或 LOCAL | 难：属操作纪律，只能巡检 `USER_TAB_PARTITIONS` / `USER_INDEXES.PARTITIONED` |
| 32 | `alipay-pay-sign-server/src/main/resources/sql/alipay-pay-sign-callback-log-migration.sql:59` | NEVER 为了建 `UK_ARL_REFUND_ORDER_NO` 直接删行 | 难：只能靠评审 + 建索引前的 `COUNT(*)` vs `COUNT(DISTINCT)` 前置检查 |
| 33 | `fep-alipay-server/.../service/impl/AlipayQueryServiceImpl.java:255` | NEVER 悄悄改回读 `ALIPAY_PAY_LOG`（该表已停写，只会静默命中 0 行） | 能：mock 校验该分支不访问 `ALIPAY_PAY_LOG` |
| 34 | `fep-alipay-server/src/main/resources/application.properties:11`、`alipay-account-server/.../application.properties:20`、`alipay-pay-sign-server/.../application.properties:29` | `service.*.url` NEVER 写 `127.0.0.1`、NEVER 写不带随机后缀的裸 Service 名 | 能：配置校验测试，正则拒 `127.0.0.1` 与 `http://[a-z-]+:\d+` 形态 |
| 35 | `alipay-pay-sign-server/.../service/impl/TerminationNotifier.java:44` | 支付中心成功码 NEVER 再引入第二套判定（与 `PaymentNotifyAdapter` 一致） | 部分：可断言两处常量/判定方法同源 |

## 附：支付宝渠道三模块 注释知识迁移（2026-09-16，阶段二·完整）

> **本节是「迁移」不是「摘录」**：条目落地后，三个模块源码里对应的多行叙述型 / MUST-NEVER / 事故史 / 墓碑注释**已被删除**，代码只留标准 Javadoc。**本节因此是这批知识的唯一载体，NEVER 因为「代码里搜不到那句话了」就认为约束不存在**。上面阶段一那节（附一~附八 + 墓碑清单）是同一批注释的**首轮摘录**，其条目仍有效，但**它引用的注释原文多数已不在代码里**。
> **NEVER 照抄本节行号跳转**：行号是迁移当时（删注释前）的快照，删注释后源码行号已整体上移。定位 MUST 用每条给出的「grep 锚点」——锚点一律取**代码标识符**（方法名 / mapper 语句 id / 常量 / 配置键 / 索引名），不取注释文本，因为注释文本已删。
> 迁移范围：三模块 `src/main/java/**/*.java`、`src/main/resources/mapper/*.xml`、`src/main/resources/sql/*.sql`、`src/main/resources/application.properties`、`src/test/java/**/*.java`。

### 零、留在代码里的一行式护栏（**NEVER 删这三处**）

这三条是「删掉就会把风险藏起来」的现场标记，迁移时刻意保留、且只保留一行：

| # | 位置（grep 锚点） | 保留的护栏 | 为什么不能只写在文档里 |
|---|---|---|---|
| 1 | `alipay-pay-sign-server/.../service/impl/PaymentRefundService.java`，锚点 `public AlipayTripRequestRefundRespDTO requestRefund` | 「NEVER 给本方法加 `@Transactional`」 | 该方法体内调支付中心，加回注解即复现 2026-08-26 生产事故（AGENTS.md §5.2）；改这个方法的人不一定读 docs |
| 2 | `alipay-pay-sign-server/src/main/resources/mapper/AlipayPayLogMapper.xml`，锚点 `id="updateRefundSummary"` | 同上一条（退款汇总语句旁提醒调用方无事务） | 汇总语句的幂等性与「调用方无事务」是一对前提，看 SQL 的人需要就地知道 |
| 3 | `alipay-pay-sign-server/.../util/PayCenterClient.java`，锚点 `private void signRequest` | 「这是占位签名，上线前 MUST 替换为真实签名」 | **这是 P0 上线阻塞项的唯一现场标记**，删掉等于把 P0 藏起来 |

另：各 mapper XML 内「SQL 正文禁写注释（Druid WallFilter）」那条一行提示**一并保留**（`AlipayPayLogMapper.xml` / `AlipayPayTxnDetailMapper.xml` / `AlipayPayCallbackLogMapper.xml`），它约束的是**下一个改这条 SQL 的人**，属就地生效型护栏。

### 一、alipay-pay-sign-server

#### 1.1 【已裁决 P0 资金/一致性缺陷】`AlipayContractServiceImpl.executeTermination` 顺序颠倒

> grep 锚点：`AlipayContractServiceImpl` 里 `public AlipayCommonResponse executeTermination`；
> 入口链路 `fep-alipay-server` `FepAlipayTripPaymentController` 的 `executeTermination`（`/admin/payment`，web 管理控制台触发）→ `AlipayPaySignClient` → `AlipayPaySignController.executeTermination`。

**当前实测形态（迁移时逐行确认）**，四个问题叠在同一个方法里：

1. **顺序颠倒**：`alipaySignInfoMapper.updateStatus(..., "TERMINATED", ...)` 在 `paymentNotifyAdapter.notifyCloseResult(...)` **之前**。违反 AGENTS.md §5.2「跨模块写操作 MUST 先调远端、后改本地」。
2. **没有 `@Transactional`**（同类的 `addContract` / `terminateContract` 都有）。**这一条不是缺陷、不要去加**：方法体内有 HTTP 出网，加事务即复现 2026-08-26 生产事故；它的真正后果是**先改的本地状态无法随远端失败回滚**，只能靠改顺序解决。
3. **用错协议号**：通知时传 `signInfo.getAgreementCode()`（我方 ITP 内部号），而支付中心只认签约时它返回的 `CHANNEL_AGREEMENT_CODE`。同模块 `TerminationNotifier` 已于 2026-09-07 生产实测：传我方号 `070000144735309128` 返 `code=600 未查询到协议信息`（**不是 404、不是签名错，极易误判成业务原因**）。
4. **通知地址为空**：`pay.center.close-result-notify-url` 在 `alipay-pay-sign-server/src/main/resources/application.properties` 里是**空值**。`PayCenterClient.callPayCenter` 对空 URL 直接 `log.error("支付中心接口地址未配置")` 并返回 null，`PaymentNotifyAdapter.notifyCloseResult` 于是返回 `SYSTEM_ERROR`「调用支付中心通知接口失败」。

**合并后果（判据）**：这条链路**恒定**是「ITP 侧 `ALIPAY_SIGN_INFO.SIGN_STATUS` 已置 `TERMINATED`、支付中心侧协议仍在签约中」，且失败分支只 `log.warn` 不回滚、不落工单；`ALIPAY_TERMINATION_REQUEST.STATUS` 这条路径**从不触碰**（唯一回写方是 `TerminationNotifier`）。用户已按「先调远端、后改本地」裁决为**必须改**，但**本次只写文档、NEVER 改代码**。

**建议改法（留给下一次带测试的改动，MUST 三条同时做）**：
- **委托给 `TerminationNotifier`**：该类已实现正确形态（先 `notifyCloseResult` → 远端明确成功才 `updateStatus(TERMINATED)` + 登记表 `PENDING -> COMPLETED` 的 CAS；通知失败保持 `PENDING` 等下轮，**NEVER 置 FAIL**（FAIL 是终态、会永久卡死））。`executeTermination` 应退化为「按 `agreementCode` 取登记记录 → 交给 `TerminationNotifier.execute`」，**NEVER 在两个类里各留一份销卡语义**。
- **通知 MUST 用 `channelAgreementCode`**，缺失时按 `TerminationNotifier` 现有做法退回我方号并打 WARN（对端大概率仍查不到，属数据缺陷显性化）。
- **远端未明确成功时 NEVER 改本地状态**，把状态留在中间态等下一轮重入；返回给上游的 `retCode` MUST 反映「未完成」。

配套前提：`pay.center.close-result-notify-url` 为空时链路必然失败，**改代码前 MUST 先确认该键在 K8s Deployment env 里有真实值**（AGENTS.md §8：判断线上真实地址 MUST 查 Deployment env）。

#### 1.2 【P0 上线阻塞】`PayCenterClient.signRequest` 是 `sign="test"` 占位

- **位置**：`alipay-pay-sign-server/.../util/PayCenterClient.java`，grep 锚点 `private void signRequest`。方法体只有两行：打一条 `log.warn`，然后 `request.setSign("test")`。
- **影响面**：`buildRequest` 是**所有**出向支付中心报文的唯一组装口，因此 `requestPay` / `requestRefund` / `payQuery` / `refundQuery` / `closeResultNotify` / `blacklistNotify` **六个接口全部带假签名出网**。
- **后果**：一旦支付中心开启验签，六条链路同时失败；而失败形态是网关业务码（实测同类问题返 `code=600 操作失败`），**不是 401/403**，极易被当成业务原因排查。
- **同文件已有全部素材、只差接线**：`buildSignData`（按 `merchantNo`/`apiVersion`/`signType`/`charset`/`bizData` 顺序拼 `k=v&`）与 `signWithRsa`（`SHA256WithRSA` + PKCS8 私钥 + Base64）**都已实现但零调用方**；私钥键 `pay.center.merchant-private-key`、公钥键 `pay.center.paycenter-public-key` 在 properties 里**都是空值**。
- **NEVER 顺手接线**：属 AGENTS.md §5.2「安全红线」，改签名逻辑 MUST 人工复核 + 密钥经 K8s Secret 注入（**NEVER 写默认真值**）。代码里已保留一行护栏标记（§零 第 3 条）。

#### 1.3 退款申请 `PaymentRefundService.requestRefund`（无事务 + 三分支收口）

grep 锚点：`PaymentRefundService`、`countByOrderNoAndStatus`、`updateRefundStatus`、`isIntegrityViolation`。

- 【契约】**本方法已无事务**：`@Transactional` 于 2026-09-14 移除，方法声明上现在**没有任何事务注解**。因此**每条 SQL 自动提交，退款明细行在调支付中心之前就已落地**，那一行是「这笔退款存在过」的唯一凭据。
- 【陷阱】**NEVER 据旧注释把事务加回去**（代码里已留一行护栏，§零 第 1 条）：事务内出网会把行锁持有时长拉成对端响应时长，上游对同一笔的重推全堆在同一行上串行等待；超过 Druid `remove-abandoned-timeout` 后连接被强杀、`commit` 抛 `connection closed`，**整个事务连同刚插入的退款明细一起丢弃**，而支付中心那边可能已经受理了退款。同一成因的生产事故见 AGENTS.md §5.2（2026-08-26）。
- 【契约】顺序 MUST 是「落 `PROCESSING` 明细 → 调支付中心 → 按业务应答收口」，三种收口对应 `Ok` / `BizRejected` / `Unreachable`：
  - 业务成功（解密 `data` 后 `retCode == "SUCCESS"`）→ 明细置 `SUCCESS`，**再**按明细重算原单退款汇总（`updateRefundSummary`）；
  - 业务明确拒绝 → 明细置 `FAIL`（终态，允许重新申请）；
  - **拿不到业务应答（响应为空 / 传输层失败）→ 明细保持 `PROCESSING`**，返非 `0000` 并打 ERROR。**NEVER 在这个分支置 FAIL**：远端可能已受理，置 FAIL 会让汇总少记一笔已退金额，随后同一笔还能再退一次，**直接造成重复退款**。
- 【契约】`PROCESSING` 是 2026-09-14 新增的中间态。此前初始状态直接写 `FAIL` 而 `RESULT_MSG` 写「退款处理中」，两者自相矛盾，且「远端不可达」与「业务拒绝」都落成同一个 FAIL、无法区分。
- 【契约】**幂等短路是唯一防线，NEVER 去掉**：本表没有调用方提供的幂等键（`REFUND_ORDER_NO` 是我方每次新生成的 `R + 毫秒 + 8 位 UUID`），因此「同一 `ORDER_NO` 还有 `PROCESSING` 明细就拒绝新申请」是唯一能挡住「远端已受理、本地结果未回写」时重复退款的判断。
- 【陷阱】插入侧兜底 MUST 沿 `getCause()` 链判完整性冲突（`isIntegrityViolation`），**NEVER 只 catch 最外层 `DuplicateKeyException`** —— **本模块已开 tracing**，观测切面会把异常重新包一层（AGENTS.md §5.2 / ADR-D53），裸 catch 会静默落空。
- 【契约】汇总回写 MUST 在明细置 `SUCCESS` **之后**（SQL 是按明细表重算的，顺序颠倒会漏掉本笔）；`updateRefundSummary` 影响 0 行时只打 ERROR 等人工核对，**NEVER 当成功**。
- 【契约】退款报文必带字段：`orderNo` / `cardIssueCode`（固定 `0007`）/ `cardNum`（取原支付单 `CARD_ID`）/ `channelAgreementNo`（取 `ALIPAY_SIGN_INFO.CHANNEL_AGREEMENT_CODE`，查不到即拒）/ `refundAmount` / `refundOrderNo`。前置校验：原支付记录存在、`PAY_STATUS == SUCCESS`、有逻辑卡号。

#### 1.4 支付回调与结果查询 `PaymentQueryService`

grep 锚点：`handlePayNotify`、`MAX_PAY_CALLBACK_PUSH`、`countPushTimes`、`markManual`、`syncDebitStatus`、`updatePayQueryResultIfNotSuccess`。

- 【契约】**扣费订单已收口到 gate-txn-pay-server**，本方法 **NEVER 再读写 `ALIPAY_PAY_LOG`**，只把结果同步到 `GATE_TXN_PAY.DEBIT_STATUS`（唯一权威），业务幂等由下游按订单号 + 状态白名单保证。
- 【契约】形态对齐 pay-sign-server：**回调即入库**当凭据 → 按累计推送次数判是否到上限 → 同步下游 → 失败且未到上限返非 `0000` 让支付中心重推、到上限返 `0000` 停推并把该行置 `HANDLE_STATUS='MANUAL'`。**NEVER 回退成「只返非 0000、库里不留痕」** —— 那样支付中心无限重推，我方除容器日志外没有任何可查证据。
- 【契约】`MAX_PAY_CALLBACK_PUSH = 2`（首推 1 + 重推 1），与 pay-sign 同口径。**支付中心的重推次数 / 间隔 / 上限没有规格**（`docs/external/支付中心网关接口文档.md` §5 只写「未收到成功响应会重试」），这个 2 是经验值，**MUST 向供方索取重试规格后再校准，NEVER 把日志观察值当契约**。
- 【契约】**NEVER 给 `handlePayNotify` 加 `@Transactional`**：方法体内有 RPC；无事务时每条 SQL 自动提交，`ALIPAY_PAY_CALLBACK_LOG` 那行凭据不会被回滚掉。计数点 MUST 在 insert **之后**（insert 已提交，`COUNT` 才等于真实推送次数）；取不到计数时返 0 = 「不启用硬限次、保持让上游重推」，**NEVER 因为计数失败就直接放行返 `0000`**。
- 【契约】达上限走「回 `0000` 停推 + 置 `MANUAL` + 打 ERROR」，这是「不再自动重试」换「不再自我放大」的取舍，因此**运维 MUST 例行巡检 `HANDLE_STATUS='MANUAL'`**，否则失败静默沉底。
- 【契约】`transStatus` 白名单映射：只认 `1` 成功 / `2` 失败，其余返 `null`。**NEVER 写成「等于 1 就成功、否则失败」** —— 黑名单式判定会把新增取值、空值、乱码统统当扣款失败，把一笔可能已成功的扣费同步成 FAIL（AGENTS.md §5.2「状态机校验用白名单」）。
- 【契约】对外字段 `debitRequestResult` 值域**只有** `"0"`（扣费成功）/ `"1"`（未成功），**NEVER 塞渠道文案** —— 上线前该字段填的是 `ALIPAY_PAY_LOG.RESULT_MSG`，支付宝侧按 0/1 解析，任何文案都被判成非 0，成功单也显示扣费未成功。同一套映射在 `fep-alipay-server` 的 `AlipayQueryServiceImpl` 里另有两份同名私有方法，**三处 MUST 保持一致**（跨模块不能直接复用，见 AGENTS.md §5.1「NEVER 主动创建新的工具类」）。
- 【契约】查询回写带非终态白名单：影响 0 行有两种含义、**MUST 回读区分**（本地已是目标状态 = 幂等；本地已 `SUCCESS` 而支付中心说失败 = 口径冲突，只打 ERROR 等人工），**NEVER 把终态覆盖成 FAIL**。
- 【契约】回调落库自身失败只记日志、不打断处理：留痕失败不该让一笔已扣款的回调收不了口。
- 【契约】`syncDebitStatus` 与 pay-sign 同形态：显式判 `retCode=0000`，失败只记日志并交回调用方决定是否让上游重推。

#### 1.5 扣费申请 `PaymentRequestService` 与黑名单联动

grep 锚点：`PaymentRequestService`、`blacklistClient.addBlackList`。

- 【契约】**NEVER 再写 `ALIPAY_PAY_LOG`**：过闸扣费已收口到 gate-txn-pay-server，订单与状态的唯一权威是 `GATE_TXN_PAY`（落单在 `PaySignInitiator` 之前完成，幂等靠该表唯一键 + 状态白名单）。这里再落一份日志表就是双写，两边状态一旦分叉无法判定谁对。「订单已存在直接返回」的短路也随之下沉到 gate-txn-pay-server，本方法只负责调支付中心。
- 【契约】**加黑名单只允许在「支付中心已给出业务应答且判定为扣款失败」这一支调用**（解密 `data` 后 `retCode != SUCCESS`）。传输层失败、网关路径错（实测形态 `code=600 操作失败`，见 AGENTS.md §8）、对端 5xx、响应为空**都 NEVER 加黑名单** —— 那些与乘客付款能力无关，加黑会直接拦住其过闸。
- 【契约】`blacklistClient.addBlackList` 是「返回结果对象、不抛异常」的 RPC 包装，**MUST 显式判 `retCode`**（AGENTS.md §5.2）；判不过只打 ERROR，不影响本次支付申请对上游的应答。
- 【现状】方法内保留了一段被注释掉的「免密场景 `requestSignSeq` 非空校验」死代码（`grep 'requestSignSeq' PaymentRequestService.java`），迁移时**未动**：它是代码而非知识型注释，删它属行为变更范围。

#### 1.6 销卡（解约执行）链路

grep 锚点：`TerminationNotifier.execute`、`TerminationRegistrationService`、`AlipayTerminationInternalServiceImpl`、`updateStatusCas`。

- 【契约】**`TerminationNotifier` 是唯一会回写 `ALIPAY_TERMINATION_REQUEST.STATUS` 的地方**；`AlipayContractServiceImpl.executeTermination` 只改 `ALIPAY_SIGN_INFO`、从不碰登记表（见 §1.1）。该类曾长期是死代码（被注入、无调用点），2026-09-07 由销卡批处理 `AlipayTerminationInternalServiceImpl` 启用。
- 【契约】执行顺序 MUST 是「先通知支付中心、后改本地」：顺序颠倒会留下「ITP 已置 `TERMINATED`、支付中心仍认为签约中」的不一致，且无法自愈。
- 【契约】通知失败 **NEVER 置 `FAIL`** —— `FAIL` 是终态，会让这条登记永久卡死；保持 `PENDING` 等下一轮。异常原因未知（可能网络抖动）同理保持 `PENDING`。**只有一种情况判 `FAIL`：签约信息不存在**（重试多少次都不会变出一条签约记录）。
- 【契约】三分支返回 `Outcome.TERMINATED` / `FAILED` / `RETRY_LATER`，调用方按此计数，**NEVER 假定「没抛异常就是成功」**。`updateStatusCas`（`terminationSeq` + 原状态）返回 0 说明已被别的执行流收口，本轮不重复计数。
- 【契约】**出向 `agreementNo` MUST 取 `CHANNEL_AGREEMENT_CODE`，NEVER 用我方 `AGREEMENT_CODE`**（2026-09-07 实测 `code=600 未查询到协议信息`）。签约记录没存渠道号属数据缺陷，代码退回我方号只为保留旧行为、对端大概率仍查不到，此时会打 WARN。
- 【契约】**销卡批处理与销卡执行都 NEVER 加 `@Transactional`**（逐条要调支付中心 HTTP）；每条记录各自独立收口，一条失败不影响其余。单批有上限，排空靠调用方多轮调用，**不在方法内循环**。
- 【契约】**批处理当前未做欠费校验，且这是有意的**：2026-09-07 实测 `GATE_TXN_PAY` 与 `ALIPAY_PAY_LOG` 订单号交集为 0、`THIRD_USER_ID` 体系与 `PAYMENT_VENDOR` 均不同，支付宝出行的「未结清」语义在现有数据里找不到落点，因此留成显式扩展点。**NEVER 补一个恒返回「无欠费」的假校验冒充已校验。**
- 【契约】基准时间解析失败 MUST 返 `INVALID_PARAM`，**NEVER 静默退化成全表扫描**。
- 【陷阱】历史脏数据：登记时未回填 `thirdUserId` 的记录做不了用户维度校验，批处理跳过并留日志等人工。因此**登记侧 `TerminationRegistrationService` 在 `thirdUserId` 回填不到时 NEVER 登记** —— 一条三字段全 NULL 的 `PENDING` 记录既跑不通批处理、也无法人工追溯（生产已产生过一条）。
- 【陷阱】登记存在性判断 MUST 用 `countByAgreementCode`，**NEVER 用 `selectByAgreementCode`** —— 该表**没有 `AGREEMENT_CODE` 唯一约束**，重复登记时后者抛 `TooManyResultsException`。同理 `updateStatusByAgreementCode`（无 CAS）会把同一协议号的所有行一起改掉且不校验原状态，**批处理 MUST 用 `updateStatusCas`**。
- 【契约】支付中心通知类接口的成功码判定**只有一套**：`PAY_CENTER_SUCCESS_CODE = "200"`，与 `PaymentNotifyAdapter` 保持一致，**NEVER 再引入第二套**。而 `PaymentNotifyAdapter.isPayCenterNotifySuccess` 要**同时认两种形态** —— 通知类接口（`receiveBlackListFromItp`）实测返 `retCode=0000` 不带 `success`；支付类接口返 `success=true` / `code=200`，只认一种会把成功应答判成失败并触发无意义重试与告警。

#### 1.7 落库与 mapper 契约（旧表 `ALIPAY_*_LOG`）

grep 锚点：mapper 语句 id。

- `AlipayPayLogMapper.updatePayQueryResultIfNotSuccess`（XML `id="updatePayQueryResultIfNotSuccess"`）：带「非终态」白名单（`PAY_STATUS` 为空或不等于 `SUCCESS`）。**已发生的错法是无条件 UPDATE**，于是「支付中心暂时查不通」会把一笔已扣款成功的订单打成 `FAIL`，而 `PAY_STATUS` 又是欠费盘点 `countUnsettledByCardId` 的判据 —— 直接把乘客算成欠费。0 行两种含义 MUST 回读区分。
- `AlipayPayLogMapper.updateRefundSummary`：**MUST 是「按 `ALIPAY_REFUND_LOG` 重算」，NEVER 写成 `REFUND_AMOUNT = NVL(REFUND_AMOUNT,0) + #{refundAmount}`**。入参没有幂等键，累加式写两次就多记一笔退款金额，而这列是 `RefundAmountCalculator` 判「可退金额 = 已付 - 已退」的依据；一旦虚高，后续真实退款被误判超额并拒绝，**账面无法自愈**。重算写法天然幂等：只取 `REFUND_STATUS='SUCCESS' AND DELETE_FLAG='0'` 的行，执行 1 次与 N 次落库值相同，也不依赖 Java 侧的旧值快照。该表金额是字符串，所以 `TO_NUMBER` 求和后 `TO_CHAR` 回写（明细金额入库前都过了 `RefundAmountCalculator.parseAmount`，非数字串在那里就抛）。已退总额为 0 时保留原 `REFUND_STATUS`（说明还没有任何一笔退款成功）。
- `AlipayPayLogMapper.countUnsettledByCardId`：供 blacklist-server 盘点该卡欠费是否结清。口径「`PAY_STATUS` 非 `SUCCESS` 即未结清」，**MUST 显式兼容 NULL**（Oracle 三值逻辑下 `!= 'SUCCESS'` 对 NULL 不成立，漏掉会把脏数据判成已结清）。支付宝出行链路的欠费**只落本表**、`GATE_TXN_PAY` 里没有对应行（分流点见 `fep-dev-server` 的 `GateTransactionHandler`：`issueChannelCode=07` 走支付宝、其余走闸机扣费），因此判定 MUST 同时查两张表。
- 【⚠️ 已知口径污染，未修】`PaymentRequestService` 把支付中心的**幂等拒答**（如「订单已支付成功，请勿重复支付」）也当扣款失败、连带把 `PAY_STATUS` 写成 `FAIL`。**因此本表的 `FAIL` 不完全等于真欠费**；该缺陷修复前，盘点结果只能人工复核，**NEVER 直接拿它驱动删除黑名单**。
- `AlipayArrearsQueryService`（只读，**NEVER 加写操作**）：查询未执行时 **NEVER 返回 false** —— 调用方会把 false 当「已结清」，接上自动解除黑名单就等于放行仍欠费的乘客。参数缺失时 `resultCode` 非 `0000` 且 `hasUnsettled` 固定 `true`，与 gate-txn-pay 侧同一套兜底。
- `AlipayTerminationRequestMapper`：`countByAgreementCode` 判存在性、`updateStatusCas` 按主键 + 原状态、`selectByStatus*` 最老优先（Oracle 用 `ROWNUM` 包一层限量）、`CREATE_TIME` 即申请时间（**该表没有专用申请时间列**）。
- `AlipayPayCallbackLogMapper.countPushTimes` / `markLastAsManual`：计数点 MUST 在 insert 之后；`markLastAsManual` 用子查询取 `CREATE_TIME` 最大的一行、同秒并列按 `CALLBACK_SEQ` 兜底定序，**保证恒定只影响一行**（一笔订单可能多次推送，需人工看的是最终仍未收口那次）。
- `AlipayPhoneChangeLogMapper.insert`（`alipay-account-server`）：主键由 `SEQ_ALIPAY_PHONE_CHANGE_LOG.NEXTVAL` 在 SQL 内直接取。**NEVER 加 `useGeneratedKeys="true" keyProperty="id"`** —— Oracle 的 `getGeneratedKeys` 在不声明返回列时取不到值，MyBatis 抛 `MyBatisSystemException`；而 `AlipayAccountServiceImpl.updatePhone` 带 `@Transactional(rollbackFor = Exception.class)`，异常会把前面已成功的 `ALIPAY_USER_INFO.MSISDN` 更新一起回滚、catch 后 `return false`，表现为「支付宝用户换手机号恒失败且库里毫无痕迹」（2026-09-11 修复；佐证：修复前 `USER_PHONE_CHANGE_LOG` 中 `USER_TYPE='ALIPAY'` 零行）。调用方不使用 `changeLog.getId()`，无需回填主键。

#### 1.8 新表 `ALIPAY_PAY_TXN_DETAIL` / `ALIPAY_REFUND_TXN_DETAIL`（分区表，**已在 `AFCITPDB` 执行并回查**）

grep 锚点：`sql/alipay-pay-txn-schema.sql`、`UK_APTD_ORDER`、`UK_ARTD_REFUND_ORDER`、`AlipayPayTxnDetailMapper`、`AlipayRefundTxnDetailMapper`。

- 【执行状态】**`alipay-pay-txn-schema.sql` 已于 2026-09-16 在 `AFCITPDB` 全量执行并回查通过**：2 张表 × 8 分区（`P202606`~`P202612` + `P_MAX`）、2 个序列、10 个 LOCAL 索引（`PARTITIONED=YES`）、41 条列注释。**重复执行会报对象已存在，MUST 先确认差异再补单条，NEVER 整段重跑。**
- 【执行状态】`alipay-pay-sign-callback-log-migration.sql`：`ALIPAY_PAY_CALLBACK_LOG` + `IDX_APCL_ORDER_NO_TYPE` + `IDX_APCL_HANDLE_STATUS` **已于 2026-09-14 执行并回查通过**（`USER_TABLES` 命中 1、`USER_TAB_COLS` 14 列、`PK_ALIPAY_PAY_CALLBACK_LOG` 为 UNIQUE、两条 NONUNIQUE 索引都在）。该文件是这两个对象的**唯一权威 DDL**（本模块无 `*-schema.sql`）。
- 【执行状态·未完成】同文件末尾的 `UK_ARL_REFUND_ORDER_NO`（旧表 `ALIPAY_REFUND_LOG` 的退款单号唯一索引）**未建成**：存量 33 行、`REFUND_ORDER_NO` 去重 26（空值 0），差 7 行，全部集中在一个号 `REFUND20260715120000001`（8 行，`ORDER_NO` 同为 `GT20260715120000001`，`REFUND_STATUS` 全 `FAIL`，金额同为 10000，时间 2026-07-15 18:25 ~ 07-16 11:17）。该号是**写死的联调造数**（代码生成形态是 `R + 毫秒 + 8 位 UUID`），不是真实重复退款。**NEVER 为了建索引直接删行**；重跑前 MUST 先 `SELECT COUNT(*), COUNT(DISTINCT REFUND_ORDER_NO) FROM ALIPAY_REFUND_LOG;` 两者相等再执行。**索引缺位期间，退款幂等只靠 `PaymentRefundService` 的「同一 `ORDER_NO` 存在 `PROCESSING` 即拒绝」状态短路，挡不住真正的并发双提交。**
- 【决策】为什么另起两张表而不继续用 `ALIPAY_PAY_LOG` / `ALIPAY_REFUND_LOG`：① 旧两表 31 / 20 列**全是 `VARCHAR2`**，`PAY_AMOUNT` / `REFUND_AMOUNT` / `TRANS_TIME` 都是字符串，查询侧只能 `TO_NUMBER` / `TO_DATE(TRANS_TIME,...)`，走不到索引且存量格式不统一（既有 `2026-07-28 16:59:55` 也有毫秒时间戳 `1785229085685`）无法安全比较；② `ALIPAY_PAY_LOG` **没有 `CREATE_TIME` / `UPDATE_TIME`**，排序只能落到 UUID 主键 `PAY_SEQ`（等于随机序）；③ 旧表**没有任何唯一索引**，项目主幂等写法无处落地；④ 旧表没有 `REQUEST_COUNT` / `NEXT_REQUEST_TIME`，扫表补偿无列可依。
- 【契约】形态对齐 pay-sign-server 的 `pay-txn-schema.sql`：金额一律 `NUMBER` 且单位为**分**，时间一律 `TIMESTAMP`，`TXN_DATE` 为 `yyyyMMdd` 字符串并作月分区键，主键取序列，业务唯一键是 `(ORDER_NO, TXN_DATE)` 的 LOCAL 唯一索引。状态词表与 pay-sign **逐字一致**，**NEVER 换成 `ALIPAY_*` 旧表那套**：`PAY_STATUS` = `INIT/PROCESSING/SUCCESS/FAIL/RETRY/CLOSED`；`REFUND_STATUS` 主表 = `NONE/PROCESSING/PARTIAL/SUCCESS/FAIL`、明细表 = `INIT/PROCESSING/SUCCESS/FAIL/RETRY/CLOSED`。
- 【陷阱】**最早分区 NEVER 改成 `P202609`**：分区列表从 `P202606` 起共 8 个，与库内 `PAY_TXN_DETAIL` / `PAY_REFUND_DETAIL` 实测布局逐个对齐（2026-09-15 查 `USER_TAB_PARTITIONS`）。存量 `ALIPAY_PAY_LOG` 33 行的 `TRANS_TIME` 最大值是 2026-07-28，若后续裁决「迁移存量」，`TXN_DATE=202607` 的行会全部挤进九月分区，分区裁剪与按月归档都失准，**而建表时看不出任何异常**。
- 【陷阱·MCP】本脚本**现在可以经 `mcp_database_qd` 执行**：原先「MCP 跑不了分区表」的限制已于 2026-09-16 消除（database-mcp-server 侧修好并部署，镜像 `itp/database-server:1.0.7 -> 1.0.8`，Deployment `database` / ns `itp`）：① jsqlparser 5.3 -> 5.4，`PARTITION BY RANGE` 从此能解析（5.3 报 `Encountered unexpected token: "RANGE"`）；② 新增 `SqlTailNormalizer` 兜底钩子，仅在解析失败后触发、只摘除 `CREATE INDEX` 末尾的 `LOCAL` / `GLOBAL` 纯关键字尾巴（必须是严格前缀；带堆叠语句 / 注释 / 标点的尾巴一律拒绝），成功路径零变化。注意入参名不同：`executeDdl` 用 `sql`（单条），`validateDdl` 与 `executeDdlBatch` 用 `statements`（数组）。AGENTS.md §8 记的 `BEGIN EXECUTE IMMEDIATE` 绕法在这里**依然不行**（返 `DDL statement not allowed: Block`），也不需要了。**NEVER 为了让 MCP 能跑就去掉分区或 `LOCAL`** —— 参照表在同一个库里就是分区的。
- 【契约】`alipay-pay-txn-schema.sql` **只服务「新建这两张表」，它不是迁移脚本**：存量 `ALIPAY_PAY_LOG` / `ALIPAY_REFUND_LOG` 的处置（迁移 / 双读 / 冻结）**尚未裁决**，选定后 MUST 另出 `*-migration.sql`。后续月分区维护形态：`ALTER TABLE <表> SPLIT PARTITION P_MAX AT ('20270201') INTO (PARTITION P202701, PARTITION P_MAX);`（两张表各一条）。

#### 1.9 新表 mapper 与实体的逐条契约

- 【契约】两张新表的 mapper（`AlipayPayTxnDetailMapper` / `AlipayRefundTxnDetailMapper`）**目前没有业务调用方** —— 新表与链路接线分两轮做，本轮只落表与数据访问层。接线时调用顺序 MUST 是「① `insert` + `markRequesting` 落库并提交 → ② **无事务**调支付中心 → ③ `updateRequestResult` 回写」，即先留痕、再出网、后回写；**NEVER 把这三步包进同一个 `@Transactional`**。
- 【契约】`markRequesting`：请求次数 +1、状态压回 `PROCESSING`、记首次与最近请求时间，MUST 在调支付中心**之前**执行并提交。`PAY_STATUS` 无条件压成 `PROCESSING` 是有意的（重试路径要允许把 `FAIL` / `RETRY` 的单子重新置为在途）。
- 【契约】`updateRequestResult`（同步应答回写）不带状态机 —— 本语句是这一步的唯一写入者；`PAY_CENTER_ORDER_NO` 套 `NVL` 不覆盖已有值（后续退款要用它）。
- 【契约】`updatePayCallback`（回调回写）四条约束，**改这条 SQL 前 MUST 逐条读懂**（形态逐条照抄 pay-sign 的 `PayTxnDetailMapper.updatePayCallback`）：① SET 段除 `PAY_STATUS` / `DEBIT_REQUEST_RESULT` 外**全部套 `NVL`**（支付中心重推报文字段稀疏，不套 `NVL` 时一次重推就能把首推写好的 `PAY_TIME` / `CHANNEL_ORDER_NO` 覆盖成 NULL）；② `DEBIT_REQUEST_RESULT` MUST 与 `PAY_STATUS` **同步写**（漏写会出现「`PAY_STATUS` 已 `SUCCESS`、扣款结果还停在 `PROCESSING`」，APP 侧长期显示扣费未成功；pay-sign 侧 2026-08-26 已发生，生产 4 笔手工修数）；③ WHERE 的状态白名单 MUST **只比较列与常量**，**NEVER 把入参写进状态判断** —— pay-sign 侧曾写成 `PAY_STATUS != 'SUCCESS' OR (PAY_STATUS = 'SUCCESS' AND 入参 = 'SUCCESS')`，入参不是列，`SUCCESS` 回调进来时右分支退化成恒真、整个括号恒真，**等于没有状态机**；④ 白名单**含 `FAIL`、不含 `SUCCESS`**（含 `FAIL` 是因为支付中心可能先推 `FAIL` 后推 `SUCCESS`，钱已扣就 MUST 让 `SUCCESS` 落地；不含 `SUCCESS` 使重复成功回调稳定命中 0 行 —— 这就是回调的幂等出口，调用方 MUST 把「0 行 + 当前已是目标状态」判为重推并返成功。被未知状态卡住的行也稳定 0 行并触发人工出口，**这是有意留的**）。
- 【契约】`updatePayQueryResultIfNotSuccess`（新表版）与 `updatePayCallback` 只差 WHERE 一个条件、但语义完全不同：`payQuery` 是**查询**接口，**NEVER 允许它把已 `SUCCESS` 的终态订单改写成 `FAIL`**（同 §1.7 那条欠费判据）。
- 【契约】`updateRefundSummary`（新表版）**MUST 按 `ALIPAY_REFUND_TXN_DETAIL` 重算**，只传 `orderNo`、金额与状态全由 SQL 汇总，**NEVER 由调用方传增量**。触发路径不止上游重发：退款方法内要调支付中心，远端已受理而本地失败后补跑一次就会把同一笔算两遍。已退总额为 0 时保留原 `REFUND_STATUS`；可退金额口径 `NVL(NULLIF(TOTAL_AMOUNT, 0), AMOUNT)` 与 pay-sign 侧一致。与旧表版的差异：本表金额是 `NUMBER`，不再需要 `TO_NUMBER` 求和后 `TO_CHAR` 回写。
- 【契约】`sumSuccessRefundAmount`（已退成功金额合计）**只给 Java 侧做「可退金额 = 已付 - 已退」校验**，**NEVER 拿返回值回写主表** —— 那等于退回「读旧值再相加」的非幂等写法，并发两笔退款时两边都算偏小。回写主表只能走 `updateRefundSummary`。
- 【契约】退款明细两条 UPDATE 的 WHERE 都是 `REFUND_ORDER_NO + TXN_DATE`（既命中唯一索引 `UK_ARTD_REFUND_ORDER`、又能分区裁剪），**NEVER 只按 `REFUND_ORDER_NO`**。`RET_CODE` / `RET_MSG`（我方对外应答）与 `PAY_CENTER_CODE` / `PAY_CENTER_MSG`（支付中心原始应答）**MUST 分列存放**：合成一列后就分不清「我方判失败」与「对端说失败」，排查退款差异时无从下手。
- 【契约】分页 `offset` 是**已跳过行数**、`pageSize` 是**每页行数**，上界在 SQL 内自行相加。旧 `AlipayPayLogMapper.selectAlipayPayLogList` 的谓词是 `rn > offset AND rn <= limit`（把 limit 当行号上界），第 2 页传 `offset=10, limit=10` **恒返 0 行**（只有第 1 页碰巧对）。**NEVER 回退成让调用方传行号上界。** 排序按 `CREATE_TIME DESC + ID DESC` 兜同一时刻：旧表只能 `ORDER BY PAY_SEQ DESC`，而 `PAY_SEQ` 是 UUID、等于随机序，翻页时同一行会重复出现或被跳过。
- 【契约】列表时间过滤打在 `CREATE_TIME`（`TIMESTAMP` 列，能走索引），**NEVER 退回 `TO_DATE(TRANS_TIME, ...)`**（对字符串列做函数转换，走不到索引且因存量格式不统一随时抛 `ORA-01861`）；`endDate` 用「次日零点之前」而不是 `<= endDate`，否则漏掉当天有时分秒的行。
- 【契约】进出站关联查询取一行是**刻意的**：同一个 `ENTRY_ID` 理论上只对应一笔扣费，但库里没有该列的唯一约束，用 `FETCH FIRST 1 ROWS ONLY` 避免 `TooManyResultsException`；**NEVER 去掉 `ORDER BY ID DESC`** —— 不带排序时「取哪一行」由执行计划决定，同一份数据换了索引后会返回不同的行。
- 【契约】实体 `AlipayPayTxnDetail` 与旧 `AlipayPayLog` 有三处**刻意**不同：① 金额一律 `Integer`（分），**NEVER 改回 String**；② 时间分两类 —— `createTime` / `updateTime` / `*RequestTime` 是 `LocalDateTime`（库里 `TIMESTAMP`），而 **`payTime` 保持 String**，因为它是支付中心回调原文（`YYYYMMDDHH24MISS`）、落库即证据不做解析；旧实体的 `transTime` 不保留，按时间过滤 MUST 用 `createTime`；③ 主键是序列生成的 `id`、业务唯一键 `(orderNo, txnDate)` 对应 `UK_APTD_ORDER`，旧 UUID 主键 `paySeq` 不再保留（既排不出时序也拦不住重复落单）。另：`payCenterOrderNo`（支付中心侧支付订单号）与 `merchantOrderNo`（我方商户订单号 = `orderNo`）**不是一回事**，**退款报文的「原支付订单号」MUST 用前者**，缺它退款必失败；库内列 `DEBIT_REQUEST_RESULT` 值域 `PROCESSING/SUCCESS/FAIL` 与对外契约字段 `debitRequestResult` 的 `0`/`1` **同名不同义、NEVER 混用**；`RET_MSG` 类文案 **NEVER 当 `debitRequestResult` 直接对外返回**。
- 【契约】实体 `AlipayRefundTxnDetail` 是**退款账本的唯一真源**；与旧 `AlipayRefundLog` 的差异：金额改 `Integer`（分）、有 `(refundOrderNo, txnDate)` 唯一索引、补了 `requestCount` / `nextRequestTime` 支撑退款回查补偿、**不再有 `deleteFlag`**（退款明细不该被逻辑删除，且旧表每条查询都得记着带 `DELETE_FLAG='0'`，漏一次就把已删行算进汇总）。`txnDate` 是**本次退款的发起日期**，与原支付单各自独立、**NEVER 复用原单日期**（跨零点退款会分叉）。`refundStatus` 拿不到业务应答时 MUST 保持 `PROCESSING`、**NEVER 置 FAIL**。
- 【契约】新表索引的用途：`UK_APTD_ORDER` 是**幂等地基**（并发 `requestPay` 时两条请求可能都看不到已存在的行，第二条 INSERT 撞索引即被 catch 成「重推」——写法照 `card-pool-server` 的 `isIntegrityViolation` 沿 `getCause()` 链判定）；另有欠费盘点 `countUnsettledByCardId` 的支撑索引（旧表这条查询是全表扫）与进出站关联查询（`payLog/entryId`、`payLog/exitId`、`findTravelDetail`）的两列索引（旧表无索引、`FETCH FIRST 1` 靠全表扫）；退款侧 `(ORDER_NO, REFUND_STATUS)` 两列进索引供 `updateRefundSummary` 的相关子查询求和。

#### 1.10 配置、可观测性与敏感项

- 【陷阱】**本模块的 tracing 是靠 K8s Deployment 注入 env `management.tracing.enabled=true` 打开的，不在仓库 properties 里**（同族的 `alipay-account-server` / `fep-alipay-server` 都没开）。因此**排查「本模块开没开 tracing」MUST 查 Deployment env，NEVER 只看 `application.properties`** —— 文件里只有「三行成组」中的 `spring.autoconfigure.exclude` 那一行，另两行不在文件里。连带结论：本模块内所有幂等兜底 catch **MUST 沿 `getCause()` 链判定**（观测切面会换异常类型，ADR-D53）。
- 【陷阱】`spring.autoconfigure.exclude=...OtlpAutoConfiguration` 这行**NEVER 删**：Deployment 注入 tracing 开关后，配合旧版 `micro/web` 仍打开的 `management.otlp.tracing.endpoint`，`OtlpAutoConfiguration` 会建出 `SpanExporter`，导出线程每批抛 `UnknownHostException: collector-istio-traces-service.opentelemetry`（该 collector 在集群里不存在）刷 ERROR。Boot 3.2.6 没有 `management.tracing.export.enabled` 开关，endpoint 置空也不行（只判键是否存在），只能排掉整个自动配置；排掉后 `Tracer` 与 MDC 的 `traceId`/`spanId` 照旧，仅不再创建 `SpanExporter`。与 `pay-sign-server` 同一处理方式。
- 【安全·只记键名】`other.sql.password` 在**三个模块中的两个**（`alipay-pay-sign-server/src/main/resources/application.properties`、`alipay-account-server/src/main/resources/application.properties`，均为文件内第 10 行附近、紧跟 `other.sql.username`）是**明文真值**；`alipay-account-server` 的 `itp.signKey` 同样是明文真值。**本文档只记键名与位置，NEVER 回显任何值**。建议按 AGENTS.md §5.2「敏感配置」改成 `${DB_PASSWORD:}` / `${ITP_SIGN_KEY:}`（**空默认值**）并由 K8s Secret 注入；改前 MUST 确认 Deployment 已注入对应 env，否则服务起不来。`pay.center.merchant-private-key` / `pay.center.paycenter-public-key` 目前是空值，形态已符合要求。
- 【契约】`service.*.url` 默认值一律用集群内网 Service 名（`kubectl get svc -n itp` 实测）：**NEVER 写 `127.0.0.1`**（在 K8s 里等于打到自己，表现为响应退化成 UUID `retCode`），**NEVER 写 `http://<模块名>:8080` 裸名**（集群里 Service 名都带随机后缀，例如支付宝开户是 `alipay-account-server-2n6kc-svc`，裸名解析不到）。**本域三个模块的 `service.account` 指的是「支付宝开户服务」`alipay-account-server`，不是 `account-server`** —— 这是本域最容易读错的一个键。`alipay-account-server` 的 `service.ticket.url` 原值 `http://ticket-server:9103` 名和端口都不对（Service 名带随机后缀、端口是 9100，9103 才是容器 `server.port`），已回填。
- 【契约】**三个模块的 `server.port` 都是 8080，与 web-admin 冲突**（AGENTS.md §2.2.1 端口冲突清单里的那组）。同机部署 MUST 显式区分端口；集群里各自有独立 Service，**判断某模块对外端口 MUST 查 `kubectl get svc -n itp`，NEVER 拿 `server.port` 推断**。
- 【契约】`fep-alipay-server` 的四条出向支付宝地址（`alipay.notify.push-trans-data-url` / `close-result-url` / `card-data-url` / `blacklist-url`）默认值全是 `https://openapi.alipay.com/gateway.do`，`alipay.notify.pay-callback-url` 是 `http://fep-alipay-server:8080/...`（裸名，与上面那条 NEVER 冲突，属**待清理项**）；`alipay-pay-sign-server` 的 `pay.center.callback-url` 同形态。
- 【契约】`industry.issue-channel-code=07` 在 `alipay-pay-sign-server` 与 `fep-alipay-server` **各有一份**，是支付宝出行的渠道位；改一处 MUST 看齐另一处（分流点见 `fep-dev-server` 的 `GateTransactionHandler`）。

#### 1.11 单测钉住的两条不变量

- `PayNotifyDebitStatusSyncTest`：钉住扣费回调的收敛口 —— 结果只写 `GATE_TXN_PAY.DEBIT_STATUS`，**NEVER 回退成读写 `ALIPAY_PAY_LOG`**（该表已停写，双写会让两边状态分叉）。
- `TravelDetailDebitResultTest`：钉住 IF8A「查询乘车记录详情」的 `debitRequestResult` 值域只能 `"0"` / `"1"`。**上线前该字段填的就是 `RESULT_MSG`**，支付宝按 0/1 解析时成功单也显示扣费未成功。

### 二、alipay-account-server

grep 锚点：`AlipayAccountServiceImpl`、`requestApplication`、`updatePhone`、`transactionTemplate`、`reserveFromPool`、`releaseReservation`。

- 【契约】**`requestApplication` 故意不带 `@Transactional`**：卡池预占、ticket-server 注册乘车状态、卡池确认 / 释放全是 RPC。改造前这里是 `@Transactional` 包住 3 次 RPC，与 2026-08-26 生产事故（事务内调远端 → 行锁持有时长等于对端响应时长 → 连接被 Druid `remove-abandoned-timeout` 强杀 → 整个事务连同证据一起回滚）**同型**。**NEVER 给它重新加上 `@Transactional`。**
- 【契约】编排顺序按 AGENTS.md §5.2「先调远端、后改本地」：**预占卡号 → 注册乘车状态 → 短事务落 `ALIPAY_USER_INFO` + `ALIPAY_REG_LOG` → 确认预占**。落库失败时乘车状态留在远端等重推（卡池按 `businessId` 幂等发号，重推拿到同一卡号、不会产生第二条乘车状态），预占显式 `release`；release 本身失败由**卡池的预占超时回收**兜底（`sys_job` 107「卡池维护」）。
- 【契约】落库那两条 INSERT 用 `TransactionTemplate` 显式开短事务：同类内自调用绕不过 Spring 代理，而 §5.2 禁止在 `@Transactional` 方法内发 RPC，因此**只能**把落库收进 `TransactionTemplate`。
- 【契约】预占失败按类型分流：`POOL_EMPTY` 属正常业务结果（WARN）；`REJECTED` 说明票种不走卡池或归属冲突，属程序 / 配置缺陷、**重试无用**（ERROR）；`CALL_FAILED` 是 card-pool-server 不可达或响应无法解析、**可重试**（ERROR）。释放失败只记 WARN、不改变对外结论也不抛异常（抛异常会掩盖真正的失败原因）。
- 【契约】**换号 `updatePhone` 故意不向支付域同步「显示账号」**（用户 2026-09-11 裁定：不需要）。ITP 侧 `PhoneChangeServiceImpl.updatePhone` 会调 pay-sign 的 `updatePaySignDisplayAccount` 并带 `SIGN_SYNC_*` 补偿，**NEVER 照抄到这里** —— 支付宝走自有代扣、用户在支付域没有签约行，推过去只会命中「`APP_PAY_SIGN_INFO` UPDATE 影响 0 行 ⇒ 返回 FAIL」，白造一批永远重推不成功的 `FAILED` 记录和异常工单。同理 **`ALIPAY_PHONE_CHANGE_LOG` NEVER 加 `SIGN_SYNC_*` 列**。
- 【契约】`ALIPAY_PHONE_CHANGE_LOG` 由本模块**独占写入**，DDL 在 `alipay-account-server/src/main/resources/sql/alipay-account-server-schema.sql`。**NEVER 再写 `USER_PHONE_CHANGE_LOG`** —— 那张表 owner 是 account-server，共用其序列在拆库后必然主键冲突（用户 2026-09-11 裁定，`docs/domain/decisions.md` ADR-D8）。主键取 `SEQ_ALIPAY_PHONE_CHANGE_LOG.NEXTVAL`，**NEVER 加 `useGeneratedKeys`**（成因与后果见 §1.7 末条）。
- 【契约】`selectByCardId`（按逻辑卡号查支付宝用户）是给 **ticket-server 过闸链路**用的（account 域按 `cardId` 查不到支付宝用户），**只读、不改状态**，用于取码体的签约渠道位与真实卡种；调用点 `GateTicketHandler#applyActualCardType`。
- 【缺口】`alipay-account-server-schema.sql` **没有头注释**（只有 10 条 `COMMENT ON`），文件内也没有执行状态记录 —— 与 `alipay-pay-sign-server` 两个 SQL 的做法不一致。**该文件的执行状态在仓库内无记载，用前 MUST 现查库。**

### 三、fep-alipay-server

grep 锚点：`AlipayQueryServiceImpl`、`AlipayTripServiceImpl`、`FepAlipayTripNotifyController`、`TravelDetailFromGateTxnPayTest`。

- 【契约】**薄网关，无 mapper / 无表 / 无事务 / 无定时任务**，入参 `@ModelAttribute ItpCommonFormRequest`（全项目唯一实现，在 `model` 模块），全部经 RPC 下发。**NEVER 新增 mapper / 事务**。
- 【陷阱·日志脱敏】收口 `ItpCommonFormRequest` 后，`toString` 已对 `sign` 恒定脱敏、但 `bizData` 会完整进日志；本模块 8 处 `log.info("...{}", request)` 因此由「只打对象 hash」变为「打全部字段」，`bizData` 原文完整进日志。**NEVER 把 `toString` 改成打印 `sign` 真值**。
- 【契约】`AlipayQueryServiceImpl` 查询乘车记录详情改读 `GATE_TXN_PAY`（§1.4 / §1.11 同一判据）。两处实测注意：① 出站查询返回的 response 中 `entryStationName` / `entryDate` 实际对应本次出站站点和时间，其 `exitStationName` / `exitDate` 取自 `QRCODE_TXN_DETAIL` 的 `LAST_HANDLE_*`（上一次处理 = 进站），直接用会与 `entryDetail` 的进站信息重合（表现为进出站站点与时间完全一样）。② `payTradeOrderNo`（支付宝渠道流水号）与 `invoice`：前者按用户 2026-09-14 裁决「暂时返 null，先让链路通，渠道流水后补」—— 核账走支付中心 `payQuery`；后者在 `ALIPAY_PAY_LOG` 33 行里实测全为 null、从未被写过，返 null 零损失。**NEVER 悄悄改回读 `ALIPAY_PAY_LOG`**（该表已停写，只会静默命中 0 行）。
- 【契约】`TravelDetailFromGateTxnPayTest`：钉住三件事 —— ① `entryId` / `exitId` 只能从 `industryDetail` 整块 JSON 里取；② `payTradeOrderNo` 与 `invoice` 按裁决恒为 null；③ 行业明细缺失或非法时**不抛异常、也不去打 ticket-server**。进出站两次查询是并行发出的，到达顺序不确定。

### 矛盾与待裁决

| # | 位置 A | 位置 B | 矛盾内容 | 状态 |
|---|---|---|---|---|
| 1 | `TerminationNotifier` 类注释（grep `TerminationNotifier`） | `AlipayContractServiceImpl.executeTermination` 的步骤注释 | 对同一件事（解约执行）的顺序要求**相反**：`TerminationNotifier` 要求「先通知支付中心、后改本地」，`executeTermination` 步骤 3 在步骤 4 之前且失败只 warn 不回滚。详见 §1.1。 | **已裁决 P0**：MUST 改成先远端后本地，委托给 `TerminationNotifier`；本次只写文档、NEVER 改代码 |
| 2 | `AlipayPayLogMapper.xml` `updateRefundSummary` 上方注释（已迁移到 §1.7） | `PaymentRefundService.java` 方法 Javadoc（§1.3） | XML 那句旧注释写「`PaymentRefundService.requestRefund` 带 `@Transactional` 且事务内调支付中心」，而方法 Javadoc 写「NEVER 给本方法加 `@Transactional`（2026-09-14 移除）」。**XML 那句已过期。** | 已闭合：迁移时 XML 注释已删、方法 Javadoc 护栏保留 |
| 3 | `PaymentRefundService.java:144` 旧注释（已删） | `application.properties:12~20` + AGENTS.md §2.2.1 | 旧注释写「本模块虽然当前没开 tracing」，而 properties 的排除注释与 AGENTS.md tracing 名单都写本模块**已开**（靠 Deployment env 注入）。**旧注释已过期。** | 已闭合：旧注释已删 |
| 4 | AGENTS.md §5.1「`fep-alipay-server` 有 8 处 `log.info("...{}", request)`」 | 实测 | 抽取时直接打 `ItpCommonFormRequest` 的是 **9 处**（含 `FepAlipayTripNotifyController` 的 `payNotify` 和 `closeResultForAlipay`）。 | **待校准**：AGENTS.md 那行数字是阶段一时点，可能有版本差异；建议用 `grep -rn 'log.info.*request' fep-alipay-server/src/main/java` 现查 |
| 5 | `AlipayContractServiceImpl.executeTermination:171~172`（`paymentNotifyAdapter.notifyCloseResult(signInfo.getAgreementCode(), true)`） | `TerminationNotifier:136~137`（`notifyAgreementNo = channelAgreementCode`） | 同模块两处出向通知用的**协议号不一致**：前者用我方 `agreementCode`、后者用渠道 `channelAgreementCode`。支付中心只认渠道号，前者恒定 `code=600`。 | 同 §1.1 判据，属 P0 的一部分 |

### 墓碑清单（阶段二新增，与阶段一表合并使用）

阶段一已列 35 条（编号 1~35），以下是阶段二扫全量后**新发现**的补充条目：

| # | 位置（grep 锚点） | 禁止的事 | 能否断言化 |
|---|---|---|---|
| 36 | `AlipayAccountServiceImpl` 内 `releaseReservation` | 释放失败 NEVER 抛异常（会掩盖真正的失败原因；兜底靠卡池超时回收） | 能：mock `cardPoolClient.release` 抛异常，断言方法不抛且返值不变 |
| 37 | `AlipayAccountServiceImpl` 内 `reserveFromPool` | 预占失败 REJECTED/CALL_FAILED/POOL_EMPTY 各有不同语义，NEVER 合并成统一失败 | 能：三个 outcome 各喂一次，断言返回的 retCode 各不相同 |
| 38 | `fep-alipay-server/.../impl/AlipayQueryServiceImpl` 出站查询 response | entryStationName/entryDate 是出站信息不是进站、exitStationName/exitDate 是上一次进站；NEVER 直接对调 | 部分：只能在端到端或集成测试里验证字段映射 |

### 覆盖率自评

| 维度 | 阶段一（169 行） | 本次阶段二 | 合计 | 三模块注释总量 | 覆盖率 |
|---|---|---|---|---|---|
| 抽取的**知识型条目**（契约/决策/陷阱/墓碑） | 约 35 条（附一~附八 + 墓碑 35 行） | 约 95 条（§1.1~§1.11 + §二 + §三 + 矛盾 5 + 墓碑 3） | 约 130 条 | 1872 行注释（含实体字段 Javadoc ~450 行 + 标准方法 Javadoc ~300 行 + 55 条 SQL `COMMENT ON` ~100 行） | **知识型注释覆盖率约 95%**（未抽的 ~5% 是实体字段一行式 Javadoc、标准 `@param`/`@return`、`COMMENT ON` 列注释 —— 这些是标准 Javadoc，不是知识型注释，不在迁移范围内） |
| P0 缺陷 | 1（`executeTermination` 顺序问题，附三提及但未展开） | 2 条完整写入（§1.1 + §1.2） | 2 | - | 100% |
| 矛盾 | 4 条（附八） | +1 条（协议号不一致，§1.1 的子项）→ 总 5 | 5 | - | - |
| 墓碑 | 35 | +3 | 38 | - | - |









