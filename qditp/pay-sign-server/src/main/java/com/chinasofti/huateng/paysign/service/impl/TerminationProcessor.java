package com.chinasofti.huateng.paysign.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.domain.TerminationStatus;
import com.chinasofti.huateng.paysign.audit.PaySignAuditLogger;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.constant.SignChannelEnum;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.exception.TerminationException;
import com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper;
import com.chinasofti.huateng.paysign.model.request.NotifyTerminationFailedReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.port.ContractGatewayPort;
import com.chinasofti.huateng.paysign.port.GatewayReply;
import com.chinasofti.huateng.paysign.port.UnsettledOrderAnswer;
import com.chinasofti.huateng.paysign.port.UnsettledOrderPort;
import com.chinasofti.huateng.paysign.service.TerminationNotifyService;
import com.chinasofti.huateng.paysign.service.CallbackDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class TerminationProcessor {

    private static final Logger log = LoggerFactory.getLogger(TerminationProcessor.class);

    private static final String STATUS_PENDING = TerminationStatus.PENDING.name();
    private static final String STATUS_SCANNING = TerminationStatus.SCANNING.name();


    private static final String GATEWAY_STATUS_UNSIGNED = "UNSIGNED";

    private static final String CALLBACK_STATUS_SUCCESS = "SUCCESS";

    private static final DateTimeFormatter DISMISSAL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final int SCANNING_TIMEOUT_MINUTES = 1440;

    public enum Outcome {
        TERMINATED,
        CONFIRMED,
        REJECTED,
        EXPIRED,
        SKIPPED
    }

    private final AppTerminationRequestMapper terminationRequestMapper;

    private final ContractGatewayPort contractGatewayPort;

    private final CallbackDomainService callbackDomainService;

    private final PaySignAuditLogger auditLogger;

    private final UnsettledOrderPort unsettledOrderPort;

    private final TerminationNotifyService terminationNotifyService;

    public TerminationProcessor(
            AppTerminationRequestMapper terminationRequestMapper,
            ContractGatewayPort contractGatewayPort,
            CallbackDomainService callbackDomainService,
            PaySignAuditLogger auditLogger,
            UnsettledOrderPort unsettledOrderPort,
            TerminationNotifyService terminationNotifyService) {
        this.terminationRequestMapper = terminationRequestMapper;
        this.contractGatewayPort = contractGatewayPort;
        this.callbackDomainService = callbackDomainService;
        this.auditLogger = auditLogger;
        this.unsettledOrderPort = unsettledOrderPort;
        this.terminationNotifyService = terminationNotifyService;
    }

    public Outcome processOne(AppTerminationRequest record) {
        String status = record.getTerminationStatus();
        if (STATUS_PENDING.equals(status)) {
            return processPending(record);
        }
        if (STATUS_SCANNING.equals(status)) {
            return confirmTermination(record);
        }
        log.info("解约申请状态不在处理白名单内，跳过, requestSignSeq={}, status={}",
                record.getRequestSignSeq(), status);
        return Outcome.SKIPPED;
    }

    private Outcome processPending(AppTerminationRequest record) {
        String requestSignSeq = record.getRequestSignSeq();
        return switch (unsettledOrderPort.hasUnsettledOrder(
                record.getThirdUserId(), record.getPaymentVendor(), null)) {
            case UnsettledOrderAnswer.Answered answered -> answered.hasUnsettledOrder()
                    ? rejectByUnsettledOrder(record, requestSignSeq)
                    : requestTermination(record, requestSignSeq);
            case UnsettledOrderAnswer.Rejected rejected -> {
                log.error("查询未结清扣费订单未成功，本次不处理, requestSignSeq={}, retCode={}, retMsg={}",
                        requestSignSeq, rejected.retCode(), rejected.retMsg());
                yield Outcome.SKIPPED;
            }
            case UnsettledOrderAnswer.Unknown unknown -> {
                log.error("查询未结清扣费订单未获答复，本次不处理, requestSignSeq={}", requestSignSeq, unknown.cause());
                yield Outcome.SKIPPED;
            }
        };
    }

    private Outcome confirmTermination(AppTerminationRequest record) {
        String requestSignSeq = record.getRequestSignSeq();
        GatewayReply reply;
        try {
            reply = contractGatewayPort.queryContractResult(requestSignSeq);
        } catch (Exception e) {
            log.error("查询支付平台协议状态异常，保持 SCANNING, requestSignSeq={}", requestSignSeq, e);
            return expireIfTimedOut(record, "查询支付平台协议状态异常");
        }
        if (!(reply instanceof GatewayReply.Accepted accepted)) {
            log.warn("查询支付平台协议状态未成功，保持 SCANNING, requestSignSeq={}, response={}",
                    requestSignSeq, JSON.toJSONString(reply.raw()));
            return expireIfTimedOut(record, "查询支付平台协议状态未成功");
        }

        String contractStatus = readGatewayStatus(accepted);
        if (!GATEWAY_STATUS_UNSIGNED.equalsIgnoreCase(contractStatus)) {
            log.info("支付平台协议尚未解约，保持 SCANNING, requestSignSeq={}, status={}",
                    requestSignSeq, contractStatus);
            return expireIfTimedOut(record, "支付平台协议状态=" + contractStatus);
        }

        ReceiveTerminationResultReqDTO callback = new ReceiveTerminationResultReqDTO();
        callback.setThirdUserId(record.getThirdUserId());
        callback.setRequestSignSeq(requestSignSeq);
        callback.setPaymentVendor(record.getPaymentVendor());
        callback.setCardId(record.getCardId());
        callback.setCardType(record.getCardType());
        callback.setStatus(CALLBACK_STATUS_SUCCESS);
        callback.setDismissalTime(LocalDateTime.now().format(DISMISSAL_TIME_FORMATTER));

        BaseRespDTO callbackResp = callbackDomainService.receiveTerminationResult(
                callback, SignChannelEnum.METRO_APP.getCode());
        if (callbackResp == null || !PaySignErrorCodeEnum.SUCCESS.getCode().equals(callbackResp.getRetCode())) {
            throw new TerminationException("解约收口处理失败，requestSignSeq=" + requestSignSeq
                    + ", response=" + JSON.toJSONString(callbackResp));
        }

        log.info("支付平台已解约，解约申请已收口, requestSignSeq={}", requestSignSeq);
        return Outcome.CONFIRMED;
    }

    private String readGatewayStatus(GatewayReply.Accepted accepted) {
        Map<String, Object> data = accepted.data();
        if (data == null) {
            return null;
        }
        Object status = data.get("status");
        return status == null ? null : String.valueOf(status);
    }

    private Outcome rejectByUnsettledOrder(AppTerminationRequest record, String requestSignSeq) {
        String failReason = "存在未结清扣费订单";
        int rejected = terminationRequestMapper.rejectPending(requestSignSeq, failReason, LocalDateTime.now());
        if (rejected == 0) {
            log.info("解约申请已被其它路径接手，放弃拒绝处理, requestSignSeq={}", requestSignSeq);
            return Outcome.SKIPPED;
        }

        notifyTerminationFailed(record, failReason);

        log.info("存在未结清扣费订单，拒绝解约, requestSignSeq={}", requestSignSeq);
        return Outcome.REJECTED;
    }


    private Outcome expireIfTimedOut(AppTerminationRequest record, String detail) {
        String requestSignSeq = record.getRequestSignSeq();
        LocalDateTime since = record.getScanTime() != null ? record.getScanTime() : record.getRequestTime();
        if (since == null) {
            log.warn("SCANNING 记录缺少 SCAN_TIME 与 REQUEST_TIME，无法判定收口超时，保持 SCANNING, requestSignSeq={}",
                    requestSignSeq);
            return Outcome.SKIPPED;
        }
        LocalDateTime now = LocalDateTime.now();
        if (since.plusMinutes(SCANNING_TIMEOUT_MINUTES).isAfter(now)) {
            return Outcome.SKIPPED;
        }

        String failReason = "解约收口超时（" + SCANNING_TIMEOUT_MINUTES + "分钟未确认）：" + detail;
        int expired = terminationRequestMapper.expireScanning(requestSignSeq, failReason, now);
        if (expired == 0) {
            log.info("解约申请已被其它路径收口，放弃超时处理, requestSignSeq={}", requestSignSeq);
            return Outcome.SKIPPED;
        }

        notifyTerminationFailed(record, failReason);

        log.error("解约收口超时，已置 FAILED 并通知 APP, requestSignSeq={}, scanTime={}, requestTime={}, failReason={}",
                requestSignSeq, record.getScanTime(), record.getRequestTime(), failReason);
        return Outcome.EXPIRED;
    }

    private void notifyTerminationFailed(AppTerminationRequest record, String failReason) {
        NotifyTerminationFailedReqDTO notifyRequest = new NotifyTerminationFailedReqDTO();
        notifyRequest.setThirdUserId(record.getThirdUserId());
        notifyRequest.setRequestSignSeq(record.getRequestSignSeq());
        notifyRequest.setPaymentVendor(record.getPaymentVendor());
        notifyRequest.setCardId(record.getCardId());
        notifyRequest.setCardType(record.getCardType());
        notifyRequest.setFailReason(failReason);
        terminationNotifyService.asyncNotifyTerminationFailed(record, notifyRequest);
    }

    private Outcome requestTermination(AppTerminationRequest record, String requestSignSeq) {
        if (terminationRequestMapper.markScanning(requestSignSeq, LocalDateTime.now()) == 0) {
            log.info("解约申请已被并发接手（未命中 PENDING），本次不再发起, requestSignSeq={}", requestSignSeq);
            return Outcome.SKIPPED;
        }

        GatewayReply reply;
        try {
            reply = contractGatewayPort.requestDismissal(requestSignSeq);
        } catch (Exception e) {
            log.error("调用支付平台解约异常，保持 SCANNING 待主动查询收口, requestSignSeq={}", requestSignSeq, e);
            auditLogger.write("EXECUTE_TERMINATION", record.getThirdUserId(), requestSignSeq,
                    record.getPaymentVendor(), null, record, null);
            throw new TerminationException("调用支付平台解约异常，requestSignSeq=" + requestSignSeq, e);
        }

        auditLogger.write("EXECUTE_TERMINATION", record.getThirdUserId(), requestSignSeq,
                record.getPaymentVendor(), null, record, reply.raw());

        if (!(reply instanceof GatewayReply.Accepted)) {
            if (terminationRequestMapper.revertScanningToPending(requestSignSeq) == 0) {
                log.warn("支付平台解约失败但状态已被改走，放弃回退 PENDING, requestSignSeq={}", requestSignSeq);
            }
            throw new TerminationException("调用支付平台解约失败，requestSignSeq=" + requestSignSeq
                    + ", gatewayResponse=" + JSON.toJSONString(reply.raw()));
        }

        log.info("已发起支付平台解约，等待回调, requestSignSeq={}", requestSignSeq);
        return Outcome.TERMINATED;
    }
}
