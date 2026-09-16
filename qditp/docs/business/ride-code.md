---
业务域: 乘车码与过闸票卡状态
模块: ticket-server, fep-dev-server, industry-data-server
---

# 提示词：乘车码 / 二维码过闸

## 何时读本文件
乘车码码体生成、离线码、闸机检票状态流转、自助补站、行程查询、票卡分析（IF5A）相关改动。

## 模块与端口
- **ticket-server** 9103 — 乘车码状态与交易明细中心（本域核心）
- **fep-dev-server** 9104 — 闸机（AGM）设备前置，**无数据库**，全部 RPC 出站
- **industry-data-server** 9105 — 行业卡数据组装与签名调用
- 依赖 **acc-security-server** 9012 做签名（`SecurityClient` → `/ci/itp/requestSignInsData`）

## 接口清单

**fep-dev-server** `FepAgmController`（前缀 `/ci/agm`）
- IF1A-01 `/notiVerifyResult`（闸机检票结果）——**应答只有 `retCode` + `retMsg`**（规格 §4.1.4），返回类型 `NotifyVerifyResultAckDTO`，详见下方「编码约束」
- IF1A-02 `/requestSynKeyList`（密钥同步）
- IF1A-03 `/deviceHeartbeat`、`/notiDeviceHeard`
- IF1A-04 `/requestQrCodeStatus`（票卡状态查询）——应答 6 字段：`retCode`/`retMsg`/`itpUserId`/`cardId`/`lastTicketStatus`/`lastHandleDateTime`
- 分派：`FepAgmController` 直接注入 `GateTransactionHandler` / `KeySyncHandler` / `QrCodeStatusHandler`
  —— **中间那层零行为的 `DevService` / `DevServiceImpl` 已于 2026-09-14 删除**（三个方法各一行 delegate，
  且把三件无关的能力绑在同一个接口上），**NEVER 加回**。同批把 `GateTransactionHandler` 589 行按变化原因拆成
  编排（本类）+ `AlipayIndustryDetailAssembler`（支付宝 21 键）+ `GateTxnPayRequestAssembler`（扣费入参 + 站名回填）
  + `DeviceUserIdCodec`（itpUserId 进制与补位），**判断逻辑逐行未改**

**ticket-server**
- `TicketAgmController`（`/ci/agm`）：IF1A-01 `notiVerifyResult`、`queryCardStatus`、IF5A-01 `requestCardDataAnalyse`、IF5A-03 **`requestCardDataUpdate`**（AGM 侧写法，`TicketAgmController.java:124`）
- `TicketRideStatusController`（`/ci/app`）：`registerRideStatus`、`queryQrCodeStatus`、IF1A-01 `notiVerifyResult`、IF8A-29 `queryUserItinerary`、`queryEntryDevice`(GET)、`queryFirstEntryTxn`
  —— **本类只做路由，分派到三个服务**：前两项 + IF8A-29 走 `ridestatus/TicketRideStatusService`，`queryQrCodeStatus` / `notiVerifyResult` 走 `gate/AgmRideStatusService`，后两项走 `entrytxn/EntryTxnQueryService`（2026-09-14 收口，见「编码约束」）
- `TicketSupplementController`（`/ci/app`）：IF8A-04 `requestExcessFare`、IF5A-01 `requestCardDataAnalyse`、IF5A-03 **`requestUpdateCardData`**（APP 侧写法，注意与 AGM 侧词序不同）
- `TicketTransController`（`/ci/app`）：IF8A-05 `requestTransList`、IF8A-41 `requestTransStatistics`、IF8A-34 `requestTransDetail`
  —— ⚠️ **这三条 URL 存在逐字相同的第二实现**：`trans-query-server`（9113，1.0.4）的 `transquery/controller/ci/app/TransQueryController`。二者**并存、尚未替换**：`trans-query-server` 已出镜像，但**未接线**（无任何模块配置其服务地址，`fep-app-server` 的 `service.ticket.url` 仍指向 ticket-server，全项目无调用方）。定位实现 **MUST** 以「模块 + URL」为准（AGENTS.md §2.2.1）；**改这三个接口前 MUST 先确认当前生效的是哪一侧，NEVER 只改一处就认为改完**。该侧实现为只读查询，其中 IF8A-34 含**归属校验**（订单 `thirdUserId` 必须与请求一致），NEVER 删那段校验。
- `AlipayTripController`（`/ci/channel`）：`findTravelList`、`findTravelDetail`
- 运营端：`QRCodeRideStatusPageController`（`/page/ride-status`）、`QRCodeTxnDetailPageController`（`/page/qrcode-txn-detail`）

**industry-data-server** `IndustryDataController` — `POST /ci/industry/buildCardData`（无接口编号标注）

## 核心流程
1. **码体生成（IF8A-03 / IF8D-03）**：`fep-app-server/.../service/impl/IndustryDataServiceImpl.java`
   `AccountClient.queryUserInfo` → `TicketClient.queryQrCodeStatus` → `IndustryDataClient.buildCardData`（拼装+签名已下沉 industry-data-server）
   - 离线码固定 `signChannelCode="17"`
   - 已进站态只下发出站码；非进站态生成 entry+exit 双码并对 `txnSeq` +1
   - 行业卡取值（`IndustryCardDataServiceImpl`）：**票种与有效期是可配置项**，`@Value("${industry.ticket-type:0441}")`（:35）、`${industry.timestamp-expire-hours:4}`（:38）；发行渠道 `01` 是入参为空时的兜底默认（`normalizeHex(issueChannelCode,2,"01")`，:96），**不是硬编码固定值**；只有时间戳基准 `BASE_TIME=2000-01-01` 是常量。
2. **过闸主流程**：`ticket-server/.../ticket/gate/GateTicketHandler.java`
   校验当前状态 → 解析真实卡类型 → 回填上次交易字段 → insert `QRCODE_TXN_DETAIL` → **CAS upsert** `QRCODE_STATUS`（`GateTicketWriter`，见「编码约束」CAS 那条）→ 日票出站调 daily-ticket `markUsed` → 推送行业数据 / 支付宝行程
3. **协作类**：`AgmRideStatusServiceImpl`、`GateCodeStatusResolver`、`GateTxnAssembler`、`GateCardTypeEnricher`、`GateDailyTicketCoordinator`、`GateResponseAssembler`、`TicketRideStatusServiceImpl`、`MemberItineraryAssembler`、`EntryTxnQueryService`、`CardDataAnalyseHandler`、`CardDataUpdateHandler`、`SupplementStateRules`、`ExcessFareHandler`、`TransListQueryHandler`、`TransStatisticsQueryHandler`、`TransDetailQueryHandler`、`TransQueryParamNormalizer`、`TransRecordAssembler`、`MerchantPartyResolver`、`StationNameResolver`、`AlipayTripHandler`、`AppNotifyServiceImpl`

## 包结构（2026-09-14 分包后，改动前 MUST 按此定位）
`ticket-server` 原先 5 类职责平铺在 `service.impl`，已按职责簇分包（**只挪位置 + 改包声明，URL 与类名一个都没改**）：

```
com.chinasofti.huateng.ticket
├── gate/        过闸主链路（2026-09-14 由 888 行的 GateTicketHandler 拆成一编排 + 五协作者）：
│                AgmRideStatusService(+Impl，本包唯一对外入口)、GateTicketHandler(仅编排)、
│                GateCodeStatusResolver(状态码纯函数)、GateTxnAssembler(明细/状态组装)、
│                GateCardTypeEnricher(账户域富化+HCE回写)、GateDailyTicketCoordinator(日票三次交互)、
│                GateResponseAssembler(应答装配)、GateTicketWriter(事务边界)、
│                GateTxnPayRequestAssembler、AlipayIndustryDetailAssembler
├── supplement/  补站：SupplementService(+Impl，本包唯一对外入口)、CardDataAnalyseHandler(IF5A-01)、
│                CardDataUpdateHandler(IF5A-03)、ExcessFareHandler(IF8A-04)、SupplementStateRules(规则表)、
│                SupplementFareQuery、SupplementGateRequestAssembler、SupplementRequestLedger、SupplementCodec
├── ridestatus/  乘车码注册与状态查询：TicketRideStatusService(+Impl)、MemberItineraryAssembler
├── notify/      两条外发链路（2026-09-14 由 425 行的 AppNotifyServiceImpl 拆开）：
│                AppNotifyService(+Impl，仅异步提交与分派)、IndustryDataNotifier(生码+推APP网关，判retCode)、
│                AlipayTripNotifier(经 ParaClient 查线路+推支付宝，只判HTTP)、NotifyFormRequestFactory(form-data骨架)
├── query/       APP 账单查询（2026-09-14 由 627 行的 TransQueryHandler 按入口拆开）：
│                TicketTransService(+Impl，本包唯一对外门面)、TransListQueryHandler(IF8A-05)、
│                TransStatisticsQueryHandler(IF8A-41)、TransDetailQueryHandler(IF8A-34)、
│                TransQueryParamNormalizer(包内共享入参归一化)、TransRecordAssembler
│                （2026-09-14 删除中间模型 TransListEntry：214 行 / 65 getter 与两个上游 DTO
│                  1:1 同形、且两个模块各存一份逐字节相同的副本；assemble 改为直接吃
│                  GateTxnPayListDTO + PayTxnDetailDTO，trans-query-server 同批删除，NEVER 加回。
│                  同批补了 TransRecordAssemblerTest：金额按分 / 扣款结果值域 / pay==null 三组）
├── station/     下沉共享：StationNameResolver（query/alipay/ridestatus 三方共用）
├── merchant/    下沉共享：MerchantPartyResolver + MerchantParty（gate/query 两方共用；
│                  MerchantParty 是三字段 record，商户号一对的唯一出口，ADR-D81）
├── entrytxn/    进站交易只读查询：EntryTxnQueryService（服务 fep-dev / gate-txn-pay）
├── alipay/      AlipayTripHandler
├── recon/       ReconExportController / Service / Mapper（对账代码保留未删，NEVER 删）
└── controller/  TicketAgmController、TicketRideStatusController、TicketSupplementController、
                 TicketTransController、AlipayTripController、page/*
（config / constant / entity / mapper / model 未动）
```

