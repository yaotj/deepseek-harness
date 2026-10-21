# 公共构件注释知识迁移（2026-09-16）

> 本文档是从 `model` / `rpc` / `resource/micro` 源码注释中**批量迁出**的知识归档。
> 2026-09-16 阶段二把 `model/**`（注释 4364 行）、`rpc/**`（1225 行）、`resource/micro/**`（230 行）里的
> 多行叙述型 / MUST-NEVER / 事故史 / 墓碑注释整批删除，代码只留标准 Javadoc；
> 代码里只保留两处一行式护栏（见 §附二.14）。
>
> **本文档是这些知识的唯一副本，NEVER 期望回到源码注释里再找。**
> 引用格式 `路径:行号` 用的是**删除前**的行号，删除后会前移，故每条都附可 grep 的符号名或原文短语，行号只作粗定位。
>
> 本文档是**历史归档**，不是主动维护的查找清单。当前公共构件的约束与查找入口见
> [`common-components.md`](common-components.md)。

---

## 附二.1 model —— 报文骨架 `ItpCommonRequest` / `ItpCommonFormRequest`

- **全项目只有这两个报文骨架类**：`model/.../app/ItpCommonRequest.java`（泛型 `<T>` 承载 `bizData`）与
  `model/.../app/ItpCommonFormRequest.java`（`extends ItpCommonRequest<String>`，form-data 入向专用，
  原文「全项目唯一实现」）。7 份业务模块副本已于 2026-09-11 删除收口，**NEVER 再在业务模块新建同形副本**。
- **只承载报文骨架、不承载签名语义**（`ItpCommonRequest.java`）。四条链路加签验签各不相同：`account-server` 摘要式
  SHA1/MD5 + `signKey`、`acc-secure-server` 的 `AccSecureSignUtils`、`pay-sign-server` 的 fastjson2 摘要、
  `collect-pay-server` 的 `signType=00` 不签、渠道方向 RSA。**NEVER 因为共用本类就顺手统一签名逻辑**（安全红线）。
- **`toString` 对 `sign` 恒定脱敏**（`ItpCommonRequest.java`，原文「sign 恒定脱敏」）：**NEVER 改成打印真实签名值**；
  但 **`bizData` 会完整进日志** —— `fep-alipay-server` 的 8 处 `log.info("...{}", request)` 因此从「只打对象 hash」
  变成「打全部字段」。要收紧日志脱敏面 MUST 改 `toString` 这一处，NEVER 逐个改调用点。

## 附二.2 model —— `CommonResult` 与响应封装

- `common/response/` 只有四个类：`CommonResult`（`retCode` / `retMsg`）、`ResultVO`、`ResultMapper`、
  `AlipayCommonResponse`。**`CommonResult` 自己的注释里没有 `retCode` 取值集合** —— 全仓唯一成文处是
  `rpc/.../outcome/RpcOutcome.java` 的 `SUCCESS_CODE = "0000"`；判断某个码的含义 MUST 看对应模块的
  `*ErrorCodeEnum`（`model` 侧是 `common/constant/FepAppErrorCodeEnum`），**NEVER 假定全项目共用一套码表**。
- 各 `*RespDTO` / `*Result` 的通用约定（逐条落点见 §附二.5）：**调用方 MUST 先判 `retCode` / `resultCode` 再取业务字段**；
  查询未真正执行时计数类字段恒为 0、布尔字段被置成保守值（如 `hasUnsettled=true`），
  **NEVER 把「没查到」读成「无欠费 / 已结清 / 无待处理」**。

## 附二.3 model —— 卡类型 `CardTypeMapping` 与发卡 / 渠道枚举

| 位置（删除前） | 迁出的知识 |
|---|---|
| `model/.../app/CardTypeMapping.java` | **日票聚合码 `05` MUST 展开成多值**（`toIssueCardTypes`）：APP 只有一个「电子日票」入口、不分一日/三日/七日/月票，而发卡卡类型按天数细分 `12~15` → `0445~0448`。**NEVER 走单值映射** |
| `CardTypeMapping.java` | **NFC 的 `03` / `04` 在查询侧同义、MUST 展开成 `0442+0443`**。实测：页签发 `03`（`/ci/app/requestTransList` 抓包 `cardType=03 + cardId=0426091000000013`），而同一张卡开户时 APP 送 `04`（`USER_ITP_REG_INFO.ITP_CARD_TYPE=04` / `CARD_TYPE=0443`），闸机上报与订单落库也是 `0443` —— 单值映射成 `0442` 后**恒命中 0 行且 `retCode=0000` 不报错**（2026-09-10 定位，订单 `GT20260910164625246000013` 查不到）。**NEVER 退回单值映射** |
| `CardTypeMapping.java` | `toIssueCardType` / `toIssueCardTypes` 对**未识别值原样返回、不抛异常**，脏值会静默流到下游（SQL 命中 0 行、卡池预占匹配不到 `CARD_TYPE` 分区）。因此**接口入口 MUST 先 `isSupportedAppCardType` 校验**，NEVER 依赖映射方法拦非法票种 |
| `CardTypeMapping.java` | 同上反向表述：`isSupportedAppCardType` 是唯一的拦截点 |
| `model/.../cardpool/CardPoolTicketType.java` | 规范化票种码 = 去空白 + 转大写 + 必须落在 `ALL_TYPES` 内。**已发生事故**：2026-09-10 日票下单送 ACC 口径 `cardType=45`，映射表无此键被原样返回、一路穿到 card-pool 预占，与任何分区都不匹配，卡池回 `code=null/msg=null`、激活报「发卡服务暂不可用」，订单 `0E202609101131510006` 支付后 1.5 小时才在激活环节暴露 |
| `model/.../enums/IssueChannelCodeEnum.java` | `thirdUserIdLength` 于 2026-09-14（ADR-D70）从两个模块上移到此：此前长度散落在 `fep-dev-server` 的 `DeviceUserIdCodec.normalize` 与 `ticket-server` 的 `SupplementCodec.normalizeDeviceThirdUserId`，而「谁算支付宝」（`isAlipay`）一直在本类。**本方法只管长度，NEVER 把补位 / 进制转换搬进来**（fep-dev 侧收的是十六进制、要先 `new BigInteger(x,16)`；ticket 侧已是十进制）。未知 / null 渠道码返回 **8**，与上移前 `isAlipay ? 10 : 8` 逐位一致，**NEVER 改成先 `fromCode` 再取实例属性**（会给未知渠道引入 null 路径） |
| `model/.../enums/CardIssueOrgEnum.java` | 发卡机构编码枚举 = APP 开户 IF8A-01 上送的 `cardIssueCode` 原值，属对外契约取值 |

## 附二.4 model —— 状态机枚举与 `domain.OutboxScan`

四台状态机枚举（`model/domain/`）共有的三条口径，**改任一处 MUST 看齐其余**：
① **只做解析 / 快速失败 / 文档化，NEVER 当作并发保证** —— 并发保证是 mapper XML 里的 CAS；
② **NEVER 改动常量字面量** —— 库内存量数据按这些字符串存储，且 `pay-sign-server` 侧仍有 `private static final String`
常量与裸字面量在比较同一批值，改名等于制造两套口径；③ **新增取值 MUST 同步 `ALLOWED` 与 mapper XML 的 CAS**。

