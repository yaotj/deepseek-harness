package com.chinasofti.huateng.collectpay.service;

import com.chinasofti.huateng.collectpay.model.response.PayCenterResponse;

public interface TvmCommonService {


    public String doRefund(String bussInessType,String orderNo, String payCenterOrderNo, int refundAmount);

    public PayCenterResponse queryRefundResult(String refundNo);

    public boolean noticeAppRefundResult(String payOrderNo, String refundResult, String refundDate, String refundAmount, String retryTimes);

}
