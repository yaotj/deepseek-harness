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

## 附：recon-server 源码注释知识抽取（2026-09-16，阶段一）

> **行号会随代码迭代漂移**。引用本节条目前 **MUST** 先按定位串（类名.方法名 或文件名）grep 确认当前行号，**NEVER** 直接复制本节的行号。
>
> 处理范围：`recon-server/src/main/java/**/*.java`（2534 行、注释约 675 行）+ `src/main/resources/mapper/*.xml`（344 行）+ `application.properties`（119 行）。
> 四类归类：契约与判据 77 条 / 决策理由 17 条 / 陷阱 11 条 / 墓碑注释 12 条（见文末清单，不进正文）。

### 一、批次编排与状态机

#### 契约与判据

- **批次状态机白名单**（`recon-server/model/ReconBatchStatus.java:3~14`）：`CREATED -> EXPORTING -> PARTIAL -> ALL_SOURCE_COMPLETED -> GENERATING -> UPLOADING -> SUCCESS`。`FAILED` 不是终态，而是补偿重试的入口，可流向 `EXPORTING` / `GENERATING` / `UPLOADING`（`ReconBatchService.allowed`:73~91）。`SUCCESS` 是终态，NEVER 从 SUCCESS 再流出。重跑 MUST 换批次号。
- **`PARTIAL -> EXPORTING` MUST 在白名单里**（`ReconBatchService.allowed`:68~71）：`runDailyBatch()` 每天或人工重跑都会先 `dispatchDailyBatch()`，对已存在的批次做 `transitionIfNeeded(EXPORTING)`。批次停在 PARTIAL 时少了这一条重入就直接抛「非法的对账批次状态变更: PARTIAL -> EXPORTING」，返 9999，**那个账期再也补不回来**。2026-09-11 实测（batchId=RECON20260909 停在 PARTIAL）。
- **`FAILED -> ALL_SOURCE_COMPLETED` MUST 在白名单里**（`ReconBatchService.allowed`:61~66）：批次在「来源已全部收齐但生成或投递失败」时落到 FAILED，下一轮 `advanceBatch` 走的是 `transitionIfNeeded(ALL_SOURCE_COMPLETED)` 再重新生成。少了这一条，FAILED 批次只能靠 `dispatchDailyBatch` 的 `FAILED -> EXPORTING` 复活，而生产 cron 一天只跑一次且 batchId 按账期变化，昨天失败的批次今天不会再被下发，**永久卡在 FAILED、每轮只刷错误日志**。2026-09-11 实测复现（batchId=RECON20260907）。
- **`dispatchDailyBatch` 的「已 SUCCESS 就短路」NEVER 删**（`ReconOrchestrationService.dispatchDailyBatch`:176~183, 202~205）：`create` 用的是 `insertIfAbsent`，对已存在的批次不改任何列，但紧随的 `transitionIfNeeded(EXPORTING)` 走 `transition`，白名单里 SUCCESS 是终态一律拒绝流出，同一账期第二次触发必抛「非法的对账批次状态变更: SUCCESS -> EXPORTING」。cron 每天 batchId 不同碰不到，但**前台「执行一次」当天点第二下必踩**（1.0.11 的 Pod 上已实测到该栈）。
- **账期与窗口推算公式**（`ReconOrchestrationService.dispatchDailyBatch`:162~173）：`businessDate = 今天 - windowOffsetDays`（默认 T-2）；`windowStart = businessDate 当天 + windowStartTime`（默认 02:00:00）；`windowEnd = businessDate + 1 天 + windowStartTime`；文件名后缀 = `businessDate` 即 T-2 日；batchId = `"RECON" + businessDateText`（形如 `RECON20260910`）。甲方自校验例：今天 2019-08-20、offset 2 ⇒ 后缀 `20190818`、窗口 `[20190818 020000, 20190819 020000)`，与甲方一致。
- **`window-offset-days` 默认 2，NEVER 改回 1**（`storage/ReconOrchestrationProperties.java:49~55`，配置在 `application.properties:80`）：改成 1 会让文件名后缀与统计区间同时前移一天，与甲方对不上。
- **`run-timeout-millis` MUST 小于 `ReconClient.getResponseTimeout()` 的 5 分钟**（`storage/ReconOrchestrationProperties.java:36~46`，默认 240000，配置在 `application.properties:78`）：web-admin 同步等 `POST /internal/recon/daily/run` 的响应，本值若超过客户端超时会变成「客户端先超时报错、服务端还在跑」，`sys_job_log` 记的失败原因失去意义。要放宽 **MUST 两处一起改**。
- **超时不丢数据**：超时抛 `IllegalStateException`，批次留非终态，下一次运行从 FAILED / PARTIAL 重入（白名单允许）。实测一整轮（4 个文件、6 行 PAY）约 60 秒（同上 36~42）。
- **并发拒绝语义**：`running` 守整批运行、`advancing` 守单轮推进，两把都是进程内 `AtomicBoolean`，因此 **recon-server MUST 单副本**（`ReconOrchestrationService` 类注释:44~46）。上一次还在跑就直接拒绝，**NEVER 改成排队等待**——那会让 Quartz worker 越积越多（`runDailyBatch`:99~101）。
- **`transitionIfNeeded` 的幂等语义**：目标状态与当前状态相同则原样返回、不抛异常（`ReconBatchService.transitionIfNeeded`:29~45）。编排每轮都会重算目标状态，沿用 `transition` 会每轮因「非法流转」刷错误日志。
- **`transition` 带 CAS**：`updateStatus(batchId, 新, 旧)` 影响行数不等于 1 即抛「对账批次状态已被其他请求更新」（`ReconBatchService.transition`:47~56）。
- **MISMATCH 停止自动推进**：任一来源为 MISMATCH 时批次置 FAILED 并要求人工介入（`ReconOrchestrationService.advanceBatch`:298~301；`model/ReconSourceStatus.java:20~24` 说明 MISMATCH 不可自动放行——数据已不一致，重试同一窗口只会重复不一致）。
- **来源级重试上限**：同一 `(来源, 文件类型)` 超过 `max-retry`（默认 3，`application.properties:82`）后编排器不再自动重下发，只记 error（`retryFailedSources`:328~337）。
- **`selectUnfinished` 的口径**：取 `STATUS <> 'SUCCESS'` 的批次、按创建时间升序、最多 20 条，FAILED 也在结果里（`mapper/ReconBatchMapper.java:18~22`；`mapper/ReconBatchMapper.xml:37` 说明 ROWNUM 限流放在外层，保证先排序再截断）。
- **需要产出的文件类型 = 期望清单里所有 `fileTypes` 的并集**，按 `ReconFileTypeEnum` 声明顺序输出（`requiredFileTypes`:381~393）。
- **`dispatchDailyBatch` 建批次失败 MUST 抛异常**（`dispatchDailyBatch`:186~188）：原先是记日志后 return，那样 `runDailyBatch()` 会拿着一个不存在的批次轮询到超时。
- **人工改状态端点仅供人工干预**（`ReconInternalController.updateStatus`:81~88，`POST /internal/recon/batches/{batchId}/status`）：正常链路的状态推进一律由编排器按收齐结果决定，手工改会绕过收齐校验，MUST 在确认数据一致后才使用。
- **`POST /internal/recon/daily/run` 是本模块唯一的日常触发入口**（`ReconInternalController.runDailyBatch`:93~105）：同步跑完再返回，失败或超时返回非 `0000`，让 `sys_job_log` 反映真实成败；返回 `CommonResult` 而非 `BatchView`，因为调用方是 Quartz、只需要「成/败 + 原因」，批次明细另查 `GET /internal/recon/batches/{batchId}`。

#### 决策理由

- **为什么 `@Scheduled` 全删、改由 sys_job 触发**（`ReconServer.java:13~22`；`ReconOrchestrationService` 类注释:34~38）：用户 2026-09-11 明确要求「不使用 EnableScheduling，改用 web-admin 调用，改为每日执行一次，由 web-admin 控制频率」。加回 `@EnableScheduling` 等于让 `sys_job` 与本模块形成两套互不知情的调度源，改 cron 时只改一处就会出现「以为改了、实际另一套还在按老频率跑」，并造成重复建批次与重复投递。
- **为什么 `runDailyBatch` 要在服务端轮询而不是「下发完就返回」**（`runDailyBatch`:88~97）：源服务的抽取是**异步**的（受理即返回、之后才回推分片），不等收齐就没有任何东西会来推进批次——本模块已没有 `@Scheduled` 兜底了。同步跑完再返回是为了让 `sys_job_log` 反映真实成败（`docs/architecture/web-server.md` §7.1：不抛异常 Quartz 一律记「成功」）。
- **为什么本类绝不能加 `@Transactional`**（`ReconOrchestrationService` 类注释:40~42）：内部既有 RPC（下发抽取指令）又有大文件 IO（合并生成、FTP 投递），被事务包住会让行级锁的持有时长等于对端响应时长与文件大小，并在 Druid 回收连接后把整个事务连同状态记录一起丢弃。每一步都靠单条 SQL 自动提交落状态。
- **为什么轮询等待用 `Thread.sleep` 而不是 `synchronized` + `wait`**（`ReconOrchestrationService.sleep`:144~149）：全服务默认虚拟线程（AGENTS.md §5.2），`Thread.sleep` 在虚拟线程上会让出载体线程、不 pin。
- **为什么本类不再有 cron 配置项**（`storage/ReconOrchestrationProperties.java:16~19`）：原 `dispatch-cron` 已删除，也 NEVER 在 K8s 注入 `RECON_DISPATCH_CRON`——那个键现在没有任何读取方，留着只会让运维误以为改它能改调度频率。
- **为什么期望清单是配置而不是硬编码**（同上 8~14）：`sources[].fileTypes` 决定 `RECON_BATCH_SOURCE` 登记哪些 `(来源, 文件类型)` 行，进而决定「全部 COMPLETED」的判据与最终产出哪几个文件。新增 / 下线一个源 MUST 改这里，NEVER 在代码里硬编码来源名。

#### 陷阱

- **`retryCount` 是装箱 `Integer`，MUST 先判空兜 0 再与 `maxRetry` 比**（`retryFailedSources`:324~326）：直接 `progress.retryCount() >= ...` 会在列为 NULL 时抛 NPE，而 NPE 会被 `advanceBatch` 的 catch 吞成「批次推进失败」，掩盖真实原因。
- **`ReconInternalExceptionHandler` 必须存在，否则分片上送的失败会被上游当成成功**（`controller/ReconInternalExceptionHandler.java:15~31`）：公共 `GlobalControllerExceptionHandler` 的兜底是 `@ExceptionHandler(Exception.class) @ResponseStatus(HttpStatus.OK)`，任何未被更具体处理器捕获的异常都返回 HTTP 200 + UUID `retCode`；而 `ReconClient` 只按 HTTP 状态码判成败。于是令牌校验失败（401）上游看到 200 认为分片已接收，分片落盘 IO 失败 / 哈希不一致拒收同样是 200、源服务继续删本地分片。本类以 `Ordered.HIGHEST_PRECEDENCE` 抢在公共 advice 之前，作用范围用 `assignableTypes` 限定在 `ReconInternalController`。2026-09-11 实测：无令牌请求 `GET /internal/recon/batches/{id}` 返回 `200 {"retCode":"<uuid>",...}`。

### 二、分片接收

#### 契约与判据

