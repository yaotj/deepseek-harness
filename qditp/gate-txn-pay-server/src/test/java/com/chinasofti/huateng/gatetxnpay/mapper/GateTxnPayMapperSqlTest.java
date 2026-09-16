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

/**
 * 钉住 {@code GateTxnPayMapper.xml} 里三类最容易被重构改坏、且改坏后编译与启动都不报错的口径。
 *
 * <p>① {@code DEBIT_STATUS} 有三套并存判据，各有各的理由，NEVER 顺手统一：
 * 解约与黑名单解除用**黑名单**（{@code IS NULL OR != 'SUCCESS'}，Oracle 三值逻辑下漏掉
 * {@code IS NULL} 会把脏数据判成已结清、放行解约）；IF8A-35 用**白名单分档**
 * （{@code IN ('INIT','PROCESSING')} 与 {@code IN ('FAIL','RETRY')}，两数之和刻意 ≠ 非 SUCCESS 总数）；
 * 状态推进用**显式前置白名单**（终态不在其中，重复回调改不动终态）。
 *
 * <p>② {@code OFFLINE_FARE_PENDING} 在三条语句里必须与 {@code DEBIT_STATUS='INIT'} **成对**出现：
 * 捞单、抢占回写、失败留痕。任一处掉了另一半，补偿要么捞不到、要么并发重复扣款。
 *
 * <p>③ 每条按主键定位的写语句 MUST 带 {@code TXN_DATE}：它是月分区键，也是唯一索引的组成列，
 * 漏掉即退化成扫全部分区，且在跨月重名时会改错行。
 *
 * <p>本测试不连数据库，只用 MyBatis 自己的 {@link XMLMapperBuilder} 解析这份 XML 并取渲染后的
 * SQL 文本，因此本机与 CI 都能无条件运行。
 */
class GateTxnPayMapperSqlTest {

    private static final String RESOURCE = "mapper/GateTxnPayMapper.xml";

    private static final String NAMESPACE =
            "com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper.";

    /** 离线码补偿三条语句：两个状态条件 MUST 成对。 */
    private static final String[] OFFLINE_PENDING_STATEMENTS = {
            "selectOfflineFarePending",
            "updateOfflineFareRecalculated",
            "updateOfflineFarePendingMsg"};

    /** 按主键定位的写语句：MUST 带分区键 TXN_DATE。 */
    private static final String[] PARTITION_KEYED_UPDATES = {
            "updateStatusFromPending",
            "updateStatusIfProcessing",
            "convergeDebitStatus",
            "updateStatus",
            "updateOriginalFareIfNull",
            "updateOfflineFareRecalculated",
            "updateOfflineFarePendingMsg"};

    /** 剥离 XML 注释用：注释里为讲清语义会引用状态字面量，计数前 MUST 先去掉。 */
    private static final Pattern XML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    private static final Pattern OFFLINE_PENDING_LITERAL =
            Pattern.compile("'OFFLINE_FARE_PENDING'");

    /**
     * 剥离注释后，{@code 'OFFLINE_FARE_PENDING'} 字面量只允许出现 3 次，即上面三条语句各 1 次。
     * 多出来说明有人又抄了一份判据，改口径时必然漏改。
     */
    private static final int ALLOWED_OFFLINE_PENDING_LITERALS = 3;

    private final Configuration configuration = parseMapper();

    // ==================== ① DEBIT_STATUS 三套口径 ====================

    /** 解约校验与黑名单解除共用黑名单口径，两条 MUST 一字不差，否则同一笔订单两处结论相反。 */
    @Test
    void unsettledQueriesUseBlacklistWithExplicitNull() {
        String expected = "(DEBIT_STATUS IS NULL OR DEBIT_STATUS != 'SUCCESS')";
        assertTrue(sqlOf("countFailedOrder").contains(expected),
                "解约未结清统计 MUST 显式兼容 NULL，漏掉会把脏数据判成已结清并放行解约");
        assertTrue(sqlOf("countUnsettledOrderByCardId").contains(expected),
                "黑名单解除判定 MUST 与 countFailedOrder 同口径，否则欠费乘客会被放行");
    }

    /** IF8A-35 分档是白名单，且两档互斥、都不含 SUCCESS 与 CLOSED。 */
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

    /** IF8A-05 的 debitRequestResult 过滤：0 走 SUCCESS，1 走带 NULL 的黑名单。 */
    @Test
    void debitResultFilterRendersBothBranches() {
        assertTrue(sqlWith("selectTransList", "debitRequestResult", "0")
                        .contains("DEBIT_STATUS = 'SUCCESS'"),
                "debitRequestResult=0 MUST 过滤出成功单");
        assertTrue(sqlWith("countTransList", "debitRequestResult", "1")
                        .contains("(DEBIT_STATUS IS NULL OR DEBIT_STATUS != 'SUCCESS')"),
                "debitRequestResult=1 MUST 与解约口径一致，否则同一笔在 APP 页签里凭空消失");
    }

    /**
     * OGNL 里 {@code '0'} 是 char 字面量、与 String 比较恒为 false。
     * 片段写成单引号时整段过滤静默失效，只有渲染出来比对才发现得了。
     */
    @Test
    void debitResultFilterIsNotSilentlyDisabled() {
        String unfiltered = sqlOf("countTransList");
        assertFalse(unfiltered.contains("'SUCCESS'"),
                "不传 debitRequestResult 时 MUST 不拼过滤条件");
        assertTrue(sqlWith("countTransList", "debitRequestResult", "0").contains("'SUCCESS'"),
                "传 0 却没拼出条件，说明 test 里的字符串被当成 char 比较了");
    }

