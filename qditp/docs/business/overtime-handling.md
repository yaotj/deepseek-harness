---
业务域: 超时处理（超时费 / 超时扣次 / 超时补收）
模块: ticket-server、daily-ticket-server、gate-txn-pay-server、para-server、pay-sign-server、recon-server
状态: **待实现契约**（不是现状描述）
---

# 提示词：超时处理

## ⚠️ 本篇与本目录其余文件性质不同

`docs/business/` 其余文件描述**代码实际落地情况**；**本篇是待实现功能的接口契约与业务要求**。
读本篇 **MUST** 同时读 §二「现状与差距」——那一节才是现状，其余各节是目标态。
**NEVER 拿本篇 §三~§六 当成已实现的行为去排查线上问题。**

## 何时读本文件
接到「超时费」「超时补票」「超时扣次」「超时罚金」「逾站补收」相关需求时。
动手前 **MUST** 先读 §七「待甲方澄清」——**目前有 6 项未闭合，其中 3 项不闭合就无法编码**。

---

## 一句话结论

甲方规格里**唯一**一条可执行的超时处理条款是**多日计次票**的「扣两次 / 次数不足则扣款」，出处是
`docs/业务需求文档/青岛地铁虚拟电子多日计次票、离线码、实时客流上传功能方案V1.docx` §一4 计费规则。
而代码里这条**一行都没实现**，且存在一个**方向相反的现存缺陷**：超时出站（`trxType=03`）时多日计次票
**连正常的那一次都不扣**（§二①）。后付费单程票（`0441`）的超时费则是「闸机算、ITP 只搬运」，
规格中不存在任何要求 ITP 计算超时费的条款——**离线码是唯一例外，而那个 20 分钟 / 3 元的口径在甲方文档里查无出处**（§二④）。

---

## 一、规格依据（逐字原文）

### 1.1 多日计次票超时扣次——唯一权威条款 ★

`docs/业务需求文档/青岛地铁虚拟电子多日计次票、离线码、实时客流上传功能方案V1.docx` §一 4、计费规则，末段原文：

> 交易超程或超时，闸机回调ITP的报文中超时金额传线网最高票价，ITP扣除正常乘车次数后再额外扣除一次作为超时费，如果剩余次数不足两次，扣除一次乘车次数后按照线网最高票价使用默认支付方式扣一笔超时费。

拆解（**不转述、只定位**）：

- 触发条件：`交易超程或超时` —— **「超程」与「超时」并列、同一处理分支**；原文**未定义时间阈值，也未定义超程判定**。
- 上送形态：`闸机回调ITP的报文中超时金额传线网最高票价` —— 即 `overtimeAmount` 由闸机填，值等于线网最高票价。
- 主规则：`ITP扣除正常乘车次数后再额外扣除一次作为超时费` —— **正常 1 次 + 超时 1 次 = 2 次**。
- 降级规则：`如果剩余次数不足两次，扣除一次乘车次数后按照线网最高票价使用默认支付方式扣一笔超时费`
  —— 扣 1 次 + 走默认支付方式扣一笔现金，金额 = 线网最高票价。
- **本条只说「线网最高票价」，没有对账那句里的「或者1」**（见 1.3，两处口径不自洽）。

同文档 §一 4 另一句（与超时**无关**，勿混）：`多日计次票剩余次数为0或未进站状态下超过失效时间，将无法展示多日计次票的二维码`
—— 那是**有效期超时**，后果是不出码 / 不开闸 / 自动退票，**不产生超时费**。§三 会把两者分列。

### 1.2 `overtimeAmount` 字段的规格定义（IF1A-01 / IF8A-19）

`docs/技术规范文档/城市轨道交通自动售检票系统技术规范-第9部分-互联网业务规范.docx` §7.4 IF1A-01 闸机检票通知，
URL `/[project]/ci/agm/notiVerifyResult`，请求参数表（表62，**只有「字段 / 类型 / 说明」三列，无长度列、无必填列**）：

```
trxType         | String | 交易类型  01进站 / 02出站 / 03 超时出站
trxAmount       | String | 实际交易金额（单位分）
overtimeAmount  | String | 超时金额（单位分）
lastTicketStatus| String | ... 06：超时出站 ...
```

三条硬事实：

1. `overtimeAmount` 的定义只有「超时金额（单位分）」六个字，**没有长度、没有必填标注、没有计算公式、没有阈值、没有一句话说明谁算**。
2. 该字段出现在 **AGM → ITP** 的上送报文里、与 `trxAmount` 并列 ⇒ **闸机算好后上报，ITP 只接收**。
   IF8A-19（BLE 通知闸机检票通知，表131）字段完全相同，方向也是设备侧 → ITP。
3. **规格中不存在任何「ITP 计算超时费」的条款**（TS9 + R6 双份文档实读，`超时费` / `逾站` / `在站时间` /
   `最长在站时间` / `票价上限` 关键字**全部零命中**）。

### 1.3 对账侧口径

`docs/接口规范文档/ACC与ITP之间的文件.docx` §一（4）虚拟电子多日计次票文件，原文：

> 由ACC根据线路上传文件的“SIGN_CHANNEL_CODE”字段进行票种区分，多日计次票正常交易不进行对账，只对车票购买的交易。产生超时费的行程会针对超时费用进行对账。

> 超时的行程在对账文件中类型为出站，费用为线网最高票价或者1。

`ITP.DETAIL` 表头 7 段：`运营日期、交易类型、逻辑卡号、交易日期时间、交易金额、当前车站名称、设备编码`。

- **没有独立的「超时费」段**。超时费的表达方式是「**`交易类型=出站` 的那一行，把第 5 段 `交易金额` 填成超时费**」。
  **NEVER 去找「第 8 段超时费」。**
