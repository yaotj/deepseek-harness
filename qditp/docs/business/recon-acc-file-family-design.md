# ITP ↔ ACC 对账文件族绑定设计（逐文件族 · 数据面 × 控制面）

> **状态：目标态绑定设计，未落地、代码零实现、待甲方 / ACC 会签**（2026-11-16 建立）。
> 本文把《对账文件》既有约定的**每一族文件**（数据面）**绑定**到《接口设计稿》的控制面协议上：每族给出 manifest 内容、校验四元组的计算口径、IR 接口时序、批次归属与激活相位（P0 / P1 / P2）、与既有约定的差异清单。
> 按 `docs/business/recon-acc-interface-design.md` 2.4 的硬约束：**本文与协议稿在会签前均不得作为生产实现依据**。
>
> **与既有文档的边界（单向指针，NEVER 拿本文当副本）**：
> - 文件名、段序、段数、字段含义、账期原始口径、FTP 凭据 → `docs/business/recon-file-catalog.md`（数据面唯一权威）；本文 NEVER 复述字段清单，只定义「哪一段进 `amountTotal`」这类**绑定口径**；
> - 接口字段、公共协议、状态机、差异字典 → `docs/business/recon-acc-interface-design.md`（控制面唯一权威）；本文 NEVER 复述接口字段表；
> - 落地现状（生成链路 / 调度 / 已知坑）→ `docs/business/recon.md`、`docs/business/recon-thirdparty.md`；
> - 商户入账件的需求侧（导入流程 / 模板校验 / FR 清单）→ `docs/business/recon-merchant-transfer-import.md`；
> - 琴岛通上传契约草案 → `docs/business/recon-qdt.md` 六·A（C1~C5）；其 `QDT_*` 文件族待会签闭环后进 catalog 并在本文补绑定章，NEVER 提前绑定；
> - 待澄清真源 → catalog 第七章 Q1~Q19；协议待会签 N1~N16（N15 / N16 为本文新增）→ 接口设计稿 §11.2。
>
> **本文不定义**：ITP 内部表结构 / 类名 / SQL / 服务接口、生产凭据、甲方只读 docx 内容、任何新文件族的字段清单（那是 catalog 的事）。

---

## 一、文件族总表（对照整理版文件总表逐族绑定）

| 整理版条目 | 方向 | fileType | batchType / sourceType | 账期口径 | 批次归属 | 相位 | 控制面绑定 |
|---|---|---|---|---|---|---|---|
| 1.1 `ITP.EXP.yyyyMMdd` | ITP→ACC | `EXP` | `NORMAL` | T-2 运营日窗口（Q10） | 批 A | P0 | IR-01 / 02 / 05 / 07 / 08 |
| 1.1 `ITP.PAY.yyyyMMdd` | ITP→ACC | `PAY` | `NORMAL` | 同上 | 批 A | P0 | 同上 |
| 1.1 `ITP.BUS.yyyyMMdd` | ITP→ACC | `BUS` | `NORMAL` | 同上 | 批 A | P0 | 同上 |
| 1.1 `ITP.DETAIL.YYYYMMDD` | ITP→ACC | `DETAIL` | `NORMAL` | 运营日 T~T+1（Q10 待对齐） | 批 B | P0 | 同上 |
| 1.1 `ITP.TXNDIFF.YYYYMMDD` | ITP→ACC | `TXNDIFF` | `NORMAL` | 自然日（Q10 待对齐） | 批 C | P1 | IR-01 / 02 / 03 / 05 / 07 → IR-09 |
| 1.2 第三方账单文件（23 行 + 22 字段 CSV） | 第三方 / 城交→ITP | **不设**（不进 ITP↔ACC 文件面） | — | — | — | —（比对单元） | 无（见第五章） |
| 1.3 ITP 业务文件导入 | 原文为空（Q1） | 占位 | — | — | — | 待 Q1 | 待 Q1 |
| 2.7 对账汇总文件 | 原文「格式参考」为空（Q1） | 占位 | — | — | — | 待 Q1 | 待 Q1 |
| 1.4 `银联pos交易YYYYMMDD.xls` | 运营导入→ITP→ACC | `POS_TXN`（N16） | `OPERATION_IMPORT`（N16） | 文件名日期（N16 待对齐） | 批 D | P1 | IR-01 / 02 / 05 / 06 / 07 → IR-08（只收妥） |
| 1.4 `银联收款码YYYYMMDD.xls` | 同上 | `QRCODE_TXN`（N16） | 同上 | 同上 | 批 D | P1 | 同上 |
| 1.5 `商户号平台转账金额YYYYMMDD.xls` | 财务上传→ITP→ACC | `MERCHANT_TRANSFER` | `FINANCE_CONFIRM / FINANCE_UPLOAD`（6.4） | 清分日期（Q15 / N13 待对齐） | 批 E | P0（依赖 Q8 / N11 / N12） | IR-01 / 02 / 05 / 06 / 07 → IR-08（只收妥） |
| Q18 反向对账文件族（单程票售票 / 储值卡充值交易对账文件，ACC→ITP） | ACC→ITP | 占位 | 待 Q18 | 待 Q18 | 批 F（占位） | 待 Q18 | 若立项：IF7A-02 文件通知 + IR-09 / IR-05 承载差异 |
| 5.1 参数文件 | ACC→ITP | 不设 | — | — | — | — | 无（见第六章） |
| 5.2 逻辑卡号文件 + IF7B-01 | ACC↔ITP | 不设 | — | — | — | — | 无（见第六章） |
| 5.3 HCE 黑名单文件 | 原文引用缺失（Q1） | 占位 | — | — | — | 待 Q1 | 待 Q1 |

