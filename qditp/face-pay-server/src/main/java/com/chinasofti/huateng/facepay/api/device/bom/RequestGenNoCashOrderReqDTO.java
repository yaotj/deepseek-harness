package com.chinasofti.huateng.facepay.api.device.bom;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/**
 * IF8A-04 非现金收款下单入参。
 *
 * <p><b>{@code transAount} 的拼写错误是既有契约</b>（少一个 m），设备侧按此拼装 bizData，
 * NEVER 更正。</p>
 *
 * <p>{@code bomOptSeq} 是 BOM 侧操作流水，在本服务里落到 {@code F2F_ORDER.DEVICE_SEQ}，
 * 由 {@code UK_F2F_ORDER_DEV_SEQ}（CHANNEL + DEVICE_ID + DEVICE_SEQ）保证同一台 BOM 的
 * 同一笔操作只会生成一张订单。旧实现只入库不判重，重复请求会重复开单。</p>
 */
public class RequestGenNoCashOrderReqDTO extends BaseDeviceRequest {

    /** BOM 交易类型：02/03/04/05/06/22/2A/2B/42。 */
    private String transType;

    /** {@code transType=42} 行政处理时必填，取值 01~0A。 */
    private String adminTransType;

    /** 操作员号。旧字段名少一个 t（operater），照搬。 */
    private String operaterId;

    private String shiftId;

    private String cardId;

    /** 交易金额，单位分。拼写错误照搬。 */
    private String transAount;

    /** BOM 侧操作流水，幂等键。 */
    private String bomOptSeq;

    /** @return null 表示不是合法数字 */
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
