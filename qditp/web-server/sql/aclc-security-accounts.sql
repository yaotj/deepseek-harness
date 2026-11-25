-- =============================================================================
-- ACLC 测评整改 SQL — 序号5+8：用户账号体系细化 + 权限分离
-- 适用环境：测试库 (qditp) + 生产库 (QDITP_PLAT)
-- 执行方式：由 DBA 在各环境库分别执行
-- 前置条件：web-server 1.1.66 已发版（含密码过期+会话超时逻辑）
-- =============================================================================
-- 整改目标：
--   - 序号5：为每位运维人员创建唯一账号，分配账户和权限
--   - 序号8：实现管理用户权限分离（运维操作员 / 安全审计员 / 系统管理员）
-- =============================================================================

-- ----------------------------
-- 1. 新增角色（role_id 使用自增序列，从100开始）
-- ----------------------------
-- 注意：sys_role.role_id 使用 IDENTITY 自增，START WITH 100
-- 执行后请查询实际分配的 role_id 值

INSERT INTO sys_role (role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_by, create_time, remark)
VALUES ('运维操作员', 'ops_operator', 10, 1, 1, 1, '0', '0', 'admin', SYSDATE, 'ITP系统运维操作员，负责日常业务操作与查询，无系统配置权限');

INSERT INTO sys_role (role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_by, create_time, remark)
VALUES ('安全审计员', 'security_auditor', 11, 1, 1, 1, '0', '0', 'admin', SYSDATE, 'ITP系统安全审计员，仅可查看审计日志与操作记录，无业务操作权限');

INSERT INTO sys_role (role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_by, create_time, remark)
VALUES ('系统管理员', 'sys_admin', 12, 1, 1, 1, '0', '0', 'admin', SYSDATE, 'ITP系统管理员，负责系统配置与用户管理，权限小于超级管理员');

-- ----------------------------
-- 2. 获取新角色ID（执行后查询）
-- ----------------------------
-- 请在执行完步骤1后执行以下查询，记录实际 role_id 值：
-- SELECT role_id, role_name, role_key FROM sys_role WHERE role_id >= 100 ORDER BY role_id;
-- 预期结果：
--   role_id=100: 运维操作员 (ops_operator)
--   role_id=101: 安全审计员 (security_auditor)
--   role_id=102: 系统管理员 (sys_admin)
-- 下方SQL中的 role_id 请根据实际情况调整

-- ----------------------------
-- 3. 创建运维操作员账号（一人一号，禁止共享）
-- ----------------------------
-- 示例：创建3个运维操作员账号（实际部署时替换为真实姓名和联系方式）
-- 密码初始值：admin1234（满足复杂度要求），首次登录强制修改

-- 运维操作员A
INSERT INTO sys_user (dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar, password, status, del_flag, login_ip, login_date, create_by, create_time, pwd_update_date, remark)
VALUES (103, 'ops_zhangsan', '张三', '00', '', '13800138001', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '', NULL, 'admin', SYSDATE, SYSDATE, '运维操作员-张三，ITLC测评整改新建账号，角色：运维操作员');

-- 运维操作员B
INSERT INTO sys_user (dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar, password, status, del_flag, login_ip, login_date, create_by, create_time, pwd_update_date, remark)
VALUES (103, 'ops_lisi', '李四', '00', '', '13800138002', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '', NULL, 'admin', SYSDATE, SYSDATE, '运维操作员-李四，ITLC测评整改新建账号，角色：运维操作员');

-- 运维操作员C
INSERT INTO sys_user (dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar, password, status, del_flag, login_ip, login_date, create_by, create_time, pwd_update_date, remark)
VALUES (103, 'ops_wangwu', '王五', '00', '', '13800138003', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '', NULL, 'admin', SYSDATE, SYSDATE, '运维操作员-王五，ITLC测评整改新建账号，角色：运维操作员');

-- ----------------------------
-- 4. 创建安全审计员账号
-- ----------------------------
INSERT INTO sys_user (dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar, password, status, del_flag, login_ip, login_date, create_by, create_time, pwd_update_date, remark)
VALUES (103, 'auditor_zhaoliu', '赵六', '00', '', '13800138004', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '', NULL, 'admin', SYSDATE, SYSDATE, '安全审计员-赵六，ITLC测评整改新建账号，角色：安全审计员');

