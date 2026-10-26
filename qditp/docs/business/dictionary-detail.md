# 字典项全量明细（码值 ↔ 中文 速查）

> **用途**：给人看的排查速查手册。每个码表给出「码值 ↔ 中文」对应关系 + 落库载体列，方便排查问题时反查码值含义。
> **数据来源**：逐类读源码实取码值（2026-10-22），**非**索引代表码值。
> **码值形态**（排查时先认这个，避免把 Java 标识符当成落库值）：
> - **码值=枚举常量名（字符串）**：库里存 `CREATED`/`SUCCESS` 这类大写英文串（状态机类都是这种，下方表里码值列就是落库值，**不是** Java 类里的字段名）。
> - **码值=定长数字/十六进制**：库里存 `0441`/`0B`/`0` 这类定长码。
> - **码值=中文**：直接存中文（极少）。
> **维护**：**本页即全量内容，排查直接看本页（wiki 与源仓同名文件互为镜像，NEVER 回源仓翻文件）**；改码值后 MUST 同步本页与 §十 速查索引。

---

## 一、票卡 / 票种

### 1.1 票卡类型编码
**载体**：`QRCODE_TXN_DETAIL.CARD_TYPE` 等 · 码值=定长4位 · 源 `model.enums.CardTypeCodeEnum`

| 码值 | 中文含义 |
|---|---|
| 0441 | 二维码后付费单程票 |
| 0442 | HCE后付费单程票 |
| 0443 | 新版HCE后付费单程票 |
| 0444 | 员工票 |
| 0445 | 一日票 |
| 0446 | 三日票 |
| 0447 | 七日票 |
| 0448 | 多日计次票 |
| 044A | 爱山东 |

> 排查提示：`isHceCard`=0442/0443；`isEmployeeCard`=0444；日票=0445~0448；员工票及日票的行业码体统一映射为 0441。

### 1.2 票种/产品（订单号前缀）
**载体**：collect-pay 订单号前缀 · 码值=2位十六进制 · 源 `collectpay.constant.ProductType`

| 码值 | 中文含义 |
|---|---|
| 00 | 一票通_单程票 |
| 01 | 一票通_计程票 |
| 02 | 一票通_计次票 |
| 03 | 一票通_计期票 |
| 07 | 一票通_员工票 |
| 08 | 一票通_琴岛通卡 |
| 09 | 一票通_青岛银行金融IC卡 |
| 0A | 一票通_交通部互联互通卡 |
| 0B | 补充票款 |
| 0C | 招行一网通_垫资补缴 |
| 0D | 招行一网通_垫资补缴 |
| 0E | 虚拟电子票 |

### 1.3 票种（ACC ES 域）
**载体**：acc-es 任务票种 · 码值=2位数字 · 源 `acc-es.enumns.TicketType`

| 码值 | 中文含义 |
|---|---|
| 01 | 单程票 |
| 02 | 储值票 |

---

## 二、支付渠道（支付方式）

> **检索别名**：「支付方式」在本仓文档统一叫**支付渠道**；钱包/第三方支付方式的差异规格另见 `docs/external/钱包支付方式差异说明.md`（供方补充规格：签约走 `requestAddPayChannel`、`transferFlag` 01/02、`cumulativeType` 01/02/03 等）。

### 2.1 支付渠道
**载体**：报文 `PAYMENT_VENDOR` / `GATE_TXN_PAY.PAY_CHANNEL_CODE` · 码值=2~4位 · 源 `paysign.constant.PaymentVendorEnum`

| 码值 | 中文含义 |
|---|---|
| 03 | 支付宝 |
| 04 | 微信 |
| 05 | 支付宝出行 |
| 06 | 龙支付 |
| 0601 | 招商银行 |
| 0602 | 中国银行 |
| 08 | 建行数币 |
| 0801 | 中行数币 |
| 0802 | 邮储数币 |
| 0803 | 交行数币 |
| 0B | 钱包（地铁钱包） |
| 0C | 数币APP |

> 排查提示：闸机侧 `GateTxnPayFieldCode` 的「地铁钱包」码值 0B 与本表 0B 同义；`PAY_CHANNEL_CODE` 列存的即此码。

### 2.2 签约渠道
**载体**：`APP_PAY_SIGN_INFO.SIGN_CHANNEL` · 码值=英文标识符 · 源 `paysign.constant.SignChannelEnum`

