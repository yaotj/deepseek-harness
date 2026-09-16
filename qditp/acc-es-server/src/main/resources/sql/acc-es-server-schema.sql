-- ============================================================================
-- acc-es-server 建表脚本（AFCITPDB / QDITP）
--
-- 背景：2026-08-25 核实 acc-es-server 13 个 mapper 对应的 13 张表在生产库
--       全部不存在，`/report/page` 报 ORA-00942。仓库中原本没有任何 DDL。
--
-- ⚠️ 本脚本由 mapper resultMap 的 jdbcType 与实体类字段类型**反推**得出，
--    甲方原始 DDL 未获得。以下为已做的取舍，拿到原始 DDL 后 MUST 逐列比对：
--    1. 所有字符串列统一用 VARCHAR2，**不用 CHAR**。
--       原因：CHAR 长度猜错会因尾部空格补齐导致等值比较失效；VARCHAR2 无此问题。
--    2. 所有 DECIMAL / NUMERIC 列用不带精度的 NUMBER。
--       原因：精度未知，不带精度可容纳任意数值，避免 ORA-01438。
--    3. 字符串长度按用途给宽：编码/ID 类 64，名称/文件名 256，长文本 1024。
--       原因：宁可偏大也不能偏小，偏小会 ORA-12899 或静默截断。
--    4. 仅主键列加 NOT NULL，其余全部可空（mapper 的 insertSelective 允许缺列）。
--    5. TASK_NO / PLAN_NO 统一 NUMBER。TblTktEsTaskMapper / TblTktEsAssignMapper
--       把 TASK_NO 标为 jdbcType=VARCHAR，与 TblTktEsReport/Proc 的 DECIMAL 冲突，
--       此处按「任务号本质是数字」统一为 NUMBER，靠 Oracle 隐式转换兼容。
--
-- 执行环境：Oracle 19c，schema QDITP
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. TBL_TKT_TASK_PLAN — 制票任务计划
-- ---------------------------------------------------------------------------
CREATE TABLE TBL_TKT_TASK_PLAN (
  PLAN_NO           NUMBER          NOT NULL,
  TASK_TYPE         VARCHAR2(64),
  TICKET_MAIN_TYPE  NUMBER,
  TICKET_TYPE       NUMBER,
  TICKET_SUB_TYPE   NUMBER,
  PLAN_NUM          NUMBER,
  ACT_NUM           NUMBER,
  ASSIGN_NUM        NUMBER,
  INIT_AMT          NUMBER,
  PLAN_TMS          TIMESTAMP,
  TASK_PLAN_STAT    VARCHAR2(64),
  LAST_UPD_ID       VARCHAR2(64),
  LAST_UPD_TMS      TIMESTAMP,
  CREATE_USER       VARCHAR2(64),
  APPROVE_USER      VARCHAR2(64),
  TICKET_STATUS     VARCHAR2(64),
  CONSTRAINT PK_TBL_TKT_TASK_PLAN PRIMARY KEY (PLAN_NO)
);

-- ---------------------------------------------------------------------------
-- 2. TBL_TKT_ES_TASK — 编码设备制票任务
-- ---------------------------------------------------------------------------
CREATE TABLE TBL_TKT_ES_TASK (
  TASK_NO           NUMBER          NOT NULL,
  PLAN_NO           NUMBER,
  CHECKOUT_NO       VARCHAR2(64),
  TASK_TYPE         VARCHAR2(64),
  PLAN_DATE         VARCHAR2(32),
  ACT_DATE          VARCHAR2(32),
  TICKET_MAIN_TYPE  NUMBER,
  TICKET_TYPE       NUMBER,
  TICKET_SUB_TYPE   NUMBER,
  TEST_FLG          VARCHAR2(8),
  VER_NO            VARCHAR2(32),
  BATCH_NO          VARCHAR2(64),
  TICKET_BATCH_NO   VARCHAR2(64),
  TASK_NUM          NUMBER,
  BEGIN_NO          NUMBER,
  SVT_TICKET_TYPE   NUMBER,
  END_NO            NUMBER,
  DEP_AMT           NUMBER,
  INIT_AMT          NUMBER,
  REWARD_AMT        NUMBER,
  VALID_DAYS        NUMBER,
  BEGIN_DATE        VARCHAR2(32),
  FINISH_NUM        NUMBER,
  WASTE_NUM         NUMBER,
  TICK_USE_TIMES    NUMBER,
  IS_MONTH_CARD     VARCHAR2(8),
  RIDE_PRIMESSION   VARCHAR2(8),
  IS_NAMED_CARD     VARCHAR2(8),
  IS_ACTIVE         VARCHAR2(8),
  IS_PRINT_PHOTO    VARCHAR2(8),
  TASK_ASSN_STAT    VARCHAR2(8),
  TASK_EXEC_STAT    VARCHAR2(8),
  GEN_TMS           TIMESTAMP,
  LAST_UPD_ID       VARCHAR2(64),
  LAST_UPD_TMS      TIMESTAMP,
  IS_MEMORIAL       VARCHAR2(8),
  WALLET_UNIT       VARCHAR2(8),
  APPR_DATE         VARCHAR2(32),
  APPR_USER         VARCHAR2(64),
  APPR_STAT         VARCHAR2(64),
  FILE_NAME         VARCHAR2(256),
  RIDE_NUM_TYPE     VARCHAR2(64),
  SPECIFY_STATIONS  VARCHAR2(1024),
  SPECIFY_AREAS     VARCHAR2(1024),
  SPECIFY_LINES     VARCHAR2(1024),
  CREATE_USER       VARCHAR2(64),
  TICKET_STATUS     VARCHAR2(64),
  CONSTRAINT PK_TBL_TKT_ES_TASK PRIMARY KEY (TASK_NO)
);