- 该 7 段表头**零处给出长度或类型**；第 5 段在本文档里是裸的「交易金额」、**无单位**，而
  `ITP.EXP` 同文档内明确写「（单位分）」，`docs/接口规范文档/青岛地铁日票-ITP与ACC交互文档.docx` 同一张表头写的是
  **「交易金额（元）」** —— **三处口径不一致，见 §七 待澄清 Q3。**
- 「或者1」原文用的是「**或者**」，**未说明二选一条件、未说明「1」的单位**。该句在 ACC 文件文档与需求方案文档里
  **逐字出现两次**，可确认是正式条款、不是笔误。

### 1.4 `ITP.EXP` 的「订单异常类型」——甲方取值与我方注释不是同一套编码

甲方原文（`ACC与ITP之间的文件.docx` §一（1），第 11 段）1~15 逐条：

```
1 单边账 (入站 )     2 单边账(出站 )      3 单边入站(人工处理单)  4 单边出站(人工处理单 )
5乘客自主补进站      6乘客自主补出站       7 TVM补币找零不足      8 TVM卡票
9 TVM/BOM发售无效票  10 闸门无用          11 无票出闸            12 人为单程票无效
13 非人为单程票无效  14 储值票无效        15 其他情况
```

- 甲方**从 1 开始、没有 `0`**，且**没有任何一项叫「双段计费超时」或含「超时」字样**。
- 我方 `orderExpType` 用的是另一套：`0 正常 / 1 单边账(入站) / 2 单边账(出站) / 3 单边入站(人工处理单) /
  4 单边出站(人工处理单) / 5 双段计费正常订单_行程超时`（出处 TS9 §7.6 IF8A-05 表，**规格自身就有这两套**）。
- 于是 **`5` 在甲方是「乘客自主补进站」、在 IF8A-05 是「行程超时」** —— 两套编码撞号。见 §七 Q4。
- 编号 `5` / `6` / `15` 原文粘连或缺空格，**解析映射 NEVER 按「数字+空格+名称」硬切**。

### 1.5 其余带「超时」字样但与本域无关的条款（勿误引）

- TS9 §5.2 表3「超时等待业务规则表」：BOM / TVM 的**通信超时**（心跳 30S/15S、查支付结果 5s、扫码支付 15s）。
- TS9 §7.5：TVM 取票二维码 **180 秒**显示超时 → 错误码 `2101`，属出票故障，不产生超时费。
- R6 IF8A-12 / 13、IF8B-04 的 `refundType=01 超时未使用自动退款`：**退款**类型码。
- TS9 §5.4：`自助补出站、更新操作、单边自动扣费、超时等涉及的金额不参加累计` —— 优惠累计的排除项，
  与 `FareCalculator` 现有的「超时费不参与优惠」一致。
- 离线码状态 `51 车票重复使用`：判据是`上次交易与本次交易时间差大于3分钟`，是**防重复使用**、不是计费。

---

## 二、现状与差距（本节是现状，每条带证据）

### ① P0 缺陷：超时出站时多日计次票一次都不扣

`ticket-server/src/main/java/com/chinasofti/huateng/ticket/gate/GateDailyTicketCoordinator.java:125`

```java
if (!TrxTypeCodeEnum.EXIT.getCode().equals(request.getTrxType())   // 只认 "02"
        || !CardTypeCodeEnum.isDailyTicket(resolvedCardType)) {
    return;
}
```

`TrxTypeCodeEnum`（`model/.../enums/TrxTypeCodeEnum.java:15`）里 `EXIT_OVERTIME("03","超时出站")` 是独立取值，
同类 `:43` 的 `isExitTxn()` **明确把 `02` 和 `03` 都当出站**（`:44` 原文
`return EXIT.code.equals(trxType) || EXIT_OVERTIME.code.equals(trxType);`），而这里用的是 `EXIT.getCode()` 精确等值。

后果：闸机上送 `trxType=03` 时**整段扣次逻辑直接 return** —— 既不扣正常次数、也不扣超时次数、
`DAILY_TICKET_INSTANCE.TICKET_STATUS` 不推进、§3.63 的 `receiveCountingTicketTimes` 通知也不发。
**乘客超时出站等于免费乘车一次。** 同一次出站却照常触发扣费链路（`GateFarePaymentOrchestrator.shouldPay` 用的是
`isExitTxn`，`02`/`03` 都进），因此**日票走的是「不扣次 + 金额已被清零」两头落空**。

### ② 闸机传来的线网最高票价被就地丢弃

`ticket-server/.../gate/GateCardTypeEnricher.java:156`

```java
if (CardTypeCodeEnum.isEmployeeCard(actualCardType) || CardTypeCodeEnum.isDailyTicket(actualCardType)) {
    request.setTrxAmount("0");
    request.setOvertimeAmount("0");
```

规格 §1.1 要求闸机把**线网最高票价**填进 `overtimeAmount`，而日票（`0445`~`0448`）在进入任何计费 / 扣次逻辑**之前**
就被无条件清零。这行本身是对的（日票免车费），但它**把车费与超时费一起清了** —— 实现 §1.1 时
**MUST 只清 `trxAmount`、保留 `overtimeAmount`**，否则超时费的金额来源直接消失。

### ③ 扣次固定 -1，没有「扣两次」的任何入口

- `daily-ticket-server/.../service/impl/DailyTicketServiceImpl.java:912` 起：`actualTimes > 0` 时调一次
  `decreaseActualTimes`，`remainTimes = actualTimes - 1`。
- `daily-ticket-server/src/main/resources/mapper/DailyTicketInstanceMapper.xml:156`：
  `set ACTUAL_TIMES = ACTUAL_TIMES - 1 ... where ID = #{id} and ACTUAL_TIMES > 0` —— **SQL 里硬编码 `- 1`**。