编码约束：
- **`notify/` 的两条外发链路已拆开，判定口径 NEVER 互相「统一」（ADR-D61）**：`IndustryDataNotifier` **MUST 显式判 `retCode`**（实测对方对无效卡号返 `7004` 而 HTTP 仍是 200，只看 HTTP 会把失败记成成功）；`AlipayTripNotifier` **只判 HTTP 2xx**（对方无业务码约定）。两者只共享 `NotifyFormRequestFactory` 的 8 个 form-data 字段与 `appNotifyExecutor` 线程池。**`AppNotifyServiceImpl` 只做异步提交 + 分派，NEVER 在里面加开关判断、URL 选择或应答判定**。
- **两条链路都没有幂等键，因此 NEVER 改回同步**：`pushAlipayTripData` 曾是同步的，外部 HTTPS 往返最坏 20s 直接加在过闸应答上，闸机超时即重发、重发即重复推行程。两个异步任务体内 **MUST 各自 catch 全部异常只记日志** —— 异步任务抛出去没有任何人接（既不回滚也不重试），漏掉 catch 只会让异常彻底无声。
- **`gate/` 与 `query/` 已于 2026-09-14 拆到「一个入口一个类」，且 4 处绕过门面的调用点全部收口（ADR-D60）**。拆分判据不是行数而是**同一个类里住着多件互不相关的事**：`GateTicketHandler` 原 888 行混着状态码纯函数、落库组装、账户域富化、日票三次交互（三套方向相反的失败处置）、应答装配；`TransQueryHandler` 原 627 行装着 IF8A-05/34/41 三个互不调用的入口 + 两段死代码。现在 **`gate/` 对外只暴露 `AgmRideStatusService`、`query/` 对外只暴露 `TicketTransService`，包外 NEVER 再注入这两个包里的任何 Handler**。
- **三个下沉共享包 `station/` / `merchant/` / `entrytxn/` 的依赖方向是单向的，NEVER 反过来**：它们只准依赖 `rpc` / `model` / `mapper`，**NEVER 依赖 `gate` / `query` / `ridestatus` / `alipay` / `supplement` / `notify`**。建这三个包的动因就是消除横向依赖 —— `StationNameResolver` 有 3 个平级调用方、`MerchantPartyResolver` 有 2 个，放在任一方家里都会让另一方去抓别人的内部类。**NEVER 把 `entrytxn` 的两个方法并进 `TicketTransService`**：那个门面的语义是「APP 账单查询」，混入闸机辅助查询会让「谁该依赖它」重新说不清。
- **商户号（归属方 / 收款方）MUST 成对取，NEVER 分开取（ADR-D81）**：`MerchantPartyResolver` 只暴露三个出口 —— `resolveFor(rideDate)` 按乘车日期选、`oldParty()` / `newParty()` 按业务规则强制选，返回的都是不可分割的 `MerchantParty` record。四个单字段 getter 与 `shouldUseOldMerchant` 已删除 / 降为 private，**NEVER 改回 public** —— 「老归属方 + 新收单方」这种混搭在数据里**事后完全看不出来**（库里就是两个合法商户号），只有对账资金流向不上才暴露，因此靠类型让它编译期不可表达。`app.trans.merchant-change-date=20260901` 至今业务未确认（P0），这一层就是那个 P0 的放大面。
- **`GateResponseAssembler` 的「单边 / 补站 / 超时回退城交」那一支与乘车日期无关，条件里 NEVER 出现 `txnDate`（ADR-D81）**：业务规则是「新商户期的单边补站也归城交」，所以它走 `oldParty()` 而不是 `resolveFor(txnDate)`。原条件多带了 `hasText(txnDate) && !resolveFor(txnDate).useOld()` 两项，其中日期判断**是冗余的**（旧商户期本来就会从 else 分支拿到同一对城交商户号，日期只改变了打哪条日志），而 `hasText(txnDate)` 反而制造了一个缺陷：`handleDateTime` 缺失时落进 else → 拿到**新**商户号，与本规则相反。2026-09-14 经用户授权收缩为 `if (isSingleSideOrSupplement)`。**方法 Javadoc 里那句「若乘车日期早于变更日，强制使用城交商户」曾把方向写反（强制城交的恰是不早于变更日那一侧），已修正，NEVER 回退。**
- **同批删掉的死代码 NEVER 加回**：`query/TransQueryHandler` 的空方法 `enrichTradeOrderNos(List)`、无调用点的 `mergeTransRecord(List)` + `setNonNull`（详情已按 `orderNo` 直查，不存在「合并进出站两条记录」的场景）、`enrichSingleStationNames` 里一行取值不赋值的死语句；`gate/GateTicketHandler` 未被读取的 `StationInfoMapper` 字段；`query/TransMerchantResolver` 的空方法 `resolveMerchantParties(String, Object)`。
- **`TicketSupplementController` 是从 `TicketRideStatusController` 拆出的**（`requestExcessFare` / `requestCardDataAnalyse` / `requestUpdateCardData` 三个端点），**`@RequestMapping("/ci/app")` 前缀与路径全部不变**——上游 `rpc/TicketClient` 硬编码的 URL 不受影响。新增补站类端点 **MUST** 放这里，**NEVER** 加回 `TicketRideStatusController`。
- **`supplement/` 已有门面 `SupplementService`（2026-09-14 重构）**：`TicketSupplementController` 只依赖它，入参非空校验也在门面里（Controller 只做日志 + 路由）。**包外 NEVER 再直接注入 `ExcessFareHandler` / 两个 CardData 处理器**——重构前 Controller 拿前者、`gate/AgmRideStatusServiceImpl` 拿后者，「补站」这件事的边界在代码里根本不存在。**`gate/AgmRideStatusService` 那两个 IF5A 委派壳已于 2026-09-14 删除（ADR-D65），本条此前写的「NEVER 因为门面存在就删那两个方法」已作废、NEVER 回退**：`TicketAgmController` 现在直接注入 `SupplementService`（与 `TicketSupplementController` 同一写法），`/ci/agm/requestCardData*` 与 `/ci/app/**` 的 URL 一个都没变。删它的动因是解环 —— 那两个壳是 `gate → supplement` 唯一的一条边，而 supplement 反过来要调 gate 的检票编排，于是构成包级双向环，并逼得 supplement 侧只能注入 gate 的内部实现类 `GateTicketHandler`（注门面会成 Spring 构造环、启动即失败）。现在两个包恢复单向：`supplement → gate.AgmRideStatusService`（门面）。
- **原 `CardDataHandler`（714 行）已于 2026-09-14 拆解并删除（ADR-D59）**，**NEVER 重建**。现在按「建议 / 执行」分成 `CardDataAnalyseHandler`（IF5A-01）与 `CardDataUpdateHandler`（IF5A-03）；共用构件 `SupplementStateRules`（两张规则表）、`SupplementFareQuery`（票价，三条链路共用）、`SupplementGateRequestAssembler`（闸机报文骨架）、`SupplementRequestLedger`（IF5A-03 幂等台账）、`SupplementCodec`（包级私有常量与编解码）。**这些类全部包级可见，NEVER 改成 public、NEVER 把 `SupplementCodec` 搬进 `model` / `rpc` / `micro`。** `resolveAdviceOpt` 与 `isUpdateAllowed` **刻意放在同一个类里**：它们是同一张规则表的建议侧与执行侧，分开维护过就出现了「执行侧漏判 `updateType`」的绕过口子。
- **`ridestatus/` 内聚性收口（2026-09-14）**：`TicketRideStatusService` 只剩 `registerRideStatus` 与 `queryUserItinerary` 两个方法，展示字段拼装委托 `MemberItineraryAssembler`（站名解析统一走 `station/StationNameResolver`，**NEVER 在本包再写一份批量查站名**）。以下三个方法已迁出、**NEVER 加回**：`requestExcessFare` → `TicketSupplementController` 经 `supplement/SupplementService` 门面；`queryEntryDevice` / `queryFirstEntryTxn` → `entrytxn/EntryTxnQueryService`（读的是 `QRCODE_TXN_DETAIL`，与乘车码状态无关，服务对象在 `fep-dev-server` 与 `gate-txn-pay-server`）。
- **`ReconExportMapper.xml` 的 namespace 已随包名改成 `com.chinasofti.huateng.ticket.recon.ReconExportMapper`**。挪 mapper 接口 **MUST** 同批改 namespace，否则 `sqlSessionFactory` 启动即挂（表现见 AGENTS.md §5.1）。
- **`gate/GateDailyTicketCoordinator` 的三套失败处置已收成枚举 `FailurePolicy`（2026-09-14）**，三次日票交互共用 `invoke(step, logContext, policy, remoteCall)` 骨架。**新增第四次日票交互 MUST 从枚举里选一个 policy，NEVER 再手写一份 try/catch**——三套口径方向相反，散着写必被改歪：`PROPAGATE`（进站校验，可重试故障 MUST 保持异常语义，NEVER 就地转业务码，否则闸机不重试、一次抖动判死正常票）/ `PASS_THROUGH`（出站扣次，乘客已在站内、拦住等于困人，失败一律 ERROR 留证据）/ `DEGRADE`（票号查询，只影响 `ticketCode` 一个字段）。枚举只管**技术失败**（RPC 异常 / 响应为 null）；业务 `retCode` 非 0000 的措辞与后续动作三处本就不同，仍留在各调用点。回归钉子 `ticket-server/src/test/java/.../gate/GateDailyTicketCoordinatorTest.java`（16 例，含「同一个 RPC 超时在三处的结论必须不一样」）。同批钉住两条现状：`countingFlag`/`countingTimes` **只由卡种推导**，三次远端全挂时仍有值；**`markUsedOnExit` 只认 `trxType=02`，`03` 超时出站不扣次**。
- **`gate/GateCardTypeEnricher` 的签约信息字段已收成 `SIGN_FIELDS` 表（2026-09-14）**：`channel→paymentVendor` / `reqContractNo→request+response 双写` / `thirdPayId→payUserId` 三组字段此前在 account 域与支付宝账户域两条链路各写一份，**新增字段 MUST 加在表里、NEVER 只在一条链路上加**（`applyFreeRideAmountReset` 的免扣费清零就是上一次同型收口）。两条链路的差异用参数显式声明：`silentWhenBlank` 列出「本来源允许缺失」的字段，支付宝侧没有 `REQ_CONTRACT_NO` 语义，故声明 `FIELD_REQUEST_SIGN_SEQ`，否则每笔支付宝过闸都刷一条无意义 WARN。`companionFlag`（仅 account 有）与 `thirdUserId→itpUserId`（仅支付宝有）**不进表**。回归钉子 `ticket-server/src/test/java/.../gate/GateCardTypeEnricherTest.java`（14 例）。同批钉住两条语义：**account 域「返回未命中」才回落支付宝，「抛异常」直接吞掉、不回落**；支付宝侧 `cardType` 是 APP 口径 2 位码、MUST 过 `CardTypeMapping.toIssueCardType` 转 4 位。**该类的类注释此前写「本笔只是新增，切换尚未发生、原副本仍是唯一被调用的一份」，与代码完全相反（`GateTicketHandler:125/189` 是唯一调用点、handler 里既无副本也无那两个 Client），已改正、NEVER 回退。**
- 分包只做物理隔离，**没有加任何模块内的调用约束**（无 ArchUnit 门禁）。跨包直接 `@Autowired` 仍然编译通过，`gate` → `notify` / `query` → `rpc` 的现有依赖方向 MUST 保持，**NEVER 让 `query` 反向依赖 `gate`**。
- **`industry-data-server` 的 64 位码体已改成段表驱动（2026-09-14）**：`IndustryCardDataServiceImpl.BODY_LAYOUT` 是 11 个 `BodySegment(名字, 定长, 取值)` 的 `List`，**列表顺序即码体字节顺序，NEVER 调整、NEVER 插段**——闸机按固定偏移解析，错位既不报错也验不过。段长之和由类初始化时的 `static` 块校验（不等于 64 直接启动失败），拼装时逐段核对实际长度并把不符的**段名**打进 ERROR 日志（此前只能看到 64 位裸串、无法定位是哪一段）。段布局：`thirdUserId(0,8) / ticketStatus(8,2) / lastStationCode(10,4) / handleDate(14,8) / timeStamp(22,8) / ticketLogicNo(30,16) / ticketType(46,4) / transSeq(50,8) / issueChannelCode(58,2) / signChannelCode(60,2) / 固定01(62,2)`。回归钉子是 `industry-data-server/src/test/java/.../IndustryCardDataBodyTest.java`（22 例，逐段精确断言；`timeStamp` 取 `now()+expire-hours` 只能断言形态）。**已知地雷已被钉住、改前先看那条用例**：签名段为空时走「签名失败」分支却把下游的 `retCode=0000` 原样回填，于是 `retCode=0000` 与 `cardData=null` 同时出现。


