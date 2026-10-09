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
- 业务：`POST /queryBlackList`、`POST /addBlackList`、`POST /deleteBlackList`（**两阶段解除，不再是物理删除**，见下面「解除是两阶段」）
- 运营：`GET /page/blacklist`（支持 `cardId` / `thirdUserId` / `status` / `channelSyncStatus` / `createTimeBegin` / `createTimeEnd` 六个可选筛选 + 分页）、`POST /page/blacklist`、`DELETE /page/blacklist/{cardId}`（带 `releaseReason` / `releaseBy` 两个 `@RequestParam`，**不传就永久丢失审计信息**）

`blacklist/controller/BlacklistInternalController.java`（**2026-09-21 起有 4 个端点、其中三个是写接口**，`POST`）：
- `/internal/blacklist/inspectReleasable` — 「可解除性」**只读**盘点，无入参，NEVER 删除任何记录
- `/internal/blacklist/channel-sync/compensate-add` — 补推**加黑**方向的渠道同步（扫 `STATUS='ACTIVE'` 且 `CHANNEL_SYNC_STATUS IN ('PENDING','FAILED')`）
- `/internal/blacklist/channel-sync/compensate-release` — 补推**解黑**方向（扫**主表**里 `STATUS='RELEASING'` 且同白名单的行；**载体在主表、NEVER 据 `BLACKLIST_RELEASED` 的 `CHANNEL_SYNC_*` 扫表**）
- `/internal/blacklist/auto-release` — **自动解除**（2026-09-21 新增，`sys_job` 230，见下面「自动解除」一节）。**这是本前缀下唯一会改黑名单本体的端点**，`limit` 是可选 `@RequestParam`

⚠️ **该前缀下现在已有三个写接口，但仍无验签** —— `BlacklistInternalController` 类注释里那句「当前只有一个只读盘点接口，因此无需鉴权」**已不成立**，与 AGENTS.md §5.2 冲突，上生产前 MUST 补，**且 MUST 优先覆盖 `/auto-release`**（误调等于把欠费卡提前放行）。四个端点现在**都有 `sys_job` 触发记录**：280 盘点 / 320 加黑补偿 / 325 解黑补偿 / 230 自动解除（**前三个 2026-09-21 随「200 以下全量重编号」由 105 / 125 / 126 改为 280 / 320 / 325，触发目标与 cron 均未变，NEVER 回退成旧号**）；**本文件此前写「两个 compensate 端点还没有 `sys_job` 触发记录、只能手工调」已过期，NEVER 回退**。

RPC：`rpc/.../blacklist/BlacklistClient.java` — `queryBlackList`、`addBlackList`、`alipayTripReceiveBlackList`（内部复用查询）、`inspectReleasable`、`compensateChannelSyncAdd`、`compensateChannelSyncRelease`、`autoRelease`。**仍没有 `deleteBlackList`**：人工解除只能走 HTTP 或运营页，程序化解除只有 `autoRelease` 这一条（且被 `BLACK_CAUSE='01'` 收窄）。
表：`BLACKLIST`、`BLACKLIST_OPERATE_LOG`、**`BLACKLIST_RELEASED`**（解除历史表）

### 解除是两阶段（2026-09-18，blacklist-server 2.0.29，已部署并端到端双分支验通，ADR-D145）
**加黑仍是乐观的**（先落库 + `afterCommit` 异步通知）；**只有解除改成两阶段**：

1. `markReleasing` CAS（`WHERE CARD_ID=? AND STATUS='ACTIVE'`）把行标成 `STATUS='RELEASING'` —— **行仍留在 `BLACKLIST` 里、`queryBlackList` 判黑照样命中、`RELEASE_REASON`/`RELEASE_BY` 已落**
2. 提交后出网通知渠道
3. **只有 `RpcOutcome.Ok` 才进阶段二** `completeRelease`：搬 `BLACKLIST_RELEASED` + 删主表行

**为什么加黑不跟着改**：加黑的业务后果是「更严」，通知晚到不会放行不该放行的人；解除反过来 —— 通知没到就放行等于**欠费用户当场能过闸**。**NEVER 把加黑也改成两阶段。**

**`9001` MUST 判 `Unreachable`、NEVER 判 `BizRejected`**（`AlipayBlacklistNotifyPort`）：`REJECTED` 是终态且**不在补偿白名单 `IN ('PENDING','FAILED')` 里**，一旦把「支付中心网关抖动」判成业务拒绝，那张卡就**永久卡在 `RELEASING`、判黑恒命中、补偿再也扫不到**。2026-09-18 实测：支付中心返 HTTP 502 → `PayCenterClient` 吞成 null → alipay 侧统一返 `9001`，而**同一地址 7 分钟后返 `0000`**。真业务拒绝走 `9999` / `8001`。详见 ADR-D145 §五（含这条判据与 alipay 侧码分配的隐式耦合告示）。

### 表结构（2026-09-18 实测，`USER_TAB_COLS`）
**`BLACKLIST` 是 19 列。此前本文件写「只有 5 列」「没有拉黑类型来源字段」「生产不存在第三张黑名单历史表」—— 三条全部已作废，NEVER 回退。**

关键列与长度（**长度都很短，联调造数必踩**）：
- `CARD_ID VARCHAR2(32) NOT NULL`、`THIRD_USER_ID VARCHAR2(16)`、**`CARD_TYPE VARCHAR2(8)`**（不是 32）
- **`CHANNEL_CODE VARCHAR2(2) NOT NULL DEFAULT '99'`** —— **只能送两位码**；实测送 `ALIPAY` 报 `ORA-12899`，支付宝是 `01`
- `BLACK_SOURCE VARCHAR2(2) NOT NULL DEFAULT '09'`、`BLACK_CAUSE VARCHAR2(2) NOT NULL DEFAULT '09'`
- `BIZ_NO VARCHAR2(128)`、`REASON VARCHAR2(1000)`、`CREATE_BY VARCHAR2(32)`
- outbox 四列：`CHANNEL_SYNC_STATUS VARCHAR2(16)` / `CHANNEL_SYNC_TIME` / `CHANNEL_SYNC_RETRY NUMBER DEFAULT 0` / `CHANNEL_SYNC_FAIL_REASON VARCHAR2(500)`
- 两阶段三列：`STATUS VARCHAR2(16) DEFAULT 'ACTIVE'` / `RELEASE_REASON VARCHAR2(500)` / `RELEASE_BY VARCHAR2(32)`
- `ID` 是 Oracle **IDENTITY**，**库里没有对应序列、NEVER 改成 `selectKey` 取 nextval**

⚠️ **Oracle 列 DEFAULT 只在「列不出现在 INSERT 列表里」时生效**，而 `insert` 的列清单里有 `STATUS` / `CHANNEL_CODE` ⇒ **显式传 null 会真落 null、把 DEFAULT 顶掉**。已因此发生两个缺陷（均已修，见 ADR-D145 §三）：`STATUS` 落 null 时三条带 `STATUS='ACTIVE'` 谓词的读路径全扫不到该行（**那张卡再也解不掉**）；`CHANNEL_CODE` 落 null 触发 `ORA-01400` 而被 `isConflict` 当成幂等命中静默吞掉。**新增列进 insert 清单时 MUST 在 `buildBlacklist` 里显式赋值，NEVER 指望列 DEFAULT。**

`BLACKLIST_OPERATE_LOG.OPERATE_TYPE` 只有 `ADD` / `DELETE`，是操作类型不是原因分类。**`BLACKLIST_OPERATE_LOG.CARD_ID` 是 16 位、`BLACKLIST.CARD_ID` 是 32 位**，列长分叉未修。

### 四个拉黑入口（物理写点只有 `BlacklistServiceImpl.addBlackList` 一处）

- **A. pay-sign 免密扣款失败** — `PaymentDomainServiceImpl.requestPay`（`:139`）→ `addBlacklistForPaymentFailure`（`:850`）。**2026-09-15 前宿主是已删除的 `PaySignWorkflow`（ADR-D87），旧行号 `:620 → :2250-2285` 已失效。** **有** payQuery 二次确认（`queryGatewayPayStatus`，`:898`），只有支付中心明确回 FAIL 才拉黑；仅 `paymentVendor ∈ {03,05}`。欠费落 `GATE_TXN_PAY`。
- **B. 支付宝出行扣款失败** — **发起点是三个**，统一经 `port/BlacklistPort` → `BlacklistRpcAdapter.addBlackList`（`:29`→`:42`）出网：`service/impl/pay/AlipayPayRequestServiceImpl`（`:197` → `addBlackListIfNeeded` `:309`）、`service/impl/payment/PaymentRequestService`（`:92` → `:131`）、`service/impl/payment/AlipayTxnPayService`（`:180` → `:270`）。**本文件此前只列 `PaymentRequestService.java:124/129 → :158-176` 一处、行号也已全错，NEVER 回退**。**「无二次确认」这条现在不全成立**：`domain/PayCenterTradeStatus` 的类注释明确它是 `AlipayPayRequestServiceImpl.addBlackListIfNeeded` 的「确认真的失败」判据，**判断某个发起点到底有没有二次确认 MUST 现读那三个方法，NEVER 沿用本行的旧断言**。欠费落 `ALIPAY_PAY_LOG.PAY_STATUS='FAIL'`，`GATE_TXN_PAY` 里**没有对应行**。
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
- 两个源的查询已抽成 `service/CardUnsettledQuery`（`gateUnsettled` / `alipayUnsettled`，三态：`TRUE` 有欠费 / `FALSE` 已结清 / `null` 查询未成功执行），**盘点与自动解除共用这一份**，NEVER 在任一侧再抄一份私有方法

### 自动解除（2026-09-21，blacklist-server 2.0.30 + web-admin 1.1.38，已部署并端到端验通）
**本文件此前那句「整条链路里没有自动解除、只能人工」已作废，NEVER 回退。**

- 入口 `POST /internal/blacklist/auto-release` → `service/BlacklistAutoReleaseService`（**不带 `@Transactional`**，方法内有出网）
- 触发方：web-admin 的 `sys_job` **230「自动解除黑名单」**，`blacklistAutoReleaseQuartzTask.autoRelease()`，cron `0 0 10,16 * * ?`
- 取数 `BlacklistMapper.selectForAutoRelease`：`STATUS='ACTIVE'` **且 `BLACK_CAUSE='01'`**，`blacklist.auto-release.batch-size` 默认 200
- 渠道路由：`01` 地铁APP → 闸机出站扣费欠费；`02` 支付宝 → 支付宝出行欠费；**`99` 未知渠道跳过**（计入 `skipped`）
- 放行条件只有一个：对应欠费源返回**已结清**。随后**复用 `BlacklistServiceImpl.deleteBlackList`** 走两阶段解除，`releaseReason='欠费已结清自动解除'`、`releaseBy='auto-release-job'`
- 响应六计数互斥且相加等于 `scanned`：`released` / `unsettled` / `unknown` / `skipped` / `failed`

三条 **NEVER**：
1. **NEVER 放宽 `BLACK_CAUSE='01'`** —— 自动解除的前提是「加黑原因已失效」，只有欠费类的失效条件能被机器证明；`02` 挂失补卡即便欠费清了也不能放行（旧卡恢复过闸），`09` 其他只有自由文本 `REASON`、无判据
2. **NEVER 把 `null`（欠费查询未成功执行）当成已结清** —— 那等于「查不通就放行」
3. **NEVER 在本类里自己 `markReleasing` + 删行** —— 会绕过渠道通知与操作日志，出现「本地已解除、支付宝侧仍拉黑」

与 `sys_job` 280 盘点**职责不同、NEVER 合并**：280 只读、覆盖全部加黑原因、结论交人工；230 会改数据、只覆盖能被机器证明的那一类。

**已知覆盖缺口（未闭合）**：`pay-sign-server` 免密扣款失败加黑时刻意写 `channelCode="99"`（`PaymentDomainServiceImpl.java:492`，注释说明不能拿 `paymentVendor` 当渠道）、运营后台默认也是 `99`，因此**这类加黑每轮计入 `skipped` 且永远解不掉**。`skipped` 长期不为 0 **MUST 从加黑入口治**（补 `channelCode`），**NEVER 在解除侧猜渠道**。另有数据脏值：库里存在 `BLACK_CAUSE='1'`（非 `'01'`）的历史行，按契约严格匹配时扫不到，是否归一化待裁决。

⚠️ 外发通知现状（2026-09-18 复核）：
- 出网**唯一**收口在 `blacklist/port/AlipayBlacklistNotifyPort`（把 `AlipayPaySignClient.notifyBlackListChange` 的「空响应返 null、异常上抛」翻成 sealed `RpcOutcome`，调用点穷尽 `switch`）。`BlacklistServiceImpl` 里原先那个 `notifyAlipayExternalBlacklistAsync` **已删除，NEVER 再按那个方法名去找出网点**。
- `notifyAppBlacklistAsync`（`app.notify.blacklist-url`）、`notifyAlipayBlacklistAsync` — **历史上被注释掉、本轮随死代码清理一并删除**。`app.notify.blacklist-url` / `alipay.notify.blacklist-url` 两个键属**死配置**（无读取方）。

