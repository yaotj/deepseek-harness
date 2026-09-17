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
- `Content-Type: application/x-www-form-urlencoded`，入参统一绑定 `@ModelAttribute ItpCommonFormRequest`
  （⚠️ **本条按 2026-09-11 收口结果重写，旧记载已作废**：现为 `model/.../model/app/ItpCommonFormRequest.java`，继承同在 **`model` 模块** 的 `ItpCommonRequest<String>`——与父类的唯一区别是 `bizData` 固定为 String。
  **原先 fep-app-server（继承式）与 fep-acc-server / fep-dev-server / fep-alipay-server（平铺式）各有一份 `CommonFormRequest`，已于 2026-09-11 全部收口到本类、全项目唯一**，`fep-app-server/src/main/java/.../fep/app/model/` 目录已不存在，代码中旧名**零残留**。
  **NEVER 按旧记载去 fep-app-server 找 `CommonFormRequest` / `CommonRequest`，也 NEVER 再新建各模块自有的副本**（旧记载写「不在 `model` 模块」「同名类另在 fep-acc-server、account-server 各有一份」——**方向正好相反，已删除**）：
  `providerId / charset / format / timestamp / deviceId / signType / sign / bizData`，`toString()` 已对 `sign` 脱敏。本类**不承载签名语义**，验签由各链路自行负责。）
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
- **有双别名**：`AppAccountController`、`AppParaController`、`AppTicketController`、`AppDailyTicketController`、`PhoneChangeController`、`GateTxnPayController`（IF8A-26 / IF8A-35 两条均为 `{"/ci/app/...", "/app/..."}`）
- **只有 `/ci/app/` 单路径**：`CollectPayController`（8 个接口）、`IndustryDataController`（2 个）、`PaySignController`（除 `requestPay` 外）

> ⚠️ 上述清单为 2026-09-16 按 `@PostMapping` 实测（fep-app-server 共 43 条映射 / 10 个 Controller）。**`GateTxnPayController` 此前未列入任何一类**，导致按「只有单路径」的旧认知去改它时会漏加 `/app/` 别名——**该 Controller 属双别名一类**。

新增接口时 **MUST** 先看目标 Controller 属于哪一类并保持一致，**NEVER** 给单路径 Controller 硬加 `/app/` 别名。

| Controller | 接口 |
|---|---|
| `AppAccountController` | IF8A-01 `requestApplication`、IF8A-02 `requestKeyList`、IF8A-23 `requestAddPayChannel`、IF8A-24 `requestSetDefaultPayChannel`、IF8A-25 `requestRemovePayChannel`、IF8A-77 `requestUpdateChannelDefaultContract`、`requestAgreeRelease`（同意解约，`AppAccountController.java:61`）、`employeeCard/query` |
| `AppTicketController` | IF8A-73 `queryBlackList`、IF8A-29 `queryUserItinerary`、IF8A-04 `requestExcessFare`、IF8A-05 `requestTransList`、IF8A-34 `requestTransDetail`、IF8A-41 `requestTransStatistics` |
| `AppParaController` | IF8A-09 `requestBuySinlgeTicketMaxNum`（拼写错误已保留，另有 `requestSingleTicketMaxNum` 别名）、IF8A-07 `requestLineCodeList`、IF8A-08 `requestStationCodeList`、IF8A-10 `requestTicketPriceByStation`、IF8A-17 `requestLineStationCodeVersion` |
| `PaySignController` | IF8A-19 `requestPay`、IF8A-16 `requestSignInfo`、`receivePayResult`、IF8A-06 `requestTermination`、IF8A-22 `requestContractResult`、`receiveSignResult`、`receiveTerminationResult`（别名 `receiveUnsignResult`，同一方法、同一转发目标）、`queryPayTxnBatch`（供 ticket-server） |
| `CollectPayController` | IF8A-20 `requestOrder`、IF8A-11 `requestPaymentInfo`、IF8A-18 `requestPayResult`、`requestRefundTicket`、`requestRefundTicketResult`、`requestPreActiveOrderList`、`requestActiveTicket`、`receiveRefundResult` |
| `AppDailyTicketController` | `/ci/app/dailyTicket/*` 转发日票 IF8A-60/61/62/64/65/67/71 + 支付回调；另有 `/app/requestCountingOrder` 与 `/app/ticket/**` 别名；规范 R6 的扁平 `/app/payment/**` 已挂 `requestPay`、`requestPayResult`、`requestRefundTicket`（2026-09-11 补挂，APP 实调该地址）、`receivePayResult` |
| `IndustryDataController` | IF8A-03 `requestIndustryData`、IF8D-03 `requestNoSignalData` |
| `GateTxnPayController` | IF8A-26 `requestPayOrder`（只生成补款单、不发起支付）、IF8A-35 `requestUserAccInfo`（未支付订单数 + 扣费失败订单数）；**两条都是 `/ci/app` + `/app` 双别名**，详见 [gate-txn-pay.md](gate-txn-pay.md) |
| `PhoneChangeController` | if8a_76 `changePhone`（**仅** `/app/changePhone` 一条路径，严格对齐规范；`updatePhone` / `/ci/app` 别名与 11 个字段别名已于 2026-09-09 收回，**NEVER** 再加） |

## 已知不一致（改动前必须知道）
- `PaySignController` 日志里的 "IF8A-07 / IF8A-10" 与 `AppParaController` 的 IF8A-07（线路代码）/ IF8A-10（票价）**编号冲突**，至少一处日志编号是错的。修改日志编号时 **MUST** 以 `docs/接口规范文档/青岛地铁-ITP与APP接口规范R6.docx` 为准。
- `AppTicketController` 中 IF8A-05 同时被 `requestTransList` 与 pay-sign 的 `queryPayTxnBatch` 使用。
- **IF8B-03「接收黑名单结果通知」已实现但从未接线**（2026-09-16 实测）：`TicketAppService.receiveBlackListFromItp` 与 DTO `model/.../app/ReceiveBlackListFromItpReqDTO.java` 都在，但 **fep-app-server 43 条 `@PostMapping` 里没有任何一条映射它**，全项目无 Controller 引用。**NEVER 认为改 Service 就等于接口生效**；要启用必须先补 Controller 端点。同样注意 `blacklist-server` 侧只有 `/internal/blacklist` 与运营端 `/page/**`，没有承接该通知的端点。

## 参考原始文档
- `docs/接口规范文档/青岛地铁-ITP与APP接口规范R6.docx`（编号与报文的唯一权威来源）

## 附：fep-app-server 源码注释知识抽取（2026-09-16，阶段一）

> **本节引用的行号有并发写入者，引用行号前 MUST 先 grep 现查**（例 `grep -n 'requestTravelOrder' fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppDailyTicketController.java`），NEVER 直接照抄本节的 `文件:行号`。
>
> 抽取范围：`fep-app-server/src/main/java/**/*.java`（25 个文件）+ `fep-app-server/src/main/resources/application.properties`。本模块无 DB、无 mapper。只收注释原文承载的知识；复述方法名/参数名的普通 Javadoc、空 Javadoc、`{@inheritDoc}`、分节横线已丢弃。MUST / NEVER 按注释原文保留，不合并近似条款。禁止性「墓碑注释」单列文末清单、不进正文。

### 一、报文骨架与解析

- APP 公共字段以表单字段提交、业务参数统一放在 `bizData` JSON 字符串中；**子类仅负责接口语义和服务编排，本类负责将其反序列化为对应的业务 DTO**（`fep-app-server` `BaseAppController` 类注释，`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/BaseAppController.java:10-15`）。
- **空 `bizData` 按空 JSON 对象处理，以保持历史接口的反序列化行为**（`fep-app-server` `BaseAppController.parseBizData`，`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/BaseAppController.java:18-26`）。
- **解码 Base64 字符串，若解码失败（非合法 Base64）则原样返回**（`fep-app-server` `BaseAppController.decodeBase64`，`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/BaseAppController.java:32-34`）。
- 回调报文解析**兼容三种形态**：①`bizData` 字段为 JSONObject（直接反序列化）②`bizData` 字段为 Base64 编码的 JSON 字符串（解码后反序列化）③无 `bizData` 字段（整体作为目标 DTO 反序列化）；**解析失败返回 null**（`fep-app-server` `BaseAppController.parseCallbackBody`，`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/BaseAppController.java:43-54`）。
- 解析异常在基类内不落日志，注释原文「由调用方 log 记录」（`fep-app-server` `BaseAppController.parseCallbackBody` catch 分支行内注释，`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/BaseAppController.java:67`）。
- **同一模块内回调有两种收法，判据是对接形态**：`receiveRefundNotify` 用 `@ModelAttribute` + `parseBizData` 而不是像 `receivePayNotify` 那样收原始报文，理由原文「本条按 ITP 信封形态对接，业务字段在 `bizData` 里」（`fep-app-server` `AppDailyTicketController.receiveRefundNotify`，`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/AppDailyTicketController.java:156-157`）。
- **入参已收口为 `model` 的 `ItpCommonFormRequest`（本模块原 `CommonRequest` / `CommonFormRequest` 副本删除）这件事，本模块注释无任何记载**——注释里只有类型引用，没有解释收口理由或禁止新建副本的文字。权威出处在 `AGENTS.md` §5.1「公共请求报文骨架全项目只有两个类」。

### 二、双别名路由

