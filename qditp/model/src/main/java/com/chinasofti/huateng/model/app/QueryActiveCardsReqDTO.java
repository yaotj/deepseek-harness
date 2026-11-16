package com.chinasofti.huateng.model.app;

/**
 * 内部 RPC：按第三方用户号查其名下有效票卡列表。
 *
 * <p>用于 IF8A-35 聚合「已进站未出站」行程数——账户域持有 thirdUserId → CARD_ID 映射，
 * 行程域持有 CARD_ID → CODE_STATUS，支付域只能经本 DTO + ticket-server 的
 * {@code queryQrCodeStatus} 逐卡查状态，NEVER 跨库 JOIN（见 ADR-D175）。
 *
 * <p>这是内部 DTO，不经过 fep-app 的 {@code parseBizData}，与对外契约无关。
 */
public class QueryActiveCardsReqDTO {

    /** 第三方用户 id。 */
    private String thirdUserId;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    @Override
    public String toString() {
        return "QueryActiveCardsReqDTO{thirdUserId='" + thirdUserId + "'}";
    }
}
