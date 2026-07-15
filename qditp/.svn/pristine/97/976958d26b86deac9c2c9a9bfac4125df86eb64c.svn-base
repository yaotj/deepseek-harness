package com.chinasofti.huateng.collectpay.entity;

import java.math.BigDecimal;

/**
 * TVM扫码购票订单表实体。
 */
public class TvmPayOrder {
    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 起点站点代码。
     */
    private String inStationCode;

    /**
     * 终点站点代码。
     */
    private String outStationCode;

    /**
     * 票价（单位：分）。单价
     */
    private String ticketPrice;

    /**
     * 购买数量。
     */
    private Integer ticketNum;

    /**
     * 购票类型：0-按站点购票，1-按固定票价购票。
     */
    private String ticketType;

    /**
     * 订单状态。
     */
    private String status;
    /**
     * 订单状态描述
     */
    private String msg;

    /**
     * 支付通道编码。
     */
    private String payCenterOrderNo;
    private String payCenterChannelOrderNo;
    private String channel;

    /**
     * 支付URL（二维码内容）。
     */
    private String url;

    /**
     * 创建时间。
     */
    private String createTime;

    /**
     * 更新时间（最后修改时间）。
     */
    private String updateTime;

    /**
     * 设备编码。
     */
    private String deviceId;

    /**
     * 撤销操作记录ID（rsv1）。
     */
    private String rsv1;

    /**
     * 退款操作记录ID（rsv2）。
     */
    private String rsv2;

    /**
     * 总价（单位：分）。
     */
    private String totalPrice;

    /**
     * 0：其他支付方式 1：数字人民币app
     */
    private String payType;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getInStationCode() {
        return inStationCode;
    }

    public void setInStationCode(String inStationCode) {
        this.inStationCode = inStationCode;
    }

    public String getOutStationCode() {
        return outStationCode;
    }

    public void setOutStationCode(String outStationCode) {
        this.outStationCode = outStationCode;
    }

    public String getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(String ticketPrice) {
        this.ticketPrice = ticketPrice;
    }

    public Integer getTicketNum() {
        return ticketNum;
    }

    public void setTicketNum(Integer ticketNum) {
        this.ticketNum = ticketNum;
    }

    public String getTicketType() {
        return ticketType;
    }

    public void setTicketType(String ticketType) {
        this.ticketType = ticketType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getPayCenterOrderNo() {
        return payCenterOrderNo;
    }

    public void setPayCenterOrderNo(String payCenterOrderNo) {
        this.payCenterOrderNo = payCenterOrderNo;
    }

    public String getPayCenterChannelOrderNo() {
        return payCenterChannelOrderNo;
    }

    public void setPayCenterChannelOrderNo(String payCenterChannelOrderNo) {
        this.payCenterChannelOrderNo = payCenterChannelOrderNo;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
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

    public String getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(String totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getPayType() {
        return payType;
    }

    public void setPayType(String payType) {
        this.payType = payType;
    }


    /**
     * 计算总价。
     * @return 总价（单位：分）
     */
    public BigDecimal calculateTotalPrice() {
        if (ticketPrice == null || ticketPrice.isEmpty() || ticketNum == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal price = new BigDecimal(ticketPrice);
        BigDecimal num = new BigDecimal(ticketNum);
        return price.multiply(num);
    }

    @Override
    public String toString() {
        return "TvmPayOrder{" +
                "orderNo='" + orderNo + '\'' +
                ", inStationCode='" + inStationCode + '\'' +
                ", outStationCode='" + outStationCode + '\'' +
                ", ticketPrice='" + ticketPrice + '\'' +
                ", ticketNum=" + ticketNum +
                ", ticketType='" + ticketType + '\'' +
                ", status='" + status + '\'' +
                ", msg='" + msg + '\'' +
                ", payCenterOrderNo='" + payCenterOrderNo + '\'' +
                ", payCenterChannelOrderNo='" + payCenterChannelOrderNo + '\'' +
                ", channel='" + channel + '\'' +
                ", url='" + url + '\'' +
                ", createTime='" + createTime + '\'' +
                ", updateTime='" + updateTime + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", rsv1='" + rsv1 + '\'' +
                ", rsv2='" + rsv2 + '\'' +
                ", totalPrice='" + totalPrice + '\'' +
                ", payType='" + payType + '\'' +
                '}';
    }
}