- **分片幂等键是 `(BATCH_ID, SOURCE_NAME, FILE_TYPE, PART_NO)`**（`ReconPartService.receive`:41~51, 67~77）：先按键查，已存在则比对 SHA-256，一致直接返回原回执、不一致抛「重复分片内容不一致」；INSERT 撞唯一键时删掉刚落盘的文件、回查并比对，仍不一致抛「并发接收的分片内容不一致」。
- **分片入参校验规则**（`ReconPartService.validatePart` / `validateBatchId`:96~107）：`source` MUST 匹配 `[A-Za-z0-9_-]{1,32}`；`partNo` ∈ `[0, 99999999]`；`records` ≤ `recon.max-part-records`（默认 250000，`application.properties:48`）；`sha256` MUST 匹配 `[0-9a-fA-F]{64}`；`batchId` MUST 匹配 `[A-Za-z0-9_-]{1,64}`。分片字节数超过 `recon.max-part-bytes`（默认 134217728，`:47`）即删临时文件并拒收。
- **边收边算 SHA-256、先写 `.receiving` 临时文件再原子改名**（`receive`:53~66，`copyAndDigest`:80~94，`moveAtomically`:109~115）：1MB 缓冲，`ATOMIC_MOVE` 不被支持时退回普通 move。校验失败即删临时文件。
- **收齐判定只认三项总账全等**（`ReconSourceService` 类注释:19~24，`declare`:90~137）：落库分片数 / 记录数 / 金额合计逐一等于源声明的 `totalParts / totalRecords / totalAmount`，任一不等即置 MISMATCH，把差异写进 `FAIL_REASON` 并 `log.error`。NEVER 放行——分片少一片最终文件就少几十万行，而纯文本文件本身看不出缺失。
- **`allCompleted` 还要求行数等于期望条数**（`ReconSourceService.allCompleted`:139~150）：否则 dispatch 那一轮若中途异常少登记了几行，剩下几行全 COMPLETED 也会被误判成收齐。期望条数 = 期望清单里所有 `(来源, 文件类型)` 组合数（`expectedCount`:158~165）。
- **`FAIL_REASON` 列长 1024，超长截断**（`ReconSourceService.truncate`:194~198）：避免 `ORA-12899` 让整条声明失败。
- **`declare` 的重复声明短路**：当前已 COMPLETED 且三项与本次声明完全相同即原样返回（`declare`:116~124）。
- **`registerExpectations` 按主键幂等**（`ReconSourceService.registerExpectations`:45~63；`mapper/ReconSourceMapper.java:15~17`；`mapper/ReconSourceMapper.xml:32` 说明重复登记不覆盖已有进度）。
- **`selectTotals` 零行返回 `(0,0,0)` 而不是 null**（`mapper/ReconPartMapper.java:42~46`；`mapper/ReconPartMapper.xml:71` 的 `NVL`），但调用点仍 MUST 判空（`model/PartTotals.java:10~13`）。
- **分片序号 MUST 从 0 起连续**（`ReconFileGenerationService.orderedParts`:396~411）：不连续即抛「分片序号不连续」；`partNo` 为 NULL 时判定为不连续而不是抛 NPE。

#### 决策理由

- **为什么置 EXPORTING 要挪到下发 RPC 之前、并改用 `updateStatusIfNotTerminal`**（`ReconOrchestrationService.dispatchSource`:248~250；`ReconSourceService.markExporting`:64~74；`mapper/ReconSourceMapper.java:50~60`；`mapper/ReconSourceMapper.xml:85~89`）：下发原先是「先发 HTTP、后写 EXPORTING」，而源侧抽取跑在自己的线程池里，空结果集（如 gate-txn-pay 的 EXP 0 行）**可能在 200ms 内就回调 complete 声明 COMPLETED**，此时编排线程才写 EXPORTING 就把 COMPLETED 覆盖回去。条件更新加了 `STATUS NOT IN ('COMPLETED','MISMATCH')` 守卫。**NEVER 换回无条件的 `updateStatus`。**
- **为什么 `isDuplicateKeyViolation` 沿 cause 链判定、NEVER 简化回 `catch (DuplicateKeyException)`**（`ReconPartService.isDuplicateKeyViolation`:122~138，ADR-D53）：本模块打开了 tracing，`MapperAspectToTrace` 会切到所有 `@Mapper` 方法上；它此前把异常包成 `new RuntimeException(e)`，按类型 catch 的幂等兜底一条都进不去，`ORA-00001` 直接冒到全局处理器。切面已改成原样抛出，这层按 cause 链判定作为第二道防线保留。

#### 陷阱

- **空结果集的源可能在 RPC 响应到达前就回调 `complete`**（同上决策理由第一条）：反过来写会把 COMPLETED 覆盖成 EXPORTING，该 `(来源, 文件类型)` 从此永远停在 EXPORTING、**批次永不收齐**。2026-09-11 实测复现：batchId=RECON20260909，gate-txn-pay/EXP 已 declareComplete 却仍是 EXPORTING，整批 240s 超时停在 PARTIAL。
- **两个装箱类型 NEVER 直接用 `==` / `!=` 比较**（`ReconSourceService.diff`:167~173）：`PartTotals` 的分量是装箱类型（MyBatis 构造器映射要求），两侧都是装箱时 `!=` 退化成引用比较，超过 `Integer` 缓存范围（-128~127）的相等值会被判成不等。MUST 先判空拆成基本类型再比。
### 三、流式聚合与文件格式

#### 契约与判据

- **两条生成路径按 `ReconFileTypeEnum.isAggregate()` 分流**（`ReconFileGenerationService` 类注释:43~58）：明细（EXP 13 段 / DETAIL 7 段）走 1MB 缓冲的流式字节拼接、全程不解析内容，内存占用与文件大小无关（`mergeDetail`:135~169）；汇总（PAY 5 键 + 16 度量 / BUS 1 键 + 3 度量）走流式逐行读 + `TreeMap` 二次聚合（`mergeAggregated`:171~216）。
- **段数一律取自 `ReconFileTypeEnum.getKeyFieldCount()` / `getMetricFieldCount()`，NEVER 在本类里硬编码下标**（同上 50~53）：键是下标 `[0, keyFieldCount)`，度量是 `[keyFieldCount, keyFieldCount + metricFieldCount)`。行字段数不足 `keyFieldCount + metricFieldCount` 时直接抛异常——错位一段就是整份错账，**NEVER 静默补零放过**（`mergeAggregated`:174~178, 198~201）。
- **来源清单取自 `RECON_BATCH_SOURCE` 中该文件类型 `STATUS='COMPLETED'` 的行，不再读 `recon.sources`**（类注释:57~58，`generate`:112~115）：没有已收齐的来源即抛「该文件类型没有已收齐的来源」。
- **文件名 = `meta.getPrefix() + "." + batch.businessDate()`**（`generate`:117），落 `<storageRoot>/<batchId>/final/`，先写 `.generating` 再原子改名；目录做了 `startsWith(root)` 越权校验，分片路径同样校验（`generate`:118~129，`resolvePart`:413~419）。
- **生成前置状态白名单**：`ALL_SOURCE_COMPLETED` / `FAILED` 时转 `GENERATING`，已是 `GENERATING` 则继续，其余一律抛「当前批次不可生成文件」（`generate`:99~103）。已有 `GENERATED` 且文件确实在磁盘上就直接返回（`generate`:105~109）。
- **明细文件校验：记录数与金额 MUST 与来源声明之和严格相等**，不等即删临时文件并抛异常（`verify`:341~373）。
- **汇总文件的 `amountTotal` 是「所有度量列之和」，只是跨环节比对的校验和，不是业务金额**（`verify` 注释:344~354）：PAY 的 16 个度量里 8 列是笔数、8 列是金额（下标 6/8/10/12/14/16/18/20，即第 7/9/11/13/15/17/19/21 段），一起相加没有业务含义；BUS 的 3 个度量恰好全是金额，但为口径统一同样按「所有度量之和」计。
- **汇总文件 NEVER 与 `declaredAmount` 严格比对**（同上 351~354）：量纲不同，强行相等只会把正常批次全部误判为失败。汇总只做健全性检查：输出行数 > 0、校验和非负；行数只在「实际 > 声明之和」时告警提示复核源端聚合口径（跨源合并后行数必然 ≤ 各源声明之和）。
- **空文件是正常的零交易日，NEVER 当失败**（`verify`:380~383）：甲方要求每个账期都要有文件，空文件也必须投递。但「来源已声明记录数 > 0 而输出 0 行」是失败（`verify`:375~379）。
- **汇总输出格式**：按键升序写 `key|m0|m1|...|m(n-1)`，分隔符与行分隔符取 `ReconRecord.DELIMITER` / `LINE_SEPARATOR`，UTF-8（`writeAggregated`:303~330）。

#### 决策理由

- **为什么明细文件绝不能走汇总路径**（类注释:53~54，`mergeAggregated` 注释:180~181）：汇总行基数只有几百到几千，`TreeMap` 常驻内存安全；明细几千万行的键全量入 Map 会直接把堆打满。
- **为什么明细路径的行数与金额取分片回执上的声明值累加**（`mergeDetail` 注释:138）：这样不需要把文件读进内存。

#### 陷阱

- **任一失败分支都要删临时文件**（`mergeDetail`:164~167，`mergeAggregated`:212~215，`verify` 各分支）：否则残留的 `.generating` 会与下一轮混淆。

### 四、线路段补齐

#### 契约与判据

- **2026-09-16 起线路段由 recon-server 在聚合完成、写文件之前统一补齐，四个源不再各自 `LEFT JOIN STATION_INFO`**（`ReconFileGenerationService.backfillLineSegment`:218~238；`mapper/ReconStationMapper.java:8~25`；`storage/ReconLineBackfillProperties.java:8~27`；`application.properties:51~63`）。
- **覆盖是无条件的**：不看源上送的线路段是空还是有值，一律按车站码重算（`backfillLineSegment` 注释:232~234；`ReconLineBackfillProperties` 类注释:21~26）。
- **只有 PAY 需要补，NEVER 往 `file-types` 里加 EXP 或 DETAIL**（`ReconLineBackfillProperties.java:34~40`；`application.properties:58~59`）：BUS 只有 1 段键（日期），EXP 与 DETAIL 走明细路径、字节流式拼接、全程不解析行内容，加进去既不生效也不报错。
- **下标契约**：`line-field-index=1`（甲方 21 段 PAY 行格式的第 2 段）、`station-field-index=2`（第 3 段），均 0 基（`ReconLineBackfillProperties.java:43~46`；`application.properties:62~63`）。下标超出键段范围即抛异常（`backfillLineSegment`:246~249）；汇总键段数与文件类型不符也抛异常（:255~258）。
- **补齐后 MUST 用新键重新插入一个新 `TreeMap` 重排**（`backfillLineSegment` 注释:224~227）：`TreeMap` 按键升序输出、键的第 2 段就是线路，只替换字符串不重排会让输出行序变成「按空线路段排」，与现行文件（日期 / 线路 / 车站 / 设备 / 支付方式 升序）不一致。重排后行序与改造前逐字节一致。
- **撞键 MUST 逐列累加度量、NEVER 覆盖**（同上 229~231）：补齐可能让两个原本不同的键变成同一个键（典型情形是「已改的源留空线路」与「未改的源自带线路」在同一账期上送同一车站的账），覆盖等于丢掉一个源的账。
- **维表查不到的车站 MUST 留空、NEVER 丢行**（`backfillLineSegment` 注释:236~237；`mapper/ReconStationMapper.java:22~24`）：线路段留空、该组单独成行，账仍在，与各源原先用 `LEFT JOIN`（而非 `INNER JOIN`）的语义一致；用内连接会把该组的账整组抹掉，那是静默漏账。按不同车站码去重计数后告警一次（`:272~276`）。
- **`STATION_INFO` 只有 4 列**（`STATION_CODE` / `LINE_CODE` / `STATION_NAME` / `STATION_EN_NAME`，2026-09-16 按 `USER_TAB_COLS` 实测），本模块只读、零写入（`mapper/ReconStationMapper.xml:4~7`）。
- **`selectStationLineCodes` 的三条 SQL 口径**（`mapper/ReconStationMapper.xml:25~38`）：不加 `WHERE` 是因为 PAY 里出现的车站码来自四个源、事前不可枚举，逐个查等于把批处理退化成 N+1；带 `LINE_CODE IS NOT NULL` 是因为线路为空的行对补齐没用；`DISTINCT` 是防御性的（防维表被导入成重复行），但拦不住「同一车站码两个不同线路」那种真冲突——那种情况调用方保留后读到的一条，**MUST 去参数域修数据、NEVER 在本文件里挑一个**。
- **NEVER 用车站码前 2 位推线路**（`mapper/ReconStationMapper.java:18~20`）：实测前 2 位恰好等于 `LINE_CODE`，但那是编码巧合、不是契约，甲方改编码规则时不会通知我方，猜错等于把汇总账挂到错误线路上。

#### 决策理由

