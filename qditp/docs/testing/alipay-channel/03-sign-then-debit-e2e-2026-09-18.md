# 支付宝渠道 先签约再扣费 端到端联调（2026-09-18）

**结论：签约段全通；扣费段链路打通但业务未成功 —— 支付中心返 `code=600 操作失败`，成因是本次用的协议号是自造的、支付中心侧不存在（同 §六）。** 4 个用例的返回码全部符合代码预期。

> ⚠️ **上面这句只适用于 §三那个自造账号。当日 16:44 换用库里真实签约的 `0700001448` 后，扣费成功路径与 `payNotify` 回调落库已完整跑通（见 §十二）。NEVER 把本文当成「测试环境扣不通」的依据。****联调暴露 5 个待裁决问题，其中 3 个是 P0**：扣费全链路零落库、扣费无任何幂等、出向签名是 `sign="test"` 占位且入向回调无验签。见 §七。

## 一、被测环境（现查实测，NEVER 引用本节，MUST 每次重查）

| 项 | 实测值 | 探活 |
|---|---|---|
| 支付宝网关（承接入向） | `fep-alipay`（`itp/fep-alipay:1.0.61`），NodePort **30020** | `http=200` |
| 支付宝账户 | `alipay-account-server`（`itp/alipay-account:1.0.15`），NodePort **30021** | `http=200` |
| 支付宝签约/支付 | `alipay-pay-sign-server`（`itp/alipay-pay-sign:1.1.23`），NodePort **30022** | `http=200` |
| 闸机扣费（扣费真实发起方） | `gate-txn-pay-server`（`itp/gate-txn-pay-server:2.0.90`），NodePort **30019** | `http=200` |
| **另一个 fep-alipay** | `fep-alipay-server`（`itp/fep-alipay:1.0.58`），NodePort **30023** | **`http=000`（不可达）** |
| 目标库 | `172.20.222.3:1521 / AFCITPDB`（`mcp_database_qd`） | — |

## 二、先纠正一个前提：扣费入口不在 fep-alipay

**`fep-alipay` 上没有 `requestPay`。** 4 个 Controller 的类级前缀是 `/channel`、`/memberContract/channel`、`/admin/payment`、`/notify`，`/channel` 下只有 addContract / terminateContract / requestApplication / requestIndustryData / findTravelList / findTravelDetail（`FepAlipayTripController.java:31`）。

**扣费的唯一入口是 `POST /api/payment/requestPay`**（`alipay-pay-sign-server/.../controller/AlipayTripPaymentController.java:23` + `:36`），`@RequestBody` JSON、**无验签无鉴权**，真实发起方是 `gate-txn-pay-server` 的 `PaySignInitiator.java:105`（闸机出站后付费，按 `ISSUE_CHANNEL_CODE=07` 分派）。因此本次扣费段是**直连 30022**，不经网关 —— 这是链路本身的形态，不是测试走了捷径。

## 三、测试账号

| 项 | 值 |
|---|---|
| `THIRD_USER_ID` | `0700009922`（本次新开新签） |
| `CARD_ID` | `0426090949000261`（`CARD_TYPE=02`） |
| `MSISDN` | `15064259922` |
| 协议号 `AGREEMENT_CODE` | `070000992222894922` |
| 渠道协议号 `CHANNEL_AGREEMENT_CODE` | `2088302232559922` |
| 未签约对照账号 | `0700009921`（2026-09-17 开户、未签约） |

## 四、签约段（`/channel/addContract`，经 30020）

请求：

```
bizData={"agreementCode":"070000992222894922","cardIssueCode":"0007","channel":"05","channelAgreementCode":"2088302232559922","channelUserAccount":"2088302232559922","thirdUserId":"0700009922"}
```

响应 `{"retCode":"0000","retMsg":"成功"}` ✅ —— **注意没有 `agreementCode`**，即 2026-09-17 记录的 P1 缺陷（`model` 那份 `AlipayTripAddContractRespDTO` 是空壳）**复现，仍未修**。

`ALIPAY_SIGN_INFO`（`mcp_database_qd` 回查）：