- **同时支持 `/ci/app` 和 `/app` 两条路径**，三个 Controller 的类注释都写了同一句：`fep-app-server` `AppAccountController`（`.../controller/AppAccountController.java:31-35`，涵盖账号申请、密钥同步、支付通道管理、员工码查询、渠道默认支付方式）、`AppTicketController`（`.../controller/AppTicketController.java:24-29`，涵盖行程查询、黑名单、自助补站、交易记录、账单统计和订单详情）、`AppParaController`（`.../controller/AppParaController.java:20-25`，线路、车站、票价和站点版本均由 para-server 提供）。
- **日票域每个接口还额外注册 `/app/ticket/**` 别名**，原因是「APP 端把 `dailyTicket` 段写成了 `ticket`」：2026-09-09 20:03 实测 `/app/ticket/updateTicket` 无映射，落到静态资源处理器报 `No static resource`，响应退化成全局异常处理器的 UUID retCode、日票激活失败；注释同时给出与 `AppTicketController` 的 `/app/ticket/**` 别名风格一致、**两边路径 NEVER 重名**的约束（`fep-app-server` `AppDailyTicketController` 类注释，`.../controller/AppDailyTicketController.java:26-35`）。
- **IF8A-70 旅游票下单**：`/app/ticket/requestTravelOrder` 是 APP 实际在调的地址（2026-09-10 14:26 生产日志实测，6 秒内重试 9 次，服务端无映射、报 `No static resource app/ticket/requestTravelOrder`，响应退化成 UUID retCode）；同时保留 `/ci/app/dailyTicket` 与 `/app/dailyTicket` 两条，**扁平那条 NEVER 删**（`fep-app-server` `AppDailyTicketController.requestTravelOrder`，`.../controller/AppDailyTicketController.java:54-62`）。
- **if8a_61 日票支付**：`/app/payment/requestPay` 是接口规范 R6 给定的地址、APP 实际在调这一条；2026-09-09 实测该路径此前被 `PaySignController` 占为 IF8A-19 通用请求支付，日票支付被透传到 pay-sign、因缺 amount 报 `retCode=8001`，APP 显示「创建支付渠道订单失败」。三条路径同时保留，**NEVER 删掉扁平那条**（`fep-app-server` `AppDailyTicketController.pay`，`.../controller/AppDailyTicketController.java:70-77`）。
- **if8a_62 日票支付结果查询**：`/app/payment/requestPayResult` 为规范给定地址（`fep-app-server` `AppDailyTicketController.queryPayResult`，`.../controller/AppDailyTicketController.java:85`）。
- **IF8A-64 日票退款**：`/app/payment/requestRefundTicket` 是 APP 实际在调的地址（2026-09-11 13:21 生产日志实测，订单 `0E202609110952160010` 4 秒内重试 2 次，服务端无映射报 `No static resource app/payment/requestRefundTicket`，响应退化成 UUID retCode）；与 `requestPay` / `requestPayResult` 同属规范 R6 的 `/app/payment/**` 扁平地址，四条路径同时保留，**NEVER 删掉扁平那条**（`fep-app-server` `AppDailyTicketController.requestRefund`，`.../controller/AppDailyTicketController.java:93-101`）。
- **日票支付回调的路径归属在下游配置里**：`/app/payment/receivePayResult` 是 daily-ticket-server 自己上报给网关的地址（`daily-ticket-server/src/main/resources/application.properties:28` 的 `daily-ticket.pay.notify-url`），此前未在此注册时回调会被全局异常处理器兜成 UUID retCode、订单卡在 PAYING；**改 notify-url 或删本路径 MUST 两边同步**（`fep-app-server` `AppDailyTicketController.receivePayNotify`，`.../controller/AppDailyTicketController.java:128-135`）。
- **日票退款结果回调同理**：`/app/payment/receiveRefundResult` 是 daily-ticket-server 上报给支付中心的地址（`daily-ticket-server` 的 `daily-ticket.pay.refund-notify-url`）；注释记「本 Controller 已因『只注册带 `dailyTicket` 段的路径』踩过三次（`updateTicket` / `requestTravelOrder` / `requestRefundTicket`）」，**扁平那条 NEVER 删**、**改 notify-url 或删本路径 MUST 两边同步**（`fep-app-server` `AppDailyTicketController.receiveRefundNotify`，`.../controller/AppDailyTicketController.java:145-158`）。
- **IF8A-19 请求支付（通用）仅内部 `/ci/app/requestPay` 一条路径**，且 **NEVER 再挂 `/app/payment/requestPay`**：接口规范 R6 中该地址属于 if8a_61 日票支付、已归还 `AppDailyTicketController`；挂在此处会让日票支付请求透传到 pay-sign，因缺 amount / subject / body / cardId / cardType 被 `PaySignWorkflow.validateRequestPay` 拦为 `retCode=8001 amount不能为空`（2026-09-09 实测，订单 0E202609091941230001）（`fep-app-server` `PaySignController.requestPay`，`.../controller/PaySignController.java:45-52`）。
- **IF8A-75 直接解绑支付方式**：与 IF8A-06 请求解约的区别是「本接口立即向支付渠道发起解绑，不等账期结束的定时任务，用于用户长时间未登录需强制解除绑定关系的场景」；`/userData/unbindAgreement` 是 APP 实际在调的路径（2026-09-09 实测：销户后 APP 紧接着请求该路径，因未注册被全局异常处理器兜成 UUID retCode + HTTP 200，APP 无法识别失败，导致用户 00522948 已注销但支付宝签约仍为 SIGNED、通道残留 ACTIVE）；三条路径同时保留，**NEVER 删掉 `/userData/` 这条，除非 APP 侧确认已切换**（`fep-app-server` `PaySignController.unbindAgreement`，`.../controller/PaySignController.java:100-110`）。
- **`/ci/app/receiveUnsignResult` 是支付中心侧使用的别名路径**，语义与 `receiveTerminationResult` 完全一致，同样转发到 pay-sign-server 的 `/ci/app/receiveTerminationResult`；该端点兼容 form 和 JSON 两种请求体格式（`fep-app-server` `PaySignController.receiveTerminationResult`，`.../controller/PaySignController.java:140-147`）。
- **IF8A-05 支付成功回调与内部签约结果通知也标注「兼容 form 和 JSON 两种请求体格式」**（`fep-app-server` `PaySignController.receivePayResult`，`.../controller/PaySignController.java:77-79`；`PaySignController.receiveSignResult`，`.../controller/PaySignController.java:126-128`）。
- **IF8A-42 用户销户的对外契约以规范表 91 为准**：路径**只有** `/app/cancelAccount`，`bizData` 收 `thirdUserId` + `phone`，应答只有 `retCode` + `retMsg`；**NEVER 再加 `/ci/app` 前缀或 `userCancel` 别名**——与 if8a_76 同一口径收敛（见 `docs/business/account-employee-card.md`）；account-server 内部仍叫 `userCancel`，属实现细节（`fep-app-server` `AppAccountController.userCancel`，`.../controller/AppAccountController.java:104-111`）。
- **if8a_76 更换手机号严格对齐《青岛地铁-ITP与APP接口规范R6》**：路径只有 `/app/changePhone`，`bizData` 只认 `newPhone` + `thirdUserId`（表 117），应答只有 `retCode` + `retMsg`（表 118）；**NEVER 再加 `updatePhone` / `/ci/app` 路径别名或 `newMsisdn` 等字段别名**——2026-09-09 曾为兼容上游误传临时放宽过 4 条路径 + 11 个字段名，与规范核对后已全部收回（`fep-app-server` `PhoneChangeController` 类注释，`.../controller/PhoneChangeController.java:14-25`）。
- **`/app/ticket/realName` 是 Mock 桩，不是实现**：APP 端已发布该调用（bizData 仅含 thirdUserId），服务端无实现、线上持续 404；规范文档中唯一相关的 IF8A-25「请求实名」已废弃、路径为 `/ci/app/requestRealNameVerify` 且应答只有 retCode/retMsg，**与此调用不是同一接口**；当前仅返回成功码止住 404、不做任何业务处理，响应字段契约需与 APP 团队确认后替换为真实实现（含实名状态等业务字段）（`fep-app-server` `AppTicketController.realName`，`.../controller/AppTicketController.java:76-85`）。

### 三、验签现状

- **本模块注释无此记载。** 25 个 java 文件与 `application.properties` 的注释里**没有任何一处**提到「不验签」「`sign` 原样透传」或验签责任的分布。权威出处是本文件 §「⚠️ 验签现状（安全红线）」与 `AGENTS.md` §2.2.1 / §4「签名算法」。NEVER 把这条当成注释知识引用。

### 四、下游路由与 `service.*.url`

