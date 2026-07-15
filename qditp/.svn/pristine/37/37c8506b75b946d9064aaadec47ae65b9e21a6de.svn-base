package com.chinasofti.huateng.fep.alipay.controller;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.fep.alipay.model.CommonFormRequest;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestIndustryDataReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestIndustryDataRespDTO;
import com.chinasofti.huateng.fep.alipay.service.AlipayTripService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝出行 MemberContract Controller。
 */
@RestController
@RequestMapping("/memberContract/channel")
public class FepAlipayTripMemberContractController {

    private static final Logger log = LoggerFactory.getLogger(FepAlipayTripMemberContractController.class);

    private final AlipayTripService alipayTripService;

    public FepAlipayTripMemberContractController(AlipayTripService alipayTripService) {
        this.alipayTripService = alipayTripService;
    }

    /**
     * 获取行业数据。
     */
    @PostMapping("/requestIndustryData")
    public AlipayTripRequestIndustryDataRespDTO requestIndustryData(@ModelAttribute CommonFormRequest request) {
        log.info("支付宝出行-获取行业数据(MemberContract),请求参数：{}", request);
        if (request == null || request.getBizData() == null) {
            AlipayTripRequestIndustryDataRespDTO result = new AlipayTripRequestIndustryDataRespDTO();
            result.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("无效的参数");
            return result;
        }
        AlipayTripRequestIndustryDataReqDTO bizData = JSON.parseObject(request.getBizData(), AlipayTripRequestIndustryDataReqDTO.class);
        log.info("支付宝出行-获取行业数据(MemberContract),解码后业务参数：{}", JSON.toJSONString(bizData));
        return alipayTripService.requestIndustryData(bizData);
    }
}
