package com.chinasofti.huateng.alipay.paysign.domain;

import org.springframework.util.StringUtils;

/**
 * 把支付中心（bestonepay）应答里的交易状态归一成本模块的 {@code PAY_STATUS}。
 *
 * <p><b>这是全模块唯一一份交易状态判据</b>，两个调用点共用：
 * ① {@code AlipayTxnPayQueryService.applyReply} —— 支付结果查询的回写判据；
 * ② {@code AlipayPayRequestServiceImpl.addBlackListIfNeeded} —— 加黑名单前的「确认真的失败」判据。
 * <b>NEVER 再抄第二份</b>：两处判据一旦分叉，就会出现「查询认为成功、加黑认为失败」这种自相矛盾的现场，
 * 而这种不一致在日志里看起来像两个无关的 bug。
 *
 * <p>本类是对 AGENTS.md §5.1「NEVER 主动创建新的工具类」的**有意破例**，理由同
 * {@code face-pay-server} 的 {@code F2fDuplicateKey}：判据要被跨包的两个类共用，抄私有方法等于两份逐字副本。
 * 它**只做值域归一、不做任何 IO、不注入任何 Spring bean**，因此可以是静态方法。
 *
 * <p><b>值域来源与已知缺口</b>：白名单取自 pay-sign 侧对同一个支付中心的既有归一
 * （{@code PaySignValues.convertPayStatus}）。供方文档 §1.2 只写了「{@code status} 交易状态」、
 * <b>没有给值域</b>，因此这份白名单**尚无本渠道的真实应答做证据**（ADR-D92 那条「外部网关的任何契约细节
 * MUST 有一次真实应答做证据」在这里还没闭合）。联调拿到第一条真实应答后 MUST 回来核对。
 *
 * <p><b>NEVER 改成「不在白名单里就算失败」</b>（黑名单式判定，AGENTS.md §5.2 明令禁止）：
 * 支付中心的中间态（受理中、待支付）一旦被判成失败，会同时引发两件坏事 ——
 * 支付明细被写成 {@code FAIL} 后既不回查也不补偿，以及**该卡被误加黑名单、乘客被拦在闸机外**。
 */
public final class PayCenterTradeStatus {

    /** 本模块 {@code ALIPAY_PAY_TXN_DETAIL.PAY_STATUS} 的成功值。 */
    public static final String SUCCESS = "SUCCESS";

    /** 本模块 {@code ALIPAY_PAY_TXN_DETAIL.PAY_STATUS} 的失败值。 */
    public static final String FAIL = "FAIL";

    private PayCenterTradeStatus() {
    }

    /**
     * 归一交易状态。
     *
     * @param tradeStatus 支付中心应答里的 {@code status} / {@code tradeStatus} 原值，可空
     * @return {@link #SUCCESS} / {@link #FAIL}，<b>判不出时返回 {@code null}</b>（调用方 MUST 当「未知」处理，
     *         NEVER 当失败）
     */
    public static String normalize(String tradeStatus) {
        if (!StringUtils.hasText(tradeStatus)) {
            return null;
        }
        String normalized = tradeStatus.trim().toUpperCase();
        if (SUCCESS.equals(normalized) || "PAID".equals(normalized)) {
            return SUCCESS;
        }
        if (FAIL.equals(normalized) || "FAILED".equals(normalized)) {
            return FAIL;
        }
        return null;
    }

    /**
     * 是否已被支付中心明确判定为失败。
     *
     * @param tradeStatus 支付中心应答里的交易状态原值，可空
     * @return 只有明确归一成 {@link #FAIL} 才返回 {@code true}；空值与中间态一律 {@code false}
     */
    public static boolean isConfirmedFail(String tradeStatus) {
        return FAIL.equals(normalize(tradeStatus));
    }
}
