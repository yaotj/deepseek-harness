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
启动类 `blacklist-server/.../BlacklistServer.java`（`@EnableRpcGateTxnPay` / `@EnableRpcPaySign` / `@EnableRpcRoute`）
`blacklist/controller/BlacklistController.java`：
- 业务：`POST /queryBlackList`、`POST /addBlackList`、`POST /deleteBlackList`（**物理删除**）
- 运营：`GET /page/blacklist`、`POST /page/blacklist`、`DELETE /page/blacklist/{cardId}`

`blacklist/controller/BlacklistInternalController.java`：
- `POST /internal/blacklist/inspectReleasable` — 「可解除性」**只读**盘点，无入参，NEVER 删除任何记录

RPC：`rpc/.../blacklist/BlacklistClient.java` — `queryBlackList`、`addBlackList`、`alipayTripReceiveBlackList`（内部复用查询）、`inspectReleasable`。**没有 `deleteBlackList`**，解除只能走 HTTP 或运营页。
表：`BLACKLIST`、`BLACKLIST_OPERATE_LOG`

### 表结构（2026-09-07 生产实测）
`BLACKLIST` **只有 5 列**：`ID` / `CARD_ID` / `THIRD_USER_ID`（可空）/ `REASON`(VARCHAR2 1000) / `CREATE_TIME`。
**没有拉黑类型、来源、有效期、是否允许自动解除等任何字段**。`BLACKLIST_OPERATE_LOG.OPERATE_TYPE` 只有 `ADD` / `DELETE`，是操作类型不是原因分类；`AddBlackListReqDTO.cardType` 是卡类型且**不落库**（只用于外发通知）。

生产不存在第三张「黑名单历史表」——`%BLACK%` 只有上述两张。

### 四个拉黑入口（物理写点只有 `BlacklistServiceImpl.addBlackList` 一处）
- **A. pay-sign 免密扣款失败** — `PaymentDomainServiceImpl.requestPay`（`:139`）→ `addBlacklistForPaymentFailure`（`:850`）。**2026-09-15 前宿主是已删除的 `PaySignWorkflow`（ADR-D87），旧行号 `:620 → :2250-2285` 已失效。** **有** payQuery 二次确认（`queryGatewayPayStatus`，`:898`），只有支付中心明确回 FAIL 才拉黑；仅 `paymentVendor ∈ {03,05}`。欠费落 `GATE_TXN_PAY`。
- **B. 支付宝出行扣款失败** — `alipay-pay-sign-server/PaymentRequestService.java:124/129 → :158-176`。**无二次确认**。欠费落 `ALIPAY_PAY_LOG.PAY_STATUS='FAIL'`，`GATE_TXN_PAY` 里**没有对应行**。
- **C. `POST /admin/payment/addBlackList`** — `fep-alipay-server/AlipayPaymentServiceImpl.java:70-105` 纯转发，reason 完全由外部传入，仓库内找不到调用方。
- **D. 运营后台手工** — `POST /page/blacklist`，reason 自由文本。

⚠️ **REASON 是四个来源混写的自由文本，NEVER 用它做程序判定**。生产 35 条 ADD 实测分布：`用户挂失补卡` 22、`订单已支付成功，请勿重复支付` 9、`操作失败` 2、`交易超过审核周期` 1、`地铁扣款失败` 1。**占比最高的挂失补卡与欠费无关**，按「欠费结清」自动删除会让挂失旧卡恢复过闸。

⚠️ **B 会把支付中心的幂等拒答当成扣款失败**（「订单已支付成功，请勿重复支付」即由此而来），连带把 `PAY_STATUS` 写成 `FAIL`。因此 `ALIPAY_PAY_LOG` 的 FAIL **不完全等于真欠费**，该缺陷修复前任何按它判定欠费的逻辑都只能供人工参考。

### 可解除性盘点（当前形态）
- 入口：web-admin 的 `blacklistReleaseInspectQuartzTask.inspect()`，cron `0 0 10,16 * * ?`
- 链路：web-admin → `BlacklistClient.inspectReleasable` → blacklist-server `BlacklistReleaseInspectService`
  → 并行问两个源（`GateTxnPayClient.hasUnsettledOrderByCard` + `AlipayPaySignClient.hasUnsettledOrderByCard`）
