# 老库导出 scheme 按域拆分（2026-10-23）

**源**：`deploy/sche/sche.sql` —— 2026-10-23 08:27 从老库 `172.20.222.3:1521/AFCITPDB`（schema `QDITP`）导出的全量 DDL，**175 个对象（全部是 CREATE TABLE；索引/约束见 §6 缺口说明）**。
**生成器**：`docs/ops/environments/scripts/migration/20261023_split_sche_by_domain.py`（可重跑；映射写死在脚本里，断言全覆盖 175 张，漏一张即报错）。
**用途**：红岛环境数据库 scheme 重建。与 2026-10-17 那版（`docs/ops/environments/scripts/migration/20261017_schema_*.sql`，DDL 取自仓库本地 SQL）的关系：**那版防老库漂移、这版对齐老库实况**，两版并存；表结构以本版为基线，索引以本地 DDL 为基线（见 §6）。

## 1. 文件与执行顺序

| 文件 | 目标 schema | 表数 | 说明 |
|---|---|---|---|
| `QDITP_PLAT.sql` | QDITP_PLAT | 102 | 平台公共，MUST 第一个建（被引用方） |
| `QDITP_USER.sql` | QDITP_USER | 20 | 用户中心 |
| `QDITP_RIDE.sql` | QDITP_RIDE | 3 | 乘运 |
| `QDITP_TXN.sql` | QDITP_TXN | 39 | 交易支付 |
| `_补充索引_本地DDL_需与老库核对.sql` | 四域 | 169 | **独立索引候选清单**（本地 DDL 来源，见 §6） |
| `_未纳入_BAK快照与STATION_INFO.sql` | — | 11 | 注释态，NEVER 直接执行 |

执行纪律（与 2026-10-17 版一致，详见各文件头）：**以域用户执行**（NEVER SYS）；顺序 PLAT→USER→RIDE→TXN；**已删全部 `TABLESPACE "USERS"` 子句**，表落域用户默认表空间（红岛 MUST 先建用户并绑 `ITP_<域>_TS`；未建默认表空间就把 `TABLESPACE "USERS"` 替换回去）；建表后 MUST 补独立索引（§6）再灌数；跨域同义词/只读授权/灌数/序列校准不在本文件（见 `20261014_create_split_grants_synonyms.sql` 与 bundle 跨库脚本）。**配套数据导入文件**（同一套域映射，按外键拓扑序拆到 `deploy/data/by-domain/`）见 `20261023_split_data_by_domain.py` 与 `deploy/data/by-domain/README.md`。

## 2. 变换规则（相对老库导出）

1. `"QDITP".<对象>` → `"QDITP_<域>".<对象>`（CREATE TABLE / COMMENT ON / CREATE INDEX / ALTER TABLE 全部改写）。
2. 删除全部 `TABLESPACE "USERS"` 子句（表/约束索引/LOB 三处）。
3. `COMMENT ON TABLE/COLUMN` 全部保留（老库注释是资产；已核对条数守恒：源 1192 列注释 + 115 表注释 = 四域 1188+114 + 未纳入 4+1）。
4. 源文件 CRLF，输出统一 LF（sqlplus 无差异）。
5. **外键零跨域**：全部 `REFERENCES` 均指向本域 schema（PLAT/USER/TXN 各自域内；RIDE 无外键）——拆分不产生跨域外键依赖，这是本次拆分最顺利的一点。
6. 分区表现状（老库实况，与工作记忆「只到 P202612」有出入，MUST 知悉）：
   - 8 张 RANGE 表（GATE_TXN_PAY / QRCODE_TXN_DETAIL / ALIPAY_PAY_TXN_DETAIL / ALIPAY_REFUND_TXN_DETAIL / ALIPAY_PAY_CENTER_MSG_LOG / PAY_TXN_DETAIL / PAY_CALLBACK_LOG / PAY_REFUND_DETAIL）均为 `P202606~P202612`（7 个月度分区，上界 '20270101'）+ **`P_MAX` 兜底分区（VALUES LESS THAN MAXVALUE，SEGMENT CREATION DEFERRED）**。即 2027-01 起新数据不会插失败，但会全部落进 P_MAX，**按月裁剪/归档仍然失效**——红岛重建 MUST 决定：拆 P_MAX 补月度分区，或改建 INTERVAL 自动扩展（F2F_ORDER/F2F_PAYMENT/F2F_RESULT_REPORT 已是 `INTERVAL (NUMTOYMINTERVAL(1,'MONTH'))` 形态，可作样板）。
   - `ALIPAY_PAY_CALLBACK_LOG` 在老库导出中**无分区**（manifest 曾列它为 RANGE）——重建时按需补。
   - `QRCODE_STATUS` / `USER_ITP_REG_INFO` 为 LIST 分区，随建表带入。

## 3. 映射总表（175 = 164 入域 + 11 不纳入）

