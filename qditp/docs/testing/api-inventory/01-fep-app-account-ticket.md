# 01 fep-app-server 账户 / 票务 / 参数 / 补款 / 行业数据 / 换手机号（27 个端点）

本文件覆盖 `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/` 下 6 个 Controller 的全部端点，按代码实读整理，用于自动化测试与压力测试。

## 通用约定

- 公共报文字段（`sign` / `charset` / `format` / `timestamp` / `deviceId` / `signType` / `bizData`）统一见 `README.md` §2，本文件**不在每个端点重复列**。
- 请求载体类：`com.chinasofti.huateng.model.app.ItpCommonFormRequest`（`extends ItpCommonRequest<String>`），Controller 一律 `@ModelAttribute` 绑定。
- `bizData` 解析入口：`BaseAppController#parseBizData`（`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/BaseAppController.java:24`），实现是 `JSON.parseObject(bizData 为空则 "{}", targetType)` —— **空 `bizData` 不会报错，会得到一个字段全 null 的 DTO**。
- `fep-app-server` **不验签**，`sign` 原样透传；这 6 个 Controller **没有类级 `@RequestMapping`**，路径全部写在方法上、是绝对路径。
- **对外 URL 带 `/fep-app/` 前缀**（istio `fep-app-vr` 的 `/fep-app/` route 会 rewrite 成 `/` 吃掉前缀），**容器内路径不含该前缀**。直接压 Pod / NodePort 时用「容器内路径」。
- 下游地址键见 `fep-app-server/src/main/resources/application.properties`。
- **本文件内全部 DTO 均无任何 Bean Validation 注解**（`@NotNull` / `@NotBlank` / `@Size` 等零命中），因此所有字段的「校验注解」列统一写「无（代码无 Bean Validation，空值不会 400）」。

## 本文件端点总览

| 序号 | 中文名 | IF 编号 | 对外 URL 主别名 | 下游服务 |
|---|---|---|---|---|
| 1 | 请求开户 | IF8A-01 | `POST /fep-app/ci/app/requestApplication` | account-server |
| 2 | 请求同步密钥 | IF8A-02 | `POST /fep-app/ci/app/requestKeyList` | key-server |
| 3 | 请求添加支付通道 | IF8A-23 | `POST /fep-app/ci/app/requestAddPayChannel` | account-server |
| 4 | 钱包解绑支付通道 | 无编号（日志无 IF 号） | `POST /fep-app/ci/app/requestAgreeRelease` | account-server |
| 5 | 请求设置默认支付通道 | IF8A-24 | `POST /fep-app/ci/app/requestSetDefaultPayChannel` | account-server |
| 6 | 更换第三方渠道码默认支付方式 | IF8A-77 | `POST /fep-app/ci/app/requestUpdateChannelDefaultContract` | account-server |
| 7 | 员工码信息查询 | 无编号（属 IF3A 族，代码未标号） | `POST /fep-app/ci/app/employeeCard/query` | account-server |
| 8 | 电子员工卡激活 / 禁用 | 无编号（属 IF3A 族，代码未标号） | `POST /fep-app/ci/app/employeeCard/activate` | account-server |
| 9 | 删除支付通道 | IF8A-25 | `POST /fep-app/ci/app/requestRemovePayChannel` | account-server |
| 10 | 用户销户 | IF8A-42 | `POST /fep-app/app/cancelAccount` | account-server |
| 11 | 查询黑名单 | IF8A-73（APP 域口径） | `POST /fep-app/ci/app/queryBlackList` | blacklist-server |
| 12 | 查询用户上次行程 | IF8A-29 | `POST /fep-app/ci/app/queryUserItinerary` | ticket-server |
| 13 | 请求自助补站 | IF8A-04 | `POST /fep-app/ci/app/requestExcessFare` | ticket-server |
| 14 | 请求查询交易记录 | IF8A-05 | `POST /fep-app/ci/app/requestTransList` | **trans-query-server** |
| 15 | 获取订单详情 | IF8A-34 | `POST /fep-app/ci/app/requestTransDetail` | **trans-query-server** |
| 16 | 查询账单统计 | IF8A-41 | `POST /fep-app/ci/app/requestTransStatistics` | **trans-query-server** |
| 17 | 实名查询（Mock 桩） | 无编号 | `POST /fep-app/app/ticket/realName` | 无（Controller 内硬编码返回） |
| 18 | 获取单次购买单程票最大张数 | IF8A-09 | `POST /fep-app/ci/app/requestBuySinlgeTicketMaxNum` | para-server |
| 19 | 获取线路代码 | IF8A-07 | `POST /fep-app/ci/app/requestLineCodeList` | para-server |
| 20 | 获取车站代码 | IF8A-08 | `POST /fep-app/ci/app/requestStationCodeList` | para-server |
| 21 | 计算票价 | IF8A-10 | `POST /fep-app/ci/app/requestTicketPriceByStation` | para-server |
| 22 | 获取线路站点代码版本 | IF8A-17 | `POST /fep-app/ci/app/requestLineStationCodeVersion` | para-server |
| 23 | 请求补款下单 | IF8A-26 | `POST /fep-app/ci/app/requestPayOrder` | **face-pay-server** |
| 24 | 查询用户账务信息 | IF8A-35 | `POST /fep-app/ci/app/requestUserAccInfo` | gate-txn-pay-server |
| 25 | 请求行业数据 | IF8A-03 | `POST /fep-app/ci/app/requestIndustryData` | **ticket-server**（不是 industry-data-server） |
| 26 | 请求离线码数据 | IF8D-03 | `POST /fep-app/ci/app/requestNoSignalData` | **ticket-server** |
| 27 | 更换手机号 | if8a_76 | `POST /fep-app/app/changePhone` | account-server + alipay-account-server |

---

## 一、AppAccountController（10 个端点）

文件：`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppAccountController.java`
下游注入：`AccountAppService` → `AccountClient`（`service.account.url`）与 `KeyClient`（`service.key.url`），见 `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/impl/AccountAppServiceImpl.java:30`、`:31`。

### 1. 请求开户（IF8A-01）

- **对外 URL**：`POST /fep-app/ci/app/requestApplication`、`POST /fep-app/app/requestApplication`
- **容器内路径**：`/ci/app/requestApplication`、`/app/requestApplication`（`/fep-app/` 前缀被 istio rewrite 成 `/` 吃掉了）
- **Controller**：`AppAccountController#requestApplication`（`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppAccountController.java:44`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：account-server（`service.account.url`），`accountClient.requestApplication`

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.app.RequestApplicationReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| thirdUserId | String | 无（代码无 Bean Validation，空值不会 400） | 第三方用户 ID |
| thirdPayId | String | 同上 | 支付账户 ID（签约回调），默认空 |
| channel | String | 同上 | 支付通道编码（默认空） |
| reqContractNo | String | 同上 | 第三方签约流水号 |
| cardType | String | 同上 | 卡类型编码 |
| msisdn | String | 同上 | 手机号 |
| userName | String | 同上 | 无字段注释 |
| userId | String | 同上 | 用户身份证号 |
| extend1 | String | 同上 | 无字段注释 |
| extend2 | String | 同上 | 无字段注释 |
| ticketCard | String | 同上 | NFC 开卡使用字段，`01` 非钱包 / `02` 钱包 NFC 卡，非必填 |
| companionFlag | String | 同上 | `0441` 卡用途：`N` 主卡、`Y` 同行码、`C` 第三方平台卡；保留用于订单，不决定开卡数量 |
| ticketLimit | String | 同上 | 注释写「必填」：`1` 按用户、所属方、映射后卡类型限制唯一卡；`2` 每次请求开新卡。**必填性只在注释与下游 account-server 侧，接入层不校验** |
| cardIssueCode | String | 同上 | 注释写「必填」：票卡所属方原值，非空且不超过 16 字符，不校验机构字典；入库 `ISSUE_ORG_CODE` |

**响应字段** — `RequestApplicationResult extends CommonResult`

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 继承自 `CommonResult`，`0000` 成功 |
| retMsg | String | 继承自 `CommonResult` |
| cardId | String | 地铁会员卡号 |
| cardType | String | 卡类型编码 |
| signType | String | `01` sha1withrsa |
| sign | String | 私钥签名 sha1withrsa |