- **默认值一律用集群内网 Service 名**（`kubectl get svc -n itp` 实测，2026-09-11）；**NEVER 写 127.0.0.1（在 K8s 里等于打到自己），也 NEVER 删键（键缺失时 rpc 退化成默认服务名 `*-service`、DNS 解析不到）**；**Service 端口不统一**——account 9098 / ticket 9100 等于容器端口，security / paySign / blacklist / key 是 8080，para 30026 / gateTxnPay 30019 / industryData 30018 / dailyTicket 30027 / collectPay 30024 等于 NodePort 号；**照抄实测值，NEVER 按 `server.port` 推断**（`fep-app-server` `application.properties` 头部注释块，`fep-app-server/src/main/resources/application.properties:9-13`）。
- **`service.collectPay.url` 是 2026-09-11 补齐的**：此键此前整条缺失，运行时落到 rpc 默认服务名 `collect-pay-service`、DNS 解析不到（`fep-app-server/src/main/resources/application.properties:48`）。
- **`service.facePay.url` 2026-09-15 新增**：IF8A-26 补款下单已迁入 face-pay-server，**地址与 web-admin 的 `service.facePay.url` 保持一致**（`fep-app-server/src/main/resources/application.properties:44`）。
- **`service.transQuery.url` 2026-09-14 新增**：IF8A-05 / IF8A-41 / IF8A-34 三个交易查询已由 ticket-server 切到 trans-query-server；默认值取实测的 `trans-query-57wpd-svc`（NodePort 与 Service 端口同为 30035）；**ticket-server 上那三个同路径端点仍在、未删，回滚只需把 `TicketAppServiceImpl` 的三处换回 `ticketClient`**（`fep-app-server/src/main/resources/application.properties:52-54`）。
- **同一个 Service 层里两个 Client 并存、分工按方法划**：IF8A-05 / IF8A-41 / IF8A-34 三个交易查询已迁到 trans-query-server（9113），本类里只有那三个方法用它；乘车码状态机、自助补站、支付宝行程仍在 ticket-server，**NEVER 把 `ticketClient` 整个换掉**（`fep-app-server` `TicketAppServiceImpl` 字段注释，`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/impl/TicketAppServiceImpl.java:32-35`）。
- **APP 过闸补款透传到 face-pay-server**，注释记「2026-09-15 补款功能自 gate-txn-pay-server 迁入」（`fep-app-server` `GateTxnPayController` 类注释，`.../controller/GateTxnPayController.java:16-21`）。
- **IF8A-26 只生成补款单并返回补款单号、不发起支付；归属校验在 face-pay-server 侧完成**：bizData 传了 thirdUserId / cardId 就按其校验，未传则从原订单反推（详见 `SupplementOrderReqDTO` 类注释）（`fep-app-server` `GateTxnPayController.requestPayOrder`，`.../controller/GateTxnPayController.java:34-39`）。
- **IF8A-35 为什么落在 gate-txn-pay-server 而不是 account-server**（只读，供 APP 做欠费提醒：未支付订单数 + 扣费失败订单数）：数据源就是 `GATE_TXN_PAY`，account-server 没有任何账务表也没注入 `GateTxnPayClient`，经它中转只是多一跳；与同域的 IF8A-05 / IF8A-26 / IF8A-34 走同一条透传链路（`fep-app-server` `GateTxnPayController.requestUserAccInfo`，`.../controller/GateTxnPayController.java:47-53`）。
- **薄网关定位（本模块只承接入口与编排、状态归下游）**：`AppDailyTicketController` 类注释记「本 Controller 只负责承接 APP 公共 FormData 报文、解析 bizData 并转发到 daily-ticket-server」（`.../controller/AppDailyTicketController.java:29-30`）；`DailyTicketAppServiceImpl` 类注释记「fep-app-server 只承接 APP 入口和服务编排，日票订单、支付、激活、退款等状态由 daily-ticket-server 维护」（`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/impl/DailyTicketAppServiceImpl.java:31-35`）。
- **纯透传的三个 Controller，注释只声明转发目标**：`CollectPayController`「承接 APP 下单、支付、退款等请求，透传到 collect-pay-server」（`.../controller/CollectPayController.java:15-18`）；`PaySignController`「所有接口透传到 pay-sign-server」（`.../controller/PaySignController.java:30-33`）；`IndustryDataController`「涵盖 IF8A-03 请求行业数据、IF8D-03 请求离线码数据」（`.../controller/IndustryDataController.java:15-18`）。
- **行业数据链路上移到独立生码服务**：`buildCardDataRequest` 注释原文「组装独立生码服务请求，fep-app 不再本地拼接码体和调用签名」（`fep-app-server` `IndustryDataServiceImpl.buildCardDataRequest`，`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/impl/IndustryDataServiceImpl.java:276-278`）。
- **HCE 卡数据由开户和闸机交易维护，不参与二维码行业数据生成与签名**（`fep-app-server` `IndustryDataServiceImpl.isHceCard`，`.../service/impl/IndustryDataServiceImpl.java:269-271`）。
- **离线码请求统一使用 `signChannelCode=17`（离线码）**，写死在 IF8D-03 分支里（`fep-app-server` `IndustryDataServiceImpl` 行内注释，`.../service/impl/IndustryDataServiceImpl.java:167`）。
- **换手机号的归属判断顺序：默认按 ITP 用户处理**——account-server 侧 `updatePhone` 只需 `thirdUserId`，内部先 `selectActiveByThirdUserId`，非 ITP 用户查不到即返回失败、不会误写；ITP 侧未成功再走支付宝分支（`fep-app-server` `PhoneChangeAppServiceImpl.updatePhone`，`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/impl/PhoneChangeAppServiceImpl.java:35-36` 与 `:48`）。
- **IF8A-75 的语义差异也记在 Service 接口上**：「IF8A-75 直接解绑支付方式：立即向支付渠道发起解绑，不等账期结束的定时任务」（`fep-app-server` `PaySignAppService`，`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/PaySignAppService.java:25`）；`AccountAppService` 同形一句「IF8A-42 用户销户，透传到 account-server」（`.../service/AccountAppService.java:43`）。

### 五、日志与脱敏

- **上游传错字段的定位手段是日志里的 `rawBizData`，不是放宽入参**：注释原文「上游传错字段时看日志里的 `rawBizData` 定位，不要再靠放宽入参掩盖问题」（`fep-app-server` `PhoneChangeController` 类注释，`.../controller/PhoneChangeController.java:23-24`）。
- **`sign` 脱敏、`bizData` 全量进日志这件事，本模块注释无记载**。本模块 Controller 大量使用 `log.info("... 请求参数: {}", request)`（直接打整个 `ItpCommonFormRequest`），但**没有任何注释说明脱敏行为或其风险**。权威出处在 `AGENTS.md` §5.1（`toString` 对 `sign` 恒定脱敏、`bizData` 会完整进日志）。
- 另有两处注释只描述日志分工，不含约束：`BaseAppController.parseCallbackBody` 的「由调用方 log 记录」（见 §一）与 `DailyTicketAppService.handlePayResultCallback` 的「解析并处理支付网关的日票支付结果原始回调报文」（`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/service/DailyTicketAppService.java:72-77`）。

### 六、配置与部署

- **`application.properties` 里唯一带业务语义的非路由键是 `industry.issue-channel-code=01`，无注释**（`fep-app-server/src/main/resources/application.properties:58`）。
- **镜像名 `itp/fep-app`（来自 `<image.prefix>`、不等于 artifactId）、`remote` profile `activeByDefault=true` 导致 `mvn package` 即推镜像、以及「给 model DTO 加字段后 MUST 同时重建本模块镜像否则 Fastjson2 静默丢字段」这三条，本模块 `src/main` 的注释里全部无记载**（`pom.xml` 不在本次抽取范围）。权威出处在 `AGENTS.md` §7 对应条目。
- 启动类只有一句「APP 前置服务启动类」，`run(String... args)` 为空实现、注释无内容（`fep-app-server` `FepAppServer`，`fep-app-server/src/main/java/com/chinasofti/huateng/FepAppServer.java:9-11`、`:39-46`）——**装配了 14 个 `@EnableRpcXxx`，但注释未解释任何一个的取舍**。

### 墓碑注释清单（建议转为断言测试）

不进正文。每条给「文件:行号 + 禁止的事 + 能否断言化」。行号 MUST 先 grep 现查。

| # | 文件:行号 | 禁止的事（注释原文口径） | 能否断言化 |
|---|---|---|---|
| 1 | `fep-app-server/.../controller/AppAccountController.java:109` | **NEVER** 再加 `/ci/app` 前缀或 `userCancel` 别名（IF8A-42 只有 `/app/cancelAccount`） | 能。扫 `RequestMappingHandlerMapping`，断言 `userCancel` 方法注册的路径集合恰好等于 `{"/app/cancelAccount"}` |
| 2 | `fep-app-server/.../controller/AppDailyTicketController.java:35` | 与 `AppTicketController` 的 `/app/ticket/**` 别名两边路径 **NEVER** 重名 | 能。收集两个 Controller 全部 `@PostMapping` 路径，断言无交集（Spring 启动即报重复映射，也可直接用「上下文能起来」当断言） |
| 3 | `fep-app-server/.../controller/AppDailyTicketController.java:61` | IF8A-70 扁平路径 `/app/ticket/requestTravelOrder` **NEVER** 删 | 能。断言路径集合包含该串 |
| 4 | `fep-app-server/.../controller/AppDailyTicketController.java:76` | if8a_61 **NEVER** 删掉扁平那条 `/app/payment/requestPay` | 能。同上；并可加一条「该路径解析出的 handler 属于 `AppDailyTicketController`、不属于 `PaySignController`」 |
| 5 | `fep-app-server/.../controller/AppDailyTicketController.java:100` | IF8A-64 **NEVER** 删掉扁平那条 `/app/payment/requestRefundTicket` | 能。断言路径集合包含该串 |
| 6 | `fep-app-server/.../controller/AppDailyTicketController.java:134` | 日票支付回调「改 notify-url 或删本路径 **MUST** 两边同步」 | 部分能。可写跨模块断言：读 `daily-ticket-server` 的 `daily-ticket.pay.notify-url`，取其 path 段并断言在本模块路径集合内；但两模块不在同一构建单元，需测试自行读文件 |
| 7 | `fep-app-server/.../controller/AppDailyTicketController.java:150` | 退款回调扁平那条 **NEVER** 删 + 改 notify-url 或删本路径 **MUST** 两边同步 | 部分能。同 6，判据来自 `daily-ticket.pay.refund-notify-url` |
| 8 | `fep-app-server/.../controller/PaySignController.java:48` | **NEVER** 再挂 `/app/payment/requestPay`（该地址属 if8a_61 日票支付） | 能。断言 `PaySignController` 注册的路径集合里不含该串（负向断言，最容易写也最有价值） |
| 9 | `fep-app-server/.../controller/PaySignController.java:109` | **NEVER** 删掉 `/userData/unbindAgreement` 这条，除非 APP 侧确认已切换 | 能。断言路径集合包含该串 |
| 10 | `fep-app-server/.../controller/PhoneChangeController.java:21-24` | **NEVER** 再加 `updatePhone` / `/ci/app` 路径别名或 `newMsisdn` 等字段别名 | 路径部分能（断言恰好等于 `{"/app/changePhone"}`）；**字段别名部分不能**——`bizData` 由 Fastjson2 反序列化到 DTO，别名会落在 `model` 模块的 DTO 注解上，本模块测不到 |
| 11 | `fep-app-server/.../service/impl/PhoneChangeAppServiceImpl.java:37-38` | **NEVER** 改回用 `queryUserInfo` 做归属判断（它强制要求 cardId，此处拿不到，会导致 ITP 分支永远进不去、恒定落到支付宝分支返 9999） | 部分能。行为断言可写：mock `accountClient.updatePhone` 返 `0000` 时断言不触碰 `alipayAccountClient`；「不得调用 `queryUserInfo`」也可用 mock 的 `verifyNoInteractions` 直接断言 |
| 12 | `fep-app-server/.../service/impl/TicketAppServiceImpl.java:34` | **NEVER** 把 `ticketClient` 整个换掉（只有 IF8A-05/41/34 三个方法走 `transQueryClient`） | 能。逐方法 mock 断言：三个查询方法只调 `transQueryClient`，其余方法只调 `ticketClient` |
| 13 | `fep-app-server/src/main/resources/application.properties:10` | **NEVER** 写 `127.0.0.1`；**NEVER** 删键（缺键退化成 `*-service`、DNS 解析不到） | 能。配置断言：加载 properties，断言每个 `@EnableRpcXxx` 对应的 `service.*.url` 键存在且值不含 `127.0.0.1` / `localhost`。键清单可由启动类注解反推（对应 §四那条排查判据） |
| 14 | `fep-app-server/src/main/resources/application.properties:13` | 照抄实测值，**NEVER** 按 `server.port` 推断 | 不能。这是人工操作纪律，端口正确性只有集群实测能判定 |

