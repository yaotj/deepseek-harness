-- ============================================================================
-- 独立索引补充候选清单（来源：仓库本地 DDL，NEVER 当作老库已核实的事实）
-- 背景：deploy/sche/sche.sql 老库导出只含「表 + 内联约束 + 约束背书索引」，
--       本地 DDL 中的独立索引几乎全部不在导出里——其中 34 个是唯一索引/幂等键
--       （F2F_*/PAY_TXN_DETAIL/GATE_TXN_PAY/QRCODE_TXN_DETAIL/DAILY_TICKET_*/SUPPLEMENT_* 等），
--       漏建 = 幂等防线丢失，金融级系统 MUST 补。
-- 执行前 MUST（缺一不可）：
--   1. 对老库 AFCITPDB 执行 all_indexes 现况核对（本机当前网络不可达库，待可达时补核），
--      确认每个索引在老库真实存在、列与唯一性与本地 DDL 一致（防本地漂移/幻影索引）
--   2. 在四域建表脚本之后、灌数之前执行（唯一索引先于数据，避免灌数后建索引失败）
--   3. 分区表索引已带 GLOBAL/LOCAL 限定（照抄本地 DDL）；F2F_* 的 GLOBAL 唯一索引
--      DROP/TRUNCATE PARTITION 会致 UNUSABLE，是 M0 裁定项
--   4. 以对应域用户执行；表名已按域加 schema 前缀
--   5. 核对方式（网络可达老库时 MUST 先跑）：
--      python3 scripts/migration/20261023_verify_indexes_against_db.py
--      连上 172.20.222.3:1521/AFCITPDB 后逐索引比对 all_indexes/all_ind_columns，
--      并把核对结论（存在列一致 / 列漂移 / 老库不存在）回写进本文件；
--      连不上时该脚本只报网络原因、NEVER 改文件
-- ============================================================================

-- ==================== QDITP_PLAT（29 个） ====================
-- IDX_ACC_SIM_HISTORY_CREATED [INDEX] ON ACC_SIMULATION_HISTORY  -- 来源 employee-card-acc-simulator/src/main/resources/sql/acc-simulator-schema.sql
CREATE INDEX "QDITP_PLAT"."IDX_ACC_SIM_HISTORY_CREATED" ON "QDITP_PLAT"."ACC_SIMULATION_HISTORY" (CREATED_AT DESC, ID DESC);

-- IDX_AGM_KEY_POOL_PROVIDER_BATCH [INDEX] ON METRO_AGM_KEY_POOL  -- 来源 20260807_agm_key_sync_schema.sql（脚本 2026-10-26 已删，DDL 见 migration/20261017_schema_plat.sql）
create index "QDITP_PLAT"."IDX_AGM_KEY_POOL_PROVIDER_BATCH" on "QDITP_PLAT"."METRO_AGM_KEY_POOL" (PROVIDER_ID, KEY_BATH_NUMBER, KEY_IDX);

-- IDX_AGM_KEY_VERSION_APPROVED [INDEX] ON METRO_AGM_KEY_VERSION  -- 来源 20260807_agm_key_sync_schema.sql（脚本 2026-10-26 已删，DDL 见 migration/20261017_schema_plat.sql）
create index "QDITP_PLAT"."IDX_AGM_KEY_VERSION_APPROVED" on "QDITP_PLAT"."METRO_AGM_KEY_VERSION" (PROVIDER_ID, PUBLIC_KEY_VERSION_STATUS, KEY_BATH_NUMBER);

-- IDX_BL_RELEASED_SYNC_SCAN [INDEX] ON BLACKLIST_RELEASED  -- 来源 blacklist-server/src/main/resources/sql/blacklist-released-channel-sync-migration.sql
CREATE INDEX "QDITP_PLAT"."IDX_BL_RELEASED_SYNC_SCAN" ON "QDITP_PLAT"."BLACKLIST_RELEASED" (CHANNEL_SYNC_STATUS, CHANNEL_SYNC_TIME);

-- IDX_FAILURE_RECORD_CREATE_TIME [INDEX] ON TBL_NOTICE_APP_FAILURE_RECORD  -- 来源 create_tbl_notice_app_failure_record.sql（脚本 2026-10-26 已删，DDL 见 migration/20261017_schema_plat.sql）
CREATE INDEX "QDITP_PLAT"."IDX_FAILURE_RECORD_CREATE_TIME" ON "QDITP_PLAT"."TBL_NOTICE_APP_FAILURE_RECORD" (CREATE_TIME) PCTFREE 10 INITRANS 2 MAXTRANS 255 STORAGE ( INITIAL 64K NEXT 1M MINEXTENTS 1 MAXEXTENTS UNLIMITED );

-- IDX_FAILURE_RECORD_ORDER_NO [INDEX] ON TBL_NOTICE_APP_FAILURE_RECORD  -- 来源 create_tbl_notice_app_failure_record.sql（脚本 2026-10-26 已删，DDL 见 migration/20261017_schema_plat.sql）
CREATE INDEX "QDITP_PLAT"."IDX_FAILURE_RECORD_ORDER_NO" ON "QDITP_PLAT"."TBL_NOTICE_APP_FAILURE_RECORD" (ORDER_NO) PCTFREE 10 INITRANS 2 MAXTRANS 255 STORAGE ( INITIAL 64K NEXT 1M MINEXTENTS 1 MAXEXTENTS UNLIMITED );

-- IDX_FAILURE_RECORD_STATUS [INDEX] ON TBL_NOTICE_APP_FAILURE_RECORD  -- 来源 create_tbl_notice_app_failure_record.sql（脚本 2026-10-26 已删，DDL 见 migration/20261017_schema_plat.sql）
CREATE INDEX "QDITP_PLAT"."IDX_FAILURE_RECORD_STATUS" ON "QDITP_PLAT"."TBL_NOTICE_APP_FAILURE_RECORD" (status) PCTFREE 10 INITRANS 2 MAXTRANS 255 STORAGE ( INITIAL 64K NEXT 1M MINEXTENTS 1 MAXEXTENTS UNLIMITED );

-- IDX_METRO_CA_KEYSTORE_KEYIDX [INDEX] ON METRO_CA_KEYSTORE  -- 来源 key-server/src/main/resources/sql/key-server-schema.sql
CREATE INDEX "QDITP_PLAT"."IDX_METRO_CA_KEYSTORE_KEYIDX" ON "QDITP_PLAT"."METRO_CA_KEYSTORE" (KEY_IDX);

-- IDX_METRO_CA_KEYSTORE_STATUS [INDEX] ON METRO_CA_KEYSTORE  -- 来源 key-server/src/main/resources/sql/key-server-schema.sql
CREATE INDEX "QDITP_PLAT"."IDX_METRO_CA_KEYSTORE_STATUS" ON "QDITP_PLAT"."METRO_CA_KEYSTORE" (KEY_STATUS);

-- IDX_RISK_LOG_CARD_ID [INDEX] ON TBL_RISK_CONTROL_LOG  -- 来源 para-server/src/main/resources/sql/para-schema.sql
CREATE INDEX "QDITP_PLAT"."IDX_RISK_LOG_CARD_ID" ON "QDITP_PLAT"."TBL_RISK_CONTROL_LOG" (CARD_ID);

-- IDX_RISK_LOG_ORDER_NO [INDEX] ON TBL_RISK_CONTROL_LOG  -- 来源 para-server/src/main/resources/sql/para-schema.sql
CREATE INDEX "QDITP_PLAT"."IDX_RISK_LOG_ORDER_NO" ON "QDITP_PLAT"."TBL_RISK_CONTROL_LOG" (USER_ORDER_NO);

-- IDX_RISK_LOG_RULE_TIME [INDEX] ON TBL_RISK_CONTROL_LOG  -- 来源 para-server/src/main/resources/sql/para-schema.sql
CREATE INDEX "QDITP_PLAT"."IDX_RISK_LOG_RULE_TIME" ON "QDITP_PLAT"."TBL_RISK_CONTROL_LOG" (RULE_ID, RISK_HIT_TIME);

-- IDX_RISK_RULE_GROUP_ID [INDEX] ON TBL_RISK_RULE  -- 来源 para-server/src/main/resources/sql/para-schema.sql
CREATE INDEX "QDITP_PLAT"."IDX_RISK_RULE_GROUP_ID" ON "QDITP_PLAT"."TBL_RISK_RULE" (GROUP_ID);

-- IDX_STL_ACCT_INFO_CSN [INDEX] ON TBL_STL_ACCT_INFO  -- 来源 acc-es-server/src/main/resources/sql/acc-es-server-schema.sql
CREATE INDEX "QDITP_PLAT"."IDX_STL_ACCT_INFO_CSN" ON "QDITP_PLAT"."TBL_STL_ACCT_INFO" (TICKET_CSN);

