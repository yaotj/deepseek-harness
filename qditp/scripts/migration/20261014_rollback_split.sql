-- =============================================================================
-- 青岛地铁 ITP 数据库按域分库 — 回滚脚本（M1 阶段）
-- 配套文档：docs/ops/数据库按域分库迁移DDL草案.md §10
-- 状态：草稿 v1.3（仅供评审，严禁直接在生产库执行）
-- 回滚时机：执行 §2 或 §M1 后发现问题需恢复原状
-- 注意：本脚本只做"撤销"，不 DROP 源数据；真正回滚需配合 §10 的"切连接串"
-- =============================================================================

SET VERIFY OFF
SET FEEDBACK 1
SET LINESIZE 200
SET PAGESIZE 100
SET SERVEROUTPUT ON SIZE UNLIMITED

-- =============================================================================
-- 回滚 Step A：撤销 itp_ro 授权 + 同义词
-- =============================================================================
-- 在 QDITP_USER 下执行：
REVOKE SELECT ON QDITP_USER.USER_ITP_REG_INFO FROM itp_ro
  ;
DROP SYNONYM itp_ro.USER_ITP_REG_INFO
  ;

-- =============================================================================
-- 回滚 Step B：撤销跨 schema GRANT SELECT（按 §4 反向）
-- =============================================================================
-- 在对应拥有 schema 下执行（PLAT / USER）

-- PLAT → USER / TXN / RIDE
REVOKE SELECT ON QDITP_PLAT.BLACKLIST               FROM QDITP_USER
  , QDITP_TXN
  , QDITP_RIDE
  ;
REVOKE SELECT ON QDITP_PLAT.BLACKLIST_OPERATE_LOG   FROM QDITP_USER
  , QDITP_TXN
  , QDITP_RIDE
  ;
REVOKE SELECT ON QDITP_PLAT.BLACKLIST_RELEASED      FROM QDITP_USER
  , QDITP_TXN
  , QDITP_RIDE
  ;
REVOKE SELECT ON QDITP_PLAT.TBL_STATION_INFO        FROM QDITP_USER
  , QDITP_RIDE
  ;
-- TODO: 其余 PLAT 表反向 REVOKE

-- USER → TXN（APP_USER_PAY_CHANNEL）
REVOKE SELECT ON QDITP_USER.APP_USER_PAY_CHANNEL FROM QDITP_TXN
  ;

-- =============================================================================
-- 回滚 Step C：撤销跨 schema 同义词（在各消费 schema 下执行）
-- =============================================================================
-- QDITP_USER
DROP SYNONYM QDITP_USER.BLACKLIST
  , QDITP_USER.BLACKLIST_OPERATE_LOG
  , QDITP_USER.BLACKLIST_RELEASED
  , QDITP_USER.TBL_STATION_INFO
  ;
-- TODO: 其余 USER 域同义词

-- QDITP_TXN
DROP SYNONYM QDITP_TXN.BLACKLIST
  , QDITP_TXN.BLACKLIST_OPERATE_LOG
  , QDITP_TXN.BLACKLIST_RELEASED
  , QDITP_TXN.APP_USER_PAY_CHANNEL
  ;
-- TODO: 其余 TXN 域同义词

-- QDITP_RIDE
DROP SYNONYM QDITP_RIDE.STATION_INFO
  , QDITP_RIDE.BLACKLIST
  ;
-- TODO: 其余 RIDE 域同义词

-- =============================================================================
-- 回滚 Step D：迁移专用角色 QDITP_MIGRATOR 收回（如有已赋）
-- =============================================================================
-- 在 DBA 或 SYSDBA 下执行：
-- REVOKE QDITP_MIGRATOR FROM <迁移执行账号>;
-- DROP ROLE QDITP_MIGRATOR;   -- 仅在确认无任何账号持有该角色时执行

-- =============================================================================
-- 回滚 Step E：表空间 + schema 删除（最后兜底，MUST 确认无任何对象依赖）
-- =============================================================================
-- 警告：此步骤会删除整 schema 及下属所有对象，MUST 在迁移失败且 CTAS 快照确认安全后执行
-- 顺序：先删同义词 → 再删 schema（CASCADE CONSTRAINTS）→ 再删表空间（INCLUDING CONTENTS AND DATAFILES）
--
-- 建议仅在 M4 全量校验失败且决定放弃本次分库时执行：
-- DROP USER QDITP_USER CASCADE;
-- DROP USER QDITP_TXN   CASCADE;
-- DROP USER QDITP_RIDE  CASCADE;
-- DROP USER QDITP_PLAT  CASCADE;
-- DROP USER itp_ro CASCADE;
-- DROP TABLESPACE ITP_TXN_TS       INCLUDING CONTENTS AND DATAFILES;
-- DROP TABLESPACE ITP_PLAT_TS      INCLUDING CONTENTS AND DATAFILES;
-- DROP TABLESPACE ITP_USER_TS      INCLUDING CONTENTS AND DATAFILES;
-- DROP TABLESPACE ITP_RIDE_TS      INCLUDING CONTENTS AND DATAFILES;
-- DROP TABLESPACE ITP_TXN_TEMP     INCLUDING CONTENTS AND DATAFILES;
-- DROP TABLESPACE ITP_PLAT_TEMP    INCLUDING CONTENTS AND DATAFILES;
-- DROP TABLESPACE ITP_USER_TEMP    INCLUDING CONTENTS AND DATAFILES;
-- DROP TABLESPACE ITP_RIDE_TEMP    INCLUDING CONTENTS AND DATAFILES;
-- DROP ROLE QDITP_MIGRATOR;

-- =============================================================================
-- 自检：回滚后确认无残留
-- =============================================================================
PROMPT === 待删 schema（应 0 行）===
SELECT username FROM dba_users
WHERE username IN ('QDITP_USER','QDITP_TXN','QDITP_RIDE','QDITP_PLAT','itp_ro')
;

PROMPT === 待删表空间（应 0 行）===
SELECT tablespace_name FROM dba_tablespaces
WHERE tablespace_name IN ('ITP_TXN_TS','ITP_PLAT_TS','ITP_USER_TS','ITP_RIDE_TS',
                          'ITP_TXN_TEMP','ITP_PLAT_TEMP','ITP_USER_TEMP','ITP_RIDE_TEMP')
;

PROMPT === 完成（回滚后请人工确认业务可正常连接原 QDITP schema）===
COMMIT;