# 日终对账（recon-server + 三个源服务）

> 本文按代码实际落地情况编写，不是需求文档。改动本域代码前先读本文，再读点名的核心类源码。
> 四类文件的行格式与字段顺序**来自甲方规格原文**
> `docs/接口规范文档/ACC与ITP之间的文件.docx` §一「对账文件」（规格原文一直在仓库内，
> 本文此前记为「甲方规格缺失、行格式是本项目自定义 v1」，**该结论是错的，已于 2026-09-11 全部返工**）。
> 行格式是**跨四个模块的契约**，改一处必须同时改 recon-server 与三个源服务。

## 涉及模块与端口

| 模块 | 端口 | 版本 / 镜像 | 在本域中的角色 |
|---|---|---|---|
| recon-server | 9112（`SERVER_PORT`） | `1.0.10` / `itp/recon-server` | 批次编排、分片接收、流式聚合、FTP 投递；**自身无调度**，由 web-admin `sys_job` 109 每日触发 |
| gate-txn-pay-server | 9106 | `2.0.50` | 源 `gate-txn-pay`，产出 **EXP、PAY、BUS、DETAIL** 四类 |
| collect-pay-server | 58101 | `1.1.69` | 源 `collect-pay`，产出 **PAY、BUS**（原有的 DETAIL 导出已删除） |
| daily-ticket-server | 9108 | `1.0.17` | 源 `daily-ticket`，产出 **PAY、DETAIL** |
| ticket-server | 9103 | `2.1.60`（本轮未再动） | **不在期望清单内**。`ReconExportController` / `ReconExportService` / `ReconExportMapper` + XML 与 `recon.*` 配置**全部保留未删**，只是 `recon.orchestration.sources` 里没有它，因此永远收不到抽取指令 |

`spring.application.name=recon-server`，已在根 `pom.xml` 聚合列表（`pom.xml:60`）。
`recon-server/pom.xml:104` 的 `kubernetes-maven-plugin` 1.19.0 execution id `build-image-after-package`、
`<phase>package</phase>`、goals 是未注释的 `build` + `push`（`deploy` 在 XML 注释里），
因此 **`mise exec -- mvn clean package` 就会 build + push 到 Harbor**；只想拿 jar 必须加 `-Djkube.skip=true`。
镜像名取自 `<image.prefix>recon-server</image.prefix>`（`recon-server/pom.xml:31`），tag 等于 pom `<version>`。

启动类 `com.chinasofti.huateng.ReconServer`：
`@SpringBootApplication` + `@EnableDefaultMybatisAutoConfig` + `@EnableRpcRecon`
+ `@EnableConfigurationProperties({ReconStorageProperties, ReconFtpProperties, ReconOrchestrationProperties})`。
**NEVER 加 `@EnableScheduling`**（2026-09-11 用户明确要求移除，调度归 web-admin）。

**为什么 `ticket` 不再是源**：甲方四类文件里没有 `QRCODE_TXN_DETAIL` 的位置——
「过闸笔数 / 过闸金额」由 `gate-txn-pay` 的 `GATE_TXN_PAY` 出，EXP 是单边账明细而非全量过闸明细。
如后续甲方要求补全量过闸明细，把 `ticket` 加回 `recon.orchestration.sources` 即可，
**NEVER 删 ticket-server 侧的对账代码**。

## 链路总览

```
                       web-admin sys_job 109「日终对账」reconQuartzTask.runDailyBatch()
                       cron 0 30 2 * * ?（频率只在这里改）
                                    |
                       POST <service.recon.url>/internal/recon/daily/run（同步，等批次收口）
                                    |
                       recon-server ReconOrchestrationService.runDailyBatch()
                                    |
        建 RECON_BATCH(batchId=RECON<businessDate>) + 按期望清单登记 RECON_BATCH_SOURCE
                                    |
              ReconExportClient 逐源 POST <源地址>/internal/recon/export（绝对 URL）
                                    |
   +------------------------+----------------+------------------+
   |      gate-txn-pay      |   collect-pay  |   daily-ticket   |  ← 受理即返回 accepted
   | EXP  PAY  BUS  DETAIL  |    PAY  BUS    |   PAY  DETAIL    |    抽取跑在平台线程池 recon-export
   +------------------------+----------------+------------------+
        Keyset 分页读库 / 库内 GROUP BY → ReconRecord.line(...) 拼管道分隔文本
        → ReconPartUploader/ReconPartSink 按 20 万行或 64MB 滚片、边写边算 SHA-256
                                    |
        POST /internal/recon/batches/{batchId}/sources/{source}/files/{fileType}/parts/{partNo}
        Content-Type: application/octet-stream（流式，单片即时上送）
                                    |
        POST .../files/{fileType}/complete  声明 totalParts/totalRecords/totalAmount
                                    |
   recon-server 三项总账比对 → RECON_BATCH_SOURCE.STATUS=COMPLETED / MISMATCH
                                    |
        全部 COMPLETED → ALL_SOURCE_COMPLETED → GENERATING
        明细 EXP/DETAIL：流式字节拼接；汇总 PAY/BUS：逐行读 + TreeMap 按 keyFieldCount 段键二次聚合
        写 <recon.storage.root>/<batchId>/final/ITP.<TYPE>.<yyyyMMdd>.generating → 原子 rename
                                    |
        UPLOADING → ReconFtpService：先传 <remote>/ITP.<TYPE>.<yyyyMMdd>.uploading
                    → 远端 rename 成正式名 → RECON_BATCH_FILE.STATUS=UPLOADED
                                    |
                              批次 STATUS=SUCCESS
```

## 真实接口清单

本域接口**没有甲方 IF 编号**（甲方规格只定义文件格式与投递路径，不定义 ITP 内部接口），
下表编号仅为本文内引用用。全部接口以 Controller 的 `@PostMapping` / `@GetMapping` / `@PutMapping` 为准。

| 编号 | 方法 | URL | 所属模块 | 说明 |
|---|---|---|---|---|
| R-00 | POST | `/internal/recon/daily/run` | recon-server | **web-admin `sys_job` 109 的唯一入口**，无参；同步跑完整批后返回 `CommonResult`（`0000` 成功 / `9999` 失败），耗时可达数分钟 |
| R-01 | POST | `/internal/recon/batches` | recon-server | 建批次，按 `batchId` 幂等（`ReconBatchMapper.insertIfAbsent`） |
| R-02 | GET | `/internal/recon/batches/{batchId}` | recon-server | 查批次，不存在返回 404 |
| R-03 | GET | `/internal/recon/batches/{batchId}/sources` | recon-server | 查该批次各 `(来源, 文件类型)` 的收齐进度 |
| R-04 | PUT | `/internal/recon/batches/{batchId}/status?status=` | recon-server | **仅供人工干预**，绕过收齐校验直接改状态 |
| R-05 | POST | `/internal/recon/batches/{batchId}/sources/{source}/files/{fileType}/parts/{partNo}?recordCount=&amountTotal=&sha256=` | recon-server | 接收分片，`Content-Type: application/octet-stream`，**流式收、边收边算 SHA-256** |
| R-06 | POST | `/internal/recon/batches/{batchId}/sources/{source}/files/{fileType}/complete` | recon-server | 源声明收齐或失败，body 是 `ReconSourceCompleteReqDTO` |
| R-07 | POST | `/internal/recon/batches/{batchId}/files/{fileType}/generate` | recon-server | 生成最终文件 |
| R-08 | POST | `/internal/recon/batches/{batchId}/files/{fileType}/upload` | recon-server | FTP 投递最终文件 |
| R-09 | POST | `/internal/recon/batches/{batchId}/advance` | recon-server | 人工推进一个批次，等价编排器的一轮 |
| R-10 | POST | `/internal/recon/export` | gate-txn-pay-server | 受理抽取指令即返回，实际抽取转平台线程池 |
| R-11 | POST | `/internal/recon/export` | collect-pay-server | 同上 |
| R-12 | POST | `/internal/recon/export` | daily-ticket-server | 同上 |
| R-13 | POST | `/internal/recon/export` | ticket-server | **接口存在但从不被调用**（`ticket` 不在期望清单），保留待甲方要求全量过闸明细时启用 |

**鉴权**：⚠️ **当前处于开发测试阶段，这组接口（含各源的 `/internal/recon/export`）全部无鉴权**。
用户 2026-09-11 明确要求「删除令牌要求，不用令牌了，当前处于开发测试阶段」，原先的 `X-Recon-Token`
定长比较已从 `ReconInternalController` 与四个源的 `ReconExportController` 中整段删除，
`ReconClient` / `ReconExportClient` 也不再发送该头（连带消除了请求头被打进日志时的令牌明文泄漏）。
`recon.internal-token` / `RECON_INTERNAL_TOKEN` 配置键仍留在各模块 properties 里但**已无任何读取方**。
**上线前 MUST 恢复**：这组端点含分片接收、批次状态变更、生成与投递，无鉴权等于允许任何网络可达方
篡改对账结果，与 AGENTS.md §5.2「新增状态变更型接口 MUST 有鉴权」相冲突，属有意为之的临时降级。
恢复时比较 MUST 用 `MessageDigest.isEqual`，两端注入同一个值。
另注意 recon-server 的 `other.web.skipCheckTokenUrls` **MUST 保持 `/**`**（键名语义与直觉相反，
写成 `/internal/recon/**` 会让所有分片上送返回 403 `check token error`）。

## 核心类

