package com.chinasofti.huateng.collectpay.entity;

import java.time.LocalDateTime;

/**
 * TVM扫码取票订单表实体。
 */
public class TvmTakeTicketOrder {
    /**
     * 主键ID。
     */
    private Long id;

    /**
     * 订单号（原支付订单号）。
     */
    private String orderNo;

    /**
     * 取票设备编码。
     */
    private String deviceId;

    /**
     * 二维码生成时间。
     */
    private String qrcodeGenDate;

    /**
     * 随机因子。
     */
    private String randomFact;

    /**
     * 激活状态：0-未激活，1-已激活。
     */
    private String activeStatus;

    /**
     * 激活时间。
     */
    private String activeTime;

    /**
     * 创建时间。
     */
    private String createTime;

    /**
     * 更新时间。
     */
    private String updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getQrcodeGenDate() {
        return qrcodeGenDate;
    }

    public void setQrcodeGenDate(String qrcodeGenDate) {
        this.qrcodeGenDate = qrcodeGenDate;
    }

    public String getRandomFact() {
        return randomFact;
    }

    public void setRandomFact(String randomFact) {
        this.randomFact = randomFact;
    }

    public String getActiveStatus() {
        return activeStatus;
    }

    public void setActiveStatus(String activeStatus) {
        this.activeStatus = activeStatus;
    }

    public String getActiveTime() {
        return activeTime;
    }

    public void setActiveTime(String activeTime) {
        this.activeTime = activeTime;
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

    @Override
    public String toString() {
        return "TvmTakeTicketOrder{" +
                "id=" + id +
                ", orderNo='" + orderNo + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", qrcodeGenDate='" + qrcodeGenDate + '\'' +
                ", randomFact='" + randomFact + '\'' +
                ", activeStatus=" + activeStatus +
                ", activeTime='" + activeTime + '\'' +
                ", createTime='" + createTime + '\'' +
                ", updateTime='" + updateTime + '\'' +
                '}';
    }
}
