-- 灰名单管理页面菜单（对标旧 ITP「灰名单管理」，挂「风险管理」组下）。
--
-- 页面：web/src/views/trans/graylist/index.vue，数据来源 blacklist-server GRAYLIST（观察/预警名单，
-- 不参与乘车拦截、不渠道同步；与黑名单的边界见 graylist-schema.sql 头注）。
--
-- 父级动态取「风险管理」M 型目录；现场分组不同（如黑名单仍挂一级）MUST 调整 parent 取法。
-- route_name MUST 与前端组件 <script setup name="GraylistManagement"> 完全一致。
-- C 型 1 行 + F 型按钮 2 行（add 进灰 / remove 移出）；NEVER 写死 menu_id，max+1 取号、按 perms 幂等。
--
-- 执行后回查：
--   select menu_id, menu_name, parent_id, path, component, route_name, perms from sys_menu
--   where perms like 'trans:graylist:%' order by menu_id;
-- 回滚：
--   delete from sys_menu where perms in ('trans:graylist:query','trans:graylist:add','trans:graylist:remove');
--   commit;

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), '灰名单管理',
       nvl((select menu_id from sys_menu where menu_name = '风险管理' and menu_type = 'M'), 0),
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = nvl((select menu_id from sys_menu where menu_name = '风险管理' and menu_type = 'M'), 0)),
       'graylist', 'trans/graylist/index', '', 'GraylistManagement', 1, 0, 'C', '0', '0', 'trans:graylist:query', 'eye-open', 'admin', sysdate, '', null, '观察/预警名单（GRAYLIST，不进拦截不渠道同步）'
from dual
where not exists (select 1 from sys_menu where perms = 'trans:graylist:query');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), '灰名单进灰',
       (select menu_id from sys_menu where perms = 'trans:graylist:query'), 1,
       '', '', '', '', 1, 0, 'F', '0', '0', 'trans:graylist:add', '#', 'admin', sysdate, '', null, '新增灰名单记录（发起方定死 09 运维手工）'
from dual
where not exists (select 1 from sys_menu where perms = 'trans:graylist:add');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), '灰名单移出',
       (select menu_id from sys_menu where perms = 'trans:graylist:query'), 2,
       '', '', '', '', 1, 0, 'F', '0', '0', 'trans:graylist:remove', '#', 'admin', sysdate, '', null, '移出灰名单并记操作流水'
from dual
where not exists (select 1 from sys_menu where perms = 'trans:graylist:remove');

commit;