- **为什么线路段统一由本模块补、而不是各源自算**（`mapper/ReconStationMapper.java:11~16`）：`STATION_INFO` 属车站 / 参数域、owner 不是本模块，在本模块直连它是**有意破例**，理由是它把原先散在四个源服务里的四处同样破例收敛成一处——gate-txn-pay / collect-pay / face-pay / ticket 的 recon mapper 曾各自写一遍 `LEFT JOIN STATION_INFO S ON S.STATION_CODE = ...`，于是「线路怎么取」这件事有四份副本、四份各自的 join 列名、四次维表结构变更的暴露面。线路是车站的函数（一个车站码只对应一条线路），因此完全可以在聚合完成后由消费端一次补齐。
- **为什么下标写成配置而不是常量**（`storage/ReconLineBackfillProperties.java:14~19`）：段数一律取自 `ReconFileTypeEnum`，但它只给「键有几段、度量有几段」、给不出「第几段是线路」；写成配置是为了**出问题时能一键关闭（`enabled=false`）退回「原样写出源上送的线路段」，不必回滚镜像**。
- **为什么无条件覆盖正是它能安全上线的原因**（同上 21~26）：线路是车站的函数，对「还没改、仍自带线路」的旧源来说覆盖进去的是同一个值、文件逐字节不变；对「已改、线路留空」的新源才是真正填上。于是**四个源与 recon-server 的部署顺序完全自由**，不存在「必须同批上线」的窗口。**NEVER 改成「只在源上送为空时才补」**——那样重新引入顺序依赖，而且一旦某个源把线路取错，错值会被当成「有值」原样放过。
- **为什么返回 `List<Map>` 而不是实体、且不做进程级缓存**（`mapper/ReconStationMapper.java:29~38`）：只要两列，没必要为一张他域维表建实体类；维表几百行量级，一次全量拉回做 Map 比逐车站查库便宜得多。日终一天只跑一轮，缓存省下的一次查询毫无意义，却会引入「参数改了但 recon-server 没重启、线路仍按旧映射填」这种只在跨天时暴露的偏差。

#### 陷阱

- **维表查询失败 MUST 抛异常、NEVER 降级成空 Map**（`loadStationLines`:282~301）：降级的后果是「整份 PAY 文件的线路段全空」，而这份文件仍会被判定为生成成功并投递给 ACC——那是静默错账。查不出维表就让本次文件生成失败、批次留非终态等下次重入。`mapping.isEmpty()` 时抛「STATION_INFO 没有任何可用的车站到线路映射，拒绝生成线路段全空的对账文件」。
- **`file-types` 的类型名大小写不敏感**（`ReconLineBackfillProperties.appliesTo`:60）：避免配置里写成小写就静默失效。
### 五、FTP 投递与回查

#### 契约与判据

- **投递成功的判据不是返回码，而是 RNTO 之后回查远端确实存在同字节数的文件**（`ReconFtpService` 类注释:18~24；`verifyRemote`:122~128；`storage/ReconFtpProperties.java:13~18`，配置键 `recon.ftp.verify-after-upload` 默认 true）：`storeFile` 的 226 与 `rename` 的 250 只说明命令被接受，回查（SIZE，失败退回 LIST）才是「文件真的躺在 `/itp/recon` 上」的证据。成功时打「对账文件投递已回查通过」，失败抛 `IOException` 让该文件落 FAILED 等下一轮重试。
- **核对投递结果 MUST 看回查日志，NEVER 用 226 / 250 应答码或事后 LIST 当判据**（同上；`ReconFtpProperties.java:20~25`）：LIST 只能证明「还没被取走」。
- **NEVER 再把「事后 LIST 看不到 `ITP.BUS.yyyyMMdd`」当成投递失败**（`ReconFtpService` 类注释:26~32）：2026-09-11 一度如此误判（DB 记 UPLOADED、几分钟后 curl 取回 550，且四个文件原本在 70ms 内投完）。用四个变体命名的探针文件在 `172.20.215.3`（vsFTPd 3.0.3）上实测推翻：`ITP.BUS.20260910` / `ITP.BUS.20260909` / `ITP.BUSX.20260910` 都在 30~45 秒内被删除，`PROBE.BUS.20260910` 与 `ITP.EXP/PAY/DETAIL.*` 一直留着——**是 ACC 侧有个按 `ITP.BUS*` 取件的进程，取完即删**，我方投递本来就是成功的（同一轮日志里 BUS bytes=19 回查通过）。
- **`verify-after-upload` NEVER 关掉**（`ReconFtpProperties.java:16~18`）：回查是链路上唯一的正向证据，也是「BUS 文件不见了」这类误判的唯一证伪手段。
- **SIZE / LIST 两条路都问不出结果时按失败处理**（`verifyRemote` 注释:125~127）：这里的存在意义就是兜住「返回码说成功、文件其实没落地」，问不出来就不能声称投递成功。
- **远端目录固定 `/itp/recon/`**，来源是甲方《ACC与ITP之间的文件》一、对账文件（`application.properties:69`）。
- **单个文件投递成功后不判批次 SUCCESS**（`ReconFileTransferService.upload`:28~34）：允许的前置批次状态是 `GENERATING` / `UPLOADING` / `FAILED`（FAILED 是重试入口），批次是否收口由编排器在全部文件都 UPLOADED 后统一决定（`ReconOrchestrationService.uploadAll`:369~379：任一文件状态不是 UPLOADED 即抛异常）。
- **`updateUpload` 的 `remotePath` 在失败分支为 null，MUST 保留 `jdbcType=VARCHAR`**（`mapper/ReconFileMapper.xml:46`，`REMOTE_PATH` 是可空 `VARCHAR2(512)`）。

#### 决策理由

- **为什么投递串行化用 `ReentrantLock` 而不是 `synchronized`**（`ReconFtpService.awaitUploadInterval`:74~79）：本方法后面紧跟着阻塞式 FTP IO，而 JDK 21 的虚拟线程在 `synchronized` 内阻塞会 pin 住载体线程（AGENTS.md §5.2）。
- **投递间隔是保守限流、不是修复项**（`ReconFtpProperties.java:28~34`）：引入时以为对端在极短间隔的连续会话上会丢文件，后经探针实测推翻（真实原因是 ACC 取件进程）。保留是因为日终一天只跑一次、多等 2.4 秒没有代价，且对端是甲方 vsFTPd + 取件进程，少并发更稳。要调小可以，但 **MUST 先在测试环境连跑几轮，并以「回查通过」日志而不是事后 LIST 作为判据**。

### 六、配置与单副本约束

#### 契约与判据

- **`X-Recon-Token` 鉴权已整段删除，属有意为之的临时降级，上线前 MUST 恢复**（`controller/ReconInternalController.java:35~43`）：用户 2026-09-11 明确要求「删除令牌要求，不用令牌了，当前处于开发测试阶段」。本组含分片接收、状态变更、生成与投递等状态变更型端点，无鉴权状态下任何网络可达方都能改批次状态或塞入分片，与 AGENTS.md §5.2 相冲突。恢复时把 `recon.internal-token` 与请求头的 `MessageDigest.isEqual` 定长比较加回每个端点即可。配置键 `recon.internal-token=${RECON_INTERNAL_TOKEN:}` 仍留在 `application.properties:49`。
- **recon-server MUST 单副本**（`ReconOrchestrationService` 类注释:44~46）：`running` / `advancing` 两把 `AtomicBoolean` 只在进程内有效，没有数据库锁。
- **`ignore.url` 类配置 NEVER 把 `/internal/recon/**` 写进去**（`application.properties:9~13`）：该键名与语义相反——`FirstFilter.checkToken()` 里只有值等于 `/**`（`web.properties:28` 的默认值）才整体跳过 JWT 校验，其余情况列表内的 URL 反而是「必须带 `Authentication` 头的 JWT」才放行。2026-09-11 实测：写成 `/internal/recon/**` 后所有分片上送一律 `403 {"msg":"check token error"}`。
- **tracing 三行成组**（`application.properties:16~32`：`management.tracing.enabled=true` + `sampling.probability=0` + `spring.autoconfigure.exclude=...OtlpAutoConfiguration`）：打开后 web-admin `sys_job` 109 发来的 W3C `traceparent` 才能续接进 MDC，公共 `log4j2-linux.xml` 的 `%X{traceId}` 才有值（2026-09-11 实测：调度日志记着 traceId，recon-server 日志里那一列全空）。**NEVER 删除排除那行**：`sampling.probability=0` 只让本服务发起的 trace 不采样，上游带 `sampled=1` 时 span 仍会进导出队列；`web.properties` 已把 `management.otlp.tracing.endpoint` 整行注释掉，本行是第二道保险（Deployment 一注入 `MANAGEMENT_OTLP_TRACING_ENDPOINT` 就会重新激活 exporter）；Boot 3.2.6 没有 `management.tracing.export.enabled` 这个开关，把 endpoint 置空也不行。
- **三个源模块 MUST 一起打开 tracing 开关**（`application.properties:21~22`）：同一条 `traceparent` 会由 Boot 的 WebClient 观测自动带给源服务的 `/internal/recon/export`，否则链路在源侧断开。
- **`ReconExportClient` 的 `baseUrl` 固定为空串，不需要自身地址配置项**（`application.properties:110~113`）：下发地址一律取 `recon.orchestration.sources[].url` 的绝对 URL。原 `service.recon.self-url` 已删除——它只是占位 baseUrl，却让 `InternalMicroHttp` 的 INFO 日志打出 `http://127.0.0.1:9112/http://<源服务>/internal/recon/export` 这种双份地址（2026-09-11 实录）。
- **`InternalMicroHttp` 的 logger 被压到 WARN**（`application.properties:116~117`）：它会把整个请求头 Map 直接打进 INFO 日志，其中包含内部令牌明文；公共构件 `resource/micro` 不改，改这里避免令牌落进日志文件与日志采集。
- **期望清单当前是四个源**（`application.properties:84~108`）：`sources[0] gate-txn-pay` = EXP,PAY,BUS,DETAIL；`sources[1] collect-pay` = PAY,BUS；`sources[2] daily-ticket` = PAY,DETAIL；`sources[3] face-pay`（2026-09-16 新增）= PAY,BUS，口径来自 `F2F_ORDER join F2F_PAYMENT`。地址是 `kubectl get svc -n itp` 实测值（Service 端口等于 NodePort 号），**线上仍以 Deployment env 覆盖为准**。
- **`face-pay` 与 `collect-pay` 是「新表 / 旧表」并列关系、NEVER 二选一**（`application.properties:100~103`）：新单只进 `F2F_*`，切流之前的历史单仍在 collect-pay 那四张旧表里、只有那个源导得出来。去掉任何一个都会让对应时间段的账凭空少一截，而对账文件是纯文本、下游读不出异常。
- **ticket-server 的 `ReconExportMapper` / `ReconExportService` 代码保留但不在期望清单内**（`application.properties:87~89`）：甲方四类文件中没有 ticket 源的位置（过闸笔数与金额由 gate-txn-pay 出，`QRCODE_TXN_DETAIL` 不参与）。如后续甲方需要过闸明细，把 ticket 源加回清单即可，**勿删源服务代码**。
- **编排总开关**（`recon.orchestration.enabled`，`application.properties:73`；`ReconOrchestrationProperties.java:24`）：关掉后 `runDailyBatch` 与 `advance` 都直接返回，单批次人工接口仍可用。
- **期望清单为空属配置缺失**（`ReconOrchestrationProperties.java:69`）：dispatch 无事可做，MUST 在部署清单里核对。
- **`SourceExpectation.name` MUST 匹配 `[A-Za-z0-9_-]{1,32}`**（`ReconOrchestrationProperties.java:107`），落库到 `RECON_BATCH_SOURCE.SOURCE_NAME`。

#### 决策理由

- **`advance-delay-millis` 的语义已改**（`ReconOrchestrationProperties.java:27~32`）：沿用原键名与默认值（60s），语义从「定时扫描间隔」变为「同一次运行内的轮询间隔」，因为源服务的抽取是异步的、dispatch 之后必须轮询等收齐。

### 七、持久层与 mapper

#### 契约与判据

