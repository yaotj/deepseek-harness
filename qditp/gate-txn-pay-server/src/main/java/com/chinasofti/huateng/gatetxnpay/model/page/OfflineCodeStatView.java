package com.chinasofti.huateng.gatetxnpay.model.page;

/**
 * 离线码交易统计运营展示对象（按车站分组）。只读聚合 {@code GATE_TXN_PAY} 中
 * {@code OFFLINE_FLAG='Y'} 的行，不含任何个人信息字段。
 *
 * <p>汇总行复用本类型：{@code stationCode} / {@code stationName} 为空即汇总。</p>
 */
public class OfflineCodeStatView {
    /** 车站编码（出站站，缺省时回落进站站）。 */
    private String stationCode;
    /** 车站中文名（由 STATION_INFO 补）。 */
    private String stationName;
    /** 该车站离线码交易笔数。 */
    private Long txnCount;
    /** 该车站离线码交易涉及的独立卡数。 */
    private Long cardCount;

    public String getStationCode() {
        return stationCode;
    }

    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }

    public String getStationName() {
        return stationName;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }

    public Long getTxnCount() {
        return txnCount;
    }

    public void setTxnCount(Long txnCount) {
        this.txnCount = txnCount;
    }

    public Long getCardCount() {
        return cardCount;
    }

    public void setCardCount(Long cardCount) {
        this.cardCount = cardCount;
    }
}
