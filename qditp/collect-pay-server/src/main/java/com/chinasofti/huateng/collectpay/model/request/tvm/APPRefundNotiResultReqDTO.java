package com.chinasofti.huateng.collectpay.model.request.tvm;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;
import com.chinasofti.huateng.collectpay.model.request.PayCenterBaseRequestDTO;
import lombok.Data;

import java.util.List;

/** 5.2 退款回调 支付中心回调itp */
@Data
//public class APPRefundNotiResultReqDTO extends BaseRequestDTO {
public class APPRefundNotiResultReqDTO extends PayCenterBaseRequestDTO {

    private String orderNo;
    private String refundResult;
    private String refundResultDesc;
    private String refundDate;
    private String refundAmount;
    private String refundNo;
    private String outRefundNo;




}
