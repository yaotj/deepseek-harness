# 04 — fep-alipay-server（支付宝出行 / 碰一下渠道网关）

> 网关前缀 `/fep-alipay/`，rewrite 成 `/`（前缀被吃掉）；目标 Service `fep-alipay-rec8g-svc:30020`。
> 模块 `server.port=8080`、`spring.application.name=fep-alipay`、**无 context-path**、**无数据库**（纯转发网关）。
> form 端点的公共报文字段（`sign` / `charset` / `format` / `timestamp` / `deviceId` / `signType` / `bizData`）见 [README.md](README.md) §2，本文件不再逐端点重复。

## 0. 本模块的测试前提（写用例前必读）

1. **本模块不验签**：整个 `fep-alipay-server` 里没有任何验签代码，`sign` 原样透传（`ItpCommonFormRequest.toString` 对 `sign` 恒定脱敏，但值本身不参与任何校验）。因此 form 端点可以送**任意 `sign` / `signType`**，压测造数不需要真签名。
2. **本模块无数据库**：`application.properties` 里没有任何数据源配置，13 个端点全部是「解析 bizData → 一次 RPC → 原样回传」。**压测本模块只压转发与 WebClient 连接池，落库压力全在下游**（`alipay-pay-sign-server` / `alipay-account-server` / `trans-query-server` / `blacklist-server` / `industry-data-server`）。
3. **`/admin/payment/**` 那 4 个从命名看是运营侧入口，但同样挂在 `/fep-alipay/` 前缀下、公网可达、且无任何鉴权**。其中 `requestRefund`（发起退款）与 `addBlackList`（加黑名单）、`executeTermination`（执行解约）都是**状态变更型**接口。安全测试 MUST 覆盖「未授权直接调用」这一项；压测也 MUST 覆盖，因为它们与业务端点共用同一个线程池与 WebClient。
4. **`/memberContract/channel/requestIndustryData` 与 `/channel/requestIndustryData` 是同一段后端逻辑的双 URL 入口**（两个 Controller 各自解析后都调 `AlipayTripServiceImpl.requestIndustryData` → `AlipayApplicationServiceImpl.requestIndustryData`）。测试 MUST 对两条路径各跑一遍并比对应答逐字一致；两者日志前缀不同（后者带 `(MemberContract)`），可用来确认落到哪个入口。
5. **`findTravelList` / `findTravelDetail` 的下游是 `trans-query-server`，不是 `ticket-server`**。2026-09-20 实测该链路在承接真实流量：`/channel/findTravelList` → `http://trans-query-57wpd-svc.itp.svc:30035/ci/alipay/travel/list`，`trans-query-server` 再回调 `alipay-pay-sign-server` 批量补齐支付明细。注意 **`trans-query-server` 的三条 URL 与 `ticket-server` 的 `TicketTransController` 逐字一致、并存未替换**，同一业务语义存在两条链路（APP 域经 `fep-app` 仍走 ticket-server），**用例 MUST 在标题或备注里标明走的是哪条**，否则结果无法归因。
6. **入参形态三种混用**，逐端点不同，**NEVER 按「本模块都是 form」批量生成脚本**：`@ModelAttribute ItpCommonFormRequest`（8 个）/ `@RequestBody`（2 个）/ `@RequestParam Map`（1 个）/ 裸 `@RequestParam`（1 个，无 body）。
7. **JSON 端点与 query 端点没有公共报文骨架**（没有 `sign` / `bizData` 那一套），送公共字段会被直接忽略或落到错误位置。逐端点已在「请求字段」标题下明写。
8. **所有 DTO 都没有 Bean Validation 注解**（`model` 模块的 `alipaytrip` / `app` 包内零 `@NotNull` / `@NotBlank`），因此**空值不会返 400**，只会走 Controller 或 Service 里手写的 `StringUtils.hasText` 判断。校验分支逐端点已列。

## 1. 全局异常处理器（决定失败断言怎么写）

`fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/exception/GlobalExceptionHandler.java`（`@RestControllerAdvice`，共 29 行，只有两个分支）：

| 分支 | 触发条件 | 应答类型 | `retCode` | `retMsg` |
|---|---|---|---|---|
| `handleBusinessException`（`:13~19`） | 抛出 `BusinessException` | `AlipayCommonResponse` | 异常自带的 `code` | 异常自带的 `msg` |
| `handleException`（`:21~27`） | 其余任何 `Exception` | `AlipayCommonResponse` | `FepAppErrorCodeEnum.SYSTEM_ERROR` = **`9001`** | **`系统内部错误`**（中文，**不是** 英文 `system internal error`） |

两条必须记的事实：

- **`BusinessException`（`exception/BusinessException.java`）在本模块的 `src/main` 里没有任何抛出点**，只有类定义本身；它的单参构造器默认 `code = SYSTEM_ERROR = 9001`。因此实际能命中的分支基本只有第二个。
- **异常分支返回的是 `AlipayCommonResponse`（只有 `retCode` / `retMsg` 两字段），与正常应答的 DTO 形状不同** —— 正常应答多数还带业务字段。断言 MUST 容忍「异常时字段缺失」，**NEVER 断言业务字段恒存在**。
- HTTP 状态码仍是 200（`@RestControllerAdvice` 未声明 `@ResponseStatus`），判成功 **MUST 看 `retCode`**。
- 各 Service 实现里普遍自带 `try/catch(Exception)` 兜底并返 `9999`（`FAIL`），因此**大多数下游异常根本到不了全局处理器**：`retCode=9999` 才是最常见的失败码，`9001` 反而罕见。

## 2. 本文件端点总览

