package com.chinasofti.huateng.gatetxnpay.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
import com.chinasofti.huateng.model.app.RequestPayTxnBatchResult;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.model.paysign.PayTxnDetailDTO;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import java.util.List;
import org.junit.jupiter.api.Test;

class CrossDomainDivergenceServiceTest {

    @Test
    void supplementSettledRowIsExplainedInsteadOfCountedAsDivergence() {
        GateTxnPayMapper mapper = mock(GateTxnPayMapper.class);
        PaySignClient paySignClient = mock(PaySignClient.class);
        when(mapper.selectSettledForDivergenceCheck(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(row("GT_SUPP", "补款单号:SP20261117143941056000384"),
                        row("GT_REAL", "支付结果回调收敛：SUCCESS")));
        when(paySignClient.queryPayTxnBatch(any())).thenReturn(batch("GT_SUPP", "GT_REAL"));

        CompensateNotifyRespDTO result = new CrossDomainDivergenceService(mapper, paySignClient).check();

        assertEquals("0000", result.getResultCode());
        assertEquals(2, result.getScanned());
        assertEquals(0, result.getSubmitted());
        assertEquals(1, result.getSkipped(),
                "只有非补款的真分叉计入 skipped：补款收口下原单直扣本未发生，属口径差异不是故障");
    }

    @Test
    void alignedRowsPassWithoutBeingCounted() {
        GateTxnPayMapper mapper = mock(GateTxnPayMapper.class);
        PaySignClient paySignClient = mock(PaySignClient.class);
        when(mapper.selectSettledForDivergenceCheck(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(row("GT_OK", "支付结果回调收敛：SUCCESS")));
        RequestPayTxnBatchResult batch = batch("GT_OK");
        batch.getPayTxnDetailList().get(0).setPayStatus("SUCCESS");
        when(paySignClient.queryPayTxnBatch(any())).thenReturn(batch);

        CompensateNotifyRespDTO result = new CrossDomainDivergenceService(mapper, paySignClient).check();

        assertEquals(1, result.getScanned());
        assertEquals(1, result.getSubmitted());
        assertEquals(0, result.getSkipped());
    }

    private GateTxnPay row(String orderNo, String remark) {
        GateTxnPay pay = new GateTxnPay();
        pay.setOrderNo(orderNo);
        pay.setTxnDate("20261119");
        pay.setDebitStatus("SUCCESS");
        pay.setRemark(remark);
        return pay;
    }

    private RequestPayTxnBatchResult batch(String... orderNos) {
        RequestPayTxnBatchResult result = new RequestPayTxnBatchResult();
        result.setRetCode("0000");
        result.setPayTxnDetailList(java.util.Arrays.stream(orderNos).map(no -> {
            PayTxnDetailDTO dto = new PayTxnDetailDTO();
            dto.setOrderNo(no);
            dto.setPayStatus("RETRY");
            return dto;
        }).toList());
        return result;
    }
}