- `SIGN_STATUS='SIGNED'`、`OPERATION_TYPE='SIGN'`、`CHANNEL='ALIPAY'`、`DELETE_FLAG='0'`
- `AGREEMENT_CODE='070000992222894922'`、`CHANNEL_AGREEMENT_CODE='2088302232559922'`
- `CARD_ID='0426090949000261'`、`CARD_TYPE='02'`（从账户域反查回填）
- `SIGN_TIME=2026-09-18 09:24:01`
- **`CHANNEL_SYNC_STATUS='SUCCESS'`、`CHANNEL_SYNC_RETRY_COUNT=1`** —— ADR-D129/D131/D132 的 outbox 改造在线上生效 ✅

`0700009921` 在该表**无行**，与下面用例 B 的 `8011` 一致。

## 五、扣费段用例与实测结果（`POST 30022/api/payment/requestPay`，全部 HTTP 200）

- **A 已签约用户扣费** → `{"retCode":"9999","retMsg":"操作失败","orderNo":"GT20260917E2E00009922"}` ⚠️ 见 §六
- **B 未签约用户（`0700009921`）** → `{"retCode":"8011","retMsg":"用户未签约"}` ✅
- **C 缺 `industryDetail`** → `{"retCode":"8001","retMsg":"订单号/支付金额/行业类型/订单标题/订单描述/行业详情不能为空"}` ✅
- **D 同 `orderNo` 重复扣费** → 与 A 逐字相同，**且日志显示第二次照样组装报文、照样出网** ❌ 无任何幂等，见 §七-2

前置校验只有两条（`PaymentRequestService.java:54~63`）：①六个必填字段非空；②`selectByThirdUserIdAndChannel(thirdUserId,'ALIPAY')` 非 null。SQL 里硬编码 `SIGN_STATUS='SIGNED' AND DELETE_FLAG='0'`（`AlipaySignInfoMapper.xml:39~41`），所以 `TERMINATED` 的用户也会被拒。**没有金额 >0 校验、没有上限、没有幂等。**

## 六、A 用例 9999 的真实成因（日志实证，NEVER 记成「测试环境打不通支付中心」）

`alipay-pay-sign-server` 日志（Pod `alipay-pay-sign-server-586cb59844-mh8tg`，09:24:34~35）：

出向报文完整、字段正确：

```json
{"orderNo":"GT20260917E2E00009922","scene":"TRIP","paymentVendor":"05","amount":1,
 "industryType":"1","subject":"地铁乘车扣费","body":"地铁乘车费用",
 "requestSignSeq":"070000992222894922","thirdUserId":"0700009922","orderTimeOut":60,
 "notifyUrl":"http://alipay-pay-sign-server:8080/api/payment/payNotify",
 "industryDetail":"{\"cardNo\":\"0426090949000261\",\"channelAgreementNo\":\"2088302232559922\"}"}
```

支付中心应答：`code=600, msg=操作失败, success=null`，耗时 1033ms（第二次 92ms）—— **是真实应答，不是超时、不是网络不通**。

`PaymentRequestService.java:98` 的日志原文：「支付宝支付申请未拿到业务应答，按传输层失败处理、NEVER 加黑名单」。

**成因判定**：`requestSignSeq` 送的是我们自造的 `070000992222894922`，支付中心侧不存在这个协议；叠加出向 `sign` 是占位值 `"test"`（`PayCenterClient.java:228~231`）。这与 2026-09-17 解约那次的 `code=600 未查询到协议信息` 同源。**这一条 NEVER 记成「测试环境打不通支付中心」。** 要拿到扣费成功，必须有一个支付中心侧真实存在的协议号 + 真实私钥。

两处出向字段口径值得单独记（都在日志里逐字确认）：

- **`requestSignSeq` 被服务端无条件覆盖成库里的 `AGREEMENT_CODE`**（`PaymentRequestService.java:65`）。入向我送的 `"1"`、上游 `gate-txn-pay` 送的 `order.getTicketTransSeq()`（`AlipayTripPayRequestFactory.java:47`），**都会被丢弃**。原来的非空校验被注释掉了（`:58`）。
- **`industryDetail` 会被 enrich 塞进 `channelAgreementNo`**，取值是 `ALIPAY_SIGN_INFO.CHANNEL_AGREEMENT_CODE`（`BizDataBuilder.java:51` → `:62`）。所以一次扣费送出**两个协议号、来自两列**，排查时别混。

## 七、待裁决问题（联调新发现，均未改码）

