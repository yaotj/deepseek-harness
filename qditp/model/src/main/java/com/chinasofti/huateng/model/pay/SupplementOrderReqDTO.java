package com.chinasofti.huateng.model.pay;

import java.util.List;

/**
 * IF8A-26 请求补款下单（{@code /app/requestPayOrder}）请求报文。
 *
 * <p>规格《青岛地铁-ITP与APP接口规范R6》§3.35 表73 只定义了 4 个字段：
 * {@code goodsCode}（固定 001）、{@code price}（分）、{@code quantity}（固定 1）、{@code orderNoList}。
 * 规格未给出长度与必填性，也没有携带用户标识。</p>
 *
 * <p>{@code thirdUserId} / {@code cardId} 是我方补充的归属校验字段，**不在甲方规格内**：
 * 本接口是状态变更型接口且各业务模块无统一验签兜底，若只按订单号列表下单，
 * 任何网络可达方都能拿别人的订单号生成补款单。传入时服务端会校验订单归属一致性；
 * 未传入时服务端从原订单反推，并要求列表内所有订单同属一个用户。</p>
 */
public class SupplementOrderReqDTO {
    /** 商品编码，规格固定 001。 */
    private String goodsCode;
    /** 补款总金额，单位分，规格为 string。 */
    private String price;
    /** 数量，规格固定 1。 */
    private String quantity;
    /** 待补款的原过闸订单号数组（GATE_TXN_PAY.ORDER_NO）。 */
    private List<String> orderNoList;
    /** 归属校验用用户标识，规格外补充字段。 */
    private String thirdUserId;
    /** 归属校验用逻辑卡号，规格外补充字段。 */
    private String cardId;

    public String getGoodsCode() {
        return goodsCode;
    }

    public void setGoodsCode(String goodsCode) {
        this.goodsCode = goodsCode;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getQuantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    public List<String> getOrderNoList() {
        return orderNoList;
    }

    public void setOrderNoList(List<String> orderNoList) {
        this.orderNoList = orderNoList;
    }

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    @Override
    public String toString() {
        return "SupplementOrderReqDTO{" +
                "goodsCode='" + goodsCode + '\'' +
                ", price='" + price + '\'' +
                ", quantity='" + quantity + '\'' +
                ", orderNoList=" + orderNoList +
                ", thirdUserId='" + thirdUserId + '\'' +
                ", cardId='" + cardId + '\'' +
                '}';
    }
}
