-- =============================================================================
-- 青岛地铁 ITP 数据库按域分库 — itp_ro 授权 + 跨库同义词（M1 阶段）
-- 配套文档：docs/ops/数据库按域分库迁移DDL草案.md §2.4 + §5.2 + §8
-- 状态：草稿 v1.3（仅供评审，严禁直接在生产库执行）
-- 前置依赖：§2 表空间 + 4 schema 已创建；USER_ITP_REG_INFO 已迁入 QDITP_USER
-- 执行时机：M1 阶段（QDITP_PLAT 建库 + 共享暴露），在 QDITP_USER 下执行
-- =============================================================================

SET VERIFY OFF
SET FEEDBACK 1
SET LINESIZE 200
SET PAGESIZE 100
SET SERVEROUTPUT ON SIZE UNLIMITED

-- =============================================================================
-- ① 网关只读账号 itp_ro 对 USER_ITP_REG_INFO 的 SELECT 授权
--    分流网关 qdscg 已按此实现：JDBC 连接使用 itp_ro，点查 third_user_id 做路由判定
-- =============================================================================
-- DBA 在 QDITP_USER 下执行（或 DBA 直接连 QDITP_USER 执行）：
GRANT SELECT ON QDITP_USER.USER_ITP_REG_INFO TO itp_ro
  ;

-- itp_ro 侧建私有同义词（避免 PUBLIC 同义词污染其他 schema）
-- 若 itp_ro 在 qdscg 网关机器上，网关连接串里用 itp_ro 账号即可；
-- 同义词由数据库侧维护，不必在每台网关机器上建
CREATE SYNONYM itp_ro.USER_ITP_REG_INFO FOR QDITP_USER.USER_ITP_REG_INFO
  ;

-- =============================================================================
-- ② QDITP_PLAT 公共引用表对 3 个业务库的 GRANT SELECT（按需启用）
--    以下列出「跨库共享策略」§4 要求必须授权的表；其余 TBL_* 参数按各模块代码引用扩展
-- =============================================================================

-- PLAT → USER（用户中心访问公共引用）
GRANT SELECT ON QDITP_PLAT.BLACKLIST               TO QDITP_USER
  ;
GRANT SELECT ON QDITP_PLAT.BLACKLIST_OPERATE_LOG   TO QDITP_USER
  ;
GRANT SELECT ON QDITP_PLAT.BLACKLIST_RELEASED      TO QDITP_USER
  ;
GRANT SELECT ON QDITP_PLAT.TBL_STATION_INFO        TO QDITP_USER
  ;
-- 其余 TBL_* 参数（TBL_ADD_PARA / TBL_BASE_FARE / TBL_CHIP_TYPE / ...）
-- 按各模块 Mapper XML 实际引用逐一 GRANT，此处留 TODO 占位，MUST 用 grep 确认最小集：
-- TODO: GRANT SELECT ON QDITP_PLAT.<各 TBL_*> TO QDITP_USER;

-- PLAT → TXN（交易支付访问公共引用）
GRANT SELECT ON QDITP_PLAT.BLACKLIST               TO QDITP_TXN
  ;
GRANT SELECT ON QDITP_PLAT.BLACKLIST_OPERATE_LOG   TO QDITP_TXN
  ;
GRANT SELECT ON QDITP_PLAT.BLACKLIST_RELEASED      TO QDITP_TXN
  ;
-- TODO: GRANT SELECT ON QDITP_PLAT.<计费相关 TBL_*> TO QDITP_TXN;

-- PLAT → RIDE（乘运访问公共引用）
GRANT SELECT ON QDITP_PLAT.BLACKLIST               TO QDITP_RIDE
  ;
GRANT SELECT ON QDITP_PLAT.TBL_STATION_INFO        TO QDITP_RIDE
  ;
-- TODO: GRANT SELECT ON QDITP_PLAT.<其余公共引用> TO QDITP_RIDE;

-- =============================================================================
-- ③ 跨库私有同义词（各业务 schema 下建，指向 PLAT 或 USER 实体表）
--    原则：业务代码查 `BLACKLIST` / `TBL_STATION_INFO` 时无需改 schema 前缀
-- =============================================================================

-- USER 域同义词
CREATE SYNONYM QDITP_USER.BLACKLIST             FOR QDITP_PLAT.BLACKLIST
  ;
CREATE SYNONYM QDITP_USER.BLACKLIST_OPERATE_LOG FOR QDITP_PLAT.BLACKLIST_OPERATE_LOG
  ;
CREATE SYNONYM QDITP_USER.BLACKLIST_RELEASED    FOR QDITP_PLAT.BLACKLIST_RELEASED
  ;
CREATE SYNONYM QDITP_USER.TBL_STATION_INFO      FOR QDITP_PLAT.TBL_STATION_INFO
  ;
-- TODO: 其余 TBL_* 同义词，按代码实际引用补齐

-- TXN 域同义词
CREATE SYNONYM QDITP_TXN.BLACKLIST              FOR QDITP_PLAT.BLACKLIST
  ;
CREATE SYNONYM QDITP_TXN.BLACKLIST_OPERATE_LOG  FOR QDITP_PLAT.BLACKLIST_OPERATE_LOG
  ;
CREATE SYNONYM QDITP_TXN.BLACKLIST_RELEASED     FOR QDITP_PLAT.BLACKLIST_RELEASED
  ;
-- 交易域引用 USER 域的 APP_USER_PAY_CHANNEL（USER 拥有，TXN 只读）
GRANT SELECT ON QDITP_USER.APP_USER_PAY_CHANNEL TO QDITP_TXN
  ;
CREATE SYNONYM QDITP_TXN.APP_USER_PAY_CHANNEL   FOR QDITP_USER.APP_USER_PAY_CHANNEL
  ;
-- TODO: 其余 TBL_* 同义词，按代码实际引用补齐

-- RIDE 域同义词（STATION_INFO 退场，指向 PLAT 的 TBL_STATION_INFO）
CREATE SYNONYM QDITP_RIDE.STATION_INFO          FOR QDITP_PLAT.TBL_STATION_INFO
  ;
CREATE SYNONYM QDITP_RIDE.BLACKLIST             FOR QDITP_PLAT.BLACKLIST
  ;
-- TODO: 其余同义词，按代码实际引用补齐

-- =============================================================================
-- 自检
-- =============================================================================
PROMPT === itp_ro 对 USER_ITP_REG_INFO 的授权 ===
SELECT grantee, table_schema, table_name, privilege
FROM   dba_tab_privs
WHERE  grantee = 'ITP_RO'
  AND  table_name = 'USER_ITP_REG_INFO'
;

PROMPT === 各业务 schema 下的同义词 ===
SELECT owner, synonym_name, table_owner, table_name
FROM   dba_synonyms
WHERE  owner IN ('QDITP_USER','QDITP_TXN','QDITP_RIDE')
  AND  table_owner IN ('QDITP_PLAT','QDITP_USER')
ORDER BY owner, synonym_name
;

PROMPT === 跨 schema GRANT SELECT ===
SELECT grantee, owner, table_name, privilege
FROM   dba_tab_privs
WHERE  grantee IN ('QDITP_USER','QDITP_TXN','QDITP_RIDE')
  AND  privilege = 'SELECT'
  AND  owner IN ('QDITP_PLAT','QDITP_USER')
ORDER BY grantee, owner, table_name
;

PROMPT === 完成 ===
COMMIT;