| # | 中文名 | 对外 URL | 入参形态 | 下游服务 |
|---|---|---|---|---|
| 1 | 支付宝出行-添加签约信息 | `POST /fep-alipay/channel/addContract` | `@ModelAttribute` form | alipay-pay-sign-server |
| 2 | 支付宝出行-解约登记 | `POST /fep-alipay/channel/terminateContract` | `@ModelAttribute` form | alipay-pay-sign-server |
| 3 | 支付宝出行-开卡申请 | `POST /fep-alipay/channel/requestApplication` | `@ModelAttribute` form | alipay-account-server |
| 4 | 支付宝出行-获取行业数据 | `POST /fep-alipay/channel/requestIndustryData` | `@ModelAttribute` form | alipay-account-server + ticket-server + industry-data-server（三跳） |
| 5 | 支付宝出行-查询乘车记录列表 | `POST /fep-alipay/channel/findTravelList` | `@ModelAttribute` form | **trans-query-server** |
| 6 | 支付宝出行-查询乘车记录详情 | `POST /fep-alipay/channel/findTravelDetail` | `@ModelAttribute` form | **trans-query-server** |
| 7 | 支付宝出行-获取行业数据（MemberContract 入口） | `POST /fep-alipay/memberContract/channel/requestIndustryData` | `@ModelAttribute` form | 同 #4（同一后端逻辑） |
| 8 | 支付宝出行-支付结果查询 | `POST /fep-alipay/admin/payment/payQuery` | `@ModelAttribute` form | alipay-pay-sign-server |
| 9 | 支付宝出行-退款申请 | `POST /fep-alipay/admin/payment/requestRefund` | `@ModelAttribute` form | alipay-pay-sign-server |
| 10 | 支付宝出行-添加黑名单 | `POST /fep-alipay/admin/payment/addBlackList` | `@RequestBody` JSON | blacklist-server |
| 11 | 支付宝出行-执行解约 | `POST /fep-alipay/admin/payment/executeTermination` | `@RequestParam` query，无 body | alipay-pay-sign-server |
| 12 | 支付中心-支付结果回调 | `POST /fep-alipay/notify/payment/payNotify` | `@RequestParam Map` form | alipay-pay-sign-server |
| 13 | 支付宝出行-业务关闭结果通知 | `POST /fep-alipay/notify/closeResultForAlipay` | `@RequestBody` JSON | alipay-pay-sign-server |

下游地址配置键（`fep-alipay-server/src/main/resources/application.properties`）：

| 配置键 | 行号 | 默认值 | 说明 |
|---|---|---|---|
| `service.account.url` | `:14` | `http://alipay-account-server-2n6kc-svc.itp.svc:30021` | **指的是 alipay-account-server，不是 account-server**；`AlipayAccountClient` 实际读 `${service.alipayAccount.url:${service.account.url:...}}`，本模块没配前者、走的是这条兜底 |
| `service.alipay-pay-sign.url` | `:23` | `http://alipay-pay-sign-server-t3o7n-svc.itp.svc:30022` | `AlipayPaySignClient` |
| `service.blacklist.url` | `:26` | `http://blacklist-server-wmfzs-svc.itp.svc:8080` | `BlacklistClient` |
| `service.ticket.url` | `:29` | `http://ticket-server-bsyju-svc.itp.svc:9100` | `TicketClient`（只被 #4 / #7 的二维码状态查询用） |
| `service.industryData.url` | `:32` | `http://industry-data-server-6dv5u-svc.itp.svc:30018` | `IndustryDataClient` |
| `service.transQuery.url` | `:45` | `http://trans-query-57wpd-svc.itp.svc:30035` | `TransQueryClient`（#5 / #6）。线上以 Deployment env 为准（键名带点） |

---

### 1. 支付宝出行-添加签约信息

- **对外 URL**：`POST /fep-alipay/channel/addContract`
- **容器内路径**：`/channel/addContract`（`/fep-alipay/` 前缀被 istio rewrite 成 `/` 吃掉）
- **Controller**：`FepAlipayTripController#addContract`（`fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/controller/FepAlipayTripController.java:45~59`，类级 `@RequestMapping("/channel")` 在 `:31`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：alipay-pay-sign-server（配置键 `service.alipay-pay-sign.url`，`AlipayPaySignClient.alipayTripAddContract`）
- **校验分支**：
  - Controller `:48` — `request == null || bizData == null` ⇒ `retCode=8001` / `retMsg=无效的参数`（`AlipayTripAddContractRespDTO`）
  - Service `AlipayContractServiceImpl.java:36` — `thirdUserId` 或 `agreementCode` 为空白 ⇒ `retCode=8001` / `无效的参数`
  - Service `:44` — 下游返回 null ⇒ `retCode=9999` / `系统内部错误`；`:51` catch 全部异常 ⇒ 同上

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `channel` | String | 无（代码无 Bean Validation，空值不会 400） | 支付渠道 |
| `thirdUserId` | String | 无（同上；但 Service `:36` 手写非空校验，空则 `8001`） | 用户ID |
| `agreementCode` | String | 无（同上；Service `:36` 手写非空校验，空则 `8001`） | 签约协议号，系统生成的唯一协议编号 |
| `channelAgreementCode` | String | 无 | 渠道协议号，第三方渠道的协议编号 |
| `channelUserAccount` | String | 无 | 渠道用户账户，用户在第三方渠道的账户标识 |
| `cardIssueCode` | String | 无 | 发卡类型代码，如：0007 |

**响应字段** — `AlipayTripAddContractRespDTO extends CommonResult`（无自有字段）

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | 返回码，`0000` 成功 |
| `retMsg` | String | 返回消息 |

**压测备注**：本模块纯转发无落库；**下游 alipay-pay-sign-server 会写签约表**，因此高并发下 MUST 用不重复的 `agreementCode`，否则下游幂等分支会掩盖真实吞吐。不调支付宝开放平台。非回调端点。