> 批次分群 A~E 是本文新增的建议决策（待会签，见 §2.0 与 §十；批 F = Q18 反向对账文件族占位，未绑定）；「相位」沿用接口设计稿 §九的 P0~P2 分期，本文 NEVER 改动分期结论。

---

## 二、ITP 交易文件族绑定（EXP / PAY / BUS / DETAIL / TXNDIFF）

### 2.0 通用绑定与批次分群（建议值，待会签）

**批次分群**：

| 批 | 文件族 | 账期口径 | 相位 |
|---|---|---|---|
| 批 A | EXP + PAY + BUS | T-2 运营日窗口（现状 T 日 2 点出 T-2 账期，沿用） | P0 |
| 批 B | DETAIL | 运营日 T~T+1 | P0 |
| 批 C | TXNDIFF | 自然日 | P1 |
| 批 D | POS_TXN + QRCODE_TXN | 文件名日期（N16 待对齐） | P1 |
| 批 E | MERCHANT_TRANSFER | 清分日期 | P0 |

**分群理由**：IR-01 的 `settlementDate / calendarType / windowStart / windowEnd` 是**批级**字段，一个批次内所有文件 MUST 同一账期口径；现状三套日切并存（Q10），在 Q10 收敛前按口径分批，**NEVER 混批**。分批只影响 IR-01 / manifest 的声明粒度，NEVER 改变既有投递路径与文件名。**批 A 实证**：现状四类（含 7 段 DETAIL）共用同一 T-2 窗口与批次——批 B 独立是 Q11 切 22 段口径后的目标态，P0 若仍产 7 段则 DETAIL 随批 A。批 F（ACC→ITP 反向对账文件族）为 Q18 占位，NEVER 提前绑定。

**窗口计算责任（2026-11-16 补）**：账期窗口 MUST 由 recon-server 按 IR-11 日历统一计算后随抽取指令（`windowStart / windowEnd`）下发，源服务 NEVER 自取当日 / 自算窗口；窗口切分列逐源为「出站时间（gate）/ 支付完成（face-pay `PAID_TMS`、daily-ticket `PAY_DATE`）」，日历变更的兼容责任收在消费端（recon-server）。

**批级 `revision`**：批级 revision = 本批当前生效文件的最新 revision；任一文件重传（revision+1）→ 批级 revision 同步 +1，IR-01 / IR-08 按新 revision 出声明与结果。

**manifest 双载体**：批级 = IR-01 的 `bizData`（含 `files[]`）；文件级 = `<fileName>.manifest.json` 同名旁挂（命名与格式建议值见 §7.4，N7）+ IR-02。两者同键（`batchNo + fileType + revision`）、数值 MUST 完全一致（接口设计稿 §12.1）。

### 2.1 单边交易明细 `ITP.EXP.yyyyMMdd`

| 绑定项 | 建议口径（待会签 N15） |
|---|---|
| 批次归属 | 批 A；`settlementDate` = 文件名日期 = T-2 账期日 |
| recordCount | 全部数据行（无表头、无汇总行，逐行计数）；唯一来源 gate-txn-pay，甲方 15 类异常中 5/6（自助补站）与 7~15（TVM/BOM）当前无源供数——行数结构性偏小 / 为 0 是已知状态，IR-08 NEVER 因「EXP 行数少」判 DIFF |
| amountTotal | 第 4 段「实际扣款金额」逐行加总（实收口径）；第 3 段「订单金额」应收口径是否并列上报待 N15 |
| 族扩展 manifest 字段 | 无 |
| IR 时序 | IR-01（批 A 声明，含本件实测四元组）→ rename → IR-02 → IR-07 → IR-08；重传经 IR-06（P1 起） |
| ITP 侧自检 | 第 11 段订单异常类型：**三套编码并存且甲方 `5` 与我方 `5` 撞号、现网原样输出内部码值**（ADR-D162，Q19）——1~15 值域校验以映射会签为前提，会签前 NEVER 按值域拒行；第 12 段支付方式：甲方文档无交易明细支付方式码表（「同交易明细」悬空引用）且实测 null 占比极高——空键段合法、照常入账，NEVER 设为拒收理由（recon.md B10） |
| 行生命周期 | 单边行补收成功（`DEBIT_STATUS` 收敛 SUCCESS）后**全仓无清除 `ORDER_EXP_TYPE` 语句**——若不处理，同一笔会在后续 revision 重报重复进 EXP、且 PAY 过闸组首次计入造成双算。**定稿（2026-11-16）：EXP 导出谓词排除「单边行且 `DEBIT_STATUS` 已收敛 SUCCESS 且已出账」的行，补收经 IR-04（`LATE_DEBIT`）申报，NEVER 重报**；22 段第 20/21 段补扣字段随 Q11 立项后承载补扣单号 / 金额（S4 前恒空） |

