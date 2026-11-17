package com.chinasofti.huateng.acc.security.feign.domain.commonmac;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public class InvestMac2Param {


    private int txnAmt;


    private String randomNum;

    @NotBlank(message = "充值设备节点不能为空")
    @Size(min = 8, max = 8, message = "充值设备节点长度为8位")
    private String devNodeId;


    private int ticketCount;


    @NotBlank(message = "卡号不能为空")
    @Size(min = 16, max = 16, message = "卡号节点长度为16位")
    private String cardNo;


    @NotBlank(message = "中心日期时间不能为空")
    @Size(min = 14, max = 14, message = "中心日期时间长度14位")
    private String centerTime;


    public int getTxnAmt() {
        return txnAmt;
    }

    public void setTxnAmt(int txnAmt) {
        this.txnAmt = txnAmt;
    }

    public String getRandomNum() {
        return randomNum;
    }

    public void setRandomNum(String randomNum) {
        this.randomNum = randomNum;
    }

    public int getTicketCount() {
        return ticketCount;
    }

    public void setTicketCount(int ticketCount) {
        this.ticketCount = ticketCount;
    }

    public String getCardNo() {
        return cardNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }

    public String getDevNodeId() {
        return devNodeId;
    }

    public void setDevNodeId(String devNodeId) {
        this.devNodeId = devNodeId;
    }

    public String getCenterTime() {
        return centerTime;
    }

    public void setCenterTime(String centerTime) {
        this.centerTime = centerTime;
    }
}

