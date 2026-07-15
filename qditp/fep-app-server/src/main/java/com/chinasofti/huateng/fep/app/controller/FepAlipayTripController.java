package com.chinasofti.huateng.fep.app.controller;

import com.chinasofti.huateng.fep.app.model.CommonFormRequest;
import com.chinasofti.huateng.fep.app.service.AlipayTripService;
import com.chinasofti.huateng.model.alipaytrip.*;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝出行专属接口控制器。
 * <p>
 * 对应支付宝出行接口文档，路径前缀统一为 /channel 和 /notify。
 * 与现有 IF8A 接口（/ci/app）体系独立。
 * </p>
 */
@RestController
@RequestMapping("/channel")
public class FepAlipayTripController {
    private static final Logger log = LoggerFactory.getLogger(FepAlipayTripController.class);

    @Autowired
    private AlipayTripService alipayTripService;

    /**
     * 1.1 支付宝出行-添加签约信息。
     */
    @PostMapping("/addContract")
    public AlipayTripAddContractRespDTO addContract(@ModelAttribute CommonFormRequest request) {
        log.info("支付宝出行-添加签约信息,请求参数：{}", request);
        AlipayTripAddContractReqDTO bizData = JSON.parseObject(request.getBizData(), AlipayTripAddContractReqDTO.class);
        return alipayTripService.addContract(bizData);
    }

    /**
     * 1.2 支付宝出行-解约登记。
     */
    @PostMapping("/terminateContract")
    public AlipayTripTerminateContractRespDTO terminateContract(@ModelAttribute CommonFormRequest request) {
        log.info("支付宝出行-解约登记,请求参数：{}", request);
        AlipayTripTerminateContractReqDTO bizData = JSON.parseObject(request.getBizData(), AlipayTripTerminateContractReqDTO.class);
        return alipayTripService.terminateContract(bizData);
    }

    /**
     * 1.3 支付宝出行-开卡申请。
     */
    @PostMapping("/requestApplication")
    public AlipayTripRequestApplicationRespDTO requestApplication(@ModelAttribute CommonFormRequest request) {
        log.info("支付宝出行-开卡申请,请求参数：{}", request);
        AlipayTripRequestApplicationReqDTO bizData = JSON.parseObject(request.getBizData(), AlipayTripRequestApplicationReqDTO.class);
        return alipayTripService.requestApplication(bizData);
    }

    /**
     * 1.4 支付宝出行-获取行业数据。
     */
    @PostMapping("/requestIndustryData")
    public AlipayTripRequestIndustryDataRespDTO requestIndustryData(@ModelAttribute CommonFormRequest request) {
        log.info("支付宝出行-获取行业数据,请求参数：{}", request);
        AlipayTripRequestIndustryDataReqDTO bizData = JSON.parseObject(request.getBizData(), AlipayTripRequestIndustryDataReqDTO.class);
        return alipayTripService.requestIndustryData(bizData);
    }

    /**
     * 1.5 支付宝出行-查询乘车记录。
     */
    @PostMapping("/findTravelList")
    public AlipayTripFindTravelListRespDTO findTravelList(@ModelAttribute CommonFormRequest request) {
        log.info("支付宝出行-查询乘车记录,请求参数：{}", request);
        AlipayTripFindTravelListReqDTO bizData = JSON.parseObject(request.getBizData(), AlipayTripFindTravelListReqDTO.class);
        return alipayTripService.findTravelList(bizData);
    }

    /**
     * 1.6 支付宝出行-查询乘车记录详情。
     */
    @PostMapping("/findTravelDetail")
    public AlipayTripFindTravelDetailRespDTO findTravelDetail(@ModelAttribute CommonFormRequest request) {
        log.info("支付宝出行-查询乘车记录详情,请求参数：{}", request);
        AlipayTripFindTravelDetailReqDTO bizData = JSON.parseObject(request.getBizData(), AlipayTripFindTravelDetailReqDTO.class);
        return alipayTripService.findTravelDetail(bizData);
    }
}
