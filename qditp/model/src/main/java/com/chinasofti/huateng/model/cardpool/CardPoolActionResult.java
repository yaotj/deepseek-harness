package com.chinasofti.huateng.model.cardpool;

/**
 * 预占确认 / 释放的调用结果。
 *
 * <p>服务端对这两个动作已做幂等兜底（影响行数为 0 时回查终态），因此 {@link CardPoolOutcome#SUCCESS}
 * 同时覆盖「本次改状态成功」与「此前已到终态」。{@link CardPoolOutcome#REJECTED} 表示归属或状态不允许，
 * {@link CardPoolOutcome#CALL_FAILED} 表示没能问到结论、需要重试或靠预占超时兜底回收。</p>
 */
public class CardPoolActionResult {

    private final CardPoolOutcome outcome;
    private final String message;

    private CardPoolActionResult(CardPoolOutcome outcome, String message) {
        this.outcome = outcome;
        this.message = message;
    }

    /**
     * 构造成功结果。
     *
     * @return 成功结果
     */
    public static CardPoolActionResult success() {
        return new CardPoolActionResult(CardPoolOutcome.SUCCESS, null);
    }

    /**
     * 构造带说明的成功结果，供「成功但有话要说」的场景使用。
     *
     * <p>典型场景是维护受理返回 {@code accepted=false}（上一轮尚未结束）：调用成功、不该告警，
     * 但调用方需要把服务端原文打进日志，否则前台只看到「执行成功」却看不出这一轮实际没干活。</p>
     *
     * @param message 服务端原文
     * @return 成功结果
     */
    public static CardPoolActionResult success(String message) {
        return new CardPoolActionResult(CardPoolOutcome.SUCCESS, message);
    }

    /**
     * 构造非成功结果。
     *
     * @param outcome 结果分类，不可为 SUCCESS
     * @param message 服务端原文或本地异常摘要
     * @return 失败结果
     */
    public static CardPoolActionResult failure(CardPoolOutcome outcome, String message) {
        return new CardPoolActionResult(outcome, message);
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
     * 读取失败原因原文。
     *
     * @return 失败原因；SUCCESS 时为 null
     */
    public String getMessage() {
        return message;
    }

    /**
     * 判断动作是否已收口。
     *
     * @return 成功返回 true
     */
    public boolean isSuccess() {
        return outcome == CardPoolOutcome.SUCCESS;
    }

    /**
     * 判断本次失败是否值得重试。
     *
     * @return 仅 CALL_FAILED 可重试；REJECTED 表示状态或归属不允许，重试无用
     */
    public boolean isRetryable() {
        return outcome == CardPoolOutcome.CALL_FAILED;
    }
}