### QDITP_PLAT（102 张）
| # | 表 | 决策来源 ||---|---|---|| 1 | ACC_SIMULATION_HISTORY | manifest 2026-10-17 四域清单 || 2 | ACC_SIM_EMPLOYEE_CARD | manifest 2026-10-17 四域清单 || 3 | AGM_DEVICE_STATUS | manifest 2026-10-17 四域清单 || 4 | BLACKLIST | manifest 2026-10-17 四域清单 || 5 | BLACKLIST_OPERATE_LOG | manifest 2026-10-17 四域清单 || 6 | BLACKLIST_RELEASED | manifest 2026-10-17 四域清单 || 7 | BOM_TOPUP_RESULT | manifest 2026-10-17 四域清单 || 8 | COM_DEVICE_SYN_KEY | manifest 2026-10-17 四域清单 || 9 | GATEWAY_ROUTE_CONFIG | manifest 2026-10-17 四域清单 || 10 | GATEWAY_ROUTE_CONFIG_AUDIT | manifest 2026-10-17 四域清单 || 11 | GEN_TABLE | manifest 2026-10-17 四域清单 || 12 | GEN_TABLE_COLUMN | manifest 2026-10-17 四域清单 || 13 | METRO_AGM_KEY_POOL | manifest 2026-10-17 四域清单 || 14 | METRO_AGM_KEY_VERSION | manifest 2026-10-17 四域清单 || 15 | METRO_CA_KEYSTORE | manifest 2026-10-17 四域清单 || 16 | METRO_MEMBER_STATIC_KEY | manifest 2026-10-17 四域清单 || 17 | QRTZ_BLOB_TRIGGERS | manifest 2026-10-17 四域清单 || 18 | QRTZ_CALENDARS | manifest 2026-10-17 四域清单 || 19 | QRTZ_CRON_TRIGGERS | manifest 2026-10-17 四域清单 || 20 | QRTZ_FIRED_TRIGGERS | manifest 2026-10-17 四域清单 || 21 | QRTZ_JOB_DETAILS | manifest 2026-10-17 四域清单 || 22 | QRTZ_LOCKS | manifest 2026-10-17 四域清单 || 23 | QRTZ_PAUSED_TRIGGER_GRPS | manifest 2026-10-17 四域清单 || 24 | QRTZ_SCHEDULER_STATE | manifest 2026-10-17 四域清单 || 25 | QRTZ_SIMPLE_TRIGGERS | manifest 2026-10-17 四域清单 || 26 | QRTZ_SIMPROP_TRIGGERS | manifest 2026-10-17 四域清单 || 27 | QRTZ_TRIGGERS | manifest 2026-10-17 四域清单 || 28 | SYS_CONFIG | manifest 2026-10-17 四域清单 || 29 | SYS_DEPT | manifest 2026-10-17 四域清单 || 30 | SYS_DICT_DATA | manifest 2026-10-17 四域清单 || 31 | SYS_DICT_TYPE | manifest 2026-10-17 四域清单 || 32 | SYS_JOB | manifest 2026-10-17 四域清单 || 33 | SYS_JOB_LOG | manifest 2026-10-17 四域清单 || 34 | SYS_LOGININFOR | manifest 2026-10-17 四域清单 || 35 | SYS_MENU | manifest 2026-10-17 四域清单 || 36 | SYS_NOTICE | manifest 2026-10-17 四域清单 || 37 | SYS_OPER_LOG | manifest 2026-10-17 四域清单 || 38 | SYS_POST | manifest 2026-10-17 四域清单 || 39 | SYS_ROLE | manifest 2026-10-17 四域清单 || 40 | SYS_ROLE_DEPT | manifest 2026-10-17 四域清单 || 41 | SYS_ROLE_MENU | manifest 2026-10-17 四域清单 || 42 | SYS_USER | manifest 2026-10-17 四域清单 || 43 | SYS_USER_POST | manifest 2026-10-17 四域清单 || 44 | SYS_USER_ROLE | manifest 2026-10-17 四域清单 || 45 | TBL_ADD_PARA | manifest 2026-10-17 四域清单 || 46 | TBL_APP_ORDER_REFUND | manifest 2026-10-17 四域清单 || 47 | TBL_BASE_FARE | manifest 2026-10-17 四域清单 || 48 | TBL_BOM_BUS_RESULT | manifest 2026-10-17 四域清单 || 49 | TBL_BOM_MAIN_TICKET | manifest 2026-10-17 四域清单 || 50 | TBL_BOM_ORDER_PAY | manifest 2026-10-17 四域清单 || 51 | TBL_BOM_ORDER_REFUND | manifest 2026-10-17 四域清单 || 52 | TBL_BOM_SALE_INFO | manifest 2026-10-17 四域清单 || 53 | TBL_BOM_SUB_TICKET | manifest 2026-10-17 四域清单 || 54 | TBL_BOM_TICKET_REFUND | manifest 2026-10-17 四域清单 || 55 | TBL_BOM_TOPUP_RESULT | manifest 2026-10-17 四域清单 || 56 | TBL_CHIP_TYPE | manifest 2026-10-17 四域清单 || 57 | TBL_FARE_GROUP | manifest 2026-10-17 四域清单 || 58 | TBL_FARE_MATRIX | manifest 2026-10-17 四域清单 || 59 | TBL_FARE_TIME | manifest 2026-10-17 四域清单 || 60 | TBL_LINE_INFO | manifest 2026-10-17 四域清单 || 61 | TBL_NOTICE_APP_FAILURE_RECORD | manifest 2026-10-17 四域清单 || 62 | TBL_NOTICE_APP_REFUND_RECORD | manifest 2026-10-17 四域清单 || 63 | TBL_NOTICE_APP_TAKETICKET_RECORD | manifest 2026-10-17 四域清单 || 64 | TBL_ORDER_REFUND_CYCLE | manifest 2026-10-17 四域清单 || 65 | TBL_PARA_VERSION | manifest 2026-10-17 四域清单 || 66 | TBL_RISK_CONTROL_LOG | manifest 2026-10-17 四域清单 || 67 | TBL_RISK_GROUP | manifest 2026-10-17 四域清单 || 68 | TBL_RISK_RULE | manifest 2026-10-17 四域清单 || 69 | TBL_SECT_INFO | manifest 2026-10-17 四域清单 || 70 | TBL_SINGLE_TICKET_PURCHASE_LIMIT | manifest 2026-10-17 四域清单 || 71 | TBL_SPECIAL_DATE | manifest 2026-10-17 四域清单 || 72 | TBL_STATION_INFO | ？ || 73 | TBL_STL_ACCT_INFO | ？ || 74 | TBL_STL_PERSON_INFO | ？ || 75 | TBL_STL_TICKET_INFO | ？ || 76 | TBL_STL_TICKET_SET | ？ || 77 | TBL_TICKET_FARE | ？ || 78 | TBL_TICKET_REFUND_RECORD | 本次新裁决（collect-pay 旧表，用户已确认归 PLAT） || 79 | TBL_TICKET_TYPE | ？ || 80 | TBL_TIME_INTERVAL | ？ || 81 | TBL_TKT_ES_ACCOUNT | ？ || 82 | TBL_TKT_ES_ASSIGN | ？ || 83 | TBL_TKT_ES_FILE_PROC_LOG | ？ || 84 | TBL_TKT_ES_INFO | ？ || 85 | TBL_TKT_ES_PROC | ？ || 86 | TBL_TKT_ES_REPORT | ？ || 87 | TBL_TKT_ES_TASK | ？ || 88 | TBL_TKT_PRE_PERSON | ？ || 89 | TBL_TKT_TASK_PLAN | ？ || 90 | TBL_TOTAL_SALE_PART | ？ || 91 | TBL_TSF_INFO | ？ || 92 | TBL_TVM_APP_ORDER | 本次新裁决（collect-pay 旧表，用户已确认归 PLAT） || 93 | TBL_TVM_MAIN_TICKET | 本次新裁决（collect-pay 旧表，用户已确认归 PLAT） || 94 | TBL_TVM_ORDER_PAY | 本次新裁决（collect-pay 旧表，用户已确认归 PLAT） || 95 | TBL_TVM_ORDER_PAY_PRE | 本次新裁决（collect-pay 旧表，用户已确认归 PLAT） || 96 | TBL_TVM_ORDER_REFUND | 本次新裁决（collect-pay 旧表，用户已确认归 PLAT） || 97 | TBL_TVM_ORDER_TOPUP | 本次新裁决（collect-pay 旧表，用户已确认归 PLAT） || 98 | TBL_TVM_SUB_TICKET | 本次新裁决（collect-pay 旧表，用户已确认归 PLAT） || 99 | TBL_TVM_TAKE_TICKET_ORDER | 本次新裁决（collect-pay 旧表，用户已确认归 PLAT） || 100 | TBL_TVM_TOPUP_NOTIY | 本次新裁决（collect-pay 旧表，用户已确认归 PLAT） || 101 | TBL_ZONE_DTL | ？ || 102 | TBL_ZONE_INFO | ？ |
### QDITP_USER（20 张）
| # | 表 | 决策来源 ||---|---|---|| 1 | ACCOUNT_EXCEPTION_TICKET | manifest 2026-10-17 四域清单 || 2 | ALIPAY_CARD_POOL | manifest 2026-10-17 四域清单 || 3 | ALIPAY_PAY_LOG | manifest 2026-10-17 四域清单 || 4 | ALIPAY_PHONE_CHANGE_LOG | manifest 2026-10-17 四域清单 || 5 | ALIPAY_REFUND_LOG | manifest 2026-10-17 四域清单 || 6 | ALIPAY_REG_LOG | manifest 2026-10-17 四域清单 || 7 | ALIPAY_SIGN_INFO | manifest 2026-10-17 四域清单 || 8 | ALIPAY_SIGN_LOG | manifest 2026-10-17 四域清单 || 9 | ALIPAY_TERMINATION_REQUEST | manifest 2026-10-17 四域清单 || 10 | ALIPAY_USER_INFO | manifest 2026-10-17 四域清单 || 11 | APP_USER_PAY_CHANNEL | manifest 2026-10-17 四域清单 || 12 | LOGIC_CARD_POOL_BATCH | manifest 2026-10-17 四域清单 || 13 | LOGIC_CARD_POOL_CARD | ？ || 14 | LOGIC_CARD_POOL_TYPE_LOCK | ？ || 15 | USER_ACC_EMPLOYEE_CARD | ？ || 16 | USER_ACC_EMPLOYEE_CARD_LOG | ？ || 17 | USER_ACC_TICKETNO | ？ || 18 | USER_ITP_REG_INFO | ？ || 19 | USER_ITP_REG_LOG | ？ || 20 | USER_PHONE_CHANGE_LOG | ？ |
### QDITP_RIDE（3 张）
| # | 表 | 决策来源 ||---|---|---|| 1 | QRCODE_STATUS | manifest 2026-10-17 四域清单 || 2 | QRCODE_SUPPLEMENT_REQUEST | manifest 2026-10-17 四域清单 || 3 | QRCODE_TXN_DETAIL | manifest 2026-10-17 四域清单 |
### QDITP_TXN（39 张）
| # | 表 | 决策来源 ||---|---|---|| 1 | ALIPAY_PAY_CALLBACK_LOG | manifest 2026-10-17 四域清单 || 2 | ALIPAY_PAY_CENTER_MSG_LOG | manifest 2026-10-17 四域清单 || 3 | ALIPAY_PAY_TXN_DETAIL | manifest 2026-10-17 四域清单 || 4 | ALIPAY_REFUND_TXN_DETAIL | manifest 2026-10-17 四域清单 || 5 | APP_PAY_LOGS | manifest 2026-10-17 四域清单 || 6 | APP_PAY_SIGN_INFO | manifest 2026-10-17 四域清单 || 7 | APP_PAY_SIGN_LOG | manifest 2026-10-17 四域清单 || 8 | APP_PAY_SIGN_REQUEST | manifest 2026-10-17 四域清单 || 9 | APP_TERMINATION_REQUEST | manifest 2026-10-17 四域清单 || 10 | DAILY_TICKET_INSTANCE | manifest 2026-10-17 四域清单 || 11 | DAILY_TICKET_ORDER | manifest 2026-10-17 四域清单 || 12 | DAILY_TICKET_PAY_LOG | manifest 2026-10-17 四域清单 || 13 | DAILY_TICKET_PAY_NOTIFY | manifest 2026-10-17 四域清单 || 14 | DAILY_TICKET_REFUND | manifest 2026-10-17 四域清单 || 15 | DAILY_TICKET_REFUND_DETAIL | manifest 2026-10-17 四域清单 || 16 | DAILY_TICKET_USAGE_LOG | manifest 2026-10-17 四域清单 || 17 | DISCOUNT_LEVEL | manifest 2026-10-17 四域清单 || 18 | F2F_DEVICE_STATUS | manifest 2026-10-17 四域清单 || 19 | F2F_NOTIFY_TASK | manifest 2026-10-17 四域清单 || 20 | F2F_ORDER | manifest 2026-10-17 四域清单 || 21 | F2F_PAYMENT | manifest 2026-10-17 四域清单 || 22 | F2F_REFUND | manifest 2026-10-17 四域清单 || 23 | F2F_RESULT_REPORT | manifest 2026-10-17 四域清单 || 24 | F2F_TICKET | manifest 2026-10-17 四域清单 || 25 | GATE_RETRY_QUEUE | manifest 2026-10-17 四域清单 || 26 | GATE_TXN_PAY | manifest 2026-10-17 四域清单 || 27 | METRO_TRANSFER_PUSH_TASK | manifest 2026-10-17 四域清单 || 28 | PAY_CALLBACK_LOG | manifest 2026-10-17 四域清单 || 29 | PAY_REFUND_DETAIL | manifest 2026-10-17 四域清单 || 30 | PAY_TXN_DETAIL | manifest 2026-10-17 四域清单 || 31 | RECON_BATCH | manifest 2026-10-17 四域清单 || 32 | RECON_BATCH_FILE | manifest 2026-10-17 四域清单 || 33 | RECON_BATCH_PART | manifest 2026-10-17 四域清单 || 34 | RECON_BATCH_SOURCE | manifest 2026-10-17 四域清单 || 35 | RECON_THIRD_BILL | manifest 2026-10-17 四域清单 || 36 | RECON_TXN_DIFF | manifest 2026-10-17 四域清单 || 37 | SUPPLEMENT_ORDER | ？ || 38 | SUPPLEMENT_ORDER_ITEM | ？ || 39 | TRAVEL_TICKET_ORDER | ？ |

