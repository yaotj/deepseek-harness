-- 新增「设备密钥同步」菜单（挂「系统监控」目录 parent_id = 2 下）。
-- 数据源：key-server 的 COM_DEVICE_SYN_KEY（每台设备仅保留最新一条同步状态）。
--
-- 执行前请先用 `select max(menu_id) from sys_menu;` 确认 max 值；本脚本按 `max+1` 取号，若并发执行需人工协调。
--
-- 回滚语句：
--   delete from sys_menu where perms = 'monitor:deviceKeySync:list';

insert into sys_menu
select (select max(menu_id) + 1 from sys_menu),
       '设备密钥同步',
       '2',
       '11',
       'device-key-sync',
       'monitor/device-key-sync/index',
       '',
       'DeviceKeySync',
       1,
       0,
       'C',
       '0',
       '0',
       'monitor:deviceKeySync:list',
       'monitor',
       'admin',
       sysdate,
       '',
       null,
       '只读展示 COM_DEVICE_SYN_KEY 的 AGM 设备最新密钥同步状态'
from dual
where not exists (select 1 from sys_menu where perms = 'monitor:deviceKeySync:list');
