package com.chinasofti.huateng.paysign.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 护栏：离线渲染 XML，钉住 6 条 CAS 的前置状态、通知轮次归零、两条留痕语句与通道清理扫表的四个坑。 */
class AppTerminationRequestMapperSqlTest {

    private static final String RESOURCE = "mapper/AppTerminationRequestMapper.xml";

    private static final String NAMESPACE =
            "com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper.";

    /** 语句名 -> 该语句 WHERE 里 MUST 出现的前置状态。删任一条即等于去掉并发保证。 */
    private static final String[][] CAS_PRECONDITIONS = {
            {"markScanning", "PENDING"},
            {"rejectPending", "PENDING"},
            {"revertScanningToPending", "SCANNING"},
            {"markSuccess", "SCANNING"},
            {"rejectScanning", "SCANNING"},
            {"expireScanning", "SCANNING"},
            {"reactivateFailed", "FAILED"},
            {"initChannelSyncPending", "SUCCESS"}};

    /** 每条 CAS 的 WHERE MUST 同时带主键与前置状态。 */
    @Test
    void everyCasStatementKeepsBothKeyAndPreconditionInWhere() {
        Configuration configuration = parseMapper();

        for (String[] pair : CAS_PRECONDITIONS) {
            String id = pair[0];
            String expectedStatus = pair[1];
            String sql = boundSql(configuration, id);
            String where = whereClauseOf(sql, id);

            assertTrue(where.contains("REQUEST_SIGN_SEQ"),
                    id + " 的 WHERE MUST 带 REQUEST_SIGN_SEQ，否则是全表更新");
            assertTrue(where.contains("TERMINATION_STATUS = '" + expectedStatus + "'"),
                    id + " 的 WHERE MUST 带前置状态 TERMINATION_STATUS = '" + expectedStatus
                            + "'，这是该语句唯一的并发保证；实际渲染：" + where);
        }
    }

    /** 三条「产生新通知」的语句 MUST 把通知轮次归零。 */
    @Test
    void statementsProducingNewNotificationResetRetryBudget() {
        Configuration configuration = parseMapper();

        for (String id : new String[]{"markSuccess", "rejectScanning", "rejectPending", "expireScanning"}) {
            String sql = boundSql(configuration, id);
            assertTrue(sql.contains("NOTIFY_STATUS = 'PENDING'"),
                    id + " MUST 把 NOTIFY_STATUS 落成 PENDING，否则这条通知没人发");
            assertTrue(sql.contains("NOTIFY_RETRY_COUNT = 0"),
                    id + " MUST 把 NOTIFY_RETRY_COUNT 归零，否则新通知继承旧轮次、补偿扫不到");
        }
    }

    /** {@code markSuccess} 与 {@code rejectScanning} MUST 落完成时间。 */
    @Test
    void scanningClosureWritesCompleteTimeInTheSameStatement() {
        Configuration configuration = parseMapper();

        assertTrue(boundSql(configuration, "markSuccess").contains("COMPLETE_TIME"),
                "markSuccess MUST 在同一条语句里落 COMPLETE_TIME");
        assertTrue(boundSql(configuration, "rejectScanning").contains("COMPLETE_TIME"),
                "rejectScanning MUST 在同一条语句里落 COMPLETE_TIME");
        assertTrue(boundSql(configuration, "rejectScanning").contains("FAIL_REASON"),
                "rejectScanning MUST 记失败原因，否则运维无法区分「对方答复失败」与「我方超时」");
    }

    /** 通知补偿的终态白名单 NEVER 去掉：PENDING / SCANNING 还没有可发的通知。 */
    @Test
    void compensableNotifyKeepsTerminalWhitelist() {
        String sql = boundSql(parseMapper(), "selectCompensableNotify");

        assertTrue(sql.replaceAll("\\s+", " ").contains("TERMINATION_STATUS in ('SUCCESS', 'FAILED')"),
                "NEVER 去掉终态白名单，否则滞留的 PENDING 会被当成「成功通知丢了」发出假解约成功通知");
    }