## 状态机（权威枚举）
`model/.../model/ticket/enums/QRCodeStatusEnum.java`，落库字段 `QRCODE_STATUS.CODE_STATUS`：

> ⚠️ 仓库中有**两份同名枚举**：`model` 版是所有业务类实际 import 的权威版本；`ticket-server/src/main/java/com/chinasofti/huateng/ticket/enums/QRCodeStatusEnum.java` 是无人引用的副本。改状态值 **MUST** 改 `model` 版，并全局 grep 确认没有代码切换到副本。

`01` 无交易 / `02` 结束行程 / `03` 初始化 / `04` 已进站 / `05` 已出站 / `06` 超时出站 /
`08` 20 分钟内免费更新 / `09` 20 分钟内付费更新 / `10` 入站码更新 /
`70` 异常 / `80` APP 自助补出站 / `81` APP 自助补进站 / `FF` 进站失败

辅助判定：`isClosedLoop()`（02/05/06/80）、`isOpenLoop()`（04/81）。

⚠️ **状态字典不同源**：`GATE_TXN_PAY.TICKET_STATUS` 的建表注释写"04 进站失败、05 已进站"
（`gate-txn-pay-server/src/main/resources/sql/gate-txn-pay-schema.sql`），与 `QRCodeStatusEnum` 冲突。
跨这两张表判断状态时 **MUST** 分别使用各自字典，**NEVER** 直接复用同一常量。

## IF5A 票卡分析与更新（BOM 单边处理）

⚠️ **两条补站链路互不相同，NEVER 混用字段名与白名单**：
- **BOM 单边处理**：IF5A-01 `requestCardDataAnalyse` → `CardDataAnalyseHandler`，IF5A-03 → `CardDataUpdateHandler`，参数 `updateType` + `adviceOpt`，`deviceId = operaterId`
- **APP 自助补站**：IF8A-04 `requestExcessFare` → `ExcessFareHandler`，参数 `upgradeAreaType`，`deviceId = 站点码 + "36" + "01"`

`adviceOpt` 取值：`000` 无需操作 / `005` 20 分钟内免费更新 / `006` 补出站（付费更新） / `018` 补进站 / `020` 免费进闸更新（补进站方向）。
**权威字典是 `model/.../model/ticket/enums/AdviceOptEnum.java`（2026-09-14 收口）**，此前同一套码值在三处各写一份：`GateTicketHandler` 的 `ADVICE_OPT_*` 常量、原 `CardDataHandler` 的约 20 处裸字面量、`fep-dev-server/GateTransactionHandler` 的 `Set.of("005","006")`。**NEVER 再复制第四份**——第三处是防资损的白名单，三处对不上即漏扣或重扣。码值本身是与 BOM / 闸机的对外契约，**NEVER 改动**；新增取值 MUST 同时确认它归 `isSupplementEntry()` 还是 `isSupplementExit()`（两组互斥，漏归类会让 `resolveTrxType` 返 null，而错落进出站组则会走到 `shouldPay` 的跳过扣费分支）。回归钉子在 `ticket-server/src/test/java/.../supplement/AdviceOptEnumTest.java` 与 `SupplementStateRulesTest.java`。
**`020` 于 2026-09-14 补登记（ADR-D76）**：厂家原厂字典 `com.bestone.itp.bom.enumtype.CardAdviceOpt` 是 `NO_UPDATE(000) / FREE_IN_20(005) / ADD_OUT(006) / ADD_IN(018) / FREE_UPDATE(020)`，**BOM 实际上送的免费更新码是 `020`**，而枚举里原先只有 `005`，于是 IF5A-03 一路返 `8001「票卡状态不允许此操作」`、零数据落库（19:58 实测）。**`020` 的业务含义是「免费进闸更新」（用户 2026-09-15 裁决）**：乘客刷卡但**没有进站成功**，BOM 在非付费区给他补一次免费进闸，之后从侧门进入付费区。**因此 `020` 是补进站方向**：`TRX_TYPE=01`、落 `CODE_STATUS=04`、进站站取 `updateStationCode`、进站时间取 `optDate`、金额 0，**且不在跳过扣费白名单里** —— 这次补出来的进站记录就是乘客随后真实出站的计费依据，跳过扣费等于整程免费（资损）。
> ⚠️ **2026-09-14 的旧口径「`020` 是更宽松版 `005`、方向补出站、落 `08`、算 BOM 已结清所以闸机跳过扣费」已于 2026-09-15 整段作废，NEVER 回退。** 那个口径实测就是坏的：`08` 不是开环态，乘客从侧门进付费区后真实出站**算不出票价、实测扣 0 元**。方向判据只在 `AdviceOptEnum` 的两组互斥方法里（`isSupplementEntry()` = {018,020}、`isSupplementExit()` = {005,006}），`CardDataUpdateHandler.resolveTrxType` 只问枚举，**NEVER 在别处写码值字面量**。

`020` 与 `018` **同为补进站方向，区别只有付费区标**：`018` 要求 `updateType=01`（付费区），`020` 要求 `updateType=00`（非付费区）。`020` 的状态白名单仍是「任何已登记取值一律放行」（`canFreeUpdateAnyRegisteredStatus`，含已出站的闭环 `02/05/06/80`、`08/09`、开环 `04/81`、`10`，以及**从未有过行程的新卡 `03` 与 `01`/`FF`/`70`**；第三次裁决原话「020 连新卡也该放行」），且不复核 `gateInTime` 时间窗（`checksFreeWindow=false` —— 补进站方向本就不适用「进站早于 20 分钟」这条拒绝理由）。于是 ITP 侧 `020` 只剩两条闸口：**`updateType=00` 与库内状态不是脏值（解析得出枚举）**；「这次更新该不该做」的实质把关方是 BOM。**NEVER 把 `018` 的 `canSupplementEntry` 套给 `020`** —— 那条排除开环 `04/81`，而「刷卡没进成」的卡完全可能已经是 `04`。**也 NEVER 把这条宽判据复用给 `005`/`006`/`018`**（005 会失去 20 分钟上限、006 会失去「按进站站报价」的前提、018 会丢掉付费区限制）。另注意 Java 常量名与厂家错位：本枚举 `FREE_UPDATE` 是 `005`、`020` 叫 `FREE_UPDATE_020`；**NEVER 把 `005` 改名成 `FREE_IN_20` 再让 `FREE_UPDATE` 指向 `020`** —— 现有调用点照旧编译、含义静默全变。**IF5A-01 建议侧已于 2026-09-15 补出 `020`**（见下文「IF5A-01 建议操作」第一条）：非付费区且卡上没有未闭合进站时给 `020`，此前一律给 `000`、等于建议侧对「刷卡没进成」这个场景永不给建议。**「建议侧仍只给 005，未改」这句已作废，NEVER 回退。**
`updateType`：`01` = 付费区（闸机内侧），`00` 或其他 = 非付费区。