CREATE INDEX IDX_TKT_ES_TASK_PLAN_NO ON TBL_TKT_ES_TASK (PLAN_NO);

-- ---------------------------------------------------------------------------
-- 3. TBL_TKT_ES_ASSIGN — 任务分配到编码设备
-- ---------------------------------------------------------------------------
CREATE TABLE TBL_TKT_ES_ASSIGN (
  TASK_NO       NUMBER          NOT NULL,
  ES_CODE       VARCHAR2(64)    NOT NULL,
  BEGIN_NO      NUMBER,
  TASK_NUM      NUMBER,
  END_NO        NUMBER,
  TASK_STAT     VARCHAR2(8),
  FILE_NM       VARCHAR2(256),
  LAST_UPD_ID   VARCHAR2(64),
  LAST_UPD_TMS  TIMESTAMP,
  CONSTRAINT PK_TBL_TKT_ES_ASSIGN PRIMARY KEY (TASK_NO, ES_CODE)
);

-- ---------------------------------------------------------------------------
-- 4. TBL_TKT_ES_INFO — 编码设备台账
-- ---------------------------------------------------------------------------
CREATE TABLE TBL_TKT_ES_INFO (
  ES_CODE       VARCHAR2(64)    NOT NULL,
  ES_NAME       VARCHAR2(256),
  INST_NO       VARCHAR2(64),
  MAKE_NUM      NUMBER,
  WASH_NUM      NUMBER,
  WALE_NUM      NUMBER,
  CANCEL_NUM    NUMBER,
  WORK_TIMES    NUMBER,
  ES_STAT       VARCHAR2(8),
  LOGIN_STAT    VARCHAR2(8),
  LOGIN_USER    VARCHAR2(64),
  LAST_UPD_ID   VARCHAR2(64),
  LAST_UPD_TMS  TIMESTAMP,
  CONSTRAINT PK_TBL_TKT_ES_INFO PRIMARY KEY (ES_CODE)
);

-- ---------------------------------------------------------------------------
-- 5. TBL_TKT_ES_ACCOUNT — 编码设备操作员账号
-- 注意：PASSWORD 是 Oracle 保留字，mapper 中已用双引号引用，此处保持一致。
-- ---------------------------------------------------------------------------
CREATE TABLE TBL_TKT_ES_ACCOUNT (
  USERNAME        VARCHAR2(64)    NOT NULL,
  "PASSWORD"      VARCHAR2(256),
  USER_TYPE       NUMBER,
  REMARK          VARCHAR2(512),
  ADD_DATE        VARCHAR2(32),
  UPDATE_DATE     VARCHAR2(32),
  LAST_UPDATE_ID  VARCHAR2(64),
  CONSTRAINT PK_TBL_TKT_ES_ACCOUNT PRIMARY KEY (USERNAME)
);

-- ---------------------------------------------------------------------------
-- 6. TBL_TKT_ES_REPORT — ES 任务报告（日志中报错表之一）
-- ---------------------------------------------------------------------------
CREATE TABLE TBL_TKT_ES_REPORT (
  TASK_NO       NUMBER          NOT NULL,
  ES_CODE       VARCHAR2(64)    NOT NULL,
  CHECKIN_NO    VARCHAR2(64),
  TASK_NUM      NUMBER,
  BEGIN_NO      NUMBER,
  END_NO        NUMBER,
  FINISH_NUM    NUMBER,
  WASTE_NUM     NUMBER,
  BEGIN_TIME    VARCHAR2(32),
  END_TIME      VARCHAR2(32),
  TASK_TYPE     VARCHAR2(64),
  LAST_UPD_ID   VARCHAR2(64),
  LAST_UPD_TMS  TIMESTAMP,
  CONSTRAINT PK_TBL_TKT_ES_REPORT PRIMARY KEY (TASK_NO, ES_CODE)
);

