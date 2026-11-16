# ITP ↔ ACC 对账接口设计（目标态协议 · 会签稿）

> **状态：目标态设计，未落地、代码零实现、待甲方 / ACC 会签**（2026-11-13 建立）。
> 本文定义 ITP 与 ACC 之间**对账业务的对外接口契约**，是「双方要遵守什么」，不是「现在有什么」。
>
> **与既有文档的边界（单向指针，NEVER 拿本文当副本）**：
> - 文件名、字段顺序、账期原始口径、渠道编码、账单样例、FTP 凭据与 19 项待澄清冲突 → `docs/business/recon-file-catalog.md`（文件级契约唯一权威，其第七章 Q1~Q19 是待确认项真源）；
> - 代码落地现状（哪些已实现 / 真实链路 / 已知坑）→ `docs/business/recon.md`、`docs/business/recon-thirdparty.md`；
> - 四方对账工作簿与指标公式评估 → `docs/business/recon-qdt.md`；
> - 综管台上传的命名 / 替换 / 拒绝提示 → `docs/ops/对账文件上传命名与替换说明.md`；
> - 财务上传的商户入账金额确认件（需求侧，未立项）→ `docs/business/recon-merchant-transfer-import.md`；本文只承接其「ITP → ACC 共享」这一段对外契约，见 6.4；
> - 逐文件族的 manifest 内容、四元组计算口径、IR 时序与相位绑定 → `docs/business/recon-acc-file-family-design.md`（2026-11-16 建立，本文的会签稿组成部分）；
> - 甲方规格原文（**只读**）→ `docs/接口规范文档/ACC与ITP之间的文件.docx`、`docs/接口规范文档/青岛地铁日票-ITP与ACC交互文档.docx`；
> - 本仓架构与一致性硬约束 → `docs/architecture/transaction-and-consistency.md`、`docs/architecture/common-components.md`。
>
> **本文不定义**：行格式段序与字段名（以文件级契约为准）、财务侧导入流程与模板校验、ITP 内部服务接口、数据库表结构、代码类名、生产环境凭据。
> 甲方规格原文目录 `docs/接口规范文档/` **NEVER 新增派生 `.md`**；本文会签通过后由双方签署正式版本号，再按 `docs/提示词/工程与文档规范.md` 的回写义务同步。

---

## 一、问题定义与设计目标

### 1.1 现状的三个结构性缺口

| # | 缺口 | 后果 |
|---|---|---|
| G1 | **单向**：ITP 生成文件投递到 ACC 的 FTP 后即收口，ACC 取件即删，既无逐文件接收回执，也无批次对账结果与差异回传 | ITP 侧只能以「远端 rename + 字节数回查」当成功，无法区分「ACC 已收」与「ACC 已核对」，差异永远由 ACC 单方掌握 |
| G2 | **口径不唯一**：账期存在 T-2 窗口、运营日 T~T+1、自然日三套日切；金额存在元与分；DETAIL / TXNDIFF 段数存在 7 / 21 / 22 三种记载；差异原因一个编码同时想表达「方向」和「原因」 | 双方无法用同一个键把一份文件、一次回执、一条差异、一张工单关联起来，跨系统核对只能靠人工 |
| G3 | **控制面无契约**：现有 ACC 实时接口（逻辑卡号申请、日票激活通知、员工卡、设备台账）各自组包，签名口径在甲方文档与落地实现间全面冲突；对账文件本身没有清单、没有版本、没有幂等键 | 无法判断「文件齐没齐」「这版是不是重传」「重传会不会覆盖」，补账与重跑无审计轨迹 |

> 现状细节只作差距说明，落地事实 MUST 查 `docs/business/recon.md`、`docs/business/recon-thirdparty.md`，NEVER 据本文推断线上行为。

### 1.2 目标（四条可验收）

1. **账期可闭环**：一个账期从声明到关账，ITP 与 ACC 双侧状态一致，任一侧都能独立回答「这个账期平没平、卡在哪一步」。
2. **差异可双向**：ITP 侧差异与 ACC 侧差异都能结构化下发，且差异方向与差异类型分开表达。
3. **重传可追溯**：任何补报、冲正、重传都以 `revision` 追加，不覆盖同版本，历史版本可查。
4. **状态可查询**：回执丢失靠查询补齐，不靠盲目重推；业务拒绝与网络不可达分流处理。

### 1.3 非目标

- 不改变四类对账文件的业务含义与统计口径（那是文件级契约与甲方规格的范围）；
- 不重做渠道侧账单获取、琴岛通四方对账工作簿，也不定义财务侧的上传流程、模板校验与导入留痕（见 `docs/business/recon-merchant-transfer-import.md`）；本文只定义这类导入件**从 ITP 共享给 ACC** 的对外契约（6.4）；
- 不定义 ITP 内部 `recon-server`、源服务、综管台之间的接口；
- 不引入新的中间件或改变现有部署拓扑。

---

## 二、总体架构：文件面与控制面分离

### 2.1 通道分工（本设计的核心决策）

| 通道 | 承载 | 方向 | 时机 | 规模 |
|---|---|---|---|---|
| **文件面**（SFTP / FTP） | 交易明细、汇总、差异明细，以及财务上传的商户入账确认件等**批量 / 成件数据** | 双向 | 日切后批量 | 明细可达百万行；确认件为单个 Excel |
| **控制面**（实时 HTTP / JSON） | 清单、校验值、回执、结果、状态、工单等**元数据** | 双向 | 事件触发 | 单请求 ≤ 数十 KB |

**判据**：任何「逐笔」数据 MUST 走文件，任何「关于这批数据的说明与结论」MUST 走控制面。
NEVER 用实时接口分页拉交易明细，也 NEVER 把对账结论只写成文件让对方去轮询目录。

### 2.2 与既有 ACC 实时接口隔离

逻辑卡号申请（IF7B-01）、日票激活通知（IF7B-09）、员工卡与设备台账、模式广播（IF7A-01）、文件通知（IF7A-02）属于**业务实时通道**（《技术规范第 9 部分》定义了 IF7A-01 / IF7A-02 / IF7B-01 编号），各自有独立的 URL、bizData 契约与生命周期。
本设计的对账控制面 **NEVER 复用这些 URL、NEVER 复用其 bizData 结构、NEVER 借其 `providerId` / `deviceId` 语义**，应单独约定地址前缀与接入账号，避免一条业务接口故障拖垮对账闭环，也避免签名口径互相污染。

### 2.3 环境与隔离

| 项 | 目标态要求 |
|---|---|
| 目录 | ITP→ACC 与 ACC→ITP 各自独立根目录，账号按方向最小权限（投递方只写、接收方只读） |
| 环境 | 测试与生产使用不同主机、不同账号、不同凭据；生产值由双方书面交付，NEVER 从测试值推导 |
| 传输 | 生产建议 SFTP 或专用文件网关 + TLS；测试期可沿用现有 FTP，但控制面 MUST HTTPS |
| 时间 | 双方以同一时间源校时，控制面时间戳精度到秒，超时窗口按第三节约定 |

**与编码设备 FTP 的关系（2026-11-16 补）**：acc-es-server 的 FTP（明文、目录 `/UPLOAD`、9050/9060/9061 前缀）只承载编码设备任务 / 报告文件，**对账与逻辑卡号文件不在该模块**——对账文件面 FTP/SFTP 与之是两套独立站点 / 账号，或明确复用及目录隔离，随 Q8 会签定稿；acc-es 现网明文传输与「下载后删源被注释」的教训一并列入 Q8 会签材料。

### 2.4 与既有约定的关系（会签前必读）

**定性**（用户 2026-11-13 说明）：`docs/business/recon-file-catalog.md` 收录的《对账文件》整理版，是 ITP 与 ACC **此前对接形成的既有约定**，覆盖文件级交换契约（四类对账文件、TXNDIFF、第三方账单 23 行 + 22 字段 CSV、非 ITP 业务导入、商户入账确认件、参数 / 逻辑卡号 / HCE 文件、FTP 路径与测试凭据），以及**唯一一个实时 HTTP 接口**——IF7B-01 逻辑卡号申请 `/ci/itp/requestQrLoigcNumList`（即 2.2 所列旁路通道之一）。
更早的甲方只读规格 `docs/接口规范文档/ACC与ITP之间的文件.docx` 是旧版（只覆盖旧四类文件 + 参数 / 逻辑卡号，测试 FTP 值不同），两者是**新旧两版**，NEVER 混用。

**本设计的定位**：只补**控制面**，不替代数据面。文件名、段序、单位、账期、路径等数据面契约继续以既有约定 / 甲方只读规格为准（本文不复述）；新增的 manifest、回执、结果、差异、工单、状态查询都**挂在既有文件之上**，NEVER 要求 ACC 改掉在跑的取件进程。

