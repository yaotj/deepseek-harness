package com.chinasofti.huateng.gatetxnpay.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class GateTxnPayMapperSqlTest {

    private static final String RESOURCE = "mapper/GateTxnPayMapper.xml";

    private static final String NAMESPACE =
            "com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper.";

    private static final String[] OFFLINE_PENDING_STATEMENTS = {
            "selectOfflineFarePending",
            "updateOfflineFareRecalculated",
            "updateOfflineFarePendingMsg"};

    private static final String[] PARTITION_KEYED_UPDATES = {
            "updateStatusFromPending",
            "updateStatusIfProcessing",
            "convergeDebitStatus",
            "updateStatus",
            "updateOriginalFareIfNull",
            "updateOfflineFareRecalculated",
            "updateOfflineFarePendingMsg"};

    private static final Pattern XML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    private static final Pattern OFFLINE_PENDING_LITERAL =
            Pattern.compile("'OFFLINE_FARE_PENDING'");


    private static final String[] OFFLINE_PENDING_EXCLUSIONS = {
            "selectRecentUnpaidCandidates",
            "prepareRecentRetry",
            "selectUserRetryCandidates",
            "prepareUserRetry",
            "selectOneSideInCandidates",
            "selectOneSideOutCandidates"};


    private static final int ALLOWED_OFFLINE_PENDING_LITERALS = 9;

    private final Configuration configuration = parseMapper();

    @Test
    void unsettledQueriesUseBlacklistWithExplicitNull() {
        String expected = "(DEBIT_STATUS IS NULL OR DEBIT_STATUS != 'SUCCESS')";
        assertTrue(sqlOf("countFailedOrder").contains(expected),
                "解约未结清统计 MUST 显式兼容 NULL，漏掉会把脏数据判成已结清并放行解约");
        assertTrue(sqlOf("countUnsettledOrderByCardId").contains(expected),
                "黑名单解除判定 MUST 与 countFailedOrder 同口径，否则欠费乘客会被放行");
    }

    @Test
    void userAccInfoUsesWhitelistBuckets() {
        String sql = sqlOf("countUserAccInfo");
        assertTrue(sql.contains("DEBIT_STATUS IN ('INIT', 'PROCESSING')"), "未支付档 MUST 是白名单");
        assertTrue(sql.contains("DEBIT_STATUS IN ('FAIL', 'RETRY')"), "失败档 MUST 是白名单");
        assertFalse(sql.contains("!= 'SUCCESS'"),
                "IF8A-35 NEVER 退回黑名单：CLOSED 与 NULL 两档刻意不计入");
        assertTrue(sql.contains("TXN_DATE >= ?"),
                "联机查询 MUST 带 TXN_DATE 下限做分区裁剪，绑定值形如 '20260608' 的字符串");
    }

    @Test
    void debitResultFilterRendersBothBranches() {
        assertTrue(sqlWith("selectTransList", "debitRequestResult", "0")
                        .contains("DEBIT_STATUS = 'SUCCESS'"),
                "debitRequestResult=0 MUST 过滤出成功单");
        assertTrue(sqlWith("countTransList", "debitRequestResult", "1")
                        .contains("(DEBIT_STATUS IS NULL OR DEBIT_STATUS != 'SUCCESS')"),
                "debitRequestResult=1 MUST 与解约口径一致，否则同一笔在 APP 页签里凭空消失");
    }

    @Test
    void debitResultFilterIsNotSilentlyDisabled() {
        String unfiltered = sqlOf("countTransList");
        assertFalse(unfiltered.contains("'SUCCESS'"),
                "不传 debitRequestResult 时 MUST 不拼过滤条件");
        assertTrue(sqlWith("countTransList", "debitRequestResult", "0").contains("'SUCCESS'"),
                "传 0 却没拼出条件，说明 test 里的字符串被当成 char 比较了");
    }


    @Test
    void issueChannelFilterIsOptionalAndNarrowsWhenPassed() {
        for (String id : new String[] {"selectTransList", "countTransList"}) {
            assertFalse(sqlOf(id).contains("ISSUE_CHANNEL_CODE = ?"),
                    id + " 不传 issueChannelCode 时 MUST 不拼渠道条件，否则打断现有全渠道口径");
            assertTrue(sqlWith(id, "issueChannelCode", "07").contains("ISSUE_CHANNEL_CODE = ?"),
                    id + " 传了 issueChannelCode 却没拼出条件，支付宝出行记录会混进非支付宝的单");
        }
    }

    @Test
    void statusTransitionsKeepExplicitWhitelist() {
        String pending = sqlOf("updateStatusFromPending");
        assertTrue(pending.contains("DEBIT_STATUS IN ('INIT', 'RETRY')"),
                "待支付态推进的前置 MUST 是 INIT / RETRY");
        assertFalse(pending.contains("PROCESSING"),
                "NEVER 把 PROCESSING 放进来：已受理单被降级回 RETRY 后会被 retryPay 重复扣款");
        assertTrue(sqlOf("updateStatusIfProcessing").contains("DEBIT_STATUS = 'PROCESSING'"),
                "已受理单收敛的前置 MUST 恰好是 PROCESSING");
        String convergeSuccess = sqlOf("convergeDebitStatus");
        assertTrue(convergeSuccess.contains("DEBIT_STATUS IN ('INIT', 'PROCESSING', 'RETRY', 'FAIL')"),
                "成功方向的回调收敛 MUST 覆盖三个中间态与 FAIL（IT-081）：只覆盖中间态时，"
                        + "首次扣费被判 FAIL 而支付中心随后回调成功的单会永久卡住");
        assertTrue(convergeSuccess.contains("DEBIT_FAIL_CODE = NULL"),
                "成功收敛 MUST 清 DEBIT_FAIL_CODE，否则成功单仍带历史失败归因");
        assertFalse(convergeSuccess.contains("'FAIL', 'SUCCESS')"),
                "SUCCESS NEVER 进前置白名单，否则重复回调会改写终态");
        assertTrue(convergeSuccess.contains(
                        "DEBIT_FAIL_CODE = NULL, DEBIT_FAIL_MSG = NULL, UPDATE_TIME = SYSTIMESTAMP"),
                "清失败码两个 SET 片段 MUST 拼接完整，逗号错位会让 UPDATE 语法失败");

        String convergeFail = sqlWith("convergeDebitStatus", "debitStatus", "FAIL");
        assertTrue(convergeFail.contains("DEBIT_STATUS IN ('INIT', 'PROCESSING', 'RETRY')"),
                "失败方向的回调收敛 MUST 只覆盖三个中间态，保留「FAIL 不被失败通知重写」的口径");
        assertFalse(convergeFail.contains("'FAIL'"),
                "失败方向 NEVER 出现 FAIL 字面量：FAIL 已被失败判定过，重写会掩盖人工处置");
    }

    @Test
    void offlinePendingConditionsAlwaysComeInPairs() {
        for (String id : OFFLINE_PENDING_STATEMENTS) {
            String sql = sqlOf(id);
            assertTrue(sql.contains("DEBIT_STATUS = 'INIT'"), id + " 丢了 DEBIT_STATUS = 'INIT'");
            assertTrue(sql.contains("DISCOUNT_CALC_STATUS = 'OFFLINE_FARE_PENDING'"),
                    id + " 丢了 DISCOUNT_CALC_STATUS = 'OFFLINE_FARE_PENDING'");
        }
    }

    @Test
    void offlinePendingUpdatesNeverTouchDebitStatus() {
        for (String id : new String[] {"updateOfflineFareRecalculated", "updateOfflineFarePendingMsg"}) {
            String setClause = sqlOf(id).replaceAll("(?i)\\s+WHERE\\s+.*$", "");
            assertFalse(setClause.contains("DEBIT_STATUS"),
                    id + " 的 SET 子句 NEVER 出现 DEBIT_STATUS");
        }
    }

    @Test
    void offlinePendingLiteralStaysInAllowedStatements() {
        String withoutComments = XML_COMMENT.matcher(readMapperSource()).replaceAll("");
        Matcher matcher = OFFLINE_PENDING_LITERAL.matcher(withoutComments);
        int literals = 0;
        while (matcher.find()) {
            literals++;
        }
        assertEquals(ALLOWED_OFFLINE_PENDING_LITERALS, literals,
                "OFFLINE_FARE_PENDING 字面量只允许出现在捞单、抢占回写、失败留痕三条语句（包含），"
                        + "以及近 N 分钟未扣费那条任务的扫表与抢占两条语句（排除）里");
    }

    @Test
    void offlinePendingExcludedFromAllDebitEntrypoints() {
        for (String id : OFFLINE_PENDING_EXCLUSIONS) {
            String sql = sqlOf(id);
            assertTrue(sql.contains("DISCOUNT_CALC_STATUS IS NULL")
                            && sql.contains("'OFFLINE_FARE_PENDING'"),
                    id + " MUST 排除离线码待重算态（金额未算准，扣下去就是扣错钱）");
            assertFalse(sql.contains("DISCOUNT_CALC_STATUS = 'OFFLINE_FARE_PENDING'"),
                    id + " 的谓词方向反了：应为排除而不是只捞待重算的那批");
        }
    }

    @Test
    void recentRetryClaimNeverTouchesRetryAccounting() {
        String setClause = sqlOf("prepareRecentRetry").replaceAll("(?i)\\s+WHERE\\s+.*$", "");
        assertFalse(setClause.contains("DEBIT_RETRY_TIMES"),
                "prepareRecentRetry 的 SET 子句 NEVER 出现 DEBIT_RETRY_TIMES");
        assertFalse(setClause.contains("DEBIT_NEXT_RETRY_TIME"),
                "prepareRecentRetry 的 SET 子句 NEVER 出现 DEBIT_NEXT_RETRY_TIME");
        assertTrue(setClause.contains("DEBIT_STATUS = 'RETRY'"),
                "INIT / FAIL MUST 归一成 RETRY，否则 updateStatusFromPending 的 CAS 白名单回写不进去");
    }

    @Test
    void keyedUpdatesCarryPartitionKey() {
        for (String id : PARTITION_KEYED_UPDATES) {
            assertTrue(sqlOf(id).contains("TXN_DATE = ?"),
                    id + " 丢了分区键 TXN_DATE，会扫全部月分区并可能改错行");
        }
    }

    @Test
    void recalculatedUpdateKeepsCasPrecondition() {
        String sql = sqlOf("updateOfflineFareRecalculated");
        assertTrue(sql.matches("(?i).*WHERE\\s+ORDER_NO\\s*=\\s*\\?\\s+AND\\s+TXN_DATE\\s*=\\s*\\?.*"),
                "抢占条件 MUST 以 ORDER_NO + TXN_DATE 定位单行");
        assertTrue(sqlOf("updateOriginalFareIfNull").contains("ORIGINAL_FARE IS NULL"),
                "原价回填 MUST 保留 IS NULL：它既是幂等条件也防止篡改历史账单快照");
    }

    @Test
    void noStatementRendersDanglingWhereAnd() {
        Pattern danglingAnd = Pattern.compile("where\\s+and\\b", Pattern.CASE_INSENSITIVE);
        for (String id : distinctStatementIds()) {
            String sql = sqlOf(id);
            assertFalse(danglingAnd.matcher(sql).find(), id + " 渲染出了 WHERE AND：" + sql);
        }
    }

    @Test
    void renderedSqlCarriesNoInlineComment() {
        for (String id : distinctStatementIds()) {
            String sql = sqlOf(id);
            assertFalse(sql.contains("/*"), id + " 的 SQL 正文里有块注释，Druid WallFilter 会判成注入");
            assertFalse(sql.contains("--"), id + " 的 SQL 正文里有行注释，Druid WallFilter 会判成注入");
        }
    }

    private Set<String> distinctStatementIds() {
        Set<String> ids = new HashSet<>();
        for (MappedStatement statement : configuration.getMappedStatements()) {
            ids.add(statement.getId().substring(statement.getId().lastIndexOf('.') + 1));
        }
        return ids;
    }

    private String sqlOf(String statementId) {
        return render(statementId, baseParams());
    }

    private String sqlWith(String statementId, String key, Object value) {
        Map<String, Object> params = baseParams();
        params.put(key, value);
        return render(statementId, params);
    }

    private String render(String statementId, Map<String, Object> params) {
        String sql = configuration.getMappedStatement(NAMESPACE + statementId)
                .getBoundSql(params)
                .getSql();
        return sql.replaceAll("\\s+", " ").trim();
    }

    private static Map<String, Object> baseParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("orderNo", "GT20260913000000000123456");
        params.put("orderNos", List.of("GT20260913000000000123456"));
        params.put("cardId", "C1");
        params.put("cardIdList", List.of("C1"));
        params.put("cardType", "04");
        params.put("cardTypeList", List.of("0445"));
        params.put("thirdUserId", "U1");
        params.put("trxType", "02");
        params.put("outTime", "20260913120000");
        params.put("ticketTransSeq", "1");
        params.put("deviceId", "D1");
        params.put("txnDate", "20260913");
        params.put("startDate", "20260901");
        params.put("endDate", "20260913");
        params.put("ticketCode", "TC1");
        params.put("signChannelCode", "01");
        params.put("debitStatus", "SUCCESS");
        params.put("discountCalcStatus", "SUCCESS");
        params.put("discountCalcMsg", "ok");
        params.put("remark", "r");
        params.put("originalFare", 300);
        params.put("limit", 100);
        params.put("offset", 0);
        params.put("codes", List.of("0622"));
        params.put("requestTime", null);
        params.put("request", statisticsRequest());
        return params;
    }

    private static Map<String, Object> statisticsRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("thirdUserId", "U1");
        request.put("cardIdList", List.of("C1"));
        request.put("cardTypeList", List.of("0445"));
        request.put("startDate", "20260901");
        request.put("endDate", "20260913");
        return request;
    }

    private static String readMapperSource() {
        try (InputStream in = Resources.getResourceAsStream(RESOURCE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("读取 " + RESOURCE + " 源文本失败", e);
        }
    }

    private static Configuration parseMapper() {
        Configuration cfg = new Configuration();
        try (InputStream in = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(in, cfg, RESOURCE, cfg.getSqlFragments()).parse();
        } catch (Exception e) {
            throw new IllegalStateException("解析 " + RESOURCE + " 失败，服务启动时同样会挂", e);
        }
        return cfg;
    }
}