- `ReconInternalController` — 只做参数校验与路由；`POST /daily/run` 是 web-admin 的入口，异常统一转 `retCode=9999`
- `ReconOrchestrationService` — `runDailyBatch()`（同步整批 + `AtomicBoolean` 拒并发）、下发抽取、失败重下发、推进批次、生成与投递编排；**无任何 `@Scheduled`**、**全类不带 `@Transactional`**
- `ReconBatchService` — 批次创建与状态流转白名单（`allowed(from, to)`）
- `ReconSourceService` — 期望登记、三项总账收齐校验、`MISMATCH` 判定
- `ReconPartService` — 分片幂等接收、SHA-256 校验、`.receiving` 临时名 + 原子 rename
- `ReconFileGenerationService` — 明细流式拼接 / 汇总按 `keyFieldCount` + `metricFieldCount` 泛化聚合、生成后校验
- `ReconFileTransferService` + `ReconFtpService` — FTP 投递、`.uploading` 临时名 + 远端 rename
- `ReconStorageProperties` / `ReconFtpProperties` / `ReconOrchestrationProperties` — `recon.*` 配置
- 各源的 `ReconExportController` / `ReconExportService` / `ReconExportMapper` + `mapper/ReconExportMapper.xml`
- 跨服务契约：`model` 的 `com.chinasofti.huateng.model.recon`（`ReconFileTypeEnum` / `ReconRecord` / `ReconExportReqDTO` / `ReconExportRespDTO` / `ReconSourceCompleteReqDTO` / `ReconCreateBatchReqDTO` / `ReconBatchViewDTO` / `ReconPartReceiptDTO`）
- 跨服务调用：`rpc` 的 `com.chinasofti.huateng.rpc.recon`（`ReconClient` / `ReconPartUploader` / `ReconPartSink` / `ReconExportClient`），注解 `@EnableRpcRecon`

## 分片文件格式契约

**格式来源是甲方规格原文 `docs/接口规范文档/ACC与ITP之间的文件.docx` §一「对账文件」，不是本项目自定义。**
四类文件由 `ReconFileTypeEnum` 定义：`EXP("ITP.EXP")`、`PAY("ITP.PAY")`、`BUS("ITP.BUS")`、`DETAIL("ITP.DETAIL")`。
枚举现在带三个属性：`aggregate` / `keyFieldCount` / `metricFieldCount`，
`getFieldCount()` 对 EXP / DETAIL 返回甲方固定段数、对 PAY / BUS 返回键 + 度量之和。

分片与最终文件的格式完全一致：**管道分隔纯文本、UTF-8、行尾 `\n`、无表头、不是 JSON**。
`ReconRecord.DELIMITER='|'`、`ReconRecord.LINE_SEPARATOR='\n'`。金额一律是**单位分的整数**（`long`）。

> **编号约定（本文全篇遵守，引用时 MUST 写明是哪一套）**：
> 「**第 N 段**」= 甲方规格的段号，**1 基**（EXP 第 1 段是逻辑卡号）；
> 「**0 基下标 N**」= Java 数组下标，与 `payLine(row, countIndex, ...)` 等代码常量一致
> （`PAY_IDX_GATE_COUNT=11` 对应甲方第 12 段）。
> **NEVER 不加限定就写「idx 11」**——两套编号差 1，错位一段就是把笔数写进旅游票金额段那类静默错账。

### ITP.EXP — 单边交易明细文件（明细类，13 段）

```
逻辑卡号|票卡交易序列号|订单金额(分)|实际扣款金额(分)|优惠金额|进站设备编码|进站时间
|出站处理设备类型|出站设备编码|出站时间|订单异常类型|支付方式|交易日期
```

第 11 段「订单异常类型」甲方定义 1~15：1 单边账(入站)、2 单边账(出站)、3 单边入站(人工处理单)、
4 单边出站(人工处理单)、5 乘客自主补进站、6 乘客自主补出站、7 TVM 补币找零不足、8 TVM 卡票、
9 TVM/BOM 发售无效票、10 闸门无用、11 无票出闸、12 人为单程票无效、13 非人为单程票无效、
14 储值票无效、15 其他情况。

⚠️ **EXP 的语义是「单边账 / 异常交易明细」，NEVER 当成全量过闸明细**——
筛选条件是「订单异常类型有值」，不是「过闸成功」。这正是 `ticket` 源被移出期望清单的原因。

### ITP.PAY — 统计汇总文件（聚合类，21 段 = 5 段键 + 16 段度量）

```
键   日期|线路|车站|设备编号|支付方式
度量 BOM/TVM发售笔数|BOM/TVM发售金额|BOM/TVM充值笔数|BOM/TVM充值金额
     |旅游票张数(发售)|旅游票金额|过闸笔数|过闸金额|APP购票笔数|APP购票金额
     |BOM行政处理笔数|BOM行政处理金额|单边交易笔数|单边交易金额|BOM处理笔数|BOM处理金额
```

16 段度量是 **8 组「笔数, 金额」**，0 基下标 5~20。各源只填自己有能力填的组、其余组写字面 `0`，
同键多行由 recon-server 二次聚合累加，**相加即等于「各填各段」**。
下标对照见下一节「各源抽取口径」。

### ITP.BUS — 商业优惠汇总文件（聚合类，4 段 = 1 段键 + 3 段度量）

```
日期|对账金额|付款金额|优惠金额
```

⚠️ **BUS 只有 1 段键（日期），NEVER 照搬 PAY 的 5 段键**——
多带一段维度会让 recon-server 按 1 段键聚合时把维度值当度量列读，整行错位且不报错。

### ITP.DETAIL — 虚拟电子多日计次票文件（明细类，7 段）

```
运营日期|交易类型|逻辑卡号|交易日期时间|交易金额|当前车站名称|设备编码
```

甲方规则（原文三句）：
- 多日计次票**正常过闸不对账**，只对「车票购买」与「产生超时费的行程」两类。
- 车票购买的交易类型记「发售」，**当前车站与设备编码传空**（第 6、7 段固定空串）。
- 超时行程的交易类型记「出站」，费用为**线网最高票价或者 1**。

### 净化与拆解

拼行的**唯一出口是 `ReconRecord.line(Object...)`**，它对每个字段做 `sanitize`——
`null` 转空串，字段内的 `|`、`\r`、`\n` 一律替换成半角空格。
拆行用 `ReconRecord.split(line)`（`split("\\|", -1)`，保留末尾空字段）；
汇总度量用 `ReconRecord.metric(fields, index)`（缺失或非数字按 0）。

### 文件名、账期与投递路径

- 最终文件名 `ITP.<TYPE>.<yyyyMMdd>`，其中 `<yyyyMMdd>` 是 `RECON_BATCH.BUSINESS_DATE`。
- **账期按甲方原文**：T 日 2 点统计 **T-2 日 2 点 ~ T-1 日 2 点**，**文件名后缀取 T-2 日**
  （甲方例：8 月 20 号 2 点生成 `ITP.EXP.20190818`）。
  因此 `recon.orchestration.window-offset-days` 默认值**已从 1 改成 2**：
  `businessDate = 今天 - 2`、`windowStart = businessDate + 02:00`、`windowEnd = businessDate+1天 + 02:00`。
  **旧实现窗口区间是对的，但文件名后缀晚一天，属账期标签错账，已修复。**
- 窗口是 14 位 `yyyyMMddHHmmss` 字符串，**左闭右开** `[windowStart, windowEnd)`。
- `batchId = "RECON" + businessDate`，例 `RECON20260909`。批次号校验正则 `[A-Za-z0-9_-]{1,64}`。
- **FTP 固定路径 `/itp/recon`**（甲方原文 `ITP上传至固定路径：/itp/recon/`），
  `recon.ftp.remote-root` 默认值已改成该路径。
- 本地生成先写 `<fileName>.generating`，`verify` 通过后 `ATOMIC_MOVE` rename 成正式名（不支持原子移动时降级为普通 move）。
- 分片落盘路径 `<recon.storage.root>/<batchId>/<source>/<filetype 小写>/part-%08d.dat`，临时名后缀 `.receiving`。
- FTP 先传 `<remoteRoot>/<fileName>.uploading`，再远端 `rename` 成正式名；
  `ReconFtpService` 对文件名做正则校验 `ITP\.(EXP|PAY|BUS|DETAIL)\.\d{8}`，不符即拒。
- **rename 之后 MUST 回查**（`recon.ftp.verify-after-upload` 默认 `true`）：先 `SIZE`，拿不到再 `LIST` 匹配文件名，
  字节数与本地一致才算投递成功并打「对账文件投递已回查通过 remotePath=..., bytes=...」，
  否则抛 `IOException` 让该文件落 `FAILED` 等下一轮。**226 / 250 只表示命令被接受，NEVER 当成投递成功的判据。**
- **ACC 侧会主动取走并删除 `ITP.BUS*` 文件，事后 LIST 看不到它是正常现象。**
  2026-09-11 在测试环境 FTP（vsFTPd 3.0.3）用四个变体命名的探针实测：`ITP.BUS.20260910` / `ITP.BUS.20260909` /
  `ITP.BUSX.20260910` 上传后 **30~45 秒内**被对端删除，而 `PROBE.BUS.20260910` 与 `ITP.EXP|PAY|DETAIL.*` 一直留着。
  当天曾据「`RECON_BATCH_FILE` 记 `UPLOADED`、几分钟后 `RETR` 返 550」误判为投递静默失败并按「对端在极短间隔
  连续会话上丢文件」修了一轮，**结论已被上述探针推翻**。核对投递结果 **MUST** 看回查日志，
  **NEVER 再把「LIST 里没有 ITP.BUS」当成我方缺陷**。
- 甲方文档同节还给出了**测试环境 FTP 的 IP / 端口 / 账号口令**。
  **凭据一律只看甲方文档原文**，仓库内 `recon.ftp.*` 全部是 `${RECON_FTP_*:}` 空默认值 + K8s Secret 注入，
  **NEVER 把账号口令抄进任何 `.md`、日志或提交信息**。

## 数据表

DDL：`recon-server/src/main/resources/sql/recon-server-schema.sql`，增量脚本 `recon-server-schema-migration.sql`。
**测试库 `AFCITPDB` 已于 2026-09-11 建好并验证，生产库仍未执行 DDL**（见文末遗留问题 A1）。

