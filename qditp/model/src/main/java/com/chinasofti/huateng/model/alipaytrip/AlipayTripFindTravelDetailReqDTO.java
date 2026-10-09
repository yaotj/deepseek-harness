package com.chinasofti.huateng.model.alipaytrip;

/**
 * 支付宝出行-查询乘车记录详情请求参数（R6 §3.72 表147）。
 *
 * <p><b>字段集严格等于表147，就这两个：`thirdUserId` / `orderNo`</b>（2026-09-20 按用户裁决「严格遵循接口契约」对齐）。
 * 原先还有 {@code handleDateTime} / {@code trxType} / {@code cardId} 三个**契约外字段**，是取数源切到
 * {@code GATE_TXN_PAY} 之前（按 {@code QRCODE_TXN_DETAIL} 的「用户+处理时间+交易类型」三键定位）的遗留，
 * 切源后 {@code AlipayTravelQueryHandler.findTravelDetail} 只用 {@code orderNo} 查库、那三个已零引用，
 * 因此整组删除。<b>NEVER 加回</b> —— 上游按表147 本就不会送，留着只会让人以为还有别的定位方式。
 *
 * <p>另注两处规格空白（**MUST 向甲方澄清、NEVER 自行假定**）：①表147 的 {@code orderNo}「订单号」
 * 在应答表148 里**没有同名字段**，文档从未说明它对应 {@code tradeOrderNo} 还是 {@code payTradeOrderNo} ——
 * 现实现按 {@code orderNo ≡ GATE_TXN_PAY.ORDER_NO ≡ 应答的 tradeOrderNo} 落地；
 * ②表147 只有 3 列（字段 / 类型 / 说明），**没有「是否必填」列**，`thirdUserId` 的归属校验要求文档也没写。
 */
public class AlipayTripFindTravelDetailReqDTO {

    /**
     * 第三方用户ID，格式化后的用户标识。
     */
    private String thirdUserId;

    /**
     * 订单号。
     */
    private String orderNo;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }
}