### 2.2 统计汇总 `ITP.PAY.yyyyMMdd`

| 绑定项 | 建议口径（待会签 N15） |
|---|---|
| 批次归属 | 批 A |
| recordCount | 全部数据行 |
| amountTotal | **不适用**——PAY 是多金额列统计汇总（段 13 / 15 / 17 / 19 / 21），无单一金额列；建议 `amountTotal="N/A"`，ACC 侧跳过金额比对、四元组其余三项照常校验 |
| 族扩展 manifest 字段 | 无 |
| IR 时序 | 同批 A 通用 |
| ITP 侧自检 | 各金额列、笔数列 ≥ 0；行列数与段数（21 段）一致 |
| 现状对照 | 现网 `RECON_BATCH_FILE.AMOUNT_TOTAL` 对 PAY 存的是「所有度量列之和」的内部校验和（recon.md 待裁决 #5）——目标态 manifest 用 `N/A` 后，该列保留为内部校验和、NEVER 进 manifest（2026-11-16 定稿） |
| ACC 核对口径 | PAY 旅游票段为主单聚合（一笔支付对 N 张票，ADR-D161），ACC 侧 NEVER 按「一笔 = 一张票」核对旅游票段 |
| 键段逐源取值 | 日期键三源分别取 `OUT_TIME` 前 8 位 / `PAID_TMS` / `PAY_DATE`（同窗横跨两个自然日，值域 = windowStart~windowEnd 内出现的自然日，ACC 四元组 / 格式校验 NEVER 按文件名账期日做单值校验）；支付方式键三源三套码空间（face-pay `PAY_CHANNEL_CODE` 可能 null 且不兜底、daily-ticket 取子单银商码、gate 无此列只能用 `PAYMENT_VENDOR`）——recon-server 聚合时按 Q16 码表统一映射，null 段落空串；gate「`PAY_CHANNEL_CODE` 库列不存在」列为映射前置核对项 |
| 圈单谓词耦合 | 过闸 / 单边组圈单与 `DEBIT_STATUS` 取值域耦合——`CLOSED` 已在 DDL、代码零写入点，未来启用关单会静默漏出 PAY / EXP；取值域扩展 MUST 触发对账口径评审（含补款收敛白名单联动） |
| 分段基线说明 | collect-pay 源移除后 PAY 15/16（BOM 行政处理）由 face-pay 源起算从 0 变实值——首个新 revision 在 IR-01 备注分段基线切换，防 ACC 把跳变当差异 |
| BOM 处理组混段 | 19/20 段混「更新交易（02~05）」与「退卡退票（06，负向金额）」——是否按 TRANS_TYPE 拆分正负或按净额口径随 N15 会签；同段改谓词时两组二分 MUST 同改 |
| 第三漏账探针 | 现有两探针（`BIZ_TYPE` 白名单 / `PAID_TMS` 为空）均覆盖不到「成功支付无 `F2F_PAYMENT` SUCCESS 行」——payType=0 本地聚合码分支落库口径未定，若裁定不落库将静默漏出全部度量段；第三探针 MUST 补位，落库口径随 N15 会签 |

### 2.3 商业优惠汇总 `ITP.BUS.yyyyMMdd`

| 绑定项 | 建议口径（待会签 N15） |
|---|---|
| 批次归属 | 批 A |
| recordCount | 全部数据行 |
| amountTotal | 第 2 段「对账金额」逐行加总 |
| 族扩展 manifest 字段 | 无 |
| 优惠数据源现状 | BUS 第 3 段「优惠金额」各源恒 0（face-pay 无优惠列、gate `DISCOUNT_LEVEL_AMT` 语义不符且大面积 NULL）——恒 0 属现状非缺漏，NEVER 自行补数；钱包折扣是否需在 BUS / IR-03 表达随 N15 会签 |
| IR 时序 | 同批 A 通用 |

### 2.4 交易明细 `ITP.DETAIL.YYYYMMDD`（虚拟电子多日计次票）