| 位置（删除前） | 迁出的知识 |
|---|---|
| `domain/SignStatus.java` | **NEVER 允许 `UNSIGNED -> SIGNED`**：已解约通道被迟到的签约回调覆盖，会让 APP 显示通道有效而渠道侧协议已注销；`NOT_SIGNED` 是复位重签用的 |
| `domain/SignStatus.java` | `parseOrNull` **NEVER 兜底成具体状态** —— 猜错方向会把脏数据推进状态机 |
| `domain/TerminationStatus.java` | **NEVER 允许 `FAILED -> SUCCESS`**（用户 2026-09-12 裁决）：该场景 MUST 走人工（`expired > 0` 的 `log.error` 是告警出口），运维到支付中心核对协议真实状态后由用户从 APP 重新申请（走 `FAILED -> PENDING` 复活） |
| `domain/TerminationStatus.java` | **NEVER 允许 `SUCCESS` 迁出**：解约成功后签约记录已 DELETE、账户域支付通道已清理，回退状态只会让补偿队列重新捞取、对 APP 重复投递 |
| `domain/TerminationStatus.java` | **NEVER 把 `PENDING` / `SCANNING` 纳入 `isNotifiable()`**：那两个态没有结果可通知，滞留后会被补偿当成「成功通知丢了」，给 APP 发一条 `status=SUCCESS` 且 `dismissalTime` 为空的**假解约成功通知**（2026-08-26 已修复过一次） |
| `domain/SyncStatus.java` | `SUCCESS` 是唯一终态、**NEVER 允许迁出**（补偿扫表按 `SIGN_SYNC_STATUS IN ('PENDING','FAILED')` 捞取，回退即对已送达记录重复推送）；**NEVER 把 NULL 兜底成 `PENDING`** —— 那等于对改造前的存量记录发起一轮真实 RPC 重推 |
| `model/.../ticket/enums/QRCodeStatusEnum.java` | 状态迁移白名单**只做解析与文档化，NEVER 当作并发保证**（并发保证是 `QRCodeStatusMapper.upsertWithCas` 的 `TXN_SEQ` CAS），也 **NEVER 用它在 CAS 之前拦请求** —— 调用方手里的「当前状态」来自更早一次 select、随时可能过期 |
| `QRCodeStatusEnum.java` | `80` / `81` 的入边已按 `ExcessFareHandler.resolveAllowedTypes` 的前置状态集合补齐，**改那个方法的允许集合时 MUST 同步看齐这里**，否则每笔补站都打一条「迁移不在白名单内」WARN；`10`（进站码更新）**没有任何写入路径**（2026-09-09 实测生产库 0 行），其出边是理论值、当前不可达，**NEVER 把新功能的前置条件挂在 `10` 上** |
| `QRCodeStatusEnum.java` | `FF`（进站失败）只在 `03/05/06/80/FF` 之后合法，**NEVER 加进 `ALWAYS_ALLOWED_TARGETS`**；上游可能上送未登记取值，**解析失败 MUST 由调用方决定处置、NEVER 在枚举里抛** |
| `model/.../domain/OutboxScan.java` | 只固化 Java 侧三条不变量（SQL 侧的坑见 `docs/domain/outbox.md`）：**单条失败 NEVER 中断整批**（一条恒失败的记录会永久挡住它后面所有行）；**NEVER 建公共 outbox 表** —— 待投递的事实 MUST 留在产生它的那张业务表上（如 `USER_PHONE_CHANGE_LOG.SIGN_SYNC_*`）；**NEVER 往本类塞 Spring / MyBatis 依赖**（需要注入就说明抽错了层）。刻意不管的四件事 MUST 留在调用方：扫表 SQL 与白名单、重试上限判据、达上限后开工单还是转人工、状态列取值集合 |
| `OutboxScan.java` | `scanned` 恒等于 `success + failed`；各域补偿端点自己的返回记录（如 `SignSyncCompensateResult`）是**对外契约**，**NEVER 用本记录替换它**，转换一下即可。`deliver` MUST 自己把成败落库（本类不碰数据库），`onUnexpected` **MUST 只记日志、NEVER 再抛** —— 从那里抛出去会中断整批 |

## 附二.5 model —— 各业务 DTO 逐条