    /** 防止 include / 条件标签改动后渲染出 `where and` 这种语法错误。 */
    @Test
    void noStatementRendersWhereAnd() {
        Configuration configuration = parseMapper();

        for (String id : configuration.getMappedStatementNames()) {
            if (!id.startsWith(NAMESPACE)) {
                continue;
            }
            String normalized = boundSql(configuration, id.substring(NAMESPACE.length()))
                    .replaceAll("\\s+", " ").toLowerCase();
            assertFalse(normalized.contains("where and"), id + " 渲染出了 `where and`");
        }
    }

    /** 整份 XML 里 {@code TERMINATION_STATUS} 的**赋值**次数固定为 7 处，防止有人悄悄新增。 */
    @Test
    void terminationStatusAssignmentCountIsPinned() {
        String xml = readResourceStrippingComments();
        Matcher matcher = Pattern.compile("set\\s+TERMINATION_STATUS\\s*=\\s*'", Pattern.CASE_INSENSITIVE)
                .matcher(xml);

        int assignments = 0;
        while (matcher.find()) {
            assignments++;
        }
        assertEquals(7, assignments,
                "TERMINATION_STATUS 的字面量赋值语句数变了，MUST 先确认新语句的 WHERE 带前置状态");
    }

    /** 两条留痕语句是「解约结果矛盾」的唯一落库手段，三条不变量都 MUST 在，且 CAS 前置状态 MUST 相反。 */
    @Test
    void manualReviewMarksAreAppendOnlyIdempotentAndStatusScoped() {
        Configuration configuration = parseMapper();

        for (String[] pair : new String[][]{
                {"markConflictForManualReview", "FAILED"},
                {"markFailureConflictForManualReview", "SUCCESS"}}) {
            String id = pair[0];
            String expectedStatus = pair[1];
            String sql = boundSql(configuration, id).replaceAll("\\s+", " ");
            String where = whereClauseOf(sql, id);

            assertTrue(where.contains("REQUEST_SIGN_SEQ"), id + " MUST 带主键，否则是全表更新");
            assertTrue(where.contains("TERMINATION_STATUS = '" + expectedStatus + "'"),
                    id + " 的 CAS 前置状态 MUST 是 " + expectedStatus + "；实际渲染：" + where);
            assertTrue(where.contains("INSTR("),
                    id + " MUST 带 INSTR 幂等闸门，否则重推会把标记反复前置拼接、挤掉原始失败原因；实际渲染：" + where);
            assertTrue(sql.contains("SUBSTR(") && sql.contains(", 1, 512)"),
                    id + " MUST 截断到 FAIL_REASON 的 512 字符列长，否则超长直接抛 ORA-12899");

            String setClause = sql.substring(sql.toLowerCase().indexOf("set "),
                    sql.toLowerCase().lastIndexOf("where "));
            assertFalse(setClause.contains("TERMINATION_STATUS"),
                    id + " NEVER 改状态：留痕不等于订正；实际 SET：" + setClause);
            assertFalse(setClause.contains("NOTIFY_"),
                    id + " NEVER 动 NOTIFY_*：要不要重发通知是业务裁决；实际 SET：" + setClause);
        }
    }