---

### 2. 支付宝出行-解约登记

- **对外 URL**：`POST /fep-alipay/channel/terminateContract`
- **容器内路径**：`/channel/terminateContract`
- **Controller**：`FepAlipayTripController#terminateContract`（`.../controller/FepAlipayTripController.java:64~78`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：alipay-pay-sign-server（`service.alipay-pay-sign.url`，`AlipayPaySignClient.alipayTripTerminateContract`）
- **校验分支**：
  - Controller `:67` — `request == null || bizData == null` ⇒ `8001` / `无效的参数`
  - Service `AlipayContractServiceImpl.java:64` — `agreementCode` 为空白 ⇒ `8001` / `无效的参数`
  - Service `:72` 下游返 null、`:79` catch 异常 ⇒ `9999` / `系统内部错误`

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.alipaytrip.AlipayTripTerminateContractReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `agreementCode` | String | 无（代码无 Bean Validation，空值不会 400；Service `:64` 手写非空校验，空则 `8001`） | 协议号，签约时生成的协议编号 |
| `merchantNo` | String | 无 | 合作方机构编号 / 商户号 |

**响应字段** — `AlipayTripTerminateContractRespDTO extends CommonResult`（无自有字段）

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | 返回码，`0000` 成功 |
| `retMsg` | String | 返回消息 |

**压测备注**：本层无落库；**是「登记」而非「执行」**，真正的解约执行在 #11。下游会改签约状态，属状态变更型接口。不调支付宝开放平台。非回调端点。

---

### 3. 支付宝出行-开卡申请

- **对外 URL**：`POST /fep-alipay/channel/requestApplication`
- **容器内路径**：`/channel/requestApplication`
- **Controller**：`FepAlipayTripController#requestApplication`（`.../controller/FepAlipayTripController.java:83~97`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：alipay-account-server（配置键 `service.account.url`，经 `AlipayAccountClient.alipayTripRequestApplication`；该 Client 的 `@Value` 是 `${service.alipayAccount.url:${service.account.url:...}}`，本模块只配了后者）
- **校验分支**：
  - Controller `:86` — `request == null || bizData == null` ⇒ `8001` / `无效的参数`
  - Service `AlipayApplicationServiceImpl.java:51` — `thirdUserId` 或 `cardType` 为空白 ⇒ `8001` / `无效的参数`
  - Service `:59` 下游返 null、`:66` catch 异常 ⇒ `9999` / `系统内部错误`

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `thirdUserId` | String | 无（Service `:51` 手写非空校验，空则 `8001`） | 第三方用户ID，格式化后的用户标识 |
| `cardType` | String | 无（Service `:51` 手写非空校验，空则 `8001`） | 卡片类型，如：02 |
| `msisdn` | String | 无 | 用户手机号码 |
| `extend1` | String | 无 | 扩展字段1 |
| `extend2` | String | 无 | 扩展字段2（可选） |
| `cardIssueCode` | String | 无 | 发卡渠道代码，0007 支付宝出行 |

**响应字段** — `AlipayTripRequestApplicationRespDTO extends CommonResult`

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | 返回码，`0000` 成功 |
| `retMsg` | String | 返回消息 |
| `cardId` | String | 卡片ID / 逻辑卡号，开卡成功后返回 |
| `cardType` | String | 卡片类型 |
| `status` | String | 用户状态，如 `ACTIVE` |

**压测备注**：本层无落库；**下游 alipay-account-server 会写账户表并可能占用卡池卡号**，属真实资源消耗型接口 —— 压测 MUST 与业主确认可用的造数额度，并准备卡号回收方案。不调支付宝开放平台。非回调端点。

---

### 4. 支付宝出行-获取行业数据

- **对外 URL**：`POST /fep-alipay/channel/requestIndustryData`
- **容器内路径**：`/channel/requestIndustryData`
- **Controller**：`FepAlipayTripController#requestIndustryData`（`.../controller/FepAlipayTripController.java:102~116`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：**三跳串行**（`AlipayApplicationServiceImpl.requestIndustryData`，`.../service/impl/AlipayApplicationServiceImpl.java:76~153`）
  1. alipay-account-server 查用户信息（`service.account.url`，`AlipayAccountClient.selectByThirdUserId`，`:156`）
  2. ticket-server 查二维码状态（`service.ticket.url`，`TicketClient.queryQrCodeStatus`，`:165`）
  3. industry-data-server 生成卡数据（`service.industryData.url`，`IndustryDataClient.buildCardData`，`:133`）
- **校验分支**（本端点错误码自成一套，**NEVER 套用 `8001=无效的参数` 那套文案**）：
  - Controller `:105` — `request == null || bizData == null` ⇒ `8001` / `无效的参数`
  - Service `:83` + `validateRequest`（`:204~218`）— `thirdUserId` / `cardId` / `cardType` 任一为空白 ⇒ `retCode=1001`，`retMsg` 分别是 `请求报文不能为空` / `thirdUserId不能为空` / `cardId不能为空` / `cardType不能为空`
  - Service `:91` — 用户信息查不到 ⇒ `9999` / `用户未开户`
  - Service `:97` / `:111` — 用户签约渠道为空或无法映射 ⇒ `8001` / `用户签约渠道不能为空`
  - Service `:103` — 请求 `cardId` 与账户侧 `cardId` 不一致 ⇒ `8002` / `卡号不匹配`
  - Service `:119` — ticket-server 返 null ⇒ `9999` / `ticket-server调用失败，返回为空`；`:125` ticket-server 非 `0000` ⇒ **原样透传下游 `retCode` / `retMsg`**
  - Service `:135` — industry-data-server 返 null 或非 `0000` ⇒ `9999` / `industry-data-server生成卡数据失败`，或原样透传其 `retCode` / `retMsg`
  - Service `:147` catch 全部异常 ⇒ `9999` / `系统内部错误`

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestIndustryDataReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `thirdUserId` | String | 无（代码无 Bean Validation，空值不会 400；Service `validateRequest` 手写校验，空则 `1001`） | 第三方用户ID |
| `cardId` | String | 无（同上，空则 `1001`） | 卡片ID / 逻辑卡号 |
| `cardType` | String | 无（同上，空则 `1001`） | 卡片类型 |

