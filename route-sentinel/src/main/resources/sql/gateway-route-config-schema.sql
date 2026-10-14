-- =============================================================
-- 网关路由配置表（落库 + 审计）
-- 目标表：QDITP.GATEWAY_ROUTE_CONFIG
-- 用途：Actuator 改路由后同步落库，参与启动兜底 + 审计留痕
--
-- 设计原则：
--   1. 路由配置落库 = 幂等操作（MERGE INTO）
--   2. 每次变更 INSERT 一行审计记录
--   3. 启动时从 DB 加载兜底（而不是 application.yml）
--   4. 网关不扫表——由 Actuator 写完后主动 SYNC，无定时任务
--   5. 路由只通过 Actuator 维护，不在此处 INSERT 初始化数据
-- =============================================================

-- 1) 路由配置表（主表，路由 ID 唯一）
CREATE TABLE IF NOT EXISTS QDITP.GATEWAY_ROUTE_CONFIG (
    ROUTE_ID          VARCHAR2(64 CHAR)     NOT NULL,
    URI               VARCHAR2(512 CHAR)    NOT NULL,
    PREDICATES        CLOB,
    FILTERS           CLOB DEFAULT '[]',
    ORDER_NO          NUMBER(10)          NOT NULL,
    CREATED_TMS       TIMESTAMP DEFAULT SYSTIMESTAMP,
    UPDATED_TMS       TIMESTAMP DEFAULT SYSTIMESTAMP,
    CREATED_BY        VARCHAR2(64 CHAR) DEFAULT 'system',
    CONSTRAINT PK_GATEWAY_ROUTE_CONFIG PRIMARY KEY (ROUTE_ID)
)
PARTITION BY LIST (ROUTE_ID) (
    PARTITION P_LEGACY VALUES ('legacy-route'),
    PARTITION P_NEW      VALUES ('new-route')
);

-- 2) 审计日志表（只插不删，按月份分区防膨胀）
CREATE TABLE IF NOT EXISTS QDITP.GATEWAY_ROUTE_CONFIG_AUDIT (
    AUDIT_ID          NUMBER(19)      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ROUTE_ID          VARCHAR2(64 CHAR) NOT NULL,
    ACTION            VARCHAR2(16 CHAR) NOT NULL,
    BEFORE_URI        VARCHAR2(512 CHAR),
    AFTER_URI         VARCHAR2(512 CHAR),
    BEFORE_PREDICATES CLOB,
    AFTER_PREDICATES  CLOB,
    OPERATOR          VARCHAR2(64 CHAR),
    OPERATE_TMS       TIMESTAMP DEFAULT SYSTIMESTAMP,
    REMARK            VARCHAR2(256 CHAR)
)
PARTITION BY RANGE (OPERATE_TMS) INTERVAL (INTERVAL '1' MONTH) (
    PARTITION P_202610 VALUES LESS THAN (TO_DATE('2026-11-01','YYYY-MM-DD'))
);
