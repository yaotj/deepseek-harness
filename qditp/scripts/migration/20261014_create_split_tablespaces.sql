-- =============================================================================
-- 青岛地铁 ITP 数据库按域分库 — 表空间 + 4 个 schema 创建脚本
-- 配套文档：docs/ops/数据库按域分库迁移DDL草案.md §2 + 附录 Step 0–4
-- 状态：v1.5 草稿（2026-10-17 改 OMF 方案 A：DATAFILE/TEMPFILE 只写磁盘组 '+DATA'；
--       由 Oracle 解析到 PDB 的 <GUID>/DATAFILE|TEMPFILE/ 目录；仅供评审，执行前 MUST 人工确认）
-- 执行前 MUST：
--   1. 路径走 OMF（方案 A）：DATAFILE/TEMPFILE 只写 '+DATA'，前置断言强校验
--      db_create_file_dest = '+DATA' 与容器 = HDTPDB，不满足即 EXIT
--   2. 口令已按推荐值内联进 CREATE USER 语句（双引号定界；本目录 svn:ignore 不入 VCS；
--      DBA 替换时避 & 和 "）
--   3. 确认当前会话具备 DBA / CREATE USER / CREATE TABLESPACE 权限（Step 0 切换）
-- 目标环境：红岛生产 HDTPDB（172.120.110.25:1521/hditpdb，MUST 连 PDB 服务名、NEVER 连 CDB 根）；
--           与测试库 172.20.222.3:1521/AFCITPDB 为两套环境，NEVER 混用
-- 安全约束：mcp_database_qd 无只读保护、删除不可回滚——本脚本只做创建，不做任何 DROP
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
-- 或（连红岛生产 hditpdb PDB，NEVER 连 CDB 根）：
--   sqlplus sys/<sys_pwd>@//172.120.110.25:1521/hditpdb as sysdba
--
-- 权限自检（执行失败说明权限不足）：
SELECT USER, SYSDATE FROM dual;
SELECT privilege FROM dba_sys_privs WHERE grantee = USER OR grantee IN (SELECT granted_role FROM dba_role_privs WHERE grantee = USER) ORDER BY privilege;

-- =============================================================================
-- Step 0b：前置断言（失败即 EXIT，防止对象建到 CDB 根 / OMF 未开）
-- =============================================================================
WHENEVER SQLERROR EXIT SQL.SQLCODE
BEGIN
  IF sys_context('USERENV','CON_NAME') <> 'HDTPDB' THEN
    raise_application_error(-20001,
      'MUST connect to hditpdb PDB via service name, NOT CDB root. Current container: '
      || sys_context('USERENV','CON_NAME'));
  END IF;
  IF (SELECT value FROM v$parameter WHERE name = 'db_create_file_dest') <> '+DATA' THEN
    raise_application_error(-20001,
      'OMF mode requires db_create_file_dest = +DATA. Current: '
      || (SELECT value FROM v$parameter WHERE name = 'db_create_file_dest'));
  END IF;
END;
/
WHENEVER SQLERROR CONTINUE
PROMPT 前置检查通过：容器 = HDTPDB，db_create_file_dest = +DATA（OMF 生效）

-- =============================================================================
-- Step 1：创建 4 个独立业务表空间
--     顺序：TXN(最大) → PLAT → USER → RIDE（与容量规划热点对齐）
--     命名规则：表空间名 ITP_<域>_TS；数据文件名由 OMF 生成，不手工指定
-- =============================================================================
-- 路径：OMF 模式（方案 A，2026-10-17 定）——DATAFILE 只写磁盘组 '+DATA'，
--       Oracle 自动落 hditpdb PDB 的 +DATA/ITPCDB/<PDB_GUID>/DATAFILE/ 目录；
--       原占位 '+RACDG/itp/' 与平级 '+DATA/ITPCDB/' 写法均已作废，NEVER 回退使用
-- 多盘均衡：热表空间（ITP_TXN_TS）可补建第二份 DATAFILE '+DATA' SIZE ... 落另一磁盘组

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

-- =============================================================================
-- Step 2：创建 4 个独立临时表空间（uniform size 1M）
--     路径与对应 TS 同源：TEMPFILE 只写 '+DATA'，OMF 落 PDB 的 <GUID>/TEMPFILE/ 目录
-- =============================================================================
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
-- Step 3：创建 4 个 schema 用户（绑定独立默认表空间 + 独立临时表空间）
-- =============================================================================
-- 口令为 2026-10-17 推荐值（风格同部署方案 itp!#951；本目录在 svn:ignore，不入 VCS）
-- 已内联进下方 CREATE USER（无变量定义；双引号定界，与最终执行版一致）
-- DBA 替换时 MUST 避免两种字符：
--   &  —— sqlplus 对任何语句都做替换变量展开，含 & 会弹「Enter value for」；
--   "  —— 双引号是口令定界符，口令内含 " 会截断导致语法错误（含 ' 无害）
-- 分发：K8s secret + 各模块 application.properties，NEVER 贴群/截图/邮件正文

CREATE USER QDITP_USER  IDENTIFIED BY "itp!#usr4726"
  DEFAULT TABLESPACE ITP_USER_TS
  TEMPORARY TABLESPACE ITP_USER_TEMP
  ;
CREATE USER QDITP_TXN   IDENTIFIED BY "itp!#txn8153"
  DEFAULT TABLESPACE ITP_TXN_TS
  TEMPORARY TABLESPACE ITP_TXN_TEMP
  ;
CREATE USER QDITP_RIDE  IDENTIFIED BY "itp!#rd2947"
  DEFAULT TABLESPACE ITP_RIDE_TS
  TEMPORARY TABLESPACE ITP_RIDE_TEMP
  ;
CREATE USER QDITP_PLAT  IDENTIFIED BY "itp!#plt6308"
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
CREATE USER itp_ro IDENTIFIED BY "itp!#ro1592"
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
WHERE  username IN ('QDITP_USER','QDITP_TXN','QDITP_RIDE','QDITP_PLAT','ITP_RO')
ORDER BY username
;

PROMPT === 角色 ===
SELECT role FROM dba_roles WHERE role = 'QDITP_MIGRATOR'
;

PROMPT === 完成 ===
COMMIT;