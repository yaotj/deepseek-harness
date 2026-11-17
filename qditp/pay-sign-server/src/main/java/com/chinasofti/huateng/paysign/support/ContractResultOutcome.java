package com.chinasofti.huateng.paysign.support;

import static com.chinasofti.huateng.paysign.support.PaySignValues.stringValue;

import com.chinasofti.huateng.model.app.RequestContractResultReqDTO;
import com.chinasofti.huateng.model.domain.SignStatus;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;

import java.util.Map;

public sealed interface ContractResultOutcome {

    PaySignInfo signInfo();


    record RefreshExisting(PaySignInfo signInfo, String previousStatus) implements ContractResultOutcome {
    }

    record OrphanSigned(PaySignInfo signInfo) implements ContractResultOutcome {
    }

    record NoLocalChange(PaySignInfo signInfo) implements ContractResultOutcome {
    }


    static ContractResultOutcome decide(RequestContractResultReqDTO request, String signChannel,
                                        PaySignInfo local, Map<String, Object> gatewayData) {
        boolean existed = local != null;
        if (gatewayData == null) {
            return new NoLocalChange(existed ? local : skeleton(request, signChannel, SignStatus.NOT_SIGNED.name()));
        }
        PaySignInfo signInfo = existed ? local : skeleton(request, signChannel, null);
        String previousStatus = existed ? signInfo.getContractStatus() : null;
        signInfo.setContractStatus(stringValue(gatewayData.get("status"), signInfo.getContractStatus()));
        signInfo.setPayAccountId(stringValue(gatewayData.get("payUserId"), signInfo.getPayAccountId()));
        signInfo.setPayAgreementNo(stringValue(gatewayData.get("payAgreementNo"), signInfo.getPayAgreementNo()));
        if (existed) {
            return new RefreshExisting(signInfo, previousStatus);
        }
        if (SignStatus.SIGNED.name().equals(signInfo.getContractStatus())) {
            return new OrphanSigned(signInfo);
        }
        return new NoLocalChange(signInfo);
    }

    private static PaySignInfo skeleton(RequestContractResultReqDTO request, String signChannel, String status) {
        PaySignInfo signInfo = new PaySignInfo();
        signInfo.setRequestSignSeq(request.getRequestSignSeq());
        signInfo.setThirdUserId(request.getThirdUserId());
        signInfo.setPaymentVendor(request.getPaymentVendor());
        signInfo.setSignChannel(signChannel);
        if (status != null) {
            signInfo.setContractStatus(status);
        }
        return signInfo;
    }
}