| 类别 | 内容 | 处置 |
|---|---|---|
| **沿用不改** | 四类文件名 `ITP.<TYPE>.<yyyyMMdd>` 与段序（EXP 13 / PAY 21 / BUS 4 / DETAIL 22，DETAIL 与落地 7 / 21 段口径冲突见 Q11）、`/itp/recon/` 投递路径、T 日 2 点统计 T-2 ~ T-1 且文件名取 T-2 的账期、参数 `/parameter/cur/97000000/` 与逻辑卡号 `/itp/qrLoigcNum/` 路径、IF7B-01 的 URL 与请求 / 响应字段（URL 与大小写笔误以联调实测报文为准，Q6）、商户入账件「导入 ITP → FTP 转投 ACC」的方向 | 直接作为本设计的数据面前提，P0 阶段 NEVER 改动 |
| **目标态变更（MUST 会签）** | 三套日切 → 唯一 `settlementDate`；差异原因 `01~04` → `diffDirection × diffType` 两维字典；**渠道编码字典统一（2026-11-16 清单标准定义列已对齐 3.2；城交侧编码映射与「提供第三方账单」列语义待签，见 Q16）**；`ITP.TXNDIFF` 22 段表头（原文注明「具体字段在 ITP 这边出定义后双方再确认」，属未会签项）；新增 manifest / `.manifest.json` 与四元组校验；生产 FTP → SFTP；控制面启用双向签名与 `requestId`；ACC 取件前先发 IR-07 回执；IR-01 触发时机与 IR-08 幂等键修订、控制面金额字符串化（2026-11-16 复核修订，明细见文件族绑定设计 §十） | **变更或补签既有约定**，MUST 与 ACC 书面会签后生效，NEVER 由 ITP 单方裁量、NEVER 静默上线 |
| **纯新增（既有约定无）** | IR-01 ~ IR-11 全部控制面接口、结算日历、差错工单、`revision` 版本机制、双侧状态机与 SLA、`MERCHANT_TRANSFER` 独立批次（6.4） | 新增能力，不改变既有字段语义 |

**硬约束**：本设计稿在会签前**不得作为生产实现依据**；任何落在「沿用不改」或「目标态变更」两列的字段、路径、码值，MUST 先与 ACC 会签。第 9 节差距表与第 11 节待会签清单是这份边界的执行清单。

**另一类「之前做的设计」，NEVER 混用**：`docs/business/recon-qdt.md` 评估的琴岛通《对账.xls》（原件 2026-11-13 归档到 `docs/testing/itp-acc/samples/琴岛通对账/`）是**琴岛通 / ACC / 地铁三方人工对账工作簿模板**，四张产品线日对账表 + 一组差异公式 + 审核闭环，状态为「未立项、代码零实现」。它定义的是**对账指标与差异口径**，全簿无接口 / 文件名 / FTP 路径 / 频率 / 账期定义，既不是甲方接口规格，也不是本节所指的 ITP↔ACC 既有约定。本文的控制面协议**不覆盖**「打包上传琴岛通」的上传契约；该契约仍属 `docs/business/recon-qdt.md` 的待确认项，NEVER 据本文推断其字段或路径。

---

## 三、控制面公共协议

> 本节对第 5 节全部接口生效。接口编号 `IR-xx` 是本文自编号，待会签后替换成甲方正式编号，NEVER 与 IF7B / IF8A 混用。

### 3.1 请求

HTTP `POST`，`Content-Type: application/json; charset=UTF-8`，UTF-8 编码，TLS。请求体分两层：公共参数与 `bizData`。

| 字段 | 必填 | 类型 | 说明 |
|---|---|---|---|
| `protocolVersion` | Y | string | 协议版本，形如 `1.0`；不兼容升级 MUST 升主版本 |
| `requestId` | Y | string(64) | ITP / ACC 各自生成、全局唯一；**幂等主键**，重复请求 MUST 返回首次处理结果 |
| `providerId` | Y | string | 发起方标识，值待会签（N5） |
| `timestamp` | Y | string(14) | `yyyyMMddHHmmss`，双方校时；超出窗口的请求 MUST 拒收 |
| `nonce` | Y | string(32) | 随机串，与 `timestamp` 共同防重放 |
| `charset` | Y | string | 固定 `UTF-8` |
| `format` | Y | string | 固定 `json` |
| `signType` / `sign` | Y | string | 见 3.4，**生产禁止免签** |
| `bizData` | Y | object | 各接口业务字段，见第五节 |

**金额字段口径**：控制面 JSON 中所有金额字段（`amountTotal` / `totalDiffAmount` / `amount` / `settledAmount` 等）一律**字符串化**传输——非负整数、单位分（导入族经 `amountUnit` 标记原件单位，见文件族绑定设计），**NEVER 使用 JSON number**，避免解析精度丢失；文件面口径见 6.3。

### 3.2 响应

| 字段 | 必填 | 类型 | 说明 |
|---|---|---|---|
| `retCode` | Y | string(4) | 结果码，分段见下表；**HTTP 状态码恒 200，业务结论只看 `retCode`** |
| `retMsg` | Y | string | 结果描述，禁止回显密钥、口令、证件号 |
| `requestId` | Y | string | 原样回传请求方 `requestId` |
| `serverTime` | Y | string(14) | 应答方时间，用于对时与超时判定 |
| `sign` / `signType` | Y | string | 应答签名，与请求同算法 |
| `data` | N | object | 业务返回体；失败时可为空 |

| `retCode` 段 | 含义 | 调用方处置 |
|---|---|---|
| `0000` | 成功 | 推进状态机 |
| `1xxx` | 参数 / 格式 / 签名错误 | **不重试**，修正后以新 `requestId` 重发 |
| `2xxx` | 业务拒绝（账期未开放、版本冲突、状态不允许） | **不重试**，转人工或按状态机补偿 |
| `3xxx` | 幂等冲突（同键不同内容） | **不重试**，落异常工单 |
| `4xxx` | 传输 / 文件校验失败 | 按 7.5 走重传流程 |
| `5xxx` | 对端系统异常 / 超时未决 | 网络类可重试；结果未决 MUST 先查 IR-05 |

**收发两侧实现要求（2026-11-16 补，源自本仓教训）**：①接收侧（ITP 收 IR-07/08/09/10/11 的端点与 ACC 收 IR-01/02/03/04/06 的端点）MUST 配专用异常处理器，未捕获异常 MUST 映射为 `5xxx`，NEVER 依赖公共兜底把异常吞成「HTTP 200 + 伪成功 retCode」；②发送侧 MUST 校验响应体 `retCode` 判定业务结论，NEVER 仅按 HTTP 状态码判成败。

### 3.3 幂等、防重放与重试

- **幂等键**：`requestId` 为请求级唯一键；业务级键见各接口「幂等键」列。接收方 MUST 落库保存首次处理结果，重复请求返回首次结果，**NEVER 重复执行副作用**。
- **同键不同内容**：MUST 返回 `3xxx`，禁止「后到覆盖先到」。
- **防重放**：`timestamp` 超出 ±N 分钟（建议 5，待会签 N3）、或 `nonce` 在窗口内重复，一律拒收。
- **重试**：仅对「网络不可达 / 应答超时且结果未决」重试，指数退避、上限 3 次；`1xxx / 2xxx / 3xxx` MUST NOT 重试。重试 MUST 复用同一 `requestId`。
- **兜底**：回执或应答丢失时，以 IR-05 查询补齐状态，**NEVER 靠重推文件或重跑整批来猜**。

### 3.4 签名与安全（待安全合规复核）

- 签名骨架沿用甲方公共报文约定（非空参数按字段名排序后拼接、附加密钥、摘要），但**具体算法、字符集、密钥、providerId、signType 取值，本文不裁决**：甲方日票规格写 `providerId=05 / signType=02 / MD5+GB18030`，现有落地是 `06 / 00 免签 / UTF-8 / 静态 sign`，两者全面冲突（会签项 N5、文件级契约 Q6）。
- **生产 MUST 启用双向签名，禁止 `signType=00`**；密钥分发、轮换、存储由双方安全接口人书面确认。
- 涉及签名 / 密钥 / 加密逻辑的实现改动，**按 `AGENTS.md` 的「4.2 安全红线」MUST 提示人工复核安全合规性**，本设计不授权任何一方可自行改算法。
- 日志 MUST NOT 打印密钥、口令、签名原文与完整报文中的敏感字段；凭据按仓库既定口径由配置直列，不外传、截图打码。

### 3.5 超时

请求方连接超时建议 10 秒、响应超时建议 30 秒（控制面只传元数据，不含批量数据）；超时即视为「结果未决」，按 3.3 处理。具体值待会签（N3）。

---

## 四、账期与批次模型

### 4.1 唯一账期键

全链路只认一个 `settlementDate`（`yyyyMMdd`，运营日），默认日切基准 **02:00 → 次日 02:00**（左闭右开），由 `settlementCalendar`（IR-11）统一下发，节假日与特殊运营日以日历为准。

现状存在 T 日 2 点统计 T-2~T-1 且文件名取 T-2、DETAIL 用运营日 T~T+1、TXNDIFF 用自然日三套口径（文件级契约 Q10）。目标态要求：**三套口径 MUST 收敛为一套**，若确因业务需要保留多套，MUST 在每个接口与每份 manifest 里显式带 `calendarType`，并给出与 `settlementDate` 的换算关系，NEVER 让接收方自行推断。

**2026-11-16 优化**：①批 A（EXP/PAY/BUS）的 T-2 02:00 窗口**已实现且经甲方自校验例验证**（recon.md 账期公式），Q10 会签时批 A 无需再议，谈判范围收敛到批 B~E；②《需求分析 v2.1》（2025-07 版）明文要求「支持不同接入机构不同时段的日切管理」——多日切是**甲方要求的能力**而非待清理的混乱，Q10 的收敛目标修正为「**主口径收敛 + 机构级日切配置能力**」（承载见 IR-11）。

### 4.2 批次号与版本

