package com.chinasofti.huateng.collectpay.entity;

/**
 * BOM业务操作结果通知实体类。
 * 对应数据库表TBL_BOM_BUS_RESULT，存储BOM业务操作结果通知信息。
 */
public class BomBusResult {

    /**
     * 通知ID（主键）。
     */
    private String notifyId;

    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 设备编码。
     */
    private String deviceId;

    /**
     * 操作结果。
     * SUCCESS-业务操作成功
     * FAILED-业务操作失败
     */
    private String optResult;

    /**
     * 通知状态。
     * 0-已通知待处理
     * 1-处理成功
     * 2-处理失败（退款成功）
     * 3-处理失败（退款失败）
     */
    private String status;

    /**
     * 退款订单号。
     * 当status=2时，记录退款订单号
     */
    private String refundOrderNo;

    /**
     * 创建时间，格式：yyyy-MM-dd HH:mm:ss。
     */
    private String createTime;

    /**
     * 更新时间，格式：yyyy-MM-dd HH:mm:ss。
     */
    private String updateTime;

    /**
     * 预留字段1。
     */
    private String rsv1;

    /**
     * 预留字段2。
     */
    private String rsv2;

    public String getNotifyId() {
        return notifyId;
    }

    public void setNotifyId(String notifyId) {
        this.notifyId = notifyId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getOptResult() {
        return optResult;
    }

    public void setOptResult(String optResult) {
        this.optResult = optResult;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRefundOrderNo() {
        return refundOrderNo;
    }

    public void setRefundOrderNo(String refundOrderNo) {
        this.refundOrderNo = refundOrderNo;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public String getRsv1() {
        return rsv1;
    }

    public void setRsv1(String rsv1) {
        this.rsv1 = rsv1;
    }

    public String getRsv2() {
        return rsv2;
    }

    public void setRsv2(String rsv2) {
        this.rsv2 = rsv2;
    }

    @Override
    public String toString() {
        return "BomBusResult{" +
                "notifyId='" + notifyId + '\'' +
                ", orderNo='" + orderNo + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", optResult='" + optResult + '\'' +
                ", status='" + status + '\'' +
                ", refundOrderNo='" + refundOrderNo + '\'' +
                ", createTime='" + createTime + '\'' +
                ", updateTime='" + updateTime + '\'' +
                ", rsv1='" + rsv1 + '\'' +
                ", rsv2='" + rsv2 + '\'' +
                '}';
    }
}