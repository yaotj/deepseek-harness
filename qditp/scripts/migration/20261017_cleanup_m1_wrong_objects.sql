-- =============================================================================
-- 青岛地铁 ITP 分库迁移 — M1 误建对象清理脚本（表空间 + 依赖用户/角色）
-- 用途：M1（20261014_bundle_m1_infra.sql）执行后对象建错位置/路径，清理后重跑
-- 目标环境：红岛生产 HDTPDB（脚本会先报告当前所在容器，不在目标处删了也白删）
-- 状态：v1.0 · 2026-10-17 · 配套：20261014_bundle_m1_infra.sql
--
-- 🔴 生产纪律（执行前 MUST 读完）：
--   1. DROP ... INCLUDING CONTENTS AND DATAFILES 会连 ASM 数据文件一起删，不可撤销；
--      生产全程有数据库审计（172.120.250.232）+ 堡垒机留痕，MUST 人工复核后执行；
--   2. 内置安全闸：任一目标表空间含段、或任一目标用户拥有非同义词对象 ⇒ 直接报错
--      中止，NEVER 带着数据盲删；确有数据 MUST 先按迁移手册做 BAK_<表>_<日期> CTAS
--      备份并人工评估后再决定；
--   3. 对象建在了 CDB$ROOT（连错容器）时，在 PDB 里跑本脚本只会看到「无对象」——
--      Phase 0 的 CON_NAME 会告诉你在哪；在 CDB 根误建就 MUST 用 SYS 连根重跑；
--   4. 清理完重跑 M1：连 hditpdb PDB 服务名（NEVER 连 CDB 根），路径走 OMF。
-- =============================================================================

SET VERIFY OFF
SET FEEDBACK 1
SET LINESIZE 200
SET PAGESIZE 100
SET SERVEROUTPUT ON SIZE UNLIMITED

-- =============================================================================
-- Phase 0：当前位置 + 目标对象盘点（只读，看清楚再决定删不删）
-- =============================================================================
PROMPT ============================================================================
PROMPT Phase 0：当前位置与盘点（只读）
PROMPT ============================================================================
SELECT sys_context('USERENV','DB_UNIQUE_NAME') AS db,
       sys_context('USERENV','CON_NAME')       AS container,
       sys_context('USERENV','INSTANCE')       AS instance
FROM dual;

PROMPT === 目标表空间（0 行 = 本容器没有；若建在 CDB 根，用 SYS 连根重跑本脚本）===
SELECT tablespace_name, status, contents
FROM   dba_tablespaces
WHERE  tablespace_name IN ('ITP_TXN_TS','ITP_PLAT_TS','ITP_USER_TS','ITP_RIDE_TS',
                           'ITP_TXN_TEMP','ITP_PLAT_TEMP','ITP_USER_TEMP','ITP_RIDE_TEMP',
                           'ITP_RO_TS')
ORDER BY tablespace_name;

PROMPT === 目标表空间的数据文件（确认文件实际落点，OMF/旧平级路径都能看到）===
SELECT tablespace_name, file_name
FROM   dba_data_files
WHERE  tablespace_name IN ('ITP_TXN_TS','ITP_PLAT_TS','ITP_USER_TS','ITP_RIDE_TS',
                           'ITP_RO_TS')
ORDER BY tablespace_name, file_name;

PROMPT === 目标用户（重跑 M1 前必须删掉，否则 CREATE USER 报 ORA-01920）===
SELECT username, default_tablespace, temporary_tablespace
FROM   dba_users
WHERE  username IN ('QDITP_USER','QDITP_TXN','QDITP_RIDE','QDITP_PLAT','ITP_RO')
ORDER BY username;

PROMPT === 角色 ===
SELECT role FROM dba_roles WHERE role = 'QDITP_MIGRATOR';

-- =============================================================================
-- 人工确认闸：必须输入 YES 才会继续，未确认则原样退出（未做任何删除）
-- =============================================================================
ACCEPT _go CHAR PROMPT '生产清理不可撤销，确认执行请输入 YES（其他任何输入都会中止）： '
BEGIN
  IF '&_go' <> 'YES' THEN
    raise_application_error(-20099, '已中止：未输入 YES，脚本退出，未做任何删除');
  END IF;
END;
/

-- =============================================================================
-- Phase 1：安全闸（有数据即中止）
-- =============================================================================
DECLARE
  v_seg  NUMBER;
  v_objs NUMBER;
