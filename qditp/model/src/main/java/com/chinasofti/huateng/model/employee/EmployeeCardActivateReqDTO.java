package com.chinasofti.huateng.model.employee;


public class EmployeeCardActivateReqDTO {

    private String cardNo;

    private Integer actionFlag;

    public String getCardNo() {
        return cardNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }

    public Integer getActionFlag() {
        return actionFlag;
    }

    public void setActionFlag(Integer actionFlag) {
        this.actionFlag = actionFlag;
    }
}
