package com.chinasofti.huateng.collectpay.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.common.DeviceResponse;
import com.chinasofti.huateng.collectpay.constant.ItpStatusEnum;
import com.chinasofti.huateng.collectpay.constant.TvmPayCodeEnum;
import com.chinasofti.huateng.collectpay.entity.TvmPayOrder;
import com.chinasofti.huateng.collectpay.entity.TvmTakeTicketOrder;
import com.chinasofti.huateng.collectpay.mapper.TvmOrderMapper;
import com.chinasofti.huateng.collectpay.mapper.TvmTakeTicketOrderMapper;
import com.chinasofti.huateng.collectpay.model.request.tvm.RequestActiveTicketReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.RequestTakeTicketAuthReqDTO;
import com.chinasofti.huateng.collectpay.model.response.tvm.TvmOrderResult;
import com.chinasofti.huateng.collectpay.service.TvmTakeTicketService;
import com.chinasofti.huateng.collectpay.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TVM扫码取票服务实现。
 */
@Slf4j
@Service
public class TvmTakeTicketServiceImpl implements TvmTakeTicketService {

    private static final DateTimeFormatter DB_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private TvmTakeTicketOrderMapper tvmTakeTicketOrderMapper;

    @Autowired
    private TvmOrderMapper tvmOrderMapper;

    // todo 激活和激活订单查询的问题：1.要激活的订单在哪里，预设tvm表的订单，如果这样，是否要在这个表中加一个新字段（是否已激活字段)
    @Override
    public JSONObject requestActiveTicket(RequestActiveTicketReqDTO request) {
        log.info("1.开始处理激活取票订单, request={}", request);


        // 查询原支付订单 todo 需确认该订单是在本系统还是在其他平台上
        TvmPayOrder payOrder = tvmOrderMapper.selectByOrderNo(request.getOrderNo());
        if (ObjectUtils.isEmpty(payOrder)) {
            log.info("2.没有找到匹配的订单，orderNo={}", request.getOrderNo());
            return TvmOrderResult.fail(TvmPayCodeEnum.ORDER_NO_ERROR.getCode(), "订单号错误,没有找到匹配的订单");
        }

        // 检查订单状态是否为支付成功
        if (!ItpStatusEnum.SUCCESS.getCode().equals(payOrder.getStatus())) {
            log.info("2.订单未支付或支付失败，orderNo={}, status={}", request.getOrderNo(), payOrder.getStatus());
            return TvmOrderResult.fail(TvmPayCodeEnum.ORDER_NOT_PAID.getCode(), TvmPayCodeEnum.ORDER_NOT_PAID.getMsg());
        }

        // 查询或创建取票订单
        TvmTakeTicketOrder takeTicketOrder = tvmTakeTicketOrderMapper.selectByOrderNoAndDevice(
                request.getOrderNo(), request.getDeviceId());
        
        String nowStr = DateUtils.getNowTime();

        // todo 这个订单在哪里 需要确认流程 如果没有取过票，则创建
        if (ObjectUtils.isEmpty(takeTicketOrder)) {
            // 创建新的取票订单
            takeTicketOrder = new TvmTakeTicketOrder();
            takeTicketOrder.setOrderNo(request.getOrderNo());
            takeTicketOrder.setDeviceId(request.getDeviceId());
            takeTicketOrder.setQrcodeGenDate(request.getQrcodeGenDate());
            takeTicketOrder.setRandomFact(request.getRandomFact());
            takeTicketOrder.setActiveStatus("0"); // 未激活
            takeTicketOrder.setCreateTime(nowStr);
            takeTicketOrder.setUpdateTime(nowStr);
            log.info("3.创建新的取票订单, takeTicketOrder={}", takeTicketOrder);
            tvmTakeTicketOrderMapper.insert(takeTicketOrder);
        }else {
            // 更新激活状态为已激活
            Map<String, Object> updateMap = new LinkedHashMap<>();
            updateMap.put("orderNo", request.getOrderNo());
            updateMap.put("deviceId", request.getDeviceId());
            updateMap.put("activeStatus", "1"); // 已激活
            updateMap.put("activeTime", nowStr);
            updateMap.put("updateTime", nowStr);
            log.info("4.更新取票订单为已激活, orderNo={}, deviceId={}", request.getOrderNo(), request.getDeviceId());
            tvmTakeTicketOrderMapper.updateActiveStatusByOrderNoAndDevice(updateMap);
        }

        log.info("5.激活取票订单处理完成, orderNo={}", request.getOrderNo());
        return TvmOrderResult.success();
    }

    @Override
    public JSONObject requestTakeTicketAuth(RequestTakeTicketAuthReqDTO request) {
        log.info("1.开始处理扫码取票订单查询, request={}", request);


        // 根据设备ID和二维码信息查询取票订单
        TvmTakeTicketOrder takeTicketOrder = tvmTakeTicketOrderMapper.selectByDeviceAndQrcode(
                request.getDeviceId(), request.getQrcodeGenDate(), request.getRandomFact());
        
        if (ObjectUtils.isEmpty(takeTicketOrder)) {
            log.info("2.没有找到匹配的取票订单, deviceId={}, qrcodeGenDate={}, randomFact={}", 
                    request.getDeviceId(), request.getQrcodeGenDate(), request.getRandomFact());
            return TvmOrderResult.fail(TvmPayCodeEnum.NO_ACTIVE_ORDER.getCode(), TvmPayCodeEnum.NO_ACTIVE_ORDER.getMsg());
        }

        // 检查激活状态
        if (takeTicketOrder.getActiveStatus() == null || StringUtils.equals(takeTicketOrder.getActiveStatus(),"0")) {
            log.info("3.订单未激活, orderNo={}", takeTicketOrder.getOrderNo());
            return TvmOrderResult.fail(TvmPayCodeEnum.NO_ACTIVE_ORDER.getCode(), TvmPayCodeEnum.NO_ACTIVE_ORDER.getMsg());
        }

        // 查询原支付订单详情 todo 要查的是哪个订单 如果是tvm订单表，那下单接口在哪里
        TvmPayOrder payOrder = tvmOrderMapper.selectByOrderNo(takeTicketOrder.getOrderNo());
        if (payOrder == null) {
            log.info("3.没有找到匹配的支付订单, orderNo={}", takeTicketOrder.getOrderNo());
            return TvmOrderResult.successData(DeviceResponse.getQueryFailResult());
        }

        log.info("4.查询到激活订单，返回订单详情, orderNo={}", payOrder.getOrderNo());
        return TvmOrderResult.successData(DeviceResponse.getQuerySuccessResult(payOrder));
    }



}
