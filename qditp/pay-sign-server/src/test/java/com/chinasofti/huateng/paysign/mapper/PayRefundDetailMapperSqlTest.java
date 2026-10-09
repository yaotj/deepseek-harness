package com.chinasofti.huateng.paysign.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 护栏：退款回查与跨表对账的扫表条件、CAS 主键两列（含 TXN_DATE）、以及 EXISTS 与 NOT EXISTS 的方向。 */
class PayRefundDetailMapperSqlTest {

    private static final String RESOURCE = "mapper/PayRefundDetailMapper.xml";

    private static final String NAMESPACE =
            "com.chinasofti.huateng.paysign.mapper.PayRefundDetailMapper.";

    /** 回查扫表 SQL MUST 同时满足四条不变量（{@code docs/domain/outbox.md} §二那几个坑的退款版）。 */
    @Test
    void refundQueryCompensationScanKeepsAllFourInvariants() {
        String sql = boundSql(parseMapper(), "selectCompensableRefundQuery").replaceAll("\\s+", " ");
        String lower = sql.toLowerCase();

        assertTrue(sql.contains("REFUND_STATUS in ('PROCESSING', 'RETRY')"),
                "状态白名单 MUST 恰好是 PROCESSING + RETRY：PROCESSING 代表请求已发给支付中心；"
                        + "RETRY 是旧版本的落法、当前代码已不再写入，但库里那 17 行历史数据在纳入本扫描前"
                        + "没有任何路径会碰（本查询只取 PROCESSING、selectDriftedRefundSummary 只取 SUCCESS）⇒ 永久悬挂。"
                        + "NEVER 把 RETRY 删掉，也 NEVER 把 INIT 加进来（请求从未发出，回查必然「未找到数据」）；实际渲染：" + sql);
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

    /** 三条写回状态的语句 MUST 都是 CAS：WHERE 同时带主键（{@code REFUND_ORDER_NO} + {@code TXN_DATE}） */
    @Test
    void everyRefundQueryWriteIsCasOnProcessing() {
        Configuration configuration = parseMapper();

        for (String id : new String[]{"finishFromQuery", "delayNextRefundQuery", "exhaustFromQuery"}) {
            String where = whereClauseOf(boundSql(configuration, id), id);

            assertTrue(where.contains("REFUND_ORDER_NO"),
                    id + " 的 WHERE MUST 带 REFUND_ORDER_NO，否则是全表更新；实际渲染：" + where);
            assertTrue(where.contains("TXN_DATE"),
                    id + " 的 WHERE MUST 带 TXN_DATE：本表是分区表、唯一索引是「退款单号 + 交易日期」；实际渲染：" + where);
            assertTrue(where.contains("REFUND_STATUS IN ('PROCESSING', 'RETRY')"),
                    id + " 的 WHERE MUST 带前置状态 REFUND_STATUS IN ('PROCESSING', 'RETRY')，"
                            + "这是该语句唯一的并发保证；三条语句的白名单 MUST 与 selectCompensableRefundQuery 一致，"
                            + "否则扫得到却推不动、行会一直卡着；实际渲染：" + where);
        }
    }

    /** 放弃收口语句 MUST 落 {@code CLOSED} 并清空 {@code NEXT_REQUEST_TIME}，且 NEVER 落 FAIL / SUCCESS。 */
    @Test
    void exhaustStatementClosesWithoutGuessingOutcome() {
        String sql = boundSql(parseMapper(), "exhaustFromQuery").replaceAll("\\s+", " ");
        String lower = sql.toLowerCase();
        String setClause = sql.substring(lower.indexOf("set "), lower.lastIndexOf("where "));

        assertTrue(setClause.contains("REFUND_STATUS = 'CLOSED'"),
                "超龄放弃 MUST 落 CLOSED：语义是「我方无法自动定性」。NEVER 落 FAIL（会让运营以为没退成功、再发一笔）"
                        + "也 NEVER 落 SUCCESS（会虚增 PAY_TXN_DETAIL 的已退金额）；实际 SET：" + setClause);
        assertFalse(setClause.contains("'FAIL'") || setClause.contains("'SUCCESS'"),
                "exhaustFromQuery NEVER 猜终态；实际 SET：" + setClause);
        assertTrue(setClause.contains("NEXT_REQUEST_TIME = NULL"),
                "MUST 清空 NEXT_REQUEST_TIME，否则这一行在状态白名单外还留着一个到点时间、语义自相矛盾；实际 SET：" + setClause);
    }

    /** 退避语句 NEVER 动 {@code REQUEST_COUNT} 与 {@code REFUND_STATUS}。 */
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

    /** 在途拦截 MUST 按原单号统计三个非终态，缺任一个都会放过一次重复提交。 */
    @Test
    void inFlightGuardCountsAllThreeNonTerminalStates() {
        String sql = boundSql(parseMapper(), "countInFlightByOrderNo").replaceAll("\\s+", " ");

        assertTrue(sql.contains("ORDER_NO = "),
                "MUST 按原支付订单号统计，NEVER 按退款单号（那样每次都是 0，等于没拦）；实际渲染：" + sql);
        assertTrue(sql.contains("REFUND_STATUS in ('INIT', 'PROCESSING', 'RETRY')"),
                "在途集合 MUST 是 INIT + PROCESSING + RETRY 三个：INIT 是 insert 后 markRequesting 前的窗口、"
                        + "RETRY 是旧版本遗留，三者都不会被 selectDriftedRefundSummary 计入 PAY_TXN_DETAIL.REFUND_AMOUNT，"
                        + "因此「按已退金额判可退额」那条校验对它们完全透明 —— 这正是同一原单被重复提交 3~4 次的成因；"
                        + "实际渲染：" + sql);
    }

    /** 收口语句 MUST 用 {@code NVL} 兜住三个单号与退款时间，NEVER 直接覆盖。 */
    @Test
    void finishStatementNeverErasesExistingChannelNumbers() {
        String sql = boundSql(parseMapper(), "finishFromQuery").replaceAll("\\s+", " ");

        for (String column : new String[]{"MERCHANT_REFUND_NO", "REFUND_NO", "CHANNEL_REFUND_NO", "REFUND_TIME"}) {
            assertTrue(sql.contains(column + " = NVL("),
                    column + " MUST 用 NVL 兜底，回查应答缺项时不能把已有值擦成空；实际渲染：" + sql);
        }
    }

    /** 跨表对账两条扫表 SQL MUST 同时满足三条不变量。 */
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

    /** 两条扫表 SQL 的差别 MUST 恰好是 {@code EXISTS} 与 {@code NOT EXISTS}，因为处置完全相反。 */
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
