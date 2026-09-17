package com.chinasofti.huateng.paysign.support;

import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import com.chinasofti.huateng.model.domain.TerminationStatus;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;

import java.time.LocalDateTime;

/** 解约申请的**本地实体装配**（2026-09-16 由 {@code ContractDomainServiceImpl.requestTermination} 逐字搬出，ADR-D101）。 */
public final class TerminationRules {

    /** 解约申请与通知的初始状态。取自枚举，NEVER 退回字面量 {@code "PENDING"}。 */
    private static final String STATUS_PENDING = TerminationStatus.PENDING.name();

    private TerminationRules() {
    }

    /** 装配一条待处理的解约申请（状态与通知状态均为 {@code PENDING}）。 */
    public static AppTerminationRequest buildTerminationRequest(RequestTerminationReqDTO request,
                                                               String cardId,
                                                               String cardType,
                                                               String paymentVendor,
                                                               LocalDateTime now) {
        AppTerminationRequest terminationRequest = new AppTerminationRequest();
        terminationRequest.setRequestSignSeq(request.getRequestSignSeq());
        terminationRequest.setThirdUserId(request.getThirdUserId());
        terminationRequest.setCardId(cardId);
        terminationRequest.setCardType(cardType);
        terminationRequest.setPaymentVendor(paymentVendor);
        terminationRequest.setTerminationStatus(STATUS_PENDING);
        terminationRequest.setNotifyStatus(STATUS_PENDING);
        terminationRequest.setNotifyRetryCount(0);
        terminationRequest.setRequestTime(now);
        terminationRequest.setCreateTime(now);
        terminationRequest.setUpdateTime(now);
        return terminationRequest;
    }
}