    /** 状态推进的前置状态是显式白名单，终态 NEVER 进白名单。 */
    @Test
    void statusTransitionsKeepExplicitWhitelist() {
        String pending = sqlOf("updateStatusFromPending");
        assertTrue(pending.contains("DEBIT_STATUS IN ('INIT', 'RETRY')"),
                "待支付态推进的前置 MUST 是 INIT / RETRY");
        assertFalse(pending.contains("PROCESSING"),
                "NEVER 把 PROCESSING 放进来：已受理单被降级回 RETRY 后会被 retryPay 重复扣款");
        assertTrue(sqlOf("updateStatusIfProcessing").contains("DEBIT_STATUS = 'PROCESSING'"),
                "已受理单收敛的前置 MUST 恰好是 PROCESSING");
        String converge = sqlOf("convergeDebitStatus");
        assertTrue(converge.contains("DEBIT_STATUS IN ('INIT', 'PROCESSING', 'RETRY')"),
                "回调收敛 MUST 覆盖三个中间态，漏掉 INIT / RETRY 会让订单永久卡中间态");
        assertFalse(converge.contains("'SUCCESS',") || converge.contains("'FAIL'"),
                "终态 NEVER 进前置白名单，否则重复回调会改写终态");
    }

    // ==================== ② OFFLINE_FARE_PENDING 成对 ====================

    @Test
    void offlinePendingConditionsAlwaysComeInPairs() {
        for (String id : OFFLINE_PENDING_STATEMENTS) {
            String sql = sqlOf(id);
            assertTrue(sql.contains("DEBIT_STATUS = 'INIT'"), id + " 丢了 DEBIT_STATUS = 'INIT'");
            assertTrue(sql.contains("DISCOUNT_CALC_STATUS = 'OFFLINE_FARE_PENDING'"),
                    id + " 丢了 DISCOUNT_CALC_STATUS = 'OFFLINE_FARE_PENDING'");
        }
    }

    /** 补偿链路 NEVER 在这两条 UPDATE 里改 DEBIT_STATUS：置 FAIL 补偿再也捞不到，置 SUCCESS 是资损。 */
    @Test
    void offlinePendingUpdatesNeverTouchDebitStatus() {
        for (String id : new String[] {"updateOfflineFareRecalculated", "updateOfflineFarePendingMsg"}) {
            String setClause = sqlOf(id).replaceAll("(?i)\\s+WHERE\\s+.*$", "");
            assertFalse(setClause.contains("DEBIT_STATUS"),
                    id + " 的 SET 子句 NEVER 出现 DEBIT_STATUS");
        }
    }

    @Test
    void offlinePendingLiteralStaysInThreeStatements() {
        String withoutComments = XML_COMMENT.matcher(readMapperSource()).replaceAll("");
        Matcher matcher = OFFLINE_PENDING_LITERAL.matcher(withoutComments);
        int literals = 0;
        while (matcher.find()) {
            literals++;
        }
        assertEquals(ALLOWED_OFFLINE_PENDING_LITERALS, literals,
                "OFFLINE_FARE_PENDING 字面量只允许出现在捞单、抢占回写、失败留痕三条语句里");
    }

    // ==================== ③ 分区键 ====================

    @Test
    void keyedUpdatesCarryPartitionKey() {
        for (String id : PARTITION_KEYED_UPDATES) {
            assertTrue(sqlOf(id).contains("TXN_DATE = ?"),
                    id + " 丢了分区键 TXN_DATE，会扫全部月分区并可能改错行");
        }
    }

    /** 抢占语义靠 WHERE 里的状态条件，返回 1 才代表抢到；去掉即退化成无条件覆盖。 */
    @Test
    void recalculatedUpdateKeepsCasPrecondition() {
        String sql = sqlOf("updateOfflineFareRecalculated");
        assertTrue(sql.matches("(?i).*WHERE\\s+ORDER_NO\\s*=\\s*\\?\\s+AND\\s+TXN_DATE\\s*=\\s*\\?.*"),
                "抢占条件 MUST 以 ORDER_NO + TXN_DATE 定位单行");
        assertTrue(sqlOf("updateOriginalFareIfNull").contains("ORIGINAL_FARE IS NULL"),
                "原价回填 MUST 保留 IS NULL：它既是幂等条件也防止篡改历史账单快照");
    }

    // ==================== 全局形状 ====================

    /** 渲染出 {@code WHERE AND} 只在运行时抛语法异常，因此对全部语句扫一遍。 */
    @Test
    void noStatementRendersDanglingWhereAnd() {
        Pattern danglingAnd = Pattern.compile("where\\s+and\\b", Pattern.CASE_INSENSITIVE);
        for (String id : distinctStatementIds()) {
            String sql = sqlOf(id);
            assertFalse(danglingAnd.matcher(sql).find(), id + " 渲染出了 WHERE AND：" + sql);
        }
    }

    /**
     * SQL 正文里 NEVER 出现注释：Druid WallFilter 的 {@code commentAllow=false} 会把带注释的语句
     * 判成注入并抛异常，该语句静默失效，只在 Oracle 环境暴露（达梦环境关了 WallFilter）。
     */
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

    /** IF8A-41 的统计语句以 {@code request.*} 取值，缺这个键会渲染成不带任何过滤的全表扫描。 */
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