- 判定：两个源都查成功且都无欠费 = `SETTLED`；任一有欠费 = `UNSETTLED`；任一查询失败 = `UNKNOWN`
- **两个源都要问，NEVER 只查一个**：`addBlackList` 按 cardId 去重，一张卡只有一行，A 先拉黑后 B 重复拉黑时第二次 insert 被跳过，行上留不下第二个来源的痕迹
- `SETTLED` 只代表钱结清，**NEVER 等同于「可以解除」**；本任务只输出日志供人工判断，**不删数据**
- blacklist-server **NEVER 直接查 `GATE_TXN_PAY` / `ALIPAY_PAY_LOG`**（不归它管），MUST 走 rpc

⚠️ 外发通知现状（`service/impl/BlacklistServiceImpl.java`）：
- `alipayPaySignClient.notifyBlackListChange` — **在用**（`service.alipay-pay-sign.url`，生产由 pod env 覆盖为 NodePort 地址）
- `notifyAppBlacklistAsync`（`app.notify.blacklist-url`）、`notifyAlipayBlacklistAsync` — **已被注释掉**

排查"黑名单没通知到 APP" 时 **MUST** 先确认这两处仍是注释状态，**NEVER** 假设通知链路完整。
删除接口是物理删除，新增删除入口 **MUST** 提示数据不可恢复并确认是否应改逻辑删除。

⚠️ `queryBlackList` 是**纯查询**，仓库内唯一调用方是 `fep-app-server/TicketAppServiceImpl.java:38-39`（IF8A-73）。过闸 / 开码 / 开户链路**都不调它**，本地查询不产生拦截，实际「过不了闸」来自出向通知支付宝。`account-server` 的 `IN_BLACKLIST("8005")` 是**死代码**，全仓库无引用。


---

## 2. key-server（端口 9103）
启动类 `key-server/.../KeyServer.java`
`key/controller/KeyController.java`：
- IF8A-02 `POST /requestKeyList` — 按卡类型分流 QR SM2 / HCE DPK
- IF1A-02 `POST /requestAgmSynKeyList` — AGM 密钥同步

RPC：`rpc/.../key/KeyClient.java`
下游：`SecurityClient` → acc-security-server 的
`requestDpk` / `requestUserSm2Key` / `requestSignPubkey` / `requestExportUserPriKey`
地址取 `service.security.url`（`key-server/src/main/resources/application.properties:23`，
当前默认值是集群内网 Service 名 `http://security-server-8h3eo-svc.itp.svc:8080`，2026-09-11 `kubectl get svc -n itp` 实测回填）。
⚠️ **该 Service 端口是 8080，acc-security-server 容器自身的 `server.port` 才是 9012，NEVER 按 `server.port` 拼地址**。
本行原记的 `http://127.0.0.1:9012` 已作废（2026-09-14 核对源码更正），**NEVER 回退**。
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
- `ParaImportController`（`/para/import/directory`、`/para/import/file`、`/para/import/ftp`、`/para/import/ftp/quartz`）

RPC：`rpc/.../para/ParaClient.java`，路径与 APP 侧接口一一对应

**参数文件导入**：`para/service/ParaFileImportService.java`
读 **22 字节文件头**，按 `paraType` 分派：`0001` 路网 / `0002` 日历 / `0003` 票卡 / `0004` 费率；版本号回退则跳过。
新增参数类型 **MUST** 在此分派处扩展并保持文件头长度约定。

