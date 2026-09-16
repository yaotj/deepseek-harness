-- 综管台新增功能菜单（2026-09-15）。
-- ⚠️ menu_id 已与线上 QDITP.SYS_MENU 实际数据对齐（2026-09-15 实测），禁止再改动 id。
-- 线上占用：2080=逻辑卡号重试导入(2078/F)、2081=交易明细查询(2071/C)、2082=交易明细按钮(2075/F)，
-- 故本文件菜单从 2083 起用。2071~2076 为用户运营既有菜单（见 user-query-menu.sql）。
-- 页面由后端动态路由加载，component 必须与 web/src/views 下路径一致。
-- route_name 必须与前端组件 <script setup name="..."> 完全一致
-- （RegStats / OfflineCodeStats / ItpUserBatchSearch / KeyVersion / ServiceStatus / OvertimeRefund），
-- 否则 keep-alive include 匹配不到 ⇒ 页面不缓存 ⇒ 切换 tab 后查询条件丢失。

-- 功能2 注册量统计（挂「用户管理」目录 2071，与同属交易域统计）
-- 线上存在 2083 与 2086 两条完全重复的菜单记录，此处原样保留以便对照清理。
insert into sys_menu values ('2083', '注册量统计', '2071', '5', 'reg-stats', 'trans/stats/register/index', '', 'RegStats', 1, 0, 'C', '0', '0', 'trans:stats:register:query', 'chart', 'admin', sysdate, '', null, '按票种分组统计 USER_ITP_REG_INFO 注册量');
insert into sys_menu values ('2086', '注册量统计', '2071', '5', 'reg-stats', 'trans/stats/register/index', '', 'RegStats', 1, 0, 'C', '0', '0', 'trans:stats:register:query', 'chart', 'admin', sysdate, '', null, '按票种分组统计 USER_ITP_REG_INFO 注册量');

-- 功能3 离线码统计（挂「用户管理」目录 2071）
insert into sys_menu values ('2084', '离线码统计', '2071', '6', 'offline-code-stats', 'trans/stats/offline-code/index', '', 'OfflineCodeStats', 1, 0, 'C', '0', '0', 'trans:stats:offlineCode:query', 'chart', 'admin', sysdate, '', null, '按车站分组统计 GATE_TXN_PAY 离线码交易笔数与独立卡数');

-- 功能4 批量导入逻辑卡号查手机号（挂「用户管理」目录 2071）
insert into sys_menu values ('2085', '批量卡号查手机号', '2071', '7', 'itp-user-batch-search', 'trans/user/batch-search/index', '', 'ItpUserBatchSearch', 1, 0, 'C', '0', '0', 'trans:user:batchSearch:query', 'search', 'admin', sysdate, '', null, '批量导入逻辑卡号查询手机号并导出');

-- 功能5 密钥版本查看（挂「用户管理」目录 2071；只读元信息，不含密钥材料）
insert into sys_menu values ('2087', '密钥版本查看', '2071', '8', 'key-version', 'trans/key/version/index', '', 'KeyVersion', 1, 0, 'C', '0', '0', 'trans:key:version:query', 'lock', 'admin', sysdate, '', null, '只读查看各密钥域（AGM/CA/HCE）当前版本元信息');

-- 功能7 服务监控（挂「系统监控」目录 2，与既有 服务监控/缓存监控 同级；order_num 顺延 7）
insert into sys_menu values ('2088', '服务状态监控', '2', '7', 'service-status', 'monitor/service-status/index', '', 'ServiceStatus', 1, 0, 'C', '0', '0', 'monitor:serviceStatus:list', 'monitor', 'admin', sysdate, '', null, '各微服务 /actuator/health 探活聚合');

-- 功能1 批量退超时罚金（挂「用户管理」目录 2071；资金操作，后端已注明入口侧需限运营网段）
insert into sys_menu values ('2089', '批量退超时罚金', '2071', '9', 'overtime-refund', 'trans/overtime-refund/index', '', 'OvertimeRefund', 1, 0, 'C', '0', '0', 'trans:overtimeRefund:refund', 'money', 'admin', sysdate, '', null, '圈定超时出站单边单并批量退超时罚金');