排查「黑名单没通知到 APP」时 **MUST** 先确认 APP 方向压根没有实现（不是配错地址），**NEVER** 假设通知链路完整。

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

---

## 附：para-server / key-server / blacklist-server 源码注释知识抽取（2026-09-16，阶段一）

**本仓库有并发写入者，引用任何 `文件:行号` 前 MUST 先 grep 现查，NEVER 直接按行号跳转。**

抽取源：三个模块的 `src/main/java/**/*.java`、`src/main/resources/mapper/*.xml`、`src/main/resources/application.properties` 的全部注释。正文只收①契约与判据 ②决策理由 ③陷阱；墓碑注释见文末清单。已丢弃复述方法名/参数名的普通 Javadoc、`{@inheritDoc}`、空 Javadoc、分节横线。MUST / NEVER 原文保留。

### 一、参数版本与文件导入（para-server）

**契约与判据**

- para-server / `ParaImportController.importFromFtpForQuartz` — `para-server/src/main/java/com/chinasofti/huateng/para/controller/ParaImportController.java:54-56`：「供 web-server Quartz 定时任务调用：扫描FTP参数文件并入库。retCode=0000 表示本次扫描全部成功，存在失败文件时返回 9999。」
- para-server / `ParaFileImportService.importLocalFile` — `para-server/src/main/java/com/chinasofti/huateng/para/service/ParaFileImportService.java:49-54`：「入库判据是「版本号 + MD5」（2026-09-08 由「仅版本号」改成本形态）：库中无该 paraType，或文件版本号更高 → 导入；文件版本号更低 → 跳过（版本回退不处理）；版本号相同：MD5 不同 → 导入；MD5 相同 → 跳过；库中 MD5 为空 → 导入（补齐 MD5）」。
- para-server / `ParaFtpScanService`（类注释）— `para-server/src/main/java/com/chinasofti/huateng/para/service/ParaFtpScanService.java:26-28`：「扫描FTP目录中的路网拓扑(0001)、费率(0004)参数文件，按「版本号 + MD5」判断有无变更，有变更时下载并解析入库。」
- para-server / `ParaFtpScanService.PARA_FILE_PATTERN` — `.../service/ParaFtpScanService.java:36`：「参数文件名规则：PRM. + 参数类型(4位) + . + 节点编码(4位) + . + 版本号(6位) + [. + 尾段]」。
- para-server / `ParaFtpScanService.findCandidates` — `.../service/ParaFtpScanService.java:126-132`：「2026-09-08 起预筛判据是「版本号 + MD5」。FTP LIST 拿不到 MD5，必须下载后才能算，因此本方法只能按版本号做**粗筛**，等版本的文件也要进候选下载：文件版本号 &gt; 库中版本 → 候选（版本升高）；文件版本号 = 库中版本 → 候选（需下载后比 MD5 才能确定有无变更）；文件版本号 &lt; 库中版本 → 跳过（版本回退不处理）」。
- para-server / `ParaFileReadUtils.md5Hex` — `para-server/src/main/java/com/chinasofti/huateng/para/util/ParaFileReadUtils.java:102-103`：「参数文件的 MD5 约定是「除尾部 16 字节校验值外的全文」，即 md5Hex(bytes, 0, len - 16)，结果就是 TBL_PARA_VERSION.MD5_VALUE 存的值。」
- para-server / `ParaImportResult.skippedSameContent` — `para-server/src/main/java/com/chinasofti/huateng/para/model/ParaImportResult.java:29`：「版本号与库中一致、且文件内容 MD5 也一致 —— 真正的「无变更」，跳过。」
- para-server / `ParaFtpScanService.importCandidate`（下载后入库处，行内注释）— `.../service/ParaFtpScanService.java:192`：「解析入库（内部按文件头版本号 + 全文MD5做权威复核，无变更会跳过）」。
- para-server / `ParaFtpProperties.remoteDir` — `para-server/src/main/java/com/chinasofti/huateng/para/config/ParaFtpProperties.java:14`：「ACC 放置参数文件的远端目录。2026-09-08 实测真实路径是 /parameter/cur/，其下没有 97000000 子目录。」
- para-server / `AppParaController`（类注释）— `para-server/src/main/java/com/chinasofti/huateng/para/controller/AppParaController.java:13-14`：「fep-app-server 接收 APP 公共报文后，会通过 rpc 模块调用这里的接口；本 Controller 不处理 APP 公共报文字段，只接收 fep-app 传入的 bizData 对象。」
- para-server / `AppParaController` 的接口编号映射 — 同文件 `:27`「IF8A-09 获取单次购买单程票最大张数」、`:37`「IF8A-07 获取线路代码」、`:49`「IF8A-08 获取车站代码」、`:61`「IF8A-10 计算票价」、`:73`「IF8A-17 获取线路站点代码版本」；`:39` 与 `:75` 均注明「请求参数；接口规范未定义业务字段，允许为空」，`:51`「请求参数；lineCode 为空时返回全部线路车站」。
- para-server / `AppParaController.queryStationName` — 同文件 `:87`：「该接口给内部服务使用，例如 ticket-server 查询上一笔行程时按站点代码补充站名。」
- para-server / `AppParaServiceImpl.requestFareCalc` — `para-server/src/main/java/com/chinasofti/huateng/para/service/impl/AppParaServiceImpl.java:93`：「根据进出站查询费率等级，并按默认 FARE_TYPE = 0 换算为基础票价。」；`:99`「进出站是计算票价的必要条件，缺任意一个都无法继续查询费率矩阵。」；`:123`「数据完整时返回成功，并按 APP 接口字段类型转成字符串。」
- para-server / `AppParaServiceImpl.requestLineStationVersion` — 同文件 `:133`「线路和车站代码同属路网参数文件，当前使用路网参数版本作为二者版本号。」、`:139`「0001 是路网拓扑参数类型，线路和车站代码都来自该参数文件。」、`:143`「如果尚未导入路网参数，返回成功但版本字段为空，避免查询接口异常。」、`:145`「APP 接口要求线路版本和车站版本分别返回，这里二者共用当前路网版本号。」、`:149`「版本变更日期取参数文件头中的生效时间。」
- para-server / `AppParaServiceImpl.requestLineCode` — 同文件 `:70`：「查询类接口只要查询过程正常，即返回成功码。」；`:73`「线路数据取 TBL_PARA_VERSION 中路网参数的当前版本。」；`:87`「request 为空时按全部线路查询；lineCode 有值时只查指定线路车站。」
- para-server / `AppParaQueryMapper.xml` 版本口径 — `para-server/src/main/resources/mapper/AppParaQueryMapper.xml:5-6`「IF8A-07：查询当前路网参数版本下的线路代码。PARA_TYPE = '0001' 表示路网拓扑参数，CURRENT_VER_NO 是当前已入库并生效的版本号。」；`:21-22`「IF8A-08：…transferYn 通过换乘站参数表判断，只要车站出现在换乘关系中即认为是换乘站。」；`:45-46`「IF8A-10：按进出站查询当前费率参数版本中的费率等级。PARA_TYPE = '0004' 表示费率参数，FARE_MATRIX 保存站点到站点的费率等级。」；`:59-60`「IF8A-10：按费率等级查询当前费率参数版本中的基础票价。APP 入参没有票价类型，按默认 FARE_TYPE = 0 查询基础票价。」；`:73-74` 与 `:104` 注明按车站代码查站名、批量查站名均为 ticket-server 行程记录查询使用。

**决策理由**

- para-server / `ParaFileImportService.importLocalFile` — `.../service/ParaFileImportService.java:56-57`：「改成带 MD5 的原因：ACC 实际出现过同一版本号两份不同内容的文件（0001 版本 41 有 4 段命名与 5 段命名两份，MD5 不同）。只比版本号时后到的那份永远进不来，两边数据无法收敛。」
- para-server / `ParaFtpScanService.findCandidates` — `.../service/ParaFtpScanService.java:134-135`：「最终「导入还是跳过」由 ParaFileImportService 单点裁决，本方法 NEVER 自己比 MD5——那会让 /para/import/ftp 与 /para/import/directory 两条路径的判据分叉。」

**陷阱**

- para-server / `ParaFtpScanService.PARA_FILE_PATTERN` — `.../service/ParaFtpScanService.java:37-38`：「2026-09-08 实测 ACC 真实文件名带第 5 段（如 PRM.0001.9900.000041.02000000），因此尾段做可选匹配；原先以 (\d{6})$ 收尾会一个文件都匹配不上。」
- para-server / `ParaFileReadUtils.md5Hex` — `.../util/ParaFileReadUtils.java:105-108`：「⚠️ RowNetworkParser / CalendarParser / TicketParser / RateParser 各有一份**算法完全相同的私有 md5Hex**（历史实现，未合并）。ParaFileImportService 的「版本号 + MD5」预筛依赖本方法与那四份的结果**逐字节一致**——改动任意一处 MUST 同步改其余四处，否则内容相同的文件会被误判成有变更，每轮扫描都重复入库。」

本小节抽取源中**没有**关于「改 `TBL_PARA_VERSION.CURRENT_VER_NO` 做重导验证 NEVER 用当前版本减 1」的任何文字（全模块 grep `CURRENT_VER_NO 减` / `减 1` 均 0 命中），因此不收录。

### 二、密钥同步（key-server）

**契约与判据**

