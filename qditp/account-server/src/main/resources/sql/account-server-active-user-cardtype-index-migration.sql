-- ADR-D49 ① 的开户防重唯一索引。此前只写在 account-server-schema.sql 里、没有独立迁移脚本，
-- 而 *-schema.sql 只服务「新建库」，对已存在的库等于没写（AGENTS.md §8「一天撞三次」的第 ③ 条）。
-- 没有这个索引时 AccountRegistrationServiceImpl 的前置查重与 INSERT 之间有窗口：两条并发 IF8A-01
-- 或 APP 超时重推会双双通过查重、双双落库，用户拿到两张有效卡，而 handleDuplicateRegistration
-- 那段 DuplicateKeyException 兜底永远走不到 —— 编译、单测、xmllint 全都发现不了。
-- 2026-09-14 已在 AFCITPDB 执行并回查（UNIQUE / FUNCTION-BASED NORMAL / VALID / PARTITIONED=NO）。
--
-- 两处 NEVER：
-- ① NEVER 简化成朴素的 UNIQUE (THIRD_USER_ID, CARD_TYPE)。两个 CASE 刻意把 COMPANION_FLAG 为
--    Y（同行票）/ C（第三方代开）的行排除在唯一性之外，那类票按业务定义「每次都给新卡」；
--    改朴素两列会让这些合法请求的第二张卡直接 INSERT 失败。查重侧 UserItpRegInfoMapper.xml 的
--    selectActiveByThirdUserIdAndCardType MUST 与本谓词逐字对齐。
-- ② NEVER 顺手加 LOCAL。USER_ITP_REG_INFO 按 THIRD_USER_ID 派生值 LIST 分区，而键是两个 CASE
--    表达式、不是分区键，Oracle 只允许 GLOBAL，加 LOCAL 报 ORA-14039。
--
-- 在新库上执行前 MUST 先按索引的确切谓词做 ORA-01452 前置统计，有重复只能与业务定归属、NEVER 删行
-- （卡号可能已发给用户）：
--   SELECT COUNT(*) AS TOTAL,
--          COUNT(DISTINCT THIRD_USER_ID || '#' || CARD_TYPE) AS DISTINCT_KEY
--     FROM USER_ITP_REG_INFO
--    WHERE DEL_YN = 1
--      AND NVL(COMPANION_FLAG, 'N') NOT IN ('Y', 'C');
--
-- 经 mcp_database_qd 执行时 MUST 包一层 PL/SQL —— 它的 SQL 解析器拒绝带 CASE 的 CREATE INDEX
-- （McpSqlValidationException），内层单引号写两个：
--   BEGIN EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UK_UIRI_ACTIVE_USER_CARDTYPE ON USER_ITP_REG_INFO (CASE WHEN DEL_YN = 1 AND NVL(COMPANION_FLAG, ''N'') NOT IN (''Y'', ''C'') THEN THIRD_USER_ID END, CASE WHEN DEL_YN = 1 AND NVL(COMPANION_FLAG, ''N'') NOT IN (''Y'', ''C'') THEN CARD_TYPE END)'; END;
CREATE UNIQUE INDEX UK_UIRI_ACTIVE_USER_CARDTYPE
    ON USER_ITP_REG_INFO (
        CASE WHEN DEL_YN = 1 AND NVL(COMPANION_FLAG, 'N') NOT IN ('Y', 'C') THEN THIRD_USER_ID END,
        CASE WHEN DEL_YN = 1 AND NVL(COMPANION_FLAG, 'N') NOT IN ('Y', 'C') THEN CARD_TYPE END
    );
