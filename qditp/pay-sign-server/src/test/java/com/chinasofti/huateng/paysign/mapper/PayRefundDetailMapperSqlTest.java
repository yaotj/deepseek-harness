package com.chinasofti.huateng.paysign.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁定 {@code PayRefundDetailMapper.xml} 里「退款回查补偿」那三条语句的扫表条件与 CAS 前置状态。
 *
 * <p><b>存在理由</b>：批次 5B 把 {@code requestRefund} 的 {@code @Transactional} 摘掉后，
 * 「退款已发出、本地停在 PROCESSING」这类单子**只能靠这条扫表 SQL 被捞出来**。
 * 扫表条件写错不会报错、编译与其它单测全绿，只会让补偿永远 {@code scanned=0}，
 * 那批单子永久悬挂（对账时才发现，且已无法区分是我方漏收口还是对方真没退）。
 *
 * <p>本测试不连数据库，只用 MyBatis 自己的 {@link XMLMapperBuilder} 离线解析这份 XML 并取渲染后的
 * SQL 文本，本机与 CI 都能无条件运行。
 *
 * <p><b>NEVER 把断言放宽成「只判断包含 REFUND_STATUS」</b>：那就同时失去了白名单与并发保证。
 */
class PayRefundDetailMapperSqlTest {

    private static final String RESOURCE = "mapper/PayRefundDetailMapper.xml";

    private static final String NAMESPACE =
            "com.chinasofti.huateng.paysign.mapper.PayRefundDetailMapper.";

    /**
     * 回查扫表 SQL MUST 同时满足四条不变量（{@code docs/domain/outbox.md} §二那几个坑的退款版）。
     *
     * <ul>
     *   <li>状态是 {@code PROCESSING} 白名单：只有它代表「退款请求确实已出网」。放宽成「非终态」
     *       会把 {@code INIT}（请求还没发）与 {@code RETRY}（已确认被拒）也捞进来，
     *       等于去问支付中心一笔它根本没收到的退款单；</li>
     *   <li>{@code NEXT_REQUEST_TIME} MUST 允许为空：正常路径上没人写它，写成裸比较会因
     *       NULL 比较为 UNKNOWN 而一条都捞不到；</li>
     *   <li>滞留判断与排序都 MUST 套 {@code NVL(LAST_REQUEST_TIME, CREATE_TIME)}：历史行该列可能为 NULL；</li>
     *   <li>{@code ROWNUM} MUST 在已排序子查询外层，否则先截断再排序、最旧的单子可能永远排不进这一批。</li>
     * </ul>
     */
    @Test
    void refundQueryCompensationScanKeepsAllFourInvariants() {
        String sql = boundSql(parseMapper(), "selectCompensableRefundQuery").replaceAll("\\s+", " ");
        String lower = sql.toLowerCase();

        assertTrue(sql.contains("REFUND_STATUS = 'PROCESSING'"),
                "MUST 只捞 PROCESSING：只有它代表退款请求已经发给支付中心；实际渲染：" + sql);
        assertFalse(sql.contains("REFUND_STATUS !=") || sql.contains("REFUND_STATUS <>")
                        || sql.contains("REFUND_STATUS not in"),
                "状态 MUST 用白名单，NEVER 写成「非终态即可处理」的黑名单；实际渲染：" + sql);
        assertTrue(sql.contains("NEXT_REQUEST_TIME is null or NEXT_REQUEST_TIME <=")
                        || sql.contains("NEXT_REQUEST_TIME IS NULL OR NEXT_REQUEST_TIME <="),
                "NEXT_REQUEST_TIME MUST 写成「IS NULL OR 到点」：正常路径上它一直是 NULL，"
                        + "裸比较会因 NULL 比较为 UNKNOWN 而一条都捞不到；实际渲染：" + sql);
        assertTrue(sql.contains("NVL(LAST_REQUEST_TIME, CREATE_TIME)"),
                "滞留判断与排序都 MUST 走 NVL(LAST_REQUEST_TIME, CREATE_TIME)，否则该列为 NULL 的历史行永远漏掉；"
                        + "实际渲染：" + sql);
        assertTrue(sql.contains("TXN_DATE >="),
                "MUST 带 TXN_DATE 下界：本表按 TXN_DATE 分区，缺它既没有分区裁剪、也没有「回查多久以前」的上限；"
                        + "实际渲染：" + sql);
        assertTrue(lower.indexOf("order by") < lower.lastIndexOf("rownum"),
                "ROWNUM MUST 套在已排序子查询的外层，否则先截断再排序；实际渲染：" + sql);
    }

