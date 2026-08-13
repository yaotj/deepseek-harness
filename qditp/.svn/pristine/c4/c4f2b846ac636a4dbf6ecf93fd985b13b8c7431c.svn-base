package com.chinasofti.huateng.model.employee;

import com.chinasofti.huateng.common.response.CommonResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 员工码状态通知处理结果。
 */
public class EmployeeCardNotifyResult extends CommonResult {
    private List<FailureItem> failList = new ArrayList<>();

    public List<FailureItem> getFailList() { return failList; }
    public void setFailList(List<FailureItem> failList) { this.failList = failList; }

    public static class FailureItem {
        private String cardNo;
        private String reason;

        public FailureItem() {
        }

        public FailureItem(String cardNo, String reason) {
            this.cardNo = cardNo;
            this.reason = reason;
        }

        public String getCardNo() { return cardNo; }
        public void setCardNo(String cardNo) { this.cardNo = cardNo; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
