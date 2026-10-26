# 字典项盘点清单（qd-itp 全仓）

> **定位**：两份配套文档——
> - **本文件（inventory）**：「字典项散落分布」全仓盘点，按载体形式分 5 类，**用于后续收敛归口**（码表在哪 + 痛点 + 收敛建议）。
> - **[dictionary-detail.md（detail）](dictionary-detail.md)**：**给人看的排查速查手册**，逐码表列「码值 ↔ 中文 ↔ 载体列」，排查问题反查码值含义用。
>
> **维护约定**：inventory 是**索引**，detail 是**全量镜像 + 速查**。码值全集以各类源 / SQL 为准源。收敛完成后两份文件都 MUST 同步回写。
> **扫描日期**：2026-10-22。

## 一、概览（按载体形式分 5 类）

| 类 | 载体 | 数量级 | 说明 |
|---|---|---|---|
| A | Java `enum`（业务码表） | ~40 | 码值 + 码名映射，落库 / 接口码值 |
| B | Java `*ErrorCodeEnum`（接口错误码） | ~12 | 4 位返回码表，各模块对外 retCode |
| C | Java 常量类 / 渠道方言收口 | ~8 | `public static final` 码值常量，散在业务模块 |
| D | DB 字典表（RuoYi `sys_dict_*`） | 13 类型 | web-server 字典管理，控制台可视维护 |
| E | 配置文件 / 码列映射 | 若干 | `.properties` / `.sql` 里的码值（如 `GateTxnPayFieldCode` 对应 DB 列） |

**核心痛点**：同一码值空间（支付渠道、票卡类型、状态机）在 A/B/C 三类**多处重复定义、命名不统一**——例如 `PaymentVendorEnum`（0B=钱包）、`GateTxnPayFieldCode.PAYMENT_VENDOR_WALLET`（0B）、model 层 `CardTypeCodeEnum`（0441~044A）互有关联却散落 3 个模块，正是本次盘点要收敛的重点。

**完整码值 ↔ 中文 ↔ 载体列明细见** [dictionary-detail.md](dictionary-detail.md)（§1.1~§9.4），本 inventory 只列「码表在哪 + 指向 detail 哪个 §」。

## 二、A 类：Java 枚举业务码表（~40 个，按模块分组）