| 位置（删除前） | 迁出的知识 |
|---|---|
| `app/AppIndustryDataNotifyReqDTO.java` | 一个 `thirdUserId` 下可同时存在主码与同行码，APP 侧 MUST 按 `cardId` 落地；票种标识只区分语义，**NEVER 当成卡的唯一键** |
| `app/BlacklistReleaseCandidateDTO.java` | 只用于**只读盘点报表，NEVER 被任何删除逻辑消费**。`BLACKLIST` 没有拉黑类型字段、`REASON` 是四个来源混写的自由文本（支付中心应答原文 / 代码拼接 / 外部接口传入 / 运营手工输入），生产实测 35 条 ADD 里 22 条是「用户挂失补卡」——与欠费无关，误删等于让挂失旧卡恢复过闸。`SETTLED` 只代表钱结清、**NEVER 等同于「可以解除」**，判定 MUST 由人看 `reason` |
| `app/BlacklistReleaseInspectRespDTO.java` | 字段与 blacklist-server 侧同名类一一对应，**改动 MUST 两侧同步**；本接口 **NEVER 删除任何黑名单记录** |
| `app/CardUnsettledQueryReqDTO.java` | 故意不带模块前缀命名，新增第三个欠费源时**直接复用，NEVER 每模块复制一份同结构 DTO** |
| `app/QueryPayChannelByContractReqDTO.java` | `APP_PAY_SIGN_INFO` 的 `CARD_ID` / `CARD_TYPE` **全库为 NULL**，真实来源是 account 侧 `APP_USER_PAY_CHANNEL`（`REQ_CONTRACT_NO` 即签约流水号）。IF8A-75 走 `createTerminationRequest` 补建申请时从签约记录回填**必然拿到 NULL 并抛 `ORA-01400`**（2026-09-08 实测） |
| `app/QueryPayChannelByContractResult.java` | 未找到返 `8004`，**MUST 判 retCode 而不是只判字段空** |
| `app/QueryTransListReqDTO.java` | 日票聚合码 `05` 展开成 `0445~0448`，因此**入参 MUST 用列表而非单值** |
| `app/RequestPayReqDTO.java` | `TXN_DATE` 是 `PAY_TXN_DETAIL` 的**月分区键**，MUST 与行程侧同一 `ORDER_NO` 的 `TXN_DATE` 一致；支付域自取 `LocalDateTime.now()` 会跨零点分叉（实测出站到落库最大滞后 94 分钟，22:26 后出站即可能跨日），两表按 `(ORDER_NO, TXN_DATE)` 关联随即落空 |
| `app/RequestTransStatisticsReqDTO.java` | IF8A-41 实测 APP 只上送 6 字段（2026-09-10 抓 fep-app 日志）：`thirdUserId/cardType/transType/cardId/startDate/endDate`；**`cardType` 与 `transType` 恒同值**（三个页签 05/05、02/02、03/03），一个语义，**NEVER 把 `transType` 当另一个查询维度**。**NEVER 复用 IF8A-05 的 `TransQueryHandler.normalizeDate`**（它按 `yyyy-MM-dd` 解析，喂 `yyyyMMdd` 抛 `IllegalArgumentException`）。未开通某票种时该页签**不带 `cardId`**，因此 `cardType` 是唯一票种过滤依据，缺它会退化成「按用户查全部票种」（实测 4 票种时把 47 条全算进来） |
| `app/RequestUserAccInfoResult.java` | IF8A-35 分档口径：`CLOSED` 与脏数据 `NULL` 两档都不计入，故 `unpaidCount + failureCount` **不等于「全部非 SUCCESS 订单数」**，与解约校验 `countFailedOrder` / 黑名单 `countUnsettledOrderByCardId` 的「非 SUCCESS 即未结清」**不是同一个口径**，比对两边数字前 MUST 先看清定义；查询未执行时两数为 0，**NEVER 当成「无欠费」**；**放行类判定（过闸、解约）各有自己的校验，NEVER 改用本接口结果** |
| `app/SyncPayAccountIdReqDTO.java` | 2026-09-11（ADR-D32）新增，补 ADR-D30 的覆盖率缺口（`PAY_ACCOUNT_ID` 原先只有 IF8A-77 会写、签约后恒空、运营页显示 `-`）。本 DTO **不经过 `parseBizData`**（调用方是 pay-sign-server），按 `docs/domain/README.md` 判据属**对内接口**，加字段不受「对外契约 NEVER 加字段」约束。**NEVER 用它去改 `USER_ITP_REG_INFO.THIRD_PAY_ID`** —— 那列是 IF8A-77「更换默认支付方式」这个业务动作的产物，越过动作去写等于替用户决定默认支付方式；三个同名概念 NEVER 混用 |
| `app/TransRecordDTO.java` | 「原价」字段 **NEVER 当成 `GATE_TXN_PAY.TOTAL_AMOUNT`**（那是实付 = 车费 + 超时费，对应 `payAmount`）；赋值处 MUST 与 `originalFare` 同源、两者不允许出现不同值 |
| `app/TripDataDTO.java` | IF8A-41 四个金额口径（2026-09-10 校准，统计源表已从 `QRCODE_TXN_DETAIL` 换到 `GATE_TXN_PAY`）：`totalPrice=SUM(NVL(ORIGINAL_FARE,TRX_AMOUNT))`、`totalDebit=SUM(TOTAL_AMOUNT)`、`totalDiscount=SUM(MAX(原价-TRX_AMOUNT,0))`、`totalOvertime=SUM(OVERTIME_AMOUNT)`。**NEVER 把 `OVERTIME_AMOUNT` 映射到 `totalDiscount`**（超时加收与优惠语义相反）—— 旧实现 `QRCodeTxnDetailMapper.selectTransStatistics` 三个标签全部错位（把 12 元超时费显示成「已优惠 12 元」）。`totalDiscount` 的减数 **MUST 是 `TRX_AMOUNT` 而非 `TOTAL_AMOUNT`**，后者含超时费会算出负数 |
| `app/UnbindAgreementReqDTO.java` / `UnbindAgreementResult.java` | 钱包（`paymentVendor=0B`）**不适用本接口**（开户即绑通道、无签约流水），解绑 MUST 走 IF8A-25 `requestRemovePayChannel`；**APP NEVER 把本接口的 `0000` 当成解绑成功直接刷新为「未绑定」** |
| `app/UpdatePhoneReqDTO.java` | 规范字段是 `newPhone`，**NEVER 改回早期自造的 `newMsisdn`**（2026-09-09 与规范核对后纠正）；字段名本身是对外契约，**改名 MUST 连带重建 `fep-app-server` 镜像并与 APP 同步切换**，否则 Fastjson2 静默丢弃该键、下游只收到 null |
| `app/UpdatePhoneResult.java` | 原先多出的 `thirdUserId` 从未赋值、恒 null，属契约冗余，2026-09-09 已删除 |
| `app/UserCancelReqDTO.java` / `UserCancelResult.java` | 销户只把 `USER_ITP_REG_INFO.DEL_YN` 置 0，**NEVER 顺手删 `USER_PAY_CHANNEL`**（那是 IF8A-75 解绑才做的事，提前删会让后续解绑找不到渠道信息）；**NEVER 把「查不到有效开户记录」当成错误** |
| `app/dailyticket/DailyTicketRefundCallbackReqDTO.java` | 字段名逐字对齐支付中心回调报文，**NEVER 改名或增删**：本 DTO 既是 fep-app 侧 `parseBizData` 的目标类型、又是转发给 daily-ticket-server 的入参 |
| `app/dailyticket/QueryDailyTicketPayInfoReqDTO.java` | 入参**只用 `ticketCode`，NEVER 改成 `cardNum`**（一张卡先后买过多张日票，按卡号查会命中历史多单）；**NEVER 与 `QueryDailyTicketInfoReqDTO` 合并**（后者服务 IF1A-01 闸机热路径，入参 `orderNo/cardId`，语义与时机都不同） |
| `app/dailyticket/QueryDailyTicketPayInfoResult.java` | 三个字段名 MUST 与 IF8A-05 / IF8A-34 出参 `ticketTransRecord` 的同名字段一致（甲方《青岛地铁-ITP与APP接口规范R6.docx》if8a_34）。数据来源（2026-09-15 用票号 `2099439054066438144` 实测打通）：`DAILY_TICKET_INSTANCE`（按 `TICKET_CODE`）→ `ORDER_NO` → `DAILY_TICKET_ORDER` 的 `TRADE_NO/PAY_DATE/PAY_CHANNEL_CODE`。**NEVER 因为「时间看着不对」改成行程时间或清空**；查不到时三个字段**一律留 null、NEVER 编造默认值**；`PAY_DATE` 与 `entryDate/exitDate` 同为 14 位定长、**NEVER 返回带分隔符的形式** |
| `paysign/CompensateNotifyRespDTO.java` | 签约 / 解约两个补偿端点**共用同一结构**；`submitted` 只代表「提交成功」，**NEVER 用它判断通知是否送达**（真实结果看 `APP_PAY_SIGN_REQUEST` / `APP_TERMINATION_REQUEST` 的 `NOTIFY_STATUS` / `NOTIFY_RESULT`）。2026-09-15 起本类是**唯一定义点**（pay-sign 侧同名副本已删），**NEVER 再在业务模块新建同形副本** |
| `paysign/ProcessTerminationReqDTO.java` | 两个字段都可省略：都不传即不按申请时间过滤（扫全部 `PENDING + SCANNING`）；传了则 `cutoff = referenceTime - delayDays`，业务口径「解约申请 4 天后才确认」由 `delayDays` 表达，过滤同时作用于两个状态 |
| `paysign/ResendSignNotifyReqDTO.java` / `RespDTO` | 联调期人工重放走这个单条接口，**NEVER 为了重发一条而去打批量补偿接口**（会把所有符合扫描条件的历史流水一起发给 APP）；`resultCode=0000` 只代表接口处理完成，**是否送达 MUST 看 `notified`** |
| `pay/GateTxnPayReqDTO.java` | 支付宝行业明细 9 项（`entryLineCode/entryLineName/exitLineCode/exitLineName/entryDeviceCode/entryId/exitId/cardNum/cardIssueCode`）**MUST 由 fep-dev-server 在出站当次组装后整块透传**：`GATE_TXN_PAY` 没有对应列，需 para 单查站线（批量接口不返回线路字段）、ticket 查进站设备号、按 `itpUserId + 时间戳`现算进出站 ID —— **出站是唯一能拿全这些值的时点**，gate-txn-pay 原样落 `INDUSTRY_DETAIL`，**NEVER 在下游重算**。非支付宝渠道恒 null，此时 `PaySignInitiator` 走老路、行业明细用订单快照 `JSON.toJSONString(order)`，**两套语义 NEVER 混用** |
| `pay/GateTxnPayReqDTO.java` | `copyCommonFields` 只搬运设备上送字段，**NEVER 在此加默认值 / trim / 大小写归一 / 空值兜底** —— 一旦加了「设备上送什么就落什么」这条前提就断了，而 `GATE_TXN_PAY` 是对账依据；也 **NEVER 把本类独有字段（`ticketStatus`/`orderExpType`/`offlineFlag`/站名/`industryDetail`）搬进来**（来源是 ticket-server 响应与 para 查询，不是设备报文）。⚠️ 这是**新增静态方法**、不受「Fastjson2 静默丢字段」约束，但调用方 MUST 用重新 install 过的 `model` 重建镜像，否则旧镜像启动即 `NoSuchMethodError` |
| `pay/GateTxnPayListDTO.java` | `entryId` / `exitId` **只有出站那一刻拿得到**，由 fep-dev-server 整块透传存下，fep-alipay 的行程详情靠它们去 ticket-server 关联进出站交易；**NEVER 在查询侧按订单字段重算**（重算出来的键名与值都与支付宝要的不一致） |
| `pay/GateTxnPaySyncStatusReqDTO.java` | 存在的原因：`GATE_TXN_PAY` 只在「日票 / 零元交易」分支直接写 `SUCCESS`，真实免密扣款订单调 pay-sign 后只停在 `PROCESSING` / `RETRY`，**没有任何代码能把它推进到 `SUCCESS`** —— 扣款结果只有支付中心回调知道，而回调只发到 pay-sign-server（2026-08-26 生产实测：全部真实扣款订单卡在中间态） |
| `pay/GateTxnPayDebitConvergeReqDTO.java` | 补款收敛是**独立端点 + 独立响应契约**（响应 MUST 带 `converged` 与当前 `debitStatus`）；**NEVER 在 face-pay 侧加回任何对 `GATE_TXN_PAY` 的写语句**（复用会让重复扣款静默漏判）。`origTxnDate` **MUST 传**（月分区裁剪），取补款明细的 `ORIG_TXN_DATE`、**NEVER 用当天日期替代**（补款可能发生在行程之后任意一天）；备注按 `"补款单号:" + 单号` 组织，**NEVER 让任何代码分支去解析它**。要把收敛白名单收窄成不含 `FAIL` 属业务规则变更，MUST 先与业务确认 |
| `pay/GateTxnPayDebitConvergeRespDTO.java` | **NEVER 只看 `retCode` 判成功**：`0000 + converged=true` = 本次把欠费单推进 `SUCCESS`（调用方标已结清）；`0000 + converged=false + debitStatus=SUCCESS` = 钱已实收但行程早已结清 ⇒ **重复支付、待退款**，MUST 标失败并留「重复支付待退款」，**NEVER 当成幂等成功悄悄跳过**；其余 = 业务拒绝、**一次即终态** + 留人工核对线索。`converged` 用 `Boolean` 而非 `boolean` 是**故意的**（Fastjson2 缺字段得 null，基本类型会静默 false，把「成功收敛」误判成「重复支付」），调用方 MUST 用 `Boolean.TRUE.equals(...)`；`debitStatus` 是 `converged=false` 时唯一的区分依据，对端 **MUST 始终回填、NEVER 因为「本次没改行」就省略**；`retMsg` 仅用于日志与工单、**NEVER 做分支判断** |
| `pay/GateTxnPayFailedOrderRespDTO.java` | 解约流程 **MUST 先判 `resultCode == "0000"` 再用 `hasFailedOrder`** |
| `collectpay/AppPayOrderRegisterReqDTO.java` | 三条资损防线：`totalAmount` 单位是**分**、会同时写 `TICKET_PRICE` / `TOTALPRICE` / `PAY_AMOUNT`，`TICKET_NUM` 固定 1；collect-pay 的 `requestPayInfo` 按 `TICKET_PRICE × TICKET_NUM` 算送去支付中心的金额，**NEVER 只写 `TOTALPRICE`**（算钱时根本不读那列）；`supplementFlag` 落 `RSV2`、**MUST 非空** |
| `collectpay/AppPayOrderCloseReqDTO.java` | 不关单的后果：乘客对一张已作废 / 已关闭的补款单再点支付时，collect-pay 仍看到 `PAY_STATUS='0'` 而正常发起预下单 —— **钱收进来了、上游那张单却已关闭，无人收敛** |
| `collectpay/AppPayOrderRespDTO.java` / `AppPayOrderResultRespDTO.java` | 调用方 MUST 用 `RpcOutcome` 三分支处置，**NEVER 只判「没抛异常就算成功」**。`found=false` 是对端明确答复的**业务事实**（这张单没登记进 APP 订单表、乘客根本付不了 ⇒ 落 ERROR 转人工 / 重新登记），而连不上 / 超时 / 5xx 会让查询方法**抛异常**，**NEVER 当成 `found=false`**（一次抖动会被误判成「单据丢失」）。**判成功 MUST 严格等于 `1`、NEVER 写成「非 2 即成功」**；`ItpStatusEnum.PAYING` 的 desc 写「支付中」但 `TBL_TVM_APP_ORDER.PAY_STATUS='0'` 实际是**下单后未支付**的初始态（关单白名单 `TvmAppOrderMapper.closeUnpaidByOrderNo` 的 `and PAY_STATUS='0'` 正是靠它选行），该枚举 0/1/2 各有两个常量（支付组与退款组共用 code），**NEVER 按字面理解** |
| `cardpool/CardPoolOutcome.java` | 三分支存在的理由：此前 `CardPoolClient` 用 `null` / `false` 表达全部失败，调用方分不清「池里真没号」「票种压根不走卡池（程序缺陷）」「card-pool-server 不可达（可重试）」，只能一律翻成「无可分配逻辑卡号」，把配置错误与网络故障都报成卡池耗尽。`REJECTED` 属参数 / 配置问题、**重试无用、MUST 告警** |
| `cardpool/CardPoolActionResult.java` / `CardPoolReserveResult.java` | 服务端对释放 / 确认两个动作**已做幂等兜底**（影响 0 行时回查终态），故 `SUCCESS` 同时覆盖「本次改状态成功」与「此前已到终态」；`getMessage()` 是服务端原文，只用于日志与告警、**NEVER 直接透给终端用户** |
| `cardpool/CardPoolReservationActionReqDTO.java` | `businessId` **MUST 与创建预占时一致**，否则命不中预占记录 |
| `ticket/NotifyVerifyResultRespDTO.java` | 本次行程消耗日票次数**恒为 1**（仅 `SIGN_CHANNEL_CODE=12/13/14/15` 写入），**NEVER 回填 `DAILY_TICKET_INSTANCE.ACTUAL_TIMES`** —— 那列用 `-99` 表示不限次，会一路透到 APP 与 `GATE_TXN_PAY.COUNTING_TIMES`（2026-09-10 订单 `GT20260910143159899000084` 已发生）。`debitRequestResult` **NEVER 依赖**：全仓无写入方、恒 null（2026-09-14 grep `setDebitRequestResult` 只命中本类 setter），看扣款结果 MUST 走 IF8A 交易记录（权威列 `GATE_TXN_PAY.DEBIT_STATUS`，`PAY_TXN_DETAIL.DEBIT_REQUEST_RESULT` 仅兜底）。实际卡类型 **MUST 走响应通道回传**：fep-dev → ticket 的请求对象是跨进程值拷贝，ticket 在 `applyActualCardType` 改的是自己那份副本；支付渠道编码已由 `payChannelCode` 承载 `USER_ITP_REG_INFO.CHANNEL`，**NEVER 再加 `paymentVendor` 表达同一个值** |
| `ticket/enums/AdviceOptEnum.java` | 这套取值此前**在三处各写一份且互不知晓**（`GateTicketHandler` 三个常量 / `CardDataHandler` 约 20 处裸字面量 / `fep-dev` 的 `Set.of("005","006")`），第三处是**防资损白名单**（BOM 已补出站的交易不再重复扣费），对不上即漏扣或重扣，2026-09-14 收口到本枚举。**码值 NEVER 改动**（`000/005/006/018/020` 是与 BOM、闸机的对外契约）；新增取值 **MUST 同时确认 `isSupplementExit()` 归属**（漏归类会让 `GateFarePaymentOrchestrator.shouldPay` 走「未知即扣费」）。**`020` NEVER 放进 `isSupplementExit()` / `SUPPLEMENT_EXIT_CODES`**：2026-09-14~15 之间曾实现成「更宽松版 005」（补出站 + 落 08 + 进资金白名单），**那版口径已作废、NEVER 回退** —— `005/006` 的前提是「卡已进站未出站」，而 `020` 要放行**从未进站的新卡 03**（用户第三次裁决），补进站口径才自洽；登记 `020` 的直接原因是 BOM 实际上送的就是它（2026-09-14 19:58 实测），枚举里没有导致 IF5A-03 一路返 `8001`、零数据落库。`isSupplementExit()` 是资金安全判据：命中即出站费用已在 BOM 侧结清、出站扣费 MUST 跳过；不命中（含未知取值）MUST 照常扣费，**NEVER 因为「020 也叫免费更新」就加回来**；上游可能上送未登记取值，**处置 MUST 由调用方决定、NEVER 在枚举里抛** |
| `recon/ReconFileTypeEnum.java` | `PAY` / `BUS` 是**汇总文件**，源服务 MUST 在数据库内 `GROUP BY` 后再上送，recon-server 按 `getKeyFieldCount()` 段键聚合、对随后 `getMetricFieldCount()` 个度量列累加；字段顺序来自甲方《ACC与ITP之间的文件.docx》§一，**改动 MUST 同步各源服务（现为三个：gate-txn-pay / daily-ticket / face-pay，collect-pay 源 2026-10-13 已移除；清单 MUST 现查）与 recon-server** —— 分片是纯文本无 schema，**错位不报错、只静默出错账**；`EXP` 是「单边账 / 异常交易」明细，**NEVER 当成全量过闸明细** |
| `recon/ReconRecord.java` | **NEVER 在业务代码里自行 `String.join("|", ...)` 拼行**：字段里一旦带分隔符或换行，整个文件从该行起全部错位，而纯文本没有 schema、下游读不出异常 |
| `recon/ReconExportReqDTO.java` / `ReconExportRespDTO.java` | 各源 MUST 用本类提供的转换方法落到自己的列类型，**NEVER 在 SQL 里对时间列做函数转换**（分区裁剪与索引全失效）；`accepted=true` 只表示已排入抽取队列，**收齐判定 MUST 以 `RECON_BATCH_SOURCE.STATUS=COMPLETED` 为准** |
| `recon/ReconPartReceiptDTO.java` | 重试 **MUST 复用相同分片号、NEVER 换号重传** —— 换号会让同一批数据在最终文件里出现两次 |
| `recon/ReconSourceCompleteReqDTO.java` | 分片数 / 记录数 / 金额三项任一不符即置 `MISMATCH` 并告警、**NEVER 放行** —— 少一片就少几十万条，而文件本身看不出缺失 |
| `alipaytrip/AlipayProcessTerminationRespDTO.java` | **MUST 先判 `resultCode=="0000"` 再看计数**，否则把「查询未执行」误读成「无待处理记录** |
| `accsecure/RequestQrLogicNumListReqDTO.java` | 申请流水号与批次号一一对应，**ACC 侧按整数解析、MUST 为纯数字** |

