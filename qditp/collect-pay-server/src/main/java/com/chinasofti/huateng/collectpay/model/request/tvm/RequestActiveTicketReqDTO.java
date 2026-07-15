package com.chinasofti.huateng.collectpay.model.request.tvm;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;
import lombok.Data;

/**
 * IF8A-15 激活取票订单请求参数。
 */
@Data
public class RequestActiveTicketReqDTO extends BaseRequestDTO {
    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 取票设备编码。
     */
    private String deviceId;

    /**
     * 设备取票二维码生成时间。
     */
    private String qrcodeGenDate;

    /**
     * 设备随机因子。
     */
    private String randomFact;
}
