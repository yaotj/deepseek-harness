-- 回滚 Option A：删除保真镜像表（不可恢复，执行前确认已留存 raw.json/csv）
DROP INDEX idx_gl_legacy_rule;
DROP INDEX idx_gl_legacy_user;
DROP TABLE GRAYLIST_LEGACY PURGE;
COMMIT;