## 附二.6 rpc —— `ProxyWebClient`（实体在 `resource/micro/web`）与动态路由

- **`ProxyWebClient` 不在 `rpc` 模块里**，实体是 `resource/micro/web/.../web/client/ProxyWebClient.java`；`rpc` 的各 Client
  只是它的子类。排查连接池 / 超时 / 请求头行为 MUST 去 micro 侧看，**NEVER 在 `rpc` 里找它的实现**。
- **超时只能从构造器传，NEVER 靠覆写 `getResponseTimeout()` 读实例字段**（`ProxyWebClient.java`）：
  `getResponseTimeout()` 是在**父类构造期**被 `getInitOkHttpClient` 调用的，那时子类的 `@Value` 字段还没赋值，
  「覆写后返回自己的超时字段」**实际读到恒为 0 / null，且编译与单测都发现不了**。需要非默认超时的子类 MUST 用带
  `responseTimeout` 形参的构造器（传 null 退回默认 10 秒）。
- **只自动透传 MDC 里的 `authorization` 一个头**（`addAuthorizationToHeader`），**不注入任何 trace 头**。因此
  Quartz 侧要带链路上下文 MUST 显式传 `QuartzTraceUtils.traceHeaders`（W3C `traceparent` + `X-Vlogs-Capture`）；
  服务间调用的 traceId 靠 Boot 托管的 `WebClient.Builder` 由观测自动带出。**NEVER 自造 `traceId` 请求头** ——
  下游 `FirstFilter` 把请求头名全部小写后塞 MDC，`traceId` 会变成 `traceid`，与 log4j2 的 `%X{traceId}` 对不上、等于白传。
- **HTTP 4xx 与连接失败在 `handleResponse` 里被包成同一个 `RuntimeException`**（`ProxyWebClient.java`；
  **注释里此前写是错的、行号已随改动漂走，引用前 MUST 先 grep 方法名、NEVER 照抄行号**）——
  这就是 `RpcOutcome` 把 4xx 归到 `Unreachable` 的原因（见 §附二.7）。
- **动态路由靠「baseUrl 传空串 + 每次调用给绝对 URL」**（`rpc/.../route/URLDynamicRouter.java`，装配注解 `@EnableRpcRoute`）；
  `ReconExportClient` 是现行唯一使用者，理由与坑见 §附二.8。

## 附二.7 rpc —— `RpcOutcome` 三分支（`Ok` / `BizRejected` / `Unreachable`）

- **为什么不是 boolean**（`outcome/RpcOutcome.java`）：`docs/architecture/transaction-and-consistency.md` §3 为「RPC 包装方法 MUST 返回 `RpcOutcome`，NEVER 返回 boolean」的包装方法
  专门写过规则，而它被违反过两次并双双造成生产不一致（`docs/domain/decisions.md` ADR-D13）。规则靠人记、boolean 靠人查；
  `sealed` + `record` 让穷尽性由**编译器**检查 —— 调用点少写一个分支直接编译失败。
- **真正的收益是可区分「业务拒绝」与「网络不可达」**：`BizRejected`（对端答复但业务拒绝，如 UPDATE 影响 0 行后返 FAIL）
  **重推一万次也不会成功、MUST 直接转终态 / 工单，NEVER 进补偿队列**；`Unreachable`（连不上 / 超时 / 4xx / 5xx）
  才是补偿队列该收的那类。**误判方向 MUST 是「把永久失败当成可重试」**（代价只是白重试几轮），
  **NEVER 反过来** —— 把网络抖动当成业务拒绝会让一笔真实待投递的事实被直接判死。
