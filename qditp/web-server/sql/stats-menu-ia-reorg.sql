-- 综管台信息架构（IA）重组：统计页独立成组 + 两处不可达菜单归位（Oracle，幂等执行）
--
-- 依据（2026-11-04 现查测试库 AFCITPDB，NEVER 按本文件推导线上现状）：
--   SELECT MAX(MENU_ID), COUNT(*) FROM SYS_MENU;  → 2100 / 148
--   · 2097「对账文件上传」PARENT_ID=2035，而 2035 在库内不存在
--     （recon-upload-file-menu.sql 假设的「交易运营」从未建过）⇒ RuoYi buildMenus 从 parent_id=0 递归，
--     该页在菜单树上整枝丢失，只能靠 SPA 直访。
--   · 107「通知公告」PARENT_ID=2032「信息管理」，而 2032 STATUS='1'（禁用）⇒ 同样整枝不可达。
--   · 2086「注册量统计」/ 2084「离线码统计」挂在 2071「用户管理」下，与 CRUD 页混排。
--
-- 执行前 MUST 先备份（本文件第 0 节），还原脚本见文件末尾「还原」。
-- 菜单变更只在**重新登录后**生效：路由由 getRouters 在登录时按 sys_menu 生成。

-- ================== 0. 备份 ==================
-- 幂等建表：已存在则跳过（ORA-00955 = 对象名已存在）。
BEGIN
    EXECUTE IMMEDIATE 'CREATE TABLE BAK_SYS_MENU_20261104 AS SELECT * FROM SYS_MENU';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -955 THEN RAISE; END IF;
END;
/
BEGIN
    EXECUTE IMMEDIATE 'CREATE TABLE BAK_SYS_ROLE_MENU_20261104 AS SELECT * FROM SYS_ROLE_MENU';
EXCEPTION WHEN OTHERS THEN IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

-- ================== 1. 新增「统计分析」目录 ==================
-- 统计页与增删改查页的心智模型不同；P2 若业主确认补建对账/清分报表，也归这一组。
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 2101, '统计分析', 0, 17, 'stats', NULL, '', 'StatsAnalysis', 1, 0, 'M', '0', '0', NULL, 'chart', 'admin', SYSDATE, '', NULL, '运营统计页统一入口'
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2101);

-- 注册量统计 / 离线码统计 迁入（menu_id 不变 ⇒ 已有 sys_role_menu 授权继续有效，只换父级）
UPDATE sys_menu SET parent_id = 2101, order_num = 1, update_by = 'admin', update_time = SYSDATE
 WHERE menu_id = 2086 AND parent_id != 2101;
UPDATE sys_menu SET parent_id = 2101, order_num = 2, update_by = 'admin', update_time = SYSDATE
 WHERE menu_id = 2084 AND parent_id != 2101;

-- 把「统计分析」目录授给已经能看到任一统计页的角色，否则迁移对有角色的账号等于把页面藏起来。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT source.role_id, 2101 FROM (SELECT DISTINCT role_id FROM sys_role_menu WHERE menu_id IN (2084, 2086)) source
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu target WHERE target.role_id = source.role_id AND target.menu_id = 2101);

-- ================== 2. 对账文件上传归位到「交易管理」(2077) ==================
UPDATE sys_menu SET parent_id = 2077,
       order_num = (SELECT NVL(MAX(order_num), 0) + 1 FROM sys_menu sibling WHERE sibling.parent_id = 2077),
       update_by = 'admin', update_time = SYSDATE
 WHERE menu_id = 2097 AND parent_id != 2077;

-- 2097 已授给 role_id=2，但其新父级 2077 未授给该角色 ⇒ 必须补，否则该角色迁移后反而看不到。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT source.role_id, source.parent_id FROM (
    SELECT rm.role_id AS role_id, m.parent_id AS parent_id
      FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id = rm.menu_id
     WHERE m.menu_id = 2097
) source
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu target WHERE target.role_id = source.role_id AND target.menu_id = source.parent_id);

-- ================== 3. 通知公告从禁用父菜单下移到「综管台管理」(1) ==================
UPDATE sys_menu SET parent_id = 1, order_num = 10, update_by = 'admin', update_time = SYSDATE
 WHERE menu_id = 107 AND parent_id != 1;

-- 同上：补齐父级授权，保证已看到 107 的角色迁移后仍能看到。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT source.role_id, source.parent_id FROM (
    SELECT rm.role_id AS role_id, m.parent_id AS parent_id
      FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id = rm.menu_id
     WHERE m.menu_id = 107
) source
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu target WHERE target.role_id = source.role_id AND target.menu_id = source.parent_id);

COMMIT;

-- ================== 自检（执行后逐条核对，任一不符即停手还原） ==================
-- 1) 三个落点正确：2086/2084 → 2101，2097 → 2077，107 → 1，且 2101 是 M 型可见目录
-- SELECT menu_id, menu_name, parent_id, order_num, menu_type, visible, status FROM sys_menu
--  WHERE menu_id IN (2101, 2086, 2084, 2097, 107) ORDER BY menu_id;
-- 2) 不存在指向缺失父级的 M/C 菜单（应 0 行）
-- SELECT m.menu_id, m.menu_name, m.parent_id FROM sys_menu m
--   WHERE m.menu_type IN ('M','C') AND m.parent_id != 0
--     AND NOT EXISTS (SELECT 1 FROM sys_menu p WHERE p.menu_id = m.parent_id);
-- 3) 不存在「子菜单可见但父目录禁用」的枝（应 0 行）
-- SELECT m.menu_id, m.menu_name FROM sys_menu m
--   WHERE m.menu_type = 'C' AND m.visible = '0' AND m.status = '0'
--     AND EXISTS (SELECT 1 FROM sys_menu p WHERE p.menu_id = m.parent_id AND p.status = '1');

-- ================== 还原（按备份表回灌，逐条执行） ==================
-- UPDATE sys_menu m SET (parent_id, order_num, update_by, update_time) =
--        (SELECT b.parent_id, b.order_num, b.update_by, b.update_time FROM BAK_SYS_MENU_20261104 b WHERE b.menu_id = m.menu_id)
--  WHERE m.menu_id IN (2086, 2084, 2097, 107);
-- DELETE FROM sys_menu WHERE menu_id = 2101;
-- DELETE FROM sys_role_menu WHERE menu_id = 2101;
-- COMMIT;
-- 兜底整表还原：TRUNCATE TABLE SYS_MENU; INSERT INTO SYS_MENU SELECT * FROM BAK_SYS_MENU_20261104; COMMIT;
-- （sys_role_menu 同理用 BAK_SYS_ROLE_MENU_20261104）