| 码值 | 中文含义 |
|---|---|
| METRO_APP | 地铁APP |
| ALIPAY | 支付宝 |
| WECHAT | 微信 |
| UNION_PAY | 云闪付 |
| LONG_PAY | 龙支付 |
| WALLET | 钱包 |

### 2.3 发行渠道
**载体**：`USER_ITP_REG_INFO.CARD_ISSUE_CODE`（4位形态） · 码值=2位 · 源 `model.enums.IssueChannelCodeEnum`

| 码值 | 中文含义 |
|---|---|
| 01 | 正常渠道（闸机/APP 直接交易；落库 4 位 0001） |
| 07 | 支付宝（落库 4 位 0007） |

### 2.4 发卡机构
**载体**：`USER_ITP_REG_INFO.ISSUE_ORG_CODE` · 码值=4位 · 源 `model.enums.CardIssueOrgEnum`

| 码值 | 中文含义 |
|---|---|
| 0004 | 海上巴士 |
| 0007 | 支付宝出行 |
| 0008 | 成都地铁 |
| 0020 | 青岛地铁(早期) |
| 5412 | 青岛地铁 |
| 5413 | 畅行U惠小程序 |

> 排查提示：未知机构码兜底归一为 4 位 `0001`（正常渠道）。

---

## 三、交易 / 业务类型

### 3.1 交易类型
**载体**：`GATE_TXN_PAY.TRX_TYPE` / `QRCODE_TXN_DETAIL.TRX_TYPE` · 码值=2位 · 源 `model.enums.TrxTypeCodeEnum`

| 码值 | 中文含义 |
|---|---|
| 01 | 进站 |
| 02 | 出站（正常） |
| 03 | 超时出站 |
| 04 | 进站失败 |
| 99 | 异常 |

> 排查提示：出站=02/03；进站=01。闸机侧 `GateTxnPayFieldCode` 同义。

### 3.2 TVM 扫码业务类型
**载体**：collect-pay 订单业务类型 · 码值=2位 · 源 `collectpay.constant.BusinessTypeEnum`

| 码值 | 中文含义 |
|---|---|
| 01 | 扫码购票 |
| 02 | 扫码充值 |
| 03 | 扫码取票 |
| 04 | BOM支付 |
| 05 | app退款 |

### 3.3 建议操作码（IF5A）
**载体**：IF5A-01 响应 `adviceOpt`（内存码表） · 码值=3位 · 源 `model.ticket.enums.AdviceOptEnum`

| 码值 | 中文含义 |
|---|---|
| 000 | 无需操作 |
| 005 | 免费更新（补出站，20分钟窗，不计票价） |
| 006 | 付费更新（补出站，按票价扣） |
| 018 | 补进站 |
| 020 | 免费更新（无20分钟窗，厂家字典，2026-09-18 裁决） |

> 排查提示：补进站方向（TRX_TYPE=01）只有 018；补出站方向（TRX_TYPE=02）是 005/006/020。

---

## 四、设备 / 证件 / 人员

### 4.1 设备类型
**载体**：设备主数据 / 行业码体 · 码值=2位 · 源 `model.enums.DeviceTypeEnum`

| 码值 | 中文含义 |
|---|---|
| 01 | ACC服务器 |
| 02 | LCC服务器 |
| 03 | SC服务器 |
| 04 | 进站AGM |
| 05 | 出站AGM |
| 06 | 双向AGM |
| 07 | TVMⅠ |
| 08 | BOM |
| 09 | PCA |
| 10 | TCM |
| 11 | ES |
| 12 | ITP服务器 |
| 13 | TVMⅡ |
| 14 | STT |
| 32 | ACC工作站 |
| 33 | LCC工作站 |
| 34 | SC工作站 |
| 35 | ITP工作站 |
| 36 | 自助补站手机 |

> 排查提示：15~31（0x0F~0x1F）为协议保留位，未定义。

### 4.2 证件类型
**载体**：acc-es 证件 `PID_CODE` · 码值=2位 · 源 `acc-es.enumns.PidCdEnum`

| 码值 | 中文含义 |
|---|---|
| 00 | 居民身份证 |
| 01 | 学生证 |
| 02 | 护照 |
| 03 | 军官证 |
| 04 | 士兵证 |
| 05 | 警官证 |
| 06 | 港澳通行证 |
| 07 | 台胞证 |
| 09 | 驾驶证 |
| 10 | 居住证 |
| 11 | 船名证 |
| 12 | 员工证 |

