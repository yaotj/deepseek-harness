package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 钉住「落单」与「扣款」分离这条裁决（2026-09-22）：出站方向一律落单，BOM 补站只是不扣款。
 *
 * <p>回归风险：把 `adviceOpt` 重新塞回落单判断，会让补站行程在乘车记录（IF8A-05，唯一源 GATE_TXN_PAY）里彻底消失。
 */
class GateFarePaymentOrchestratorTest {

    private GateTxnPayClient gateTxnPayClient;
    private GateFarePaymentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        gateTxnPayClient = mock(GateTxnPayClient.class);
        AlipayIndustryDetailAssembler industryDetailAssembler = mock(AlipayIndustryDetailAssembler.class);
        GateTxnPayRequestAssembler payRequestAssembler = mock(GateTxnPayRequestAssembler.class);
        when(payRequestAssembler.assemble(any(), any(), any())).thenReturn(new GateTxnPayReqDTO());
        orchestrator = new GateFarePaymentOrchestrator(gateTxnPayClient, industryDetailAssembler, payRequestAssembler);
    }

    @Test
    void bomSupplementExitStillCreatesOrder() {
        orchestrator.settleIfNeeded(exitRequest("006"), new NotifyVerifyResultRespDTO());
        verify(gateTxnPayClient).requestGateTxnPay(any());
    }

    @Test
    void normalExitCreatesOrder() {
        orchestrator.settleIfNeeded(exitRequest(null), new NotifyVerifyResultRespDTO());
        verify(gateTxnPayClient).requestGateTxnPay(any());
    }

    @Test
    void entryTxnCreatesNoOrder() {
        NotifyVerifyResultReqDTO request = exitRequest(null);
        request.setTrxType("01");
        orchestrator.settleIfNeeded(request, new NotifyVerifyResultRespDTO());
        verifyNoInteractions(gateTxnPayClient);
    }

    /** adviceOpt 必须随报文搬到扣费入参里——gate-txn-pay 就是靠它判断「落单但不扣款」。 */
    @Test
    void adviceOptIsCarriedIntoPayRequest() {
        assertEquals("006", GateTxnPayReqDTO.fromVerifyResult(exitRequest("006")).getAdviceOpt());
    }

    private NotifyVerifyResultReqDTO exitRequest(String adviceOpt) {
        NotifyVerifyResultReqDTO request = new NotifyVerifyResultReqDTO();
        request.setTrxType("02");
        request.setCardId("CARD0001");
        request.setHandleDateTime("20260922153642");
        request.setTrxAmount("200");
        request.setAdviceOpt(adviceOpt);
        return request;
    }
}