**1（P0）扣费全链路零落库。** `PaymentRequestService` 只注了 `AlipaySignInfoMapper` 且只用于 `select`，**一行库都不写**（单测把这条当缺陷现状钉住：`AlipayPaymentCharacterizationTest.java:201`，用例名 `requestPayHasNoPayLogWriterAtAll_currentDefect`）。本次实测复核：`ALIPAY_PAY_LOG` 中 `ORDER_NO LIKE 'GT20260917E2E%'` **0 行**，`ALIPAY_PAY_CALLBACK_LOG` **全表 0 行**。

`ALIPAY_PAY_LOG` 的 `insert` / `updatePayStatus` / `updatePayNotify` 三条写语句在 `src/main` 下**零调用点**，`PayLogBuilder` 也零调用方。连带断链：`payQuery`（`PaymentQueryService.java:67~70`）与 `requestRefund`（`PaymentRefundService.java:68~71`）都以「表里有这一行」为前提，因此**新发起的扣费单事后既查不了也退不了**。库侧该表现有 33 行且**无 `CREATE_TIME` 列**，无法判定是何时写的，只能确定不是当前代码写的。

**2（P0）扣费无幂等。** 用例 D 已实测：同 `orderNo` 第二次照样出网。既无「已存在订单短路」，也无唯一索引兜底（因为压根不写库）。对比退款侧是有的（`PaymentRefundService.java:83~88` PROCESSING 短路 + `:119~128` `UK_ARL_REFUND_ORDER_NO` 竞态兜底）。**闸机重推同一笔出站会重复向支付中心发起扣费。**

**3（P0）双向都没有有效签名。** 出向 `PayCenterClient.java:228~231` 是 `request.setSign("test")`，真正的 `signWithRsa`(`:257`) 无人调用、`pay.center.merchant-private-key` 为空（`application.properties:66`）；入向回调 `POST /api/payment/payNotify` 与网关侧 `/notify/payment/payNotify` **都无验签**（两个类里 grep 不到任何验签方法）。端点裸暴露、任何网络可达方都能伪造扣费成功回调去改 `GATE_TXN_PAY.DEBIT_STATUS`。

**4 回调地址三处不一致、且都指不到活的 handler。**

- `gate-txn-pay` 送出的 `pay.center.callback-url` = `fep-alipay-server-uk4mo-svc.itp.svc:30023/api/payment/payNotify`（`application.properties:37`）—— 而 **30023 本次探活 `http=000`**，且 `fep-alipay` 上根本没有 `/api/payment/**`（真实路径是 `/notify/payment/payNotify`）
- `alipay-pay-sign` 的兜底值是 `http://alipay-pay-sign-server:8080/api/payment/payNotify`（`application.properties:68`）—— 本次日志里出网报文用的就是这个，而集群里 Service 名是 `alipay-pay-sign-server-t3o7n-svc`，**这个短名解析不到**
- `fep-alipay-server/application.properties:56` 的 `alipay.notify.pay-callback-url` 同为不存在的路径

三处**线上真实值 MUST 查 Deployment env 才能定论**，仓库口径是「回调打过去落不到 handler」。这解释了 `ALIPAY_PAY_CALLBACK_LOG` 为什么 0 行 —— 但本次没有成功扣费，无法区分「回调没来」与「回调来了没落」。

**5 退款请求 DTO 少 4 个字段（同 addContract 那类同名 DTO 漂移）。** `model/.../AlipayTripRequestRefundReqDTO.java:6` 有 6 个字段（含 `cardIssueCode` / `cardNum` / `channelAgreementNo` / `refundOrderNo`），而 `alipay-pay-sign-server/.../model/request/AlipayTripRequestRefundReqDTO.java:9` **只有 `orderNo` / `refundAmount` 两个**。fep-alipay 用 model 版发 RPC，Fastjson2 静默丢弃那 4 个值。服务端目前不依赖它们（自己反查 + 自己生成 `refundOrderNo`），**所以现在不出错，但调用方送的那 4 个值等于无效**。

