-- 报表管理对标旧 ITP 的 12 个只读报表页菜单（挂「统计分析」目录 parent_id = 2101 下）。
--
-- 2101「统计分析」由 stats-menu-ia-reorg.sql（2026-11-04）建立，其注释明确：
-- 「P2 若业主确认补建对账/清分报表，也归这一组」；如现场目录 ID 不同 MUST 调整。
--
-- 页面与数据来源（全部只读，无写端点）：
--   itp平台与支付宝/微信/银联对账   report/txn-diff-ali|wx|union        ← recon /page/recon/txn-diff（RECON_TXN_DIFF，渠道预填值仅供参考、可改）
--   ITP与支付宝/微信/银联对账报表   report/third-bill-ali|wx|union     ← recon /page/recon/third-bill（RECON_THIRD_BILL）
--   日统计汇总(按支付账户)          report/daily-stats-account-summary ← recon /page/recon/daily-stats?groupBy=VENDOR（PAY_TXN_DETAIL 纯查询）
--   日统计报表（按支付账户）        report/daily-stats-account-report  ← 同上（同口径，明细分项展示）
--   日统计汇总（按当天交易）        report/daily-stats-day             ← recon /page/recon/daily-stats?groupBy=HOUR（单日窗）
--   业务清分详细报表                report/clearing-detail             ← recon /page/recon/clearing（RECON_THIRD_BILL 聚合）
--   TVM充值日报表                  report/tvm-topup-daily             ← collect-pay /page/report/tvm-topup-daily（TBL_TVM_ORDER_TOPUP 按日聚合）
--   非现金充值数据查询              report/nocash-topup                ← collect-pay /page/report/nocash-topup（TBL_BOM_ORDER_PAY TRANS_TYPE=22）
--
-- route_name MUST 与前端组件 <script setup name="..."> 完全一致（同 device-info-menu.sql 说明）。
-- NEVER 写死 menu_id：max(menu_id)+1 取号，not exists 按 perms 幂等。
-- 库内 role_id=1「超级管理员」走 *:*:* 无需补 SYS_ROLE_MENU，其它角色在「角色管理」里勾选。
--
-- 执行后回查：
--   select menu_id, menu_name, parent_id, path, route_name, perms from sys_menu
--   where parent_id = 2101 and menu_type = 'C' order by order_num;
-- 回滚（12 行均由本文件新增，可安全删除；菜单无缓存、删后即时生效，无需重启）：
--   delete from sys_menu where perms in
--   ('report:recon-diff-ali:query','report:recon-diff-wx:query','report:recon-diff-union:query',
--    'report:recon-bill-ali:query','report:recon-bill-wx:query','report:recon-bill-union:query',
--    'report:daily-stats-acc-summary:query','report:daily-stats-acc-report:query','report:daily-stats-day:query',
--    'report:clearing-detail:query','report:tvm-topup-daily:query','report:nocash-topup:query');
--   commit;

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), 'itp平台与支付宝对账', 2101,
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = 2101),
       'txn-diff-ali', 'report/txn-diff-ali/index', '', 'ReconTxnDiffAli', 1, 0, 'C', '0', '0', 'report:recon-diff-ali:query', 'list', 'admin', sysdate, '', null, '对账差异明细（RECON_TXN_DIFF，渠道预填支付宝、可改）'
from dual
where not exists (select 1 from sys_menu where perms = 'report:recon-diff-ali:query');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), 'itp平台与微信对账', 2101,
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = 2101),
       'txn-diff-wx', 'report/txn-diff-wx/index', '', 'ReconTxnDiffWx', 1, 0, 'C', '0', '0', 'report:recon-diff-wx:query', 'list', 'admin', sysdate, '', null, '对账差异明细（RECON_TXN_DIFF，渠道预填微信、可改）'
from dual
where not exists (select 1 from sys_menu where perms = 'report:recon-diff-wx:query');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), 'itp平台与银联对账', 2101,
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = 2101),
       'txn-diff-union', 'report/txn-diff-union/index', '', 'ReconTxnDiffUnion', 1, 0, 'C', '0', '0', 'report:recon-diff-union:query', 'list', 'admin', sysdate, '', null, '对账差异明细（RECON_TXN_DIFF，渠道预填银联、可改）'
