package com.chinasofti.huateng.paysign.service.impl;

import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillError;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillSuccess;
import static com.chinasofti.huateng.paysign.support.PaySignValidators.validateReceiveSignResult;
import static com.chinasofti.huateng.paysign.support.PaySignValues.parseDateTime;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.domain.SignStatus;
import com.chinasofti.huateng.paysign.audit.PaySignAuditLogger;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.support.CallbackLookups;
import com.chinasofti.huateng.paysign.domain.PaySignDuplicateKey;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.event.SignResultCommittedEvent;
import com.chinasofti.huateng.paysign.mapper.PaySignInfoMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignRequestMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * IPD02 签约结果回调的真实现（2026-09-17 由 {@code CallbackDomainServiceImpl} 按依赖簇拆出，ADR-D120）。
 *
 * <p>协作者 4 个：签约主表、流水表、审计流水、事件发布器。<b>方法体一字未改</b>，
 * 只把共享的 {@code resolveThirdUserId} 换成 {@link CallbackLookups} 的静态调用。
 *
 * <p><b>NEVER 把解约回调搬进本类</b>：那条链路要 {@code AppTerminationRequestMapper}、
 * {@code AppNotifyService}、{@code TransactionTemplate}、{@code ChannelSyncDeliverer} 四个本类用不到的协作者，
 * 合在一起就是拆分前那个 8 协作者的类。
 *
 * <p>{@code @Transactional} 保留：本方法两次 INSERT（签约主表 + 流水表）MUST 原子，
 * 且事件发布靠它落到 {@code AFTER_COMMIT}（ADR-D48）。
 */
@Service
public class SignResultCallbackHandler {
    private static final Logger log = LoggerFactory.getLogger(SignResultCallbackHandler.class);

    /**
     * 签约状态字面量**一律从 {@link SignStatus} 取**，NEVER 写裸字符串（2026-09-17，ADR-D127）。
     *
     * <p>库内 {@code APP_PAY_SIGN_INFO.SIGN_STATUS} 与 {@code APP_PAY_SIGN_REQUEST.SIGN_STATUS}
     * 存的就是枚举名，改枚举即改全部比较点；写裸字面量则会漏改。
     */
    private static final String STATUS_SIGNED = SignStatus.SIGNED.name();
    private static final String SIGN_LOG_STATUS_FAILED = SignStatus.FAILED.name();

    private final PaySignInfoMapper paySignInfoMapper;
    private final PaySignRequestMapper paySignRequestMapper;
    private final PaySignAuditLogger auditLogger;
    private final ApplicationEventPublisher eventPublisher;

    public SignResultCallbackHandler(
            PaySignInfoMapper paySignInfoMapper,
            PaySignRequestMapper paySignRequestMapper,
            PaySignAuditLogger auditLogger,
            ApplicationEventPublisher eventPublisher) {
        this.paySignInfoMapper = paySignInfoMapper;
        this.paySignRequestMapper = paySignRequestMapper;
        this.auditLogger = auditLogger;
        this.eventPublisher = eventPublisher;
    }

