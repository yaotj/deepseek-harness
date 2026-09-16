-- Web page menu supplement (Oracle).
-- This script is idempotent. It only adds missing menus and grants them to
-- the administrator role (role_id = 2); change the role ID as needed.

-- Parameter-management pages that are present under web/src/views/para.
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 2026, '线路信息', 2003, 7, 'line', 'para/line/index', '', 'LineInfo', 1, 0, 'C', '0', '0', 'para:line:query', 'guide', 'admin', SYSDATE, '', NULL, '查询当前生效路网版本的线路信息'
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2026);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 2027, '车站信息', 2003, 8, 'station', 'para/station/index', '', 'StationInfo', 1, 0, 'C', '0', '0', 'para:station:query', 'place', 'admin', SYSDATE, '', NULL, '查询当前生效路网版本的车站信息'
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2027);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 2028, '线站版本信息', 2003, 9, 'line-station-version', 'para/lineStationVersion/index', '', 'LineStationVersion', 1, 0, 'C', '0', '0', 'para:line-station-version:query', 'date', 'admin', SYSDATE, '', NULL, '查询线路和车站代码版本信息'
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2028);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 2029, '操作员管理', 2003, 10, 'operator', 'para/operator/index', '', 'Operator', 1, 0, 'C', '0', '0', 'para:operator:list', 'user', 'admin', SYSDATE, '', NULL, '维护操作员信息'
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2029);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 2030, '操作员查询', 2029, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'para:operator:query', '#', 'admin', SYSDATE, '', NULL, ''
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2030);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 2031, '操作员新增', 2029, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'para:operator:add', '#', 'admin', SYSDATE, '', NULL, ''
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2031);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 2032, '操作员修改', 2029, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'para:operator:edit', '#', 'admin', SYSDATE, '', NULL, ''
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2032);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 2033, '操作员删除', 2029, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'para:operator:remove', '#', 'admin', SYSDATE, '', NULL, ''
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2033);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 2034, '操作员导出', 2029, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'para:operator:export', '#', 'admin', SYSDATE, '', NULL, ''
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2034);

-- The nested directory paths deliberately match the hard-coded Vue routes:
-- /trans/daily-ticket/refund and /trans/daily-ticket/refund-record.
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 2035, '交易运营', 0, 14, 'trans', NULL, '', '', 1, 0, 'M', '0', '0', '', 'money', 'admin', SYSDATE, '', NULL, '交易运营目录'
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2035);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 2036, '日票退款', 2035, 1, 'daily-ticket', NULL, '', '', 1, 0, 'M', '0', '0', '', 'tickets', 'admin', SYSDATE, '', NULL, '日票退款业务目录'
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2036);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 2037, '日票退款处理', 2036, 1, 'refund', 'trans/daily-ticket/refund/index', '', 'DailyTicketRefund', 1, 0, 'C', '0', '0', 'trans:daily-ticket:refund:query', 'refresh-left', 'admin', SYSDATE, '', NULL, '查询并处理可退款日票订单'
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2037);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 2038, '日票退款记录', 2036, 2, 'refund-record', 'trans/daily-ticket/refund-record/index', '', 'DailyTicketRefundRecord', 1, 0, 'C', '0', '0', 'trans:daily-ticket:refund-record:query', 'form', 'admin', SYSDATE, '', NULL, '查询日票退款申请和处理记录'
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2038);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 2039, '日票发起退款', 2037, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'trans:daily-ticket:refund:apply', '#', 'admin', SYSDATE, '', NULL, ''
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2039);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 2040, '日票退款结果查询', 2037, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'trans:daily-ticket:refund:result', '#', 'admin', SYSDATE, '', NULL, ''
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2040);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
SELECT 2041, '日票退款重试', 2037, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'trans:daily-ticket:refund:retry', '#', 'admin', SYSDATE, '', NULL, ''
FROM dual WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 2041);

-- Grant the menus to the built-in administrator role. The parent menus are
-- included so the dynamic-router tree contains the required route ancestry.
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, menu_id
FROM (
  SELECT 2003 AS menu_id FROM dual UNION ALL SELECT 2026 FROM dual UNION ALL
  SELECT 2027 FROM dual UNION ALL SELECT 2028 FROM dual UNION ALL
  SELECT 2029 FROM dual UNION ALL SELECT 2030 FROM dual UNION ALL
  SELECT 2031 FROM dual UNION ALL SELECT 2032 FROM dual UNION ALL
  SELECT 2033 FROM dual UNION ALL SELECT 2034 FROM dual UNION ALL
  SELECT 2035 FROM dual UNION ALL SELECT 2036 FROM dual UNION ALL
  SELECT 2037 FROM dual UNION ALL SELECT 2038 FROM dual UNION ALL
  SELECT 2039 FROM dual UNION ALL SELECT 2040 FROM dual UNION ALL
  SELECT 2041 FROM dual
) source
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_menu target
  WHERE target.role_id = 2 AND target.menu_id = source.menu_id
);

COMMIT;