| 表 | 主键 | 关键列 |
|---|---|---|
| `RECON_BATCH` | `BATCH_ID` | `BUSINESS_DATE`(8) / `WINDOW_START`(32) / `WINDOW_END`(32) / `STATUS`(24) / `FAIL_REASON`(1024) / `CREATED_AT` / `UPDATED_AT` |
| `RECON_BATCH_SOURCE` | `(BATCH_ID, SOURCE_NAME, FILE_TYPE)` | `STATUS` / `DECLARED_PARTS` / `DECLARED_RECORDS` / `DECLARED_AMOUNT` / `RETRY_COUNT` / `FAIL_REASON`；索引 `IDX_RECON_SOURCE_STATUS (BATCH_ID, STATUS)` |
| `RECON_BATCH_PART` | `(BATCH_ID, SOURCE_NAME, FILE_TYPE, PART_NO)` | `BYTE_COUNT` / `RECORD_COUNT` / `AMOUNT_TOTAL` / `SHA256`(64) / `FILE_PATH`(512) / `STATUS` / `RETRY_COUNT`；索引 `IDX_RECON_PART_STATUS (BATCH_ID, STATUS)` |
| `RECON_BATCH_FILE` | `(BATCH_ID, FILE_TYPE)` | `FILE_NAME`(128) / `FILE_PATH`(512) / `BYTE_COUNT` / `RECORD_COUNT` / `AMOUNT_TOTAL` / `SHA256` / `STATUS` / `REMOTE_PATH`(512) |

后三张表都有指向 `RECON_BATCH(BATCH_ID)` 的外键，因此**建表顺序必须先 `RECON_BATCH`**。
`DECLARED_*` / `RETRY_COUNT` / `BYTE_COUNT` / `AMOUNT_TOTAL` 是 `NUMBER ... DEFAULT 0 NOT NULL`，写入不得为 null。

另需在 `daily-ticket-server` 侧建**一条**索引，脚本为
`daily-ticket-server/src/main/resources/sql/daily-ticket-recon-export-index.sql`：

```sql
CREATE INDEX IDX_DAILY_TICKET_ORDER_RECON ON DAILY_TICKET_ORDER (PAY_STATUS, PAY_DATE, ORDER_NO);
```

DETAIL 的 Keyset 与 PAY 的旅游票汇总**共用这一条**：两者都是 `PAY_STATUS` 等值 + `PAY_DATE` 范围。
旅游票汇总不再查主单 `TRAVEL_TICKET_ORDER`，因此**主单侧不需要任何新索引**。旅游票汇总另有的
`PARENT_ORDER_NO IS NOT NULL` 谓词由已存在的
`IDX_DAILY_TICKET_ORDER_PARENT (PARENT_ORDER_NO)` 兜住（Oracle B-tree 不存全 NULL 键，
该单列索引里只有旅游票子单，对 `IS NOT NULL` 是可用路径），**无需新增索引**。

## 状态取值

批次状态 `ReconBatchStatus`（落 `RECON_BATCH.STATUS`），白名单在 `ReconBatchService.allowed`：

- `CREATED` → 只允许 `EXPORTING`、`FAILED`
- `EXPORTING` → 只允许 `PARTIAL`、`ALL_SOURCE_COMPLETED`、`FAILED`
- `PARTIAL` → 只允许 `ALL_SOURCE_COMPLETED`、`FAILED`
- `ALL_SOURCE_COMPLETED` → 只允许 `GENERATING`、`FAILED`
- `GENERATING` → 只允许 `UPLOADING`、`FAILED`
- `UPLOADING` → 只允许 `SUCCESS`、`FAILED`
- `FAILED` → 只允许回到 `EXPORTING`、`GENERATING`、`UPLOADING`（三个补偿入口）
- `SUCCESS` → **终态，一律拒绝流出**。同一账期要重跑必须换 `batchId`
- 非法流转由 `transition` 抛 `IllegalStateException: 非法的对账批次状态变更`；
  `transitionIfNeeded` 只在「当前已等于目标」时短路返回，不放宽白名单
- `ALL_SOURCE_COMPLETED → GENERATING` 这一步**由 `ReconFileGenerationService.generate` 自己做**，
  编排器不单独推；因此直接调 R-08 upload 而没先 generate 时会因批次仍在 `ALL_SOURCE_COMPLETED` 被拒
- `updateStatus` 带 `expectedStatus` 做 CAS，影响行数不为 1 即抛「对账批次状态已被其他请求更新」

来源状态 `ReconSourceStatus`（落 `RECON_BATCH_SOURCE.STATUS`）：

- `PENDING` — `registerExpectations` 登记后的初始态
- `EXPORTING` — 抽取指令已被源受理（`accepted=true`），同时清空上一轮 `FAIL_REASON`
- `COMPLETED` — 源声明收齐且三项总账全等，是参与文件生成的唯一状态
- `FAILED` — 指令被回绝、参数非法或源声明失败；编排器下一轮在 `RETRY_COUNT < max-retry` 时重下发
- `MISMATCH` — 三项总账有任一项不等。**不自动重试、不参与生成**，`advanceBatch` 见到即把批次置 `FAILED` 并打 `log.error`，必须人工介入

文件状态 `ReconFileStatus`（落 `RECON_BATCH_FILE.STATUS`）：`GENERATED` → `UPLOADED`。
分片状态 `ReconPartStatus`：落库即 `RECEIVED`。

## 批次编排与调度

调度**在 web-admin 的 Quartz（`sys_job` job_id 109「日终对账」，`reconQuartzTask.runDailyBatch()`，cron `0 30 2 * * ?`）**。
recon-server 本身**一个 `@Scheduled` 都没有**、启动类也没有 `@EnableScheduling`（2026-09-11 按用户要求
「不使用 EnableScheduling，改用 web-admin 调用，改为每日执行一次，由 web-admin 控制频率」返工）。
本节此前记载的「调度全在 recon-server 自己的 `@Scheduled` 里、`sys_job` 里没有对账任务」**已作废，NEVER 回退**。
改频率只改那条 cron；`recon.orchestration.dispatch-cron` 与 `RECON_DISPATCH_CRON` 已删除。

- 入口 `POST /internal/recon/daily/run` → `ReconOrchestrationService.runDailyBatch()`，**同步**跑完整批：
  1. `properties.isEnabled()` 为 false 直接抛异常（web-admin 侧会记失败，不再是静默空转）；
  2. `AtomicBoolean running` CAS 失败即抛「上一次尚未结束，本次拒绝」——**拒绝而不排队**；
  3. `dispatchDailyBatch()`：算账期与窗口 → `batchService.create`（幂等）→
     `sourceService.registerExpectations`（按主键幂等）→ 批次推 `EXPORTING` → 逐源 `dispatchSource`，
     返回 `batchId`；建批次或登记期望失败**抛异常**（不再只打 log 就 return）；
  4. 循环 `advanceBatch(batchId)`，每轮间隔 `recon.orchestration.advance-delay-millis`（默认 60000ms）；
     见到 `SUCCESS` 即返回，累计耗时将超 `recon.orchestration.run-timeout-millis`（默认 240000ms）时抛异常，
     批次留在非终态、下一次运行重入，不丢数据。
- **`run-timeout-millis` MUST 小于 `ReconClient.getResponseTimeout()` 的 5 分钟**，否则会变成
  「web-admin 先超时记失败、recon-server 还在跑」。放宽时两处一起改。
- `advanceBatch(batchId)` 一轮的顺序：重下发 FAILED 来源 → 有 `MISMATCH` 即置 `FAILED` 返回 →
  未全齐则按「有没有已 COMPLETED 的」决定停在 `EXPORTING` 还是推 `PARTIAL` →
  全齐推 `ALL_SOURCE_COMPLETED` → 逐类型 generate → 推 `UPLOADING` 并逐类型 upload → 推 `SUCCESS`。
  任一步抛异常统一 `catch` 后置 `FAILED`，下一轮从 `FAILED` 重入。
- 要生成哪几类文件由 `RECON_BATCH_SOURCE` 里出现过的 `FILE_TYPE` 并集决定，按 `ReconFileTypeEnum` 声明顺序输出。
  当前三个源的并集恰好是四类全覆盖（EXP 只有 gate-txn-pay 一个来源）。
- **只有进程内 `AtomicBoolean`、没有数据库锁**，因此 **recon-server MUST 单副本**（web-admin 同样单副本）。
  多副本会让两个 Pod 同时推进同一批次、重复生成与重复投递。
- 人工补跑有两条路：web-admin 前台对 job 109 点「执行一次」，或直接打
  `POST /internal/recon/daily/run`；单批次单步推进仍可用 R-09 `/internal/recon/batches/{batchId}/advance`。

因为每轮都会重算目标状态，`ReconBatchService.transitionIfNeeded` 把「已在目标状态」判为成功，
否则每轮都会因非法流转刷一条 ERROR。**NEVER 把编排链路上的 `transitionIfNeeded` 换回 `transition`。**

### 2026-09-11 首次由 web-admin 驱动时踩到的四件事（1.0.9 / 1.0.10 / 1.0.12 修复三件）

1. **源侧「秒回 COMPLETED」被编排线程覆盖成 EXPORTING**（1.0.9 修复）。原实现是「先发 HTTP、收到
   `accepted` 后再写 EXPORTING」，而 gate-txn-pay 的 EXP 是空结果集、约 250ms 就回调 complete，
   于是 COMPLETED 被随后的 EXPORTING 覆盖，该 `(来源, 文件类型)` 永远收不齐、整批停在 `PARTIAL`。
   两处一起改：`dispatchSource` 改成**先置 EXPORTING 再发 HTTP**；`markExporting` 改走
   `ReconSourceMapper.updateStatusIfNotTerminal`（`STATUS NOT IN ('COMPLETED','MISMATCH')`）。
   **NEVER 把任一处改回去。**
2. **`PARTIAL -> EXPORTING` 不在状态白名单里，导致停在 PARTIAL 的账期再也补不回来**（1.0.10 修复）。
   每次 `runDailyBatch()` 都先 `dispatchDailyBatch()` → `transitionIfNeeded(EXPORTING)`，缺这一条就返回
   `9999: 非法的对账批次状态变更: PARTIAL -> EXPORTING`。
3. **未修复（属既有环境缺口）：`recon.storage.root` 不是 ReadWriteMany PVC，Pod 一换分片就没了。**
   2026-09-11 实测：19:49 由旧 Pod 收下的分片，滚更到新 Pod 后 generate 阶段报
   `分片文件不存在或路径非法: /home/javaapp/app/recon/RECON20260909/gate-txn-pay/pay/part-00000000.dat`，
   批次反复落 `FAILED`；而已 `COMPLETED` 的来源不会被重下发，**该账期只能靠清掉 `RECON_*` 四张表里
   这个 batchId 的行后重跑**。因此「一次运行必须在同一个 Pod 生命周期内跑完」，
   **上线前 MUST 落地 PVC**（见 `docs/ops/生产环境清单.md` §六 P0 第 2 条）。