| 绑定项 | 建议口径（待会签 N15） |
|---|---|
| 批次归属 | 双轨：**现状 7 段版随批 A 同窗生成**（四类共用同一 T-2 窗口）；批 B 独立分批的前提是 Q11 会签切换 22 段口径——P0 落地仍产 7 段时 DETAIL 留在批 A、不单独声明 |
| recordCount | 双轨：7 段现行 = 出站（超时）+ 发售行计数（**无「正常交易行」，正常过闸不对账**）；22 段目标态（Q11 会签后）= **发售行 + 超时出站行**（catalog 2.4「正常交易不对账」口径，与三源现行一致，NEVER 按「全量明细」解读） |
| amountTotal | 双轨：7 段现行 = 第 5 段「交易金额」加总（**单位未闭合**：日票版标「元」、22 段版标「分」，Q5/Q11 会签前 NEVER 参与比对）；22 段目标态 = 第 15 段「订单金额」加总 |
| 族扩展 manifest 字段 | `overtimeAmountTotal` 双轨：7 段现行 = 「交易类型=出站」行的第 5 段加总（超时费=出站行金额，NEVER 加段）；22 段目标态 = 第 18 段「超时金额」加总（口径 Q14 未定，先按文件原值加总留痕，NEVER 参与金额比对） |
| IR 时序 | 同批 A 通用 |
| ITP 侧自检 | 双轨：7 段现行校验交易类型 ∈ {发售, 出站}、车票购买行车站 / 设备传空；22 段目标态再校验订单状态五枚举 |
| 甲方「总金额」落点 | 甲方要求 DETAIL 附「明细和总金额」而 7 段文件体无汇总行位置——目标态由 manifest `amountTotal` 承载，会签时声明「总金额经控制面下发、文件体内不加汇总行」 |
| 报文范围裁决 | 22 段订单状态五枚举含「未支付 / 退款 / 单边」，但三源现行只出 `PAY_STATUS='PAID'` 发售行 + 超时行——「未支付 / 退款 / 单边订单是否入文件」MUST 裁决后定义映射（单边数据源在 gate `ORDER_EXP_TYPE`；daily-ticket 票种列 `CODE_TICKET_TYPE` 恒 `'0441'`、`CARD_TYPE` 混三套码空间，NEVER 凭猜收窄），22 段定稿前裁决、NEVER 二次翻案 |
| 第 17/20/21/22 段数据源 | 17「优惠累计消费金额」唯一来源钱包累计查询实测不可信（返回 0 与 8 笔扣费矛盾，挂澄清）；20/21「补扣订单号 / 补扣金额」= `DAILY_TICKET_OVERTIME_FEE`（S4 前恒空）；22「退款金额」**定稿恒 0**（2026-11-16：超时罚金退款不出文件，退款载体由第三方账单存在性比对覆盖；若甲方要求入文件再另立落库需求） |
| 应收 vs 实收口径差 | DETAIL 超时行报**应收**（刻意不过滤 PAY_STATUS）、PAY 过闸组只报**实收**（SUCCESS）——未结清超时费必然形成 DETAIL > PAY 的固定方向系统性差异，随接口设计稿 §8.1 预置类目、NEVER 逐期报差错 |
| 旅游票 / 未激活行 | 发售行含旅游票子单与已付未激活行（LEFT JOIN 保行、逻辑卡号空串），「甲方是否要求排除子单」未决——若排除则 `selectDetailPage` 追加 `PARENT_ORDER_NO IS NULL`（改法与风险见 daily-ticket），22 段定稿前裁决 |
| S4 切换说明 | 7 段出站行在 S4 前恒 0 行（`GateCardTypeEnricher` 刻意清零 OVERTIME_AMOUNT，否则当期凭空多行）——S4 起出站行量 / 金额跳变，MUST 与 overtime §九 S4「完整账期逐行比对」绑定验收 |
| 超时费单位单一实现 | 库内分 → APP 报文 → 对账文件的换算 MUST 单一实现（S4 后源切 `DAILY_TICKET_OVERTIME_FEE.OVERTIME_AMOUNT` 同为分），APP 账单与 DETAIL 钳制口径共用同一参数，防只改文件侧漏掉 APP 侧 |

### 2.5 对账差异明细 `ITP.TXNDIFF.YYYYMMDD`

| 绑定项 | 建议口径 |
|---|---|
| 批次归属 | 批 C（独立批：自然日口径，Q10 收敛前 NEVER 与批 A / B 混批） |
| 相位 | P1——IR-03 / IR-09 与两维字典均在 P1（接口设计稿 §九）；现状该链路默认关闭（落地现状见 `recon-thirdparty.md`） |
| recordCount | 差异明细行数 |
| amountTotal | 差异金额绝对值合计，与 IR-03 的 `totalDiffAmount` 同口径同值 |
| 族扩展 manifest 字段 | 无（分类计数由 IR-03 承载，NEVER 在 manifest 重复） |
| 差异编码 | 既有约定 01~04 为过渡态；目标态 `diffDirection × diffType` 两维（接口设计稿 §8.1），映射关系随 N8 会签，NEVER 两套并行上线 |
| 逐行差异金额 | **未定义、N15 必签**：落地 `RECON_TXN_DIFF` 无差异金额列——建议值：单边行 = 该行实扣金额、金额不符行 = 实扣差额绝对值、状态不符行 = 0；`amountTotal`（绝对值合计）按该定义复算 |
| 关联键 | `merchant_order_no` 现状非唯一（重试 1:N，落地 SUCCESS 优先取行）——键唯一性与取数规则随 N14 会签，NEVER 把启发式当契约 |

---

## 三、非 ITP 业务交易文件族绑定（`POS_TXN` / `QRCODE_TXN`，待会签 N16）

整理版 1.4：运营在综管台导入 → ITP 通过 FTP 定时传输至 ACC。**该族此前在接口设计稿中没有 fileType / 批次契约**，本文补齐绑定（值域新增均待会签 N16）：

