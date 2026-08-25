---
业务域: 闸机出站扣费（后付费）
模块: gate-txn-pay-server
---

# 提示词：闸机出站扣费

## 何时读本文件
后付费乘车码出站扣款、扣费失败重试、APP 交易记录/详情（IF8A-05 / IF8A-34）、解约前的失败订单校验相关改动。

## 模块定位
`gate-txn-pay-server`，端口 **9106**（`gate-txn-pay-server/src/main/resources/application.properties`）。
职责：接收 fep-dev-server 转发的出站交易 → 生成扣费订单 → 异步调 pay-sign 免密扣款；
并为 ticket-server 提供交易查询、为 pay-sign 提供"是否存在扣费失败订单"判断、为运营端提供分页与退款。

## 接口清单
`gate-txn-pay-server/.../controller/GateTxnPayController.java`（前缀 `/ci/gateTxnPay`）
- `/requestPay` — 出站扣费下单
- `/retryPay` — 扣费重试
- `/queryOrderByBizKey`
- `/app/requestTransList`（IF8A-05）、`/app/countTransList`
- `/app/queryByOrderNo`（IF8A-34）
- `/hasFailedOrder` — 供 pay-sign 解约流程判断

`gate-txn-pay-server/.../controller/page/GateTxnPayPageController.java`（前缀 `/page/gate-txn-pay`）
- `GET /` 分页、`POST /{orderNo}/refund`

## 核心类
- `service/impl/GateTxnPayServiceImpl.java` — 扣费主逻辑
- `writer/GateTxnPayWriter.java` — 事务边界与幂等落库（写操作 **MUST** 经此类）
- `mapper/GateTxnPayMapper.java` + `src/main/resources/mapper/GateTxnPayMapper.xml`
- **换乘推送链路**（本模块内独立子链路）：`entity/MetroTransferPushTask.java`、`mapper/MetroTransferPushTaskMapper.java`、`service/impl/MetroTransferPushTaskProcessor.java`、`service/impl/MetroTransferPushClient.java`
- **钱包与优惠**：`service/impl/WalletAppGatewayClient.java`、`entity/DiscountLevel.java` + `mapper/DiscountLevelMapper.java`

## 关键业务规则（改动前必须遵守）
- 只处理 `trxType=02 出站` / `03 超时出站`；其余直接返回 **8001「非出站扣费交易」**
- 订单号规则：`GT + yyyyMMddHHmmssSSS + cardId 后 6 位`
- 金额单位为 **分**；`totalAmount = trxAmount + overtimeAmount`
- 日票（`CardTypeCodeEnum.isDailyTicket`）或 `totalAmount <= 0`：直接入库置 SUCCESS，**不调支付**
- 扣款走 pay-sign 免密代扣，失败订单会阻断用户解约（配合 `/hasFailedOrder`）

## 数据表
`GATE_TXN_PAY`
- Oracle 按 `TXN_DATE` **月分区**，序列 `SEQ_GATE_TXN_PAY`
- DDL：`fep-dev-server/src/main/resources/sql/gate-txn-pay-schema.sql`（注意 DDL 不在本模块目录下）
- 幂等靠两个本地唯一索引：
  - `UK_GATE_TXN_PAY_ORDER_NO(ORDER_NO, TXN_DATE)`
  - `UK_GATE_TXN_PAY_BIZ(CARD_ID, TRX_TYPE, OUT_TIME, TICKET_TRANS_SEQ, DEVICE_ID, TXN_DATE)`

⚠️ 分区表约束：所有唯一索引 **MUST** 包含分区键 `TXN_DATE`。新增唯一约束时不带 `TXN_DATE` 会建表失败。

## 状态字典冲突
`GATE_TXN_PAY.TICKET_STATUS` 注释为"04 进站失败、05 已进站"，与 `QRCodeStatusEnum`（04 已进站、FF 进站失败）**不同源**。
跨表联查或迁移数据时 **MUST** 显式转换，**NEVER** 假设两处状态值可互换。

## 编码约束
- 新增写路径 **MUST** 走 `GateTxnPayWriter` 并依赖唯一索引 + `DuplicateKeyException` 兜底，**NEVER** 引入 Redis 锁
- 涉及扣款金额与免密代扣的改动 **MUST** 提示人工复核资金安全
- 相关迁移脚本参考 `scripts/20260821_pay_txn_detail_data_migration.sql`

## 参考原始文档
- `docs/接口规范文档/ITP与APP接口规范R6_接口清单.md`（IF8A-05、IF8A-34）
- `docs/技术规范文档/城市轨道交通自动售检票系统技术规范-第9部分-互联网业务规范.docx`