    /**
     * 两条写回状态的语句 MUST 都是 CAS：WHERE 同时带主键（{@code REFUND_ORDER_NO} + {@code TXN_DATE}）
     * 与前置状态 {@code PROCESSING}。
     *
     * <p>只带主键 = 无条件覆盖（一条已被退款回调置成 SUCCESS 的明细会被迟到的回查改成 FAIL，
     * 而 {@code PAY_TXN_DETAIL} 的已退总额是按本表重算的，跟着一起错）；只带状态 = 全表更新。
     * <b>本表主键是「退款单号 + 交易日期」两列（分区表 + 本地唯一索引 UK_PAY_REFUND_DETAIL_NO），
     * 少写 TXN_DATE 就等于跨分区更新，NEVER 只带退款单号。</b>
     */
    @Test
    void everyRefundQueryWriteIsCasOnProcessing() {
        Configuration configuration = parseMapper();

        for (String id : new String[]{"finishFromQuery", "delayNextRefundQuery"}) {
            String where = whereClauseOf(boundSql(configuration, id), id);

            assertTrue(where.contains("REFUND_ORDER_NO"),
                    id + " 的 WHERE MUST 带 REFUND_ORDER_NO，否则是全表更新；实际渲染：" + where);
            assertTrue(where.contains("TXN_DATE"),
                    id + " 的 WHERE MUST 带 TXN_DATE：本表是分区表、唯一索引是「退款单号 + 交易日期」；实际渲染：" + where);
            assertTrue(where.contains("REFUND_STATUS = 'PROCESSING'"),
                    id + " 的 WHERE MUST 带前置状态 REFUND_STATUS = 'PROCESSING'，"
                            + "这是该语句唯一的并发保证；实际渲染：" + where);
        }
    }

    /**
     * 退避语句 NEVER 动 {@code REQUEST_COUNT} 与 {@code REFUND_STATUS}。
     *
     * <p>{@code REQUEST_COUNT} 的语义是「我方发起退款的次数」，回查不是发起退款；
     * 把回查次数混进去会让运维把一笔单次退款误判成重复退款。状态更不能动：
     * 退避只是「等下次再问」，不是订正。
     */
    @Test
    void delayStatementOnlyPushesNextRequestTime() {
        String sql = boundSql(parseMapper(), "delayNextRefundQuery").replaceAll("\\s+", " ");
        String lower = sql.toLowerCase();
        String setClause = sql.substring(lower.indexOf("set "), lower.lastIndexOf("where "));

        assertTrue(setClause.contains("NEXT_REQUEST_TIME"),
                "delayNextRefundQuery MUST 推 NEXT_REQUEST_TIME，否则下一轮立刻又扫到同一批；实际 SET：" + setClause);
        assertFalse(setClause.contains("REQUEST_COUNT"),
                "delayNextRefundQuery NEVER 动 REQUEST_COUNT：那一列计的是发起退款次数；实际 SET：" + setClause);
        assertFalse(setClause.contains("REFUND_STATUS"),
                "delayNextRefundQuery NEVER 改状态：退避不等于订正；实际 SET：" + setClause);
    }

    /**
     * 收口语句 MUST 用 {@code NVL} 兜住三个单号与退款时间，NEVER 直接覆盖。
     *
     * <p>§3.2 应答里这几项可能缺项，直接覆盖会把 §3.1 受理时已经拿到的值擦成空 ——
     * 那几个号是事后与支付中心对账的唯一线索。
     */
    @Test
    void finishStatementNeverErasesExistingChannelNumbers() {
        String sql = boundSql(parseMapper(), "finishFromQuery").replaceAll("\\s+", " ");

        for (String column : new String[]{"MERCHANT_REFUND_NO", "REFUND_NO", "CHANNEL_REFUND_NO", "REFUND_TIME"}) {
            assertTrue(sql.contains(column + " = NVL("),
                    column + " MUST 用 NVL 兜底，回查应答缺项时不能把已有值擦成空；实际渲染：" + sql);
        }
    }