| 绑定项 | 建议口径（待会签 N16） |
|---|---|
| fileType | `POS_TXN`（银联 pos 交易）/ `QRCODE_TXN`（银联收款码）——新值域 |
| batchType / sourceType | `OPERATION_IMPORT` / `sourceType=OPERATION_IMPORT`——新值域 |
| settlementDate | 文件名日期 `YYYYMMDD`；与运营日日切的换算关系待对齐（N16，联动 Q10） |
| 批次归属 | 批 D（POS 与收款码同日两件同批）；NEVER 与批 A~C / E 混批 |
| 共享形态 | **A 原样转发（已定稿，随 N12 请 ACC 确认）**——xls 字节不变、ITP 不改数；manifest 结构字段帮助 ACC 定位表头，NEVER 默认转换格式 |
| recordCount | 明细数据行（不含首行汇总行与末行说明行） |
| amountTotal | 「交易金额」列逐行加总；`amountUnit=YUAN`（原件单位元，NEVER 未会签先换算，Q5） |
| 交叉校验 | manifest 值 vs 首行「总交易金额 / 总交易笔数」；差异**只留痕**（NEVER 抹平 / 修正）；末行 D1 预估说明行跳过 |
| 结构字段（建议值） | `sourceChannel=UNIONPAY` / `headerRow=2` / `columnCount=25 或 26`（POS 与收款码原文 25 / 26、样例均为 26，以 Q17 会签结论定 `formatVersion`）/ `encoding` / `summaryRow=1` / `footerRow`（末行固定说明） |
| IR 时序 | IR-01 → IR-02 → IR-07 → IR-08（**只回收妥 / 拒收**，NEVER 出 PASS / DIFF 逐笔结论）；重传经 IR-06 |
| 相位 | P1——N16 值域待会签、不阻塞 P0「四类有回执有结果」目标；生产路径凭据依赖 Q8 |
| 重传衔接 | 现网综管台「同账期同类重复上传 = 替换」（旧件改名 `.replaced-<ts>` 磁盘留存、无 revision 概念）——P1 接 IR-06 前，每次替换上传由 ITP 侧映射为 `revision+1` 重走 IR-01/02/07，替换留痕即历史版本可查 |
| 上传入口缺口 | 综管台上传页现只允许 `ITP.<类别>.<账期>` 五类与 `reconciliation-bill-<账期>`——**无 POS / 收款码、商户入账件类别**；批 D / E 导入入口 MUST 扩展上传页类别（FILE_CATEGORY）或新页面，随 FR-01 / Q13 一并需求确认 |

---

## 四、商户入账金额汇总文件族绑定（`MERCHANT_TRANSFER`）

整理版 1.5：财务（角色待确认，Q15 / N11）上传 → ITP 校验 / 留痕 → 共享给 ACC。**性质与硬约束以接口设计稿 6.4 为准（人工汇总确认金额、NEVER 参与逐笔差异比对、NEVER 自动改数），本文不复述，只补绑定细节**：

| 绑定项 | 口径 |
|---|---|
| fileType / batchType / sourceType | `MERCHANT_TRANSFER` / `FINANCE_CONFIRM` / `FINANCE_UPLOAD`（6.4 已定） |
| settlementDate | 「清分日期」；与日切口径待对齐（Q15 / N13） |
| 批次归属 | 批 E（独立批，6.4 第 1 条） |
| recordCount | 数据行（不含两级合并表头行与说明行） |
| amountTotal | 「应转账金额」列加总；`amountUnit` 待确认（需求草案 Q2，以原件与甲方确认件为准）；NEVER 自动换算 / 改数 |
| 交叉校验 | FR-04 公式链：合计 = 各组之和、应转账金额 = 合计金额 − 合计手续费、平台转账金额差异留痕（需求侧细则见 `recon-merchant-transfer-import.md`）；列组数 3 组 vs 4 组待原件（需求草案 Q3） |
| 结构字段 | 6.4 已定 5 字段（`sourceType / originalFileName / originalFileSha256 / uploadedBy / sheetName / headerRows`）+ 本文新增建议：`amountUnit / columnCount / summaryRow / footerRow`（随 N15，结构校验通用化） |
| IR 时序 | IR-01 → IR-02 → IR-07 → IR-08（只回收妥 / 拒收）；重传：P1 起走 IR-06，P0 期由财务重新上传触发 `revision+1` 后重走 IR-01 / 02 / 07 |
| 相位 | P0（接口设计稿差距表已列；依赖 Q8 / N11 / N12） |
| 上传入口缺口 | 综管台上传页现无「商户号平台转账金额」类别（命名说明只含 ITP 五类与渠道账单）——FR-01 / S2 落地前 MUST 扩展上传页类别，NEVER 借用 ITP 五类之一 |

---

## 五、第三方账单文件：比对单元定位（不进 ITP↔ACC 文件面）

