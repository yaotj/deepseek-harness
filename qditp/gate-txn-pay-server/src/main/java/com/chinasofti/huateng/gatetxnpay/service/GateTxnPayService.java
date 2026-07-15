package com.chinasofti.huateng.gatetxnpay.service;

import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;

public interface GateTxnPayService {
    GateTxnPayRespDTO requestPay(GateTxnPayReqDTO request);
}
