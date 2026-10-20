# 分域 Schema 清单与建表脚本（v1.0 · 2026-10-17）

> 由 `scripts/migration/20261017_schema_gen.py` 生成；域清单权威源 = `20261014_bundle_m2_m3_migration.sql` 的 IN 列表，与 4 个 export 脚本 `tables=` 交叉校验。
> DDL 来源 = 仓库本地 SQL（21 个 `*-schema.sql` + migration + RuoYi + quartz + scripts 种子脚本），**NEVER 从老库 GET_DDL**（防老库漂移）；老库仅用于数据导出与结构 diff。
> 目标 = 红岛生产 `172.120.110.25:1521/hditpdb`（PDB）。执行 MUST 用各域用户（无 TABLESPACE 子句 → 落 M1 绑定的默认表空间）。

## 执行顺序与方式

```bash
# 顺序 MUST：PLAT → USER → RIDE → TXN（跨域外键/同义词依赖；PLAT 是被引用方）
sqlplus QDITP_PLAT/<pwd>@//172.120.110.25:1521/hditpdb @scripts/migration/20261017_schema_plat.sql
sqlplus QDITP_USER/<pwd>@//172.120.110.25:1521/hditpdb @scripts/migration/20261017_schema_user.sql
sqlplus QDITP_RIDE/<pwd>@//172.120.110.25:1521/hditpdb @scripts/migration/20261017_schema_ride.sql
sqlplus QDITP_TXN/<pwd>@//172.120.110.25:1521/hditpdb @scripts/migration/20261017_schema_txn.sql
```
- MUST 以域用户执行：DDL 无 `TABLESPACE` 子句，表落**执行用户的默认表空间**（M1 已绑定 `ITP_<域>_TS`）；**NEVER 用 SYS 执行**（落到 SYSTEM 且 owner 错）
- 幂等：重复执行报 `ORA-00955`（名称已存在）可忽略；单表失败不阻断（sqlplus 默认继续），但 MUST 回查输出
- 建表后：灌数（跨库脚本 Phase 5）→ 序列校准（Phase 6.1）→ 重跑 M1 Step 5/6/7 授权 + 菜单种子（Phase 6.7）

## 总览

| 域 | 目标 schema | 默认表空间 | 清单表数 | 本地有 DDL | 缺 DDL（需补） |
|---|---|---|---|---|---|
| PLAT | QDITP_PLAT | ITP_PLAT_TS | 71 | 71 | 0 |
| USER | QDITP_USER | ITP_USER_TS | 13 | 13 | 0 |
| RIDE | QDITP_RIDE | ITP_RIDE_TS | 3 | 3 | 0 |
| TXN | QDITP_TXN | ITP_TXN_TS | 36 | 36 | 0 |

## 逐域明细

### PLAT 域（71/71）