> 排查提示：码值 08 缺失（未定义），09 直接是驾驶证。

### 4.3 人员类型
**载体**：acc-es 任务人员 · 码值=1位 · 源 `acc-es.enumns.PersonTypeEnum`

| 码值 | 中文含义 |
|---|---|
| 0 | 普通乘客 |
| 1 | 地铁员工 |

### 4.4 设备管理模式（业主自定义，2026-10-22 新增）
**载体**：`TBL_DEVICE_INFO.MANAGE_MODE` · 码值=2位 · 源 `web/src/utils/deviceManageMode.js`（前端唯一真源）+ `para-server/.../sql/para-schema.sql` CHECK 约束 `CK_TBL_DEVICE_INFO_MODE`

| 码值 | 中文含义 |
|---|---|
| 01 | 正常使用 |
| 02 | 节能关机 |

> 排查提示：`01`/`02` 在**各码值空间含义完全不同**（支付渠道 01-APP、卡种 02-后付费二维码、F2F 设备类型 02-TVM），本表是独立码值空间，**NEVER** 与其他码表互相套用。前端工具 `deviceManageMode.js` 头注已固化该纪律。

### 4.5 ACC 管理级别（码值清单待 ACC 提供，NEVER 臆造）
**载体**：`TBL_DEVICE_INFO.ACC_MANAGE_LEVEL`（ACC 设备台账同步字段） · 源 ACC 侧 `/api/v1/Config/device`（manageLevel 字段）

> ⚠️ ACC 码值清单**尚未提供**，页面只透出编码不显示中文（`para-server` `AccDeviceSyncService` 原样落库）。ACC 提供清单后 MUST 补齐本表。

---

## 五、状态机（码值=枚举常量名，落库为英文串）

> ⚠️ 这一类排查最易踩坑：库里存的是 `CREATED`、`SUCCESS` 这种**英文串**，**不是** Java 类里的字段名。下表「码值」列就是落库值。注意：部分码值（如 `CLOSED`）在 `DEBIT_STATUS` 上 DDL 存在但**代码无写入点**，权威清单以 `gate-txn-pay.md` §4 为准，详见各表「排查提示」。注意：部分码值（如 `CLOSED`）在 `DEBIT_STATUS` 上 DDL 存在但**代码无写入点**，权威清单以 `gate-txn-pay.md` §4 为准，详见各表「排查提示」。

### 5.1 QR 码车票状态机
**载体**：`QRCODE_STATUS.QR_STATUS` · 码值=2位 · 源 `model.ticket.enums.QRCodeStatusEnum`

| 码值 | 中文含义 |
|---|---|
| 01 | 无交易 |
| 02 | 结束行程 |
| 03 | 初始化 |
| 04 | 已进站 |
| 05 | 已出站 |
| 06 | 超时出站 |
| 08 | 20分钟内免费更新 |
| 09 | 20分钟内付费更新 |
| 10 | 入站码更新 |
| 70 | 异常 |
| 80 | APP自助补出站更新 |
| 81 | APP自助补进站更新 |
| FF | 进站失败 |

### 5.2 F2F 订单状态机（人脸支付）
**载体**：`F2F_ORDER.ORDER_STATUS` · 码值=英文串 · 源 `facepay.domain.F2fOrderStatus`

| 码值 | 中文含义 |
|---|---|
| CREATED | 已下单，未发起支付 |
| PAYING | 已向支付中心发起支付，结果未定 |
| PAID | 支付成功 |
| FULFILLED | 业务已履约（出票/充值成功） |
| FULFILL_FAILED | 业务履约失败 |
| TOPUP_SUSPECT | 充值可疑，需人工核实 |
| PAY_FAILED | 支付失败 |
| EXPIRED | 二维码超时未支付 |
| REFUNDING | 退款已提交，等支付中心收口 |
| REFUNDED | 退款成功 |
| CANCELED | 订单取消 |

> 排查提示：可退（REFUNDABLE）=PAID/FULFILLED/FULFILL_FAILED；失败态（FAILED_LIKE）=PAY_FAILED/EXPIRED/CANCELED。

### 5.3 F2F 退款状态
**载体**：`F2F_ORDER.REFUND_STATUS` · 码值=英文串 · 源 `facepay.domain.F2fOrderRefundStatus`