| 键 | 规则 |
|---|---|
| `batchNo` | 建议 `ITP-<settlementDate>-<batchType>-<sequence>`，全局唯一、不可复用；与既有内部批次 `RECON_BATCH.BATCH_ID`（现状 `RECON<businessDate>`）MUST 一对一映射（manifest 可选带 `internalBatchId`），NEVER 两套批次 ID 互不知情 |
| `revision` | 从 `1` 起；补报 / 冲正 / 重传 MUST `+1`，**NEVER 覆盖同 revision** |
| `fileType` | `EXP / PAY / BUS / DETAIL / TXNDIFF / MERCHANT_TRANSFER`，前五类值域与文件级契约一致；`MERCHANT_TRANSFER` 为财务上传的商户入账金额确认件（6.4），按同一批次模型管理但**不参与逐笔差异比对**；`POS_TXN / QRCODE_TXN`（银联 POS / 收款码导入件）为新增建议值，待会签（N16） |
| 关联五元组 | `settlementDate + batchNo + fileType + revision + requestId` |

**判定**：`SUCCESS` / `CLOSED` 是终态；终态账期如需重开，MUST 走 IR-06 申请并生成新 `revision`，NEVER 原地改状态。

### 4.3 建议时间线（SLA，待会签 N1）

| 时点 | 动作 | 责任方 |
|---|---|---|
| D 日 02:00 ~ D+1 日 02:00 | 运营日 D 的交易发生 | 双方 |
| D+2 日 02:00 | ITP 生成并投递 D 账期文件，发 IR-01 / IR-02 | ITP |
| D+2 日当日 | ACC 逐文件回 IR-07；收齐后回 IR-08 | ACC |
| D+3 日 | 双方差异明细下发（IR-03 / IR-09） | 双方 |
| D+4 日 | 单边账 / 晚传交易判定，提交差错工单 IR-04 | ITP |
| D+5 日 | ACC 回 IR-10，双方关账 `CLOSED` | 双方 |

> D+2 出账与现状「T 日 2 点出 T-2 账期」一致，是兼容现有取件节奏的建议值；具体天数与截止时间点 MUST 由双方会签，NEVER 据本文改成生产调度。

---

## 五、接口清单

### 5.1 总表

| 编号 | 名称 | 方向 | 触发时机 | 幂等键 |
|---|---|---|---|---|
| IR-01 | 批次声明 `batchDeclare` | ITP→ACC | 本批文件全部生成完成后、首份文件投递（rename）前 | `settlementDate + batchNo + revision` |
| IR-02 | 文件就绪 `fileReady` | ITP→ACC | 单个文件远端 rename 完成后 | `batchNo + fileType + revision` |
| IR-03 | 差异汇总上报 `diffReport` | ITP→ACC | ITP 侧差异文件就绪 | `settlementDate + batchNo + revision` |
| IR-04 | 差错工单提交 `adjustmentSubmit` | ITP→ACC | 人工裁决后补报 / 冲正 / 复报 | `ticketNo + revision` |
| IR-05 | 回执与结果查询 `receiptQuery` | ITP→ACC | 回执超时 / 状态不一致 / 对账核查 | 只读，无副作用 |
| IR-06 | 重传申请 `retransmitApply` | ITP→ACC | 文件校验失败或终态账期重开 | `batchNo + fileType + revision` |
| IR-07 | 文件接收回执 `fileReceipt` | ACC→ITP | 每份文件取件并校验后 | `batchNo + fileType + revision` |
| IR-08 | 批次对账结果 `batchResult` | ACC→ITP | 批次文件全部收齐并完成核对 | `settlementDate + batchNo + revision` |
| IR-09 | ACC 差异下发 `accDiffNotice` | ACC→ITP | ACC 侧核出差异 | `settlementDate + batchNo + revision` |
| IR-10 | 差错处理结论 `adjustmentDecision` | ACC→ITP | 差错工单裁决完成 | `ticketNo + revision` |
| IR-11 | 结算日历下发 `settlementCalendar` | ACC→ITP | 日历 / 日切基准 / 参数版本变更 | `calendarVersion` |

### 5.2 ITP → ACC 接口字段

**IR-01 批次声明**（`bizData`）

| 字段 | 必填 | 说明 |
|---|---|---|
| `settlementDate` / `calendarType` | Y | 账期与日切口径 |
| `windowStart` / `windowEnd` | Y | 14 位，左闭右开，MUST 与 manifest 一致 |
| `batchNo` / `revision` / `batchType` | Y | 批次标识；`batchType` = `NORMAL / RETRANSMIT / ADJUSTMENT / FINANCE_CONFIRM`（6.4）；`OPERATION_IMPORT`（非 ITP 导入族）为新增建议值，待会签（N16） |
| `files[]` | Y | 本批文件清单，每项含 `fileType / fileName / recordCount / amountTotal / byteSize / sha256 / formatVersion`（四元组为生成后实测值，与 `.manifest.json` 完全一致；族扩展字段见文件族绑定设计 §七） |
| `expectedFileCount` | Y | ACC 据此判断「文件齐没齐」，替代现状的目录轮询 |

**IR-02 文件就绪**：`batchNo / revision / fileType / fileName / byteSize / recordCount / amountTotal / sha256 / formatVersion / uploadedAt`。
每个文件单独一条；MUST 在远端临时名 rename 成正式名之后发出，NEVER 在上传中发。

**IR-03 差异汇总上报**：`settlementDate / batchNo / revision / diffFileName / diffFileSha256 / totalDiffCount / totalDiffAmount` + 按 `diffDirection × diffType` 的分类计数（见第八节）。明细进文件，此处只带汇总与校验值。

**IR-04 差错工单提交**：`ticketNo / revision / settlementDate / relatedBatchNo / adjustType / reasonType / amount / orderKeys[] / attachments[] / operatorId / submittedAt`。
`adjustType` = `SUPPLEMENT / REVERSAL / RESUBMIT`；`orderKeys[]` 必须给出可回溯的业务键（订单号 / 交易流水号 / 逻辑卡号），NEVER 只给金额。

**IR-05 回执与结果查询**：`queryType` = `FILE_RECEIPT / BATCH_RESULT / TICKET_STATUS` + 对应键（`batchNo` / `fileType` / `ticketNo` / `settlementDate`）。返回该键的当前状态、最后一次 `retCode`、处理时间；**只读**。

**IR-06 重传申请**：`batchNo / fileType / fromRevision / toRevision / reasonCode / expectedChangeSummary`；`reasonCode` 建议值域含 `SOURCE_RECALC_FORBIDDEN`（账期落在跨源重叠 / 旧表窗口、源不可重算，只能按已生成文件原字节重投，ADR-D160 判据）与 `LATE_DEBIT`（单边补款跨账期迟到，ADR-D170）。
ACC 同意后才允许重传；拒绝则转人工。NEVER 未申请就覆盖同名文件。

### 5.3 ACC → ITP 接口字段

**IR-07 文件接收回执**：`batchNo / fileType / revision / fileName / receiptStatus / checkedValues{} / rejectCode / receivedAt`。
`receiptStatus` = `RECEIVED / REJECTED`；`checkedValues{}` MUST 回传 ACC 实收的 `byteSize / recordCount / amountTotal / sha256`，由 ITP 侧比对，**不比对通过不算收妥**。
`rejectCode` 建议值域：`FILE_MISSING / SIZE_MISMATCH / COUNT_MISMATCH / AMOUNT_MISMATCH / HASH_MISMATCH / FIELD_COUNT_MISMATCH / DUPLICATE_REVISION / OUT_OF_WINDOW`，以及第三方账单件的结构类 `HEADER_MISMATCH / COLUMN_COUNT_MISMATCH / ENCODING_MISMATCH / FORMULA_ROW_NOT_SKIPPED`（见 6.5）。

> 这一条是 G1 的收口点：ACC 现有取件进程会删文件，**MUST 在删除前先落回执并成功发出 IR-07**；未发出即删除的一律按「未收妥」处理，由 ITP 用 IR-05 兜底。

**IR-08 批次对账结果**：`settlementDate / batchNo / revision / result / itpTotal{} / accTotal{} / diffCount / diffAmount / checkedAt`（`revision` = 本批当前生效文件版本集）。
`result` = `PASS / DIFF / PENDING`；`PENDING` 必须带 `expectedBy` 时间，**NEVER 让批次无限期悬空**。幂等与覆盖：同一键内 `PENDING → 终态` 属合法推进，终态结果覆盖 `PENDING`，NEVER 按 `3xxx` 冲突处理；同键两条**终态**内容不同才是 `3xxx`；重传 `revision+1` 后按新键出结果，旧 `revision` 的终态结果标记 `superseded`、历史可查。

**IR-09 ACC 差异下发**：`settlementDate / batchNo / revision / diffFileName / diffFileSha256 / totalDiffCount / totalDiffAmount / summaryByType[]`。
差异明细仍走文件（字段以文件级契约为准），接口只带汇总、校验值与分类计数。

**IR-10 差错处理结论**：`ticketNo / revision / decision / rejectReason / settledAmount / decidedAt`。
`decision` = `APPROVED / REJECTED / RESUBMIT_REQUIRED`；驳回 MUST 带可执行的拒绝原因，NEVER 只回「不同意」。

**IR-11 结算日历下发**：`calendarVersion / effectiveFrom / dayCutTime / holidays[] / parameterVersion / publishedAt`。ITP 收妥后才允许用新日历生成账期文件。`parameterVersion` 承载对账参数（含《技术规范第 9 部分》默认阈值 T0 结算周期 / T1~T3），可选 `settlementCycles[]`（产品线 × T+N × 判定基准）为琴岛通等 T+1 / T+2 结算预留占位，随 N1 会签；`calendarVersion` 未按预期到达（含账期无 IR-01 声明）时，ACC 与 ITP 双方 MUST 各自按日历 + SLA 对「缺声明」告警，NEVER 静默等待。