- RPC 请求体是裸 `Map`（`rpc/.../dailyticket/DailyTicketClient.java:269` 的 `markUsed`，键只有
  `cardNum` / `countingEnd` / `orderNo` / `inStation` / `outStation`），**没有次数字段**；接收端
  `DailyTicketController.java:133` 同样不接次数。
- `GateDailyTicketCoordinator:27` 的 `DAILY_TICKET_TIMES_PER_TRIP = 1` 是常量。
- **没有「剩余次数不足两次」的分支，也没有任何「扣次失败转扣款」的链路。**

### ④ 离线码那个「20 分钟 / 3 元」在甲方文档里查无出处

`gate-txn-pay-server/.../fare/FareCalculator.java:147`：`rideSeconds > offlineTimeoutSeconds` 即
`overtimeFee = offlineTimeoutFeeCents`，配置在 `gate-txn-pay-server/src/main/resources/application.properties:73`：

```properties
offline.billing.timeout-seconds=${OFFLINE_BILLING_TIMEOUT_SECONDS:1200}
offline.billing.timeout-fee-cents=${OFFLINE_BILLING_TIMEOUT_FEE_CENTS:300}
```

三份甲方文档实读结果：**「3元 / 3 元/ 三元」零命中**；「20 分钟」的 4 处命中**全部**属 BOM 更新 / 票卡状态语义
（`005: 20分免费进站更新无法进站`、`transAmount 20分付费更新 金额`、`08：20 分钟内免费更新`、`09：20 分钟内付费更新`），
**没有一处出现在离线码计费规则里**。离线码计费规则原文只有：

> ITP平台将根据本次序列号查询本次行程进出站的车站与进出站时间，计算行程票价与是否超时。

—— **说了要算「是否超时」，但既没给阈值、也没给金额**。因此当前的 1200 秒 / 300 分是**实现自定义口径**，
且与 §1.1 的「线网最高票价」不一致。见 §七 Q2。
（这两个键都有 `${ENV:}` 包装，**线上实际值 MUST 查 Deployment env**。）

### ⑤ 「线网最高票价」参数在代码里零命中

`maxFare` / `MAX_FARE` / `最高票价` / `线网最高` / `maxPrice` 全仓 grep **零命中**（仅
`docs/business/recon.md:651` 与 `docs/ops/生产环境清单.md:959` 两处文档提及，均记为未闭合项 B9）。

para-server 的票价体系是两级查询：`TBL_FARE_MATRIX` 存 OD 对 → `FARE_TIER`，再按 `FARE_TIER` 查价，
**没有 `MAX(票价)` 聚合接口**。`ParaClient` 现有方法只有 `requestTicketPriceByStation`（按进出站算价）、
`requestBuySinlgeTicketMaxNum`（单次购票最大张数，**名字像但与票价无关**）等 8 个，**没有取线网最高票价的方法**。

### ⑥ 「默认支付方式扣一笔」有零件、没链路

- 免密扣款入口存在：`rpc/.../paysign/PaySignClient.java:116` 的 `requestPay` → `POST /ci/app/requestPay`，
  DTO `model/.../app/RequestPayReqDTO`。现有调用方是 gate-txn-pay-server 的出站扣费。
- **daily-ticket-server 没有任何 `PaySignClient.requestPay` 调用**；该模块的支付走自己的购票网关
  （`client/DailyTicketPayGatewayClient`，银商 RSA2），与闸机扣费链路完全隔离。
- 「默认支付方式」在库里**不是** `APP_USER_PAY_CHANNEL` 上的 flag（该表主键
  `(THIRD_USER_ID, CARD_TYPE, CHANNEL)`，**无 `DEFAULT_FLAG` 列**），而由
  `USER_ITP_REG_INFO` 的 `CHANNEL` / `THIRD_PAY_ID` / `REQ_CONTRACT_NO` 三字段表达（IF8A-77 换默认支付方式写这三个）。

### ⑦ 后付费单程票（0441）侧：只搬运，且有一处反向功能

| 环节 | 位置 |
|---|---|
| 落交易明细 | `ticket-server/.../gate/GateTxnAssembler.java:41` → `QRCODE_TXN_DETAIL.OVERTIME_AMOUNT` |
| 落扣费表 | `gate-txn-pay-server/.../service/impl/GateTxnPayServiceImpl.java:235` → `GATE_TXN_PAY.OVERTIME_AMOUNT` |
| 进扣款总额 | `TOTAL_AMOUNT = 实扣 + 超时费` |
| IF8A-41 账单统计 | `GateTxnPayMapper.xml:457` 的 `totalOvertime` → `model/app/TripDataDTO.java:13` |
| 对账 `ITP.DETAIL` | `gate-txn-pay-server/.../mapper/ReconExportMapper.xml:93`：`NVL(OVERTIME_AMOUNT,0) > 0 AND CARD_TYPE IN ('0445','0446','0447','0448')` |
| **反向功能：综管台批量退超时罚金** | `GateTxnPayPageController.java:62` `GET /page/gate-txn-pay/overtime-refundable`、`:72` `POST /page/gate-txn-pay/batch-refund-overtime`（≤200 笔）；前端 `web/src/views/trans/overtime-refund/index.vue` |

两条补站链路的 `overtimeAmount` **硬编码 `"0"`**：`SupplementGateRequestAssembler.java:28`（IF8A-04 自助补站
`/ci/app/requestExcessFare` 与 IF5A BOM 补站共用）。IF8A-04 的规格参数表（TS9 表101 / R6 表11 逐字一致）
**没有任何金额字段**，只有 `thirdUserId` / `cardId` / `cardType` / `upgradeAreaType` / `upgradeStationCode` /
`upgradeReason` / `upgradeDateTime` —— 它是**状态修复**接口，不是补收接口。

---

## 三、业务规则（目标态）

四类「超时」**MUST 分开实现，NEVER 合并**：