BEGIN
  -- 1a. 目标表空间内的段数（含任何 owner；>0 ⇒ 有对象/数据，停）
  FOR r IN (SELECT tablespace_name FROM dba_tablespaces
            WHERE tablespace_name IN ('ITP_TXN_TS','ITP_PLAT_TS','ITP_USER_TS','ITP_RIDE_TS',
                                      'ITP_TXN_TEMP','ITP_PLAT_TEMP','ITP_USER_TEMP','ITP_RIDE_TEMP',
                                      'ITP_RO_TS'))
  LOOP
    SELECT COUNT(*) INTO v_seg FROM dba_segments WHERE tablespace_name = r.tablespace_name;
    IF v_seg > 0 THEN
      raise_application_error(-20010,
        '表空间 ' || r.tablespace_name || ' 含 ' || v_seg || ' 个段，MUST 先 CTAS 备份并人工评估，NEVER 直接删');
    END IF;
  END LOOP;

  -- 1b. 目标用户是否拥有非同义词对象（表/索引等 ⇒ M3 可能已迁数据，停）
  FOR u IN (SELECT username FROM dba_users
            WHERE username IN ('QDITP_USER','QDITP_TXN','QDITP_RIDE','QDITP_PLAT','ITP_RO'))
  LOOP
    SELECT COUNT(*) INTO v_objs FROM dba_objects
    WHERE owner = u.username AND object_type <> 'SYNONYM';
    IF v_objs > 0 THEN
      raise_application_error(-20011,
        '用户 ' || u.username || ' 拥有 ' || v_objs || ' 个非同义词对象，MUST 先备份评估，NEVER 直接删');
    END IF;
  END LOOP;

  DBMS_OUTPUT.PUT_LINE('[PASS] 安全闸通过：目标表空间均无段、目标用户均无非同义词对象');
END;
/

-- =============================================================================
-- Phase 2：删用户（CASCADE 连各自 schema 内的同义词一起删；先用户后表空间）
-- =============================================================================
BEGIN
  FOR u IN (SELECT username FROM dba_users
            WHERE username IN ('QDITP_USER','QDITP_TXN','QDITP_RIDE','QDITP_PLAT','ITP_RO')
            ORDER BY username)
  LOOP
    EXECUTE IMMEDIATE 'DROP USER "' || u.username || '" CASCADE';
    DBMS_OUTPUT.PUT_LINE('[DROP USER] ' || u.username);
  END LOOP;
END;
/

-- =============================================================================
-- Phase 3：删角色（先收回所有持有者，避免 ORA-01932）
-- =============================================================================
BEGIN
  FOR g IN (SELECT grantee FROM dba_role_privs WHERE granted_role = 'QDITP_MIGRATOR')
  LOOP
    EXECUTE IMMEDIATE 'REVOKE QDITP_MIGRATOR FROM "' || g.grantee || '"';
    DBMS_OUTPUT.PUT_LINE('[REVOKE] QDITP_MIGRATOR FROM ' || g.grantee);
  END LOOP;
  FOR r IN (SELECT role FROM dba_roles WHERE role = 'QDITP_MIGRATOR')
  LOOP
    EXECUTE IMMEDIATE 'DROP ROLE QDITP_MIGRATOR';
    DBMS_OUTPUT.PUT_LINE('[DROP ROLE] QDITP_MIGRATOR');
  END LOOP;
END;
/

-- =============================================================================
-- Phase 4：删表空间（INCLUDING CONTENTS AND DATAFILES ⇒ ASM 文件一并删除）
-- =============================================================================
BEGIN
  FOR t IN (SELECT tablespace_name FROM dba_tablespaces
            WHERE tablespace_name IN ('ITP_TXN_TS','ITP_PLAT_TS','ITP_USER_TS','ITP_RIDE_TS',
                                      'ITP_TXN_TEMP','ITP_PLAT_TEMP','ITP_USER_TEMP','ITP_RIDE_TEMP',
                                      'ITP_RO_TS')
            ORDER BY tablespace_name)
  LOOP
    EXECUTE IMMEDIATE 'DROP TABLESPACE "' || t.tablespace_name ||
                      '" INCLUDING CONTENTS AND DATAFILES';
    DBMS_OUTPUT.PUT_LINE('[DROP TABLESPACE] ' || t.tablespace_name);
  END LOOP;
END;
/

-- =============================================================================
-- Phase 5：验证（应全部 0 行；ASM 文件删除情况见下方 asmcmd 提示）
-- =============================================================================
PROMPT ============================================================================
PROMPT Phase 5：清理后验证（应全部 0 行）
PROMPT ============================================================================
PROMPT === 表空间残留（应 0 行）===
SELECT tablespace_name FROM dba_tablespaces WHERE tablespace_name LIKE 'ITP!_%' ESCAPE '!';

PROMPT === 用户残留（应 0 行）===
SELECT username FROM dba_users
WHERE username IN ('QDITP_USER','QDITP_TXN','QDITP_RIDE','QDITP_PLAT','ITP_RO');