from dual
where not exists (select 1 from sys_menu where perms = 'report:recon-diff-union:query');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), 'ITP与支付宝对账报表', 2101,
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = 2101),
       'third-bill-ali', 'report/third-bill-ali/index', '', 'ReconThirdBillAli', 1, 0, 'C', '0', '0', 'report:recon-bill-ali:query', 'money', 'admin', sysdate, '', null, '渠道账单行（RECON_THIRD_BILL，渠道预填支付宝、可改）'
from dual
where not exists (select 1 from sys_menu where perms = 'report:recon-bill-ali:query');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), 'ITP与微信对账报表', 2101,
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = 2101),
       'third-bill-wx', 'report/third-bill-wx/index', '', 'ReconThirdBillWx', 1, 0, 'C', '0', '0', 'report:recon-bill-wx:query', 'money', 'admin', sysdate, '', null, '渠道账单行（RECON_THIRD_BILL，渠道预填微信、可改）'
from dual
where not exists (select 1 from sys_menu where perms = 'report:recon-bill-wx:query');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), 'ITP与银联对账报表', 2101,
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = 2101),
       'third-bill-union', 'report/third-bill-union/index', '', 'ReconThirdBillUnion', 1, 0, 'C', '0', '0', 'report:recon-bill-union:query', 'money', 'admin', sysdate, '', null, '渠道账单行（RECON_THIRD_BILL，渠道预填银联、可改）'
from dual
where not exists (select 1 from sys_menu where perms = 'report:recon-bill-union:query');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), '日统计汇总(按支付账户)', 2101,
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = 2101),
       'daily-stats-account-summary', 'report/daily-stats-account-summary/index', '', 'DailyStatsAccountSummary', 1, 0, 'C', '0', '0', 'report:daily-stats-acc-summary:query', 'chart', 'admin', sysdate, '', null, 'PAY_TXN_DETAIL 按交易日期+支付账户聚合（纯查询）'
from dual
where not exists (select 1 from sys_menu where perms = 'report:daily-stats-acc-summary:query');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), '日统计报表(按支付账户)', 2101,
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = 2101),
       'daily-stats-account-report', 'report/daily-stats-account-report/index', '', 'DailyStatsAccountReport', 1, 0, 'C', '0', '0', 'report:daily-stats-acc-report:query', 'chart', 'admin', sysdate, '', null, '同上口径，明细分项展示（现金/优惠/退款金额）'
from dual
where not exists (select 1 from sys_menu where perms = 'report:daily-stats-acc-report:query');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), '日统计汇总(按当天交易)', 2101,
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = 2101),
       'daily-stats-day', 'report/daily-stats-day/index', '', 'DailyStatsDay', 1, 0, 'C', '0', '0', 'report:daily-stats-day:query', 'chart', 'admin', sysdate, '', null, 'PAY_TXN_DETAIL 按当天小时桶聚合（仅单日窗）'
from dual
where not exists (select 1 from sys_menu where perms = 'report:daily-stats-day:query');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), '业务清分详细报表', 2101,
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = 2101),
       'clearing-detail', 'report/clearing-detail/index', '', 'ReconClearingDetail', 1, 0, 'C', '0', '0', 'report:clearing-detail:query', 'chart', 'admin', sysdate, '', null, 'RECON_THIRD_BILL 按账单日+支付商户聚合（纯查询）'
from dual
where not exists (select 1 from sys_menu where perms = 'report:clearing-detail:query');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), 'TVM充值日报表', 2101,
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = 2101),
       'tvm-topup-daily', 'report/tvm-topup-daily/index', '', 'TvmTopupDailyReport', 1, 0, 'C', '0', '0', 'report:tvm-topup-daily:query', 'chart', 'admin', sysdate, '', null, 'TBL_TVM_ORDER_TOPUP 按下单日期聚合（collect-pay 纯查询）'
from dual
where not exists (select 1 from sys_menu where perms = 'report:tvm-topup-daily:query');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), '非现金充值数据查询', 2101,
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = 2101),
       'nocash-topup', 'report/nocash-topup/index', '', 'NocashTopupQuery', 1, 0, 'C', '0', '0', 'report:nocash-topup:query', 'chart', 'admin', sysdate, '', null, 'TBL_BOM_ORDER_PAY TRANS_TYPE=22（充值）按下单日期聚合（collect-pay 纯查询）'
from dual
where not exists (select 1 from sys_menu where perms = 'report:nocash-topup:query');

commit;