| # | 表 | DDL 来源 | 类型 |
|---|---|---|---|
| 1 | ACC_SIMULATION_HISTORY | employee-card-acc-simulator/src/main/resources/sql/acc-simulator-schema.sql | base |
| 2 | ACC_SIM_EMPLOYEE_CARD | employee-card-acc-simulator/src/main/resources/sql/acc-simulator-schema.sql | base |
| 3 | BLACKLIST | blacklist-server/src/main/resources/sql/blacklist-schema.sql | base |
| 4 | BLACKLIST_OPERATE_LOG | blacklist-server/src/main/resources/sql/blacklist-schema.sql | base |
| 5 | BLACKLIST_RELEASED | blacklist-server/src/main/resources/sql/blacklist-schema.sql | base |
| 6 | COM_DEVICE_SYN_KEY | scripts/20260807_agm_key_sync_schema.sql | script |
| 7 | GEN_TABLE | web-server/sql/ry_20250522_oracle.sql | ruoyi |
| 8 | GEN_TABLE_COLUMN | web-server/sql/ry_20250522_oracle.sql | ruoyi |
| 9 | METRO_AGM_KEY_POOL | scripts/20260807_agm_key_sync_schema.sql | script |
| 10 | METRO_AGM_KEY_VERSION | scripts/20260807_agm_key_sync_schema.sql | script |
| 11 | METRO_CA_KEYSTORE | key-server/src/main/resources/sql/key-server-schema.sql | base |
| 12 | METRO_MEMBER_STATIC_KEY | key-server/src/main/resources/sql/key-server-schema.sql | base |
| 13 | QRTZ_BLOB_TRIGGERS | web-server/sql/quartz_oracle.sql | quartz |
| 14 | QRTZ_CALENDARS | web-server/sql/quartz_oracle.sql | quartz |
| 15 | QRTZ_CRON_TRIGGERS | web-server/sql/quartz_oracle.sql | quartz |
| 16 | QRTZ_FIRED_TRIGGERS | web-server/sql/quartz_oracle.sql | quartz |
| 17 | QRTZ_JOB_DETAILS | web-server/sql/quartz_oracle.sql | quartz |
| 18 | QRTZ_LOCKS | web-server/sql/quartz_oracle.sql | quartz |
| 19 | QRTZ_PAUSED_TRIGGER_GRPS | web-server/sql/quartz_oracle.sql | quartz |
| 20 | QRTZ_SCHEDULER_STATE | web-server/sql/quartz_oracle.sql | quartz |
| 21 | QRTZ_SIMPLE_TRIGGERS | web-server/sql/quartz_oracle.sql | quartz |
| 22 | QRTZ_TRIGGERS | web-server/sql/quartz_oracle.sql | quartz |
| 23 | SYS_CONFIG | web-server/sql/ry_20250522_oracle.sql | ruoyi |
| 24 | SYS_DEPT | web-server/sql/ry_20250522_oracle.sql | ruoyi |
| 25 | SYS_DICT_DATA | web-server/sql/ry_20250522_oracle.sql | ruoyi |
| 26 | SYS_DICT_TYPE | web-server/sql/ry_20250522_oracle.sql | ruoyi |
| 27 | SYS_JOB | web-server/sql/ry_20250522_oracle.sql | ruoyi |
| 28 | SYS_LOGININFOR | web-server/sql/ry_20250522_oracle.sql | ruoyi |
| 29 | SYS_MENU | web-server/sql/ry_20250522_oracle.sql | ruoyi |
| 30 | SYS_NOTICE | web-server/sql/ry_20250522_oracle.sql | ruoyi |
| 31 | SYS_OPER_LOG | web-server/sql/ry_20250522_oracle.sql | ruoyi |
| 32 | SYS_POST | web-server/sql/ry_20250522_oracle.sql | ruoyi |
| 33 | SYS_ROLE | web-server/sql/ry_20250522_oracle.sql | ruoyi |
| 34 | SYS_USER | web-server/sql/ry_20250522_oracle.sql | ruoyi |
| 35 | TBL_ADD_PARA | para-server/src/main/resources/sql/para-schema.sql | base |
| 36 | TBL_BASE_FARE | para-server/src/main/resources/sql/para-schema.sql | base |
| 37 | TBL_CHIP_TYPE | para-server/src/main/resources/sql/para-schema.sql | base |
| 38 | TBL_FARE_GROUP | para-server/src/main/resources/sql/para-schema.sql | base |
| 39 | TBL_FARE_MATRIX | para-server/src/main/resources/sql/para-schema.sql | base |
| 40 | TBL_FARE_TIME | para-server/src/main/resources/sql/para-schema.sql | base |
| 41 | TBL_LINE_INFO | para-server/src/main/resources/sql/para-schema.sql | base |
| 42 | TBL_NOTICE_APP_FAILURE_RECORD | scripts/create_tbl_notice_app_failure_record.sql | script |
| 43 | TBL_ORDER_REFUND_CYCLE | para-server/src/main/resources/sql/para-schema.sql | base |
| 44 | TBL_PARA_VERSION | para-server/src/main/resources/sql/para-schema.sql | base |
| 45 | TBL_RISK_CONTROL_LOG | para-server/src/main/resources/sql/para-schema.sql | base |
| 46 | TBL_RISK_GROUP | para-server/src/main/resources/sql/para-schema.sql | base |
| 47 | TBL_RISK_RULE | para-server/src/main/resources/sql/para-schema.sql | base |
| 48 | TBL_SECT_INFO | para-server/src/main/resources/sql/para-schema.sql | base |
| 49 | TBL_SINGLE_TICKET_PURCHASE_LIMIT | para-server/src/main/resources/sql/para-schema.sql | base |
| 50 | TBL_SPECIAL_DATE | para-server/src/main/resources/sql/para-schema.sql | base |
| 51 | TBL_STATION_INFO | para-server/src/main/resources/sql/para-schema.sql | base |
| 52 | TBL_STL_ACCT_INFO | acc-es-server/src/main/resources/sql/acc-es-server-schema.sql | base |
| 53 | TBL_STL_PERSON_INFO | acc-es-server/src/main/resources/sql/acc-es-server-schema.sql | base |
| 54 | TBL_STL_TICKET_INFO | acc-es-server/src/main/resources/sql/acc-es-server-schema.sql | base |
| 55 | TBL_STL_TICKET_SET | acc-es-server/src/main/resources/sql/acc-es-server-schema.sql | base |
| 56 | TBL_TICKET_FARE | para-server/src/main/resources/sql/para-schema.sql | base |
| 57 | TBL_TICKET_TYPE | para-server/src/main/resources/sql/para-schema.sql | base |
| 58 | TBL_TIME_INTERVAL | para-server/src/main/resources/sql/para-schema.sql | base |
| 59 | TBL_TKT_ES_ACCOUNT | acc-es-server/src/main/resources/sql/acc-es-server-schema.sql | base |
| 60 | TBL_TKT_ES_ASSIGN | acc-es-server/src/main/resources/sql/acc-es-server-schema.sql | base |
| 61 | TBL_TKT_ES_FILE_PROC_LOG | acc-es-server/src/main/resources/sql/acc-es-server-schema.sql | base |
| 62 | TBL_TKT_ES_INFO | acc-es-server/src/main/resources/sql/acc-es-server-schema.sql | base |
| 63 | TBL_TKT_ES_PROC | acc-es-server/src/main/resources/sql/acc-es-server-schema.sql | base |
| 64 | TBL_TKT_ES_REPORT | acc-es-server/src/main/resources/sql/acc-es-server-schema.sql | base |
| 65 | TBL_TKT_ES_TASK | acc-es-server/src/main/resources/sql/acc-es-server-schema.sql | base |
| 66 | TBL_TKT_PRE_PERSON | acc-es-server/src/main/resources/sql/acc-es-server-schema.sql | base |
| 67 | TBL_TKT_TASK_PLAN | acc-es-server/src/main/resources/sql/acc-es-server-schema.sql | base |
| 68 | TBL_TOTAL_SALE_PART | para-server/src/main/resources/sql/para-schema.sql | base |
| 69 | TBL_TSF_INFO | para-server/src/main/resources/sql/para-schema.sql | base |
| 70 | TBL_ZONE_DTL | para-server/src/main/resources/sql/para-schema.sql | base |
| 71 | TBL_ZONE_INFO | para-server/src/main/resources/sql/para-schema.sql | base |