### 不纳入（11 张）

| 表 | 原因 |
|---|---|
| BAK_ALIPAY_PAY_TXN_DETAIL_20261015 | CTAS 快照，重建不需要 |
| BAK_ALIPAY_REFUND_TXN_DETAIL_20261015 | CTAS 快照，重建不需要 |
| BAK_DAILY_TICKET_INSTANCE_20261015 | CTAS 快照，重建不需要 |
| BAK_DAILY_TICKET_ORDER_20261015 | CTAS 快照，重建不需要 |
| BAK_F2F_DEVICE_STATUS_20261015 | CTAS 快照，重建不需要 |
| BAK_RECON_BATCH_20261013 | CTAS 快照，重建不需要 |
| BAK_RECON_BATCH_PART_20261013 | CTAS 快照，重建不需要 |
| BAK_RECON_BATCH_SOURCE_20261013 | CTAS 快照，重建不需要 |
| BAK_SYS_CONFIG_20261015 | CTAS 快照，重建不需要 |
| BAK_TRAVEL_TICKET_ORDER_20261015 | CTAS 快照，重建不需要 |
| STATION_INFO | 不迁（同义词方案） |

## 4. 待用户确认（本次新裁决，改脚本列表重跑即可，10 秒完事）

1. **22 张 collect-pay 旧表归 PLAT（2026-10-23 用户裁定「放哪都行」，维持 PLAT）**：`TBL_TVM_*`（9）、`TBL_BOM_*`（8）、`BOM_TOPUP_RESULT`、`TBL_APP_ORDER_REFUND`、`TBL_TICKET_REFUND_RECORD`、`TBL_NOTICE_APP_REFUND_RECORD`、`TBL_NOTICE_APP_TAKETICKET_RECORD`。依据：分库计划 §3 勘误的首选建议（PLAT）+ collect-pay 2026-10-13 已下线 0 pod、旧表无新数据 + PLAT 已托管 ACC 设备与 NOTICE 同类表。若日后要改归 TXN（计划给过的备选是「按 tvm-bom-pay 独断」第五域），改本脚本 PLAT/TXN 两个列表重跑即可。
2. **STATION_INFO 不迁**：沿用 manifest 裁决（RIDE 改同义词读 `QDITP_PLAT.TBL_STATION_INFO`）。若红岛确认 ticket-server 仍直连 `STATION_INFO`，需改归 RIDE 或补建。
3. **ALIPAY_PAY_LOG 被 gate-txn-pay 跨域读**（`GateTxnPayManualOpsService`）：归 USER 后 TXN 侧需同义词只读授权或改 RPC，列入 M1 授权清单核对。
4. **10 张 BAK_* 快照**未纳入；红岛若要保留历史备份形态，自行从 `_未纳入_*.sql` 解注。

