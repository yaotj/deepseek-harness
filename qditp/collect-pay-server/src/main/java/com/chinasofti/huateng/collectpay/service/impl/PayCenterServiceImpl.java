package com.chinasofti.huateng.collectpay.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.collectpay.model.request.PayCenterRequest;
import com.chinasofti.huateng.collectpay.model.response.PayCenterResponse;
import com.chinasofti.huateng.collectpay.service.PayCenterService;
import com.chinasofti.huateng.collectpay.utils.HttpUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class PayCenterServiceImpl implements PayCenterService {

    @Autowired
    HttpUtils httpUtils;

    // 调用支付中心接口
    public PayCenterResponse callPayCenter(String url, PayCenterRequest request) {
        try {
            String response = httpUtils.doPost2(url, request);
            log.info("支付中心响应, response={}", response);
            PayCenterResponse payCenterResponse = JSON.parseObject(response, PayCenterResponse.class);
            log.info("转化payCenterResponse is {}",payCenterResponse);
            return payCenterResponse;
        } catch (Exception e) {
            log.error("调用支付中心异常", e);
            return null;
        }
    }

}
