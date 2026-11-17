package com.chinasofti.huateng.paysign.support;

import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import com.chinasofti.huateng.model.domain.TerminationStatus;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;

import java.time.LocalDateTime;

public final class TerminationRules {

    private static final String STATUS_PENDING = TerminationStatus.PENDING.name();

    private TerminationRules() {
    }

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