| # | 场景 | 卡种 | 金额来源 | 处置 |
|---|---|---|---|---|
| A | 多日计次票行程超时 / 超程 | `0446`~`0448`（计次类） | 闸机送 `overtimeAmount` = 线网最高票价 | 扣 2 次；不足 2 次则扣 1 次 + 扣款 |
| B | 后付费单程票超时出站 | `0441` | 闸机送 `overtimeAmount` | 并入 `TOTAL_AMOUNT` 免密扣款（**现状即如此，不改**） |
| C | 离线码超时 | `0441` + 离线渠道 | **ITP 自算** | 单列 `OVERTIME_AMOUNT`、不参与优惠（**现状如此，但阈值/金额待裁决**） |
| D | 有效期超时（票卡失效） | 全部日票类 | 无金额 | 不出码 / 不开闸 / 未激活自动退票（**与超时费无关**） |

### 3.1 场景 A：多日计次票超时扣次（本次新建的主体）

**触发判据**（白名单，**NEVER 写成「非正常即超时」**）：

```
trxType ∈ {"03"}                                  ← 闸机判定的超时出站
AND CardTypeCodeEnum.isDailyTicket(resolvedCardType)
AND 该票为计次类（有 ACTUAL_TIMES 语义）
```

**处置顺序**（MUST 按此序，理由见 §八）：

1. **正常次数**：调 `markUsed` 扣 1 次。失败即终止，不进入超时处置（**NEVER 在没扣掉正常次数时就去扣超时次数**）。
2. **判定剩余次数**：`markUsed` 应答 **MUST 回传扣减后的 `remainTimes`**（现应答不带，见 §四 4.2）。
3. **分支**：
   - `remainTimes >= 1` ⇒ **再扣 1 次**作为超时费，两次扣减合计 2 次。
   - `remainTimes == 0` ⇒ **不再扣次**，改走「按线网最高票价用默认支付方式扣一笔」（§3.2）。
4. **出向通知**：§3.63 `receiveCountingTicketTimes` 的 `times` 传**本次实际扣减的总次数**（1 或 2）。
   ⚠️ **这会推翻 AGENTS.md §2.2.2 现记的「`times` 恒传 1」口径**，且那条明确写着
   「**一旦 `times` 可能为 2，宿主必须改成 daily-ticket 发通知**」—— 因为只有 daily-ticket 知道实际扣了几次。
   **实现场景 A MUST 同批把通知宿主从 `GateDailyTicketCoordinator` 迁到 daily-ticket 侧，并更新 AGENTS.md 那三条口径。**

**幂等**：同一 `(cardId, ticketTransSeq)` 重复上送 **MUST** 只扣一次（闸机有重试机制，规格明确
「闸机应有重试机制，重试至服务器响应」）。**MUST** 用唯一索引 + cause 链判定兜底，**NEVER 靠查询后判断**。

### 3.2 场景 A 的降级：次数不足时扣款

- 金额 = **线网最高票价**（§七 Q1 未闭合前**不可编码**：para-server 没有这个参数）。
- 支付方式 = `USER_ITP_REG_INFO` 的 `CHANNEL` / `THIRD_PAY_ID` / `REQ_CONTRACT_NO` 三字段确定的默认通道。
- 扣款走 `PaySignClient.requestPay`（`POST /ci/app/requestPay`）。
- **MUST 落一张自己的超时费单**（§五），扣款失败进「落库状态 + 补偿端点」而**不是**当场重试：
  这条链路在闸机出站的热路径上，**NEVER 让乘客等支付中心响应**。
- 扣款失败**不阻塞开闸**：闸机侧已放行，ITP 只能事后追缴。

### 3.3 场景 C：离线码超时（现状保留 + 待裁决）

现状是「> 1200 秒 ⇒ 固定 300 分」，甲方无出处。**闭合 Q2 前 MUST 保持现状不动**，
但 **MUST** 把两个配置键的「无规格出处」写进 `docs/ops/生产环境清单.md`。
若甲方裁定统一为「线网最高票价」，则 `FareCalculator` 的 `offlineTimeoutFeeCents` 改为从 para-server 取值，
**MUST 带本地兜底**（para 不可达时不能把超时费算成 0，也不能让整条出站扣费失败）。

### 3.4 场景 D：有效期超时（现状已实现，仅列出以防混淆）

`超出有效期后将卡号设置为已失效，并清空剩余次数，无法生成二维码` /
`未激活的票卡将在车票超过激活后的使用有效期+1日后自动退票`。
**这条不产生任何超时费**，`ACTUAL_TIMES` 是被清空而不是被扣减。

---

## 四、接口契约

### 4.1 入向：IF1A-01 闸机检票通知（**契约不变，只改服务端行为**）

`POST /itpagm/ci/agm/notiVerifyResult`（fep-dev-server → ticket-server）

**NEVER 给这个 DTO 加字段** —— `NotifyVerifyResultReqDTO` 能被 `parseBizData` 解析，是对外契约
（判据见 `docs/domain/README.md`）。超时处理所需的三个输入**已经全在报文里**：

| 字段 | 类型 | 说明 | 本域用法 |
|---|---|---|---|
| `trxType` | String | `01` 进站 / `02` 出站 / `03` 超时出站 | **`03` 即超时触发条件** |
| `overtimeAmount` | String | 超时金额（单位分） | 场景 A 的金额基数（闸机送线网最高票价） |
| `ticketTransSeq` | String | 票卡交易序列号 | 幂等键 + §3.63 的 `transSeq`（**NEVER 取 `QRCODE_STATUS.TXN_SEQ`**） |

服务端**唯二**行为改动：
- `GateDailyTicketCoordinator:125` 的 `EXIT.getCode()` 改为 `TrxTypeCodeEnum.isExitTxn(...)`（**放开 `03`**）。
- `GateCardTypeEnricher:158` 的 `setOvertimeAmount("0")` **只在非超时出站时执行**。