## 5. 与 2026-10-17 版的表数差

- 2026-10-17：PLAT 76（71+5 RuoYi）/ USER 13（含幻影表 `USER_ITP_REG_INFO_BAK20260909`，库内不存在）/ RIDE 3 / TXN 36 = 128 行清单。
- 本版（老库实况 175）：PLAT 102 / USER 20 / RIDE 3 / TXN 39 = 164 入域 + BAK 10 + STATION_INFO 1。
- 增量全部来自 manifest 未覆盖的表：alipay-account 8（→USER）、gate-txn-pay 拥有的 DISCOUNT_LEVEL / METRO_TRANSFER_PUSH_TASK 与 APP_PAY_LOGS（→TXN）、22 张 collect-pay 旧表（→PLAT，待确认）、AGM_DEVICE_STATUS / GATEWAY_ROUTE_CONFIG* / QRTZ_SIMPROP_TRIGGERS（→PLAT，manifest 缺口裁决）。

## 6. ⚠️ 已知缺口：独立索引不在老库导出里（红岛重建 MUST 补）

老库导出工具只吐了「表 + 内联约束 + 约束背书索引」：全文件仅 2 条独立索引语句（`UK_GATE_TXN_PAY_BIZ` 配 ALTER TABLE 加约束、`IDX_ALIPAY_SIGN_INFO_THIRD_USER_ID`），而**仓库本地 DDL 中的独立索引几乎全部（169 个）不在导出里（含 33 个唯一索引）**。漏建唯一索引 = 幂等防线丢失（AGENTS.md §4.2 幂等设计是硬约束），**NEVER 只靠本导出建红岛 scheme**。

