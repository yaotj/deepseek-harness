package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderRespDTO;
import com.chinasofti.huateng.paysign.audit.PaySignAuditLogger;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.exception.TerminationException;
import com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignRequestMapper;
import com.chinasofti.huateng.paysign.model.request.NotifyTerminationFailedReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.port.ContractGatewayPort;
import com.chinasofti.huateng.paysign.port.GatewayReply;
import com.chinasofti.huateng.paysign.service.AppNotifyService;
import com.chinasofti.huateng.paysign.service.CallbackDomainService;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 护栏：扫表两支的状态归属 —— 查不通走超时判定，欠费未查明 NEVER 继续解约。 */
class TerminationProcessorGuardTest {

    private static final String SEQ = "0052290701523992";
    private static final String USER = "U-TEST-0009";
    private static final String ALIPAY_VENDOR = "03";

    private final AppTerminationRequestMapper terminationRequestMapper = mock(AppTerminationRequestMapper.class);
    private final ContractGatewayPort contractGatewayPort = mock(ContractGatewayPort.class);
    private final CallbackDomainService callbackDomainService = mock(CallbackDomainService.class);
    private final PaySignRequestMapper paySignRequestMapper = mock(PaySignRequestMapper.class);
    private final PaySignAuditLogger auditLogger = new PaySignAuditLogger(paySignRequestMapper);
    private final GateTxnPayClient gateTxnPayClient = mock(GateTxnPayClient.class);
    private final AppNotifyService appNotifyService = mock(AppNotifyService.class);

    private final TerminationProcessor processor = new TerminationProcessor(
            terminationRequestMapper, contractGatewayPort,
            callbackDomainService, auditLogger, gateTxnPayClient, appNotifyService);

    /** 查协议状态抛异常：保持 SCANNING（未超时 → SKIPPED），NEVER 收口。 */
    @Test
    void queryExceptionKeepsScanning() {
        when(contractGatewayPort.queryContractResult(SEQ))
                .thenThrow(new RuntimeException("connect timed out"));

        assertEquals(TerminationProcessor.Outcome.SKIPPED, processor.processOne(scanning()));

        verify(callbackDomainService, never()).receiveTerminationResult(any(), anyString());
    }

    /** 查协议状态返回非成功码：同样保持 SCANNING，NEVER 当成「未解约」去打 FAILED。 */
    @Test
    void queryBusinessFailureKeepsScanning() {
        when(contractGatewayPort.queryContractResult(SEQ))
                .thenReturn(new GatewayReply.Rejected(gateway(600, "操作失败", null)));

        assertEquals(TerminationProcessor.Outcome.SKIPPED, processor.processOne(scanning()));

        verify(callbackDomainService, never()).receiveTerminationResult(any(), anyString());
    }

    /** 协议仍是 SIGNED：支付平台尚未解约，保持 SCANNING。 */
    @Test
    void stillSignedKeepsScanning() {
        when(contractGatewayPort.queryContractResult(SEQ))
                .thenReturn(new GatewayReply.Accepted(gateway(0, "成功", "SIGNED")));

        assertEquals(TerminationProcessor.Outcome.SKIPPED, processor.processOne(scanning()));

        verify(callbackDomainService, never()).receiveTerminationResult(any(), anyString());
    }

    /** 确认 UNSIGNED：复用回调收口，返回 CONFIRMED。 */
    @Test
    void unsignedConfirmsThroughCallbackPath() {
        when(contractGatewayPort.queryContractResult(SEQ))
                .thenReturn(new GatewayReply.Accepted(gateway(0, "成功", "UNSIGNED")));
        when(callbackDomainService.receiveTerminationResult(any(ReceiveTerminationResultReqDTO.class), anyString()))
                .thenReturn(ok());

        assertEquals(TerminationProcessor.Outcome.CONFIRMED, processor.processOne(scanning()));

        verify(callbackDomainService).receiveTerminationResult(any(ReceiveTerminationResultReqDTO.class), anyString());
    }

