package com.chinasofti.huateng.acc.security.feign.domain.commonmac;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @author houkepan
 * @date 2020/5/6 10:57
 */
public class SaleAndRefundParam {

    /**
     * 物理卡号
     */
    @NotBlank(message = "卡号不能为空")
    @Size(min = 16, max = 16, message = "卡号节点长度为16位")
    private String cardNo;

    /**
     * mac
     */
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