**`ExcessFareHandler` 的允许补站类型白名单 MUST 用 `Set` 的相等语义，NEVER 用 `String.contains` 做子串匹配**（2026-09-14 代码审查修复）。原实现把白名单写成逗号分隔字符串（`"02,03,04"` / `"01"`）再 `contains(upgradeAreaType)`，于是 `"02,03,04".contains("0")`、`"01".contains("1")`、`","`、`"2,0"` **全部为 true**，非法的 `upgradeAreaType` 会被原样组装进 `trxType` 发给闸机（只靠闸机侧兜底）。现在 `AllowedTypesResult.allowedTypes` 是 `Set<String>`（`Set.of("01")` / `Set.of("02","03","04")` / `Set.of()`）。上游 `SupplementService.requestExcessFare` 的 `hasText` 校验只挡住了空串与 NPE，**挡不住子串穿透**（该校验 2026-09-14 先从 `TicketRideStatusServiceImpl` 移到 Controller、再随门面重构落到 `SupplementServiceImpl`，位置变了两次、**结论没变**）。

**IF5A-01 建议操作**（`SupplementStateRules.resolveAdviceOpt`）：
- 闭环 `02/05/06/80`、初始化 `03`、更新态 `08/09`：付费区 → `018`，**非付费区 → `020`**（2026-09-15 补，此前一律 `000`）。判据是「卡上查不到未闭合的进站，而乘客人却在闸外找 BOM」= `020` 的场景「刷卡了但没进站成功」；BOM 不会替没问题的乘客发起 IF5A-01，「来分析」本身就是异常信号。**NEVER 退回 `000`** —— 那等于建议侧对这个场景永不给建议、`020` 只能靠 BOM 自行发起。
- **开环 `04/81`：付费区 → `000`；非付费区且 `isWithinFreeWindow(gateInTime)` → `005` 免费更新，超 20 分钟 → `006` 付费补出站**（2026-09-09 修复，此前无条件 `006`）。**NEVER 把 `020` 挂到开环上** —— 这两个状态库里已有一次未闭合进站，`020` 会把进站站与进站时间覆盖成 BOM 当前站点，**原行程的计费基准就丢了**。
- **但进站站未知（`FFFF` / 空）时 NEVER 给 `006`，改返 `000` + `WARN_STATION_UNKNOWN`**（2026-09-14 / ADR-D59）：报价函数会兜底 0 元，而执行侧要用真实进站站查票价、必然失败，那个 `006` 是**注定执行不下去的建议**。
- `10` 入站码更新：付费区且进站站未知 → `018`，否则 `000`；非付费区且 20 分钟内 → `005`
- 其他状态兜底 `000`；**库内 `CODE_STATUS` 有值但不在枚举里时直接拒绝整笔请求**（不再静默提升成 `03`，见下条）
- **建议侧给出的 `020` 集合是执行侧 `canFreeUpdateAnyRegisteredStatus` 的真子集**，因此不会出现「IF5A-01 建议了、IF5A-03 又拒掉」。这条对称性由 `SupplementStateRulesTest.adviseFreeEntryUpdateWhenNoOpenTripInFreeArea` 末尾的循环钉住。

**IF5A-03 执行白名单**（`SupplementStateRules.isUpdateAllowed`，白名单不是黑名单）：
- `018`：闭环 `02/05/06/80`、`03`、`08`/`09`、`10` **一律要求 `updateType=01`**（2026-09-14 / ADR-D59 修复：`03` 此前无条件放行，而建议侧对「`03` + 非付费区」只给 `000`，不对称即绕过口子 —— BOM 直送 `018 + updateType=00` 就能给从未进站的新码补进站。**NEVER 回退成无条件 true**）
- `006`：开环 `04/81` 或 `10`，且 `updateType=00`。**不设时间下限**（006 本就是超时分支）
- `005`：开环 `04/81` 或 `10`，且 `updateType=00`；**且 `isWithinFreeWindow(gateInTime)` 必须成立**（2026-09-10 新增；此前时间判定只在 IF5A-01，执行侧不复核，等于「先拿 005、隔 40 分钟再提交」照样免费出站）
- `020`：**与 `018` 同为补进站方向，只差付费区标**（见上文 ADR-D76）——要求 `updateType=00`（非付费区），不复核时间窗（`checksFreeWindow=false`），状态白名单是 `canFreeUpdateAnyRegisteredStatus`，**任何已登记的 `QRCodeStatusEnum` 一律放行**：已出站的闭环 `02/05/06/80`、`08/09`、开环 `04/81`、`10`、**新卡 `03`**、`01`、`70`、`FF` 全允许。唯一还会拒的是「库内 `CODE_STATUS` 是脏值、解析不出枚举」。**NEVER 把 `018` 的 `canSupplementEntry` 套给 `020`**（那条排除开环 `04/81`，而「刷卡没进成」的卡可能已是 `04`），**也 NEVER 把这条宽判据复用给 `005`/`006`/`018`**
- `codeStatus` 解析为 null（脏值）时**一律拒绝**

**`ticket.default-code-status` 启动即校验**（2026-09-14 / ADR-D59）：解析不出 `QRCodeStatusEnum` 直接启动失败。默认值 `03` 恰在枚举里，因此**只要 K8s env 把它覆盖成枚举外的值，每笔 IF5A-01/03 都 NPE**，而编译、单测、启动全都发现不了。**NEVER 改成「解析不出就静默兜底 03」。** 同时区分两种空：列为空 = 正常新卡按默认态处理；列有值但未登记 = 脏数据，拒绝。

**IF5A-03 幂等靠 `QRCODE_SUPPLEMENT_REQUEST` 表**（2026-09-14 / ADR-D59，唯一索引 `UK_QSR_CARD_SEQ_ADVICE` = `CARD_ID + TXN_SEQ + ADVICE_OPT`）。原实现是「下发闸机前再 select 一次比对快照」，纯 TOCTOU：两条并发同卡请求各自比对通过、各下发一次，`006` 分支即两次扣费。现在下发前先 INSERT 声明，冲突时只有 `HANDLE_STATUS='REJECTED'` 允许重新声明（`PENDING`/`SUCCESS`/`UNKNOWN` 一律返 `8305`）。闸机超时 / 无响应时该行留 `UNKNOWN` 作证据（此前只有一行 `log.error`、库里零痕迹）。**`selectUnknown` 已就绪但目前没有调度方** —— ticket-server 没有 `@EnableScheduling`，接线 MUST 建在 web-admin `sys_job`。

编码约束：
- **`005` / `020` 全程不产生金额**，改这条链路 **MUST** 保持三处都不算钱：IF5A-01 的 `transAmount` 仅在 `adviceOpt` 含 `AdviceOptEnum.PAID_UPDATE`（006）时调 `calculatePayAmount`、IF5A-03 的票价仅在 `AdviceOptEnum.PAID_UPDATE.matches(adviceOpt)` 时算、`buildGateRequest` 的 `overtimeAmount` 硬编码 `"0"`。**注意 `020` 的「不产生金额」只限本笔补进站**：它落的是开环 `04`，乘客随后真实出站时 **MUST 正常扣费**，**NEVER 把这条与「跳过扣费」混为一谈**。
- **BOM 补出站一律不触发 gate-txn-pay 扣费**（2026-09-10）。`fep-dev-server` 的 `shouldPay` = 出站交易 **且 `adviceOpt` 命中白名单 `BOM_SUPPLEMENT_EXIT_ADVICE_OPTS`**（2026-09-14 起该常量直接取 `AdviceOptEnum.SUPPLEMENT_EXIT_CODES`，即 **`{005,006}`**，**NEVER 在 fep-dev 里改回裸字面量**——它必须与 ticket-server 判「BOM 是否已收过出站费」的那份永远相等；**「fep-dev 经 `model` 与 ticket-server 共享这条资金判据」的耦合方向已由用户 2026-09-14 明确接受，见 ADR-D58，NEVER 再当成待确认项重开**）；未知取值打 WARN 后**照常扣费**。**`020` 已于 2026-09-15 随方向反转从这份白名单移出，NEVER 因为它名字里有「免费更新」就加回来** —— 它落的是进站记录，留在白名单里等于乘客拿着这张 `04` 出站时不扣钱、整程免费（资损）。**NEVER 改成「`adviceOpt` 非空即跳过」**——那是黑名单，违反 §5.2，将来别的链路带上未知 `adviceOpt` 会静默跳过真实出站扣费（资损）。判据成立的前提是 **`adviceOpt` 只有 IF5A-03 链路上送**（真实闸机报文恒 `null`，APP 自助补站用 `excessFareType`）——**改 `buildGateRequest` 或 `ExcessFareHandler` 时 MUST 保持这个不变量**，否则会误伤真实检票的扣费。业务口径：006 的钱由 BOM 现场收（用户 2026-09-10 裁决）。此前 005/006 都会落 `GATE_TXN_PAY` 且 `ORDER_EXP_TYPE=0`，与正常出站同形。2026-09-10 18:51 已用 `006` 端到端复测：明细表落 `RESERVE1='006'` 的出站记录，`GATE_TXN_PAY` 零新增。
- **`buildGateRequest` 把 `adviceOpt` 写进 `reserve1`**：`QRCODE_TXN_DETAIL` 没有 `EXCESS_FARE_TYPE` / `ADVICE_OPT` 列（实测 21 列），借 `RESERVE1` 零 DDL 标记补站。**NEVER 把 `reserve1` 挪作它用**。
- **`handleCardDataUpdate` 与 `AgmRideStatusServiceImpl.requestCardDataUpdate` 都不带 `@Transactional`**（2026-09-10 摘除）。该方法一条写 SQL 都没有——状态推进在 fep-dev 回调 `/ci/agm/notiVerifyResult` 那个独立请求里；加事务只会把连接持有到三个 RPC 返回为止。**NEVER 加回来。**
- **幂等命中返回 `8305`（`CARD_STATUS_CHANGED`），NEVER 返回 `0000`**：BOM 按 `0000` 判成功会去写卡，而 ITP 本次并未执行更新。
- **状态 `10`（入站码更新）没有任何写入路径**：写 `CODE_STATUS` 的只有 `TicketRideStatusServiceImpl:81`(03)、`ExcessFareHandler:71`(03)、`GateTicketHandler:348`(04/05/06/08/09/FF/01) 与运营端人工改（`QRCodeRideStatusPageController`）。2026-09-09 实测生产库 `10` 为 0 行。**因此 NEVER 把新功能的前置条件挂在 `10` 上**——那等于永不触发；这正是免费更新长期不可用的根因。
- **本节两张规则表甲方规格里没有对应条文**（§5.3.1 只写「ITP 根据卡号与付费区标分析可做哪些操作」，未列举白名单），属实现方自定义。改动只能验「与代码设计一致」，**NEVER** 据此声称「符合需求」。
- ⚠️ 规格自相矛盾（待甲方确认）：IF5A-01 应答的 `adviceOpt` 取值表里**没有「20 分付费更新」**，但同表 `transAmount` 说明写的是「20分付费更新 金额」；且 §5.3.1 正文用的是另一套编码（`00/01/02/03/04`），代码按接口表的 `000/005/006/018` 实现。
- **IF5A-01 的 `msisdn` / `cardIssueDate` 由 `queryUserInfo` 内部写入 response，NEVER 在其后用局部变量回写**——曾因此把 account-server 查到的值无条件覆盖成空串（2026-09-10 修复）。`queryCardTypeByCardId` 的返回已带 `msisdn` + `regTms`，非支付宝发行方**不需要**二次调 `queryUserInfo`。
- IF5A-03 **无验签、无归属校验**，是状态变更 + 涉及资金的接口，见 `docs/testing/bom-oneside/02-阻塞项与缺陷候选.md` B1。用户 2026-09-10 裁决本轮不加（会打断现网 BOM 联调），**上线前必须补**。
- **BOM 侧入口是 `face-pay-server`（58101）的 `/itpbom/ci/bom/**`，ticket-server 侧命中 `/ci/app/**`**（`rpc/TicketClient.java:147,156` 硬编码）。`TicketAgmController` 的 `/ci/agm/requestCardData*` 是同名旁路，**实际链路不经过它，那套入参校验不生效**。详见 `docs/testing/bom-oneside/00-链路事实与状态机.md`。

