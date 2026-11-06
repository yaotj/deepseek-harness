-- ============================================================
-- 40 · B19 部门/岗位演示数据清理
-- ============================================================
-- 问题：sys_dept 8 行 / sys_post 4 行是 RuoYi 框架默认演示值，非真实组织架构。
--       综管台「部门管理」「岗位管理」页因此显示「测试部门/董事长/CEO」等无意义数据。
-- 依据：方案 §二·D（08 执行记录 §三 B19），业主 2026-11-06 裁决「两个都做」。
--
-- ⚠️ 写库动作影响同库其他会话（共享测试环境），已获业主授权。
-- ⚠️ 执行前 MUST 先跑 00-backup-before.sql（已含 sys_dept/sys_post 快照）。
-- ⚠️ 删除前 MUST 核对：是否有真实部门/岗位被误删（RuoYi 演示数据有固定特征）。
-- ============================================================

-- ---------- 1. 删除前核对（人工确认，应返回 RuoYi 演示值特征） ----------
-- RuoYi 默认部门：100 测试部门 / 101 董事长 / 102 总经理 / 103 法务 / 104 财务 / 105 行政
SELECT DEPT_ID, DEPT_NAME, PARENT_ID, ORDER_NUM, DEL_FLAG, CREATE_BY, CREATE_TIME
  FROM SYS_DEPT ORDER BY DEPT_ID;

-- RuoYi 默认岗位：1 董事长 / 2 总经理 / 3 法务 / 4 财务 / 5 行政
SELECT POST_ID, POST_CODE, POST_NAME, POST_SORT, STATUS, DEL_FLAG, CREATE_BY, CREATE_TIME
  FROM SYS_POST ORDER BY POST_ID;

-- 关联检查：是否有用户绑定了这些部门/岗位（有则删除会破坏用户组织关系）
SELECT U.USER_NAME, U.DEPT_ID, D.DEPT_NAME
  FROM SYS_USER U LEFT JOIN SYS_DEPT D ON U.DEPT_ID = D.DEPT_ID
 WHERE U.DEPT_ID IS NOT NULL ORDER BY U.DEPT_ID;
SELECT UR.USER_NAME, R.POST_ID, P.POST_NAME
  FROM SYS_USER_ROLE UR
  JOIN SYS_USER U ON UR.USER_ID = U.USER_ID
  JOIN SYS_ROLE R ON UR.ROLE_ID = R.ROLE_ID
  LEFT JOIN SYS_POST P ON R.POST_ID = P.POST_ID
 WHERE R.POST_ID IS NOT NULL;

-- ---------- 2. 执行清理（仅删 RuoYi 演示值，保留 create_by 非 admin 的真实数据） ----------
-- 判据：RuoYi 演示数据的 create_by 恒为 'admin' 且 del_flag='0' 且创建时间集中在框架初始化时刻。
--      若现场有非 admin 创建的行，NEVER 删（那是真实数据）。
DELETE FROM SYS_DEPT
 WHERE CREATE_BY = 'admin'
   AND DEPT_ID IN (SELECT DEPT_ID FROM BAK_SYS_DEPT_20261106
                    WHERE CREATE_BY = 'admin'
                      AND DEPT_NAME IN ('测试部门','董事长','总经理','法务','财务','行政','运维部','市场部'));
COMMIT;

DELETE FROM SYS_POST
 WHERE CREATE_BY = 'admin'
   AND POST_ID IN (SELECT POST_ID FROM BAK_SYS_POST_20261106
                    WHERE CREATE_BY = 'admin'
                      AND POST_NAME IN ('董事长','总经理','法务','财务','行政','CEO','CFO','CTO'));
COMMIT;

-- ---------- 3. 删除后核对（应只剩真实部门/岗位，或全空） ----------
SELECT 'SYS_DEPT' AS TBL, COUNT(*) AS ROWS FROM SYS_DEPT
UNION ALL SELECT 'SYS_POST', COUNT(*) FROM SYS_POST;

-- ---------- 4. 若误删，用 99-restore-after.sql 还原 ----------