-- IDX_STL_TICKET_INFO_CSN [INDEX] ON TBL_STL_TICKET_INFO  -- 来源 acc-es-server/src/main/resources/sql/acc-es-server-schema.sql
CREATE INDEX "QDITP_PLAT"."IDX_STL_TICKET_INFO_CSN" ON "QDITP_PLAT"."TBL_STL_TICKET_INFO" (TICKET_CSN);

-- IDX_TKT_ES_TASK_PLAN_NO [INDEX] ON TBL_TKT_ES_TASK  -- 来源 acc-es-server/src/main/resources/sql/acc-es-server-schema.sql
CREATE INDEX "QDITP_PLAT"."IDX_TKT_ES_TASK_PLAN_NO" ON "QDITP_PLAT"."TBL_TKT_ES_TASK" (PLAN_NO);

-- idx_bl_released_biz_no [INDEX] ON BLACKLIST_RELEASED  -- 来源 blacklist-server/src/main/resources/sql/blacklist-refactor-migration.sql
CREATE INDEX "QDITP_PLAT"."idx_bl_released_biz_no" ON "QDITP_PLAT"."BLACKLIST_RELEASED" (biz_no);

-- idx_bl_released_card_id [INDEX] ON BLACKLIST_RELEASED  -- 来源 blacklist-server/src/main/resources/sql/blacklist-refactor-migration.sql
CREATE INDEX "QDITP_PLAT"."idx_bl_released_card_id" ON "QDITP_PLAT"."BLACKLIST_RELEASED" (card_id);

-- idx_bl_released_time [INDEX] ON BLACKLIST_RELEASED  -- 来源 blacklist-server/src/main/resources/sql/blacklist-refactor-migration.sql
CREATE INDEX "QDITP_PLAT"."idx_bl_released_time" ON "QDITP_PLAT"."BLACKLIST_RELEASED" (release_time);

-- idx_blacklist_biz_no [INDEX] ON BLACKLIST  -- 来源 blacklist-server/src/main/resources/sql/blacklist-refactor-migration.sql
CREATE INDEX "QDITP_PLAT"."idx_blacklist_biz_no" ON "QDITP_PLAT"."BLACKLIST" (biz_no);

-- idx_blacklist_log_card_id [INDEX] ON BLACKLIST_OPERATE_LOG  -- 来源 blacklist-server/src/main/resources/sql/blacklist-schema.sql
CREATE INDEX "QDITP_PLAT"."idx_blacklist_log_card_id" ON "QDITP_PLAT"."BLACKLIST_OPERATE_LOG" (card_id);

-- idx_blacklist_log_operate_time [INDEX] ON BLACKLIST_OPERATE_LOG  -- 来源 blacklist-server/src/main/resources/sql/blacklist-schema.sql
CREATE INDEX "QDITP_PLAT"."idx_blacklist_log_operate_time" ON "QDITP_PLAT"."BLACKLIST_OPERATE_LOG" (operate_time);

-- idx_blacklist_sync_scan [INDEX] ON BLACKLIST  -- 来源 blacklist-server/src/main/resources/sql/blacklist-refactor-migration.sql
CREATE INDEX "QDITP_PLAT"."idx_blacklist_sync_scan" ON "QDITP_PLAT"."BLACKLIST" (channel_sync_status, channel_sync_time);

-- idx_blacklist_third_user_id [INDEX] ON BLACKLIST  -- 来源 blacklist-server/src/main/resources/sql/blacklist-refactor-migration.sql
CREATE INDEX "QDITP_PLAT"."idx_blacklist_third_user_id" ON "QDITP_PLAT"."BLACKLIST" (third_user_id);

-- idx_sys_logininfor_lt [INDEX] ON SYS_LOGININFOR  -- 来源 web-server/sql/ry_20250522_oracle.sql
CREATE INDEX "QDITP_PLAT"."idx_sys_logininfor_lt" ON "QDITP_PLAT"."sys_logininfor" (login_time);

-- idx_sys_logininfor_s [INDEX] ON SYS_LOGININFOR  -- 来源 web-server/sql/ry_20250522_oracle.sql
CREATE INDEX "QDITP_PLAT"."idx_sys_logininfor_s" ON "QDITP_PLAT"."sys_logininfor" (status);

-- idx_sys_oper_log_bt [INDEX] ON SYS_OPER_LOG  -- 来源 web-server/sql/ry_20250522_oracle.sql
CREATE INDEX "QDITP_PLAT"."idx_sys_oper_log_bt" ON "QDITP_PLAT"."sys_oper_log" (business_type);

-- idx_sys_oper_log_ot [INDEX] ON SYS_OPER_LOG  -- 来源 web-server/sql/ry_20250522_oracle.sql
CREATE INDEX "QDITP_PLAT"."idx_sys_oper_log_ot" ON "QDITP_PLAT"."sys_oper_log" (oper_time);

-- idx_sys_oper_log_s [INDEX] ON SYS_OPER_LOG  -- 来源 web-server/sql/ry_20250522_oracle.sql
CREATE INDEX "QDITP_PLAT"."idx_sys_oper_log_s" ON "QDITP_PLAT"."sys_oper_log" (status);

-- ==================== QDITP_USER（26 个） ====================
-- IDX_ACCT_EXC_TICKET_STATUS [INDEX] ON ACCOUNT_EXCEPTION_TICKET  -- 来源 account-server/src/main/resources/sql/account-server-schema.sql
CREATE INDEX "QDITP_USER"."IDX_ACCT_EXC_TICKET_STATUS" ON "QDITP_USER"."ACCOUNT_EXCEPTION_TICKET" (TICKET_STATUS, CREATE_TMS);

-- IDX_ALIPAY_PHONE_CHG_TIME [INDEX] ON ALIPAY_PHONE_CHANGE_LOG  -- 来源 alipay-account-server/src/main/resources/sql/alipay-account-server-schema.sql
CREATE INDEX "QDITP_USER"."IDX_ALIPAY_PHONE_CHG_TIME" ON "QDITP_USER"."ALIPAY_PHONE_CHANGE_LOG" (OPER_TIME);

-- IDX_ALIPAY_PHONE_CHG_USER_ID [INDEX] ON ALIPAY_PHONE_CHANGE_LOG  -- 来源 alipay-account-server/src/main/resources/sql/alipay-account-server-schema.sql
CREATE INDEX "QDITP_USER"."IDX_ALIPAY_PHONE_CHG_USER_ID" ON "QDITP_USER"."ALIPAY_PHONE_CHANGE_LOG" (THIRD_USER_ID);

-- IDX_ASI_CHANNEL_SYNC [INDEX] ON ALIPAY_SIGN_INFO  -- 来源 alipay-pay-sign-server/src/main/resources/sql/alipay-sign-channel-sync-migration.sql
CREATE INDEX "QDITP_USER"."IDX_ASI_CHANNEL_SYNC" ON "QDITP_USER"."ALIPAY_SIGN_INFO" (CHANNEL_SYNC_STATUS, CHANNEL_SYNC_TIME);

-- IDX_LOGIC_CARD_POOL_BATCH_STATE [INDEX] ON LOGIC_CARD_POOL_BATCH  -- 来源 card-pool-server/src/main/resources/sql/card-pool-schema.sql
CREATE INDEX "QDITP_USER"."IDX_LOGIC_CARD_POOL_BATCH_STATE" ON "QDITP_USER"."LOGIC_CARD_POOL_BATCH" (CARD_TYPE, STATUS, CREATE_TIME);

-- IDX_LOGIC_CARD_POOL_CARD_AVAIL [INDEX] ON LOGIC_CARD_POOL_CARD  -- 来源 card-pool-server/src/main/resources/sql/card-pool-schema.sql
CREATE INDEX "QDITP_USER"."IDX_LOGIC_CARD_POOL_CARD_AVAIL" ON "QDITP_USER"."LOGIC_CARD_POOL_CARD" (CARD_TYPE, STATUS, ID);

-- IDX_LOGIC_CARD_POOL_CARD_STATUS [INDEX] ON LOGIC_CARD_POOL_CARD  -- 来源 card-pool-server/src/main/resources/sql/card-pool-schema.sql
CREATE INDEX "QDITP_USER"."IDX_LOGIC_CARD_POOL_CARD_STATUS" ON "QDITP_USER"."LOGIC_CARD_POOL_CARD" (CARD_TYPE, STATUS);

