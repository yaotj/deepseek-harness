package com.chinasofti.huateng.paysign.service.impl;

import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillError;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillSuccess;
import static com.chinasofti.huateng.paysign.support.PaySignValidators.validateReceiveSignResult;
import static com.chinasofti.huateng.paysign.support.PaySignValidators.validateReceiveTerminationResult;
import static com.chinasofti.huateng.paysign.support.PaySignValues.normalizeVendor;
import static com.chinasofti.huateng.paysign.support.PaySignValues.parseDateTime;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.model.domain.SignStatus;
import com.chinasofti.huateng.model.domain.TerminationStatus;
import com.chinasofti.huateng.paysign.audit.PaySignAuditLogger;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.support.CallbackLookups;
import com.chinasofti.huateng.paysign.support.PaymentChannel;
import com.chinasofti.huateng.paysign.support.PaymentChannels;
import com.chinasofti.huateng.paysign.constant.PaymentVendorEnum;
import com.chinasofti.huateng.paysign.domain.TerminationFailReason;
import com.chinasofti.huateng.paysign.domain.TerminationStatusTransition;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.event.SignResultCommittedEvent;
import com.chinasofti.huateng.paysign.exception.TerminationException;
import com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignInfoMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignRequestMapper;
import com.chinasofti.huateng.paysign.model.request.NotifyTerminationFailedReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.service.TerminationNotifyService;
import com.chinasofti.huateng.paysign.service.CallbackDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
/**
 * IPD03 解约结果回调的真实现（2026-09-17 由 {@code CallbackDomainServiceImpl} 按依赖簇拆出，ADR-D120）。
 *
 * <p>协作者 7 个。与 {@link SignResultCallbackHandler} 重复注入 3 个
 * （签约主表、流水表、审计流水）—— 这是拆分的**已知代价**，经人裁决接受：
 * 两个簇的交集就是这 3 个，NEVER 因为「看起来重复」把两条回调链路再合回一个类。
 *
 * <p>本方法 <b>NEVER 加 {@code @Transactional}</b>（2026-09-12 / ADR-D48 摘掉）：
 * 本地写入用 {@code transactionTemplate} 显式开短事务，出网清理留到提交之后。
 */
@Service
public class TerminationResultCallbackHandler {
    private static final Logger log = LoggerFactory.getLogger(TerminationResultCallbackHandler.class);

    private static final String STATUS_SIGNED = SignStatus.SIGNED.name();
    private static final String STATUS_UNSIGNED = SignStatus.UNSIGNED.name();
    private static final String STATUS_FAILED = TerminationStatus.FAILED.name();
    private static final String STATUS_SCANNING = TerminationStatus.SCANNING.name();
    private static final String STATUS_SUCCESS = TerminationStatus.SUCCESS.name();
    private static final String SIGN_LOG_STATUS_FAILED = SignStatus.FAILED.name();

    private final PaySignInfoMapper paySignInfoMapper;
    private final PaySignRequestMapper paySignRequestMapper;
    private final AppTerminationRequestMapper terminationRequestMapper;
    private final TerminationNotifyService terminationNotifyService;
    private final PaySignAuditLogger auditLogger;
    private final TransactionTemplate transactionTemplate;
    private final ChannelSyncDeliverer channelSyncDeliverer;

    public TerminationResultCallbackHandler(
            PaySignInfoMapper paySignInfoMapper,
            PaySignRequestMapper paySignRequestMapper,
            AppTerminationRequestMapper terminationRequestMapper,
            TerminationNotifyService terminationNotifyService,
            PaySignAuditLogger auditLogger,
            TransactionTemplate transactionTemplate,
            ChannelSyncDeliverer channelSyncDeliverer) {
        this.paySignInfoMapper = paySignInfoMapper;
        this.paySignRequestMapper = paySignRequestMapper;
        this.terminationRequestMapper = terminationRequestMapper;
        this.terminationNotifyService = terminationNotifyService;
        this.auditLogger = auditLogger;
        this.transactionTemplate = transactionTemplate;
        this.channelSyncDeliverer = channelSyncDeliverer;
    }

