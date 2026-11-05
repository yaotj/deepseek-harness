package com.chinasofti.huateng.facepay.service;

public record RefundOutcome(String refundNo,
                            String refundStatus,
                            boolean alreadyExisted,
                            String failureReason) {

    public static RefundOutcome rejected(String reason) {
        return new RefundOutcome(null, null, false, reason);
    }

    public boolean isRejected() {
        return refundNo == null;
    }

    public boolean isAccepted() {
        return F2fRefundService.STATUS_PROCESSING.equals(refundStatus)
                || F2fRefundService.STATUS_SUCCESS.equals(refundStatus);
    }
}
