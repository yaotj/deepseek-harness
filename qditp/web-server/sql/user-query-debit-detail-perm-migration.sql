-- 执行记录：2026-09-20 已在 AFCITPDB 执行并回查通过，实际落到 menu_id = 2090
-- （执行前 max(menu_id)=2089、SYS_MENU 共 137 行；回查 menu_id=2090 / menu_type=F /
--  parent_id=2071 / order_num=5 / visible=0 / status=0 / perms=trans:user:debit-detail:query）。
-- 因此本文件已是「已落库」状态，重复执行会被下面的 not exists 拦住、affectedRows=0。
--
-- 同批实查到的 2071 现有子节点：2072 / 2073 / 2074 / 2081 / 2084 / 2085 / 2086 / 2089 / 2090。
-- 另注：库内 2082 是 F 型「交易明细查询」、parent_id=2075、perms 与 2081 重复；
-- 仓库 business-menu-permissions.sql:18 把同一条写成 2076，与库不符 —— 那个文件整体已过期，NEVER 照它执行。
--
-- 角色授权现状（2026-09-20 实查 SYS_ROLE / SYS_ROLE_MENU）：只有两个角色，
-- role_id=1「超级管理员」（role_key=admin，24 条菜单）与 role_id=2「普通角色」（role_key=common，88 条菜单）。
-- **普通角色连 2072「支付宝用户查询」页本身都没授权**，因此本条按钮权限当前无需给它补，
-- 页面对它整个不可见；admin 走 `*:*:*` 不受 v-hasPermi 影响。
-- 将来把该页开放给某个角色时，MUST 把 2072 与 2090 一起勾上，否则进了页面看不到「扣费信息」按钮。
--
-- 补「扣费信息查询」按钮权限（perms = trans:user:debit-detail:query），归属 2071 用户管理目录。
-- 背景：支付宝用户查询页（trans/user/alipay/index.vue）与用户查询页（trans/user/itp/index.vue）
-- 各有「交易明细 / 扣费信息 / 乘车状态」三个跳转按钮。前两者已有权限串可用
-- （交易明细 = 2081 的 trans:user:txn-detail:query，乘车状态 = 2074 的 trans:user:ride-status:edit），
-- 只有「扣费信息」全库无对应 perms，因此本文件补一条 F 型（按钮）菜单。
--
-- NEVER 在本文件里写死 menu_id：2071 下已有 2072/2073/2074/2081（user-query-menu.sql）
-- 与 2084/2085/2086/2089（zongguantai-menu.sql），2075/2076 属参数管理，中间号段是否空闲无法凭仓库判断。
-- 因此取 max(menu_id)+1，并用 not exists 按 perms 做幂等：重复执行不会插第二行。
-- F 型菜单不进侧边栏，order_num 只是排序占位，与同级重复无副作用。
--
-- 执行后回查：
--   select menu_id, menu_name, parent_id, menu_type, perms from sys_menu where perms = 'trans:user:debit-detail:query';
-- 并确认角色已被授予该权限（admin 是 *:*:* 不受影响，其余角色 MUST 在后台「角色管理」里勾上），
-- 否则前端 v-hasPermi 会把「扣费信息」按钮对这些角色整个隐藏。
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), '扣费信息查询', '2071', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'trans:user:debit-detail:query', '#', 'admin', sysdate, '', null, '查询 GATE_TXN_PAY 出站扣费记录，从用户查询页带卡号跳转'
from dual
where not exists (select 1 from sys_menu where perms = 'trans:user:debit-detail:query');