> **2026-09-20 已修**：把 `model` 那份裁回 `orderNo` + `refundAmount` 两个字段（与接收端一致），两个类的 Javadoc 都写明「字段 MUST 同步、那 4 个值由服务端自己解析、NEVER 加回」。**修的是契约谎言、不是行为** —— 服务端本来就自己反查 `cardNum` / `channelAgreementNo` 并自己生成 `refundOrderNo`，这也是刻意的（让调用方指定退款单号或渠道协议号等于把资金键交给外部）。同批 `model` + `rpc` 已 install、`fep-alipay-server` / `alipay-pay-sign-server` 136 例单测全绿；**两个镜像尚未重建，线上仍是旧 class（行为等价，不影响功能）**。同段提到的 `addContract` 那类同名 DTO 漂移**未核、未修**。

**扣费方向没有任何补偿端点、没有 `@Scheduled`、没有 outbox 载体表。** 该模块 3 个 `/internal/**` 分别属于欠款查询（只读）、签约通道同步、解约，都不是扣费补偿。代码注释里自己标了三处缺口：加黑失败永久丢（`PaymentRequestService.java:124~129`）、传输层失败只记日志不留行（`:95~105`）、退款未知结果留 `PROCESSING` 靠人工（`PaymentRefundService.java:143~159`）。

## 八、合规项（本次核对无问题）

- `PaymentRequestService` 全文件 **0 个 `@Transactional`**，因此不存在「事务内发起 RPC」违规；`PaymentQueryService.java:181` / `PaymentRefundService.java:57` 有刻意声明。
- `gate-txn-pay` 侧 `GateTxnPayWriter` 那几个 `@Transactional` 都是纯本地短事务，RPC 在 `PaySignInitiator.converge`(`:71~77`) 里、在事务之外。
- 扣费请求/响应 DTO 虽然也是两份同名类，但**字段逐字一致，无漂移**（各 16 / 3 个字段）。

## 九、从闸机真实入口跑的完整链路（追加，2026-09-18 09:48）

`POST http://172.20.211.23:30019/ci/gateTxnPay/requestPay`（`GateTxnPayController.java:37`，`@RequestBody`），报文：

```json
{"trxType":"02","itpUserId":"0700009922","cardId":"0426090949000261","cardType":"0441",
 "issueChannelCode":"07","signChannelCode":"07","deviceId":"E2ETEST01",
 "handleDateTime":"20260918093000","handleStationCode":"0102",
 "lastHandleStationCode":"0101","lastHandleDateTime":"20260918092000",
 "trxAmount":"200","overtimeAmount":"0","ticketTransSeq":"1","payChannelCode":"07",
 "industryDetail":"{\"cardNo\":\"0426090949000261\"}"}
```

同步应答 `{"retCode":"0000","retMsg":"成功","orderNo":"GT20260918094803693000261","payStatus":"PROCESSING"}`。

**必填只有三项**（`GateTxnPayServiceImpl.validate:202-216`）：`trxType` ∈ {`02`,`03`}（`GateTxnPayFieldCode.isExitTrxType`）、`cardId` 非空、`handleDateTime` 长度 ≥ 8。走支付宝分派只看 `ISSUE_CHANNEL_CODE='07'`。

链路逐跳（`gate-txn-pay-server` 日志，Pod `gate-txn-pay-server-6fcf98c5c8-ln225`）：

1. `09:48:03.693` `buildOrder` 建订单快照。**注意 `order.setThirdUserId(request.getItpUserId())`（`:223`）—— 直接取，不做十六进制解码**，与 `DeviceUserIdCodec` 那条不是同一路
2. `09:48:03.731` `INSERT INTO GATE_TXN_PAY ... 'INIT'`
3. `09:48:03.733` **同步应答已返回**（`payStatus=PROCESSING`）
4. `09:48:03.733` 异步线程 `pay-sign-async-4` 才开始调 `alipay-pay-sign`
5. `09:48:04.398` 收到 `{"retCode":"9999","retMsg":"操作失败"}`（同 §六，支付中心 `code=600`）
6. `09:48:04.403` CAS 回写：`UPDATE GATE_TXN_PAY SET DEBIT_STATUS='RETRY', REMARK='操作失败' ... AND DEBIT_STATUS IN ('INIT','RETRY')`

