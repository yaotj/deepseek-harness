package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.exception.TerminationException;
import com.chinasofti.huateng.paysign.model.request.ExecuteTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.port.GatewayReply;
import org.junit.jupiter.api.Test;

class TerminationExecutorGuardTest {

    private static final String SEQ = "0052290701523993";
    private static final String USER = "U-TEST-0008";
    private static final String ALIPAY_VENDOR = "03";

    @Test
    void gatewayExceptionKeepsScanningAndNeverRevertsToPending() {
        TerminationInternalFixture fixture = scanningReady("PENDING");
        when(fixture.contractGatewayPort.requestDismissal(SEQ))
                .thenThrow(new RuntimeException("connect timed out"));

        assertThrows(TerminationException.class, () -> fixture.service.executeTermination(request()));

        verify(fixture.terminationRequestMapper, never()).revertScanningToPending(anyString());
        verify(fixture.paySignRequestMapper).insert(any());
    }

    @Test
    void gatewayBusinessFailureRevertsToPendingAndThrows() {
        TerminationInternalFixture fixture = scanningReady("PENDING");
        when(fixture.contractGatewayPort.requestDismissal(SEQ))
                .thenReturn(new GatewayReply.Rejected(gateway(600, "操作失败")));
        when(fixture.terminationRequestMapper.revertScanningToPending(SEQ)).thenReturn(1);

        assertThrows(TerminationException.class, () -> fixture.service.executeTermination(request()));

        verify(fixture.terminationRequestMapper).revertScanningToPending(SEQ);
    }

    @Test
    void revertMissStillThrows() {
        TerminationInternalFixture fixture = scanningReady("PENDING");
        when(fixture.contractGatewayPort.requestDismissal(SEQ))
                .thenReturn(new GatewayReply.Rejected(gateway(600, "操作失败")));
        when(fixture.terminationRequestMapper.revertScanningToPending(SEQ)).thenReturn(0);

        assertThrows(TerminationException.class, () -> fixture.service.executeTermination(request()));
    }

    @Test
    void gatewaySuccessAnswersOkWithoutReverting() {
        TerminationInternalFixture fixture = scanningReady("PENDING");
        when(fixture.contractGatewayPort.requestDismissal(SEQ))
                .thenReturn(new GatewayReply.Accepted(gateway(0, "成功")));

        BaseRespDTO response = fixture.service.executeTermination(request());

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), response.getRetCode());
        verify(fixture.terminationRequestMapper, never()).revertScanningToPending(anyString());
    }

    @Test
    void terminalAndScanningStatesShortCircuitWithoutCalling() {
        for (String status : new String[] {"SCANNING", "SUCCESS", "FAILED"}) {
            TerminationInternalFixture fixture = scanningReady(status);

            BaseRespDTO response = fixture.service.executeTermination(request());

            assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), response.getRetCode(), "status=" + status);
            verify(fixture.terminationRequestMapper, never()).markScanning(anyString(), any());
            verify(fixture.contractGatewayPort, never()).requestDismissal(anyString());
        }
    }

    @Test
    void unknownStatusIsRejected() {
        TerminationInternalFixture fixture = scanningReady("WHATEVER");

        BaseRespDTO response = fixture.service.executeTermination(request());

        assertEquals(PaySignErrorCodeEnum.TERMINATION_REQUEST_NOT_FOUND.getCode(), response.getRetCode());
        verify(fixture.contractGatewayPort, never()).requestDismissal(anyString());
    }

    @Test
    void losingTheCasSkipsGatewayCall() {
        TerminationInternalFixture fixture = scanningReady("PENDING");
        when(fixture.terminationRequestMapper.markScanning(anyString(), any())).thenReturn(0);

        BaseRespDTO response = fixture.service.executeTermination(request());

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), response.getRetCode());
        verify(fixture.contractGatewayPort, never()).requestDismissal(anyString());
    }

    private TerminationInternalFixture scanningReady(String status) {
        TerminationInternalFixture fixture = TerminationInternalFixture.create();
        AppTerminationRequest row = new AppTerminationRequest();
        row.setRequestSignSeq(SEQ);
        row.setThirdUserId(USER);
        row.setPaymentVendor(ALIPAY_VENDOR);
        row.setTerminationStatus(status);
        when(fixture.terminationRequestMapper.selectByRequestSignSeq(SEQ)).thenReturn(row);
        when(fixture.terminationRequestMapper.markScanning(anyString(), any())).thenReturn(1);
        return fixture;
    }

    private ExecuteTerminationReqDTO request() {
        ExecuteTerminationReqDTO request = new ExecuteTerminationReqDTO();
        request.setThirdUserId(USER);
        request.setPaymentVendor(ALIPAY_VENDOR);
        request.setRequestSignSeq(SEQ);
        return request;
    }

    private PaySignGatewayResponse gateway(int code, String msg) {
        PaySignGatewayResponse response = new PaySignGatewayResponse();
        response.setCode(code);
        response.setMsg(msg);
        return response;
    }
}