4. **同一账期第二次触发直接返 9999：`SUCCESS -> EXPORTING` 是终态流出**（1.0.12 修复）。
   `runDailyBatch()` 一进来就调 `dispatchDailyBatch()`，后者无条件执行
   `transitionIfNeeded(EXPORTING)`；`create` 用的是 `insertIfAbsent`、不改任何列，但白名单里
   `case SUCCESS -> false`，于是抛「非法的对账批次状态变更: SUCCESS -> EXPORTING」。
   cron 每天算出的 batchId 不同所以碰不到，**但前台「执行一次」在同一天点第二下必然踩**
   （2026-09-11 在 1.0.11 的 Pod 上实测到这条栈：`ReconOrchestrationService.java:195`
   → `ReconBatchService$$SpringCGLIB$$0.transitionIfNeeded`）。修法是在 `create` 之后加一句
   「已 `SUCCESS` 就 log 后直接 `return batchId`」，外层第一轮 `advanceBatch` 拿到 `SUCCESS`
   即正常收口。**NEVER 删这个短路**。1.0.12 实测：`{"retCode":"0000","retMsg":"日终对账已完成:
   batchId=RECON20260909, status=SUCCESS"}`，日志「对账批次本账期已收口，跳过重复下发」+
   「rounds=1, elapsedMs=285」，`MERGE INTO RECON_BATCH` 的 `fetchRowCount:0`（确认没改任何行）。
   连带记一条：**`runDailyBatch` 的 Javadoc 曾声称「重复触发安全」，那只在 `advanceBatch`
   那一层成立**，`dispatchDailyBatch` 这一层此前并没有兑现，**NEVER 再据那句话推断幂等**。

### traceId 贯穿（2026-09-11 补齐，参考 pay-sign / card-pool 的口径）

按 `sys_job_log` 里那条 traceId 去查四个模块的日志，此前**一条都查不到**：`ReconQuartzTask` 早就在发
W3C 头（`QuartzTraceUtils.traceHeaders` 生成 `traceparent` + `X-Vlogs-Capture: 1`），
公共 `log4j2-linux.xml` 的 pattern 也早就写着 `%X{traceId}`，但四个模块都没打开
`management.tracing.enabled`（`resource/micro/web/src/main/resources/web.properties:78` 默认 `false`），
于是**入向的 traceparent 无人续接、那一列恒为空**。已在四个模块的配置里补上同一组三行
（`management.tracing.enabled=true` + `sampling.probability=0` + 排除 `OtlpAutoConfiguration`）：

- `recon-server/src/main/resources/application.properties`（1.0.11，当前部署 1.0.12）
- `gate-txn-pay-server/src/main/resources/application.properties`（2.0.60）
- `daily-ticket-server/src/main/resources/application.properties`（1.0.22）
- `collect-pay-server/src/main/resources/application.yml`（1.1.78，注意本模块是 YAML，
  `spring.autoconfigure.exclude` 要并进已有的 `spring:` 段）

链路是 web-admin（`AbstractQuartzJob` 造 traceId 并写进 `sys_job_log.job_message`）→ recon-server
（`ReconClient` 带 `traceparent` 进来）→ 三个源（recon-server 侧 `ProxyWebClient` 用 Boot 托管的
`WebClient.Builder`，观测自动带出 `traceparent`）。**四个模块 MUST 一起开**，漏一个链路就断在那一环。

三条注意：**①`sampling.probability=0` 不影响 MDC**，它只管 span 上不上报，traceId 照样进 MDC；
**②排除 `OtlpAutoConfiguration` 那行 NEVER 删**，Boot 3.2.6 没有 `management.tracing.export.enabled`，
只要 Deployment 注入 `MANAGEMENT_OTLP_TRACING_ENDPOINT` 就会重新起 exporter；
**③NEVER 自造一个 `traceId` 请求头** —— `FirstFilter` 会把所有请求头小写后塞进 MDC，
自定义头会落到 `traceid` 键、和 pattern 里的 `%X{traceId}` 对不上，永远不显示。

## 幂等与重试设计

本域不用 Redis、不用消息队列，全部靠「落库状态 + 唯一键 + `@Scheduled` 扫表重推」。

- **建批次幂等**：`insertIfAbsent` + 主键 `BATCH_ID`，重复触发（人工重跑、Pod 重启后错过补跑）都安全。
- **期望登记幂等**：`RECON_BATCH_SOURCE` 主键 `(BATCH_ID, SOURCE_NAME, FILE_TYPE)` + `insertIfAbsent`。
- **分片幂等**：主键 `(BATCH_ID, SOURCE_NAME, FILE_TYPE, PART_NO)` + **内容哈希比对**。
  同键重传时若 `SHA256` 与已落库值一致，直接返回原回执；不一致抛「重复分片内容不一致」。
  并发插入撞主键由 `DuplicateKeyException` 兜底：删掉刚落的文件、回查已存在行、再比一次哈希。
- **分片落盘顺序**：先写 `.receiving` → 校验字节上限（`recon.max-part-bytes`）与 SHA-256 →
  原子 rename → 才 INSERT。校验失败即删临时文件，**不留半截分片**。
- **收齐三项校验**（`ReconSourceService.declare`）：把源声明的 `totalParts` / `totalRecords` / `totalAmount`
  与 `RECON_BATCH_PART` 的实际落库聚合值逐项比对，**三项全等**才置 `COMPLETED`；
  任一不等置 `MISMATCH`，差异写进 `FAIL_REASON`（超 1024 截断）并 `log.error`。
  已是 `COMPLETED` 且声明值与上次完全一致时短路返回（重复声明幂等）。
- **`allCompleted` 还要求行数等于期望条数**（期望清单里所有 `(来源, 文件类型)` 组合数，当前是 8 组），
  防止 dispatch 那轮中途异常少登记几行、剩下几行全 `COMPLETED` 被误判成收齐。
- **来源失败重试**：`FAILED` 且 `RETRY_COUNT < recon.orchestration.max-retry`（默认 3）时，
  `increaseRetry` 后按「只重下发该文件类型」补偿；达到上限只打 `log.error`、不再自动重下发。
- **`MISMATCH` 不自动重试**，这是有意为之：分片少一片，最终文件就少几十万行，而纯文本文件本身看不出缺失。
- **源侧未提交即失败**：`ReconPartSink` 必须在成功路径显式 `commit()`；
  没 `commit()` 就 `close()`（抛异常、提前 return）时会自动向 recon-server 声明失败，
  声明本身再失败只记日志、不抛（否则会覆盖业务侧真正的异常）。
- **生成幂等**：已存在 `GENERATED` 记录且磁盘文件仍在时直接返回，不重复合并。
- **生成后校验分两档**（`ReconFileGenerationService.verify`）：
  - **明细类（EXP / DETAIL）严格比对**：记录数与金额都必须等于各 `COMPLETED` 来源声明之和，不等即抛。
  - **聚合类（PAY / BUS）不与源声明的业务金额严格比对**。合并后 `amountTotal` 的口径是
    **该文件所有度量列之和**（PAY 是 16 列、BUS 是 3 列全加），是**跨环节校验和、不是业务金额**；
    源声明的却是业务金额，两者本质不可比。这一层只做非负与行数健全性检查，
    行数大于声明之和时 `log.warn` 提示复核源端聚合口径。
    **NEVER 把聚合类改回「金额必须相等」**——一定会误报 `MISMATCH`。
- **分片号必须从 0 连续**：`orderedParts` 校验 `partNo` 严格递增且从 0 起，不连续即抛「分片序号不连续」。
- **投递幂等**：允许的前置批次状态是 `GENERATING` / `UPLOADING` / `FAILED`；单个文件投递成功只更新
  自己的 `STATUS` 与 `REMOTE_PATH`，**不判批次 SUCCESS**，收口由编排器在全部文件 `UPLOADED` 后统一决定。
- **路径穿越防护**：分片与最终文件路径都 `normalize()` 后校验 `startsWith(root)`；
  FTP 远端目录不允许含 `..`。

## 各源抽取口径

三个源的共性：
- 入口都是 `POST /internal/recon/export`，同一个 `X-Recon-Token`，**受理即返回**（`accepted`），
  真正的抽取提交到名为 `recon-export` 的**固定大小平台线程池**（`recon.export.worker`，默认 1）。
  **NEVER 在请求线程上抽取**——全服务 `spring.threads.virtual.enabled=true`，
  阻塞的 ojdbc8 调用会 pin 住载体线程。
- 明细分页一律 **Keyset 游标**（`(时间列, 主键)` 复合游标 + `FETCH FIRST #{limit} ROWS ONLY`），
  **没有 OFFSET**，每批默认 5000 行（`recon.export.page-size`）。
- 抽取方法**不带 `@Transactional`**，每条 SQL 自动提交、不保持长事务。
- 汇总（PAY / BUS）在**数据库内 GROUP BY** 后上送，源侧不做内存聚合。
- 期望清单（`recon.orchestration.sources`）共 **8 组** `(来源, 文件类型)`。

### 源 `gate-txn-pay`（gate-txn-pay-server，主表 `GATE_TXN_PAY`）→ EXP / PAY / BUS / DETAIL

窗口条件四类文件共用 `Recon_Window`：`TXN_DATE` 两端闭（VARCHAR2 存 8 位 `yyyyMMdd`，
同时是月分区键，**是分区裁剪的唯一依据**）+ `OUT_TIME` 左闭右开（VARCHAR2 存 14 位 `yyyyMMddHHmmss`，
**NEVER 套 `TO_DATE`**，会让索引前缀失效）。