`GATE_TXN_PAY` 落库回查：`DEBIT_STATUS='RETRY'`、`TOTAL_AMOUNT=200`、`TRX_AMOUNT=200`、`ISSUE_CHANNEL_CODE='07'`、`THIRD_USER_ID='0700009922'`、`REMARK='操作失败'`、`CREATE_TIME=09:48:03` / `UPDATE_TIME=09:48:04`。CAS 白名单与 outbox 式收敛**均按预期工作** ✅

### 9.1 同步应答的 `payStatus=PROCESSING` 是乐观值，与真实扣费结果无关

扣费是 `paySignAsyncExecutor` 异步发起的（`GateTxnPayServiceImpl.java:148`），**应答在 `.733` 就返回、扣费结果 `.402` 才到**。本次闸机拿到的是 `PROCESSING`，而库里最终是 `RETRY`。

**因此上游 NEVER 凭 `/ci/gateTxnPay/requestPay` 的同步 `payStatus` 判断扣费成功**，只能靠回调或查库。这是现行设计（闸机不能等支付中心），不是缺陷 —— 但把它当成扣费结果就是缺陷。

### 9.2（新发现）异步扣费段的 `traceId` 断了，按 traceId 检索会漏掉半条链路

同一笔的日志里，tomcat 线程那几行 traceId 是 `ddfd233ecb61fed3ea58e999fab5f18a`，而 **`[pay-sign-async-4]` 线程的两行 traceId 列是空的**（`PaySignInitiator:102` 与 `:106`）；中间 `InternalMicroHttp` 与 `SqlAudit` 那两行又各自是**另外两个 traceId**（`12ae17377ddc3a86...`、`412edf2f8ac8518e...`，是 WebClient 观测新建的 span）。

`gate-txn-pay-server` 在 §2.2.1 那份 tracing 名单里、开关是开的，所以这不是「没开 tracing」那条。成因是 **`paySignAsyncExecutor` 没做 MDC / 观测上下文传播**。后果：**拿入向 traceId 去检索，只能捞到「建单 + 同步应答」，捞不到「调 alipay-pay-sign + 回写 DEBIT_STATUS」这后半段** —— 而后半段才是排查扣费失败要看的。排查扣费 **MUST 用 `orderNo` 检索，NEVER 只用 traceId**。

### 9.3 站码不存在时不报错，直接用闸机上送金额

日志 `09:48:03.725` WARN：「站名回填未命中任何站码，保留上游站名, inStation=0101, outStation=0102」，且落库 **`ORIGINAL_FARE=null`** —— `fillOriginalFare` 没算出票价，但**没抛异常**，`TOTAL_AMOUNT` 直接等于闸机上送的 `TRX_AMOUNT=200`。

这意味着**票价校验在站码无效时静默失效**，扣多少完全听闸机的。是否可接受需业务裁决（本次是测试造的假站码，真实闸机送的应是有效站码）。

## 十、复核 SQL


```sql
SELECT THIRD_USER_ID, CHANNEL, SIGN_STATUS, AGREEMENT_CODE, CHANNEL_AGREEMENT_CODE,
       CARD_ID, CHANNEL_SYNC_STATUS, CHANNEL_SYNC_RETRY_COUNT
  FROM ALIPAY_SIGN_INFO WHERE THIRD_USER_ID = '0700009922';

SELECT COUNT(*) FROM ALIPAY_PAY_LOG WHERE ORDER_NO LIKE 'GT20260917E2E%';
SELECT COUNT(*) FROM ALIPAY_PAY_CALLBACK_LOG;

SELECT ORDER_NO, DEBIT_STATUS, TOTAL_AMOUNT, ORIGINAL_FARE, ISSUE_CHANNEL_CODE, REMARK
  FROM GATE_TXN_PAY WHERE ORDER_NO = 'GT20260918094803693000261';
```

## 十一、未覆盖

- ~~**扣费成功路径** —— 需要支付中心侧真实存在的协议号 + 真实商户私钥，当前拿不到~~ **已于当日 16:44 覆盖，本条作废、NEVER 回退，见 §十二**
- ~~扣费结果回调 `payNotify` 的落库与 `GATE_TXN_PAY.DEBIT_STATUS` 收敛（依赖上一条）~~ **已覆盖，见 §十二**
- `payQuery` / `requestRefund` —— 都以 `ALIPAY_PAY_LOG` 有行为前提，而扣费不写库，无法在新单上验证
- `SIGN_STATUS='TERMINATED'` 的用户扣费（按 SQL 谓词推断返 8011，未实测）
- 并发同 `orderNo` 扣费（无幂等，预期会重复出网）
- 有效站码下的算价链路（本次站码 `0101`/`0102` 在 para 里不存在、`ORIGINAL_FARE` 为 null，见 §9.3）
- `/ci/gateTxnPay/retryPay` 运营重试入口（订单已是 `RETRY`、满足 `DebitStatus.isRetryable`，可直接试）

