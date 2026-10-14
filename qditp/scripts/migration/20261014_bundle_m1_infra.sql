-- =============================================================================
-- 青岛地铁 ITP 数据库按域分库 — 包 2：M1 基础建设（表空间 + schema + 共享暴露）
-- 配套文档：docs/ops/数据库按域分库迁移DDL草案.md §2 / §5
-- 状态：v1.6 执行包（仅供评审，严禁直接在生产库执行）
-- 目标：创建 4 个独立表空间 + 4 个 schema + itp_ro 网关只读账号 + 跨库同义词授权
-- 执行顺序：
--   1. create_tablespaces.sql     ← 4 TS + 4 TEMP + 4 schema + itp_ro + QDITP_MIGRATOR 角色
--   2. create_grants_synonyms.sql ← itp_ro 授权 + PLAT→USER/TXN/RIDE 共享
-- 前置依赖：包 1（M0 P0 修复）已完成（非强依赖，但建议先修分区）
-- 回滚：rollback_split.sql（逐级撤销）
-- =============================================================================

SET VERIFY OFF
SET FEEDBACK 1
SET LINESIZE 32767
SET PAGESIZE 100
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============================================================================
PROMPT 包 2：M1 基础建设 — 表空间 + schema + 共享暴露
PROMPT ============================================================================
PROMPT 执行前 MUST：
PROMPT 1. 用 SYSDBA 或具有 DBA 角色的账号连接（172.20.222.3:1521/AFCITPDB）
PROMPT 2. DBA 替换所有 '+RACDG/itp/' 路径前缀为真实 ASM / 裸设备路径
PROMPT 3. DBA 填入 5 个口令（4 个 schema + itp_ro），不入 VCS
PROMPT 4. 确认 RACDG 有足够空间（至少 4GB 余量）
PROMPT ============================================================================

-- =============================================================================
-- Step 1：创建 4 个独立业务表空间 + 4 个独立临时表空间
-- =============================================================================
-- 命名约定：ITP_<域>_TS / ITP_<域>_TEMP
-- 路径：+RACDG/itp/（DBA 替换为真实值）

CREATE TABLESPACE ITP_TXN_TS
  DATAFILE '+RACDG/itp/ITP_TXN_TS01.dbf' SIZE 2G AUTOEXTEND ON NEXT 256M MAXSIZE UNLIMITED
  ONLINE PERMANENT BLOCKSIZE 8192
  EXTENT MANAGEMENT LOCAL AUTOALLOCATE
  SEGMENT SPACE MANAGEMENT AUTO
  ;

CREATE TABLESPACE ITP_PLAT_TS
  DATAFILE '+RACDG/itp/ITP_PLAT_TS01.dbf' SIZE 1G AUTOEXTEND ON NEXT 128M MAXSIZE UNLIMITED
  ONLINE PERMANENT BLOCKSIZE 8192
  EXTENT MANAGEMENT LOCAL AUTOALLOCATE
  SEGMENT SPACE MANAGEMENT AUTO
  ;

CREATE TABLESPACE ITP_USER_TS
  DATAFILE '+RACDG/itp/ITP_USER_TS01.dbf' SIZE 512M AUTOEXTEND ON NEXT 128M MAXSIZE UNLIMITED
  ONLINE PERMANENT BLOCKSIZE 8192
  EXTENT MANAGEMENT LOCAL AUTOALLOCATE
  SEGMENT SPACE MANAGEMENT AUTO
  ;

CREATE TABLESPACE ITP_RIDE_TS
  DATAFILE '+RACDG/itp/ITP_RIDE_TS01.dbf' SIZE 512M AUTOEXTEND ON NEXT 128M MAXSIZE UNLIMITED
  ONLINE PERMANENT BLOCKSIZE 8192
  EXTENT MANAGEMENT LOCAL AUTOALLOCATE
  SEGMENT SPACE MANAGEMENT AUTO
  ;

CREATE TEMPORARY TABLESPACE ITP_TXN_TEMP
  TEMPFILE '+RACDG/itp/ITP_TXN_TEMP01.dbf' SIZE 512M AUTOEXTEND ON NEXT 64M MAXSIZE UNLIMITED
  EXTENT MANAGEMENT LOCAL UNIFORM SIZE 1M
  ;

CREATE TEMPORARY TABLESPACE ITP_USER_TEMP
  TEMPFILE '+RACDG/itp/ITP_USER_TEMP01.dbf' SIZE 256M AUTOEXTEND ON NEXT 64M MAXSIZE UNLIMITED
  EXTENT MANAGEMENT LOCAL UNIFORM SIZE 1M
  ;

