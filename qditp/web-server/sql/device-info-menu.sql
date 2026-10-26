-- 「设备管理」页面菜单（挂「参数管理」目录 parent_id = 2003 下，如现场目录 ID 不同 MUST 调整）。
--
-- 页面：web/src/views/para/device/index.vue，数据来源 para-server TBL_DEVICE_INFO
-- （ACC 设备台账镜像 + 业主自定义管理模式：01 正常使用 / 02 节能关机）。
--
-- route_name MUST 与前端组件 <script setup name="DeviceInfo"> 完全一致，写错 keep-alive 匹配不上
-- （同 user-query-menu.sql / device-heartbeat-menu.sql 头部说明）。
--
-- perms 用 para:device:* 新码值空间，NEVER 并入其他模块的权限串。
-- C 型 1 行 + F 型按钮 2 行（edit=批量设置管理模式，sync=立即同步）；
-- 库内 role_id=1「超级管理员」走 *:*:* 无需补 SYS_ROLE_MENU，其它角色在「角色管理」里勾选。
--
-- NEVER 写死 menu_id：用 max(menu_id)+1 取号，not exists 按 perms 幂等；
-- F 型行的 parent_id 动态引用 C 行取号结果，保证三行落在同一父节点下。
-- 数值列（MENU_ID / PARENT_ID / ORDER_NUM / IS_FRAME / IS_CACHE）写数字字面量而非字符串（库内是 NUMBER）。
--
-- 执行后回查：
--   select menu_id, menu_name, parent_id, path, component, route_name, perms from sys_menu
--   where perms like 'para:device:%' order by menu_id;
-- 回滚（三行均由本文件新增，可安全删除；菜单无缓存、删后即时生效，无需重启）：
--   delete from sys_menu where perms in ('para:device:query', 'para:device:edit', 'para:device:sync');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), '设备管理', 2003,
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = 2003),
       'device-info', 'para/device/index', '', 'DeviceInfo', 1, 0, 'C', '0', '0', 'para:device:query', 'monitor', 'admin', sysdate, '', null, 'ACC设备台账查询与业主自定义管理模式（正常使用/节能关机）维护'
from dual
where not exists (select 1 from sys_menu where perms = 'para:device:query');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), '设备管理模式设置',
       (select menu_id from sys_menu where perms = 'para:device:query'), 1,
       '', '', '', '', 1, 0, 'F', '0', '0', 'para:device:edit', '#', 'admin', sysdate, '', null, '批量设置设备管理模式（01正常使用/02节能关机）'
from dual
where not exists (select 1 from sys_menu where perms = 'para:device:edit');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), '设备台账立即同步',
       (select menu_id from sys_menu where perms = 'para:device:query'), 2,
       '', '', '', '', 1, 0, 'F', '0', '0', 'para:device:sync', '#', 'admin', sysdate, '', null, '手动触发一次ACC设备台账全量同步'
from dual
where not exists (select 1 from sys_menu where perms = 'para:device:sync');
