package com.chinasofti.huateng.facepay.api.device.bom;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/**
 * IF2A-09 BOM 充值结果通知入参。
 *
 * <p><b>{@code topupStatus} 的取值方向与直觉相反</b>：{@code 00} 成功、{@code 01} 失败并退款。
 * 与 TVM 的 {@code topupCardFailNoti} 一致（{@code TopupCardFailNotiReqDTO.STATUS_FAILED}），
 * 属既有契约。</p>
 */
public class NotiTopupResultReqDTO extends BaseDeviceRequest {

    /** 00 充值成功；01 充值失败，需退款。 */
    public static final String STATUS_OK = "00";

    /** 见 {@link #STATUS_OK}。 */
    public static final String STATUS_FAILED = "01";

    private String orderNo;

    private String topupStatus;

    public boolean needRefund() {
        return STATUS_FAILED.equals(topupStatus);
    }

    public boolean isOk() {
        return STATUS_OK.equals(topupStatus);
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getTopupStatus() {
        return topupStatus;
    }

    public void setTopupStatus(String topupStatus) {
        this.topupStatus = topupStatus;
    }

    @Override
    public String toString() {
        return "NotiTopupResultReqDTO{orderNo=" + orderNo
                + ", topupStatus=" + topupStatus
                + ", deviceId=" + getDeviceId() + '}';
    }
}
