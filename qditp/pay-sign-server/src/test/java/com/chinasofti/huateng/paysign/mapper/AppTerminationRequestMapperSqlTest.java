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

/**
 * 锁定 {@code AppTerminationRequestMapper.xml} 里 6 条 CAS 的前置条件与副作用列。
 *
 * <p>存在理由：解约状态机的<b>并发保证只在这些 WHERE 里</b>，Java 侧的白名单枚举只做解析与文档化。
 * 一旦某条 CAS 的前置条件被删掉（或被拆成多条无 CAS 的 update），SQL 依然语法合法、编译与其它单测
 * 全绿，只会在生产上表现为「已超时打成 FAILED 的申请被迟到回调改成 SUCCESS，APP 收到两条相反通知」——
 * 2026-09-12 之前主收口路径就是那样。
 *
 * <p>本测试不连数据库，只用 MyBatis 自己的 {@link XMLMapperBuilder} 离线解析这份 XML 并取渲染后的
 * SQL 文本，本机与 CI 都能无条件运行。
 *
 * <p><b>NEVER 把断言放宽成「只判断包含 TERMINATION_STATUS」</b>：那就失去了前置条件保护。
 */
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

    /**
     * 每条 CAS 的 WHERE MUST 同时带主键与前置状态。
     *
     * <p>只带主键 = 无条件覆盖；只带状态 = 全表更新。两者都必须在。
     */
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

    /**
     * 三条「产生新通知」的语句 MUST 把通知轮次归零。
     *
     * <p>沿用残留轮次会让新通知一上来就接近 {@code app.notify.max-retry-count} 上限，
     * 首次投递失败后补偿再也扫不到它（{@code selectCompensableNotify} 按
     * {@code NVL(NOTIFY_RETRY_COUNT,0) < maxRetryCount} 过滤）。
     */
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

    /**
     * {@code markSuccess} 与 {@code rejectScanning} MUST 落完成时间。
     *
     * <p>2026-09-12 之前完成时间由单独的 {@code updateCompleteTime} 写，那条语句没有 CAS；
     * 合成一条之后 COMPLETE_TIME 与状态同生共死，NEVER 再拆开。
     */
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

    /**
     * 整份 XML 里 {@code TERMINATION_STATUS} 的**赋值**次数固定为 7 处，防止有人悄悄新增
     * 一条不带 CAS 的状态写语句 —— 那种语句加进来编译与上面按名单的断言都不会红。
     *
     * <p>7 = markScanning / revertScanningToPending / rejectPending / markSuccess /
     * rejectScanning / expireScanning / reactivateFailed。
     * {@code updateStatus} 与 {@code updateFailReason} 用的是 {@code #{status}} 占位符、不是字面量，
     * 因此不计入；它们已无解约收口调用方，见 ArchUnit 门禁。
     *
     * <p>数字变了 **MUST 先确认新语句带 CAS**，NEVER 直接改这个期望值。
     */
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

    /**
     * 两条留痕语句是「解约结果矛盾」的唯一落库手段，三条不变量都 MUST 在，且 CAS 前置状态 MUST 相反。
     *
     * <p>它们本身不改状态，因此上面按状态名单做的断言覆盖不到，必须单独钉：
     * 少了 CAS 会把任意状态的行都标成需人工；少了 {@code INSTR} 幂等闸门，支付中心每次重推
     * 都再前置拼一遍标记，512 字符的 {@code FAIL_REASON} 很快被挤满、原始原因反而被截掉；
     * 一旦它们写了 {@code TERMINATION_STATUS} 或 {@code NOTIFY_}，就等于替业务决定了「要不要反悔」。
     *
     * <p>两条的前置状态 MUST 分别是 {@code FAILED} 与 {@code SUCCESS}：写反等于在错误的那一半留痕。
     */
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

    /**
     * 通道清理补偿的扫表 SQL MUST 同时满足四条不变量（{@code docs/domain/outbox.md} §二的四个坑）。
     *
     * <p>这条 SQL 是 2026-09-12 才接上调用方的（此前 5 条 CHANNEL_SYNC 语句全是死代码），
     * 四个坑任缺一个都不会报错、只会让某一类记录**永远补不回来**：
     * <ul>
     *   <li>{@code ROWNUM} 与 {@code ORDER BY} 同层 ⇒ 先截断再排序，最旧的记录可能永远排不进这一批；</li>
     *   <li>重试次数不套 {@code NVL} ⇒ 该列为 NULL 的行比较结果 UNKNOWN、一条都捞不到；</li>
     *   <li>状态改成「不等于 SUCCESS」的黑名单 ⇒ 把 NULL 的历史行与 MANUAL 一起捞进来，
     *       前者等于凭空再删一次通道，后者等于覆盖人工结论；</li>
     *   <li>{@code TERMINATION_STATUS} 放宽到含 FAILED ⇒ 给没有通道要删的申请发起清理。</li>
     * </ul>
     */
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

    /**
     * 三条会写 {@code CHANNEL_SYNC_STATUS} 的 update MUST 带 MANUAL 闸门，否则人工处理完又被补偿改回去。
     *
     * <p>{@code markChannelSyncManual} 自己是**进入** MANUAL 的那一条，前置条件是 FAILED，不在此列。
     */
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
