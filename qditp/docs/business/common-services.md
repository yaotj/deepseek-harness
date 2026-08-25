---
业务域: 公共能力（黑名单 / 密钥 / 参数）
模块: blacklist-server, key-server, para-server
---

# 提示词：公共能力服务

## 何时读本文件
黑名单增删查、密钥同步（IF8A-02 / IF1A-02）、线路车站参数与票价、风控规则、退款周期、单程票限购、参数文件导入相关改动。

## 统一约束
- 这三个服务均通过 `rpc` 模块的对应 Client 被调用（`BlacklistClient`、`KeyClient`、`ParaClient`），地址取 `service.blacklist.url` / `service.key.url` / `service.para.url`。
- 新增对外能力 **MUST** 同时在 `rpc` 模块补 Client 方法，**NEVER** 让业务模块自己 new WebClient。

---

## 1. blacklist-server（端口 9102）
启动类 `blacklist-server/.../BlacklistServer.java`
`blacklist/controller/BlacklistController.java`：
- 业务：`POST /queryBlackList`、`POST /addBlackList`、`POST /deleteBlackList`（**物理删除**）
- 运营：`GET /page/blacklist`、`POST /page/blacklist`、`DELETE /page/blacklist/{cardId}`

RPC：`rpc/.../blacklist/BlacklistClient.java` — `queryBlackList`、`addBlackList`、`alipayTripReceiveBlackList`（内部复用查询）
表：`BLACKLIST`、`BLACKLIST_OPERATE_LOG`

⚠️ 外发通知现状（`service/impl/BlacklistServiceImpl.java`）：
- `alipayPaySignClient.notifyBlackListChange` — **在用**
- `notifyAppBlacklistAsync`（`app.notify.blacklist-url`）、`notifyAlipayBlacklistAsync` — **已被注释掉**

排查"黑名单没通知到 APP" 时 **MUST** 先确认这两处仍是注释状态，**NEVER** 假设通知链路完整。
删除接口是物理删除，新增删除入口 **MUST** 提示数据不可恢复并确认是否应改逻辑删除。

---

## 2. key-server（端口 9103）
启动类 `key-server/.../KeyServer.java`
`key/controller/KeyController.java`：
- IF8A-02 `POST /requestKeyList` — 按卡类型分流 QR SM2 / HCE DPK
- IF1A-02 `POST /requestAgmSynKeyList` — AGM 密钥同步

RPC：`rpc/.../key/KeyClient.java`
下游：`SecurityClient` → acc-security-server（`service.security.url=http://127.0.0.1:9012`）的
`requestDpk` / `requestUserSm2Key` / `requestSignPubkey` / `requestExportUserPriKey`
实现类：`key/service/impl/KeySyncServiceImpl.java`

表：`METRO_CA_KEYSTORE`、`METRO_MEMBER_STATIC_KEY`、`METRO_AGM_KEY_POOL`、`METRO_AGM_KEY_VERSION`、`COM_DEVICE_SYN_KEY`
AGM 密钥同步相关脚本：`scripts/20260807_agm_key_sync_schema.sql`、`scripts/20260807_agm_key_sync_initial_data.sql`

⚠️ **安全**：`key-server/src/main/resources/application.properties` 明文写入 `appserver.3des.key` 与 `acc.3des.key`。
触碰该文件 **MUST** 提示人工复核并建议外置到密钥管理，**NEVER** 把密钥值输出到对话或日志。
密钥派生 / 3DES / SM2 逻辑 **NEVER** 擅自修改。

⚠️ 端口 9103 与 ticket-server 相同，同机部署 **MUST** 提示改端口。

---

## 3. para-server（端口 9107）
启动类 `para-server/.../ParaServer.java`

APP 侧 `para/controller/AppParaController.java`（前缀 `/ci/app`）：
- IF8A-09 `/requestBuySinlgeTicketMaxNum`（拼写沿用规范原文）
- IF8A-07 `/requestLineCodeList`、IF8A-08 `/requestStationCodeList`
- IF8A-10 `/requestTicketPriceByStation`、IF8A-17 `/requestLineStationCodeVersion`
- `/requestStationName`、`/requestStationLineInfo`、`/requestStationNameBatch`

运营 / 导入侧：
- `ParaPageController`（`/page/line-info`、`/page/station-info`、`/page/line-station-version`）
- `BaseFarePageController`（`/page/base-fare`、`/stations`）
- `RiskManagementController`（`/page/risk/groups|rules|control-logs`，全 CRUD）
- `OrderRefundCycleController`（`/page/order-refund-cycle`，CRUD）
- `SingleTicketPurchaseLimitController`（`/page/single-ticket-purchase-limit`，GET/PUT）
- `ParaImportController`（`/para/import/directory`、`/para/import/file`）

RPC：`rpc/.../para/ParaClient.java`，路径与 APP 侧接口一一对应

**参数文件导入**（与 ACC 参数下发相关，非 FTP）：`para/service/ParaFileImportService.java`
读 **22 字节文件头**，按 `paraType` 分派：`0001` 路网 / `0002` 日历 / `0003` 票卡 / `0004` 费率；版本号回退则跳过。
新增参数类型 **MUST** 在此分派处扩展并保持文件头长度约定。

---

## 编码约束
- 票价与限购是资金相关参数，改动 **MUST** 提示人工复核，并确认是否需要同步 `docs/` 参数说明
- 这三个服务被大量模块依赖，改动响应 DTO **MUST** 先 grep `rpc` 模块与各调用方，确认无破坏性变更

## 参考原始文档
- `docs/接口规范文档/ITP与APP接口规范R6_接口清单.md`（IF8A-02/07/08/09/10/17/73）
- `docs/技术规范文档/第4部分-系统接口规范-接口清单.md`（参数与密钥下发部分）