## 十二、扣费成功路径已打通（追加，2026-09-18 16:44，为验 `ALIPAY_PAY_TXN_DETAIL.TRANS_TIME` 而跑）

**结论：闸机出站 → 扣费 → 支付中心 → `payNotify` 回调 → 落库的完整链路当日已真实跑通，`TRANS_TIME` 按设计写入。** 因此 §六 与 §十一 首两条那套「必须有真实协议号 + 真实私钥才能扣费成功」的判断**只对 §三那个自造账号成立，NEVER 当成环境结论**。

### 12.1 §三那个测试账号已失效，别再照抄 §九 的报文

现查 `ALIPAY_SIGN_INFO` 全表**只剩 1 行 SIGNED**，`0700009922` 已不在表内：

| 项 | 现行有效值 |
|---|---|
| `THIRD_USER_ID` | `0700001448` |
| `CARD_ID` | `2607031119542741`（`CARD_TYPE='02'`，闸机侧送 `cardType=0441`） |
| `AGREEMENT_CODE` | `070000144847869166` |
| `CHANNEL_AGREEMENT_CODE` | `2088302232551032` |
| 站码 / 设备 | `0245`（合川路）/ `02450604` —— **para 里真实存在，`ORIGINAL_FARE` 能算出来** |

用 §九 的 `0700009922` 打进去只会拿到 `8011 用户未签约`（16:41 实测）。**跑这条链路前 MUST 先现查该表拿当前签约账号，NEVER 引用本表**。

### 12.2 `industryDetail` 里缺 `orderDate` ⇒ 支付中心返 `10002 交易时间orderTime不可为空`

16:44:06 那笔（`GT20260918164406335542741`）industryDetail 只送了 `cardNum` 等 7 个键，支付中心**真实应答** `retCode=10002, retMsg=交易时间orderTime不可为空`，明细行被回写成 `FAIL`。补上 `orderDate` 后立即通过。

连带两点：①`orderTime` 我方**不单独送字段**，它由 `industryDetail.orderDate` 派生；②该笔的加黑前回查又踩了另一个契约洞 —— 支付中心返 `604 逻辑卡号不能为空`，代码按「确认不了」跳过加黑（这是既有设计，见 §七）。

### 12.3 成功那笔的完整证据（`GT20260918164438714542741`）

出站请求走 `POST http://172.20.211.23:30019/ci/gateTxnPay/requestPay`，`ticketTransSeq=41`、`trxAmount=200`，industryDetail 按 15:49 那笔的形状补全（含 `orderDate` / `entryDate` / `exitDate` / `entryId` / `exitId` / 线路站点全套）。

逐跳（`alipay-pay-sign-server` 1.1.40 日志）：

1. `16:44:38.787` INSERT 明细行 `PAY_STATUS='INIT'`、`TRANS_TIME=null`
2. `16:44:38.813` 出向支付中心，`requestSignSeq` 被覆盖成 `070000144847869166`（同 §六那条口径）
3. `16:44:48.482` `PayCenterCallbackController` **收到真实回调**，原始报文：
   ```json
   {"cardNo":"00072607031119542741","channelVoucherId":"2026091823001451031410281354",
    "orderNo":"GT20260918164438714542741","transAmount":"200","transStatus":"1",
    "transTime":"2026-09-18 16:44:41"}
   ```
4. `16:44:48.504` `updatePayCallback` 执行，SQL 里 `TRANS_TIME = NVL('2026-09-18 16:44:41', TRANS_TIME)`，影响 1 行
5. `16:44:48.505` `PayTxnCallbackWriter` 日志带上了 `transTime=2026-09-18 16:44:41`
6. `16:44:48.505` 才去 `syncDebitStatus`（**回写明细在同步扣费订单之前**，与单测钉住的顺序一致）

落库回查：

