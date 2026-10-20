-- 新增「已注销用户查询」菜单（USER_ITP_REG_LOG 销户记录，OPER_TYPE=2）。
-- 父菜单 2071「用户管理」下已有：2073 用户查询（有效）、2072 支付宝用户查询、2081 交易明细查询、2090 扣费信息查询按钮等。
-- 页面由后端动态路由加载，component / route_name 必须与 web/src/views 下路径和组件 name 一致。
--
-- 执行前请先用 `select max(menu_id) from sys_menu;` 确认 max 值；本脚本按 `max+1` 取号，若并发执行需人工协调。
insert into sys_menu
select (select max(menu_id) + 1 from sys_menu),
       '已注销用户查询',
       '2071',
       '5',
       'itp-canceled-user',
       'trans/user/itp-canceled/index',
       '',
       'ItpUserCanceledSearch',
       1,
       0,
       'C',
       '0',
       '0',
       'trans:user:itp-canceled:query',
       'user',
       'admin',
       sysdate,
       '',
       null,
       '查询 USER_ITP_REG_LOG 已注销用户销户记录（OPER_TYPE=2），覆盖已物理归档的行'
from dual
where not exists (select 1 from sys_menu where perms = 'trans:user:itp-canceled:query');
