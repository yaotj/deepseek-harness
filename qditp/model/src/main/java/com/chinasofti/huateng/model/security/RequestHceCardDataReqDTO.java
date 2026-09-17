package com.chinasofti.huateng.model.security;

/**
 * account-server 调用安全服务 {@code /ci/itp/requestHceCardData} 的请求参数。
 */
public class RequestHceCardDataReqDTO {
    /**
     * NFC 票卡类型。
     */
    private String ticketCard;

    /**
     * 物理卡号，格式为 {@code 000000} + 四字节 ITP 用户编码的十六进制字符串。
     */
    private String iptUserId;

    public String getTicketCard() {
        return ticketCard;
    }

    public void setTicketCard(String ticketCard) {
        this.ticketCard = ticketCard;
    }

    public String getIptUserId() {
        return iptUserId;
    }

    public void setIptUserId(String iptUserId) {
        this.iptUserId = iptUserId;
    }
}
