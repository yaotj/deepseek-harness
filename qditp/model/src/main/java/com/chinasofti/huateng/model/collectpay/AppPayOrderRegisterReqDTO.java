package com.chinasofti.huateng.model.collectpay;

/**
 * 把一张外部补款单登记成 {@code TBL_TVM_APP_ORDER} + {@code TBL_TVM_ORDER_PAY_PRE} 两行的请求。
 *
 * <p><b>这是对内契约，不是 APP 报文</b>：调用方只有 gate-txn-pay-server 的 IF8A-26 补款链路，
 * 走 {@code POST /internal/app-order/register}。因此加字段是安全的（对比 {@code parseBizData}
 * 解析的对外 DTO，见 {@code docs/domain/README.md} 的分类判据）。</p>
 *
 * <p><b>这三条口径是资损防线，改字段含义前 MUST 逐条复核</b>：</p>
 * <ul>
 *   <li>{@code totalAmount} 单位是**分**，会同时写进 {@code TICKET_PRICE} / {@code TOTALPRICE} /
 *       {@code PAY_AMOUNT}，而 {@code TICKET_NUM} 固定 {@code 1}。collect-pay 的
 *       {@code requestPayInfo} 按 {@code TICKET_PRICE × TICKET_NUM} 算送去支付中心的金额，
 *       **NEVER** 只写 {@code TOTALPRICE} —— 那一列算钱时根本不读。</li>
 *   <li>{@code supplementFlag} 落 {@code RSV2}，**MUST 非空**。collect-pay 的
 *       {@code refundAppNotTakeTickets} 会把「已支付 + {@code RSV2} 为空 + 昨天创建 +
 *       主票表查不到票」的订单当成购票未取票**全额退款**；补款单永远不会有票，
 *       {@code RSV2} 一空就等于次日把收到的欠费退回给乘客。</li>
 *   <li>{@code transType} 落前置单的 {@code TRANS_TYPE}，补款单固定 {@code 03}（扫码取票）。
 *       collect-pay 的 {@code payNotice} 按这一列分派，只有 {@code 03} 会进
 *       {@code appOrderService.payNotice}（只改 APP 订单行、不出票）。</li>
 * </ul>
 */
public class AppPayOrderRegisterReqDTO {

    /** 外部单号，同时作为两张表的 {@code ORDER_NO}，也是本接口的幂等键。 */
    private String orderNo;

    /** ITP 用户号，落 {@code USER_ID}，与 collect-pay 自己写的同命名空间。 */
    private String userId;

    /** 应收总额（单位：分）。 */
    private String totalAmount;

    /** 票种标记，落 {@code TICKET_TYPE}，用于人工在表里区分补款单与真实单程票。 */
    private String ticketType;

    /** 卡号，落 {@code RSV1}。 */
    private String cardId;

    /** 备注，落 {@code MSG}。 */
    private String msg;

    /** 补款标记，落 {@code RSV2}，MUST 非空（见类注释）。 */
    private String supplementFlag;

    /** 来源标识，落前置单的 {@code DEVICE_ID}，便于在 collect-pay 侧一眼认出来源。 */
    private String deviceId;

    /** 前置单交易类型，补款单固定 {@code 03}（见类注释）。 */
    private String transType;

    public String getOrderNo() { return orderNo; }

    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getUserId() { return userId; }

    public void setUserId(String userId) { this.userId = userId; }

    public String getTotalAmount() { return totalAmount; }

    public void setTotalAmount(String totalAmount) { this.totalAmount = totalAmount; }

    public String getTicketType() { return ticketType; }

    public void setTicketType(String ticketType) { this.ticketType = ticketType; }

    public String getCardId() { return cardId; }

    public void setCardId(String cardId) { this.cardId = cardId; }

    public String getMsg() { return msg; }

    public void setMsg(String msg) { this.msg = msg; }

    public String getSupplementFlag() { return supplementFlag; }

    public void setSupplementFlag(String supplementFlag) { this.supplementFlag = supplementFlag; }

    public String getDeviceId() { return deviceId; }

    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getTransType() { return transType; }

    public void setTransType(String transType) { this.transType = transType; }

    @Override
    public String toString() {
        return "AppPayOrderRegisterReqDTO{" +
                "orderNo='" + orderNo + '\'' +
                ", userId='" + userId + '\'' +
                ", totalAmount='" + totalAmount + '\'' +
                ", ticketType='" + ticketType + '\'' +
                ", cardId='" + cardId + '\'' +
                ", supplementFlag='" + supplementFlag + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", transType='" + transType + '\'' +
                '}';
    }
}