| 码值 | 中文含义 |
|---|---|
| NONE | 未发生过成功退款 |
| PARTIAL | 已部分退回 |
| SUCCESS | 已全额退回 |

### 5.4 通道签约状态机
**载体**：`APP_PAY_SIGN_INFO.SIGN_STATUS` · 码值=英文串 · 源 `model.domain.SignStatus`

| 码值 | 中文含义 |
|---|---|
| NOT_SIGNED | 未签约 |
| SIGNED | 已签约（免密扣款与解约的唯一合法前置态） |
| UNSIGNED | 已解约（终态之一，可复位重签） |
| FAILED | 签约失败 |

### 5.5 支付宝出行签约状态机
**载体**：`ALIPAY_SIGN_INFO.SIGN_STATUS` · 码值=英文串 · 源 `model.domain.AlipaySignStatus`

| 码值 | 中文含义 |
|---|---|
| SIGNED | 已签约 |
| TERMINATED | 已销卡（终态，NEVER 回到 SIGNED） |

> ⚠️ 与 5.4 **刻意不统一**（ADR-D124）：两渠道词表不同，库里各存各的。

### 5.6 解约申请状态机
**载体**：`APP_TERMINATION_REQUEST.TERMINATION_STATUS` · 码值=英文串 · 源 `model.domain.TerminationStatus`

| 码值 | 中文含义 |
|---|---|
| PENDING | 待处理 |
| SCANNING | 扫描中（在途） |
| SUCCESS | 解约成功（终态） |
| FAILED | 解约失败（可复活重扫） |

### 5.7 支付宝出行解约登记状态机
**载体**：`ALIPAY_TERMINATION_REQUEST.STATUS` · 码值=英文串 · 源 `model.domain.AlipayTerminationStatus`

| 码值 | 中文含义 |
|---|---|
| PENDING | 待处理 |
| COMPLETED | 销卡完成（终态） |
| FAIL | 明确失败（终态） |

> ⚠️ 与 5.6 刻意不统一：本渠道只有 PENDING→COMPLETED/FAIL，无 SCANNING 中间态；拼写差（FAIL vs FAILED）是库现状。

### 5.8 跨域同步状态机
**载体**：`USER_PHONE_CHANGE_LOG.SIGN_SYNC_STATUS` · 码值=英文串 · 源 `model.domain.SyncStatus`

| 码值 | 中文含义 |
|---|---|
| PENDING | 待同步 |
| SUCCESS | 同步成功（终态） |
| FAILED | 同步失败（可补偿重推） |

### 5.9 员工卡状态
**载体**：`USER_ACC_EMPLOYEE_CARD.CARD_STATUS` · 码值=1位数字 · 源 `account.domain.EmployeeCardStatus`

| 码值 | 中文含义 |
|---|---|
| 1 | 正常（已激活可用） |
| 2 | 禁用（ACC 侧停用，可再激活） |
| 3 | 未激活（发卡起始态） |
| 4 | 注销（终态） |

### 5.10 员工卡事件类型
**载体**：`USER_ACC_EMPLOYEE_CARD_LOG.EVENT_TYPE` · 码值=英文串 · 源 `account.domain.EmployeeCardEvent`

| 码值 | 中文含义 |
|---|---|
| OPEN | 开通（首次建行） |
| STATUS | 状态变更（激活/禁用） |
| CHANGE | 资料变更（换手机号等） |
| CANCEL | 注销（CARD_STATUS 落终态 4） |

### 5.11 闸机扣款状态机
**载体**：`GATE_TXN_PAY.DEBIT_STATUS` · 码值=英文串 · 源 `gatetxnpay.constant.DebitStatus`

| 码值 | 中文含义 |
|---|---|
| INIT | 已落单未发起扣款（含离线码待重算） |
| PROCESSING | 已向支付域发起扣款，等回调 |
| RETRY | 发起失败/无响应，留补偿重试 |
| SUCCESS | 终态：已收到钱（免扣费/日票交易落单即此态） |
| FAIL | 终态：扣款失败 |
| CLOSED | DDL 列存在、代码无写入点（历史遗留/人工处理单；权威源 `gate-txn-pay.md` §4「三套口径」） |

> 排查提示：运营页面单笔重试只碰 INIT/RETRY；批量重试额外含 FAIL；可退=SUCCESS/PROCESSING。权威源 = `gate-txn-pay-server/src/main/resources/sql/gate-txn-pay-schema.sql` `DEBIT_STATUS` 列注释（6 值清单）。