### USER 域（13/13）

| # | 表 | DDL 来源 | 类型 |
|---|---|---|---|
| 1 | ACCOUNT_EXCEPTION_TICKET | account-server/src/main/resources/sql/account-server-schema.sql | base |
| 2 | ALIPAY_PHONE_CHANGE_LOG | alipay-account-server/src/main/resources/sql/alipay-account-server-schema.sql | base |
| 3 | APP_USER_PAY_CHANNEL | account-server/src/main/resources/sql/account-server-schema.sql | base |
| 4 | LOGIC_CARD_POOL_BATCH | card-pool-server/src/main/resources/sql/card-pool-schema.sql | base |
| 5 | LOGIC_CARD_POOL_CARD | card-pool-server/src/main/resources/sql/card-pool-schema.sql | base |
| 6 | LOGIC_CARD_POOL_TYPE_LOCK | card-pool-server/src/main/resources/sql/card-pool-schema.sql | base |
| 7 | USER_ACC_EMPLOYEE_CARD | account-server/src/main/resources/sql/account-server-schema.sql | base |
| 8 | USER_ACC_EMPLOYEE_CARD_LOG | account-server/src/main/resources/sql/account-server-schema.sql | base |
| 9 | USER_ACC_TICKETNO | account-server/src/main/resources/sql/account-server-schema.sql | base |
| 10 | USER_ITP_REG_INFO | account-server/src/main/resources/sql/account-server-schema.sql | base |
| 11 | USER_ITP_REG_INFO_BAK20260909 | scripts/20260909_issue_org_code_migration.sql | script |
| 12 | USER_ITP_REG_LOG | account-server/src/main/resources/sql/account-server-schema.sql | base |
| 13 | USER_PHONE_CHANGE_LOG | account-server/src/main/resources/sql/account-server-schema.sql | base |