### 5.4 接口与文件的对应关系

| 文件族 | 前置接口 | 伴随接口 | 收口接口 |
|---|---|---|---|
| `ITP.EXP / PAY / BUS / DETAIL` | IR-01 | 每文件 IR-02 | 每文件 IR-07 → 整批 IR-08 |
| `ITP.TXNDIFF` | IR-01 | IR-02 + IR-03 | IR-07 → IR-09（ACC 侧差异） |
| 重传 / 补报文件 | IR-06 | IR-02（新 revision） | IR-07 → IR-08 |
| 差错工单 | IR-04 | — | IR-10 |
| `商户号平台转账金额YYYYMMDD.xls`（`MERCHANT_TRANSFER`） | IR-01（带 `sourceType=FINANCE_UPLOAD`） | IR-02 | IR-07 → IR-08（只确认收妥，NEVER 出逐笔差异结论） |
| `银联pos交易 / 银联收款码YYYYMMDD.xls`（`POS_TXN / QRCODE_TXN`，N16） | IR-01（带 `sourceType=OPERATION_IMPORT`，N16） | IR-02 | IR-07 → IR-08（只确认收妥，NEVER 出逐笔差异结论） |

### 5.5 请求样例（IR-01，字段为示意，值待会签）

```json
{
  "protocolVersion": "1.0",
  "requestId": "ITP-20261113-0001",
  "providerId": "06",
  "timestamp": "20261113020005",
  "nonce": "9f2c...",
  "charset": "UTF-8",
  "format": "json",
  "signType": "02",
  "sign": "<signature>",
  "bizData": {
    "settlementDate": "20261111",
    "calendarType": "OPERATION_DAY",
    "windowStart": "20261111020000",
    "windowEnd": "20261112020000",
    "batchNo": "ITP-20261111-NORMAL-001",
    "revision": 1,
    "batchType": "NORMAL",
    "expectedFileCount": 4,
    "files": [
      {
        "fileType": "PAY",
        "fileName": "ITP.PAY.20261111",
        "recordCount": 1234,
        "amountTotal": "98765400",
        "byteSize": 234567,
        "sha256": "<sha256>",
        "formatVersion": "1.0"
      }
    ]
  }
}
```

---

## 六、文件面协议（目标态）

### 6.1 命名与兼容策略

保留 `ITP.<TYPE>.<yyyyMMdd>` 主文件名（ACC 现有取件进程按前缀匹配，改名会破坏在跑链路），新增同名控制文件 `<fileName>.manifest.json`。

**⚠️ 通配取件硬约束（2026-11-16 实证）**：ACC 取件进程按 `ITP.BUS*` 前缀**通配**匹配且取完即删（实测 30~45 秒，探针 `ITP.BUSX.*` 亦被删；EXP / PAY / DETAIL 族实测不被删）——`.uploading` 临时名与 `.manifest.json` 旁挂件**命中同一通配**，会在 IR-02 发出前被 ACC 取走删除。因此会签 MUST 把「取件改为正式名精确匹配、显式忽略 `.uploading` / `.manifest.json` 后缀」列为 ACC 侧改造点；会签前 BUS 族的 manifest 内容只经 IR-01 / IR-02 控制面承载，NEVER 落旁挂文件。

| 规则 | 要求 |
|---|---|
| 临时名 | 先传 `<fileName>.uploading`，全部写完后远端 rename 成正式名；rename 完成才可见、才发 IR-02 |
| 清单 | manifest 与数据文件同批到达，含文件清单、四元组、协议版本、账期窗口、生成时间、`revision` |
| 重传 | 文件名不变，`revision` 递增，manifest 与 IR-02 同步携带；**禁止同 revision 覆盖** |
| 格式版本 | 每个文件带 `formatVersion`，段数 / 单位 / 编码变更 MUST 升版本，NEVER 让接收方猜 |
| 标志文件 | 可选 `.ok` 标志；若使用，MUST 与 IR-02 二选一，NEVER 两套并行造成双触发 |

### 6.2 校验四元组

每份文件 MUST 同时校验 `byteSize`、`recordCount`、`amountTotal`、`sha256`，另加行级段数校验。
任一不符即 `REJECTED` 并回 IR-07 带 `rejectCode`，由 ITP 走 IR-06 重传申请。
**判据**：完整性以 IR-07 回执为准，FTP 的 `226 / 250` 应答码与事后目录 LIST 都不算收妥（现状已有 ACC 取件即删导致误判的教训；取件即删实测仅 BUS 族、窗口 30~45 秒，NEVER 据此推断其他文件族的存活期）。

### 6.3 编码、单位与金额符号

| 项 | 目标态 |
|---|---|
| 编码 | UTF-8；行尾 `\n`；字段分隔符 `\|`；无表头（沿用文件级契约） |
| 金额 | 统一**分**、整数、字符串化传输，禁止小数与科学计数法 |
| 方向 | 收 / 退用 `businessType` 或等价字段表达，金额本身始终非负 |
| 换算 | 元→分的换算落点、四舍五入规则 MUST 书面会签（文件级契约 Q5），NEVER 由任一实现方自行决定 |
| 空值 | 空段与 `0` 语义区分；金额类空段 MUST 明确定义为「不适用」而非 0 |

> 段数、段序、字段中文名、订单异常类型与渠道编码一律以 `docs/business/recon-file-catalog.md` 与甲方只读规格为准，本文不复述。
> 6.1 ~ 6.3 针对纯文本对账文件；Excel 类导入件的补充契约见 6.4。

### 6.4 财务上传件的共享契约（`MERCHANT_TRANSFER`）

**来源与方向**（用户 2026-11-13 补充）：财务按日人工汇总 → 上传到 ITP 平台 → **ITP 共享给 ACC**。
文件名 `商户号平台转账金额YYYYMMDD.xls`，载体 Excel、两级合并表头（表头内含红字口径提示 4475 / 3080；说明行与公式单元格待原件确认，需求草案 Q1）；字段与表头以 `docs/business/recon-file-catalog.md` 的 4.3 节为准，本文不复述。
上传方角色在整理版原文记为「运营导入」，与用户口径不一致——**ITP 侧已定稿：财务**（用户 2026-11-13 口径，2026-11-16 处理确认）；整理版原文差异随会签向甲方 / ACC 报备修订（文件级契约 Q15、本文 N11 收窄为对外报备项）。

**性质判据（本节的硬约束）**：这是**人工汇总的确认金额，不是交易流水**。因此：

- MUST NOT 参与第八节的逐笔差异比对（IR-03 / IR-09），也 MUST NOT 灌进任何交易流水表或分库导入包；
- ACC 侧消费用途是「商户入账金额汇总确认」，与四类对账文件的「应收 / 实收核对」是两件事，MUST 单独批次、单独回执；
- 数值正确性由财务负责，ITP 只负责格式与公式校验、原件留痕、传输闭环，**NEVER 自动改数、NEVER 自动抹平差异**。

| 项 | 四类对账文件 | 商户入账确认件 |
|---|---|---|
| 生成方 | ITP 从业务库自动抽取 | 财务人工汇总后上传 |
| 载体 | 管道分隔纯文本、无表头 | Excel、两级合并表头 + 说明行 |
| 粒度 | 逐笔明细 / 聚合行 | 按清分日期 × 支付方式汇总 |
| 单位 | 分（整数） | 待确认（需求草案 Q2，以原件与甲方确认件为准）；银联件的「小数点后 2 位分」自相矛盾见 Q9，NEVER 据此推断本件单位 |
| 账期 | 统一 `settlementDate` | 「清分日期」，与日切口径待对齐 |
| 校验 | 四元组 + 行级段数 | 四元组 + 表头结构 + 公式关系 + 说明行跳过 |
| 重算 | 可由源数据重跑 | **系统不可重算**，只能由财务重新上传并 `revision+1` |
| 差异比对 | 参与 | 不参与 |

**共享形态（P0 定稿取 A，2026-11-16；B 待条件成熟另议，N12 仅剩 ACC 确认）**：

| 案 | 做法 | 优点 | 代价 |
|---|---|---|---|
| A 原样转发 | ITP 保留 xls 原件字节不变，只补 manifest + IR-01 / IR-02 / IR-07，ACC 自行解析 | 无损、责任清晰、不需先定换算规则 | 表头 / 单位 / 说明行的歧义全压给 ACC |
| B 转换后投递 | ITP 解析成字段、单位、编码统一的标准件，原件留痕并随 manifest 带 `originalFileSha256` | 与 6.1 ~ 6.3 一致，ACC 消费单一格式 | ITP 承担口径转换责任，换算规则未会签即会静默改数 |

**定稿（2026-11-16）：P0 取 **A**——该件的数值是财务确认结果而非系统计算值，任何中间转换都属于替财务改数；B 待单位、码表、公式链全部会签后再评估，NEVER 在 A 会签通过前实施 B。**

**无论哪案都 MUST 满足**：

