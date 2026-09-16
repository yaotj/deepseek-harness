package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/**
 * IF2A-07 取票订单激活请求报文。
 * 对应 {@code POST /itptvm/ci/tvm/requestActiveTicket} 的 {@code bizData}。
 *
 * <p><b>与旧 DTO 的一处有意差异</b>：旧 {@code RequestActiveTicketReqDTO} 用 Lombok 在子类
 * 重复声明了 {@code deviceId}，生成的 getter 覆盖父类，导致父类字段恒空、
 * 表单上的 {@code deviceId} 永远读不到。本类<b>不重复声明</b>，
 * {@code deviceId} 统一走 {@link BaseDeviceRequest}，由
 * {@code DeviceRequests.unwrap} 决定取表单值还是 bizData 值（表单非空优先）。
 * 报文层面无差异：设备把 deviceId 放 bizData 里照样能解出来。</p>
 */
public class RequestActiveTicketReqDTO extends BaseDeviceRequest {

    /** 订单号。必填。 */
    private String orderNo;

    /** 二维码生成日期，与 {@code randomFact} 一起构成取票凭证。必填。 */
    private String qrcodeGenDate;

    /** 二维码随机因子。必填。 */
    private String randomFact;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getQrcodeGenDate() {
        return qrcodeGenDate;
    }

    public void setQrcodeGenDate(String qrcodeGenDate) {
        this.qrcodeGenDate = qrcodeGenDate;
    }

    public String getRandomFact() {
        return randomFact;
    }

    public void setRandomFact(String randomFact) {
        this.randomFact = randomFact;
    }

    @Override
    public String toString() {
        return super.toString() + ",RequestActiveTicketReqDTO{orderNo=" + orderNo
                + ", qrcodeGenDate=" + qrcodeGenDate
                + ", randomFact=" + randomFact + '}';
    }
}