    /** 收口未成功：抛异常交批处理入口记账，状态原样留 SCANNING 等下一轮（收口本身幂等，重试安全）。 */
    @Test
    void confirmFailureThrowsAndLeavesStateUntouched() {
        when(contractGatewayPort.queryContractResult(SEQ))
                .thenReturn(new GatewayReply.Accepted(gateway(0, "成功", "UNSIGNED")));
        when(callbackDomainService.receiveTerminationResult(any(ReceiveTerminationResultReqDTO.class), anyString()))
                .thenReturn(fail());

        AppTerminationRequest row = scanning();
        assertThrows(TerminationException.class, () -> processor.processOne(row));
    }

    /** 欠费查询未成功：NEVER 继续解约（放行等于在用户可能仍欠费时解约），也不改状态。 */
    @Test
    void unsettledQueryFailureBlocksTermination() {
        when(gateTxnPayClient.hasFailedOrder(any(GateTxnPayFailedOrderReqDTO.class)))
                .thenReturn(failedOrderResp("9999", false));

        assertEquals(TerminationProcessor.Outcome.SKIPPED, processor.processOne(pending()));

        verify(terminationRequestMapper, never()).markScanning(anyString(), any());
        verify(contractGatewayPort, never()).requestDismissal(anyString());
    }

    /** 欠费查询返回 null（异常被兜住）：同样阻断。 */
    @Test
    void unsettledQueryNullBlocksTermination() {
        when(gateTxnPayClient.hasFailedOrder(any(GateTxnPayFailedOrderReqDTO.class))).thenReturn(null);

        assertEquals(TerminationProcessor.Outcome.SKIPPED, processor.processOne(pending()));

        verify(contractGatewayPort, never()).requestDismissal(anyString());
    }

    /** 有未结清欠费：拒绝解约（REJECTED）、通知 APP 解约失败，且 NEVER 调支付中心。 */
    @Test
    void unsettledOrderRejectsWithoutCallingPayCenter() {
        when(gateTxnPayClient.hasFailedOrder(any(GateTxnPayFailedOrderReqDTO.class)))
                .thenReturn(failedOrderResp("0000", true));
        when(terminationRequestMapper.rejectPending(anyString(), anyString(), any())).thenReturn(1);

        assertEquals(TerminationProcessor.Outcome.REJECTED, processor.processOne(pending()));

        verify(contractGatewayPort, never()).requestDismissal(anyString());
        verify(appNotifyService).asyncNotifyTerminationFailed(any(), any(NotifyTerminationFailedReqDTO.class));
    }

    /** 欠费拒绝路径上 CAS 未抢到 PENDING：MUST 什么都不做。 */
    @Test
    void unsettledRejectLosingCasSendsNoNotify() {
        when(gateTxnPayClient.hasFailedOrder(any(GateTxnPayFailedOrderReqDTO.class)))
                .thenReturn(failedOrderResp("0000", true));
        when(terminationRequestMapper.rejectPending(anyString(), anyString(), any())).thenReturn(0);

        assertEquals(TerminationProcessor.Outcome.SKIPPED, processor.processOne(pending()));

        verify(appNotifyService, never()).asyncNotifyTerminationFailed(any(), any());
        verify(contractGatewayPort, never()).requestDismissal(anyString());
    }

    /** CAS 未抢到 PENDING（已被 /internal/termination/execute 或另一轮抢走）：MUST 放弃，NEVER 出网。 */
    @Test
    void losingTheCasSkipsGatewayCall() {
        when(gateTxnPayClient.hasFailedOrder(any(GateTxnPayFailedOrderReqDTO.class)))
                .thenReturn(failedOrderResp("0000", false));
        when(terminationRequestMapper.markScanning(anyString(), any())).thenReturn(0);

        assertEquals(TerminationProcessor.Outcome.SKIPPED, processor.processOne(pending()));

        verify(contractGatewayPort, never()).requestDismissal(anyString());
    }

    /** 无欠费 + 抢到 CAS + 网关成功：已发起解约，等回调（TERMINATED）。 */
    @Test
    void noUnsettledOrderStartsTermination() {
        when(gateTxnPayClient.hasFailedOrder(any(GateTxnPayFailedOrderReqDTO.class)))
                .thenReturn(failedOrderResp("0000", false));
        when(terminationRequestMapper.markScanning(anyString(), any())).thenReturn(1);
        when(contractGatewayPort.requestDismissal(SEQ)).thenReturn(new GatewayReply.Accepted(gateway(0, "成功", null)));

        assertEquals(TerminationProcessor.Outcome.TERMINATED, processor.processOne(pending()));

        verify(terminationRequestMapper, never()).revertScanningToPending(anyString());
    }