处置：`_补充索引_本地DDL_需与老库核对.sql` 已按域归位全部 169 个候选索引（GLOBAL/LOCAL 限定照抄本地 DDL，表名已加域前缀；`TABLESPACE` 子句已删、`EXECUTE IMMEDIATE '...'` 的 `''` 转义与尾引号已还原、`TABLE(` 缺空格已补——本地 DDL 这三类不规范会原样传导，故在生成端统一净化）。

**核对方式（网络可达老库时 MUST 先跑，一条命令）**：
```bash
python3 docs/ops/environments/scripts/migration/20261023_verify_indexes_against_db.py
```
连上 `172.20.222.3:1521/AFCITPDB`（默认 `qditp/qditp`，可用 `ITP_DB_PASSWORD` 覆盖）后逐索引比对 `all_indexes`/`all_ind_columns`/`all_part_indexes`，判定 ✅一致 / ⚠️列漂移·唯一性·分区类型·状态不一致 / ❌老库不存在，**并把结论按条目回写进补充索引文件**（可重跑刷新）；连不上时只报网络原因、exit 2、NEVER 改文件。函数式索引（`UK_UIRI_*` 的 CASE WHEN 表达式）只验存在性与唯一性、不逐列比。反向缺口（老库有、本地清单与老库导出都没有）会单独列出——那些 MUST 补进红岛 scheme。**本机当前网络不可达该库（2026-10-23 实测 ICMP/TCP 全不通），待可达时执行**。

