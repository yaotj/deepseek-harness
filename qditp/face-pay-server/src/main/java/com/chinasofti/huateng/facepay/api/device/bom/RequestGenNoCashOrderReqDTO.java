package com.chinasofti.huateng.facepay.api.device.bom;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/** IF8A-04 非现金收款下单入参。 */
public class RequestGenNoCashOrderReqDTO extends BaseDeviceRequest {

    /** BOM 交易类型：02/03/04/05/06/22/2A/2B/42。 */
    private String transType;

    /** {@code transType=42} 行政处理时必填，取值 01~0A。 */
    private String adminTransType;

    /** 操作员号。 */
    private String operaterId;

    private String shiftId;

    private String cardId;

    /** 交易金额，单位分。 */
    private String transAount;

    /** BOM 侧操作流水，幂等键。 */
    private String bomOptSeq;

    /**
     * @return null 表示不是合法数字
     */
    public Long amountInFen() {
        if (transAount == null || transAount.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(transAount.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

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
        return "RequestGenNoCashOrderReqDTO{transType=" + transType
                + ", adminTransType=" + adminTransType
                + ", operaterId=" + operaterId
                + ", shiftId=" + shiftId
                + ", cardId=" + cardId
                + ", transAount=" + transAount
                + ", bomOptSeq=" + bomOptSeq
                + ", deviceId=" + getDeviceId() + '}';
    }
}
