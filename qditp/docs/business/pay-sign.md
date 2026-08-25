---
业务域: 支付签约 / 解约 / 免密扣款
模块: pay-sign-server
---

# 提示词：支付签约与免密扣款

## 何时读本文件
签约咨询、签约结果、解约（含 ACC 同意解约）、免密支付、退款、渠道回调、APP 异步通知补偿相关改动。

## 模块定位
`pay-sign-server`，端口 **9096**，`spring.application.name=pay-sign`
启动类 `pay-sign-server/.../PaySignServer.java`（`@EnableRpcRoute`、`@EnableRpcAccount`、`@EnableScheduling`）

## 接口清单

| Controller | 前缀 | 接口 |
|---|---|---|
| `PaySignAppController` | `/ci/app` | `requestContractAdvisory`(IF8A-21)、`requestContractResult`(IF8A-22)、`requestTermination`(IF8A-06)、`requestAgreeRelease`(IF8A-36)、`requestPay`(IF8A-19)、`requestRefund`、`receiveSignResult`、`receivePayResult`、`receiveTerminationResult`(IF8B-02)、`queryPayTxnBatch`(IF8A-05)、`updateDisplayAccount` |
| `PaySignController` | 无类级前缀 | `/requestSignInfo`(IF8A-16)、`/querySignInfoBySeq` |
| `PaySignAlipayTripController` | `/channel` | `requestContractAdvisory`、`requestContractResult`、`requestTermination`、`addContract` |
| `PaySignNotifyController` | `/app` | `/receiveSignResult` |
| `PaySignAlipayTripNotifyController` | `/notify` | `receiveSignResult`、`receiveTerminationResult` |
| `TerminationNotifyController` | `/ticket` | `/receiveTerminationResultFromItp` |
| `TerminationInternalController` | `/internal/termination` | `checkFailedOrders`、`execute`、`notifyFailed` |

代码中真实出现的编号：IF8A-05/06/16/19/21/22/36、IF8B-02。

## 核心类
- `service/impl/PaySignWorkflow.java`（**近 2000 行（2026-08-25 实测 1964 行），本域事实上的单一实现中心**）：签约/咨询/解约/支付/退款/回调全在此，事务边界也在此。依赖 `PayGatewayClient`、`AccountClient`、`BlacklistClient`。
- 领域服务壳（均委派回 Workflow，勿误认为独立实现）：`ContractDomainServiceImpl`、`PaymentDomainServiceImpl`、`CallbackDomainServiceImpl`、`PaySignServiceImpl`
- `service/impl/AppNotifyServiceImpl.java`：异步通知 APP 签约/解约/解约失败结果 + 定时补偿
- `service/impl/TerminationInternalServiceImpl.java`：解约内部流程（查扣费失败订单 → 执行解约 → 通知失败）

> 新增逻辑 **MUST** 优先放进对应领域服务而非继续膨胀 `PaySignWorkflow`；但拆分时 **MUST** 保持现有事务边界不变。

## 状态取值（无枚举，String 常量）
- 签约状态（`PaySignWorkflow` 顶部常量）：`NOT_SIGNED`、`SIGNED`、`UNSIGNED`、`FAILED`、`PENDING`、`SCANNING`、`SUCCESS`
- 解约流程（`TerminationInternalServiceImpl`）：`PENDING`、`SCANNING`、`SUCCESS`、`FAILED`
- **MUST** 复用这些常量；新增状态值 **MUST** 全局 grep 字面量确认所有比较点。

真正的枚举只有两个，扩渠道时 **MUST** 改这里：
- `constant/PaymentVendorEnum.java`：`ALIPAY=03`、`WECHAT=04`、`ALIPAY_TRAVEL=05`、`LONG_PAY=06`、`CMB_BANK=0601`、`BOC_BANK=0602`、`CBDC_CONSTRUCTION=08`、`CBDC_BOC=0801`、`CBDC_PSBC=0802`、`CBDC_COMM=0803`、`WALLET=0B`、`CBDC_APP=0C`
- `constant/SignChannelEnum.java`：`METRO_APP` / `ALIPAY` / `WECHAT` / `UNION_PAY` / `LONG_PAY` / `WALLET`
- 错误码：`constant/PaySignErrorCodeEnum.java`

## 数据表
`APP_PAY_SIGN_INFO`、`APP_PAY_SIGN_REQUEST`、`APP_PAY_SIGN_LOG`、`APP_TERMINATION_REQUEST`、`PAY_TXN_DETAIL`、`PAY_REFUND_DETAIL`、`PAY_CALLBACK_LOG`、`APP_USER_PAY_CHANNEL`

DDL 分两处，**改表结构前先确认改哪个脚本**：
- `pay-sign-server/src/main/resources/sql/pay-sign-schema.sql` — `APP_PAY_SIGN_INFO`、`APP_PAY_SIGN_LOG`、`APP_PAY_SIGN_REQUEST`、`APP_USER_PAY_CHANNEL`、`APP_TERMINATION_REQUEST`
- `fep-dev-server/src/main/resources/sql/pay-txn-schema.sql` — `PAY_TXN_DETAIL`(:6)、`PAY_REFUND_DETAIL`(:140)、`PAY_CALLBACK_LOG`(:225)

## 幂等与重试（实际实现）
- 唯一索引：`UK_APP_PAY_SIGN_INFO_USER_VENDOR (THIRD_USER_ID, PAYMENT_VENDOR)`、`UK_ATR_REQUEST_SIGN_SEQ`
- `DuplicateKeyException` 兜底：`PaySignWorkflow.ensurePayTxn`（并发插入重复只 warn，不抛）
- MERGE 幂等写入：`PaySignLogMapper.xml`（`MERGE INTO APP_PAY_SIGN_LOG`）、`PaySignInfoMapper.xml`（`MERGE INTO APP_PAY_SIGN_INFO`）
- 定时补偿：`AppNotifyServiceImpl` 的 `@Scheduled(fixedDelay = 300000)` → `compensateNotify()`，条件 `notifyStatus='FAILED' && retryCount < 3`，走线程池重投；依赖索引 `IDX_ATR_NOTIFY_STATUS(NOTIFY_STATUS, NOTIFY_RETRY_COUNT)`
- **无 MQ、无 Redis、无 Spring Retry**。新增异步补偿 **MUST** 沿用"落库状态 + @Scheduled 扫表"模式。

## 关联依赖
- 解约前置校验调 `gate-txn-pay-server` 的 `/hasFailedOrder`：存在扣费失败订单时不得解约
- 签名工具 `util/RSASignUtils.java`（渠道侧 RSA）。**NEVER** 擅自修改签名/加密逻辑，**MUST** 提示人工复核

## 参考原始文档
- `docs/接口规范文档/ITP与APP接口规范R6_接口清单.md`（IF8A-06/16/19/21/22/36、IF8B-02）
- `docs/技术规范文档/城市轨道交通自动售检票系统技术规范-第9部分-互联网业务规范.docx`
