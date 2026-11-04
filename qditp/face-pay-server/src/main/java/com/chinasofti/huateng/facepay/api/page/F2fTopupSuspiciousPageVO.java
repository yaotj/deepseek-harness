package com.chinasofti.huateng.facepay.api.page;

/** 运营端 TVM 充值存疑回执（{@code F2F_RESULT_REPORT}）分页查询视图对象。 */
public class F2fTopupSuspiciousPageVO {

    /** ITP 订单号。 */
    private String orderNo;

    /** 受理渠道：01-APP，02-TVM，03-BOM。 */
    private String channel;

    /** 受理设备号。 */
    private String deviceId;

    /** 操作员编号。 */
    private String operatorId;

    /** 充值回执状态：00 成功，01 失败，02 存疑，03 取消。 */
    private String topupStatus;

    /** 处理结论。 */
    private String optResult;

    /** 处理结论说明。 */
    private String optResultDesc;

    /** TVM 打印的故障单号。 */
    private String faultSlipSeq;

    /** 错误码。 */
    private String errorCode;

    /** 错误信息。 */
    private String errorMessage;

    /** 是否已处理：0-否，1-是。 */
    private String processed;

    /** 充值金额，单位分。 */
    private Long transAmount;

    /** 设备上报时刻。 */
    private String reportTms;

    /** 平台接收时刻，格式 {@code YYYY-MM-DD HH:MI:SS}。 */
    private String receiveTms;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }

    public String getTopupStatus() { return topupStatus; }
    public void setTopupStatus(String topupStatus) { this.topupStatus = topupStatus; }

    public String getOptResult() { return optResult; }
    public void setOptResult(String optResult) { this.optResult = optResult; }

    public String getOptResultDesc() { return optResultDesc; }
    public void setOptResultDesc(String optResultDesc) { this.optResultDesc = optResultDesc; }

    public String getFaultSlipSeq() { return faultSlipSeq; }
    public void setFaultSlipSeq(String faultSlipSeq) { this.faultSlipSeq = faultSlipSeq; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getProcessed() { return processed; }
    public void setProcessed(String processed) { this.processed = processed; }

    public Long getTransAmount() { return transAmount; }
    public void setTransAmount(Long transAmount) { this.transAmount = transAmount; }

    public String getReportTms() { return reportTms; }
    public void setReportTms(String reportTms) { this.reportTms = reportTms; }

    public String getReceiveTms() { return receiveTms; }
    public void setReceiveTms(String receiveTms) { this.receiveTms = receiveTms; }
}
