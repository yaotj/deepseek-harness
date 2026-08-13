package com.chinasofti.huateng.alipay.paysign.service.impl;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayLog;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayLogMapper;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripPayQueryReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripPayQueryRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.alipay.paysign.util.PayCenterClient;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
public class PaymentQueryService {
    private static final Logger log = LoggerFactory.getLogger(PaymentQueryService.class);

    @Autowired
    private AlipayPayLogMapper alipayPayLogMapper;

    @Autowired
    private PayCenterClient payCenterClient;

    public AlipayTripPayQueryRespDTO payQuery(AlipayTripPayQueryReqDTO request) {
        AlipayTripPayQueryRespDTO response = new AlipayTripPayQueryRespDTO();
        log.info("收到支付查询: orderNo={}", request != null ? request.getOrderNo() : null);

        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "订单号不能为空");
        }
        AlipayPayLog payLog = alipayPayLogMapper.selectByOrderNo(request.getOrderNo());
        if (payLog == null) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "订单不存在");
        }

        Map<String, Object> bizDataMap = new java.util.LinkedHashMap<>();
        bizDataMap.put("orderNo", request.getOrderNo());
        bizDataMap.put("cardIssueCode", "0007");
        if (StringUtils.hasText(payLog.getCardId())) {
            bizDataMap.put("cardNum", payLog.getCardId());
        }

        String channelAgreementNo = request.getChannelAgreementNo();
        if (!StringUtils.hasText(channelAgreementNo) && StringUtils.hasText(payLog.getIndustryDetail())) {
            try {
                JSONObject detailJson = JSON.parseObject(payLog.getIndustryDetail());
                channelAgreementNo = detailJson.getString("channelAgreementNo");
                log.info("支付宝出行-支付结果查询,从industryDetail解析channelAgreementNo={}", channelAgreementNo);
            } catch (Exception e) {
                log.warn("支付宝出行-支付结果查询,解析industryDetail失败, industryDetail={}", payLog.getIndustryDetail(), e);
            }
        }
        if (StringUtils.hasText(channelAgreementNo)) {
            bizDataMap.put("channelAgreementNo", channelAgreementNo);
        }

        log.info("支付宝出行-支付结果查询,调用支付中心查询接口,请求参数: {}", JSON.toJSONString(bizDataMap));
        com.chinasofti.huateng.alipay.paysign.model.response.PayCenterResponse payCenterResponse = payCenterClient.payQuery(bizDataMap);
        log.info("支付宝出行-支付结果查询,支付中心响应结果: code={}, msg={}, success={}, responseBody={}",
                payCenterResponse != null ? payCenterResponse.getCode() : "null",
                payCenterResponse != null ? payCenterResponse.getMsg() : "null",
                payCenterResponse != null ? payCenterResponse.getSuccess() : "null",
                payCenterResponse != null ? JSON.toJSONString(payCenterResponse) : "null");

        String payStatus = payLog.getPayStatus();
        String tradeNo = payLog.getTradeNo();
        String transTime = payLog.getTransTime();
        String payAmount = payLog.getPayAmount();
        String resultMsg = payLog.getResultMsg();

        if (payCenterResponse != null && (payCenterResponse.getCode() != null && payCenterResponse.getCode() == 200 || Boolean.TRUE.equals(payCenterResponse.getSuccess()))) {
            String centerTradeNo = payCenterClient.getStringFromData(payCenterResponse, "tradeNo");
            String centerPayAmount = payCenterClient.getStringFromData(payCenterResponse, "totalAmount");
            String centerTransTime = payCenterClient.getStringFromData(payCenterResponse, "paymentTime");
            String centerTransStatus = payCenterClient.getStringFromData(payCenterResponse, "tradeStatus");
            String dataRetCode = payCenterClient.getStringFromData(payCenterResponse, "retCode");
            if (dataRetCode == null) {
                dataRetCode = payCenterClient.getStringFromData(payCenterResponse, "returnCode");
            }
            String dataRetMsg = payCenterClient.getStringFromData(payCenterResponse, "retMsg");
            if (dataRetMsg == null) {
                dataRetMsg = payCenterClient.getStringFromData(payCenterResponse, "returnMsg");
            }

            log.info("支付宝出行-支付结果查询,支付中心解密后数据: retCode={}, retMsg={}, tradeNo={}, totalAmount={}, paymentTime={}, tradeStatus={}",
                    dataRetCode, dataRetMsg, centerTradeNo, centerPayAmount, centerTransTime, centerTransStatus);

            if (StringUtils.hasText(centerTradeNo)) {
                tradeNo = centerTradeNo;
            }
            if (StringUtils.hasText(centerPayAmount)) {
                payAmount = centerPayAmount;
            }
            if (StringUtils.hasText(centerTransTime)) {
                transTime = centerTransTime;
            }

            if ("SUCCESS".equals(dataRetCode)) {
                payStatus = "SUCCESS";
                resultMsg = StringUtils.hasText(dataRetMsg) ? dataRetMsg : "支付成功";
            } else {
                payStatus = "FAIL";
                resultMsg = StringUtils.hasText(dataRetMsg) ? dataRetMsg : "支付失败";
            }

            alipayPayLogMapper.updatePayNotify(
                    payLog.getOrderNo(),
                    payStatus,
                    tradeNo,
                    transTime,
                    payAmount,
                    FepAppErrorCodeEnum.SUCCESS.getCode(),
                    resultMsg
            );
            log.info("支付宝出行-支付结果查询,本地支付流水更新成功, orderNo={}, payStatus={}, tradeNo={}, payAmount={}", request.getOrderNo(), payStatus, tradeNo, payAmount);
        } else if (payCenterResponse != null) {
            resultMsg = payCenterResponse.getMsg() != null ? payCenterResponse.getMsg() : "支付失败";
            payStatus = "FAIL";
            alipayPayLogMapper.updatePayNotify(
                    payLog.getOrderNo(),
                    payStatus,
                    tradeNo,
                    transTime,
                    payAmount,
                    FepAppErrorCodeEnum.FAIL.getCode(),
                    resultMsg
            );
            log.info("支付宝出行-支付结果查询,本地支付流水更新失败, orderNo={}, payStatus={}, resultMsg={}", request.getOrderNo(), payStatus, resultMsg);
        } else {
            resultMsg = "调用支付中心失败";
            payStatus = "FAIL";
            alipayPayLogMapper.updatePayNotify(
                    payLog.getOrderNo(),
                    payStatus,
                    tradeNo,
                    transTime,
                    payAmount,
                    FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(),
                    resultMsg
            );
            log.warn("支付宝出行-支付结果查询,支付中心响应为空, orderNo={}, payStatus={}", request.getOrderNo(), payStatus);
        }

        payLog = alipayPayLogMapper.selectByOrderNo(request.getOrderNo());
        log.info("支付宝出行-支付结果查询,更新后支付流水: orderNo={}, payStatus={}, tradeNo={}", request.getOrderNo(), payLog.getPayStatus(), payLog.getTradeNo());

        response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("查询成功");
        response.setOutTradeNo(payLog.getOrderNo());
        response.setTradeStatus(payLog.getPayStatus());
        response.setTotalAmount(payLog.getPayAmount());
        response.setTradeNo(payLog.getTradeNo());
        response.setTradeDesc(payLog.getResultMsg());
        response.setPaymentTime(payLog.getTransTime());
        log.info("支付宝出行-支付结果查询,查询结果: {}", JSON.toJSONString(response));
        return response;
    }

    public AlipayCommonResponse handlePayNotify(AlipayTripPayNotifyReqDTO request) {
        log.info("接收到支付宝支付结果回调, 原始报文: {}", JSON.toJSONString(request));
        AlipayCommonResponse response = new AlipayCommonResponse();
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            log.warn("支付宝支付回调报文为空或订单号为空");
            response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("参数异常：orderNo不能为空");
            return response;
        }

        AlipayPayLog payLog = alipayPayLogMapper.selectByOrderNo(request.getOrderNo());
        if (payLog == null) {
            log.warn("支付回调未找到对应订单, orderNo={}", request.getOrderNo());
            response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
            response.setRetMsg("未查询到支付流水");
            return response;
        }

        // 幂等性检查：如果已经是成功，直接返回
        String currentStatus = payLog.getPayStatus();
        if ("SUCCESS".equals(currentStatus)) {
            log.info("支付宝支付回调,订单已是成功,跳过更新, orderNo={}", request.getOrderNo());
            response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg("成功");
            return response;
        }

        String payStatus = "1".equals(request.getTransStatus()) ? "SUCCESS" : "FAIL";
        String resultCode = "1".equals(request.getTransStatus()) ? FepAppErrorCodeEnum.SUCCESS.getCode() : FepAppErrorCodeEnum.FAIL.getCode();
        String resultMsg = "1".equals(request.getTransStatus()) ? "支付成功" : "支付失败";
        String tradeNo = StringUtils.hasText(request.getChannelVoucherId()) ? request.getChannelVoucherId() : payLog.getTradeNo();
        String transTime = request.getTransTime();
        String payAmount = StringUtils.hasText(request.getTransAmount()) ? request.getTransAmount() : payLog.getPayAmount();

        log.info("支付宝支付回调,准备更新本地流水, orderNo={}, payStatus={}, tradeNo={}, transTime={}, payAmount={}, resultCode={}, resultMsg={}",
                request.getOrderNo(), payStatus, tradeNo, transTime, payAmount, resultCode, resultMsg);

        try {
            alipayPayLogMapper.updatePayNotify(
                    payLog.getOrderNo(),
                    payStatus,
                    tradeNo,
                    transTime,
                    payAmount,
                    resultCode,
                    resultMsg
            );
            log.info("支付宝支付回调,本地流水更新成功, orderNo={}, payStatus={}", request.getOrderNo(), payStatus);
        } catch (Exception e) {
            log.error("支付宝支付回调,本地流水更新失败, orderNo={}", request.getOrderNo(), e);
            response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
            response.setRetMsg("系统内部错误");
            return response;
        }

        log.info("支付宝支付回调处理完成, orderNo={}, payStatus={}", request.getOrderNo(), payStatus);
        response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("成功");
        return response;
    }

    public AlipayTripFindTravelDetailRespDTO findTravelDetail(AlipayTripFindTravelDetailReqDTO request) {
        AlipayTripFindTravelDetailRespDTO response = new AlipayTripFindTravelDetailRespDTO();
        log.info("查询乘车记录详情: orderNo={}", request != null ? request.getOrderNo() : null);

        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "订单号不能为空");
        }
        AlipayPayLog payLog = alipayPayLogMapper.selectByOrderNo(request.getOrderNo());
        if (payLog == null) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "未查询到支付流水");
        }

        response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("查询成功");
        response.setPayOrderNoDate(payLog.getTransTime());
        response.setPayChannelCode("ALIPAY");
        response.setTradeOrderNo(payLog.getTradeNo());
        response.setTotalAmount(payLog.getPayAmount());
        response.setDebitRequestResult(payLog.getResultMsg());
        log.info("查询乘车记录详情响应结果：{}", JSON.toJSONString(response));
        return response;
    }
}
