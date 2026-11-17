package com.chinasofti.huateng.acc.security.feign.domain.commonmac;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public class SaleAndRefundParam {


    @NotBlank(message = "卡号不能为空")
    @Size(min = 16, max = 16, message = "卡号节点长度为16位")
    private String cardNo;


    @NotBlank(message = "随机数不能为空")
    @Size(min = 16, max = 16, message = "随机数长度为16位")
    private String randomNum;

    public String getCardNo() {
        return cardNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }

    public String getRandomNum() {
        return randomNum;
    }

    public void setRandomNum(String randomNum) {
        this.randomNum = randomNum;
    }
}