-- ---------------------------------------------------------------------------
-- 7. TBL_TKT_ES_PROC — ES 报告文件处理结果（日志中报错表之一）
-- ---------------------------------------------------------------------------
CREATE TABLE TBL_TKT_ES_PROC (
  TASK_NO       NUMBER          NOT NULL,
  ES_CODE       VARCHAR2(64)    NOT NULL,
  FILE_SEQ_NO   NUMBER          NOT NULL,
  REPORT_DATE   VARCHAR2(32),
  FILE_NM       VARCHAR2(256),
  REC_NUM       NUMBER,
  PROC_NUM      NUMBER,
  PROC_STAT     VARCHAR2(8),
  LAST_UPD_ID   VARCHAR2(64),
  LAST_UPD_TMS  TIMESTAMP,
  CONSTRAINT PK_TBL_TKT_ES_PROC PRIMARY KEY (TASK_NO, ES_CODE, FILE_SEQ_NO)
);

-- ---------------------------------------------------------------------------
-- 8. TBL_TKT_ES_FILE_PROC_LOG — 文件逐条处理明细
-- ---------------------------------------------------------------------------
CREATE TABLE TBL_TKT_ES_FILE_PROC_LOG (
  TASK_NO           NUMBER          NOT NULL,
  ES_CODE           VARCHAR2(64)    NOT NULL,
  RECORD_NO         NUMBER          NOT NULL,
  TASK_TYPE         VARCHAR2(64),
  FILE_NM           VARCHAR2(256),
  FILE_PROC_STATE   VARCHAR2(8),
  PROC_REC_NUM      NUMBER,
  PROC_SUCCESS_NUM  NUMBER,
  PROC_FAIL_NUM     NUMBER,
  RECORD_CONTENT    VARCHAR2(2048),
  LAST_UPD_ID       VARCHAR2(64),
  LAST_UPD_TMS      TIMESTAMP,
  CONSTRAINT PK_TBL_TKT_ES_FILE_PROC_LOG PRIMARY KEY (TASK_NO, ES_CODE, RECORD_NO)
);

-- ---------------------------------------------------------------------------
-- 9. TBL_TKT_PRE_PERSON — 记名卡预登记人员
-- 注意：ID 是列名（mapper 中即为 ID），非保留字，可直接使用。
-- ---------------------------------------------------------------------------
CREATE TABLE TBL_TKT_PRE_PERSON (
  TASK_NO      NUMBER          NOT NULL,
  PHY_CODE     VARCHAR2(64)    NOT NULL,
  PERSON_TYPE  VARCHAR2(64),
  USER_NAME    VARCHAR2(256),
  ID_TYPE      VARCHAR2(64),
  ID           VARCHAR2(64),
  EMP_NO       VARCHAR2(64),
  WORK_UNIT    VARCHAR2(256),
  CONSTRAINT PK_TBL_TKT_PRE_PERSON PRIMARY KEY (TASK_NO, PHY_CODE)
);

-- ---------------------------------------------------------------------------
-- 10. TBL_STL_TICKET_SET — 票种设置
-- ---------------------------------------------------------------------------
CREATE TABLE TBL_STL_TICKET_SET (
  TICKET_TYPE      NUMBER          NOT NULL,
  TICKET_NAME      VARCHAR2(256),
  ISSUE_ID         VARCHAR2(64),
  CYCLE_STLMT_FLG  VARCHAR2(8),
  CARD_TYPE        VARCHAR2(8),
  PRICE_TYPE       VARCHAR2(8),
  TYPE1            VARCHAR2(64),
  TYPE2            VARCHAR2(64),
  TYPE3            VARCHAR2(64),
  TYPE4            VARCHAR2(64),
  TYPE5            VARCHAR2(64),
  USE_FLG          VARCHAR2(8),
  LAST_UPD_ID      VARCHAR2(64),
  LAST_UPD_TMS     TIMESTAMP,
  CONSTRAINT PK_TBL_STL_TICKET_SET PRIMARY KEY (TICKET_TYPE)
);

