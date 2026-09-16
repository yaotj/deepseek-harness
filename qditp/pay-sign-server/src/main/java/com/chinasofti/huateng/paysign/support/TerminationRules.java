package com.chinasofti.huateng.paysign.support;

import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import com.chinasofti.huateng.model.domain.TerminationStatus;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;

import java.time.LocalDateTime;

/**
 * 解约申请的**本地实体装配**（2026-09-16 由 {@code ContractDomainServiceImpl.requestTermination} 逐字搬出，ADR-D101）。
 *
 * <p>与 {@link PayRefundRules} / {@link PayTxnRules} 同一条判据（ADR-D98）：只收**不持有任何协作者**的逻辑。
 * <b>本类 MUST 保持纯函数、零状态、零依赖，NEVER 注入 mapper / client / properties。</b>
 *
 * <p><b>这是「片段级」外提，不是整方法外提</b>：`requestTermination` 104 行里能独立出来的只有这一段
 * —— 其余每一段都夹着 `paySignInfoMapper` / `terminationRequestMapper` / `auditLogger`。
 * <b>NEVER 试图把该方法的状态机判断（FAILED 复活的 CAS、按流水号的重复申请拦截）也搬进来</b>，
 * 那些都要读写 mapper。
 */
public final class TerminationRules {

    /** 解约申请与通知的初始状态。取自枚举，<b>NEVER 退回字面量 {@code "PENDING"}</b>。 */
    private static final String STATUS_PENDING = TerminationStatus.PENDING.name();

    private TerminationRules() {
    }

    /**
     * 装配一条待处理的解约申请（状态与通知状态均为 {@code PENDING}）。
     *
     * <p>{@code cardId} / {@code cardType} / {@code paymentVendor} <b>由调用方解析后传入</b>，
     * 不在本方法内从 {@code signInfo} 回填 —— 那三个值在「FAILED 复活」分支里也要用（喂给
     * {@code reactivateFailed}），放进来会让两个分支各解析一次、口径可能漂移。
     *
     * <p><b>{@code CREATE_TIME} / {@code UPDATE_TIME} MUST 显式赋值</b>：为 null 时不会走列默认值，
     * 直接触发 {@code ORA-01400}。同理 {@code cardId} / {@code cardType} / {@code paymentVendor}
     * 三列在 {@code APP_TERMINATION_REQUEST} 里都是 NOT NULL。
     */
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
