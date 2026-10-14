package com.chinasofti.huateng.recon.thirdparty;

/**
 * ITP 侧对账源记录（融合 PAY_TXN_DETAIL 与 F2F_* 两流），承载 TXNDIFF 第 1~19 段 + 关联键 + ITP 侧终态。
 *
 * <p>单位：{@code originalFare/orderAmount/actualAmount/discountCumulativeAmount/overtimeAmount} 均为「分」。</p>
 *
 * <p>来源标记 {@code source} ∈ {pay-sign, face-pay}：
 * <ul>
 *   <li>pay-sign：取自 {@code PAY_TXN_DETAIL}，覆盖 QR 后付费 / 日票（face-pay 不落此表，见 recon-thirdparty.md §八）。</li>
 *   <li>face-pay：取自 {@code F2F_ORDER JOIN F2F_PAYMENT(SUCCESS)}，覆盖当面付(BOM/TVM)；关联键
 *       {@code merchantOrderNo=F2F_ORDER.ORDER_NO}（即支付中心 merchantOrderNo）、{@code channelTradeNo=F2F_PAYMENT.CHANNEL_ORDER_NO}。</li>
 * </ul>
 * 第 1~19 段为 DETAIL 同义字段；某来源不持有的乘车域字段（车站/设备/次数/激活时间/超时等）取 null，由生成层兜底为空串/0。</p>
 */
public class ItpTxnRecord {

    // ---- 第 1~19 段（与 DETAIL / TXNDIFF 同义；null 表示来源不持有，生成层兜底为空串/0） ----
    private String txnType;                     // 2 交易类型
    private String logicCardNo;                 // 3 逻辑卡号
    private String txnDateTime;                 // 4 交易日期(yyyyMMddHHmmss)
    private Long originalFare;                  // 5 交易原价
    private String curStationName;              // 6 当前车站名称
    private String deviceNo;                    // 7 设备编码
    private String ticketType;                  // 8 票卡类型
    private String ticketSubType;               // 9 票卡子类型
    private String times;                       // 10 次数
    private String payChannel;                  // 11 支付渠道
    private String activateTime;                // 12 激活时间
    private String quantity;                    // 13 数量
    private String ticketTransSeq;              // 14 交易流水号
    private String orderNo;                     // 15 交易订单号
    private Long orderAmount;                   // 16 订单金额
    private Long actualAmount;                  // 17 实际扣款金额
    private Long discountCumulativeAmount;      // 18 优惠累计消费金额
    private Long overtimeAmount;                // 19 超时金额

    // ---- 关联键（与渠道账单 merchant_order_no / channel_trade_no 对齐） ----
    private String merchantOrderNo;
    private String channelTradeNo;

    // ---- 比对用 ITP 侧终态：SUCCESS / FAIL / null（非终态或未知） ----
    private String itpPayStatus;

    // ---- 来源标记，便于排障与后续按域拆分 ----
    private String source;

    public String getTxnType() { return txnType; }
    public void setTxnType(String txnType) { this.txnType = txnType; }
    public String getLogicCardNo() { return logicCardNo; }
    public void setLogicCardNo(String logicCardNo) { this.logicCardNo = logicCardNo; }
    public String getTxnDateTime() { return txnDateTime; }
    public void setTxnDateTime(String txnDateTime) { this.txnDateTime = txnDateTime; }
    public Long getOriginalFare() { return originalFare; }
    public void setOriginalFare(Long originalFare) { this.originalFare = originalFare; }
    public String getCurStationName() { return curStationName; }
    public void setCurStationName(String curStationName) { this.curStationName = curStationName; }
    public String getDeviceNo() { return deviceNo; }
    public void setDeviceNo(String deviceNo) { this.deviceNo = deviceNo; }
    public String getTicketType() { return ticketType; }
    public void setTicketType(String ticketType) { this.ticketType = ticketType; }
    public String getTicketSubType() { return ticketSubType; }
    public void setTicketSubType(String ticketSubType) { this.ticketSubType = ticketSubType; }
    public String getTimes() { return times; }
    public void setTimes(String times) { this.times = times; }
    public String getPayChannel() { return payChannel; }
    public void setPayChannel(String payChannel) { this.payChannel = payChannel; }
    public String getActivateTime() { return activateTime; }
    public void setActivateTime(String activateTime) { this.activateTime = activateTime; }
    public String getQuantity() { return quantity; }
    public void setQuantity(String quantity) { this.quantity = quantity; }
    public String getTicketTransSeq() { return ticketTransSeq; }
    public void setTicketTransSeq(String ticketTransSeq) { this.ticketTransSeq = ticketTransSeq; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getOrderAmount() { return orderAmount; }
    public void setOrderAmount(Long orderAmount) { this.orderAmount = orderAmount; }
    public Long getActualAmount() { return actualAmount; }
    public void setActualAmount(Long actualAmount) { this.actualAmount = actualAmount; }
    public Long getDiscountCumulativeAmount() { return discountCumulativeAmount; }
    public void setDiscountCumulativeAmount(Long discountCumulativeAmount) { this.discountCumulativeAmount = discountCumulativeAmount; }
    public Long getOvertimeAmount() { return overtimeAmount; }
    public void setOvertimeAmount(Long overtimeAmount) { this.overtimeAmount = overtimeAmount; }
    public String getMerchantOrderNo() { return merchantOrderNo; }
    public void setMerchantOrderNo(String merchantOrderNo) { this.merchantOrderNo = merchantOrderNo; }
    public String getChannelTradeNo() { return channelTradeNo; }
    public void setChannelTradeNo(String channelTradeNo) { this.channelTradeNo = channelTradeNo; }
    public String getItpPayStatus() { return itpPayStatus; }
    public void setItpPayStatus(String itpPayStatus) { this.itpPayStatus = itpPayStatus; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    /** 关联键：(商户订单号, 渠道交易流水号)，与 {@link ThirdPartyBill#joinKey()} 同构。 */
    public String joinKey() {
        return (merchantOrderNo == null ? "" : merchantOrderNo) + "\u0001" + (channelTradeNo == null ? "" : channelTradeNo);
    }
}