**压测备注**：写库（account-server 侧 `USER_ITP_REG_INFO`）/ 会经 card-pool-server 做卡号预占（`reserveFromPool` 按「用户 + 所属方 + 票种」业务键幂等，**并发同键会拿到同一个卡号与同一个 reservationId**）/ 幂等键 = `thirdUserId` + `cardIssueCode` + 映射后卡类型（仅 `ticketLimit=1` 参与查重）/ 不调外部支付网关。并发压测同一个 `thirdUserId` + `ticketLimit=1` 是最容易暴露卡池竞态的用例；`ticketLimit=2` 每次开新卡，压测会持续消耗卡池库存。

### 2. 请求同步密钥（IF8A-02）

- **对外 URL**：`POST /fep-app/ci/app/requestKeyList`、`POST /fep-app/app/requestKeyList`
- **容器内路径**：`/ci/app/requestKeyList`、`/app/requestKeyList`
- **Controller**：`AppAccountController#requestKeyList`（`AppAccountController.java:50`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：**key-server**（`service.key.url`），`keyClient.requestKeyList`（`AccountAppServiceImpl.java:45`）—— 注意本端点在 `AppAccountController` 里，但下游不是 account-server

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.app.RequestKeyListReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| thirdUserId | String | 无（代码无 Bean Validation，空值不会 400） | 第三方用户 ID，后续会转换为四字节 HEX 作为 `keyUserId` |
| cardId | String | 同上 | 地铁会员卡号，也作为生成用户 SM2 密钥时的逻辑卡号基础数据 |
| cardType | String | 同上 | 卡类型编码：`02` 二维码后付费单程票，`03`/`04` HCE 卡 |

**响应字段** — `RequestKeyListResult extends CommonResult`

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 继承自 `CommonResult` |
| retMsg | String | 继承自 `CommonResult` |
| signType | String | 签名类型，按接口样例返回 `01`（sha1withrsa） |
| sign | String | 当前项目暂未实现响应报文私钥签名，返回空字符串 |
| keyList | List\<KeyItemDTO\> | 密钥列表。二维码卡返回一条 `keyId=01` 的用户非对称密钥；HCE 卡返回一条 `keyId=00` 的 DPK |

**嵌套 DTO** — `com.chinasofti.huateng.model.app.KeyItemDTO`

| 字段 | 类型 | 说明 |
|---|---|---|
| keyId | String | 密钥编码，`01` 用户非对称密钥对，`00` HCE DPK |
| keyType | String | 密钥类型，`1` 用户非对称密钥，`0` HCE DPK |
| keyUserId | String | 密钥用户 ID，由 `thirdUserId` 转四字节 HEX |
| keyPrivate | String | APP 侧加密后的用户私钥或 HCE DPK |
| keyPublic | String | 用户公钥 XY，固定 128 位 HEX（前 64 位 X、后 64 位 Y） |
| keyPublicEffectiveDate | String | 公钥有效期，四字节 HEX，按 2000-01-01 00:00:00 起算秒数 |
| signData | String | CA 签名用户公钥数据，由 acc-security-server 用 CA 密钥对公钥 X 分量签名 |
| caIdx | String | CA 密钥索引，来自 `METRO_CA_KEYSTORE.KEY_IDX` |
| keyWrapValue | String | 预留字段，当前样例为空 |
| keyEffectiveDate | String | 预留字段，当前样例为空 |
| kvc | String | 密钥校验值，当前样例为空 |
| reserve | String | 预留字段，当前样例为空 |

**压测备注**：链路最长的一条（fep-app → key-server → acc-security-server 加密机 TCP 长连）/ 加密机是单点外部依赖，**压测并发上限实际由 HSM 连接池决定，不是本服务**/ 会写 key-server 侧密钥表 / 无幂等键，同一 `thirdUserId` 重复请求的行为取决于 key-server 实现。`keyPrivate` / `keyPublic` / `signData` 在 `KeyItemDTO.toString()` 里只打长度不打真值，压测日志断言不要依赖这三个字段的明文。

### 3. 请求添加支付通道（IF8A-23）

- **对外 URL**：`POST /fep-app/ci/app/requestAddPayChannel`、`POST /fep-app/app/requestAddPayChannel`
- **容器内路径**：`/ci/app/requestAddPayChannel`、`/app/requestAddPayChannel`
- **Controller**：`AppAccountController#requestAddPayChannel`（`AppAccountController.java:56`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：account-server（`service.account.url`），`accountClient.requestAddPayChannel`

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.app.RequestAddPayChannelReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| thirdUserId | String | 无（代码无 Bean Validation，空值不会 400） | DTO 内无字段注释 |
| cardId | String | 同上 | DTO 内无字段注释 |
| cardType | String | 同上 | DTO 内无字段注释 |
| channel | String | 同上 | DTO 内无字段注释 |
| thirdPayId | String | 同上 | DTO 内无字段注释 |
| reqContractNo | String | 同上 | DTO 内无字段注释 |

**响应字段** — `RequestAddPayChannelResult extends CommonResult`（**自身零字段，只继承两个**）

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | `0000` 成功 |
| retMsg | String | 返回消息 |

**压测备注**：写库（account-server 侧 `APP_USER_PAY_CHANNEL`，含回写 `PAY_ACCOUNT_ID`）/ 会经 RPC 同步到 pay-sign-server（展示账号同步）/ 幂等键在 account-server 侧、接入层无 / 不调外部支付网关。

### 4. 钱包解绑支付通道（日志无 IF 编号）

- **对外 URL**：`POST /fep-app/ci/app/requestAgreeRelease`、`POST /fep-app/app/requestAgreeRelease`
- **容器内路径**：`/ci/app/requestAgreeRelease`、`/app/requestAgreeRelease`
- **Controller**：`AppAccountController#requestAgreeRelease`（`AppAccountController.java:62`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：account-server（`service.account.url`），`accountClient.requestAgreeRelease`

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.app.RequestRemovePayChannelReqDTO`（**与第 9 个端点 IF8A-25 共用同一个 DTO**）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| thirdUserId | String | 无（代码无 Bean Validation，空值不会 400） | 三方用户 ID |
| cardId | String | 同上 | 卡 ID |
| cardType | String | 同上 | 卡类型 |
| channel | String | 同上 | 支付通道编码 |

**响应字段** — `RequestRemovePayChannelResult extends CommonResult`（**自身零字段**）

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | `0000` 成功 |
| retMsg | String | 返回消息 |

**压测备注**：写库（account-server 侧删通道行）/ 会触发跨模块清理链路（account → pay-sign）/ 与第 9 个端点**请求与响应 DTO 完全相同**，测试用例可复用报文但 **URL 不同、下游方法不同，NEVER 当成同一个接口**。

### 5. 请求设置默认支付通道（IF8A-24）

- **对外 URL**：`POST /fep-app/ci/app/requestSetDefaultPayChannel`、`POST /fep-app/app/requestSetDefaultPayChannel`
- **容器内路径**：`/ci/app/requestSetDefaultPayChannel`、`/app/requestSetDefaultPayChannel`
- **Controller**：`AppAccountController#requestSetDefaultPayChannel`（`AppAccountController.java:69`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：account-server（`service.account.url`），`accountClient.requestSetDefaultPayChannel`

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.app.RequestSetDefaultPayChannelReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| thirdUserId | String | 无（代码无 Bean Validation，空值不会 400） | DTO 内无字段注释 |
| cardId | String | 同上 | DTO 内无字段注释 |
| cardType | String | 同上 | DTO 内无字段注释 |
| channel | String | 同上 | DTO 内无字段注释 |

**响应字段** — `RequestSetDefaultPayChannelResult extends CommonResult`（**自身零字段**）

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | `0000` 成功 |
| retMsg | String | 返回消息 |

**压测备注**：写库（account-server 侧默认通道标记，同一用户多通道互斥）/ 同一 `thirdUserId` 并发切默认通道会互相覆盖，**是典型的「最后写入胜」竞态用例**/ 不调外部支付网关。

### 6. 更换第三方渠道码默认支付方式（IF8A-77）

