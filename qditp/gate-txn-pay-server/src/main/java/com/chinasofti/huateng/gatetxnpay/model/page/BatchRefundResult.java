package com.chinasofti.huateng.gatetxnpay.model.page;

import java.util.ArrayList;
import java.util.List;

/**
 * 综管台批量退超时罚金的结果对象。
 *
 * <p>批量语义是「逐单走单笔退款链路、单票失败不阻断整批」，因此结果必须能
 * 表达部分成功：汇总计数 + 逐笔明细（含失败原因），NEVER 退化成「整批成功/失败」
 * 的二值——运营需要知道哪几笔没退成、为什么，再决定是否补退。</p>
 */
public class BatchRefundResult {
    /** 本批提交的总笔数。 */
    private int total;
    /** 成功发起退款的笔数（仅指支付中心受理，不代表已退到账）。 */
    private int successCount;
    /** 发起失败的笔数（含圈单后被单笔白名单/金额校验拦下的）。 */
    private int failCount;
    /** 逐笔明细，长度恒等于 total。 */
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

    /** 逐笔退款发起结果。 */
    public static class ItemResult {
        private String orderNo;
        /** 本笔退款金额（分），取自订单 OVERTIME_AMOUNT。 */
        private Integer refundAmount;
        /** true=支付中心已受理；false=被拒（retMsg 给原因）。 */
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