    /** IPD02 签约结果回调。 */
    @Transactional(rollbackFor = Exception.class)
    public PaySignCallbackResult receiveSignResult(ReceiveSignResultReqDTO request, String signChannel) {
        PaySignCallbackResult response = new PaySignCallbackResult();
        try {
            if (request == null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "请求报文不能为空");
                auditLogger.write("RECEIVE_SIGN_RESULT", null, null, null, null, null, response);
                return response;
            }
            String validMsg = validateReceiveSignResult(request);
            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                auditLogger.write("RECEIVE_SIGN_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            String thirdUserId = CallbackLookups.resolveThirdUserId(paySignRequestMapper, request.getRequestSignSeq(), request.getThirdUserId());
            if (!StringUtils.hasText(thirdUserId)) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "thirdUserId不能为空");
                auditLogger.write("RECEIVE_SIGN_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }
            request.setThirdUserId(thirdUserId);

            String displayAccount = resolveDisplayAccount(request.getRequestSignSeq(), request.getDisplayAccount());
            request.setDisplayAccount(displayAccount);

            boolean isSuccess = "SUCCESS".equalsIgnoreCase(request.getStatus());

            log.info("签约成功，准备通知app--- {} ., displayAccount: {}",request,displayAccount);
            if (isSuccess) {
                PaySignInfo signInfo = new PaySignInfo();
                signInfo.setRequestSignSeq(request.getRequestSignSeq());
                signInfo.setThirdUserId(request.getThirdUserId());
                signInfo.setPaymentVendor(request.getPaymentVendor());
                signInfo.setSignChannel(signChannel);
                signInfo.setDisplayAccount(request.getDisplayAccount());
                signInfo.setPayAccountId(request.getPayUserId());
                signInfo.setPayAgreementNo(request.getPayAgreementNo());
                signInfo.setContractStatus(STATUS_SIGNED);
                signInfo.setSignTime(parseDateTime(request.getSignTime(), null));
                try {
                    paySignInfoMapper.insert(signInfo);
                } catch (RuntimeException e) {
                    if (!PaySignDuplicateKey.isConflict(e)) {
                        throw e;
                    }
                    return replaySignResult(request, signChannel, response);
                }

                auditLogger.writeSignResultNotifyPending(request, signChannel, STATUS_SIGNED);

                eventPublisher.publishEvent(new SignResultCommittedEvent(
                        request.getRequestSignSeq(), request.getPaymentVendor(), request));
            } else {
                auditLogger.write("RECEIVE_SIGN_RESULT", request.getThirdUserId(), request.getRequestSignSeq(),
                         request.getPaymentVendor(), signChannel, request, response, SIGN_LOG_STATUS_FAILED);
            }

            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("处理签约结果通知异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            auditLogger.write("RECEIVE_SIGN_RESULT", request != null ? request.getThirdUserId() : null, request != null ? request.getRequestSignSeq() : null, request != null ? request.getPaymentVendor() : null, signChannel, request, response);
            return response;
        }
    }
    /**
     * 同一 {@code requestSignSeq} 的签约成功回调重放（2026-09-17，ADR-D123）。
     *
     * <p>撞 {@code UK_APPSI_REQUEST_SIGN_SEQ} 说明上一次已经落库并已发过 APP 通知，
     * 因此这里 <b>只留一条审计痕迹、NEVER 再发 {@code SignResultCommittedEvent}</b>
     * （否则 APP 会收到重复通知），也 NEVER 再插带 {@code NOTIFY_STATUS='PENDING'} 的流水
     * （否则 {@code /internal/paySign/compensateNotify} 会把它当待补偿任务再推一遍）。
     *
     * <p>返回 {@code 0000}：渠道与支付中心的回调普遍会重推，返失败码只会引来更多重推。
     */
    private PaySignCallbackResult replaySignResult(ReceiveSignResultReqDTO request, String signChannel,
                                                   PaySignCallbackResult response) {
        String observedStatus = paySignInfoMapper.selectSignStatusBySeq(request.getRequestSignSeq());
        if (!STATUS_SIGNED.equals(observedStatus)) {
            log.warn("签约结果回调重放，但库内状态不是 SIGNED，按幂等返成功且不复活, requestSignSeq={}, observedStatus={}, signChannel={}",
                    request.getRequestSignSeq(), observedStatus, signChannel);
        } else {
            log.warn("签约结果回调幂等重放（已是SIGNED，不重复通知）, requestSignSeq={}, signChannel={}",
                    request.getRequestSignSeq(), signChannel);
        }
        fillSuccess(response);
        auditLogger.write("RECEIVE_SIGN_RESULT_REPLAY", request.getThirdUserId(), request.getRequestSignSeq(),
                request.getPaymentVendor(), signChannel, request, response);
        return response;
    }

    /** 支付平台回调可能不带 displayAccount，尝试从流水表补充。 */
    private String resolveDisplayAccount(String requestSignSeq, String displayAccount) {
        if (StringUtils.hasText(displayAccount)) {
            return displayAccount;
        }
        if (!StringUtils.hasText(requestSignSeq)) {
            return null;
        }
        try {
            PaySignRequest record = paySignRequestMapper.selectByRequestSignSeq(requestSignSeq);
            if (record != null && StringUtils.hasText(record.getDisplayAccount())) {
                log.info("从流水表补充 displayAccount, requestSignSeq={}, displayAccount={}", requestSignSeq, record.getDisplayAccount());
                return record.getDisplayAccount();
            }
        } catch (Exception e) {
            log.warn("查询流水表补充 displayAccount 异常, requestSignSeq={}", requestSignSeq, e);
        }
        return null;
    }
}