- **对外 URL**：`POST /fep-app/ci/app/requestUpdateChannelDefaultContract`、`POST /fep-app/app/requestUpdateChannelDefaultContract`
- **容器内路径**：`/ci/app/requestUpdateChannelDefaultContract`、`/app/requestUpdateChannelDefaultContract`
- **Controller**：`AppAccountController#requestUpdateChannelDefaultContract`（`AppAccountController.java:75`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：account-server（`service.account.url`），`accountClient.requestUpdateChannelDefaultContract`

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.app.RequestUpdateChannelDefaultContractReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| thirdUserId | String | 无（代码无 Bean Validation，空值不会 400） | DTO 内无字段注释 |
| channel | String | 同上 | DTO 内无字段注释 |
| cardIssueCode | String | 同上 | DTO 内无字段注释 |
| regSignSeq | String | 同上 | DTO 内无字段注释 |

**响应字段** — `RequestUpdateChannelDefaultContractResult extends CommonResult`（**自身零字段**）

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | `0000` 成功 |
| retMsg | String | 返回消息 |

**压测备注**：写库（account-server 侧渠道默认签约关系）/ 入参里**没有 `cardId`**、定位维度是「用户 + 渠道 + 所属方」，与第 5 个端点不是同一套键 / 不调外部支付网关。

### 7. 员工码信息查询（代码未标 IF 编号，属 IF3A 族）

- **对外 URL**：`POST /fep-app/ci/app/employeeCard/query`、`POST /fep-app/app/employeeCard/query`
- **容器内路径**：`/ci/app/employeeCard/query`、`/app/employeeCard/query`
- **Controller**：`AppAccountController#queryEmployeeCard`（`AppAccountController.java:81`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：account-server（`service.account.url`），`accountClient.queryEmployeeCard`

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.employee.EmployeeCardQueryReqDTO`（**注意包名是 `model.employee`，不是 `model.app`**）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| cardNo | String | 无（代码无 Bean Validation，空值不会 400） | 员工码卡号。**Controller 在日志里直接调 `bizData.getCardNo()`，`bizData` 恒非 null（`parseBizData` 空串走 `"{}"`），因此不会 NPE** |

**响应字段** — `EmployeeCardQueryResult extends CommonResult`

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 继承自 `CommonResult` |
| retMsg | String | 继承自 `CommonResult` |
| cardNo | String | 员工码卡号 |
| phone | String | 手机号 |
| employeeName | String | 员工姓名 |
| idCardNo | String | 身份证号 |
| company | String | 公司 |
| center | String | 中心 |
| department | String | 部门 |
| position | String | 岗位 |
| photoUrl | String | 照片地址 |
| cardStatus | Integer | 卡状态（`1` 已激活 / `3` 待激活，语义见第 8 个端点的 `actionFlag` 注释） |

**压测备注**：纯读（account-server 员工卡表）/ 无幂等问题 / 不调外部网关 / **响应含身份证号、姓名、手机号、照片地址等 PII**，压测报告与断言样本 MUST 用脱敏数据。

### 8. 电子员工卡激活 / 禁用（代码未标 IF 编号，属 IF3A 族）

- **对外 URL**：`POST /fep-app/ci/app/employeeCard/activate`、`POST /fep-app/app/employeeCard/activate`
- **容器内路径**：`/ci/app/employeeCard/activate`、`/app/employeeCard/activate`
- **Controller**：`AppAccountController#activateEmployeeCard`（`AppAccountController.java:88`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：account-server（`service.account.url`），`accountClient.activateEmployeeCard`

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.employee.EmployeeCardActivateReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| cardNo | String | 无（代码无 Bean Validation，空值不会 400） | 员工码卡号，长度上限 20，服务端会 `trim` 后再查库与转发 ACC |
| actionFlag | Integer | 同上 | 动作标志：`1` 激活（要求当前 `CARD_STATUS=3`）、`0` 禁用（要求当前 `CARD_STATUS=1`） |

**响应字段** — `com.chinasofti.huateng.common.response.CommonResult`（**Controller 返回类型就是 `CommonResult` 本体，不是子类**）

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | `0000` 成功 |
| retMsg | String | 返回消息 |

**压测备注**：写库 + 状态机（白名单前置态：激活要求 `3`、禁用要求 `1`）/ 会向 ACC 侧转发 / **同一 `cardNo` 反复 `actionFlag=1` 压测，第二次起必被状态机拒绝**，压测脚本要么准备足量卡号、要么把非 `0000` 计入预期 / `actionFlag` 是 `Integer`，`bizData` 里传非数字会在 Fastjson2 反序列化阶段失败，退化成全局异常处理器的 UUID `retCode`。

### 9. 删除支付通道（IF8A-25）

- **对外 URL**：`POST /fep-app/ci/app/requestRemovePayChannel`、`POST /fep-app/app/requestRemovePayChannel`
- **容器内路径**：`/ci/app/requestRemovePayChannel`、`/app/requestRemovePayChannel`
- **Controller**：`AppAccountController#requestRemovePayChannel`（`AppAccountController.java:95`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：account-server（`service.account.url`），`accountClient.requestRemovePayChannel`

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.app.RequestRemovePayChannelReqDTO`（**与第 4 个端点共用**，字段见第 4 节）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| thirdUserId | String | 无（代码无 Bean Validation，空值不会 400） | 三方用户 ID |
| cardId | String | 同上 | 卡 ID |
| cardType | String | 同上 | 卡类型 |
| channel | String | 同上 | 支付通道编码 |

**响应字段** — `RequestRemovePayChannelResult extends CommonResult`（**自身零字段**）

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | `0000` 成功 |
| retMsg | String | 返回消息 |

**压测备注**：写库（删通道行）+ 跨模块清理 / **与第 4 个端点 DTO 完全同构**，两条 URL 的差异只能从 account-server 日志里区分 / 与解约链路相关，压测前后 MUST 核对 `APP_USER_PAY_CHANNEL` 与支付域签约表是否一致。

### 10. 用户销户（IF8A-42）

- **对外 URL**：`POST /fep-app/app/cancelAccount`（**该类唯一的单路径端点，没有 `/ci/app/` 别名**）
- **容器内路径**：`/app/cancelAccount`
- **Controller**：`AppAccountController#userCancel`（`AppAccountController.java:104`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：account-server（`service.account.url`），`accountClient.userCancel`

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.app.UserCancelReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| thirdUserId | String | 无（代码无 Bean Validation，空值不会 400） | 第三方用户标识，注销口径以此为准 |
| phone | String | 同上 | 手机号。规格表 91 要求 APP 上送，**当前仅登记在日志里、不参与注销口径** |

**响应字段** — `UserCancelResult`（**注意：不继承 `CommonResult`，自己声明了同名两字段**）

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | `0000` 成功 |
| retMsg | String | 返回消息 |

**压测备注**：**破坏性写操作**（销户会连带清理账户与通道），压测 MUST 用一次性造的账号，NEVER 拿联调账号跑 / `UserCancelReqDTO.toString()` 对 `phone` 做了掩码（前 3 后 4），日志里看不到完整手机号 / 幂等键 = `thirdUserId`，重复销户的行为由 account-server 状态机决定。

---

## 二、AppTicketController（7 个端点）

文件：`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppTicketController.java`
下游注入三个 Client（`TicketAppServiceImpl.java:30`~`:33`）：`BlacklistClient`（`service.blacklist.url`）、`TicketClient`（`service.ticket.url`）、`TransQueryClient`（`service.transQuery.url`）。
**本类每个非 Mock 端点都有 3 个别名**（`/ci/app/xxx`、`/app/xxx`、`/app/ticket/xxx`），是全文件里别名最多的一组。

### 11. 查询黑名单（IF8A-73，APP 域口径）

