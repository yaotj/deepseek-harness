package com.chinasofti.huateng.facepay.api.device.bom;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/** IF5A-09 HCE 票卡更新结果通知入参。 */
public class NotiUpdateHceDataReqDTO extends BaseDeviceRequest {

    private String cardId;

    private String hceData;

    private String adviceOpt;

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

    public String getAdviceOpt() {
        return adviceOpt;
    }

    public void setAdviceOpt(String adviceOpt) {
        this.adviceOpt = adviceOpt;
    }

    /** {@code hceData} 只输出长度，不输出内容。 */
    @Override
    public String toString() {
        return "NotiUpdateHceDataReqDTO{cardId=" + cardId
                + ", adviceOpt=" + adviceOpt
                + ", hceDataLength=" + (hceData == null ? 0 : hceData.length())
                + ", deviceId=" + getDeviceId() + '}';
    }
}