**响应字段** — `AlipayTripRequestIndustryDataRespDTO extends CommonResult`

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | 返回码，`0000` 成功 |
| `retMsg` | String | 返回消息 |
| `cardData` | String | 卡数据 HexString |
| `signType` | String | 签名类型，**Service `:79` 无条件预置为 `00`**（成功与失败分支都带） |
| `sign` | String | **Service `:80` 无条件预置为空串 `""`**，本模块不加签 |

**压测备注**：本模块无落库，但**这是本文件里链路最长的端点（一次请求打三个下游、串行）**，是本模块的响应时间瓶颈候选，压测 MUST 单列一组。`industry-data-server` 内部会调签名服务（加密机链路），**不是纯 CPU 操作**。不直接调支付宝开放平台。非回调端点。另注：`buildCardData` 的 `txnSeq` 由 `nextTxnSeq`（`:174~184`）在二维码状态基础上 +1，**同一 `cardId` 并发请求会拿到相同序列号**，压测同卡号并发时 MUST 预期下游出现重复序列。

---

### 5. 支付宝出行-查询乘车记录列表

- **对外 URL**：`POST /fep-alipay/channel/findTravelList`
- **容器内路径**：`/channel/findTravelList`
- **Controller**：`FepAlipayTripController#findTravelList`（`.../controller/FepAlipayTripController.java:121~135`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：**trans-query-server**（配置键 `service.transQuery.url`，`TransQueryClient.findTravelList`，`AlipayQueryServiceImpl.java:47~63`），容器内目标路径实测为 `/ci/alipay/travel/list`；该服务再回调 `alipay-pay-sign-server` 批量补齐支付明细
- **校验分支**：
  - Controller `:124` — `request == null || bizData == null` ⇒ `8001` / `无效的参数`
  - Service `:52` catch 异常或 `:56` 下游返 null ⇒ `retCode=9999`（`FAIL`）/ `系统内部错误`
  - **Service 层不再做任何字段级校验**，`thirdUserId` 为空也会原样转给下游

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `thirdUserId` | String | 无（代码无 Bean Validation，空值不会 400） | 第三方用户ID，格式化后的用户标识 |
| `page` | String | 无 | 页码，从 0 开始 |
| `size` | String | 无 | 每页大小 |
| `debitRequestResult` | String | 无 | 扣款请求结果筛选（可选）。筛选谓词打在 `GATE_TXN_PAY.DEBIT_STATUS` 上 |
| `invoice` | String | 无 | 发票状态（可选）。**按裁决刻意不参与筛选**：传 `0` / `1` / 不传三种返回逐字一致，**这是预期行为、NEVER 当缺陷报** |
| `startDate` | String | 无 | 开始日期（可选） |
| `endDate` | String | 无 | 结束日期（可选） |

**响应字段** — `AlipayTripFindTravelListRespDTO extends CommonResult`

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | 返回码，`0000` 成功 |
| `retMsg` | String | 返回消息 |
| `pageNumber` | Integer | 当前页码 |
| `pageSize` | Integer | 每页大小 |
| `totalPage` | Integer | 总页数 |
| `totalCount` | Integer | 总记录数 |
| `ticketTransRecord` | `List<AlipayTripTravelRecordDTO>` | 乘车记录列表，展开见下表 |

**`ticketTransRecord[]` 元素字段** — `com.chinasofti.huateng.model.alipaytrip.AlipayTripTravelRecordDTO`

| 字段 | 类型 | 说明 |
|---|---|---|
| `entryStationName` | String | 进站站点名称 |
| `entryDate` | String | 进站时间 |
| `exitStationName` | String | 出站站点名称 |
| `exitDate` | String | 出站时间 |
| `payAmount` | String | 实付金额（单位：分） |
| `totalAmount` | String | 总金额（单位：分） |
| `orderExpType` | String | 订单扩展类型，**Java 字段默认值 `"0"`** |
| `tradeOrderNo` | String | 交易订单号 |
| `payTradeOrderNo` | String | 支付交易订单号 |
| `payOrderNoDate` | String | 支付订单日期 |
| `debitRequestResult` | String | 扣款请求结果，值域只有 `0`（成功）/ `1`（其余）。唯一数据源是 `GATE_TXN_PAY.DEBIT_STATUS`，**与筛选同源** |
| `companionFlag` | String | 同行票标识 |
| `cardNum` | String | 逻辑卡号 |
| `ticketCode` | String | 日票票号 |
| `countingTimes` | String | 计次次数（预留） |
| `countingFlag` | String | 计次标识（预留） |
| `discountFee` | String | 优惠金额（分）。**恒为空串**（取数范围内两张表都没有渠道优惠金额列），**NEVER 断言有值** |
| `discountInfo` | String | 优惠详情 JSON 数组。**恒为空串**，成因同上 |
| `invoice` | String | 发票状态。透出 `ALIPAY_PAY_TXN_DETAIL.INVOICE`，该列**全库尚无写入方、实际恒为空** |
| `payChannelCode` | String | 支付渠道代码 |