- key-server / `KeyController`（类注释）— `key-server/src/main/java/com/chinasofti/huateng/key/controller/KeyController.java:17-19`：「承接 IF8A-02 请求同步密钥的后端业务编排，供 fep-app-server 调用。按卡类型处理二维码 SM2 密钥或 HCE DPK；APP 侧公共报文解析仍放在 fep-app-server，key-server 只处理 bizData 中的业务参数。」
- key-server / `KeyController.requestKeyList` — 同文件 `:34-35`：「卡类型 `03`/`04` 时返回 HCE 消费 DPK；其余卡均按二维码卡生成用户 SM2 密钥。具体密钥材料均由 acc-security-server 导出后转加密。」
- key-server / `KeySyncServiceImpl`（类注释）— `key-server/src/main/java/com/chinasofti/huateng/key/service/impl/KeySyncServiceImpl.java:47-49`：「处理步骤与旧系统保持一致：查询可用 CA 密钥，随机选择一条 CA，调用 acc-security-server 生成用户 SM2 密钥对，使用 CA 签名用户公钥，导出用户私钥后进行 3DES 转加密，最后组装 keyList 返回 APP。」
- key-server / `KeySyncServiceImpl.requestHceKeyList` — 同文件 `:269-270` 与 `:273`：「安全服务返回的是 ACC KEK 加密的 DPK。key-server 在内存中使用 ACC 3DES 密钥解密，并立即按 APP_SERVER 3DES 密钥转加密，最终只向 APP 返回转加密结果。」「@return keyId=00、keyType=0 的 DPK 密钥列表」。
- key-server / `KeySyncServiceImpl.buildHexEffectiveDate` — 同文件 `:498`：「旧系统按当前时间加 key.sync.days 后转成 2000-01-01 以来秒数的四字节HEX。」
- key-server / `KeySyncService.requestAgmSynKeyList` — `key-server/src/main/java/com/chinasofti/huateng/key/service/KeySyncService.java:32-33`：「Compares an AGM's reported key batches with approved platform batches and returns the key material required to bring the AGM up to date.」
- key-server / `TripleDesEcbUtils`（类注释）— `key-server/src/main/java/com/chinasofti/huateng/key/util/TripleDesEcbUtils.java:14-16`：「IF8A-02 中用户私钥需要先用 ACC 侧 3DES 密钥解密，再用 APP_SERVER 侧 3DES 密钥加密后返回给 APP。旧系统配置可能使用16字节双长度密钥或24字节三长度密钥；本工具在遇到16字节密钥时按 K1 + K2 + K1 扩展为24字节。」
- key-server / `TripleDesEcbUtils.encryptTextToBase64` — 同文件 `:50-51`：「旧工具类使用 DESede/ECB/PKCS5Padding，明文和密钥都按 UTF-8 字符串取字节，加密结果用 Base64 输出。IF8A-02 返回给 APP 的 keyPrivate 需要保持这种格式。」
- key-server / `MetroMemberStaticKey`（类注释）— `key-server/src/main/java/com/chinasofti/huateng/key/entity/MetroMemberStaticKey.java:8`：「仅保存 ACC KEK 加密的 DPK，不能在数据库中保存解密后的 DPK 明文。」
- key-server / `MetroCaKeystore.keyStatus` — `key-server/src/main/java/com/chinasofti/huateng/key/entity/MetroCaKeystore.java:35`：「状态：0初始化，1使用，2暂停。」
- key-server / `KeyVersionQueryServiceImpl.STATUS_DESC` — `key-server/src/main/java/com/chinasofti/huateng/key/service/impl/KeyVersionQueryServiceImpl.java:23-24` 与 `:34`：「状态码→中文的映射集中在 STATUS_DESC：AGM 域用 20010/20020/20030，CA 与 HCE 域用 0/1/2，两套口径共存，NEVER 合并成一张表（两个域的 1 含义不同）。」「域 → (状态码 → 中文)。AGM 与另两域是两套独立口径，键冲突属预期，故按域分表。」
- key-server / `KeyVersionQueryService.listCurrentVersions` — `key-server/src/main/java/com/chinasofti/huateng/key/service/KeyVersionQueryService.java:11-14`：「**安全红线**：本接口只暴露版本号、状态、时间等元信息，NEVER 返回任何密钥材料明文（KEY_VALUE / KEY_PRIVATE / KEY_PUBLIC / KEY_WRAP_VALUE*）。任何一个域查询失败时该域记一条「查询失败」行而不让整体 500，避免单个域的库表问题把整个监控页打挂。」
- key-server / `KeyPageController`（类注释）— `key-server/src/main/java/com/chinasofti/huateng/key/controller/page/KeyPageController.java:15-16`：「**安全红线**：响应 NEVER 包含任何密钥材料明文，只放版本号/状态/时间，见 KeyVersionView 的类注释。」
- key-server / `KeyVersionView`（类注释与字段）— `key-server/src/main/java/com/chinasofti/huateng/key/page/KeyVersionView.java:6-8`：「本视图 NEVER 携带任何密钥材料明文（KEY_VALUE / KEY_PRIVATE / KEY_PUBLIC / KEY_WRAP_VALUE*），只放版本号、状态、时间等元信息。填充方 MUST 逐字段核对来源 SQL 的 select 列表。」；字段口径见 `:11`「密钥域：AGM_KEY（闸机密钥）/ CA_KEYSTORE（CA 密钥仓库）/ HCE_STATIC_KEY（HCE 静态密钥）」、`:13`「接入方编码（仅 AGM 密钥按接入方分组，其余域为 null）」、`:15`「当前版本标识：AGM 为批次号，CA 为 KEY_IDX，HCE 为卡数汇总」、`:17`「状态码（域内各自口径，页面原样展示）」、`:21`「生效日期（源列原样，yyyyMMdd）」。
- key-server / `MetroAgmKeyVersionMapper.selectLatestByProvider` — `key-server/src/main/java/com/chinasofti/huateng/key/mapper/MetroAgmKeyVersionMapper.java:17-18`：「只读元信息（批次号/状态/时间），本表不存密钥材料。NEVER 在本接口加返回 KEY_VALUE 的方法——密钥材料在 METRO_AGM_KEY_POOL，出库即越红线。」
- key-server / `MetroCaKeystoreMapper.selectVersionSummary` — `key-server/src/main/java/com/chinasofti/huateng/key/mapper/MetroCaKeystoreMapper.java:26`：「NEVER 在对应 SQL 里 select KEY_PRIVATE / KEY_PUBLIC / KEY_PAIR。」
- key-server / `MetroMemberStaticKeyMapper.selectStatusSummary` — `key-server/src/main/java/com/chinasofti/huateng/key/mapper/MetroMemberStaticKeyMapper.java:33-35`：「按状态聚合的 HCE 静态密钥卡数汇总（每卡一条、无统一版本号）。NEVER 在对应 SQL 里 select KEY_WRAP_VALUE1 明文。」
- key-server / `MetroMemberStaticKeyMapper.insertIfAbsent` — 同文件 `:25`：「写入静态密钥缓存；若同一卡号已由并发请求写入则保持原有记录。」
- key-server / `MetroAgmKeyVersionMapper.xml` — `key-server/src/main/resources/mapper/MetroAgmKeyVersionMapper.xml:12-14`：「按接入方取最新一条版本记录（按批次号降序取首行）。只读元信息，本表本就不存密钥材料；NEVER 关联 METRO_AGM_KEY_POOL 取 KEY_VALUE。PROVIDER_ID 排序保证多接入方时输出稳定。」
- key-server / `MetroCaKeystoreMapper.xml` — `key-server/src/main/resources/mapper/MetroCaKeystoreMapper.xml:27-29`：「CA 密钥仓库的元信息列表。NEVER 在本语句 select KEY_PRIVATE / KEY_PUBLIC / KEY_PAIR —— 运营页只看版本索引、生效日期、状态，密钥材料明文出库即越红线。」
- key-server / `MetroMemberStaticKeyMapper.xml` — `key-server/src/main/resources/mapper/MetroMemberStaticKeyMapper.xml:21-22`：「HCE 静态密钥按状态聚合计数（每卡一条、无统一版本号，版本列展示状态对应的卡数）。NEVER select KEY_WRAP_VALUE1 明文。」

**决策理由**

- key-server / `KeyVersionQueryServiceImpl`（类注释）— `.../service/impl/KeyVersionQueryServiceImpl.java:20-21`：「三个域各自独立查询：单域失败只降级成一条「查询失败」行，NEVER 让整体接口 500（监控页的意义就是在出问题时仍看得到其它域）。」；`:61`「单域查询 + 域标记 + 状态翻译；失败降级成一条错误行。」

**陷阱**

- 本模块 Java 与 mapper 注释里没有「只在运行时或某一分支暴露」型告示（安全红线类已归入契约）。唯一带运行期误判警告的是配置注释，见第六小节 key-server 一条。

### 三、黑名单与渠道通知（blacklist-server）

> ⚠️ **本节及下方「§三、blacklist-server（9102）：黑名单增删查与渠道通知」都是 2026-09-16 的注释抽取快照，其中与解除链路 / 表结构 / 出网点相关的描述已于 2026-09-18 被 ADR-D145 取代。** 以下四类表述**一律以本文件 §1 与 ADR-D145 为准，NEVER 按本节的旧口径动手**：①「`deleteBlackList` 是物理删除」→ 现为**两阶段解除**；②「`BLACKLIST` 只有 5 列」→ 现为 **19 列**；③「唯一活着的出网点是 `notifyAlipayExternalBlacklistAsync` / `alipay.external.blacklist-url`」→ 该方法**已删除**，出网收口在 `AlipayBlacklistNotifyPort`；④「`/internal/blacklist/**` 只有一个只读端点、无需鉴权」→ 现有**三个写端点**（含 2026-09-21 新增的 `/auto-release`）、鉴权 MUST 补。快照本身保留，是为了留住当时的取证过程；但**其中「为什么不自动解除」那套判据已被 2026-09-21 的自动解除部分推翻 —— 结论不是「不能自动解除」，而是「只有 `BLACK_CAUSE='01'` 欠费类能自动解除」，见 §1「自动解除」一节**。

**契约与判据**

- blacklist-server / `BlacklistInternalController`（类注释）— `blacklist-server/src/main/java/com/chinasofti/huateng/blacklist/controller/BlacklistInternalController.java:14-16`：「当前只有一个只读盘点接口，不改任何数据，因此无需鉴权与归属校验。后续若在本前缀下新增写接口（例如真正执行自动解除），MUST 先补验签（对齐 `AccountRequestVerifier` / `ItpRequestSignVerifier`，NEVER 自造签名逻辑）。」
- blacklist-server / `BlacklistInternalController.inspectReleaseCandidates` — 同文件 `:31` 与 `:33-34`：「盘点黑名单记录的欠费结清情况，供 web-server 的 Quartz 任务调用。」「**只读，NEVER 删除任何黑名单记录。**无入参：批量范围由服务端的 `blacklist.inspect.batch-size` 控制，避免调用方能通过参数放大单次开销。」
- blacklist-server / `BlacklistReleaseInspectService`（类注释）— `blacklist-server/src/main/java/com/chinasofti/huateng/blacklist/service/BlacklistReleaseInspectService.java:23-27`：「**本类 NEVER 删除任何黑名单记录。**它只把每条记录的欠费事实查清楚并输出，供人工核对。原因：`BLACKLIST` 只有 5 列、没有拉黑类型字段，`REASON` 是四个来源混写的自由文本（支付中心应答原文 / 代码拼接模板 / 外部接口传入 / 运营手工输入）。生产实测 35 条 ADD 里 22 条是「用户挂失补卡」——与欠费无关，按「欠费结清」删掉等于让挂失旧卡恢复过闸。「钱结清了」与「可以解除」不是一回事，后者 MUST 由人看 REASON 判断。」
- blacklist-server / `BlacklistReleaseInspectService`（类注释续）— 同文件 `:29-32`：「欠费事实要问两个模块，缺一个就会漏判：闸机出站扣费在 gate-txn-pay-server 的 `GATE_TXN_PAY`，支付宝出行在 alipay-pay-sign-server 的 `ALIPAY_PAY_LOG`，两张表都不归 blacklist-server 管，因此 MUST 走 rpc 只读接口，**NEVER** 在本模块直接写 SQL 查它们。」
- blacklist-server / `BlacklistReleaseInspectService` 三态口径 — 同文件 `:49`「两个欠费源都查成功且都无欠费。只代表钱结清，NEVER 等同于「可以解除」。」、`:51`「至少一个欠费源仍有未结清订单。」、`:53`「至少一个欠费源查询失败，事实不明。」；`:148` 与 `:169`「返回 null 表示查询未成功执行（事实不明）」。
- blacklist-server / `BlacklistMapper.selectForInspect` — `blacklist-server/src/main/java/com/chinasofti/huateng/blacklist/mapper/BlacklistMapper.java:65-66`：「按 CREATE_TIME 升序取最早的 limit 条：先拉黑的先盘点，避免长期积压的记录永远排不上。MUST 带 limit——盘点每条都要跨模块查两次欠费，条数不受控会把单次调度拖成长事务级别的耗时。」
- blacklist-server / `BlacklistReleaseInspectService.batchSize` — `.../service/BlacklistReleaseInspectService.java:63`：「单次盘点上限。每条要跨模块查两次，条数不受控会把单次调度拖得很长。」
- blacklist-server / `BlacklistController.mapOperateResult` — `blacklist-server/src/main/java/com/chinasofti/huateng/blacklist/controller/BlacklistController.java:60`：「内部业务接口使用 0000 协议；运营页面统一转换为 ResultVO 响应。」
- blacklist-server / `BlacklistController.createForPage` / `deleteForPage` — 同文件 `:45` 与 `:51`：「后台新增黑名单，复用原业务逻辑以保留操作日志和渠道同步。」「后台删除指定卡ID，复用原业务逻辑以保留操作日志和渠道同步。」
- blacklist-server / `BlacklistServiceImpl.page` — `blacklist-server/src/main/java/com/chinasofti/huateng/blacklist/service/impl/BlacklistServiceImpl.java:62`：「运营管理页查询不触发外部渠道通知。」
- blacklist-server / 渠道通知的 `blackListType` 取值 — `.../service/impl/BlacklistServiceImpl.java:233`、`:276`、`:320`（三处出向通知方法的同形参数说明）：「黑名单类型，1：加入黑名单，2：移除黑名单」。
- blacklist-server / `BlacklistOperateLog.operateType` — `blacklist-server/src/main/java/com/chinasofti/huateng/blacklist/entity/BlacklistOperateLog.java:25`：「操作类型，ADD新增，DELETE删除。」
- blacklist-server / `BlacklistMapper.xml` — `blacklist-server/src/main/resources/mapper/BlacklistMapper.xml:4`：「黑名单运营页按创建时间倒序，支持卡ID、三方用户ID和时间范围筛选。」；`:70-73`「「可解除性」只读盘点取数：按 CREATE_TIME 升序取最早的 limit 条。升序而非倒序：先拉黑的先盘点，避免积压最久的记录被新记录一直挤到后面。REASON 必须取出来——它是人工判断「这条该不该解除」的唯一依据（BLACKLIST 没有拉黑类型字段，生产实测 REASON 里既有欠费类也有挂失补卡类）。」

**决策理由**

- blacklist-server / `BlacklistReleaseInspectService`（类注释）— `.../service/BlacklistReleaseInspectService.java:34-36`：「不按拉黑来源分流去只查一个源：`BlacklistServiceImpl.addBlackList` 按 cardId 去重，一张卡在表里只有一行，先被闸机链路拉黑、后被支付宝链路重复拉黑时第二次 insert 会被跳过，行上留不下第二个来源的痕迹。只查一个源必然漏判。」
- blacklist-server / `BlacklistReleaseInspectService`（类注释）— 同文件 `:38-39`：「本类**无 `@Transactional`**：方法内有 RPC，事务包住网络调用会让行锁持有时长等于对端响应时长（AGENTS.md 硬约束，已有生产事故）。而且本类只读，本来也不需要事务。」
- blacklist-server / `BlacklistReleaseInspectService.inspectOne` — 同文件 `:117`：「盘点单条记录，任何异常都收敛为 UNKNOWN，不让一条坏数据中断整批。」
- blacklist-server / `BlacklistServiceImpl.addBlackList` — `.../service/impl/BlacklistServiceImpl.java:136`：「TODO 先注释掉地铁APP和内部支付宝通知，只保留支付宝外部通知」（`:137` `notifyAppBlacklistAsync(...)`、`:138` `notifyAlipayBlacklistAsync(...)` 两行为注释掉的调用）。
- blacklist-server / `BlacklistServiceImpl.deleteBlackList` — 同文件 `:171`：「TODO 先注释掉内部支付宝通知，只保留支付宝外部通知」（`:172` `notifyAlipayBlacklistAsync(...)` 为注释掉的调用）。