缺失的唯一索引清单（33 个，按本地 DDL）：

| 索引 | 表 | 类型 | 来源 |
|---|---|---|---|
| UK_APPSI_REQUEST_SIGN_SEQ | APP_PAY_SIGN_INFO | UNIQUE（幂等键） | pay-sign-server/src/main/resources/sql/pay-sign-schema.sql |
| UK_APTD_ORDER | ALIPAY_PAY_TXN_DETAIL | UNIQUE（幂等键） | alipay-pay-sign-server/src/main/resources/sql/alipay-pay-txn-detail-rebuild-migration.sql |
| UK_ARL_REFUND_ORDER_NO | ALIPAY_REFUND_LOG | UNIQUE（幂等键） | alipay-pay-sign-server/src/main/resources/sql/alipay-pay-sign-callback-log-migration.sql |
| UK_ARTD_REFUND_ORDER | ALIPAY_REFUND_TXN_DETAIL | UNIQUE（幂等键） | alipay-pay-sign-server/src/main/resources/sql/alipay-pay-txn-schema.sql |
| UK_ATR_REQUEST_SIGN_SEQ | APP_TERMINATION_REQUEST | UNIQUE（幂等键） | pay-sign-server/src/main/resources/sql/pay-sign-schema.sql |
| UK_DAILY_TICKET_INSTANCE_ORDER | DAILY_TICKET_INSTANCE | UNIQUE（幂等键） | daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql |
| UK_DAILY_TICKET_PAY_NOTIFY_ORDER | DAILY_TICKET_PAY_NOTIFY | UNIQUE（幂等键） | daily-ticket-server/src/main/resources/sql/daily-ticket-pay-notify-migration.sql |
| UK_DAILY_TICKET_REFUND_DETAIL | DAILY_TICKET_REFUND_DETAIL | UNIQUE（幂等键） | daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql |
| UK_DAILY_TICKET_REFUND_ORDER | DAILY_TICKET_REFUND | UNIQUE（幂等键） | daily-ticket-server/src/main/resources/sql/daily-ticket-refund-page-migration.sql |
| UK_DTUL_ORDER | DAILY_TICKET_USAGE_LOG | UNIQUE（幂等键） | daily-ticket-server/src/main/resources/sql/daily-ticket-usage-log-migration.sql |
| UK_F2F_NOTIFY_IDEM | F2F_NOTIFY_TASK | UNIQUE（幂等键） | face-pay-server/src/main/resources/sql/f2f-schema.sql |
| UK_F2F_ORDER_DEV_SEQ | F2F_ORDER | UNIQUE（幂等键） | face-pay-server/src/main/resources/sql/f2f-schema.sql |
| UK_F2F_ORDER_NO | F2F_ORDER | UNIQUE（幂等键） | face-pay-server/src/main/resources/sql/f2f-schema.sql |
| UK_F2F_PAY_ATTEMPT | F2F_PAYMENT | UNIQUE（幂等键） | face-pay-server/src/main/resources/sql/f2f-schema.sql |
| UK_F2F_PAY_SUCCESS | F2F_PAYMENT | UNIQUE（幂等键） | face-pay-server/src/main/resources/sql/f2f-schema.sql |
| UK_F2F_REFUND_IDEM | F2F_REFUND | UNIQUE（幂等键） | face-pay-server/src/main/resources/sql/f2f-schema.sql |
| UK_F2F_REFUND_NO | F2F_REFUND | UNIQUE（幂等键） | face-pay-server/src/main/resources/sql/f2f-schema.sql |
| UK_F2F_REPORT_IDEM | F2F_RESULT_REPORT | UNIQUE（幂等键） | face-pay-server/src/main/resources/sql/f2f-schema.sql |
| UK_F2F_TICKET_LOGIC | F2F_TICKET | UNIQUE（幂等键） | face-pay-server/src/main/resources/sql/f2f-schema.sql |
| UK_GATE_RETRY_QUEUE_ORDER | GATE_RETRY_QUEUE | UNIQUE（幂等键） | gate-txn-pay-server/src/main/resources/sql/gate-txn-pay-retry-queue-migration.sql |
| UK_GATE_TXN_PAY_ORDER_NO | GATE_TXN_PAY | UNIQUE（幂等键） | gate-txn-pay-server/src/main/resources/sql/gate-txn-pay-schema.sql |
| UK_PAY_REFUND_DETAIL_NO | PAY_REFUND_DETAIL | UNIQUE（幂等键） | pay-sign-server/src/main/resources/sql/pay-txn-schema.sql |
| UK_PAY_TXN_DETAIL_ORDER | PAY_TXN_DETAIL | UNIQUE（幂等键） | pay-sign-server/src/main/resources/sql/pay-txn-schema.sql |
| UK_QRCODE_TXN_DETAIL_BIZ | QRCODE_TXN_DETAIL | UNIQUE（幂等键） | ticket-server/src/main/resources/sql/qrcode-txn-detail-schema.sql |
| UK_QR_CODE_STATUS_CARD | QRCODE_STATUS | UNIQUE（幂等键） | ticket-server/src/main/resources/sql/ticket-server-schema.sql |
| UK_QSR_CARD_SEQ_ADVICE | QRCODE_SUPPLEMENT_REQUEST | UNIQUE（幂等键） | ticket-server/src/main/resources/sql/ticket-server-supplement-request-migration.sql |
| UK_SUPPLEMENT_ITEM_ACTIVE | SUPPLEMENT_ORDER_ITEM | UNIQUE（幂等键） | gate-txn-pay-server/src/main/resources/sql/supplement-order-active-orig-migration.sql |
| UK_SUPPLEMENT_ITEM_ORDER | SUPPLEMENT_ORDER_ITEM | UNIQUE（幂等键） | gate-txn-pay-server/src/main/resources/sql/supplement-order-schema.sql |
| UK_SUPPLEMENT_ORDER_NO | SUPPLEMENT_ORDER | UNIQUE（幂等键） | gate-txn-pay-server/src/main/resources/sql/supplement-order-schema.sql |
| UK_UAEC_CARD_NO | USER_ACC_EMPLOYEE_CARD | UNIQUE（幂等键） | account-server/src/main/resources/sql/account-server-schema.sql |
| UK_UAT_CARD_ID | USER_ACC_TICKETNO | UNIQUE（幂等键） | account-server/src/main/resources/sql/account-server-schema.sql |
| UK_UIRI_ACTIVE_USER_CARDTYPE | USER_ITP_REG_INFO | UNIQUE（幂等键） | account-server/src/main/resources/sql/account-server-active-user-cardtype-index-migration.sql |
| UK_UIRI_LIMIT_PENDING | USER_ITP_REG_INFO | UNIQUE（幂等键） | account-server/src/main/resources/sql/account-server-ticket-limit-migration.sql |

