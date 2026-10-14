-- =============================================================
-- 网关路由配置表（落库 + 审计）
-- 目标表：QDITP.GATEWAY_ROUTE_CONFIG
-- 用途：Actuator 改路由后同步落库，参与启动兜底 + 审计留痕
--
-- 设计原则：
--   1. 路由配置落库 = 幂等操作（INSERT ... ON DUPLICATE KEY UPDATE）
--   2. 每次变更 INSERT 一行审计记录（audit_log）
--   3. 启动时从 DB 加载兜底（而不是 application.yml）
--   4. 网关不扫表——由 Actuator 写完后主动 SYNC，无定时任务
-- =============================================================

-- 1) 路由配置表（主表，路由 ID 唯一）
CREATE TABLE IF NOT EXISTS QDITP.GATEWAY_ROUTE_CONFIG (
    ROUTE_ID          VARCHAR2(64 CHAR)     NOT NULL,        -- 路由 ID（legacy-route / new-route）
    URI               VARCHAR2(512 CHAR)    NOT NULL,        -- 出口 URL
    PREDICATES        CLOB,                                -- predicates JSON 数组，例：["Path=/itptvm/**","Path=/itpbom/**"]
    FILTERS           CLOB DEFAULT '[]',                   -- filters JSON 数组（当前空）
    ORDER_NO          NUMBER(10)          NOT NULL,        -- 路由 order（legacy=0, new=1）
    CREATED_TMS       TIMESTAMP DEFAULT SYSTIMESTAMP,
    UPDATED_TMS       TIMESTAMP DEFAULT SYSTIMESTAMP,
    CREATED_BY        VARCHAR2(64 CHAR) DEFAULT 'system',  -- 操作人（DBA 姓名 / actuator）
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
    ACTION            VARCHAR2(16 CHAR) NOT NULL,           -- CREATE / UPDATE / DELETE
    BEFORE_URI        VARCHAR2(512 CHAR),
    AFTER_URI         VARCHAR2(512 CHAR),
    BEFORE_PREDICATES CLOB,
    AFTER_PREDICATES  CLOB,
    OPERATOR          VARCHAR2(64 CHAR),                    -- 操作人（从 Actuator header X-Operator 取）
    OPERATE_TMS       TIMESTAMP DEFAULT SYSTIMESTAMP,
    REMARK            VARCHAR2(256 CHAR)
)
PARTITION BY RANGE (OPERATE_TMS) INTERVAL (INTERVAL '1' MONTH) (
    PARTITION P_202610 VALUES LESS THAN (TO_DATE('2026-11-01','YYYY-MM-DD'))
);

-- 3) 路由配置表索引（点查 ROUTE_ID 用，分区键即主键无需额外索引）
-- （PK 已隐含唯一索引）

-- 4) 初始化两条默认路由（灰度前 legacy-url 可空）
MERGE INTO QDITP.GATEWAY_ROUTE_CONFIG t
USING (SELECT 'legacy-route' AS route_id FROM DUAL) s
ON (t.ROUTE_ID = s.ROUTE_ID)
WHEN MATCHED THEN
  UPDATE SET URI='http://', PREDICATES='["Path=/itptvm/**","Path=/itpbom/**"]', ORDER_NO=0, UPDATED_TMS=SYSTIMESTAMP, CREATED_BY='init'
WHEN NOT MATCHED THEN
  INSERT (ROUTE_ID, URI, PREDICATES, FILTERS, ORDER_NO, CREATED_TMS, UPDATED_TMS, CREATED_BY)
  VALUES ('legacy-route', 'http://', '["Path=/itptvm/**","Path=/itpbom/**"]', '[]', 0, SYSTIMESTAMP, SYSTIMESTAMP, 'init');

MERGE INTO QDITP.GATEWAY_ROUTE_CONFIG t
USING (SELECT 'new-route' AS route_id FROM DUAL) s
ON (t.ROUTE_ID = s.ROUTE_ID)
WHEN MATCHED THEN
  UPDATE SET URI='http://fep-app-svc:9101', PREDICATES='["Path=/itptvm/**","Path=/itpbom/**"]', ORDER_NO=1, UPDATED_TMS=SYSTIMESTAMP, CREATED_BY='init'
WHEN NOT MATCHED THEN
  INSERT (ROUTE_ID, URI, PREDICATES, FILTERS, ORDER_NO, CREATED_TMS, UPDATED_TMS, CREATED_BY)
  VALUES ('new-route', 'http://fep-app-svc:9101', '["Path=/itptvm/**","Path=/itpbom/**"]', '[]', 1, SYSTIMESTAMP, SYSTIMESTAMP, 'init');