**陷阱**

- blacklist-server / `BlacklistReleaseInspectService.queryGate` — `.../service/BlacklistReleaseInspectService.java:155-156`：「MUST 先判 resultCode：下游查询未执行时会把 hasUnsettled 置 true，但那是「不明」不是「有欠费」，两者在报表上要区分开。」

### 四、风控与限购（para-server）

**契约与判据**

- para-server / `RiskManagementController`（类注释）— `para-server/src/main/java/com/chinasofti/huateng/para/controller/RiskManagementController.java:22-23`：「运营端风险参数及风险命中记录接口。风险组和风险规则可维护；命中记录属于审计数据，仅允许分页查询。」
- para-server / `RiskManagementController` 逐端点口径 — 同文件 `:56`「删除无关联规则的风险组。」、`:70`「新增风险规则，规则编号在创建后不可变更。」、`:80`「删除风险规则；历史命中日志仍保留规则编号用于审计。」、`:84`「分页查询风险规则命中记录，不提供页面侧写入或删除能力。」
- para-server / `RiskRule` 字段口径 — `para-server/src/main/java/com/chinasofti/huateng/para/entity/risk/RiskRule.java:6`「风险阈值规则定义；规则编号是稳定的业务主键。」、`:11`「可选的所属风险组主键。」、`:14`「关联查询得到的风险组名称，不直接落规则表。」、`:20`「风险等级，限定为1至5。」、`:23`「规则触发阈值或最大值。」
- para-server / `RiskControlLog` — `para-server/src/main/java/com/chinasofti/huateng/para/entity/risk/RiskControlLog.java:5`「风险规则命中审计记录，仅供运营端查询。」、`:19`「左关联当前规则表得到的规则名称，规则删除后可能为空。」、`:22`「风险发生或最后一次命中时间。」
- para-server / `RiskManagementServiceImpl` 校验口径 — `para-server/src/main/java/com/chinasofti/huateng/para/service/impl/RiskManagementServiceImpl.java:24`「页面输入统一在此处做裁剪和边界校验，避免风险参数直接依赖数据库异常提示。」、`:52`「风险组名称唯一，重复时转为可读的业务错误。」、`:83`「删除前先判断规则引用，防止丢失规则归属关系。」、`:99`「新建规则时校验编号、等级与非负阈值。」、`:114`「规则编号取路径参数，防止请求体篡改主键。」、`:149`「删除规则配置；风险控制历史日志不会级联删除。」、`:156`「风险命中记录是审计数据，因此只提供查询。」
- para-server / `RiskRuleMapper.countByGroupId` — `para-server/src/main/java/com/chinasofti/huateng/para/mapper/risk/RiskRuleMapper.java:25`：「统计风险组的引用规则数，用于删除保护。」
- para-server / `SingleTicketPurchaseLimitMapper` — `para-server/src/main/java/com/chinasofti/huateng/para/mapper/ticket/SingleTicketPurchaseLimitMapper.java:10`：「使用版本号更新，防止两个运营人员的修改相互覆盖。」
- para-server / `OrderRefundCycle` — `para-server/src/main/java/com/chinasofti/huateng/para/entity/ticket/OrderRefundCycle.java:5`「票卡类型对应的订单自动退款周期参数，周期单位为天。」、`:7`「唯一票卡类型，作为该参数的业务主键。」、`:10`「自动退款等待周期，单位天。」
- para-server / `OrderRefundCycleServiceImpl` — `para-server/src/main/java/com/chinasofti/huateng/para/service/impl/OrderRefundCycleServiceImpl.java:15`「退款周期参数校验与维护实现，周期范围由服务层和表约束双重保证。」、`:24`「票卡类型为唯一配置键，查询使用精确匹配。」、`:68`「更新以路径票卡类型为准，防止请求体修改配置键。」、`:83`「删除不存在的配置时返回业务错误，避免误以为删除成功。」、`:93`「该范围与 TBL_ORDER_REFUND_CYCLE 的 CHECK 约束保持一致。」
- para-server / `BaseFarePageServiceImpl` — `para-server/src/main/java/com/chinasofti/huateng/para/service/impl/BaseFarePageServiceImpl.java:18`「基础票价页面服务，只读取参数当前版本而不暴露历史版本。」、`:26`「查询费率矩阵与 FARE_TYPE=0 基础票价关联后的当前有效票价。」

**决策理由**

- para-server / `RiskManagementServiceImpl.createGroup` / `updateGroup` — `.../service/impl/RiskManagementServiceImpl.java:52` 与 `:67`：唯一约束冲突「转为可读的业务错误」/「重复名称同样转为业务错误」，即不让库异常直接冒到页面。
- para-server / `OrderRefundCycleServiceImpl.create` — `.../service/impl/OrderRefundCycleServiceImpl.java:32`：「先规范化输入，再将唯一键冲突转为明确提示。」

**陷阱**

- para-server / `RiskManagementServiceImpl.isDuplicateKeyViolation` — `.../service/impl/RiskManagementServiceImpl.java:133-138`：「**MUST** 逐层遍历 cause，**NEVER** 直接 `catch (DuplicateKeyException)`：`MapperAspectToTrace`（`resource/micro/web/src/main/java/com/chinasofti/huateng/micro/monitor/trace/MapperAspectToTrace.java:51`）把 mapper 抛出的任何异常统一包成 `RuntimeException`，单层类型判断在 `management.tracing.enabled=true` 的模块（para-server 即是）捕不到。2026-09-08 实测：重复风险组名原本落到全局异常处理器、返回 UUID retCode，页面完全看不到「名称已存在」。」

### 五、持久层与 mapper（三模块）

**契约与判据**

- para-server / `RiskControlLogMapper.xml` — `para-server/src/main/resources/mapper/RiskControlLogMapper.xml:4`「审计日志使用左关联：规则删除后仍可查询历史命中记录。」、`:12`「命中时间倒序，便于运营人员优先处理最新风险。」
- para-server / `RiskGroupMapper.xml` — `para-server/src/main/resources/mapper/RiskGroupMapper.xml:4`「风险组基础映射，时间字段由数据库的 SYSDATE 维护。」、`:13`「管理页按名称模糊查询，按最近修改时间优先展示。」、`:19`「规则编辑页下拉选项，按名称排序便于选择。」
- para-server / `RiskRuleMapper.xml` — `para-server/src/main/resources/mapper/RiskRuleMapper.xml:4`「风险规则查询通过左关联带出分组名称，允许规则暂未分组。」、`:19`「编号精确匹配，名称模糊匹配，支持按所属风险组筛选。」
- para-server / `OrderRefundCycleMapper.xml` — `para-server/src/main/resources/mapper/OrderRefundCycleMapper.xml:4`「票卡类型为业务主键，创建和修改时间统一由数据库时间生成。」、`:14`「票卡类型为精确筛选条件，保证配置查询可预期。」
- para-server / `BaseFarePageMapper.xml` — `para-server/src/main/resources/mapper/BaseFarePageMapper.xml:4`「票价页只读当前费率版本；基础票价固定关联 FARE_TYPE=0。」、`:44`「进出站筛选只展示当前路网版本的车站，可按线路过滤。」、`:55`「线路下拉只展示当前路网版本的线路。」

**决策理由**

- para-server / `RiskGroupMapper.xml`（insert 语句上方）— `.../mapper/RiskGroupMapper.xml:27-28`：「GROUP_ID 由库侧生成，调用方 RiskManagementServiceImpl.createGroup 不使用回填值。确有回填需求时 MUST 补 keyColumn="GROUP_ID"，或改用 selectKey 显式取序列值。」

**陷阱**

- para-server / `RiskGroupMapper.xml`（insert 语句上方）— `.../mapper/RiskGroupMapper.xml:23-26`：「NEVER 在此加回 useGeneratedKeys="true"：Oracle 下 MyBatis 会走 connection.prepareStatement(sql, RETURN_GENERATED_KEYS)，ojdbc 返回的是 ROWID，无法回填 Long groupId，于是插入已提交却仍抛 MyBatisSystemException——表现为「新增风险组必然报错、数据其实已落库」（2026-09-08 生产日志实测并修复）。」
- para-server / `RiskGroupMapper.xml` + `RiskRuleMapper.xml`（两个写 mapper 的全部参数）— 两文件的 insert / update **MUST 显式写 `jdbcType=`**（字符串列 `VARCHAR`、数值列 `NUMERIC`），NEVER 依赖 MyBatis 推断。可空列（`GROUP_DESC` / `GROUP_ID` / `MANAGER_CODE` / `REMARK`）为 `null` 时 MyBatis 按 `JdbcType.OTHER`(1111) 下发，Oracle 报 `java.sql.SQLException: 无效的列类型: 1111`，包成 `MyBatisSystemException: null`（message 是 `null`）。**NEVER 用全局 `mybatis.configuration.jdbc-type-for-null` 兜底、也 NEVER 把 null 归一成空串**（前者改所有模块行为、后者改变列语义）。2026-09-23 生产日志实测：综管台风险组新增时描述留空（前端 `groupDesc: form.groupDesc.trim()||null`）⇒ **HTTP 500**，日志原文 `Could not set parameters for mapping: ParameterMapping{property='groupDesc', ..., jdbcType=null, ...}`；`PUT /page/risk/groups/{id}` 清空描述、`POST /page/risk/rules` 不带管理编码/备注/风险组三处同样 500。同期全模块核对：`para-server` 的 25 个 mapper 里**只有这两个写 mapper 没写 `jdbcType`**（`RiskControlLogMapper` 只有 SELECT、不受影响），其余（含 `OrderRefundCycleMapper`、`SingleTicketPurchaseLimitMapper`）均合规 ⇒ 本条已按既成约定收口，见 `recon-server/.../ReconPartMapper.xml:29`。
- para-server / `OrderRefundCycleServiceImpl.isDuplicateKeyViolation` — `.../service/impl/OrderRefundCycleServiceImpl.java:53-57`：「**MUST** 逐层遍历 cause，**NEVER** 直接 `catch (DuplicateKeyException)`：`MapperAspectToTrace`（`resource/micro/web/src/main/java/com/chinasofti/huateng/micro/monitor/trace/MapperAspectToTrace.java:51`）把 mapper 抛出的任何异常统一包成 `RuntimeException`，单层类型判断在 `management.tracing.enabled=true` 的模块（para-server 即是）捕不到，唯一键冲突会漏成全局异常处理器的 UUID retCode。」（与上一小节 `RiskManagementServiceImpl` 那条是两处独立的近似条款，未合并。）

关于「Druid WallFilter 拒 SQL 正文注释」「`where 1 = 1` 打头 + 全部谓词可选 `<if>`」「mapper XML 注释里连续减号」三条已知陷阱：
- para-server 注释中无相关告示，属抽取源缺失而非已确认无风险。
- key-server 注释中无相关告示，属抽取源缺失而非已确认无风险。
- blacklist-server 注释中无相关告示，属抽取源缺失而非已确认无风险。

### 六、配置（application.properties）

**契约与判据**

- key-server / `service.security.url` — `key-server/src/main/resources/application.properties:21`：「签名服务（acc-security-server）地址：默认值用集群内网 Service 名（`kubectl get svc -n itp` 实测，2026-09-11）。」
- blacklist-server / `service.*.url` — `blacklist-server/src/main/resources/application.properties:23`：「服务间调用地址：默认值一律用集群内网 Service 名（`kubectl get svc -n itp` 实测，2026-09-11），取代原先的 `172.20.211.23:30xxx` NodePort 写法。」
- para-server / `application.properties`：**全文无注释**（25 行，含 `para.ftp.*` 六个键与 `DB_*` 五个键），因此本小节没有 para-server 条目；`para.ftp.remote-dir` 的实测口径写在 `ParaFtpProperties.java:14`（见第一小节）。

**陷阱**

- key-server / `service.security.url` — `key-server/src/main/resources/application.properties:22`：「Service 端口是 8080，容器 server.port 才是 9012，NEVER 按 server.port 推断。」
- blacklist-server / `service.*.url` — `blacklist-server/src/main/resources/application.properties:24`：「NEVER 删键：键缺失时 rpc 退化成默认服务名 `*-service`、DNS 解析不到。」

### 墓碑注释清单（建议转为断言测试）

引用前 MUST 先 grep 现查行号。