- `retMsg` 仅用于日志与工单、**NEVER 做分支判断**；`Unreachable.cause` **MUST 保留原始异常**，
  调用方 **NEVER 只打 `getMessage()`**；`isSuccess()` **只用于日志与计数，分支判断 MUST 用 switch 模式匹配**，
  否则又退回 boolean。**本类型 NEVER 参与序列化**：它是进程内调用结果、不是报文 DTO，因此放 `rpc` 而不是 `model`。
- **落地方式是「新增方法、老方法保留」**：`rpc` 版本号锁死在 2.0.1、被 21 个模块引用，**MUST 只增不改签名**、逐调用点迁移；
  `PaySignClient.updatePaySignDisplayAccount` 只作为 `@Deprecated` 兼容壳，实现已委托给
  `updateDisplayAccountOutcome`，**NEVER 在兼容壳里重复一份解析逻辑**。新版方法 **NEVER 向外抛异常**：
  异常统一收成 `Unreachable` 并带 cause；解析 MUST 收在方法内—— HTTP 2xx 但响应体不是 JSON（如网关返 HTML 错误页）时
  `JSONUtil.parseObj` 会抛 `JSONException`，逃出去就破坏了「NEVER 向外抛异常」的契约。

## 附二.8 rpc —— 各 `*Client` 逐条（20 个 Client）

`rpc` 下现有 20 个 Client：`account` / `alipay.account` / `alipay.paysign` / `blacklist` / `cardpool` / `collectpay` /
`dailyticket` / `f2f` / `facepay` / `fepDev` / `industry` / `key` / `para` / `pay(GateTxnPay)` / `paySign` / `recon(ReconClient
+ ReconExportClient)` / `security` / `ticket` / `transquery`。下表是它们注释里迁出的知识（`dailyticket` / `fepDev` /
`industry` / `key` / `security` / `ticket` 六个当时无 MUST-NEVER 类注释）。

