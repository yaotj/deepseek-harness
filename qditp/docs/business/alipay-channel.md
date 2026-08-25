---
业务域: 支付宝渠道（出行 / 碰一下）
模块: fep-alipay-server, alipay-pay-sign-server, alipay-account-server
---

# 提示词：支付宝渠道对接

## 何时读本文件
支付宝出行渠道的签约/解约、渠道侧支付与退款、行程记录查询、渠道开户、黑名单变更通知相关改动。

## 三个模块的分工（勿混淆）
- **fep-alipay-server**：端口 8080，`spring.application.name=fep-alipay`。**薄网关，无 mapper / 无表 / 无事务 / 无定时任务**，入参 `@ModelAttribute CommonFormRequest`，全部经 RPC 下发。
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

**alipay-account-server**
- `FepAlipayTripRequestApplicationController`（`/channel`）：`requestApplication`、`queryUserInfo`、`updatePaymentChannel`、`updatePhone`
- `AlipayUserPageController`（`/page/user/alipay`）：`/search`

> 本域代码中**没有任何 IF 编号标注**。需要对应 IF 编号时 **MUST** 回查 `docs/接口规范文档/`，**NEVER** 自行编造编号。

## 核心类
- 签约登记：`alipay-pay-sign-server/.../service/impl/AlipayContractServiceImpl.java`、`TerminationRegistrationService`、`TerminationNotifier`
- 支付：`AlipayTripPaymentServiceImpl` + 协作类 `PaymentRequestService`、`PaymentQueryService`、`PaymentRefundService`、`PaymentNotifyAdapter`、`RefundAmountCalculator`、`PayLogBuilder`、`SignLogRecorder`、`BizDataBuilder`、`IndustryDetailEnricher`
- 查询：`AlipayPayLogQueryServiceImpl`

## 状态取值（String 常量，无枚举）
- `AlipayContractServiceImpl`：`SIGNED`、`PENDING`，渠道常量 `CHANNEL_ALIPAY="ALIPAY"`
- `TerminationRegistrationService`：`PENDING`、`COMPLETED`
- `TerminationNotifier`：`TERMINATED`、`COMPLETED`、`FAIL`

⚠️ 与 pay-sign-server 的状态词表**不完全一致**（如 pay-sign 用 `SUCCESS`/`UNSIGNED`，此处用 `COMPLETED`/`TERMINATED`）。跨模块对接 **MUST** 显式映射。

## 数据表
`ALIPAY_SIGN_INFO`、`ALIPAY_SIGN_LOG`、`ALIPAY_PAY_LOG`、`ALIPAY_REFUND_LOG`、`ALIPAY_TERMINATION_REQUEST`
- 逻辑删除标记 `DELETE_FLAG='0'`，查询 **MUST** 带此条件（见 `AlipaySignInfoMapper.xml`、`AlipayRefundLogMapper.xml`）
- ⚠️ 本模块**无 `resources/sql` 建表脚本**，唯一索引无法从仓库确认。新增幂等约束前 **MUST** 让用户确认线上 DDL。

## 幂等
仅有 `@Transactional(rollbackFor = Exception.class)` + 查询已存在记录短路。无唯一索引可依赖、无定时任务、无 MQ。
这是本仓幂等最薄的支付链路之一，新增写入 **MUST** 主动补状态短路判断。

## 编码约束
- fep-alipay-server **NEVER** 新增 mapper / 事务，只做转发与报文适配
- 黑名单变更通知：blacklist-server 侧 `alipayPaySignClient.notifyBlackListChange` 已启用；`notifyAppBlacklistAsync`、`notifyAlipayBlacklistAsync` 在 `BlacklistServiceImpl` 中**已被注释**，勿误认为在用
- 渠道签名逻辑 **NEVER** 擅自修改，**MUST** 提示人工复核

## 参考原始文档
- `docs/接口规范文档/ITP与APP接口规范R6_接口清单.md`
- `docs/接口规范文档/青岛地铁-ITP与APP接口规范R6.docx`