1. 单独 `batchType=FINANCE_CONFIRM` + `fileType=MERCHANT_TRANSFER`，NEVER 混进四类对账文件的同一批次；
2. IR-01 的 `files[]` 除四元组外 MUST 带 `sourceType=FINANCE_UPLOAD`、`originalFileName`、`originalFileSha256`、`uploadedBy`（角色）、`sheetName` / `headerRows`（Excel 专有）；
3. **复用 IR-01 / IR-02 / IR-05 / IR-06 / IR-07 / IR-08，不为这一族新增专用接口**——避免协议随文件族膨胀；
4. 校验除四元组外 MUST 覆盖两级表头列数与列序、说明行跳过、清分日期格式、支付方式枚举，以及合计金额 / 手续费 / 应转账金额 / 平台转账金额的公式关系；差异**只留痕**，NEVER 自动修正；
5. 重传只能由财务重新上传触发 `revision+1`，ITP 侧 NEVER 自动重新生成确认金额；
6. IR-08 对该批次只回「收妥 / 拒收」，NEVER 出 `PASS / DIFF` 的逐笔对账结论；
7. 原件、转换件（若采用 B）、manifest、回执 MUST 用同一个 `batchNo + revision` 关联，可回溯到上传人与上传时间。

### 6.5 第三方账单归一化与 22 字段映射

**目的**：把 `docs/business/recon-file-catalog.md` 3.3 节的 `reconciliation-bill-date.csv` 22 字段从「字段定义」落到「可实现的映射」，让 IR-03 / IR-09 的差异比对有统一比对单元。依据同文 3.7 节的 2026-09-28 真实样例（**识别态**，冲突见 Q17）。

**判据**：各渠道原始账单的表头行位置、列数、编码、金额口径互不相同，**NEVER 直接拿两家原始账单互比**；MUST 先归一化成 22 字段记录，再按 `merchant_order_no` / `channel_trade_no` 比对。

| 标准字段 | 支付宝（卖出 / 退款） | 微信（收 / 退） | 建行（收 / 退） | 银联 POS / 收款码 | 归一化规则 |
|---|---|---|---|---|---|
| `bill_date` | 查询起始日期 / 清算日期 | 文件名账期 | 出单日期 / 交易日期 | 清算时间 | 取**账期日**，非交易时刻 |
| `payment_channel` / `payment_vendor` / `channel_name` | 派生 | 派生 | 派生 | 派生 | 由文件来源 + 商户号按 Q16 会签码表派生，**NEVER 按文件名猜** |
| `channel_merchant_no` | 元数据账号 | 商户号 / 公众账号ID | 柜台代码 | 商户号 | 原值 |
| `terminal_no` | 门店 | 设备号 | 柜台代码 | 终端号 | 原值 |
| `business_type` | 卖出件 PAY / 退款件 REFUND | 按收 / 退文件 + 退款状态 | 按收 / 退文件 | 按交易类型 | `PAY / REFUND / REVERSAL / OTHER` |
| `trade_status` | 交易状态 / 退款状态 | 交易状态 / 退款状态 | 订单状态 | 交易类型 | 归一到 `SUCCESS / FAIL / CLOSED / UNKNOWN` |
| `trade_time` | 支付时间 / 退款申请时间 | 交易时间 | 交易时间 | 交易时间 | `yyyy-MM-dd HH:mm:ss` |
| `clearing_date` | 清算日期 | 账务日期 | 记账日期 | 清算时间 | `yyyy-MM-dd` |
| `merchant_order_no` | 商户订单号 | 商户订单号 | 商户流水号 / 订单号 | 商户订单号 | **关联键**，原值 |
| `channel_trade_no` | 支付宝交易号 / 银行订单号 | 微信订单号 | 银行流水号 | 银商订单号 / 流水号 | **关联键**，原值 |
| `merchant_refund_no` | 退款请求号 | 商户退款单号 | 商户流水号 | 检索号 | 退款记录必带 |
| `channel_refund_no` | 支付宝交易号（退款件） | 微信退款单号 | 银行流水号（退款件） | 流水号（退款件） | 退款记录必带 |
| `currency` | 默认 CNY | 货币种类 | 默认 CNY | 默认 CNY | ISO 4217 |
| `order_amount` | 订单金额(元) | 订单金额 | 订单金额 | 交易金额 | 元，`decimal(18,2)` |
| `transaction_amount` | 商家实收 / 退款金额 | 应结订单金额 / 申请退款金额 | 交易金额 | 清算金额 | **始终正数**，方向靠 `business_type` |
| `fee_amount` | 服务费 / 退服务费 | 手续费 | 手续费 | 手续费 | 微信退款件手续费可为负，符号规则待会签 |
| `settlement_amount` | 商家实收 | 应结订单金额 | 结算金额 | 清算金额 | 元 |
| `discount_amount` | 商家优惠 / 商家实退 | 代金券金额 / 充值券退款金额 | 优惠金额 | 优惠金额 | 元 |
| `product_name` | 商品名称 | 商品名称 | 交易类型 | 交易方式 | 原值 |
| `remark` | 付款备注 / 退款原因 | 商户数据包 / 费率备注 | 备注一 / 备注二 | 付款附言 / 备注 | 原值 |

**归一化规则（MUST 会签 N14）**：

1. **单位**：渠道侧与 22 字段 CSV 都是**元**；ITP 四类对账文件是**分**。换算落点按已落地事实（2026-11-16 实证）：归一化件入库保持元（`RECON_THIRD_BILL` NUMBER(18,2)），×100 发生在**比对 / 差异层**（`RECON_TXN_DIFF` NUMBER(18) 分），NEVER 在导入归一化时混算；四舍五入规则仍待 Q5 会签。
2. **方向与金额**：`transaction_amount` 恒正，退款靠 `business_type=REFUND` 表达；微信的 `REFUND` 同时可能是 `trade_status` 取值，两者 MUST 分开判定。
3. **一文件多条**：一张账单可同时含支付行与退款行，MUST 按行拆成多条 22 字段记录，NEVER 按文件聚成一条。
4. **渠道码表**：`payment_channel` / `payment_vendor` 由文件来源 + 商户号派生，取 Q16 会签后的码表。
5. **噪声剥离**：前导反引号、UTF-8 BOM、CRLF、首行汇总、末行说明、公式说明行 MUST 在归一化前剥离，剥离规则随 `formatVersion` 走。
6. **可追溯**：每条归一化记录 MUST 带 `sourceFileName` + `sourceRowNo` + `formatVersion`，差异可回溯到原件。

**2026-11-16 优化增补**：

7. **渠道账单最低字段**：《技术规范第 9 部分》表 37 规定渠道账单至少含 13 项（交易时间 / 商户号 / 设备号 / 支付渠道订单号 / 交易类型 / 交易状态 / 付款银行 / 总金额 / 支付渠道退款单号 / 退款金额 / 退款类型 / 商品名称 / 费率）——归一化前置校验按此判定账单完备性，缺项即 `REJECTED`。
8. **退款行比对键降级**：ITP 侧退款号三列现状收口后仍全 NULL（ADR-D92）——退款行比对键 MUST 降级为 `merchant_order_no` + 双金额对，`channel_refund_no` 仅透传展示不参与 JOIN；退款号字段缺失列入对支付中心的索取依赖。
9. **关联键唯一性**：`(merchant_order_no, channel_trade_no)` 现状非全局唯一（`PAY_TXN_DETAIL` 唯一键是 `(ORDER_NO, TXN_DATE)`，重试产生 1:N，落地以 SUCCESS 优先取行）——键唯一性与 1:N 取数规则 MUST 会签，NEVER 把启发式当已定契约。
10. **商户号归一**：`channel_merchant_no` 参与比对 / 汇总前 MUST 按 ITP 侧 `MerchantParty`（新旧商户对，ADR-D81）归一，映射随 Q16 码表会签。
11. **账单获取模型**：按 catalog 3.6（2026-11-16 版），渠道账单以「标准定义」编码索引，ACC 可按编号获取分渠道件；ITP 侧城交合并 CSV 与 ACC 侧分渠道件的归一化 MUST 同 `formatVersion`。
12. **退款件比对规则**：第三方账单的独立退款件（`business_type=REFUND`、金额恒正）对 ITP 侧做**存在性比对**（按 `merchant_refund_no` 回查 `F2F_REFUND` / `DAILY_TICKET_REFUND`），命中即预期差异（`diffType=REFUND`）、不出差错工单；ITP 侧 PAY / BUS 报全款、退款不冲减属甲方规格，NEVER 自行扣减、NEVER 逐期人工核。

**结构校验契约（补 6.2 的四元组；适用对象 2026-11-16 重定位）**：第三方账单件（城交 CSV 与渠道原始账单）方向为「第三方 / 城交 → ITP」，**不进 ITP↔ACC 文件面**，NEVER 为其定义 IR-01 / IR-07 绑定（定位见文件族绑定设计第五章）；上述结构校验字段（`sourceChannel` / `headerRow` / `columnCount` / `encoding` / `leadingQuote` / `summaryRow` / `footerRow` / `formulaRow` / `formatVersion`）的适用对象为**运营导入并经 ITP 转投 ACC 的 Excel 族**——`POS_TXN / QRCODE_TXN`（N16）与 `MERCHANT_TRANSFER`，IR-07 的 `rejectCode` 相应补 `HEADER_MISMATCH` / `COLUMN_COUNT_MISMATCH` / `ENCODING_MISMATCH` / `FORMULA_ROW_NOT_SKIPPED`。ITP 与 ACC 两侧 MUST 使用同一 `formatVersion`，版本不一致即 `2xxx` 业务拒绝，NEVER 靠容错解析蒙混过去。

**与其余接口的关系**：原始账单首行汇总值的交叉校验发生在 **ITP 导入 / 归一化环节（ITP 内部）**，NEVER 进入 IR-01；IR-03 / IR-09 的差异明细以 22 字段记录为比对单元，`orderKeys[]` 取 `merchant_order_no` + `channel_trade_no`；6.4 的商户入账确认件与归一化结果是**输入与消费**关系，NEVER 互相覆盖。

