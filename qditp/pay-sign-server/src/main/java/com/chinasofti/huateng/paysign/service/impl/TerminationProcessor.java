package com.chinasofti.huateng.paysign.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.domain.TerminationStatus;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderRespDTO;
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
import com.chinasofti.huateng.paysign.service.AppNotifyService;
import com.chinasofti.huateng.paysign.service.CallbackDomainService;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** 单条解约申请的处理器。 */
@Service
public class TerminationProcessor {

    private static final Logger log = LoggerFactory.getLogger(TerminationProcessor.class);

    private static final String STATUS_PENDING = TerminationStatus.PENDING.name();
    private static final String STATUS_SCANNING = TerminationStatus.SCANNING.name();

    /** 支付平台协议状态：已解约。 */
    private static final String GATEWAY_STATUS_UNSIGNED = "UNSIGNED";
    /** 解约回调报文的成功状态值，见 CallbackDomainServiceImpl.receiveTerminationResult。 */
    private static final String CALLBACK_STATUS_SUCCESS = "SUCCESS";

    private static final DateTimeFormatter DISMISSAL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** SCANNING 多久（分钟）没能确认解约就判为收口超时，置 FAILED 并通知 APP。 */
    private static final int SCANNING_TIMEOUT_MINUTES = 1440;

    /** 处理结论。 */
    public enum Outcome {
        /** 已发起支付平台解约，等下一轮扫表或回调收口。 */
        TERMINATED,
        /** 主动查询确认支付平台已解约，已收口为 SUCCESS。 */
        CONFIRMED,
        /** 存在未结清扣费订单，拒绝解约并已置 FAILED。 */
        REJECTED,
        /** SCANNING 超过 SCANNING_TIMEOUT_MINUTES 仍未确认解约，已置 FAILED 并通知 APP。 */
        EXPIRED,
        /** 未能定论，状态原样保留，下次扫表重试。 */
        SKIPPED
    }

    private final AppTerminationRequestMapper terminationRequestMapper;

    /** 支付中心签约/解约方向的出向端口（2026-09-16，ADR-D115）。 */
    private final ContractGatewayPort contractGatewayPort;

    /** 解约回调的收口实现，2026-09-15 随回调组由 {@code PaySignWorkflow} 搬到。 */
    private final CallbackDomainService callbackDomainService;

    /** 审计流水的唯一写入点（2026-09-14 抽出）；此前是 {@code paySignWorkflow.writeLog}，属反向依赖。 */
    private final PaySignAuditLogger auditLogger;

    private final GateTxnPayClient gateTxnPayClient;

    private final AppNotifyService appNotifyService;

    /** 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备。 */
    public TerminationProcessor(
            AppTerminationRequestMapper terminationRequestMapper,
            ContractGatewayPort contractGatewayPort,
            CallbackDomainService callbackDomainService,
            PaySignAuditLogger auditLogger,
            GateTxnPayClient gateTxnPayClient,
            AppNotifyService appNotifyService) {
        this.terminationRequestMapper = terminationRequestMapper;
        this.contractGatewayPort = contractGatewayPort;
        this.callbackDomainService = callbackDomainService;
        this.auditLogger = auditLogger;
        this.gateTxnPayClient = gateTxnPayClient;
        this.appNotifyService = appNotifyService;
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

    /** PENDING：确认无未结清欠费后发起支付平台解约。 */
    private Outcome processPending(AppTerminationRequest record) {
        String requestSignSeq = record.getRequestSignSeq();
        GateTxnPayFailedOrderRespDTO orderResp = queryUnsettledOrder(record);
        if (orderResp == null || !PaySignErrorCodeEnum.SUCCESS.getCode().equals(orderResp.getResultCode())) {
            log.error("查询未结清扣费订单未成功，本次不处理, requestSignSeq={}, response={}",
                    requestSignSeq, orderResp == null ? null : JSON.toJSONString(orderResp));
            return Outcome.SKIPPED;
        }

        if (orderResp.isHasFailedOrder()) {
            return rejectByUnsettledOrder(record, requestSignSeq);
        }
        return requestTermination(record, requestSignSeq);
    }

    /** SCANNING：主动向支付平台查协议状态收口。 */
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

    /** 从网关成功应答的 data 中读取协议状态。 */
    private String readGatewayStatus(GatewayReply.Accepted accepted) {
        Map<String, Object> data = accepted.data();
        if (data == null) {
            return null;
        }
        Object status = data.get("status");
        return status == null ? null : String.valueOf(status);
    }

    /** 查询该用户该支付渠道下是否还有未结清扣费订单。 */
    private GateTxnPayFailedOrderRespDTO queryUnsettledOrder(AppTerminationRequest record) {
        try {
            GateTxnPayFailedOrderReqDTO request = new GateTxnPayFailedOrderReqDTO();
            request.setThirdUserId(record.getThirdUserId());
            request.setPaymentVendor(record.getPaymentVendor());
            return gateTxnPayClient.hasFailedOrder(request);
        } catch (Exception e) {
            log.error("查询未结清扣费订单异常, requestSignSeq={}", record.getRequestSignSeq(), e);
            return null;
        }
    }

    /** 存在未结清欠费：不调支付平台，置 FAILED 并通知 APP 解约失败。 */
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

    /**
     * SCANNING 收口超时判定：滞留未超阈值就原样保留，超了则置 FAILED 并通知 APP。
     *
     * @param detail 本轮没能确认的原因，进 FAIL_REASON 供人工核查（列长 1024，够用）
     */
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

    /** 提交「解约失败」通知。 */
    private void notifyTerminationFailed(AppTerminationRequest record, String failReason) {
        NotifyTerminationFailedReqDTO notifyRequest = new NotifyTerminationFailedReqDTO();
        notifyRequest.setThirdUserId(record.getThirdUserId());
        notifyRequest.setRequestSignSeq(record.getRequestSignSeq());
        notifyRequest.setPaymentVendor(record.getPaymentVendor());
        notifyRequest.setCardId(record.getCardId());
        notifyRequest.setCardType(record.getCardType());
        notifyRequest.setFailReason(failReason);
        appNotifyService.asyncNotifyTerminationFailed(record, notifyRequest);
    }

    /** 无未结清欠费：CAS 抢执行权置 SCANNING，再调支付平台解约。 */
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