- **对外 URL**：`POST /fep-app/ci/app/queryBlackList`、`POST /fep-app/app/queryBlackList`、`POST /fep-app/app/ticket/queryBlackList`
- **容器内路径**：`/ci/app/queryBlackList`、`/app/queryBlackList`、`/app/ticket/queryBlackList`
- **Controller**：`AppTicketController#queryBlackList`（`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppTicketController.java:37`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：blacklist-server（`service.blacklist.url`），`blacklistClient.queryBlackList`（`TicketAppServiceImpl.java:44`）

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.app.QueryBlackListReqDTO`（**只有一个字段**）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| cardId | String | 无（代码无 Bean Validation，空值不会 400） | 卡号，**支持多个、逗号分隔** |

**响应字段** — `QueryBlackListResult`（**不继承 `CommonResult`，自己声明 retCode / retMsg**）

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 返回码 |
| retMsg | String | 返回消息 |
| inBlack | String | 是否在黑名单，`0` 否、`1` 是 |
| failedCount | Integer | 失败次数 |

**压测备注**：纯读 / **`cardId` 支持逗号分隔多卡，压测时单请求内卡数直接放大 blacklist-server 的查询开销**，是本文件里唯一能靠「单条报文变长」压下游的端点 / 无幂等问题 / 不调外部网关。**IF8A-73 在本类是「查询黑名单」，而 `AppDailyTicketController` 的 IF8A-73 是「免费票下单」——同编号不同含义，测试用例命名 MUST 带模块与 URL。**

### 12. 查询用户上次行程（IF8A-29）

- **对外 URL**：`POST /fep-app/ci/app/queryUserItinerary`、`POST /fep-app/app/queryUserItinerary`、`POST /fep-app/app/ticket/queryUserItinerary`
- **容器内路径**：`/ci/app/queryUserItinerary`、`/app/queryUserItinerary`、`/app/ticket/queryUserItinerary`
- **Controller**：`AppTicketController#queryUserItinerary`（`AppTicketController.java:43`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：ticket-server（`service.ticket.url`），`ticketClient.queryUserItinerary`（`TicketAppServiceImpl.java:58`）

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.app.QueryUserItineraryReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| cardNum | String | 无（代码无 Bean Validation，空值不会 400） | DTO 内无字段注释。**注意字段名是 `cardNum`、不是 `cardId`** |
| cardType | String | 同上 | 卡类型 |
| phone | String | 同上 | 手机号 |
| status | String | 同上 | 查询场景：`53` 查进站后行程，`54` 查出站后行程 |
| level | String | 同上 | 钱包折扣 |

**响应字段** — `QueryUserItineraryResult extends CommonResult`

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 继承自 `CommonResult` |
| retMsg | String | 继承自 `CommonResult` |
| memberItinerary | MemberItineraryDTO | 行程明细，单个对象（不是列表） |

**嵌套 DTO** — `com.chinasofti.huateng.model.app.MemberItineraryDTO`（内部无更深一层嵌套）

| 字段 | 类型 | 说明 |
|---|---|---|
| payStatus | String | 支付状态 |
| thisStationName | String | 本次车站名 |
| thisTransTime | String | 本次交易时间 |
| thisStationCode | String | 本次车站代码 |
| lastStationName | String | 上次车站名 |
| lastTransTime | String | 上次交易时间 |
| lastStationCode | String | 上次车站代码 |
| transSeq | String | 交易序号 |
| transValue | Integer | 交易金额 |
| overtimeTransValue | Integer | 超时金额 |
| ticketStatus | String | 票卡状态 |
| payChannel | String | 支付渠道 |
| oriTicketAmt | Integer | 原始票价 |
| debitAmt | Integer | 实扣金额 |
| orderExpType | Integer | 订单异常类型（**此处是 `Integer`，而 `TransRecordDTO.orderExpType` 是 `String`，同名不同类型**） |
| discountInfo | String | 优惠信息 |
| orderNo | String | 订单号 |
| carbonDiscount | Integer | 碳减排优惠 |

**压测备注**：纯读（ticket-server 侧 `QRCODE_STATUS` / 交易明细）/ `status` 取 `53` / `54` 走不同查询分支，压测 MUST 两个值都覆盖 / 无幂等问题 / 不调外部网关。

### 13. 请求自助补站（IF8A-04，APP 域口径）

- **对外 URL**：`POST /fep-app/ci/app/requestExcessFare`、`POST /fep-app/app/requestExcessFare`、`POST /fep-app/app/ticket/requestExcessFare`
- **容器内路径**：`/ci/app/requestExcessFare`、`/app/requestExcessFare`、`/app/ticket/requestExcessFare`
- **Controller**：`AppTicketController#requestExcessFare`（`AppTicketController.java:49`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：ticket-server（`service.ticket.url`），`ticketClient.requestExcessFare`（`TicketAppServiceImpl.java:64`，进出两侧都打 `JSON.toJSONString` 全量日志）

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.app.RequestExcessFareReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| thirdUserId | String | 无（代码无 Bean Validation，空值不会 400） | DTO 内无字段注释 |
| cardId | String | 同上 | DTO 内无字段注释 |
| cardType | String | 同上 | DTO 内无字段注释 |
| upgradeAreaType | String | 同上 | `01` 补进站、`02` 补出站 |
| upgradeStationCode | String | 同上 | 补站车站代码 |
| upgradeReason | String | 同上 | 补站原因 |
| upgradeDateTime | String | 同上 | 格式 `YYYYMMDDHHmmss` |

**响应字段** — `RequestExcessFareResult`（**不继承 `CommonResult`，自己声明两字段**）

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 返回码 |
| retMsg | String | 返回消息 |

**压测备注**：写库 + 推进票卡状态机（ticket-server 侧 `QRCODE_STATUS`）/ **同一张卡并发补站是状态机竞态用例**/ 幂等键在 ticket-server 侧 / `upgradeDateTime` 格式错会被下游拒 / 本端点在 fep-app 侧**进出双向全量打印报文**，高并发压测会明显放大 fep-app 的日志 IO，评估吞吐时 MUST 把这一点计入。

### 14. 请求查询交易记录（IF8A-05，APP 域口径）

- **对外 URL**：`POST /fep-app/ci/app/requestTransList`、`POST /fep-app/app/requestTransList`、`POST /fep-app/app/ticket/requestTransList`
- **容器内路径**：`/ci/app/requestTransList`、`/app/requestTransList`、`/app/ticket/requestTransList`
- **Controller**：`AppTicketController#requestTransList`（`AppTicketController.java:55`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：**trans-query-server**（`service.transQuery.url`，默认 `http://trans-query-57wpd-svc.itp.svc:30035`），`transQueryClient.requestTransList`（`TicketAppServiceImpl.java:72`）

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.app.RequestTransListReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| thirdUserId | String | 无（代码无 Bean Validation，空值不会 400） | DTO 内无字段注释 |
| cardId | String | 同上 | DTO 内无字段注释 |
| cardType | String | 同上 | DTO 内无字段注释 |
| pageNumber | Integer | 同上 | 页码 |
| pageSize | Integer | 同上 | 每页条数 |
| totalPage | Integer | 同上 | 总页数（请求侧也有这个字段） |
| startDate | String | 同上 | 开始日期 `yyyy-MM-dd`（非必填） |
| endDate | String | 同上 | 结束日期 `yyyy-MM-dd`（非必填） |
| debitRequestResult | String | 同上 | 扣款结果：空 = 全部，`0` 成功，`1` 失败 |
| ticketCode | String | 同上 | 日票票号 |

**响应字段** — `RequestTransListResult`（**不继承 `CommonResult`**）

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 返回码 |
| retMsg | String | 返回消息 |
| pageNumber | String | 页码（**响应侧是 String，请求侧是 Integer**） |
| pageSize | String | 每页条数（同上，类型不一致） |
| totalPage | String | 总页数（同上，类型不一致） |
| ticketTransRecord | List\<TransRecordDTO\> | 交易记录列表 |
| signType | String | 签名类型 `00` 不签名 / `01` sha1withrsa / `02` MD5 |
| sign | String | 私钥签名 |

**嵌套 DTO** — `com.chinasofti.huateng.model.app.TransRecordDTO`（内部无更深一层嵌套）