- **record 的数值分量 MUST 用装箱类型，NEVER 退回 `int` / `long`**（`model/SourceProgress.java:16~23`，同款说明在 `PartReceipt.java:17~19`、`PartTotals.java:10~12`、`ReconFileView.java:17~19`、`BatchView.java:8~9`）：MyBatis 走构造器映射时，resultMap 里 `javaType="int"` / `"long"` 经 `TypeAliasRegistry` 解析出来的是 `Integer` / `Long`，随后用 `getDeclaredConstructor(装箱类型...)` 精确查找构造器——基本类型不会自动拆箱匹配，**一旦真正查出数据行就必抛 `NoSuchMethodException`（空结果集不会触发，因此极易假通过）**。装箱后各调用点 MUST 显式判空兜底，NEVER 直接参与算术或 `>=` 比较。
- **resultMap 的 `javaType` MUST 写全限定装箱类型，NEVER 改回 `"int"` / `"long"` 别名**（`mapper/ReconSourceMapper.xml:4~7`，同款一行说明在 `ReconFileMapper.xml:4`、`ReconPartMapper.xml:4`）：写全限定名是为了让映射意图与 record 的装箱分量一一对上。
- **本目录所有 `#{}` 一律显式写 `jdbcType`，NEVER 省略**（`mapper/ReconSourceMapper.xml:22~30`，引用说明在 `ReconBatchMapper.xml:16~21`、`ReconFileMapper.xml:20~23`、`ReconPartMapper.xml:29`）：Oracle + ojdbc8 下 `#{}` 值为 null 且未写 `jdbcType` 时，MyBatis 用 `JdbcType.OTHER(1111)` 调 `setNull`，驱动报「无效的列类型: 1111」。2026-09-11 实测阻断来源声明收口（`ReconSourceService.declare()` 成功路径 `failReason` 传 null 更新 `RECON_BATCH_SOURCE`，整条 UPDATE 抛 `MyBatisSystemException`、接口 500）。取值按 `recon-server-schema.sql` 的列类型：`VARCHAR2` 列写 `VARCHAR`、`NUMBER` 列写 `NUMERIC`。
- **两处已知的 null 参数位置**：`updateDeclared` / `updateStatus` 的 `failReason` 在成功路径恒为 null（`mapper/ReconSourceMapper.xml:60, 74`，`FAIL_REASON` 是可空 `VARCHAR2(1024)`）；`updateUpload` 的 `remotePath` 在失败分支为 null（`ReconFileMapper.xml:46`）。
- **`RECON_BATCH` 侧当前没有写 `FAIL_REASON` 的语句**（`mapper/ReconBatchMapper.xml:19~20`）：该列由人工排查用，代码只在 `RECON_BATCH_SOURCE` 侧写失败原因；日后若新增写它的 UPDATE，MUST 带 `jdbcType=VARCHAR`。
- **重跑生成时覆盖既有行，`AMOUNT_TOTAL` 在 `MATCHED` 分支也要写**（`mapper/ReconFileMapper.xml:20~21`）。
- **分片表的重试计数口径**：`RETRY_COUNT` 首次落库固定写 0（`mapper/ReconPartMapper.xml:40`），状态变更即视为一次重投、`RETRY_COUNT` 同步 +1，便于定位反复失败的分片（`:50`）。
- **`selectTotals` 只统计 `STATUS='RECEIVED'` 的分片**（`mapper/ReconPartMapper.xml:20`）。

#### 陷阱

- **NEVER 在 SQL 正文写注释**（`mapper/ReconStationMapper.xml:11~13`）：全服务共用的 Druid WallFilter 默认 `commentAllow=false`，SQL 带注释会被判成注入并抛 `sql injection violation`，该语句**静默失效、编译与单测都发现不了**。说明一律写在 XML 注释里。
- **mapper XML 的注释里 NEVER 出现两个连续减号**（`mapper/ReconStationMapper.xml:15~18`）：XML 规范禁止注释文本含连续减号，MyBatis 解析该 mapper 直接失败、`sqlSessionFactory` 建不起来、**整个服务启动即挂**，而异常链最外层报的是某个无关 mapper 的注入失败，极易误判成依赖问题。这与上一条是孪生陷阱：上一条要求把「禁止在 SQL 正文写行注释」写进 XML 注释，而说明里若原样引用那两个符号就会踩本条。
- **`ReconStationMapper.xml` 当前没有 `#{}` 参数，因此不涉及「Oracle 下 null 参数必须显式写 `jdbcType`」那条**（`mapper/ReconStationMapper.xml:20~22`）；后续若加带参数的语句，MUST 照 `ReconSourceMapper.xml` 的做法逐个补。
- **`*-schema.sql` 只服务「新建库」**：本次抽取范围内的源码注释未涉及 `recon-server-schema.sql` 与迁移脚本的执行状态；四张 `RECON_*` 表的建表与回查结论以正文与 AGENTS.md 为准，**新增列 / 索引 MUST 出独立 `*-migration.sql` 并当场执行 + 回查**（AGENTS.md §8）。
### 墓碑注释清单（建议转为断言测试）

以下条目**只记「禁止做什么」**，不进上面的正文。每条给「文件:行号 + 禁止的事 + 能否断言化」。行号同样会漂，引用前 MUST 先 grep。

| # | 文件:行号 | 禁止的事 | 能否断言化 |
|---|---|---|---|
| T1 | `recon-server/src/main/java/com/chinasofti/huateng/ReconServer.java:16~21` | NEVER 加回 `@EnableScheduling`（会与 `sys_job` 形成两套互不知情的调度源，重复建批次与重复投递） | **可**：反射断言 `ReconServer.class.getAnnotation(EnableScheduling.class) == null`，并加一条「`src/main` 下 `@Scheduled` 出现次数为 0」的源码扫描断言（需排除注释行，锚定行首） |
| T2 | `recon-server/storage/ReconOrchestrationProperties.java:16~19`；`application.properties:74~75` | NEVER 加回 `recon.orchestration.dispatch-cron`，NEVER 在 K8s 注入 `RECON_DISPATCH_CRON`（无读取方，留着会让运维误以为改它能改频率） | **可**：断言 `ReconOrchestrationProperties` 无 `dispatchCron` 属性 + 断言 `application.properties` 不含 `dispatch-cron` 字样 |
| T3 | `application.properties:87~89` | NEVER 删 ticket-server 侧的 `ReconExportMapper` / `ReconExportService`（当前不在期望清单，但甲方若要过闸明细需加回） | **难**：跨模块存在性断言只能写成「`ticket-server` 下这两个类文件存在」的弱断言，且属另一个模块，建议留文档 |
| T4 | `ReconSourceMapper.java:59`；`ReconSourceMapper.xml:85~89`；`ReconOrchestrationService.dispatchSource:248~249`；`ReconSourceService.markExporting:69` | NEVER 把置 EXPORTING 换回无条件的 `updateStatus`（空结果集的源会被打回 EXPORTING、批次永不收齐） | **可**：断言 `ReconSourceMapper.xml` 的 `updateStatusIfNotTerminal` 语句含 `STATUS NOT IN ('COMPLETED', 'MISMATCH')`；行为断言需要 DB，可写成 mapper 语句文本断言 |
| T5 | `ReconOrchestrationService.dispatchDailyBatch:176~183` | 「已 SUCCESS 就直接 return」这一条 NEVER 删（同一账期第二次触发必抛 `SUCCESS -> EXPORTING`） | **可**：纯单测——mock `batchService.create` 返回 `status=SUCCESS`，断言 `dispatchDailyBatch` 不调用 `transitionIfNeeded` 且不抛异常 |
| T6 | `ReconPartService.isDuplicateKeyViolation:125~128`（ADR-D53） | NEVER 简化回 `catch (DuplicateKeyException)`（本模块开了 tracing，切面曾把异常包一层，按类型 catch 全部落空） | **可**：纯单测——构造 `new RuntimeException(new IllegalStateException(new DuplicateKeyException("x")))`，断言 `isDuplicateKeyViolation` 为 true |
| T7 | `ReconLineBackfillProperties.java:25~26`；`application.properties:58~59` | NEVER 把线路补齐改成「只在源上送为空时才补」（重新引入部署顺序依赖，且源取错的线路会被当「有值」放过）；NEVER 往 `file-types` 加 EXP / DETAIL（明细路径不解析行内容，加了不生效也不报错） | **可**：前者纯单测——同一车站码分别用「线路为空」与「线路为错值」两组键，断言两者补齐后落到同一键且度量累加；后者断言 `appliesTo("EXP")` / `appliesTo("DETAIL")` 在默认配置下为 false |
| T8 | `ReconFtpProperties.java:16~18, 25`；`ReconFtpService` 类注释:26~32 | NEVER 关掉 `verify-after-upload`；NEVER 再把「事后 LIST 看不到 `ITP.BUS`」当成我方缺陷 | **半可**：可断言默认值为 true；「不得据 LIST 判缺陷」是人的判断，只能留文档 |
| T9 | `mapper/ReconSourceMapper.xml:7`（及 `ReconFileMapper.xml:4`、`ReconPartMapper.xml:4`） | NEVER 把 resultMap 的 `javaType` 改回 `"int"` / `"long"` 别名；record 数值分量 NEVER 退回基本类型 | **可**：反射断言四个 record（`SourceProgress` / `PartReceipt` / `PartTotals` / `ReconFileView`）的数值分量类型都是 `Integer` / `Long`；再加一条「mapper XML 内不出现 `javaType="int"` / `javaType="long"`」的文本断言 |
| T10 | `mapper/ReconStationMapper.java:18~20` | NEVER 用车站码前 2 位推线路（编码巧合、非契约） | **半可**：可断言 `backfillLineSegment` 的线路值只来自 `stationMapper` 返回的映射（用只含一条映射的 mock，断言其余车站的线路段为空而不是前 2 位） |
| T11 | `ReconFileGenerationService` 类注释:52 | NEVER 在本类里硬编码段数下标（一律取自 `ReconFileTypeEnum`） | **半可**：可写「本类源码不出现字面量下标 `fields[5]` 这类模式」的弱文本断言；更可靠的是断言改 `ReconFileTypeEnum` 的段数后聚合行为随之变化 |
| T12 | `ReconOrchestrationService.runDailyBatch:101` | NEVER 把并发拒绝改成排队等待（Quartz worker 会越积越多） | **可**：纯单测——并发两次调用，断言第二次立刻抛 `IllegalStateException("上一次日终对账运行尚未结束，本次拒绝")` 而不是阻塞 |

> 说明：T1 / T2 / T5 / T6 / T7 / T12 是**纯内存单测即可覆盖**的，优先落这六条；本模块**当前无任何单元测试**（正文「未闭合项」已记），这六条可以作为第一批。

## 附：recon-server 注释知识迁移（2026-09-16，阶段二·完整）

> **本节是「注释 → 文档」的收口版**：阶段一（上一节）按四类做了摘录，本节把**即将从代码里删除的叙述型注释**全部落成可检索条目。此后 `recon-server/src/main` 只保留标准 Javadoc 与 7 条一行式护栏（清单见 §十三），**这些知识的唯一权威副本就是本节**，代码里查不到了。
>
> **行号会漂**。每条都带 `路径:行号` + 一个**可 grep 的原文短语**；引用前 **MUST** 先 grep 那个短语，**NEVER** 直接按行号跳。
>
> 抽取范围（2026-09-16 实测）：`recon-server/src/main` 共 40 个文件 —— Java 32 个（2534 行）、mapper XML 5 个（344 行）、`application.properties` 1 个（130 行）、SQL 2 个（91 行）。注释行合计 **829 行**（Java 690 / XML 75 / properties 64 / **SQL 0**）。两个 SQL 文件 `sql/recon-server-schema.sql` 与 `sql/recon-server-schema-migration.sql` **一行注释都没有、`COMMENT ON` 0 条**，因此列语义在库里查不到，只能靠本文正文「数据表」节 —— **NEVER 以为「库里有表」列语义也跟着在库里**。

### 一、调度形态：本模块最容易被读反的一处

- **`recon-server` 没有任何 `@Scheduled`，启动类也没有 `@EnableScheduling` —— 但调度本身是开着的。** 判据两条，MUST 同时看：
  - `recon-server/src/main/java/com/chinasofti/huateng/ReconServer.java:16`（grep `NEVER 加回`）：用户 2026-09-11 明确要求「不使用 EnableScheduling，改用 web-admin 调用，改为每日执行一次，由 web-admin 控制频率」，因此本模块**不显式声明**该注解，`src/main` 下也没有任何 `@Scheduled`。
  - 同文件 `:24`（grep `但调度本身是开着的`）：`resource/micro/web` 的 `WebAutoConfig` **类上带 `@EnableScheduling`**，并经 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册成自动配置 —— 凡依赖 micro web 构件的模块**调度一律开启**；公共构件里那 3 个 `@Scheduled`（`SqlConfiguration.resetAllDruidStatData` 与 `ResetMetersJob` 的两个 Prometheus 指标重置 / 打印）在本模块**照样在跑**。
  - **因此「本模块一个 `@Scheduled` 都没有、启动类也没有 `@EnableScheduling`」只在「本模块源码」这个口径下成立。NEVER 据「启动类没有这个注解」推断调度未启用，也 NEVER 把它写成可断言项** —— 能断言的只有「本模块源码不含这两个字样」，断言不到「容器里没有调度器」。
  - 加回 `@EnableScheduling` 的后果：`sys_job` 与本模块形成**两套互不知情的调度源**，改 cron 时只改一处就出现「以为改了、实际另一套仍按老频率跑」，并造成**重复建批次与重复投递**（与 `docs/architecture/web-server.md` §7.1 末条同一约束）。