| 文件 | SQL | 业务筛选 | 说明 |
|---|---|---|---|
| EXP | `selectExpPage` | `ORDER_EXP_TYPE IS NOT NULL AND ORDER_EXP_TYPE <> ' '` | **全量单边账明细**。**刻意不加 `DEBIT_STATUS`**：单边账的本质就是扣费链路没走完，按 `SUCCESS` 过滤等于把要报的行全滤掉。空格判定是必须的（历史数据可能写单个空格表示无异常） |
| PAY | `selectPayGateSummary` | `DEBIT_STATUS='SUCCESS'` | 填「过闸」组，0 基下标 **11 / 12** |
| PAY | `selectPayExpSummary` | 同 EXP 的异常口径 | 填「单边交易」组，0 基下标 **17 / 18** |
| BUS | `selectBusSummary` | `DEBIT_STATUS='SUCCESS'` | 按 `TXN_DATE` 单键分组，对账金额与付款金额都取 `NVL(SUM(TOTAL_AMOUNT),0)`（本表无独立实收列，两列同值是有意的） |
| DETAIL | `selectDetailPage` | `NVL(OVERTIME_AMOUNT,0) > 0 AND CARD_TYPE IN ('0445','0446','0447','0448')` | **只出「超时费」那部分**，交易类型固定中文「出站」。**刻意不加 `DEBIT_STATUS`**：超时费是否结清与是否要报账是两件事 |

- PAY 两条 SQL 的键都是 `TXN_DATE / LINE_CODE / OUT_STATION / DEVICE_ID / SIGN_CHANNEL_CODE`，
  **「线路」段自 2026-09-11 起由 SQL 侧 `LEFT JOIN STATION_INFO S ON S.STATION_CODE = T.OUT_STATION`
  取 `S.LINE_CODE`**（不再补空串），Java 侧 `payLine` 读 `LINE_CODE`、为 `null` 时输出空串。
  **MUST 用 `LEFT JOIN`，NEVER 用 `INNER JOIN`**（维表缺一条就整组漏账）；
  **NEVER 用车站代码前缀去猜线路**。
  为此本 mapper 全部 5 条 select 的主表都统一起了别名 `T`，三个公共片段
  （`Recon_Window` / `Recon_Keyset` / `Recon_Exp_Filter`）里的列名也一律带 `T.` 前缀——
  否则带 join 的两条会依赖「两表无同名列」这一偶然事实，`STATION_INFO` 后续加列即 `ORA-00918`。
  **新增 select MUST 同样把主表别名写成 `T`。**
  两个结果集各输出一行、彼此把对方那两段写 0，同键两行由 recon-server 累加合并。
  `payLine(row, countIndex, ...)` 只需传笔数下标，金额固定写在下一段。
- `DEBIT_STATUS` 取值 `INIT / PROCESSING / SUCCESS / FAIL / RETRY / CLOSED`，
  **只有 `SUCCESS` 代表钱已到账，这是白名单，NEVER 改写成「非 FAIL」之类的黑名单**。
- DETAIL 的四个卡类型码来源是 `model` 的 `CardTypeMapping.DAY_TICKET_ISSUE_TYPES` /
  `CardTypeCodeEnum`（0445 一日票 / 0446 三日票 / 0447 七日票 / 0448 多日计次票），
  **NEVER 凭 `SIGN_CHANNEL_CODE` 猜票种**；「当前车站名称」取 `EXIT_STATION_NAME`。
- EXP 行里由 Java 侧补空 / 补 0 的段：第 5 段优惠金额（补 `0`）、第 6 段进站设备编码（补空）、
  第 8 段出站处理设备类型（补空）。第 3、4 段（订单金额 / 实际扣款金额）都取 `TOTAL_AMOUNT`，
  第 9 段出站设备编码取 `DEVICE_ID`。第 11 段 `ORDER_EXP_TYPE` **原样输出、不做映射**（见遗留问题 B7）。

### 源 `collect-pay`（collect-pay-server，四张表）→ PAY / BUS

四张表：`TBL_TVM_ORDER_PAY` / `TBL_TVM_ORDER_TOPUP` / `TBL_TVM_APP_ORDER` / `TBL_BOM_ORDER_PAY`。
窗口列一律 `CREATE_TIME`（**VARCHAR2(100) 存 `yyyy-MM-dd HH:mm:ss`，直接字符串比较**，左闭右开，
`jdbcType=VARCHAR`，**NEVER 套 `TO_DATE`**）。成功状态白名单：三张表 `STATUS='1'`、
`TBL_TVM_APP_ORDER` 是 `PAY_STATUS='1'`（`ItpStatusEnum`：0 进行中 / 1 成功 / 2 失败 / 3 未支付）。

| 文件 | SQL | 填哪一组（0 基下标） | 金额列 |
|---|---|---|---|
| PAY | `selectTvmPayPaySummary` + `selectBomPayPaySummary` | BOM/TVM 发售 **5 / 6** | `TOTAL_PRICE` / `TRANS_AOUNT` |
| PAY | `selectTvmTopupPaySummary` | BOM/TVM 充值 **7 / 8** | `TRANS_AMOUNT` |
| PAY | `selectAppOrderPaySummary` | APP 购票 **13 / 14** | `PAY_AMOUNT` |
| BUS | 四张表各一条 `select*BusSummary` | 日期单键 + 对账金额 / 付款金额 | 同上，四条结果依次写入同一个 BUS sink，由 recon-server 按 1 段键累加 |

- **`DETAIL` 已从本模块删除**：甲方 DETAIL 是虚拟电子多日计次票文件，与 TVM / BOM 无关，
  原先四条给 DETAIL 用的 keyset select 已整段删掉，改由 `gate-txn-pay`（超时费）与 `daily-ticket`（发售）负责。
- `txnDate` 段用 `REPLACE(SUBSTR(CREATE_TIME,1,10),'-','')`，该表达式只在 SELECT 列表与 GROUP BY，**不进 WHERE**。
- 金额列全是 VARCHAR2，**SUM 前必须 `TO_NUMBER`，外面再包 `NVL`**；
  **NEVER 靠隐式转换**（口径不可控，脏数据直接 `ORA-01722`）。
- BOM 金额列 DDL 原文拼错成 `TRANS_AOUNT`（少一个 M），**NEVER 顺手改成 `TRANS_AMOUNT`**。
- 混合大小写列 `totalPrice` / `merchantOrderNo` / `PAYCENTER_channelOrderNo` **全部回避不引用**。
- 缺列导致的空段：充值表与 BOM 表无车站列、APP 表无设备列，对应键段由 Java 补空串。
- **线路段（PAY 第 2 段）自 2026-09-11 起两组能填、两组仍空**：
  `selectTvmPayPaySummary`（`TBL_TVM_ORDER_PAY`）与 `selectAppOrderPaySummary`（`TBL_TVM_APP_ORDER`）
  加了 `LEFT JOIN STATION_INFO S ON S.STATION_CODE = T.IN_STATION_CODE` 取 `S.LINE_CODE`；
  `selectTvmTopupPaySummary`（`TBL_TVM_ORDER_TOPUP`）与 `selectBomPayPaySummary`（`TBL_BOM_ORDER_PAY`）
  **两表连车站码列都没有（只有 `DEVICE_ID`），线路段只能留空**。
  join 键取 `IN_STATION_CODE` 而非 `OUT_STATION_CODE`：一是与车站段同源（车站段本来就是
  `IN_STATION_CODE`，取 OUT 会出现「车站 6 号线 + 线路 01」的自相矛盾行），
  二是实测 `TBL_TVM_ORDER_PAY` 111 行里 55 行 `OUT_STATION_CODE` 为 NULL、`IN_STATION_CODE` 一行不缺。
  **MUST `LEFT JOIN`，NEVER `INNER JOIN`；NEVER 用车站码前 2 位推线路。**
  本文件没有公共 sql 片段，带 join 的两条各自把主表别名写成 `T`、维表 `S`，不牵连其他 select。
  `writePayRow` 因此多了一个 `lineCode` 形参（第 3 位），四处调用点都已同步。
- 该模块配置写在 `application.yml`（**没有 `application.properties`**）。

### 源 `daily-ticket`（daily-ticket-server）→ PAY / DETAIL

| 文件 | SQL | 主表 | 窗口列 | 状态过滤 |
|---|---|---|---|---|
| DETAIL | `selectDetailPage` | `DAILY_TICKET_ORDER` LEFT JOIN `DAILY_TICKET_INSTANCE` | `PAY_DATE`（TIMESTAMP，绑 `jdbcType=TIMESTAMP`） | `PAY_STATUS='PAID'` |
| PAY | `selectTravelTicketPaySummary` | `DAILY_TICKET_ORDER`（旅游票**子单**，`PARENT_ORDER_NO IS NOT NULL`） | `PAY_DATE` | `PAY_STATUS='PAID'` |

- DETAIL 出**「车票购买（发售）」**行：第 2 段固定中文「发售」，
  **第 6 段当前车站名称与第 7 段设备编码固定空串**（甲方明文要求传空）。
  逻辑卡号取 `DAILY_TICKET_INSTANCE.CARD_NUM`（`ORDER_NO` 上有唯一索引，一对一、不放大行数；
  用 `LEFT` 而非 `INNER` 是因为已付未激活的订单也必须出现在发售明细里）。
  **DETAIL 里也含旅游票子单**（没有加 `PARENT_ORDER_NO IS NULL`）：PAY 是汇总、DETAIL 是明细，
  并存不算重复计账；甲方是否要求 DETAIL 只含独立日票**无依据，当前不加该条件**。
- PAY 只填「旅游票张数(发售) / 旅游票金额」一组，0 基下标 **9 / 10**。
  **取数对象是旅游票子单 `DAILY_TICKET_ORDER`，不是主单 `TRAVEL_TICKET_ORDER`**（2026-09-11 改）：
  主单是聚合壳、`PAY_STATUS` 永不回写，支付事实（`PAY_DATE` / `PAY_AMOUNT` / `PAY_CHANNEL_CODE`）
  全在子单上，见下方 C13。
  **张数用 `COUNT(*)`**——子单表没有 `TICKET_COUNT` 列，且下单时按 `ticketCount` 循环拆单、
  一条子单恰好一张票，笔数即张数；**NEVER 换回 `SUM(TICKET_COUNT)`**（那是主单的列）。
  金额用 `NVL(SUM(NVL(PAY_AMOUNT, TICKET_PRICE)), 0)`。
  5 段键中**第 5 段「支付方式」填子单 `PAY_CHANNEL_CODE`**（因此 SQL 按「日期 + 渠道」分组、
  一天可能多行），线路 / 车站 / 设备三段仍由 Java 补空串（本模块 5 张表无这三列）。