| 字段 | 类型 | 说明 |
|---|---|---|
| entryStationName | String | 进站车站名 |
| entryDate | String | 进站时间 |
| exitStationName | String | 出站车站名 |
| exitDate | String | 出站时间 |
| payAmount | String | 支付金额 |
| orderExpType | String | 订单异常类型（**String，与 `MemberItineraryDTO` 的 Integer 不同**） |
| tradeOrderNo | String | 交易订单号 |
| payTradeOrderNo | String | 支付交易订单号 |
| payOrderNoDate | String | 支付订单日期 |
| debitRequestResult | String | 扣款结果 |
| discountFee | Integer | 优惠金额 |
| discountInfo | String | 优惠信息 |
| companionFlag | String | 同行标志 |
| cardNum | String | 卡号 |
| ticketCode | String | 票号 |
| countingTimes | Integer | 计次次数 |
| countingFlag | String | 计次标志 |
| offlineFlag | String | 离线标志 |
| attributableParty | String | 归属方 |
| receivingParty | String | 收款方 |
| payChannelCode | String | 支付渠道编码（如 `03` 支付宝、`05` 微信），映射自 `PAY_TXN_DETAIL.PAYMENT_VENDOR` |
| transferFlag | String | 换乘标志 |
| cumulativeType | String | 累计类型 |
| originalFare | Integer | 原始票价（分） |
| totalAmount | Integer | 原始票价（分），**值与 `originalFare` 完全相同**，供 APP 按 `totalAmount` 取用 |
| walletTotalAmt | Integer | 钱包总额 |
| discountLevelAmt | Integer | 折扣档金额 |
| discountRate | BigDecimal | 折扣率（**唯一的 `BigDecimal` 字段**） |
| expectedGateAmount | Integer | 预期闸机金额 |

**压测备注**：纯读、**分页查询，是本文件里最重的读接口**/ **下游不是 ticket-server 而是 trans-query-server**（`TicketAppServiceImpl.java:32` 有明确注释「仅 IF8A-05 / IF8A-41 / IF8A-34 三个交易查询走本 Client」），压测时看错服务的指标是常见误判 / `pageSize` 越大下游 SQL 与 fep-app 的 `JSON.toJSONString` 序列化开销同步放大（该方法进出两侧都打全量响应日志）/ 无写、无幂等问题 / 不调外部网关。

### 15. 获取订单详情（IF8A-34）

- **对外 URL**：`POST /fep-app/ci/app/requestTransDetail`、`POST /fep-app/app/requestTransDetail`、`POST /fep-app/app/ticket/requestTransDetail`
- **容器内路径**：`/ci/app/requestTransDetail`、`/app/requestTransDetail`、`/app/ticket/requestTransDetail`
- **Controller**：`AppTicketController#requestTransDetail`（`AppTicketController.java:61`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：**trans-query-server**（`service.transQuery.url`），`transQueryClient.requestTransDetail`（`TicketAppServiceImpl.java:88`）

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.app.RequestTransDetailReqDTO`（**只有两个字段**）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| thirdUserId | String | 无（代码无 Bean Validation，空值不会 400） | DTO 内无字段注释 |
| orderNo | String | 同上 | 订单号 |

**响应字段** — `RequestTransDetailResult`（**不继承 `CommonResult`**）

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 返回码 |
| retMsg | String | 返回消息 |
| ticketTransRecord | TransRecordDTO | 单条交易记录（**与 IF8A-05 共用同一个 `TransRecordDTO`，但那里是 List、这里是单对象**），字段见第 14 节 |

**压测备注**：纯读、单条查询，是三条交易查询里最轻的一条 / 下游同为 trans-query-server / `orderNo` 需预置真实订单号，随机串会得到空结果而不是错误码 —— **压测断言不要只看 `retCode=0000`，MUST 同时断言 `ticketTransRecord` 非空**，否则「查不到」会被误判成通过。

### 16. 查询账单统计（IF8A-41）

- **对外 URL**：`POST /fep-app/ci/app/requestTransStatistics`、`POST /fep-app/app/requestTransStatistics`、`POST /fep-app/app/ticket/requestTransStatistics`
- **容器内路径**：`/ci/app/requestTransStatistics`、`/app/requestTransStatistics`、`/app/ticket/requestTransStatistics`
- **Controller**：`AppTicketController#requestTransStatistics`（`AppTicketController.java:67`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：**trans-query-server**（`service.transQuery.url`），`transQueryClient.requestTransStatistics`（`TicketAppServiceImpl.java:80`）

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| cardId | String | 无（代码无 Bean Validation，空值不会 400） | 卡号，**支持多个、逗号分隔** |
| startDate | String | 同上 | 开始日期 `yyyyMMdd`（**与 IF8A-05 的 `yyyy-MM-dd` 格式不同**） |
| endDate | String | 同上 | 结束日期 `yyyyMMdd` |
| thirdUserId | String | 同上 | 用户 id |
| cardType | String | 同上 | APP 上送的卡类型（`02`/`03`/`04`/`05`/`11`~`15`），**由 service 映射为 `cardTypeList` 后清空** |
| cardIdList | List\<String\> | 同上 | 卡号列表，**MyBatis foreach 内部使用，不是对外字段** |
| cardTypeList | List\<String\> | 同上 | 发卡卡类型列表，**由 `CardTypeMapping.toIssueCardTypes` 展开，内部字段** |

**响应字段** — `RequestTransStatisticsResult`（**不继承 `CommonResult`**）

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 返回码 |
| retMsg | String | 返回消息 |
| tripData | TripDataDTO | 统计结果，单个对象 |

**嵌套 DTO** — `com.chinasofti.huateng.model.app.TripDataDTO`

| 字段 | 类型 | 说明 |
|---|---|---|
| totalPrice | String | 总金额（元）：地铁原价合计，原价缺失时退化为票价 |
| totalDebit | String | 总支付（元）：实付合计，含超时加收 |
| totalDiscount | String | 总优惠（元）：原价 − 票价，逐笔下取 0 |
| totalOvertime | String | 总超时费（元）：超时加收合计，与优惠是相反方向的量 |
| count | Integer | 订单数量 |

**压测备注**：纯读聚合查询 / **`cardIdList` / `cardTypeList` 是内部字段，APP 报文里不该出现**，但 `parseBizData` 不做未知字段拦截、也不拦这两个字段——压测若手工塞进去会直接进 MyBatis foreach，**属可疑输入面，MUST 在测试用例里单列一条**/ 日期区间越大下游聚合越重 / 该 DTO 是「给 `model` 加字段必须连带重建接入层镜像」那条历史教训的当事人（`cardType` 字段），压测前 MUST 确认 fep-app 与 trans-query 两侧镜像都含新字段。

### 17. 实名查询（Mock 桩，无 IF 编号）

- **对外 URL**：`POST /fep-app/app/ticket/realName`（**单路径，没有 `/ci/app/` 与 `/app/` 别名**）
- **容器内路径**：`/app/ticket/realName`
- **Controller**：`AppTicketController#realName`（`AppTicketController.java:76`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：**无**。方法体内新建 `CommonResult`、硬编码 `retCode="0000"` / `retMsg="成功"` 直接返回（`AppTicketController.java:79`~`:82`），**根本不调用任何 service，也不解析 `bizData`**

**bizData 请求字段** — **无对应 DTO**（该方法不调 `parseBizData`，传任何 `bizData` 都被忽略）

**响应字段** — `com.chinasofti.huateng.common.response.CommonResult`

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 恒为 `0000` |
| retMsg | String | 恒为 `成功` |

**压测备注**：**Mock 桩，契约待确认**（方法 Javadoc 原文即「实名查询 Mock 桩，契约待确认」）/ 不读不写不出网 / **恒返 `0000`，任何入参都成功** —— 它可以当「fep-app 自身框架层（Tomcat + 虚拟线程 + form 绑定 + 日志）吞吐上限」的基准探针，但 **NEVER 把它的压测数字当成任何真实业务接口的性能结论**；反过来，功能测试里它也 **NEVER 可用于验证实名逻辑**。

---

## 三、AppParaController（5 个端点）

文件：`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppParaController.java`
下游统一是 para-server（`service.para.url`），经 `ParaAppService` → `ParaClient`（`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/impl/ParaAppServiceImpl.java:18`）。

### 18. 获取单次购买单程票最大张数（IF8A-09）

- **对外 URL**：`POST /fep-app/ci/app/requestBuySinlgeTicketMaxNum`、`POST /fep-app/app/requestBuySinlgeTicketMaxNum`、`POST /fep-app/ci/app/requestBuySingleTicketMaxNum`、`POST /fep-app/app/requestBuySingleTicketMaxNum`（**4 个别名，前两个是拼写错误的 `Sinlge`，后两个是正确拼写 `Single`**）
- **容器内路径**：`/ci/app/requestBuySinlgeTicketMaxNum`、`/app/requestBuySinlgeTicketMaxNum`、`/ci/app/requestBuySingleTicketMaxNum`、`/app/requestBuySingleTicketMaxNum`
- **Controller**：`AppParaController#requestBuySingleTicketMaxNum`（`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppParaController.java:33`，方法名是正确拼写、注解里的前两个别名是错拼）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：para-server（`service.para.url`），`paraClient.requestBuySinlgeTicketMaxNum()`（**RPC 方法名也是错拼的 `Sinlge`**）

