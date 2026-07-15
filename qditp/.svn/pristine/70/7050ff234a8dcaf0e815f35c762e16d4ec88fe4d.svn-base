package com.chinasofti.huateng.collectpay.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.collectpay.common.DeviceResponse;
import com.chinasofti.huateng.collectpay.common.PayCenterCommon;
import com.chinasofti.huateng.collectpay.common.UpdateDbMap;
import com.chinasofti.huateng.collectpay.config.PayCenterProperties;
import com.chinasofti.huateng.collectpay.constant.*;
import com.chinasofti.huateng.collectpay.entity.TvmMainTicket;
import com.chinasofti.huateng.collectpay.entity.TvmSubTicket;
import com.chinasofti.huateng.collectpay.mapper.*;
import com.chinasofti.huateng.collectpay.entity.TvmPayOrder;
import com.chinasofti.huateng.collectpay.entity.RefundOrder;
import com.chinasofti.huateng.collectpay.model.request.PayCenterRequest;
import com.chinasofti.huateng.collectpay.model.request.RequestPayReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.NotiTakeTicketFailResultReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.NotiTakeTicketResultReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.RequestGenSjtOrderReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.RequestPayResultReqDTO;
import com.chinasofti.huateng.collectpay.model.response.PayCenterResponse;
import com.chinasofti.huateng.collectpay.model.response.tvm.NotiTakeTicketFailResultRespDTO;
import com.chinasofti.huateng.collectpay.model.response.tvm.NotiTakeTicketResultRespDTO;
import com.chinasofti.huateng.collectpay.model.response.tvm.TvmOrderResult;
import com.chinasofti.huateng.collectpay.model.response.tvm.RequestPayResultRespDTO;
import com.chinasofti.huateng.collectpay.service.PayCenterService;
import com.chinasofti.huateng.collectpay.service.TvmOrderService;
import com.chinasofti.huateng.collectpay.utils.DateUtils;
import com.chinasofti.huateng.collectpay.utils.HttpUtils;
import com.chinasofti.huateng.collectpay.utils.SignUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
    private RefundOrderMapper refundOrderMapper;

    @Autowired
    Environment environment;
    @Autowired
    PayCenterCommon payCenterCommon;
    @Autowired
    private PayCenterService payCenterService;
    @Autowired
    private SignUtils signUtils;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public JSONObject requestTvmPayOrder(RequestGenSjtOrderReqDTO request) {
            log.info("1.开始处理提交单程票订单, deviceId={}, request={}", request.getDeviceId(), request);

            String now = DateUtils.getNowTime();
            String orderNo = generateOrderNo();
            log.info("2.now is {} orderNo is {}",now,orderNo);

            // tvm订单 todo 订单号需要去其他平台获取
            TvmPayOrder order = buildTvmPayOrder(orderNo, request.getDeviceId(), request, now);
            log.info("3.tvm订单 order is {}",order);

            // 保存支付订单前置信息
            tvmOrderPreMapper.insert(getTvmOrderPre(order, request.getDeviceId()));
            tvmOrderMapper.insert(order);

            // 非数币渠道直接返回
            if(request.getPayType().equals("0")){
                // todo 签名
                String sign = "sign-test";
                String payUrl = "http://dtcustomer.bestonepay.com/ngpayment-gateway/api/v1/checkoutCounter"+"&orderNo="+orderNo+"&sign="+sign;
                return TvmOrderResult.successData(DeviceResponse.getLaMaSuccessRespose(orderNo, payUrl));
            }
//            PayCenterRequest payCenterRequest = buildPayRequest(order);
            PayCenterRequest payCenterRequest = payCenterCommon.buildTvmPayRequest(order.getOrderNo(),order.getTotalPrice(),order.getPayType(),"单程票购票","地铁单程票");
            String tvmPayUrl = payCenterProperties.getPayCenterPayUrl();
            log.info("4.拉码请求 tvmPayUrl is {} , payCenterRequest is {}",tvmPayUrl,payCenterRequest);
            PayCenterResponse payResponse = payCenterService.callPayCenter(tvmPayUrl, payCenterRequest);
            log.info("5.拉码结束 payResponse is {}",payResponse);

            // 默认为失败
            JSONObject result = TvmOrderResult.fail();
            Map<String, String> uMap = UpdateDbMap.getLaMaUpdateFailDb(orderNo);

            if(ObjectUtils.isEmpty(payResponse)){
                log.info("6.支付中心返回结果为空,结束");
            }else {

                // todo 当前以code=200为业务实际返回成功
                if (StringUtils.equals(payResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {
                    log.info("7.支付中心返回成功");
                    Map<String, Object> data = payResponse.getData();
                    if (data != null) {
                        log.info("8.支付中心返回业务数据为 data is {}", data);
                        String payUrl = getStringFromData(data, "data");
                        String payCenterOrderNo = getStringFromData(data, "orderNo");
                        String payCenterChannelOrderNo = getStringFromData(data, "channelOrderNo");
                        // 修改数据库记录
                        uMap = UpdateDbMap.getLaMaUpdateSuccessDb(orderNo, payCenterOrderNo,payCenterChannelOrderNo,payUrl);
                        // 给设备返回结果
                        result = TvmOrderResult.successData(DeviceResponse.getLaMaSuccessRespose(orderNo, payUrl));
                    }
                } else {
                    log.info("7.支付中心返回业务数据失败");
                }
            }

        log.info("9.开始修改记录 uMap is {}",uMap);
        int i = tvmOrderMapper.updateByOrderNo(uMap);
        log.info("10.修改结束 i is {}",i);

        return result;

    }

    private Map<String,Object> getTvmOrderPre(TvmPayOrder order ,String deviceId ){
        Map<String,Object> preMap = new HashMap<>();
        preMap.put("orderNo",order.getOrderNo());
        preMap.put("transAmount",order.getTotalPrice());
        preMap.put("deviceId",deviceId);
        // 01-扫码购票  02-扫码充值
        preMap.put("transType","01");
        preMap.put("createTime",DateUtils.getNowTime());
        preMap.put("updateTime","");
        return preMap;
    }

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

        log.info("3.tvmQueryUrl is {} , queryPayRequest is {}",tvmQueryUrl,queryPayRequest);
        PayCenterResponse queryPayResponse = payCenterService.callPayCenter(tvmQueryUrl, queryPayRequest);

        log.info("queryPayResponse is {}", queryPayResponse);

        if (ObjectUtils.isEmpty(queryPayResponse)) {
            log.info("6.支付中心返回结果为空,结束");
        }else {

        if (StringUtils.equals(queryPayResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {

            Map<String, String> uMap = UpdateDbMap.getLaMaUpdateFailDb(request.getOrderNo());
            Map<String, Object> data = queryPayResponse.getData();
            if (data != null) {
                String status = getStringFromData(data, "status");
                String channelOrderNo = getStringFromData(data, "channelOrderNo");
                String paymentChannelCode = getStringFromData(data, "paymentVendor");

                // 查询支付中心支付状态为支付成功
                if (PayCenterStatusEnum.SUCCESS.getCode().equals(status)) {

                    log.info("查询到支付成功的结果");
                    uMap = UpdateDbMap.getQueryUpdateSuccessDb(request.getOrderNo(),channelOrderNo);
                    result = TvmOrderResult.successData(DeviceResponse.getPaySuccessResult(paymentChannelCode));

                } else if (PayCenterStatusEnum.FAILED.getCode().equals(status)) {
                    log.info("查询到支付失败的结果");
                    result = TvmOrderResult.successData(DeviceResponse.getPayFailResult(paymentChannelCode));
                } else {
                    log.info("查询到不明确的结果，按已下单-支付中处理");
                    result = TvmOrderResult.successData(DeviceResponse.getPayIngResult(paymentChannelCode));
                }
                log.info("开始修改记录 uMap is {}",uMap);
                int i = tvmOrderMapper.updateByOrderNo(uMap);
                log.info("修改结束 i is {}",i);
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


        // 查询原支付订单 说明：如果设备发起出票结果通知，说明该订单已经支付成功；所以不需要校验原订单状态，但防止设备传输了错误的订单号，所以要判断订单号是否存在
        TvmPayOrder order = tvmOrderMapper.selectByOrderNo(request.getOrderNo());
        if (ObjectUtils.isEmpty(order)) {
            log.info("2.没有找到匹配的订单，orderNo={}", request.getOrderNo());
            return TvmOrderResult.fail(TvmPayCodeEnum.ORDER_NO_ERROR.getCode(), "订单号错误,没有找到匹配的订单");
        }


        int buyNum = order.getTicketNum();
        int actualNum = Integer.parseInt(request.getActualTakeTicketNum());

        // 保存出票主记录
        TvmMainTicket mainTicket = new TvmMainTicket();
        mainTicket.setId(tvmMainTicketMapper.getTvmMainTicketSeq());
        mainTicket.setOrderNo(request.getOrderNo());
        mainTicket.setActualTakeTicketNum(actualNum);
        mainTicket.setBuyTicketNum(buyNum);
        mainTicket.setTakeTickeDate(request.getTakeTickeDate());
        mainTicket.setNotifyType("0"); // 0-出票结果通知
        mainTicket.setCreateTime(DateUtils.getNowTime());
        log.info("3.开始保存出票主记录, mainTicket={}", mainTicket);
        tvmMainTicketMapper.insert(mainTicket);

        log.info("request.getTicketList() is {}",request.getTicketList());
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

        // 如果购票数量大于实际出票数量，发起退款
        int refundAmount = handleRefund(order, buyNum, actualNum);
        log.info("5.出票结果通知处理完成, orderNo={}, buyNum={}, actualNum={}, refundAmount={}",
                request.getOrderNo(), buyNum, actualNum, refundAmount);

        return TvmOrderResult.success();
    }

    @Override
    public JSONObject notiTakeTicketFailResult(NotiTakeTicketFailResultReqDTO request) {
        log.info("1.开始处理出票故障通知, deviceId={}, request={}", request.getDeviceId(), request);

        // 查询原支付订单
        TvmPayOrder order = tvmOrderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            log.info("2.没有找到匹配的订单，orderNo={}", request.getOrderNo());
            return TvmOrderResult.fail(TvmPayCodeEnum.ORDER_NO_ERROR.getCode(), "订单号错误,没有找到匹配的订单");
        }

        int buyNum = order.getTicketNum();
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

        // 如果购票数量大于实际出票数量，发起退款 退款时发送的是支付中心的订单号
        int refundAmount = handleRefund(order, buyNum, actualNum);
        log.info("5.出票故障通知处理完成, orderNo={}, buyNum={}, actualNum={}, refundAmount={}",
                request.getOrderNo(), buyNum, actualNum, refundAmount);

        return TvmOrderResult.success();
    }



    /**
     * 处理退款逻辑。
     * 如果购票数量大于实际出票数量，发起退款并返回退款金额。
     *
     * @param order     原支付订单
     * @param buyNum    购票数量
     * @param actualNum 实际出票数量
     * @return 退款金额（分），未退款返回0
     */
    private int handleRefund(TvmPayOrder order, int buyNum, int actualNum) {
        log.info("开始退款处理");
        if (order != null && buyNum > actualNum) {
            int refundNum = buyNum - actualNum;
            BigDecimal price = new BigDecimal(order.getTicketPrice());
            BigDecimal refundAmount = price.multiply(new BigDecimal(refundNum));
            log.info("购票数量大于实际出票数量，发起退款, orderNo={}, buyNum={}, actualNum={}, refundAmount={}",
                    order.getOrderNo(), buyNum, actualNum, refundAmount);

            doRefund(order, refundAmount.intValue());
            return refundAmount.intValue();
        }
        return 0;
    }

    /**
     * 发起退款。
     * 根据支付中心退款接口规范，保存退款记录到tbl_refund_order，
     * 并将退款单号更新到原支付订单的rsv2字段。
     */
    private void doRefund(TvmPayOrder order, int refundAmount) {
        try {

            String payOrderNo = order.getOrderNo();

            // 1. 生成退款单号
            String refundNo = "RF" + LocalDateTime.now().format(DATE_FORMATTER) + UUID.randomUUID().toString().substring(0, 6);

            PayCenterRequest payCenterRefundRequest = payCenterCommon.getRefundRequest(refundNo, order.getOrderNo(), order.getPayCenterOrderNo(), refundAmount);

            log.info("退款开始 payCenterRefundRequest is {}",payCenterRefundRequest);

            // 4. 调用支付中心退款接口
            PayCenterResponse payCenterResponse = payCenterService.callPayCenter(payCenterProperties.getPayCenterRefundUrl(), payCenterRefundRequest);

            log.info("退款结束 payCenterResponse is {}",payCenterResponse);
            // 5. 保存退款记录
            RefundOrder refundOrder = new RefundOrder();
            refundOrder.setRefundNo(refundNo);
            refundOrder.setPayOrderNo(payOrderNo);
            refundOrder.setRefundAmount(refundAmount);
            refundOrder.setRefundReason(environment.getProperty("pay.center.refundReason"));
            refundOrder.setCreateTime(DateUtils.getNowTime());

            if (payCenterResponse != null && org.apache.commons.lang3.StringUtils.equals(payCenterResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {
                // 退款成功
                Map<String, Object> data = payCenterResponse.getData();
                refundOrder.setMerchantRefundNo(getStringFromData(data, "merchantRefundNo"));
                refundOrder.setChannelRefundNo(getStringFromData(data, "channelRefundNo"));

                String refundTime = getStringFromData(data, "refundTime");
                // 如果不为空，将格式转为yyyy-MM-dd HH:mm:ss
                if(!StringUtils.isEmpty(refundTime)){
                    refundTime = getRefundTime(refundTime);
                }
                refundOrder.setRefundTime(refundTime);
                refundOrder.setRefundStatus(ItpStatusEnum.REFUND_SUCCESS.getCode()); // 1-退款成功
                refundOrder.setRefundMsg(ItpStatusEnum.REFUND_SUCCESS.getDesc()); // 1-退款成功
                log.info("退款成功, payOrderNo={}, refundNo={}, refundAmount={}", payOrderNo, refundNo, refundAmount);
            } else {
                // 退款失败
                String errorMsg = payCenterResponse != null ? payCenterResponse.getMsg() : "调用支付中心退款失败";
                refundOrder.setRefundStatus(ItpStatusEnum.REFUNDING_FAIL.getCode()); // 2-退款失败
                refundOrder.setRefundMsg(ItpStatusEnum.REFUNDING_FAIL.getDesc()); // 2-退款失败
                log.error("退款失败, payOrderNo={}, refundNo={}, errorMsg={}", payOrderNo, refundNo, errorMsg);
            }
            refundOrderMapper.insert(refundOrder);

            // 6. 更新原支付订单的rsv2字段（退款记录ID），不修改原支付状态
            Map<String,String> upMap = new HashMap<>();
            upMap.put("orderNo",order.getOrderNo());
            upMap.put("rsv2",refundNo);
            upMap.put("updateTime",DateUtils.getNowTime());
            tvmOrderMapper.updateByOrderNo(upMap);

        } catch (Exception e) {
            log.error("发起退款异常, payOrderNo={}", order.getOrderNo(), e);
        }
    }

    public static String getRefundTime(String refundTime) throws ParseException {

        Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(refundTime);

        String result = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);

        return result;
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
        return "00" + LocalDateTime.now().format(DATE_FORMATTER) + UUID.randomUUID().toString().substring(0, 8);
    }

    private String getStringFromData(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
}