- `TO_CHAR(PAY_DATE, ...)` 只出现在 SELECT 列表与 GROUP BY，**不进 WHERE**。
- 两条查询**共用一条索引** `IDX_DAILY_TICKET_ORDER_RECON`（都是 `PAY_STATUS` 等值 + `PAY_DATE`
  范围），不建即 400 万级全表扫，见遗留问题 A2。

## 新增配置键

recon-server（`recon-server/src/main/resources/application.properties`）：

- `recon.storage.root`（默认 `/home/javaapp/app/recon`）、`recon.max-part-bytes`（默认 134217728，128MB）、
  `recon.max-part-records`（默认 250000）、`recon.internal-token`（默认空，Secret 注入）
- `recon.ftp.host` / `port`（默认 21）/ `username` / `password`（默认空，Secret 注入）/
  **`remote-root`（默认 `/itp/recon`，来自甲方原文）** / `enabled`（默认 `false`）/
  **`verify-after-upload`（默认 `true`，NEVER 关，rename 后 SIZE/LIST 回查字节数）** /
  **`upload-interval-millis`（默认 800，相邻投递间隔，保守限流不是修复项）**
- `recon.orchestration.enabled`（默认 `true`）/ **`advance-delay-millis`（默认 60000，一次运行内的轮询间隔）** /
  **`run-timeout-millis`（默认 240000，一次整批运行的总超时，MUST < `ReconClient` 的 5 分钟响应超时）** /
  **`window-offset-days`（默认 2，按甲方账期从 1 改过来）** /
  `window-start-time`（默认 `020000`）/ `max-retry`（默认 3）
- **`dispatch-cron` / `RECON_DISPATCH_CRON` 已于 2026-09-11 删除，NEVER 加回**：触发时机与频率由
  web-admin `sys_job` 109 的 cron 决定，本模块没有任何 cron 配置。
- `recon.orchestration.sources[N].name` / `.url` / `.file-types`，**N=0..2 三个源**：
  `gate-txn-pay`（EXP,PAY,BUS,DETAIL）、`collect-pay`（PAY,BUS）、`daily-ticket`（PAY,DETAIL）
- **`service.recon.self-url` 已删除**（2026-09-11）：`ReconExportClient` 的 baseUrl 现在固定为空串，
  复用 `URLDynamicRouter` 的动态路由做法，下发地址只来自 `recon.orchestration.sources[].url` 的绝对 URL。
  原占位 baseUrl 让 `InternalMicroHttp` 的 INFO 日志打出
  `url=http://127.0.0.1:9112/http://<源服务>/internal/recon/export` 这种双份地址，误导排查。
- `service.recon.openLogger`（默认 `true`）— `ReconExportClient` 是否打请求日志

各源服务（含仍保留代码的 ticket-server）：`service.recon.url`、`recon.internal-token`、
`recon.export.temp-dir`（默认 `/home/javaapp/app/recon-export`）、`recon.export.page-size`（默认 5000）、
`recon.export.worker`（默认 1）。另有两个只写在 `ReconPartUploader` 的 `@Value` 里、
properties 中没有显式列出的滚片阈值：`recon.export.max-part-records`（默认 200000）与
`recon.export.max-part-bytes`（默认 67108864，64MB）。

敏感项 `recon.ftp.password` 默认值为空，由 K8s Secret 注入。
`recon.internal-token` 虽然形态相同，但**已无任何读取方、不需要注入**（见 §鉴权现状）——
**NEVER 再写「由 Secret 注入」把它和 `recon.ftp.password` 并列**，否则上线核对会去找一个没人读的值。
`recon.ftp.enabled=false` 时 `ReconFtpService.upload` 直接抛「对账 FTP 未启用」，批次会停在 `FAILED`。

⚠️ **`recon.sources`（环境变量 `RECON_SOURCES`）是死配置**，默认值里还留着 `ticket`，
**别拿它当「参与对账的源清单」**（见遗留问题 D10）。真实清单只看 `recon.orchestration.sources`。

## 编码约束

- **改行格式、字段顺序或分隔符前 MUST 先核对甲方规格原文**
  `docs/接口规范文档/ACC与ITP之间的文件.docx` §一，**NEVER 自行发明字段或调整顺序**。
  改动 **MUST 同时改 recon-server 与三个源服务**（ticket-server 侧的保留代码也要跟着改，否则将来启用即错位），
  并按 AGENTS.md §7 的约束重建链路上每一个经手 `model` DTO 的模块镜像。
- **NEVER 用 JSON 承载分片或最终文件**。四类文件都是无 schema 的管道分隔纯文本，
  拼行只能走 `ReconRecord.line(...)`；**NEVER 自己 `String.join("|", ...)`**——
  字段里带上分隔符或换行会让整个文件从该行起全部错位，而下游读不出异常。
- **NEVER 把明细全量读进内存**。明细合并只做 1MB 缓冲的**流式字节拼接**，全程不解析行内容；
  行数与金额直接累加分片回执上的声明值。
- **明细文件 NEVER 走聚合路径**。`mergeAggregated` 用 `TreeMap` 常驻内存，只适用于行基数几百到几千的汇总文件；
  几千万行明细的键全量入 Map 会直接把堆打满。分流依据是 `ReconFileTypeEnum.isAggregate()`。
- **聚合段数一律读枚举，NEVER 硬编码**。聚合器已泛化为 `keyFieldCount` + `metricFieldCount`
  （PAY 5+16、BUS 1+3），**NEVER 退回「固定 5 段键 + 2 段度量」的旧写法**。
- **NEVER 在 SQL 正文写注释**（Druid WallFilter `commentAllow=false`，带注释的语句静默失效）。
  各 `ReconExportMapper.xml` 的口径说明全部写在 `<!-- -->` 里，且 XML 注释内不能出现连续两个半角减号。
- **抽取 NEVER 跑在请求线程**，必须提交到 `recon-export` 平台线程池（虚拟线程 pin 载体线程）。
- **分页 NEVER 用 OFFSET**，一律 Keyset 游标；`ORDER BY` 必须与游标条件严格对应，
  第二段用主键破平（同秒多笔只比时间列会漏行或死循环）。
- **编排链路上的方法 NEVER 加 `@Transactional`**：`ReconOrchestrationService` 内部既有 RPC 又有大文件 IO，
  被事务包住会让行级锁的持有时长等于对端响应时长与文件大小，Druid 回收连接后整个事务连状态记录一起丢弃。
- **汇总 SQL 的 WHERE 必须与对应明细 SQL 逐字一致**（`selectPayExpSummary` 对 `selectExpPage`、
  `selectPayGateSummary` 对 `selectBusSummary`），改一处必须同步另一处。
- 状态机一律白名单，**NEVER 写成「非终态即可处理」**。
- **NEVER 删 ticket-server 侧的对账代码**：它是甲方要求全量过闸明细时的现成入口，删了要重写。

## 已知坑与遗留问题

分四组：**A 环境类**（运维可闭合）、**B 甲方文档自身的歧义与缺口**（需向甲方澄清）、
**C 数据侧空缺**（导致部分字段恒 0 或范围过宽）、**D 实现侧其他遗留**。
**不要美化、不要漏**：下面每一条都还没闭合。

### A 环境类

- **A1 四张 `RECON_*` 表：测试库已建、生产库仍未建**：`RECON_BATCH` / `RECON_BATCH_SOURCE` /
  `RECON_BATCH_PART` / `RECON_BATCH_FILE`（含索引 `IDX_RECON_SOURCE_STATUS`、`IDX_RECON_PART_STATUS`）
  已于 **2026-09-11 在测试库 `172.20.222.3:1521/AFCITPDB`（用户 `qditp`，口令走 K8s env、不入文档）执行**，
  并用 `USER_TABLES` 查到 4 张表全在。当时库内数据量很小：
  `DAILY_TICKET_ORDER` 44 行、`TRAVEL_TICKET_ORDER` 3 行、`GATE_TXN_PAY` 40 行。
  **生产库仍未执行**：DDL `recon-server/src/main/resources/sql/recon-server-schema.sql`，
  增量脚本 `recon-server-schema-migration.sql`。后三张有外键，**建表顺序必须先 `RECON_BATCH`**。
  本项目「代码有 mapper、生产库无表」是高频缺陷，**NEVER 假定仓库有 DDL 就等于生产库已建表**。
  测试库回退用的还原 SQL（**按外键反序**，与 A2 的索引一并列出）：

  ```sql
  DROP TABLE RECON_BATCH_FILE PURGE;
  DROP TABLE RECON_BATCH_PART PURGE;
  DROP TABLE RECON_BATCH_SOURCE PURGE;
  DROP TABLE RECON_BATCH PURGE;
  DROP INDEX IDX_DAILY_TICKET_ORDER_RECON;
  ```
- **A2 索引：测试库已建、生产库仍未建**：`IDX_DAILY_TICKET_ORDER_RECON (PAY_STATUS, PAY_DATE, ORDER_NO)`，
  脚本在 `daily-ticket-server/src/main/resources/sql/daily-ticket-recon-export-index.sql`。
  2026-09-11 已在测试库 `AFCITPDB` 建好并用 `USER_INDEXES` 验证存在
  （同表另有既存的 `IDX_DAILY_TICKET_ORDER_PARENT`）。
  DETAIL 与 PAY 两条查询共用它，生产库不建即 400 万级全表扫。
  这是对账**唯一**需要新建的索引——主单侧索引已随旅游票改按子单统计而废弃、脚本里也已删除。
- **A3 共享存储未落地 + 必须单副本**：`recon.storage.root` 默认 `/home/javaapp/app/recon`，
  **不挂 ReadWriteMany PVC 就是 Pod 本地盘**——recon-server 重启会丢已收分片（库里有记录、文件没了，
  生成时报「分片文件不存在或路径非法」）。且 `advance()` 只有进程内 `AtomicBoolean`、没有数据库锁，
  **recon-server MUST 单副本**。