> 另有 136 个非唯一索引（IDX_*）同样在补充文件里，按域分组列出。
> **UNKNOWN 桶**：少量本地索引所属表不在四域映射内——`TBL_DEVICE_INFO`（para-schema.sql 定义但**老库导出中无此表**，疑幻影或已删，MUST 现查）、`STATION_INFO`（不纳入域，随同义词方案）、`BAK_*` 快照表。这些索引未加 schema 前缀，归域裁决后处理。


---

## 8. 红岛四域执行记录（2026-10-23 12:50 全部完成）

**目标**：红岛生产 `172.120.110.25:1521/hditpdb`（19c，字符集 **ZHS16GBK**），四域用户各自默认表空间 `ITP_<域>_TS`。
**执行器**：`docs/ops/environments/scripts/migration/20261023_apply_domain_to_db.py --domain <域> --execute`（可重跑；日志 `deploy/logs/apply_<域>_*.log`）。
**执行顺序**：PLAT → USER → RIDE → TXN（PLAT 是被引用方）。

| 域 | 表 | 表/列注释 | 索引 | 数据行数 | 序列 | 表空间 |
|---|---|---|---|---|---|---|
| PLAT | 102 | 63 / 554 | 129（28 补充 + 1 合理跳过） | 303,453 | 4 | 84 MB |
| USER | 20 | — / — | 59（26 补充） | 1,105,249 | 5 | 962 MB |
| RIDE | 3 | — / — | 9（7 补充） | 1,001,281 | 1 | 1,364 MB |
| TXN | 39 | 383 条 | 165（102 补充 + 1 跳过） | 1,002,016 | 16 | 1,183 MB |
| **合计** | **164** | — | **362** | **3,412,000** | **26** | **3,593 MB** |

- **逐表行数全部与源文件一致**（PLAT 55 表 / USER 12 表 / RIDE 3 表 / TXN 33 表，零差异）；四域主键/外键全 `ENABLED + VALIDATED`；统计信息已收集。
- 中文零乱码（GBK 库 × UTF-8 文件，oracledb 自动转码；`sys_job.remark` 实测正确）。

