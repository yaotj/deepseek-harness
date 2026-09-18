# 支付宝渠道 先签约再扣费 端到端联调（2026-09-18）

**结论：签约段全通；扣费段链路打通但业务未成功 —— 支付中心返 `code=600 操作失败`，成因是本次用的协议号是自造的、支付中心侧不存在（同 §六）。** 4 个用例的返回码全部符合代码预期。**联调暴露 5 个待裁决问题，其中 3 个是 P0**：扣费全链路零落库、扣费无任何幂等、出向签名是 `sign="test"` 占位且入向回调无验签。见 §七。

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

- **扣费成功路径** —— 需要支付中心侧真实存在的协议号 + 真实商户私钥，当前拿不到
- 扣费结果回调 `payNotify` 的落库与 `GATE_TXN_PAY.DEBIT_STATUS` 收敛（依赖上一条）
- `payQuery` / `requestRefund` —— 都以 `ALIPAY_PAY_LOG` 有行为前提，而扣费不写库，无法在新单上验证
- `SIGN_STATUS='TERMINATED'` 的用户扣费（按 SQL 谓词推断返 8011，未实测）
- 并发同 `orderNo` 扣费（无幂等，预期会重复出网）
- 有效站码下的算价链路（本次站码 `0101`/`0102` 在 para 里不存在、`ORIGINAL_FARE` 为 null，见 §9.3）
- `/ci/gateTxnPay/retryPay` 运营重试入口（订单已是 `RETRY`、满足 `DebitStatus.isRetryable`，可直接试）