-- IDX_UAECL_CARD_NO_TMS [INDEX] ON USER_ACC_EMPLOYEE_CARD_LOG  -- 来源 account-server/src/main/resources/sql/account-server-schema.sql
CREATE INDEX "QDITP_USER"."IDX_UAECL_CARD_NO_TMS" ON "QDITP_USER"."USER_ACC_EMPLOYEE_CARD_LOG" (CARD_NO, CREATE_TMS DESC);

-- IDX_UAEC_CARD_STATUS [INDEX] ON USER_ACC_EMPLOYEE_CARD  -- 来源 account-server/src/main/resources/sql/account-server-schema.sql
CREATE INDEX "QDITP_USER"."IDX_UAEC_CARD_STATUS" ON "QDITP_USER"."USER_ACC_EMPLOYEE_CARD" (CARD_STATUS);

-- IDX_UAEC_PHONE [INDEX] ON USER_ACC_EMPLOYEE_CARD  -- 来源 account-server/src/main/resources/sql/account-server-schema.sql
CREATE INDEX "QDITP_USER"."IDX_UAEC_PHONE" ON "QDITP_USER"."USER_ACC_EMPLOYEE_CARD" (PHONE);

-- IDX_UAEC_PHONE_STATUS [INDEX] ON USER_ACC_EMPLOYEE_CARD  -- 来源 account-server/src/main/resources/sql/account-server-schema.sql
CREATE INDEX "QDITP_USER"."IDX_UAEC_PHONE_STATUS" ON "QDITP_USER"."USER_ACC_EMPLOYEE_CARD" (PHONE, CARD_STATUS);

-- IDX_UAEC_THIRD_USER_ID [INDEX] ON USER_ACC_EMPLOYEE_CARD  -- 来源 account-server/src/main/resources/sql/account-server-schema.sql
CREATE INDEX "QDITP_USER"."IDX_UAEC_THIRD_USER_ID" ON "QDITP_USER"."USER_ACC_EMPLOYEE_CARD" (THIRD_USER_ID);

-- IDX_UAT_UNUSED_CARD [INDEX] ON USER_ACC_TICKETNO  -- 来源 account-server/src/main/resources/sql/account-server-schema.sql
CREATE INDEX "QDITP_USER"."IDX_UAT_UNUSED_CARD" ON "QDITP_USER"."USER_ACC_TICKETNO" (THIRD_USER_ID, INSERT_TMS);

-- IDX_UIRI_CARD_ID [INDEX] ON USER_ITP_REG_INFO  -- 来源 account-server/src/main/resources/sql/account-server-schema.sql
CREATE INDEX "QDITP_USER"."IDX_UIRI_CARD_ID" ON "QDITP_USER"."USER_ITP_REG_INFO" (CARD_ID) LOCAL;

-- IDX_UIRI_THIRD_PAY_ID [INDEX] ON USER_ITP_REG_INFO  -- 来源 account-server/src/main/resources/sql/account-server-schema.sql
CREATE INDEX "QDITP_USER"."IDX_UIRI_THIRD_PAY_ID" ON "QDITP_USER"."USER_ITP_REG_INFO" (THIRD_PAY_ID) LOCAL;

-- IDX_UIRI_THIRD_USER_ACTIVE [INDEX] ON USER_ITP_REG_INFO  -- 来源 account-server/src/main/resources/sql/account-server-schema.sql
CREATE INDEX "QDITP_USER"."IDX_UIRI_THIRD_USER_ACTIVE" ON "QDITP_USER"."USER_ITP_REG_INFO" (THIRD_USER_ID, DEL_YN, REG_TMS DESC) LOCAL;

-- IDX_UIRL_CARD_ID [INDEX] ON USER_ITP_REG_LOG  -- 来源 account-server/src/main/resources/sql/account-server-schema.sql
CREATE INDEX "QDITP_USER"."IDX_UIRL_CARD_ID" ON "QDITP_USER"."USER_ITP_REG_LOG" (CARD_ID, OPER_DATE_TIME DESC);

-- IDX_UIRL_THIRD_USER_ID [INDEX] ON USER_ITP_REG_LOG  -- 来源 account-server/src/main/resources/sql/account-server-schema.sql
CREATE INDEX "QDITP_USER"."IDX_UIRL_THIRD_USER_ID" ON "QDITP_USER"."USER_ITP_REG_LOG" (THIRD_USER_ID, OPER_DATE_TIME DESC);

-- IDX_UPCL_SIGN_SYNC [INDEX] ON USER_PHONE_CHANGE_LOG  -- 来源 account-server/src/main/resources/sql/account-server-phone-sync-migration.sql
CREATE INDEX "QDITP_USER"."IDX_UPCL_SIGN_SYNC" ON "QDITP_USER"."USER_PHONE_CHANGE_LOG" (SIGN_SYNC_STATUS, SIGN_SYNC_RETRY_COUNT);

-- IDX_USER_PHONE_CHANGE_LOG_TIME [INDEX] ON USER_PHONE_CHANGE_LOG  -- 来源 account-server/src/main/resources/sql/account-server-schema.sql
CREATE INDEX "QDITP_USER"."IDX_USER_PHONE_CHANGE_LOG_TIME" ON "QDITP_USER"."USER_PHONE_CHANGE_LOG" (OPER_TIME);

-- IDX_USER_PHONE_CHANGE_LOG_USER_ID [INDEX] ON USER_PHONE_CHANGE_LOG  -- 来源 account-server/src/main/resources/sql/account-server-schema.sql
CREATE INDEX "QDITP_USER"."IDX_USER_PHONE_CHANGE_LOG_USER_ID" ON "QDITP_USER"."USER_PHONE_CHANGE_LOG" (THIRD_USER_ID);

-- UK_ARL_REFUND_ORDER_NO [UNIQUE] ON ALIPAY_REFUND_LOG  -- 来源 alipay-pay-sign-server/src/main/resources/sql/alipay-pay-sign-callback-log-migration.sql
CREATE UNIQUE INDEX "QDITP_USER"."UK_ARL_REFUND_ORDER_NO" ON "QDITP_USER"."ALIPAY_REFUND_LOG" (REFUND_ORDER_NO);

-- UK_UAEC_CARD_NO [UNIQUE] ON USER_ACC_EMPLOYEE_CARD  -- 来源 account-server/src/main/resources/sql/account-server-schema.sql
CREATE UNIQUE INDEX "QDITP_USER"."UK_UAEC_CARD_NO" ON "QDITP_USER"."USER_ACC_EMPLOYEE_CARD" (CARD_NO);

-- UK_UAT_CARD_ID [UNIQUE] ON USER_ACC_TICKETNO  -- 来源 account-server/src/main/resources/sql/account-server-schema.sql
CREATE UNIQUE INDEX "QDITP_USER"."UK_UAT_CARD_ID" ON "QDITP_USER"."USER_ACC_TICKETNO" (CARD_ID);

-- UK_UIRI_ACTIVE_USER_CARDTYPE [UNIQUE] ON USER_ITP_REG_INFO  -- 来源 account-server/src/main/resources/sql/account-server-active-user-cardtype-index-migration.sql
CREATE UNIQUE INDEX "QDITP_USER"."UK_UIRI_ACTIVE_USER_CARDTYPE" ON "QDITP_USER"."USER_ITP_REG_INFO" ( CASE WHEN DEL_YN = 1 AND (TICKET_LIMIT = '1' OR (TICKET_LIMIT IS NULL AND NVL(COMPANION_FLAG, 'N') NOT IN ('Y', 'C'))) THEN THIRD_USER_ID END, CASE WHEN DEL_YN = 1 AND (TICKET_LIMIT = '1' OR (TICKET_LIMIT IS NULL AND NVL(COMPANION_FLAG, 'N') NOT IN ('Y', 'C'))) THEN ISSUE_ORG_CODE END, CASE WHEN DEL_YN = 1 AND (TICKET_LIMIT = '1' OR (TICKET_LIMIT IS NULL AND NVL(COMPANION_FLAG, 'N') NOT IN ('Y', 'C'))) THEN CARD_TYPE END);

-- UK_UIRI_LIMIT_PENDING [UNIQUE] ON USER_ITP_REG_INFO  -- 来源 account-server/src/main/resources/sql/account-server-ticket-limit-migration.sql
CREATE UNIQUE INDEX "QDITP_USER"."UK_UIRI_LIMIT_PENDING" ON "QDITP_USER"."USER_ITP_REG_INFO" ( CASE WHEN DEL_YN = 1 AND (TICKET_LIMIT = '1' OR (TICKET_LIMIT IS NULL AND NVL(COMPANION_FLAG, 'N') NOT IN ('Y', 'C'))) THEN THIRD_USER_ID END, CASE WHEN DEL_YN = 1 AND (TICKET_LIMIT = '1' OR (TICKET_LIMIT IS NULL AND NVL(COMPANION_FLAG, 'N') NOT IN ('Y', 'C'))) THEN ISSUE_ORG_CODE END, CASE WHEN DEL_YN = 1 AND (TICKET_LIMIT = '1' OR (TICKET_LIMIT IS NULL AND NVL(COMPANION_FLAG, 'N') NOT IN ('Y', 'C'))) THEN CARD_TYPE END);