| # | 文件:行号 | 想禁止的事 | 能否写成断言测试 |
|---|---|---|---|
| 1 | `para-server/src/main/java/com/chinasofti/huateng/para/service/ParaFtpScanService.java:134-135` | 在 `ParaFtpScanService` 里自己比 MD5（判据必须单点在 `ParaFileImportService`） | 部分可（可断言该类不引用 `md5Hex`，无法断言语义单点） |
| 2 | `para-server/src/main/java/com/chinasofti/huateng/para/util/ParaFileReadUtils.java:105-108` | 五份 `md5Hex` 实现结果不一致 | 可（同一字节数组喂五处实现，断言输出逐字节相等） |
| 3 | `para-server/src/main/resources/mapper/RiskGroupMapper.xml:23-26` | 在 insert 上加回 `useGeneratedKeys="true"`（无 `keyColumn`） | 部分可（可对 XML 文本断言不含该属性；真实症状需 Oracle 集成环境） |
| 4 | `para-server/src/main/java/com/chinasofti/huateng/para/service/impl/OrderRefundCycleServiceImpl.java:53-57` | 直接 `catch (DuplicateKeyException)`、不沿 cause 链判定 | 可（构造 `new RuntimeException(new DuplicateKeyException(...))`，断言 `isDuplicateKeyViolation` 为 true） |
| 5 | `para-server/src/main/java/com/chinasofti/huateng/para/service/impl/RiskManagementServiceImpl.java:133-138` | 同上（风险组/规则维护侧） | 可（同 4 的构造方式） |
| 6 | `key-server/src/main/java/com/chinasofti/huateng/key/mapper/MetroAgmKeyVersionMapper.java:17-18` | 在该 mapper 上新增返回 `KEY_VALUE` 的方法 | 部分可（可断言接口方法返回类型只含 `KeyVersionView`、无 `KEY_VALUE` 相关方法名） |
| 7 | `key-server/src/main/java/com/chinasofti/huateng/key/mapper/MetroCaKeystoreMapper.java:26` | 对应 SQL 里 select `KEY_PRIVATE` / `KEY_PUBLIC` / `KEY_PAIR` | 可（对 `MetroCaKeystoreMapper.xml` 的 `selectVersionSummary` 文本断言不含这三列） |
| 8 | `key-server/src/main/resources/mapper/MetroCaKeystoreMapper.xml:28-29` | 同 7（语句侧） | 可（同 7） |
| 9 | `key-server/src/main/java/com/chinasofti/huateng/key/mapper/MetroMemberStaticKeyMapper.java:35` | 对应 SQL 里 select `KEY_WRAP_VALUE1` 明文 | 可（对 XML `selectStatusSummary` 文本断言） |
| 10 | `key-server/src/main/resources/mapper/MetroMemberStaticKeyMapper.xml:22` | 同 9（语句侧） | 可（同 9） |
| 11 | `key-server/src/main/resources/mapper/MetroAgmKeyVersionMapper.xml:13` | 关联 `METRO_AGM_KEY_POOL` 取 `KEY_VALUE` | 可（对该 select 文本断言不含表名与列名） |
| 12 | `key-server/src/main/java/com/chinasofti/huateng/key/page/KeyVersionView.java:6-8` | 视图携带任何密钥材料明文字段 | 部分可（可反射断言字段名白名单，仍拦不住把明文塞进已有字符串字段） |
| 13 | `key-server/src/main/java/com/chinasofti/huateng/key/service/KeyVersionQueryService.java:11-12` | 该接口返回密钥材料明文 | 部分可（同 12） |
| 14 | `key-server/src/main/java/com/chinasofti/huateng/key/controller/page/KeyPageController.java:15-16` | 响应体包含密钥材料明文 | 部分可（可对响应 JSON 断言不含 `keyValue` / `keyPrivate` 等键） |
| 15 | `key-server/src/main/java/com/chinasofti/huateng/key/service/impl/KeyVersionQueryServiceImpl.java:21` | 单域查询失败导致整体接口 500 | 可（mock 三个 mapper 之一抛异常，断言返回含「查询失败」行且不抛出） |
| 16 | `key-server/src/main/java/com/chinasofti/huateng/key/service/impl/KeyVersionQueryServiceImpl.java:24` | 把 AGM 与 CA/HCE 两套状态码映射合并成一张表 | 否（属结构性约束，无运行期可观测差异，除非断言 `STATUS_DESC` 的键集合形状） |
| 17 | `key-server/src/main/resources/application.properties:22` | 按 `server.port` 推断签名服务的 Service 端口 | 否（人类判断类告示） |
| 18 | `blacklist-server/src/main/java/com/chinasofti/huateng/blacklist/service/BlacklistReleaseInspectService.java:23` | 本类删除黑名单记录 | 可（mock `BlacklistMapper`，断言删除类方法零调用；**注意 `deleteByCardIds` 已于 2026-09-18 随两阶段改造删除，现在的删除方法是 `deleteReleasedById`**） |
| 19 | `blacklist-server/src/main/java/com/chinasofti/huateng/blacklist/service/BlacklistReleaseInspectService.java:32` | 在本模块直接写 SQL 查 `GATE_TXN_PAY` / `ALIPAY_PAY_LOG` | 部分可（可断言本模块 mapper XML 不含这两个表名） |
| 20 | `blacklist-server/src/main/java/com/chinasofti/huateng/blacklist/service/BlacklistReleaseInspectService.java:49` | 把 `SETTLED` 当成「可以解除」 | 否（语义约束，落在人工流程上） |
| 21 | `blacklist-server/src/main/java/com/chinasofti/huateng/blacklist/service/BlacklistReleaseInspectService.java:155-156` | 不判 `resultCode` 就用 `hasUnsettled` | 可（mock 下游返 `resultCode != 0000` 且 `hasUnsettled=true`，断言状态为 `UNKNOWN` 而非 `UNSETTLED`） |
| 22 | `blacklist-server/src/main/java/com/chinasofti/huateng/blacklist/controller/BlacklistInternalController.java:15-16` | 在 `/internal/**` 下新增写接口而不补验签 | 部分可（可断言该 Controller 只有 GET/只读端点，验签本身需接线后才可测） |
| 23 | `blacklist-server/src/main/java/com/chinasofti/huateng/blacklist/controller/BlacklistInternalController.java:33` | 该端点删除黑名单记录 | 可（同 18，端点级 mock 验证） |
| 24 | `blacklist-server/src/main/java/com/chinasofti/huateng/blacklist/mapper/BlacklistMapper.java:66` | 盘点取数不带 limit | 可（断言 `selectForInspect` 必传 limit，且 XML 含 `fetch first ... rows only`） |
| 25 | `blacklist-server/src/main/resources/application.properties:24` | 删除 `service.*.url` 键 | 部分可（可断言 properties 含这两个键；DNS 退化行为不可单测） |

---

## 附：para / key / blacklist 注释知识迁移（2026-09-16，阶段二·完整）

阶段一（上一节）只抽了 149 行、且**源码注释当时未删**。本节是**迁移**：源码里的多行叙述型 / MUST-NEVER / 事故史 / 墓碑注释**已删除**，知识只在本节存续。

> ⚠️ **本节所有 `路径:行号` 都是删注释后的当前值，引用前 MUST 现查**（删除令行号整体上移，上一节 §墓碑注释清单里的行号**已全部失效**，NEVER 直接引用那张表的行号）。
> ⚠️ 阶段一 §六 引用 key-server `service.security.url` 注释原文写的是「Service 端口是 8080，容器 server.port 才是 9012」，那句**缺主语、已在源码里被订正过一轮**，现行表述见本节 §二·2.4，**NEVER 回退成那句**。

### 一、para-server（9107）：线路车站 / 票价 / 风控 / 限购 / 参数文件导入

#### 1.1 模块事实（迁自类注释）
- 启动类 `para-server/src/main/java/com/chinasofti/huateng/ParaServer.java` — 参数服务启动类，无 `@Scheduled`、无 `@EnableScheduling`；FTP 扫描由 web-admin Quartz 调 `POST /para/import/ftp` 触发。
- `para/controller/AppParaController.java` — **APP 侧参数查询 RPC 接口**。`fep-app-server` 收 APP 公共报文后经 `rpc` 调这里；**本 Controller 不处理公共报文字段，只收 fep-app 传入的 bizData 对象**（可 grep 原文短语「不处理 APP 公共报文字段」——该句已删）。落地接口：IF8A-09 单程票最大张数、IF8A-07 线路代码、IF8A-08 车站代码、IF8A-10 计算票价、IF8A-17 线路站点代码版本，另有三个内部用途端点（按站码查站名、按站码查站名+所属线路、批量按站码查站名，`ticket-server` 查上一笔行程补站名时用）。
- `para/service/impl/AppParaServiceImpl.java` — 线路 / 车站取**当前路网参数版本**；票价链路是「`TBL_FARE_MATRIX` 按进出站定位 `FARE_TIER` → `TBL_BASE_FARE` 按 `FARE_TIER` + **默认 `FARE_TYPE = 0`** 取 `TICKET_PRICE`」。**APP 入参没有票价类型**，`FARE_TYPE = 0` 是代码写死的默认值。
- **IF8A-17 的线路版本与车站版本是同一个数**：二者同属路网参数文件（`PARA_TYPE='0001'`），代码把当前路网版本号同时填给两个字段。**NEVER 因为接口有两个版本字段就去找两套版本来源。**
- `para/controller/BaseFarePageController.java` / `ParaPageController.java` / `RiskManagementController.java` / `OrderRefundCycleController.java` / `SingleTicketPurchaseLimitController.java` — 运营后台 `/page/**` 侧只读与参数维护。
- `para/service/RowNetworkParser.java` — **只读取与解析、不落库（无 mapper 注入）**。落库在 `RowNetworkImportService`（注入本类、方法带 `@Transactional`、按 `BATCH_SIZE=100` 批量写 6 张路网表），上游入口是 `ParaFileImportService` 的 `case "0001"`。**已删的墓碑**：源码曾写「测试阶段只读取、解析、打印，不写入数据库」，该句**是错的**（本类自身确实不落库，但它已在写库链路中间，「测试阶段」会被误读成整条链路都没落库），**NEVER 回退成那句**。

#### 1.2 `para-schema.sql` 的 23 张表与 195 条 `COMMENT ON`
`para-server/src/main/resources/sql/para-schema.sql` 里有 **195 条 `COMMENT ON`**（23 条 `COMMENT ON TABLE` + 172 条 `COMMENT ON COLUMN`），是全仓单文件最多的列注释。

> **处理方式：195 条一条没删、全部原样保留在 SQL 文件里。** 判据是**它们是 Oracle DDL 语句、不是注释**——`COMMENT ON` 由 SQLPlus 执行后写进 `USER_TAB_COMMENTS` / `USER_COL_COMMENTS`，删掉等于改 DDL 行为，会触发本次任务的「代码不变量」失败。本次删的是同一文件里的 `--` 行注释（3 条，见 §墓碑清单）。**NEVER 把 `COMMENT ON` 当注释清理。**

23 张表（按 schema 内出现顺序，路网 6 / 日历 3 / 车票 5 / 费率 5 / 运营参数 4）：

| 域 | 表 | 用途 |
|---|---|---|
| 路网 `0001` | `TBL_LINE_INFO` | 线路信息（`LINE_CODE` 线路节点编码 / `LINE_NM` 中文名 / `LINE_E_NM` 英文名） |
| 路网 | `TBL_STATION_INFO` | 车站信息（`STATION_CODE` / `OWNER_LINE_ID` 所属线路 / `OWNER_INCOME_ID` 所属收益方 / `STATION_TYPE` 车站类型 / 中英文名） |
| 路网 | `TBL_TSF_INFO` | 换乘站（`FROM_STAT_CODE` / `FROM_LINE_CODE` / `TO_LINE_CODE` / `TO_STAT_CODE` / `TSF_STATION_TYPE` 换乘类型 / `TSF_TIME` / `TSF_DISTANCE`） |
| 路网 | `TBL_ZONE_INFO` / `TBL_ZONE_DTL` | 区域与区域车站明细（`ZONE_NO` / `SEQ_NO` / `STATION_CODE`） |
| 路网 | `TBL_SECT_INFO` | 区段（`SECT_NO` / `STAT_CODE1` 开始站 / `STAT_CODE2` 结束站） |
| 日历 `0002` | `TBL_SPECIAL_DATE` | 特殊日期（`DATE_TYPE` 日期类型 / `SPECIAL_DATE`） |
| 日历 | `TBL_TIME_INTERVAL` | 时间段（`INTERVAL_NO` / `BEGIN_TIME` / `END_TIME`） |
| 日历 | `TBL_FARE_TIME` | 超时时间（`FARE_TIER` 费率等级代码 / `ALLOWED_TIME`） |
| 车票 `0003` | `TBL_CHIP_TYPE` / `TBL_TICKET_TYPE` | 芯片类型 / 车票类型 |
| 车票 | `TBL_TOTAL_SALE_PART` | 累计优惠分段（`PART_SEQ_NO` / `BEGIN_TOTAL_SALE` / `END_TOTAL_SALE` / `SALE_RATIO` 优惠比例） |
| 车票 | `TBL_ADD_PARA` | 银行卡充值（`BANK_MIN_AMT` / `BANK_MAX_AMT`） |
| 费率 `0004` | `TBL_BASE_FARE` | 基础票价（`FARE_TYPE` 费率组代码 / `FARE_TIER` 费率等级 / `TICKET_PRICE` 费率值） |
| 费率 | `TBL_FARE_MATRIX` | 费率等级矩阵（`BEGIN_STAT_CODE` / `END_STAT_CODE` → `FARE_TIER`） |
| 费率 | `TBL_TICKET_FARE` | 车票费率（`TICKET_TYPE` / `CHIP_TYPE` / `FARE_GROUP_NO`） |
| 费率 | `TBL_FARE_GROUP` | 费率组（`DATE_TYPE` + `FARE_TYPE` + `INTERVAL_NO` → `FARE_GROUP`） |
| 版本 | `TBL_PARA_VERSION` | **参数当前版本维护表**，见 §1.3 |
| 运营 | `TBL_ORDER_REFUND_CYCLE` | 订单自动退款周期（`REFUND_CYCLE` 单位**天**） |
| 运营 | `TBL_RISK_GROUP` / `TBL_RISK_RULE` / `TBL_RISK_CONTROL_LOG` | 风险组 / 风险规则 / 命中审计，见 §1.5 |
| 运营 | `TBL_SINGLE_TICKET_PURCHASE_LIMIT` | 单程票最大购买张数，**只维护 `SINGLE_TICKET` 一条全局配置** |

