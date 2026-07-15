package com.chinasofti.huateng.collectpay.entity;

/**
 * BOM非现金收款订单实体类。
 * 对应数据库表TBL_BOM_NOCASH_ORDER，存储BOM非现金收款业务的订单信息。
 */
public class BomNoCashOrder {

    /**
     * 订单号（主键）。
     */
    private String orderNo;

    /**
     * 设备编码。
     */
    private String deviceId;

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
     * 行政交易类型代码（transType=42时使用）。
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

    /**
     * 订单状态。
     * 0-支付中
     * 1-支付成功
     * 2-支付失败
     * 3-未支付
     */
    private String status;

    /**
     * 状态描述。
     * 描述订单当前状态的具体信息，如"支付成功"、"支付失败"、"处理中"等。
     */
    private String msg;

    /**
     * 支付通道编码。
     */
    private String paymentCode;

    /**
     * 支付账户认证码。
     */
    private String paymentVendor;

    /**
     * 支付渠道。
     */
    private String channel;

    /**
     * 支付URL。
     */
    private String url;

    /**
     * 创建时间，格式：yyyy-MM-dd HH:mm:ss。
     */
    private String createTime;

    /**
     * 更新时间，格式：yyyy-MM-dd HH:mm:ss。
     */
    private String updateTime;

    /**
     * 预留字段1（撤销操作ID）。
     */
    private String rsv1;

    /**
     * 预留字段2（退款操作ID）。
     */
    private String rsv2;

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

    public String getPaymentCode() {
        return paymentCode;
    }

    public void setPaymentCode(String paymentCode) {
        this.paymentCode = paymentCode;
    }

    public String getPaymentVendor() {
        return paymentVendor;
    }

    public void setPaymentVendor(String paymentVendor) {
        this.paymentVendor = paymentVendor;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
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
        return "BomNoCashOrder{" +
                "orderNo='" + orderNo + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", transType='" + transType + '\'' +
                ", adminTransType='" + adminTransType + '\'' +
                ", operaterId='" + operaterId + '\'' +
                ", shiftId='" + shiftId + '\'' +
                ", cardId='" + cardId + '\'' +
                ", transAount='" + transAount + '\'' +
                ", bomOptSeq='" + bomOptSeq + '\'' +
                ", status='" + status + '\'' +
                ", msg='" + msg + '\'' +
                ", paymentCode='" + paymentCode + '\'' +
                ", paymentVendor='" + paymentVendor + '\'' +
                ", channel='" + channel + '\'' +
                ", url='" + url + '\'' +
                ", createTime='" + createTime + '\'' +
                ", updateTime='" + updateTime + '\'' +
                ", rsv1='" + rsv1 + '\'' +
                ", rsv2='" + rsv2 + '\'' +
                '}';
    }
}