-- ==================== QDITP_RIDE（7 个） ====================
-- IDX_QRCODE_TXN_DETAIL_CARD_DATE [INDEX] ON QRCODE_TXN_DETAIL  -- 来源 ticket-server/src/main/resources/sql/qrcode-txn-detail-schema.sql
CREATE INDEX "QDITP_RIDE"."IDX_QRCODE_TXN_DETAIL_CARD_DATE" ON "QDITP_RIDE"."QRCODE_TXN_DETAIL" ( CARD_ID, TXN_DATE, HANDLE_DATE_TIME ) LOCAL;

-- IDX_QRCODE_TXN_DETAIL_DEVICE_DATE [INDEX] ON QRCODE_TXN_DETAIL  -- 来源 ticket-server/src/main/resources/sql/qrcode-txn-detail-schema.sql
CREATE INDEX "QDITP_RIDE"."IDX_QRCODE_TXN_DETAIL_DEVICE_DATE" ON "QDITP_RIDE"."QRCODE_TXN_DETAIL" ( DEVICE_ID, TXN_DATE, HANDLE_DATE_TIME ) LOCAL;

-- IDX_QRCODE_TXN_DETAIL_USER_DATE [INDEX] ON QRCODE_TXN_DETAIL  -- 来源 ticket-server/src/main/resources/sql/qrcode-txn-detail-schema.sql
CREATE INDEX "QDITP_RIDE"."IDX_QRCODE_TXN_DETAIL_USER_DATE" ON "QDITP_RIDE"."QRCODE_TXN_DETAIL" ( ITP_USER_ID, TXN_DATE, HANDLE_DATE_TIME ) LOCAL;

-- IDX_QSR_STATUS_CREATE [INDEX] ON QRCODE_SUPPLEMENT_REQUEST  -- 来源 ticket-server/src/main/resources/sql/ticket-server-supplement-request-migration.sql
CREATE INDEX "QDITP_RIDE"."IDX_QSR_STATUS_CREATE" ON "QDITP_RIDE"."QRCODE_SUPPLEMENT_REQUEST" (HANDLE_STATUS, CREATE_TIME);

-- UK_QRCODE_TXN_DETAIL_BIZ [UNIQUE] ON QRCODE_TXN_DETAIL  -- 来源 ticket-server/src/main/resources/sql/qrcode-txn-detail-schema.sql
CREATE UNIQUE INDEX "QDITP_RIDE"."UK_QRCODE_TXN_DETAIL_BIZ" ON "QDITP_RIDE"."QRCODE_TXN_DETAIL" ( CARD_ID, TRX_TYPE, HANDLE_DATE_TIME, TICKET_TRANS_SEQ, DEVICE_ID, TXN_DATE ) LOCAL;

-- UK_QR_CODE_STATUS_CARD [UNIQUE] ON QRCODE_STATUS  -- 来源 ticket-server/src/main/resources/sql/ticket-server-schema.sql
CREATE UNIQUE INDEX "QDITP_RIDE"."UK_QR_CODE_STATUS_CARD" ON "QDITP_RIDE"."QRCODE_STATUS" (CARD_ID, CARD_ID_TAIL) LOCAL;

-- UK_QSR_CARD_SEQ_ADVICE [UNIQUE] ON QRCODE_SUPPLEMENT_REQUEST  -- 来源 ticket-server/src/main/resources/sql/ticket-server-supplement-request-migration.sql
CREATE UNIQUE INDEX "QDITP_RIDE"."UK_QSR_CARD_SEQ_ADVICE" ON "QDITP_RIDE"."QRCODE_SUPPLEMENT_REQUEST" (CARD_ID, TXN_SEQ, ADVICE_OPT);

-- ==================== QDITP_TXN（103 个） ====================
-- IDX_APCL_HANDLE_STATUS [INDEX] ON ALIPAY_PAY_CALLBACK_LOG  -- 来源 alipay-pay-sign-server/src/main/resources/sql/alipay-pay-sign-callback-log-migration.sql
CREATE INDEX "QDITP_TXN"."IDX_APCL_HANDLE_STATUS" ON "QDITP_TXN"."ALIPAY_PAY_CALLBACK_LOG" (HANDLE_STATUS);

-- IDX_APCL_ORDER_NO_TYPE [INDEX] ON ALIPAY_PAY_CALLBACK_LOG  -- 来源 alipay-pay-sign-server/src/main/resources/sql/alipay-pay-sign-callback-log-migration.sql
CREATE INDEX "QDITP_TXN"."IDX_APCL_ORDER_NO_TYPE" ON "QDITP_TXN"."ALIPAY_PAY_CALLBACK_LOG" (ORDER_NO, CALLBACK_TYPE);

-- IDX_APCML_API_DATE [INDEX] ON ALIPAY_PAY_CENTER_MSG_LOG  -- 来源 alipay-pay-sign-server/src/main/resources/sql/alipay-pay-center-msg-log-migration.sql
CREATE INDEX "QDITP_TXN"."IDX_APCML_API_DATE" ON "QDITP_TXN"."ALIPAY_PAY_CENTER_MSG_LOG" (API_NAME, TXN_DATE) LOCAL;

-- IDX_APCML_ORDER [INDEX] ON ALIPAY_PAY_CENTER_MSG_LOG  -- 来源 alipay-pay-sign-server/src/main/resources/sql/alipay-pay-center-msg-log-migration.sql
CREATE INDEX "QDITP_TXN"."IDX_APCML_ORDER" ON "QDITP_TXN"."ALIPAY_PAY_CENTER_MSG_LOG" (ORDER_NO, TXN_DATE) LOCAL;

-- IDX_APP_PAY_SIGN_INFO_USER_ID [INDEX] ON APP_PAY_SIGN_INFO  -- 来源 pay-sign-server/src/main/resources/sql/pay-sign-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_APP_PAY_SIGN_INFO_USER_ID" ON "QDITP_TXN"."APP_PAY_SIGN_INFO" (THIRD_USER_ID);

-- IDX_APP_PAY_SIGN_LOG_USER_ID [INDEX] ON APP_PAY_SIGN_LOG  -- 来源 pay-sign-server/src/main/resources/sql/pay-sign-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_APP_PAY_SIGN_LOG_USER_ID" ON "QDITP_TXN"."APP_PAY_SIGN_LOG" (THIRD_USER_ID);

-- IDX_APP_PAY_SIGN_REQUEST_NOTIFY_STATUS [INDEX] ON APP_PAY_SIGN_REQUEST  -- 来源 pay-sign-server/src/main/resources/sql/pay-sign-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_APP_PAY_SIGN_REQUEST_NOTIFY_STATUS" ON "QDITP_TXN"."APP_PAY_SIGN_REQUEST" (NOTIFY_STATUS, NOTIFY_RETRY_COUNT);

-- IDX_APP_PAY_SIGN_REQUEST_REQUEST_SIGN_SEQ [INDEX] ON APP_PAY_SIGN_REQUEST  -- 来源 pay-sign-server/src/main/resources/sql/pay-sign-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_APP_PAY_SIGN_REQUEST_REQUEST_SIGN_SEQ" ON "QDITP_TXN"."APP_PAY_SIGN_REQUEST" (REQUEST_SIGN_SEQ);

-- IDX_APTD_CHANNEL_ORDER [INDEX] ON ALIPAY_PAY_TXN_DETAIL  -- 来源 alipay-pay-sign-server/src/main/resources/sql/alipay-pay-txn-detail-rebuild-migration.sql
CREATE INDEX "QDITP_TXN"."IDX_APTD_CHANNEL_ORDER" ON "QDITP_TXN"."ALIPAY_PAY_TXN_DETAIL" (CHANNEL_ORDER_NO, TXN_DATE) LOCAL;

-- IDX_APTD_STATUS [INDEX] ON ALIPAY_PAY_TXN_DETAIL  -- 来源 alipay-pay-sign-server/src/main/resources/sql/alipay-pay-txn-detail-rebuild-migration.sql
CREATE INDEX "QDITP_TXN"."IDX_APTD_STATUS" ON "QDITP_TXN"."ALIPAY_PAY_TXN_DETAIL" (PAY_STATUS, TXN_DATE, NEXT_REQUEST_TIME) LOCAL;