### 5.12 闸机优惠计算状态
**载体**：`GATE_TXN_PAY.DISCOUNT_CALC_STATUS` · 码值=英文串 · 源 `gatetxnpay.constant.DiscountCalcStatus`

| 码值 | 中文含义 |
|---|---|
| SUCCESS | 优惠算好 |
| SKIPPED | 不适用优惠（非钱包/同行票/免扣费），非失败 |
| FALLBACK | 取数/算价失败，退化为原价放行 |
| OFFLINE_FARE_PENDING | 离线码入库拿不到金额，待重算 |

---

## 六、对账（recon）

### 6.1 对账批次状态机
**载体**：recon 批次态 · 码值=英文串 · 源 `recon.model.ReconBatchStatus`

| 码值 | 中文含义 |
|---|---|
| CREATED | 批次已建，未下发抽取 |
| EXPORTING | 已下发抽取指令，等分片 |
| PARTIAL | 部分来源完成 |
| ALL_SOURCE_COMPLETED | 全部来源完成且三项校验通过 |
| GENERATING | 正在合并生成最终文件 |
| UPLOADING | 正在向 FTP 投递 |
| SUCCESS | 全部投递成功（终态） |
| FAILED | 任一环节失败，等补偿 |

### 6.2 单来源抽取进度
**载体**：recon 来源进度 · 码值=英文串 · 源 `recon.model.ReconSourceStatus`

| 码值 | 中文含义 |
|---|---|
| PENDING | 已登记期望，未下发 |
| EXPORTING | 指令已下发，等分片与完成声明 |
| COMPLETED | 源声明完成且校验通过 |
| FAILED | 源声明失败/被回绝 |
| MISMATCH | 完成但校验不符，不可自动放行 |

### 6.3 对账文件投递状态
**载体**：recon 文件态 · 码值=英文串 · 源 `recon.model.ReconFileStatus`

| 码值 | 中文含义 |
|---|---|
| GENERATED | 已生成 |
| UPLOADING | 投递中 |
| UPLOADED | 已投递 |
| FAILED | 投递失败 |

### 6.4 对账分片接收状态
**载体**：recon 分片态 · 码值=英文串 · 源 `recon.model.ReconPartStatus`

| 码值 | 中文含义 |
|---|---|
| RECEIVING | 接收中 |
| RECEIVED | 已接收 |
| FAILED | 接收失败 |

### 6.5 对账文件类型
**载体**：recon 文件名前缀 · 码值=文件前缀串 · 源 `recon.model.ReconFileType`（与 model `ReconFileTypeEnum` 同源）

| 码值 | 中文含义 |
|---|---|
| ITP.EXP | 单边交易明细（13段） |
| ITP.PAY | 统计汇总（21段，聚合） |
| ITP.BUS | 商业优惠汇总（4段，聚合） |
| ITP.DETAIL | 虚拟电子多日计次票明细（21段） |
| ITP.F2F_TXN | face-pay TXNDIFF 分片（21段） |

---

## 七、ACC ES 任务域

### 7.1 ES 设备状态
**载体**：acc-es 设备 · 码值=1位 · 源 `acc-es.enumns.EsStat`

| 码值 | 中文含义 |
|---|---|
| 0 | 工作 |
| 1 | 暂停 |
| 3 | 故障 |

### 7.2 ES 签到状态
**载体**：acc-es 签到 · 码值=1位 · 源 `acc-es.enumns.LoginStat`

| 码值 | 中文含义 |
|---|---|
| 0 | 未签到 |
| 1 | 已签到 |

### 7.3 ES 任务执行状态
**载体**：acc-es 任务 · 码值=1位 · 源 `acc-es.enumns.TaskExecuteStat`

| 码值 | 中文含义 |
|---|---|
| 0 | 未执行 |
| 1 | 执行中 |
| 2 | 失败 |
| 3 | 完成 |
| 9 | 取消 |

### 7.4 ES 任务申请审批状态
**载体**：acc-es 任务申请 · 码值=1位 · 源 `acc-es.enumns.TaskApplyStat`

| 码值 | 中文含义 |
|---|---|
| 0 | 申请中 |
| 1 | 审批通过 |
| 2 | 拒绝通过 |

### 7.5 ES 任务分配状态
**载体**：acc-es 任务分配 · 码值=1位 · 源 `acc-es.enumns.TaskAssignStat`

