package com.chinasofti.huateng.facepay.api.device.bom;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/** IF2A-08 BOM 业务操作结果通知入参。 */
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
