package com.chinasofti.huateng.acc.security.server.service;

import com.chinasofti.huateng.acc.security.feign.domain.commonmac.SaleAndRefundParam;
import com.chinasofti.huateng.common.response.ResultVO;


public interface CommonSaleAndRefundService {

    ResultVO<String> getSaleAndRefundKey(SaleAndRefundParam param) throws InterruptedException;

}