CREATE TEMPORARY TABLESPACE ITP_RIDE_TEMP
  TEMPFILE '+RACDG/itp/ITP_RIDE_TEMP01.dbf' SIZE 256M AUTOEXTEND ON NEXT 64M MAXSIZE UNLIMITED
  EXTENT MANAGEMENT LOCAL UNIFORM SIZE 1M
  ;

CREATE TEMPORARY TABLESPACE ITP_PLAT_TEMP
  TEMPFILE '+RACDG/itp/ITP_PLAT_TEMP01.dbf' SIZE 256M AUTOEXTEND ON NEXT 64M MAXSIZE UNLIMITED
  EXTENT MANAGEMENT LOCAL UNIFORM SIZE 1M
  ;

-- =============================================================================
-- Step 2：创建 4 个 schema 用户（绑定独立默认表空间 + 独立临时表空间）
-- =============================================================================
DEFINE QDITP_USER_PWD = '<QDITP_USER_PWD>'
DEFINE QDITP_TXN_PWD   = '<QDITP_TXN_PWD>'
DEFINE QDITP_RIDE_PWD  = '<QDITP_RIDE_PWD>'
DEFINE QDITP_PLAT_PWD  = '<QDITP_PLAT_PWD>'

CREATE USER QDITP_USER  IDENTIFIED BY &QDITP_USER_PWD
  DEFAULT TABLESPACE ITP_USER_TS
  TEMPORARY TABLESPACE ITP_USER_TEMP
  ;
CREATE USER QDITP_TXN   IDENTIFIED BY &QDITP_TXN_PWD
  DEFAULT TABLESPACE ITP_TXN_TS
  TEMPORARY TABLESPACE ITP_TXN_TEMP
  ;
CREATE USER QDITP_RIDE  IDENTIFIED BY &QDITP_RIDE_PWD
  DEFAULT TABLESPACE ITP_RIDE_TS
  TEMPORARY TABLESPACE ITP_RIDE_TEMP
  ;
CREATE USER QDITP_PLAT  IDENTIFIED BY &QDITP_PLAT_PWD
  DEFAULT TABLESPACE ITP_PLAT_TS
  TEMPORARY TABLESPACE ITP_PLAT_TEMP
  ;

GRANT CREATE SESSION, CREATE TABLE, CREATE VIEW, CREATE SYNONYM,
      CREATE SEQUENCE, CREATE PROCEDURE, CREATE TRIGGER,
      CREATE MATERIALIZED VIEW, UNLIMITED TABLESPACE
  TO QDITP_USER, QDITP_TXN, QDITP_RIDE, QDITP_PLAT
  ;

-- =============================================================================
-- Step 3：网关只读账号 itp_ro（共享 USERS + TEMP）
-- =============================================================================
DEFINE ITP_RO_PWD = '<ITP_RO_PWD>'

CREATE USER itp_ro IDENTIFIED BY &ITP_RO_PWD
  ;
GRANT CREATE SESSION, CREATE SYNONYM TO itp_ro
  ;

-- =============================================================================
-- Step 4：迁移专用角色 QDITP_MIGRATOR（仅迁移窗口赋给执行账号）
-- =============================================================================
CREATE ROLE QDITP_MIGRATOR
  ;
GRANT CREATE ANY TABLE, DROP ANY TABLE, ALTER ANY TABLE, INSERT ANY TABLE,
      SELECT ANY TABLE, CREATE ANY INDEX, DROP ANY INDEX, CREATE ANY SYNONYM,
      CREATE ANY SEQUENCE, CREATE ANY TRIGGER, SELECT ANY DICTIONARY
  TO QDITP_MIGRATOR
  ;

-- =============================================================================
-- Step 5：itp_ro 对 USER_ITP_REG_INFO 的 SELECT 授权（分流网关依赖）
-- =============================================================================
GRANT SELECT ON QDITP_USER.USER_ITP_REG_INFO TO itp_ro
  ;
CREATE SYNONYM itp_ro.USER_ITP_REG_INFO FOR QDITP_USER.USER_ITP_REG_INFO
  ;

-- =============================================================================
-- Step 6：PLAT 公共引用表对 3 个业务库的 GRANT SELECT
-- =============================================================================
-- PLAT → USER
GRANT SELECT ON QDITP_PLAT.BLACKLIST               TO QDITP_USER
  , QDITP_PLAT.BLACKLIST_OPERATE_LOG   TO QDITP_USER
  , QDITP_PLAT.BLACKLIST_RELEASED      TO QDITP_USER
  , QDITP_PLAT.TBL_STATION_INFO        TO QDITP_USER
  ;
