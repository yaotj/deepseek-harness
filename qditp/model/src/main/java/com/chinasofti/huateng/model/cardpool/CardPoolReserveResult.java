package com.chinasofti.huateng.model.cardpool;

/**
 * 逻辑卡号预占的调用结果。
 *
 * <p>{@link #getData()} 仅在 {@link CardPoolOutcome#SUCCESS} 时非空；其余分支看 {@link #getOutcome()}
 * 决定降级方式，{@link #getMessage()} 是服务端原文，只用于日志与告警，NEVER 直接透给终端用户。</p>
 */
public class CardPoolReserveResult {

    private final CardPoolOutcome outcome;
    private final CardPoolReservationRespDTO data;
    private final String message;

    private CardPoolReserveResult(CardPoolOutcome outcome, CardPoolReservationRespDTO data, String message) {
        this.outcome = outcome;
        this.data = data;
        this.message = message;
    }

    /**
     * 构造成功结果。
     *
     * @param data 预占数据，含 reservationId、卡号与过期时间
     * @return 成功结果
     */
    public static CardPoolReserveResult success(CardPoolReservationRespDTO data) {
        return new CardPoolReserveResult(CardPoolOutcome.SUCCESS, data, null);
    }

    /**
     * 构造非成功结果。
     *
     * @param outcome 结果分类，不可为 SUCCESS
     * @param message 服务端原文或本地异常摘要
     * @return 失败结果
     */
    public static CardPoolReserveResult failure(CardPoolOutcome outcome, String message) {
        return new CardPoolReserveResult(outcome, null, message);
    }

    /**
     * 读取结果分类。
     *
     * @return 结果分类
     */
    public CardPoolOutcome getOutcome() {
        return outcome;
    }

    /**
     * 读取预占数据。
     *
     * @return 预占数据；非 SUCCESS 时为 null
     */
    public CardPoolReservationRespDTO getData() {
        return data;
    }

    /**
     * 读取失败原因原文。
     *
     * @return 失败原因；SUCCESS 时为 null
     */
    public String getMessage() {
        return message;
    }

    /**
     * 判断是否拿到了可用卡号。
     *
     * @return 成功且数据非空返回 true
     */
    public boolean isSuccess() {
        return outcome == CardPoolOutcome.SUCCESS && data != null;
    }

    /**
     * 判断本次失败是否值得重试。
     *
     * @return 仅 CALL_FAILED 与 POOL_EMPTY 可重试；REJECTED 属程序缺陷，重试无用
     */
    public boolean isRetryable() {
        return outcome == CardPoolOutcome.CALL_FAILED || outcome == CardPoolOutcome.POOL_EMPTY;
    }
}