- **A4 三个源地址已实测回填，recon-server 自身尚未部署**：recon 侧
  `recon.orchestration.sources[0..2].url` 的默认值已按 **2026-09-11 `kubectl get svc -n itp` 实测**回填为
  `http://gate-txn-pay-server-jomf4-svc.itp.svc:30019`、
  `http://collect-pay-c23ku-svc.itp.svc:30024`、
  `http://daily-ticket-server-rdbe5-svc.itp.svc:30027`。
  ⚠️ **易错点：这些 Service 的端口等于 NodePort 号，不等于容器内的 `server.port`**
  （collect-pay 容器是 58101，但 Service 端口是 30024；gate-txn-pay 是 9106 / 30019、
  daily-ticket 是 9108 / 30027）。**NEVER 按容器端口拼 Service 地址。**
  **三个源的 `service.recon.url` 已于 2026-09-11 回填 recon-server 的真实 Service 名**
  （`http://recon-server-bjzdy-svc.itp.svc:30034`，该服务同日部署完成）。
  ⚠️ **本段此前写的「仍是占位值、集群内既没有 recon-server 的 Service 也没有 Deployment、本服务尚未部署」
  已全部作废，NEVER 回退**。线上真实值一律 **MUST 查 Deployment env**
  （`RECON_SRC_*_URL` / `SERVICE_RECON_URL` 会覆盖 jar 内默认值）。
  `recon.internal-token` **已无任何读取方、不需要注入**（见本文 §鉴权现状与 `AGENTS.md` §2.2.2）：
  X-Recon-Token 校验已按用户 2026-09-11 的要求整段删除，
  **NEVER 再写「两端必须是同一个值 / 为空时 `/internal/recon/**` 返回 401」** —— 那是已作废的说法。
  该键保留只为便于日后恢复鉴权，**上线前 MUST 恢复**。
- **A5 在跳板机上跑对账相关 SQL 的可行手段（2026-09-11 实测）**：`k8s-master` 上**没有 `sqlplus` /
  `sqlcl` / `python3`，只有 Java 8**。可从 `/home/java/ticket-server-*.jar` 里
  `jar xf BOOT-INF/lib` 取出 `ojdbc8-19.18.0.0.jar`，配一个 **Java 8 语法**的小 runner 执行建表 / 校验查询。
  ⚠️ **本机 JDK 21 编译该 runner MUST 加 `--release 8`**；且**远端 `javac` 是 Java 8、默认 ASCII 编码**，
  直接把含中文注释的源码丢到远端编译会报 `unmappable character`，所以源码不要带中文、或在本机编好 class 再传。
  DB 口令 **MUST 从 Deployment env 取、只经 shell 变量传给进程，NEVER 打印**（日志、回显、脚本里都不许出现）。

### B 甲方文档自身的歧义与缺口（需向甲方澄清）

- **B5 DETAIL 第 6 段用词不一致**：表头写「当前车站**名称**」，同节规则句写
  「当前车站**类型**和设备编码传空」。两处用词不同，**当前实现两种读法结果都是传空**，
  但若甲方本意是「名称要传值、只有类型传空」，实现需要改。
- **B6 EXP / PAY / BUS 三节的「文件存放路径：」在原文里是空的**：`/itp/recon/` 只出现在 DETAIL 节末尾
  （「ITP 上传至固定路径：/itp/recon/」）。当前按**四类文件共用该路径**实现，需甲方确认。
- **B7 「订单异常类型」取值冲突**：甲方定义 1~15，而我方 `GATE_TXN_PAY.ORDER_EXP_TYPE` 的列注释
  （`scripts/20260818_if8a_schema_migration.sql:28`）是
  「0 正常, 1 单边账(入), 2 单边账(出), 3 单边入站人工, 4 单边出站人工, **5 双段计费超时**」——
  1~4 能对上，**第 5 位含义完全不同**（甲方是「乘客自主补进站」），且我方多一个「0 正常」而甲方无 0。
  当前实现**原样输出 `ORDER_EXP_TYPE`、不做任何映射**（猜错会把超时行程报成自主补站，账目性质变了）。
  连带后果：**`ORDER_EXP_TYPE='0'`（正常单）也会被 EXP 与 PAY 单边组捞进去**，
  需甲方裁决是否排除 `'0'`。
- **B8 DETAIL 的「总金额」没有产出**：甲方要求「加入虚拟电子多日计次票的明细**和总金额**」，
  「总金额」这一行 / 字段**三个源与 recon-server 都没有实现**（DETAIL 是 7 段明细，没有汇总行的位置）。
- **B9 超时费金额未做钳制**：甲方要求「费用为线网最高票价或者 1」，
  当前 `gate-txn-pay` 直接取 `OVERTIME_AMOUNT` 原值、**没有钳制**。
  钳制需要 para-server 的线网最高票价参数，属跨模块改动。
- **B10 ⚠️ 「支付方式（同交易明细）」是悬空引用（2026-09-11 用 MCP 实测更正了此前的记载）**：
  EXP 第 12 段与 PAY 第 5 段（1 基段号）原文都只注明「同交易明细」，
  但本 docx 内**没有定义「交易明细」的支付方式码表**、也没指明是哪一份文档；
  测试库 `SYS_DICT_TYPE` 里只有 RuoYi 自带的 10 个系统字典，**也没有支付方式码表**。
  ⚠️ **此前本条写「三个源各填各的、口径根本不统一」，这个判断是错的**：实测四张来源表的值域
  一致，都是两位码且共享 `03` / `04` ——
  `GATE_TXN_PAY.SIGN_CHANNEL_CODE` 取 `03/0B/12/01/04/null`、
  `TBL_TVM_ORDER_PAY.CHANNEL` 取 `03/null`、`TBL_BOM_ORDER_PAY.CHANNEL` 取 `04/03/null`、
  `TBL_TVM_APP_ORDER.PAY_CHANNEL_CODE` 取 `03/04/null`、
  `DAILY_TICKET_ORDER.PAY_CHANNEL_CODE` 取 `03/04/null`。
  即列名不同但编码空间是同一套，**不需要做跨源映射**。
  **真正的风险是 null 占比高**：TVM 购票 111 行里 108 行 `CHANNEL` 为 null、
  daily-ticket 44 行里 35 行 `PAY_CHANNEL_CODE` 为 null。
  null 会让 PAY 第 5 段成为空段，**甲方无法按支付方式归类**，同一天同车站同设备的多种支付方式
  会被聚成一个「空支付方式」组。
  仍待甲方确认「同交易明细」指的是不是这套两位码；**联调 MUST 拿真实文件核对第 5 段**，
  并同步排查上游为何大面积不落渠道码。
- **B11 段数是我方数出来的、不是甲方显式声明的**：原文行格式串里有多处漏引号
  （`出站处理设备类型'`、`单边交易笔数'` 缺前引号），字段边界靠人工判读；
  文件名后缀在 EXP/PAY/BUS 写 `yyyyMMdd`、在 DETAIL 写 `YYYYMMDD`。
  当前按 13 / 21 / 4 / 7 段实现，**首次与 ACC 联调 MUST 拿真实文件逐段核对**。
- **B12 PAY / BUS 两节的账期说明沿用了 EXP 的措辞**：三节的「注意」都写
  「T 日 2 点统计 T-2 日 2 点 - T-1 日 2 点的**单边明细**」，BUS 节甚至写「生成**单边交易文件**，
  文件名为 ITP.BUS.20190818」。按字面读会得出「PAY / BUS 也只统计单边明细」的错误结论。
  当前按「三类文件共用同一时间窗口、各统计自己的口径」实现。
- **B13 甲方 DETAIL 节的票种区分依据与我方不同**：原文写「由 ACC 根据线路上传文件的
  `SIGN_CHANNEL_CODE` 字段进行票种区分」——那描述的是 **ACC 侧对闸机文件的处理**，不是 ITP 侧。
  我方 DETAIL 用 `CARD_TYPE IN ('0445'~'0448')` 收窄。两者是否一致需甲方确认。
- **B14 ⚠️ 扣费失败且未标异常类型的订单，在甲方四类文件里没有落点（2026-09-11 实测新发现）**：
  窗口 `[20260910020000, 20260911020000)` 内 `GATE_TXN_PAY` 有 4 笔 `DEBIT_STATUS='FAIL'`，
  其中 1 笔 `ORDER_EXP_TYPE=5`（金额 1740）正常进了 EXP；另外 **3 笔 `ORDER_EXP_TYPE='0'`
  （2×90、1×200）既不进 PAY 过闸组（非 `SUCCESS`）也不进 EXP（`'0'` 已被 `Recon_Exp_Filter` 排除），
  在 EXP / PAY / BUS / DETAIL 四类文件里全都没有位置**——钱没收到、也没有任何一份文件向 ACC 声明。
  需甲方明确二者之一：①这类订单应归入哪一类文件（若归 EXP，则我方需要给它们补一个异常类型码，
  连带影响 B7 的映射）；②它们只是待重试的中间态、本期不报、成功后落到后续账期即可。
  **NEVER 自行把 `'0'` 放回 EXP 口径**——那会连正常单一起捞进单边账，正是 2026-09-11 刚修掉的缺陷。

### C 数据侧空缺（导致部分字段恒 0 或范围过宽）

- **C10 `gate-txn-pay` 无可靠优惠金额列**：`DISCOUNT_LEVEL_AMT` 的语义是「命中的累计金额门槛」
  （`GateTxnPay.java:54`），**不是本笔优惠额**，且生产数据大面积为 NULL
  （见 `docs/testing/user-card/01-二维码电子票.md` 与 `05-阻塞项与缺陷候选.md`）。
  因此 **EXP 第 5 段与 BUS 第 4 段恒 0**。同源还有两段恒空：
  EXP 第 6 段「进站设备编码」与第 8 段「出站处理设备类型」（表无对应列）。
- **C11 `collect-pay` 的 `TBL_BOM_ORDER_PAY.TRANS_TYPE` 取值三处互相矛盾**：
  `entity/BomNoCashOrder.java` 的注释清单里**没有「购票 / 发售」这一项**；
  `constant/BomBusinessCodeEnum` 把 `01` 与 `22` 的描述**都写成「充值」**；
  `BomOrderServiceImpl` 发售建单却硬编码 `setTransType("01")`。
  且该列有一条来源是**设备上送**。因此**未按购票 / 充值拆分，整张表计入「BOM/TVM 发售」组**；
  连带 **「BOM 行政处理」（idx 15/16）与「BOM 处理」（idx 19/20）两组恒 0**。
  四张表均无优惠列，**BUS 优惠段恒 0**。
