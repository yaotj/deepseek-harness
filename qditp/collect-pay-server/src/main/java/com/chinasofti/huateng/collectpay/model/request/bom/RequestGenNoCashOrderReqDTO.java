package com.chinasofti.huateng.collectpay.model.request.bom;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;

/**
 * IF8A-04 请求非现金收款下单请求DTO。
 * BOM向ITP平台发起非现金收款订单请求时使用的业务参数。
 */
public class RequestGenNoCashOrderReqDTO extends BaseRequestDTO {

    /**
     * 交易类型。
     * 02:超时更新
     * 03:超程更新/一卡通余额不足更新
     * 04:未出站更新处理
     * 05:无入站更新处理
     * 06:储值票即时退卡/单程票退票
     * 22:充值
     * 2A:黑名单卡锁定
     * 2B:卡锁定解除
     * 42:行政处理
     */
    private String transType;

    /**
     * 行政交易类型代码。
     * 当transType=42时必填。
     * 01：付费区内丢失车票、无票出闸、车票折损
     * 02：乘客出闸时，闸门被误用
     * 03：乘客所持车票损坏，不能出闸
     * 04：TVM发售单程票时，发生卡票
     * 05：TVM找零时，发生卡币或找零不够
     * 06：TVM/BOM发售的单程票无效，不能进闸
     * 07：储值卡购票时已扣值，但发售未正常完成
     * 08：预发售单程票退票
     * 09：特殊情况下单程票退票
     * 0A：其它情况
     */
    private String adminTransType;

    /**
     * 操作员编码。
     */
    private String operaterId;

    /**
     * 班次序列号。
     */
    private String shiftId;

    /**
     * 逻辑卡号。
     */
    private String cardId;

    /**
     * 交易金额，单位：分。
     */
    private String transAount;

    /**
     * 操作流水号（终端设备流水号）。
     */
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