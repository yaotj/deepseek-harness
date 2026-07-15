package com.chinasofti.huateng.collectpay.model.request.tvm;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;
import lombok.Data;

/**
 * IF2A-09 请求充值下单请求参数。
 */
@Data
public class RequestTopupReqDTO extends BaseRequestDTO {
    /**
     * 票卡逻辑号。
     */
    private String ticketLogicNum;

    /**
     * 票卡物理号。
     */
    private String ticketPhysicsNum;

    /**
     * 充值前金额。
     */
    private String beforeAmount;

    /**
     * 请求交易金额。
     */
    private String transAmount;

    private String payType;
}
