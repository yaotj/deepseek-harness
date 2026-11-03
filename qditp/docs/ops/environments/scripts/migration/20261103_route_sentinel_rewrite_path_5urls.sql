-- =============================================================
-- route-sentinel 路由变更：5 条 URL 转发到旧 ITP，并做路径重写
-- 处理单：docs/tracking/IT-050-route-sentinel路径重写与双端口下游.md
--
-- 前置 DDL：审计表须含 BEFORE_FILTERS / AFTER_FILTERS 两列（1.0.5 起 Java 侧读写）。
-- 若列已存在，下面 ALTER TABLE 会报 ORA-01430，属无害重复执行；首次执行时必须加列。
--
-- 生效范围：
--   - 测试环境 QDITP.GATEWAY_ROUTE_CONFIG（172.20.222.3:1521/AFCITPDB）
--   - 红岛生产 QDITP_PLAT.GATEWAY_ROUTE_CONFIG（hditpdb）
--
-- 变更内容：
--   1. 删除旧路由 legacy-route / face-pay-split-route（route_id 主键冲突）
--   2. 新增 4 条分流路由：TVM/BOM 各分 legacy / face-pay-split 两条，因 TVM/BOM
--      下游端口不同（8180 / 8580）无法共用同一 base URI
--   3. legacy 路由（无用户）：
--      - BOM 路径 /itpbom/ci/bom/xxx 重写为 /itpbom/ci/bomDispatch/xxx
--      - TVM 路径 /itptvm/ci/tvm/xxx 原路径转发（旧 ITP 172.20.202.10:8180 实测无 tvmDispatch 端点）
--   4. face-pay-split 路由（有用户）：原路径不变，转发到 face-pay-server-internal:8080
--
-- 注意：
--   - 红岛 QDITP_PLAT.GATEWAY_ROUTE_CONFIG 有 LIST 分区 DEFAULT，新 route_id 落入默认分区
--   - 测试环境 QDITP.GATEWAY_ROUTE_CONFIG 无分区，直接插入
--   - 本脚本不删除 fep-app-route / face-pay-default-route，只确保 order_no 不冲突
-- =============================================================

-- ---------------- 测试环境（QDITP）----------------
ALTER TABLE QDITP.GATEWAY_ROUTE_CONFIG_AUDIT ADD (BEFORE_FILTERS CLOB, AFTER_FILTERS CLOB);

DELETE FROM QDITP.GATEWAY_ROUTE_CONFIG WHERE route_id IN ('legacy-route', 'face-pay-split-route');

INSERT INTO QDITP.GATEWAY_ROUTE_CONFIG (route_id, uri, predicates, filters, order_no, created_by) VALUES
    ('legacy-route-tvm', 'http://172.20.202.10:8180',
     '["Path=/itptvm/ci/tvm/notiTakeTicketResult","Path=/itptvm/ci/tvm/notiTakeTicketFailResult","Path=/itptvm/ci/tvm/requestTakeTicketAuth"]',
     '[]',
     0, 'migration-20261103');
INSERT INTO QDITP.GATEWAY_ROUTE_CONFIG (route_id, uri, predicates, filters, order_no, created_by) VALUES
    ('legacy-route-bom', 'http://172.20.202.10:8580',
     '["Path=/itpbom/ci/bom/requestCardDataAnalyse","Path=/itpbom/ci/bom/requestUpdateCardData"]',
     '["RewritePath=/itpbom/ci/bom/(?<segment>.*),/itpbom/ci/bomDispatch/${segment}"]',
     1, 'migration-20261103');
INSERT INTO QDITP.GATEWAY_ROUTE_CONFIG (route_id, uri, predicates, filters, order_no, created_by) VALUES
    ('face-pay-split-route-tvm', 'http://face-pay-server-internal:8080',
     '["Path=/itptvm/ci/tvm/notiTakeTicketResult","Path=/itptvm/ci/tvm/notiTakeTicketFailResult","Path=/itptvm/ci/tvm/requestTakeTicketAuth"]',
     '[]',
     2, 'migration-20261103');