- `ALIPAY_PAY_TXN_DETAIL`：`PAY_STATUS='SUCCESS'`、`CHANNEL_ORDER_NO='2026091823001451031410281354'`、**`TRANS_TIME='2026-09-18 16:44:41'`**、`PAY_CENTER_ORDER_NO=null`（回调报文里就没有这个字段）
- `GATE_TXN_PAY`：`DEBIT_STATUS='SUCCESS'`、`REMARK='支付结果回调收敛：SUCCESS'`

### 12.4 `TRANS_TIME` 的两条口径（**第一条已于 2026-09-20 被用户裁决翻转，见 §十三**）

- ~~**存的是报文 `transTime` 原文、全链路不解析**~~ —— **已作废**。本次（2026-09-18）拿到的是 `2026-09-18 16:44:41`（`yyyy-MM-dd HH:mm:ss`），而单测钉的另一形态是 `20260918021500`，**「同一列两种格式」正是它被判定为缺陷、随后统一成 14 位的原因**（见 §十三）。列类型仍是 `VARCHAR2(32)`、仍 **NEVER 改成 `DATE`、NEVER 用 `TO_DATE` 查它**（归一失败时按原样入库，列里可能仍有非 14 位的值）。
- **只由支付回调写入且套 `NVL`**（这条不变），所以「回调没带 `transTime`」不会把已有值擦成 null。支付宝出行记录应答的 `payOrderNoDate` 取的就是这一列。

### 12.5 本次残留的测试数据

`GATE_TXN_PAY` / `ALIPAY_PAY_TXN_DETAIL` 各新增 3 行（`GT20260918164144466000261` 8011 未签约、`GT20260918164406335542741` 10002 FAIL、`GT20260918164438714542741` SUCCESS）。**均为新建单，没有改动任何历史行**。

```sql
SELECT ORDER_NO, PAY_STATUS, CHANNEL_ORDER_NO, TRANS_TIME
  FROM ALIPAY_PAY_TXN_DETAIL WHERE ORDER_NO = 'GT20260918164438714542741';

SELECT THIRD_USER_ID, CARD_ID, CARD_TYPE, SIGN_STATUS, AGREEMENT_CODE, CHANNEL_AGREEMENT_CODE
  FROM ALIPAY_SIGN_INFO WHERE SIGN_STATUS = 'SIGNED' AND DELETE_FLAG = '0';
```

## 十三、`TRANS_TIME` 统一成 14 位 `yyyyMMddHHmmss`（追加，2026-09-20，alipay-pay-sign 1.1.43）

用户裁决：「这个需要统一这个列的值，按照一个格式，需要按照地铁 app 和项目中的多数格式落库」。全仓既有口径就是 14 位（`docs/business/ride-code.md:351` / `:679` 的 IF8A-05/34 `payOrderNoDate`、`daily-ticket.md:417` 的 `SimpleDateFormat("yyyyMMddHHmmss")`、同一条应答里的 `entryDate` / `exitDate`），因此**归一放在写入侧**：`PayTxnCallbackWriter.normalizeTransTime` 剥非数字取前 14 位后再入库，存量 2 行由 `alipay-pay-txn-detail-trans-time-normalize-migration.sql` 补齐（`affectedRows=2`）。

端到端复验（订单 `GT20260920101619588542741`，`ticketTransSeq=62`、`trxAmount=200`）：

- 同步应答 `{"retCode":"0000","payStatus":"PROCESSING"}`；`10:16:48` 回调收敛 `PAY_STATUS='SUCCESS'`、`CHANNEL_ORDER_NO='2026092023001451031420996308'`
- **`ALIPAY_PAY_CALLBACK_LOG.TRANS_TIME='2026-09-20 10:16:23'`（LEN=19，原文未动）**
- **`ALIPAY_PAY_TXN_DETAIL.TRANS_TIME='20260920101623'`（LEN=14，已归一）** —— 这一对就是「归一发生在写入侧、台账仍留原文」的硬证据
- `findTravelList` 返 `payOrderNoDate="20260920101623"`、`debitRequestResult="0"`

**NEVER 回退成「明细表存报文原文」**；要举证对端送了什么 MUST 查 `ALIPAY_PAY_CALLBACK_LOG` 的 `TRANS_TIME` / `RAW_BODY`，**NEVER 归一那张表**。