**195 条里唯一带枚举取值域的一条**（其余 194 条都是纯字段名中文释义，无取值域）：
- `TBL_PARA_VERSION.PARA_TYPE` — `0001` 路网、`0002` 日历、`0003` 车票、`0004` 费率。**代码里只有 `0001` 与 `0004` 走 FTP 自动扫描导入**（`ParaFtpScanService` 只认这两个 `paraType`），`0002` / `0003` 有解析器（`CalendarParser` / `TicketParser`）但不在 FTP 扫描清单里。
- 另外两处**取值域在实体注释里、schema 的 `COMMENT ON` 没写**，本次一并迁出：`TBL_RISK_RULE.RISK_LEVEL` **限定 1~5**（`para/entity/risk/RiskRule.java` 原注释「风险等级，限定为1至5」）；`TBL_STATION_INFO.OWNER_INCOME_ID` 与 `STATION_TYPE` **解析时恒写 `"0"`**（见 §1.4），因此这两列在库里没有真实取值域。

#### 1.3 参数版本与「版本号 + MD5」三分判据（本节是本次迁移的核心，源码已删）

**`TBL_PARA_VERSION` 是唯一的「当前生效版本」权威表**，下游一律 `WHERE PARA_VER_NO = (SELECT CURRENT_VER_NO FROM TBL_PARA_VERSION WHERE PARA_TYPE = ...)`。

**MUST：改 `TBL_PARA_VERSION.CURRENT_VER_NO` 做重导验证时，NEVER 用「当前版本减 1」。** 明细表只保留少数几个历史版本（2026-09-08 实测 `TBL_LINE_INFO` 只有 28 / 41 两个版本，`TBL_FARE_MATRIX` 只有 33 / 44），把 `CURRENT_VER_NO` 指到一个库里不存在的版本号 **等于把参数整体清空**——下游那句子查询命中 0 行，IF8A-07 / 08 / 10 / 17 全部返空，而**没有任何报错**。**MUST 先 `SELECT PARA_VER_NO, COUNT(*) FROM <明细表> GROUP BY PARA_VER_NO` 查出真实存在的上一个版本**再回退，并当场记下原值与还原 SQL（`172.20.211.23:300xx` 是测试环境，但这条属破坏性写库）。同一条也在 AGENTS.md §8 有一份，两处 MUST 同时改。

**入库判据（最终裁决单点在 `ParaFileImportService.importLocalFile`）**：
- 库中无该 `paraType` → 导入
- 文件版本号 **更高** → 导入
- 文件版本号 **更低** → 跳过（`skipped`，版本回退不处理）
- 版本号 **相同**：MD5 不同 → 导入；MD5 相同 → 跳过（`skippedSameContent`）；**库中 MD5 为空 → 导入（补齐 MD5）**

**为什么加 MD5**：ACC 实际出现过**同一版本号两份不同内容**的文件（`0001` 版本 41 有「4 段命名」与「5 段命名」两份，MD5 不同）。只比版本号时后到的那份**永远进不来**，两边数据无法收敛。该判据 2026-09-08 由「仅版本号」改成现形态。

**已删的墓碑（NEVER 回退）**：`ParaImportController` 的 `/para/import/ftp` 曾注释成「版本号高于库中版本时下载并解析入库」——**那是改 MD5 之前的旧口径、是错的**。按旧口径理解会得出「同版本号的新文件永远不会重导」，**与实际相反**（同版本号但 MD5 不同的文件**会**被导入，这正是引入 MD5 要解决的场景）。

**分层职责，NEVER 合并**：
- `ParaFtpScanService.listCandidates` 只按**版本号粗筛**（FTP `LIST` 拿不到 MD5，必须下载后才能算）：`>` 库中版本 → 候选；`=` 库中版本 → **也进候选**（需下载后比 MD5）；`<` → 跳过。
- **`ParaFtpScanService` NEVER 自己比 MD5** —— 那会让 `/para/import/ftp` 与 `/para/import/directory` 两条路径的判据分叉。裁决单点只能是 `ParaFileImportService`。

**参数文件名规则**：`PRM.` + 参数类型(4 位) + `.` + 节点编码(4 位) + `.` + 版本号(6 位) + `[. + 尾段]`。**2026-09-08 实测 ACC 真实文件名带第 5 段**（如 `PRM.0001.9900.000041.02000000`），因此尾段做**可选**匹配；**原先以 `(\d{6})$` 收尾时一个文件都匹配不上**（表现为扫描返 0 个候选、不报错）。

**MD5 的口径**：参数文件的 MD5 是「**除尾部 16 字节校验值外的全文**」，即 `md5Hex(bytes, 0, len - 16)`，结果就是 `TBL_PARA_VERSION.MD5_VALUE` 存的值。
**MUST 五处同步**：`ParaFileReadUtils` 有一份公开的 `md5Hex`，`RowNetworkParser` / `CalendarParser` / `TicketParser` / `RateParser` **各有一份算法完全相同的私有 `md5Hex`**（历史实现、未合并，共 5 份）。「版本号 + MD5」预筛依赖这 5 份结果**逐字节一致**——改动任意一处 MUST 同步改其余四处，否则**内容相同的文件会被误判成有变更、每轮扫描重复入库**。

**FTP 远端目录**：`para.ftp.remote-dir` 默认 `/parameter/cur/`，**2026-09-08 实测真实路径就是它、其下没有 `97000000` 子目录**（曾按有子目录写过路径，扫不到文件）。

#### 1.4 `RowNetworkParser` 的字段口径（迁自方法内隐含约定）
`para/service/RowNetworkParser.java`，`HEADER_LENGTH = 22`、`MD5_LENGTH = 16`；头部 `paraVerNo` 取 4 字节**小端**整数（`ParaFileReadUtils.fourBytesToIntLittle`）；正文按 `readLines → readStations → readTransfers → readZones → readSections` **固定顺序**读，5 段顺序即 6 张路网表的写入顺序，**NEVER 调换**。

| 目标列 | 取值口径 |
|---|---|
| `TBL_LINE_INFO.LINE_CODE` | 节点号 `nodeNo.substring(0, 2)` —— **线路码取节点号前 2 位** |
| `TBL_LINE_INFO.LINE_E_NM` / `LINE_NM` | 各 **GB2312** 定长 40 字节（`readGb2312`），**先英文名后中文名** |
| `TBL_STATION_INFO.STATION_CODE` | `nodeNo.substring(0, 4)` —— **站码取节点号前 4 位** |
| `TBL_STATION_INFO.STATION_E_NM` / `STATION_NM` | GB2312，**英文 60 字节 / 中文 40 字节**（两者长度不同，NEVER 写成同长） |
| `TBL_STATION_INFO.OWNER_LINE_ID` | `nodeNo.substring(0, 2)`，与线路码同源 |
| `TBL_STATION_INFO.OWNER_INCOME_ID` | **恒写 `"0"`**（文件里没有该字段，解析器写死） |
| `TBL_STATION_INFO.STATION_TYPE` | **恒写 `"0"`**（同上） |
| `TBL_TSF_INFO` 四个站/线码 | 每个都是独立 `readNodeNo` 后再截取：站码取前 4 位、线路码取前 2 位 |
| `TBL_TSF_INFO.TSF_STATION_TYPE` | 单字节整数，`leftPad(..., 2, '0')` → **两位字符串**（`"01"` 而非 `"1"`） |
| `TBL_TSF_INFO.TSF_TIME` | **恒写 `60`**（秒，文件里没有该字段） |
| `TBL_TSF_INFO.TSF_DISTANCE` | **恒写 `0`** |
| `TBL_ZONE_INFO.ZONE_NO` / `TBL_SECT_INFO.SECT_NO` | 4 字节小端整数 |
| `TBL_ZONE_DTL.SEQ_NO` | 循环下标 **`j + 1`，从 1 开始**（不是 0） |
| `TBL_SECT_INFO.STAT_CODE1/2` | `readNodeNo` 后取前 4 位 |
| 中英文名（zone / sect） | 均 GB2312 40 字节，**先英文后中文** |

**判据**：上表里 `OWNER_INCOME_ID` / `STATION_TYPE` / `TSF_TIME` / `TSF_DISTANCE` 四列是**解析器写死的常量、不来自 ACC 文件**。排查「这四列值不对」MUST 先看这里，**NEVER 去 ACC 文件里找对应字节**。

#### 1.5 风控与限购
- `TBL_RISK_GROUP`（风险组，`GROUP_NAME` **唯一**）/ `TBL_RISK_RULE`（阈值规则，`RULE_NO` 是**稳定业务主键、创建后不可变更**，`RISK_LEVEL` 限 1~5，可选归属风险组）/ `TBL_RISK_CONTROL_LOG`（命中审计）。
- **`TBL_RISK_CONTROL_LOG` 是审计数据：只提供分页查询，页面侧无写入、无删除**。删风险规则时**不级联删除历史命中日志**，日志行仍保留 `RULE_NO` 用于审计，因此规则名是**左关联当前规则表得到**的、**规则删除后可能为空**。
- 删风险组前 **MUST** 先 `RiskRuleMapper` 统计引用规则数（删除保护）；有引用即拒绝。
- 唯一名冲突（风险组 / 退款周期票卡类型）**MUST 沿 `getCause()` 链判定 `DuplicateKeyException`**，见 §1.6。
- `TBL_SINGLE_TICKET_PURCHASE_LIMIT` **只有 `SINGLE_TICKET` 一行全局配置**；更新走**版本号乐观锁**（`SingleTicketPurchaseLimitMapper` 原注释：「使用版本号更新，防止两个运营人员的修改相互覆盖」）。**NEVER 改成无条件 UPDATE。**
- `TBL_ORDER_REFUND_CYCLE`：`REFUND_CYCLE` 单位**天**；更新时**路径里的票卡类型不可修改**，只能改周期与备注。

#### 1.6 tracing 归因标注（两处，**结论存疑、MUST 重查**）

两处 `isDuplicateKeyViolation(Throwable)` 私有方法：
- `para-server/src/main/java/com/chinasofti/huateng/para/service/impl/OrderRefundCycleServiceImpl.java`（退款周期唯一键冲突）
- `para-server/src/main/java/com/chinasofti/huateng/para/service/impl/RiskManagementServiceImpl.java`（风险组名重复）

**保留的实现约定**：**MUST 逐层遍历 `getCause()`，NEVER 直接 `catch (DuplicateKeyException)`** ——`resource/micro/web/src/main/java/com/chinasofti/huateng/micro/monitor/trace/MapperAspectToTrace.java` 会把 mapper 抛出的异常统一包一层，单层类型判断捕不到。这段 cause 链兜底是**为将来按「三行成组」开启 tracing 后准备的唯一防线**，**NEVER 简化回裸 catch 具体异常类型**。

**归因存疑本次已部分闭合（2026-09-23 实测，据此重写本小节，旧表述 NEVER 回退）**：
- **旧前提之一已被实测推翻**：页面曾写「`para-server` 当前未开 tracing、`application.properties` 全文无 `management.tracing` 键 ⇒ 那两个观测切面在本模块从未生效」——**后半句推论是错的**。`application.properties` 确实没有该键，但 **`deploy/k8s/10-deployments/para-server.yaml:35` 由 Deployment env 注入了 `management.tracing.enabled=true`**（`:81` 为 `sampling.probability=0`），因此 `shouldSkipAopTraceLogic()` 返回 `false`、**切面在 para-server 里实际在跑**（日志实证：`MapperAspectToTrace.around` 打出 `Mapper method execute error: ...RiskGroupMapper.insert`；且 para-server 日志行的 traceId 列有值）。**判据随之修正：判断某模块是否开了 tracing，MUST 同时查 Deployment env，NEVER 只 grep 模块的配置文件。**
- **旧结论「切面把异常换类型导致 UUID retCode」仍然不成立，但理由换了**：自 ADR-D53 起两个切面已改成 `observation.start()` + `openScope()` + **`throw e` 原样抛出**（`MapperAspectToTrace.java:57` 带护栏注释），现在即便切面在跑也不会改变异常类型。2026-09-23 实测重复风险组名：返 `{"code":"500","msg":"风险组名称已存在"}`，**cause 链兜底正常生效、UUID retCode 未复现**。
- **剩余候选（仍未定位，NEVER 当作已确认）**：AGENTS.md §5.1 那两条 Druid WallFilter 规则（SQL 正文写了注释 / `where 1 = 1` 打头 + 全部谓词可选 `<if>`），或 cause 链判定是在 2026-09-08 之后才补上的（老镜像行为）。**NEVER 把「切面换异常类型」当成已确认结论写进任何地方。**
- 源码里**各留了一行式现场标记**（`路径:行号` MUST 现查，grep 短语「归因存疑」），作用是「别再照这个结论排查」。