**ACC 参数文件 FTP 拉取**（2026-09-08 新增，同事 SVN r420 骨架 + 三处修正合并后的现状）：`para/service/ParaFtpScanService.java` + `para/config/ParaFtpProperties.java`
- ACC 把参数文件放在 FTP 上，ITP 主动拉取。`scanAndImport()` 只做「列目录 → 版本预筛 → 下载到 `Files.createTempDirectory("para-ftp-")` → 调 `ParaFileImportService.importLocalFile`」，临时文件在 `finally` 里删除——**NEVER 把下载塞进 `@Transactional` 方法**（事务内不做网络 IO）。
- 两条触发路径：`POST /para/import/ftp`（人工，返回 `FtpScanResponse` 明细）与 `POST /para/import/ftp/quartz`（供 web-server Quartz 调，返回 `CommonResult`，`retCode=0000` 全成功 / `9999` 有失败）。**NEVER 在 para-server 本模块加 `@Scheduled`**；定时走 web-server 的 `paraQuartzTask.scanFtpPara()`（`quartz/task/ParaQuartzTask.java` → `rpc/.../para/ParaClient.quartzScanFtpPara()` → `service.para.url`），见 `docs/architecture/web-server.md` §七。
- 幂等判据是**「版本号 + MD5」**（2026-09-08 由「仅版本号」改成本形态）。裁决单点在 `ParaFileImportService.importLocalFile`：库中无记录或文件版本号更高 → 导入；版本号更低 → 跳过；**版本号相同则比全文 MD5**，不同才导入，相同则跳过（库中 MD5 为空时按导入处理，顺带把 MD5 补上）。MD5 算法是 `ParaFileReadUtils.md5Hex(bytes, 0, len-16)`，与 `TBL_PARA_VERSION.MD5_VALUE` 存的 `md5Calculated` 同源。
- **为什么必须带 MD5**：ACC 实测出现过同一版本号两份不同内容的文件（`0001` 版本 41 同时存在 4 段命名 `PRM.0001.9900.000041` 与 5 段命名 `PRM.0001.9900.000041.02000000`，MD5 不同）。只比版本号时后到的那份永远进不来，两边数据无法收敛。
- **FTP 侧只能按版本号粗筛**：`findCandidates()` 拿不到 MD5（FTP LIST 无此信息），所以「版本号相同」的文件也会进候选并被下载，MD5 由下载后的单点裁决判定。代价是每轮都要下载 `0001` + `0004`（约 500KB），换来的是同版本换内容可被发现。**NEVER 在 `ParaFtpScanService` 里自己比 MD5**——那会让 `/ftp` 与 `/directory` 两条路径判据分叉。
- ⚠️ `ParaFileReadUtils.md5Hex` 与四个 Parser 的私有 `md5Hex` 是**算法相同的五份实现**（历史遗留，未合并）。改任意一处 MUST 同步其余四处，否则预筛会把内容相同的文件误判成有变更、每轮重复入库。
- **真实远端目录是 `/parameter/cur/`**（2026-09-08 实测）。甲方口述的 `/parameter/cur/97000000/` 在 FTP 上不存在，`CWD` 会被拒；同级 `fur` / `temp` / `input` / `test` 下也没有 `97000000` 子目录。改这个路径 **MUST** 先实测。
- **文件名正则必须容忍第 5 段**：真实文件名形如 `PRM.0001.9900.000041.02000000`，`PARA_FILE_PATTERN` 以 `(?:\.\d+)?$` 收尾。原先写成 `(\d{6})$` 时一个文件都匹配不上、扫描静默返回 0 条。
- **FTP 侧正则当前只认 `0001` / `0004`**，而 `ParaFileImportService` 的 `switch (paraType)` 支持 `0001`~`0004`。`0002`（日历）/`0003`（票卡）已实测能正常解析入库，是否放进正则**待业务确认**；扩展时两处都要改。
- 配置前缀是 `para.ftp.*` / `PARA_FTP_*`，**NEVER 用 `ftp.*` / `FTP_*`**——那组键属于 acc-es-server 的另一台 FTP 服务器，同名会串。
- 依赖新增 `commons-net 3.11.1`（与 acc-es-server 同版本）。**注意 acc-es-server 的 `util/FTP.java` 是死代码**，其真实调用点用的是 hutool `cn.hutool.extra.ftp.Ftp`；para-server 不引 hutool，直接用 commons-net。

---

## 编码约束
- 票价与限购是资金相关参数，改动 **MUST** 提示人工复核，并确认是否需要同步 `docs/` 参数说明
- 这三个服务被大量模块依赖，改动响应 DTO **MUST** 先 grep `rpc` 模块与各调用方，确认无破坏性变更

## 参考原始文档
- `docs/接口规范文档/青岛地铁-ITP与APP接口规范R6.docx`（IF8A-02/07/08/09/10/17/73）
- 技术规范第 4 部分（参数与密钥下发部分）：**源文档不在仓库内**，需向甲方索取。
