package com.chinasofti.huateng.paysign.support;

import static com.chinasofti.huateng.paysign.support.PaySignValues.stringValue;

import com.chinasofti.huateng.model.app.RequestContractResultReqDTO;
import com.chinasofti.huateng.model.domain.SignStatus;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;

import java.util.Map;

/** IF8A-22 签约结果查询在拿到支付平台应答后的**决策**（2026-09-16，ADR-D107）。 */
public sealed interface ContractResultOutcome {

    /** 决策后的签约信息快照；三个分支都带，调用方据它填应答。 */
    PaySignInfo signInfo();

    /**
     * 本地已有签约记录，且支付平台返回了 {@code data} ⇒ 需要把目标状态落库。
     *
     * @param previousStatus 刷新**之前**的本地状态，落库时用来判断是「同状态补字段」还是「状态迁移」。
     */
    record RefreshExisting(PaySignInfo signInfo, String previousStatus) implements ContractResultOutcome {
    }

    /** **孤儿协议**：支付平台说已签约，本地却没有签约记录。 */
    record OrphanSigned(PaySignInfo signInfo) implements ContractResultOutcome {
    }

    /** 其余情形：不需要对本地做任何写入（含「平台无 data」与「本地无记录且平台未签约」）。 */
    record NoLocalChange(PaySignInfo signInfo) implements ContractResultOutcome {
    }

    /**
     * 按支付平台应答算出决策。逐字保留原判断顺序与 {@code stringValue} 的「取不到就保留原值」语义。
     *
     * @param local       本地签约记录，{@code null} 表示不存在
     * @param gatewayData 网关 {@code data}，{@code null} 表示平台没给数据
     */
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