| 位置（删除前） | 迁出的知识 |
|---|---|
| `paySign/PaySignClient.java` | 解约申请批处理 `/internal/termination/process` 供 web-admin Quartz 调；**不抛业务异常，失败体现在 `resultCode` 上，响应为 null 说明 HTTP 层没通**。带 headers 的重载只需传 `traceparent`（W3C `00-<32hex>-<16hex>-00`），**NEVER 传自定义 `traceId` 头** |
| `PaySignClient.java` | 签约通知补偿扫 `APP_PAY_SIGN_REQUEST`、解约通知补偿扫 `APP_TERMINATION_REQUEST`，**覆盖的表不同、不可互相替代、MUST 各自调度**。**调用方 NEVER 在同一次调度内循环调用**：下游在提交重发**之前**就同步 `NOTIFY_RETRY_COUNT +1` 并置 FAILED，而通知是异步发的，紧接着再调一轮会立刻扫到同一批、**几秒内烧完全部重试预算**；排空靠 cron 周期，**调度间隔 MUST 大于下游 PENDING 滞留阈值**。`submitted` 只代表「提交成功」 |
| `PaySignClient.java` | 退款回查补偿 `/internal/payment/compensateRefundQuery`：扫 `PAY_REFUND_DETAIL` 里停在 `PROCESSING` 且距上次发起超 `REFUND_QUERY_STALE_MINUTES`（现 5 分钟）的单，逐条**出网**调支付中心 §3.2 `refundQuery` 回查并 CAS 收口。**与 `compensateRefundSummary` 是两件事、NEVER 合并调度**（本条出网、要贴退款时效；那条不出网、属日终级别；合并后既没法分别调频，出网这半边一挂还把纯本地那半边拖停）。**调度间隔 MUST 大于 staleMinutes**（建议 `0 0/10 * * * ?`）；单条是否收口 MUST 看 `PAY_REFUND_DETAIL.REFUND_STATUS` |
| `PaySignClient.java` | 退款汇总跨表对账 `/internal/payment/compensateRefundSummary`：**不出网**，只比对 `PAY_REFUND_DETAIL`（唯一账本）与 `PAY_TXN_DETAIL` 的 `REFUND_AMOUNT` / `REFUND_STATUS` 汇总并逐单重算。**`skipped` 长期非 0 是预期**，它混着两类：重算影响 0 行（下轮自愈）与「明细已 SUCCESS 但 `PAY_TXN_DETAIL` 里没有该 `ORDER_NO`」（**永远不会自愈**、只打 WARN 等人工）。**调用方 MUST NOT 把 `skipped > 0` 记成 ERROR**，区分两类只能看 pay-sign 侧日志措辞 |
| `PaySignClient.java` | 四个无参 POST 的 **body MUST NOT 传 null**：`ProxyWebClient.postJsonAndGetResponse` 无条件调 `bodyValue(...)`，而 Spring 的 `BodyInserters.fromValue` 断言非 null，传 null 抛 `IllegalArgumentException: 'body' must not be null`、请求根本发不出去；传空 Map 序列化成 `{}`（`BlacklistClient.java` 同因，那里写了完整解释） |
| `pay/GateTxnPayClient.java` | `hasUnsettledOrderByCard` 按 `CARD_ID`、不带渠道、不带时间下限（`BLACKLIST` 无渠道字段，判定必须覆盖该卡全部历史欠费）；`syncDebitStatus` 的 `retCode` 非 `0000` 即 `GATE_TXN_PAY` 没收敛，**NEVER 因为「没抛异常」就认为对齐了**；IF8A-35 两个计数在查询未执行时为 0、**NEVER 当成「无欠费」** |
| `GateTxnPayClient.java` | `countTransList` 是本 Client 里**唯一「对端返裸标量、既无 DTO 也无 retCode」**的方法（gate-txn-pay 侧 `/ci/gateTxnPay/app/countTransList` 直接把 int 写进响应体）。这个隐式契约没有编译期保护，**因此 MUST 把「对端到底返了什么」带进异常消息**（裸 `Integer.parseInt` 的 `NumberFormatException` 只有 `For input string: "..."`，看不出是包装变了还是 envoy 返了错误页）。**NEVER 改成 catch 住返 0** —— 那会把「对端契约变了」伪装成「该用户没有交易记录」，分页总数恒 0、APP 列表永远只剩第一页且日志里一条错都没有 |
| `GateTxnPayClient.java` | IF8A-41 统计源表是 `GATE_TXN_PAY`（同时有原价 / 票价 / 超时费 / 实付四个量，单表可算全）。**NEVER 退回 ticket-server 用 `QRCODE_TXN_DETAIL` 自算** —— 那张表没有 `ORIGINAL_FARE`，只能拿超时费冒充优惠（2026-09-10 修正的语义错位） |
| `GateTxnPayClient.java` | `recoverOfflineFare`（离线码金额补偿）/ `pushMetroTransfer`（公交换乘推送）供 web-admin Quartz 调，对端 2.0.73 起才有这两个端点（此前是模块内 `@Scheduled`）。两者**同步跑完一轮才返回**，`sys_job` 的「禁止并发」才有意义 —— 对端改成异步受理即返回时禁并发会失效；**对端恒返 `retCode=0000`**（本轮扫表异常属可自愈），结论只在 `retMsg` 里，**NEVER 为了让 `sys_job_log` 更「灵敏」而要求对端把可自愈错误返成非 0**。两个方法语义完全一致、**改一个 MUST 看齐另一个** |
| `GateTxnPayClient.java` | `convergeDebitStatusForSupplement` 与上面两个 `/internal/**` 不同：**不是补偿批处理、对端也不恒返 `0000`**，调用方 MUST 按 `retCode` + `converged` + `debitStatus` 三者分支（判据在 `GateTxnPayDebitConvergeRespDTO` 类注释，见 §附二.5），**NEVER 只看 `retCode`**。**本方法不吞异常**（网络不可达时原样抛给调用方 = `Unreachable`，调用方 MUST 不改本地状态、留补偿重入），**NEVER catch 后返 null 或造假业务码**；本方法**不带 headers 形参**（调用方是业务链路、traceId 由观测自动带出），将来要加内部令牌 **MUST 新增重载、NEVER 改签名** |
| `GateTxnPayClient.java` | IF8A-26 补款下单**已随补款功能迁入 face-pay-server**（2026-09-15），本 Client 里同名方法已删除，改走 `facepay/FacePayClient.java`（`retCode` 8001 参数问题 / 8003 订单状态或金额校验不通过，**两者都不应重试**） |
| `account/AccountClient.java` | IF8A-75 补建解约申请前用它取 `cardId` / `cardType`（权威来源是 `APP_USER_PAY_CHANNEL`，见 §附二.5 那条）；未找到返 `8004`，**MUST 判 retCode 而不是只判字段空** |
| `AccountClient.java` | `syncPayAccountId`（ADR-D32）**MUST 当「允许失败」处理**：只回写一列展示值，权威值始终在支付域 `APP_PAY_SIGN_INFO`，丢一次不影响任何业务动作（IF8A-77 仍会自行取值）。调用点 **MUST catch 全部异常只记日志、NEVER 让它把已提交的签约结果翻成失败**；命中 0 行时账户域返 `8004` 而不是抛错（签约先于开卡是合法时序），仍 **MUST 判 retCode、NEVER 假定「没抛异常就是写成功」** |
| `AccountClient.java` | 两个带 trace 头的重载专供 web-admin Quartz；`ProxyWebClient` 只透传 `authorization`、不注入 trace 头，**不传就 traceId 断链、前台「执行日志」查不到下游** |
| `alipay/account/AlipayAccountClient.java` | 配置键用**嵌套默认值** `service.alipayAccount.url` → `service.account.url`，**NEVER 退回只读后者**：`service.account.url` 在不同模块含义不同（`fep-alipay-server` 把它配成 alipay-account 地址，而 `ticket-server` 的同名键指向**真 account-server**），于是 ticket-server 的 `CardDataHandler` 按 `thirdUserId` 查支付宝用户时一直打错目标，且它早已配好的 `service.alipayAccount.url` 没有任何读取方（2026-09-11 定位）。`fep-alipay-server` 没有该键会自动回落、**行为不变、不需要改那边配置** |
| `alipay/paysign/AlipayPaySignClient.java` | 销卡批处理**服务端单次只处理一批（上限 200 条）**，调用方 **MUST 反复调用直到 `scanned` 为 0 才算排空**；不抛业务异常，MUST 先判 `resultCode`，返 null 说明 HTTP 层没通；带 trace 头的重载口径同 `PaySignClient.processTermination` |
| `AlipayPaySignClient.java` | 支付宝出行欠费**只落 `ALIPAY_PAY_LOG`、`GATE_TXN_PAY` 里没有对应行**，因此判定「该卡欠费是否结清」**MUST 同时问本接口与 `GateTxnPayClient.hasUnsettledOrderByCard`，缺一个就漏判**；下游在查询未真正执行时把 `hasUnsettled` 置 true，**NEVER 当成「已结清」** |
| `collectpay/CollectPayClient.java` | `/internal/app-order` 登记的**幂等键是 `orderNo`**（对端按订单号回查、已登记就返成功），因此可被补偿任务无限重放 —— 这是「先落本地 PENDING、提交后再同步」那套 outbox 成立的前提，**NEVER 让对端改成「重复即报错」**。查询方法在**网络失败时 MUST 抛异常、NEVER 返回「查不到」**（两者处置方向相反，见 §附二.5 `AppPayOrderResultRespDTO`） |
| `f2f/F2FClient.java` | 四个通知接口请求体都是空、只有路径不同，统一在此发出；请求体**保持 `null`、NEVER 改成空 Map** —— 下游 `NoticeAppTask` 的入参形态未经验证，改动等于改契约（**与 `PaySignClient` 那组「body MUST NOT 传 null」方向相反、NEVER 合并理解**：那组走 `postJsonAndGetResponse`，这条走的是另一条发送路径） |
| `para/ParaClient.java` | **零字段 DTO 场景 MUST 改送空 JSON 对象**：`RequestLineCodeListReqDTO` / `RequestLineStationCodeVersionReqDTO` 都是零字段类，直接 `bodyValue(request)` 会被 WebClient 判定为无可用编码器并抛 `UnsupportedMediaTypeException: Content type 'application/json' not supported for bodyType=...`，**请求根本发不出去、响应退化成 UUID retCode**；入参保留在签名上只为兼容调用方，**NEVER 改回 `postJsonAndGetResponse(url, request)`** |
| `ParaClient.java` | 带 trace 头的重载供 web-admin Quartz，口径同 `PaySignClient`；⚠️ `ProxyWebClient` 不会自动注入任何 trace 头（只从 MDC 取 `authorization`），**这个重载是必需的、不能指望框架兜底** |
| `cardpool/CardPoolClient.java` | 三个方法都返回结果对象而不是 `null` / `false`，调用方 **MUST 按 `CardPoolOutcome` 分流**（池空 / 参数或票种被拒 / 远端不可达），**NEVER 再统一报成「无可分配逻辑卡号」**。释放已幂等（重复释放同一 `reservationId` 仍成功；已 `ASSIGNED` 的不允许释放、返 REJECTED），`CALL_FAILED` 时**不必强行重试到成功**，预占超时回收会兜底。卡池维护端点**只做受理**（提交给单线程维护池后立即返回，ACC 申请 / FTP 下载 / 十万行入库都在服务端后台跑），**返回成功不代表这一轮已导完**；`data.accepted=false` 表示上一轮未结束、本轮被丢弃，这是**正常限流、NEVER 因此抛异常告警**（否则前台调度日志长期一片红） |
| `recon/ReconClient.java` | 分片上传走 `application/octet-stream` **流式**发送（`BodyInserters.fromResource`）：**NEVER 改成读成 `byte[]` 再发**（单片上限 128MB，读成数组等于每片一次 128MB 堆分配、并发几片就 OOM），也 **NEVER 改成 JSON**（Base64 放大 33%，且 400 万条明细无法整体入内存）。本客户端**失败即抛异常、不返回 false**，调用方 `ReconPartSink` 据此中断本次抽取并声明失败，**NEVER 吞异常继续写下一片** —— 那会产生分片号空洞、最终在生成阶段报「分片序号不连续」而整批失败 |
| `ReconClient.java` | `runDailyBatch` 会**阻塞到批次收口或超时**（服务端 `recon.orchestration.run-timeout-millis` 默认 4 分钟 < 本客户端 `getResponseTimeout()` 的 5 分钟）；无参重载 MUST 委托到带 headers 的那个、**NEVER 让两个重载各写一份请求逻辑**。分片按 `(batchId, source, fileType, partNo)` 幂等：哈希相同返首次回执、不同则拒收，**重试 MUST 用同一 partNo 与同一文件内容** |
| `recon/ReconExportClient.java` | 本类**不绑定单一目标地址**（三个源地址各不相同），复用 `URLDynamicRouter` 的做法：**baseUrl MUST 保持空串、NEVER 填 `service.recon.self-url` 之类占位地址**。两层理由：① `InternalMicroHttp.logRequest()` 打的是无条件 `joinUrl(getBaseUrl(), url)`，占位 baseUrl 会让 INFO 日志出现 `url=http://127.0.0.1:9112/http://gate-txn-pay-server-...:30019/internal/recon/export` 这种双份地址（2026-09-11 端到端实录），把人往「打到自己身上」误导；② 能否忽略 baseUrl 取决于 `DefaultUriBuilderFactory` 的实现细节（绝对 URL 带 host 时才丢弃 baseUri），依赖它属**隐式契约** |
| `ReconExportClient.java` | **下发时固定带 `X-Vlogs-Capture: 1`（2026-09-14 新增，NEVER 删）**：三个源的 INFO 能不能进 VictoriaLogs 取决于其 MDC 有没有该键，而 `ProxyWebClient.addAuthorizationToHeader` **只转发 `authorization`**，web-admin 发出的该头到 recon-server 这一跳就断了；少这一行时按 traceId 反查只能看到 web-admin + recon-server 两段、三个源的 INFO **一条都查不到**（2026-09-14 实测）。写死 `1` 是因为本端点**每天每源一次**、量级可忽略，**NEVER 把这个写法照搬到高频业务端点**（等于把该链路全部 INFO 灌进日志库） |
| `ReconExportClient.java` | `dispatchExport` **从不抛异常**（地址非法 / 不可达 / 响应无法解析都返 `accepted=false` 带原因）—— `ReconOrchestrationService.dispatchSource` 依赖这个语义把来源逐个标 FAILED 后继续下发其余源，**抛异常会让同一轮里剩下的源全部漏发**；`sourceBaseUrl` **缺 scheme 即当配置错误直接拒绝**，NEVER 放过去让 WebClient 当相对路径拼到空 baseUrl 上 |
| `recon/ReconPartSink.java` | 用法固定 try-with-resources 且成功路径 **MUST 显式 `commit()`**；没走到 `commit()` 就 `close()`（抛异常、提前 return）时本类会向 recon-server **声明失败而不是静默退出** —— 否则该源永远停在 `EXPORTING`、整批对账挂死等不到收齐。**NEVER 把全部记录缓存到 `List` 再一次性写**（400 万条 × 100 字节 = 400MB 堆），本类只持有 1MB 级 `BufferedOutputStream`、内存与数据量无关；行内容 MUST 由 `ReconRecord.line(...)` 生成 |
| `recon/ReconPartUploader.java` | 分片粒度「记录数或未压缩字节先到先滚」，默认 **20 万条 / 64MB**（400 万条明细约落 20~40 片）。**NEVER 把上限调到百万级**：单片越大重传成本越高，且服务端 `recon.max-part-bytes`（默认 128MB）会直接拒收。临时目录默认 `/home/javaapp/app/recon-export`，分片一经上送即删，磁盘峰值 = 并发文件类型数 × 单片上限 |
| `transquery/TransQueryClient.java` | 三个方法与 `TicketClient` 同名方法**路径、请求 DTO、应答 DTO 全部逐字相同**，唯一差别是 baseUrl；另起 Client 而不是改 `TicketClient` 的 baseUrl，是因为 ticket-server 上还有乘车码状态机、自助补站等一批接口没迁走。**ticket-server 那三个同路径端点仍在、未删**（过渡期双活），回滚只需把调用点换回 `ticketClient`；两边同时在跑期间 **MUST 保证 `app.trans.*` 五个键取值一致**，否则同一笔订单在新旧链路会返回不同商户号。支付宝行程与日票乘车记录**故意不在这里**（还没迁进 trans-query-server、阻塞在支付宝 pay-sign 新表），仍 MUST 走 `TicketClient` |