| 码值 | 中文含义 |
|---|---|
| 0 | 未分配 |
| 1 | 已分配 |

### 7.6 ES 计划单状态
**载体**：acc-es 计划单 · 码值=1/2位混用 · 源 `acc-es.enumns.PlanStat`

| 码值 | 中文含义 |
|---|---|
| 01 | 申请中 |
| 02 | 审批通过 |
| 03 | 审批未通过 |
| 04 | 拆分中 |
| 05 | 拆分完成 |
| 5 | 未执行 |
| 6 | 已执行 |

> 排查提示：同义区两位码（01~05）与一位码（5/6）混用，落库前归一。

### 7.7 ES 任务类型（class 常量类，非 enum）
**载体**：acc-es 任务类型 · 码值=1位 · 源 `acc-es.enumns.TaskType`

| 码值 | 中文含义 |
|---|---|
| 1 | 票卡发行 |
| 2 | 票卡预赋值 |
| 3 | 缴销 |
| 4 | 注销 |
| 5 | 分拣 |
| 6 | 重编码 |
| 7 | 个性化 |

### 7.8 ES 任务分类（class 常量类，非 enum，码值同 7.7）
**源**：`acc-es.enumns.TaskClassification`

| 码值 | 中文含义 |
|---|---|
| 1 | 发行 |
| 2 | 预赋值 |
| 3 | 缴销 |
| 4 | 注销 |
| 5 | 分拣 |
| 6 | 重编码 |
| 7 | 个性化 |

---

## 八、渠道方言 / 接口返回码

### 8.1 闸机渠道方言（对应 DB 列）
**源**：`gatetxnpay.constant.GateTxnPayFieldCode`

| 码值 | 中文含义 | 对应列 |
|---|---|---|
| 02 | 出站（普通） | GATE_TXN_PAY.TRX_TYPE |
| 03 | 出站（超时/特殊） | 同上 |
| 0B | 地铁钱包 | GATE_TXN_PAY.PAYMENT_VENDOR |
| 01 | 蓝牙渠道（不推公交换乘） | GATE_TXN_PAY.CHANNEL_TYPE |
| Y | 同行票 | GATE_TXN_PAY.COMPANION_FLAG |
| C | 第三方票 | 同上 |

### 8.2 闸机对上游 retCode
**源**：`gatetxnpay.constant.GateTxnPayRetCode`

| 码值 | 中文含义 |
|---|---|
| 0000 | 成功 |
| 8001 | 入参非法/状态不允许（业务拒绝） |
| 8002 | 订单入库失败（含离线码待重算） |
| 8003 | 状态机拒绝 |
| 9002 | 查询类接口通用失败 |

### 8.3 ITP 支付/退款订单状态（collect-pay）
**源**：`collectpay.constant.ItpStatusEnum` · 载体：ITP 订单态

| 码值 | 支付态含义 | 退款态含义 |
|---|---|---|
| 0 | 支付中 | 退款中 |
| 1 | 支付成功 | 退款成功 |
| 2 | 支付失败 | 退款失败 |
| 3 | 未支付 | （无） |

> 排查提示：支付态与退款态**共用 0/1/2**，语义靠上下文区分。

### 8.4 卡激活标记
**源**：`collectpay.constant.ActivateFlagEnum` · 载体：LOGIC_CARD_POOL_CARD / 订单激活态

| 码值 | 中文含义 |
|---|---|
| 0 | 未激活 |
| 1 | 已激活 |

---

## 九、DB 字典表（RuoYi sys_dict，控制台可视维护）

> 载体：`sys_dict_data`（`dict_type` + `dict_value` + `dict_label`）；业务 3 类直接落 `BLACKLIST` 表对应列。

### 9.1 黑名单渠道（业务）
**dict_type**：`itp_black_channel` · 载体：`BLACKLIST.CHANNEL_CODE`

| 码值 | 中文含义 |
|---|---|
| 01 | 地铁APP |
| 02 | 支付宝 |
| 99 | 未知（拿不到渠道的合法落库值，NEVER 用 null） |

### 9.2 黑名单发起方（业务）
**dict_type**：`itp_black_source` · 载体：`BLACKLIST.BLACK_SOURCE`

| 码值 | 中文含义 |
|---|---|
| 01 | 系统自动（扣费失败链路自动拉黑） |
| 02 | 渠道通知（外部渠道要求加黑） |
| 09 | 运维手工（综管台手工新增） |

