package com.chinasofti.huateng.collectpay.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.constant.TvmPayCodeEnum;
import com.chinasofti.huateng.collectpay.entity.TvmPayOrder;
import com.chinasofti.huateng.collectpay.entity.TvmPayPreOrder;
import com.chinasofti.huateng.collectpay.mapper.TvmOrderPreMapper;
import com.chinasofti.huateng.collectpay.model.request.tvm.RequestPayResultReqDTO;
import com.chinasofti.huateng.collectpay.model.response.tvm.TvmOrderResult;
import com.chinasofti.huateng.collectpay.service.TvmOrderPreService;
import com.chinasofti.huateng.collectpay.service.TvmOrderService;
import com.chinasofti.huateng.collectpay.service.TvmTopupService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

@Service
@Slf4j
public class TvmOrderPreServiceImpl implements TvmOrderPreService {

    @Autowired
    TvmOrderService tvmOrderService;
    @Autowired
    TvmTopupService tvmTopupService;

    @Autowired
    private TvmOrderPreMapper tvmOrderPreMapper;


    @Override
    public JSONObject requestPayResult(RequestPayResultReqDTO request) {


        TvmPayPreOrder tvmPayPreOrder = tvmOrderPreMapper.selectByOrderNo(request.getOrderNo());
        log.info("tvm前置订单匹配 tvmPayPreOrder is {}",tvmPayPreOrder);
        if (ObjectUtils.isEmpty(tvmPayPreOrder) || StringUtils.isEmpty(tvmPayPreOrder.getTransType())) {
            return TvmOrderResult.fail(TvmPayCodeEnum.INVALID_PARAM.getCode(), "没有找到匹配的订单，请确认订单号是否正确");
        }
        String transType = tvmPayPreOrder.getTransType();

        // 扫码购票
        if(StringUtils.equals(transType,"01")){
            log.info("查询 扫码购票 订单支付结果");
            return tvmOrderService.requestPayResult(request);
        }
        // 扫码充值
        if (StringUtils.equals(transType,"02")){
            log.info("查询 扫码充值 订单支付结果");
            return tvmTopupService.requestPayResult(request);
        }
        return null;

    }
}