## 附二.9 rpc —— `@EnableRpcXxx` 装配注解（18 个）

`EnableRpcAccount` / `AlipayAccount` / `Blacklist` / `CardPool` / `CollectPay` / `DailyTicket` / `F2f` / `FacePay` /
`GateTxnPay` / `IndustryData` / `Key` / `Para` / `PaySign` / `Recon` / `Route` / `Security` / `Ticket` / `TransQuery`。

- 新增跨服务调用的三件套（缺一不可）：① 在对应 Client 加方法；② 调用方启动类加 `@EnableRpcXxx`；③ 调用方配置补 `service.xxx.url`。
- **`@EnableRpcTransQuery` 与 `@EnableRpcTicket` 不互斥、通常成对出现**（`EnableRpcTransQuery.java`）：交易列表 / 统计 / 详情
  走前者，支付宝行程与乘车码状态机等仍留 ticket-server。加了它 **MUST 同批补 `service.transQuery.url`**，
  否则 baseUrl 退化成默认服务名 `trans-query-service`、**集群里 DNS 解析不到**。

## 附二.10 resource/micro —— `sql-datasource`（Druid 配置与两条孪生陷阱）

- **两条孪生陷阱同源于 `sql.properties` 的 `spring.datasource.druid.filter.wall.enabled=true`**（全部服务共用），
  Druid WallFilter 默认 `commentAllow=false`：① **NEVER 在 SQL 正文写注释**（`--` 或 `/* */`）——
  抛 `SQLException: sql injection violation ... comment not allow`，语句**静默失效**（已发生：`PayTxnDetailMapper.updatePayCallback`
  的 WHERE 内两行行注释让支付成功回调的 UPDATE 全部失败、`PAY_STATUS` 长期卡 `PROCESSING`，2026-08-25 修复）；
  ② **NEVER 写 `where 1 = 1` 打头 + 全部谓词都是可选 `<if>`** —— 条件全空时恒真条件成为唯一谓词，报
  `sql injection violation ... select alway true condition not allow`，MUST 用 MyBatis `<where>` 标签
  （已发生：`account-server` 的 `UserItpRegInfoMapper.countGroupByCardType`，2.0.73 修复）。
  **NEVER 靠放宽 `wall.config.comment-allow` 绕过**（全局降低注入防护等级）；注意 **`dm.properties` 在达梦环境关闭了
  WallFilter**，因此这两类缺陷**只在 Oracle 环境暴露**。
- **两个超时是「双层保护」，改一个 MUST 想清另一个**（原注释已迁出、配置行保留）：`sql.properties`
  `druid.query-timeout=30`（池级兜底，未显式 `setQueryTimeout` 的语句 30s 超时；业务显式设置的如对账抽取会覆盖）+
  `oracle.properties` 的 `oracle.jdbc.ReadTimeout=10000`（socket 读 10s）。后者的真实目的是**限制单次慢 SQL 的 pin 时长上限**：
  ojdbc8 的 `PhysicalConnection` / `OracleStatement` 大量方法是 `synchronized`，JDK 21 未落地 JEP 491，虚拟线程在
  `synchronized` 内阻塞会 pin 载体线程；10s 读超时让 DB 响应超时的语句直接抛 `SocketTimeoutException`、pin 的载体线程立刻释放。
  已在 2026-08-26 pay-sign-server 生产事故中观察到「对端 17ms 答完、本端 287s 才处理」的形态，根因即载体线程被 pin 满。
- **`sql.properties` `remove-abandoned-timeout=300`（5 分钟）是刻意降下来的**：默认 30 分钟在虚拟线程环境下太长，
  pin 满载体线程前连接不会被回收。长耗时批处理（对账抽取等）**已显式切到 `newFixedThreadPool` 平台线程上跑、不受此限制**。
- 其余现状：`stat-view-servlet` 默认开启且 `login-username/password` 都是 `admin`（`sql.properties`）——
  属**上线前 MUST 处置**的暴露面，见 §矛盾与待裁决。多数据源按 `other.sql.double-datasource` + `ConditionalOnDoubleDatasource`
  条件装配；`SqlAudit` 由 `other.sql.audit` / `other.sql.rows.warn=1000` 控制慢查询与大结果集告警。
  **`SqlConfiguration.java` 里有一个共享 `@Scheduled`**（`other.monitor.resetAllMetersCron`，见 §附二.12）。

## 附二.11 resource/micro —— `mybatis-adaptor`

- 只有三个类：`EnableDefaultMybatisAutoConfig`（业务模块启动类必加）、`DefaultMybatisConfiguration`、
  `DynamicMybatisConfiguration`；**当时全模块只有 7 行流程性注释、无任何约定性说明**，因此这里记的是**实测形态**而非迁出文本。
- `persistent.properties` 的 `mybatis.mapper-locations=classpath*:/mappers/*.xml` 是**默认值**，
  而多数业务模块把 mapper 放在 `resources/mapper/`（单数）并在自己的 `application.properties` 里覆盖该键 ——
  **新建模块时 MUST 确认这两者对得上**，否则表现为「mapper XML 明明在、方法却报 `Invalid bound statement`」。
- 数据访问 **MUST 走 `mybatis-adaptor` + mapper XML，NEVER 写原生 JDBC 或 MyBatis 注解 SQL**（AGENTS.md §4.1）。

## 附二.12 resource/micro —— web 组件（`WebAutoConfig` / `FirstFilter` / 两个观测切面 / `web.properties`）

- **`WebAutoConfig.java` 的 `@EnableScheduling` 是全局生效的**（该类还带 `@EnableAspectJAutoProxy` / `@EnableAsync` /
  `@ComponentScan` 三个包 / `@PropertySource("classpath:web.properties")` / `@AutoConfigureBefore` Knife4j）。
  **只要模块依赖了 micro-web（实测 31 个 pom 引用），调度容器就在跑**，与该模块自己有没有 `@EnableScheduling` 无关 ——
  排查「我没开调度为什么有定时任务」MUST 先看这一行，**NEVER 断言「本模块没开 `@EnableScheduling` 所以不会有 `@Scheduled`」**。
- **公共构件自带 3 个共享 `@Scheduled`**（2026-09-16 逐个实测，全仓 micro 侧只有这三处）：
  `micro/monitor/prometheus/ResetMetersJob.java`（`other.monitor.resetAllMetersCron`，默认 `0 0 2 * * ?`）、
  `ResetMetersJob.java`（`other.monitor.logAllMetersCron`，默认 `0/30 * * * * ?`）、
  `micro/datasource/SqlConfiguration.java`（同 `resetAllMetersCron`）。它们**在每个依赖 micro 的服务里都跑**，
  统计「某模块有几个 `@Scheduled`」时 **MUST 声明是否含这三个**（`docs/business/README.md` 全局事实那份清单是**业务侧口径、不含它们**）。
- **`FirstFilter`（`micro/web/filter/FirstFilter.java`）把全部请求头名 `toLowerCase()` 后逐个 `MDC.put(key, value)`**。三条连带结论：① **NEVER 自造 `traceId` 请求头** —— 会落到 `traceid` 键、与 log4j2 的
  `%X{traceId}` 大小写对不上、**永远不显示**（这条在 5 个 rpc Client 的注释里各写了一份，源头就是这里）；
  ② `X-Vlogs-Capture` 正是靠这里变成 MDC 的 `x-vlogs-capture` 才能命中 appender 的 `ThreadContextMapFilter`；
  ③ **`authorization` 也进了 MDC**，因此 VictoriaLogs 的 event template **MUST 只输出 `traceId` / `spanId` 两个白名单键**，
  **NEVER 改成全量输出 MDC**（等于把令牌送进日志后端）。
- **两个观测切面 MUST 用 `observation.start()` + `openScope()` + `catch (Throwable e) { observation.error(e); throw e; }` +
  `finally observation.stop()`，NEVER 回退成 `observation.observe(Supplier)`**（`micro/monitor/trace/MapperAspectToTrace.java`
  与 `ServiceAspectToTrace.java`，2026-09-14 修，ADR-D53）。`observe` 收的是 `Supplier`、不能抛受检异常，原实现在 lambda 里
  `throw new RuntimeException(e)` 把真实异常包了一层，后果是**调用方按类型 catch 一律失效** ——
  本项目幂等兜底全靠 `catch (DataIntegrityViolationException / DuplicateKeyException)`，包一层后那些 catch 永远进不去、
  异常直冒到全局处理器变成 500。**只在打开 tracing 的模块中招**（`shouldSkipAopTraceLogic()` 短路其余模块），
  因此**同一份业务代码在 account-server 上正常、在 card-pool-server 上就崩**：2026-09-14 并发开户实测，卡池 `reserve`
  明明写了 `catch (DataIntegrityViolationException)` 去回查兄弟请求的预占，却因本条被跳过，并发同 `businessId` 的第二条请求
  直接 500、APP 收到「暂无卡数据资源」。**这两处各保留了一行式护栏注释，见 §附二.14。**