## 数据表
`QRCODE_STATUS`、`QRCODE_TXN_DETAIL`、`QRCODE_SUPPLEMENT_REQUEST`（IF5A-03 补站台账，2026-09-14 新建）、`STATION_INFO`

- ⚠️ **`STATION_INFO` 是本模块自建的重复维表，不是 para-server 的参数表**，两者是两张表：para 域 owner 的 `TBL_STATION_INFO` 带 `PARA_VER_NO` 版本列、列名 `STATION_NM` / `OWNER_LINE_ID`、数据来自 ACC 参数文件导入；本模块这张只有 4 列（`STATION_CODE` / `LINE_CODE` / `STATION_NAME` / `STATION_EN_NAME`）、**无版本列**，`sql/station-info-schema.sql` 用 `MERGE` 硬编码灌了约 190 个车站，**参数版本推进时它不会动**。
- **2026-09-14（2.1.74）已把唯一的 Java 侧读点改走 para-server**：`notify/AlipayTripNotifier.resolveLineCode` 原先读本表取 `transLine`，现改为 `ParaClient.requestStationLineInfo`（与同模块 `gate/AlipayIndustryDetailAssembler.queryStationLineInfo` 统一，那边一直就是这个写法）。配套删除 `mapper/StationInfoMapper.java` + `.xml` + `entity/StationInfo.java`（已无引用），**NEVER 加回**。修的是一个静默错误：本表与参数版本脱钩后，`resolveLineCode` 的兜底会**把车站代码当成线路代码推给支付宝**、只留一条 warn。那条兜底本身是已联调通过的契约，**NEVER 改成 null、NEVER 改成拒推**（该链路只判 HTTP 2xx、失败不重试），只能让查询本身准。
- **表本身保留未删**，仍被两处 SQL 的 `LEFT JOIN` 使用：`QRCodeTxnDetailMapper.xml`（账单列表取当前站 / 上一站名）与 `ReconExportMapper.xml`（对账明细的线路段；gate-txn-pay / collect-pay 的同名 mapper 也 join 这张表）。**这三处仍是未收口的耦合**，要迁 MUST 连对账 SQL 形状一起评估，不能只改 Java。

## 幂等
- `QRCODE_TXN_DETAIL` insert 捕获 `DuplicateKeyException` 视为重复上送，不中断流程（`GateTicketWriter`）
- `QRCODE_STATUS` 走 `MERGE INTO`：过闸链路用 **CAS 版 `upsertWithCas`**（`ON` 条件带 `T.TXN_SEQ = #{expectedTxnSeq}`），开卡复位与运营端仍走无条件 `upsert` / `updateCodeStatus`（`QRCodeStatusMapper.xml`）
- ⚠️ CAS **只挡住「基于同一个 `TXN_SEQ` 的第二次推进」**，`USE_COUNT` / `TXN_SEQ` 仍是相对增量，**不等于全链路幂等**。详见「编码约束」CAS 那条与相对增量那条。
- 无 Redis 锁。新增写入 **MUST** 沿用"唯一约束 + MERGE"模式

## 编码约束
- **码体入参口径两条路径 MUST 一致**（2026-08-30 对齐）。生码有两条链路，都调 `industry-data-server` 的 `buildCardData`：
  - 路径 A「APP 主动取码」IF8A-03 `fep-app-server/.../IndustryDataServiceImpl#buildCardDataRequest`
  - 路径 B「过闸/补站后反向推码」`ticket-server/.../AppNotifyServiceImpl#buildCardDataRequest`
  两侧曾各取一套来源，导致同一张码刷码前后解析出的**票种位与签约渠道位不同**。现约定：
  - **票种位（cardType）取上游上送的原始值**，**NEVER** 用 account 开户卡种覆盖后的值。路径 B 因此在
    `GateTicketHandler#handleGateTransaction` 调 `GateCardTypeEnricher#applyActualCardType` **之前**留存 `gateCardType` 并透传给
    `AppNotifyService#notifyVerifyResult`（第三个参数）。改这条链路 **MUST** 保持这个留存动作，
    `applyActualCardType` 会就地改写 `request.cardType`。
  - **签约渠道位取 account 的 `USER_ITP_REG_INFO.CHANNEL`**（路径 B 用 `applyActualCardType` 写入的
    `request.paymentVendor`，经 `SignChannelUtils.resolve`），**NEVER** 直接用闸机上送的 `signChannelCode`——
    闸机侧该字段承载**票种语义**（`GateTicketHandler:106/251` 用它判日票 12~15、`:574` 判离线码 17），
    与账户签约渠道不是同一个字典。account CHANNEL 缺失时才回退闸机值并打 WARN。
  - **例外**：`issueChannelCode` 的爱山东（044A）判定仍用 account 卡种，**NEVER** 改用上送值——
    闸机对鲁通码可能仍上送 0441，用上送值判会漏掉「发行渠道强制 01」。
  - **反向推码一律推在线码，NEVER 让闸机上送的 `17` 透到码体里**（用户 2026-09-11 裁决「推送要推送在线码」，
    `ticket-server` 已按此改，`AppNotifyServiceImpl#resolveSignChannelCode` 只认 account CHANNEL，缺失才回退闸机值）。
    **此前的实现是「闸机上送解析为 17 就沿用 17」，已废弃**：APP 用过一次离线码（IF8D-03 硬编码 17）后，
    闸机每次都把 17 回传，反向推码就一路保留 17，**即使网络恢复推给 APP 的仍是离线码、永不自愈**，
    只能靠 APP 重新调 IF8A-03 才回到在线渠道。2026-09-11 实测：卡 `0426090942000095` 10:46/10:47 两趟
    `SIGN_CHANNEL_CODE=03`，14:09 起 8 笔全部 `17`（全表 `17` 只有这 8 行、都是当天），而 account CHANNEL 一直是 `03`；
    fep-app 日志显示 APP 在 14:27:27 先调 IF8A-03 拿到 `...0103 01...`、200ms 后又调 IF8D-03 拿 `...0117 01...`，
    离线码后到、覆盖了在线码。**离线码标识没有丢**，仍由两处承载：IF8D-03 生成时硬编码 17、
    IF1A-01 应答的 `offlineFlag` 仍按闸机上送值判 17（`GateResponseAssembler#resolveOfflineFlag`）——改动 **MUST** 保持这两处不变。
  - 离线码路径 IF8D-03 的 `signChannelCode` 仍硬编码 `17`。
- **反向推码（`AppNotifyServiceImpl#doNotifyIndustryData`）的 `bizData` MUST 带 `companionFlag`，漏了会让同行码「能进不能出」**（2026-09-11 定位并修复，`ticket-server:2.1.61`，APP 侧已确认因果）。
  字段口径以甲方规范**表 55「行业数据推送请求参数信息」**为准：`thirdUserId` / `cardId` / `cardType` / `cardData` / `companionFlag` / `entryDeviceCode` / `exitDeviceCode`。
  **故障链条（完整记下来，避免重复排查）**：推送不带 `companionFlag` ⇒ APP 无法区分同一 `thirdUserId` 下的主码与同行码 ⇒ 把新码落到主码上 ⇒ **同行码展示的码永远停在开卡那一刻**（`lastTicketStatus=03` / `lastHandleStationCode=FFFF` / `tikcetTransSeq=0`）⇒ 进站闸机接受 03 码所以每次都能进（且每次都把 `QRCODE_STATUS` 又推一格），出站闸机需要 `04` + 进站站点，拿到 03 码即拒。
  **`companionFlag` 取 `request.getCompanionFlag()`**（来源 `USER_ITP_REG_INFO.COMPANION_FLAG`，`Y`/`N`/`C`），由 `GateTicketHandler:161` 的 `applyActualCardType` 写入、异步推送任务在 `:235` 才提交，因此读到的一定是 account 真值——**NEVER 把 `applyActualCardType` 挪到 `notifyVerifyResult` 之后**。
  一个 `thirdUserId` 下可以同时存在主码与同行码，**APP 侧 MUST 按 `cardId` 落地**，`companionFlag` 只表达票种语义、**NEVER 当作卡的唯一键**。
  遗留：`entryDeviceCode` / `exitDeviceCode` **仍未送**（进站设备码需走 `entrytxn/EntryTxnQueryService.queryEntryDevice(cardId)` 反查，2026-09-14 从 `TicketRideStatusService` 迁出），待业务确认是否补。