-- IDX_ARTD_ORDER_STATUS [INDEX] ON ALIPAY_REFUND_TXN_DETAIL  -- 来源 alipay-pay-sign-server/src/main/resources/sql/alipay-pay-txn-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_ARTD_ORDER_STATUS" ON "QDITP_TXN"."ALIPAY_REFUND_TXN_DETAIL" (ORDER_NO, REFUND_STATUS) LOCAL;

-- IDX_ARTD_STATUS [INDEX] ON ALIPAY_REFUND_TXN_DETAIL  -- 来源 alipay-pay-sign-server/src/main/resources/sql/alipay-pay-txn-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_ARTD_STATUS" ON "QDITP_TXN"."ALIPAY_REFUND_TXN_DETAIL" (REFUND_STATUS, TXN_DATE, NEXT_REQUEST_TIME) LOCAL;

-- IDX_ATR_CHANNEL_SYNC [INDEX] ON APP_TERMINATION_REQUEST  -- 来源 pay-sign-server/src/main/resources/sql/pay-sign-channel-sync-migration.sql
CREATE INDEX "QDITP_TXN"."IDX_ATR_CHANNEL_SYNC" ON "QDITP_TXN"."APP_TERMINATION_REQUEST" (CHANNEL_SYNC_STATUS, CHANNEL_SYNC_RETRY_COUNT);

-- IDX_ATR_NOTIFY_STATUS [INDEX] ON APP_TERMINATION_REQUEST  -- 来源 pay-sign-server/src/main/resources/sql/pay-sign-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_ATR_NOTIFY_STATUS" ON "QDITP_TXN"."APP_TERMINATION_REQUEST" (NOTIFY_STATUS, NOTIFY_RETRY_COUNT);

-- IDX_ATR_USER_STATUS [INDEX] ON APP_TERMINATION_REQUEST  -- 来源 pay-sign-server/src/main/resources/sql/pay-sign-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_ATR_USER_STATUS" ON "QDITP_TXN"."APP_TERMINATION_REQUEST" (THIRD_USER_ID, TERMINATION_STATUS, REQUEST_TIME);

-- IDX_DAILY_TICKET_INSTANCE_CARD [INDEX] ON DAILY_TICKET_INSTANCE  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_DAILY_TICKET_INSTANCE_CARD" ON "QDITP_TXN"."DAILY_TICKET_INSTANCE" (CARD_NUM);

-- IDX_DAILY_TICKET_INSTANCE_STATUS [INDEX] ON DAILY_TICKET_INSTANCE  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_DAILY_TICKET_INSTANCE_STATUS" ON "QDITP_TXN"."DAILY_TICKET_INSTANCE" (TICKET_STATUS, ACC_NOTICE_STATUS);

-- IDX_DAILY_TICKET_ORDER_BATCH_REFUND [INDEX] ON DAILY_TICKET_ORDER  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-batch-refund-migration.sql
CREATE INDEX "QDITP_TXN"."IDX_DAILY_TICKET_ORDER_BATCH_REFUND" ON "QDITP_TXN"."DAILY_TICKET_ORDER" (ORDER_STATUS, PAY_STATUS, PAY_DATE);

-- IDX_DAILY_TICKET_ORDER_CREATE [INDEX] ON DAILY_TICKET_ORDER  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-refund-page-migration.sql
CREATE INDEX "QDITP_TXN"."IDX_DAILY_TICKET_ORDER_CREATE" ON "QDITP_TXN"."DAILY_TICKET_ORDER" (CREATE_TIME);

-- IDX_DAILY_TICKET_ORDER_PARENT [INDEX] ON DAILY_TICKET_ORDER  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_DAILY_TICKET_ORDER_PARENT" ON "QDITP_TXN"."DAILY_TICKET_ORDER" (PARENT_ORDER_NO);

-- IDX_DAILY_TICKET_ORDER_PAYMENT [INDEX] ON DAILY_TICKET_ORDER  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-refund-page-migration.sql
CREATE INDEX "QDITP_TXN"."IDX_DAILY_TICKET_ORDER_PAYMENT" ON "QDITP_TXN"."DAILY_TICKET_ORDER" (PAYMENT_ORDER_NO);

-- IDX_DAILY_TICKET_ORDER_RECON [INDEX] ON DAILY_TICKET_ORDER  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-recon-export-index.sql
CREATE INDEX "QDITP_TXN"."IDX_DAILY_TICKET_ORDER_RECON" ON "QDITP_TXN"."DAILY_TICKET_ORDER" (PAY_STATUS, PAY_DATE, ORDER_NO);

-- IDX_DAILY_TICKET_ORDER_STATUS [INDEX] ON DAILY_TICKET_ORDER  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_DAILY_TICKET_ORDER_STATUS" ON "QDITP_TXN"."DAILY_TICKET_ORDER" (ORDER_STATUS, PAY_STATUS);

-- IDX_DAILY_TICKET_ORDER_USER [INDEX] ON DAILY_TICKET_ORDER  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_DAILY_TICKET_ORDER_USER" ON "QDITP_TXN"."DAILY_TICKET_ORDER" (THIRD_USER_ID, USER_ID);

-- IDX_DAILY_TICKET_PAY_LOG_ORDER [INDEX] ON DAILY_TICKET_PAY_LOG  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_DAILY_TICKET_PAY_LOG_ORDER" ON "QDITP_TXN"."DAILY_TICKET_PAY_LOG" (ORDER_NO, BIZ_TYPE);

-- IDX_DAILY_TICKET_PAY_NOTIFY_STATUS [INDEX] ON DAILY_TICKET_PAY_NOTIFY  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-pay-notify-migration.sql
CREATE INDEX "QDITP_TXN"."IDX_DAILY_TICKET_PAY_NOTIFY_STATUS" ON "QDITP_TXN"."DAILY_TICKET_PAY_NOTIFY" (NOTIFY_STATUS);

-- IDX_DAILY_TICKET_REFUND_CREATE [INDEX] ON DAILY_TICKET_REFUND  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-refund-page-migration.sql
CREATE INDEX "QDITP_TXN"."IDX_DAILY_TICKET_REFUND_CREATE" ON "QDITP_TXN"."DAILY_TICKET_REFUND" (CREATE_TIME);

-- IDX_DAILY_TICKET_REFUND_DETAIL_PARENT [INDEX] ON DAILY_TICKET_REFUND_DETAIL  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_DAILY_TICKET_REFUND_DETAIL_PARENT" ON "QDITP_TXN"."DAILY_TICKET_REFUND_DETAIL" (PARENT_ORDER_NO);

-- IDX_DAILY_TICKET_REFUND_NOTIFY [INDEX] ON DAILY_TICKET_REFUND  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-refund-notify-migration.sql
CREATE INDEX "QDITP_TXN"."IDX_DAILY_TICKET_REFUND_NOTIFY" ON "QDITP_TXN"."DAILY_TICKET_REFUND" (NOTIFY_STATUS);

-- IDX_DAILY_TICKET_REFUND_ORDER_NO [INDEX] ON DAILY_TICKET_REFUND  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-refund-page-migration.sql
CREATE INDEX "QDITP_TXN"."IDX_DAILY_TICKET_REFUND_ORDER_NO" ON "QDITP_TXN"."DAILY_TICKET_REFUND" (REFUND_ORDER_NO);

-- IDX_DAILY_TICKET_REFUND_PARENT [INDEX] ON DAILY_TICKET_REFUND  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-travel-closed-loop-migration.sql
CREATE INDEX "QDITP_TXN"."IDX_DAILY_TICKET_REFUND_PARENT" ON "QDITP_TXN"."DAILY_TICKET_REFUND" (PARENT_ORDER_NO);

-- IDX_DAILY_TICKET_REFUND_PLATFORM_NO [INDEX] ON DAILY_TICKET_REFUND  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-refund-page-migration.sql
CREATE INDEX "QDITP_TXN"."IDX_DAILY_TICKET_REFUND_PLATFORM_NO" ON "QDITP_TXN"."DAILY_TICKET_REFUND" (PLATFORM_REFUND_NO);

-- IDX_DAILY_TICKET_REFUND_VERIFY [INDEX] ON DAILY_TICKET_REFUND  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_DAILY_TICKET_REFUND_VERIFY" ON "QDITP_TXN"."DAILY_TICKET_REFUND" (REFUND_STATUS, VERIFY_AFTER_TIME);

-- IDX_DTUL_CARD [INDEX] ON DAILY_TICKET_USAGE_LOG  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-usage-log-migration.sql
CREATE INDEX "QDITP_TXN"."IDX_DTUL_CARD" ON "QDITP_TXN"."DAILY_TICKET_USAGE_LOG" (CARD_NUM, CREATE_TIME);

-- IDX_F2F_DEVICE_HB [INDEX] ON F2F_DEVICE_STATUS  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_F2F_DEVICE_HB" ON "QDITP_TXN"."F2F_DEVICE_STATUS" (LAST_HEARTBEAT_TMS);