**压测备注**：本模块纯转发无落库；**下游 trans-query-server 是只读查询 + 一次回调 alipay-pay-sign-server 补支付明细**，是典型读放大链路，压测 MUST 同时观察 trans-query 与 alipay-pay-sign 两侧。不调支付宝开放平台。非回调端点。**用例 MUST 标明「本条走 trans-query-server 链路」** —— 同一业务语义在 APP 域经 `fep-app` 走的是 ticket-server 的同名 URL，两条并存。

---

### 6. 支付宝出行-查询乘车记录详情

- **对外 URL**：`POST /fep-alipay/channel/findTravelDetail`
- **容器内路径**：`/channel/findTravelDetail`
- **Controller**：`FepAlipayTripController#findTravelDetail`（`.../controller/FepAlipayTripController.java:140~154`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：**trans-query-server**（`service.transQuery.url`，`TransQueryClient.findTravelDetail`，`AlipayQueryServiceImpl.java:66~82`）。本层**原样转发下游应答、不拆平不重组**
- **校验分支**：
  - Controller `:143` — `request == null || bizData == null` ⇒ `8001` / `无效的参数`
  - Service `:69` catch 异常或 `:75` 下游返 null ⇒ `retCode=9001`（**`SYSTEM_ERROR`，与 #5 的 `9999` 不同，NEVER 混用**）/ `系统内部错误`

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO`（**字段集严格只有两个**）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `thirdUserId` | String | 无（代码无 Bean Validation，空值不会 400） | 第三方用户ID，格式化后的用户标识 |
| `orderNo` | String | 无 | 订单号。现实现按 `orderNo ≡ GATE_TXN_PAY.ORDER_NO ≡ 应答的 tradeOrderNo` 落地 |

> 曾存在的 `handleDateTime` / `trxType` / `cardId` 三个契约外字段**已删除**。Fastjson2 宽松模式会静默丢弃它们，送了不报错、只是无效。

**响应字段** — `AlipayTripFindTravelDetailRespDTO extends CommonResult`（**三层结构：顶层只有两个码 + 一个 `data` 对象**）

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | 返回码，`0000` 成功 |
| `retMsg` | String | 返回消息 |
| `data` | `AlipayTripTravelDetailDTO` | 业务体；**失败分支（参数非法 / 订单不存在 / 系统异常）恒为 `null`**，不会塞空对象 |

**`data` 字段** — `com.chinasofti.huateng.model.alipaytrip.AlipayTripTravelDetailDTO`（20 个字段）

| 字段 | 类型 | 说明 |
|---|---|---|
| `entryStationName` | String | 进站站点名称 |
| `entryDate` | String | 进站时间 |
| `exitStationName` | String | 出站站点名称 |
| `exitDate` | String | 出站时间 |
| `payAmount` | String | 实付金额（单位：分） |
| `totalAmount` | String | 总金额（单位：分） |
| `orderExpType` | String | 订单扩展类型（**本类无默认值，与列表那个 `"0"` 不同**） |
| `tradeOrderNo` | String | 交易订单号 |
| `payTradeOrderNo` | String | 支付交易订单号 |
| `payOrderNoDate` | String | 支付订单日期 |
| `debitRequestResult` | String | 扣款请求结果，值域只有 `0` / `1` |
| `payChannelCode` | String | 支付渠道代码 |
| `companionFlag` | String | 同行票标识 |
| `cardNum` | String | 逻辑卡号 |
| `ticketCode` | String | 日票票号 |
| `countingTimes` | String | 计次次数（预留） |
| `countingFlag` | String | 计次标识（预留） |
| `invoice` | String | 发票状态（可选） |
| `discountFee` | String | 优惠金额（单位：分） |
| `discountInfo` | String | 优惠信息 |

> `AlipayTripFindTravelDetailRespVO`（同包，含 `retCode` / `retMsg` / `data`，其中 `data` 是 `AlipayTripFindTravelDetailRespDTO`）**不是本端点的应答类型**，本端点直接返 `AlipayTripFindTravelDetailRespDTO`。**NEVER 按那个 VO 写断言**（会多一层嵌套）。

**压测备注**：本模块纯转发无落库；下游只读。**断言路径 MUST 是 `data.xxx`，不是顶层 `xxx`** —— 扁平结构是已作废的旧口径。不调支付宝开放平台。非回调端点。同 #5，用例 MUST 标明走的是 trans-query-server 链路。

---

### 7. 支付宝出行-获取行业数据（MemberContract 入口）

- **对外 URL**：`POST /fep-alipay/memberContract/channel/requestIndustryData`
- **容器内路径**：`/memberContract/channel/requestIndustryData`
- **Controller**：`FepAlipayTripMemberContractController#requestIndustryData`（`fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/controller/FepAlipayTripMemberContractController.java:34~48`，类级 `@RequestMapping("/memberContract/channel")` 在 `:20`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：与 #4 完全相同（三跳：alipay-account-server → ticket-server → industry-data-server）
- **校验分支**：Controller `:37` 的 `request == null || bizData == null` ⇒ `8001` / `无效的参数`；其余分支与 #4 逐条一致（同一个 `AlipayApplicationServiceImpl.requestIndustryData`）

**bizData 请求字段** — DTO `AlipayTripRequestIndustryDataReqDTO`，**与 #4 同一个类、逐字段相同**，此处不再重复，见 #4。

**响应字段** — `AlipayTripRequestIndustryDataRespDTO`，**与 #4 相同**，见 #4。

**压测备注**：本模块纯转发无落库；下游负载与 #4 完全共享（同一段代码、同样三跳）。**测试重点是「双入口行为一致性」**：同一份报文分别打两条 URL，应答应逐字一致；区分落点看日志前缀（本端点的日志带 `(MemberContract)` 后缀，如「支付宝出行-获取行业数据(MemberContract),请求参数」）。压测时 **NEVER 把两条 URL 的 QPS 当成两份独立容量** —— 它们打的是同一批下游。非回调端点。

