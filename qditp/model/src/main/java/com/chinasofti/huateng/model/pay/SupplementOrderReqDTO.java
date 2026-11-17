package com.chinasofti.huateng.model.pay;

import java.util.List;


public class SupplementOrderReqDTO {
    private String goodsCode;
    private String price;
    private String quantity;
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