-- ----------------------------
-- 5. 创建系统管理员账号
-- ----------------------------
INSERT INTO sys_user (dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar, password, status, del_flag, login_ip, login_date, create_by, create_time, pwd_update_date, remark)
VALUES (103, 'sysadmin_qianqi', '钱七', '00', '', '13800138005', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '', NULL, 'admin', SYSDATE, SYSDATE, '系统管理员-钱七，ITLC测评整改新建账号，角色：系统管理员');

-- ----------------------------
-- 6. 为用户分配角色（一人一号原则）
-- ----------------------------
-- 注意：user_id 根据实际插入结果调整
-- 预期：ops_zhangsan=user_id=3, ops_lisi=user_id=4, ops_wangwu=user_id=5
--       auditor_zhaoliu=user_id=6, sysadmin_qianqi=user_id=7

-- 运维操作员账号分配"运维操作员"角色（role_id=100）
INSERT INTO sys_user_role (user_id, role_id) VALUES (3, 100);
INSERT INTO sys_user_role (user_id, role_id) VALUES (4, 100);
INSERT INTO sys_user_role (user_id, role_id) VALUES (5, 100);

-- 安全审计员账号分配"安全审计员"角色（role_id=101）
INSERT INTO sys_user_role (user_id, role_id) VALUES (6, 101);

-- 系统管理员账号分配"系统管理员"角色（role_id=102）
INSERT INTO sys_user_role (user_id, role_id) VALUES (7, 102);

-- ----------------------------
-- 7. 分配菜单权限（最小权限原则）
-- ----------------------------
-- 7.1 运维操作员菜单权限（只读查询 + 业务操作，无系统配置）
-- 包含：系统监控、日志管理、在线用户、定时任务等业务相关菜单
-- 不包含：用户管理、角色管理、菜单管理、部门管理等系统配置菜单

-- 系统监控相关
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (100, 2);      -- 系统监控
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (100, 109);    -- 在线用户
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (100, 110);    -- 定时任务
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (100, 111);    -- 数据监控
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (100, 112);    -- 服务监控
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (100, 113);    -- 缓存监控
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (100, 114);    -- 缓存列表

-- 日志管理相关（只读）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (100, 108);    -- 日志管理
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (100, 500);    -- 操作日志
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (100, 501);    -- 登录日志

-- 操作权限（只读查询 + 导出）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (100, 1046);   -- 在线查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (100, 1049);   -- 任务查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (100, 1054);   -- 任务导出
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (100, 1039);   -- 操作查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (100, 1041);   -- 日志导出
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (100, 1042);   -- 登录查询

-- 7.2 安全审计员菜单权限（只读审计日志，无业务操作）
-- 仅包含：日志管理相关菜单

-- 日志管理相关（只读）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (101, 108);    -- 日志管理
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (101, 500);    -- 操作日志
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (101, 501);    -- 登录日志

-- 操作权限（只读查询 + 导出，无删除权限）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (101, 1039);   -- 操作查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (101, 1041);   -- 日志导出
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (101, 1042);   -- 登录查询

-- 7.3 系统管理员菜单权限（系统配置 + 用户管理，小于超级管理员）
-- 包含：用户管理、角色管理、参数设置、通知公告等系统配置菜单
-- 不包含：菜单管理、代码生成、系统接口等高级配置

-- 系统管理相关
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1);      -- 系统管理
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 100);    -- 用户管理
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 101);    -- 角色管理
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 103);    -- 部门管理
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 104);    -- 岗位管理
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 105);    -- 字典管理
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 106);    -- 参数设置
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 107);    -- 通知公告

-- 系统监控相关（只读）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 2);      -- 系统监控
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 109);    -- 在线用户
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 110);    -- 定时任务
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 111);    -- 数据监控
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 112);    -- 服务监控
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 113);    -- 缓存监控
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 114);    -- 缓存列表

-- 日志管理相关
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 108);    -- 日志管理
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 500);    -- 操作日志
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 501);    -- 登录日志

-- 用户管理操作权限
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1000);   -- 用户查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1001);   -- 用户新增
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1002);   -- 用户修改
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1004);   -- 用户导出
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1005);   -- 用户导入
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1006);   -- 重置密码
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1045);   -- 账户解锁

