package com.chinasofti.huateng.collectpay.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.common.DeviceResponse;
import com.chinasofti.huateng.collectpay.constant.BusinessTypeEnum;
import com.chinasofti.huateng.collectpay.constant.TvmPayCodeEnum;
import com.chinasofti.huateng.model.enums.DeviceTypeEnum;
import com.chinasofti.huateng.collectpay.entity.TvmPayPreOrder;
import com.chinasofti.huateng.collectpay.mapper.TvmOrderPreMapper;
import com.chinasofti.huateng.collectpay.model.request.tvm.NotiTakeTicketResultReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.PayNoticeReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.RequestPayResultReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.RequestRefundReqDTO;
import com.chinasofti.huateng.collectpay.model.response.tvm.TvmOrderResult;
import com.chinasofti.huateng.collectpay.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

@Service
@Slf4j
public class TvmOrderPreServiceImpl implements TvmOrderPreService {

    private static final String TVM_PG = DeviceTypeEnum.TVM_1.getCode();
    private static final String TVM_TOPUP = "02";
    private static final String TVM_APP = "03";

    @Autowired
    TvmOrderService tvmOrderService;
    @Autowired
    TvmTopupService tvmTopupService;
    @Autowired
    AppOrderService appOrderService;
    @Autowired
    BomOrderService bomOrderService;

    @Autowired
    private TvmOrderPreMapper tvmOrderPreMapper;


    @Override
    public JSONObject requestPayResult(RequestPayResultReqDTO request) {


        TvmPayPreOrder tvmPayPreOrder = tvmOrderPreMapper.selectByOrderNo(request.getOrderNo());
        log.info("设备查询支付结果 tvm前置订单匹配 tvmPayPreOrder is {}",tvmPayPreOrder);
        if (ObjectUtils.isEmpty(tvmPayPreOrder) || StringUtils.isEmpty(tvmPayPreOrder.getTransType())) {
            return TvmOrderResult.fail(TvmPayCodeEnum.INVALID_PARAM.getCode(), "没有找到匹配的订单，请确认订单号是否正确");
        }
        String transType = tvmPayPreOrder.getTransType();

        // 扫码购票
        if(StringUtils.equals(transType,TVM_PG)){
            log.info("查询 扫码购票 订单支付结果");
            return tvmOrderService.requestPayResult(request);
        }
        // 扫码充值
        if (StringUtils.equals(transType,TVM_TOPUP)){
            log.info("查询 扫码充值 订单支付结果");
            return tvmTopupService.requestPayResult(request);
        }
        return TvmOrderResult.fail(TvmPayCodeEnum.FAIL.getCode(),"交易类型不明确，请联系工作人员");

    }

    @Override
    public JSONObject requestPayOrderDetail(RequestPayResultReqDTO request) {
        TvmPayPreOrder tvmPayPreOrder = tvmOrderPreMapper.selectByOrderNo(request.getOrderNo());
        log.info("支付中心查询订单详情 tvm前置订单匹配 tvmPayPreOrder is {}",tvmPayPreOrder);
        if (ObjectUtils.isEmpty(tvmPayPreOrder) || StringUtils.isEmpty(tvmPayPreOrder.getTransType())) {
            return TvmOrderResult.fail(TvmPayCodeEnum.INVALID_PARAM.getCode(), "没有找到匹配的订单，请确认订单号是否正确");
        }
        String transType = tvmPayPreOrder.getTransType();

        // 扫码购票
        if(StringUtils.equals(transType,"01")){
            log.info("支付中心查询 扫码购票 订单支付详情");
            return tvmOrderService.requestPayOrderDetail(request);
        }
        // 扫码充值
        if (StringUtils.equals(transType,"02")){
            log.info("支付中心查询 扫码充值 订单支付详情");
            return tvmTopupService.requestPayOrderDetail(request);
        }
        return TvmOrderResult.fail(TvmPayCodeEnum.FAIL.getCode(),"交易类型不明确，请联系工作人员");
    }

    @Override
    public JSONObject requestRefund(RequestRefundReqDTO request) {
        TvmPayPreOrder tvmPayPreOrder = tvmOrderPreMapper.selectByOrderNo(request.getOrderNo());
        log.info("tvm前置订单匹配 tvmPayPreOrder is {}",tvmPayPreOrder);
        if (ObjectUtils.isEmpty(tvmPayPreOrder) || StringUtils.isEmpty(tvmPayPreOrder.getTransType())) {
            return TvmOrderResult.fail(TvmPayCodeEnum.INVALID_PARAM.getCode(), "没有找到匹配的订单，请确认订单号是否正确");
        }
        String transType = tvmPayPreOrder.getTransType();

        // 扫码购票
        if(StringUtils.equals(transType,"01")){
            log.info("支付中心查询 扫码购票 退款");
            return tvmOrderService.requestRefund(request);
        }
        // 扫码充值
        if (StringUtils.equals(transType,"02")){
            log.info("支付中心查询 扫码充值 退款");
            return tvmTopupService.requestRefund(request);
        }
        return TvmOrderResult.fail(TvmPayCodeEnum.FAIL.getCode(),"交易类型不明确，请联系工作人员");
    }

    @Override
    public JSONObject payNotice(PayNoticeReqDTO request) {

        log.info("payCenterService 接收到支付结果通知 request is {}",request);
        // 此处用支付中心传来的商户订单号来查，也就是itp的订单号
        TvmPayPreOrder tvmPayPreOrder = tvmOrderPreMapper.selectByOrderNo(request.getMerchantOrderNo());

        log.info("设备查询支付结果 tvm前置订单匹配 tvmPayPreOrder is {}",tvmPayPreOrder);

        if (ObjectUtils.isEmpty(tvmPayPreOrder) || StringUtils.isEmpty(tvmPayPreOrder.getTransType())) {
            return TvmOrderResult.fail("-1", "没有找到匹配的订单，请确认订单号是否正确");
        }
        String transType = tvmPayPreOrder.getTransType();

        // 扫码购票
        if(StringUtils.equals(transType, BusinessTypeEnum.TVM_SCAN_QR_BUYTICKET.getCode())){
            log.info("支付中心通知 扫码购票 支付结果");
            return tvmOrderService.payNotice(request);
        }
        // 扫码充值
        if (StringUtils.equals(transType,BusinessTypeEnum.TVM_SCAN_QR_RECHARGE.getCode())){
            log.info("支付中心通知 扫码充值 支付结果");
            return tvmTopupService.payNotice(request);
        }
        // app下单
        if (StringUtils.equals(transType,BusinessTypeEnum.TVM_SCAN_QR_TAKETICKET.getCode())){
            log.info("支付中心通知 app下单 支付结果");
            return appOrderService.payNotice(request);
        }

        // bom支付
        if (StringUtils.equals(transType,BusinessTypeEnum.BOM_SCANED_PAY.getCode())){
            log.info("支付中心通知 bom支付 支付结果");
            return bomOrderService.payNotice(request);
        }

        return TvmOrderResult.fail(TvmPayCodeEnum.FAIL.getCode(),"交易类型不明确，请联系工作人员");
    }

}
