package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.QueryPayTxnBatchReqDTO;
import com.chinasofti.huateng.model.app.RequestPayTxnBatchResult;
import com.chinasofti.huateng.paysign.entity.PayCallbackLog;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueryPayTxnBatchDiscountInfoTest {

    private static final String CHANNEL_DISCOUNT =
            "[{\"amount\":1800,\"name\":\"聚合优惠-活动储值券-定额20\",\"type\":\"COUPON\"}]";

    @Test
    void 渠道优惠明细随批量查询透出() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        PayTxnDetail txn = new PayTxnDetail();
        txn.setOrderNo("GT20261117135410121000508");
        when(fixture.payTxnDetailMapper.selectByOrderNos(anyList())).thenReturn(List.of(txn));
        PayCallbackLog callbackLog = new PayCallbackLog();
        callbackLog.setMerchantOrderNo("GT20261117135410121000508");
        callbackLog.setDiscountInfo(CHANNEL_DISCOUNT);
        when(fixture.payCallbackLogMapper.selectLatestPayDiscountInfoByOrderNos(anyList(), eq("PAY")))
                .thenReturn(List.of(callbackLog));

        RequestPayTxnBatchResult result = fixture.service.queryPayTxnBatch(batchReq("GT20261117135410121000508"));

        assertEquals("0000", result.getRetCode());
        assertEquals(1, result.getPayTxnDetailList().size());
        assertEquals(CHANNEL_DISCOUNT, result.getPayTxnDetailList().get(0).getChannelDiscountInfo());
    }

    @Test
    void 无优惠回调时透出字段保持null() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        PayTxnDetail txn = new PayTxnDetail();
        txn.setOrderNo("GT-NO-DISCOUNT");
        when(fixture.payTxnDetailMapper.selectByOrderNos(anyList())).thenReturn(List.of(txn));
        when(fixture.payCallbackLogMapper.selectLatestPayDiscountInfoByOrderNos(anyList(), eq("PAY")))
                .thenReturn(List.of());

        RequestPayTxnBatchResult result = fixture.service.queryPayTxnBatch(batchReq("GT-NO-DISCOUNT"));

        assertEquals("0000", result.getRetCode());
        assertNull(result.getPayTxnDetailList().get(0).getChannelDiscountInfo());
    }

    @Test
    void 订单号列表为空直接拒绝() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();

        RequestPayTxnBatchResult result = fixture.service.queryPayTxnBatch(batchReq());

        assertEquals("9999", result.getRetCode());
    }

    private static QueryPayTxnBatchReqDTO batchReq(String... orderNos) {
        QueryPayTxnBatchReqDTO req = new QueryPayTxnBatchReqDTO();
        req.setOrderNos(List.of(orderNos));
        return req;
    }
}
