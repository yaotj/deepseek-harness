package com.chinasofti.huateng.gatetxnpay.model.page;

import java.util.ArrayList;
import java.util.List;

public class BatchRefundResult {
    private int total;
    private int successCount;
    private int failCount;
    private List<ItemResult> items = new ArrayList<>();

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public void setFailCount(int failCount) {
        this.failCount = failCount;
    }

    public List<ItemResult> getItems() {
        return items;
    }

    public void setItems(List<ItemResult> items) {
        this.items = items;
    }

    public static class ItemResult {
        private String orderNo;
        private Integer refundAmount;
        private boolean success;
        private String retMsg;

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public Integer getRefundAmount() {
            return refundAmount;
        }

        public void setRefundAmount(Integer refundAmount) {
            this.refundAmount = refundAmount;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getRetMsg() {
            return retMsg;
        }

        public void setRetMsg(String retMsg) {
            this.retMsg = retMsg;
        }
    }
}
