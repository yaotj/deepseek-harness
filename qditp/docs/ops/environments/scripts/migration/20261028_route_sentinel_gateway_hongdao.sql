-- =============================================================
-- 红岛 itpb · route-sentinel 网关表（QDITP_PLAT 域）建表 + 跨域只读同义词
-- 对应处理单：docs/tracking/IT-021-红岛网关路由表缺失.md
-- 执行库：    红岛生产 hditpdb（service name = hditpdb，NEVER 用 SID）
-- 执行时机：  在运维注入 route-sentinel 的 DB env 之前（IT-021 解封第 2 件）
--
-- 为什么单独出这份脚本（不直接用 route-sentinel 自带 schema SQL）：
--   route-sentinel/src/main/resources/sql/gateway-route-config-schema.sql
--   写的是 QDITP.（测试单 schema 前缀），且分区值域写死 'new-route'，
--   而红岛按域分库后网关表归 QDITP_PLAT，实际路由 id 是 'face-pay-split-route'
--   （照抄 'new-route' 会导致新路由插不进分区 → ORA-14400）。本脚本是红岛专用修正版。
--
-- 执行身份与顺序（务必按 Step 顺序，跨 schema 授权需对象属主或 DBA 代授）：
--   Step 1  以 QDITP_PLAT 身份建两张表（默认 schema=QDITP_PLAT，应用非限定名直接解析）
--   Step 2  以 QDITP_USER 身份（或 DBA 代授）把 USER_ITP_REG_INFO 的 SELECT 授给 QDITP_PLAT
--   Step 3  以 QDITP_PLAT 身份建私有同义词，使应用非限定名 user_itp_reg_info 可解析
--
-- 注意：Oracle（< 23ai）不支持 CREATE TABLE IF NOT EXISTS，故用普通 CREATE。
--       执行前先按"预检"确认表/同义词不存在，避免 ORA-00955（名称已由现有对象使用）。
-- =============================================================

-- -------------------- 预检（任一返回行则说明已存在，勿重复建） --------------------
SELECT table_name
  FROM all_tables
 WHERE owner = 'QDITP_PLAT'
   AND table_name IN ('GATEWAY_ROUTE_CONFIG', 'GATEWAY_ROUTE_CONFIG_AUDIT');

SELECT synonym_name, table_owner, table_name
  FROM all_synonyms
 WHERE owner = 'QDITP_PLAT'
   AND synonym_name = 'USER_ITP_REG_INFO';

-- ============================ Step 1：建表（QDITP_PLAT） ============================

CREATE TABLE QDITP_PLAT.GATEWAY_ROUTE_CONFIG (
    ROUTE_ID          VARCHAR2(64 CHAR)     NOT NULL,
    URI               VARCHAR2(512 CHAR)    NOT NULL,
    PREDICATES        CLOB,
    FILTERS           CLOB                  DEFAULT '[]',
    ORDER_NO          NUMBER(10)            NOT NULL,
    CREATED_TMS       TIMESTAMP             DEFAULT SYSTIMESTAMP,
    UPDATED_TMS       TIMESTAMP             DEFAULT SYSTIMESTAMP,
    CREATED_BY        VARCHAR2(64 CHAR)     DEFAULT 'system',
    CONSTRAINT PK_GATEWAY_ROUTE_CONFIG PRIMARY KEY (ROUTE_ID)
)
PARTITION BY LIST (ROUTE_ID) (
    PARTITION P_LEGACY  VALUES ('legacy-route'),
    PARTITION P_FACEPAY VALUES ('face-pay-split-route'),
    PARTITION P_DEFAULT VALUES (DEFAULT)
);

COMMENT ON TABLE QDITP_PLAT.GATEWAY_ROUTE_CONFIG
  IS '网关路由配置主表（route-sentinel 启动兜底加载 + Actuator 增删改落库，PLAT 域）';

CREATE TABLE QDITP_PLAT.GATEWAY_ROUTE_CONFIG_AUDIT (
    AUDIT_ID          NUMBER(19)            GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ROUTE_ID          VARCHAR2(64 CHAR)     NOT NULL,
    ACTION            VARCHAR2(16 CHAR)     NOT NULL,   -- CREATE / UPDATE / DELETE
    BEFORE_URI        VARCHAR2(512 CHAR),
    AFTER_URI         VARCHAR2(512 CHAR),
    BEFORE_PREDICATES CLOB,
    AFTER_PREDICATES  CLOB,
    OPERATOR          VARCHAR2(64 CHAR),
    OPERATE_TMS       TIMESTAMP             DEFAULT SYSTIMESTAMP,
    REMARK            VARCHAR2(256 CHAR)
)
PARTITION BY RANGE (OPERATE_TMS) INTERVAL (INTERVAL '1' MONTH) (
    PARTITION P_202610 VALUES LESS THAN (TO_DATE('2026-11-01', 'YYYY-MM-DD'))
);

COMMENT ON TABLE QDITP_PLAT.GATEWAY_ROUTE_CONFIG_AUDIT
  IS '网关路由变更审计表（只插不删，按月区间分区防膨胀）';

-- ===================== Step 2：跨域只读授权（USER 域 → PLAT 域） =====================
-- 须以 QDITP_USER 身份执行，或由具备该对象授权权限的 DBA 代授。
GRANT SELECT ON QDITP_USER.USER_ITP_REG_INFO TO QDITP_PLAT;

-- ===================== Step 3：QDITP_PLAT 侧私有同义词 =====================
-- 须以 QDITP_PLAT 身份执行；应用代码中全部使用非限定名 user_itp_reg_info，
-- 依赖此同义词解析到 QDITP_USER.USER_ITP_REG_INFO（只读）。
CREATE SYNONYM QDITP_PLAT.USER_ITP_REG_INFO FOR QDITP_USER.USER_ITP_REG_INFO;

-- ============================ 验证 ============================
-- （以 QDITP_PLAT 身份）
SELECT table_name
  FROM user_tables
 WHERE table_name IN ('GATEWAY_ROUTE_CONFIG', 'GATEWAY_ROUTE_CONFIG_AUDIT');

SELECT synonym_name, table_owner, table_name, status
  FROM user_synonyms
 WHERE synonym_name = 'USER_ITP_REG_INFO';

-- 连通性自测：用 QDITP_PLAT 连 hditpdb 后执行（确认非限定名均能解析）
SELECT COUNT(*) FROM gateway_route_config;
SELECT COUNT(*) FROM user_itp_reg_info;

-- ============================ 回滚（确认无业务数据后） ============================
-- DROP SYNONYM QDITP_PLAT.USER_ITP_REG_INFO;
-- REVOKE SELECT ON QDITP_USER.USER_ITP_REG_INFO FROM QDITP_PLAT;
-- DROP TABLE QDITP_PLAT.GATEWAY_ROUTE_CONFIG_AUDIT;
-- DROP TABLE QDITP_PLAT.GATEWAY_ROUTE_CONFIG;