---

### 8. 支付宝出行-支付结果查询

- **对外 URL**：`POST /fep-alipay/admin/payment/payQuery`
- **容器内路径**：`/admin/payment/payQuery`
- **Controller**：`FepAlipayTripPaymentController#payQuery`（`fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/controller/FepAlipayTripPaymentController.java:35~49`，类级 `@RequestMapping("/admin/payment")` 在 `:21`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：alipay-pay-sign-server（`service.alipay-pay-sign.url`，`AlipayPaySignClient.alipayTripPayQuery`，`AlipayQueryServiceImpl.java:85~119`）
- **校验分支**：
  - Controller `:38` — `request == null || bizData == null` ⇒ `8001` / `无效的参数`
  - Service `:88` — `orderNo` 为空白 ⇒ `8001` / `无效的参数：orderNo不能为空`
  - Service `:96` 下游返 null ⇒ `9999` / `系统内部错误：支付签约服务返回空响应`；`:112` catch 异常 ⇒ `9999` / `系统内部错误：` + 异常 message（**`retMsg` 带异常原文，断言用前缀匹配、NEVER 全等**）

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.alipaytrip.AlipayTripPayQueryReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `orderNo` | String | 无（代码无 Bean Validation，空值不会 400；Service `:88` 手写非空校验，空则 `8001`） | 订单号 |
| `cardIssueCode` | String | 无 | 卡机构编号，支付宝 `0007` |
| `cardNum` | String | 无 | 逻辑卡号 |
| `channelAgreementNo` | String | 无 | 渠道协议号 |

**响应字段** — `AlipayTripPayQueryRespDTO`（**不继承 `CommonResult`，自己声明了 `retCode` / `retMsg`**）

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | 返回码，`0000` 成功 |
| `retMsg` | String | 返回消息 |
| `outTradeNo` | String | 订单号 |
| `paymentTime` | String | 支付时间 |
| `tradeStatus` | String | 支付状态 |
| `totalAmount` | String | 支付金额 |
| `tradeNo` | String | 支付渠道订单号 |
| `tradeDesc` | String | 支付结果 |

**压测备注**：本模块纯转发无落库；下游只读查询，是本组 `/admin/payment/**` 里唯一的只读端点，适合当该前缀的**基线探针**。**命名像运营侧但公网可达、无鉴权**，安全测试 MUST 覆盖。不调支付宝开放平台。非回调端点。

---

### 9. 支付宝出行-退款申请

- **对外 URL**：`POST /fep-alipay/admin/payment/requestRefund`
- **容器内路径**：`/admin/payment/requestRefund`
- **Controller**：`FepAlipayTripPaymentController#requestRefund`（`.../controller/FepAlipayTripPaymentController.java:54~68`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：alipay-pay-sign-server（`service.alipay-pay-sign.url`，`AlipayPaySignClient.alipayTripRequestRefund`，`AlipayPaymentServiceImpl.java:36~67`），下游端点 `POST /internal/alipay/payment/requestRefund`
- **校验分支**：
  - Controller `:57` — `request == null || bizData == null` ⇒ `8001` / `无效的参数`
  - Service `:40` — `orderNo` 为空白 ⇒ `8001` / `无效的参数：orderNo不能为空`
  - Service `:49` 下游返 null ⇒ `9999` / `系统内部错误：支付签约服务返回空响应`；`:59` catch 异常 ⇒ `9999` / `系统内部错误：` + 异常 message

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestRefundReqDTO`（**只有两个字段，且是实测过的真实契约**）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `orderNo` | String | 无（代码无 Bean Validation，空值不会 400；Service `:40` 手写非空校验，空则 `8001`） | 原订单号 |
| `refundAmount` | String | 无 | 退款金额，单位分；**不传即按原支付金额全额退** |

> 该 DTO 曾多出 `cardIssueCode` / `cardNum` / `channelAgreementNo` / `refundOrderNo` 四个字段，**已删除**：接收端同名 DTO 本来就只有这两个，Fastjson2 静默丢弃其余，那四个值从未到达过服务端。退款单号、渠道协议号等由服务端自行解析生成，**测试 NEVER 尝试通过请求指定退款单号**（送了也无效）。

**响应字段** — `AlipayTripRequestRefundRespDTO`（**不继承 `CommonResult`，自己声明两字段**）

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | 返回码，`0000` 成功 |
| `retMsg` | String | 返回消息 |

**压测备注**：本模块纯转发无落库；**下游写退款单并出网调支付中心退款接口（真实资金动作）**。这是本文件里风险最高的端点：**NEVER 对生产数据做退款压测**，MUST 先与业主确认造数与额度，并准备退款单清理方案。**公网可达、无鉴权**，安全测试 MUST 覆盖「任意人按订单号发起退款」这一项。非回调端点。

---

### 10. 支付宝出行-添加黑名单

- **对外 URL**：`POST /fep-alipay/admin/payment/addBlackList`
- **容器内路径**：`/admin/payment/addBlackList`
- **Controller**：`FepAlipayTripPaymentController#addBlackList`（`.../controller/FepAlipayTripPaymentController.java:73~79`）
- **Content-Type**：**`application/json`**
- **入参形态**：**`@RequestBody AddBlackListReqDTO`（JSON 直传，不是 form）**
- **下游**：blacklist-server（`service.blacklist.url`，`BlacklistClient.addBlackList`，`AlipayPaymentServiceImpl.java:70~123`）
- **校验分支**：
  - **Controller 无任何校验**（`:74` 直接 delegate）
  - Service `:74` — `request == null || cardId` 为空白 ⇒ `8001` / `无效的参数：cardId不能为空`
  - Service `:97` 下游返 null ⇒ `9999` / `系统内部错误：黑名单服务返回空响应`；`:104` 下游非 `0000` ⇒ **原样透传下游 `retCode` / `retMsg`**；`:115` catch 异常 ⇒ `9999` / `系统内部错误：` + 异常 message
