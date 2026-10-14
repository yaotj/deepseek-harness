-- =============================================================================
-- 青岛地铁 ITP 数据库按域分库 — 表空间 + 4 个 schema 创建脚本
-- 配套文档：docs/ops/数据库按域分库迁移DDL草案.md §2 + 附录 Step 0–4
-- 状态：草稿 v1.3（仅供评审，严禁直接在生产库执行）
-- 执行前 MUST：
--   1. DBA 替换所有 '+RACDG/itp/' 路径前缀为真实 ASM / 裸设备 / 数据文件路径
--   2. DBA 填入 4 个 <QDITP_*_PWD> + 1 个 <ITP_RO_PWD> 真实值
--   3. 确认当前会话具备 DBA / CREATE USER / CREATE TABLESPACE 权限（Step 0 切换）
-- 安全约束：唯一目标库 172.20.222.3:1521/AFCITPDB，无第二环境；
--           mcp_database_qd 无只读保护、删除不可回滚——本脚本只做创建，不做任何 DROP
-- =============================================================================

SET VERIFY OFF
SET FEEDBACK 1
SET LINESIZE 200
SET PAGESIZE 100
SET SERVEROUTPUT ON SIZE UNLIMITED

-- =============================================================================
-- Step 0：权限切换（用 SYSDBA 或具有 DBA 角色的账号执行）
-- =============================================================================
-- 执行前请替换下面这行注释为实际连接串，或在 SQL*Plus 启动时加：
--   sqlplus / as sysdba
-- 或
--   sqlplus sys/<sys_pwd>@//172.20.222.3:1521/AFCITPDB as sysdba
--
-- 权限自检（执行失败说明权限不足）：
SELECT USER, SYSDATE FROM dual;
SELECT privilege FROM dba_sys_privs WHERE grantee = USER OR grantee IN (SELECT granted_role FROM dba_role_privs WHERE grantee = USER) ORDER BY privilege;

-- =============================================================================
-- Step 1：创建 4 个独立业务表空间
--     顺序：TXN(最大) → PLAT → USER → RIDE（与容量规划热点对齐）
--     命名规则：ITP_<域>_TS<序号>.dbf，MUST 不与既有 ITP 表空间重名
-- =============================================================================
-- DBA 替换前缀 '+RACDG/itp/' → 真实 ASM 磁盘组 / 裸设备 / 数据文件路径
-- 示例 ASM：+RACDG/ITP/ITP_TXN_TS01.dbf
-- 示例 裸设备：/data1/oradata/ITP/ITP_TXN_TS01.dbf
-- 多盘均衡：热表空间（ITP_TXN_TS）可补建 ITP_TXN_TS02.dbf 落另一磁盘组

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

-- =============================================================================
-- Step 2：创建 4 个独立临时表空间（uniform size 1M）
--     路径建议与对应 TS 同源或同磁盘组
-- =============================================================================
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
-- Step 3：创建 4 个 schema 用户（绑定独立默认表空间 + 独立临时表空间）
-- =============================================================================
-- DBA 填入真实口令；严禁写入 VCS / 邮件正文，落地 K8s secret + 各模块 application.properties
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
-- Step 4：网关只读账号 itp_ro（共享 USERS + TEMP，不建独立表空间）
-- =============================================================================
DEFINE ITP_RO_PWD = '<ITP_RO_PWD>'

CREATE USER itp_ro IDENTIFIED BY &ITP_RO_PWD
  ;
GRANT CREATE SESSION, CREATE SYNONYM TO itp_ro
  ;

-- =============================================================================
-- Step 5：创建迁移专用角色 QDITP_MIGRATOR（仅迁移窗口赋给执行账号，完成后 REVOKE）
-- =============================================================================
CREATE ROLE QDITP_MIGRATOR
  ;
GRANT CREATE ANY TABLE, DROP ANY TABLE, ALTER ANY TABLE, INSERT ANY TABLE,
      SELECT ANY TABLE, CREATE ANY INDEX, DROP ANY INDEX, CREATE ANY SYNONYM,
      CREATE ANY SEQUENCE, CREATE ANY TRIGGER, SELECT ANY DICTIONARY
  TO QDITP_MIGRATOR
  ;
-- 用法：
--   GRANT QDITP_MIGRATOR TO <迁移执行账号>;
--   -- 执行迁移 ...
--   REVOKE QDITP_MIGRATOR FROM <迁移执行账号>;

-- =============================================================================
-- 自检：确认刚创建的 TS / 用户 / 角色
-- =============================================================================
PROMPT === 表空间 ===
SELECT tablespace_name, status, content$, logging, force_logging, block_size, extent_management, segment_space_management
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

PROMPT === 完成 ===
COMMIT;