**bizData 请求字段** — **无**。该方法 **不调用 `parseBizData`**（`AppParaController.java:37` 直接 `paraAppService.requestBuySinlgeTicketMaxNum()`，无参），`bizData` 传什么都被忽略。

**响应字段** — `RequestBuySinlgeTicketMaxNumResult extends CommonResult`（**类名同样是错拼**）

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 继承自 `CommonResult` |
| retMsg | String | 继承自 `CommonResult` |
| buySinlgeTicketMaxNum | String | 单次可购买的单程票最大张数（**响应字段名也是错拼 `Sinlge`**） |

**压测备注**：纯读、无入参、返回体极小，是 para-server 侧最轻的一条 / **拼写错误贯穿 URL 别名、RPC 方法名、Result 类名与响应字段名四处**，自动化断言 MUST 按 `buySinlgeTicketMaxNum` 取值，写成 `Single` 一律取不到 / 4 条别名 MUST 逐条压一遍（错拼那两条是历史契约，APP 现网可能仍在用）/ 该字段是「购票张数」，**NEVER 当成票价**。

### 19. 获取线路代码（IF8A-07）

- **对外 URL**：`POST /fep-app/ci/app/requestLineCodeList`、`POST /fep-app/app/requestLineCodeList`
- **容器内路径**：`/ci/app/requestLineCodeList`、`/app/requestLineCodeList`
- **Controller**：`AppParaController#requestLineCodeList`（`AppParaController.java:40`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：para-server（`service.para.url`），`paraClient.requestLineCodeList`

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.app.RequestLineCodeListReqDTO`

**该 DTO 一个字段都没有**（只有类声明与 Javadoc，`private` 字段零命中）。它仍然被 `parseBizData` 解析，因此 `bizData` 传任何 JSON 都会被解析成一个空对象、不报错。

**响应字段** — `RequestLineCodeListResult extends CommonResult`

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 继承自 `CommonResult` |
| retMsg | String | 继承自 `CommonResult` |
| lineCodeRecord | List\<LineCodeRecordDTO\> | 线路列表，**字段初始化为 `new ArrayList<>()`，不会是 null** |

**嵌套 DTO** — `com.chinasofti.huateng.model.app.LineCodeRecordDTO`

| 字段 | 类型 | 说明 |
|---|---|---|
| lineCode | String | 线路代码 |
| lineNameZH | String | 线路中文名称 |
| lineNameEN | String | 线路英文名称 |
| orderIndex | String | 展示排序序号 |

**压测备注**：纯读、全量参数表查询、**响应体大小与线网线路数成正比且与入参无关**，适合做 para-server 的稳定吞吐基线 / 无入参可变量，因此**同一份报文可无限复用**/ 无写、无幂等问题。

### 20. 获取车站代码（IF8A-08）

- **对外 URL**：`POST /fep-app/ci/app/requestStationCodeList`、`POST /fep-app/app/requestStationCodeList`
- **容器内路径**：`/ci/app/requestStationCodeList`、`/app/requestStationCodeList`
- **Controller**：`AppParaController#requestStationCodeList`（`AppParaController.java:46`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：para-server（`service.para.url`），`paraClient.requestStationCodeList`

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.app.RequestStationCodeListReqDTO`（**只有一个字段**）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| lineCode | String | 无（代码无 Bean Validation，空值不会 400） | 线路代码 |

**响应字段** — `RequestStationCodeListResult extends CommonResult`

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 继承自 `CommonResult` |
| retMsg | String | 继承自 `CommonResult` |
| stationCodeRecord | List\<StationCodeRecordDTO\> | 车站列表，**初始化为 `new ArrayList<>()`** |

**嵌套 DTO** — `com.chinasofti.huateng.model.app.StationCodeRecordDTO`

| 字段 | 类型 | 说明 |
|---|---|---|
| lineCode | String | 线路代码（字段本身无注释） |
| stationCode | String | 车站代码 |
| stationNameZH | String | 车站中文名称 |
| stationNameEN | String | 车站英文名称 |
| transferYn | String | 是否换乘站，`Y` 是、`N` 否 |

**压测备注**：纯读 / **`lineCode` 不传时的行为由 para-server 决定（可能返全量车站）**，压测 MUST 分「带 lineCode」「不带 lineCode」两组，后者响应体可能大一个数量级 / 无写、无幂等问题。

### 21. 计算票价（IF8A-10，APP 域口径）

- **对外 URL**：`POST /fep-app/ci/app/requestTicketPriceByStation`、`POST /fep-app/app/requestTicketPriceByStation`
- **容器内路径**：`/ci/app/requestTicketPriceByStation`、`/app/requestTicketPriceByStation`
- **Controller**：`AppParaController#requestTicketPriceByStation`（`AppParaController.java:52`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：para-server（`service.para.url`），`paraClient.requestTicketPriceByStation`

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.app.RequestTicketPriceByStationReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| entryStationCode | String | 无（代码无 Bean Validation，空值不会 400） | 进站车站代码 |
| exitStationCode | String | 同上 | 出站车站代码 |

**响应字段** — `RequestTicketPriceByStationResult extends CommonResult`

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 继承自 `CommonResult` |
| retMsg | String | 继承自 `CommonResult` |
| ticketPrice | String | 票价，**单位按参数表票价字段定义**（DTO 注释原文，未写明分或元） |

**压测备注**：纯读（para-server 票价矩阵 `TBL_FARE_MATRIX`）/ 响应体最小、单次查询最轻，适合做高并发基准 / **票价矩阵按 `PARA_VER_NO` 版本查询，压测期间若有人改 `TBL_PARA_VERSION.CURRENT_VER_NO`，会整体返空**/ `ticketPrice` 单位未在契约里写明，断言金额前 MUST 先与 para-server 侧确认。

### 22. 获取线路站点代码版本（IF8A-17）

