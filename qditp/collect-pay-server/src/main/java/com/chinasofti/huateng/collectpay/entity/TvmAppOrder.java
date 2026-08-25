package com.chinasofti.huateng.collectpay.entity;

/**
 * TVM APP下单订单实体类。
 * 对应数据库表TBL_TVM_APP_ORDER，存储APP下单业务的订单信息。
 */
public class TvmAppOrder {

    /**
     * 订单号（主键）。
     */
    private String orderNo;

    /**
     * 用户编码。
     */
    private String userId;

    /**
     * 起点站点代码。
     */
    private String inStationCode;

    /**
     * 终点站点代码。
     */
    private String outStationCode;

    /**
     * 票价，单位：分。
     */
    private String ticketPrice;

    /**
     * 购买数量。
     */
    private String ticketNum;

    /**
     * 总价
     */
    private String totalPrice;

    /**
     * 购票类型：0-有起点站和终点站。
     */
    private String ticketType;

    /**
     * 支付状态。
     * 0-未支付，1-支付成功，2-支付失败，3-支付中。
     */
    private String payStatus;

    private String msg;

    /**
     * 发起支付标志 0-未发起 1-已发起
     */
    private String requestPayFlag;

    /**
     * 支付通道编码。
     */
    private String payChannelCode;

    private String payCenterOrderNo;
    private String payCenterChannelOrderNo;

    /**
     * 支付交易流水号。
     */
    private String merchantOrderNo;

    /**
     * 支付金额。
     */
    private String payAmount;

    /**
     * 支付时间。
     */
    private String payTime;

    private String paymentInfo;

    /**
     * 激活标志。
     * 0-未激活，1-已激活。
     */
    private String activateFlag;

    private String deviceId;
    private String qrcodeGenDate;
    private String randomFact;
    private String activeTime;

    /**
     * 取票凭证（用于生成二维码）。
     */
    private String voucher;

    /**
     * 签名类型。
     * 00：不签名，01：sha1withrsa，02：MD5。
     */
    private String signType;

    /**
     * 签名值。
     */
    private String sign;

    /**
     * 创建时间。
     */
    private String createTime;

    /**
     * 更新时间。
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

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public String getTicketNum() {
        return ticketNum;
    }

    public void setTicketNum(String ticketNum) {
        this.ticketNum = ticketNum;
    }

    public String getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(String totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getTicketType() {
        return ticketType;
    }

    public void setTicketType(String ticketType) {
        this.ticketType = ticketType;
    }

    public String getPayStatus() {
        return payStatus;
    }

    public void setPayStatus(String payStatus) {
        this.payStatus = payStatus;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getRequestPayFlag() {
        return requestPayFlag;
    }

    public void setRequestPayFlag(String requestPayFlag) {
        this.requestPayFlag = requestPayFlag;
    }

    public String getPayChannelCode() {
        return payChannelCode;
    }

    public void setPayChannelCode(String payChannelCode) {
        this.payChannelCode = payChannelCode;
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

    public String getMerchantOrderNo() {
        return merchantOrderNo;
    }

    public void setMerchantOrderNo(String merchantOrderNo) {
        this.merchantOrderNo = merchantOrderNo;
    }

    public String getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(String payAmount) {
        this.payAmount = payAmount;
    }

    public String getPayTime() {
        return payTime;
    }

    public void setPayTime(String payTime) {
        this.payTime = payTime;
    }

    public String getPaymentInfo() {
        return paymentInfo;
    }

    public void setPaymentInfo(String paymentInfo) {
        this.paymentInfo = paymentInfo;
    }

    public String getActivateFlag() {
        return activateFlag;
    }

    public void setActivateFlag(String activateFlag) {
        this.activateFlag = activateFlag;
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

    public String getActiveTime() {
        return activeTime;
    }

    public void setActiveTime(String activeTime) {
        this.activeTime = activeTime;
    }

    public String getVoucher() {
        return voucher;
    }

    public void setVoucher(String voucher) {
        this.voucher = voucher;
    }

    public String getSignType() {
        return signType;
    }

    public void setSignType(String signType) {
        this.signType = signType;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
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
}