-- IDX_F2F_NOTIFY_SCAN [INDEX] ON F2F_NOTIFY_TASK  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_F2F_NOTIFY_SCAN" ON "QDITP_TXN"."F2F_NOTIFY_TASK" (NOTIFY_STATUS, NEXT_RETRY_TMS);

-- IDX_F2F_ORDER_BATCH_REFUND [INDEX] ON F2F_ORDER  -- 来源 face-pay-server/src/main/resources/sql/f2f-batch-refund-migration.sql
CREATE INDEX "QDITP_TXN"."IDX_F2F_ORDER_BATCH_REFUND" ON "QDITP_TXN"."F2F_ORDER" (ORDER_STATUS, BIZ_TYPE, PAID_TMS) LOCAL;

-- IDX_F2F_ORDER_CARD [INDEX] ON F2F_ORDER  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_F2F_ORDER_CARD" ON "QDITP_TXN"."F2F_ORDER" (CARD_ID, CREATE_TMS DESC) LOCAL;

-- IDX_F2F_ORDER_QRCODE [INDEX] ON F2F_ORDER  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_F2F_ORDER_QRCODE" ON "QDITP_TXN"."F2F_ORDER" (DEVICE_ID, QRCODE_GEN_DATE, RANDOM_FACT) LOCAL;

-- IDX_F2F_ORDER_SCAN [INDEX] ON F2F_ORDER  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_F2F_ORDER_SCAN" ON "QDITP_TXN"."F2F_ORDER" (ORDER_STATUS, EXPIRE_TMS) LOCAL;

-- IDX_F2F_ORDER_SUSPECT [INDEX] ON F2F_ORDER  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_F2F_ORDER_SUSPECT" ON "QDITP_TXN"."F2F_ORDER" (SUSPECT_SLIP_NO) LOCAL;

-- IDX_F2F_ORDER_USER [INDEX] ON F2F_ORDER  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_F2F_ORDER_USER" ON "QDITP_TXN"."F2F_ORDER" (THIRD_USER_ID, ACTIVATE_FLAG, CREATE_TMS DESC) LOCAL;

-- IDX_F2F_PAY_CENTER_NO [INDEX] ON F2F_PAYMENT  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_F2F_PAY_CENTER_NO" ON "QDITP_TXN"."F2F_PAYMENT" (PAY_CENTER_ORDER_NO) LOCAL;

-- IDX_F2F_PAY_ORDER [INDEX] ON F2F_PAYMENT  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_F2F_PAY_ORDER" ON "QDITP_TXN"."F2F_PAYMENT" (ORDER_NO, ATTEMPT_NO DESC) LOCAL;

-- IDX_F2F_REFUND_CENTER [INDEX] ON F2F_REFUND  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_F2F_REFUND_CENTER" ON "QDITP_TXN"."F2F_REFUND" (PAY_CENTER_REFUND_NO);

-- IDX_F2F_REFUND_ORIG [INDEX] ON F2F_REFUND  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_F2F_REFUND_ORIG" ON "QDITP_TXN"."F2F_REFUND" (ORIG_ORDER_NO);

-- IDX_F2F_REFUND_SCAN [INDEX] ON F2F_REFUND  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_F2F_REFUND_SCAN" ON "QDITP_TXN"."F2F_REFUND" (REFUND_STATUS, NEXT_QUERY_TMS);

-- IDX_F2F_REPORT_PENDING [INDEX] ON F2F_RESULT_REPORT  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_F2F_REPORT_PENDING" ON "QDITP_TXN"."F2F_RESULT_REPORT" (PROCESSED, RECEIVE_TMS) LOCAL;

-- IDX_F2F_REPORT_SLIP [INDEX] ON F2F_RESULT_REPORT  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_F2F_REPORT_SLIP" ON "QDITP_TXN"."F2F_RESULT_REPORT" (FAULT_SLIP_SEQ) LOCAL;

-- IDX_F2F_TICKET_ORDER [INDEX] ON F2F_TICKET  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_F2F_TICKET_ORDER" ON "QDITP_TXN"."F2F_TICKET" (ORDER_NO);

-- IDX_F2F_TICKET_RECENT [INDEX] ON F2F_TICKET  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_F2F_TICKET_RECENT" ON "QDITP_TXN"."F2F_TICKET" (TICKET_LOGIC_NUM, TRANS_DATE DESC);

-- IDX_GATE_RETRY_QUEUE_CONSUME [INDEX] ON GATE_RETRY_QUEUE  -- 来源 gate-txn-pay-server/src/main/resources/sql/gate-txn-pay-retry-queue-migration.sql
CREATE INDEX "QDITP_TXN"."IDX_GATE_RETRY_QUEUE_CONSUME" ON "QDITP_TXN"."GATE_RETRY_QUEUE" (STATUS, CREATE_TIME) WHERE STATUS IN ('PENDING', 'FAILED');

-- IDX_GATE_TXN_PAY_CARD_DATE [INDEX] ON GATE_TXN_PAY  -- 来源 gate-txn-pay-server/src/main/resources/sql/gate-txn-pay-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_GATE_TXN_PAY_CARD_DATE" ON "QDITP_TXN"."GATE_TXN_PAY" ( CARD_ID, TXN_DATE, OUT_TIME ) LOCAL;

-- IDX_GATE_TXN_PAY_DEVICE_DATE [INDEX] ON GATE_TXN_PAY  -- 来源 gate-txn-pay-server/src/main/resources/sql/gate-txn-pay-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_GATE_TXN_PAY_DEVICE_DATE" ON "QDITP_TXN"."GATE_TXN_PAY" ( DEVICE_ID, TXN_DATE, OUT_TIME ) LOCAL;

-- IDX_GATE_TXN_PAY_STATUS_DATE [INDEX] ON GATE_TXN_PAY  -- 来源 gate-txn-pay-server/src/main/resources/sql/gate-txn-pay-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_GATE_TXN_PAY_STATUS_DATE" ON "QDITP_TXN"."GATE_TXN_PAY" ( DEBIT_STATUS, TXN_DATE, CREATE_TIME ) LOCAL;

-- IDX_GATE_TXN_PAY_USER_DATE [INDEX] ON GATE_TXN_PAY  -- 来源 gate-txn-pay-server/src/main/resources/sql/gate-txn-pay-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_GATE_TXN_PAY_USER_DATE" ON "QDITP_TXN"."GATE_TXN_PAY" ( THIRD_USER_ID, TXN_DATE, OUT_TIME ) LOCAL;

-- IDX_PAY_CALLBACK_LOG_ORDER [INDEX] ON PAY_CALLBACK_LOG  -- 来源 pay-sign-server/src/main/resources/sql/pay-txn-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_PAY_CALLBACK_LOG_ORDER" ON "QDITP_TXN"."PAY_CALLBACK_LOG" ( ORDER_NO, TXN_DATE ) LOCAL;

-- IDX_PAY_CALLBACK_LOG_REFUND [INDEX] ON PAY_CALLBACK_LOG  -- 来源 pay-sign-server/src/main/resources/sql/pay-txn-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_PAY_CALLBACK_LOG_REFUND" ON "QDITP_TXN"."PAY_CALLBACK_LOG" ( REFUND_ORDER_NO, TXN_DATE ) LOCAL;

-- IDX_PAY_REFUND_DETAIL_ORDER [INDEX] ON PAY_REFUND_DETAIL  -- 来源 pay-sign-server/src/main/resources/sql/pay-txn-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_PAY_REFUND_DETAIL_ORDER" ON "QDITP_TXN"."PAY_REFUND_DETAIL" ( ORDER_NO, TXN_DATE ) LOCAL;

-- IDX_PAY_REFUND_DETAIL_STATUS [INDEX] ON PAY_REFUND_DETAIL  -- 来源 pay-sign-server/src/main/resources/sql/pay-txn-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_PAY_REFUND_DETAIL_STATUS" ON "QDITP_TXN"."PAY_REFUND_DETAIL" ( REFUND_STATUS, TXN_DATE, NEXT_REQUEST_TIME ) LOCAL;

-- IDX_PAY_TXN_DETAIL_CARD_DATE [INDEX] ON PAY_TXN_DETAIL  -- 来源 pay-sign-server/src/main/resources/sql/pay-txn-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_PAY_TXN_DETAIL_CARD_DATE" ON "QDITP_TXN"."PAY_TXN_DETAIL" ( CARD_ID, TXN_DATE ) LOCAL;

