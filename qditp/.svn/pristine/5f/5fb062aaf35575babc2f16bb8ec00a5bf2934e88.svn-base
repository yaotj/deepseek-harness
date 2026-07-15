package com.chinasofti.huateng.collectpay.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.collectpay.common.DeviceResponse;
import com.chinasofti.huateng.collectpay.common.PayCenterCommon;
import com.chinasofti.huateng.collectpay.common.UpdateDbMap;
import com.chinasofti.huateng.collectpay.config.PayCenterProperties;
import com.chinasofti.huateng.collectpay.constant.*;
import com.chinasofti.huateng.collectpay.entity.RefundOrder;
import com.chinasofti.huateng.collectpay.entity.TvmPayOrder;
import com.chinasofti.huateng.collectpay.entity.TvmTopupOrder;
import com.chinasofti.huateng.collectpay.mapper.RefundOrderMapper;
import com.chinasofti.huateng.collectpay.mapper.TvmOrderPreMapper;
import com.chinasofti.huateng.collectpay.mapper.TvmTopupOrderMapper;
import com.chinasofti.huateng.collectpay.model.request.PayCenterRequest;
import com.chinasofti.huateng.collectpay.model.request.RequestPayReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.RequestPayResultReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.RequestTopupReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.TopupCardFailNotiReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.TopupCardResultNotiReqDTO;
import com.chinasofti.huateng.collectpay.model.response.PayCenterResponse;
import com.chinasofti.huateng.collectpay.model.response.tvm.TvmOrderResult;
import com.chinasofti.huateng.collectpay.service.PayCenterService;
import com.chinasofti.huateng.collectpay.service.TvmTopupService;
import com.chinasofti.huateng.collectpay.utils.DateUtils;
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
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * TVM扫码充值服务实现。
 */