- **闸机上送的 `handleResultCode` 非 `000` 时 ITP 直接短路，NEVER 在 ITP 侧找原因**。`FepAgmController.java:63` 判非成功即打 WARN「读写器返回非成功状态，跳过业务处理」并返回，`notiVerifyResult` 根本不进业务逻辑，ticket-server / gate-txn-pay 也就查不到任何痕迹。**`089` 是闸机读写器自己给的码**，含义是它读到的码体状态不满足本次交易（如出站拿到 `03` 码）。排查这类问题 **MUST 先比对闸机上送 bizData 里的 `lastTicketStatus` / `lastHandleStationCode` / `tikcetTransSeq` 与 `QRCODE_STATUS` 是否一致**——不一致就是 APP 展示的码没刷新，不是服务端状态机的问题。
- **判断 APP 推送是否成功 MUST 解析 `retCode`，NEVER 只看 HTTP 状态**（2026-09-11 补）。该接口对无效卡号返回 `{"retCode":"7004","retMsg":"处理过程出现错误!"}` 而 **HTTP 仍是 200**（实测，用不存在的卡号探针验证过）；`Content-Type` 是 `text/plain;charset=UTF-8` 而报文体是 JSON，解析 **NEVER 依赖 Content-Type**。修复前该方法只打印 httpCode 与 body、不做判定，`7004` 也会记成「推送完成」，运维完全看不见。
  我方用 `MultipartBody.FORM`（`multipart/form-data`）发送，与甲方规范写的 `x-www-form-urlencoded` **不一致但实测对方能正常解析**（真实卡返回 `0000`、坏卡返回 `7004`，说明 `bizData` 确实被解出来了），**NEVER 因为「与规范不一致」去改**——改了反而可能弄坏当前能通的形态。
  当前**没有失败补偿**：失败只打 WARN，不落库、不重试。可接受的前提是「每次过闸都会重推一次，失败会被下一次过闸自然覆盖」，真正的暴露窗口只有「失败后到下次过闸之间」（**最后一次过闸推失败即永久丢失**）。补偿方案已按 outbox 模板设计完毕但 **2026-09-14 用户裁决搁置、代码一行未改**，重新开工 MUST 先读 `docs/domain/outbox.md` §七①（含载体表选择、`RpcOutcome` 改造点，以及本链路特有的「补偿推最新码而非快照、`TICKET_TRANS_SEQ < QRCODE_STATUS.TXN_SEQ` 即已被后续过闸覆盖应短路」规则），**NEVER 从零重新设计**。要挂补偿的位置就是 `AppNotifyServiceImpl` 里 `if (!doNotifyIndustryData(...))` 那个分支。
- **日票出站扣次（`markUsed`）失败也没有补偿，且补偿前 MUST 先补幂等键**（`GateTicketHandler:217~238`）。当前失败一律 `log.error` 后放行出站——**出站放行这一点 NEVER 改**，乘客已在站内，拦住等于把人困在付费区。缺口是扣次丢失属资损（乘客白坐一次）且无任何自愈路径。**关键阻塞点：`markUsed` 传 `orderNo=null`（`:223`），而 daily-ticket-server 的 `UK_DTUL_ORDER` 在 `null` 上不拦截（Oracle 允许多个 null），因此直接加重试会重复扣次 —— 反向资损，比不补偿更糟。** 补偿方案与「先补确定性 `orderNo`」的前置条件见 `docs/domain/outbox.md` §七②，同样于 2026-09-14 搁置。另注意 `:215` 的注释记载「用户已否决扫表/定时任务」与 outbox 模板 §五冲突，**重新开工前 MUST 先与用户确认扫表方案是否解禁**。

- **APP 推送地址 `app.notify.industry-data-url` 线上靠 K8s Deployment env 注入**，`ticket-server/application.properties:50` 曾是损坏行（键名被打断成 `app.notify.industry-data-urlx/receiveCardDataFromItp`，2026-09-11 修复）。损坏时会静默落到 `AppNotifyServiceImpl.java:49` 的默认值 `http://127.0.0.1:8080/...`，在 K8s 里等于打自己。**该地址目前仍是 `testngbackV2` 测试路径**（已确认是被测 APP 真实在用的后端，链路能通），**上生产前 MUST 换成生产地址**。
- **IF8A-05 / IF8A-34 的 `payAmount` 单位是分，NEVER 在出口转元**（2026-09-07 修复）。全链路（AGM 上送 → `GateTxnPayServiceImpl.parseAmount` → `GATE_TXN_PAY` → 支付中心 `amount`）统一按分，APP 侧也按分解析。`TransRecordAssembler` 原先用 `String.format("%.2f", fen / 100.0)` 转成元，APP 再除一次 100，库内 `TRX_AMOUNT=200`（2 元）在 APP 上显示成 **0.02**。同一 DTO 的 `discountFee` / `originalFare` / `walletTotalAmt` / `discountLevelAmt` / `expectedGateAmount` 一直是分，只有 `payAmount` 曾是元，内部本就不自洽。
- **`payAmount` 取 `GATE_TXN_PAY.TOTAL_AMOUNT`（车费 + 超时费），列表与详情同一口径**。`TOTAL_AMOUNT` 才是实际请求扣款的金额（`GateTxnPayServiceImpl.requestPaySign` 传的就是它）；**NEVER** 退回 `TRX_AMOUNT`（漏超时费），也 **NEVER** 用 `PAY_TXN_DETAIL.AMOUNT`——它会被 `pay.sign.test-force-amount` 覆盖（2026-09-07 生产实测：多数行 `AMOUNT=1` 分而对应 `TOTAL_AMOUNT=200`），拿它展示等于给乘客看错误金额。
- **APP 乘车记录页的 `cardType` 与 `CardTypeMapping` 是两套编号，`05` 是日票聚合桶**（2026-09-10 抓 fep-app 日志实测 + 用户确认语义）。APP「乘车记录」有三个页签，各自发一套 `cardType` / `transType`：
  - 扫码过闸（免密二维码）`cardType=02` / `transType=02` → `CardTypeMapping` 映射为 `0441`，能查到数据
  - NFC 过闸 `cardType=03` / `transType=03`、新 NFC `cardType=04` → **两者都是「NFC 聚合桶」，展开为 `0442`+`0443`**（用户 2026-09-10 裁决「不论传 03 还是 04，同时查 03、04」）。原因是**开户与查询的口径不一致**：同一张新 NFC 卡开户时 APP 送 `04`（库内 `USER_ITP_REG_INFO.ITP_CARD_TYPE=04`、`CARD_TYPE=0443`），而乘车记录页签实测发的是 `03`（抓 fep-app 日志：`cardType=03` + `cardId=0426091000000013`），旧的单值映射 `03→0442` 恒命中 0 行且 `retCode=0000` 不报错——订单 `GT20260910164625246000013` 就是这么「丢」的（2026-09-10 定位并修复，`itp/ticket-server:2.1.55`）。**NEVER 退回单值映射，也 NEVER 指望 APP 改成只发 04**（旧 NFC 卡 `0442` 会反过来查不到）。实测：`03` 与 `04` 都能查到 `0443` 的新卡记录，`03` 也仍能查到 `0442` 的旧卡记录（用户 `00522947` 卡 `0426090900000011`）；IF8A-41 同一映射，`cardType=03` 返回 `count=1 / totalPrice=2`。
  - 电子日票 `cardType=05` / `transType=05` → **`05` 是「日票聚合桶」**（用户 2026-09-10 确认）：APP 只有一个电子日票入口，不区分一日 / 三日 / 七日 / 月票，因此一个入参对应 4 个发卡卡类型 `0445`~`0448`。而 `ISSUE_CARD_TYPES` 里按天数细分的 `12→0445`、`13→0446`、`14→0447`、`15→0448`（另有 `11→0444` 员工票）是**另一层编号**，两套并存。
  **IF8A-05 已按聚合桶修复（2026-09-10）**：`CardTypeMapping.toIssueCardTypes(String)` 返回列表（`05` 展开 4 值、其余单值），`QueryTransListReqDTO.cardTypeList` 承载，`GateTxnPayMapper.xml` 的 `selectTransList` / `countTransList` 用 `<choose>` 优先按 `CARD_TYPE IN (...)` 过滤。**两个条件互斥，NEVER 同时下发 `cardType` 与 `cardTypeList`**——AND 起来必然命中 0 行，因此 `TransListQueryHandler` 映射后显式把 `cardType` 置 `null`。入口同时加了 `isSupportedAppCardType` 白名单校验，非法卡类型返回 `INVALID_PARAM` 而不是静默返回空列表。
  修复前的行为（排查同类问题时的参照）：`getOrDefault(normalized, normalized)` 把未识别的 `05` 原样透传，拼成 `AND CARD_TYPE = '05'`，而该列存的是 `04xx`（`CardTypeCodeEnum` 全集 `0441`~`0448`/`044A`），**恒命中 0 行且 `retCode=0000`**，看不出任何异常。**NEVER** 用单值映射「修」这类聚合码——补 `05→0445` 只覆盖一日票，三日 / 七日 / 月票照旧查不到。
  两点连带结论：①`TransQueryParamNormalizer.parseCardIds`（`:270`）已 `filter(!isEmpty)`，APP 上送的尾随逗号（实测 `cardId="0426090951000040,"`）**无害**，排查空结果 **NEVER** 往这个方向猜；②`cardTypeList` 映射修好后，电子日票页签**在测试库里仍查不到数据，但那是历史数据缺口，不是代码问题**——见下一条。