## 附：fep-app-server 注释知识迁移（2026-09-16，阶段二·完整）

> **本节与上一节（阶段一）的区别是「闭环」**：阶段一只是抽取、代码里的注释原样留着；本节是**迁移**——知识落到本文件后，`fep-app-server/src/main/**` 里的多行叙述型 / MUST-NEVER / 事故史 / 墓碑注释**已被删除**，代码里只剩标准 Javadoc + 四条一行式护栏（清单见本节末「保留的一行式护栏」）。**因此从此刻起，本节是这些知识的唯一载体，NEVER 期望在代码注释里再找到它们。**
>
> 覆盖范围：`fep-app-server/src/main/**` 全部 **26 个文件**（25 个 `.java` + 1 个 `application.properties`）。本模块**无 DB、无 mapper、无 mapper XML、无本模块自有 DTO**（`.../fep/app/config/` 与 `.../fep/app/model/` 是 SVN 纳管的**空目录**，见「矛盾与待裁决」第 3 条）。
>
> **本节引用的 `路径:行号` 全部是「迁移前」的位置**，删除后行号已整体上移。**引用前 MUST 用可 grep 短语现查**，NEVER 照抄行号。每条都给了可 grep 的原文短语。

### 一、本模块不验签、`sign` 原样透传 —— 这条知识**没有代码侧对应物**

**这是本次迁移里唯一「零源注释」的条目，也是风险最高的一条。**

- **实测事实**：`fep-app-server/src/main/**` 全部 26 个文件的注释里（阶段一 89 行、阶段二完整 320 行两轮都确认过）**没有任何一处**提到「不验签」「`sign` 原样透传」或验签责任如何分布。代码层面唯一的物证是 `CollectPayController.toFormDataMap()` 里那句 `map.put("sign", request.getSign());`（可 grep 短语：`map.put("sign"`，原 `fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/CollectPayController.java:86`）—— 它**把上游送来的 `sign` 原值塞进出向 form-data 直接转给 collect-pay，中间没有任何校验分支**。
- **为什么这是风险，而不只是「文档缺失」**：
  1. 本模块**没有** spring-security、**没有**全局验签拦截器、**没有** `*Verifier` 类。`fep-app-server` 的 43 条 `@PostMapping` 里**每一条都是裸暴露的状态变更或查询端点**，其中 `/app/cancelAccount`（销户）、`/userData/unbindAgreement`（解绑支付方式）、`/app/changePhone`（换手机号）、`/app/payment/requestRefundTicket`（退款）**都是按 `thirdUserId` / 订单号操作他人数据的写接口**。这直接撞上 `AGENTS.md` §5.2「**新增状态变更型接口 MUST 有鉴权与归属校验**……裸暴露的 POST 接口等于允许任何网络可达方按流水号操作任意用户数据」。
  2. **「知识只在 docs、代码里零痕迹」本身就是缺陷放大器**：改本模块的人在代码里看不到任何「此处不验签、下游负责」的提示，于是会**默认接入层已经验过**。而下游的校验强度参差不齐 —— `AccountRequestVerifier` 在 `signType=00` 时**直接免签**，`ItpRequestSignVerifier` 是 SHA1/MD5，`CollectPayController` 这条链路则完全依赖 collect-pay / face-pay 自己的 `SignUtils`。**没有任何一层能保证「所有 43 条端点都被校验过至少一次」。**
  3. 阶段一已把这条记成「本模块注释无此记载」（本文件上一节 §三）。**本次迁移刻意没有把它写回代码**：写进注释等于在 40 处 Controller 方法上复制同一段话，且注释不是防线。**正确的收口是补一层真实的入向验签**，而不是加注释。
- **MUST / NEVER**：
  - 改 `fep-app-server` 任何端点前 **MUST 先读本条**，NEVER 假定「接入层已验签」。
  - 新增状态变更型端点 **MUST 同时明确它的鉴权归属**（本层补，还是下游哪个 `*Verifier`），并把结论写进本文件 §「⚠️ 验签现状（安全红线）」。
  - **NEVER 在本模块自造签名逻辑**（`AGENTS.md` §5.2 末条），**NEVER 因为 `ItpCommonFormRequest` 是全项目唯一报文骨架就顺手在它上面统一签名语义**（`AGENTS.md` §5.1，该类只承载骨架、四条链路加签验签互不相同）。
  - 上线前 **MUST** 把「43 条端点逐条确认鉴权归属」列进上线核对清单（与 `recon` 的 `X-Recon-Token` 临时删除属同类未闭合项）。

### 二、报文骨架与 `parseBizData` 的解析边界

宿主类 `BaseAppController`（`fep-app-server/src/main/java/com/chinasofti/huateng/fep/app/controller/BaseAppController.java`，包私有 `abstract`，**新增 Controller MUST 继承它**）。三个方法就是本模块**全部**的报文解析入口，**NEVER 在 Controller 里自己 `JSON.parseObject`**。

| 方法 | 迁移前位置 | 可 grep 短语 | 行为（注释原文口径） |
|---|---|---|---|
| `parseBizData(request, targetType)` | `BaseAppController.java:18-30` | `protected <T> T parseBizData` | 把 form-data 的 `bizData` JSON 串反序列化成业务 DTO；**空 `bizData` 按空 JSON 对象 `{}` 处理，以保持历史接口的反序列化行为**（`bizData == null \|\| bizData.trim().isEmpty() ? "{}" : bizData`）。**改这条空值语义会同时影响 40 处调用点** |
| `decodeBase64(data)` | `BaseAppController.java:32-41` | `protected String decodeBase64` | 解码 Base64；**解码失败（非合法 Base64）时原样返回**，`catch (IllegalArgumentException)` 不抛 |
| `parseCallbackBody(requestBody, targetType)` | `BaseAppController.java:43-70` | `protected <T> T parseCallbackBody` | 回调报文解析，**兼容三种形态**：①`bizData` 字段是 JSONObject → 直接反序列化；②`bizData` 字段是 Base64 编码的 JSON 串 → 解码后反序列化；③**无 `bizData` 字段 → 整体作为目标 DTO 反序列化**。**解析失败返回 `null`、且基类内不落日志**（原行内注释「由调用方 log 记录」，可 grep `由调用方 log 记录`） |

- **`parseCallbackBody` 返回 `null` 是正常返回值、不是异常**，调用方 MUST 自己兜。现有兜法是「再按 `ItpCommonFormRequest` 走一遍 `parseBizData`」，`PaySignController` 里**三处逐字相同**（`receivePayResult` / `receiveSignResult` / `receiveTerminationResult`，可 grep `if (dto == null) {`）。**新增回调端点 MUST 照抄这个双路兜底，NEVER 只调 `parseCallbackBody` 就用**。
- **同一模块内回调有两种收法，判据是对接形态，NEVER 混用**：
  - `@RequestBody String requestBody` + `parseCallbackBody` —— 用于**外部平台/支付网关**直接投递的原始报文（`AppDailyTicketController.receivePayNotify`、`PaySignController` 的三个 `receive*`）。
  - `@ModelAttribute ItpCommonFormRequest` + `parseBizData` —— 用于**按 ITP 信封形态对接、业务字段在 `bizData` 里**的回调。活样例 `AppDailyTicketController.receiveRefundNotify`（原 `AppDailyTicketController.java:156-157`，可 grep `本条按 ITP 信封形态对接`）。
- **对外契约判据（`docs/domain` 那条，本模块的落地形态）**：**能被 `parseBizData` / `parseCallbackBody` 解析的 DTO 就是对外契约，NEVER 加字段、NEVER 让两个契约 DTO 共享父类或互相复用。** 本模块 `parseBizData` 的目标类型**全部来自 `model` 模块**（`com.chinasofti.huateng.model.app.**` 与 `model.employee.**` / `model.pay.**`），**本模块自己一个 DTO 都没有**——因此这条护栏在代码里的落点只能是 `BaseAppController.parseBizData` 的 Javadoc（已压成一行，见末节护栏 ③），**NEVER 期望在 `fep-app-server` 里找到带该告示的 DTO 类**。
- **公共报文骨架全项目只有两个类**：`model/.../model/app/ItpCommonRequest<T>` 与 `ItpCommonFormRequest extends ItpCommonRequest<String>`。字段 `providerId / charset / format / timestamp / deviceId / signType / sign / bizData`。**2026-09-11 已把本模块原有的 `CommonRequest` + `CommonFormRequest` 两份副本删除并收口**，代码中旧名零残留（可 grep 验证：`grep -rn 'CommonFormRequest' fep-app-server/src/main` 只会命中 `ItpCommonFormRequest`）。**NEVER 在本模块新建同形副本。**
- **`toString()` 对 `sign` 恒定脱敏、但 `bizData` 会完整进日志**。本模块 Controller 里有大量 `log.info("... 请求参数: {}", request)`（可 grep `请求参数: {}`），**等于把整个 `bizData` 明文写进日志**。这件事**本模块注释里同样零记载**（阶段一已确认），权威出处是 `AGENTS.md` §5.1。**NEVER 把 `sign` 改成打印真值**；新增日志涉及敏感 `bizData` 时 MUST 只打必要字段（现成样例：`PhoneChangeController` 只打 `thirdUserId` / `newPhone` / `rawBizData`，`AppAccountController.queryEmployeeCard` 只打 `cardNo`）。

