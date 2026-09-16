package com.chinasofti.huateng.model.employee;

/**
 * APP 请求电子员工卡激活或禁用。
 */
public class EmployeeCardActivateReqDTO {

    /** 员工码卡号，长度上限 20，服务端会 trim 后再查库与转发 ACC。 */
    private String cardNo;

    /** 动作标志：1 激活（要求当前 {@code CARD_STATUS=3}）、0 禁用（要求当前 {@code CARD_STATUS=1}）。 */
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
