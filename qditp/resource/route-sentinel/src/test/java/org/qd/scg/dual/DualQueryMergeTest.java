package org.qd.scg.dual;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.qd.scg.dual.DualQueryService.Side;
import org.qd.scg.dual.DualQueryService.SideResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DualQueryMergeTest {

    private static final String NEW_POS = "{\"orderNo\":\"00202611100000000001\",\"retCode\":\"0000\",\"retMsg\":\"成功\"}";
    private static final String OLD_POS = "{\"orderNo\":\"00112611100000000002\",\"retCode\":\"0000\",\"retMsg\":\"成功\"}";
    private static final String NEW_NEG = "{\"orderNo\":null,\"retCode\":\"2003\",\"retMsg\":\"无激活的订单\"}";
    private static final String OLD_NEG = "{\"orderNo\":null,\"retCode\":\"2003\",\"retMsg\":\"无激活的订单\"}";

    @Test
    @DisplayName("新侧正向即取新侧，不论老侧结果")
    void newPositiveWins() {
        assertEquals("NEW", DualQueryService.merge(pos(Side.NEW, NEW_POS), neg(Side.LEGACY)).winner());
        assertEquals(NEW_POS, DualQueryService.merge(pos(Side.NEW, NEW_POS), pos(Side.LEGACY, OLD_POS)).body());
        assertEquals("NEW", DualQueryService.merge(pos(Side.NEW), err(Side.LEGACY)).winner());
    }

    @Test
    @DisplayName("仅老侧正向取老侧(老用户订单,方案核心目标)")
    void legacyPositiveWins() {
        DualQueryService.MergeOutcome outcome = DualQueryService.merge(neg(Side.NEW), pos(Side.LEGACY, OLD_POS));
        assertEquals("LEGACY", outcome.winner());
        assertEquals(OLD_POS, outcome.body());
    }

    @Test
    @DisplayName("双负取新侧负向,设备继续轮询")
    void bothNegativeTakesNew() {
        DualQueryService.MergeOutcome outcome = DualQueryService.merge(neg(Side.NEW, NEW_NEG), neg(Side.LEGACY, OLD_NEG));
        assertEquals("NEW", outcome.winner());
        assertEquals(NEW_NEG, outcome.body());
    }

    @Test
    @DisplayName("新侧故障+老侧负向,取老侧负向")
    void newErrorLegacyNegative() {
        DualQueryService.MergeOutcome outcome = DualQueryService.merge(err(Side.NEW), neg(Side.LEGACY, OLD_NEG));
        assertEquals("LEGACY", outcome.winner());
        assertEquals(OLD_NEG, outcome.body());
    }

    @Test
    @DisplayName("双侧故障回兜底负向(NEVER 5xx)")
    void bothErrorFallback() {
        DualQueryService.MergeOutcome outcome = DualQueryService.merge(err(Side.NEW), err(Side.LEGACY));
        assertEquals("FALLBACK", outcome.winner());
        assertEquals(DualQueryService.FALLBACK_BODY, outcome.body());
    }

    @Test
    @DisplayName("一侧熔断跳过时等价于该侧故障,负向取有应答的一侧")
    void skippedSideBehavesLikeError() {
        assertEquals("LEGACY", DualQueryService.merge(skip(Side.NEW), pos(Side.LEGACY, OLD_POS)).winner());
        DualQueryService.MergeOutcome outcome = DualQueryService.merge(skip(Side.NEW), neg(Side.LEGACY, OLD_NEG));
        assertEquals("LEGACY", outcome.winner());
        assertEquals(OLD_NEG, outcome.body());
        assertEquals("FALLBACK", DualQueryService.merge(skip(Side.NEW), skip(Side.LEGACY)).winner());
    }

    @Test
    @DisplayName("正向判定: retCode=0000 且 orderNo 非空")
    void positiveClassification() {
        assertTrue(DualQueryService.isPositive(NEW_POS));
        assertFalse(DualQueryService.isPositive(NEW_NEG));
        assertFalse(DualQueryService.isPositive("{\"orderNo\":\"\",\"retCode\":\"0000\"}"));
        assertFalse(DualQueryService.isPositive("{\"retCode\":\"2003\",\"retMsg\":\"无激活的订单\"}"));
        assertFalse(DualQueryService.isPositive(null));
        assertFalse(DualQueryService.isPositive(""));
        assertFalse(DualQueryService.isPositive("not json at all"));
    }

    private static SideResult pos(Side side) {
        return SideResult.positive(side, NEW_POS, 10);
    }

    private static SideResult pos(Side side, String body) {
        return SideResult.positive(side, body, 10);
    }

    private static SideResult neg(Side side) {
        return SideResult.negative(side, NEW_NEG, 10);
    }

    private static SideResult neg(Side side, String body) {
        return SideResult.negative(side, body, 10);
    }

    private static SideResult err(Side side) {
        return SideResult.error(side, "timeout", 2500);
    }

    private static SideResult skip(Side side) {
        return SideResult.skipped(side);
    }
}
