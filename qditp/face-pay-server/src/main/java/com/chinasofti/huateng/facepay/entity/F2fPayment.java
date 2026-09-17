package com.chinasofti.huateng.facepay.entity;

import java.time.LocalDateTime;

/** 与支付中心的支付交互流水（表 F2F_PAYMENT），一行一次尝试。 */
public class F2fPayment {

    /** 自增主键。 */
    private Long id;

    /** ITP 订单号，关联 F2F_ORDER.ORDER_NO。 */
    private String orderNo;

    /** 同一订单内的尝试序号，从1递增。 */
    private Integer attemptNo;

    /** qrcode-设备拉码用户扫，scan-主动扫用户付款码，app-APP内支付。 */
    private String payScene;

    /** 支付状态，取值 INIT / PROCESSING / SUCCESS / FAILED / UNKNOWN。 */
    private String payStatus;

    /** 本次尝试的支付金额，单位分。 */
    private Long payAmount;

    /** 0-本地拼聚合码URL不调支付中心，其他-调支付中心预下单。 */
    private String payType;

    /** 用户付款码，仅PAY_SCENE=scan时有值。 */
    private String authCode;

    /** 支付服务商标识。 */
    private String paymentVendor;

    /** 支付渠道编码。 */
    private String payChannelCode;

    /** 返回给设备显示为二维码的完整字符串，设备不解析。 */
    private String payUrl;

    /** 支付中心侧订单号，回调按此号反查本行，命中 IDX_F2F_PAY_CENTER_NO。 */
    private String payCenterOrderNo;

    /** 渠道（微信 / 支付宝等）侧订单号。 */
    private String channelOrderNo;

    /** 本次请求支付中心的原始报文，排障用。 */
    private String requestBody;

    /** 支付中心应答的原始报文，排障用。 */
    private String responseBody;

    /** 支付中心返回码。 */
    private String retCode;

    /** 支付中心返回描述。 */
    private String retMsg;

    /** 本次与支付中心交互耗时毫秒，用于排查虚拟线程pin与超时。 */
    private Integer costMs;

    /** 发起本次请求的时间。 */
    private LocalDateTime requestTms;

    /** 本次尝试到达终态（SUCCESS / FAILED）的时间。 */
    private LocalDateTime finishTms;

    /** 创建时间，分区键。 */
    private LocalDateTime createTms;

    /** 更新时间。 */
    private LocalDateTime updateTms;

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

    public Integer getAttemptNo() {
        return attemptNo;
    }

    public void setAttemptNo(Integer attemptNo) {
        this.attemptNo = attemptNo;
    }

    public String getPayScene() {
        return payScene;
    }

    public void setPayScene(String payScene) {
        this.payScene = payScene;
    }

    public String getPayStatus() {
        return payStatus;
    }

    public void setPayStatus(String payStatus) {
        this.payStatus = payStatus;
    }

    public Long getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(Long payAmount) {
        this.payAmount = payAmount;
    }

    public String getPayType() {
        return payType;
    }

    public void setPayType(String payType) {
        this.payType = payType;
    }

    public String getAuthCode() {
        return authCode;
    }

    public void setAuthCode(String authCode) {
        this.authCode = authCode;
    }

    public String getPaymentVendor() {
        return paymentVendor;
    }

    public void setPaymentVendor(String paymentVendor) {
        this.paymentVendor = paymentVendor;
    }

    public String getPayChannelCode() {
        return payChannelCode;
    }

    public void setPayChannelCode(String payChannelCode) {
        this.payChannelCode = payChannelCode;
    }
    public String getPayUrl() {
        return payUrl;
    }

    public void setPayUrl(String payUrl) {
        this.payUrl = payUrl;
    }

    public String getPayCenterOrderNo() {
        return payCenterOrderNo;
    }

    public void setPayCenterOrderNo(String payCenterOrderNo) {
        this.payCenterOrderNo = payCenterOrderNo;
    }

    public String getChannelOrderNo() {
        return channelOrderNo;
    }

    public void setChannelOrderNo(String channelOrderNo) {
        this.channelOrderNo = channelOrderNo;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public String getRetCode() {
        return retCode;
    }

    public void setRetCode(String retCode) {
        this.retCode = retCode;
    }
    public String getRetMsg() {
        return retMsg;
    }

    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

    public Integer getCostMs() {
        return costMs;
    }

    public void setCostMs(Integer costMs) {
        this.costMs = costMs;
    }

    public LocalDateTime getRequestTms() {
        return requestTms;
    }

    public void setRequestTms(LocalDateTime requestTms) {
        this.requestTms = requestTms;
    }

    public LocalDateTime getFinishTms() {
        return finishTms;
    }

    public void setFinishTms(LocalDateTime finishTms) {
        this.finishTms = finishTms;
    }

    public LocalDateTime getCreateTms() {
        return createTms;
    }

    public void setCreateTms(LocalDateTime createTms) {
        this.createTms = createTms;
    }

    public LocalDateTime getUpdateTms() {
        return updateTms;
    }

    public void setUpdateTms(LocalDateTime updateTms) {
        this.updateTms = updateTms;
    }
}