- **方向**：第三方渠道 / 城交大数据 → ITP（catalog 1.2 与第六章凭据表），**不经过 ACC**。
- **NEVER 进 ITP↔ACC 批次**：不为它定义 `fileType / batchType`，NEVER 出现在任何 IR-01 的 `files[]`，NEVER 接收 IR-07 回执。
- **定位**：归一化为 22 字段记录（接口设计稿 6.5）后，作为 ITP 侧 TXNDIFF 生成与 IR-03 上报的**比对单元**；`orderKeys[]` 取 `merchant_order_no` + `channel_trade_no`。
- 原始账单首行汇总值的交叉校验发生在 **ITP 导入 / 归一化环节（ITP 内部）**，NEVER 进入 IR-01。
- 6.5 的结构类 `rejectCode`（`HEADER_MISMATCH` 等）适用对象随其 2026-11-16 重定位改挂在批 D / E（第三、四章），NEVER 挂在第三方账单件。
- 若未来需要把归一化件共享给 ACC：那是**新决策**，MUST 走会签新增文件族与批次模型，NEVER 由本文默认成立。
- **账单获取与编号索引（用户 2026-11-16 口径，待与 ACC 确认）**：渠道账单按 catalog 3.6「标准定义」编码索引（2026-11-16 版已与 3.2 对齐）；**ACC 可根据编号获取这些账单文件**。若成立，账单获取模型为「ITP 取城交合并 CSV（catalog 3.3）+ ACC 按编号取分渠道件」的双侧模式，两侧归一化 MUST 使用同一 `formatVersion` 与同一 22 字段映射（6.5），NEVER 各自引申字段口径。
- **账单比对范围**：以 catalog 3.6「提供第三方账单=是」的渠道为准（当前：03 支付宝 / 04 微信 / 07 云闪付）；「暂无」渠道走 catalog 3.5 直出对账，**NEVER 伪造比对单元**。该列与 3.2 文件来源口径的不一致（Q16 剩余项）裁决后同步本节。
- 能力演进：「是 ↔ 暂无」变化时 catalog 3.6 先更、本节随更，NEVER 两侧各维护一份能力清单。
- **退款件比对规则（N14）**：第三方账单按「卖出 / 退款」两件下发（`business_type=REFUND`、金额恒正），ITP 侧 PAY / BUS 报全款、退款不可见（甲方规格无退款段，NEVER 自行冲减）——退款行按 `merchant_refund_no` 回查 ITP 退款单（`F2F_REFUND` / `DAILY_TICKET_REFUND`）做**存在性比对**而非金额对碰，命中归 `diffType=REFUND` 预期差异、不出差错工单；该规则随 N14 会签，NEVER 逐期人工核。

---

## 六、参数 / 逻辑卡号 / HCE 文件：不在对账控制面范围

- 参数文件（ACC→ITP）与逻辑卡号文件（ACC→ITP）是**业务数据交换**，非对账结论：不产生对账批次、不进控制面、不出现在任何 IR 接口。
- IF7B-01 逻辑卡号申请属业务实时通道（接口设计稿 2.2 旁路通道），沿用不改；URL 与大小写笔误以联调实测报文为准（Q6），NEVER 照抄整理版示例。
- HCE 黑名单文件：整理版仅一句引用且《青岛地铁ITP平台-HCE设计方案V1.7》不在仓内（Q1），绑定设计**占位**，待 Q1 闭环后补。
- 「ITP 业务文件导入」与「对账汇总文件」：整理版原文为空（Q1），本文同样占位待补，NEVER 编造字段或批次模型。
- 储值票账户类对账诉求（若甲方提出）数据源在 acc-es-server 的 `TBL_STL_*`（ES 报告含初始金额 / 押金 / 充值 / 消费次数字段），不落在三个 recon 源，NEVER 误落 recon 域。
- HCE 票卡更新审计已落 `F2F_RESULT_REPORT`（`ORDER_NO` 位放 cardId）——「HCE 审计是否与 ACC 侧核对、核对单元是什么」列为 Q1 闭环子问题。

---

## 七、manifest 字段分配总表

### 7.1 批级（IR-01 `bizData`，全部批次通用）

`settlementDate / calendarType / windowStart / windowEnd / batchNo / revision / batchType / expectedFileCount / files[]`——字段定义以接口设计稿 §5.2 为准，本文不复述。

### 7.2 文件级通用字段（IR-01 `files[]` 项 / `.manifest.json` / IR-02 三处共有）

`fileType / fileName / recordCount / amountTotal / byteSize / sha256 / formatVersion`。

### 7.3 文件级族扩展字段（仅对应族必带）

| 族 | 扩展字段 | 定位 |
|---|---|---|
| `DETAIL` | `overtimeAmountTotal` | 超时金额合计（第 18 段），口径受 Q14 |
| `POS_TXN / QRCODE_TXN` | `sourceType / sourceChannel / headerRow / columnCount / encoding / summaryRow / footerRow / amountUnit` | 结构校验（源自接口设计稿 6.5，适用对象 2026-11-16 重定位后落本族） |
| `MERCHANT_TRANSFER` | `sourceType / originalFileName / originalFileSha256 / uploadedBy / sheetName / headerRows`（6.4 已定）+ `amountUnit / columnCount / summaryRow / footerRow`（本文新增建议，随 N15） | 财务件溯源 + 结构校验通用化 |

### 7.4 `.manifest.json` 建议值（N7）

`<fileName>.manifest.json` 同名旁挂；UTF-8 JSON；内容 = 对应文件级字段（§7.2 + §7.3）+ `protocolVersion / settlementDate / batchNo / revision / generatedAt`；与数据文件同批到达、数值与 IR-01 / IR-02 完全一致。

---

## 八、四元组与校验统一口径