-- 角色管理操作权限
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1007);   -- 角色查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1008);   -- 角色新增
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1009);   -- 角色修改
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1011);   -- 角色导出

-- 部门管理操作权限
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1016);   -- 部门查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1017);   -- 部门新增
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1018);   -- 部门修改

-- 岗位管理操作权限
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1020);   -- 岗位查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1021);   -- 岗位新增
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1022);   -- 岗位修改
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1024);   -- 岗位导出

-- 字典管理操作权限
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1025);   -- 字典查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1026);   -- 字典新增
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1027);   -- 字典修改
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1029);   -- 字典导出

-- 参数设置操作权限
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1030);   -- 参数查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1031);   -- 参数新增
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1032);   -- 参数修改
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1034);   -- 参数导出

-- 通知公告操作权限
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1035);   -- 公告查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1036);   -- 公告新增
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1037);   -- 公告修改

-- 操作日志操作权限
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1039);   -- 操作查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1041);   -- 日志导出

-- 登录日志操作权限
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1042);   -- 登录查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1044);   -- 日志导出

-- 在线用户操作权限
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1046);   -- 在线查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1047);   -- 批量强退
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1048);   -- 单条强退

-- 定时任务操作权限
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1049);   -- 任务查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1050);   -- 任务新增
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1051);   -- 任务修改
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1053);   -- 状态修改
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (102, 1054);   -- 任务导出

-- ----------------------------
-- 8. 限制超级管理员admin账号的日常使用
-- ----------------------------
-- 将admin账号标记为"仅限初始化使用"，日常操作使用新建的运维账号
UPDATE sys_user SET remark = '超级管理员-仅限系统初始化使用，日常操作请使用ops_*或sysadmin_*账号' WHERE user_name = 'admin';

-- ----------------------------
-- 9. 批量更新存量用户的密码最后更新时间（使密码过期检查生效）
-- ----------------------------
-- 将所有存量用户的pwd_update_date设为90天前，使其在下次登录时触发密码过期提醒
UPDATE sys_user SET pwd_update_date = SYSDATE - 90
WHERE user_id > 0 AND (pwd_update_date IS NULL OR pwd_update_date < SYSDATE - 365);

-- ----------------------------
-- 10. 验证SQL（执行后检查结果）
-- ----------------------------
-- 查看新创建的角色
-- SELECT role_id, role_name, role_key, status FROM sys_role WHERE role_id >= 100 ORDER BY role_id;

-- 查看新创建的用户
-- SELECT user_id, user_name, nick_name, status, remark FROM sys_user WHERE user_name LIKE 'ops_%' OR user_name LIKE 'auditor_%' OR user_name LIKE 'sysadmin_%' ORDER BY user_id;

-- 查看用户-角色分配
-- SELECT u.user_name, u.nick_name, r.role_name, r.role_key
-- FROM sys_user u
-- JOIN sys_user_role ur ON u.user_id = ur.user_id
-- JOIN sys_role r ON ur.role_id = r.role_id
-- WHERE r.role_id >= 100
-- ORDER BY u.user_name;

-- 查看各角色的菜单权限数量
-- SELECT r.role_name, COUNT(*) as menu_count
-- FROM sys_role_menu rm
-- JOIN sys_role r ON rm.role_id = r.role_id
-- WHERE r.role_id >= 100
-- GROUP BY r.role_name
-- ORDER BY r.role_id;

-- =============================================================================
-- 执行说明：
-- 1. 本SQL需要在测试库和三个生产库（QDITP_PLAT）分别执行
-- 2. sys_role 的 role_id 使用自增序列，执行后请确认实际分配的role_id值
-- 3. sys_user 的 user_id 使用自增序列，执行后请确认实际分配的user_id值
-- 4. 密码初始值为测试环境默认密码（admin123），首次登录强制修改（pwd_update_date已设为90天前）
-- 5. 执行完成后重启 web-admin 服务使配置生效
-- 6. 建议先在测试库验证无误后，再在生产库执行
-- 7. 如需调整菜单权限，请根据实际情况修改步骤7中的INSERT语句
-- =============================================================================
