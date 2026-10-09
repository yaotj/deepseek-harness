/*
 * BLACKLIST.STATUS 为 NULL 的行回填为 ACTIVE。
 *
 * 为什么会有第二次回填（blacklist-release-two-phase-migration.sql 里已经回填过一次）：
 * 两阶段解除改造给 BLACKLIST 加 STATUS 列时，虽然把该列写进了 insert 语句的列清单，
 * 但 BlacklistServiceImpl.buildBlacklist 漏了赋初值 —— Oracle 的列 DEFAULT 'ACTIVE'
 * 只在「该列不出现在 INSERT 列表里」时才生效，显式传 null 就真的落 null。
 * 于是那之后新加黑的行 STATUS 全是 NULL，而三条读语句都带 STATUS = 'ACTIVE' 谓词：
 *   selectPendingChannelSync —— 加黑通知补偿永久扫不到该行，最后一次推失败即永久丢；
 *   markReleasing            —— 解除阶段一 CAS 恒影响 0 行，这张卡再也解不掉；
 *   selectForInspect         —— 可解除性盘点永远看不到它。
 * 三者都不报错、不告警，编译 / 单测 / xmllint 全都发现不了（2026-09-18 端到端实测暴露）。
 *
 * 代码侧已修（buildBlacklist 显式 setStatus(STATUS_ACTIVE)，blacklist-server 2.0.27 起），
 * 本脚本只负责把缺陷存续期间落下的 NULL 行补回来。
 *
 * 幂等：可重复执行，第二次起影响 0 行。
 * 回查：执行后 SELECT COUNT(*) FROM BLACKLIST WHERE STATUS IS NULL 必须为 0。
 */
UPDATE BLACKLIST SET STATUS = 'ACTIVE' WHERE STATUS IS NULL;

COMMIT;