| 文件族 | recordCount | amountTotal | 金额单位 | 备注 |
|---|---|---|---|---|
| `EXP` | 全部数据行 | 第 4 段「实际扣款金额」加总 | 分（字符串） | 应收口径（第 3 段）是否并列上报待 N15 |
| `PAY` | 全部数据行 | **`"N/A"`（不适用）** | 不适用 | ACC 跳过金额比对，其余三项照常 |
| `BUS` | 全部数据行 | 第 2 段「对账金额」加总 | 分（字符串） | — |
| `DETAIL` | 双轨：7 段现行 = 出站 + 发售行计数 / 22 段目标态 = 全部数据行 | 双轨：7 段现行 = 第 5 段「交易金额」加总（单位未闭合）/ 22 段目标态 = 第 15 段「订单金额」加总 | 7 段版单位未闭合（Q5/Q11） | 超时金额另列 `overtimeAmountTotal`（双轨定义见 §2.4） |
| `TXNDIFF` | 差异明细行数 | 差异金额绝对值合计 | 分（字符串） | 与 IR-03 `totalDiffAmount` 同值 |
| `POS_TXN / QRCODE_TXN` | 明细数据行 | 「交易金额」列加总 | `amountUnit=YUAN` | 与首行汇总交叉校验，差异只留痕 |
| `MERCHANT_TRANSFER` | 数据行 | 「应转账金额」列加总 | `amountUnit` 待确认 | 与 FR-04 公式链交叉校验 |

**通用口径**（对上表全部族生效）：

1. `byteSize`：文件字节精确长度（十进制字符串）；
2. `sha256`：整文件字节的 SHA-256，hex 小写；
3. **空文件（定稿，2026-11-16）**：0 行仍生成并投递、`recordCount="0"`——甲方既定要求且已实现（空文件是正常的零交易日，NEVER 当失败；「来源声明 >0 而输出 0 行」才是失败）；空文件 `amountTotal` 按族口径取值（四类 / TXNDIFF = `"0"`、PAY = `"N/A"`，定稿）；
4. **换算**：元→分只发生在「生成 ITP 四类 / TXNDIFF 文件」这一步（Q5）；导入族 manifest 金额按 `amountUnit` 标记的原件单位传输，**NEVER 未会签先换算、NEVER 自动改数**；
5. **交叉校验**：导入族（批 D / E）manifest 值 vs 原件首行汇总 / 合计行，差异只留痕，NEVER 抹平；
6. 金额字段一律字符串化（接口设计稿 §3.1 金额字段口径），NEVER 使用 JSON number。
7. **四元组落点**：与既有 `RECON_BATCH_FILE` 的 `BYTE_COUNT / RECORD_COUNT / AMOUNT_TOTAL / SHA256` 四列一一对应，明细类实测值即分片声明累加值；manifest 四元组 NEVER 复用「所有度量列之和」型校验和语义（PAY 现状列语义见 §2.2 现状对照）。

---

## 九、相位与 IR 激活矩阵

| 批 | P0 激活 | P1 激活 | P2 激活 | 不适用 |
|---|---|---|---|---|
| 批 A（EXP/PAY/BUS） | manifest + IR-01 / 02 / 05 / 07 / 08 | IR-06（重传） | IR-04 / 10（差错）、IR-11（日历） | IR-03 / 09（该三族自身无差异上报） |
| 批 B（DETAIL） | 同批 A | 同批 A | 同批 A | 同批 A |
| 批 C（TXNDIFF） | — | manifest + IR-01 / 02 / 03 / 05 / 06 / 07 / 09 | IR-11 | IR-08（该批无 PASS / DIFF 结论）、IR-04 / 10 |
| 批 D（POS / 收款码） | — | manifest + IR-01 / 02 / 05 / 06 / 07 + IR-08（只收妥） | IR-11 | IR-03 / 09（不参与逐笔差异比对）、IR-04 / 10 |
| 批 E（商户入账件） | manifest + IR-01 / 02 / 05 / 07 + IR-08（只收妥） | IR-06 | IR-11 | IR-03 / 09、IR-04 / 10 |
| 批 F（ACC→ITP 反向对账文件族，Q18 占位） | —（待 Q18 裁决是否立项） | — | — | 待 Q18 |
| 第三方账单 / 参数 / 逻辑卡号 / HCE | —（不进控制面，见第五、六章） | — | — | 全部 |

> 矩阵与接口设计稿 §九分期对齐：IR-05 为 P0 全局查询兜底；IR-06 随「revision 重传」P1 落地；IR-04 / 10 / 11 为 P2。矩阵变化 MUST 同步接口设计稿 §九，NEVER 两处分期。

---

## 十、与既有约定 / 前版协议的差异清单（会签输入）

**沿用不改（本文逐族复核确认零改动）**：四类 + TXNDIFF 的文件名 / 段序 / 段数（Q11 会签前不动）、`/itp/recon/` 投递路径、T-2 窗口、商户入账件与 POS 件「导入 ITP → FTP 转投 ACC」的方向、参数 / 逻辑卡号路径、IF7B-01（URL 与大小写笔误以联调实测报文为准，Q6）。

**目标态变更（MUST 会签）**：