@Slf4j
@Service
public class TvmTopupServiceImpl implements TvmTopupService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DB_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long DEFAULT_ORDER_TIMEOUT = 180;

    @Autowired
    private PayCenterProperties payCenterProperties;

    @Autowired
    private TvmTopupOrderMapper tvmTopupOrderMapper;
    @Autowired
    private TvmOrderPreMapper tvmOrderPreMapper;

    @Autowired
    private RefundOrderMapper refundOrderMapper;

    @Autowired
    private PayCenterCommon payCenterCommon;
    @Autowired
    private PayCenterService payCenterService;
    @Autowired
    private SignUtils signUtils;

    @Override
    public JSONObject requestTopup(RequestTopupReqDTO request) {
        log.info("1.开始处理请求充值下单, deviceId={}, request={}", request.getDeviceId(), request);

        // 生成订单号（充值订单以08开头）
        String orderNo = "08" + LocalDateTime.now().format(DATE_FORMATTER) + UUID.randomUUID().toString().substring(0, 6);
        log.info("2.生成充值订单号, orderNo={}", orderNo);

        // 创建充值订单
        TvmTopupOrder order = buildTopupOrder(orderNo, request);

        // 保存支付订单前置信息
        tvmOrderPreMapper.insert(getTvmOrderPre(order, request.getDeviceId()));
        tvmTopupOrderMapper.insert(order);
        log.info("3.保存充值订单到数据库, orderNo={}", orderNo);

        // 非数币渠道直接返回
        if(request.getPayType().equals("0")){
            // todo 签名
            String sign = "sign-test";
            String payUrl = "http://dtcustomer.bestonepay.com/ngpayment-gateway/api/v1/checkoutCounter"+"&orderNo="+orderNo+"&sign="+sign;
            return TvmOrderResult.successData(DeviceResponse.getLaMaSuccessRespose(orderNo, payUrl));
        }

        // 调用支付中心预下单
        String payTopupUrl = payCenterProperties.getPayCenterPayUrl();
        PayCenterRequest payTopupRequest = payCenterCommon.buildTvmPayRequest(order.getOrderNo(),order.getTransAmount(),order.getPayType(),"地铁票卡充值","地铁票卡充值");
        log.info("4.充值请求 payTopupUrl is {} , payCenterRequest is {}",payTopupUrl,payTopupRequest);
        PayCenterResponse payTopupResponse = payCenterService.callPayCenter(payTopupUrl, payTopupRequest);

        // 默认为失败
        JSONObject result = TvmOrderResult.fail();
        Map<String, String> uMap = UpdateDbMap.getTopupUpdateFailDb(orderNo,request.getBeforeAmount());

        if (ObjectUtils.isEmpty(payTopupResponse)) {
            log.info("6.支付中心返回结果为空,结束");
        }else {
        if (StringUtils.equals(payTopupResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {
            Map<String, Object> data = payTopupResponse.getData();
            String payUrl = getStringFromData(data, "data");
            String payCenterOrderNo = getStringFromData(data, "orderNo");
            String payCenterChannelOrderNo = getStringFromData(data, "channelOrderNo");
            log.info("4.支付中心预下单成功, payUrl={}", payUrl);

            uMap = UpdateDbMap.getLaMaUpdateSuccessDb(orderNo, payCenterOrderNo,payCenterChannelOrderNo,payUrl);
            result = TvmOrderResult.successData(DeviceResponse.getTopupSuccessRespose(orderNo, payUrl));
        } else {
            log.info("7.支付中心返回业务数据失败");
        }
    }

        log.info("9.开始修改记录 uMap is {}",uMap);
        int i = tvmTopupOrderMapper.updateByOrderNo(uMap);
        log.info("10.修改结束 i is {}",i);

        return result;
    }

    private Map<String,Object> getTvmOrderPre(TvmTopupOrder order ,String deviceId ){
        Map<String,Object> preMap = new HashMap<>();
        preMap.put("orderNo",order.getOrderNo());
        preMap.put("transAmount",order.getTransAmount());
        preMap.put("deviceId",deviceId);
        // 01-扫码购票  02-扫码充值
        preMap.put("transType","02");
        preMap.put("createTime",DateUtils.getNowTime());
        preMap.put("updateTime","");
        return preMap;
    }

//    private PayCenterRequest buildTopupPayRequest(TvmTopupOrder order) {
//
//        // 公共参数
//        PayCenterRequest payCenterRequest = new PayCenterRequest();
//        payCenterRequest.setMerchantNo(payCenterProperties.getMerchantNo());
//        payCenterRequest.setApiVersion(payCenterProperties.getApiVersion());
//        payCenterRequest.setSignType(payCenterProperties.getSignType());
//        payCenterRequest.setCharset(payCenterProperties.getCharset());
//
//        // 业务参数
//        RequestPayReqDTO payReqDTO = new RequestPayReqDTO();
//        payReqDTO.setOrderNo(order.getOrderNo());
//        payReqDTO.setScene("qrcode");
//        payReqDTO.setPaymentVendor("0C");
//        payReqDTO.setPayType(order.getPayType());
//        int totalAmount = Integer.parseInt(order.getTransAmount());
//        payReqDTO.setAmount(totalAmount);
//        payReqDTO.setIndustryType("1");
//        payReqDTO.setSubject("票卡充值");
//        payReqDTO.setBody("地铁票卡充值" );
//        payReqDTO.setOrderTimeOut(DEFAULT_ORDER_TIMEOUT);
//        payCenterRequest.setBizData(Base64.getEncoder().encodeToString(JSON.toJSONString(payReqDTO).getBytes(StandardCharsets.UTF_8)));
//
//        // 签名
//        String sign = signUtils.signRequest(payCenterRequest);
//        payCenterRequest.setSign(sign);
//        return payCenterRequest;
//    }

    @Override
    public JSONObject requestPayResult(RequestPayResultReqDTO request) {

        log.info("1.开始处理查询充值支付结果, deviceId={}, request={}", request.getDeviceId(), request);
        JSONObject result = new JSONObject();

        TvmTopupOrder payTopupOrderInfo = tvmTopupOrderMapper.selectByOrderNo(request.getOrderNo());
        if (payTopupOrderInfo == null) {
            return TvmOrderResult.fail(TvmPayCodeEnum.INVALID_PARAM.getCode(), "没有找到匹配的订单，请确认订单号是否正确");
        }

        // 如果已经是成功，直接返回
        if (ItpStatusEnum.SUCCESS.getCode().equals(payTopupOrderInfo.getStatus())) {
            log.info("2.数据库查询结果为支付成功，直接返回");
            return TvmOrderResult.successData(DeviceResponse.getPaySuccessResult(payTopupOrderInfo.getChannel()));
        }

        // 如果已经是失败，直接返回
        if (ItpStatusEnum.FAILED.getCode().equals(payTopupOrderInfo.getStatus())) {
            log.info("2.数据库查询结果为支付失败，直接返回");
            return TvmOrderResult.failData(DeviceResponse.getPayFailResult(payTopupOrderInfo.getChannel()));
        }
        // 如果已经是未支付，直接返回 未支付也是终态，轮询结束后没有支付则认为未支付
        if (ItpStatusEnum.UNPAID.getCode().equals(payTopupOrderInfo.getStatus())) {
            log.info("2.数据库查询结果为未支付，直接返回");
            return TvmOrderResult.failData(DeviceResponse.getPayFailResult(payTopupOrderInfo.getChannel()));
        }

        String tvmQueryUrl = payCenterProperties.getPayCenterQueryUrl();
        // 订单为已下单状态，需要向支付中心查询实际支付结果
        PayCenterRequest queryPayRequest = payCenterCommon.buildQueryPayCenterRequest(payTopupOrderInfo.getOrderNo());

        log.info("3.tvmQueryUrl is {} , queryPayRequest is {}",tvmQueryUrl,queryPayRequest);
        PayCenterResponse queryPayResponse = payCenterService.callPayCenter(tvmQueryUrl, queryPayRequest);

        log.info("queryPayResponse is {}", queryPayResponse);

        if (ObjectUtils.isEmpty(queryPayResponse)) {
            log.info("6.支付中心返回结果为空,结束");
        }else {

            if (StringUtils.equals(queryPayResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {

                Map<String, String> uMap = UpdateDbMap.getTopupUpdateFailDb(payTopupOrderInfo.getOrderNo(),payTopupOrderInfo.getBeforeAmount());
                Map<String, Object> data = queryPayResponse.getData();
                if (data != null) {
                    String status = getStringFromData(data, "status");
                    String channelOrderNo = getStringFromData(data, "channelOrderNo");
                    String paymentChannelCode = getStringFromData(data, "paymentVendor");

                    // 查询支付中心支付状态为支付成功
                    if (PayCenterStatusEnum.SUCCESS.getCode().equals(status)) {

                        log.info("查询到支付成功的结果");
                        String aftAmount = String.valueOf(new BigDecimal(payTopupOrderInfo.getBeforeAmount()).add(new BigDecimal(payTopupOrderInfo.getTransAmount())));

                        uMap = UpdateDbMap.getTopupUpdateSuccessDb(payTopupOrderInfo.getOrderNo(), channelOrderNo,aftAmount);
                        result = TvmOrderResult.successData(DeviceResponse.getPaySuccessResult(paymentChannelCode));

                    } else if (PayCenterStatusEnum.FAILED.getCode().equals(status)) {
                        log.info("查询到支付失败的结果");
                        result = TvmOrderResult.successData(DeviceResponse.getPayFailResult(paymentChannelCode));
                    } else {
                        log.info("查询到不明确的结果，按已下单-支付中处理");
                        result = TvmOrderResult.successData(DeviceResponse.getPayIngResult(paymentChannelCode));
                    }
                    log.info("开始修改记录 uMap is {}",uMap);
                    int i = tvmTopupOrderMapper.updateByOrderNo(uMap);
                    log.info("修改结束 i is {}",i);
                    return result;
                }
            }

        }
        // 没有查询到支付结果，或结果为空，全部按照支付中-已下单返回
        return TvmOrderResult.successData(DeviceResponse.getPayIngResult(payTopupOrderInfo.getChannel()));
    }

    @Override
    public JSONObject topupCardResultNoti(TopupCardResultNotiReqDTO request) {
        log.info("1.开始处理充值结果通知, deviceId={}, request={}", request.getDeviceId(), request);


        // 查询原充值订单
        TvmTopupOrder order = tvmTopupOrderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            log.info("2.没有找到匹配的充值订单，orderNo={}", request.getOrderNo());
            return TvmOrderResult.fail(TvmPayCodeEnum.ORDER_NO_ERROR.getCode(), TvmPayCodeEnum.ORDER_NO_ERROR.getMsg());
        }

        Map<String, String> map = getNotiy(request);

        tvmTopupOrderMapper.insertNotiy(map);

        log.info("3.充值结果通知处理完成, orderNo={}", request.getOrderNo());
        return TvmOrderResult.success();
    }

    private Map<String,String> getNotiy(TopupCardResultNotiReqDTO  request){
        Map<String,String> map = new HashMap<>();
        map.put("orderNo",request.getOrderNo());
        map.put("ticketLogicNum",request.getTicketLogicNum());
        map.put("ticketPhysicsNum",request.getTicketPhysicsNum());
        map.put("transDate",request.getTransDate());
        map.put("transAmount",request.getTransAmount());
        map.put("afterAmount",request.getAfterAmount());
        map.put("transType","01");
        map.put("createTime", DateUtils.getNowTime());
        return map;
    }



    @Override
    public JSONObject topupCardFailNoti(TopupCardFailNotiReqDTO request) {
        log.info("1.开始处理充值失败通知, deviceId={}, request={}", request.getDeviceId(), request);

        // 查询原充值订单
        TvmTopupOrder order = tvmTopupOrderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            log.info("2.没有找到匹配的充值订单，orderNo={}", request.getOrderNo());
            return TvmOrderResult.fail(TvmPayCodeEnum.ORDER_NO_ERROR.getCode(), TvmPayCodeEnum.ORDER_NO_ERROR.getMsg());
        }

        Map<String, String> map = getFailNotiy(request);

        tvmTopupOrderMapper.insertFailtNotiy(map);

        // 如果充值失败，发起退款
        String topupStatus = request.getTopupStatus();
        // todo 02: 存疑 03: 取消是否要退款
        if ("01".equals(topupStatus) ) {
            log.info("3.充值失败，发起退款, orderNo={}", request.getOrderNo());
            int refundAmount = Integer.parseInt(order.getTransAmount());
            doRefund(request.getOrderNo(), refundAmount);
        }

        log.info("4.充值失败通知处理完成, orderNo={}", request.getOrderNo());
        return TvmOrderResult.success();
    }

    private Map<String,String> getFailNotiy(TopupCardFailNotiReqDTO  request){
        Map<String,String> map = new HashMap<>();
        map.put("orderNo",request.getOrderNo());
        map.put("ticketLogicNum",request.getTicketLogicNum());
        map.put("ticketPhysicsNum",request.getTicketPhysicsNum());
        map.put("topupStatus",request.getTopupStatus());
        map.put("faultOccurDate",request.getFaultOccurDate());
        map.put("faultSlipSeq",request.getFaultSlipSeq());
        map.put("errorCode",request.getErrorCode());
        map.put("errorMessage",request.getErrorMessage());
        map.put("transType","02");
        map.put("createTime", DateUtils.getNowTime());
        return map;
    }

    /**
     * 构建充值订单实体。
     */
    private TvmTopupOrder buildTopupOrder(String orderNo,  RequestTopupReqDTO request) {
        TvmTopupOrder order = new TvmTopupOrder();
        order.setOrderNo(orderNo);
        order.setDeviceId(request.getDeviceId());
        order.setTicketLogicNum(request.getTicketLogicNum());
        order.setTicketPhysicsNum(request.getTicketPhysicsNum());
        order.setBeforeAmount(request.getBeforeAmount());
        order.setTransAmount(request.getTransAmount());
        order.setStatus(ItpStatusEnum.PAYING.getCode());
        order.setMsg(ItpStatusEnum.PAYING.getDesc());
        order.setPayType(request.getPayType());
        order.setCreateTime(LocalDateTime.now().format(DB_DATE_FORMATTER));
        order.setUpdateTime(LocalDateTime.now().format(DB_DATE_FORMATTER));
        return order;
    }

    /**
     * 构建支付中心充值请求。
     */
    private PayCenterRequest buildTopupPayCenterRequest(TvmTopupOrder order) {
        PayCenterRequest payCenterRequest = new PayCenterRequest();
        payCenterRequest.setMerchantNo(payCenterProperties.getMerchantNo());
        payCenterRequest.setApiVersion(payCenterProperties.getApiVersion());
        payCenterRequest.setSignType(payCenterProperties.getSignType());
        payCenterRequest.setCharset(payCenterProperties.getCharset());

        Map<String, Object> bizDataMap = new LinkedHashMap<>();
        bizDataMap.put("orderNo", order.getOrderNo());
        bizDataMap.put("scene", "TOPUP");
        bizDataMap.put("paymentVendor", "QR");
        int totalAmount = Integer.parseInt(order.getTransAmount());
        bizDataMap.put("amount", totalAmount);
        bizDataMap.put("industryType", "1");
        bizDataMap.put("subject", "票卡充值");
        bizDataMap.put("body", "地铁票卡充值");
        bizDataMap.put("orderTimeOut", DEFAULT_ORDER_TIMEOUT);

        payCenterRequest.setBizData(JSON.toJSONString(bizDataMap));
        signRequest(payCenterRequest);
        return payCenterRequest;
    }

    /**
     * 发起退款。
     */
    private void doRefund(String orderNo, int refundAmount) {
        try {
            TvmTopupOrder order = tvmTopupOrderMapper.selectByOrderNo(orderNo);
            if (order == null) {
                log.error("发起退款失败，原订单不存在, orderNo={}", orderNo);
                return;
            }

            String refundNo = "RF" + LocalDateTime.now().format(DATE_FORMATTER) + UUID.randomUUID().toString().substring(0, 6);

            PayCenterRequest refundRequest = payCenterCommon.getRefundRequest(refundNo, order.getOrderNo(), order.getPayCenterOrderNo(), refundAmount);

            log.info("发起退款 refundRequest is {}",refundRequest);
            PayCenterResponse payCenterResponse = payCenterService.callPayCenter(payCenterProperties.getPayCenterRefundUrl(), refundRequest);

            RefundOrder refundOrder = new RefundOrder();
            refundOrder.setRefundNo(refundNo);
            refundOrder.setPayOrderNo(orderNo);
            refundOrder.setRefundAmount(refundAmount);
            refundOrder.setRefundReason("充值失败");
            refundOrder.setCreateTime(DateUtils.getNowTime());

            if (payCenterResponse != null && StringUtils.equals(payCenterResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {
                Map<String, Object> data = payCenterResponse.getData();
                refundOrder.setMerchantRefundNo(getStringFromData(data, "merchantRefundNo"));
                refundOrder.setChannelRefundNo(getStringFromData(data, "channelRefundNo"));
                refundOrder.setRefundTime(getStringFromData(data, "refundTime"));
                refundOrder.setRefundStatus(ItpStatusEnum.REFUND_SUCCESS.getCode()); // 1-退款成功
                refundOrder.setRefundMsg(ItpStatusEnum.REFUND_SUCCESS.getDesc()); // 1-退款成功
                log.info("充值退款成功, orderNo={}, refundNo={}", orderNo, refundNo);
            } else {
                String errorMsg = payCenterResponse != null ? payCenterResponse.getMsg() : "调用支付中心退款失败";
                refundOrder.setRefundStatus(ItpStatusEnum.REFUNDING_FAIL.getCode()); // 2-退款失败
                refundOrder.setRefundMsg(ItpStatusEnum.REFUNDING_FAIL.getDesc()); // 2-
                log.error("充值退款失败, orderNo={}, errorMsg={}", orderNo, errorMsg);
            }
            refundOrderMapper.insert(refundOrder);

            Map<String, String> updateMap = new LinkedHashMap<>();
            updateMap.put("orderNo", orderNo);
            updateMap.put("rsv2", refundNo);
            tvmTopupOrderMapper.updateByOrderNo(updateMap);

        } catch (Exception e) {
            log.error("发起退款异常, orderNo={}", orderNo, e);
        }
    }

    /**
     * 调用支付中心接口。
     */
    private PayCenterResponse callPayCenter(String path, PayCenterRequest request) {
        try {
            OkHttpClient client = new OkHttpClient();
            MediaType mediaType = MediaType.parse("application/json;charset=UTF-8");
            String jsonStr = JSON.toJSONString(request);
            RequestBody body = RequestBody.create(jsonStr, mediaType);

            Request httpRequest = new Request.Builder()
                    .url(payCenterProperties.getGatewayUrl() + path)
                    .post(body)
                    .build();

            Response response = client.newCall(httpRequest).execute();
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                return JSON.parseObject(responseBody, PayCenterResponse.class);
            }
        } catch (IOException e) {
            log.error("调用支付中心接口异常, path={}", path, e);
        }
        return null;
    }

    /**
     * 签名请求。
     */
    private void signRequest(PayCenterRequest request) {
        try {
            String privateKeyStr = payCenterProperties.getPrivateKey();
            byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyStr);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

            StringBuilder sb = new StringBuilder();
            Map<String, String> params = new TreeMap<>();
            params.put("merchantNo", request.getMerchantNo());
            params.put("apiVersion", request.getApiVersion());
            params.put("signType", request.getSignType());
            params.put("charset", request.getCharset());
            params.put("bizData", request.getBizData());

            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (StringUtils.isNotEmpty(entry.getValue())) {
                    if (sb.length() > 0) {
                        sb.append("&");
                    }
                    sb.append(entry.getKey()).append("=").append(entry.getValue());
                }
            }

            Signature signature = Signature.getInstance("SHA1withRSA");
            signature.initSign(privateKey);
            signature.update(sb.toString().getBytes(StandardCharsets.UTF_8));
            byte[] signBytes = signature.sign();
            request.setSign(Base64.getEncoder().encodeToString(signBytes));

        } catch (Exception e) {
            log.error("签名请求异常", e);
        }
    }

    /**
     * 从Map中获取字符串值。
     */
    private String getStringFromData(Map<String, Object> data, String key) {
        if (data == null || !data.containsKey(key)) {
            return null;
        }
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
}