---

## 七、状态机与闭环

### 7.1 ITP 侧批次状态（白名单迁移表）

| 状态 | 允许的前置状态 | 触发 | 终态 |
|---|---|---|---|
| `DECLARED` | （新建，或 `REJECTED`） | IR-01 受理 | 否 |
| `UPLOADING` | `DECLARED` | 首份文件开始投递 | 否 |
| `ALL_UPLOADED` | `UPLOADING` | 全部 IR-02 已发 | 否 |
| `ACKED` | `ALL_UPLOADED` | 全部 IR-07 为 `RECEIVED` | 否 |
| `RECONCILING` | `ACKED` | 收到任一 IR-08（或 ACC 侧声明核对中） | 否 |
| `PASS` | `RECONCILING` | IR-08 `result=PASS` | 是（可关账） |
| `DIFF` | `RECONCILING` | IR-08 `result=DIFF` 或 IR-09 到达 | 否 |
| `ADJUSTING` | `DIFF` | IR-04 已提交 | 否 |
| `CLOSED` | `PASS` / `ADJUSTING` | IR-10 `APPROVED` 且双方账平 | **是** |
| `REJECTED` | `UPLOADING` / `ALL_UPLOADED` / `ACKED` | 任一 IR-07 为 `REJECTED` | 否（可修复后重入） |
| `EXPIRED` | 任意非终态 | 超过 SLA 且 IR-05 查询仍无结果 | 否（转人工） |

**规则**：只允许上表列出的前置状态继续处理，其余一律拒绝，NEVER 写成「非终态即可处理」；`CLOSED` 一律拒绝流出；状态变更用「当前状态 + 目标状态」的 CAS 更新，影响行数不为 1 即判并发冲突。收到终态 IR-08 时先 `ACKED→RECONCILING` 再按 `result` 迁 `PASS / DIFF`，两步在同一事务内完成，NEVER 跳过白名单直接落地终态。终态账期重开 MUST 走 IR-06 + 新 `revision`。

**与既有内部状态机的关系（2026-11-16 补）**：现网 `RECON_BATCH` 另有一套白名单状态机 `CREATED→EXPORTING→PARTIAL→ALL_SOURCE_COMPLETED→GENERATING→UPLOADING→SUCCESS / FAILED`（文件生成 / 投递生命周期）。本表 11 态是**对外闭环生命周期**，两者语义不同（现网 `UPLOADING`=投递中瞬态，与本表 `UPLOADING` 撞名不同义；现网终态 `SUCCESS` ≈ 本表 `ALL_UPLOADED` 而非终态），**MUST NOT 共用同一状态列**。落地映射：内部 `SUCCESS` → IR-02 发毕推进本表 `ALL_UPLOADED`；内部 `FAILED` → 补偿后入本表 `REJECTED` 或 `EXPIRED`。取值真源同步 `docs/business/dictionary-detail.md` §6.1「对账批次状态机」，状态机清单同步 `docs/domain/state-machines.md`。

### 7.2 ACC 侧状态（供 IR-05 / IR-08 对齐）

`NOT_DECLARED → DECLARED → RECEIVING → VERIFIED / REJECTED → CHECKING → PASS / DIFF → CLOSED`（`CHECKING` 对应 IR-08 `result=PENDING`）。
IR-08 的 `result`、`IR-05` 的查询返回，MUST 使用同一套取值，NEVER 两侧各定义一套码表。

### 7.3 差错工单状态

`OPEN → SUBMITTED → ACC_REVIEW → APPROVED / REJECTED / RESUBMIT_REQUIRED → CLOSED`。
`REJECTED` 可回到 `OPEN` 并生成新 `revision`；`CLOSED` 为终态。工单落库 MUST 采用「业务键唯一索引 + `DuplicateKeyException` 幂等 + CAS 状态迁移」形态（参照 `ACCOUNT_EXCEPTION_TICKET`），recon 域自建同构表，NEVER 跨域写 account-server 表；关单 / 裁决 MUST 走状态迁移，NEVER 手工 UPDATE。

### 7.4 并发控制

状态推进 MUST 依赖数据库唯一键 + CAS，NEVER 依赖进程内互斥量。现状批次编排靠进程内锁并要求单副本，目标态多副本部署时该假设不成立，需在落地方案中替换（见第九节差距表「数据库 CAS 替代进程内锁」行）。

### 7.5 补偿路径

| 异常 | 处置 |
|---|---|
| IR-07 / IR-08 超时 | IR-05 查询补齐；查到已处理则按返回推进，查不到则继续等下一个退避周期 |
| 文件校验失败 | IR-07 `REJECTED` → 批次 `REJECTED` → IR-06 申请 → 新 `revision` 重传 |
| 业务拒绝 `2xxx` | 不重试，按状态机补偿或转人工 |
| 超 SLA 未闭环 | 置 `EXPIRED`，人工介入，**NEVER 自动重跑整批** |
| 生成前失败（无 IR-01 可发） | 内部 FAILED 不等价于对外任何状态——账期对 ACC 不可见；ACC 与 ITP MUST 按 IR-11 日历 + SLA 对「缺声明」各自告警，补产成功后才发 IR-01 |
| 终态账期重开重算 | 重算前 MUST 校验账期是否落在跨源重叠 / 旧表窗口（ADR-D160：手工补跑旧账期曾致 TVM 24 笔 + BOM 151 笔双算）；命中则只允许按已生成文件原字节重投（IR-06 `reasonCode=SOURCE_RECALC_FORBIDDEN`）或转人工 |
| 补偿动作本身失败 | 任何补偿（补录回执、重推 IR-02、重算汇总）区分可自愈 / 不可自愈（ADR-D91）；影响 0 行 NEVER 当成功，不可自愈转 IR-04 差错工单 |
| 补扣收敛事件 | `DEBIT_STATUS` 收敛为 SUCCESS 且账期已出账（退避 720 分钟 × 5 次，可晚数天）→ **不再重报该单边行**（EXP 导出谓词排除已收敛行，2026-11-16 定稿），补收经 IR-04 申报（`reasonCode=LATE_DEBIT`）+ 新账期 PAY 首次计入实收 |
| 双方状态不一致 | 以 IR-05 查询结果为准做对账，不各自改本地状态掩盖差异 |

---

## 八、差异与差错口径

### 8.1 差异两维模型（解决 G2 的编码冲突）

现状把「谁多了一笔」和「为什么差异」压进一个 `01~04` 编码，且编码与文字说明顺序相反（文件级契约 Q3、落地侧另有 `ITP_ONLY / THIRD_ONLY` 一套）。目标态 MUST 拆成两个独立字段：

| 维度 | 取值 | 说明 |
|---|---|---|
| `diffDirection` | `ITP_ONLY / ACC_ONLY / BOTH` | 差异方，回答「谁有这笔」 |
| `diffScope` | `ACC / THIRD_PARTY` | 比对参照系：批 A / B 对 ACC、批 C 对第三方（2026-11-16 定稿，批 C 必带，随协议整体会签） |
| `diffType` | `MISSING / AMOUNT / STATUS / LATE / DUPLICATE / REFUND / FEE / OTHER` | 差异类型，回答「差在哪」 |
| `responsibleParty`（可选） | `SOURCE / CHANNEL / TIMING / MANUAL` | 责任归属，供差错工单分流 |

三者正交组合，**NEVER 再定义「01=ITP无」这类同时承担方向与原因的单字段编码**。差异原因码的最终字典 MUST 与 ACC 会签（N8）。

**三套编码映射（N8 会签件，2026-11-16 补）**：既有编码 `01 ITP无 / 02 第三方无 / 03 金额不一致 / 04 扣款失败`、落地 `RECON_TXN_DIFF.DIFF_SIDE = ITP_ONLY / THIRD_ONLY / AMOUNT_MISMATCH / STATUS_MISMATCH`、本表两维字典三者并存。映射建议：`01→(ITP_ONLY, MISSING)`；`02→比对 ACC 时 (ACC_ONLY, MISSING)`，批 C 比对第三方时即「第三方有」的镜像语义；`03→(BOTH, AMOUNT)`；`04→(BOTH, STATUS)`（失败细节由 TXNDIFF 第 22 段承载）。**批 C 的比对对象是第三方而非 ACC**，`diffDirection` 以 ACC 为参照系会语义错位——`diffScope`（`ACC / THIRD_PARTY`）**已定稿为批 C 必带字段**（`THIRD_ONLY` 限定值方案不采用，保持字典正交），随协议整体会签生效。

**结算状态分流（2026-11-16 补）**：琴岛通工作簿的挂账 / 补账 / 拒付 / 可疑 / 调整是**账期级结算台账状态**，NEVER 塞进 `diffType`（避免再造 G2 式混合编码）：补账 → `adjustType=SUPPLEMENT` 已覆盖；拒付（CHARGEBACK）、挂账（PENDING_SETTLEMENT）进 IR-04 `reasonType` 值域候选；可疑 / 调整属琴岛通侧输入，其反馈文件若立项 MUST 复用 IR-09 通道与同一字典。

**预期系统性差异预置（2026-11-16 补）**：以下为**结构性、固定方向**的差异，IR-03 / IR-09 应按预置类目表达、NEVER 逐期生成差错工单——①退款（ITP 侧 PAY / BUS 无退款段、第三方账单有独立退款件，比对走存在性规则）；②DETAIL 应收 vs PAY 实收的未结清超时费；③离线码自定义超时费（1200 秒 / 300 分，无甲方出处，Q2 裁决前 NEVER 调整相关配置键）。

### 8.2 判定原则

