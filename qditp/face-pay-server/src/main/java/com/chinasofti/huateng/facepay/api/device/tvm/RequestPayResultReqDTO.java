package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/**
 * IF2A-03 查询支付结果请求报文。
 * 对应 {@code POST /itptvm/ci/tvm/requestPayResult} 的 {@code bizData}。
 *
 * <p>只有 {@code orderNo} 会被读取；{@code userId} 在旧实现的 TVM 查询链路上<b>从未被使用</b>，
 * 保留仅为报文兼容。</p>
 */
public class RequestPayResultReqDTO extends BaseDeviceRequest {

    /** 订单号。必填，为空时返回 retCode=2002。 */
    private String orderNo;

    /** 用户标识。旧实现未读取，保留兼容。 */
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
        return super.toString() + ",RequestPayResultReqDTO{orderNo=" + orderNo + ", userId=" + userId + '}';
    }
}