#### 1.7 构建与部署（para-server 专有坑）
- **`para-server` 的 jkube `build-image-remote` 绑在 `<phase>package</phase>`**（`para-server/pom.xml:121`）：**`mvn clean package` 就会 build + push 到 Harbor**，2026-09-08 实测直接推出 `itp/para-server:2.0.19`。这条实测**推翻了「`build-image-remote` 等于绑 `install`」的旧记载**，**NEVER 回退**；**execution id 不能用来推断阶段**。
- 因此只想拿 jar **MUST 加 `-Djkube.skip=true`**。
- `/para-server/` 是 `fep-app-vr` 那 8 条入向前缀之一（见 `docs/ops/流量切换.md`）。

---

### 二、key-server（9103）：密钥同步 SM2 / DPK / AGM 密钥池

#### 2.1 IF8A-02 请求同步密钥
- 入口 `key-server/src/main/java/com/chinasofti/huateng/key/controller/KeyController.java`，供 `fep-app-server` 调；**APP 公共报文解析仍在 fep-app-server，key-server 只处理 bizData 业务参数**。
- **按卡类型分流**：`cardType` **`03` / `04` → 返回 HCE 消费 DPK**；**其余卡一律按二维码卡生成用户 SM2 密钥**。密钥材料均由 `acc-security-server` 导出后**在 key-server 内存里转加密**。
- SM2 链路（`KeySyncServiceImpl`，与旧系统步骤一致）：查可用 CA 密钥 → **随机选一条启用状态的 CA** → 调 acc-security 生成用户 SM2 密钥对 → 用 CA 签名用户公钥 → 导出 KEK 加密的用户私钥 → **3DES 转加密**（ACC 侧 `acc.3des.key` 解密 → APP 侧 `appserver.3des.key` 加密）→ 组装 `keyList` 返 APP。
- HCE DPK 链路：`acc-security-server` 返回的是 **ACC KEK 加密的 DPK**；key-server **在内存中**用 ACC 3DES 密钥解密、**立即**按 APP_SERVER 3DES 密钥转加密，**只向 APP 返回转加密结果**。返回形态固定 `keyId=00` / `keyType=0`。
- `METRO_MEMBER_STATIC_KEY` **只保存 ACC KEK 加密的 DPK，NEVER 在库里保存解密后的 DPK 明文**；写入时若同一卡号已被并发请求写入则**保持原有记录**（`insertIfAbsent` 语义）。`KEY_STATUS`：**`1` 启用、`2` 停用**。
- 公钥有效期口径：按**当前时间 + `key.sync.days`（默认 7 天）**，转成 **2000-01-01 以来秒数的四字节 HEX**（沿用旧系统）。`thirdUserId` 转四字节 HEX 作为 `userId`。用户公钥标准化为 **X+Y 共 128 位 HEX**。
- `key/util/TripleDesEcbUtils.java`：3DES **ECB**。**旧系统配置可能给 16 字节双长度密钥或 24 字节三长度密钥**，本工具遇 16 字节时按 **K1 + K2 + K1 扩展成 24 字节**。另有一个「按旧系统 `ThreeDES.encryptThreeDESECB` 的方式」加密的方法：`DESede/ECB/PKCS5Padding`、**明文与密钥都按 UTF-8 字符串取字节、结果 Base64 输出** —— IF8A-02 返给 APP 的 `keyPrivate` **MUST 保持这种格式**。**属安全红线，改动 MUST 人工复核。**
- `METRO_CA_KEYSTORE.KEY_STATUS`：**`0` 初始化、`1` 使用、`2` 暂停**（schema `COMMENT ON` 原文，19 条 `COMMENT ON` 已原样保留）。

#### 2.2 综管台密钥版本查看（只读）与安全红线
`/page/**` 只读页，三个域各自独立查询：**AGM**（`METRO_AGM_KEY_VERSION`，按接入方 `PROVIDER_ID` 取最新一条、按批次号降序取首行）、**CA**（`METRO_CA_KEYSTORE` 元信息）、**HCE**（`METRO_MEMBER_STATIC_KEY` 按状态聚合卡数，**每卡一条、无统一版本号**，版本列展示「状态对应的卡数」）。

- **安全红线（源码里保留一行式）**：`KeyVersionView` / `KeyVersionQueryService` / `KeyPageController` **NEVER 携带或返回任何密钥材料明文**（`KEY_VALUE` / `KEY_PRIVATE` / `KEY_PUBLIC` / `KEY_PAIR` / `KEY_WRAP_VALUE*`），只放版本号、状态、时间等元信息；填充方 **MUST 逐字段核对来源 SQL 的 select 列表**。
- **mapper 侧同一条红线**：`MetroCaKeystoreMapper.xml` 的版本汇总语句 **NEVER select `KEY_PRIVATE` / `KEY_PUBLIC` / `KEY_PAIR`**；`MetroMemberStaticKeyMapper.xml` **NEVER select `KEY_WRAP_VALUE1`**；`MetroAgmKeyVersionMapper.xml` **NEVER 关联 `METRO_AGM_KEY_POOL` 取 `KEY_VALUE`**（密钥材料在那张表，出库即越红线）。`MetroAgmKeyVersionMapper` 接口上 **NEVER 新增返回 `KEY_VALUE` 的方法**。
- **单域失败只降级成一条「查询失败」行，NEVER 让整体接口 500** —— 监控页的意义就是出问题时仍看得到其它域。
- **状态码→中文的两套口径共存、NEVER 合并成一张表**：AGM 域用 `20010` / `20020` / `20030`，CA 与 HCE 域用 `0` / `1` / `2`。两个域的 `1` 含义不同，合并即错。实现是 `KeyVersionQueryServiceImpl.STATUS_DESC`「域 → (状态码 → 中文)」，**键冲突属预期**，故按域分表。

#### 2.3 端口冲突（key-server 自身）
**key-server 的 `server.port=9103` 与 `ticket-server` 撞号**（AGENTS.md §2.2.1 端口冲突清单里的那一组）。同机部署需确认；K8s 下各自独立 Pod 不冲突，但**排查「打 9103 打到了谁」MUST 先确认目标 Pod**。

#### 2.4 `service.security.url`：被调方 Service 端口 ≠ 对方 `server.port`
`key-server/src/main/resources/application.properties` 的 `service.security.url` 默认值用集群内网 Service 名（`kubectl get svc -n itp` 实测，2026-09-11）。

**MUST 用集群实测的 Service 端口填地址，NEVER 按对方 `server.port` 推断。** 被调方 `acc-security-server` 的 **Service 端口是 8080、而它容器内 `server.port` 是 9012**，两者不一致。
**已删的墓碑**：此前注释写「Service 端口是 8080，容器 `server.port` 才是 9012，NEVER 按 `server.port` 推断」——**那句缺主语**，读者会误当成 key-server 自己（**本文件第 1 行是 `server.port=9103`，9012 属 acc-security-server**）。**NEVER 回退成那句缺主语的写法。** 源码 properties 里**保留一行式护栏**。

---

### 三、blacklist-server（9102）：黑名单增删查与渠道通知

#### 3.1 渠道通知：**现在只剩支付宝外部一路**（本次迁移最要紧的一条）
`blacklist-server/src/main/java/com/chinasofti/huateng/blacklist/service/impl/BlacklistServiceImpl.java` 里，`addBlackList` 与 `deleteBlackList` 的通知调用**被注释掉了两路 / 一路**，只留 `notifyAlipayExternalBlacklistAsync`（支付宝**外部**直连）。**2026-09-16 核实的现状**：

| 入口 | 仍在发的 | 被注释停用的 | 后果 |
|---|---|---|---|
| `addBlackList`（加黑） | 支付宝外部 | **地铁 APP** + **内部支付宝（fep-alipay-server）** | 加黑名单时**地铁 APP 与内部支付宝收不到通知** |
| `deleteBlackList`（解黑） | 支付宝外部 | **内部支付宝** | 解黑名单时**内部支付宝收不到通知** |

连带的**死配置**（键还在、字段还注入、但已无生效读取方）：
- `app.notify.blacklist-url`（`blacklist-server/src/main/resources/application.properties`，约 `:21`）仍被注入到 `appNotifyBlacklistUrl` 字段，但 `notifyAppBlacklistAsync` **在 `src/main` 内零调用方**。
- `alipay.notify.blacklist-url` 对应字段**属声明即死**（`notifyAlipayBlacklistAsync` 零调用方）。
- `alipay.external.blacklist-url` 是**唯一还活着**的通知地址。

**两条 NEVER（源码里各保留一行式护栏，共 2 处）**：
- **NEVER 取消注释恢复这些调用** —— 那属**行为变更，需另行评审**（会让 APP 与内部支付宝重新开始收到黑名单变更通知）。
- 排查「加/解黑名单后 APP 没收到通知」**MUST 先看这两处是不是还注释着**，**NEVER 先去查 `app.notify.blacklist-url` 配得对不对**——那个键现在**根本没有读取方，配对了也没用**；而且**停用是静默的：不报错、不留痕、日志里一行都没有**。
- 两处 `TODO` 原文短语可 grep：「先注释掉地铁APP和内部支付宝通知」「先注释掉内部支付宝通知」。

其余通知实现细节（保留在源码 Javadoc）：三个 `notify*Async` 方法参数一致，`blackListType` **`1` 加入黑名单 / `2` 移除黑名单**。

#### 3.2 `BlacklistServiceImpl` 的两处现状告警（已迁出，源码只留一行式）
1. **`addBlackList` 处**（上表第一行）：两行调用被注释 ⇒ 两个方法 + 一个配置键连带失效。
2. **`deleteBlackList` 处**（上表第二行）：一行调用被注释 ⇒ 与 `addBlackList` **同型**（那里还多丢一路地铁 APP 通知）。

另记两条实现事实：
- `addBlackList` **按 `cardId` 去重**：同一张卡在 `BLACKLIST` 里**只有一行**，第二次 insert 会被跳过 —— 因此**行上留不下第二个拉黑来源的痕迹**（见 §3.3 为什么盘点必须查两个源）。
- `deleteBlackList` 是**物理删除**，删前先 `selectByCardIds` 取出记录用于写 `BLACKLIST_OPERATE_LOG`（`OPERATE_TYPE`：**`ADD` 新增 / `DELETE` 删除**）。`cardId` 支持**逗号分隔多卡**（`parseCardIds`）。

#### 3.3 「可解除性」只读盘点（`BlacklistReleaseInspectService`）
- **本类 NEVER 删除任何黑名单记录**，只把每条记录的欠费事实查清输出、供人工核对。入口 `POST /internal/blacklist/inspectReleasable`，**无入参**（批量范围由服务端 `blacklist.inspect.batch-size`，默认 200，控制；避免调用方通过参数放大单次开销），供 web-admin Quartz 调。
- **为什么不自动解除**：`BLACKLIST` **只有 5 列、没有拉黑类型字段**，`REASON` 是四个来源混写的自由文本（支付中心应答原文 / 代码拼接模板 / 外部接口传入 / 运营手工输入）。**生产实测 35 条 `ADD` 里 22 条是「用户挂失补卡」**——与欠费无关，按「欠费结清」删掉**等于让挂失旧卡恢复过闸**。**「钱结清了」与「可以解除」不是一回事**，后者 MUST 由人看 `REASON` 判断。**NEVER 把 `SETTLED` 当成「可以解除」。**
- **欠费事实要问两个模块，缺一个就漏判**：闸机出站扣费在 `gate-txn-pay-server` 的 `GATE_TXN_PAY`，支付宝出行在 `alipay-pay-sign-server` 的 `ALIPAY_PAY_LOG`。两张表**都不归 blacklist-server 管**，**MUST 走 `rpc` 只读接口，NEVER 在本模块直接写 SQL 查它们**。
- **NEVER 按拉黑来源分流只查一个源**：`addBlackList` 按 `cardId` 去重（§3.2），先被闸机链路拉黑、后被支付宝链路重复拉黑时第二次 insert 被跳过，**行上没有第二个来源的痕迹**，只查一个源必然漏判。
- **MUST 先判下游 `resultCode` 再用 `hasUnsettled`**：下游查询**未执行**时会把 `hasUnsettled` 置 `true`，不判 `resultCode` 会把「事实不明」误报成「有欠费」。两个查询方法各自 `return null` 表示「查询未成功执行（事实不明）」，单条盘点的任何异常都收敛为 `UNKNOWN`、**不让一条坏数据中断整批**。
- **本类无 `@Transactional`，且是刻意的**：方法内有 RPC，事务包住网络调用会让行锁持有时长等于对端响应时长（AGENTS.md §5.2 硬约束，已有生产事故）。而且本类只读、本来不需要事务。**NEVER 加 `@Transactional`。**
- 取数 SQL（`BlacklistMapper.selectForInspect` / `BlacklistMapper.xml`）：**按 `CREATE_TIME` 升序取最早的 `limit` 条**（升序而非倒序：**先拉黑的先盘点**，避免积压最久的记录被新记录一直挤到后面）；**MUST 带 `limit`** —— 每条要跨模块查两次欠费，条数不受控会把单次调度拖成长事务级别的耗时；**`REASON` 必须 select 出来**，它是人工判断「这条该不该解除」的唯一依据。

