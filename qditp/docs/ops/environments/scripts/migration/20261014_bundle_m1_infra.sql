-- =============================================================================
-- 青岛地铁 ITP 数据库按域分库 — 包 2：M1 基础建设（表空间 + schema + 共享暴露）
-- 配套文档：docs/ops/数据库按域分库迁移DDL草案.md §2 / §5
-- 状态：v1.8 执行包（2026-10-17 改 OMF 方案 A：DATAFILE/TEMPFILE 只写磁盘组 '+DATA'，
--       由 Oracle 解析到 PDB 的 <GUID>/DATAFILE|TEMPFILE/ 目录；仅供评审，执行前 MUST 人工确认）
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
PROMPT 1. 用 SYSDBA 连接**红岛生产 hditpdb PDB**（172.120.110.25:1521/hditpdb，NEVER 连 CDB 根）
PROMPT    —— 连 CDB 会把对象建到根容器，业务侧全部看不到
PROMPT 2. 路径走 OMF（方案 A，2026-10-17 定）：DATAFILE/TEMPFILE 只写 '+DATA'，
PROMPT    Oracle 自动落 hditpdb PDB 的 <GUID>/DATAFILE|TEMPFILE/ 目录（前置断言会强校验）
PROMPT 3. 口令已内联进 CREATE USER 语句（双引号定界；本目录 svn:ignore 不入 VCS；DBA 替换时避 & 和 "）
PROMPT 4. 确认 +DATA 磁盘组有足够空间（至少 4GB 余量，grid 用户 asmcmd lsdg 查）
PROMPT ============================================================================

-- =============================================================================
-- Step 0：前置断言（失败即 EXIT，防止对象建到 CDB 根 / OMF 未开）
-- =============================================================================
WHENEVER SQLERROR EXIT SQL.SQLCODE
-- ⚠ 2026-10-26 修：原写法 `IF (SELECT value FROM v$parameter ...) <> '+DATA'` 是非法 PL/SQL
--   （IF 条件不接受子查询），整块无法编译；配合上面的 WHENEVER SQLERROR EXIT 会以
--   **编译错误退出**，而不是执行预期断言 —— 防「建到 CDB 根」的守护实际是失效的。
--   改为 SELECT INTO 取到变量再比较。
DECLARE
  v_cfd v$parameter.value%TYPE;
BEGIN
  IF sys_context('USERENV','CON_NAME') <> 'HDTPDB' THEN
    raise_application_error(-20001,
      'MUST connect to hditpdb PDB via service name, NOT CDB root. Current container: '
      || sys_context('USERENV','CON_NAME'));
  END IF;
  SELECT value INTO v_cfd FROM v$parameter WHERE name = 'db_create_file_dest';
  IF NVL(v_cfd, '#NONE#') <> '+DATA' THEN
    raise_application_error(-20001,
      'OMF mode requires db_create_file_dest = +DATA. Current: ' || v_cfd);
  END IF;
END;
/
WHENEVER SQLERROR CONTINUE
PROMPT 前置检查通过：容器 = HDTPDB，db_create_file_dest = +DATA（OMF 生效）

-- =============================================================================
-- Step 1：创建 4 个独立业务表空间 + 4 个独立临时表空间
-- =============================================================================
-- 命名约定：ITP_<域>_TS / ITP_<域>_TEMP（表空间名；数据文件名由 OMF 生成，不手工指定）
-- 路径：OMF 模式（方案 A，2026-10-17 定）——DATAFILE/TEMPFILE 只写磁盘组 '+DATA'，
--       Oracle 按 db_create_file_dest 解析到当前容器（hditpdb PDB）的
--       +DATA/ITPCDB/<PDB_GUID>/DATAFILE|TEMPFILE/ 目录，无需关心 GUID 值；
--       Step 0 断言已强校验容器与 db_create_file_dest，换集群执行 MUST 重新核实

CREATE TABLESPACE ITP_TXN_TS
  DATAFILE '+DATA' SIZE 2G AUTOEXTEND ON NEXT 256M MAXSIZE UNLIMITED
  ONLINE PERMANENT BLOCKSIZE 8192
  EXTENT MANAGEMENT LOCAL AUTOALLOCATE
  SEGMENT SPACE MANAGEMENT AUTO
  ;

CREATE TABLESPACE ITP_PLAT_TS
  DATAFILE '+DATA' SIZE 1G AUTOEXTEND ON NEXT 128M MAXSIZE UNLIMITED
  ONLINE PERMANENT BLOCKSIZE 8192
  EXTENT MANAGEMENT LOCAL AUTOALLOCATE
  SEGMENT SPACE MANAGEMENT AUTO
  ;

