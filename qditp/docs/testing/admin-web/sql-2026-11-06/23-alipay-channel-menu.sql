-- 支付宝渠道管理菜单（对标旧 ITP「支付宝出行管理」组）：新建一级 M 目录 + 3 个只读页 C 行。
--
-- 页面与数据来源（全部只读查询，无写端点）：
--   签约用户查询   web/src/views/trans/alipay-channel/signed-users/index.vue   ← alipay-pay-sign /page/alipay/signed-users（ALIPAY_SIGN_INFO）
--   渠道退款记录   web/src/views/trans/alipay-channel/refund-records/index.vue ← alipay-pay-sign /page/alipay/refund-records（ALIPAY_REFUND_TXN_DETAIL）
--   支付统计       web/src/views/trans/alipay-channel/pay-stats/index.vue     ← gate-txn-pay /page/gate-txn-pay?issueChannelCode=07（GATE_TXN_PAY，支付宝出行渠道扣费）
--
-- 注意：「支付统计」不落 alipay-pay-sign（ALIPAY_PAY_TXN_DETAIL 按该模块设计不是查询入口）；
--       扣费权威在 GATE_TXN_PAY，渠道口径 ISSUE_CHANNEL_CODE='07'（支付宝出行）。
--
-- route_name MUST 与前端组件 <script setup name="..."> 完全一致。NEVER 写死 menu_id：max+1 取号，not exists 按 perms/menu_id 幂等。
-- 新 M 目录对非超管角色需补 sys_role_menu（超管走 *:*:* 自动可见）；此处不给具体角色授权，现场按需勾选。
--
-- 执行后回查：
--   select menu_id, menu_name, parent_id, menu_type, path, component, route_name, perms from sys_menu
--   where perms like 'trans:alipay:%' or menu_name = '支付宝渠道管理' order by menu_id;
-- 回滚（4 行均由本文件新增，可安全删除；菜单无缓存、删后即时生效，无需重启）：
--   delete from sys_menu where menu_id in (
--     select menu_id from sys_menu where perms in
--     ('trans:alipay:signed-users','trans:alipay:refund-records','trans:alipay:pay-stats')
--   ) or (menu_name = '支付宝渠道管理' and menu_type = 'M'
--         and parent_id = 0 and not exists (select 1 from sys_menu child where child.parent_id = sys_menu.menu_id));
--   commit;

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), '支付宝渠道管理', 0,
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = 0),
       'alipay-channel', null, '', 'AlipayChannel', 1, 0, 'M', '0', '0', null, 'alipay', 'admin', sysdate, '', null, '支付宝出行渠道运营页统一入口（对标旧 ITP 支付宝出行管理组）'
from dual
where not exists (select 1 from sys_menu where route_name = 'AlipayChannel' and menu_type = 'M');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), '签约用户查询',
       (select menu_id from sys_menu where route_name = 'AlipayChannel' and menu_type = 'M'),
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = (select menu_id from sys_menu where route_name = 'AlipayChannel' and menu_type = 'M')),
       'signed-users', 'trans/alipay-channel/signed-users/index', '', 'AlipaySignedUsers', 1, 0, 'C', '0', '0', 'trans:alipay:signed-users', 'people', 'admin', sysdate, '', null, '签约用户（ALIPAY_SIGN_INFO，alipay-pay-sign 只读）'
from dual
where not exists (select 1 from sys_menu where perms = 'trans:alipay:signed-users');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), '渠道退款记录',
       (select menu_id from sys_menu where route_name = 'AlipayChannel' and menu_type = 'M'),
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = (select menu_id from sys_menu where route_name = 'AlipayChannel' and menu_type = 'M')),
       'refund-records', 'trans/alipay-channel/refund-records/index', '', 'AlipayRefundRecords', 1, 0, 'C', '0', '0', 'trans:alipay:refund-records', 'money', 'admin', sysdate, '', null, '渠道退款记录（ALIPAY_REFUND_TXN_DETAIL，alipay-pay-sign 只读）'
from dual
where not exists (select 1 from sys_menu where perms = 'trans:alipay:refund-records');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select (select max(menu_id) + 1 from sys_menu), '支付统计',
       (select menu_id from sys_menu where route_name = 'AlipayChannel' and menu_type = 'M'),
       (select nvl(max(order_num), 0) + 1 from sys_menu where parent_id = (select menu_id from sys_menu where route_name = 'AlipayChannel' and menu_type = 'M')),
       'pay-stats', 'trans/alipay-channel/pay-stats/index', '', 'AlipayPayStats', 1, 0, 'C', '0', '0', 'trans:alipay:pay-stats', 'chart', 'admin', sysdate, '', null, '支付宝出行渠道扣费统计（GATE_TXN_PAY issueChannelCode=07，gate-txn-pay 只读）'
from dual
where not exists (select 1 from sys_menu where perms = 'trans:alipay:pay-stats');

commit;
