-- 风险管理页面菜单，挂在“参数管理”菜单（2003）下。
insert into sys_menu values ('2016', '风险组管理', '2003', '4', 'risk-group', 'para/risk/group/index', '', 'RiskGroup', 1, 0, 'C', '0', '0', 'para:risk-group:query', 'collection-tag', 'admin', sysdate, '', null, '维护风险组');
insert into sys_menu values ('2017', '风险组新增', '2016', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'para:risk-group:add', '#', 'admin', sysdate, '', null, '新增风险组');
insert into sys_menu values ('2018', '风险组修改', '2016', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'para:risk-group:edit', '#', 'admin', sysdate, '', null, '修改风险组');
insert into sys_menu values ('2019', '风险组删除', '2016', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'para:risk-group:remove', '#', 'admin', sysdate, '', null, '删除风险组');

insert into sys_menu values ('2020', '风险规则管理', '2003', '5', 'risk-rule', 'para/risk/rule/index', '', 'RiskRule', 1, 0, 'C', '0', '0', 'para:risk-rule:query', 'warning', 'admin', sysdate, '', null, '维护风险规则');
insert into sys_menu values ('2021', '风险规则新增', '2020', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'para:risk-rule:add', '#', 'admin', sysdate, '', null, '新增风险规则');
insert into sys_menu values ('2022', '风险规则修改', '2020', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'para:risk-rule:edit', '#', 'admin', sysdate, '', null, '修改风险规则');
insert into sys_menu values ('2023', '风险规则删除', '2020', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'para:risk-rule:remove', '#', 'admin', sysdate, '', null, '删除风险规则');

insert into sys_menu values ('2024', '风险控制操作记录', '2003', '6', 'risk-control-log', 'para/risk/control-log/index', '', 'RiskControlLog', 1, 0, 'C', '0', '0', 'para:risk-control-log:query', 'document', 'admin', sysdate, '', null, '查询风险控制命中审计记录');
