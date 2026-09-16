-- 用户运营菜单（与 AFCITPDB 实际数据一致，menu_id 2071~2075）。
-- 页面由后端动态路由加载，component 必须与 web/src/views 下路径一致。
-- route_name 必须与前端组件 <script setup name="..."> 完全一致（AlipayUserSearch / ItpUserSearch），
-- 否则 web-server 会退化成 capitalize(path)（如 Alipay-user），与组件 name 对不上，
-- keep-alive include 匹配不到 ⇒ 页面不缓存 ⇒ 切换 tab 后查询条件丢失。
-- 例外：2075 交易明细查询的 route_name 刻意用 TxnDetailQuery 而非组件名 UserTransactionDetail，
-- 因为前端 router/index.js 隐藏路由 /trans/user-detail/transaction-detail 已占用该 name，
-- 重名会导致 vue-router 覆盖路由记录；该页 is_cache=1 不缓存，无需 keep-alive name 匹配。
-- 本文件早期版本用的 menu_id 2000/2001/2002/2006 与实际库不符（2000 是「参数管理」、2006 是「日票退款记录」），
-- 已按库内真实 ID 修正；已建库补 route_name 走 sys-menu-route-name-migration.sql。
insert into sys_menu values ('2071', '用户管理', '0', '10', 'user-operation', '', '', '', 1, 0, 'M', '0', '0', '', 'peoples', 'admin', sysdate, '', null, '用户运营菜单');
insert into sys_menu values ('2072', '支付宝用户查询', '2071', '1', 'alipay-user', 'trans/user/alipay/index', '', 'AlipayUserSearch', 1, 0, 'C', '0', '0', 'trans:user:alipay:query', 'user', 'admin', sysdate, '', null, '查询 ALIPAY_USER_INFO 注册用户');
insert into sys_menu values ('2073', '用户查询', '2071', '2', 'itp-user', 'trans/user/itp/index', '', 'ItpUserSearch', 1, 0, 'C', '0', '0', 'trans:user:itp:query', 'user', 'admin', sysdate, '', null, '查询 USER_ITP_REG_INFO 注册用户');
insert into sys_menu values ('2074', '修改用户乘车状态', '2071', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'trans:user:ride-status:edit', '#', 'admin', sysdate, '', null, '人工修改 QRCODE_STATUS 当前乘车状态');
insert into sys_menu values ('2075', '交易明细查询', '2071', '4', 'txn-detail', 'trans/user/transaction-detail/index', '', 'TxnDetailQuery', 1, 1, 'C', '0', '0', 'trans:user:txn-detail:query', 'list', 'admin', sysdate, '', null, '独立查询 QRCODE_TXN_DETAIL 交易明细，也可从用户查询带卡号跳转');
