package com.chinasofti.huateng.dailyticket.service.support;

import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉住从 {@code DailyTicketServiceImpl} 收口进来的判定逻辑，确保拆分前后逐字一致。
 */
class DailyTicketOrderSupportTest {

    @Test
    void validateOrderNoAcceptsOnlyDailyAndTravelType() {
        assertNull(DailyTicketOrderSupport.validateOrderNo("0E202609200001", "1"));
        assertNull(DailyTicketOrderSupport.validateOrderNo("0T202609200001", "2"));
        assertEquals("orderType必须为1或2", DailyTicketOrderSupport.validateOrderNo("0E202609200001", "3"));
        assertEquals("orderType必须为1或2", DailyTicketOrderSupport.validateOrderNo("0E202609200001", null));
    }

    @Test
    void validateOrderNoRejectsBlankOrderNo() {
        assertEquals("orderNo不能为空", DailyTicketOrderSupport.validateOrderNo(null, "1"));
        assertEquals("orderNo不能为空", DailyTicketOrderSupport.validateOrderNo("  ", "1"));
    }

    @Test
    void freePaymentMatchesEitherNo() {
        assertTrue(DailyTicketOrderSupport.isFreePayment("FREE-0E01", null));
        assertTrue(DailyTicketOrderSupport.isFreePayment(null, "FREE-0E01"));
        assertFalse(DailyTicketOrderSupport.isFreePayment("0E01", "PAY0E01"));
        assertFalse(DailyTicketOrderSupport.isFreePayment(null, null));
    }

    @Test
    void freeOrderIsFalseForNullOrder() {
        assertFalse(DailyTicketOrderSupport.isFreeOrder((DailyTicketOrder) null));
        assertFalse(DailyTicketOrderSupport.isFreeOrder((TravelTicketOrder) null));
    }

    @Test
    void freeOrderReadsTradeNoAndPaymentOrderNo() {
        DailyTicketOrder daily = new DailyTicketOrder();
        daily.setTradeNo("FREE-0E01");
        assertTrue(DailyTicketOrderSupport.isFreeOrder(daily));

        TravelTicketOrder travel = new TravelTicketOrder();
        travel.setPaymentOrderNo("FREE-0T01");
        assertTrue(DailyTicketOrderSupport.isFreeOrder(travel));

        TravelTicketOrder paid = new TravelTicketOrder();
        paid.setTradeNo("0T01");
        paid.setPaymentOrderNo("PAY0T01");
        assertFalse(DailyTicketOrderSupport.isFreeOrder(paid));
    }

    @Test
    void externalNoGatewaySourcesAreSeaBusAndCxuhOnly() {
        assertTrue(DailyTicketOrderSupport.isSeaBusOrderSource("4"));
        assertTrue(DailyTicketOrderSupport.isCxuhOrderSource("6"));
        assertTrue(DailyTicketOrderSupport.isExternalNoGatewayOrderSource("4"));
        assertTrue(DailyTicketOrderSupport.isExternalNoGatewayOrderSource("6"));
        assertFalse(DailyTicketOrderSupport.isExternalNoGatewayOrderSource("1"));
        assertFalse(DailyTicketOrderSupport.isExternalNoGatewayOrderSource(null));
    }

    @Test
    void tradeNoPrefixesMatchChannel() {
        assertEquals("FREE-0E01", DailyTicketOrderSupport.buildFreeTradeNo("0E01"));
        assertEquals("SEA_BUS-0E01", DailyTicketOrderSupport.buildSeaBusTradeNo("0E01"));
        assertEquals("CXUH-0E01", DailyTicketOrderSupport.buildCxuhTradeNo("0E01"));
    }

    @Test
    void successAndFailSetRetCodeAndReturnSameInstance() {
        DailyTicketBaseResult ok = new DailyTicketBaseResult();
        assertSame(ok, DailyTicketOrderSupport.success(ok));
        assertEquals("0000", ok.getRetCode());
        assertEquals("成功", ok.getRetMsg());

        DailyTicketBaseResult bad = new DailyTicketBaseResult();
        assertSame(bad, DailyTicketOrderSupport.fail(bad, "订单不存在"));
        assertEquals("9999", bad.getRetCode());
        assertEquals("订单不存在", bad.getRetMsg());
    }
}