    /**
     * 跨表对账两条扫表 SQL MUST 同时满足三条不变量。
     *
     * <ul>
     *   <li>带 {@code TXN_DATE >=} 下界：{@code TXN_DATE} 是本表分区键，缺它这条对账 SQL 就是全分区扫；
     *       代价是「畸形 TXN_DATE 的行永远扫不到」，那是已登记的已知盲区（见 mapper javadoc），
     *       <b>NEVER 靠去掉下界来覆盖它</b>；</li>
     *   <li>{@code ROWNUM} 限流：单轮上限，且 MUST 在已排序子查询外层；</li>
     *   <li>只统计 {@code REFUND_STATUS = 'SUCCESS'} 的明细：{@code PAY_TXN_DETAIL.REFUND_AMOUNT}
     *       的语义是「已退成功总额」，把 {@code PROCESSING} / {@code RETRY} 算进来会把在途退款
     *       当成已退，汇总反而被这条「对账」改错。</li>
     * </ul>
     *
     * <p>顺带守住「SQL 正文里没有行注释」：Druid WallFilter 默认 {@code commentAllow=false}，
     * 带注释的语句会被判定为注入并<b>静默失效</b>（编译与单测都发现不了，只在 Oracle 生产环境炸）。
     */
    @Test
    void refundSummaryReconScansKeepAllThreeInvariants() {
        Configuration configuration = parseMapper();

        for (String id : new String[]{"selectDriftedRefundSummary", "selectOrphanRefundOrders"}) {
            String sql = boundSql(configuration, id).replaceAll("\\s+", " ");
            String lower = sql.toLowerCase();

            assertTrue(sql.contains("TXN_DATE >="),
                    id + " MUST 带 TXN_DATE 下界：本表按 TXN_DATE 分区，缺它就是全分区扫；实际渲染：" + sql);
            assertTrue(lower.contains("rownum"),
                    id + " MUST 带 ROWNUM 限流，否则一轮可能捞回整张表；实际渲染：" + sql);
            assertTrue(lower.indexOf("order by") < lower.lastIndexOf("rownum"),
                    id + " 的 ROWNUM MUST 套在已排序子查询外层，否则先截断再排序；实际渲染：" + sql);
            assertTrue(sql.contains("REFUND_STATUS = 'SUCCESS'"),
                    id + " MUST 只统计 REFUND_STATUS = 'SUCCESS' 的明细：汇总列的语义是已退成功总额，"
                            + "把在途退款算进来等于用对账把汇总改错；实际渲染：" + sql);
            assertFalse(sql.contains("--"),
                    id + " 的 SQL 正文 NEVER 出现行注释：Druid WallFilter 会判定为注入并让该语句静默失效；"
                            + "实际渲染：" + sql);
        }
    }

    /**
     * 两条扫表 SQL 的差别 MUST 恰好是 {@code EXISTS} 与 {@code NOT EXISTS}，因为处置完全相反：
     * A 类（原单存在）重算即收口，B 类（原单不存在）<b>一行都不能改</b>、只能记 WARN 等人工。
     *
     * <p>写反了不会有任何编译或运行错误：{@code updateRefundSummary} 对 B 类影响 0 行、
     * 对 A 类影响 1 行，两者都「没抛异常」。唯一的后果是<b>一批真正的坏账从告警里消失</b>。
     */
    @Test
    void orphanScanUsesNotExistsWhileDriftScanUsesExists() {
        Configuration configuration = parseMapper();

        String drift = boundSql(configuration, "selectDriftedRefundSummary")
                .replaceAll("\\s+", " ").toLowerCase();
        String orphan = boundSql(configuration, "selectOrphanRefundOrders")
                .replaceAll("\\s+", " ").toLowerCase();

        assertTrue(drift.contains("exists ("),
                "A 类 MUST 用 EXISTS 关联 PAY_TXN_DETAIL：只有原单存在的才可能被重算修好；实际渲染：" + drift);
        assertFalse(drift.contains("not exists"),
                "A 类 NEVER 用 NOT EXISTS —— 那捞到的是修不了的 B 类，重算一律影响 0 行；实际渲染：" + drift);
        assertTrue(orphan.contains("not exists"),
                "B 类 MUST 用 NOT EXISTS：它的定义就是 PAY_TXN_DETAIL 里没有该 ORDER_NO；实际渲染：" + orphan);
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
}
