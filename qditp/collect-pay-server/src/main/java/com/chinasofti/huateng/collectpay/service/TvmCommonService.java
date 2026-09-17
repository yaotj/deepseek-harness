package com.chinasofti.huateng.collectpay.service;

import com.chinasofti.huateng.collectpay.model.response.PayCenterResponse;
import com.chinasofti.huateng.collectpay.utils.BaseResult;

public interface TvmCommonService {


    /** 扫码购票和扫码充值共用 */
    public boolean doRefund(String bussInessType,String orderNo, String payCenterOrderNo, int refundAmount,String refundNo);

    public PayCenterResponse queryRefundResult(String refundNo);

//    public boolean getPayCenterRefundResult(String payOrderNo, String refundNo, String refundAmount, String businessType);
    public BaseResult getPayCenterRefundResult(String refundNo);
}