- **对外 URL**：`POST /fep-app/ci/app/requestLineStationCodeVersion`、`POST /fep-app/app/requestLineStationCodeVersion`
- **容器内路径**：`/ci/app/requestLineStationCodeVersion`、`/app/requestLineStationCodeVersion`
- **Controller**：`AppParaController#requestLineStationCodeVersion`（`AppParaController.java:58`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：para-server（`service.para.url`），`paraClient.requestLineStationCodeVersion`

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.app.RequestLineStationCodeVersionReqDTO`

**该 DTO 一个字段都没有**（与 IF8A-07 的请求 DTO 同型：只有类声明与 Javadoc）。**但与 IF8A-09 不同**，本端点**确实调了 `parseBizData`**（`AppParaController.java:61`），只是解析出来的对象没有任何字段。

**响应字段** — `RequestLineStationCodeVersionResult extends CommonResult`

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 继承自 `CommonResult` |
| retMsg | String | 继承自 `CommonResult` |
| lineCodeIdx | String | 线路代码版本号 |
| stationCodeIdx | String | 车站代码版本号 |
| changeDate | String | 版本变更日期 |

**压测备注**：纯读、无入参、响应体极小 / APP 侧靠它判断要不要重拉 IF8A-07 / IF8A-08，**因此现网真实 QPS 通常高于那两条**，压测配比 MUST 按这个关系设计 / 无写、无幂等问题。

---

## 四、GateTxnPayController（2 个端点）

文件：`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/GateTxnPayController.java`
**本类没有 service 层**，Controller 直接注入并调用两个 RPC Client（`GateTxnPayController.java:23`、`:24`），是 6 个 Controller 里唯一这么写的。

### 23. 请求补款下单（IF8A-26）

- **对外 URL**：`POST /fep-app/ci/app/requestPayOrder`、`POST /fep-app/app/requestPayOrder`
- **容器内路径**：`/ci/app/requestPayOrder`、`/app/requestPayOrder`
- **Controller**：`GateTxnPayController#requestPayOrder`（`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/GateTxnPayController.java:34`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：**face-pay-server**（`service.facePay.url`，默认 `http://face-pay-server-svc.itp.svc:30025`），`facePayClient.requestPayOrder`（`GateTxnPayController.java:37`）—— **类名叫 `GateTxnPayController`，但这条实际打到 facePayClient，不是 gateTxnPayClient**

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.pay.SupplementOrderReqDTO`（**包名是 `model.pay`，不是 `model.app`**）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| goodsCode | String | 无（代码无 Bean Validation，空值不会 400） | 商品编码，规格固定 `001` |
| price | String | 同上 | 补款总金额，**单位分，规格为 string** |
| quantity | String | 同上 | 数量，规格固定 `1` |
| orderNoList | List\<String\> | 同上 | 待补款的原过闸订单号数组（`GATE_TXN_PAY.ORDER_NO`） |
| thirdUserId | String | 同上 | 字段本身无注释 |
| cardId | String | 同上 | 字段本身无注释 |

**响应字段** — `com.chinasofti.huateng.model.pay.SupplementOrderRespDTO`（**不继承 `CommonResult`**）

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 返回码，`0000` 表示补款单已生成 |
| retMsg | String | 返回消息 |
| orderNo | String | 补款单号，`SP` 前缀，同时作为后续调支付接口的 `orderNo` |
| payStatus | String | 补款单支付状态：`INIT` / `PROCESSING` / `SUCCESS` / `FAIL` / `CLOSED` |
| totalAmount | Long | 补款单总金额，**单位分**（**请求侧 `price` 是 String、响应侧 `totalAmount` 是 Long**） |

**压测备注**：**写库 + 生成补款单**（face-pay-server 侧 `F2F_*` 表）/ `orderNoList` 是 List，**单请求内订单数直接放大下游写入量**，是本文件里唯一「一次请求写多笔关联数据」的端点 / 幂等键由 face-pay-server 按 `orderNoList` 判定，**并发用同一组 `orderNoList` 压测是关键幂等用例**/ **下游有补款收敛与超时关单两个定时任务在并行改这批数据**（face-pay-server 侧，cron 5 分钟 / 10 分钟），压测结果核对 MUST 把定时任务的影响排除 / 不直接调外部支付网关（下单阶段），但后续支付会。

### 24. 查询用户账务信息（IF8A-35）

- **对外 URL**：`POST /fep-app/ci/app/requestUserAccInfo`、`POST /fep-app/app/requestUserAccInfo`
- **容器内路径**：`/ci/app/requestUserAccInfo`、`/app/requestUserAccInfo`
- **Controller**：`GateTxnPayController#requestUserAccInfo`（`GateTxnPayController.java:43`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：gate-txn-pay-server（`service.gateTxnPay.url`，默认 `http://gate-txn-pay-server-jomf4-svc.itp.svc:30019`），`gateTxnPayClient.requestUserAccInfo`

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.app.RequestUserAccInfoReqDTO`（**只有一个字段**）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| thirdUserId | String | 无（代码无 Bean Validation，空值不会 400） | 第三方用户标识 |

**响应字段** — `RequestUserAccInfoResult`（**不继承 `CommonResult`**）

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 返回码，`0000` 表示查询成功执行 |
| retMsg | String | 返回消息 |
| unpaidCount | int | 未支付订单数（`DEBIT_STATUS` 为 `INIT` / `PROCESSING`）。**基本类型 int，不会是 null，查不到时是 0** |
| failureCount | int | 扣费失败订单数（`DEBIT_STATUS` 为 `FAIL` / `RETRY`）。同上 |

**压测备注**：纯读 / 下游是**按月分区的 `GATE_TXN_PAY` 表**，查询跨分区时开销显著上升，压测账号的历史交易量会直接影响 RT / 两个计数字段是 `int` 而非 `Integer`，**「查不到」与「真的是 0」在响应里无法区分**，断言 MUST 配合预置数据 / 该表同时被 gate-txn-pay 的扣费重试端点（web-admin Quartz 每天 01:00 / 每分钟 recent 两档）改状态，压测期间读到的计数会漂移。

---

## 五、IndustryDataController（2 个端点）

文件：`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/IndustryDataController.java`
**两个端点都只有 `/ci/app/` 单路径，没有 `/app/` 别名**（`@PostMapping({"/ci/app/xxx"})` 数组里只有一个元素）。
下游：`IndustryDataService` → **`TicketClient`**（`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/impl/IndustryDataServiceImpl.java:26`）。

### 25. 请求行业数据（IF8A-03）

- **对外 URL**：`POST /fep-app/ci/app/requestIndustryData`（**单路径，无 `/app/` 别名**）
- **容器内路径**：`/ci/app/requestIndustryData`
- **Controller**：`IndustryDataController#requestIndustryData`（`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/IndustryDataController.java:28`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：**ticket-server**（`service.ticket.url`），`ticketClient.requestIndustryData`（`IndustryDataServiceImpl.java:34`）。**注意 `application.properties:28` 存在 `service.industryData.url`，但本 Controller 链路并不使用它** —— 行业数据的组装在 ticket-server 之后才转到 industry-data-server

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.app.RequestIndustryDataReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| thirdUserId | String | 无（代码无 Bean Validation，空值不会 400） | DTO 内无任何字段注释 |
| cardId | String | 同上 | DTO 内无任何字段注释 |
| cardType | String | 同上 | DTO 内无任何字段注释 |

**响应字段** — `RequestIndustryDataResult extends CommonResult`

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 继承自 `CommonResult` |
| retMsg | String | 继承自 `CommonResult` |
| cardData | String | 行业卡数据（字段无注释） |
| signType | String | 签名类型（字段无注释） |
| sign | String | 签名（字段无注释） |

**压测备注**：链路 fep-app → ticket-server → industry-data-server → 签名服务（acc-security-server / HSM），**是本文件里出网层级最深的一条**/ **并发上限实际由加密机决定**/ 会读 ticket-server 票卡状态、可能写乘车码状态 / **只有 `/ci/app/` 一条路径**，压测脚本若按其他端点的双别名习惯打 `/app/requestIndustryData` 会得到 404 伪装成的 HTTP 200 + UUID `retCode`。

### 26. 请求离线码数据（IF8D-03）

- **对外 URL**：`POST /fep-app/ci/app/requestNoSignalData`（**单路径，无 `/app/` 别名**）
- **容器内路径**：`/ci/app/requestNoSignalData`
- **Controller**：`IndustryDataController#requestNoSignalData`（`IndustryDataController.java:34`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：**ticket-server**（`service.ticket.url`），`ticketClient.requestNoSignalData`（`IndustryDataServiceImpl.java:39`）

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.app.RequestNoSignalDataReqDTO`（**与 IF8A-03 的请求 DTO 字段完全相同：`thirdUserId` / `cardId` / `cardType`，但是两个独立的类，NEVER 混用**）

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| thirdUserId | String | 无（代码无 Bean Validation，空值不会 400） | DTO 内无任何字段注释 |
| cardId | String | 同上 | DTO 内无任何字段注释 |
| cardType | String | 同上 | DTO 内无任何字段注释 |

**响应字段** — `RequestNoSignalDataResult extends CommonResult`

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | 继承自 `CommonResult` |
| retMsg | String | 继承自 `CommonResult` |
| exitData | String | 出站离线码数据（字段无注释） |
| entryData | String | 进站离线码数据（字段无注释） |
| channel | String | 渠道（字段无注释） |

**压测备注**：与 IF8A-03 同链路同下游、**请求 DTO 字段同构**，因此压测可复用同一份 `bizData`，但**下游走的是离线码分支**（一次返进站 + 出站两段数据，响应体比 IF8A-03 大）/ 同样依赖加密机 / 离线码的「20 分钟 / 3 元」阈值是实现自定义口径、甲方文档查无出处，**压测断言 NEVER 依赖这两个数值**/ 同样只有 `/ci/app/` 一条路径。

---

## 六、PhoneChangeController（1 个端点）

文件：`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/PhoneChangeController.java`

### 27. 更换手机号（if8a_76）

- **对外 URL**：`POST /fep-app/app/changePhone`（**单路径，没有 `/ci/app/` 别名**）
- **容器内路径**：`/app/changePhone`
- **Controller**：`PhoneChangeController#updatePhone`（`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/PhoneChangeController.java:27`）
- **Content-Type**：`application/x-www-form-urlencoded`
- **入参形态**：`@ModelAttribute ItpCommonFormRequest`
- **下游**：account-server（`service.account.url`）**先试**，失败再试 alipay-account-server，见 `PhoneChangeAppServiceImpl.java:36` / `:45` / `:47`

