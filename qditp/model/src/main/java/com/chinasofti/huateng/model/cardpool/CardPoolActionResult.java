package com.chinasofti.huateng.model.cardpool;


public class CardPoolActionResult {

    private final CardPoolOutcome outcome;
    private final String message;

    private CardPoolActionResult(CardPoolOutcome outcome, String message) {
        this.outcome = outcome;
        this.message = message;
    }


    public static CardPoolActionResult success() {
        return new CardPoolActionResult(CardPoolOutcome.SUCCESS, null);
    }


    public static CardPoolActionResult success(String message) {
        return new CardPoolActionResult(CardPoolOutcome.SUCCESS, message);
    }


    public static CardPoolActionResult failure(CardPoolOutcome outcome, String message) {
        return new CardPoolActionResult(outcome, message);
    }


    public CardPoolOutcome getOutcome() {
        return outcome;
    }


    public String getMessage() {
        return message;
    }


    public boolean isSuccess() {
        return outcome == CardPoolOutcome.SUCCESS;
    }


    public boolean isRetryable() {
        return outcome == CardPoolOutcome.CALL_FAILED;
    }
}