### RIDE 域（3/3）

| # | 表 | DDL 来源 | 类型 |
|---|---|---|---|
| 1 | QRCODE_STATUS | ticket-server/src/main/resources/sql/ticket-server-schema.sql | base |
| 2 | QRCODE_SUPPLEMENT_REQUEST | ticket-server/src/main/resources/sql/ticket-server-supplement-request-migration.sql | migration ⚠️仅migration |
| 3 | QRCODE_TXN_DETAIL | ticket-server/src/main/resources/sql/qrcode-txn-detail-schema.sql | base |

### TXN 域（36/36）

| # | 表 | DDL 来源 | 类型 |
|---|---|---|---|
| 1 | ALIPAY_PAY_CALLBACK_LOG | alipay-pay-sign-server/src/main/resources/sql/alipay-pay-sign-callback-log-migration.sql | migration ⚠️仅migration |
| 2 | ALIPAY_PAY_CENTER_MSG_LOG | alipay-pay-sign-server/src/main/resources/sql/alipay-pay-txn-schema.sql | base |
| 3 | ALIPAY_PAY_TXN_DETAIL | alipay-pay-sign-server/src/main/resources/sql/alipay-pay-txn-schema.sql | base |
| 4 | ALIPAY_REFUND_TXN_DETAIL | alipay-pay-sign-server/src/main/resources/sql/alipay-pay-txn-schema.sql | base |
| 5 | APP_PAY_SIGN_INFO | pay-sign-server/src/main/resources/sql/pay-sign-schema.sql | base |
| 6 | APP_PAY_SIGN_LOG | pay-sign-server/src/main/resources/sql/pay-sign-schema.sql | base |
| 7 | APP_PAY_SIGN_REQUEST | pay-sign-server/src/main/resources/sql/pay-sign-schema.sql | base |
| 8 | APP_TERMINATION_REQUEST | pay-sign-server/src/main/resources/sql/pay-sign-schema.sql | base |
| 9 | DAILY_TICKET_INSTANCE | daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql | base |
| 10 | DAILY_TICKET_ORDER | daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql | base |
| 11 | DAILY_TICKET_PAY_LOG | daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql | base |
| 12 | DAILY_TICKET_PAY_NOTIFY | daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql | base |
| 13 | DAILY_TICKET_REFUND | daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql | base |
| 14 | DAILY_TICKET_REFUND_DETAIL | daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql | base |
| 15 | DAILY_TICKET_USAGE_LOG | daily-ticket-server/src/main/resources/sql/daily-ticket-usage-log-migration.sql | migration ⚠️仅migration |
| 16 | F2F_DEVICE_STATUS | face-pay-server/src/main/resources/sql/f2f-schema.sql | base |
| 17 | F2F_NOTIFY_TASK | face-pay-server/src/main/resources/sql/f2f-schema.sql | base |
| 18 | F2F_ORDER | face-pay-server/src/main/resources/sql/f2f-schema.sql | base |
| 19 | F2F_PAYMENT | face-pay-server/src/main/resources/sql/f2f-schema.sql | base |
| 20 | F2F_REFUND | face-pay-server/src/main/resources/sql/f2f-schema.sql | base |
| 21 | F2F_RESULT_REPORT | face-pay-server/src/main/resources/sql/f2f-schema.sql | base |
| 22 | F2F_TICKET | face-pay-server/src/main/resources/sql/f2f-schema.sql | base |
| 23 | GATE_RETRY_QUEUE | gate-txn-pay-server/src/main/resources/sql/gate-txn-pay-retry-queue-migration.sql | migration ⚠️仅migration |
| 24 | GATE_TXN_PAY | gate-txn-pay-server/src/main/resources/sql/gate-txn-pay-schema.sql | base |
| 25 | PAY_CALLBACK_LOG | pay-sign-server/src/main/resources/sql/pay-txn-schema.sql | base |
| 26 | PAY_REFUND_DETAIL | pay-sign-server/src/main/resources/sql/pay-txn-schema.sql | base |
| 27 | PAY_TXN_DETAIL | pay-sign-server/src/main/resources/sql/pay-txn-schema.sql | base |
| 28 | RECON_BATCH | recon-server/src/main/resources/sql/recon-server-schema.sql | base |
| 29 | RECON_BATCH_FILE | recon-server/src/main/resources/sql/recon-server-schema.sql | base |
| 30 | RECON_BATCH_PART | recon-server/src/main/resources/sql/recon-server-schema.sql | base |
| 31 | RECON_BATCH_SOURCE | recon-server/src/main/resources/sql/recon-server-schema.sql | base |
| 32 | RECON_THIRD_BILL | recon-server/src/main/resources/sql/recon-thirdparty-schema.sql | base |
| 33 | RECON_TXN_DIFF | recon-server/src/main/resources/sql/recon-thirdparty-schema.sql | base |
| 34 | SUPPLEMENT_ORDER | gate-txn-pay-server/src/main/resources/sql/supplement-order-schema.sql | base |
| 35 | SUPPLEMENT_ORDER_ITEM | gate-txn-pay-server/src/main/resources/sql/supplement-order-schema.sql | base |
| 36 | TRAVEL_TICKET_ORDER | daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql | base |