### 三、双别名路由：本模块的 5 个路径族 + 网关侧的 8 条前缀

**这是两层、不是一层，排查 404 MUST 分清是哪一层缺映射。**

**第 1 层 — 网关侧 8 条前缀**（`fep-app-vr` 这条 VirtualService，ns `itp`，是本项目**唯一**的入向路由开关）：`/fep-app/`、`/fep-dev/`、`/fep-acc/`、`/fep-alipay/`、`/itptvm/`、`/itpbom/`、`/itpagm/`、`/para-server/`。链路 `58.56.166.170:48000` → 节点 `172.20.211.200`（`k8s02-gateway-866a2`）→ `itp-gateway`（ns `itp-gateway`，svc `:50908`）→ `fep-app-vr` → 各业务 Service。**只有 `/fep-app/` 那条落到本模块**；`/itptvm/` 与 `/itpbom/` 已于 2026-09-16 17:31 指向 `face-pay-server-svc:30025`。**判断某前缀现在归谁 MUST 现查 `kubectl get vs fep-app-vr -n itp -o yaml` 或 envoy `config_dump`，NEVER 用 `kubectl get vs -A` 的摘要表**（那张表不显示 path）。Runbook 是 `docs/ops/流量切换.md`。

**第 2 层 — 本模块内部的 5 个路径族**（`@PostMapping` 实测，43 条映射 / 10 个 Controller）：

| 路径族 | 谁在用 | 约束 |
|---|---|---|
| `/ci/app/**` + `/app/**` 双别名 | `AppAccountController`、`AppParaController`、`AppTicketController`、`AppDailyTicketController`、`GateTxnPayController` | 新增接口 MUST 与同 Controller 内其余方法保持一致 |
| **只有 `/ci/app/**` 单路径** | `CollectPayController`（8 条）、`IndustryDataController`（2 条）、`PaySignController`（除 `unbindAgreement` 外全部） | **NEVER 给单路径 Controller 硬加 `/app/` 别名** |
| `/app/ticket/**` 扁平别名 | `AppTicketController`（6 条 + `realName`）、`AppDailyTicketController`（每条都额外注册一个） | 成因：**APP 端把 `dailyTicket` 段写成了 `ticket`**（2026-09-09 20:03 实测 `/app/ticket/updateTicket` 无映射 → 落静态资源处理器报 `No static resource` → 响应退化成全局异常处理器的 UUID retCode → 日票激活失败）。**两个 Controller 的 `/app/ticket/**` 路径 NEVER 重名**（重名时 Spring 启动即报重复映射） |
| `/app/payment/**` 扁平地址 | `AppDailyTicketController` 的 `requestPay` / `requestPayResult` / `requestRefundTicket` / `receivePayResult` / `receiveRefundResult` | 接口规范 R6 给定、**APP 与 daily-ticket-server 实际都在用这一条**，**NEVER 删扁平那条** |
| `/userData/**` | 只有 `PaySignController.unbindAgreement` 一条 | IF8A-75，APP 实际在调，**NEVER 删，除非 APP 侧确认已切换** |
| `/app/cancelAccount`、`/app/changePhone` | `AppAccountController.userCancel`、`PhoneChangeController.updatePhone` | **严格单路径、按规范收敛**，NEVER 加 `/ci/app` 前缀或旧别名 |

**「只注册带 `dailyTicket` 段的路径」这一类缺陷，`AppDailyTicketController` 已踩过四次**（注释原文记三次 + IF8A-64 那次），四次的共同症状是**服务端无映射 → `No static resource <path>` → HTTP 200 + UUID retCode → 上游看不出失败、疯狂重试**：

| # | 接口 | 缺的路径 | 实测证据（注释原文口径） |
|---|---|---|---|
| 1 | IF8A-67 日票激活 | `/app/ticket/updateTicket` | 2026-09-09 20:03，`No static resource`，日票激活失败 |
| 2 | IF8A-70 旅游票下单 | `/app/ticket/requestTravelOrder` | 2026-09-10 14:26 生产日志，**6 秒内重试 9 次** |
| 3 | if8a_61 日票支付 | `/app/payment/requestPay` | 2026-09-09，该路径此前被 `PaySignController` 占为 IF8A-19，日票支付被透传到 pay-sign、因缺 `amount` 报 `retCode=8001`，APP 显示「创建支付渠道订单失败」，订单 `0E202609091941230001` |
| 4 | IF8A-64 日票退款 | `/app/payment/requestRefundTicket` | 2026-09-11 13:21 生产日志，订单 `0E202609110952160010` **4 秒内重试 2 次** |

**两条回调路径的归属在下游配置里，改一边 MUST 两边同步**（可 grep `MUST 两边同步` 已随注释删除，判据改记于此）：
- `/app/payment/receivePayResult` ← `daily-ticket-server/src/main/resources/application.properties:28` 的 `daily-ticket.pay.notify-url`（daily-ticket-server 自己上报给支付网关的地址）。此前未在本模块注册时，回调被兜成 UUID retCode、**订单永久卡在 `PAYING`**。
- `/app/payment/receiveRefundResult` ← `daily-ticket-server` 的 `daily-ticket.pay.refund-notify-url`（上报给支付中心）。
- **改 `notify-url` 或删本模块任一条路径，MUST 同时改另一边**；只改一边不报错、只表现为回调静默失败。

### 四、`service.*.url` 三类问题在本模块的具体形态

本模块装配了 **14 个 `@EnableRpcXxx`**（`FepAppServer.java`，可 grep `@EnableRpcRoute`）：`Route / Blacklist / Key / Account / Security / PaySign / Ticket / IndustryData / Para / DailyTicket / CollectPay / GateTxnPay / TransQuery / FacePay`。`application.properties` 里现有 **13 组 `service.*.url`**（`Route` 不需要地址）。排查调用不通 **MUST 先按这 14 个注解逐个回头对键，NEVER 只顺着 properties 逐行看** —— 顺着看永远发现不了「压根没写」的那种。

**② 键缺失 —— `service.collectPay.url`（本模块的活样例，已修）**
- 该键**此前整条缺失**：`CollectPayController` 一直在注 `CollectPayClient`（8 条 IF8A 端点全走它），而 properties 里没有对应键。运行时 `rpc` 退化成**默认服务名 `collect-pay-service`**，集群里没有这个 Service ⇒ **CoreDNS 解析不到** ⇒ 8 条端点全废，且**仓库里连一个写错的键都没有**（与 `fep-dev-server` 缺 `service.key.url` 同型，是这一类里最难发现的形态）。
- **2026-09-11 已补齐**：`service.collectPay.url=${SERVICE_COLLECT_PAY_URL:http://collect-pay-c23ku-svc.itp.svc:30024}`（迁移前 `application.properties:48-50`，可 grep `service.collectPay.url`）。
- **本次迁移在该键上留了一行式护栏**（末节护栏 ②）：「本模块 NEVER 依赖 rpc 默认服务名，新增下游 MUST 显式配 `service.*.url`」。**这是唯一还留在代码里的这类告示，NEVER 删。**

**③ 键存在但值指向自身 Pod —— `service.gateTxnPay.url`**
- `AGENTS.md` §8 记录的形态是 `service.gateTxnPay.url=http://127.0.0.1:9106`（`fep-app-server/application.properties:36`，2026-09-08 实测）。在 K8s 里 `127.0.0.1` **等于打到自己这个 Pod**，`fep-app` 自己不听 9106 ⇒ 连接失败 ⇒ 响应退化成全局异常处理器的 **UUID `retCode`**（不是 404、不是超时，最难认的一种表现）。
- **2026-09-16 复核：仓库里那一行已经不是 `127.0.0.1` 了**，现值 `service.gateTxnPay.url=${SERVICE_GATE_TXN_PAY_URL:http://gate-txn-pay-server-jomf4-svc.itp.svc:30019}`（迁移前 `application.properties:41`）。**因此 `AGENTS.md` §8 那条「`:36` 是 `127.0.0.1:9106`」按仓库口径已过期**，见「矛盾与待裁决」第 1 条。
- **本次迁移仍在该键上留了一行式护栏**（末节护栏 ①），措辞按现状调整为「**NEVER 回退成 `127.0.0.1`（在 K8s 里等于打到自身 Pod，表现为 UUID retCode）**」——**没有按原指令写成「此值指向自身 Pod、是已知缺陷」，因为那与现值不符、会变成一条假注释**。

**① 键值错配 —— 本模块的形态是「Service 端口不统一」**
- 迁移前 `application.properties:9-13` 的注释块记着一份实测口径（`kubectl get svc -n itp`，2026-09-11）：**account 9098 / ticket 9100 等于容器端口；security / paySign / blacklist / key 是 8080；para 30026 / gateTxnPay 30019 / industryData 30018 / dailyTicket 30027 / collectPay 30024 / transQuery 30035 / facePay 30025 等于 NodePort 号。**
- **NEVER 按 `server.port` 推断这些端口** —— 这是本模块最容易写错的一类，且写错后症状与「键缺失」一样是 UUID retCode。**MUST 照抄 `kubectl get svc -n itp` 实测值。**
- 三条带变更史的键（注释已迁走）：
  - `service.facePay.url` —— **2026-09-15 新增**，IF8A-26 补款下单已迁入 face-pay-server，**地址 MUST 与 web-admin 的 `service.facePay.url` 保持一致**。
  - `service.transQuery.url` —— **2026-09-14 新增**，IF8A-05 / IF8A-41 / IF8A-34 三个交易查询由 ticket-server 切到 trans-query-server（NodePort 与 Service 端口同为 30035）。**ticket-server 上那三个同路径端点仍在、未删；回滚只需把 `TicketAppServiceImpl` 的三处换回 `ticketClient`。**
  - `industry.issue-channel-code=01` —— `application.properties` 里**唯一带业务语义的非路由键**，原本无注释。
