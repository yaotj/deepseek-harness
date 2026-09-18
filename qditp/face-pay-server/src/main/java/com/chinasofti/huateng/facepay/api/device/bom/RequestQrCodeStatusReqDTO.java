package com.chinasofti.huateng.facepay.api.device.bom;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/**
 * IF1A-04 查询票卡状态入参（BOM / TVM 前缀别名用）。
 *
 * <p>与 {@code fep-dev-server} 的 {@code RequestQrCodeStatusReqDTO} 字段同形但**不是同一个类**：
 * 两者都是能被设备报文解析的对外契约，按 {@code docs/domain} 的判据 NEVER 共享父类或互相复用。
 */
public class RequestQrCodeStatusReqDTO extends BaseDeviceRequest {

    private String itpUserId;

    private String cardId;

    private String trxType;

    private String ticketTransSeq;

    private String qrType;

    public String getItpUserId() {
        return itpUserId;
    }

    public void setItpUserId(String itpUserId) {
        this.itpUserId = itpUserId;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getTrxType() {
        return trxType;
    }

    public void setTrxType(String trxType) {
        this.trxType = trxType;
    }

    public String getTicketTransSeq() {
        return ticketTransSeq;
    }

    public void setTicketTransSeq(String ticketTransSeq) {
        this.ticketTransSeq = ticketTransSeq;
    }

    public String getQrType() {
        return qrType;
    }

    public void setQrType(String qrType) {
        this.qrType = qrType;
    }

    @Override
    public String toString() {
        return "RequestQrCodeStatusReqDTO{itpUserId=" + itpUserId
                + ", cardId=" + cardId
                + ", trxType=" + trxType
                + ", ticketTransSeq=" + ticketTransSeq
                + ", qrType=" + qrType
                + ", deviceId=" + getDeviceId() + '}';
    }
}
