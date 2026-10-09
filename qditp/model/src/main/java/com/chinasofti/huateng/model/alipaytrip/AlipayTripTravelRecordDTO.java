package com.chinasofti.huateng.model.alipaytrip;

/**
 * 支付宝出行-查询乘车记录单条记录。
 */
public class AlipayTripTravelRecordDTO {

    /**
     * 进站站点名称。
     */
    private String entryStationName;

    /**
     * 进站时间。
     */
    private String entryDate;

    /**
     * 出站站点名称。
     */
    private String exitStationName;

    /**
     * 出站时间。
     */
    private String exitDate;

    /**
     * 实付金额（单位：分）
     */
    private String payAmount;

    /**
     * 总金额（单位：分）
     */
    private String totalAmount;

    /**
     * 订单扩展类型。
     */
    private String orderExpType = "0";

    /**
     * 交易订单号。
     */
    private String tradeOrderNo;

    /**
     * 支付交易订单号。
     */
    private String payTradeOrderNo;

    /**
     * 支付订单日期。
     */
    private String payOrderNoDate;

    /**
     * 扣款请求结果：{@code "0"} 成功 / {@code "1"} 其余，<b>NEVER 放渠道文案</b>。
     *
     * <p><b>唯一数据源是 {@code GATE_TXN_PAY.DEBIT_STATUS}</b>（订单主表的扣费结果，实测值域
     * SUCCESS / RETRY / FAIL / INIT），映射在 {@code AlipayTravelQueryHandler.mapDebitStatusToResult}。
     * 本字段口头常被叫成「支付结果」，但 <b>NEVER 因此改成取 {@code ALIPAY_PAY_TXN_DETAIL.PAY_STATUS}</b>
     * —— 那张表一次支付尝试一行，重试成功后主表已 SUCCESS 而旧明细行仍是 FAIL，换过去会把已扣费成功的行
     * 对 APP 报成未成功。2026-09-18 定、2026-09-20 用户复核后维持原判。
     * 列表的 {@code debitRequestResult} 筛选谓词同样打在 {@code DEBIT_STATUS} 上，<b>回显与筛选 MUST 同源</b>。
     */
    private String debitRequestResult;

    /**
     * 同行票标识。
     */
    private String companionFlag;

    private String cardNum;

    /**
     * 日票票号。
     */
    private String ticketCode;

    /**
     * 计次次数（预留）
     */
    private String countingTimes;

    /**
     * 计次标识（预留）
     */
    private String countingFlag;

    /**
     * 优惠金额（分）。
     *
     * <p><b>恒为空串</b>：按 2026-09-18 的裁决，本接口只允许从 {@code GATE_TXN_PAY} 与
     * {@code ALIPAY_PAY_TXN_DETAIL} 两张表取数，而这两张表**都没有渠道优惠金额列** ——
     * {@code ALIPAY_PAY_TXN_DETAIL} 全表只有 {@code AMOUNT}（我方送出的请求金额）；
     * {@code GATE_TXN_PAY.DISCOUNT_LEVEL_AMT} 是**优惠档位阈值、不是优惠金额**，且实测 118 行里
     * 大于 0 的有 0 行。**NEVER 拿 {@code DISCOUNT_LEVEL_AMT} / {@code ORIGINAL_FARE - TOTAL_AMOUNT}
     * 填这里** —— 前者恒 0，后者是「我方自算优惠」、与甲方要的渠道优惠不是一个口径。
     */
    private String discountFee;

    /**
     * 优惠详情 JSON 数组。
     *
     * <p><b>恒为空串</b>，成因同 {@link #discountFee}。有原文的只有 pay-sign 域的
     * {@code PAY_CALLBACK_LOG.DISCOUNT_INFO} / {@code PAY_TXN_DETAIL.DISCOUNT_INFO}，
     * 既在本接口的取数范围外、实测也全为空。
     */
    private String discountInfo;

    /**
     * 发票状态（可选）
     */
    private String invoice;

    /**
     * 支付渠道代码。
     */
    private String payChannelCode;

    public String getEntryStationName() {
        return entryStationName;
    }

    public void setEntryStationName(String entryStationName) {
        this.entryStationName = entryStationName;
    }

    public String getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(String entryDate) {
        this.entryDate = entryDate;
    }

    public String getExitStationName() {
        return exitStationName;
    }

    public void setExitStationName(String exitStationName) {
        this.exitStationName = exitStationName;
    }

    public String getExitDate() {
        return exitDate;
    }

    public void setExitDate(String exitDate) {
        this.exitDate = exitDate;
    }

    public String getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(String payAmount) {
        this.payAmount = payAmount;
    }

    public String getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(String totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getOrderExpType() {
        return orderExpType;
    }

    public void setOrderExpType(String orderExpType) {
        this.orderExpType = orderExpType;
    }

    public String getTradeOrderNo() {
        return tradeOrderNo;
    }

    public void setTradeOrderNo(String tradeOrderNo) {
        this.tradeOrderNo = tradeOrderNo;
    }

    public String getPayTradeOrderNo() {
        return payTradeOrderNo;
    }

    public void setPayTradeOrderNo(String payTradeOrderNo) {
        this.payTradeOrderNo = payTradeOrderNo;
    }

    public String getPayOrderNoDate() {
        return payOrderNoDate;
    }

    public void setPayOrderNoDate(String payOrderNoDate) {
        this.payOrderNoDate = payOrderNoDate;
    }

    public String getDebitRequestResult() {
        return debitRequestResult;
    }

    public void setDebitRequestResult(String debitRequestResult) {
        this.debitRequestResult = debitRequestResult;
    }

    public String getCompanionFlag() {
        return companionFlag;
    }

    public void setCompanionFlag(String companionFlag) {
        this.companionFlag = companionFlag;
    }

    public String getCardNum() {
        return cardNum;
    }

    public void setCardNum(String cardNum) {
        this.cardNum = cardNum;
    }

    public String getTicketCode() {
        return ticketCode;
    }

    public void setTicketCode(String ticketCode) {
        this.ticketCode = ticketCode;
    }

    public String getCountingTimes() {
        return countingTimes;
    }

    public void setCountingTimes(String countingTimes) {
        this.countingTimes = countingTimes;
    }

    public String getCountingFlag() {
        return countingFlag;
    }

    public void setCountingFlag(String countingFlag) {
        this.countingFlag = countingFlag;
    }

    public String getDiscountFee() {
        return discountFee;
    }

    public void setDiscountFee(String discountFee) {
        this.discountFee = discountFee;
    }

    public String getDiscountInfo() {
        return discountInfo;
    }

    public void setDiscountInfo(String discountInfo) {
        this.discountInfo = discountInfo;
    }

    public String getInvoice() {
        return invoice;
    }

    public void setInvoice(String invoice) {
        this.invoice = invoice;
    }

    public String getPayChannelCode() {
        return payChannelCode;
    }

    public void setPayChannelCode(String payChannelCode) {
        this.payChannelCode = payChannelCode;
    }
}
