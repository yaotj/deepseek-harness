package com.chinasofti.huateng.model.cardpool;


public class CardPoolReserveResult {

    private final CardPoolOutcome outcome;
    private final CardPoolReservationRespDTO data;
    private final String message;

    private CardPoolReserveResult(CardPoolOutcome outcome, CardPoolReservationRespDTO data, String message) {
        this.outcome = outcome;
        this.data = data;
        this.message = message;
    }


    public static CardPoolReserveResult success(CardPoolReservationRespDTO data) {
        return new CardPoolReserveResult(CardPoolOutcome.SUCCESS, data, null);
    }


    public static CardPoolReserveResult failure(CardPoolOutcome outcome, String message) {
        return new CardPoolReserveResult(outcome, null, message);
    }


    public CardPoolOutcome getOutcome() {
        return outcome;
    }


    public CardPoolReservationRespDTO getData() {
        return data;
    }


    public String getMessage() {
        return message;
    }


    public boolean isSuccess() {
        return outcome == CardPoolOutcome.SUCCESS && data != null;
    }


    public boolean isRetryable() {
        return outcome == CardPoolOutcome.CALL_FAILED || outcome == CardPoolOutcome.POOL_EMPTY;
    }
}
