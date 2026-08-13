package com.chinasofti.huateng.model.app;

/**
 * 更新 HCE 卡数据请求。
 *
 * <p>IF1A-01 闸机检票成功后，闸机在 {@code reserve1} 中上送更新后的 64 字节 HCE 卡数据。
 * ticket-server 通过该对象将数据按逻辑卡号回写到账户注册信息。</p>
 */
public class UpdateHceDataReqDTO {
    /**
     * HCE 逻辑卡号。
     */
    private String cardId;

    /**
     * 闸机处理后的 HCE 卡数据，来源于 IF1A-01 的 {@code reserve1}。
     */
    private String hceData;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getHceData() {
        return hceData;
    }

    public void setHceData(String hceData) {
        this.hceData = hceData;
    }
}