- 比对 MUST 基于双方共同认可的业务键（订单号 / 交易流水号 / 逻辑卡号 / 票卡交易序列号），NEVER 只按金额或按日期聚合比对；
- 单边账、AFC 晚传交易、T+4 判定等规则的**判定细则由甲方提供**，本设计只预留 `diffType` 与工单位置，NEVER 代为定义；
- 「预付费订单不计应收、以交易完成支付为应收条件」等既有业务口径继续有效，本设计不改变；
- 明细走文件、汇总与状态走接口：IR-03 / IR-09 只带分类计数与校验值，NEVER 在 JSON 里塞逐笔差异。
- **判定框架已有标准出处（2026-11-16 补）**：《技术规范第 9 部分》定义了完整规则链——结算周期 T0=4 天、单边交易判定（周期到达 + 缺一方记录 + 当前日期−交易日期 > T0）、**每天 9 点定点扣费批量匹配**、人工更新（BOM / 自助）优先扣费、T1/T2/T3 阈值；会签时以该框架为基线确认适用性，Q14 剩余项为「线网最高票价或者 1」的「1」单位与口径适用边界。
- **ITP 侧前置事实**：非终态单边交易不参与比对（ADR-D170 落地口径，当前仅记日志、sys_job 405 未注册）、face-pay 结构性漏账（线路段 backfill、探针仅告警）为已知差异归因基线（ADR-D160 §五），IR-09 差异人工归因时先对齐该清单，NEVER 当新差异误判。
- **商户维度**：`ATTRIBUTABLE_PARTY / RECEIVING_PARTY` 应收 / 实收商户可不等——「应收 ≠ 实收商户」是 IR-03 差异的潜在 `diffType` 来源；22 字段映射与商户入账件的「商户编码」列取值源（`RECEIVING_PARTY`）随 N14 确认。

### 8.3 差错闭环

差错工单 MUST 可回溯到 `settlementDate + batchNo + orderKeys[]`，并带 `revision` 与处理时限。补报 / 冲正 MUST 追加新版本，NEVER 修改已回执的历史文件。

---

## 九、与现状的差距（只写差距，不写落地细节）

| 目标态要求 | 现状 | 类别 | 建议分期 |
|---|---|---|---|
| 逐文件接收回执 IR-07 | 无，成功判据只到远端 rename + 字节数回查 | 闭环 | P0 |
| 批次声明 manifest / IR-01 | 无，ACC 靠目录与前缀匹配取件 | 闭环 | P0 |
| 统一 `settlementDate` + 日历 IR-11 | 三套日切并存 | 口径 | P0（会签前置） |
| 一致的金额单位、段数、格式版本 | 元/分、7/21/22 段、编码多处冲突 | 口径 | P0（会签前置） |
| 双向签名 + 幂等 `requestId` + 防重放 | 控制面免签 / 静态 sign，无请求级幂等键 | 安全 | P0 |
| 差异两维字典与双向下发 | 仅 ITP 侧 TXNDIFF，且链路默认关闭、无生产触发方 | 业务 | P1 |
| 差错工单 IR-04 / IR-10 | 无 | 业务 | P2 |
| 数据库 CAS 替代进程内锁 | 单副本硬前提 | 工程 | P1 |
| `revision` 重传与终态重开 | 批次终态后只能换批次号补跑 | 工程 | P1 |
| 财务上传件共享给 ACC 的独立批次、manifest 与回执（6.4） | 整理版只写「ITP 通过 FTP 定时传输至 ACC 相应路径」，路径 / 凭据 / 频率原文留空，无清单无回执 | 业务 | P0（依赖 Q8 / N11 / N12） |
| ACC 取件精确匹配改造（`.uploading` / `.manifest.json` 防前缀通配误取） | ACC 按 `ITP.BUS*` 前缀通配取件即删（探针实证 30~45 秒） | 闭环 | P0 |

**分期建议**：
- **P0｜协议层**（不改文件格式）：会签账期 / 单位 / 段数 / 编码 / 签名 / 凭据 → 补 manifest、IR-01、IR-02、IR-05、IR-07、IR-08、鉴权与幂等，先做到「每个账期有回执、有结果」；财务上传件按 6.4 以独立 `MERCHANT_TRANSFER` 批次接入，同样先做到「有清单、有回执」；
- **P1｜双向差异**：IR-03、IR-06、IR-09 + 差异两维字典 + 数据库 CAS + revision 重传（IR-03 首个验收用例可由 face-pay 漏账探针升格提供——探针结果落库即真实差异源，与既有缺口闭合绑定）；
- **P2｜差错与关账**：IR-04、IR-10、IR-11 + 差错工单、SLA 报表、关账对账。

> 现状事实的权威在 `docs/business/recon.md`、`docs/business/recon-thirdparty.md`；本表是目标态与现状的对照，NEVER 当作线上实况清单使用。

---

## 十、落地约束（保证本设计可在本仓实现）

本设计是**对外协议**，但 ITP 侧的实现 MUST 遵守本仓既有架构，NEVER 为落地本协议引入新中间件：

- 服务间调用统一走 `rpc` 模块的 `ProxyWebClient` + `service.*.url` HTTP 直连，无 Feign / Dubbo / 注册中心；新增内部接口 DTO 放 `model`、Client 方法放 `rpc`；
- **不使用 Redis / Kafka / RabbitMQ**；异步与重试一律「落库状态 + 扫表重试」，并发控制用唯一索引 + `DuplicateKeyException` + CAS 兜底；
- 状态机用白名单 + 迁移表 + 终态短路，NEVER 散落在 `if` 里比状态字符串，并配白名单单测；
- 事务方法内 NEVER 发起 RPC / 网络调用，跨模块写先远端后本地、远端失败留中间态等补偿（见 `docs/architecture/transaction-and-consistency.md`）；
- 涉及签名 / 密钥 / 加密逻辑的改动 MUST 人工复核安全合规（`AGENTS.md` 的「4.2 安全红线」）；
- 生产环境的所有操作 MUST 二次确认，NEVER 自动执行；
- 凭据口径：仓库内明文配置为真源，生产值补齐后同步配置与本文所引文件级契约，日志不打印明文、截图打码。
- **IR 控制面调用 MUST 落库后异步发送**（落库状态 + 扫表重试），NEVER 串进 `runDailyBatch` 同步链路（现状总超时 240 秒、实测整批约 60 秒）——控制面慢 / 重试会顶爆窗口、连累 `sys_job_log` 记假失败。
- **ACC→ITP 接收端点 MUST 独立鉴权**：对账内部接口的 `X-Recon-Token` 鉴权现状整段删除（问题清单 P0-3），上线前 MUST 恢复；控制面端点鉴权在位是 P0 上线核对项，NEVER 以「开发测试阶段」暂缓。

---

## 十一、待会签清单（本文不裁决）

### 11.1 沿用文件级契约的阻塞项

以下条目只标编号与对接口设计的影响，内容以 `docs/business/recon-file-catalog.md` 第七章为准，NEVER 在此复述：

| 编号 | 对接口设计的影响 |
|---|---|
| Q3 | 差异原因编码与「差异方」口径不等价，直接决定第八节字典与 IR-03 / IR-09 字段 |
| Q4 | 渠道编码归属互换，影响 manifest 与差异分类的码值 |
| Q5 | 元 / 分不统一，决定 6.3 换算落点与金额字段类型 |
| Q6 | 签名与公共参数大小写 / 拼写冲突，决定 3.4 能否定稿 |
| Q8 | 三处 FTP 口径不一致且生产凭据全空，决定第二节环境与隔离 |
| Q9 | 银联 POS / 收款码件首行「小数点后 2 位分」与汇总项「(元)」自相矛盾，决定 6.4 / 6.5 的单位与结构校验取值 |
| Q10 | 三套日切，决定 4.1 能否收敛为唯一 `settlementDate` |
| Q11 | DETAIL / TXNDIFF 段数 21 vs 22，决定 `formatVersion` 校验规则 |
| Q12 | 账单文件命名占位符歧义，决定 6.1 命名规则 |
| Q14 | 差异判定细则与超时费单位：《技术规范第 9 部分》已给出框架（T0=4 天结算周期、每天 9 点定点扣费、人工更新优先、单边定义、T1/T2/T3），剩余为适用性确认与「线网最高票价或者 1」的「1」单位，决定 8.2 判定原则能否关闭；「线网最高票价」落地候选 = para-server 0004 费率文件导入或新参数四件套（均需本地兜底，para 不可达不得算 0） |
| Q15 | 商户入账确认件的上传方角色（财务 / 运营）与 ACC 侧路径、频率，决定 6.4 的责任边界与投递节奏 |
| Q16 | 渠道编码清单（2026-11-16 版）：标准定义列已与 3.2 对齐、旧冲突主体解决；剩余——城交侧编码映射（行 3 释义「云闪付05」）、行 16 笔误、行 5/11/7~9 空待补、「提供第三方账单」列语义与 3.2 文件来源口径（云闪付/龙支付/钱包/支付宝出行），决定 manifest 的 `paymentVendor`、差异分类码值、**账单比对渠道范围**与商户入账件「支付方式」校验能否定稿 |
| Q17 | 2026-09-28 真实账单样例与整理版原文 5 处不一致（银联两件同为 26 列、首行无单位说明、建行收 42 / 退 23 列、微信收 27 / 退 29 列、汇总表公式说明行），决定 6.5 的 `formatVersion` 与结构校验能否定稿 |
| Q18 | 《技术规范第 9 部分》与《需求分析 v2.1》（2025-07 版）均定义 **ACC→ITP 反向对账文件族**（单程票售票交易对账文件、储值卡充值交易对账文件，ACC 用其与 ITP 在线订单核对）；整理版与 catalog 均未收录、落地无实现，决定对账是否需要 ACC→ITP 文件族与批 F 绑定 |
| Q19 | EXP 第 11 段「订单异常类型」三套编码并存（甲方原文 1~15、我方库内 0~5、实体卡 0/1/2，ADR-D162），甲方 `5` 与我方 `5` 撞号且现网原样输出内部码值，决定 EXP 差异分类与自检规则能否定稿 |