**bizData 请求字段** — DTO `com.chinasofti.huateng.model.app.UpdatePhoneReqDTO`

| 字段 | 类型 | 校验注解 | 说明 |
|---|---|---|---|
| thirdUserId | String | 无（代码无 Bean Validation，空值不会 400） | ITP 侧第三方用户标识，用来定位 `USER_ITP_REG_INFO` 与该用户名下的员工码 |
| newPhone | String | 同上 | 变更后的手机号 |

**响应字段** — `UpdatePhoneResult extends CommonResult`（**自身零字段**）

| 字段 | 类型 | 说明 |
|---|---|---|
| retCode | String | **在 Controller 内硬编码**：成功 `0000`、失败 `9999` |
| retMsg | String | **在 Controller 内硬编码**：`更换手机号成功` / `更换手机号失败` |

**Controller 层业务逻辑（异常现象，测试重点）**

本端点是 6 个 Controller 里**唯一在 Controller 层写业务逻辑**的（违反项目「Controller 只做校验与路由」的分层约定），具体三处：

1. `PhoneChangeController.java:31` 对 `newPhone` 做 `trim()`，**传给 service 的是 trim 后的值**；
2. `:34`~`:37` 用 `StringUtils.hasText(newPhone)` 判空，**为空只打 WARN、不中断**，照样往下调 service；
3. `:38`~`:47` **把 service 返回的 `boolean` 就地翻译成 `retCode`**：`true` → `0000`，`false` → `9999`。

因此**所有失败原因（参数为空 / 用户不存在 / 下游异常 / 支付宝侧失败）在响应里全部收敛成同一个 `9999`**，从响应无法区分。

下游侧的分支逻辑（`PhoneChangeAppServiceImpl.updatePhone`）同样值得注意：

- `:28`~`:32` 参数任一为空直接 `return false`（不出网）；
- `:36` 先调 `accountClient.updatePhone`，`retCode == "0000"` 即返回成功；
- `:41` 否则打日志后走支付宝分支：`:45` 先 `alipayAccountClient.selectByThirdUserId` 判存在，`:47` 再 `alipayAccountClient.updatePhone`；
- `:52` 整段包在 `try/catch(Exception)` 里，**任何异常都被吞成 `return false`**。

**压测备注**：**写库两处**（account-server 的 `USER_ITP_REG_INFO` 与员工码手机号；支付宝分支写 alipay-account-server）/ **一次请求最多出网 3 次**（account 1 次 + alipay 查 1 次 + alipay 改 1 次），是本文件里出网次数最多的单个端点 / **幂等：同一 `thirdUserId` + 同一 `newPhone` 重复调会重复写库，代码里无幂等键**/ **`service.alipayAccount.url` 在 `fep-app-server/application.properties` 里根本不存在**（全文件 13 个 `service.*.url` 键里没有它），运行时会落到 `rpc` 默认服务名、与集群实际 Service 名不一致 —— **压测前 MUST 先查 `fep-app` Deployment 的 env 是否注入了该键**，否则支付宝分支会稳定走到异常被吞、统一返 `9999`，看起来像「支付宝用户全部改不了手机号」/ 响应恒为 `0000` / `9999` 两值，**NEVER 用 `retCode` 区分失败原因，MUST 去 fep-app 与 account-server 日志取真实分支**。

---

## 附：跨端点异常现象汇总（测试用例设计要点）

1. **别名数量三档不统一**：`AppTicketController` 6 个端点各 3 个别名；`AppAccountController` / `AppParaController` / `GateTxnPayController` 多为 2 个；`IndustryDataController` 两条与 `PhoneChangeController` 一条、`AppAccountController` 的 `/app/cancelAccount`、`AppTicketController` 的 `/app/ticket/realName` 都是**单路径**。压测脚本 **NEVER 按「所有端点都有 `/ci/app/` 与 `/app/` 两条」批量生成 URL**。
2. **拼写错误别名**：IF8A-09 的 4 个别名里两个是 `requestBuySinlgeTicketMaxNum`（`Sinlge`），且错拼一路贯穿 RPC 方法名、Result 类名、响应字段名。
3. **两个端点共用一个请求 DTO**：`requestAgreeRelease`（第 4）与 `requestRemovePayChannel`（第 9）共用 `RequestRemovePayChannelReqDTO` 与 `RequestRemovePayChannelResult`。
4. **两个 DTO 字段同构但是不同类**：`RequestIndustryDataReqDTO` 与 `RequestNoSignalDataReqDTO` 都只有 `thirdUserId` / `cardId` / `cardType`。
5. **不解析 bizData 的端点**：IF8A-09（`requestBuySingleTicketMaxNum`，service 方法无参）与 `/app/ticket/realName`（Mock 桩）。
6. **零字段请求 DTO**：`RequestLineCodeListReqDTO`（IF8A-07）、`RequestLineStationCodeVersionReqDTO`（IF8A-17）。
7. **Mock 桩**：`/app/ticket/realName` 不调 service、硬编码 `0000`。
8. **Controller 层写业务逻辑**：只有 `PhoneChangeController`（trim / 判空只警告 / boolean → retCode 翻译）。
9. **类名与实际下游不符**：`GateTxnPayController` 的 IF8A-26 打到 `facePayClient`（face-pay-server），只有 IF8A-35 才打 `gateTxnPayClient`。
10. **Controller 名与实际下游不符（第二例）**：`AppAccountController` 的 IF8A-02 打到 `keyClient`（key-server），不是 account-server。
11. **同一 IF 编号不同含义**：IF8A-73 在本类是「查询黑名单」，在 `AppDailyTicketController` 是「免费票下单」；IF8A-04 / IF8A-05 在 APP 域与 BOM 域含义不同；IF8A-09 / IF8A-10 存在多模块重复实现。**定位实现 MUST 以「模块 + URL」为准。**
12. **交易查询三条已切到 trans-query-server**：IF8A-05 / IF8A-34 / IF8A-41 走 `service.transQuery.url`，不再走 `service.ticket.url`（`TicketAppServiceImpl.java:32` 有明确注释）。同一批 URL 在 ticket-server 侧也仍然存在、逐字一致，**压测看服务端指标 MUST 认准 trans-query-server**。
13. **继承关系不统一**：`RequestTransListResult` / `RequestTransDetailResult` / `RequestTransStatisticsResult` / `RequestExcessFareResult` / `QueryBlackListResult` / `RequestUserAccInfoResult` / `UserCancelResult` / `SupplementOrderRespDTO` **都不继承 `CommonResult`**，而是各自声明 `retCode` / `retMsg`；其余 Result 继承 `CommonResult`。统一解析响应的测试框架 **NEVER 假定所有响应都是同一个基类**。
14. **同名字段类型不一致**：`orderExpType` 在 `MemberItineraryDTO` 是 `Integer`、在 `TransRecordDTO` 是 `String`；分页三字段在 `RequestTransListReqDTO` 是 `Integer`、在 `RequestTransListResult` 是 `String`；补款金额在请求是 `String price`、在响应是 `Long totalAmount`。
15. **日期格式两套**：IF8A-05 的 `startDate` / `endDate` 是 `yyyy-MM-dd`，IF8A-41 的是 `yyyyMMdd`。
16. **内部字段暴露在对外 DTO 上**：`RequestTransStatisticsReqDTO` 的 `cardIdList` / `cardTypeList` 注释写明「MyBatis foreach 使用」，但它们在能被 `parseBizData` 解析的对外 DTO 里、不做拦截。
17. **`service.alipayAccount.url` 在 `fep-app-server/application.properties` 里缺失**，而 `PhoneChangeAppServiceImpl` 在用 `AlipayAccountClient`（第 27 端点压测备注）。
18. **`service.industryData.url` 配了但该链路不用**（第 25 端点实际走 `TicketClient`）。
