-- 逻辑卡号卡池运营菜单（Oracle，幂等执行）。挂在「系统监控」(menu_id=2) 下。
-- 注意：2071~2073 已被「用户管理」占用，此处改用 2078~2080。
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 2078, '逻辑卡号管理', 2, 9, 'card-pool', 'trans/card-pool/index', '', 'CardPoolManagement', 1, 0, 'C', '0', '0', 'trans:card-pool:query', 'tickets', 'admin', SYSDATE, '', NULL, '按票种查看逻辑卡号库存和批次'
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2078);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 2079, '逻辑卡号申请批次', 2078, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'trans:card-pool:apply', '#', 'admin', SYSDATE, '', NULL, '' FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2079);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 2080, '逻辑卡号重试导入', 2078, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'trans:card-pool:retry', '#', 'admin', SYSDATE, '', NULL, '' FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2080);
INSERT INTO sys_role_menu (role_id, menu_id) SELECT 2, menu_id FROM (SELECT 2 menu_id FROM dual UNION ALL SELECT 2078 FROM dual UNION ALL SELECT 2079 FROM dual UNION ALL SELECT 2080 FROM dual) source WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu target WHERE target.role_id = 2 AND target.menu_id = source.menu_id);
COMMIT;