## 缺口与未分域（MUST 人工裁决）

### 本地有 DDL、不在任何域清单（未分域，待裁决）

| 表 | 来源 | 建议归属（待确认） |
|---|---|---|
| AGM_DEVICE_STATUS | fep-dev-server/src/main/resources/sql/agm-device-status.sql | PLAT（设备基础设施，同 COM_DEVICE_SYN_KEY） |
| GATEWAY_ROUTE_CONFIG | account-server/src/main/resources/sql/gateway-route-config-schema.sql | PLAT（网关路由配置，基础设施） |
| GATEWAY_ROUTE_CONFIG_AUDIT | account-server/src/main/resources/sql/gateway-route-config-schema.sql | PLAT（网关路由审计，随主表） |
| QRTZ_SIMPROP_TRIGGERS | web-server/sql/quartz.sql | PLAT（Quartz 配套） |
| STATION_INFO | ticket-server/src/main/resources/sql/station-info-schema.sql | **不迁**（已退场，RIDE 域用同义词指向 PLAT.TBL_STATION_INFO） |

> **2026-10-19 更新**：原列于此的 5 张 RuoYi 关联表（`SYS_JOB_LOG` / `SYS_ROLE_DEPT` / `SYS_ROLE_MENU` / `SYS_USER_POST` / `SYS_USER_ROLE`）
> **已补入 bundle 的 `QDITP_PLAT` IN 清单与 export `tables=` 列表**（来源 `web-server/sql/ry_20250522_oracle.sql`；
> 建表+种子见 `20261018_sys_seed.sql` / `20261018_sys_seed_data.sql`），不再属于"未分域"。
> PLAT 域表数由 71 → **76**（schema 拆分仍为 `20261017_schema_plat.sql` 71 张 + `20261018_sys_seed.sql` 5 张）。
> 若重跑 `20261017_schema_gen.py`，因 bundle IN 清单已含这 5 张，`20261017_schema_plat.sql` 将直接生成 76 张；
> 此时 `20261018_sys_seed.sql` 的建表部分与之重复（`ORA-00955` 可忽略），但**种子部分仍须单独执行**。