- **`web.properties` 里六个「一改就全局生效」的键**： `server.port=8080`（业务模块各自覆盖）、
   `spring.threads.virtual.enabled=false`（**2026-10-20 起全服务默认禁用虚拟线程**；历史曾是 `true`）、
   `spring.mvc.throw-exception-if-no-handler-found=true` + `spring.resources.add-mappings=false`
  （**这两条正是「404 变成 HTTP 200 + UUID retCode」的机理**：无 handler 抛异常 → 全局处理器兜成 UUID 码）、
  `management.tracing.enabled=false`（**默认关闭**，因此 `%X{traceId}` 那一列默认恒空；打开 MUST「三行成组」）、
  上面那两个 cron。OTLP 上报配置**整段注释掉且 NEVER 删除**（2026-09-08）：endpoint 指向的
  collector 在集群里不存在，任何打开 tracing 的服务都会被 `OtlpAutoConfiguration` 建出 exporter、导出线程每批抛
  `UnknownHostException` 刷日志（pay-sign 已实测）；且其中 6 行键名在 Spring Boot 3.2.6 里**根本不存在**
  （真实前缀是 `management.otlp.tracing.*`、只有 endpoint/timeout/compression/headers 四个键），**从来没生效过、恢复上报时勿照抄**。
- `log4j2` 相关知识（`CustomLoggingConfiguration` 的 ClassLoader 坑、`VictoriaLogsAppender` 的四条设计约束、
  两级过滤与 `AppenderRef` 引用层短路）**已在 `common-components.md` §3.1.1 成文**，本次不重复；
  `CustomLoggingConfiguration.java` 与 `VictoriaLogsAppender.java` 的注释按同一口径删除，
  **NEVER 退回不带 ClassLoader 的 `Configurator.initialize` 重载**这条判据以 `common-components.md` §3.1.1 为准。

## 附二.13 公共构件改动的连带影响（版本号锁死 + 逐个重建镜像）

- **`model` / `rpc` 的 `<version>` 锁死在 2.0.0 / 2.0.1**（用户 2026-09-07 明确要求，`AGENTS.md §6`）：被 21 个模块 pom 的
  `<model.version>` / `<rpc.version>` 引用，升一次要连带改 21 个文件、漏一个即构建期报「找不到依赖」。
  连带三条硬约束：① 公共构件里 **MUST 只增方法、NEVER 改已有签名**（`RpcOutcome` 的落地方式、
  `GateTxnPayClient.convergeDebitStatusForSupplement` 的「要加令牌就新增重载」都是这条的产物）；
  ② 兼容壳（`updatePaySignDisplayAccount`）只能留、不能删；③ 版本号相同意味着**镜像里的 class 与本机 `~/.m2` 可能不一致**。
- **改完 MUST 逐个重建受影响模块镜像**：`mvn install` 只更新本机 `~/.m2`、对已在跑的 Pod 零影响。两类后果不同 ——
  给 DTO **加字段**时 Fastjson2 宽松模式**静默丢弃**目标类里不存在的字段（接入层用旧 class 一反序列化就没了，下游收到 `null`；
  已发生：IF8A-41 加 `cardType` 只重建 ticket-server，日志里 `cardType='null'`，重建 `itp/fep-app:2.0.77` 后立即正常）；
  改**行为语义**时更严重 —— ADR-D53 修两个观测切面后只重建了 `card-pool-server:1.0.17`，**其余 6 个 tracing 模块线上仍是旧切面、
  幂等兜底 catch 依然失效**。判据：「谁受影响」= 依赖该构件且真正走到那段代码的全部模块；
  **NEVER 认为「`mvn install` 过了」就等于生效**，只有重建镜像 + `kubectl set image` 才算。
- `resource/micro` **不在根 pom 聚合列表**，改后 MUST 先单独 `mvn install` 该子模块再构建业务模块。

## 附二.14 代码里保留的两处一行式护栏（NEVER 删）

删这两行会**直接诱发回退到已知事故写法**，因此本次刻意留在代码里，与本节形成双写：

| 文件 | 保留内容 | 删掉的后果 |
|---|---|---|
| `resource/micro/sql-datasource/src/main/resources/sql.properties`（`wall.enabled=true` 上方一行） | `# WallFilter 拦 SQL 注释与恒真条件；NEVER 放宽 wall.config.comment-allow，详见 docs/architecture/common-components.md` | 下一个人为了让带注释的 SQL 跑通，最省事的动作就是加一行 `comment-allow=true`，**全局降低注入防护等级** |
| `resource/micro/web/.../monitor/trace/MapperAspectToTrace.java` 与 `ServiceAspectToTrace.java`（`around` 方法上方各一行） | `// NEVER 回退成 observation.observe(Supplier)：会把异常包一层、幂等兜底 catch 全部落空（ADR-D53）` | `observe(Supplier)` 写法更短、看着更「干净」，回退后**只在打开 tracing 的模块上炸**，单测与编译都发现不了 |

## 矛盾与待裁决

1. **`stat-view-servlet` 默认开启 + 弱口令**（`sql.properties`：`enabled=true`、`login-username/password=admin`、
   `reset-enable=true`、`allow=` 为空即不限 IP）。与 security-sensitivity.md（已删除） §一「敏感配置」冲突，**上线前 MUST 处置**（关掉或按 IP 白名单 + Secret 注入）。
   本次只迁移注释、**未改任何配置值**。
2. **`ReconClient.java` / `ReconExportClient.java` 的 `X-Recon-Token` 已整段删除**（用户 2026-09-11 要求，开发测试阶段），
   与新增状态变更型接口 MUST 有鉴权冲突；恢复时**两端 MUST 同时改回**。属待办、不是断言对象。
3. **`web.properties` 的虚拟线程默认 `true` 与 ojdbc8 的 `synchronized` 天然冲突**：现有缓解是三层
   （`ReadTimeout=10s` / `query-timeout=30s` / `remove-abandoned-timeout=300s` + `docker/Dockerfile` 的 `JAVA_TOOL_OPTIONS`），
   但**根因（JDK 21 无 JEP 491）无法在本项目内消除**。是否给重 DB 模块单独关掉虚拟线程，**尚未裁决**。
4. **`rabbitmq-adaptor` 留着不删**：`src/main/resources/mq.properties` 含主机与 guest 口令，源码与 properties **零注释**、
   没有任何说明它为什么还在。建议整体移除（AGENTS.md 已写「NEVER 在业务模块引用」），**删不删待裁决**。
5. **`persistent.properties` 的 `mapper-locations` 默认值（`/mappers/`，复数）与多数模块实际目录（`/mapper/`，单数）不一致**，
   靠各模块覆盖生效。是「统一成一个」还是「保持各自覆盖」**待裁决**；在裁决前 **NEVER 只改公共默认值**（会打挂所有没覆盖的模块）。
6. **`countTransList` 的裸标量契约**（`GateTxnPayClient.java`）没有编译期保护，只靠「异常消息里带上对端原文」兜。
   要不要给它套 `CommonResult` 属**跨模块契约变更**，需与 gate-txn-pay / trans-query 两侧一起定，**待裁决**。

## 墓碑清单（迁出后仍需盯的「NEVER 回退」条目）

「墓碑」= 注释形态为「NEVER 回退 / NEVER 改回 / 那版口径已作废 / NEVER 加回」的条目，作用是拦住一次**已被实证推翻**的改动。
本次从三个模块迁出 **12 条**：

| 条目 | 曾经的错写法 | 判据来源 |
|---|---|---|
| 观测切面 `observe(Supplier)` | 异常包一层 ⇒ 幂等兜底失效 | ADR-D53（已留一行式护栏） |
| `ProxyWebClient` 覆写 `getResponseTimeout()` 读实例字段 | 父类构造期读到恒 0 / null | `ProxyWebClient.java` |
| `CardTypeMapping` 日票 `05` / NFC `03`+`04` 单值映射 | 恒命中 0 行且 `retCode=0000` | 2026-09-10 订单 `GT20260910164625246000013` |
| `TripDataDTO` 把 `OVERTIME_AMOUNT` 映射到 `totalDiscount` | 12 元超时费显示成「已优惠 12 元」 | 2026-09-10 校准 |
| `AdviceOptEnum` 把 `020` 当「更宽松版 005」进资金白名单 | 给没进过站的卡补出站、自相矛盾 | 用户 2026-09-14 第三次裁决 |
| `NotifyVerifyResultRespDTO` 回填 `ACTUAL_TIMES` | `-99`（不限次）透到 APP 与 `COUNTING_TIMES` | 2026-09-10 订单 `GT20260910143159899000084` |
| `UpdatePhoneReqDTO` 用 `newMsisdn` | 自造命名、与规范不符 | 2026-09-09 与规范核对 |
| `ParaClient` 零字段 DTO 直接 `bodyValue(request)` | `UnsupportedMediaTypeException`、请求发不出去 | `ParaClient.java` |
| `AlipayAccountClient` 只读 `service.account.url` | ticket-server 一直打错目标 | 2026-09-11 定位 |
| `ReconExportClient` 给 baseUrl 填占位地址 | 日志出现双份 URL、误导排查 | 2026-09-11 端到端实录 |
| `ReconExportClient` 删掉写死的 `X-Vlogs-Capture: 1` | 三个源的 INFO 一条都查不到 | 2026-09-14 实测 |
| `GateTxnPayClient.countTransList` catch 住返 0 | 分页总数恒 0、APP 只剩第一页、无一条错误日志 | `GateTxnPayClient.java` |