CREATE TABLESPACE ITP_USER_TS
  DATAFILE '+DATA' SIZE 512M AUTOEXTEND ON NEXT 128M MAXSIZE UNLIMITED
  ONLINE PERMANENT BLOCKSIZE 8192
  EXTENT MANAGEMENT LOCAL AUTOALLOCATE
  SEGMENT SPACE MANAGEMENT AUTO
  ;

CREATE TABLESPACE ITP_RIDE_TS
  DATAFILE '+DATA' SIZE 512M AUTOEXTEND ON NEXT 128M MAXSIZE UNLIMITED
  ONLINE PERMANENT BLOCKSIZE 8192
  EXTENT MANAGEMENT LOCAL AUTOALLOCATE
  SEGMENT SPACE MANAGEMENT AUTO
  ;

CREATE TEMPORARY TABLESPACE ITP_TXN_TEMP
  TEMPFILE '+DATA' SIZE 512M AUTOEXTEND ON NEXT 64M MAXSIZE UNLIMITED
  EXTENT MANAGEMENT LOCAL UNIFORM SIZE 1M
  ;

CREATE TEMPORARY TABLESPACE ITP_USER_TEMP
  TEMPFILE '+DATA' SIZE 256M AUTOEXTEND ON NEXT 64M MAXSIZE UNLIMITED
  EXTENT MANAGEMENT LOCAL UNIFORM SIZE 1M
  ;

CREATE TEMPORARY TABLESPACE ITP_RIDE_TEMP
  TEMPFILE '+DATA' SIZE 256M AUTOEXTEND ON NEXT 64M MAXSIZE UNLIMITED
  EXTENT MANAGEMENT LOCAL UNIFORM SIZE 1M
  ;

CREATE TEMPORARY TABLESPACE ITP_PLAT_TEMP
  TEMPFILE '+DATA' SIZE 256M AUTOEXTEND ON NEXT 64M MAXSIZE UNLIMITED
  EXTENT MANAGEMENT LOCAL UNIFORM SIZE 1M
  ;

-- =============================================================================
-- Step 2：创建 4 个 schema 用户（绑定独立默认表空间 + 独立临时表空间）
-- =============================================================================
-- ⚠ 口令 MUST 由调用方以 sqlplus 位置参数传入：
--     sqlplus sys/<sys_pwd>@//<TGT_DSN> as sysdba @20261014_bundle_m1_infra.sql \
--            <user_pwd> <txn_pwd> <ride_pwd> <plat_pwd> <ro_pwd>
--   本文件不再含任何口令字面量（双引号定界，与最终执行版一致）。
--   ⚠⚠ 安全：本文件曾内联 5 个明文口令，并随提交 a67ccc2 / ebf27098e0 进入 git
--   历史、已推送远端（旧注释称「本目录 svn:ignore，不入 VCS」是错误陈述——
--   文档层归 git 仓库 qd/）。字面量已移除，但**历史里的旧口令 MUST 视为已泄露**，
--   根本处置是找 DBA 轮换，NEVER 把口令写回本仓库任何文件。
-- DBA 替换时 MUST 避免两种字符：
--   &  —— sqlplus 对任何语句都做替换变量展开，含 & 会弹「Enter value for」；
--   "  —— 双引号是口令定界符，口令内含 " 会截断导致语法错误（含 ' 无害）
-- 分发：K8s secret + 各模块 application.properties，NEVER 贴群/截图/邮件正文

CREATE USER QDITP_USER  IDENTIFIED BY "&1"
  DEFAULT TABLESPACE ITP_USER_TS
  TEMPORARY TABLESPACE ITP_USER_TEMP
  ;
CREATE USER QDITP_TXN   IDENTIFIED BY "&2"
  DEFAULT TABLESPACE ITP_TXN_TS
  TEMPORARY TABLESPACE ITP_TXN_TEMP
  ;
CREATE USER QDITP_RIDE  IDENTIFIED BY "&3"
  DEFAULT TABLESPACE ITP_RIDE_TS
  TEMPORARY TABLESPACE ITP_RIDE_TEMP
  ;
CREATE USER QDITP_PLAT  IDENTIFIED BY "&4"
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
CREATE USER itp_ro IDENTIFIED BY "&5"
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
-- ⚠️ 新生产 PDB 此时业务表尚未迁入（M3 才建表），Step 5 / Step 6 / Step 7 里的
--    GRANT ... ON <业务表> 都会报 ORA-00942（表不存在）而失败；脚本无 EXIT ON ERROR
--    会继续执行，不影响已创建的 TS/用户/同义词（同义词可指向不存在的对象，能建成）。
--    M3 数据迁移完成后 MUST 重跑 Step 5 + Step 6 + Step 7 的 GRANT 补授权。
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
WHERE  username IN ('QDITP_USER','QDITP_TXN','QDITP_RIDE','QDITP_PLAT','ITP_RO')
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
WHERE  owner IN ('QDITP_USER','QDITP_TXN','QDITP_RIDE','ITP_RO')
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