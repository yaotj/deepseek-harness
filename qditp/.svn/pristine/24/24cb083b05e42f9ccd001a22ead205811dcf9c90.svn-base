package com.chinasofti.huateng.fep.alipay.controller;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.fep.alipay.model.CommonFormRequest;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestIndustryDataReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestIndustryDataRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTerminateContractRespDTO;
import com.chinasofti.huateng.fep.alipay.service.AlipayTripService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝出行 Trip Controller。
 */
@RestController
@RequestMapping("/channel")
public class FepAlipayTripController {

    private static final Logger log = LoggerFactory.getLogger(FepAlipayTripController.class);

    private final AlipayTripService alipayTripService;

    public FepAlipayTripController(AlipayTripService alipayTripService) {
        this.alipayTripService = alipayTripService;
    }

    /**
     * 添加签约信息。
     */
    @PostMapping("/addContract")
    public AlipayTripAddContractRespDTO addContract(@ModelAttribute CommonFormRequest request) {
        log.info("支付宝出行-添加签约信息,请求参数：{}", request);
        if (request == null || request.getBizData() == null) {
            AlipayTripAddContractRespDTO result = new AlipayTripAddContractRespDTO();
            result.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("无效的参数");
            return result;
        }
        AlipayTripAddContractReqDTO bizData = JSON.parseObject(request.getBizData(), AlipayTripAddContractReqDTO.class);
        log.info("支付宝出行-添加签约信息,解码后业务参数：{}", JSON.toJSONString(bizData));
        return alipayTripService.addContract(bizData);
    }

    /**
     * 解约登记。
     */
    @PostMapping("/terminateContract")
    public AlipayTripTerminateContractRespDTO terminateContract(@ModelAttribute CommonFormRequest request) {
        log.info("支付宝出行-解约登记,请求参数：{}", request);
        if (request == null || request.getBizData() == null) {
            AlipayTripTerminateContractRespDTO result = new AlipayTripTerminateContractRespDTO();
            result.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("无效的参数");
            return result;
        }
        AlipayTripTerminateContractReqDTO bizData = JSON.parseObject(request.getBizData(), AlipayTripTerminateContractReqDTO.class);
        log.info("支付宝出行-解约登记,解码后业务参数：{}", JSON.toJSONString(bizData));
        return alipayTripService.terminateContract(bizData);
    }

    /**
     * 开卡申请。
     */
    @PostMapping("/requestApplication")
    public AlipayTripRequestApplicationRespDTO requestApplication(@ModelAttribute CommonFormRequest request) {
        log.info("支付宝出行-开卡申请,请求参数：{}", request);
        if (request == null || request.getBizData() == null) {
            AlipayTripRequestApplicationRespDTO result = new AlipayTripRequestApplicationRespDTO();
            result.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("无效的参数");
            return result;
        }
        AlipayTripRequestApplicationReqDTO bizData = JSON.parseObject(request.getBizData(), AlipayTripRequestApplicationReqDTO.class);
        log.info("支付宝出行-开卡申请,解码后业务参数：{}", JSON.toJSONString(bizData));
        return alipayTripService.requestApplication(bizData);
    }

    /**
     * 获取行业数据。
     */
    @PostMapping("/requestIndustryData")
    public AlipayTripRequestIndustryDataRespDTO requestIndustryData(@ModelAttribute CommonFormRequest request) {
        log.info("支付宝出行-获取行业数据,请求参数：{}", request);
        if (request == null || request.getBizData() == null) {
            AlipayTripRequestIndustryDataRespDTO result = new AlipayTripRequestIndustryDataRespDTO();
            result.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("无效的参数");
            return result;
        }
        AlipayTripRequestIndustryDataReqDTO bizData = JSON.parseObject(request.getBizData(), AlipayTripRequestIndustryDataReqDTO.class);
        log.info("支付宝出行-获取行业数据,解码后业务参数：{}", JSON.toJSONString(bizData));
        return alipayTripService.requestIndustryData(bizData);
    }

    /**
     * 查询乘车记录列表。
     */
    @PostMapping("/findTravelList")
    public AlipayTripFindTravelListRespDTO findTravelList(@ModelAttribute CommonFormRequest request) {
        log.info("支付宝出行-查询乘车记录列表,请求参数：{}", request);
        if (request == null || request.getBizData() == null) {
            AlipayTripFindTravelListRespDTO result = new AlipayTripFindTravelListRespDTO();
            result.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("无效的参数");
            return result;
        }
        AlipayTripFindTravelListReqDTO bizData = JSON.parseObject(request.getBizData(), AlipayTripFindTravelListReqDTO.class);
        log.info("支付宝出行-查询乘车记录列表,解码后业务参数：{}", JSON.toJSONString(bizData));
        return alipayTripService.findTravelList(bizData);
    }

    /**
     * 查询乘车记录详情。
     */
    @PostMapping("/findTravelDetail")
    public AlipayTripFindTravelDetailRespDTO findTravelDetail(@ModelAttribute CommonFormRequest request) {
        log.info("支付宝出行-查询乘车记录详情,请求参数：{}", request);
        if (request == null || request.getBizData() == null) {
            AlipayTripFindTravelDetailRespDTO result = new AlipayTripFindTravelDetailRespDTO();
            result.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("无效的参数");
            return result;
        }
        AlipayTripFindTravelDetailReqDTO bizData = JSON.parseObject(request.getBizData(), AlipayTripFindTravelDetailReqDTO.class);
        log.info("支付宝出行-查询乘车记录详情,解码后业务参数：{}", JSON.toJSONString(bizData));
        return alipayTripService.findTravelDetail(bizData);
    }
}