- **日票同样落 `GATE_TXN_PAY`，IF8A-05 全票种单源查该表，NEVER 按票种分流到 `QRCODE_TXN_DETAIL`**（2026-09-10 先误判后更正，已发生过一次错误上线，务必读完本条）。
  代码链路（这是权威依据，**MUST 以此为准，NEVER 靠「预付费所以不产生扣费订单」推断**）：
  - `GateTransactionHandler.shouldPay`（`fep-dev-server`，`:407`）只判 `TrxTypeCodeEnum.isExitTxn(trxType)`，即 `02`/`03`，**完全不看 `cardType`**，日票出站照样进扣费链路。
  - `GateTxnPayServiceImpl:166` 的 `if (isDailyTicket(order) || order.getTotalAmount() <= 0)` 分支**照常 `INSERT` 订单**，`DEBIT_STATUS` 直接置 `SUCCESS`、备注「日票交易默认支付成功」，只是跳过 pay-sign。`isDailyTicket` → `CardTypeCodeEnum.isDailyTicket`，覆盖 `0445`~`0448`。
  - 另外 `retryPay`（`:241`）与 `requestRefund`（`:302`）都显式拒绝日票订单——**这些分支的存在本身就证明日票订单会在表里**。
  「`GATE_TXN_PAY` 全表零条 `0445`~`0448`」的真实原因是**时间窗不重叠**，与票种无关：
  ```
  GATE_TXN_PAY        20260828 ~ 20260910   29 行
  日票出站流水         20260722 ~ 20260812   18 行   ← 止于 GATE_TXN_PAY 有数据之前
  二维码出站流水       20260626 ~ 20260910   78 行
  ```
  **20260828 之后没有任何日票过闸样本**，所以无从落库。把「当前无数据」读成「架构上不产生」是 2026-09-10 那次误判的根源；已按此错误前提上线 2.1.48 / 2.1.49 的按票种分流，2.1.50 已回退。判断某票种是否落某表 **MUST** 先比对两边的数据时间窗，**NEVER** 只看「某取值 count=0」。
  为什么不能分流查 `QRCODE_TXN_DETAIL`（回退理由，避免再走一遍）：①`GATE_TXN_PAY` 的日票行字段更全（`ENTRY_STATION_NAME`/`EXIT_STATION_NAME` 已落库、有 `ORDER_NO`/`TICKET_CODE`/`COUNTING_TIMES`/`COUNTING_FLAG`），`QRCODE_TXN_DETAIL` 只有站点编码、要额外调 para-server；②`tradeOrderNo` 只能退化成 `TICKET_TRANS_SEQ`（`0`/`1`/`2`），而 IF8A-34 详情走 `gateTxnPayClient.queryByOrderNo`，**日票记录点进详情必返 `8002 无数据`**（2026-09-10 实测）；③一旦有人刷日票，新订单落 `GATE_TXN_PAY`，分流实现只查 QRCODE，等于主动忽略更完整的那份数据。
  遗留：20260812 之前那 18 条历史日票流水在 `GATE_TXN_PAY` 里确实没有，回退后仍查不到。另外 20260828 之前连 `0441` 也一条没有，而 `INSERT` 发生在调 pay-sign 之前、逻辑上不受 2026-08-26 那次 `/v1` 修复影响，**该表是否被清过或迁移过尚无证据**。
- **日票落 `GATE_TXN_PAY` 已由真实过闸实测闭合**（2026-09-10 14:16 进 / 14:31 出，卡 `0426090951000084`、用户 `00522943`、`CARD_TYPE=0445` 地铁一日票）。落库结果：`GATE_TXN_PAY.ORDER_NO=GT20260910143159899000084`、`TOTAL_AMOUNT=0`、`DEBIT_STATUS=SUCCESS`、`TICKET_TRANS_SEQ=1`，`QRCODE_TXN_DETAIL` 同时有 `TRX_TYPE=01/02` 两行，两表字段一致。因此上文那次「日票不落 GATE_TXN_PAY」的判断确认为误判，**IF8A-05 单一数据源（GATE_TXN_PAY）是正确设计**。
  连带闭合：**日票记录点详情不会返 `8002`**。`tradeOrderNo` 就是 `GT...` 订单号，IF8A-34 走 `queryByOrderNo` 实测返回 `0000`（上文 §138 第②条的顾虑只在「若改成查 QRCODE_TXN_DETAIL」的假设下成立，现实链路不成立）。IF8A-05 / IF8A-34 / IF8A-41 三个接口对该行程返回一致（列表 1 条、详情成功、统计 `count=1` 金额 0）。
- **IF8A-34 详情里日票的三个支付字段由 daily-ticket-server 回填，NEVER 当成「空串是正常的」**（2026-09-15，ADR-D82）。日票过闸免扣费、没有 `PAY_TXN_DETAIL`，`TransRecordAssembler.assemble` 在 `pay == null` 时把 `payTradeOrderNo` / `payOrderNoDate` / `payChannelCode` 输出成空串（2026-09-14 实测免扣费单 `GT20260914180952303000039` 三个字段全空）。现在 `TransDetailQueryHandler.enrichDailyTicketPayInfo` 按 `record.ticketCode` 调 `POST /ci/daily-ticket/queryDailyTicketPayInfo` 回溯购票订单补上。四条约束：①`ticket-server` 与 `trans-query-server` **两份副本逐字段一致，改一处 MUST 同批改两处**；②方法内 **MUST catch 全部异常只记日志** —— `DailyTicketClient` 不吞异常，不接住会把整条 IF8A-34 变成 9999，而这三个字段只是补充信息；③`payOrderNoDate` 是**购票付款时刻**、不是过闸时刻，长周期票会显示很早的时间，**属有意为之**；④短路条件是「`ticketCode` 为空」或「`payTradeOrderNo` 已有值」，非日票单**一次 RPC 都不发**。
- **离线码的中文站名由 gate-txn-pay-server 负责，ticket-server 这份只是兜底**（2026-09-15，ADR-D83）。用户报「离线码列表站名显示成 `0622`」，根因是站名与编码**不同源且时序相反**：站名此前唯一写入源是 `GateTxnPayRequestAssembler.fillStationNames`，入参 `lastHandleStationCode` 在 `GateTicketHandler:129` 被**无条件覆盖**成 `QRCODE_STATUS.LAST_TXN_STATION`（开卡初值是占位 `FFFF`，`ticket.default-last-txn-station`，**实测该表 86 行里 51 行就是 `FFFF`、而 `TBL_STATION_INFO` 当前版本查不到这个码**）；而离线码的真实进站码要到 gate-txn-pay 的 `FareCalculator` 按 `cardId + ticketTransSeq` 重查、覆盖 `IN_STATION` 时才确定 —— **发生在站名解析之后**。于是编码被修对、站名留空或留着上一趟行程的错名，列表侧回落显示编码。现在站名的 owner 收口到 `gate-txn-pay-server` 的 `StationNameBackfiller`（详见 `docs/business/gate-txn-pay.md` §编码约束末条）。**ticket-server 这份 `fillStationNames` 保留未删、NEVER 删**，它既是对端拿不到 para 应答时的降级路径，也是**非离线码路径上站名的唯一写入方**（那些路径永不重算，`StationNameBackfiller` 兜不到）。
- **`fillStationNames` 的两个站名已改成各自独立回填**（2026-09-15，ticket-server 2.1.84，ADR-D83 续，用户明确授权）。旧语义是「进站码为空即整体 `return`、**连查得到的出站站名一起丢**」，而出站站名与进站码无关，属纯连带损失。现在两个码各自 `hasText` 判定后放进同一个 `LinkedHashSet`（**MUST 逐个 `add`，NEVER 换成 `Set.of(...)`** —— 进出同站会抛 `IllegalArgumentException` 且它拒绝 `null`），一次查询、各自独立写回；**查不到就不写，NEVER 把编码写进站名列**；**NEVER 回退成「进站码为空即整体 return」**。用例见 `GateTxnPayRequestAssemblerStationNameTest`（6 条，含「占位 `FFFF` 只让进站名留空」「两码全空时一次 RPC 都不发」）。**本文此前写的「该语义本批未动」已作废。** 另：钱包渠道（`paymentVendor='0B'`）的离线码在测试环境**走不到算价之后**（`FareCalculator:199` 的公交换乘查询即抛异常、订单卡 `OFFLINE_FARE_PENDING`），因此该渠道的站名回填**尚无端到端实证**，详见 ADR-D83 续。
- **`countingTimes` 的语义是「本次行程消耗的次数」，恒为 `1`，NEVER 回填剩余次数**（用户 2026-09-10 裁定，`ticket-server:2.1.52` 已修）。原实现 `GateDailyTicketCoordinator.applyDailyTicketFields` 把 `DAILY_TICKET_INSTANCE.ACTUAL_TIMES` 直接回填，而该列用 **`-99` 表示不限次**（`daily-ticket-server-schema.sql:99`），于是 `-99` 一路经 fep-dev → `GateTxnPayReqDTO` → `GATE_TXN_PAY.COUNTING_TIMES` 落库并透给 APP（实测订单 `GT20260910143159899000084`、`GT20260910144231685000084` 均为 `-99`，APP 会渲染成「-99 次」）。一日票 / 多日票是「这趟用掉一次日票」，多日计次票是「这趟扣一次」，两者都是 1；剩余次数属票卡资产状态，归日票查询接口，**不属于行程记录**。历史两行已 `UPDATE GATE_TXN_PAY SET COUNTING_TIMES=1 WHERE COUNTING_TIMES=-99` 修正（还原 SQL：`UPDATE GATE_TXN_PAY SET COUNTING_TIMES=-99 WHERE ORDER_NO IN ('GT20260910143159899000084','GT20260910144231685000084')`）；`countingFlag` 语义不变（1=计时票 0445~0447，2=计次票 0448）。**新代码路径尚缺一次 2.1.52 之后的真实日票过闸来验证**（已验证的是接口出口与库内数据）。
- **`DAILY_TICKET_INSTANCE` 里的卡类型是第三套编号，`05` 在库里根本不存在**（2026-09-10 实测）。该表 `APP_CARD_TYPE` 取值为 **`45` / `46` / `48`**（发卡卡类型去掉 `04` 前缀），`TICKET_TYPE` 取值为 **`12`(地铁一日票) / `13`(地铁三日票) / `15`(地铁单次票-臻宝游青岛)**，`CODE_TICKET_TYPE` 恒为 `0441`。这正好交叉验证了 `CardTypeMapping.ISSUE_CARD_TYPES` 的 `12→0445` / `13→0446` / `15→0448`（`0445`/`0446`/`0448` 在 `QRCODE_TXN_DETAIL` 里的行数与该表实例数同向），也确认 **`05` 只是 APP 页签标识，任何库表都不存该值**。`14→0447` 仍无任何实例与流水，**待实测**。查日票 **MUST 认准列名 `CARD_NUM` / `THIRD_USER_ID`**，该表**没有** `CARD_ID` / `CARD_TYPE` / `VALID_*` / `USED_TIMES` 列（按 `CARD_ID` 查会报 `ORA-00904`）。
- **IF8A-41 `requestTransStatistics` 已修复并端到端验证通过**（2026-09-10，`ticket-server:2.1.51` + `fep-app:2.0.77`）。原有四个缺陷，**改动前 MUST 通读，NEVER 只改其中一处**：
  1. `RequestTransStatisticsReqDTO` 没有 `cardType` 字段 → `parseBizData` 静默丢弃 → 未开通某票种的页签退化成「按用户查全部票种」。
  2. `selectTransStatistics` 的 WHERE 没有 `CARD_TYPE` 条件（同 mapper 的 `:213`/`:239`/`:287`/`:315` 都有，属遗漏而非设计）。
  3. `COUNT(1)` 统计的是**闸机事件数而非乘车次数**——本表进站行也在其中，已补 `TRX_TYPE IN ('02','03')` 只算出站行（实测用户 `00522949` 有 2 进 1 出，旧口径返回 3）。**金额此前碰巧正确**（进站行 `TRX_AMOUNT` 恒为 0），**NEVER 因为「金额对」就认为口径没问题**。
  4. 空结果集时 `COUNT(1)` 返回 0 而三个 `SUM()` 返回 `NULL`，只判 `tripData == null` 会把 `null` 金额透给 APP。兜底 **MUST 逐字段判空**。
  另修了一个边界：`CARD_ID` 的 `<if>` 原先判 `cardId` 而 `foreach` 用 `cardIdList`，`cardId=","` 会生成 `AND CARD_ID IN ()` 并抛 `ORA-00936`。
  **APP 实际上送 6 个字段**（抓 fep-app 日志实测）：`thirdUserId` / `cardType` / `transType` / `cardId` / `startDate` / `endDate`。其中 **`cardType` 与 `transType` 恒同值**（三个页签分别 05/05、02/02、03/03），一个语义两个字段，DTO 只接 `cardType`，**NEVER 把 `transType` 当成另一个查询维度**。**日期格式是 `yyyyMMdd`**（与 `TXN_DATE` 同格式直接可比），**NEVER 复用 IF8A-05 的 `TransQueryParamNormalizer.normalizeDate`**——那个按 `yyyy-MM-dd` 解析，两个接口的日期格式**不一致**。**未开通某票种时该页签不带 `cardId`**（NFC 页签只有 `thirdUserId` + `cardType`），因此 `cardType` 是唯一的票种过滤依据。
  端到端实测（经 fep-app `10.100.146.184:9101`，与直连库 `GROUP BY CARD_TYPE` 逐项对账）：`00522949` 二维码 `count=1/2.00`（旧口径 3）、NFC 与日票 `count=0` 且三个金额为 `"0.00"`（旧口径分别是「镜像二维码的 3」与「金额 null」）；`00522889`（同时持 4 票种）按页签 `02/03/05` 分别得 `6/0.00`、`4/14.00`、`14/0.00`，不带 `cardType` 时得全票种 `24`；`cardType=99` → `8001 卡类型非法`；`cardId=","` 不再 500。
  **编码前置条件已核实（2026-09-10 直连生产库）：`QRCODE_TXN_DETAIL.CARD_TYPE` 与 `GATE_TXN_PAY.CARD_TYPE` 是同一套 `04xx` 编码**（前者实测取值 `0441`/`0442`/`0445`/`0446`/`0448`/`NULL`，后者 `0441`/`0442`，均落在 `CardTypeCodeEnum` 全集内），因此 **可以直接复用 `CardTypeMapping.toIssueCardTypes`**，此前「不可照搬」的顾虑已排除。该表有 3 行 `CARD_TYPE IS NULL`（20260626~20260810），加 `IN` 条件后会被排除，属预期。
  **两表关系已查明（2026-09-10 实测 + 代码印证）**：`GATE_TXN_PAY` 是 `QRCODE_TXN_DETAIL` **出站行的下游派生**，关联键 `(CARD_ID, TICKET_TRANS_SEQ, TXN_DATE)`，29 行中 28 行能匹配到出站行，且 `IN_STATION`/`IN_TIME`/`OUT_STATION`/`OUT_TIME`/`TRX_AMOUNT` **28/28 完全一致**——因为 `GateTxnPayServiceImpl:384-387` 就是 `IN_* ← LAST_HANDLE_*`、`OUT_* ← HANDLE_*` 直接赋值的。唯一不匹配的 1 行是 `TRX_TYPE='03'` 补站单，走 `:727-735` 另一条路径（`selectFirstEntryBySequence` 反查进站行填 `IN_*`），**`QRCODE_TXN_DETAIL` 里没有它的出站行**，因此以 QRCODE 为主干的查询会漏掉补站记录。
  连带结论：IF8A-41 走 `QRCODE_TXN_DETAIL`、IF8A-05 走 `GATE_TXN_PAY`，两者口径差异**只来自数据时间窗与补站单**（QRCODE 从 20260626 起、GATE_TXN_PAY 从 20260828 起），不来自票种。改 IF8A-41 前 **MUST** 与业务确认这两处差异是否可接受。