    /** 通道清理补偿的扫表 SQL MUST 同时满足四条不变量（{@code docs/domain/outbox.md} §二的四个坑）。 */
    @Test
    void channelSyncCompensationScanKeepsAllFourInvariants() {
        String sql = boundSql(parseMapper(), "selectCompensableChannelSync").replaceAll("\\s+", " ");
        String lower = sql.toLowerCase();

        assertTrue(lower.indexOf("order by") < lower.lastIndexOf("rownum"),
                "ROWNUM MUST 套在已排序子查询的外层，否则先截断再排序；实际渲染：" + sql);
        assertTrue(sql.contains("NVL(CHANNEL_SYNC_RETRY_COUNT, 0) <"),
                "重试次数比较 MUST 套 NVL，否则该列为 NULL 的行一条都捞不到；实际渲染：" + sql);
        assertTrue(sql.contains("TERMINATION_STATUS = 'SUCCESS'"),
                "MUST 只捞解约成功的申请：解约失败的没有通道要删；实际渲染：" + sql);
        assertTrue(sql.contains("CHANNEL_SYNC_STATUS = 'FAILED'")
                        && sql.contains("CHANNEL_SYNC_STATUS = 'PENDING'"),
                "状态 MUST 用 FAILED / 滞留 PENDING 的白名单，NEVER 写成「不等于 SUCCESS」的黑名单；实际渲染：" + sql);
        assertFalse(sql.contains("'MANUAL'"),
                "MANUAL 是终态，NEVER 出现在扫表 SQL 里（哪怕是取反），否则人工结论会被自动流程覆盖；实际渲染：" + sql);
        assertTrue(sql.contains("NVL(CHANNEL_SYNC_TIME, CREATE_TIME)"),
                "滞留判断与排序都 MUST 走 NVL(CHANNEL_SYNC_TIME, CREATE_TIME)："
                        + "PENDING 行该列为 NULL，直接比较结果 UNKNOWN、排序也会被甩到最后；实际渲染：" + sql);
    }

    /** 三条会写 {@code CHANNEL_SYNC_STATUS} 的 update MUST 带 MANUAL 闸门，否则人工处理完又被补偿改回去。 */
    @Test
    void channelSyncWritesNeverOverwriteManualOutcome() {
        Configuration configuration = parseMapper();

        for (String id : new String[]{"updateChannelSyncStatus", "increaseChannelSyncRetryCount"}) {
            String where = whereClauseOf(boundSql(configuration, id), id);
            assertTrue(where.contains("NVL(CHANNEL_SYNC_STATUS, 'X') != 'MANUAL'"),
                    id + " MUST 带 MANUAL 闸门且 NVL 兜底，否则人工结论会被覆盖、历史 NULL 行会被漏改；实际渲染：" + where);
        }

        String manualWhere = whereClauseOf(boundSql(configuration, "markChannelSyncManual"), "markChannelSyncManual");
        assertTrue(manualWhere.contains("CHANNEL_SYNC_STATUS = 'FAILED'"),
                "markChannelSyncManual MUST 只从 FAILED 转 MANUAL，否则会把刚成功的行标成需人工；实际渲染：" + manualWhere);
    }

    private Configuration parseMapper() {
        try (InputStream in = Resources.getResourceAsStream(RESOURCE)) {
            Configuration configuration = new Configuration();
            new XMLMapperBuilder(in, configuration, RESOURCE, configuration.getSqlFragments()).parse();
            return configuration;
        } catch (Exception e) {
            throw new IllegalStateException("解析 " + RESOURCE + " 失败，服务启动时同样会挂", e);
        }
    }

    private String boundSql(Configuration configuration, String statementId) {
        return configuration.getMappedStatement(NAMESPACE + statementId)
                .getBoundSql(new HashMap<String, Object>())
                .getSql();
    }

    /** 取 WHERE 之后的片段；断言前置条件时只看 WHERE，避免把 SET 里的同名列误当成前置条件。 */
    private String whereClauseOf(String sql, String statementId) {
        String normalized = sql.replaceAll("\\s+", " ");
        int idx = normalized.toLowerCase().lastIndexOf("where ");
        assertTrue(idx >= 0, statementId + " 没有 WHERE，等于全表更新");
        return normalized.substring(idx);
    }

    private String readResourceStrippingComments() {
        try (InputStream in = Resources.getResourceAsStream(RESOURCE)) {
            String xml = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return xml.replaceAll("(?s)<!--.*?-->", "");
        } catch (Exception e) {
            throw new IllegalStateException("读取 " + RESOURCE + " 失败", e);
        }
    }
}