- **服务端默认值填充**（`AlipayPaymentServiceImpl.java:84~95`，**调用方送了则以调用方为准**）：`channelCode` 缺省 `02`、`blackSource` 缺省 `02`、`blackCause` 缺省 `01`、`createBy` 缺省 `fep-alipay-server`

**请求体字段（本端点不带公共报文字段：没有 `sign` / `charset` / `format` / `timestamp` / `deviceId` / `signType` / `bizData`，整个 JSON 就是下面这个 DTO）** — `com.chinasofti.huateng.model.app.AddBlackListReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `cardId` | String | 无（代码无 Bean Validation，空值不会 400；Service `:74` 手写非空校验，空则 `8001`） | 卡ID，唯一业务键，javadoc 标注「必填」 |
| `thirdUserId` | String | 无 | 三方用户ID，审计冗余，不参与命中判定 |
| `cardType` | String | 无 | 卡类型编码（票种，如 `0441`），出向通知契约需要 |
| `channelCode` | String | 无 | 业务渠道：`01` 地铁APP / `02` 支付宝 / `99` 未知。**本端点不传时服务端填 `02`** |
| `blackSource` | String | 无 | 发起方：`01` 系统自动 / `02` 渠道通知 / `09` 运维手工。**不传时服务端填 `02`** |
| `blackCause` | String | 无 | 拉黑原因：`01` 未付费欠费 / `02` 挂失补卡 / `09` 其他。**不传时服务端填 `01`**。只有 `01` 参与自动解除 |
| `bizNo` | String | 无 | 关联业务单号，语义由 `channelCode` 与 `blackSource` 共同决定，可空 |
| `reason` | String | 无 | 拉黑备注，自由文本，非判定依据 |
| `createBy` | String | 无 | 操作者。**不传时服务端填 `fep-alipay-server`** |

**响应字段** — `com.chinasofti.huateng.model.app.BlackListOperateResult extends CommonResult`（无自有字段）

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | 返回码，`0000` 成功（成功分支 `retMsg` 被服务端固定改写为 `成功`，见 `:112~113`） |
| `retMsg` | String | 返回消息 |

**压测备注**：本模块纯转发无落库；**下游 blacklist-server 会写黑名单表并触发渠道通知出网**，且**被加黑的卡会无法过闸** —— 压测 MUST 只用专用造数卡号，事后 MUST 清理。本端点填的 `blackCause=01`（欠费类）**是唯一能被 `sys_job` 自动解除覆盖的那一类**，与 `channelCode=02` 配合可被自动解除链路捞到。**公网可达、无鉴权、JSON 直传**，安全测试 MUST 覆盖。非回调端点。

---

### 11. 支付宝出行-执行解约

- **对外 URL**：`POST /fep-alipay/admin/payment/executeTermination?agreementCode=xxx`
- **容器内路径**：`/admin/payment/executeTermination`
- **Controller**：`FepAlipayTripPaymentController#executeTermination`（`.../controller/FepAlipayTripPaymentController.java:82~88`）
- **Content-Type**：**无请求体**（query 参数即可；`application/x-www-form-urlencoded` 的 body 也能被 `@RequestParam` 接住）
- **入参形态**：**`@RequestParam(required = false) String agreementCode`**
- **下游**：alipay-pay-sign-server（`service.alipay-pay-sign.url`，`AlipayPaySignClient.executeTermination`，`AlipayPaymentServiceImpl.java:126~162`）
- **校验分支**：
  - **`required = false`，因此不传该参数 NEVER 返 400**，会进 Service `:131` 的空白判断 ⇒ `retCode=8001` / `无效的参数：agreementCode不能为空` / `status=FAIL`
  - Service `:141` 下游返 null ⇒ `9999` / `系统内部错误：支付签约服务返回空响应` / `status=FAIL`
  - Service `:149~151` — 原样透传下游 `retCode` / `retMsg`，并按 `retCode=="0000"` 把 `status` 置成 `COMPLETED`，否则 `FAIL`
  - Service `:153` catch 异常 ⇒ `9999` / `系统内部错误：` + 异常 message / `status=FAIL`

**Query 参数（本端点不带公共报文字段：没有 `sign` / `bizData` 那一套，只有一个 query 参数）**

| 参数 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `agreementCode` | String | `@RequestParam(required = false)`（**故意可选**，无 Bean Validation；空值走 Service 手写校验返 `8001`，不会 400） | 协议号 |

**响应字段** — `com.chinasofti.huateng.model.alipaytrip.TerminationExecuteResult`（**不继承 `CommonResult`**）

| 字段 | 类型 | 说明 |
|---|---|---|
| `agreementCode` | String | 协议号，**服务端 `:129` 无条件回填入参值**（含失败分支） |
| `retCode` | String | 返回码，`0000` 成功 |
| `retMsg` | String | 返回消息 |
| `status` | String | 当前解约状态。DTO javadoc 声明值域 `PENDING` / `COMPLETED` / `FAIL` / `TERMINATED`，**但本端点实际只会产出 `COMPLETED` 与 `FAIL` 两种**（`:151`），**NEVER 按四值写断言** |

**压测备注**：本模块纯转发无落库；**下游会真正解除签约（状态变更 + 出网）**，与 #2「解约登记」不是同一件事，两者 MUST 分开用例。**公网可达、无鉴权、且入参只有一个协议号** —— 这是本文件里最容易被外部滥用的形态，安全测试 MUST 重点覆盖。不直接调支付宝开放平台（出网动作在下游）。非回调端点。

---

### 12. 支付中心-支付结果回调

