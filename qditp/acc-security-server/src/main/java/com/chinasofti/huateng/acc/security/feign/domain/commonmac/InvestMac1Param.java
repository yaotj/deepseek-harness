package com.chinasofti.huateng.acc.security.feign.domain.commonmac;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @author houkepan
 * @date 2020/5/6 10:57
 */
public class InvestMac1Param {

    /**
     * 交易前金额
     */
    private int beforeAmt;

    /**
     * 交易金额
     */
    private int txnAmt;

    @NotBlank(message = "充值设备节点不能为空")
    @Size(min = 8, max = 8, message = "充值设备节点长度为8位")
    private String devNodeId;

    /**
     * 随机数
     *
     * @return
     */
    private String randomNum;

    /**
     * 票卡计数器
     */
    private int ticketCount;

    /**
     * 卡号
     */
    @NotBlank(message = "卡号不能为空")
    @Size(min = 16, max = 16, message = "卡号节点长度为16位")
    private String cardNo;

    /**
     * mac
     */
    @NotBlank(message = "MAC不能为空")
    @Size(min = 8, max = 8, message = "MAC长度为8位")
    private String mac;


    public int getBeforeAmt() {
        return beforeAmt;
    }

    public void setBeforeAmt(int beforeAmt) {
        this.beforeAmt = beforeAmt;
    }

    public int getTxnAmt() {
        return txnAmt;
    }

    public void setTxnAmt(int txnAmt) {
        this.txnAmt = txnAmt;
    }

    public String getDevNodeId() {
        return devNodeId;
    }

    public void setDevNodeId(String devNodeId) {
        this.devNodeId = devNodeId;
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

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }
}

