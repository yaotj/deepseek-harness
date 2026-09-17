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

/** 护栏：网关异常=结果未知保持 SCANNING，明确失败才回退 PENDING，回退 CAS 0 行仍抛异常。 */
class TerminationExecutorGuardTest {

    private static final String SEQ = "0052290701523993";
    private static final String USER = "U-TEST-0008";
    private static final String ALIPAY_VENDOR = "03";

    /** 结果未知：保持 SCANNING、NEVER 回退，且审计流水照写（无事务，立即提交）。 */
    @Test
    void gatewayExceptionKeepsScanningAndNeverRevertsToPending() {
        TerminationInternalFixture fixture = scanningReady("PENDING");
        when(fixture.contractGatewayPort.requestDismissal(SEQ))
                .thenThrow(new RuntimeException("connect timed out"));

        assertThrows(TerminationException.class, () -> fixture.service.executeTermination(request()));

        verify(fixture.terminationRequestMapper, never()).revertScanningToPending(anyString());
        verify(fixture.paySignRequestMapper).insert(any());
    }

    /** 明确失败：MUST 回退 PENDING 并抛异常，把执行权交还扫表任务。 */
    @Test
    void gatewayBusinessFailureRevertsToPendingAndThrows() {
        TerminationInternalFixture fixture = scanningReady("PENDING");
        when(fixture.contractGatewayPort.requestDismissal(SEQ))
                .thenReturn(new GatewayReply.Rejected(gateway(600, "操作失败")));
        when(fixture.terminationRequestMapper.revertScanningToPending(SEQ)).thenReturn(1);

        assertThrows(TerminationException.class, () -> fixture.service.executeTermination(request()));

        verify(fixture.terminationRequestMapper).revertScanningToPending(SEQ);
    }

    /** 回退 CAS 命中 0 行（已被回调收口）：只告警，仍然抛异常，NEVER 吞成成功。 */
    @Test
    void revertMissStillThrows() {
        TerminationInternalFixture fixture = scanningReady("PENDING");
        when(fixture.contractGatewayPort.requestDismissal(SEQ))
                .thenReturn(new GatewayReply.Rejected(gateway(600, "操作失败")));
        when(fixture.terminationRequestMapper.revertScanningToPending(SEQ)).thenReturn(0);

        assertThrows(TerminationException.class, () -> fixture.service.executeTermination(request()));
    }

    /** 成功路径：答 0000，且 NEVER 回退状态。 */
    @Test
    void gatewaySuccessAnswersOkWithoutReverting() {
        TerminationInternalFixture fixture = scanningReady("PENDING");
        when(fixture.contractGatewayPort.requestDismissal(SEQ))
                .thenReturn(new GatewayReply.Accepted(gateway(0, "成功")));

        BaseRespDTO response = fixture.service.executeTermination(request());

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), response.getRetCode());
        verify(fixture.terminationRequestMapper, never()).revertScanningToPending(anyString());
    }

    /** 三个终态/进行中状态一律幂等短路：不 CAS、不出网。 */
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

    /** 白名单之外的状态一律拒绝（NEVER「非终态即可处理」）。 */
    @Test
    void unknownStatusIsRejected() {
        TerminationInternalFixture fixture = scanningReady("WHATEVER");

        BaseRespDTO response = fixture.service.executeTermination(request());

        assertEquals(PaySignErrorCodeEnum.TERMINATION_REQUEST_NOT_FOUND.getCode(), response.getRetCode());
        verify(fixture.contractGatewayPort, never()).requestDismissal(anyString());
    }

    /** CAS 未抢到 PENDING（并发已被别的副本接手）：答成功且 NEVER 出网。 */
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
