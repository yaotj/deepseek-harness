package com.chinasofti.huateng.facepay.entity;

import java.time.LocalDateTime;

/**
 * 设备业务结果上报（表 F2F_RESULT_REPORT）。
 *
 * <p>字段与 face-pay-server/src/main/resources/sql/f2f-schema.sql 一一对应，
 * 改字段 MUST 同步改 DDL。
 *
 * <p>本表统一承载六个设备上报接口（TVM 出票结果 / 出票故障 / 充值结果 / 充值失败，
 * BOM 业务操作结果 / 充值结果），收敛为五个 REPORT_TYPE：
 * {@code TAKE_TICKET_OK}、{@code TAKE_TICKET_FAIL}、{@code TOPUP_OK}、
 * {@code TOPUP_FAIL}、{@code BOM_BIZ_RESULT}（见 CK_F2F_REPORT_TYPE）。
 *
 * <p>三处必须知道的设计前提：
 * <ul>
 *   <li>规格要求设备断网后重传，幂等靠 UK_F2F_REPORT_IDEM (REPORT_TYPE, ORDER_NO)。
 *       写入只 INSERT，重复由唯一索引抛 {@code DuplicateKeyException}，
 *       application 层捕获后当作「已收到过」返回成功。</li>
 *   <li>{@code PROCESSED} 把「接收」与「后续动作」解耦：退款、状态推进由扫表驱动，
 *       命中 IDX_F2F_REPORT_PENDING (PROCESSED, RECEIVE_TMS)。</li>
 *   <li>{@code REPORT_TMS} 是 VARCHAR2(14) 的 YYYYMMDDHHMMSS 字符串，不是时间类型，
 *       故映射为 {@code String}；只有 RECEIVE_TMS / CREATE_TMS 是 TIMESTAMP(6)。</li>
 * </ul>
 */
public class F2fResultReport {

    /** 自增主键。 */
    private Long id;

    /** 上报类型，取值见 CK_F2F_REPORT_TYPE 五个枚举，决定该行哪些业务列有值。 */
    private String reportType;

    /** 一般为ITP订单号；ERROR_CODE=2101时规格要求填取票二维码的randomFact，故不建外键。 */
    private String orderNo;

    /** 受理渠道：01-APP，02-TVM，03-BOM。 */
    private String channel;

    /** 上报设备号。 */
    private String deviceId;

    /** 操作员号，BOM 侧有值。 */
    private String operatorId;

    /** 订单应出票张数。 */
    private Integer orderTicketNum;

    /** 实际出票张数，与订单张数不等时需按差额退款。 */
    private Integer actualTicketNum;

    /** 充值状态：00成功，01失败，02存疑，03取消。 */
    private String topupStatus;

    /** BOM 业务操作结果标识。 */
    private String optResult;

    /** BOM 业务操作结果描述。 */
    private String optResultDesc;

    /** TVM 打印的故障单号，BOM 凭此号查询，命中 IDX_F2F_REPORT_SLIP。 */
    private String faultSlipSeq;

    /** 2101表示取票二维码超时需解锁订单。 */
    private String errorCode;

    /** 设备上报的错误描述。 */
    private String errorMessage;

    /** 本次交易金额，单位分。 */
    private Long transAmount;

    /** 交易后卡内余额，单位分。 */
    private Long afterAmount;

    /** 设备侧发生时间YYYYMMDDHHMMSS；ERROR_CODE=2101时为二维码生成时间。 */
    private String reportTms;

    /** ITP 接收上报的时间，与 PROCESSED 组成扫表索引。 */
    private LocalDateTime receiveTms;

    /** 0未处理，1已处理；后续动作（退款、状态推进）由扫表驱动，与接收解耦。 */
    private String processed;

    /** 原始报文留证，规格允许出现接口未声明的字段。 */
    private String rawBody;

    /** 创建时间，分区键。 */
    private LocalDateTime createTms;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public Integer getOrderTicketNum() {
        return orderTicketNum;
    }

    public void setOrderTicketNum(Integer orderTicketNum) {
        this.orderTicketNum = orderTicketNum;
    }

    public Integer getActualTicketNum() {
        return actualTicketNum;
    }

    public void setActualTicketNum(Integer actualTicketNum) {
        this.actualTicketNum = actualTicketNum;
    }
    public String getTopupStatus() {
        return topupStatus;
    }

    public void setTopupStatus(String topupStatus) {
        this.topupStatus = topupStatus;
    }

    public String getOptResult() {
        return optResult;
    }

    public void setOptResult(String optResult) {
        this.optResult = optResult;
    }

    public String getOptResultDesc() {
        return optResultDesc;
    }

    public void setOptResultDesc(String optResultDesc) {
        this.optResultDesc = optResultDesc;
    }

    public String getFaultSlipSeq() {
        return faultSlipSeq;
    }

    public void setFaultSlipSeq(String faultSlipSeq) {
        this.faultSlipSeq = faultSlipSeq;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    public Long getTransAmount() {
        return transAmount;
    }

    public void setTransAmount(Long transAmount) {
        this.transAmount = transAmount;
    }

    public Long getAfterAmount() {
        return afterAmount;
    }

    public void setAfterAmount(Long afterAmount) {
        this.afterAmount = afterAmount;
    }

    public String getReportTms() {
        return reportTms;
    }

    public void setReportTms(String reportTms) {
        this.reportTms = reportTms;
    }

    public LocalDateTime getReceiveTms() {
        return receiveTms;
    }

    public void setReceiveTms(LocalDateTime receiveTms) {
        this.receiveTms = receiveTms;
    }

    public String getProcessed() {
        return processed;
    }

    public void setProcessed(String processed) {
        this.processed = processed;
    }

    public String getRawBody() {
        return rawBody;
    }

    public void setRawBody(String rawBody) {
        this.rawBody = rawBody;
    }

    public LocalDateTime getCreateTms() {
        return createTms;
    }

    public void setCreateTms(LocalDateTime createTms) {
        this.createTms = createTms;
    }
}