### 二、唯一入口与同步编排

- 唯一日常入口 **`POST /internal/recon/daily/run`** → `ReconOrchestrationService.runDailyBatch()`（`recon-server/src/main/java/com/chinasofti/huateng/recon/controller/ReconInternalController.java:104`，grep `/daily/run`；服务侧 `recon/service/ReconOrchestrationService.java:106`，grep `上一次日终对账运行尚未结束`）。
- 编排是**同步**的：`dispatchDailyBatch()` 建批次 + 登记期望 + 逐源下发（`ReconOrchestrationService.java:189`），随后 `while(true)` 轮询 `advanceBatch(batchId)` 直到 `SUCCESS`。轮询间隔 = `recon.orchestration.advance-delay-millis`（**60s**，`recon/storage/ReconOrchestrationProperties.java:34`），下限被 `Math.max(1000L, ...)` 钳到 1s（`ReconOrchestrationService.java:117`）。
- **为什么必须在这里轮询、而不是「下发完就返回」**：源服务的抽取是**异步**的（受理即返回、之后才回推分片），不等收齐就没有任何东西会推进批次 —— 本模块已经没有 `@Scheduled` 兜底了（`ReconOrchestrationService.java:93`，grep `本模块已没有`）。
- **为什么同步跑完再返回**：让 `SYS_JOB_LOG` 反映真实成败 —— Quartz 侧「不抛异常一律记成功」（`docs/architecture/web-server.md` §7.1），因此失败或超时都抛 `IllegalStateException`，Controller 转成非 `0000` 的 retCode（`ReconInternalController.java:112~116`，grep `日终对账运行失败`）。返回 `CommonResult` 而不是 `BatchView`，是因为调用方只需要「成/败 + 原因」、判据与 web-admin 其它任务一致（`retCode` 是否 `0000`），批次明细另走 `GET /internal/recon/batches/{batchId}`。
- 总超时 `recon.orchestration.run-timeout-millis` = **240000ms（4 分钟）**（`ReconOrchestrationProperties.java:47`），**MUST 小于 `ReconClient.getResponseTimeout()` 的 5 分钟**（同文件 `:43`，grep `MUST 小于`）。超过客户端超时会变成「客户端先超时报错、服务端还在跑」，`SYS_JOB_LOG` 记的失败原因**就没有意义了**；要放宽 **MUST 两处一起改**。超时抛错时批次留**非终态**，下一次运行从 `FAILED` / `PARTIAL` 重入、**不丢数据**（`ReconOrchestrationService.java:128~130`，grep `批次留在非终态`）。实测一整轮（4 个文件、6 行 PAY）约 60 秒。
- **本类绝不能加 `@Transactional`**（`ReconOrchestrationService.java:40`，grep `绝不能加`）：内部既有 RPC（下发抽取）又有大文件 IO（合并、FTP），被事务包住会让行级锁持有时长等于对端响应时长 + 文件大小，并在 Druid 回收连接后把整个事务连同状态记录一起丢弃。每一步都靠单条 SQL 自动提交落状态。
- 用 `Thread.sleep` 而不是 `synchronized` + `wait`（`ReconOrchestrationService.java:147`，grep `不 pin`）：全服务默认虚拟线程（AGENTS.md §5.2），`Thread.sleep` 在虚拟线程上让出载体线程、不 pin。

### 三、单副本约束（进程内锁，没有数据库锁）

- `runDailyBatch` 用 `running`（`AtomicBoolean`）拒绝并发：上一次还在跑，第二次**直接抛异常拒绝、NEVER 改成排队等待** —— 排队会让 Quartz worker 越积越多（`ReconOrchestrationService.java:100~101`，grep `NEVER 改成排队等待`）。
- `advance()`（扫全部非终态批次、供人工干预）另有一把 `advancing`（`ReconOrchestrationService.java:265`，grep `进程内串行化`）：上一轮没结束就跳过本轮。
- **两把锁都只在进程内有效、库里没有任何锁行或 `SELECT FOR UPDATE`**，因此 **`recon-server` 与 `web-admin` 都 MUST 单副本**（`ReconOrchestrationService.java:44~46`，grep `MUST 单副本`；web-admin 侧同一约束见 AGENTS.md §8 Quartz 那条：内存 JobStore 下每个副本各跑一份）。两副本同时跑同一账期的后果不是报错而是**重复投递**：批次幂等只挡住「同一批次的状态流转」，挡不住两个进程各自把文件 STOR 到 `/itp/recon` 一次。
- 共享存储（ReadWriteMany PVC）**至今未落地**（正文「已知坑 A 环境类」已记）：分片落在 `recon.storage.root`（默认 `/home/javaapp/app/recon`）的容器本地盘，多副本时第二个副本看不到第一个副本收到的分片，`generate` 会直接报缺片。这是「MUST 单副本」的第二个独立理由。

### 四、触发方与频率（改频率只有一个地方可改）

- 触发方是 web-admin 的 `sys_job` **job_id 109「日终对账」**：`reconQuartzTask.runDailyBatch()`、cron **`0 30 2 * * ?`**、走 `service.recon.url`（`recon-server/src/main/resources/application.properties:78`，grep `触发时机与频率由 web-admin`）。
- **要改频率只能改这条 cron。`recon.orchestration.dispatch-cron` 与 env `RECON_DISPATCH_CRON` 已删除、NEVER 加回**（`application.properties:79`，grep `NEVER 加回`；`ReconOrchestrationProperties.java:16~19`，grep `本类不再有 cron 配置`）。那两个键现在**没有任何读取方**，留着只会让运维误以为改它能改调度频率。
- `recon.orchestration.enabled`（默认 true，`ReconOrchestrationProperties.java:24`）是编排总开关：关掉后 `runDailyBatch` 抛「对账编排总开关已关闭」、`advance` 直接返回，**单批次人工接口仍可用**。

### 五、三个修复过的缺陷：逐条「缺这一条会怎样」

这三条只在**同步链路**下暴露，`@Scheduled` 自驱时代碰不到。**NEVER 回退**。

1. **1.0.9：把「置 `EXPORTING`」挪到下发 RPC 之前，并改用 `updateStatusIfNotTerminal`**
   - 位置：`ReconOrchestrationService.dispatchSource:248~250`（grep `MUST 先置 EXPORTING 再发 HTTP`）；`recon/service/ReconSourceService.java:64~70`（grep `NEVER 换回无条件的 updateStatus`）；`recon/mapper/ReconSourceMapper.java:50~60`（grep `为什么必须条件更新`）；SQL 守卫在 `resources/mapper/ReconSourceMapper.xml:85~89`（grep `置 EXPORTING 专用`），谓词是 `STATUS NOT IN ('COMPLETED', 'MISMATCH')`。
   - **缺这一条会怎样**：源侧抽取跑在自己的线程池里，**空结果集（如 gate-txn-pay 的 EXP 0 行）可能在 200ms 内就回调 `complete` 声明 `COMPLETED`**，而编排线程此时才写 `EXPORTING`，于是把 `COMPLETED` **覆盖回中间态** —— 该 `(来源, 文件类型)` 从此永远停在 `EXPORTING`、批次永不收齐、整批 240s 超时停在 `PARTIAL`。2026-09-11 实测复现（`batchId=RECON20260909`，gate-txn-pay/EXP 已 `declareComplete` 却仍是 `EXPORTING`）。
2. **1.0.10：把 `PARTIAL -> EXPORTING` 补进状态白名单**
   - 位置：`recon/service/ReconBatchService.java:68~71`（grep `PARTIAL -&gt; EXPORTING 也必须在白名单里`，实际源码里是 `PARTIAL -> EXPORTING`）。
   - **缺这一条会怎样**：`runDailyBatch()` 每天（或人工重跑）都先 `dispatchDailyBatch()`，而它对已存在批次做 `transitionIfNeeded(EXPORTING)`；批次只要停在 `PARTIAL`（部分来源收齐），重入就直接抛「非法的对账批次状态变更: PARTIAL -> EXPORTING」、接口返 9999，**那个账期再也补不回来**（cron 每天算出新 batchId，昨天那个批次再没人下发）。2026-09-11 实测（`RECON20260909` 停在 `PARTIAL`）。
   - 同处还有一条同型的：**`FAILED -> ALL_SOURCE_COMPLETED` 也必须在白名单里**（`ReconBatchService.java:61~66`，grep `FAILED -&gt; ALL_SOURCE_COMPLETED 必须在白名单里`）。批次在「来源已全部收齐、但生成或投递失败」时落 `FAILED`，下一轮 `advanceBatch` 走的正是 `transitionIfNeeded(ALL_SOURCE_COMPLETED)` 再重新生成；少了它，`FAILED` 批次只能靠 `dispatchDailyBatch` 的 `FAILED -> EXPORTING` 复活，而生产 cron 一天只跑一次且 batchId 按账期变，**昨天失败的批次今天不会再被下发**，于是永久卡 `FAILED`、每轮只刷「非法的对账批次状态变更」错误日志。2026-09-11 实测复现（`RECON20260907`）。
3. **1.0.12：`dispatchDailyBatch` 加「批次已 `SUCCESS` 就直接 return」的短路**
   - 位置：`ReconOrchestrationService.dispatchDailyBatch:176~183`（grep `已 SUCCESS 就直接返回`）与实现处 `:202~205`（grep `对账批次本账期已收口`）。
   - **缺这一条会怎样**：`create` 用的是 `insertIfAbsent`、对已存在批次不改任何列，但紧随其后的 `transitionIfNeeded(EXPORTING)` 会走 `ReconBatchService.transition`，而白名单里 **`SUCCESS` 是终态、一律拒绝流出**，于是同一账期第二次触发必抛「非法的对账批次状态变更: SUCCESS -> EXPORTING」、接口返 9999。**cron 每天 batchId 不同碰不到，前台「执行一次」当天点第二下必踩**；1.0.11 的 Pod 上已实测到该栈。
   - 连带一条同源修复：`dispatchDailyBatch` 的 catch 分支**现在 MUST 抛 `IllegalStateException`**，原先是「记日志后 return」（`ReconOrchestrationService.java:186~187`，grep `原先是记日志后 return`）—— 不抛的话 `runDailyBatch()` 会拿着一个**不存在的批次**去轮询到超时。

### 六、状态机与收齐判定

- 批次状态白名单（`ReconBatchService.allowed`，`ReconBatchService.java:58` 起）：`CREATED -> EXPORTING -> PARTIAL -> ALL_SOURCE_COMPLETED -> GENERATING -> UPLOADING -> SUCCESS`；`FAILED -> EXPORTING | GENERATING | UPLOADING`（三个补偿入口）；外加上面那两条 `FAILED -> ALL_SOURCE_COMPLETED`、`PARTIAL -> EXPORTING`。**`SUCCESS` 是终态、NEVER 从它流出，重跑 MUST 换批次号**（`recon/model/ReconBatchStatus.java:12~13`，grep `NEVER 从 SUCCESS 再流出`）。
- `transitionIfNeeded` 是**幂等**的：目标状态与当前相同就原样返回、不抛异常（`ReconBatchService.java:29~33`，grep `幂等的状态推进`）—— 编排每轮都会重算目标状态，用 `transition` 会每轮刷「非法流转」。
- 收齐判定**只认三项总账全等**：落库分片数 / 记录数 / 金额合计逐一等于源声明的 `totalParts / totalRecords / totalAmount`，任一不等即置 `MISMATCH`、**NEVER 放行**（`ReconSourceService.java:19~24`，grep `收齐判定只认三项总账全等`）—— 分片少一片，最终文件就少几十万行，而纯文本文件本身看不出缺失。`MISMATCH` 是**不可自动放行**的状态：数据已不一致，重试同一窗口只会重复不一致，MUST 人工介入（`recon/model/ReconSourceStatus.java:20~23`）；`advanceBatch` 一旦看到任一来源 `MISMATCH` 就停止自动推进并把批次置 `FAILED`（`ReconOrchestrationService.java:298~301`，grep `MUST 人工介入 batchId`）。
- `allCompleted` 除了「每行都是 COMPLETED」还要求**行数等于期望条数**（`ReconSourceService.java:139~144`，grep `否则 dispatch 那一轮若中途异常`）—— 否则 dispatch 那轮若中途异常少登记了几行，剩下几行全 `COMPLETED` 也会被误判成收齐。
- 期望清单是收齐判定的源头：`sources[].fileTypes` 决定 `RECON_BATCH_SOURCE` 登记哪些 `(来源, 文件类型)` 行，进而决定「全部 COMPLETED」的判据与最终产出哪几个文件。**新增 / 下线一个源 MUST 改配置，NEVER 在代码里硬编码来源名**（`ReconOrchestrationProperties.java:11~14`，grep `NEVER 在代码里硬编码来源名`）。
- 重试上限 `recon.orchestration.max-retry=3`；`retryCount` 是装箱 `Integer`（MyBatis 构造器映射要求），**MUST 先判空兜 0 再与 `maxRetry` 比** —— 直接 `>=` 会在列为 NULL 时抛 NPE，而 NPE 会被 `advanceBatch` 的 catch 吞成「批次推进失败」、掩盖真实原因（`ReconOrchestrationService.java:324~326`，grep `此处 MUST 先判空`）。

