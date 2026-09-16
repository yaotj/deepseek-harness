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

**alipay-account-server**
- `FepAlipayTripRequestApplicationController`（`/channel`）：`requestApplication`、`queryUserInfo`、`updatePaymentChannel`、`updatePhone`
- `AlipayUserPageController`（`/page/user/alipay`）：`/search`

> 本域代码中**没有任何 IF 编号标注**。需要对应 IF 编号时 **MUST** 回查 `docs/接口规范文档/`，**NEVER** 自行编造编号。

## 核心类
- 签约登记：`alipay-pay-sign-server/.../service/impl/AlipayContractServiceImpl.java`、`TerminationRegistrationService`、`TerminationNotifier`
- 销卡批处理（2026-09-07 新增）：`AlipayTerminationInternalServiceImpl`（扫 PENDING → 逐条 `TerminationNotifier.execute`）+ web-admin 侧 `AlipayTerminationQuartzTask`
- 支付：`AlipayTripPaymentServiceImpl` + 协作类 `PaymentRequestService`、`PaymentQueryService`、`PaymentRefundService`、`PaymentNotifyAdapter`、`RefundAmountCalculator`、`PayLogBuilder`、`SignLogRecorder`、`BizDataBuilder`、`IndustryDetailEnricher`
- 查询：`AlipayPayLogQueryServiceImpl`

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

## 状态取值（String 常量，无枚举）
- `AlipayContractServiceImpl`：`SIGNED`、`PENDING`，渠道常量 `CHANNEL_ALIPAY="ALIPAY"`
- `TerminationRegistrationService`：`PENDING`、`COMPLETED`
- `TerminationNotifier`：`TERMINATED`（写 `ALIPAY_SIGN_INFO`）、`COMPLETED`、`FAIL`（写 `ALIPAY_TERMINATION_REQUEST`）；返回值 `Outcome{TERMINATED, FAILED, RETRY_LATER}`

⚠️ 与 pay-sign-server 的状态词表**不完全一致**（如 pay-sign 用 `SUCCESS`/`UNSIGNED`，此处用 `COMPLETED`/`TERMINATED`）。跨模块对接 **MUST** 显式映射。

## 数据表
`ALIPAY_SIGN_INFO`、`ALIPAY_SIGN_LOG`、`ALIPAY_PAY_LOG`、`ALIPAY_REFUND_LOG`、`ALIPAY_TERMINATION_REQUEST`、`ALIPAY_PAY_CALLBACK_LOG`
- 逻辑删除标记 `DELETE_FLAG='0'`，查询 **MUST** 带此条件（见 `AlipaySignInfoMapper.xml`、`AlipayRefundLogMapper.xml`）
- ⚠️ 本模块**无 `*-schema.sql` 建表脚本**，前五张表的 DDL 在仓库内没有权威副本，唯一索引无法从仓库确认。新增幂等约束前 **MUST** 让用户确认线上 DDL。
- **`ALIPAY_PAY_CALLBACK_LOG` 是例外**：它的权威 DDL 就是 `alipay-pay-sign-server/src/main/resources/sql/alipay-pay-sign-callback-log-migration.sql`，2026-09-14 已在 `AFCITPDB` 执行并回查（`USER_TABLES` 命中、14 列、`PK_ALIPAY_PAY_CALLBACK_LOG` + 两条 NONUNIQUE 索引齐全）。

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
