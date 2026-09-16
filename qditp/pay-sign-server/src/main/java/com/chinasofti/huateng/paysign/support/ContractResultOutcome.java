package com.chinasofti.huateng.paysign.support;

import static com.chinasofti.huateng.paysign.support.PaySignValues.stringValue;

import com.chinasofti.huateng.model.app.RequestContractResultReqDTO;
import com.chinasofti.huateng.model.domain.SignStatus;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;

import java.util.Map;

/**
 * IF8A-22 签约结果查询在拿到支付平台应答后的**决策**（2026-09-16，ADR-D107）。
 *
 * <p><b>为什么是「决策」而不是「代码块」</b>：`requestContractResult` 里这段逻辑原本把三件事缠在一起 ——
 * ①用网关 {@code data} 算出 signInfo 的目标状态；②判断该不该落库；③真的落库（走 mapper）。
 * 整块外提会把 ①② 和 ③ 拆到两个文件，而「刷新状态」与「按状态决定是否落库」必须一起读才看得懂
 * （尤其是那条「孤儿协议不落库」的口径）。本类的做法是：**①② 收进纯函数并返回一个 sealed 结果，
 * ③ 留在业务类用穷尽 {@code switch} 消费** —— 判断与副作用分层，而不是把判断切成两半。
 *
 * <p>与 {@code RpcOutcome}（ADR-D45）/ {@code AccountQuery}（ADR-D94）同一手法：
 * <b>新增一个分支时，业务类那个 {@code switch} 会直接编译失败</b>，不依赖人记得去改。
 *
 * <p><b>本类 MUST 保持纯函数、零状态、零依赖</b>（support 包那条条件式破例，见 {@code PaySignValidators}）：
 * <b>NEVER 往本类注入 mapper / client</b> —— 一旦需要它们，说明落库动作被误搬进来了。
 */
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

    /**
     * **孤儿协议**：支付平台说已签约，本地却没有签约记录。
     *
     * <p><b>调用方 MUST 只告警、NEVER 落库</b>：本接口是查询接口，报文里拿不到
     * {@code CARD_ID} / {@code CARD_TYPE}，凭空 insert 会让解约链路拿 {@code NO_ACCOUNT_CARD}
     * 卡死在 SCANNING（生产已发生 3 条，只能改库清理）。签约记录只由 {@code receiveSignResult}
     * 与支付宝出行 {@code requestSignInfo} 创建。
     */
    record OrphanSigned(PaySignInfo signInfo) implements ContractResultOutcome {
    }

    /** 其余情形：不需要对本地做任何写入（含「平台无 data」与「本地无记录且平台未签约」）。 */
    record NoLocalChange(PaySignInfo signInfo) implements ContractResultOutcome {
    }

    /**
     * 按支付平台应答算出决策。<b>逐字保留原判断顺序与 {@code stringValue} 的「取不到就保留原值」语义。</b>
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
        // 以支付平台返回结果为准，刷新本地签约状态和协议号。
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