### model 模块（跨模块共用码表核心，`com.chinasofti.huateng.model.*`）
| 全限定类名 | detail 指针 | 业务含义 |
|---|---|---|
| `model.enums.IssueChannelCodeEnum` | [detail §1.3](dictionary-detail.md#_1-3-票种-acc-es-域) | 发行渠道编码 |
| `model.enums.CardIssueOrgEnum` | [detail §1.2](dictionary-detail.md#_1-2-票种-产品-订单号前缀) | 发卡机构编码（APP 开户 cardIssueCode） |
| `model.enums.CardTypeCodeEnum` | [detail §1.1](dictionary-detail.md#_1-1-票卡类型编码) | 票卡类型编码 |
| `model.enums.TrxTypeCodeEnum` | [detail §3.1](dictionary-detail.md#_3-1-交易类型) | 交易类型编码 |
| `model.enums.DeviceTypeEnum` | [detail §4.1](dictionary-detail.md#_4-1-设备类型) | 设备类型 |
| `model.ticket.enums.AdviceOptEnum` | [detail §3.3](dictionary-detail.md#_3-3-建议操作码-if5a) | IF5A 建议操作码 |
| `model.ticket.enums.QRCodeStatusEnum` | [detail §5.1](dictionary-detail.md#_5-1-qr-码车票状态机) | QR 码车票状态机 |
| `model.domain.SignStatus` | [detail §5.4](dictionary-detail.md#_5-4-通道签约状态机) | 通道签约状态机（APP_PAY_SIGN_INFO） |
| `model.domain.AlipaySignStatus` | [detail §5.5](dictionary-detail.md#_5-5-支付宝出行签约状态机) | 支付宝出行签约状态机 |
| `model.domain.TerminationStatus` | [detail §5.6](dictionary-detail.md#_5-6-解约申请状态机) | 解约申请状态机 |
| `model.domain.AlipayTerminationStatus` | [detail §5.7](dictionary-detail.md#_5-7-支付宝出行解约登记状态机) | 支付宝解约登记状态机 |
| `model.domain.SyncStatus` | [detail §5.8](dictionary-detail.md#_5-8-跨域同步状态机) | 跨域同步状态机（USER_PHONE_CHANGE_LOG） |
| `model.recon.ReconFileTypeEnum` | [detail §6.5](dictionary-detail.md#_6-5-对账文件类型) | 日终对账文件类型 |

### pay-sign-server
| 全限定类名 | detail 指针 | 业务含义 |
|---|---|---|
| `paysign.constant.SignChannelEnum` | [detail §2.2](dictionary-detail.md#_2-2-签约渠道) | 签约渠道 |
| `paysign.constant.PaymentVendorEnum` | [detail §2.1](dictionary-detail.md#_2-1-支付渠道) | 支付渠道（含数币各银行） |

### collect-pay-server（TVM/BOM 扫码购票 + 充值，**2026-10-13 已下线 0 pod**）
| 全限定类名 | detail 指针 | 业务含义 |
|---|---|---|
| `collectpay.constant.BusinessTypeEnum` | [detail §3.2](dictionary-detail.md#_3-2-tvm-扫码业务类型) | TVM 扫码业务类型 |
| `collectpay.constant.ProductType` | [detail §1.2](dictionary-detail.md#_1-2-票种-产品-订单号前缀) | 票种/产品（订单号前缀） |
| `collectpay.constant.ItpStatusEnum` | [detail §8.3](dictionary-detail.md#_8-3-itp-支付-退款订单状态-collect-pay) | ITP 支付/退款订单状态 |
| `collectpay.constant.ActivateFlagEnum` | [detail §8.4](dictionary-detail.md#_8-4-卡激活标记) | 卡激活标记 |

### gate-txn-pay-server（闸机交易扣款）
| 全限定类名 | detail 指针 | 业务含义 |
|---|---|---|
| `gatetxnpay.constant.DebitStatus` | [detail §5.11](dictionary-detail.md#_5-11-闸机扣款状态机) | GATE_TXN_PAY.DEBIT_STATUS 扣款状态机 |
| `gatetxnpay.constant.DiscountCalcStatus` | [detail §5.12](dictionary-detail.md#_5-12-闸机优惠计算状态) | 优惠计算状态 |
| `gatetxnpay.constant.GateTxnPayFieldCode` | [detail §8.1](dictionary-detail.md#_8-1-闸机渠道方言-对应-db-列) | 闸机渠道方言收口 |
| `gatetxnpay.constant.GateTxnPayRetCode` | [detail §8.2](dictionary-detail.md#_8-2-闸机对上游-retcode) | 闸机对上游 retCode |

### face-pay-server（F2F 人脸支付）
| 全限定类名 | detail 指针 | 业务含义 |
|---|---|---|
| `facepay.domain.F2fOrderStatus` | [detail §5.2](dictionary-detail.md#_5-2-f2f-订单状态机-人脸支付) | F2F_ORDER.ORDER_STATUS 订单状态机 |
| `facepay.domain.F2fOrderRefundStatus` | [detail §5.3](dictionary-detail.md#_5-3-f2f-退款状态) | F2F_ORDER.REFUND_STATUS |

### account-server（员工卡）
| 全限定类名 | detail 指针 | 业务含义 |
|---|---|---|
| `account.domain.EmployeeCardStatus` | [detail §5.9](dictionary-detail.md#_5-9-员工卡状态) | USER_ACC_EMPLOYEE_CARD.CARD_STATUS |
| `account.domain.EmployeeCardEvent` | [detail §5.10](dictionary-detail.md#_5-10-员工卡事件类型) | 员工卡事件类型 |

### recon-server（日终对账）
| 全限定类名 | detail 指针 | 业务含义 |
|---|---|---|
| `recon.model.ReconBatchStatus` | [detail §6.1](dictionary-detail.md#_6-1-对账批次状态机) | 对账批次状态机 |
| `recon.model.ReconSourceStatus` | [detail §6.2](dictionary-detail.md#_6-2-单来源抽取进度) | 单来源抽取进度 |
| `recon.model.ReconFileType` | [detail §6.5](dictionary-detail.md#_6-5-对账文件类型) | 对账文件类型 |
| `recon.model.ReconFileStatus` | [detail §6.3](dictionary-detail.md#_6-3-对账文件投递状态) | 对账文件投递状态 |
| `recon.model.ReconPartStatus` | [detail §6.4](dictionary-detail.md#_6-4-对账分片接收状态) | 对账分片接收状态 |

### acc-es-server（ACC ES 设备任务，`enumns` 包）
| 全限定类名 | detail 指针 | 业务含义 |
|---|---|---|
| `acc.es.server.enumns.PersonTypeEnum` | [detail §4.3](dictionary-detail.md#_4-3-人员类型) | 人员类型 |
| `acc.es.server.enumns.TicketType` | [detail §1.3](dictionary-detail.md#_1-3-票种-acc-es-域) | 票种 |
| `acc.es.server.enumns.PidCdEnum` | [detail §4.2](dictionary-detail.md#_4-2-证件类型) | 证件类型码表 |
| `acc.es.server.enumns.EsStat` | [detail §7.1](dictionary-detail.md#_7-1-es-设备状态) | ES 设备状态 |
| `acc.es.server.enumns.LoginStat` | [detail §7.2](dictionary-detail.md#_7-2-es-签到状态) | ES 签到状态 |
| `acc.es.server.enumns.TaskExecuteStat` | [detail §7.3](dictionary-detail.md#_7-3-es-任务执行状态) | ES 任务执行状态 |
| `acc.es.server.enumns.TaskApplyStat` | [detail §7.4](dictionary-detail.md#_7-4-es-任务申请审批状态) | ES 任务申请审批状态 |
| `acc.es.server.enumns.TaskAssignStat` | [detail §7.5](dictionary-detail.md#_7-5-es-任务分配状态) | ES 任务分配状态 |
| `acc.es.server.enumns.PlanStat` | [detail §7.6](dictionary-detail.md#_7-6-es-计划单状态) | ES 计划单状态 |

> ⚠️ **注意**：`acc-es-server.enumns.TaskType` 与 `TaskClassification` 是 **`public class` 常量类**（非 enum），码值 1=发行~7=个性化，归 C 类管理。

## 三、B 类：Java 接口错误码（`*ErrorCodeEnum`，~12 个）

各 server 模块对外 retCode 表，4 位码值映射。**是否纳入字典收敛由业主裁决**——接口错误码通常随接口契约走，不宜与业务码表混一池。
> 详细码值清单（本类**不纳入** detail 镜像，因为接口错误码有专属契约）。

| 模块 | 类名 | 说明 |
|---|---|---|
| collect-pay | `BomPayCodeEnum` / `TvmPayCodeEnum` / `AppCodeEnum` / `BomBusinessCodeEnum` | BOM/TVM/APP 返回码 |
| face-pay | `DeviceRetCode` / `PaymentResult` / `PayCenterStatus` / `PayScene` | 设备对外 retCode / 支付中心状态 |
| model/common | `FepAppErrorCodeEnum` | fep-app 接口错误码 |
| fep-dev | `FepDevErrorCodeEnum` | 设备前置返回码 |
| ticket/collect-ticket/key/blacklist/trans-query/pay-sign/acc-secure/account | 各自 `*ErrorCodeEnum` | 各模块接口错误码表 |

## 四、C 类：Java 常量类 / 渠道方言收口（~8 处，散在业务模块）

非枚举的码值常量，常与 A 类枚举、DB 列**三处重复定义**——收敛重点。
> 详细码值清单见 [detail §8.1](dictionary-detail.md#_8-1-闸机渠道方言-对应-db-列)~§8.4。

| 类名（全限定） | 模块 | detail 指针 | 说明 |
|---|---|---|---|
| `GateTxnPayFieldCode` | gate-txn-pay | [detail §8.1](dictionary-detail.md#_8-1-闸机渠道方言-对应-db-列) | 闸机渠道方言收口（出站/钱包/蓝牙/同行票） |
| `GateTxnPayRetCode` | gate-txn-pay | [detail §8.2](dictionary-detail.md#_8-2-闸机对上游-retcode) | 闸机对上游 retCode |
| `AccResultCode` | account | （纯判据类，无完整码值） | ACC 出向成功判定 |
| `TaskType` / `TaskClassification` | acc-es | [detail §7.7](dictionary-detail.md#_7-7-es-任务类型-class-常量类-非-enum)/§7.8 | ES 任务类型/分类（**class 非 enum**） |
| `UserConstants` | web-common | RuoYi 框架通用常量 | 用户/角色常量（非业务码表） |
| `ScheduleConstants` | web-common | RuoYi 框架通用常量 | 定时任务状态（非业务码表） |
| `GenConstants` | web-common | RuoYi 框架通用常量 | 代码生成器常量（非业务码表） |
| `web/src/utils/deviceManageMode.js` | web（综管台前端） | [detail §4.4](dictionary-detail.md#_4-4-设备管理模式-业主自定义-2026-10-22-新增) | 设备管理模式 01/02（2026-10-22 新增，与 `TBL_DEVICE_INFO.MANAGE_MODE` CHECK 约束同源；独立码值空间 NEVER 并入其他码表） |
| （ACC 侧 manageLevel） | para-server 同步落库 | [detail §4.5](dictionary-detail.md#_4-5-acc-管理级别-码值清单待-acc-提供-never-臆造) | ACC 管理级别，码值清单待 ACC 提供（`TBL_DEVICE_INFO.ACC_MANAGE_LEVEL`） |

> `Constants`（web-common 通用常量）是**框架通用**（HTTP/字符集/权限分隔符），**非业务码表**，已排除。

## 五、D 类：DB 字典表（RuoYi `sys_dict_type` / `sys_dict_data`，13 类型）

> 详细码值清单见 [detail §9.1](dictionary-detail.md#_9-1-黑名单渠道-业务)~§9.4。

### RuoYi 框架自带 10 类（`web-server/sql/ry_20250522.sql`，admin 控制台维护）
| dict_type | 字典名 | detail 指针 |
|---|---|---|
| `sys_user_sex` | 用户性别 | [detail §9.4](dictionary-detail.md#_9-4-ruoyi-框架自带-10-类-admin-控制台维护-非地铁业务码表-备查) |
| `sys_show_hide` | 菜单状态 | 同上 |
| `sys_normal_disable` | 系统开关 | 同上 |
| `sys_job_status` | 任务状态 | 同上 |
| `sys_job_group` | 任务分组 | 同上 |
| `sys_yes_no` | 系统是否 | 同上 |
| `sys_notice_type` | 通知类型 | 同上 |
| `sys_notice_status` | 通知状态 | 同上 |
| `sys_oper_type` | 操作类型 | 同上 |
| `sys_common_status` | 系统状态 | 同上 |

### 业务自定义 3 类（`blacklist-server/sql/blacklist-refactor-migration.sql`）
| dict_type | 字典名 | detail 指针 |
|---|---|---|
| `itp_black_channel` | 黑名单渠道 | [detail §9.1](dictionary-detail.md#_9-1-黑名单渠道-业务) |
| `itp_black_source` | 黑名单发起方 | [detail §9.2](dictionary-detail.md#_9-2-黑名单发起方-业务) |
| `itp_black_cause` | 拉黑原因 | [detail §9.3](dictionary-detail.md#_9-3-拉黑原因-业务) |

> 对应表：`web-server/web-common/.../entity/SysDictType.java`、`SysDictData.java`；`DictUtils` 提供缓存读取。**业务字典 3 类是地铁码值**（黑名单域），与 `BLACKLIST` 表 `CHANNEL_CODE/BLACK_SOURCE/BLACK_CAUSE` 列直接对应。

## 六、E 类：配置文件 / DB 码列映射

| 码列 | 所在表 | detail 指针 |
|---|---|---|
| `PAY_CHANNEL_CODE` | GATE_TXN_PAY / QRCODE_TXN_DETAIL | [detail §2.1](dictionary-detail.md#_2-1-支付渠道) |
| `TRX_TYPE` | GATE_TXN_PAY / QRCODE_TXN_DETAIL | [detail §3.1](dictionary-detail.md#_3-1-交易类型) |
| `PAYMENT_VENDOR` | GATE_TXN_PAY | [detail §2.1](dictionary-detail.md#_2-1-支付渠道) |
| `CHANNEL_TYPE` | GATE_TXN_PAY | [detail §8.1](dictionary-detail.md#_8-1-闸机渠道方言-对应-db-列) |
| `COMPANION_FLAG` | GATE_TXN_PAY | [detail §8.1](dictionary-detail.md#_8-1-闸机渠道方言-对应-db-列) |
| `ISSUE_ORG_CODE` | USER_ITP_REG_INFO | [detail §1.2](dictionary-detail.md#_1-2-票种-产品-订单号前缀) |
| `CARD_ISSUE_CODE` | USER_ITP_REG_INFO | [detail §1.3](dictionary-detail.md#_1-3-票种-acc-es-域) |
| `DEBIT_STATUS` / `DISCOUNT_CALC_STATUS` | GATE_TXN_PAY | [detail §5.11](dictionary-detail.md#_5-11-闸机扣款状态机)/§5.12 |
| `QR_STATUS` | QRCODE_STATUS | [detail §5.1](dictionary-detail.md#_5-1-qr-码车票状态机) |
| `CARD_TYPE` | BLACKLIST | [detail §1.1](dictionary-detail.md#_1-1-票卡类型编码)（票种） |
| `CHANNEL_CODE` / `BLACK_SOURCE` / `BLACK_CAUSE` | BLACKLIST | [detail §9.1](dictionary-detail.md#_9-1-黑名单渠道-业务)~§9.3 |
| `STATION_CODE` / `LINE_CODE` | 站线表 | 主数据（4C 站码 / 2C 线码），非码表 |
| 各 `*_STAT_CODE` | 各业务表 | 由对应状态机枚举定 |

- **`.properties`/`.yml`**：以 `service.*.url` 等配置为主，**未发现独立码表**（码值集中在 Java 枚举 / DB 字典）。
- **菜单码**：`sys_menu`（`MENU_ID/PARENT_ID/ORDER_NUM/IS_FRAME/IS_CACHE`，NUMBER）——严格说是主数据非码表，但常被当字典用，**本次不列入**。

## 七、回写义务与下一步收敛建议

1. **收敛真源**：支付渠道（`PaymentVendorEnum` + `GateTxnPayFieldCode` + DB `PAY_CHANNEL_CODE` 列）、票卡类型（`CardTypeCodeEnum` + `ProductType` + `acc-es.TicketType`）是**三处重复重灾区**，建议指定 `model` 模块为单一真源，业务模块引用而非自建。
2. **接口错误码（B 类）** 是否并入字典体系：默认**不入**（随接口契约走）；若要做全局码表，需在 `model` 建统一 `ErrorCode` 规范。
3. **维护**：本清单是**索引**，码值全集以 detail 镜像为准。后续每收敛一个码表，MUST 在 §七 标注收敛状态（`[已收敛]`/`[进行中]`）并回写 §一 痛点 + 同步 detail。