PROMPT === 数据文件残留（应 0 行）===
SELECT file_name FROM dba_data_files WHERE tablespace_name LIKE 'ITP!_%' ESCAPE '!';

PROMPT === 临时文件残留（应 0 行）===
SELECT file_name FROM dba_temp_files WHERE tablespace_name LIKE 'ITP!_%' ESCAPE '!';

PROMPT === 角色残留（应 0 行）===
SELECT role FROM dba_roles WHERE role = 'QDITP_MIGRATOR';

COMMIT;

PROMPT ============================================================================
PROMPT 清理完成。收尾确认（DBA）：
PROMPT   1. 若旧版平级路径（+DATA/ITPCDB/ITP_*.dbf）建过文件：grid 用户
PROMPT      asmcmd ls +DATA/ITPCDB   确认无残留 ITP_*.dbf 别名
PROMPT      （AND DATAFILES 正常会一并删掉；有残留用 asmcmd rm 清）
PROMPT   2. 重跑 M1：连 hditpdb PDB 服务名（NEVER 连 CDB 根）
PROMPT      sqlplus sys/<pwd>@//172.120.110.25:1521/hditpdb as sysdba
PROMPT      @scripts/migration/20261014_bundle_m1_infra.sql
PROMPT      （路径走 OMF；Step 0 断言会校验容器与 db_create_file_dest）
PROMPT ============================================================================

-- =============================================================================
-- 附录：直执行语句清单（无 PL/SQL 版，2026-10-17 用户要求）
-- 适用：已实测确认误建对象为下列白名单（dba_data_files 显示 4 个 ITP_*_TS
--       平级建于 +DATA/ITPCDB/，PDB 正主目录为 GUID 5DB3BAB10F5C0758E063156E78ACB0D0）。
-- 与上方分相版等价，但无安全闸——执行前 MUST 先跑 ① 自检 + 人工确认。
-- =============================================================================
--
-- ① 删前自检（应无输出；有行 = 有数据，停手先备份）
-- SELECT tablespace_name, COUNT(*) AS seg_count FROM dba_segments
-- WHERE tablespace_name IN ('ITP_TXN_TS','ITP_PLAT_TS','ITP_USER_TS','ITP_RIDE_TS')
-- GROUP BY tablespace_name;
--
-- ② 删用户（先用户后表空间；ORA-01918 = 用户不存在，忽略继续）
-- DROP USER QDITP_USER CASCADE;
-- DROP USER QDITP_TXN  CASCADE;
-- DROP USER QDITP_RIDE CASCADE;
-- DROP USER QDITP_PLAT CASCADE;
-- DROP USER itp_ro     CASCADE;
--
-- ③ 删角色（ORA-01932 = 还被持有，先 REVOKE）
-- DROP ROLE QDITP_MIGRATOR;
--
-- ④ 删 4 个误建数据表空间（AND DATAFILES 连 ASM 平级文件一起删，NEVER 省）
-- DROP TABLESPACE ITP_TXN_TS  INCLUDING CONTENTS AND DATAFILES;
-- DROP TABLESPACE ITP_PLAT_TS INCLUDING CONTENTS AND DATAFILES;
-- DROP TABLESPACE ITP_USER_TS INCLUDING CONTENTS AND DATAFILES;
-- DROP TABLESPACE ITP_RIDE_TS INCLUDING CONTENTS AND DATAFILES;
--
-- ⑤ 删 4 个临时表空间（dba_data_files 看不到 temp；不存在报 ORA-00959 忽略）
-- DROP TABLESPACE ITP_TXN_TEMP  INCLUDING CONTENTS AND DATAFILES;
-- DROP TABLESPACE ITP_USER_TEMP INCLUDING CONTENTS AND DATAFILES;
-- DROP TABLESPACE ITP_RIDE_TEMP INCLUDING CONTENTS AND DATAFILES;
-- DROP TABLESPACE ITP_PLAT_TEMP INCLUDING CONTENTS AND DATAFILES;
--
-- ⑥ 删后验证（应全部 0 行）
-- SELECT tablespace_name FROM dba_tablespaces WHERE tablespace_name LIKE 'ITP!_%' ESCAPE '!';
-- SELECT username FROM dba_users WHERE username IN ('QDITP_USER','QDITP_TXN','QDITP_RIDE','QDITP_PLAT','ITP_RO');
-- SELECT file_name FROM dba_data_files WHERE tablespace_name LIKE 'ITP!_%' ESCAPE '!';
-- SELECT role FROM dba_roles WHERE role = 'QDITP_MIGRATOR';