#### 3.4 `/internal/**` 前缀的鉴权现状
`BlacklistInternalController` **当前只有一个只读盘点接口、不改任何数据，因此无鉴权与归属校验**。**后续若在本前缀下新增写接口（例如真正执行自动解除），MUST 先补验签**（对齐 `AccountRequestVerifier` / `ItpRequestSignVerifier`，**NEVER 自造签名逻辑**）—— 本项目多数业务模块没有 spring-security、没有全局拦截器兜底，裸暴露的写接口等于允许任何网络可达方按卡号操作。

#### 3.5 `service.*.url`
`blacklist-server/src/main/resources/application.properties` 的 `service.alipay-pay-sign.url` / `service.gateTxnPay.url` 默认值一律用集群内网 Service 名（`kubectl get svc -n itp` 实测，2026-09-11），**取代原先的 `172.20.211.23:30xxx` NodePort 写法**。**NEVER 删键**：键缺失时 `rpc` 退化成默认服务名 `*-service`、DNS 解析不到（源码 properties 保留一行式护栏）。

---

### 矛盾与待裁决

| # | 矛盾点 | 两侧说法 | 现状裁决 |
|---|---|---|---|
| 1 | **para 那两处「唯一键冲突返 UUID retCode」的成因** | 源码注释归因于观测切面换异常类型 / 实测 contra：**para-server 的 tracing 由 Deployment env 打开（切面在跑，推翻「从未生效」的旧前提），但切面自 ADR-D53 起原样 `throw e`、不换类型**；2026-09-23 重复组名实测返 `code=500 + 风险组名称已存在`，UUID retCode 未复现 | **归因作废、成因未知，MUST 重查**。候选仍是 Druid WallFilter 的「SQL 正文注释」或「`where 1 = 1` + 全可选 `<if>`」两条（另有可能是 cause 链判定后补）。**NEVER 引用旧归因**，也 **NEVER 再用「para-server 没开 tracing」当论据**（详见 §1.6） |
| 2 | **cause 链兜底是否还有必要** | 本模块未开 tracing ⇒ 当前无切面包装 / 将来开 tracing 后它是唯一防线 | **保留**。`NEVER` 简化回裸 `catch (DuplicateKeyException)` |
| 3 | **para-server 该不该开 tracing** | AGENTS.md §2.2.1 名单里没有它 ⇒ 按 traceId 检索它的日志恒 0 条 / 开之前 MUST「三行成组」 | **未裁决**。要开 MUST 三行成组 + 重建镜像（`-Djkube.skip=true` 不能带） |
| 4 | **阶段一文档的行号与措辞** | 阶段一 §墓碑注释清单给了 25 条精确行号 / 本次删注释后**行号全部失效** | 阶段一那张表**只保留「想禁止的事」与「能否写成断言测试」两列可用**，**行号 NEVER 引用** |
| 5 | **key-server `service.security.url` 的旧措辞** | 阶段一 §六 引的是缺主语版本 / 源码已订正为带主语版本 | 以 §二·2.4 为准，**NEVER 回退** |
| 6 | **`app.notify.blacklist-url` 到底算不算残留** | 键在、字段在、被注入 / **零生效读取方** | **算死配置**。清理需与「恢复通知」一并评审，**当前 NEVER 单独删键或单独恢复调用** |
| 7 | **`BLACKLIST` 无拉黑类型字段** | 盘点想自动解除 / `REASON` 自由文本、22/35 是挂失补卡 | **维持只读盘点**。真要自动化 MUST 先加拉黑类型列（属 DDL 变更，MUST 出 `*-migration.sql`） |
| 8 | **`RiskGroupMapper.insert` 的主键回填** | 想要 `useGeneratedKeys` 拿 `GROUP_ID` / Oracle + ojdbc 返回 ROWID、回填不进 `Long` | **不回填**。确有需求 MUST 补 `keyColumn="GROUP_ID"` 或改 `selectKey` 取序列，**NEVER 裸加 `useGeneratedKeys="true"`**（详见墓碑 #4） |
| 9 | **`key-server` 与 `ticket-server` 抢 9103** | 两模块 `server.port` 都是 9103 | **未裁决**（AGENTS.md §2.2.1 已登记）。K8s 下各自 Pod 不冲突 |

### 墓碑清单

**本次从源码删除、只在本节存续的「想禁止的事」。引用位置前 MUST 现查行号。**

| # | 原宿主文件 | 想禁止的事 | 源码是否留一行式护栏 |
|---|---|---|---|
| 1 | `para/controller/ParaImportController.java` | 把导入判据理解成「只比版本号」⇒ 得出「同版本号新文件永不重导」的反向结论 | 否（迁入 §1.3） |
| 2 | `para/service/ParaFtpScanService.java` | 在扫描服务里自己比 MD5（判据 MUST 单点在 `ParaFileImportService`） | 否（迁入 §1.3） |
| 3 | `para/util/ParaFileReadUtils.java` | 五份 `md5Hex` 实现结果不一致 ⇒ 内容相同的文件每轮重复入库 | 否（迁入 §1.3，**可写断言测试**：同一字节数组喂五处实现、断言逐字节相等） |
| 4 | `para/../mapper/RiskGroupMapper.xml` | 在 insert 上加回 `useGeneratedKeys="true"`（无 `keyColumn`）—— 2026-09-08 生产日志实测：**插入已提交却仍抛 `MyBatisSystemException`**，表现为「新增风险组必然报错、数据其实已落库」 | 否（迁入 §矛盾 #8。**可对 XML 文本断言不含该属性**） |
| 5 | `para/service/RowNetworkParser.java` | 把「本类不落库」误读成「整条链路还没落库」（旧注释的「测试阶段」措辞） | 否（迁入 §1.1） |
| 6 | `para/service/impl/OrderRefundCycleServiceImpl.java` | 直接 `catch (DuplicateKeyException)`、不沿 cause 链判定 | **是**（含「归因存疑」标记） |
| 7 | `para/service/impl/RiskManagementServiceImpl.java` | 同 #6（风险组 / 规则维护侧） | **是**（含「归因存疑」标记） |
| 8 | `key/page/KeyVersionView.java`、`key/service/KeyVersionQueryService.java`、`key/controller/page/KeyPageController.java` | 视图 / 接口 / 响应体携带密钥材料明文 | **是**（安全红线，压缩为一行） |
| 9 | `key/mapper/MetroCaKeystoreMapper.java` + `.xml`、`MetroMemberStaticKeyMapper.java` + `.xml`、`MetroAgmKeyVersionMapper.java` + `.xml` | 对应 SQL select `KEY_PRIVATE` / `KEY_PUBLIC` / `KEY_PAIR` / `KEY_WRAP_VALUE1`，或关联 `METRO_AGM_KEY_POOL` 取 `KEY_VALUE` | **是**（6 处各一行，安全红线） |
| 10 | `key/service/impl/KeyVersionQueryServiceImpl.java` | 单域查询失败导致整体接口 500；把 AGM 与 CA/HCE 两套状态码映射合并成一张表 | **是**（压缩为一行） |
| 11 | `key-server/src/main/resources/application.properties` | 按对方 `server.port` 推断 Service 端口（以及回退成缺主语的旧措辞） | **是**（用户点名保留） |
| 12 | `blacklist/service/BlacklistReleaseInspectService.java` | 本类删除黑名单记录；在本模块直接写 SQL 查 `GATE_TXN_PAY` / `ALIPAY_PAY_LOG`；把 `SETTLED` 当「可解除」；不判 `resultCode` 就用 `hasUnsettled`；给本类加 `@Transactional` | 否（整段迁入 §3.3，**#4 与 #5 可写断言测试**） |
| 13 | `blacklist/controller/BlacklistInternalController.java` | 在 `/internal/**` 下新增写接口而不补验签；该端点删除黑名单记录 | 否（迁入 §3.4） |
| 14 | `blacklist/mapper/BlacklistMapper.java` + `.xml` | 盘点取数不带 `limit`、改成倒序、不 select `REASON` | 否（迁入 §3.3，**可断言 XML 含 `fetch first ... rows only` 与 `REASON`**） |
| 15 | `blacklist/service/impl/BlacklistServiceImpl.java`（两处） | **取消注释恢复已停用的 APP / 内部支付宝通知调用** | **是**（用户点名保留，2 处各一行） |
| 16 | `blacklist-server/src/main/resources/application.properties` | 删除 `service.*.url` 键（键缺失 ⇒ `rpc` 退化成 `*-service`、DNS 解析不到） | **是** |
| 17 | `para/config/ParaFtpProperties.java` | 把远端目录写成带 `97000000` 子目录 | 否（迁入 §1.3 末条） |
| 18 | `para/../mapper/RiskGroupMapper.xml` + `RiskRuleMapper.xml` | 删掉写语句参数上的 `jdbcType=`（含看似无害的「顺手简化」）—— 2026-09-23 生产日志实测：可空列为 `null` 时报 `无效的列类型: 1111`，表现为「描述留空时新增/修改必现 HTTP 500」；已于 para-server `2.0.26` 修复并生产复测通过（描述 `null` → `200`） | 源码 XML 注释留了一行式护栏；**可写断言测试**（对两个 XML 的 insert / update 文本断言每个 `#{...}` 都含 `jdbcType=`） |

### 覆盖率自评

| 维度 | 结果（本次实测，非估算） |
|---|---|
| 源码注释总量（三模块 `src/main`，含 Javadoc / 行注释 / XML 注释 / SQL `--`） | **删除前 401 块 / 1208 行**（para 243/574、key 76/340、blacklist 82/294）→ **删除后 381 块 / 1058 行**（para 239/499、key 72/302、blacklist 70/257） |
| 本次实际删掉的注释行 | **150 行**（block 数净减 20 —— 多数是「长块压成短块」而非整块删掉） |
| 改动的编辑操作 / 文件 | **39 个锚定编辑操作**、**27 个源码文件**（另 1 个 docs 文件） |
| 本节抽取规模 | **264 行**、**56 条 bullet**、**76 行表格**、**NEVER 50 次 / MUST 36 次** |
| 195 条 `COMMENT ON` | **一条未删**（是 DDL 语句、不是注释），取值域已镜像进 §1.2；其中**只有 1 条自带枚举**（`TBL_PARA_VERSION.PARA_TYPE`），另补 2 条来自实体注释 / 解析器常量的取值域 |
| 矛盾条数 | **9 条**（§矛盾与待裁决） |
| 墓碑条数 | **17 条**（§墓碑清单），其中 **7 条在源码保留了一行式护栏**、10 条只在本节 |
| 源码剩余的 MUST/NEVER 护栏 | **21 处一行式**（可 `grep -rn "NEVER\|MUST" para-server/src key-server/src blacklist-server/src` 复核；其中 1 处是 `log.error` 的日志文案、不是注释） |
| 未覆盖 / 有意不抽 | ①`KeySyncServiceImpl` 那 16 块方法级 Javadoc 的**参数说明**（属标准 Javadoc、保留在源码，本节只抽链路与安全约束）；②`AppParaQueryMapper.xml` 7 段 SQL 说明性 XML 注释（**保留未删**，它们解释的是 `PARA_TYPE='0001'/'0004'` 与 `FARE_TYPE=0` 的口径，同一事实已在 §1.1 / §1.2 有一份）；③`para-schema.sql` 的 3 条 `--` 行注释（风险三表用途说明）**已删**、内容在 §1.5 |
| 已知缺口 | **三模块里 NEVER 出现过「SQL 正文禁写注释」那条 mapper XML 护栏**（全仓 grep 只在 para 那两处 tracing 注释里以「Druid WallFilter 规则（SQL 正文注释 …）」作为**候选成因**被提及，而那两处正文已删、只留一行式）。因此该护栏**本次无从保留**；要补 MUST 新增，且注释里 NEVER 出现两个连续减号 |
| 不变量自证 | `svn cat -r BASE` 取 **157 个文件**基线，python 逐文件剥注释 + 空行归一后比对：**删除前 157/157 一致、删除后 157/157 一致**（`DIFFERENT=0`），即**本次一行代码都没动** |
| 构建 / 测试 | `mise exec -- mvn -o clean package -pl para-server,key-server,blacklist-server -am -DskipTests -Djkube.skip=true`；三模块**都没有测试源码**（`key-server` / `blacklist-server` 无 `src/test` 目录，`para-server/src/test` 存在但 **0 个文件**），编译通过即为验证上限 |