-- IDX_PAY_TXN_DETAIL_CHANNEL_ORDER [INDEX] ON PAY_TXN_DETAIL  -- 来源 pay-sign-server/src/main/resources/sql/pay-txn-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_PAY_TXN_DETAIL_CHANNEL_ORDER" ON "QDITP_TXN"."PAY_TXN_DETAIL" ( CHANNEL_ORDER_NO, TXN_DATE ) LOCAL;

-- IDX_PAY_TXN_DETAIL_STATUS [INDEX] ON PAY_TXN_DETAIL  -- 来源 pay-sign-server/src/main/resources/sql/pay-txn-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_PAY_TXN_DETAIL_STATUS" ON "QDITP_TXN"."PAY_TXN_DETAIL" ( PAY_STATUS, TXN_DATE, NEXT_REQUEST_TIME ) LOCAL;

-- IDX_PAY_TXN_DETAIL_USER_DATE [INDEX] ON PAY_TXN_DETAIL  -- 来源 pay-sign-server/src/main/resources/sql/pay-txn-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_PAY_TXN_DETAIL_USER_DATE" ON "QDITP_TXN"."PAY_TXN_DETAIL" ( THIRD_USER_ID, TXN_DATE ) LOCAL;

-- IDX_RECON_PART_STATUS [INDEX] ON RECON_BATCH_PART  -- 来源 recon-server/src/main/resources/sql/recon-server-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_RECON_PART_STATUS" ON "QDITP_TXN"."RECON_BATCH_PART" (BATCH_ID, STATUS);

-- IDX_RECON_SOURCE_STATUS [INDEX] ON RECON_BATCH_SOURCE  -- 来源 recon-server/src/main/resources/sql/recon-server-schema-migration.sql
CREATE INDEX "QDITP_TXN"."IDX_RECON_SOURCE_STATUS" ON "QDITP_TXN"."RECON_BATCH_SOURCE" (BATCH_ID, STATUS);

-- IDX_RECON_THIRD_BILL_KEY [INDEX] ON RECON_THIRD_BILL  -- 来源 recon-server/src/main/resources/sql/recon-thirdparty-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_RECON_THIRD_BILL_KEY" ON "QDITP_TXN"."RECON_THIRD_BILL" (BILL_DATE, PAYMENT_CHANNEL, MERCHANT_ORDER_NO, CHANNEL_TRADE_NO);

-- IDX_RECON_TXN_DIFF_BILL [INDEX] ON RECON_TXN_DIFF  -- 来源 recon-server/src/main/resources/sql/recon-thirdparty-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_RECON_TXN_DIFF_BILL" ON "QDITP_TXN"."RECON_TXN_DIFF" (BILL_DATE, PAY_CHANNEL, DIFF_SIDE);

-- IDX_SUPPLEMENT_ITEM_ORIG [INDEX] ON SUPPLEMENT_ORDER_ITEM  -- 来源 gate-txn-pay-server/src/main/resources/sql/supplement-order-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_SUPPLEMENT_ITEM_ORIG" ON "QDITP_TXN"."SUPPLEMENT_ORDER_ITEM" (ORIG_ORDER_NO);

-- IDX_SUPPLEMENT_ORDER_SALESYNC [INDEX] ON SUPPLEMENT_ORDER  -- 来源 gate-txn-pay-server/src/main/resources/sql/supplement-order-sale-sync-migration.sql
CREATE INDEX "QDITP_TXN"."IDX_SUPPLEMENT_ORDER_SALESYNC" ON "QDITP_TXN"."SUPPLEMENT_ORDER" (SALE_SYNC_STATUS, CREATE_TIME);

-- IDX_SUPPLEMENT_ORDER_STATUS [INDEX] ON SUPPLEMENT_ORDER  -- 来源 gate-txn-pay-server/src/main/resources/sql/supplement-order-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_SUPPLEMENT_ORDER_STATUS" ON "QDITP_TXN"."SUPPLEMENT_ORDER" (PAY_STATUS, CREATE_TIME);

-- IDX_SUPPLEMENT_ORDER_UPDT [INDEX] ON SUPPLEMENT_ORDER  -- 来源 gate-txn-pay-server/src/main/resources/sql/supplement-order-active-orig-migration.sql
CREATE INDEX "QDITP_TXN"."IDX_SUPPLEMENT_ORDER_UPDT" ON "QDITP_TXN"."SUPPLEMENT_ORDER" (PAY_STATUS, UPDATE_TIME);

-- IDX_SUPPLEMENT_ORDER_USER [INDEX] ON SUPPLEMENT_ORDER  -- 来源 gate-txn-pay-server/src/main/resources/sql/supplement-order-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_SUPPLEMENT_ORDER_USER" ON "QDITP_TXN"."SUPPLEMENT_ORDER" (THIRD_USER_ID, TXN_DATE);

-- IDX_TRAVEL_TICKET_ORDER_BATCH_REFUND [INDEX] ON TRAVEL_TICKET_ORDER  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-batch-refund-migration.sql
CREATE INDEX "QDITP_TXN"."IDX_TRAVEL_TICKET_ORDER_BATCH_REFUND" ON "QDITP_TXN"."TRAVEL_TICKET_ORDER" (ORDER_STATUS, PAY_STATUS, PAY_DATE);

-- IDX_TRAVEL_TICKET_ORDER_CREATE [INDEX] ON TRAVEL_TICKET_ORDER  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_TRAVEL_TICKET_ORDER_CREATE" ON "QDITP_TXN"."TRAVEL_TICKET_ORDER" (CREATE_TIME);

-- IDX_TRAVEL_TICKET_ORDER_PAYMENT [INDEX] ON TRAVEL_TICKET_ORDER  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_TRAVEL_TICKET_ORDER_PAYMENT" ON "QDITP_TXN"."TRAVEL_TICKET_ORDER" (PAYMENT_ORDER_NO);

-- IDX_TRAVEL_TICKET_ORDER_STATUS [INDEX] ON TRAVEL_TICKET_ORDER  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_TRAVEL_TICKET_ORDER_STATUS" ON "QDITP_TXN"."TRAVEL_TICKET_ORDER" (ORDER_STATUS, PAY_STATUS);

-- IDX_TRAVEL_TICKET_ORDER_USER [INDEX] ON TRAVEL_TICKET_ORDER  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql
CREATE INDEX "QDITP_TXN"."IDX_TRAVEL_TICKET_ORDER_USER" ON "QDITP_TXN"."TRAVEL_TICKET_ORDER" (USER_ID);

-- UK_APPSI_REQUEST_SIGN_SEQ [UNIQUE] ON APP_PAY_SIGN_INFO  -- 来源 pay-sign-server/src/main/resources/sql/pay-sign-schema.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_APPSI_REQUEST_SIGN_SEQ" ON "QDITP_TXN"."APP_PAY_SIGN_INFO" (REQUEST_SIGN_SEQ);

-- UK_APTD_ORDER [UNIQUE] ON ALIPAY_PAY_TXN_DETAIL  -- 来源 alipay-pay-sign-server/src/main/resources/sql/alipay-pay-txn-detail-rebuild-migration.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_APTD_ORDER" ON "QDITP_TXN"."ALIPAY_PAY_TXN_DETAIL" (ORDER_NO, TXN_DATE) LOCAL;

-- UK_ARTD_REFUND_ORDER [UNIQUE] ON ALIPAY_REFUND_TXN_DETAIL  -- 来源 alipay-pay-sign-server/src/main/resources/sql/alipay-pay-txn-schema.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_ARTD_REFUND_ORDER" ON "QDITP_TXN"."ALIPAY_REFUND_TXN_DETAIL" (REFUND_ORDER_NO, TXN_DATE) LOCAL;

-- UK_ATR_REQUEST_SIGN_SEQ [UNIQUE] ON APP_TERMINATION_REQUEST  -- 来源 pay-sign-server/src/main/resources/sql/pay-sign-schema.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_ATR_REQUEST_SIGN_SEQ" ON "QDITP_TXN"."APP_TERMINATION_REQUEST" (REQUEST_SIGN_SEQ);

-- UK_DAILY_TICKET_INSTANCE_ORDER [UNIQUE] ON DAILY_TICKET_INSTANCE  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_DAILY_TICKET_INSTANCE_ORDER" ON "QDITP_TXN"."DAILY_TICKET_INSTANCE" (ORDER_NO);

-- UK_DAILY_TICKET_PAY_NOTIFY_ORDER [UNIQUE] ON DAILY_TICKET_PAY_NOTIFY  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-pay-notify-migration.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_DAILY_TICKET_PAY_NOTIFY_ORDER" ON "QDITP_TXN"."DAILY_TICKET_PAY_NOTIFY" (ORDER_NO);