-- PLAT → TXN
GRANT SELECT ON QDITP_PLAT.BLACKLIST               TO QDITP_TXN
  , QDITP_PLAT.BLACKLIST_OPERATE_LOG   TO QDITP_TXN
  , QDITP_PLAT.BLACKLIST_RELEASED      TO QDITP_TXN
  ;
-- PLAT → RIDE
GRANT SELECT ON QDITP_PLAT.BLACKLIST               TO QDITP_RIDE
  , QDITP_PLAT.TBL_STATION_INFO        TO QDITP_RIDE
  ;

-- =============================================================================
-- Step 7：各业务 schema 下的跨库私有同义词
-- =============================================================================
-- USER 域同义词
CREATE SYNONYM QDITP_USER.BLACKLIST             FOR QDITP_PLAT.BLACKLIST
  , QDITP_USER.BLACKLIST_OPERATE_LOG FOR QDITP_PLAT.BLACKLIST_OPERATE_LOG
  , QDITP_USER.BLACKLIST_RELEASED    FOR QDITP_PLAT.BLACKLIST_RELEASED
  , QDITP_USER.TBL_STATION_INFO      FOR QDITP_PLAT.TBL_STATION_INFO
  ;
-- TXN 域同义词
CREATE SYNONYM QDITP_TXN.BLACKLIST              FOR QDITP_PLAT.BLACKLIST
  , QDITP_TXN.BLACKLIST_OPERATE_LOG FOR QDITP_PLAT.BLACKLIST_OPERATE_LOG
  , QDITP_TXN.BLACKLIST_RELEASED    FOR QDITP_PLAT.BLACKLIST_RELEASED
  ;
-- 交易域引用 USER 域的 APP_USER_PAY_CHANNEL
GRANT SELECT ON QDITP_USER.APP_USER_PAY_CHANNEL TO QDITP_TXN
  ;
CREATE SYNONYM QDITP_TXN.APP_USER_PAY_CHANNEL   FOR QDITP_USER.APP_USER_PAY_CHANNEL
  ;
-- RIDE 域同义词（STATION_INFO 退场，指向 PLAT 的 TBL_STATION_INFO）
CREATE SYNONYM QDITP_RIDE.STATION_INFO          FOR QDITP_PLAT.TBL_STATION_INFO
  , QDITP_RIDE.BLACKLIST             FOR QDITP_PLAT.BLACKLIST
  ;

-- =============================================================================
-- Step 8：自检（确认刚创建的对象）
-- =============================================================================
PROMPT === 表空间 ===
SELECT tablespace_name, status, content$, block_size, extent_management, segment_space_management
FROM   dba_tablespaces
WHERE  tablespace_name IN ('ITP_TXN_TS','ITP_PLAT_TS','ITP_USER_TS','ITP_RIDE_TS',
                           'ITP_TXN_TEMP','ITP_PLAT_TEMP','ITP_USER_TEMP','ITP_RIDE_TEMP')
ORDER BY tablespace_name
;

PROMPT === 用户 ===
SELECT username, default_tablespace, temporary_tablespace, created
FROM   dba_users
WHERE  username IN ('QDITP_USER','QDITP_TXN','QDITP_RIDE','QDITP_PLAT','itp_ro')
ORDER BY username
;

PROMPT === 角色 ===
SELECT role FROM dba_roles WHERE role = 'QDITP_MIGRATOR'
;

PROMPT === itp_ro 对 USER_ITP_REG_INFO 的授权 ===
SELECT grantee, table_schema, table_name, privilege
FROM   dba_tab_privs
WHERE  grantee = 'ITP_RO' AND table_name = 'USER_ITP_REG_INFO'
;

PROMPT === 跨 schema 同义词 ===
SELECT owner, synonym_name, table_owner, table_name
FROM   dba_synonyms
WHERE  owner IN ('QDITP_USER','QDITP_TXN','QDITP_RIDE','itp_ro')
ORDER BY owner, synonym_name
;

PROMPT === 完成 ===
COMMIT;

PROMPT ============================================================================
PROMPT 包 2 完成，下一步操作：
PROMPT ============================================================================
PROMPT 1. 确认以上自检输出符合预期（8 张 TS + 5 个用户 + 1 个角色 + 同义词）
PROMPT 2. 把 4 个 schema 口令安全分发到各模块 K8s secret + application.properties
PROMPT 3. 继续执行包 3（4 库 DDL 导出 + 逐表迁移）
PROMPT ============================================================================