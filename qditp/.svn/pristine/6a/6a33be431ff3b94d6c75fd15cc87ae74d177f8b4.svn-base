package com.chinasofti.huateng.collectpay.common;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.collectpay.config.PayCenterProperties;
import com.chinasofti.huateng.collectpay.entity.BomNoCashOrder;
import com.chinasofti.huateng.collectpay.entity.TvmPayOrder;
import com.chinasofti.huateng.collectpay.model.request.PayCenterRequest;
import com.chinasofti.huateng.collectpay.model.request.RequestPayReqDTO;
import com.chinasofti.huateng.collectpay.model.request.bom.RequestPaymentReqDTO;
import com.chinasofti.huateng.collectpay.utils.SignUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;


@Component
@Slf4j
public class PayCenterCommon {

    private static final Long DEFAULT_ORDER_TIMEOUT = 180L;
    @Autowired
    PayCenterProperties payCenterProperties;
    @Autowired
    SignUtils signUtils;
    @Autowired
    Environment environment;


    // tvm 发起支付请求参数
    public PayCenterRequest buildTvmPayRequest(String ordeNo,String amt,String payType,String subject,String body) {

        // 公共参数
        PayCenterRequest payCenterRequest = new PayCenterRequest();
        payCenterRequest.setMerchantNo(payCenterProperties.getMerchantNo());
        payCenterRequest.setApiVersion(payCenterProperties.getApiVersion());
        payCenterRequest.setSignType(payCenterProperties.getSignType());
        payCenterRequest.setCharset(payCenterProperties.getCharset());

        // 业务参数
        RequestPayReqDTO payReqDTO = new RequestPayReqDTO();
        payReqDTO.setOrderNo(ordeNo);
        payReqDTO.setScene("qrcode");
        payReqDTO.setPaymentVendor("0C");
        payReqDTO.setPayType(payType);
        int totalAmount = Integer.parseInt(amt);
        payReqDTO.setAmount(totalAmount);
        payReqDTO.setIndustryType("1");
        payReqDTO.setSubject(subject);
        payReqDTO.setBody(body);
        payReqDTO.setOrderTimeOut(DEFAULT_ORDER_TIMEOUT);
        log.info("payReqDTO is {}",payReqDTO);
        payCenterRequest.setBizData(Base64.getEncoder().encodeToString(JSON.toJSONString(payReqDTO).getBytes(StandardCharsets.UTF_8)));

        // 签名
        String sign = signUtils.signRequest(payCenterRequest);
        payCenterRequest.setSign(sign);
        return payCenterRequest;
    }

    // bom 发起支付请求参数
    public PayCenterRequest buildBomPayRequest(String ordeNo,String amt,String subject,String body,String paymentCode,String authCode) {

        // 公共参数
        PayCenterRequest payCenterRequest = new PayCenterRequest();
        payCenterRequest.setMerchantNo(payCenterProperties.getMerchantNo());
        payCenterRequest.setApiVersion(payCenterProperties.getApiVersion());
        payCenterRequest.setSignType(payCenterProperties.getSignType());
        payCenterRequest.setCharset(payCenterProperties.getCharset());

        // 业务参数
        RequestPayReqDTO payReqDTO = new RequestPayReqDTO();
        payReqDTO.setOrderNo(ordeNo);
        payReqDTO.setScene("scan");
        payReqDTO.setPaymentVendor(paymentCode);
        int totalAmount = Integer.parseInt(amt);
        payReqDTO.setAmount(totalAmount);
        payReqDTO.setIndustryType("1");
        payReqDTO.setSubject(subject);
        payReqDTO.setBody(body);
        payReqDTO.setOrderTimeOut(DEFAULT_ORDER_TIMEOUT);
        payReqDTO.setAuthCode(authCode);
        log.info("payReqDTO is {}",payReqDTO);
        payCenterRequest.setBizData(Base64.getEncoder().encodeToString(JSON.toJSONString(payReqDTO).getBytes(StandardCharsets.UTF_8)));

        // 签名
        String sign = signUtils.signRequest(payCenterRequest);
        payCenterRequest.setSign(sign);
        return payCenterRequest;
    }


    // 查询支付中心参数
    public PayCenterRequest buildQueryPayCenterRequest(String orderNo) {
        PayCenterRequest payCenterRequest = new PayCenterRequest();
        payCenterRequest.setMerchantNo(payCenterProperties.getMerchantNo());
        payCenterRequest.setApiVersion(payCenterProperties.getApiVersion());
        payCenterRequest.setSignType(payCenterProperties.getSignType());
        payCenterRequest.setCharset(payCenterProperties.getCharset());

        Map<String, Object> bizDataMap = new LinkedHashMap<>();
        bizDataMap.put("merchantOrderNo", orderNo);
        payCenterRequest.setBizData(Base64.getEncoder().encodeToString(JSON.toJSONString(bizDataMap).getBytes(StandardCharsets.UTF_8)));

        // 签名
        String sign = signUtils.signRequest(payCenterRequest);
        payCenterRequest.setSign(sign);

        return payCenterRequest;
    }


public PayCenterRequest getRefundRequest(String refundNo,String orderNo,String payCenterOrderNo,int refundAmount){
    // 2. 构建支付中心退款请求
    PayCenterRequest payCenterRequest = new PayCenterRequest();
    payCenterRequest.setMerchantNo(payCenterProperties.getMerchantNo());
    payCenterRequest.setApiVersion(payCenterProperties.getApiVersion());
    payCenterRequest.setSignType(payCenterProperties.getSignType());
    payCenterRequest.setCharset(payCenterProperties.getCharset());

    Map<String, Object> bizDataMap = new LinkedHashMap<>();
    bizDataMap.put("refundOrderNo", refundNo);
    bizDataMap.put("merchantOrderNo", orderNo);
//    bizDataMap.put("orderNo", order.getPayCenterOrderNo());
    bizDataMap.put("orderNo", payCenterOrderNo);
    bizDataMap.put("refundAmount", refundAmount);
    bizDataMap.put("refundReason", environment.getProperty("pay.center.refundReason"));

    payCenterRequest.setBizData(Base64.getEncoder().encodeToString(JSON.toJSONString(bizDataMap).getBytes(StandardCharsets.UTF_8)));

    String sign = signUtils.signRequest(payCenterRequest);
    payCenterRequest.setSign(sign);
    return payCenterRequest;
}





}
