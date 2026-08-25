---
业务域: 乘车码与过闸票卡状态
模块: ticket-server, fep-dev-server, industry-data-server
---

# 提示词：乘车码 / 二维码过闸

## 何时读本文件
乘车码码体生成、离线码、闸机检票状态流转、自助补站、行程查询、票卡分析（IF5A）相关改动。

## 模块与端口
- **ticket-server** 9103 — 乘车码状态与交易明细中心（本域核心）
- **fep-dev-server** 9104 — 闸机（AGM）设备前置，**无数据库**，全部 RPC 出站
- **industry-data-server** 9105 — 行业卡数据组装与签名调用
- 依赖 **acc-security-server** 9012 做签名（`SecurityClient` → `/ci/itp/requestSignInsData`）

## 接口清单

**fep-dev-server** `FepAgmController`（前缀 `/ci/agm`）
- IF1A-01 `/notiVerifyResult`（闸机检票结果）
- IF1A-02 `/requestSynKeyList`（密钥同步）
- IF1A-03 `/deviceHeartbeat`、`/notiDeviceHeard`
- IF1A-04 `/requestQrCodeStatus`（票卡状态查询）
- 分派：`DevServiceImpl` → `GateTransactionHandler` / `KeySyncHandler` / `QrCodeStatusHandler`

**ticket-server**
- `TicketAgmController`（`/ci/agm`）：IF1A-01 `notiVerifyResult`、`queryCardStatus`、IF5A-01 `requestCardDataAnalyse`、IF5A-03 **`requestCardDataUpdate`**（AGM 侧写法，`TicketAgmController.java:124`）
- `TicketRideStatusController`（`/ci/app`）：`registerRideStatus`、`queryQrCodeStatus`、IF1A-01 `notiVerifyResult`、IF8A-29 `queryUserItinerary`、IF8A-04 `requestExcessFare`、IF5A-01 `requestCardDataAnalyse`、IF5A-03 **`requestUpdateCardData`**（APP 侧写法，注意与 AGM 侧词序不同，`TicketRideStatusController.java:101`）、`queryEntryDevice`(GET)
- `TicketTransController`（`/ci/app`）：IF8A-05 `requestTransList`、IF8A-41 `requestTransStatistics`、IF8A-34 `requestTransDetail`
- `AlipayTripController`（`/ci/channel`）：`findTravelList`、`findTravelDetail`
- 运营端：`QRCodeRideStatusPageController`（`/page/ride-status`）、`QRCodeTxnDetailPageController`（`/page/qrcode-txn-detail`）

**industry-data-server** `IndustryDataController` — `POST /ci/industry/buildCardData`（无接口编号标注）

## 核心流程
1. **码体生成（IF8A-03 / IF8D-03）**：`fep-app-server/.../service/impl/IndustryDataServiceImpl.java`
   `AccountClient.queryUserInfo` → `TicketClient.queryQrCodeStatus` → `IndustryDataClient.buildCardData`（拼装+签名已下沉 industry-data-server）
   - 离线码固定 `signChannelCode="17"`
   - 已进站态只下发出站码；非进站态生成 entry+exit 双码并对 `txnSeq` +1
   - 行业卡取值（`IndustryCardDataServiceImpl`）：**票种与有效期是可配置项**，`@Value("${industry.ticket-type:0441}")`（:35）、`${industry.timestamp-expire-hours:4}`（:38）；发行渠道 `01` 是入参为空时的兜底默认（`normalizeHex(issueChannelCode,2,"01")`，:96），**不是硬编码固定值**；只有时间戳基准 `BASE_TIME=2000-01-01` 是常量。
2. **过闸主流程**：`ticket-server/.../service/impl/GateTicketHandler.java`
   校验当前状态 → 解析真实卡类型 → 回填上次交易字段 → insert `QRCODE_TXN_DETAIL` → upsert `QRCODE_STATUS` → 日票出站调 daily-ticket `markUsed` → 推送行业数据 / 支付宝行程
3. **协作类**：`AgmRideStatusServiceImpl`、`TicketRideStatusServiceImpl`、`CardDataHandler`、`ExcessFareHandler`、`TransQueryHandler`、`TransRecordAssembler`、`TransMerchantResolver`、`TransStationNameResolver`、`AlipayTripHandler`、`AppNotifyServiceImpl`

## 状态机（权威枚举）
`model/.../model/ticket/enums/QRCodeStatusEnum.java`，落库字段 `QRCODE_STATUS.CODE_STATUS`：

> ⚠️ 仓库中有**两份同名枚举**：`model` 版是所有业务类实际 import 的权威版本；`ticket-server/src/main/java/com/chinasofti/huateng/ticket/enums/QRCodeStatusEnum.java` 是无人引用的副本。改状态值 **MUST** 改 `model` 版，并全局 grep 确认没有代码切换到副本。

`01` 无交易 / `02` 结束行程 / `03` 初始化 / `04` 已进站 / `05` 已出站 / `06` 超时出站 /
`08` 20 分钟内免费更新 / `09` 20 分钟内付费更新 / `10` 入站码更新 /
`70` 异常 / `80` APP 自助补出站 / `81` APP 自助补进站 / `FF` 进站失败

辅助判定：`isClosedLoop()`（02/05/06/80）、`isOpenLoop()`（04/81）。

⚠️ **状态字典不同源**：`GATE_TXN_PAY.TICKET_STATUS` 的建表注释写"04 进站失败、05 已进站"
（`fep-dev-server/src/main/resources/sql/gate-txn-pay-schema.sql`），与 `QRCodeStatusEnum` 冲突。
跨这两张表判断状态时 **MUST** 分别使用各自字典，**NEVER** 直接复用同一常量。

## 数据表
`QRCODE_STATUS`、`QRCODE_TXN_DETAIL`、`STATION_INFO`（mapper 在 `ticket-server/src/main/resources/mapper/`）

## 幂等
- `QRCODE_TXN_DETAIL` insert 捕获 `DuplicateKeyException` 视为重复上送，不中断流程（`GateTicketHandler`）
- `QRCODE_STATUS` 走 `MERGE INTO`（`QRCodeStatusMapper.xml` 的 `upsert`）
- 无 Redis 锁。新增写入 **MUST** 沿用"唯一约束 + MERGE"模式

## 编码约束
- `entity` 字段 `gateStatus`、`txnSeq`、`gateInTime/gateInStation`、`lastTxnTime/lastTxnStation`、`useCount`、`trxAmount` 有明确语义，改动前 **MUST** 读 `ticket-server/.../entity/QRCodeStatus.java`
- fep-dev-server / industry-data-server **无数据库**，**NEVER** 在其中新增 mapper
- 码体签名走 `SecurityClient`，**NEVER** 在业务模块内自行实现 SM2/签名

## 参考原始文档
- `docs/技术规范文档/城市轨道交通自动售检票系统技术规范-第9部分-互联网业务规范.docx`
- `docs/接口规范文档/ITP与APP接口规范R6_接口清单.md`（IF8A-03/04/29/34/41、IF8D-03）
- `docs/业务需求文档/虚拟电子多日计次票接口清单.md`（离线码部分）