### 七、四类文件的段数、账期口径与生成路径

- **段数固定**（全部取自 `model` 的 `ReconFileTypeEnum`，**NEVER 在生成类里硬编码下标** —— `recon/service/ReconFileGenerationService.java:52`，grep `NEVER 在本类里硬编码下标`）：
  - **EXP 13 段**（单边交易明细，明细类）、**DETAIL 7 段**（虚拟电子多日计次票，明细类）：走 **1MB 缓冲的流式字节拼接**，全程不解析行内容，内存占用与文件大小无关；行数与金额直接取分片回执上的声明值累加（`ReconFileGenerationService.java:135~138`，grep `不解析行内容`）。
  - **PAY 21 段 = 5 键 + 16 度量**、**BUS 4 段 = 1 键 + 3 度量**（汇总类）：流式逐行读 + `TreeMap` 二次聚合，键是下标 `[0, keyFieldCount)`、度量是 `[keyFieldCount, keyFieldCount + metricFieldCount)`，逐列累加进 `long[metricFieldCount]`。**行字段数不足即抛异常，NEVER 静默补零放过** —— 错位一段就是整份错账（`ReconFileGenerationService.java:171~178`，grep `NEVER 静默补零放过`）。
  - **明细文件绝不能走汇总路径**：几千万行的键全量入 `TreeMap` 会直接把堆打满；汇总行基数只有几百到几千，常驻内存安全（`ReconFileGenerationService.java:53~54` 与 `:180~181`，grep `明细文件绝不能`）。
- **来源清单取自 `RECON_BATCH_SOURCE` 中该文件类型 `STATUS='COMPLETED'` 的行，不再读 `recon.sources`**（`ReconFileGenerationService.java:57~58`，grep `不再读`）—— 各源产出哪些文件类型完全由期望清单决定。`application.properties:50` 那个 `recon.sources=gate-txn-pay,ticket,collect-pay,daily-ticket` 是**遗留键**（仍含已移出的 `ticket`），**判断「谁出哪个文件」MUST 看 `recon.orchestration.sources[*]`，NEVER 看 `recon.sources`**。
- **账期与窗口**（`ReconOrchestrationService.dispatchDailyBatch:162~173`，grep `businessDate = 今天 -`）：
  ```
  businessDate = 今天 - windowOffsetDays   默认 2 ⇒ T-2
  windowStart  = businessDate 当天 + windowStartTime      默认 020000
  windowEnd    = businessDate + 1 天 + windowStartTime
  文件名后缀    = businessDate               即 T-2 日
  batchId      = "RECON" + businessDate     形如 RECON20260910
  ```
  甲方例子作为自校验依据：8 月 20 号 2 点生成，文件名 `ITP.EXP.20190818`，统计区间 T-2 日 2 点 ~ T-1 日 2 点；代入公式得后缀 `20190818`、窗口 `[20190818 020000, 20190819 020000)`，与甲方一致。**`window-offset-days` NEVER 改回 1** —— 改 1 会让文件名后缀与统计区间同时前移一天、与甲方对不上（`ReconOrchestrationProperties.java:50~54`，grep `NEVER 改回 1`）。
- 生成后的**校验口径按明细 / 汇总分开**（`ReconFileGenerationService.verify:341~355`，grep `NEVER 与 declaredAmount 严格比对`）：
  - 明细（EXP / DETAIL）：记录数与金额都 **MUST 与各 COMPLETED 来源声明之和严格相等**，不等即删除临时文件并抛异常。
  - 汇总（PAY / BUS）的 `amountTotal` 是「该文件所有度量列之和」，只是**跨环节比对的校验和、不是业务金额**：PAY 的 16 个度量里 8 列是笔数、8 列是金额（0 基下标 6/8/10/12/14/16/18/20，即甲方第 7/9/11/13/15/17/19/21 段），相加没有业务含义；BUS 的 3 个度量恰好全是金额，但为口径统一同样按「所有度量之和」计。因此汇总文件 **NEVER 与 `declaredAmount` 严格比对**（不同量纲，强行相等会把正常批次全判失败），只做健全性检查：输出行数 > 0、校验和非负；**行数同样不可比**（多源可能落在同一聚合键上，跨源合并后输出行数必然 ≤ 各源声明之和，只在大于时告警提示复核源端聚合口径）。
- 分片连续性：取某来源该文件类型的已接收分片时**校验分片号从 0 起连续**，`partNo` 是装箱 `Integer`、先判空兜 -1 再比，NULL 直接判「序号不连续」抛异常而不是抛 NPE（`ReconFileGenerationService.java:396~400`，grep `先判空兜成 -1`）。

### 八、FTP 投递：唯一判据是回查，不是应答码

- **投递成功的判据不是返回码，而是 RNTO 之后回查远端确实存在同字节数的文件**（`recon/service/ReconFtpService.java:21~24`，grep `投递成功的判据不是返回码`）：`storeFile` 的 **226** 与 `rename` 的 **250** 只说明命令被接受；回查（先 `SIZE`，BINARY 模式下回 `213 <bytes>`；拿不到再退回 `LIST` 解析目录项）才是「文件真的躺在 `/itp/recon` 上」的证据。成功时打 **「对账文件投递已回查通过」**，失败抛 `IOException` 让该文件落 `FAILED` 等下一轮重试。两条路都问不出结果时**按失败处理**（`ReconFtpService.java:122~127`，grep `问不出来就不能声称投递成功`）。
- 开关 `recon.ftp.verify-after-upload` 默认 **true、NEVER 关掉**（`recon/storage/ReconFtpProperties.java:16~18`，grep `NEVER 关掉`）。它同时是「BUS 文件不见了」这类误判的**唯一证伪手段**。
- **ACC 侧会在约 30~45 秒内自行取走并删除 `ITP.BUS*` 文件 —— NEVER 再把「事后 LIST 看不到 `ITP.BUS.yyyyMMdd`」当成我方缺陷**（`ReconFtpService.java:26~32`，grep `NEVER 再把`；`ReconFtpProperties.java:20~25`，grep `取完即删`）。2026-09-11 曾据「DB 记 `UPLOADED`、几分钟后 curl 取回 550，且四个文件原本在 70ms 内投完」判定投递静默失败；用四个变体命名的探针在 `172.20.215.3`（vsFTPd 3.0.3）上实测推翻：`ITP.BUS.20260910` / `ITP.BUS.20260909` / `ITP.BUSX.20260910` 都在 30~45 秒内被删除，而 `PROBE.BUS.20260910` 与 `ITP.EXP/PAY/DETAIL.*` 一直留着 —— 是 ACC 侧有个**按 `ITP.BUS*` 取件的进程**，取完即删；同一轮日志里 BUS `bytes=19` 回查通过，我方投递本来就是成功的。**核对投递结果 MUST 看回查日志，LIST 只能证明「还没被取走」，NEVER 用 226 / 250 应答码或事后 LIST 当判据。**
- 远端目录固定 `/itp/recon`（`recon.ftp.remote-root`，`application.properties:73~74`，grep `固定路径`）—— 来源是甲方《ACC与ITP之间的文件》§一「对账文件」。`recon.ftp.enabled` 默认 **false**，线上靠 env 打开。
- 串行化 + 相邻投递间隔用 `ReentrantLock` 而不是 `synchronized`（`ReconFtpService.java:74~78`，grep `而不是`）：后面紧跟阻塞式 FTP IO，JDK 21 虚拟线程在 `synchronized` 内阻塞会 pin 载体线程。间隔项 **不是修复项、只是保守限流**（`ReconFtpProperties.java:28~34`，grep `这不是修复项`）：引入时以为对端在极短间隔连续会话上会丢文件，已被探针实测推翻；保留是因为日终一天只跑一次、多等 2.4 秒没代价。要调小 MUST 先在测试环境连跑几轮，并以「回查通过」日志而不是事后 LIST 作判据。
- 文件投递成功后**不判 `SUCCESS`**：批次是否收口由编排器在全部文件都 `UPLOADED` 后统一决定，单个文件只更新自己的状态与远端路径；允许的前置批次状态是 `GENERATING / UPLOADING / FAILED`（`FAILED` 是重试入口）（`recon/service/ReconFileTransferService.java:28~33`，grep `投递成功后**不判 SUCCESS**` 的关键词 `不判`）。

### 九、鉴权现状：整段删除、当前无鉴权

- **`/internal/recon/**` 当前【无鉴权】**：原先每个端点上的 `X-Recon-Token` 定长比较（`MessageDigest.isEqual`）已按用户 2026-09-11 的明确要求（原话「删除令牌要求，不用令牌了，当前处于开发测试阶段」）**整段删除**，`ReconInternalController` 里连 `checkToken` 方法都不存在了（`ReconInternalController.java:38~43`，grep `本组接口当前无鉴权`；配置侧 `application.properties:13~17`，grep `当前【无鉴权】`）。
- 这组端点里含**分片接收、状态变更、生成与投递**等状态变更型接口，无鉴权状态下任何网络可达方都能改批次状态或塞入分片 —— 与 AGENTS.md §5.2「新增状态变更型接口 MUST 有鉴权」**直接冲突，属有意为之的临时降级，上线前 MUST 恢复**。恢复方式：把 `recon.internal-token` 与请求头的定长比较加回每个端点。`recon.internal-token` / env `RECON_INTERNAL_TOKEN` 目前**只留配置键、无读取方**（`application.properties:53`）。
- **NEVER 回退成「鉴权靠 `ReconInternalController.checkToken()` 的 `X-Recon-Token` 定长比较」**（那段代码已删）；也 **NEVER 把 `/internal/recon/**` 写进 `other.web.skipCheckTokenUrls`** —— 该键名与语义相反：`FirstFilter.checkToken()` 里只有值等于 `/**`（`web.properties:28` 的默认值）才整体跳过 JWT 校验，其余情况**列表内的 URL 反而是「必须带 `Authentication` 头的 JWT」才放行**；2026-09-11 实测写成 `/internal/recon/**` 后所有分片上送一律 `403 {"msg":"check token error"}`。本模块现值是 `other.web.skipCheckTokenUrls=/**`（`application.properties:18`）。
- 另一条与鉴权配套的护栏：`recon/controller/ReconInternalExceptionHandler.java:15~31`（grep `必须存在`）—— 公共构件 `GlobalControllerExceptionHandler` 的兜底是 `@ExceptionHandler(Exception.class) @ResponseStatus(HttpStatus.OK)`，即**任何未被更具体处理器捕获的异常都返回 HTTP 200 + 一个 UUID `retCode`**，而 `ReconClient` 只按 HTTP 状态码判成败。于是「令牌校验失败」「分片落盘 IO 失败」「哈希不一致拒收」都会被上游当成**接收成功**、源服务继续删本地分片。本类以 `Ordered.HIGHEST_PRECEDENCE` 抢在公共 advice 之前、用 `assignableTypes` 限定在 `ReconInternalController`，把状态码还原成真实语义。**NEVER 删这个类**。

### 十、线路段补齐（2026-09-16 起生效）