    /** 网关明确失败：MUST 回退 PENDING 并抛异常。 */
    @Test
    void gatewayFailureRevertsToPendingAndThrows() {
        when(gateTxnPayClient.hasFailedOrder(any(GateTxnPayFailedOrderReqDTO.class)))
                .thenReturn(failedOrderResp("0000", false));
        when(terminationRequestMapper.markScanning(anyString(), any())).thenReturn(1);
        when(contractGatewayPort.requestDismissal(SEQ)).thenReturn(new GatewayReply.Rejected(gateway(600, "操作失败", null)));

        AppTerminationRequest row = pending();
        assertThrows(TerminationException.class, () -> processor.processOne(row));

        verify(terminationRequestMapper).revertScanningToPending(SEQ);
    }

    /** 发起解约抛异常：保持 SCANNING（NEVER 退回 PENDING），抛异常交上层。 */
    @Test
    void gatewayExceptionKeepsScanningNotPending() {
        when(gateTxnPayClient.hasFailedOrder(any(GateTxnPayFailedOrderReqDTO.class)))
                .thenReturn(failedOrderResp("0000", false));
        when(terminationRequestMapper.markScanning(anyString(), any())).thenReturn(1);
        when(contractGatewayPort.requestDismissal(SEQ))
                .thenThrow(new RuntimeException("connect timed out"));

        AppTerminationRequest row = pending();
        assertThrows(TerminationException.class, () -> processor.processOne(row));

        verify(terminationRequestMapper, never()).revertScanningToPending(anyString());
    }

    /** 白名单之外（已终态）一律不动。 */
    @Test
    void terminalStatesAreSkipped() {
        for (String status : new String[] {"SUCCESS", "FAILED"}) {
            AppTerminationRequest row = scanning();
            row.setTerminationStatus(status);

            assertEquals(TerminationProcessor.Outcome.SKIPPED, processor.processOne(row), "status=" + status);
        }
        verify(contractGatewayPort, never()).queryContractResult(anyString());
        verify(contractGatewayPort, never()).requestDismissal(anyString());
    }

    /** SCAN_TIME 取当下 —— 保证 expireIfTimedOut 判定为「未超时」，这样断言才落在 SKIPPED 上。 */
    private AppTerminationRequest scanning() {
        AppTerminationRequest row = baseRow();
        row.setTerminationStatus("SCANNING");
        row.setScanTime(LocalDateTime.now());
        return row;
    }

    private AppTerminationRequest pending() {
        AppTerminationRequest row = baseRow();
        row.setTerminationStatus("PENDING");
        row.setRequestTime(LocalDateTime.now());
        return row;
    }

    private AppTerminationRequest baseRow() {
        AppTerminationRequest row = new AppTerminationRequest();
        row.setRequestSignSeq(SEQ);
        row.setThirdUserId(USER);
        row.setPaymentVendor(ALIPAY_VENDOR);
        row.setCardId("CARD-9");
        row.setCardType("0441");
        return row;
    }

    private PaySignGatewayResponse gateway(int code, String msg, String contractStatus) {
        PaySignGatewayResponse response = new PaySignGatewayResponse();
        response.setCode(code);
        response.setMsg(msg);
        if (contractStatus != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("status", contractStatus);
            response.setData(data);
        }
        return response;
    }

    private GateTxnPayFailedOrderRespDTO failedOrderResp(String resultCode, boolean hasFailedOrder) {
        GateTxnPayFailedOrderRespDTO response = new GateTxnPayFailedOrderRespDTO();
        response.setResultCode(resultCode);
        response.setHasFailedOrder(hasFailedOrder);
        return response;
    }

    private BaseRespDTO ok() {
        BaseRespDTO response = new BaseRespDTO();
        response.setRetCode("0000");
        return response;
    }

    private BaseRespDTO fail() {
        BaseRespDTO response = new BaseRespDTO();
        response.setRetCode("9999");
        return response;
    }
}
