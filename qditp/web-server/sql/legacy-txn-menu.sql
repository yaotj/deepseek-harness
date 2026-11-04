-- 「交易管理」对标旧 ITP 补建的 6 个只读查询页菜单（挂「交易管理」目录 parent_id = 2077 下，
-- 如现场目录 ID 不同 MUST 调整；2077 存在性依据 stats-menu-ia-reorg.sql 2026-11-04 现查）。
--
-- 页面与数据来源：
--   TVM充值订单管理      web/src/views/trans/tvm-topup-order/index.vue       ← collect-pay TBL_TVM_ORDER_TOPUP（+Notiy 回执）
--   TVM充值存疑订单处理  web/src/views/trans/tvm-topup-suspicious/index.vue  ← face-pay F2F_RESULT_REPORT（TOPUP_STATUS=02）
--   BOM非现金管理        web/src/views/trans/bom-nocash/index.vue           ← collect-pay TBL_BOM_ORDER_PAY
--   单程票购票订单       web/src/views/trans/sjt-order/index.vue            ← collect-pay TBL_BOM_ORDER_PAY（TRANS_TYPE=01 购票发售）
--   单边查询            web/src/views/trans/one-side-query/index.vue        ← gate-txn-pay GATE_TXN_PAY（ORDER_EXP_TYPE 1/2）
--   在线补款订单管理     web/src/views/trans/supplement-order/index.vue     ← face-pay SUPPLEMENT_ORDER
--
-- route_name MUST 与前端组件 <script setup name="..."> 完全一致（keep-alive 匹配，同 device-info-menu.sql 说明）。
-- 全部为 C 型只读查询菜单，无 F 型按钮（无写操作端点）。
--
-- NEVER 写死 menu_id：用 max(menu_id)+1 取号，not exists 按 perms 幂等。
-- 库内 role_id=1「超级管理员」走 *:*:* 无需补 SYS_ROLE_MENU，其它角色在「角色管理」里勾选。
--
-- 执行后回查：
--   select menu_id, menu_name, parent_id, path, component, route_name, perms from sys_menu
--   where perms like 'trans:%:query' and perms not in
--   ('trans:face-pay:query','trans:daily-ticket:query','trans:refund-record:query','trans:txn-detail:query')
--   order by menu_id;
-- 回滚（六行均由本文件新增，可安全删除；菜单无缓存、删后即时生效，无需重启）：
--   delete from sys_menu where perms in
--   ('trans:tvm-topup:query','trans:topup-suspicious:query','trans:bom-nocash:query',
--    'trans:sjt-order:query','trans:one-side:query','trans:supplement-order:query');
--   commit;

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), 'TVM充值订单管理', 2077,
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = 2077),
       'tvm-topup-order', 'trans/tvm-topup-order/index', '', 'TvmTopupOrder', 1, 0, 'C', '0', '0', 'trans:tvm-topup:query', 'money', 'admin', sysdate, '', null, 'TVM充值订单查询（含最新充值回执态，collect-pay TBL_TVM_ORDER_TOPUP）'
from dual
where not exists (select 1 from sys_menu where perms = 'trans:tvm-topup:query');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), 'TVM充值存疑订单处理', 2077,
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = 2077),
       'tvm-topup-suspicious', 'trans/tvm-topup-suspicious/index', '', 'TvmTopupSuspicious', 1, 0, 'C', '0', '0', 'trans:topup-suspicious:query', 'warning', 'admin', sysdate, '', null, '设备充值回执存疑（TOPUP_STATUS=02）查询，face-pay F2F_RESULT_REPORT'
from dual
where not exists (select 1 from sys_menu where perms = 'trans:topup-suspicious:query');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), 'BOM非现金管理', 2077,
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = 2077),
       'bom-nocash', 'trans/bom-nocash/index', '', 'BomNoCashOrder', 1, 0, 'C', '0', '0', 'trans:bom-nocash:query', 'money', 'admin', sysdate, '', null, 'BOM 非现金订单查询（TRANS_TYPE 01 购票发售/22 充值，collect-pay TBL_BOM_ORDER_PAY）'
from dual
where not exists (select 1 from sys_menu where perms = 'trans:bom-nocash:query');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), '单程票购票订单', 2077,
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = 2077),
       'sjt-order', 'trans/sjt-order/index', '', 'SjtOrder', 1, 0, 'C', '0', '0', 'trans:sjt-order:query', 'list', 'admin', sysdate, '', null, 'BOM 侧单程票购票订单（TRANS_TYPE=01；TVM 侧见当面付订单查询）'
from dual
where not exists (select 1 from sys_menu where perms = 'trans:sjt-order:query');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), '单边查询', 2077,
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = 2077),
       'one-side-query', 'trans/one-side-query/index', '', 'OneSideQuery', 1, 0, 'C', '0', '0', 'trans:one-side:query', 'search', 'admin', sysdate, '', null, '单边交易查询（ORDER_EXP_TYPE 单边入1/单边出2，gate-txn-pay GATE_TXN_PAY）'
from dual
where not exists (select 1 from sys_menu where perms = 'trans:one-side:query');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), '在线补款订单管理', 2077,
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = 2077),
       'supplement-order', 'trans/supplement-order/index', '', 'SupplementOrderPage', 1, 0, 'C', '0', '0', 'trans:supplement-order:query', 'money', 'admin', sysdate, '', null, 'IF8A-26 在线补款单查询（face-pay SUPPLEMENT_ORDER）'
from dual
where not exists (select 1 from sys_menu where perms = 'trans:supplement-order:query');

commit;