### 4.2 内部：`markUsed` 扩展（**改签名，daily-ticket-server**）

`POST /ci/daily-ticket/ticket/markUsed`

请求（现为裸 `Map`，**本次 MUST 改成 `model` 模块的具名 DTO**，理由见 §八）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `cardNum` | String | Y | 逻辑卡号 |
| `inStation` / `outStation` | String | N | 进出站编码（现有） |
| `countingEnd` / `orderNo` | Long / String | N | 现有字段，保持 |
| `ticketTransSeq` | String | **Y（新增）** | 幂等键，闸机上送的票卡交易序列号 |
| `overtime` | String | **Y（新增）** | `0` 正常出站 / `1` 超时出站 |
| `overtimeAmount` | Long | N（新增） | 超时金额（分），`overtime=1` 时必填 |

应答（`DailyTicketBaseResult` 扩展）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` / `retMsg` | String | 现有 |
| `deductedTimes` | Integer | **本次实际扣减次数（1 或 2）**，用于 §3.63 的 `times` |
| `remainTimes` | Integer | 扣减后剩余次数 |
| `overtimeFeeOrderNo` | String | 走了扣款分支时的超时费单号，否则 null |

⚠️ **`model` 的 DTO 加字段后，MUST 重建链路上每一个经手它的模块镜像**（ticket-server + daily-ticket-server，
必要时含 fep-dev-server）—— `model` 版本号恒为 2.0.0，旧镜像里是旧 class，Fastjson2 会**静默丢字段、不报错**。

### 4.3 内部：超时费扣款补偿端点（新增，daily-ticket-server）

按本项目定式，**补偿不用 `@Scheduled`，由 web-admin `sys_job` 触发**（新增任务 MUST 先读
`docs/architecture/web-server.md` §七）：

- `POST /internal/daily-ticket/overtime-fee/compensate-pay` — 重投未成功的超时费扣款
- `POST /internal/daily-ticket/overtime-fee/compensate-query` — 回查扣款结果

两个端点 **MUST 有鉴权**（§5.2 红线）。返回体沿用 `DailyTicketBaseResult`。

### 4.4 出向：§3.63 多日票次数扣减通知（**已实现，本次改口径**）

`POST <app.notify.counting-ticket-times-url>` → `/app/receiveCountingTicketTimes`

字段不变（`thirdUserId` / `cardId` / `transSeq` / `times`），但：
- `times` 由恒 1 改为 `deductedTimes`（1 或 2）；
- 宿主由 `ticket-server` 的 `CountingTicketTimesNotifier` 迁到 daily-ticket 侧（§3.1 第 4 步）。

### 4.5 出向：§3.64 接收签约异常状态通知（**本次不实现**）

`/app/receiveAgreementException`。接口说明是「ITP 扣费失败后判断出是签约异常，把异常信息通知 APP」，
与 §3.2 的「默认支付方式扣款失败」天然相关，**但规格表131 的参数是逐字复制 §3.63 的四个字段、
连 `times`「扣减次数（最大值为2）」都照搬**，与接口语义明显不符。
**MUST 先向甲方澄清真实字段（Q6），NEVER 照抄那张表。**

---

## 五、数据模型

### 5.1 新增表 `DAILY_TICKET_OVERTIME_FEE`（daily-ticket-server）

超时费**必须有独立载体**，理由三条：①§3.2 的扣款要能补偿，而补偿需要落库状态；
②对账要能按「超时的行程」出 `ITP.DETAIL` 行；③`DAILY_TICKET_INSTANCE` 是票实例、不是流水，
挂不住「第 N 次行程超时」（1:N 子实体不能当父状态机的状态，判据见 `docs/domain/README.md`）。

列形状按 `docs/domain/outbox.md` 的四列模板：

| 列 | 类型 | 说明 |
|---|---|---|
| `ID` | VARCHAR2 | 主键 |
| `CARD_NUM` | VARCHAR2 | 逻辑卡号 |
| `TICKET_TRANS_SEQ` | VARCHAR2 | 票卡交易序列号，**幂等键** |
| `HANDLE_TYPE` | VARCHAR2(1) | `1` 扣次 / `2` 扣款 |
| `OVERTIME_AMOUNT` | NUMBER | 超时金额（**分**，与 `GATE_TXN_PAY.OVERTIME_AMOUNT` 同单位） |
| `PAY_STATUS` | VARCHAR2 | `INIT` / `PAYING` / `PAID` / `FAIL`（对齐本模块现有字面量） |
| `RETRY_COUNT` / `NEXT_RETRY_TIME` / `LAST_ERROR` | NUMBER / DATE / VARCHAR2 | outbox 四列 |
| `IN_STATION` / `OUT_STATION` / `HANDLE_TIME` | VARCHAR2 / DATE | 对账需要 |
| `CREATE_TIME` / `UPDATE_TIME` | DATE | |

唯一索引：`UK_DTOF_TRANS_SEQ ON DAILY_TICKET_OVERTIME_FEE(CARD_NUM, TICKET_TRANS_SEQ)` —— 幂等靠它，
**MUST 配 cause 链 `isConflict(Throwable)` 兜底**（裸 `catch (DuplicateKeyException)` 在开 tracing 的模块会失效，
daily-ticket-server **正在** tracing 名单里）。

### 5.2 迁移脚本（硬性要求）

**MUST 出独立 `daily-ticket-server/src/main/resources/sql/daily-ticket-overtime-fee-migration.sql`**，
**NEVER 只改 `daily-ticket-server-schema.sql`** —— 后者只服务「新建库」，对已存在的 `AFCITPDB` 等于没写。
建完 **MUST 同一次会话内在 `AFCITPDB` 执行并用 `USER_TABLES` / `USER_INDEXES` 回查，把回查结果写进 ADR**。
建唯一索引前 **MUST** 先按索引确切谓词比对 `COUNT(*)` vs `COUNT(DISTINCT 键)`（`ORA-01452` 防线）。

### 5.3 不新增的东西

- **NEVER** 给 `DAILY_TICKET_INSTANCE` 加「超时次数」列。
- **NEVER** 抽一张跨域的公共 outbox 表（违反域边界判据 3，见 ADR-D46）。
- **NEVER** 复用 `SUPPLEMENT_ORDER`（face-pay-server 的支付补款单，面向订单笔数，
  无超时时长 / 免费时限 / 费率任何列，且属 TVM/BOM 域）。

---

## 六、对账口径

`ITP.DETAIL` 侧（recon-server + daily-ticket-server 源）：

- 超时行 = `交易类型=出站`，第 5 段 `交易金额` 填超时费（**不是 `TOTAL_AMOUNT`**）。
- 数据源改为 `DAILY_TICKET_OVERTIME_FEE`（现在是 `GATE_TXN_PAY` 按 `OVERTIME_AMOUNT > 0` +
  `CARD_TYPE IN ('0445'..'0448')` 圈单，见 `ReconExportMapper.xml:93`）。
- **刻意不加 `PAY_STATUS` 过滤**：超时费是否结清与是否要报账是两件事（沿用
  `docs/business/recon.md:428` 已确立的口径）。
- 金额钳制（「线网最高票价或者1」）**Q1 + Q3 闭合后才能实现**，当前 B9 未闭合项照旧。

---

## 七、待甲方澄清（**3 项标 ★ 的不闭合无法编码**）

| # | 问题 | 影响 | 现状证据 |
|---|---|---|---|
| **Q1 ★** | 「线网最高票价」取值来源与维护方式：是 para-server 新增参数、还是由 ACC 参数文件下发、还是运营后台配置？ | §3.2 的扣款金额、§六 的对账钳制全部悬空 | 代码零命中（§二⑤），para-server 无该参数、无 RPC 方法 |
| **Q2 ★** | 离线码超时的**阈值与金额**：现实现是 1200 秒 / 300 分，甲方文档零出处。是否统一为「线网最高票价」？超时阈值取多少？ | §3.3 能否动现有配置 | 规格只写「计算行程票价与是否超时」，无阈值无金额（§二④） |
| **Q3 ★** | `ITP.DETAIL` 第 5 段「交易金额」的**单位**：ACC 文件文档无单位 / 日票 ACC 文档写「（元）」/ 同文档 `ITP.EXP` 写「（单位分）」。且「线网最高票价**或者1**」里的「1」是 1 元还是 1 分？「或者」的二选一条件是什么？ | 对账文件金额可能差 100 倍 | 三处口径不一致（§1.3） |
| Q4 | `ITP.EXP` 第 11 段「订单异常类型」用哪套编码：甲方 1~15（`5` = 乘客自主补进站、无「超时」项）还是 IF8A-05 的 `0`~`5`（`5` = 双段计费正常订单_行程超时）？ | EXP 圈单条件；`'0'` 会被误捞 | 规格自身两套编码撞号（§1.4） |
| Q5 | 「超程」的判定归属：规格把「超程或超时」并列成同一分支，但超程（乘客坐超区间）与超时（在站时间过长）是两件事。是否都由闸机判定后统一用 `trxType=03` 表达？ | 是否需要区分两种 `HANDLE_TYPE` | 原文 `交易超程或超时`，未定义任何判定（§1.1） |
| Q6 | §3.64 `/app/receiveAgreementException` 的真实请求字段 | §4.5 能否实现 | 表131 逐字复制 §3.63，与接口语义不符 |

**另有一条内部裁决**（不是甲方问题）：本篇按「闸机送 `overtimeAmount`、ITP 只做扣次/扣款判定」设计，
与「ITP 侧自行计算超时费」是**两种不同方案**。规格明确是前者（§1.2 第 2、3 条），
若要改为后者，则需先闭合 Q1 + Q2。注意 `docs/business/ride-code.md:383` 与 `:954` 那两条契约
（「`overtimeAmount` 恒为 `"0"`：ITP 侧不算超时费，超时费只由闸机 / AGM 通过 `trxType=03` 上送」）
**约束的是两条补站链路（IF8A-04 / IF5A-03），本域不改它们、无需推翻**；
但那句话里「ITP 侧不算超时费」的**论断范围** MUST 收窄为「补站链路不算」——
离线码（场景 C）本来就是 ITP 自算，已是那句话的既有例外。

---

## 八、编码约束（本域特有 + 复用的红线）

1. **NEVER 给 `NotifyVerifyResultReqDTO` / `RequestExcessFareReqDTO` 加字段** —— 能被 `parseBizData`
   解析的就是对外契约。超时处理所需输入已全在报文里（§4.1）。
2. **`markUsed` 改成具名 DTO 并放 `model` 模块** —— 现在是裸 `Map`，加字段无编译期保护，
   漏改一端就静默丢值。改完 **MUST 重建 ticket-server + daily-ticket-server 两个镜像**。
3. **扣次与扣款 NEVER 放在同一个事务里**，扣款是 RPC。`@Transactional` 内发 RPC 已在本项目造成过生产事故
   （2026-08-26，行锁持有 8 分钟 + 证据 INSERT 一起回滚）。**MUST** 短事务落库 + `afterCommit` 出网。
4. **状态机用白名单**：只放行 `trxType=03` + 计次票 + 票状态在允许集合内，其余一律拒绝。
   **NEVER 写「非终态即可处理」。**
5. **幂等靠唯一索引 + cause 链判定**。daily-ticket-server 在 tracing 名单里，
   裸 `catch (DuplicateKeyException)` 会被观测切面换掉异常类型而失效。
6. **SQL 正文 NEVER 写注释**；mapper XML 的 `<!-- -->` 内 **NEVER 出现两个连续减号**（会导致整个服务启动即挂）；
   可选谓词 **MUST 用 `<where>` 标签**，NEVER `where 1 = 1`。新增 XML 后 **MUST** 跑 `xmllint --noout`。
7. **新增补偿端点 MUST 有鉴权**；新增出向地址键 **MUST 写 `${ENV:}` 空默认值 + 默认关**，
   **NEVER 再新增一处 `testngbackV2` 硬编码**。
8. **改动 §3.63 的 `times` 口径时，MUST 同批更新 AGENTS.md §2.2.2 那三条 NEVER**
   —— 那里现在写着「`times` 恒传 1」，本域一旦落地就与之矛盾。

---

## 九、实施计划（按阻塞关系分阶段，S0 可立即开工）

> **排序原则**：把「不依赖任何澄清、能独立上线」的止血改动拎到最前；把三项 ★ 阻塞项的澄清作为**并行的对外动作**，
> 不让它挡住 S0；把「会改变对账输出」的改动与对账验证**绑在同一阶段**，避免静默改变账期产物。

### S0 — 止血：让超时出站至少扣 1 次（**零依赖，可立即开工**）

- **改动**（ticket-server，2 处）：
  - `gate/GateDailyTicketCoordinator.java:125`：`TrxTypeCodeEnum.EXIT.getCode().equals(...)` → `TrxTypeCodeEnum.isExitTxn(request.getTrxType())`。
  - 同方法 Javadoc `:123` 的「仅出站 trxType 触发」补一句「含 `03` 超时出站」。
- **刻意不做**：`GateCardTypeEnricher:158` 的 `setOvertimeAmount("0")` **本阶段不动**。
  放开它会立刻改变对账输出 —— `ReconExportMapper.xml:93` 的 `ITP.DETAIL` 圈单条件正是
  `NVL(OVERTIME_AMOUNT,0) > 0 AND CARD_TYPE IN ('0445'..'0448')`，一旦闸机送的线网最高票价落进库里，
  当期 `ITP.DETAIL` 会凭空多出行、且金额单位口径（Q3）还没定。**MUST 留到 S4 与对账一起验。**
- **口径不变**：本阶段仍是扣 1 次，因此 §3.63 的 `times` **恒传 1 不变**，
  AGENTS.md 那三条 NEVER **本阶段不用改**。
- **单测**（ticket-server 已有测试目录）：加 `trxType="03"` + 计次票 ⇒ 断言 `dailyTicketClient.markUsed` 被调用一次、
  `notifyCountingTicketTimes` 收到 `times=1`；并保留一条 `trxType="01"` 不触发的反例。
- **验证**：`mise exec -- mvn test -pl ticket-server` → 部署后造一笔 `trxType=03` 出站，
  查 `DAILY_TICKET_INSTANCE.ACTUAL_TIMES` 是否 -1、`TICKET_STATUS` 是否推进。
- **交付边界**：S0 单独可上线，且**把「免费乘车」缺口从 100% 收到 50%**（应扣 2 次、现扣 1 次）。

### S1 — 澄清（**对外并行，不阻塞 S0**）

把 §七 六项按优先级发出，**Q1 / Q2 / Q3 未回不得进 S3 之后**：

- 给甲方：Q1 线网最高票价的取值来源与维护方式、Q2 离线码阈值与金额、Q3 `ITP.DETAIL` 金额单位与「或者1」、
  Q4 订单异常类型用哪套编码、Q5 超程判定归属。
- 给 APP 侧：Q6 §3.64 `receiveAgreementException` 的真实字段。
- 同时把 §二④ 那两个「无规格出处」的配置键补进 `docs/ops/生产环境清单.md` 的问题章节。

### S2 — 数据与参数基建（依赖 Q1）

- **新表**：`daily-ticket-server/src/main/resources/sql/daily-ticket-overtime-fee-migration.sql`
  （`DAILY_TICKET_OVERTIME_FEE` + `UK_DTOF_TRANS_SEQ`，列形状见 §5.1）。
  **MUST 同一次会话内在 `AFCITPDB` 执行 + `USER_TABLES` / `USER_INDEXES` 回查 + 回查结果写进 ADR**；
  同步把建表语句补进 `daily-ticket-server-schema.sql`（两处都要，缺一不可）。
- **线网最高票价参数**：按 Q1 结论落地。若裁定进 para-server，则是「表 / 参数项 + `/ci/para/**` 只读端点 +
  `ParaClient` 补方法（**返回 `RpcOutcome`，NEVER 返 boolean**）+ `service.para.url` 在 daily-ticket 侧补键」四件套。
  **MUST 带本地兜底**：para 不可达时**不能把超时费算成 0，也不能让出站链路整体失败**。
- **不做**：暂不接线到任何业务分支，本阶段只让「表在、参数取得到」。

### S3 — 扣次改造：实现「扣 2 次 / 不足则降级」（依赖 S2 + Q1 + Q5）

- **`model`**：`markUsed` 请求改具名 DTO（新增 `ticketTransSeq` / `overtime` / `overtimeAmount`），
  `DailyTicketBaseResult` 加 `deductedTimes` / `remainTimes` / `overtimeFeeOrderNo`。
  **NEVER 升 `model` 版本号**，改完 `mvn install`。
- **`rpc`**：`DailyTicketClient.markUsed` 换成收该 DTO，**NEVER 升 `rpc` 版本号**。
- **daily-ticket-server**：`DailyTicketServiceImpl.markUsed` 内实现 §3.1 四步；
  `decreaseActualTimes` **保持 `- 1` 不变**，「扣 2 次」= 调两次 + 各自判影响行数
  （**NEVER 改成 `- 2`** —— 那样无法表达「第二次没扣到」，也拿不到中间的 `remainTimes`）。
- **ticket-server**：`markUsedOnExit` 传 `overtime` 标志；§3.63 通知宿主**迁到 daily-ticket 侧**、
  `times` 改传 `deductedTimes`。
- **镜像**：`model` 改了 DTO ⇒ **MUST 同批重建 ticket-server + daily-ticket-server 两个镜像**
  （Fastjson2 会静默丢字段，只重建一端等于白改）。
- **同批文档**：更新 AGENTS.md §2.2.2 §3.63 那三条 NEVER 的第一条 + `docs/business/daily-ticket.md` 扣次那节。
- **单测**：`remainTimes>=1` 扣 2 次 / `remainTimes==0` 扣 1 次 + 出降级单 / 重复 `ticketTransSeq` 只扣一次（幂等）
  / 正常出站（`overtime=0`）仍扣 1 次不产生超时费单。

### S4 — 扣款降级链路 + 放开 `overtimeAmount` + 对账接入（依赖 S3 + Q3）

- **扣款**：daily-ticket-server 接 `PaySignClient.requestPay`；补 `service.paySign.url` 键；
  **短事务落 `DAILY_TICKET_OVERTIME_FEE` → `afterCommit` 出网**，**NEVER 把 RPC 包进事务**。
- **补偿**：两个 `/internal/daily-ticket/overtime-fee/**` 端点（**MUST 带鉴权**）+ web-admin 两条 `sys_job`
  （新增任务前 MUST 读 `docs/architecture/web-server.md` §七；**改完 `sys_job` MUST 重启 web-admin**，
  内存 JobStore 下直接 INSERT 不生效；判断跑没跑 **MUST 查 `SYS_JOB_LOG`**）。
- **放开 `overtimeAmount`**：`GateCardTypeEnricher:156~159` 改成「只在**非**超时出站时清 `overtimeAmount`」，
  `trxAmount` 照旧恒清 0。
- **对账**：`ITP.DETAIL` 数据源改挂 `DAILY_TICKET_OVERTIME_FEE`；按 Q3 结论定金额单位与钳制。
  **验证 MUST 跑一次完整账期**（`sys_job` 109 → 批次收口 `SUCCESS` → 看 recon-server 的
  「对账文件投递已回查通过」日志），并与改动前的产物逐行比对差异。

### S5 — 离线码口径收敛（依赖 Q2，**可能是空操作**）

Q2 若裁定维持现状 ⇒ 只更新文档；若裁定统一为线网最高票价 ⇒ `FareCalculator` 的
`offlineTimeoutFeeCents` 改为从 para 取值 + 本地兜底，并重建 gate-txn-pay-server 镜像。

### S6 — §3.64 签约异常通知（依赖 Q6，可与 S4 并行）

字段确定后按 `ticket/notify/` 包的 `FormDataNotifyTemplate` 定式落地；
新增 `app.notify.agreement-exception-url` / `-enabled` 两个键（**`${ENV:}` 空默认 + 默认关**）。

### 构建与部署备注（本次实测）

- **`ticket-server` 与 `daily-ticket-server` 的 jkube 都在 `remote` profile 内、`activeByDefault=true`、
  `<phase>package</phase>`、goals `build` + `push` 均未注释**（`ticket-server/pom.xml:178`、
  `daily-ticket-server/pom.xml:107`），因此**两者 `mvn clean package` 就会直接推镜像到 Harbor**，
  不需要 `-Premote`。只想拿 jar **MUST** 加 `-Djkube.skip=true`。
  （`ticket-server` 另有一段被整体注释掉的 `build-image-after-package`，**勿被它误导**。）
- 版本号按需 +0.0.1，但 **MUST 每次现查 pom 与 Deployment**，**NEVER 引用文档里的版本数字**。
- 每阶段部署后 **MUST** 探活 `curl http://172.20.211.23:<NodePort>/actuator/health`
  （单次 503 不算失败，间隔 30 秒复探），**NEVER 只看 `rollout status`**。

### 阶段依赖速览

- S0 → 无依赖
- S1 → 无依赖（对外）
- S2 → Q1
- S3 → S2 + Q1 + Q5
- S4 → S3 + Q3
- S5 → Q2（独立）
- S6 → Q6（独立）

---

## 参考原始文档

| 文档 | 相关章节 |
|---|---|
| `docs/业务需求文档/青岛地铁虚拟电子多日计次票、离线码、实时客流上传功能方案V1.docx` | §一4 计费规则（**唯一权威超时条款**）、§一5 对账改造、§二4 离线码计费规则、§二5 异常处理 |
| `docs/技术规范文档/城市轨道交通自动售检票系统技术规范-第9部分-互联网业务规范.docx` | §7.4 IF1A-01（`overtimeAmount` 定义）、§7.6 IF8A-04 / IF8A-05、§5.4 优惠累计排除项 |
| `docs/接口规范文档/青岛地铁-ITP与APP接口规范R6.docx` | §2.1 请求自助补站流程、§3.4 IF8A-04、§3.63 / §3.64 |
| `docs/接口规范文档/ACC与ITP之间的文件.docx` | §一（1）`ITP.EXP` 订单异常类型、§一（4）`ITP.DETAIL` 超时费 |
| `docs/接口规范文档/青岛地铁日票-ITP与ACC交互文档.docx` | §一 对账文件（**金额单位写「元」，与上一份冲突**） |

## 相关提示词

- `docs/business/daily-ticket.md` — 扣次现状、`DAILY_TICKET_INSTANCE` 状态字面量
- `docs/business/ride-code.md` — `overtimeAmount` 恒 0 的现行不变量（**本域会推翻它**）
- `docs/business/gate-txn-pay.md` — 0441 侧超时费搬运与离线码算价
- `docs/business/recon.md` — `ITP.DETAIL` 行格式与 B9 未闭合项
- `docs/domain/outbox.md` — 超时费单的 outbox 四列模板与扫表 SQL 的坑
- `docs/architecture/web-server.md` §七 — 新增 `sys_job` 定时任务的定式
