---
业务域: APP 统一接入层
模块: fep-app-server
---

# 提示词：APP 统一接入层（fep-app-server）

## 何时读本文件
需要新增/修改 APP 侧 `IF8A-xx`、`IF8B-xx`、`IF8D-xx` 接口，或调整 APP 请求的报文解析、路由、透传逻辑时。

## 模块定位（重要认知）
`fep-app-server` 是**薄接入层**，端口 **9101**（`fep-app-server/src/main/resources/application.properties`）。
它只做三件事：解析表单报文 → 通过 `rpc` 客户端转发到后端服务 → 回传 DTO。
**NEVER** 在本模块写业务规则、事务或直连数据库；本模块无 mapper、无数据源。

## 报文契约
- `Content-Type: application/x-www-form-urlencoded`，入参统一绑定 `@ModelAttribute CommonFormRequest`
  （`fep-app-server/.../model/CommonFormRequest.java` 继承**本模块内**的 `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/model/CommonRequest.java`（**不在 `model` 模块**；`model` 模块下没有 `CommonRequest`，同名类另在 fep-acc-server、account-server 各有一份，勿混用）：
  `providerId / charset / format / timestamp / deviceId / signType / sign / bizData`，`toString()` 已对 `sign` 脱敏）。
- `bizData` 解析 **MUST** 复用 `BaseAppController` 的三个方法，**NEVER** 自己 `JSON.parseObject`：
  - `parseBizData()` — 空串按 `{}` 处理
  - `decodeBase64()`
  - `parseCallbackBody()` — 兼容 bizData 为 JSONObject / Base64 串 / 整体报文三种形态
- 新增 controller **MUST** 继承 `BaseAppController`。
- 响应基类 `model/.../common/response/CommonResult.java`（`retCode`/`retMsg`）；运营端接口用 `ResultVO` + `ResultMapper`。
- 全局异常兜底在 `resource/micro/web/.../global/GlobalControllerExceptionHandler.java`。

## ⚠️ 验签现状（安全红线）
`fep-app-server` **内部没有任何验签代码**，`sign` 被原样透传给下游（见 `CollectPayController.toFormDataMap()`）。
实际校验分散在下游：
- `account-server/.../service/AccountRequestVerifier.java` — 校验 providerId/charset/format/timestamp/signType，`signType=00` 免签，用 `itp.signKey` 摘要，**不是 RSA**
- `acc-security-server/.../itp/service/ItpRequestSignVerifier.java` — 参数排序 + `key=signKey`，SHA1/MD5
- `pay-sign-server/.../util/RSASignUtils.java`、`collect-pay-server/.../utils/SignUtils.java`、`collect-ticket-server/.../util/PaySignUtils.java` — 渠道侧 RSA

即：AGENTS.md 声明的 `SHA256WithRSA` 只适用于**渠道对接方向**，APP 入向并非统一 RSA 验签。
涉及签名的改动 **MUST** 明确指出这一现状并提示人工复核，**NEVER** 擅自"补齐"验签导致存量 APP 版本报错。

## Controller 与接口（真实映射）
双别名（`/ci/app/...` + `/app/...`）**只存在于部分 Controller**，不是全模块惯例：
- **有双别名**：`AppAccountController`、`AppParaController`、`AppTicketController`、`AppDailyTicketController`、`PhoneChangeController`
- **只有 `/ci/app/` 单路径**：`CollectPayController`（8 个接口）、`IndustryDataController`（2 个）、`PaySignController`（除 `requestPay` 外）

新增接口时 **MUST** 先看目标 Controller 属于哪一类并保持一致，**NEVER** 给单路径 Controller 硬加 `/app/` 别名。

| Controller | 接口 |
|---|---|
| `AppAccountController` | IF8A-01 `requestApplication`、IF8A-02 `requestKeyList`、IF8A-23 `requestAddPayChannel`、IF8A-24 `requestSetDefaultPayChannel`、IF8A-25 `requestRemovePayChannel`、IF8A-77 `requestUpdateChannelDefaultContract`、`requestAgreeRelease`（同意解约，`AppAccountController.java:61`）、`employeeCard/query` |
| `AppTicketController` | IF8A-73 `queryBlackList`、IF8A-29 `queryUserItinerary`、IF8A-04 `requestExcessFare`、IF8A-05 `requestTransList`、IF8A-34 `requestTransDetail`、IF8A-41 `requestTransStatistics` |
| `AppParaController` | IF8A-09 `requestBuySinlgeTicketMaxNum`（拼写错误已保留，另有 `requestSingleTicketMaxNum` 别名）、IF8A-07 `requestLineCodeList`、IF8A-08 `requestStationCodeList`、IF8A-10 `requestTicketPriceByStation`、IF8A-17 `requestLineStationCodeVersion` |
| `PaySignController` | IF8A-19 `requestPay`、IF8A-16 `requestSignInfo`、`receivePayResult`、IF8A-06 `requestTermination`、IF8A-22 `requestContractResult`、`receiveSignResult`、`receiveTerminationResult`、`queryPayTxnBatch`（供 ticket-server） |
| `CollectPayController` | IF8A-20 `requestOrder`、IF8A-11 `requestPaymentInfo`、IF8A-18 `requestPayResult`、`requestRefundTicket`、`requestRefundTicketResult`、`requestPreActiveOrderList`、`requestActiveTicket`、`receiveRefundResult` |
| `AppDailyTicketController` | `/ci/app/dailyTicket/*` 转发日票 IF8A-60/61/62/64/65/67/71 + 支付回调；另有 `/app/requestCountingOrder` 别名 |
| `IndustryDataController` | IF8A-03 `requestIndustryData`、IF8D-03 `requestNoSignalData` |
| `PhoneChangeController` | `updatePhone`（编号未定） |

## 已知不一致（改动前必须知道）
- `PaySignController` 日志里的 "IF8A-07 / IF8A-10" 与 `AppParaController` 的 IF8A-07（线路代码）/ IF8A-10（票价）**编号冲突**，至少一处日志编号是错的。修改日志编号时 **MUST** 以 `docs/接口规范文档/ITP与APP接口规范R6_接口清单.md` 为准。
- `AppTicketController` 中 IF8A-05 同时被 `requestTransList` 与 pay-sign 的 `queryPayTxnBatch` 使用。

## 参考原始文档
- `docs/接口规范文档/ITP与APP接口规范R6_接口清单.md`（80 个接口，权威）
- `docs/接口规范文档/ITP与APP接口清单.json`
- `docs/不重复需求接口清单.md`