- **判断线上真实地址 MUST 查 `fep-app` Deployment env，NEVER 只看仓库 properties**：env 覆盖 jar 内同名键，「仓库写对」不等于「线上是对的」，反之亦然。

### 五、切流量：APP 域与设备域是**两个**开关，本模块只管其中一个

| 开关 | 位置 | 影响面 | 改法 |
|---|---|---|---|
| **设备域** | `fep-app-vr` VirtualService（ns `itp`）对应前缀那条 route 的 `destination.host` / `port.number` | 只影响**入向**流量（`/itptvm/`、`/itpbom/`、`/itpagm/` 等经网关进来的），内部链路一行不动 | `kubectl patch vs fep-app-vr -n itp --type=json`，**MUST 带 `test` op** 校验下标与原值 |
| **APP 域** | **`fep-app` Deployment 的 env**，如 `service.collectPay.url` | 只影响 `fep-app` 转发给下游的目标，**会触发滚动重启** | `kubectl set env` / 改 Deployment yaml |

- **键名带点、不是下划线大写**：APP 域那个 env 的 key 是 **`service.collectPay.url`**，**NEVER 写成 `SERVICE_COLLECT_PAY_URL`**（Spring relaxed binding 两种都能读，但集群里现存的那条 env 用的是带点形式；照下划线形式新加一条会变成**两条键同时存在**、谁生效取决于 PropertySource 顺序，属于最难查的一类）。
- **「切一个业务域的全部流量」通常要动两处**（网关 route + `fep-app` env）。只改一处的典型后果：设备侧已切到新应用、APP 侧还打旧应用，**两边落到不同的表**（旧 `*_ORDER` vs 新 `F2F_*`），对账时才发现。
- **NEVER 改 K8s Service selector 来切流量**：目标服务未必听同一个 `targetPort`（`collect-pay-c23ku-svc` 的 `targetPort` 是 8080，靠 Deployment env `server.port=8080` 顶掉 yml 里的 58101），且**翻 selector 会连带打断所有「直连该 Service、不经网关」的内部调用方**（recon 对账源、gate-txn-pay 补款、web-admin Quartz）。
- **改前 MUST 记下原值作为回滚命令**；切完 MUST 用「同一份报文分别打新旧服务、比对 retCode」做契约基线核对，并在目标服务日志里确认落点。完整 Runbook：`docs/ops/流量切换.md`。
- **判断流量现在在哪，MUST 现查 VS + `fep-app` env，NEVER 引用本节任何具体地址**（这类值在 2026-09-16 一天内就变过一轮）。

### 六、构建与镜像：三条只在本模块成立的判据

1. **镜像名是 `itp/fep-app`，不是 `itp/fep-app-server`。** 来源是 `fep-app-server/pom.xml:28` 的 `<image.prefix>fep-app</image.prefix>`（可 grep `<image.prefix>`），镜像全名 `os-harbor-svc.default.svc.cloudos:443/itp/fep-app:${project.version}`。**核对 push 日志 / Harbor tag / 远端 `build-push.sh` 的 `JAR_MAP` 时 MUST 先 grep `<image.prefix>`，NEVER 用模块目录名拼镜像名**（已因此漏看过 push 日志、误判成没推成功）。
2. **`remote` profile 有 `activeByDefault=true`，所以 `mvn package` 就会 build + push。** `fep-app-server/pom.xml:83-104`：`<profile><id>remote</id><activation><activeByDefault>true</activeByDefault>`，内含 execution `build-image-remote` / `<phase>package</phase>` / goals `build` + `push`（**都没被注释**）。因此：
   - **只想拿 jar MUST 加 `-Djkube.skip=true`**，否则一次 `package` 就把 `itp/fep-app:<pom version>` 推上 Harbor，并**覆盖同 tag 的旧镜像**（`imagePullPolicy: IfNotPresent` 的节点不会自动换新，需重启 Deployment 或换版本号）。
   - **NEVER 因为 `face-pay-server` 的 `remote` 需要 `-Premote` 就假定本模块也要加** —— 两者恰好相反：本模块默认激活、face-pay 刻意不激活。
   - 判构建成败 **MUST 看 `BUILD SUCCESS` / `[ERROR]` / `BUILD FAILURE`，NEVER 看 shell 退出码**（管道会吃掉 Maven 退出码）；判有没有推镜像 **MUST 过滤 `Pushed` 或 `digest:`**（push 日志有两种措辞，按 `Pushed itp/` 过滤会漏掉带 Harbor 前缀的那种）。
   - `local` profile 另有一份 jkube（`build-image-local`，goals `resource`/`build`/`apply`，镜像 `huateng/fep-app`，Service NodePort 30085 / port 9103）—— **注意那份 `<port>9103</port>` 与本模块真实 `server.port=9101` 不一致**，见「矛盾与待裁决」第 2 条。
3. **给 `model` 的 DTO 加字段后，MUST 连本模块镜像一起重建。** `model` 版本号恒为 `2.0.0`（NEVER 升），`mvn install` 只更新本机 `~/.m2`、对已在跑的 Pod 零影响；旧镜像里打进去的是**旧 DTO 的 class**。而 **Fastjson2 宽松模式静默丢弃目标 DTO 里不存在的字段、不报错不告警** —— 于是 APP 明明上送了新字段，本模块用旧 class 一 `parseBizData` 就丢了，再 RPC 转给下游时字段也不存在，**下游即便已是新 class 收到的也是 `null`**。
   - **实测证据（2026-09-10）**：IF8A-41 给 `RequestTransStatisticsReqDTO` 加 `cardType`，只重建了 `itp/ticket-server:2.1.51`，端到端打 fep-app 时 ticket-server 日志里 `cardType='null'`，**一度误判成代码没生效**；重建并部署 `itp/fep-app:2.0.77` 后立即正常。
   - **判据**：受影响模块 = 链路上**所有会经手该 DTO 的 `parseBizData` / RPC 序列化点**。APP 类接口**至少两个**：`fep-app-server` + 目标业务服务。
   - **NEVER 认为「`mvn install` 过了」就等于生效**；只有重建镜像 + `kubectl set image` 才算。

### 七、跨模块宿主与域边界

**`PaySignController.requestPay`（IF8A-19）的校验宿主是 `PaySignValidators.validateRequestPay`**
- 迁移前位置 `fep-app-server/.../controller/PaySignController.java:45-56`（可 grep `IF8A-19 请求支付`）。
- **本模块只有 `/ci/app/requestPay` 一条路径，NEVER 再挂 `/app/payment/requestPay`** —— 接口规范 R6 里那个地址属于 **if8a_61 日票支付**，已归还 `AppDailyTicketController`（见 §三表格第 3 行）。挂错的后果是**日票支付请求被透传到 pay-sign**，因缺 `amount` / `subject` / `body` / `cardId` / `cardType` 被拦为 `retCode=8001 amount不能为空`。
- **宿主链条（三次变更，NEVER 回退成中间任一版）**：
  1. 最早注释引的是 **`PaySignWorkflow.validateRequestPay`** —— 那个 god class **已于 2026-09-15 删除（ADR-D87）**，引用它的注释一律作废。
  2. 现宿主是 **`pay-sign-server/.../paysign/support/PaySignValidators.validateRequestPay`**（可 grep `PaySignValidators`）。
  3. **唯一调用点是 `PaymentDomainServiceImpl`**（同批拆分迁入）。
- **NEVER 在本模块补一份 `requestPay` 的参数校验** —— 校验语义归 pay-sign 域，本层只做解析 + 转发。本模块唯一做的两件「补默认值」是：`scene` 为空时从 `bizData` 的 `channelType` 兜（可 grep `bizData.containsKey("channelType")`）、`industryType` 为空时置 `"1"`。**这两处是有意为之的兼容，NEVER 当成校验、也 NEVER 删**。

**`DailyTicketAppService` 与日票域的边界**
- 接口 `fep-app-server/.../service/DailyTicketAppService.java`（11 个方法：IF8A-60 / 70 / 61 / 62 / 64 / 65 / 67 / 71 + 支付回调 + 退款回调 + `handlePayResultCallback`），实现 `.../service/impl/DailyTicketAppServiceImpl.java`。
- **边界原文（`DailyTicketAppServiceImpl.java:31-35`，可 grep `日票订单、支付、激活、退款等状态由daily-ticket-server维护`）**：「fep-app-server 只承接 APP 入口和服务编排，**日票订单、支付、激活、退款等状态由 daily-ticket-server 维护**」。同一句在 Controller 类注释上也有一份（`AppDailyTicketController.java:29-30`，「只负责承接 APP 公共 FormData 报文、解析 bizData 并转发到 daily-ticket-server」）。
- **落地判据**：本模块**没有任何日票状态机、没有 `PAYING` / `PAID` 之类的状态字面量、没有金额计算、没有订单表**。`DailyTicketAppServiceImpl` 全部方法都是「转 DTO → 调 `dailyTicketClient` → 回传」。**NEVER 在本模块判断或改写日票订单状态**（改状态值 MUST 去 `daily-ticket-server`，并按 `AGENTS.md` §2.2.1 全局 grep 所有比较点）。
- **两个回调方法的分工**：`handlePayResultCallback(requestBody)` 收**支付网关原始报文**（Controller 用 `@RequestBody String`）；`receiveRefundResult(dto)` 收**ITP 信封形态**（Controller 用 `@ModelAttribute` + `parseBizData`）。**两者不可互换**，理由见 §二末条。
- 另有两条同形的「薄网关」声明，一并迁到此处：`CollectPayController` = 「承接 APP 下单、支付、退款等请求，**透传到 collect-pay-server**」；`PaySignController` = 「**所有接口透传到 pay-sign-server**」；`IndustryDataController` = 「涵盖 IF8A-03 请求行业数据、IF8D-03 请求离线码数据」。**NEVER 在这三个 Controller 里写业务规则、事务或直连 DB**（本模块无数据源）。