### 11.2 本设计新增的待会签项

| 编号 | 待定内容 | 影响 |
|---|---|---|
| N1 | 日切基准、D+2 出账与 D+3 / D+4 / D+5 SLA | 4.1 / 4.3、第七节超时判定 |
| N2 | 控制面地址前缀、接入账号、测试与生产隔离方案 | 2.3、第五节全部接口 |
| N3 | 时间戳窗口、nonce 有效期、连接 / 响应超时、重试上限。建议值：重试上限 3（与现状 `max-retry=3` 对齐）；双方从 ACLC 时钟源校时（要求时差 ≤100ms）后时间戳窗口可收紧至分钟级；IR-02 → IR-07 回执 SLA 建议 T+1 日内（取件窗口实测仅 BUS 族 30~45 秒，NEVER 按它定 SLA） | 3.3 / 3.5 |
| N4 | `retCode` 分段与 `rejectCode` 值域是否沿用甲方既有码表 | 3.2、IR-07 |
| N5 | 签名算法、字符集、密钥、`providerId` / `deviceId` / `signType` 取值及密钥轮换。建议值：《技术规范第 9 部分》表 38 已定 `providerId` 编码（05=ACC / 06=ITP）、`signType` 枚举（00 不签名 / 01 sha1withrsa / 02 MD5）与签名组串规则（非空参数按字段名 ASCII 排序 QueryString 拼接、空值不参与、sign URL Encode、允许升级加参数）——建议控制面 `providerId` 沿用 06 / 05、`signType=01 sha1withRSA`（禁 00 免签；MD5 为弱算法需安全合规复核） | 3.4、全部接口（安全合规复核前置） |
| N6 | `batchNo` / `requestId` / `ticketNo` 编号规则与长度 | 4.2、第五节 |
| N7 | manifest 文件名、格式与是否使用 `.ok` 标志文件 | 6.1、IR-02 |
| N8 | 差异两维字典最终取值与责任归属码 + 三套编码映射（01~04 / DIFF_SIDE / 两维）、`04 扣款失败` 归类、FEE 判定基准（费率 / 计算基数 / 退款件是否计费，与 Q16 渠道码表联动）；`diffScope` 已定稿（§8.1，随协议整体会签） | 8.1、IR-03 / IR-09 |
| N9 | 差错责任归属、关账时限、终态账期重开审批流 | 8.3、IR-04 / IR-10 |
| N10 | 接口正式编号（替换 `IR-xx`）、协议版本号、生效与灰度策略 | 全文 |
| N11 | 商户入账确认件的对外报备：ITP 侧已定稿财务上传（6.4），整理版原文「运营导入」差异需向甲方 / ACC 报备修订，`uploadedBy` 取值随会签 | 6.4、IR-01 的 `uploadedBy` |
| N12 | 共享形态：ITP 侧已定稿 **P0 取 A 原样转发**（6.4），请 ACC 确认；B（转换后投递）待元 / 分、码表、公式链全部会签后另议 | 6.4 全节、manifest 字段 |
| N13 | ACC 侧该件的消费用途（仅展示 / 参与清分 / 与 49~52 指标联动），及其与四类对账文件是否共用账期 | 6.4 性质判据、IR-08 取值 |
| N14 | 第三方账单 → 22 字段的归一化规则（字段映射、单位换算落点按已落地事实、手续费符号、`business_type` 与 `trade_status` 判定、一文件多条拆分、退款行比对键降级、退款件存在性比对规则、关联键 1:N 取数、`MerchantParty` 商户号归一、商户编码列取值源、表 37 最低字段校验） | 6.5 全节、IR-03 / IR-09 比对单元 |
| N15 | 逐文件族的 `recordCount` / `amountTotal` 计算口径（加总段位、PAY 不适用语义、`amountUnit`）+ 第 3 段应收口径候选 `ORIGINAL_FARE`、22 段第 17 段数据源澄清与第 20/21 段数据源时点、BOM 处理组 19/20 拆分、payType=0 落库口径（归属 tvm-bom-pay 域裁决）；已定稿：空文件口径（§8）、EXP 已补收行不重报（§7.5 / 绑定稿 §2.1）、第 22 段恒 0（绑定稿 §2.4）、PAY 校验和列保留内部（绑定稿 §2.2） | 文件族绑定设计 §2.1~2.5 / §4 / §8、IR-01 `files[]`、IR-02、IR-07 校验 |
| N16 | 新增 `fileType=POS_TXN / QRCODE_TXN`、`batchType=OPERATION_IMPORT`、导入族 `settlementDate` 口径（文件名日期 vs 运营日）与共享形态（建议与 N12 合并裁决） | §4.2 / §5.4 值域、文件族绑定设计 §3、IR-08 取值、Q8 路径凭据 |

> 任一项会签闭环后，MUST 直接改写对应正文并删去该行，NEVER 追加「已确认」补丁句。

---

## 十二、验收口径

1. **协议一致性**：以真实账期文件逐段核对段数 / 单位 / 编码，manifest 四元组与文件实测值完全一致，IR-07 的 `checkedValues{}` 回传一致。
2. **闭环完整性**：每个账期 ITP 与 ACC 双侧状态一致；每份文件恰有一条 `RECEIVED` 回执；每个批次当前 `revision` 恰有一条终态 `IR-08`（重传后旧 `revision` 终态标记 `superseded`、历史可查）；`PENDING` 必须有 `expectedBy`。
3. **幂等**：同一 `requestId` 重放、同键不同内容、重复 IR-02 三类用例分别返回首次结果 / `3xxx` / 首次结果，且不产生重复副作用。
4. **重传**：`revision+1` 后双侧能检出版本变化，旧 revision 被拒，历史版本可查，终态账期重开需 IR-06 受理。
5. **异常**：回执丢失靠 IR-05 补齐；`2xxx` 不重试；超 SLA 进 `EXPIRED`；业务拒绝与网络不可达分流正确。
6. **安全**：免签请求被拒、过期时间戳被拒、重放 `nonce` 被拒，日志与截图无密钥 / 口令明文。
7. **不回归**：P0 阶段不改变四类文件的段序与业务口径；ACC 取件的文件匹配与解析逻辑无需改造即可继续取件，唯一新增改造点是**取件删除前先落 IR-07 回执**（G1 收口点）。
8. **财务上传件独立闭环**：`MERCHANT_TRANSFER` 批次与四类对账文件不混批；原件 sha256 与 manifest 一致、说明行跳过、公式差异只留痕不被抹平；IR-08 只回收妥结论；重传只能由重新上传触发 `revision+1`。
9. **不替财务改数**：取方案 A 时投递字节与上传原件逐字节一致；取方案 B 时每个换算后的数值都能回溯到原件单元格与换算规则版本。
10. **账单归一化**：用 2026-09-28 样例跑通 6.5 的 22 字段映射，表头行 / 列数 / 编码 / 前导反引号 / 公式说明行 / 末行说明全部按 `formatVersion` 正确剥离；归一化记录数、金额合计与原件首行汇总值一致；版本不一致时返回 `2xxx` 而非容错解析。
11. **通配取件防护**：`.manifest.json` 与 `.uploading` 不被 ACC 前缀通配取件进程取走删除（或该族 manifest 只走控制面），BUS 族实证场景回归通过。
12. **接收端点安全**：ACC→ITP 控制面端点独立鉴权在位、专用异常处理器在位（未捕获异常返回 `5xxx`），发送侧按 `retCode` 判结论。

---

## 十三、维护约定

- 本文只写 **ITP↔ACC 对外接口契约**。字段名 / 段序 / 文件命名 / 账期原始口径变动改 `docs/business/recon-file-catalog.md`；落地现状改 `docs/business/recon.md`、`docs/business/recon-thirdparty.md`；财务 / 运营上传与导入需求改 `docs/business/recon-merchant-transfer-import.md`；综管台上传操作改 `docs/ops/对账文件上传命名与替换说明.md`；**NEVER 各处各写一份**。
- 逐文件族的 manifest 内容、四元组计算口径、IR 时序与相位绑定 → `docs/business/recon-acc-file-family-design.md`；本文协议字段变动 MUST 同步该文对应族，NEVER 两处各写一份。
- 甲方规格原文目录 `docs/接口规范文档/` 只读，NEVER 在其中新增派生 `.md`。
- 接口编号、URL、状态取值、码值、`retCode` 分段、账期口径变动，MUST 同步本文与 `docs/business/README.md` 业务域清单索引行，并按 `docs/提示词/工程与文档规范.md` 的回写义务处理。
- 状态取值变动 MUST 同步 `docs/business/dictionary-detail.md` §6.1「对账批次状态机」，状态机载体 / 白名单变动 MUST 同步 `docs/domain/state-machines.md`，NEVER 只改一处。
- 本文「状态：目标态设计、未落地」的定性在首批接口上线前不得删除；上线后把「怎么落地的」写进对应业务域文档，NEVER 把实现细节回填到本文。