- **`totalDiscount` 取的是 `OVERTIME_AMOUNT`（超时/加收金额），字段语义与「优惠」相反**（`QRCodeTxnDetailMapper.xml:478`，待业务确认）。同一 SQL 里 `totalPrice = SUM(TRX_AMOUNT + OVERTIME_AMOUNT)`，把加收计入总价是对的，但把它同时当优惠加总则可疑。改动前 **MUST** 先确认「优惠」在业务上对应哪个字段，**NEVER** 直接改聚合列。
- **AGM 出向应答 NEVER 多返回字段**。闸机侧是老项目，JSON 反序列化遇未知字段直接抛异常 → 判定本次上送失败 → 重发（上限 3 次）。IF1A-01 的应答 **MUST** 只有 `retCode` + `retMsg`（`NotifyVerifyResultAckDTO`，`FepAgmController.java:43`），**NEVER** 直接把 `model` 模块的 `NotifyVerifyResultRespDTO`（16 字段）返回给闸机——后者是 ticket-server → fep-dev 的**内部**应答，承载 `ticketStatus`/`payChannelCode`/`requestSignSeq` 等供 `GateTransactionHandler#requestGateTxnPay` 组装扣费报文，两者不可合并。
  已发生事故（2026-08-27 生产）：因应答带 16 字段，**每笔进站、每笔出站均被上送 3 次**（进站 11:17:30.322/30.721/31.692，出站 11:17:41.915/42.166/42.558），每次都在我方返回 `0000` 后约 19ms 到达且报文 `timestamp` 重新生成（闸机重建报文而非重传）。后果：一趟行程把 `QRCODE_STATUS.USE_COUNT`/`TXN_SEQ` 推进 6 格而非 2 格；行业数据向渠道推了 6 份。新增或修改任何 `/ci/agm/**` 接口的应答字段前 **MUST** 先核对甲方接口规范的「应答参数」表。
- **`QRCODE_STATUS` 的 upsert 已有 CAS 保护，但仍是相对增量，重复上送不完全幂等**（2026-09-14 CAS 改造后更新）。过闸链路改走 `GateTicketWriter.saveTxnAndAdvanceStatus(detail, nextStatus, expectedTxnSeq)`（第三参 = `currentStatus.getTxnSeq()`），`upsertWithCas` 的 `ON` 条件含 `T.TXN_SEQ = #{expectedTxnSeq}`：同一个 `TXN_SEQ` 只有一次写入赢，AGM 超时重发第二次的 UPDATE 分支命中 0 行 → 回查库内状态 → 以库内真实状态继续应答（观察期，不拒绝请求，打 `IF1A-01 CAS upsert 未命中` WARN）。`buildNextStatus` 仍取 `current + 1`（`:470`/`:475`），**CAS 挡住了并发推进但没改增量方式**。改这条链路时 MUST 注意：①`saveTxnAndAdvanceStatus` 是 `@Transactional`，NEVER 在其内部加 RPC；②`WriteResult` 三字段 `(duplicate, updateCount, actualStatus)` MUST 全部处理，`duplicate=true` 时 `actualStatus` 是回查值而非传入值；③明细的 `DuplicateKeyException` 在 `GateTicketWriter` 内部吞掉，**不影响后续 CAS upsert**（吞掉后改走回查，`duplicate` 置 true）。
- `entity` 字段 `gateStatus`、`txnSeq`、`gateInTime/gateInStation`、`lastTxnTime/lastTxnStation`、`useCount`、`trxAmount` 有明确语义，改动前 **MUST** 读 `ticket-server/.../entity/QRCodeStatus.java`
- fep-dev-server / industry-data-server **无数据库**，**NEVER** 在其中新增 mapper
- 码体签名走 `SecurityClient`，**NEVER** 在业务模块内自行实现 SM2/签名
- 闸机上送报文的流水号字段名拼写为 `tikcetTransSeq`（甲方侧拼错，`ticketTransSeq` 的错拼），**NEVER** 按正确拼写去解析

## 参考原始文档
- `docs/技术规范文档/城市轨道交通自动售检票系统技术规范-第9部分-互联网业务规范.docx`
- `docs/接口规范文档/青岛地铁-ITP与APP接口规范R6.docx`（IF8A-03/04/29/34/41、IF8D-03）
- `docs/业务需求文档/青岛地铁虚拟电子多日计次票、离线码、实时客流上传功能方案V1.docx`（离线码部分）
