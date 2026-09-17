package com.chinasofti.huateng.model.pay;

import java.util.List;

/**
 * IF8A-26 请求补款下单（{@code /app/requestPayOrder}）请求报文。
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
    private String thirdUserId;
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
