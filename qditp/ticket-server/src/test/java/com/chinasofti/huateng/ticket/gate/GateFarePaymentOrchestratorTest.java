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


class GateFarePaymentOrchestratorTest {

    private GateTxnPayClient gateTxnPayClient;
    private AlipayIndustryDetailAssembler industryDetailAssembler;
    private GateFarePaymentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        gateTxnPayClient = mock(GateTxnPayClient.class);
        industryDetailAssembler = mock(AlipayIndustryDetailAssembler.class);
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


    @Test
    void nonAlipayExitAlsoAssemblesIndustryDetail() {
        NotifyVerifyResultReqDTO request = exitRequest(null);
        request.setIssueChannelCode("01");
        orchestrator.settleIfNeeded(request, new NotifyVerifyResultRespDTO());
        verify(industryDetailAssembler).assemble(any());
    }

    @Test
    void alipayExitAssemblesIndustryDetail() {
        NotifyVerifyResultReqDTO request = exitRequest(null);
        request.setIssueChannelCode("07");
        orchestrator.settleIfNeeded(request, new NotifyVerifyResultRespDTO());
        verify(industryDetailAssembler).assemble(any());
    }

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