- 线路段由 **recon-server 在聚合完成、写文件之前统一补齐**，四个源服务不再各自 `LEFT JOIN STATION_INFO`（`recon/mapper/ReconStationMapper.java:11~16`，grep `为什么这张表在 recon-server 里`）。判据：`STATION_INFO` 属车站 / 参数域、owner 不是本模块，直连它是**有意破例**，理由是它把原先散在 gate-txn-pay / collect-pay / face-pay / ticket 四处的同样破例收敛成一处 —— 「线路怎么取」曾有四份副本、四份 join 列名、四次维表结构变更暴露面；而**线路是车站的函数**（一个车站码只对应一条线路），完全可以在聚合后由消费端一次补齐。
- **NEVER 用车站码前 2 位推线路**（`ReconStationMapper.java:18~20`，grep `NEVER 用车站码前 2 位`）：实测前 2 位恰好等于 `LINE_CODE`，但那是**编码巧合、不是契约**，甲方改编码规则不会通知我方，猜错等于把汇总账挂到错误线路上。
- **查不到的车站 MUST 留空、NEVER 丢行**（同处 `:22~24`）：维表缺记录时线路段为空、该组单独成行，账仍在；与原先各源用 `LEFT JOIN`（而非 `INNER JOIN`）的取舍一致 —— 内连接会把该组账整组抹掉，那是**静默漏账**，比线路段为空严重得多。补不到的车站按不同车站码去重计数后**告警一次**，便于回头补参数（`ReconFileGenerationService.java:236~237`）。
- **维表查询失败时抛异常、NEVER 降级成空 Map**（`ReconFileGenerationService.java:282~286`，grep `NEVER 降级成空 Map`）：降级的后果是「整份 PAY 文件线路段全空」，而这份文件仍会被判定生成成功并投递给 ACC —— 那是静默错账。查不出维表就让本次生成失败、批次留非终态等重入。
- **补完必须按新键重排、并对撞上的键逐列累加**（`ReconFileGenerationService.java:224~231`，grep `为什么必须重排`）：`TreeMap` 按键升序输出、键的第 2 段就是线路，只就地替换字符串会让行序变成「按空线路段排」的顺序，与现行文件（日期 / 线路 / 车站 / 设备 / 支付方式 升序）不一致；因此换一个新 `TreeMap` 用补好的新键重插，行序与改造前逐字节一致。补齐可能让两个原本不同的键变成同一个（典型情形：「已改的源留空线路」与「未改的源自带线路」同账期上送同一车站），那本来就该是一组，**MUST 逐列累加、NEVER 覆盖**（覆盖等于丢掉一个源的账）。
- **覆盖是无条件的，这正是它能安全上线的原因**（`recon/storage/ReconLineBackfillProperties.java:21~26`，grep `覆盖是无条件的`）：不判断源上送的线路段是空还是有值，一律按车站码重算。对「还没改、仍自带线路」的旧源，覆盖进去的是同一个值、文件逐字节不变；对「已改、线路留空」的新源才是真正填上。于是**四个源与 recon-server 的部署顺序完全自由**，不存在「必须同批上线」的窗口。**NEVER 改成「只在源上送为空时才补」** —— 那样重新引入顺序依赖，且某个源把线路取错时错值会被当「有值」原样放过。
- **为什么下标是配置项而不是常量**（同文件 `:14~19`）：生成类的规矩是「段数一律取自 `ReconFileTypeEnum`」，而 `ReconFileTypeEnum` 只给「键几段、度量几段」、给不出「第几段是线路」；写成配置是为了**出问题时一键关闭（`enabled=false`）退回「原样写出源上送的线路段」，不必回滚镜像**。现值：`recon.line-backfill.enabled=true`、`file-types=PAY`、`line-field-index=1`、`station-field-index=2`（`application.properties:64~67`）。
- **NEVER 往 `file-types` 里加 EXP 或 DETAIL**（`ReconLineBackfillProperties.java:34~39`）：那两类走明细路径、字节流式拼接、全程不解析行内容，**加进去既不生效也不报错**。BUS 只有 1 段键（日期），也没有线路 / 车站段。类型名大小写不敏感（`appliesTo`，同文件 `:60`），避免配置写成小写就静默失效。

### 十一、持久层：MyBatis 映射的三条硬约束

1. **record 的数值分量 MUST 用装箱类型，NEVER 退回 `int` / `long`**（`recon/model/SourceProgress.java:16~23`，grep `MyBatis 走构造器映射`）。MyBatis 走构造器映射时，resultMap 里 `javaType="int"` / `"long"` 经 `TypeAliasRegistry` 解析出来的是 `Integer` / `Long`，随后用 `getDeclaredConstructor(装箱类型...)` **精确查找**构造器 —— 基本类型不会自动拆箱匹配，**一旦真正查出数据行就必抛 `NoSuchMethodException`；空结果集不触发，因此极易假通过**。四个 record 受此约束：`SourceProgress` / `PartReceipt`（`model/PartReceipt.java:17~19`）/ `PartTotals`（`model/PartTotals.java:10~12`）/ `ReconFileView`（`model/ReconFileView.java:17~19`）；`BatchView` **没有任何基本类型分量**、不受影响，但新增数值分量时同样 MUST 用装箱（`model/BatchView.java:8~9`）。装箱后各调用点 **MUST 显式判空兜底、NEVER 直接参与算术或 `>=` 比较**。
   - mapper XML 侧配套：`javaType` **MUST 写全限定装箱类型**（`resources/mapper/ReconSourceMapper.xml:4~7`，grep `NEVER 改回`；同形注释在 `ReconFileMapper.xml:4`、`ReconPartMapper.xml:4`），写全限定名是为了让映射意图与 record 的装箱分量一一对上。
   - 还有一条同源的比较陷阱：`PartTotals` 的分量是装箱类型，三项总账比对**MUST 先判空拆成基本类型再比** —— 两侧都是装箱时 `!=` 退化成**引用比较**，超过 `Integer` 缓存范围（-128~127）的相等值会被判成不等。**NEVER 让两个装箱类型直接用 `==` / `!=` 比较**（`ReconSourceService.java:167~173`，grep `NEVER 让两个装箱类型`）。
2. **Oracle + ojdbc8 下所有 `#{}` MUST 显式写 `jdbcType`，NEVER 省略**（`ReconSourceMapper.xml:22~30`，grep `无效的列类型`）。参数值为 null 且未写 `jdbcType` 时 MyBatis 用 `JdbcType.OTHER(1111)` 调 `setNull`，驱动报「无效的列类型: 1111」。2026-09-11 实测阻断来源声明收口：`ReconSourceService.declare()` 成功路径的 `failReason` 传 null 去更新 `RECON_BATCH_SOURCE`，整条 UPDATE 抛 `MyBatisSystemException`、接口 500。**MySQL 能容忍 `OTHER`，因此这类缺陷只在 Oracle 暴露，编译与单测都发现不了。** 取值按 `sql/recon-server-schema.sql` 的列类型：`VARCHAR2` 列写 `VARCHAR`、`NUMBER` 列写 `NUMERIC`（与 `gate-txn-pay-server/ReconExportMapper.xml` 的 `lastId` 同风格）。逐处提醒：`ReconSourceMapper.xml:60`（`declare` 成功路径 `failReason` 恒 null）、`:74`（下发成功时 `failReason` 为 null）、`ReconFileMapper.xml:46`（上传失败分支 `remotePath` 为 null，`REMOTE_PATH` 是可空 `VARCHAR2(512)`）、`ReconBatchMapper.xml:16~21`（本表当前没有写 `FAIL_REASON` 的语句，**日后新增 UPDATE MUST 带 `jdbcType=VARCHAR`**）。
3. **`NEVER` 在 SQL 正文写注释**（`--` 或 `/* */`）—— 全服务共用的 Druid WallFilter 默认 `commentAllow=false`，SQL 带注释会被判成注入并抛 `sql injection violation`，该语句**静默失效**、编译与单测都发现不了；说明一律写在 XML 注释里。而 **XML 注释里 NEVER 出现两个连续减号**，否则 MyBatis 解析该 mapper 直接失败 ⇒ `sqlSessionFactory` 建不起来 ⇒ **整个服务启动即挂**，且异常链最外层报的是某个无关 mapper 的注入失败、极易误判成依赖问题。这两条是**孪生陷阱**（原文成段写在 `resources/mapper/ReconStationMapper.xml:9~22`，grep `三条约定`）。
- 其余持久层细节：`selectUnfinished` 取 `STATUS <> 'SUCCESS'` 的批次、按创建时间升序、最多 20 条，**`FAILED` 也在结果里**（它是补偿入口不是终态，`recon/mapper/ReconBatchMapper.java:18~21`；SQL 侧 `ReconBatchMapper.xml:37` 的 `ROWNUM` 限流放在外层，保证先排序再截断）。`selectTotals` 用 `NVL` + `COUNT` 保证零行时返回 `(0,0,0)` 而不是 null（`ReconPartMapper.java:42~45`、`ReconPartMapper.xml:71`）。`RECON_BATCH_PART` 的 `RETRY_COUNT` 首次落库固定写 0、之后由 `updateStatus` 递增，**状态变更即视为一次重投**（`ReconPartMapper.xml:40`、`:50`）。`FAIL_REASON` 列长 1024、超长截断，避免 `ORA-12899` 让整条声明失败（`ReconSourceService.java:194`）。
- 车站维表查询（`ReconStationMapper.xml:25~39`）四条设计判据：**不加 WHERE**（PAY 里出现的车站码来自四个源、事前不可枚举，逐个查等于把批处理退化成 N+1；表基数几百行、全量拉回内存做 Map 更便宜）；**带 `LINE_CODE IS NOT NULL`**（线路为空的行对补齐没用，真正「线路为空」的结果由「查不到即留空」给出）；**带 `DISTINCT`**（防御性，拦不住「同一车站码对应两个线路」那种真冲突，那种情况调用方会保留后读到的一条，**MUST 去参数域修数据、NEVER 在 SQL 里挑一个**）；**每生成一个 PAY 文件查一次、不做进程级缓存**（日终一天一轮，缓存省下的一次查询毫无意义，却会引入「参数改了但 recon-server 没重启、线路仍按旧映射填」这种只在跨天暴露的偏差）。`STATION_INFO` 只有 `STATION_CODE` / `LINE_CODE` / `STATION_NAME` / `STATION_EN_NAME` 四列（2026-09-16 按 `USER_TAB_COLS` 实测），本模块**只读、零写入**。
- 分片重复接收的幂等兜底 **NEVER 简化回 `catch (DuplicateKeyException)`**（`recon/service/ReconPartService.java:122~128`，grep `NEVER 简化回`，ADR-D53）：本模块**开了 tracing**，`MapperAspectToTrace` 会切到所有 `@Mapper` 方法上；它此前把异常包成 `new RuntimeException(e)`，按类型 catch 的兜底**一条都进不去**、`ORA-00001` 直接冒到全局处理器。切面已改成原样抛出，本模块这层**沿 cause 链判定**作为第二道防线保留。

### 十二、配置与日志

