package com.chinasofti.huateng.collectpay.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.common.DeviceResponse;
import com.chinasofti.huateng.collectpay.common.ItpCommon;
import com.chinasofti.huateng.collectpay.common.PayCenterCommon;
import com.chinasofti.huateng.collectpay.common.UpdateDbMap;
import com.chinasofti.huateng.collectpay.config.PayCenterProperties;
import com.chinasofti.huateng.collectpay.constant.*;
import com.chinasofti.huateng.collectpay.entity.*;
import com.chinasofti.huateng.collectpay.mapper.*;
import com.chinasofti.huateng.collectpay.model.request.AppCommonRequest;
import com.chinasofti.huateng.collectpay.model.request.PayCenterRequest;
import com.chinasofti.huateng.collectpay.model.request.app.NoticeAppTakeTicketDTO;
import com.chinasofti.huateng.collectpay.model.request.app.NoticeAppTakeTicketFailureDTO;
import com.chinasofti.huateng.collectpay.model.request.bom.RequestGenNoCashOrderReqDTO;
import com.chinasofti.huateng.collectpay.model.request.bom.RequestPaymentReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.*;
import com.chinasofti.huateng.collectpay.model.response.PayCenterResponse;
import com.chinasofti.huateng.collectpay.model.response.tvm.RequestPaymentRespDTO;
import com.chinasofti.huateng.collectpay.model.response.tvm.RequestRefundRespDTO;
import com.chinasofti.huateng.collectpay.model.response.tvm.TvmOrderResult;
import com.chinasofti.huateng.collectpay.service.BomOrderService;
import com.chinasofti.huateng.collectpay.service.PayCenterService;
import com.chinasofti.huateng.collectpay.service.TvmCommonService;
import com.chinasofti.huateng.collectpay.service.TvmOrderService;
import com.chinasofti.huateng.collectpay.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
public class TvmOrderServiceImpl implements TvmOrderService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private PayCenterProperties payCenterProperties;
    @Autowired
    private TvmOrderMapper tvmOrderMapper;
    @Autowired
    private TvmOrderPreMapper tvmOrderPreMapper;
    @Autowired
    private TvmMainTicketMapper tvmMainTicketMapper;
    @Autowired
    private TvmSubTicketMapper tvmSubTicketMapper;
    @Autowired
    private TvmCommonService tvmCommonService;
    private RefundOrderMapper refundOrderMapper;
    @Autowired
    private com.chinasofti.huateng.collectpay.mapper.OrderSeqMapper orderSeqMapper;
    @Autowired
    private PayCenterCommon payCenterCommon;
    @Autowired
    private PayCenterService payCenterService;
    @Autowired
    private TvmAppOrderMapper tvmAppOrderMapper;
    @Autowired
    private SignUtils signUtils;
    @Autowired
    Environment environment;
    @Autowired
    BomOrderService bomOrderService;
    @Autowired
    HttpUtils httpUtils;
    @Autowired
    TvmNoticeAppMapper tvmNoticeAppMapper;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public JSONObject requestTvmPayOrder(RequestGenSjtOrderReqDTO request) {
        log.info("1.开始处理提交单程票订单, deviceId={}, request={}", request.getDeviceId(), request);

        String now = DateUtils.getNowTime();
        String orderNo = generateOrderNo();
        log.info("2.now is {} orderNo is {}", now, orderNo);

        // tvm订单 todo 订单号需要去其他平台获取
        TvmPayOrder order = buildTvmPayOrder(orderNo, request.getDeviceId(), request, now);
        log.info("3.tvm订单 order is {}", order);

        // 保存支付订单前置信息
        tvmOrderPreMapper.insert(getTvmOrderPre(order, request.getDeviceId()));
        tvmOrderMapper.insert(order);

        // 非数币渠道直接返回
        if (request.getPayType().equals("0")) {
            String sign = signUtils.getJhmSign(orderNo);
            String payUrl = payCenterProperties.getPayCenterJhmUrl() + "?orderNo=" + orderNo + "&sign=" + sign;
            Map<String, String> jmap = new HashMap<>();
            jmap.put("orderNo", orderNo);
            jmap.put("url", payUrl);
            tvmOrderMapper.updateByOrderNo(jmap);
            return TvmOrderResult.successData(DeviceResponse.getLaMaSuccessRespose(orderNo, payUrl));
        }
        PayCenterRequest payCenterRequest = payCenterCommon.buildTvmPayRequest(order.getOrderNo(), order.getTotalPrice(), order.getPayType(), "单程票购票", "地铁单程票");
        String tvmPayUrl = payCenterProperties.getPayCenterPayUrl();
        log.info("4.拉码请求 tvmPayUrl is {} , payCenterRequest is {}", tvmPayUrl, payCenterRequest);
        PayCenterResponse payResponse = payCenterService.callPayCenter(tvmPayUrl, payCenterRequest);
        log.info("5.拉码结束 payResponse is {}", payResponse);

        // 默认为失败
        JSONObject result = TvmOrderResult.fail();
        Map<String, String> uMap = UpdateDbMap.getLaMaUpdateFailDb(orderNo);

        if (ObjectUtils.isEmpty(payResponse)) {
            log.info("6.支付中心返回结果为空,结束");
        } else {

            // todo 当前以code=200为业务实际返回成功
            if (StringUtils.equals(payResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {
                log.info("7.支付中心返回成功");
                Map<String, Object> data = payResponse.getData();
                if (data != null) {
                    log.info("8.支付中心返回业务数据为 data is {}", data);
                    String payUrl = TransforUtils.getStringFromData(data, "data");
                    String payCenterOrderNo = TransforUtils.getStringFromData(data, "orderNo");
                    String payCenterChannelOrderNo = TransforUtils.getStringFromData(data, "channelOrderNo");
                    // 修改数据库记录
                    uMap = UpdateDbMap.getLaMaUpdateSuccessDb(orderNo, payCenterOrderNo, payCenterChannelOrderNo, payUrl);
                    // 给设备返回结果
                    result = TvmOrderResult.successData(DeviceResponse.getLaMaSuccessRespose(orderNo, payUrl));
                }
            } else {
                log.info("7.支付中心返回业务数据失败");
            }
        }

        log.info("9.开始修改记录 uMap is {}", uMap);
        int i = tvmOrderMapper.updateByOrderNo(uMap);
        log.info("10.修改结束 i is {}", i);

        return result;

    }

    @Override
    public JSONObject createBomSaleOrder(RequestGenSjtOrderReqDTO request) {
        log.info("1.开始处理TVM仅下单, deviceId={}, request={}", request.getDeviceId(), request);

        JSONObject jsonObject = bomOrderService.requestBomSaleOrder(request);
        log.info("4.下单完成, jsonObject={}", jsonObject);

        if (!ObjectUtils.isEmpty(jsonObject)) {
            String retCode = jsonObject.get("retCode").toString();
            if (StringUtils.equals(retCode, BomPayCodeEnum.SUCCESS.getCode())) {
                // 这里会将bom返回的retCode和retMsg覆盖，改为tvm定义的键值
                return TvmOrderResult.successData(jsonObject);
            }
        }
        return TvmOrderResult.fail(TvmPayCodeEnum.FAIL.getCode(), "下单失败");
    }


    @Override
    public JSONObject requestPayment(RequestPaymentReqDTO request) {
        log.info("1.开始处理TVM扫码支付, deviceId={}, request={}", request.getDeviceId(), request);

        // 查询订单信息
        TvmPayOrder order = tvmOrderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            log.info("订单不存在, orderNo={}", request.getOrderNo());
            return RequestPaymentRespDTO.fail("9999", "订单号错误,没有找到匹配的订单", "FAILED", "订单不存在");
        }

        // 订单已支付成功，直接返回
        if (ItpStatusEnum.SUCCESS.getCode().equals(order.getStatus())) {
            log.info("订单已支付成功");
            return RequestPaymentRespDTO.success("SUCCESS", "支付成功");
        }

        // 订单已支付失败，直接返回
        if (ItpStatusEnum.FAILED.getCode().equals(order.getStatus())) {
            log.info("订单已支付失败");
            return RequestPaymentRespDTO.success("FAILED", "支付失败");
        }

        // 构建支付中心扫码支付请求
        PayCenterRequest payCenterRequest = payCenterCommon.buildBomPayRequest(
                order.getOrderNo(),
                order.getTotalPrice(),
                "tvm扫码支付",
                "地铁单程票",
                request.getPaymentCode(),
                request.getPaymentVendor()
        );
        String tvmPayUrl = payCenterProperties.getPayCenterPayUrl();
        log.info("4.扫码支付请求 tvmPayUrl is {} , payCenterRequest is {}", tvmPayUrl, payCenterRequest);

        // 调用支付中心接口
        PayCenterResponse payResponse = payCenterService.callPayCenter(tvmPayUrl, payCenterRequest);
        log.info("5.支付中心响应 payResponse={}", payResponse);

        Map<String, String> uMap = new HashMap<>();
        uMap.put("orderNo", request.getOrderNo());

        // 支付中心返回结果为空
        if (ObjectUtils.isEmpty(payResponse)) {
            log.info("6.支付中心返回结果为空");
            uMap.put("status", ItpStatusEnum.FAILED.getCode());
            uMap.put("msg", "支付中心返回结果为空");
            uMap.put("updateTime", DateUtils.getNowTime());
            tvmOrderMapper.updateByOrderNo(uMap);
            return RequestPaymentRespDTO.success("FAILED", "支付中心返回结果为空");
        }

        // 支付中心返回成功
        if (StringUtils.equals(payResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {
            log.info("7.支付中心返回成功");
            Map<String, Object> data = payResponse.getData();
            if (data != null) {
                String status = TransforUtils.getStringFromData(data, "status");
                String paymentChannelCode = TransforUtils.getStringFromData(data, "paymentVendor");
                String payCenterOrderNo = TransforUtils.getStringFromData(data, "orderNo");
                String payCenterChannelOrderNo = TransforUtils.getStringFromData(data, "channelOrderNo");

                if (PayCenterStatusEnum.SUCCESS.getCode().equals(status)) {
                    log.info("查询到支付成功的结果");
                    uMap.put("status", ItpStatusEnum.SUCCESS.getCode());
                    uMap.put("msg", "支付成功");
                    uMap.put("payCenterOrderNo", payCenterOrderNo);
                    uMap.put("payCenterChannelOrderNo", payCenterChannelOrderNo);
                    uMap.put("channel", paymentChannelCode);
                    uMap.put("updateTime", DateUtils.getNowTime());
                    tvmOrderMapper.updateByOrderNo(uMap);
                    return RequestPaymentRespDTO.success("SUCCESS", "支付成功");
                } else if (PayCenterStatusEnum.FAILED.getCode().equals(status)) {
                    log.info("查询到支付失败的结果");
                    uMap.put("status", ItpStatusEnum.FAILED.getCode());
                    uMap.put("msg", "支付失败");
                    uMap.put("channel", paymentChannelCode);
                    uMap.put("updateTime", DateUtils.getNowTime());
                    tvmOrderMapper.updateByOrderNo(uMap);
                    return RequestPaymentRespDTO.success("FAILED", "支付失败");
                } else {
                    log.info("支付结果不明确");
                    return RequestPaymentRespDTO.success("PROCESSING", "处理中");
                }
            }
        }

        // 支付中心返回业务数据失败
        log.info("8.支付中心返回业务数据失败");
        uMap.put("status", ItpStatusEnum.FAILED.getCode());
        uMap.put("msg", "支付中心返回失败");
        uMap.put("updateTime", DateUtils.getNowTime());
        tvmOrderMapper.updateByOrderNo(uMap);
        return RequestPaymentRespDTO.success("FAILED", "支付中心返回失败");
    }

    private Map<String, Object> getTvmOrderPre(TvmPayOrder order, String deviceId) {
        Map<String, Object> preMap = new HashMap<>();
        preMap.put("orderNo", order.getOrderNo());
        preMap.put("transAmount", order.getTotalPrice());
        preMap.put("deviceId", deviceId);
        // 01-扫码购票  02-扫码充值
        preMap.put("transType", BusinessTypeEnum.TVM_SCAN_QR_BUYTICKET.getCode());
        preMap.put("createTime", DateUtils.getNowTime());
        preMap.put("updateTime", "");
        return preMap;
    }

//    // bom售票
//    private Map<String, Object> getBomOrderSalePre(TvmPayOrder order, String deviceId) {
//        Map<String, Object> preMap = new HashMap<>();
//        preMap.put("orderNo", order.getOrderNo());
//        preMap.put("transAmount", order.getTotalPrice());
//        preMap.put("deviceId", deviceId);
//        // 01-扫码购票  02-扫码充值
//        preMap.put("transType", BusinessTypeEnum.BOM_SCANED_SALE_PAY.getCode());
//        preMap.put("createTime", DateUtils.getNowTime());
//        preMap.put("updateTime", "");
//        return preMap;
//    }

    @Override
    public JSONObject requestPayResult(RequestPayResultReqDTO request) {

        log.info("1.开始处理查询支付结果, deviceId={}, request={}", request.getDeviceId(), request);
        JSONObject result = new JSONObject();

        TvmPayOrder payOrderInfo = tvmOrderMapper.selectByOrderNo(request.getOrderNo());
        if (payOrderInfo == null) {
            return TvmOrderResult.fail(TvmPayCodeEnum.INVALID_PARAM.getCode(), "没有找到匹配的订单，请确认订单号是否正确");
        }

        // 如果已经是成功，直接返回
        if (ItpStatusEnum.SUCCESS.getCode().equals(payOrderInfo.getStatus())) {
            log.info("2.数据库查询结果为支付成功，直接返回");
            return TvmOrderResult.successData(DeviceResponse.getPaySuccessResult(payOrderInfo.getChannel()));
        }

        // 如果已经是失败，直接返回
        if (ItpStatusEnum.FAILED.getCode().equals(payOrderInfo.getStatus())) {
            log.info("2.数据库查询结果为支付失败，直接返回");
            return TvmOrderResult.failData(DeviceResponse.getPayFailResult(payOrderInfo.getChannel()));
        }
        // 如果已经是未支付，直接返回 未支付也是终态，轮询结束后没有支付则认为未支付
        if (ItpStatusEnum.UNPAID.getCode().equals(payOrderInfo.getStatus())) {
            log.info("2.数据库查询结果为未支付，直接返回");
            return TvmOrderResult.failData(DeviceResponse.getPayFailResult(payOrderInfo.getChannel()));
        }

        String tvmQueryUrl = payCenterProperties.getPayCenterQueryUrl();
        // 订单为已下单状态，需要向支付中心查询实际支付结果
        PayCenterRequest queryPayRequest = payCenterCommon.buildQueryPayCenterRequest(payOrderInfo.getOrderNo());

        log.info("3.tvmQueryUrl is {} , queryPayRequest is {}", tvmQueryUrl, queryPayRequest);
        PayCenterResponse queryPayResponse = payCenterService.callPayCenter(tvmQueryUrl, queryPayRequest);

        log.info("queryPayResponse is {}", queryPayResponse);

        if (ObjectUtils.isEmpty(queryPayResponse)) {
            log.info("6.支付中心返回结果为空,结束");
        } else {

            if (StringUtils.equals(queryPayResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {

                Map<String, String> uMap = UpdateDbMap.getLaMaUpdateFailDb(request.getOrderNo());
                Map<String, Object> data = queryPayResponse.getData();
                if (data != null) {
                    String status = TransforUtils.getStringFromData(data, "status");
                    String payCenterOrderNo = TransforUtils.getStringFromData(data, "orderNo");
                    String payCenterChannelOrderNo = TransforUtils.getStringFromData(data, "channelOrderNo");
                    String paymentChannelCode = TransforUtils.getStringFromData(data, "paymentVendor");

                    // 查询支付中心支付状态为支付成功
                    if (PayCenterStatusEnum.SUCCESS.getCode().equals(status)) {

                        log.info("查询到支付成功的结果");
                        uMap = UpdateDbMap.getQueryUpdateSuccessDb(request.getOrderNo(), payCenterOrderNo, payCenterChannelOrderNo);
                        result = TvmOrderResult.successData(DeviceResponse.getPaySuccessResult(paymentChannelCode));

                    } else if (PayCenterStatusEnum.FAILED.getCode().equals(status)) {
                        log.info("查询到支付失败的结果");
                        uMap = UpdateDbMap.getQueryUpdateFailDb(request.getOrderNo());
                        result = TvmOrderResult.successData(DeviceResponse.getPayFailResult(paymentChannelCode));
                    } else {
                        log.info("查询到不明确的结果，按已下单-支付中处理");
                        result = TvmOrderResult.successData(DeviceResponse.getPayIngResult(paymentChannelCode));
                        return result;
                    }
                    log.info("开始修改记录 uMap is {}", uMap);
                    int i = tvmOrderMapper.updateByOrderNo(uMap);
                    log.info("修改结束 i is {}", i);
                    return result;
                }
            }

        }
        // 没有查询到支付结果，或结果为空，全部按照支付中-已下单返回
        return TvmOrderResult.successData(DeviceResponse.getPayIngResult(payOrderInfo.getChannel()));
    }

    @Override
    public JSONObject notiTakeTicketResult(NotiTakeTicketResultReqDTO request) {
        log.info("1.开始处理出票结果通知, deviceId={}, request={}", request.getDeviceId(), request);

        String payOrderNo = request.getOrderNo();

        TvmPayPreOrder tvmPayPreOrder = tvmOrderPreMapper.selectByOrderNo(payOrderNo);
        if (org.springframework.util.ObjectUtils.isEmpty(tvmPayPreOrder) || StringUtils.isEmpty(tvmPayPreOrder.getTransType())) {
            return TvmOrderResult.fail("-1", "没有找到匹配的订单，请确认订单号是否正确");
        }
        // 业务类型
        String transType = tvmPayPreOrder.getTransType();

        int buyNum = 0;
        String payCenterOrderNo = "";
        String ticketPrice = "";
        String businessType = transType;
        String ticketNum = "";
        // 扫码购票
        if (StringUtils.equals(transType, BusinessTypeEnum.TVM_SCAN_QR_BUYTICKET.getCode())) {
            log.info("支付中心通知 扫码购票 支付结果");
            TvmPayOrder order = tvmOrderMapper.selectByOrderNo(request.getOrderNo());
            buyNum = order.getTicketNum();
            payCenterOrderNo = order.getPayCenterOrderNo();
            ticketPrice = order.getTicketPrice();
            ticketNum = String.valueOf(order.getTicketNum());
        } else if (StringUtils.equals(transType, BusinessTypeEnum.TVM_SCAN_QR_TAKETICKET.getCode())) {
            TvmAppOrder order = tvmAppOrderMapper.selectByOrderNo(request.getOrderNo());
            buyNum = Integer.valueOf(order.getTicketNum());
            payCenterOrderNo = order.getPayCenterOrderNo();
            ticketPrice = order.getTicketPrice();
            ticketNum = order.getTicketNum();
        }


        int actualNum = Integer.parseInt(request.getActualTakeTicketNum());

        // 保存出票主记录
        TvmMainTicket mainTicket = new TvmMainTicket();
        mainTicket.setId(tvmMainTicketMapper.getTvmMainTicketSeq());
        mainTicket.setOrderNo(request.getOrderNo());
        mainTicket.setActualTakeTicketNum(actualNum);
        mainTicket.setBuyTicketNum(buyNum);
        mainTicket.setTakeTickeDate(request.getTakeTickeDate());
        mainTicket.setBusinessType(businessType);
        mainTicket.setNotifyType("0"); // 0-出票结果通知
        mainTicket.setCreateTime(DateUtils.getNowTime());
        log.info("3.开始保存出票主记录, mainTicket={}", mainTicket);
        tvmMainTicketMapper.insert(mainTicket);

        log.info("request.getTicketList() is {}", request.getTicketList());
        // 保存出票明细记录
        if (request.getTicketList() != null && !request.getTicketList().isEmpty()) {
            log.info("4.开始保存出票明细记录, 数量={}", request.getTicketList().size());
            for (NotiTakeTicketResultReqDTO.TicketInfo ticketInfo : request.getTicketList()) {
                TvmSubTicket subTicket = new TvmSubTicket();
                subTicket.setMainTicketId(mainTicket.getId());
                subTicket.setTicketLogicNum(ticketInfo.getTicketLogicNum());
                subTicket.setTransDate(ticketInfo.getTransDate());
                subTicket.setTransAmount(ticketInfo.getTransAmount());
                subTicket.setCreateTime(DateUtils.getNowTime());
                tvmSubTicketMapper.insert(subTicket);
            }
        }

        // 如果是扫码取票的业务，通知app取票结果
        if (StringUtils.equals(transType, BusinessTypeEnum.TVM_SCAN_QR_TAKETICKET.getCode())) {
            log.info("扫码取票业务，开始给app发送取票结果通知");
            this.noticeAppTakeTicketResult(payOrderNo, ticketNum, request.getActualTakeTicketNum(), request.getTakeTickeDate());
        }

        // 如果购票数量大于实际出票数量，发起退款
        int refundAmount = handleRefund(payOrderNo, payCenterOrderNo, ticketPrice, buyNum, actualNum, businessType);
        log.info("5.出票结果通知处理完成, orderNo={}, buyNum={}, actualNum={}, refundAmount={}",
                request.getOrderNo(), buyNum, actualNum, refundAmount);
        return TvmOrderResult.success();
    }

    // 通知app取票结果
    public void noticeAppTakeTicketResult(String payOrderNo, String orderTicketNum, String actualTakeTicketNum, String takeTickeDate) {

        log.info("开始通知app取票结果");
        // 通知app的参数
        Map<String, String> noticeMap = new HashMap<>();
        noticeMap.put("orderNo", payOrderNo);
        noticeMap.put("orderTicketNum", orderTicketNum);
        noticeMap.put("actualTakeTicketNum", actualTakeTicketNum);
        noticeMap.put("takeTickeDate", takeTickeDate);
        log.info("noticeMap is {}", noticeMap);

        // 保存到数据库
        Map<String, String> saveMap = new HashMap<>();
        saveMap.putAll(noticeMap);
        saveMap.put("status", ItpCommon.NOTICE_INIT);
        saveMap.put("createTime", DateUtils.getNowTime());
        String retryTimes = "0";
        saveMap.put("retryTimes", retryTimes);

        int insert = tvmNoticeAppMapper.insertTakeNotice(saveMap);
        log.info("通知app记录保存成功 i is {}", insert);

        log.info("开始发送给app");
        boolean b = sendNoticeAppTakeTicketRecord(payOrderNo, orderTicketNum, actualTakeTicketNum, takeTickeDate, retryTimes);
        log.info("发送结束 b is {}", b);
    }

    @Override
    // 发送给app
    public boolean sendNoticeAppTakeTicketRecord(String payOrderNo, String orderTicketNum, String actualTakeTicketNum, String takeTickeDate, String retryTimes) {

        boolean b = false;
        String noticeAppTakeTicketResultUrl = environment.getProperty("pay.center.notice-app-taketicketresult-url");

        log.info("noticeAppTakeTicketResultUrl is {}", noticeAppTakeTicketResultUrl);
        AppCommonRequest<NoticeAppTakeTicketDTO> request = payCenterCommon.buildNoticeAppTakeTicketResultRequest(payOrderNo, orderTicketNum, actualTakeTicketNum, takeTickeDate);
        String httpResult = httpUtils.doPostFormData(noticeAppTakeTicketResultUrl, request);

        log.info("请求结束 httpResult is {}", httpResult);
        JSONObject httpResultJson = (JSONObject) JSONObject.parse(httpResult);
        String retCode = String.valueOf(httpResultJson.get("retCode"));
        log.info("retCode is {}", retCode);

        Map<String, Object> upMap = new HashMap<>();
        upMap.put("orderNo", payOrderNo);
        upMap.put("updateTime", DateUtils.getNowTime());
        upMap.put("retryTimes", retryTimes);
        if (StringUtils.equals(AppCodeEnum.SUCCESS.getCode(), retCode)) {
            log.info("通知成功，修改通知记录状态为成功");
            upMap.put("status", ItpCommon.NOTICE_SUCCESS);
            b = true;
        } else {
            log.info("通知失败，修改通知状态为失败");
            upMap.put("status", ItpCommon.NOTICE_FAIL);
        }

        int i = tvmNoticeAppMapper.updateTakeNoticeByOrderNo(upMap);
        log.info("修改通知记录状态结束 i is {}", i);
        return b;
    }

    // 通知app取票故障结果
    public void noticeAppTakeTicketFailureResult(String payOrderNo, String orderTicketNum, String actualTakeTicketNum, String takeTickeDate,String takeTiketFaultReason,String refundAmount) {

        log.info("payOrderNo is {} orderTicketNum is {} actualTakeTicketNum is {} takeTickeDate is {} takeTiketFaultReason is {} refundAmount is {} ", payOrderNo,  orderTicketNum,  actualTakeTicketNum,  takeTickeDate, takeTiketFaultReason, refundAmount);
        log.info("开始通知app取票故障结果");
        // 通知app的参数
        Map<String, String> noticeFailureMap = new HashMap<>();
        noticeFailureMap.put("orderNo", payOrderNo);
        noticeFailureMap.put("orderTicketNum", orderTicketNum);
        noticeFailureMap.put("actualTakeTicketNum", actualTakeTicketNum);
        noticeFailureMap.put("takeTickeDate", takeTickeDate);
        noticeFailureMap.put("takeTiketFaultReason", takeTiketFaultReason);
        noticeFailureMap.put("refundAmount", refundAmount);
        log.info("noticeFailureMap is {}", noticeFailureMap);

        // 保存到数据库
        Map<String, String> saveMap = new HashMap<>();
        saveMap.putAll(noticeFailureMap);
        saveMap.put("status", ItpCommon.NOTICE_INIT);
        saveMap.put("createTime", DateUtils.getNowTime());
        String retryTimes = "0";
        saveMap.put("retryTimes", retryTimes);

        int insert = tvmNoticeAppMapper.insertTakeFailureNotice(saveMap);
        log.info("通知app取票故障记录保存成功 i is {}", insert);

        log.info("开始发送给app取票故障");
        // 这里很明确是第一次发送所以retryTimes设置为1
        boolean b = sendNoticeAppTakeTicketFailureRecord( payOrderNo,  orderTicketNum,  actualTakeTicketNum,  takeTickeDate, takeTiketFaultReason, refundAmount,"1");
        log.info("发送取票故障结束 b is {}", b);
    }

    @Override
    public boolean sendNoticeAppTakeTicketFailureRecord(String payOrderNo, String orderTicketNum, String actualTakeTicketNum, String takeTickeDate,String takeTiketFaultReason,String refundAmount, String retryTimes) {

        boolean b = false;
        String taketicketfailureresultUrl = environment.getProperty("pay.center.notice-app-taketicketfailureresult-url");

        log.info("出票故障 taketicketfailureresultUrl is {}", taketicketfailureresultUrl);
        AppCommonRequest<NoticeAppTakeTicketFailureDTO> request = payCenterCommon.buildNoticeAppTakeTicketFailureResultRequest(payOrderNo, orderTicketNum, actualTakeTicketNum, takeTickeDate,takeTiketFaultReason,refundAmount);
        String httpResult = httpUtils.doPost2(taketicketfailureresultUrl, request);

        log.info("出票故障 请求结束 httpResult is {}", httpResult);
        JSONObject httpResultJson = (JSONObject) JSONObject.parse(httpResult);
        String retCode = String.valueOf(httpResultJson.get("retCode"));
        log.info("出票故障 retCode is {}", retCode);

        Map<String, Object> upMap = new HashMap<>();
        upMap.put("orderNo", payOrderNo);
        upMap.put("updateTime", DateUtils.getNowTime());
        upMap.put("retryTimes", retryTimes);
        if (StringUtils.equals(AppCodeEnum.SUCCESS.getCode(), retCode)) {
            log.info("出票故障通知成功，修改通知记录状态为成功");
            upMap.put("status", ItpCommon.NOTICE_SUCCESS);
            b = true;
        } else {
            log.info("出票故障通知失败，修改通知状态为失败");
            upMap.put("status", ItpCommon.NOTICE_FAIL);
        }

        int i = tvmNoticeAppMapper.updateTakeFailureNoticeByOrderNo(upMap);
        log.info("出票故障 修改通知记录状态结束 i is {}", i);
        return b;
    }


    @Override
    public JSONObject notiTakeTicketFailResult(NotiTakeTicketFailResultReqDTO request) {

        log.info("1.开始处理出票故障通知, deviceId={}, request={}", request.getDeviceId(), request);
        String payOrderNo = request.getOrderNo();

        TvmPayPreOrder tvmPayPreOrder = tvmOrderPreMapper.selectByOrderNo(payOrderNo);
        if (org.springframework.util.ObjectUtils.isEmpty(tvmPayPreOrder) || StringUtils.isEmpty(tvmPayPreOrder.getTransType())) {
            return TvmOrderResult.fail("-1", "没有找到匹配的订单，请确认订单号是否正确");
        }
        // 业务类型
        String transType = tvmPayPreOrder.getTransType();

        int buyNum = 0;
        String payCenterOrderNo = "";
        String ticketPrice = "";
        String businessType = transType;
        // 扫码购票
        if (StringUtils.equals(transType, BusinessTypeEnum.TVM_SCAN_QR_BUYTICKET.getCode())) {
            log.info("支付中心通知 扫码购票 支付结果");
            TvmPayOrder order = tvmOrderMapper.selectByOrderNo(request.getOrderNo());
            buyNum = order.getTicketNum();
            payCenterOrderNo = order.getPayCenterOrderNo();
            ticketPrice = order.getTicketPrice();
        } else if (StringUtils.equals(transType, BusinessTypeEnum.TVM_SCAN_QR_TAKETICKET.getCode())) {
            TvmAppOrder order = tvmAppOrderMapper.selectByOrderNo(request.getOrderNo());
            buyNum = Integer.valueOf(order.getTicketNum());
            payCenterOrderNo = order.getPayCenterOrderNo();
            ticketPrice = order.getTicketPrice();
        }
        int actualNum = 0;
        if (StringUtils.isNotEmpty(request.getActualTakeTicketNum())) {
            actualNum = Integer.parseInt(request.getActualTakeTicketNum());
        }

        // 保存出票故障主记录
        TvmMainTicket mainTicket = new TvmMainTicket();
        mainTicket.setId(tvmMainTicketMapper.getTvmMainTicketSeq());
        mainTicket.setOrderNo(request.getOrderNo());
        mainTicket.setActualTakeTicketNum(actualNum);
        mainTicket.setBuyTicketNum(buyNum);
        mainTicket.setTakeTickeDate(request.getFaultOccurDate());
        mainTicket.setNotifyType("1"); // 1-出票故障通知
        mainTicket.setFaultSlipSeq(request.getFaultSlipSeq());
        mainTicket.setErrorCode(request.getErrorCode());
        mainTicket.setErrorMessage(request.getErrorMessage());
        mainTicket.setCreateTime(DateUtils.getNowTime());
        log.info("3.开始保存出票故障主记录, mainTicket={}", mainTicket);
        tvmMainTicketMapper.insert(mainTicket);

        // 保存出票明细记录（如果有）
        if (request.getTicketList() != null && !request.getTicketList().isEmpty()) {
            log.info("4.开始保存出票明细记录, 数量={}", request.getTicketList().size());
            for (NotiTakeTicketFailResultReqDTO.TicketInfo ticketInfo : request.getTicketList()) {
                TvmSubTicket subTicket = new TvmSubTicket();
                subTicket.setMainTicketId(mainTicket.getId());
                subTicket.setTicketLogicNum(ticketInfo.getTicketLogicNum());
                subTicket.setTransDate(ticketInfo.getTransDate());
                subTicket.setTransAmount(ticketInfo.getTransAmount());
                subTicket.setCreateTime(DateUtils.getNowTime());
                tvmSubTicketMapper.insert(subTicket);
            }
        }

        // 如果是扫码取票的业务，通知app故障结果
        if (StringUtils.equals(transType, BusinessTypeEnum.TVM_SCAN_QR_TAKETICKET.getCode())) {
            log.info("扫码取票业务，开始给app发送取票结果通知");

            // app要的取票时间，tvm没有这个字段，这里取tvm发来的故障时间-faultOccurDate
            String takeTickeDate = request.getFaultOccurDate().substring(0, 8);
            // 计算要退款的金额
            int refundNum = buyNum - actualNum;
            String refundAmount = String.valueOf(new BigDecimal(ticketPrice).multiply(new BigDecimal(refundNum)));
            log.info("当前计算要退款的金额是 refundAmount is {}",refundAmount);
            this.noticeAppTakeTicketFailureResult(payOrderNo, String.valueOf(buyNum), request.getActualTakeTicketNum(),takeTickeDate,request.getErrorMessage(), refundAmount);
        }

        // 如果购票数量大于实际出票数量，发起退款 退款时发送的是支付中心的订单号
        int refundAmount = handleRefund(payOrderNo, payCenterOrderNo, ticketPrice, buyNum, actualNum, businessType);
        log.info("5.出票故障通知处理完成, orderNo={}, buyNum={}, actualNum={}, refundAmount={}",
                request.getOrderNo(), buyNum, actualNum, refundAmount);

        return TvmOrderResult.success();
    }


    /**
     * 处理退款逻辑。
     * 如果购票数量大于实际出票数量，发起退款并返回退款金额。
     *
     * @param buyNum    购票数量
     * @param actualNum 实际出票数量
     * @return 退款金额（分），未退款返回0
     */
//    private int handleRefund(TvmPayOrder order, int buyNum, int actualNum) {
//        log.info("开始退款处理");
//        if (order != null && buyNum > actualNum) {
//            int refundNum = buyNum - actualNum;
//            BigDecimal price = new BigDecimal(order.getTicketPrice());
//            BigDecimal refundAmount = price.multiply(new BigDecimal(refundNum));
//            log.info("购票数量大于实际出票数量，发起退款, orderNo={}, buyNum={}, actualNum={}, refundAmount={}",
//                    order.getOrderNo(), buyNum, actualNum, refundAmount);
//
//            String refundNo = tvmCommonService.doRefund(BusinessTypeEnum.TVM_SCAN_QR_BUYTICKET.getCode(), order.getOrderNo(), order.getPayCenterOrderNo(), refundAmount.intValue());
//
//            // 如果refundNo不为空，则证明退款结束 退款结果可以是成功的也可以是失败的
//            if (StringUtils.isEmpty(refundNo)) {
//                // 6. 更新原支付订单的rsv2字段（退款记录ID），不修改原支付状态
//                Map<String, String> upMap = new HashMap<>();
//                upMap.put("orderNo", order.getOrderNo());
//                upMap.put("rsv2", refundNo);
//                upMap.put("updateTime", DateUtils.getNowTime());
//                tvmOrderMapper.updateByOrderNo(upMap);
//            }
//
//
//            return refundAmount.intValue();
//        }
//        return 0;
//    }
    private int handleRefund(String orderNo, String payCenterOrderNo, String ticketPrice, int buyNum, int actualNum, String businessType) {
        log.info("开始判断是否需要退款处理");

        if (buyNum > actualNum) {
            int refundNum = buyNum - actualNum;
            BigDecimal price = new BigDecimal(ticketPrice);
            BigDecimal refundAmount = price.multiply(new BigDecimal(refundNum));
            log.info("购票数量大于实际出票数量，发起退款, orderNo={}, buyNum={}, actualNum={}, refundAmount={}",
                    orderNo, buyNum, actualNum, refundAmount);

            String refundNo = tvmCommonService.doRefund(businessType, orderNo, payCenterOrderNo, refundAmount.intValue());

            log.info("退款结束 refundNo is {}", refundNo);
            // 如果refundNo不为空，则证明退款结束 退款结果可以是成功的也可以是失败的
            if (!StringUtils.isEmpty(refundNo)) {
                // 6. 更新原支付订单的rsv2字段（退款记录ID），不修改原支付状态
                Map<String, String> upMap = new HashMap<>();
                upMap.put("orderNo", orderNo);
                upMap.put("rsv2", refundNo);
                upMap.put("updateTime", DateUtils.getNowTime());
                tvmOrderMapper.updateByOrderNo(upMap);
            }
            return refundAmount.intValue();
        } else {
            log.info("出票数量和购买数量相等，不发起退款");
        }
        return 0;
    }


    @Override
    public JSONObject requestRefund(RequestRefundReqDTO request) {
        log.info("1.开始处理退款请求, deviceId={}, request={}", request.getDeviceId(), request);

        if (request == null || !StringUtils.isNotBlank(request.getOrderNo())) {
            log.info("参数校验失败, orderNo为空");
            return RequestRefundRespDTO.fail("9999", "订单号不能为空");
        }

        // 查询订单信息
        TvmPayOrder order = tvmOrderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            log.info("2.没有找到匹配的订单, orderNo={}", request.getOrderNo());
            return RequestRefundRespDTO.fail("9999", "订单号错误,没有找到匹配的订单");
        }

        // 订单已支付成功才退款
        if (!ItpStatusEnum.SUCCESS.getCode().equals(order.getStatus())) {
            log.info("2.订单状态不是支付成功, 不能退款, status={}", order.getStatus());
            return RequestRefundRespDTO.fail("9999", "订单状态不是支付成功,不能退款");
        }

        String refundNo = tvmCommonService.doRefund(BusinessTypeEnum.TVM_SCAN_QR_BUYTICKET.getCode(), order.getOrderNo(), order.getPayCenterOrderNo(), Integer.valueOf(request.getRefundAmt()));
        // 如果refundNo不为空，则证明退款结束 退款结果可以是成功的也可以是失败的
        log.info("退款解释 refundNo is {}", refundNo);
        if (!StringUtils.isEmpty(refundNo)) {
            // 6. 更新原支付订单的rsv2字段（退款记录ID），不修改原支付状态
            Map<String, String> upMap = new HashMap<>();
            upMap.put("orderNo", order.getOrderNo());
            upMap.put("rsv2", refundNo);
            upMap.put("updateTime", DateUtils.getNowTime());
            tvmOrderMapper.updateByOrderNo(upMap);
        }
        log.info("退款结束");

        return TvmOrderResult.success();
    }

    // 构建拉码请求参数
    private TvmPayOrder buildTvmPayOrder(String orderNo, String deviceId, RequestGenSjtOrderReqDTO request, String now) {
        TvmPayOrder order = new TvmPayOrder();
        order.setOrderNo(orderNo);
        order.setDeviceId(deviceId);
        order.setInStationCode(request.getEntryStationCode());
        order.setOutStationCode(request.getExitStationCode());
        order.setTicketPrice(request.getTicketPrice());
        try {
            order.setTicketNum(Integer.parseInt(request.getSingelTicketNum()));
        } catch (NumberFormatException e) {
            order.setTicketNum(0);
        }
        order.setTicketType(request.getSingleTicketType());
        order.setPayType(request.getPayType());
        order.setStatus(ItpStatusEnum.PAYING.getCode());
        order.setMsg(ItpStatusEnum.PAYING.getDesc());
        order.setCreateTime(now);
        order.setUpdateTime(now);

        BigDecimal totalPrice = order.calculateTotalPrice();
        order.setTotalPrice(totalPrice.toString());
        return order;
    }


    private String generateOrderNo() {
        long seq = orderSeqMapper.nextval();
        return OrderNoUtils.generateOrderNo(ProductType.ordinaryTicket, seq);
    }

//    private String TransforUtils.getStringFromData(Map<String, Object> data, String key) {
//        Object value = data.get(key);
//        return value != null ? value.toString() : null;
//    }

    @Override
    public JSONObject requestPayOrderDetail(RequestPayResultReqDTO request) {

        String orderNo = request.getOrderNo();
        log.info("1.支付中心查询 扫码购票 订单详情,orderNo is {}", orderNo);

        // 查询订单信息
        TvmPayOrder order = tvmOrderMapper.selectByOrderNo(orderNo);
        log.info("2.支付中心查询 扫码购票 订单详情,order is {}", order);

        if (ObjectUtils.isEmpty(order)) {
            log.info("3.没有找到匹配的 扫码购票 订单，请确认订单号是否正确");
            return TvmOrderResult.fail(TvmPayCodeEnum.INVALID_PARAM.getCode(), "没有找到匹配的订单，请确认订单号是否正确");
        }

        // 组装返回结果
        JSONObject jsonObject = getPayCenterPayOrderDetailResult(order);
        log.info("3.返回结果 jsonObject is {}", jsonObject);

        return TvmOrderResult.successData(jsonObject);
    }

    private JSONObject getPayCenterPayOrderDetailResult(TvmPayOrder order) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("orderNo", order.getOrderNo());
        // todo 需改为中文名
        jsonObject.put("singlePickupStationName", order.getInStationCode());
        jsonObject.put("singlePickupStationCode", order.getInStationCode());
        // todo 需改为中文名
        jsonObject.put("singleGetoffStationName", order.getOutStationCode());
        jsonObject.put("singleGetoffStationCode", order.getOutStationCode());
        jsonObject.put("singleTicketNum", order.getTicketNum());
        jsonObject.put("totalTicketPrice", order.getTotalPrice());
        jsonObject.put("regDate", order.getCreateTime().replace("-", "").replace(":", "").replace(" ", ""));
        String status = order.getStatus();

        String orderStatus = "";
        if (StringUtils.equals(status, ItpStatusEnum.PAYING.getCode())) {
            orderStatus = "1";
        } else if (StringUtils.equals(status, ItpStatusEnum.SUCCESS.getCode())) {
            orderStatus = "2";
            jsonObject.put("payDate", order.getUpdateTime().replace("-", "").replace(":", "").replace(" ", ""));
            // 说明发生了退款 先判断是否支付成功，只有支付成功后，才可以发起退款
            if (!StringUtils.isEmpty(order.getRsv2())) {
                log.info("该订单已发生退款");
                orderStatus = "7";
            }
        }
        jsonObject.put("orderStatus", orderStatus);
        jsonObject.put("subject", "一票通_单程票");
        jsonObject.put("body", "一票通_单程票");
        jsonObject.put("notifyUrl", environment.getProperty("pay.center.pay-notice"));
        return jsonObject;
    }

    @Override
    public JSONObject payNotice(PayNoticeReqDTO request) {

        // 查询订单信息
        TvmPayOrder order = tvmOrderMapper.selectByOrderNo(request.getMerchantOrderNo());
        if (order == null) {
            log.info("支付结果通知 订单不存在, orderNo={}", request.getOrderNo());
            return TvmOrderResult.fail(TvmPayCodeEnum.ORDER_NO_ERROR.getCode(), TvmPayCodeEnum.ORDER_NO_ERROR.getMsg());
        }

        // 订单已支付成功，直接返回
        if (ItpStatusEnum.SUCCESS.getCode().equals(order.getStatus())) {
            log.info("支付结果通知 订单已支付成功 不做处理 返回成功");
            return TvmOrderResult.success();
        }

        // 订单已支付失败，直接返回
        if (ItpStatusEnum.FAILED.getCode().equals(order.getStatus())) {
            log.info("支付结果通知 订单已支付失败 返回成功");
            return TvmOrderResult.success();
        }

        log.info("开始处理 tvm扫码购票 支付结果通知 request is {}", request);
        Map<String, String> uMap = new HashMap<>();
        String status = request.getStatus();
        // itp订单号
        String orderNo = request.getMerchantOrderNo();
        // 支付中心订单号
        String payCenterOrderNo = request.getOrderNo();
        // 渠道订单号
        String channelOrderNo = request.getChannelOrderNo();

        // 查询支付中心支付状态为支付成功
        if (PayCenterStatusEnum.SUCCESS.getCode().equals(status)) {

            log.info("支付结果通知 支付成功 的结果");
            uMap = UpdateDbMap.getQueryUpdateSuccessDb(orderNo, payCenterOrderNo, channelOrderNo);

        } else if (PayCenterStatusEnum.FAILED.getCode().equals(status)) {
            log.info("支付结果通知 支付失败 的结果");
            uMap = UpdateDbMap.getQueryUpdateFailDb(request.getOrderNo());
        } else {
            log.info("支付结果通知 不明确的结果，按已下单-支付中处理");
            return TvmOrderResult.fail();
        }
        log.info("支付结果通知 开始修改记录 uMap is {}", uMap);
        int i = tvmOrderMapper.updateByOrderNo(uMap);
        log.info("支付结果通知 修改结束 i is {}", i);
        return TvmOrderResult.success();

    }

}
