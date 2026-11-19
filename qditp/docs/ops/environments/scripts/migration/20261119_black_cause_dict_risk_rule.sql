-- =============================================================
-- itp_black_cause 码表新增：03=风控规则命中
-- 关联：IT-083 风险规则命中自动加黑定时任务
--   docs/tracking/IT-083-风险规则命中自动加黑定时任务.md
-- 用户裁定：2026-11-19「添加更符合现在要做的事情」
--
-- 变更内容：
--   SYS_DICT_DATA 增 DICT_CODE=129 / DICT_VALUE='03' / DICT_LABEL='风控规则命中'
--
-- 语义与安全边界：
--   - 自动解除（autoRelease）只扫 BLACK_CAUSE='01' 欠费类（欠费结清盘点），
--     03 不在其列 → 风控规则加黑 NEVER 被欠费结清误放行，语义正确；
--   - 综管台黑名单页「拉黑原因」下拉通过字典接口自动带出本项（DictUtils 缓存，
--     直接改库后需调 /system/dict/refreshCache 或重启 web-admin 生效）；
--   - 存量 BLACKLIST 行不变，新码只约束变更后产生的记录。
--
-- ⚠️ 执行前 MUST：
--   - 确认目标库 SYS_DICT_DATA 无 DICT_TYPE='itp_black_cause' AND DICT_VALUE='03'
--     （重复执行会造出两个同值码，页签下拉出现重复项）
-- =============================================================

INSERT INTO QDITP.SYS_DICT_DATA ("DICT_CODE", "DICT_SORT", "DICT_LABEL", "DICT_VALUE", "DICT_TYPE", "CSS_CLASS", "LIST_CLASS", "IS_DEFAULT", "STATUS", "CREATE_BY", "CREATE_TIME", "UPDATE_BY", "UPDATE_TIME", "REMARK")
SELECT 129, 4, '风控规则命中', '03', 'itp_black_cause', NULL, 'danger', 'N', '0', 'admin', SYSDATE, NULL, NULL, '风险规则自动加黑（黑名单直拉规则组定时任务），不可欠费结清自动解除'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM QDITP.SYS_DICT_DATA WHERE DICT_TYPE = 'itp_black_cause' AND DICT_VALUE = '03');

COMMIT;

-- ===================== 回滚 =====================
-- DELETE FROM QDITP.SYS_DICT_DATA WHERE DICT_TYPE='itp_black_cause' AND DICT_VALUE='03' AND DICT_CODE=129;
-- COMMIT;

-- ============================================================
-- 分库后环境（QDITP_PLAT）替换说明：
--   把全文 QDITP. 替换为 QDITP_PLAT. 即可（SYS_DICT_DATA 属平台域）。
-- ============================================================
