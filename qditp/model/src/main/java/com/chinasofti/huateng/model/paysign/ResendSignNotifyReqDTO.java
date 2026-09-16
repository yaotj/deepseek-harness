package com.chinasofti.huateng.model.paysign;

/**
 * 单条签约结果通知重发请求（内部接口 /internal/paySign/resendNotify）。
 *
 * <p>与批量补偿 {@code /internal/paySign/compensateNotify} 的区别：本接口只处理指定的一条流水，
 * 不扫表、不递增 NOTIFY_RETRY_COUNT，因此不会牵连库里其它历史流水。联调期人工重放用这个，
 * NEVER 为了重发一条而去打批量补偿接口——那会把所有符合扫描条件的历史流水一起发给 APP。</p>
 */
public class ResendSignNotifyReqDTO {

    /** 签约流水号，必填。 */
    private String requestSignSeq;

    public String getRequestSignSeq() {
        return requestSignSeq;
    }

    public void setRequestSignSeq(String requestSignSeq) {
        this.requestSignSeq = requestSignSeq;
    }
}