-- ---------------------------------------------------------------------------
-- 11. TBL_STL_TICKET_INFO — 票卡信息
-- ---------------------------------------------------------------------------
CREATE TABLE TBL_STL_TICKET_INFO (
  TICKET_LOGIC_NO   VARCHAR2(64)    NOT NULL,
  TICKET_TYPE       NUMBER,
  TICKET_SUB_TYPE   NUMBER,
  TICKET_CSN        VARCHAR2(64),
  FACE_ID           VARCHAR2(64),
  TICKET_KIND_FLAG  VARCHAR2(8),
  TICKET_VER        NUMBER,
  PHY_TYPE          NUMBER,
  PUB_DATE          VARCHAR2(32),
  PUB_BATCH_NO      NUMBER,
  VALID_AREA        VARCHAR2(256),
  MISC_CD           VARCHAR2(64),
  INIT_AMT          NUMBER,
  INIT_REW_AMT      NUMBER,
  EXPIRE_DATE       VARCHAR2(32),
  TICKET_STATUS     VARCHAR2(8),
  CHANGE_DATE       VARCHAR2(32),
  SALE_TIME         TIMESTAMP,
  CONS_COUNTER      NUMBER,
  TICKET_BAL        NUMBER,
  ACCT_BAL          NUMBER,
  LAST_TXN_TMS      TIMESTAMP,
  LAST_UPD_USER     VARCHAR2(64),
  LAST_UPD_TMS      TIMESTAMP,
  CONSTRAINT PK_TBL_STL_TICKET_INFO PRIMARY KEY (TICKET_LOGIC_NO)
);

CREATE INDEX IDX_STL_TICKET_INFO_CSN ON TBL_STL_TICKET_INFO (TICKET_CSN);

-- ---------------------------------------------------------------------------
-- 12. TBL_STL_ACCT_INFO — 储值票卡账户信息
-- ---------------------------------------------------------------------------
CREATE TABLE TBL_STL_ACCT_INFO (
  TICKET_LOGIC_NO  VARCHAR2(64)    NOT NULL,
  TICKET_TYPE      NUMBER,
  TICKET_SUB_TYPE  NUMBER,
  TICKET_CSN       VARCHAR2(64),
  TICKET_FACE_NO   VARCHAR2(64),
  CARD_TYPE        VARCHAR2(8),
  TICKET_VER       NUMBER,
  PHY_TYPE         NUMBER,
  PUB_DATE         VARCHAR2(32),
  PUB_BATCH_NO     NUMBER,
  VALID_AREA       VARCHAR2(256),
  MISC_CD          VARCHAR2(64),
  INIT_AMT         NUMBER,
  INIT_REW_AMT     NUMBER,
  DEP_AMT          NUMBER,
  REM_DEP_AMT      NUMBER,
  EXPIRE_DATE      VARCHAR2(32),
  TICKET_STATUS    VARCHAR2(8),
  REFUND_STATUS    VARCHAR2(8),
  CHANGE_DATE      VARCHAR2(32),
  SALE_TIME        TIMESTAMP,
  TOT_ADD_AMT      NUMBER,
  TOT_ADD_COUNT    NUMBER,
  ADD_COUNTER      NUMBER,
  TOT_CONS_COUNT   NUMBER,
  TOT_CONS_AMT     NUMBER,
  CONS_COUNTER     NUMBER,
  TICKET_BAL       NUMBER,
  ACCT_BAL         NUMBER,
  LAST_TXN_TMS     TIMESTAMP,
  ORG_TICKET_ID    VARCHAR2(64),
  NEW_TICKET_ID    VARCHAR2(64),
  LAST_UPD_USER    VARCHAR2(64),
  LAST_UPD_TMS     TIMESTAMP,
  CONSTRAINT PK_TBL_STL_ACCT_INFO PRIMARY KEY (TICKET_LOGIC_NO)
);

-- TblStlAcctInfoMapper 有 WHERE TICKET_CSN = '00000000' || #{csn} 的查询
CREATE INDEX IDX_STL_ACCT_INFO_CSN ON TBL_STL_ACCT_INFO (TICKET_CSN);

-- ---------------------------------------------------------------------------
-- 13. TBL_STL_PERSON_INFO — 记名卡持卡人信息（含照片 BLOB）
-- ---------------------------------------------------------------------------
CREATE TABLE TBL_STL_PERSON_INFO (
  TICKET_ID      VARCHAR2(64)    NOT NULL,
  PERSON_NAME    VARCHAR2(256),
  PERSON_SEX     VARCHAR2(8),
  PID_CD         VARCHAR2(64),
  PID_CODE       VARCHAR2(64),
  PASSWD         VARCHAR2(256),
  LANG_CD        VARCHAR2(64),
  COMPANY_NM     VARCHAR2(256),
  EMP_NO         VARCHAR2(64),
  CON_TEL_NO     VARCHAR2(64),
  CON_MAIL       VARCHAR2(256),
  ADDRESS        VARCHAR2(512),
  POST_CD        VARCHAR2(64),
  LAST_UPD_USER  VARCHAR2(64),
  LAST_UPD_TMS   TIMESTAMP,
  PHOTO_BLOB     BLOB,
  CONSTRAINT PK_TBL_STL_PERSON_INFO PRIMARY KEY (TICKET_ID)
);