- **traceId**（`application.properties:20~27`，grep `traceId 关联`）：`micro/web` 默认 `management.tracing.enabled=false`，本模块显式打开（现值 `:28~29` + `:37` 的 `spring.autoconfigure.exclude`，**三行成组**）。打开后 web-admin `sys_job` 109 发来的 W3C `traceparent` 才能续接进 MDC、日志 pattern 的 `%X{traceId}` 才有值；否则前台按 `SYS_JOB_LOG` 的 traceId 检索本模块日志会 **0 条**（2026-09-11 实测：调度日志记着 traceId、recon-server 那一列全空）。本模块**没有自带 log4j2 配置、走公共 `log4j2-linux.xml`**，其 pattern 已含 `%X{traceId}`、只差这个开关。同一条 `traceparent` 会由 Boot 的 WebClient 观测自动带给三个源服务的 `/internal/recon/export`，因此 **gate-txn-pay / collect-pay / daily-ticket 三个源 MUST 一起打开本开关**，否则链路在源侧断开。
- **`spring.autoconfigure.exclude=...OtlpAutoConfiguration` 这行 NEVER 删**（`application.properties:30~36`，grep `NEVER 删除下面这行排除`），三条理由：① `sampling.probability=0` 只让**本服务发起**的 trace 不采样（采样器是 `parentBased(traceIdRatioBased(0))`），上游带 `sampled=1` 的 `traceparent` / `b3` 进来时 span 仍会被采样并进导出队列；② `micro/web` 的 `web.properties` 虽已把 `management.otlp.tracing.endpoint` 整行注释掉，但 **K8s Deployment 只要注入 `MANAGEMENT_OTLP_TRACING_ENDPOINT` env 就会重新激活 exporter**，本行是第二道保险；③ Boot 3.2.6 **没有** `management.tracing.export.enabled` 这个开关，把 endpoint 置空也不行（`OtlpAutoConfiguration` 只判断键是否存在），只能排掉整个自动配置。
- **`InternalMicroHttp` 的 logger 压到 WARN、NEVER 删那行配置**（`application.properties:123~129`，grep `NEVER 删除下面这行配置`）。原始理由（防内部令牌明文落日志）**已不成立** —— `X-Recon-Token` 不再发送；**NEVER 回退成「其中包含内部令牌 X-Recon-Token 的明文」那个说法**。现行理由：该 logger 会把**整个请求头 Map** 直接打进 INFO，加上下面那条双份地址，噪音对排查无益；且恢复鉴权后同一风险会立刻回来。公共构件 `resource/micro` 不改，只压本模块这个 logger。
- **`ReconExportClient` 的 baseUrl 固定为空串**（复用 `URLDynamicRouter` 的动态路由做法），下发地址一律取 `recon.orchestration.sources[].url` 的绝对 URL，因此**不需要自身地址配置项**；原 `service.recon.self-url` **已删除** —— 它只是占位 baseUrl，却让 `InternalMicroHttp` 的 INFO 打出 `http://127.0.0.1:9112/http://<源服务>/internal/recon/export` 这种双份地址（2026-09-11 实录）（`application.properties:117~120`，grep `双份地址`）。
- **四个源的期望清单**（`application.properties:88~115`）：`sources[0] gate-txn-pay → EXP,PAY,BUS,DETAIL`、`sources[1] collect-pay → PAY,BUS`、`sources[2] daily-ticket → PAY,DETAIL`、`sources[3] face-pay → PAY,BUS`。地址是 `kubectl get svc -n itp` 实测值（**Service 端口等于 NodePort 号、不等于容器 `server.port`**），线上仍以 Deployment env 覆盖为准；`SourceExpectation.url` 的注释也强调 **NEVER 按对方容器 `server.port` 推断端口**，并声明此前示例 `http://ticket-server-svc.itp.svc:9103` **两处都已过期、NEVER 回退**（`ReconOrchestrationProperties.java:110~115`）。
  - **`face-pay` 与 `collect-pay` 是「新表 / 旧表」并列关系、NEVER 二选一**（`application.properties:103~112`，grep `NEVER 二选一`）：设备域与 APP 域入向流量已于 2026-09-16 17:31 切到 face-pay（ADR-D117），新单只进 `F2F_*`；**切流之前的历史单仍在 collect-pay 那四张旧表里，只有那个源导得出来**。去掉任何一个都会让对应时间段的账**凭空少一截**，而对账文件是纯文本、下游读不出异常。此处还留了一条纠错：**此前写「已于 2026-09-15 切到 face-pay（ADR-D85）」是错的** —— ADR-D85 只是「切流量 MUST 改 VirtualService、NEVER 改 Service selector」的方法论，日期也与真实切流时点不符（其间 ADR-D112 还曾按当日口径记成「尚未切」，已被 D117 取代），**NEVER 回退**。
  - **ticket 源已移出期望清单**，但 `ticket-server` 侧的 `ReconExportMapper` / `ReconExportService` **保留未删、NEVER 删**（`application.properties:91~93`）：甲方四类文件里没有 ticket 源的位置（过闸笔数与金额由 gate-txn-pay 出，`QRCODE_TXN_DETAIL` 不参与），后续甲方若要过闸明细，把 ticket 源加回清单即可。
- 期望清单为空时 dispatch 无事可做，属**配置缺失**，MUST 在部署清单里核对（`ReconOrchestrationProperties.java:69`）。来源标识落 `RECON_BATCH_SOURCE.SOURCE_NAME`，**MUST 匹配 `[A-Za-z0-9_-]{1,32}`**（同文件 `:107`）。
- 人工干预端点的边界：`PUT /internal/recon/batches/{batchId}/status` **仅供人工干预**，正常链路的状态推进一律由编排器按收齐结果决定，**手工改状态会绕过收齐校验，MUST 在确认数据一致后才用**（`ReconInternalController.java:81~86`，grep `仅供人工干预`）。

### 十三、删除后代码里保留的一行式护栏（recon 侧 6 条）

删注释时**只保留这 6 条**（第 7 条在 collect-ticket 侧，见 `app-ticket-collect.md` 同名小节）。它们是「改代码的人一定会看到、且看不到就会踩」的位置，其余全部迁进本节：

| # | 位置 | 保留的一行 |
|---|---|---|
| ① | `ReconServer.java` 类上 | 不显式声明 `@EnableScheduling` ≠ 调度未启用（micro web 的 `WebAutoConfig` 全局启用）；NEVER 写成可断言项 |
| ② | `ReconOrchestrationService.runDailyBatch` 上 | 只有进程内 `AtomicBoolean`、无数据库锁 ⇒ recon-server 与 web-admin 都 MUST 单副本 |
| ③ | `ReconOrchestrationProperties.runTimeoutMillis` 上 | MUST 小于 `ReconClient` 的响应超时（5 分钟） |
| ④ | `ReconBatchService.allowed`（状态白名单）上 | `PARTIAL -> EXPORTING` MUST 在白名单里，缺它那个账期再也补不回来 |
| ⑤ | `ReconInternalController` 类上 | 本组接口当前无鉴权，有意临时降级，上线前 MUST 恢复 |
| ⑥ | `application.properties` 的 logger 行上 | 把 `InternalMicroHttp` 的 logger 压到 WARN，NEVER 删除下面这行配置 |

另外五个 mapper XML 各保留一条「SQL 正文禁写注释（Druid WallFilter），说明写在 XML 注释里」的一行式提醒（第 ⑦ 条）。

### 矛盾与待裁决

1. **`recon.sources` 与 `recon.orchestration.sources[*]` 并存且内容不一致**：前者 `application.properties:50` 仍是 `gate-txn-pay,ticket,collect-pay,daily-ticket`（含已移出的 `ticket`、缺 2026-09-16 新增的 `face-pay`），后者是四个源的真实期望清单。生成路径已改成「读 `RECON_BATCH_SOURCE` 里 `COMPLETED` 的行」，因此 `recon.sources` **当前是否还有读取方尚未逐处确认**。待裁决：删掉该键，还是回填成与 `sources[*]` 一致。**在裁决前 NEVER 拿 `recon.sources` 判断「谁出哪个文件」。**
2. **`recon.internal-token` / `RECON_INTERNAL_TOKEN` 留键无读取方**：与 §九「上线前 MUST 恢复鉴权」配套。待裁决：上线前恢复定长比较（推荐），还是改走 `AccountRequestVerifier` / `ItpRequestSignVerifier` 那套现成验签。**NEVER 自造签名逻辑**（AGENTS.md §5.2）。
3. **共享存储（ReadWriteMany PVC）与「MUST 单副本」两条约束都只写在文档里，集群侧未落地**：当前靠「事实上只部了一个副本」兜着，没有任何机制阻止有人把 replicas 改成 2。待裁决：加 PDB / 注释说明 / 还是在启动时自检（例如探测同名批次的并发写）。
4. **两个 SQL 文件 0 条 `COMMENT ON`**：`RECON_*` 四张表的列语义只存在于本文与 mapper XML。待裁决：是否补 `COMMENT ON COLUMN`（本项目其它模块也普遍没有，属全局风格问题，**MUST 先问用户再动**）。
5. **`amountTotal` 在明细与汇总两条路径上语义不同**（明细=业务金额，汇总=所有度量列之和的校验和），同一列名承载两种语义。待裁决：是否拆成两列 / 改列名。**在裁决前 NEVER 把 `RECON_BATCH_FILE.AMOUNT_TOTAL` 当业务金额对外汇报。**

### 墓碑清单（本轮删除的注释里带「NEVER 回退 / 已删除 / 已过期」的断言）

| # | 原注释位置 | 断言内容（迁入本节后仍生效） |
|---|---|---|
| M1 | `ReconServer.java:28~30` | 此前只写「本模块一个 `@Scheduled` 都没有、启动类也没有 `@EnableScheduling`」，会被读成「调度未启用」；**NEVER 据此推断，NEVER 写成可断言项** |
| M2 | `ReconOrchestrationProperties.java:16~19`、`application.properties:79` | `recon.orchestration.dispatch-cron` / `RECON_DISPATCH_CRON` **已删除、NEVER 加回** |
| M3 | `application.properties:15~16` | 「鉴权靠 `checkToken()` 的 `X-Recon-Token` 定长比较」**已作废**（代码已删），NEVER 回退 |
| M4 | `application.properties:125~126` | 「压 logger 是因为请求头含 `X-Recon-Token` 明文」**已不成立**（不再发送该头），NEVER 回退；但那行配置本身 NEVER 删 |
| M5 | `application.properties:107~109` | 「已于 2026-09-15 切到 face-pay（ADR-D85）」**是错的**（D85 是方法论、日期不符，D112 的「尚未切」也已被 D117 取代），NEVER 回退 |
| M6 | `ReconOrchestrationProperties.java:113~115` | 源地址示例 `http://ticket-server-svc.itp.svc:9103` **两处都已过期**（ticket 已移出清单、9103 是容器端口口径），NEVER 回退 |
| M7 | `application.properties:117~120` | `service.recon.self-url` **已删除**（占位 baseUrl 导致双份地址日志），NEVER 加回 |
| M8 | `ReconFtpService.java:26~32`、`ReconFtpProperties.java:20~25` | 「BUS 投递静默失败」**已被探针实证推翻**（ACC 侧取件即删），NEVER 再当我方缺陷 |
| M9 | `ReconFtpProperties.java:31~34` | 相邻投递间隔**不是修复项**（「对端在极短间隔连续会话丢文件」那个判断已被推翻），只是保守限流 |
| M10 | `ReconOrchestrationService.java:182~183` | 「重复触发安全」此前只在 `advanceBatch` 那层成立、`dispatchDailyBatch` 这层**并未兑现**（1.0.12 才补上短路） |
| M11 | `ReconOrchestrationService.java:186~187` | `dispatchDailyBatch` 失败分支**原先是记日志后 return**，现在 MUST 抛，NEVER 回退 |
| M12 | `ReconPartService.java:125~128` | 兜底 catch **NEVER 简化回 `catch (DuplicateKeyException)`**（ADR-D53，本模块开了 tracing） |
| M13 | `ReconLineBackfillProperties.java:25~26` | 线路补齐 **NEVER 改成「只在源上送为空时才补」**；NEVER 往 `file-types` 加 EXP / DETAIL |
| M14 | `ReconOrchestrationProperties.java:52~54` | `window-offset-days` **NEVER 改回 1** |

### 覆盖率自评

- **按文件**：40 个文件里有注释的 30 个（Java 24 / XML 5 / properties 1），本节逐条覆盖 **30/30**；无注释的 10 个（2 个 SQL + 8 个纯字段 record / 枚举）已按「无可迁移知识」说明。
- **按注释行**：829 行中，**判为「有知识、已迁入本节」的约 470 行**（含 §一~§十二 全部条目）；**判为「标准 Javadoc、随代码保留」的约 300 行**（`@param` / `@return` / 一句话方法说明 / 枚举常量说明）；**判为「纯样板、丢弃」的约 60 行**（`/**` `*/` 单独成行、复述方法名）。
- **按四类**：契约与判据（段数 / 账期 / 白名单 / 收齐三项 / jdbcType / 装箱）**全覆盖**；决策理由（为何同步轮询 / 为何不加事务 / 为何线路收口到本模块 / 为何不缓存维表 / 为何不加 WHERE）**全覆盖**；陷阱（覆盖 COMPLETED / 白名单缺项 / SUCCESS 短路 / 装箱 `!=` / `OTHER(1111)` / XML 连续减号 / 兜底 catch 被切面吞 / 明细走汇总路径 OOM / 降级空 Map / `skipCheckTokenUrls` 语义相反）**全覆盖 10 条**；墓碑 **14 条**（阶段一记 12 条，本轮新增 M1「调度形态易读反」与 M10「重复触发安全未兑现」两条）。
- **未覆盖 / 无法从注释得到的**：① `RECON_*` 四张表的列长与非空约束（注释里没有，MUST 看 `sql/recon-server-schema.sql` 或 `USER_TAB_COLS`）；② 分片落盘目录结构（`recon.storage.root` 之下的实际布局只在代码里，注释没写）；③ 各源的抽取 SQL 口径（在三个源服务里，属那三个模块的迁移范围，本文正文「各源抽取口径」已有）；④ 甲方规格原文的逐段字段名（只在 `.docx` 里，本文正文「分片文件格式契约」已按规格落地）。