### 八、其余接口级注释知识（逐条，均已从代码删除）

| # | 原位置 | 知识 |
|---|---|---|
| 1 | `AppAccountController.java:104-111` | **IF8A-42 用户销户对外契约以规范表 91 为准**：路径**只有** `/app/cancelAccount`，`bizData` 收 `thirdUserId` + `phone`，应答只有 `retCode` + `retMsg`。**NEVER 再加 `/ci/app` 前缀或 `userCancel` 别名**（与 if8a_76 同一口径收敛）。account-server 内部仍叫 `userCancel`，属实现细节 |
| 2 | `PhoneChangeController.java:14-25` | **if8a_76 严格对齐规范 R6**：路径只有 `/app/changePhone`，`bizData` 只认 `newPhone` + `thirdUserId`（表 117），应答只有 `retCode` + `retMsg`（表 118）。**NEVER 再加 `updatePhone` / `/ci/app` 路径别名或 `newMsisdn` 等字段别名** —— 2026-09-09 曾为兼容上游误传临时放宽过 **4 条路径 + 11 个字段名**，与规范核对后已全部收回。**上游传错字段时看日志里的 `rawBizData` 定位，不要再靠放宽入参掩盖问题**（可 grep `rawBizData`，该日志字段仍在代码里） |
| 3 | `PhoneChangeAppServiceImpl.java:35-38` | **换手机号的归属判断顺序：默认按 ITP 用户处理**。account-server 侧 `updatePhone` 只需 `thirdUserId`，内部先 `selectActiveByThirdUserId`，非 ITP 用户查不到即返回失败、不会误写；ITP 侧未成功再走支付宝分支。**NEVER 改回用 `queryUserInfo` 做归属判断** —— 它强制要求 `cardId`，此处拿不到，**会导致 ITP 分支永远进不去、恒定落到支付宝分支返 9999** |
| 4 | `PaySignController.java:103-113` | **IF8A-75 直接解绑支付方式**，与 IF8A-06 请求解约的区别是「**立即向支付渠道发起解绑，不等账期结束的定时任务**，用于用户长时间未登录需强制解除绑定关系的场景」（同一句在 `PaySignAppService.java:25` 也有一份）。`/userData/unbindAgreement` 是 APP 实际在调的路径：2026-09-09 实测销户后 APP 紧接着请求该路径，**因未注册被兜成 UUID retCode + HTTP 200、APP 无法识别失败**，导致用户 `00522948` 已注销但支付宝签约仍为 `SIGNED`、通道残留 `ACTIVE`。三条路径同时保留，**NEVER 删 `/userData/` 这条，除非 APP 侧确认已切换** |
| 5 | `PaySignController.java:143-150` | **`/ci/app/receiveUnsignResult` 是支付中心侧使用的别名路径**，语义与 `receiveTerminationResult` 完全一致，同样转发到 pay-sign-server 的 `/ci/app/receiveTerminationResult`（同一个方法、同一个转发目标） |
| 6 | `PaySignController.java:80-83`、`:129-132`、`:143-146` | 三个回调端点均标注「**兼容 form 和 JSON 两种请求体格式**」（`receivePayResult` / `receiveSignResult` / `receiveTerminationResult`），实现即 §二那条双路兜底 |
| 7 | `AppTicketController.java:76-85` | **`/app/ticket/realName` 是 Mock 桩、不是实现**：APP 端已发布该调用（`bizData` 仅含 `thirdUserId`），服务端无实现、线上持续 404。规范里唯一相关的 IF8A-25「请求实名」**已废弃**、路径为 `/ci/app/requestRealNameVerify` 且应答只有 `retCode`/`retMsg`，**与此调用不是同一接口**。当前仅返回 `0000` 止住 404、不做任何业务处理，**响应字段契约需与 APP 团队确认后替换为真实实现** |
| 8 | `GateTxnPayController.java:16-21`、`:34-39` | **APP 过闸补款透传到 face-pay-server**（2026-09-15 补款功能自 gate-txn-pay-server 迁入）。**IF8A-26 只生成补款单并返回补款单号、不发起支付**；**归属校验在 face-pay-server 侧完成**：`bizData` 传了 `thirdUserId` / `cardId` 就按其校验，未传则从原订单反推（详见 `model` 的 `SupplementOrderReqDTO` 类注释） |
| 9 | `GateTxnPayController.java:47-53` | **IF8A-35 为什么落在 gate-txn-pay-server 而不是 account-server**：只读、供 APP 做欠费提醒（未支付订单数 + 扣费失败订单数），**数据源就是 `GATE_TXN_PAY`**；account-server 没有任何账务表、也没注入 `GateTxnPayClient`，经它中转只是多一跳。与同域的 IF8A-05 / IF8A-26 / IF8A-34 走同一条透传链路。**注意本 Controller 同时注入 `GateTxnPayClient` 与 `FacePayClient`：`requestPayOrder` 走 face-pay、`requestUserAccInfo` 仍走 gate-txn-pay，NEVER 整个换掉其中任一个** |
| 10 | `TicketAppServiceImpl.java:32-35` | **同一个 Service 里两个 Client 并存、分工按方法划**：IF8A-05 / IF8A-41 / IF8A-34 三个交易查询走 `transQueryClient`（trans-query-server 9113）；乘车码状态机、自助补站、支付宝行程仍走 `ticketClient`。**NEVER 把 `ticketClient` 整个换掉** |
| 11 | `IndustryDataServiceImpl.java:276-278` | **行业数据链路已上移到独立生码服务**：「组装独立生码服务请求，**fep-app 不再本地拼接码体和调用签名**」（`buildCardDataRequest`）。**NEVER 在本模块恢复本地拼码或本地调签名** |
| 12 | `IndustryDataServiceImpl.java:269-271` | **HCE 卡数据由开户和闸机交易维护，不参与二维码行业数据生成与签名**（`isHceCard`） |
| 13 | `IndustryDataServiceImpl.java:167` | **离线码请求统一使用 `signChannelCode=17`**（离线码），写死在 IF8D-03 分支里。可 grep `signChannelCode = "17"`（该行内注释是本次**保留**的一行式说明之一，属标准实现注释） |
| 14 | `AppParaController.java:20-25` | 线路、车站、票价、站点版本**均由 para-server 提供**；IF8A-09 的 URL **拼写错误 `requestBuySinlgeTicketMaxNum` 已保留**，另加 `requestSingleTicketMaxNum` 正确拼写别名，两者 × `/ci/app` + `/app` 共 **4 条路径**。**NEVER 删拼错那两条** |
| 15 | `FepAppServer.java:9-11`、`:39-46` | 启动类只有一句「APP 前置服务启动类」，`run(String... args)` 是**空实现**。**装配了 14 个 `@EnableRpcXxx` 但注释未解释任何一个的取舍** —— 取舍判据改记于本节 §四首段 |

### 矛盾与待裁决

| # | 矛盾 | 两侧口径 | 现状处置 | 待裁决点 |
|---|---|---|---|---|
| 1 | **`service.gateTxnPay.url` 到底是不是 `127.0.0.1`** | `AGENTS.md` §8 第三类问题写「`fep-app-server/application.properties:36` 是 `service.gateTxnPay.url=http://127.0.0.1:9106`，2026-09-08 实测」；**2026-09-16 现查仓库该键在 `:41`、值是 `${SERVICE_GATE_TXN_PAY_URL:http://gate-txn-pay-server-jomf4-svc.itp.svc:30019}`**，不含 `127.0.0.1`（全文件 grep `127.0.0.1` 零命中） | **护栏措辞按现状写成「NEVER 回退成 `127.0.0.1`」，没有按原指令写「此值指向自身 Pod、是已知缺陷」** —— 后者与现值不符，写进代码等于制造一条假注释 | `AGENTS.md` §8 那条**按仓库口径已过期**，是否改写为「已修复，NEVER 回退」需用户裁决（**线上 env 是否也已修，MUST 现查 `fep-app` Deployment**，仓库对不等于线上对） |
| 2 | **`local` profile 声明的端口与真实 `server.port` 不一致** | `fep-app-server/pom.xml:197-199` 的 `local` profile Service 写 `<port>9103</port><targetPort>9103</targetPort><nodePort>30085</nodePort>`；而 `application.properties:1` 是 `server.port=9101`，且 9103 是 `ticket-server` / `key-server` 的端口（`AGENTS.md` §2.2.1 已记的三组端口冲突之一） | 未改（`local` profile 未被激活、不影响 `remote` 部署） | 是否把 `local` 那三行改成 9101，或整段删掉 |
| 3 | **`.../fep/app/config/` 与 `.../fep/app/model/` 两个空目录** | 本文件 §报文契约（第 19 行）写「`fep-app-server/src/main/java/.../fep/app/model/` **目录已不存在**」；**实测两个目录都在磁盘上、且都被 SVN 纳管**（`svn ls .../fep/app/` 返回 `config/ controller/ model/ service/`，`ls` 显示两者均为空目录、0 文件） | 未动（空目录不影响编译，删除属 SVN 结构变更） | 本文件第 19 行的「已不存在」MUST 改成「已清空但目录仍在」；两个空目录是否 `svn delete` |
| 4 | `PaySignController` 日志里的 IF 编号与 `AppParaController` 冲突 | `PaySignController.receiveSignResult` 打 `IF8A-07`、`receiveTerminationResult` 打 `IF8A-10`；而 `AppParaController` 的 IF8A-07 是线路代码、IF8A-10 是计算票价 | 未改（属日志文案，改动不影响行为） | 以 `docs/接口规范文档/青岛地铁-ITP与APP接口规范R6.docx` 为准确认哪一处编号错（本文件「已知不一致」章已记，本次未推进） |
| 5 | `AppTicketController` 与 `PaySignController` 都用 IF8A-05 | `requestTransList`（交易记录）、`receivePayResult`（支付回调）、`queryPayTxnBatch`（批量查支付明细）三处日志都打 IF8A-05 | 未改 | 同 4 |
| 6 | **本模块没有任何 DTO，「对外契约 NEVER 加字段」这条护栏没有天然宿主** | 原指令要求「各 DTO 上压成一行」；实测本模块 `src/main` 下**零个 DTO 类**，全部契约 DTO 在 `model` 模块 | 把该护栏一行式挂在 `BaseAppController.parseBizData` 的 Javadoc 上（**解析边界即契约边界**，是本模块唯一能挂的位置） | 是否同时在 `model` 侧那批 `*ReqDTO` 上补同款一行（跨模块，属另一次迁移的范围） |
| 7 | **本模块无 mapper XML** | 原指令要求保留「SQL 正文禁写注释」那条护栏 | **N/A，未落地**：本模块无 DB、无 mapper、无 `*.xml`（`fep-app-server/src/main/resources/` 下只有 `application.properties` 一个文件） | 无 |

