package com.chinasofti.huateng.facepay.api.device.app;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/**
 * APP 侧「按订单号 + 用户号」的通用入参：支付结果查询、请求退款、退款结果查询三个接口共用。
 *
 * <p>旧实现这三个接口复用的是 TVM 的 {@code RequestPayResultReqDTO}，
 * 而那个类里同时带 {@code userId}——TVM 链路从不用它。这里单独建一个 APP 专用类，
 * 字段与旧的完全一致，只是不再跨渠道共享。</p>
 *
 * <p><b>{@code userId} 目前只做非空校验，不做归属校验</b>——旧实现同样如此，
 * 因此任何网络可达方凭 {@code orderNo} 就能对他人订单发起退款。
 * 补归属校验属行为变更（会拒绝掉线上现有的部分请求），MUST 独立评审，
 * 见 {@code docs/architecture/face-pay-refactor.md} 的安全决策记录。</p>
 */
public class RequestAppPayResultReqDTO extends BaseDeviceRequest {

    private String orderNo;

    private String userId;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Override
    public String toString() {
        return "RequestAppPayResultReqDTO{orderNo=" + orderNo + ", userId=" + userId + '}';
    }
}