- **对外 URL**：`POST /fep-alipay/notify/payment/payNotify`
- **容器内路径**：`/notify/payment/payNotify`
- **Controller**：`FepAlipayTripNotifyController#paymentPayNotify`（`fep-alipay-server/src/main/java/com/chinasofti/huateng/fep/alipay/controller/FepAlipayTripNotifyController.java:34~43`，类级 `@RequestMapping("/notify")` 在 `:23`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：**`@RequestParam Map<String, String> params`**，Controller `:37` 从 map 里取 `bizData` 这一个键，`:38` 用 Fastjson2 解析成 `AlipayTripPayNotifyReqDTO`
- **下游**：alipay-pay-sign-server（`service.alipay-pay-sign.url`，`AlipayPaySignClient.handlePayNotify`，`AlipayNotifyServiceImpl.java:32~59`）
- **校验分支**：
  - **Controller 无校验**；`bizData` 缺失时 `JSON.parseObject(null, ...)` 返回 `null`，进 Service
  - Service `:36` — `request == null || orderNo` 为空白 ⇒ `8001` / `参数异常：orderNo不能为空`
  - Service `:45` — 下游 `retCode=="0000"` ⇒ `0000` / `成功`；否则 ⇒ `9999` + **下游的 `retMsg`**（下游为 null 时用 `系统内部错误`）
  - Service `:52` catch 异常 ⇒ `9999` / `系统内部错误`

**bizData 请求字段（注意：本端点虽是 form，但 Controller 只读 `params.get("bizData")` 这一个键，其余公共字段全部被忽略、不参与任何逻辑）** — DTO `com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `orderNo` | String | 无（代码无 Bean Validation，空值不会 400；Service `:36` 手写非空校验，空则 `8001`） | 原订单号 |
| `channelVoucherId` | String | 无 | 支付渠道订单号 |
| `transAmount` | String | 无 | 支付金额，单位分 |
| `transTime` | String | 无 | 交易时间 `yyyy-MM-dd HH:mm:ss` |
| `transStatus` | String | 无 | 交易状态 `1` 成功 / `2` 失败 |
| `cardNo` | String | 无 | 支付宝逻辑卡号：卡机构编号 + 地铁逻辑卡号 |

**响应字段** — `AlipayTripPayNotifyRespDTO extends CommonResult`（无自有字段）

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | 返回码，`0000` 成功 |
| `retMsg` | String | 返回消息（成功时是 `FepAppErrorCodeEnum.SUCCESS.getMsg()` = `成功`） |

**压测备注**：**这是回调端点**（支付中心 → ITP），方向与业务端点相反。本模块无落库，但**下游 alipay-pay-sign-server 会按支付结果写流水并推进订单状态**，属状态变更型。压测需自行构造回调报文，MUST 用专用订单号；**重复回调的幂等性由下游负责，本层不做去重**，压测同一 `orderNo` 多次时 MUST 到下游核对是否重复记账。不调支付宝开放平台。**公网可达、无验签** —— 回调端点无鉴权意味着任何人都能伪造支付成功通知，安全测试 MUST 覆盖。

---

### 13. 支付宝出行-业务关闭结果通知

- **对外 URL**：`POST /fep-alipay/notify/closeResultForAlipay`
- **容器内路径**：`/notify/closeResultForAlipay`
- **Controller**：`FepAlipayTripNotifyController#closeResultForAlipay`（`.../controller/FepAlipayTripNotifyController.java:46~52`）
- **Content-Type**：**`application/json`**
- **入参形态**：**`@RequestBody AlipayTripCloseResultReqDTO`（JSON 直传）**
- **下游**：alipay-pay-sign-server（`service.alipay-pay-sign.url`，`AlipayPaySignClient.notifyCloseResult`，`AlipayNotifyServiceImpl.java:62~90`）。注意 Service `:72~75` **只把 `agreementNo` 与一个 boolean 传给下游**，`result == null` 时按 `false` 处理
- **校验分支**：
  - **Controller 无校验**（`:47` 直接 delegate）
  - Service `:65` — `request == null || agreementNo` 为空白 ⇒ `8001` / `无效的参数`
  - Service `:76` — 下游 `retCode=="0000"` ⇒ `0000` / `成功`；否则 ⇒ `9999` + 下游 `retMsg`（为 null 时 `系统内部错误`）
  - Service `:83` catch 异常 ⇒ `9999` / `系统内部错误`

**请求体字段（本端点不带公共报文字段：没有 `sign` / `bizData` 那一套，整个 JSON 就是下面这个 DTO）** — `com.chinasofti.huateng.model.alipaytrip.AlipayTripCloseResultReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| `result` | Boolean | 无（代码无 Bean Validation，空值不会 400；**`null` 被 Service `:74` 当作 `false`**） | 是否同意关闭，`true` 同意 / `false` 拒绝 |
| `agreementNo` | String | 无（Service `:65` 手写非空校验，空则 `8001`） | 协议号 |

**响应字段** — `AlipayTripCloseResultRespDTO extends CommonResult`（无自有字段）

| 字段 | 类型 | 说明 |
|---|---|---|
| `retCode` | String | 返回码，`0000` 成功 |
| `retMsg` | String | 返回消息（成功时固定 `成功`） |

**压测备注**：**这是回调端点**（支付宝侧 → ITP），本模块无落库、下游改签约关闭状态，属状态变更型。**`result` 传 `null` 与传 `false` 行为一致**（都按拒绝走），用例 MUST 覆盖这三态（`true` / `false` / 不传）以钉住该口径。不调支付宝开放平台（本层不出网到开放平台；`alipay.notify.*-url` 那批配置是**出向**通知地址，与本端点方向相反、不在本清单范围）。**公网可达、无验签**，安全测试 MUST 覆盖。