### 墓碑清单

「墓碑」= 只说「**不要做某事**」、不解释当前行为的注释。本次**全部从代码删除**，改由本节承载；下表给「原位置 + 禁止的事 + 能否断言化」，是阶段一那张表的**替换版**（阶段一 14 行里 13 行已随注释删除，行号已失效，**NEVER 再引用阶段一那张表的行号**）。

| # | 原位置 | 禁止的事 | 能否断言化 |
|---|---|---|---|
| 1 | `AppAccountController.java:109` | 再加 `/ci/app` 前缀或 `userCancel` 别名 | **能**。扫 `RequestMappingHandlerMapping`，断言 `userCancel` 注册路径集合**恰好等于** `{"/app/cancelAccount"}` |
| 2 | `AppDailyTicketController.java:35` | 与 `AppTicketController` 的 `/app/ticket/**` 别名重名 | **能**。收集两个 Controller 全部 `@PostMapping` 路径断言无交集（Spring 启动即报重复映射，也可直接用「上下文能起来」当断言） |
| 3 | `AppDailyTicketController.java:61` | 删 `/app/ticket/requestTravelOrder` | **能**。断言路径集合包含该串 |
| 4 | `AppDailyTicketController.java:76` | 删 `/app/payment/requestPay` | **能**。同上；并可加「该路径的 handler 属于 `AppDailyTicketController`、**不属于** `PaySignController`」 |
| 5 | `AppDailyTicketController.java:100` | 删 `/app/payment/requestRefundTicket` | **能**。断言路径集合包含该串 |
| 6 | `AppDailyTicketController.java:134` | 改 `notify-url` 或删本路径而不同步另一边 | **部分能**。跨模块断言：读 `daily-ticket-server` 的 `daily-ticket.pay.notify-url`，取 path 段断言在本模块路径集合内；两模块不在同一构建单元，需测试自行读文件 |
| 7 | `AppDailyTicketController.java:150` | 删 `/app/payment/receiveRefundResult` + 不同步 `refund-notify-url` | **部分能**。同 6 |
| 8 | `PaySignController.java:48` | 再挂 `/app/payment/requestPay` | **能**。**负向断言**：`PaySignController` 注册路径集合不含该串（最容易写、价值最高的一条） |
| 9 | `PaySignController.java:112` | 删 `/userData/unbindAgreement` | **能**。断言路径集合包含该串 |
| 10 | `PhoneChangeController.java:21-24` | 再加 `updatePhone` / `/ci/app` 路径别名或 `newMsisdn` 等字段别名 | **路径部分能**（断言恰好等于 `{"/app/changePhone"}`）；**字段别名部分不能** —— 别名会落在 `model` 模块 DTO 的注解上，本模块测不到 |
| 11 | `PhoneChangeAppServiceImpl.java:37-38` | 改回用 `queryUserInfo` 做归属判断 | **部分能**。行为断言：mock `accountClient.updatePhone` 返 `0000` 时断言**不触碰** `alipayAccountClient`；「不得调用 `queryUserInfo`」可用 `verifyNoInteractions` 断言 |
| 12 | `TicketAppServiceImpl.java:34` | 把 `ticketClient` 整个换成 `transQueryClient` | **能**。逐方法 mock 断言：三个查询方法只调 `transQueryClient`，其余方法只调 `ticketClient` |
| 13 | `PaySignController.java:53-55` | 回退成引用已删除的 `PaySignWorkflow` | **不能**（跨模块 + 纯文本约束）。可退化为一条 CI 文本检查：全仓 grep `PaySignWorkflow` 应为 0 命中 |
| 14 | `application.properties:10` | 写 `127.0.0.1`；删任一 `service.*.url` 键 | **能**。配置断言：加载 properties，断言启动类 14 个 `@EnableRpcXxx` 对应的键**全部存在**且值不含 `127.0.0.1` / `localhost`。**这一条已作为一行式护栏保留在代码里**（护栏 ① ②） |
| 15 | `application.properties:13` | 按 `server.port` 推断下游端口 | **不能**。人工操作纪律，端口正确性只有 `kubectl get svc -n itp` 实测能判定 |

### 覆盖率自评

- **文件覆盖 26 / 26（100%）**：25 个 `.java` + 1 个 `application.properties`。两个 SVN 纳管的空目录（`config/`、`model/`）无文件，记在「矛盾」第 3 条。
- **注释行覆盖 320 / 320（100%）**：`.java` **310 行**（python 逐文件扫描、含块注释与行注释、已剔除字符串字面量里的 `//`）+ `application.properties` **10 行**（`:9-13` 路由说明块 5 行、`:44` facePay 1 行、`:48` collectPay 1 行、`:52-54` transQuery 3 行）。**与阶段一「src/main 注释 320 行」的口径一致**；阶段一只抽了 89 行，**本次补齐 231 行**。
- **抽取条目 46 条**：§一 1 条 + §二 8 条 + §三 12 条（5 个路径族 + 4 次事故 + 2 条回调归属 + 1 条网关前缀）+ §四 7 条 + §五 6 条 + §六 3 条 + §七 4 条 + §八 15 条 —— 去重合并后**正文 46 条**，另加 7 条矛盾 + 15 条墓碑。
- **每条都带 `路径:行号` + 可 grep 短语**：行号是**迁移前**位置，短语取自注释或代码原文，**代码里仍存在的短语**（如 `map.put("sign"`、`rawBizData`、`signChannelCode = "17"`、`bizData.containsKey("channelType")`、`<image.prefix>`）标注为可直接 grep 验证。
- **未覆盖 / 有意丢弃**：复述方法名与参数名的纯 Javadoc（`FepAppServer` 的 `@param args`、`DailyTicketAppService` 11 个方法的「IF8A-xx 日票 XX」单句、`PhoneChangeAppService` 的 `@param`/`@return`）—— 这类**不承载知识、且本次未从代码删除**（属标准 Javadoc，按要求保留）。
- **已知盲区 3 处**：①`pom.xml` 与 `docker/Dockerfile` 不在「注释抽取」范围内，§六那三条是**从 pom 结构 + `AGENTS.md` §7 反推**、非注释来源；②线上 `fep-app` Deployment 的 env 未在本次会话现查，§四 / §五涉及线上值的判断**MUST 自行现查**；③本模块**没有任何单元测试**（`src/test` 不存在），墓碑清单里标「能断言化」的 12 条**一条都还没写成测试**。
- **删除侧实测（迁移闭环的另一半）**：`src/main` 注释行 **320 → 187，删除 133 行、涉 14 个文件**（`AppDailyTicketController` -41、`PaySignController` -23、`GateTxnPayController` -11、`AppTicketController` -10、`PhoneChangeController` -9、`AppAccountController` -8、`BaseAppController` -8、`application.properties` -7、`PhoneChangeAppServiceImpl` -4、`AppParaController` -3、`TicketAppServiceImpl` -3、`CollectPayController` -2、`IndustryDataController` -2、`DailyTicketAppServiceImpl` -2）。剩下的 187 行全部是标准 Javadoc + 4 条护栏里的 3 条。**不变量已自证**：python 逐文件剥注释（字符串/字符字面量感知的状态机）后与 `svn cat -r BASE` 比对，**26/26 文件代码逐行一致**，即本次改动**未触碰任何一行可执行代码**。

### 保留的一行式护栏（代码里仅剩这 4 处告示，NEVER 删）

| # | 位置 | 原文 | 说明 |
|---|---|---|---|
| ① | `application.properties` 的 `service.gateTxnPay.url` 行上方 | `# NEVER 回退成 127.0.0.1（K8s 里等于打到自身 Pod，表现为 UUID retCode）。MUST 用 kubectl get svc -n itp 实测地址。` | 按现值调整过措辞，见「矛盾」第 1 条 |
| ② | `application.properties` 的 `service.collectPay.url` 行上方 | `# 本模块 NEVER 依赖 rpc 默认服务名，新增下游 MUST 显式配 service.*.url。` | 原「键此前整条缺失」的事故史已迁走，只留前瞻约束 |
| ③ | `BaseAppController.parseBizData` 的 Javadoc | `<p>能被本方法解析的 DTO 就是对外契约，NEVER 加字段。</p>` | 本模块无 DTO，挂在解析边界上；见「矛盾」第 6 条 |
| ④ | —— | **N/A** | 本模块无 mapper XML、无 SQL，「SQL 正文禁写注释」那条无处可放；见「矛盾」第 7 条 |