### 9.3 拉黑原因（业务）
**dict_type**：`itp_black_cause` · 载体：`BLACKLIST.BLACK_CAUSE`

| 码值 | 中文含义 |
|---|---|
| 01 | 未付费欠费（唯一可参与欠费结清盘点） |
| 02 | 挂失补卡（与欠费无关，NEVER 按欠费结清自动解除） |
| 09 | 其他（原因见 REASON 自由文本） |

### 9.4 RuoYi 框架自带 10 类（admin 控制台维护，非地铁业务码表，备查）

| dict_type | 字典名 | 码值 ↔ 中文 |
|---|---|---|
| sys_user_sex | 用户性别 | 0男 / 1女 / 2未知 |
| sys_show_hide | 菜单状态 | 0显示 / 1隐藏 |
| sys_normal_disable | 系统开关 | 0正常 / 1停用 |
| sys_job_status | 任务状态 | 0正常 / 1暂停 |
| sys_job_group | 任务分组 | DEFAULT默认 / SYSTEM系统 |
| sys_yes_no | 系统是否 | Y是 / N否 |
| sys_notice_type | 通知类型 | 1通知 / 2公告 |
| sys_notice_status | 通知状态 | 0正常 / 1停用 |
| sys_oper_type | 操作类型 | 1其他/2新增/3修改/4删除/5授权/6导出 |
| sys_common_status | 系统状态 | 登录状态 |

---

## 十、排查速查索引（按载体列反查）

| 落库列 | 码值形态 | 速查表 |
|---|---|---|
| QRCODE_TXN_DETAIL.CARD_TYPE | 4位票卡码 | §1.1 |
| GATE_TXN_PAY.TRX_TYPE | 2位交易码 | §3.1 |
| GATE_TXN_PAY.PAY_CHANNEL_CODE | 渠道码 | §2.1 |
| GATE_TXN_PAY.PAYMENT_VENDOR | 渠道码 | §2.1 |
| GATE_TXN_PAY.DEBIT_STATUS | 英文串 | §5.11 |
| GATE_TXN_PAY.DISCOUNT_CALC_STATUS | 英文串 | §5.12 |
| QRCODE_STATUS.QR_STATUS | 2位 | §5.1 |
| F2F_ORDER.ORDER_STATUS | 英文串 | §5.2 |
| F2F_ORDER.REFUND_STATUS | 英文串 | §5.3 |
| APP_PAY_SIGN_INFO.SIGN_STATUS | 英文串 | §5.4 |
| ALIPAY_SIGN_INFO.SIGN_STATUS | 英文串 | §5.5 |
| APP_TERMINATION_REQUEST.TERMINATION_STATUS | 英文串 | §5.6 |
| ALIPAY_TERMINATION_REQUEST.STATUS | 英文串 | §5.7 |
| USER_PHONE_CHANGE_LOG.SIGN_SYNC_STATUS | 英文串 | §5.8 |
| USER_ACC_EMPLOYEE_CARD.CARD_STATUS | 1位数字 | §5.9 |
| USER_ACC_EMPLOYEE_CARD_LOG.EVENT_TYPE | 英文串 | §5.10 |
| USER_ITP_REG_INFO.ISSUE_ORG_CODE | 4位 | §2.4 |
| USER_ITP_REG_INFO.CARD_ISSUE_CODE | 4位 | §2.3 |
| BLACKLIST.CHANNEL_CODE | 2位 | §9.1 |
| BLACKLIST.BLACK_SOURCE | 2位 | §9.2 |
| BLACKLIST.BLACK_CAUSE | 2位 | §9.3 |
| TBL_DEVICE_INFO.MANAGE_MODE | 2位（01/02） | §4.4 |
| TBL_DEVICE_INFO.ACC_MANAGE_LEVEL | ACC 码值待提供 | §4.5 |
| STATION_CODE / LINE_CODE | 4C/2C | 站线主数据（非码表） |

## 十一、回写义务
1. 本文件是**全量镜像 + 排查速查**，码值全集以各类源 / SQL 为准源；改码值后 MUST 同步 §十 速查索引。
2. 码值「暂缓收敛」（版本已固化）：支付渠道/票卡类型/对账文件类型三处重复，待后续稳定通知后再归口 `model`，届时同步本文件。
