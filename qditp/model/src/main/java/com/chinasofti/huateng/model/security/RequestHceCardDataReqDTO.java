package com.chinasofti.huateng.model.security;

/**
 * account-server 调用安全服务 {@code /ci/itp/requestHceCardData} 的请求参数。
 *
 * <p>APP 开户卡类型为 {@code 03}（HCE卡）或 {@code 04}（新版HCE卡）时，
 * account-server 按开户请求中的 NFC 票卡类型请求安全服务生成 HCE 卡数据。</p>
 */
public class RequestHceCardDataReqDTO {
    /**
     * NFC 票卡类型。
     */
    private String ticketCard;

    /**
     * 物理卡号，格式为 {@code 000000} + 四字节 ITP 用户编码的十六进制字符串。
     *
     * <p>调用方将十进制 {@code thirdUserId} 转为八位大写十六进制字符串后再拼接前缀。</p>
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