| # | 变更 | 动机 |
|---|---|---|
| 1 | IR-01 触发时机：「账期文件开始生成前」→「本批文件全部生成完成后、首份文件投递（rename）前」，`files[]` 四元组为实测值 | 修复前版「生成前不可能有实测值」的内部矛盾（2026-11-16 复核） |
| 2 | IR-08 幂等键补 `revision` + `PENDING → 终态` 覆盖规则 + 旧 revision 终态标记 `superseded` | 修复前版幂等键与 PENDING 两段式、重传再核对互相矛盾（2026-11-16 复核） |
| 3 | `RECONCILING` 白名单迁移的两步处理（终态 IR-08 先迁 RECONCILING 再按 result 推进，同事务） | 消除 ACKED 直接收终态被白名单拒绝的死角 |
| 4 | 控制面 JSON 金额字段一律字符串化（禁 JSON number） | 精度与解析一致性 |
| 5 | `fileType / batchType` 值域扩展：`POS_TXN / QRCODE_TXN / OPERATION_IMPORT`（N16） | 整理版 1.4 族此前无绑定落点 |
| 6 | 逐族 `amountTotal` 口径与 `amountUnit`（N15） | manifest 校验规则可落地 |
| 7 | 批次分群（批 A~E，批 F = Q18 占位）：由「一目录齐投」细化为按账期口径分批声明 | IR-01 批级账期字段要求批内同口径（Q10 收敛前） |
| 8 | 6.5 结构校验适用对象重定位：第三方账单件不进 ITP↔ACC 文件面，结构字段落批 D / E | 第三方账单方向为第三方/城交→ITP，IR-07 对它无意义（2026-11-16 复核） |
| 9 | manifest 旁挂件防护：ACC 取件 MUST 精确匹配正式名（`.uploading` / `.manifest.json` 命中 `ITP.BUS*` 通配会被取走删除，实证） | 会签前置改造点；会签前 BUS 族 manifest 只走控制面 |
| 10 | 对外 11 态状态机与现网 `RECON_BATCH` 8 态状态机分层共存、NEVER 共列 | 两者 `UPLOADING` 撞名不同义（2026-11-16 补） |
| 11 | 批 A 的 T-2 窗口作为 Q10 已定基准；多日切转为「机构级配置能力」（IR-11 承载） | 批 A 账期公式已实现且经甲方自校验例；《需求分析 v2.1》明文要求多日切管理 |
| 12 | 渠道账单按「标准定义」编号索引、ACC 可按编号获取（Q16 2026-11-16 版）+「提供第三方账单」能力列界定比对范围 | 用户 2026-11-16 口径，获取通道待 ACC 确认 |

**纯新增**：`.manifest.json` 旁挂件（N7 建议值）、`OPERATION_IMPORT` 批次族、本文（逐文件族绑定层）。

---

## 十一、待会签增量（N15 / N16，已同步挂接口设计稿 §11.2）

| 编号 | 待定内容 | 建议值位置 | 影响 |
|---|---|---|---|
| N15 | 逐文件族 `recordCount / amountTotal` 计算口径（加总段位、PAY 不适用语义、`amountUnit`）+ 第 3 段应收口径候选 `ORIGINAL_FARE`、22 段第 17 段数据源澄清与第 20/21 段数据源时点、BOM 处理组 19/20 拆分、payType=0 落库口径（归属 tvm-bom-pay 域裁决）；已定稿：空文件口径（§8）、EXP 已补收行不重报（§2.1）、第 22 段恒 0（§2.4）、PAY 校验和列保留内部（§2.2） | §2.1~2.5、§4、§8 | IR-01 `files[]`、IR-02、IR-07 校验、IR-08 的 `itpTotal{}` |
| N16 | 新增 `fileType=POS_TXN / QRCODE_TXN`、`batchType=OPERATION_IMPORT`、导入族 `settlementDate` 口径（文件名日期 vs 运营日）、共享形态（建议与 N12 合并裁决：A 原样转发） | §3 | §4.2 / §5.4 值域、IR-08 取值、Q8 路径凭据 |

---

## 十二、验收口径（绑定层）

1. 每族 manifest 数值与文件实测一致（口径按 §8 汇总表）；
2. 批次分群与 §9 矩阵一致：P0 只上线批 A / B / E，批 C / D 不在 P0 出现；
3. `PAY` 的 `amountTotal="N/A"` 时 ACC 跳过金额比对、四元组其余三项照常校验；
4. 导入族（批 D / E）交叉校验差异只留痕，NEVER 自动修正 / 抹平；
5. 第三方账单文件在任何批次、任何 IR 接口中均不出现（§5 定位）；
6. 本文与接口设计稿 §12 验收正交：协议级验收在彼、绑定级验收在此，NEVER 互替。

---

## 十三、维护约定

- 真源分工：catalog = 字段契约、接口设计稿 = 协议契约、本文 = 逐文件族绑定；本文 NEVER 复述前两者内容，前两者变动 MUST 检查本文受影响族并同步。
- N15 / N16 会签闭环后：直接改写本文对应章节为定稿、删接口设计稿 §11.2 对应行，NEVER 追加「已确认」补丁句。
- 新文件族（如 `QDT_*`）会签闭环后：先落 catalog 新章，再在本文补绑定章，NEVER 跳过 catalog 直接绑定。
- 文件族增减、fileType / batchType / 批次分群、四元组口径变动，MUST 同步本文与 `docs/business/README.md` 索引行，并按 `docs/提示词/工程与文档规范.md` 的回写义务处理。
- 甲方规格原文目录 `docs/接口规范文档/` 只读，NEVER 在其中新增派生 `.md`。