INSERT INTO QDITP.GATEWAY_ROUTE_CONFIG (route_id, uri, predicates, filters, order_no, created_by) VALUES
    ('face-pay-split-route-bom', 'http://face-pay-server-internal:8080',
     '["Path=/itpbom/ci/bom/requestCardDataAnalyse","Path=/itpbom/ci/bom/requestUpdateCardData"]',
     '[]',
     3, 'migration-20261103');

-- 确保 fep-app-route / face-pay-default-route 的 order_no 与新路由不冲突
UPDATE QDITP.GATEWAY_ROUTE_CONFIG SET order_no = 4 WHERE route_id = 'fep-app-route' AND order_no < 4;
UPDATE QDITP.GATEWAY_ROUTE_CONFIG SET order_no = 5 WHERE route_id = 'face-pay-default-route' AND order_no < 4;

COMMIT;

-- ---------------- 红岛生产（QDITP_PLAT）----------------
-- 红岛按域分库，网关表在 QDITP_PLAT；执行前请确认已以 QDITP_PLAT 身份连接 hditpdb。
ALTER TABLE QDITP_PLAT.GATEWAY_ROUTE_CONFIG_AUDIT ADD (BEFORE_FILTERS CLOB, AFTER_FILTERS CLOB);

DELETE FROM QDITP_PLAT.GATEWAY_ROUTE_CONFIG WHERE route_id IN ('legacy-route', 'face-pay-split-route');

INSERT INTO QDITP_PLAT.GATEWAY_ROUTE_CONFIG (route_id, uri, predicates, filters, order_no, created_by) VALUES
    ('legacy-route-tvm', 'http://172.20.202.10:8180',
     '["Path=/itptvm/ci/tvm/notiTakeTicketResult","Path=/itptvm/ci/tvm/notiTakeTicketFailResult","Path=/itptvm/ci/tvm/requestTakeTicketAuth"]',
     '[]',
     0, 'migration-20261103');
INSERT INTO QDITP_PLAT.GATEWAY_ROUTE_CONFIG (route_id, uri, predicates, filters, order_no, created_by) VALUES
    ('legacy-route-bom', 'http://172.20.202.10:8580',
     '["Path=/itpbom/ci/bom/requestCardDataAnalyse","Path=/itpbom/ci/bom/requestUpdateCardData"]',
     '["RewritePath=/itpbom/ci/bom/(?<segment>.*),/itpbom/ci/bomDispatch/${segment}"]',
     1, 'migration-20261103');
INSERT INTO QDITP_PLAT.GATEWAY_ROUTE_CONFIG (route_id, uri, predicates, filters, order_no, created_by) VALUES
    ('face-pay-split-route-tvm', 'http://face-pay-server-internal:8080',
     '["Path=/itptvm/ci/tvm/notiTakeTicketResult","Path=/itptvm/ci/tvm/notiTakeTicketFailResult","Path=/itptvm/ci/tvm/requestTakeTicketAuth"]',
     '[]',
     2, 'migration-20261103');
INSERT INTO QDITP_PLAT.GATEWAY_ROUTE_CONFIG (route_id, uri, predicates, filters, order_no, created_by) VALUES
    ('face-pay-split-route-bom', 'http://face-pay-server-internal:8080',
     '["Path=/itpbom/ci/bom/requestCardDataAnalyse","Path=/itpbom/ci/bom/requestUpdateCardData"]',
     '[]',
     3, 'migration-20261103');

UPDATE QDITP_PLAT.GATEWAY_ROUTE_CONFIG SET order_no = 4 WHERE route_id = 'fep-app-route' AND order_no < 4;
UPDATE QDITP_PLAT.GATEWAY_ROUTE_CONFIG SET order_no = 5 WHERE route_id = 'face-pay-default-route' AND order_no < 4;

COMMIT;

-- ---------------- 验证 ----------------
SELECT route_id, uri, predicates, filters, order_no
  FROM QDITP.GATEWAY_ROUTE_CONFIG
 ORDER BY order_no;

SELECT route_id, uri, predicates, filters, order_no
  FROM QDITP_PLAT.GATEWAY_ROUTE_CONFIG
 ORDER BY order_no;