-- UK_DAILY_TICKET_REFUND_DETAIL [UNIQUE] ON DAILY_TICKET_REFUND_DETAIL  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-server-schema.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_DAILY_TICKET_REFUND_DETAIL" ON "QDITP_TXN"."DAILY_TICKET_REFUND_DETAIL" (REFUND_ORDER_NO, SUB_ORDER_NO);

-- UK_DAILY_TICKET_REFUND_ORDER [UNIQUE] ON DAILY_TICKET_REFUND  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-refund-page-migration.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_DAILY_TICKET_REFUND_ORDER" ON "QDITP_TXN"."DAILY_TICKET_REFUND" (ORDER_NO);

-- UK_DTUL_ORDER [UNIQUE] ON DAILY_TICKET_USAGE_LOG  -- 来源 daily-ticket-server/src/main/resources/sql/daily-ticket-usage-log-migration.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_DTUL_ORDER" ON "QDITP_TXN"."DAILY_TICKET_USAGE_LOG" (ORDER_NO);

-- UK_F2F_NOTIFY_IDEM [UNIQUE] ON F2F_NOTIFY_TASK  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_F2F_NOTIFY_IDEM" ON "QDITP_TXN"."F2F_NOTIFY_TASK" (NOTIFY_TYPE, ORDER_NO, NVL(REFUND_NO, '#NONE#'));

-- UK_F2F_ORDER_DEV_SEQ [UNIQUE] ON F2F_ORDER  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_F2F_ORDER_DEV_SEQ" ON "QDITP_TXN"."F2F_ORDER" ( CASE WHEN DEVICE_SEQ IS NULL THEN NULL ELSE CHANNEL END, CASE WHEN DEVICE_SEQ IS NULL THEN NULL ELSE DEVICE_ID END, DEVICE_SEQ ) GLOBAL;

-- UK_F2F_ORDER_NO [UNIQUE] ON F2F_ORDER  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_F2F_ORDER_NO" ON "QDITP_TXN"."F2F_ORDER" (ORDER_NO) GLOBAL;

-- UK_F2F_PAY_ATTEMPT [UNIQUE] ON F2F_PAYMENT  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_F2F_PAY_ATTEMPT" ON "QDITP_TXN"."F2F_PAYMENT" (ORDER_NO, ATTEMPT_NO) GLOBAL;

-- UK_F2F_PAY_SUCCESS [UNIQUE] ON F2F_PAYMENT  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_F2F_PAY_SUCCESS" ON "QDITP_TXN"."F2F_PAYMENT" ( CASE WHEN PAY_STATUS = 'SUCCESS' THEN ORDER_NO ELSE NULL END ) GLOBAL;

-- UK_F2F_REFUND_IDEM [UNIQUE] ON F2F_REFUND  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_F2F_REFUND_IDEM" ON "QDITP_TXN"."F2F_REFUND" ( ORIG_ORDER_NO, NVL(TICKET_LOGIC_NUM, '#WHOLE#'), REFUND_SOURCE );

-- UK_F2F_REFUND_NO [UNIQUE] ON F2F_REFUND  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_F2F_REFUND_NO" ON "QDITP_TXN"."F2F_REFUND" (REFUND_NO);

-- UK_F2F_REPORT_IDEM [UNIQUE] ON F2F_RESULT_REPORT  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_F2F_REPORT_IDEM" ON "QDITP_TXN"."F2F_RESULT_REPORT" (REPORT_TYPE, ORDER_NO) GLOBAL;

-- UK_F2F_TICKET_LOGIC [UNIQUE] ON F2F_TICKET  -- 来源 face-pay-server/src/main/resources/sql/f2f-schema.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_F2F_TICKET_LOGIC" ON "QDITP_TXN"."F2F_TICKET" (TICKET_LOGIC_NUM, TRANS_DATE);

-- UK_GATE_RETRY_QUEUE_ORDER [UNIQUE] ON GATE_RETRY_QUEUE  -- 来源 gate-txn-pay-server/src/main/resources/sql/gate-txn-pay-retry-queue-migration.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_GATE_RETRY_QUEUE_ORDER" ON "QDITP_TXN"."GATE_RETRY_QUEUE" (ORDER_NO, TXN_DATE);

-- UK_GATE_TXN_PAY_ORDER_NO [UNIQUE] ON GATE_TXN_PAY  -- 来源 gate-txn-pay-server/src/main/resources/sql/gate-txn-pay-schema.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_GATE_TXN_PAY_ORDER_NO" ON "QDITP_TXN"."GATE_TXN_PAY" ( ORDER_NO, TXN_DATE ) LOCAL;

-- UK_PAY_REFUND_DETAIL_NO [UNIQUE] ON PAY_REFUND_DETAIL  -- 来源 pay-sign-server/src/main/resources/sql/pay-txn-schema.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_PAY_REFUND_DETAIL_NO" ON "QDITP_TXN"."PAY_REFUND_DETAIL" ( REFUND_ORDER_NO, TXN_DATE ) LOCAL;

-- UK_PAY_TXN_DETAIL_ORDER [UNIQUE] ON PAY_TXN_DETAIL  -- 来源 pay-sign-server/src/main/resources/sql/pay-txn-schema.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_PAY_TXN_DETAIL_ORDER" ON "QDITP_TXN"."PAY_TXN_DETAIL" ( ORDER_NO, TXN_DATE ) LOCAL;

-- UK_SUPPLEMENT_ITEM_ACTIVE [UNIQUE] ON SUPPLEMENT_ORDER_ITEM  -- 来源 gate-txn-pay-server/src/main/resources/sql/supplement-order-active-orig-migration.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_SUPPLEMENT_ITEM_ACTIVE" ON "QDITP_TXN"."SUPPLEMENT_ORDER_ITEM" (ACTIVE_ORIG_ORDER_NO);

-- UK_SUPPLEMENT_ITEM_ORDER [UNIQUE] ON SUPPLEMENT_ORDER_ITEM  -- 来源 gate-txn-pay-server/src/main/resources/sql/supplement-order-schema.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_SUPPLEMENT_ITEM_ORDER" ON "QDITP_TXN"."SUPPLEMENT_ORDER_ITEM" (ORDER_NO, ORIG_ORDER_NO);

-- UK_SUPPLEMENT_ORDER_NO [UNIQUE] ON SUPPLEMENT_ORDER  -- 来源 gate-txn-pay-server/src/main/resources/sql/supplement-order-schema.sql
CREATE UNIQUE INDEX "QDITP_TXN"."UK_SUPPLEMENT_ORDER_NO" ON "QDITP_TXN"."SUPPLEMENT_ORDER" (ORDER_NO);

-- ==================== 表域未知（MUST 人工裁决后归位） ====================
-- IDX_DEVICE_INFO_MODE [INDEX] ON TBL_DEVICE_INFO  -- 来源 para-server/src/main/resources/sql/para-schema.sql
-- TODO: 表 TBL_DEVICE_INFO 不在四域映射内（可能是不在老库导出中的表，如 TBL_DEVICE_INFO；或不纳入的 STATION_INFO/BAK_*）——归域后补 schema 前缀再执行
CREATE INDEX IDX_DEVICE_INFO_MODE ON TBL_DEVICE_INFO (MANAGE_MODE);

-- IDX_DEVICE_INFO_STATION [INDEX] ON TBL_DEVICE_INFO  -- 来源 para-server/src/main/resources/sql/para-schema.sql
-- TODO: 表 TBL_DEVICE_INFO 不在四域映射内（可能是不在老库导出中的表，如 TBL_DEVICE_INFO；或不纳入的 STATION_INFO/BAK_*）——归域后补 schema 前缀再执行
CREATE INDEX IDX_DEVICE_INFO_STATION ON TBL_DEVICE_INFO (STATION_ID);

-- IDX_DEVICE_INFO_TYPE [INDEX] ON TBL_DEVICE_INFO  -- 来源 para-server/src/main/resources/sql/para-schema.sql
-- TODO: 表 TBL_DEVICE_INFO 不在四域映射内（可能是不在老库导出中的表，如 TBL_DEVICE_INFO；或不纳入的 STATION_INFO/BAK_*）——归域后补 schema 前缀再执行
CREATE INDEX IDX_DEVICE_INFO_TYPE ON TBL_DEVICE_INFO (LINE_ID, DEVICE_TYPE);

-- IDX_STATION_INFO_LINE [INDEX] ON STATION_INFO  -- 来源 ticket-server/src/main/resources/sql/station-info-schema.sql
-- TODO: 表 STATION_INFO 不在四域映射内（可能是不在老库导出中的表，如 TBL_DEVICE_INFO；或不纳入的 STATION_INFO/BAK_*）——归域后补 schema 前缀再执行
CREATE INDEX IDX_STATION_INFO_LINE ON STATION_INFO (LINE_CODE);
