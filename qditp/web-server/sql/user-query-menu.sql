-- 用户查询菜单。页面由后端动态路由加载，component 必须与 web/src/views 下路径一致。
insert into sys_menu values ('2000', '用户运营', '0', '10', 'user-operation', '', '', 1, 0, 'M', '0', '0', '', 'peoples', 'admin', sysdate, '', null, '用户运营菜单');
insert into sys_menu values ('2001', '支付宝用户查询', '2000', '1', 'alipay-user', 'trans/user/alipay/index', '', '', 1, 0, 'C', '0', '0', 'trans:user:alipay:query', 'user', 'admin', sysdate, '', null, '查询 ALIPAY_USER_INFO 注册用户');
insert into sys_menu values ('2002', '其他用户查询', '2000', '2', 'itp-user', 'trans/user/itp/index', '', '', 1, 0, 'C', '0', '0', 'trans:user:itp:query', 'user', 'admin', sysdate, '', null, '查询 USER_ITP_REG_INFO 注册用户');
insert into sys_menu values ('2006', '修改用户乘车状态', '2000', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'trans:user:ride-status:edit', '#', 'admin', sysdate, '', null, '人工修改 QRCODE_STATUS 当前乘车状态');
