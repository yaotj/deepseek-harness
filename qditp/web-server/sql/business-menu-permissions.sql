-- 补充业务菜单按钮权限

-- 用户运营 - 支付宝用户查询按钮权限
insert into sys_menu values ('2031', '支付宝用户查询', '2001', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'trans:user:alipay:query', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2032', '支付宝用户新增', '2001', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'trans:user:alipay:add', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2033', '支付宝用户修改', '2001', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'trans:user:alipay:edit', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2034', '支付宝用户删除', '2001', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'trans:user:alipay:remove', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2035', '支付宝用户导出', '2001', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'trans:user:alipay:export', '#', 'admin', sysdate, '', null, '');

-- 用户运营 - 其他用户查询按钮权限
insert into sys_menu values ('2036', '其他用户查询', '2002', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'trans:user:itp:query', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2037', '其他用户新增', '2002', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'trans:user:itp:add', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2038', '其他用户修改', '2002', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'trans:user:itp:edit', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2039', '其他用户删除', '2002', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'trans:user:itp:remove', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2040', '其他用户导出', '2002', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'trans:user:itp:export', '#', 'admin', sysdate, '', null, '');

-- 用户运营 - 交易明细查询按钮权限
insert into sys_menu values ('2076', '交易明细查询', '2075', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'trans:user:txn-detail:query', '#', 'admin', sysdate, '', null, '');

-- 交易运营 - 当面付订单查询按钮权限
insert into sys_menu values ('2041', '当面付订单查询', '2007', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'trans:face-pay:query', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2042', '当面付订单退款', '2007', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'trans:face-pay:refund', '#', 'admin', sysdate, '', null, '');

-- 交易运营 - 黑名单管理按钮权限
insert into sys_menu values ('2043', '黑名单查询', '2013', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'trans:blacklist:query', '#', 'admin', sysdate, '', null, '');

-- 交易运营 - 日票退款按钮权限
insert into sys_menu values ('2044', '日票退款查询', '2029', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'trans:daily-ticket:refund:query', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2045', '日票退款记录查询', '2030', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'trans:daily-ticket:refund:list', '#', 'admin', sysdate, '', null, '');

-- 参数管理 - 单程票最大购买张数按钮权限
insert into sys_menu values ('2046', '单程票最大购买张数查询', '2004', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'para:single-ticket-purchase-limit:query', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2047', '单程票最大购买张数修改', '2004', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'para:single-ticket-purchase-limit:edit', '#', 'admin', sysdate, '', null, '');

-- 参数管理 - 基础票价查询按钮权限
insert into sys_menu values ('2048', '基础票价查询', '2008', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'para:base-fare:query', '#', 'admin', sysdate, '', null, '');

-- 参数管理 - 订单退款周期按钮权限
insert into sys_menu values ('2049', '订单退款周期查询', '2009', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'para:order-refund-cycle:query', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2050', '订单退款周期新增', '2009', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'para:order-refund-cycle:add', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2051', '订单退款周期修改', '2009', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'para:order-refund-cycle:edit', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2052', '订单退款周期删除', '2009', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'para:order-refund-cycle:remove', '#', 'admin', sysdate, '', null, '');

-- 参数管理 - 风险组管理按钮权限
insert into sys_menu values ('2053', '风险组查询', '2016', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'para:risk-group:query', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2054', '风险组新增', '2016', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'para:risk-group:add', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2055', '风险组修改', '2016', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'para:risk-group:edit', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2056', '风险组删除', '2016', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'para:risk-group:remove', '#', 'admin', sysdate, '', null, '');

-- 参数管理 - 风险规则管理按钮权限
insert into sys_menu values ('2057', '风险规则查询', '2020', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'para:risk-rule:query', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2058', '风险规则新增', '2020', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'para:risk-rule:add', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2059', '风险规则修改', '2020', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'para:risk-rule:edit', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2060', '风险规则删除', '2020', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'para:risk-rule:remove', '#', 'admin', sysdate, '', null, '');

-- 参数管理 - 风险控制操作记录按钮权限
insert into sys_menu values ('2061', '风险控制操作记录查询', '2024', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'para:risk-control-log:query', '#', 'admin', sysdate, '', null, '');

-- 参数管理 - 线路管理按钮权限
insert into sys_menu values ('2062', '线路查询', '2026', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'para:line:query', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2063', '线路新增', '2026', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'para:line:add', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2064', '线路修改', '2026', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'para:line:edit', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2065', '线路删除', '2026', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'para:line:remove', '#', 'admin', sysdate, '', null, '');

-- 参数管理 - 站点管理按钮权限
insert into sys_menu values ('2066', '站点查询', '2027', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'para:station:query', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2067', '站点新增', '2027', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'para:station:add', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2068', '站点修改', '2027', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'para:station:edit', '#', 'admin', sysdate, '', null, '');
insert into sys_menu values ('2069', '站点删除', '2027', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'para:station:remove', '#', 'admin', sysdate, '', null, '');

-- 参数管理 - 线路站点版本管理按钮权限
insert into sys_menu values ('2070', '线路站点版本查询', '2028', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'para:lineStationVersion:query', '#', 'admin', sysdate, '', null, '');
