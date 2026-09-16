package com.chinasofti.huateng.facepay.api.device.bom;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/**
 * IF2A-08 BOM 业务操作结果通知入参。
 *
 * <p>{@code optResult} 只有 {@code SUCCESS} / {@code FAILED} 两种有效取值，
 * {@code FAILED} 触发原单全额退款。</p>
 *
 * <p><b>{@code tranDate} 少一个 s</b>（不是 transDate），既有契约，NEVER 更正。
 * 旧实现拿到这个字段后完全没用过，本实现把它落到 {@code F2F_RESULT_REPORT.REPORT_TMS}。</p>
 */
public class NotiBusResultReqDTO extends BaseDeviceRequest {

    /** 业务操作结果：SUCCESS / FAILED。 */
    private String optResult;

    private String orderNo;

    private String optResultDesc;

    /** 交易日期，拼写照搬。 */
    private String tranDate;

    /** 旧实现的判定口径：只有字面量 FAILED 才退款，其它取值静默忽略。 */
    public boolean isFailed() {
        return "FAILED".equals(optResult);
    }

    public boolean isSuccess() {
        return "SUCCESS".equals(optResult);
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getOptResult() {
        return optResult;
    }

    public void setOptResult(String optResult) {
        this.optResult = optResult;
    }

    public String getOptResultDesc() {
        return optResultDesc;
    }

    public void setOptResultDesc(String optResultDesc) {
        this.optResultDesc = optResultDesc;
    }

    public String getTranDate() {
        return tranDate;
    }

    public void setTranDate(String tranDate) {
        this.tranDate = tranDate;
    }

    @Override
    public String toString() {
        return "NotiBusResultReqDTO{orderNo=" + orderNo
                + ", optResult=" + optResult
                + ", optResultDesc=" + optResultDesc
                + ", tranDate=" + tranDate
                + ", deviceId=" + getDeviceId() + '}';
    }
}