### 三个坑（必读）
1. **语句 NEVER 带结尾分号**：`python-oracledb` 走 OCI，分号是 sqlplus 语法，带上会被当 SQL 内容 → 全部 `CREATE TABLE` 报 **ORA-00922**（首轮 102/102 全失败的真因）。
2. **数据文件是 MySQL 风格多行 VALUES**：`VALUES (..),(..)` Oracle 不支持 → **ORA-00933**。执行器自动转 `INSERT ALL INTO … INTO … SELECT 1 FROM DUAL`（每条最多 10 个元组，远低于 127 上限）。
3. **`GENERATED ALWAYS AS IDENTITY` 列带显式值** → ORA-32795。PLAT 的 `GATEWAY_ROUTE_CONFIG_AUDIT.AUDIT_ID` 属此类：建表临时降 `BY DEFAULT`，灌完 `ALTER … GENERATED ALWAYS AS IDENTITY (START WITH LIMIT VALUE)` 已恢复。

### 另外四个坑（本轮新踩）
4. **虚拟列不能 INSERT（ORA-54013）**：`USER_ITP_REG_INFO.THIRD_USER_ID_SUFFIX`、`RIDE.QRCODE_STATUS.CARD_ID_TAIL` 是 `GENERATED ALWAYS AS (SUBSTR(…)) VIRTUAL`。导数据 MUST 从列清单和每个元组里同步剔掉该列 —— 执行器已按 `user_tab_cols.virtual_column='YES'` 自动处理（首轮 USER 103 条、RIDE 205 条失败即此因，修后 0 失败）。
5. **`WHERE` 子句的部分索引 Oracle 不支持**：`IDX_GATE_RETRY_QUEUE_CONSUME` 是 PostgreSQL 语法 `(STATUS, CREATE_TIME) WHERE STATUS IN (…)`，报 ORA-02158，**跳过属正确**（Oracle 无部分索引，需要时改函数式索引）。
6. **identity 列必须校准**：四域共 34 个（含 RuoYi `sys_*` 的 `GENERATED BY DEFAULT ON NULL`），灌数后一律 `START WITH LIMIT VALUE`，否则后台首次新增即主键冲突。
7. **序列导出缺口**：`scheme.sql` 只导表**不导序列**。四域共 26 个业务序列全按 `docs/ops/environments/scripts/migration/20261017_schema_<域>.sql` 的权威 DDL 补建，并校准到 `MAX(主键)+1`：
   - PLAT 4（`ACC_SIM_HISTORY_SEQ` / `SEQ_METRO_AGM_KEY_POOL` / `SEQ_METRO_AGM_KEY_VERSION` / `SEQ_METRO_MEMBER_STATIC_KEY`）
   - USER 5（含 `LOGIC_CARD_POOL_BATCH_SEQ` → `LOGIC_CARD_POOL_BATCH.BATCH_NO`，`MINVALUE 100000 MAXVALUE 999999`）
   - RIDE 1（`SEQ_QRCODE_TXN_DETAIL` → 1,018,055）
   - TXN 16（含 `F2F_ORDER_NO_SEQ`：4 位循环计数器 `MAXVALUE 9999 CYCLE`，**不校准**）
   - 10-17 脚本缺 alipay-account 那 8 张表，故 `SEQ_ALIPAY_SIGN_LOG`（USER）、`SEQ_METRO_TRANSFER_PUSH_TASK`（TXN）由代码 grep 反推补入。

### 分区现状（与旧 P0 记录不同，需重新评估归档方案）
- RANGE 表（`GATE_TXN_PAY` / `PAY_TXN_DETAIL` / `QRCODE_TXN_DETAIL` / `PAY_REFUND_DETAIL` / `PAY_CALLBACK_LOG` / `ALIPAY_*` 等）均为 **P202606~P202612 + `P_MAX`(MAXVALUE) 共 8 个分区**。
- `F2F_ORDER` / `F2F_PAYMENT` / `F2F_RESULT_REPORT` 是 **INTERVAL(NUMTOYMINTERVAL(1,'MONTH')) 自动分区**，无上限隐患。
- `USER_ITP_REG_INFO` / `QRCODE_STATUS` 是 **LIST 101 分区**。
- ⚠️ 前一类表 2027-01-01 起数据全落 `P_MAX`，不报错但**按月归档失效**，仍须在 2026-12-15 前处理（补月度分区或改 INTERVAL）。
- 分区索引状态全 `USABLE`；`user_indexes.STATUS='N/A'` 是分区索引/LOB 索引的正常显示，**不是故障**。

### 遗留事项
- 回收站残留（PLAT 174 / USER 133 / TXN 271 / RIDE 114 个 `BIN$` 对象），均为用户清空旧表未 `PURGE` 所留的空表，`PURGE RECYCLEBIN;` 可清。
- **跨域同义词与只读授权尚未建立**：`docs/ops/environments/scripts/migration/20261014_create_split_grants_synonyms.sql` 仍是草稿（其文件头写明「严禁直接在生产库执行」）。服务真正切到分域连接前 MUST 先落实同义词/授权，否则跨域引用表（如各模块读 PLAT 的 `TBL_STATION_INFO`）会直接 `ORA-00942`。
- 索引侧「与老库 `all_indexes` 核对」仍待做：`docs/ops/environments/scripts/migration/20261023_verify_indexes_against_db.py`（老库可达时执行）。