## 域清单交叉校验（bundle IN 列表 vs export tables=）

- **QDITP_PLAT**：仅 bundle 有 ['ACC_SIMULATION_HISTORY', 'ACC_SIM_EMPLOYEE_CARD', 'BLACKLIST', 'BLACKLIST_OPERATE_LOG', 'BLACKLIST_RELEASED', 'COM_DEVICE_SYN_KEY', 'GEN_TABLE', 'GEN_TABLE_COLUMN', 'METRO_AGM_KEY_POOL', 'METRO_AGM_KEY_VERSION', 'METRO_CA_KEYSTORE', 'METRO_MEMBER_STATIC_KEY', 'QRTZ_BLOB_TRIGGERS', 'QRTZ_CALENDARS', 'QRTZ_CRON_TRIGGERS', 'QRTZ_FIRED_TRIGGERS', 'QRTZ_JOB_DETAILS', 'QRTZ_LOCKS', 'QRTZ_PAUSED_TRIGGER_GRPS', 'QRTZ_SCHEDULER_STATE', 'QRTZ_SIMPLE_TRIGGERS', 'QRTZ_TRIGGERS', 'SYS_CONFIG', 'SYS_DEPT', 'SYS_DICT_DATA', 'SYS_DICT_TYPE', 'SYS_JOB', 'SYS_JOB_LOG', 'SYS_LOGININFOR', 'SYS_MENU', 'SYS_NOTICE', 'SYS_OPER_LOG', 'SYS_POST', 'SYS_ROLE', 'SYS_ROLE_DEPT', 'SYS_ROLE_MENU', 'SYS_USER', 'SYS_USER_POST', 'SYS_USER_ROLE', 'TBL_ADD_PARA', 'TBL_BASE_FARE', 'TBL_CHIP_TYPE', 'TBL_FARE_GROUP', 'TBL_FARE_MATRIX', 'TBL_FARE_TIME', 'TBL_LINE_INFO', 'TBL_NOTICE_APP_FAILURE_RECORD', 'TBL_ORDER_REFUND_CYCLE', 'TBL_PARA_VERSION', 'TBL_RISK_CONTROL_LOG', 'TBL_RISK_GROUP', 'TBL_RISK_RULE', 'TBL_SECT_INFO', 'TBL_SINGLE_TICKET_PURCHASE_LIMIT', 'TBL_SPECIAL_DATE', 'TBL_STATION_INFO', 'TBL_STL_ACCT_INFO', 'TBL_STL_PERSON_INFO', 'TBL_STL_TICKET_INFO', 'TBL_STL_TICKET_SET', 'TBL_TICKET_FARE', 'TBL_TICKET_TYPE', 'TBL_TIME_INTERVAL', 'TBL_TKT_ES_ACCOUNT', 'TBL_TKT_ES_ASSIGN', 'TBL_TKT_ES_FILE_PROC_LOG', 'TBL_TKT_ES_INFO', 'TBL_TKT_ES_PROC', 'TBL_TKT_ES_REPORT', 'TBL_TKT_ES_TASK', 'TBL_TKT_PRE_PERSON', 'TBL_TKT_TASK_PLAN', 'TBL_TOTAL_SALE_PART', 'TBL_TSF_INFO', 'TBL_ZONE_DTL', 'TBL_ZONE_INFO']；仅 export 有 []
- **QDITP_USER**：仅 bundle 有 ['ACCOUNT_EXCEPTION_TICKET', 'ALIPAY_PHONE_CHANGE_LOG', 'APP_USER_PAY_CHANNEL', 'LOGIC_CARD_POOL_BATCH', 'LOGIC_CARD_POOL_CARD', 'LOGIC_CARD_POOL_TYPE_LOCK', 'USER_ACC_EMPLOYEE_CARD', 'USER_ACC_EMPLOYEE_CARD_LOG', 'USER_ACC_TICKETNO', 'USER_ITP_REG_INFO', 'USER_ITP_REG_INFO_BAK20260909', 'USER_ITP_REG_LOG', 'USER_PHONE_CHANGE_LOG']；仅 export 有 []
- **QDITP_RIDE**：仅 bundle 有 ['QRCODE_STATUS', 'QRCODE_SUPPLEMENT_REQUEST', 'QRCODE_TXN_DETAIL']；仅 export 有 []
- **QDITP_TXN**：仅 bundle 有 ['ALIPAY_PAY_CALLBACK_LOG', 'ALIPAY_PAY_CENTER_MSG_LOG', 'ALIPAY_PAY_TXN_DETAIL', 'ALIPAY_REFUND_TXN_DETAIL', 'APP_PAY_SIGN_INFO', 'APP_PAY_SIGN_LOG', 'APP_PAY_SIGN_REQUEST', 'APP_TERMINATION_REQUEST', 'DAILY_TICKET_INSTANCE', 'DAILY_TICKET_ORDER', 'DAILY_TICKET_PAY_LOG', 'DAILY_TICKET_PAY_NOTIFY', 'DAILY_TICKET_REFUND', 'DAILY_TICKET_REFUND_DETAIL', 'DAILY_TICKET_USAGE_LOG', 'F2F_DEVICE_STATUS', 'F2F_NOTIFY_TASK', 'F2F_ORDER', 'F2F_PAYMENT', 'F2F_REFUND', 'F2F_RESULT_REPORT', 'F2F_TICKET', 'GATE_RETRY_QUEUE', 'GATE_TXN_PAY', 'PAY_CALLBACK_LOG', 'PAY_REFUND_DETAIL', 'PAY_TXN_DETAIL', 'RECON_BATCH', 'RECON_BATCH_FILE', 'RECON_BATCH_PART', 'RECON_BATCH_SOURCE', 'RECON_THIRD_BILL', 'RECON_TXN_DIFF', 'SUPPLEMENT_ORDER', 'SUPPLEMENT_ORDER_ITEM', 'TRAVEL_TICKET_ORDER']；仅 export 有 []

## 分区表特别注记（导入后 MUST 处理）

- `USER_ITP_REG_INFO`：LIST 分区（THIRD_USER_ID_SUFFIX，P00~P99），本地 DDL 完整，随建随用
- 8 张 RANGE 分区表（GATE_TXN_PAY / QRCODE_TXN_DETAIL / ALIPAY_PAY_TXN_DETAIL / ALIPAY_REFUND_TXN_DETAIL / ALIPAY_PAY_CALLBACK_LOG / ALIPAY_PAY_CENTER_MSG_LOG / F2F_ORDER / F2F_PAYMENT / F2F_RESULT_REPORT / PAY_TXN_DETAIL）：本地 DDL 的分区上界以文件为准，若仍到 `P202612`，灌数后 MUST 按 M0 补分区（跨库脚本 Phase 6.5）
- F2F_* 的 GLOBAL 唯一索引随块内联（保持现状；GLOBAL→LOCAL 是 M0 裁定项）