- **C12 `daily-ticket` 无法按票种收窄**：`DAILY_TICKET_INSTANCE.CODE_TICKET_TYPE` 全表恒 `'0441'`
  （DDL 默认值与激活时无条件写入的值都是 `'0441'`，零区分度；且该值语义是「二维码后付费单程票」、
  与列名不符）；`DAILY_TICKET_ORDER.CARD_TYPE` 直接落 APP 上送值、入口不做码值校验，
  库里混着三套编码空间（APP 口径的日票聚合桶 `'05'` 一个值覆盖 0445~0448，**本身就分不出计次票**；
  ACC 两位口径 `'48'`；发卡口径 `'0448'`）；`SHOW_TYPE` 全仓库无比较点、语义未定义。
  因此当前**导出全部已付日票订单**（含普通日票），**票种范围待甲方给码值**。
  这是有意的降级：**NEVER 凭猜写码值**——猜错会静默命中 0 行或漏掉整类票，比多导难查得多。
- **C13 旅游票主单是聚合壳，`PAY_STATUS` 永不回写 —— 这是当前设计，NEVER 再据此判 P0**：
  `TRAVEL_TICKET_ORDER` 插入时写死 `ORDER_STATUS='CREATED'` / `PAY_STATUS='INIT'`，
  全仓库对 `travelTicketOrderMapper` 只有注入与一次 `insert`、**没有任何 UPDATE**。
  原因是**聚合支付未实现**：`DailyTicketServiceImpl.requestPay` 第一步
  `orderMapper.selectByOrderNo` 查的是 `DAILY_TICKET_ORDER`，传主单号 `0T...` 命中 0 行、
  直接返回「订单不存在」（`validateOrderNo` 只校验 `orderType='1'` 与非空，不认单号前缀）。
  APP 只能拿子单号逐张付：上送网关的商户单号是**子单号**、金额是**单张 `TICKET_PRICE`**
  （`buildDailyTicketPayRequest`），回写走 `updatePayResultIfPaying`，把
  `PAY_STATUS='PAID'` / `PAY_DATE` / `PAY_AMOUNT` 落在子单上。
- **C14 旅游票 PAY 汇总已改按子单取数（2026-09-11 修复，原记「恒 0 行 P0」已失效）**：
  `selectTravelTicketPaySummary` 现在查 `DAILY_TICKET_ORDER`，
  过滤 `PARENT_ORDER_NO IS NOT NULL AND PAY_STATUS='PAID' AND PAY_DATE` 窗口，
  按 `TO_CHAR(PAY_DATE,'YYYYMMDD')` 与 `PAY_CHANNEL_CODE` 分组；
  张数 `COUNT(*)`（一条子单一张票），金额 `NVL(SUM(NVL(PAY_AMOUNT, TICKET_PRICE)), 0)`，
  **支付方式段填 `PAY_CHANNEL_CODE`、不再留空**。
  这同时消掉了原来「主单无 `PAY_DATE`、只能用 `UPDATE_TIME` 切窗口、非支付更新会重复计入」的偏差。
  排查「旅游票段为 0」**MUST 先确认子单是否真有 `PAY_STATUS='PAID'` 且 `PAY_DATE` 落在窗口内**，
  NEVER 再回去看主单状态。
- **C15 线路段（PAY 第 2 段）已部分补齐（2026-09-11 实测 + 改造）**：
  改造前三个源全部硬填空串；现在改为 LEFT JOIN 同库同 schema 的车站维表 `STATION_INFO`
  （`STATION_CODE` / `LINE_CODE` / `STATION_NAME` / `STATION_EN_NAME`，实测共 8 条线路
  `01/02/03/04/06/08/11/13`，车站码 4 位如 `0121`）取 `LINE_CODE`。
  **已能填**：`gate-txn-pay` 的过闸组与单边组（join `GATE_TXN_PAY.OUT_STATION`，
  实测 10 个车站码里 9 个命中，只有 `0245`（22 笔，测试造的假车站）查不到）；
  `collect-pay` 的 TVM 购票组与 APP 购票组（join `IN_STATION_CODE`）。
  **仍为空**：`collect-pay` 的 TVM 充值组与 BOM 组（两表只有 `DEVICE_ID`、无车站码列）、
  `daily-ticket` 全部组（连设备列都没有）。
  **MUST 用 `LEFT JOIN`，NEVER 用 `INNER JOIN`**：像 `0245` 这种维表里没有的车站码，
  INNER JOIN 会把整组数据从对账文件里丢掉，那是漏账，比线路段为空严重得多。
  collect-pay 侧同样存在这种车站码——`TBL_TVM_APP_ORDER.IN_STATION_CODE='0101'`
  在 `STATION_INFO` 里也查不到（2026-09-11 把四条改造后的 SQL 直接在测试库跑过，
  该组 `LINE_CODE` 为 NULL 但笔数金额都在）。
  **NEVER 用车站码前 2 位推线路**（实测前 2 位恰好等于 `LINE_CODE` 是编码巧合、不是契约，
  AGENTS.md 也明令禁止）。
- **C16 「过闸笔数 > 0 而过闸金额 = 0」不是缺陷（2026-09-11 实测确认）**：
  窗口 `[20260910020000, 20260911020000)` 内 `DEBIT_STATUS='SUCCESS'` 的行恰好 3 组、
  且**全部 `TOTAL_AMOUNT=0`**，与 PAY 里的 3 行一一对应。取数口径（`NVL(SUM(TOTAL_AMOUNT),0)`）
  是对的，是测试数据本身金额为 0。**排查金额为 0 MUST 先 `SELECT TOTAL_AMOUNT` 看源数据，
  NEVER 直接去改 SQL。**

### D 实现侧其他遗留

- **D15 端到端已在测试环境实跑通过（2026-09-11，recon-server 1.0.7）**，四类文件都有真实产物：
  `ITP.EXP.20260910`(86B) / `ITP.PAY.20260910`(363B) / `ITP.BUS.20260910`(19B) / `ITP.DETAIL.20260910`(52B)，
  批次 `RECON20260910` 收口 `SUCCESS`、四行 `RECON_BATCH_FILE` 全 `UPLOADED`、四条「投递已回查通过」齐全。
  **但本域仍无单元测试**，且用的是测试数据、金额多处为 0（见 C 组）。
  复跑核对顺序：三源 `accepted=true` → `RECON_BATCH_PART` 有行 → `RECON_BATCH_SOURCE` 8 组全 `COMPLETED` →
  `RECON_BATCH_FILE` 四类 `GENERATED` → **四条「对账文件投递已回查通过」日志**（不是事后 LIST，`ITP.BUS*` 会被 ACC 取走），
  并抽查文件首尾行的段数（13 / 21 / 4 / 7）与金额单位（分）。**鉴权已删，不需要核对 token 两端一致。**
- **D16 中间态订单的账期归属未定**：`gate-txn-pay` 按 `OUT_TIME` 切窗口但 PAY / BUS 只导
  `DEBIT_STATUS='SUCCESS'`，T+1 才回调成功的订单会漏出当期，除非重跑历史账期
  （重跑必须换 `batchId`，`SUCCESS` 是终态）。EXP 与 DETAIL 不受影响（两者刻意不加状态过滤）。
- **D17 聚合文件的校验强度低于明细文件**：PAY / BUS 生成后只做非负与行数健全性检查，
  **不与源声明金额严格比对**（口径不可比，见「幂等与重试设计」）。
  因此「源端聚合把度量填错列」这类缺陷在这一层发现不了，只能靠首次联调时人工核对真实文件。
- **D18 `RECON_BATCH.FAIL_REASON` 列已建但代码尚未写入**，批次级失败原因目前只在日志里
  （`advanceBatch` 的 `log.error`）。来源级失败原因是写库的（`RECON_BATCH_SOURCE.FAIL_REASON`）。
- **D19 `@EnableRpcRecon` 会把 `ReconClient` / `ReconPartUploader` 也扫进 recon-server**（同包），
  属无害冗余 bean，recon-server 自己不会用它们上送分片。
- **D20 `ReconStorageProperties.sources`（配置键 `recon.sources`）是死配置**：
  文件生成服务改读 `RECON_BATCH_SOURCE` 里 `STATUS='COMPLETED'` 的行，不再读这个列表。
  它的默认值里**仍留着 `ticket`**，改它不会影响任何行为，**别拿它当「参与对账的源清单」**。

## 参考原始文档

- **甲方规格原文：`docs/接口规范文档/ACC与ITP之间的文件.docx` §一「对账文件」**（只读，NEVER 修改）。
  该节给出四类文件的完整字段、订单异常类型 1~15 的取值、账期规则（T 日 2 点统计 T-2 ~ T-1）、
  DETAIL 的三条业务规则、FTP 固定路径 `/itp/recon/`，以及测试环境 FTP 的地址与凭据。
  ⚠️ **本文此前记载「甲方对账规格原文在仓库内缺失、行格式是本项目自定义 v1」——该结论是错的**：
  规格原文一直在仓库内，只是没被读到。代码已于 **2026-09-11** 按原文全部返工，
  自定义 v1 格式已废弃。**NEVER 再声称对账规格缺失**；改格式前 MUST 先读该 docx。
  同一文档 §一 2 还定义了第三方账单文件、参数文件与**逻辑卡号文件**的路径（`/itp/qrLoigcNum/`），
  其中**逻辑卡号文件对账仍无实现**（逻辑卡号的申请与导入在 `docs/business/card-pool.md`）。
- 与本域相邻的已有提示词：`docs/business/gate-txn-pay.md`、`docs/business/tvm-bom-pay.md`、
  `docs/business/daily-ticket.md`（三个源的业务口径与状态取值）；
  `docs/business/ride-code.md`（ticket-server，**其对账代码保留但不在期望清单**）；
  `docs/business/acc-es-file.md`（acc-es-server 的 FTP，**与本域 FTP 是不同的服务器与用途**）。
- 部署、环境变量、PVC 与上线核对：`docs/ops/生产环境清单.md`（§二 FTP、§三 环境变量、§六 P0、§七 核对清单）。
