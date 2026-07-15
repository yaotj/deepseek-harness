package com.chinasofti.huateng.fep.app.controller;

import com.chinasofti.huateng.fep.app.model.CommonFormRequest;
import com.chinasofti.huateng.fep.app.service.AlipayTripService;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestIndustryDataReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestIndustryDataRespDTO;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝出行-行业数据专属接口控制器。
 * <p>
 * 路径前缀为 /memberContract/channel，与 FepAlipayTripController 体系独立。
 * </p>
 */
@RestController
@RequestMapping("/memberContract/channel")
public class FepAlipayTripMemberContractController {
    private static final Logger log = LoggerFactory.getLogger(FepAlipayTripMemberContractController.class);

    @Autowired
    private AlipayTripService alipayTripService;

    /**
     * 1.4 支付宝出行-获取行业数据。
     */
    @PostMapping("/requestIndustryData")
    public AlipayTripRequestIndustryDataRespDTO requestIndustryData(@ModelAttribute CommonFormRequest request) {
        log.info("支付宝出行-获取行业数据,请求参数：{}", request);
        AlipayTripRequestIndustryDataReqDTO bizData = JSON.parseObject(request.getBizData(), AlipayTripRequestIndustryDataReqDTO.class);
        return alipayTripService.requestIndustryData(bizData);
    }
}
