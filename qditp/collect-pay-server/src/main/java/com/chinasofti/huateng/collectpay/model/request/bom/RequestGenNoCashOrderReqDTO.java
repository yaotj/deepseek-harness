package com.chinasofti.huateng.collectpay.model.request.bom;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;

/** IF8A-04 请求非现金收款下单请求DTO。 */
public class RequestGenNoCashOrderReqDTO extends BaseRequestDTO {

    /** 交易类型。 */
    private String transType;

    /** 行政交易类型代码。 */
    private String adminTransType;

    /** 操作员编码。 */
    private String operaterId;

    /** 班次序列号。 */
    private String shiftId;

    /** 逻辑卡号。 */
    private String cardId;

    /** 交易金额，单位：分。 */
    private String transAount;

    /** 操作流水号（终端设备流水号）。 */
    private String bomOptSeq;

    public String getTransType() {
        return transType;
    }

    public void setTransType(String transType) {
        this.transType = transType;
    }

    public String getAdminTransType() {
        return adminTransType;
    }

    public void setAdminTransType(String adminTransType) {
        this.adminTransType = adminTransType;
    }

    public String getOperaterId() {
        return operaterId;
    }

    public void setOperaterId(String operaterId) {
        this.operaterId = operaterId;
    }

    public String getShiftId() {
        return shiftId;
    }

    public void setShiftId(String shiftId) {
        this.shiftId = shiftId;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getTransAount() {
        return transAount;
    }

    public void setTransAount(String transAount) {
        this.transAount = transAount;
    }

    public String getBomOptSeq() {
        return bomOptSeq;
    }

    public void setBomOptSeq(String bomOptSeq) {
        this.bomOptSeq = bomOptSeq;
    }

    @Override
    public String toString() {
        return "RequestGenNoCashOrderReqDTO{" +
                "transType='" + transType + '\'' +
                ", adminTransType='" + adminTransType + '\'' +
                ", operaterId='" + operaterId + '\'' +
                ", shiftId='" + shiftId + '\'' +
                ", cardId='" + cardId + '\'' +
                ", transAount='" + transAount + '\'' +
                ", bomOptSeq='" + bomOptSeq + '\'' +
                ", providerId='" + getProviderId() + '\'' +
                ", deviceId='" + getDeviceId() + '\'' +
                '}';
    }
}