    /** IPD03 解约结果回调。 */
    /** 本方法 NEVER 加 @Transactional（2026-09-12 / ADR-D48 摘掉，此前一直带着）。 */
    public BaseRespDTO receiveTerminationResult(ReceiveTerminationResultReqDTO request, String signChannel) {
        BaseRespDTO response = new BaseRespDTO();
        try {
            if (request == null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "请求报文不能为空");
                auditLogger.write("RECEIVE_TERMINATION_RESULT", null, null, null, null, null, response);
                return response;
            }
            switch (PaymentChannels.classify(request.getPaymentVendor())) {
                case PaymentChannel.Wallet ignored -> {
                    fillSuccess(response);
                    response.setMsg("钱包支付不需要解约回调");
                    response.setRetMsg("钱包支付不需要解约回调");
                    auditLogger.write("RECEIVE_TERMINATION_RESULT_WALLET_IGNORED", request.getThirdUserId(),
                            request.getRequestSignSeq(), PaymentChannels.walletCode(),
                            signChannel, request, response);
                    return response;
                }
                case PaymentChannel.Contracted ignored -> {
                }
            }
            String validMsg = validateReceiveTerminationResult(request);
            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                auditLogger.write("RECEIVE_TERMINATION_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            String thirdUserId = CallbackLookups.resolveThirdUserId(paySignRequestMapper, request.getRequestSignSeq(), request.getThirdUserId());
            if (!StringUtils.hasText(thirdUserId)) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "thirdUserId不能为空");
                auditLogger.write("RECEIVE_TERMINATION_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }
            request.setThirdUserId(thirdUserId);

            resolveCardInfoFromSignInfo(request);

            AppTerminationRequest terminationRequest = terminationRequestMapper.selectByRequestSignSeq(request.getRequestSignSeq());
            if (terminationRequest == null) {
                fillError(response, PaySignErrorCodeEnum.TERMINATION_REQUEST_NOT_FOUND, "解约申请不存在");
                auditLogger.write("RECEIVE_TERMINATION_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            if (STATUS_SUCCESS.equals(terminationRequest.getTerminationStatus()) || STATUS_FAILED.equals(terminationRequest.getTerminationStatus())) {
                fillSuccess(response);
                auditLogger.write("RECEIVE_TERMINATION_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            resolveCardInfoFromTerminationRequest(request, terminationRequest);

            if (!STATUS_SCANNING.equals(terminationRequest.getTerminationStatus())) {
                fillError(response, PaySignErrorCodeEnum.TERMINATION_REQUEST_NOT_FOUND, "解约申请不存在或状态不正确");
                auditLogger.write("RECEIVE_TERMINATION_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            boolean isSuccess = "SUCCESS".equalsIgnoreCase(request.getStatus());

            if (isSuccess) {
                record LocalClosure(PaySignInfo signInfo, TerminationStatusTransition.Result transit) {
                }
                LocalClosure closure = transactionTemplate.execute(txStatus -> {
                    PaySignInfo local = paySignInfoMapper.selectByUserAndVendor(request.getThirdUserId(), request.getPaymentVendor());
                    paySignInfoMapper.deleteByUserAndVendor(request.getThirdUserId(), request.getPaymentVendor());

                    auditLogger.writeTerminationResultNotifyPending(request, signChannel, STATUS_UNSIGNED);

                    TerminationStatusTransition.Result t = TerminationStatusTransition.classify(
                            terminationRequestMapper.markSuccess(request.getRequestSignSeq(), LocalDateTime.now()),
                            TerminationStatus.SUCCESS,
                            () -> terminationRequestMapper.selectTerminationStatusBySeq(request.getRequestSignSeq()));
                    if (t.isDone()) {
                        terminationRequestMapper.initChannelSyncPending(request.getRequestSignSeq());
                    }
                    return new LocalClosure(local, t);
                });

                TerminationStatusTransition.Result transit = closure.transit();
                if (transit.isDone()) {
                    terminationNotifyService.asyncNotifyTerminationResult(terminationRequest, closure.signInfo(), request);
                    channelSyncDeliverer.deliver(request.getRequestSignSeq(), request.getThirdUserId(),
                            request.getPaymentVendor(), request.getCardId(), request.getCardType());
                } else if (transit.isConflict()) {
                    log.error("解约成功收口未命中 SCANNING，已跳过成功通知，MUST 人工核对, "
                                    + "requestSignSeq={}, outcome={}, observedStatus={}",
                            request.getRequestSignSeq(), transit.outcome(), transit.observedStatus());

                    if (TerminationStatus.FAILED.name().equals(transit.observedStatus())) {
                        int marked = terminationRequestMapper.markConflictForManualReview(
                                request.getRequestSignSeq(),
                                TerminationFailReason.successConflictNote(),
                                TerminationFailReason.MANUAL_REVIEW_MARK);
                        log.warn("解约结果矛盾已留痕, requestSignSeq={}, markedRows={}",
                                request.getRequestSignSeq(), marked);
                    }
                } else {
                    log.info("解约成功收口已由另一路完成，本次按幂等跳过, requestSignSeq={}, observedStatus={}",
                            request.getRequestSignSeq(), transit.observedStatus());
                }
            } else {
                String failReason = StringUtils.hasText(request.getStatus()) ? request.getStatus() : "支付平台解约失败";
                TerminationStatusTransition.Result rejectTransit = TerminationStatusTransition.classify(
                        terminationRequestMapper.rejectScanning(request.getRequestSignSeq(), failReason, LocalDateTime.now()),
                        TerminationStatus.FAILED,
                        () -> terminationRequestMapper.selectTerminationStatusBySeq(request.getRequestSignSeq()));

                if (rejectTransit.isDone()) {
                    NotifyTerminationFailedReqDTO failedNotifyRequest = new NotifyTerminationFailedReqDTO();
                    failedNotifyRequest.setThirdUserId(request.getThirdUserId());
                    failedNotifyRequest.setRequestSignSeq(request.getRequestSignSeq());
                    failedNotifyRequest.setPaymentVendor(request.getPaymentVendor());
                    failedNotifyRequest.setCardId(request.getCardId());
                    failedNotifyRequest.setCardType(request.getCardType());
                    failedNotifyRequest.setFailReason(failReason);
                    terminationNotifyService.asyncNotifyTerminationFailed(terminationRequest, failedNotifyRequest);
                } else {
                    log.error("解约失败收口未命中 SCANNING，已跳过失败通知, "
                                    + "requestSignSeq={}, outcome={}, observedStatus={}",
                            request.getRequestSignSeq(), rejectTransit.outcome(), rejectTransit.observedStatus());

                    if (TerminationStatus.SUCCESS.name().equals(rejectTransit.observedStatus())) {
                        int marked = terminationRequestMapper.markFailureConflictForManualReview(
                                request.getRequestSignSeq(),
                                TerminationFailReason.failureConflictNote(),
                                TerminationFailReason.MANUAL_REVIEW_MARK);
                        log.warn("解约结果矛盾已留痕（本次答复失败、库内已成功）, requestSignSeq={}, markedRows={}",
                                request.getRequestSignSeq(), marked);
                    }
                }

                auditLogger.write("RECEIVE_TERMINATION_RESULT", request.getThirdUserId(), request.getRequestSignSeq(),
                         request.getPaymentVendor(), signChannel, request, response, SIGN_LOG_STATUS_FAILED);
            }

            fillSuccess(response);
            return response;
        } catch (TerminationException e) {
            log.error("处理解约结果通知异常", e);
            throw e;
        } catch (Exception e) {
            log.error("处理解约结果通知异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            auditLogger.write("RECEIVE_TERMINATION_RESULT", request != null ? request.getThirdUserId() : null, request != null ? request.getRequestSignSeq() : null, request != null ? request.getPaymentVendor() : null, signChannel, request, response);
            return response;
        }
    }
    /** 支付平台回调可能不带 cardId/cardType，尝试从签约主表补充。 */
    private void resolveCardInfoFromSignInfo(ReceiveTerminationResultReqDTO request) {
        if (StringUtils.hasText(request.getCardId()) && StringUtils.hasText(request.getCardType())) {
            return;
        }
        if (!StringUtils.hasText(request.getRequestSignSeq())) {
            return;
        }
        try {
            PaySignInfo signInfo = paySignInfoMapper.selectBySeq(request.getRequestSignSeq(), request.getPaymentVendor());
            if (signInfo != null) {
                if (!StringUtils.hasText(request.getCardId()) && StringUtils.hasText(signInfo.getCardId())) {
                    request.setCardId(signInfo.getCardId());
                    log.info("从签约主表补充 cardId, requestSignSeq={}, cardId={}", request.getRequestSignSeq(), signInfo.getCardId());
                }
                if (!StringUtils.hasText(request.getCardType()) && StringUtils.hasText(signInfo.getCardType())) {
                    request.setCardType(signInfo.getCardType());
                    log.info("从签约主表补充 cardType, requestSignSeq={}, cardType={}", request.getRequestSignSeq(), signInfo.getCardType());
                }
            }
        } catch (Exception e) {
            log.warn("查询签约主表补充 cardId/cardType 异常, requestSignSeq={}", request.getRequestSignSeq(), e);
        }
    }
    /** 从解约申请记录补齐 cardId/cardType。 */
    private void resolveCardInfoFromTerminationRequest(ReceiveTerminationResultReqDTO request,
                                                       AppTerminationRequest terminationRequest) {
        if (terminationRequest == null) {
            return;
        }
        if (!StringUtils.hasText(request.getCardId()) && StringUtils.hasText(terminationRequest.getCardId())) {
            request.setCardId(terminationRequest.getCardId());
            log.info("从解约申请表补充 cardId, requestSignSeq={}, cardId={}",
                    request.getRequestSignSeq(), terminationRequest.getCardId());
        }
        if (!StringUtils.hasText(request.getCardType()) && StringUtils.hasText(terminationRequest.getCardType())) {
            request.setCardType(terminationRequest.getCardType());
            log.info("从解约申请表补充 cardType, requestSignSeq={}, cardType={}",
                    request.getRequestSignSeq(), terminationRequest.getCardType());
        }
    }

    /** 通道清理的**唯一发起点**已内联到 `receiveTerminationResult` 的本地事务提交之后（ADR-D48 / D122 续）。 */
}
