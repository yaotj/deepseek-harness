package com.chinasofti.huateng.model.cardpool;

/**
 * 预占确认 / 释放的调用结果。
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
     * @return 成功结果。
     */
    public static CardPoolActionResult success() {
        return new CardPoolActionResult(CardPoolOutcome.SUCCESS, null);
    }

    /**
     * 构造带说明的成功结果，供「成功但有话要说」的场景使用。
     * @param message 服务端原文。
     * @return 成功结果。
     */
    public static CardPoolActionResult success(String message) {
        return new CardPoolActionResult(CardPoolOutcome.SUCCESS, message);
    }

    /**
     * 构造非成功结果。
     * @param outcome 结果分类，不可为 SUCCESS。
     * @param message 服务端原文或本地异常摘要。
     * @return 失败结果。
     */
    public static CardPoolActionResult failure(CardPoolOutcome outcome, String message) {
        return new CardPoolActionResult(outcome, message);
    }

    /**
     * 读取结果分类。
     * @return 结果分类。
     */
    public CardPoolOutcome getOutcome() {
        return outcome;
    }

    /**
     * 读取失败。
     * @return 失败。
     */
    public String getMessage() {
        return message;
    }

    /**
     * 判断动作是否已收口。
     * @return 成功返回 true。
     */
    public boolean isSuccess() {
        return outcome == CardPoolOutcome.SUCCESS;
    }

    /**
     * 判断本次失败是否值得重试。
     * @return 仅 CALL_FAILED 可重试；REJECTED 表示状态或归。
     */
    public boolean isRetryable() {
        return outcome == CardPoolOutcome.CALL_FAILED;
    }
}
