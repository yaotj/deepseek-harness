package com.chinasofti.huateng.alipay.paysign.service.impl;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayLog;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayLogMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.PayCenterResponse;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.alipay.paysign.util.PayCenterClient;
import com.chinasofti.huateng.alipay.paysign.config.PayCenterProperties;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.model.app.AddBlackListReqDTO;
import com.chinasofti.huateng.model.app.BlackListOperateResult;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
public class PaymentRequestService {
    private static final Logger log = LoggerFactory.getLogger(PaymentRequestService.class);
    private static final String CHANNEL_ALIPAY = "ALIPAY";

    @Autowired
    private AlipaySignInfoMapper alipaySignInfoMapper;

    @Autowired
    private AlipayPayLogMapper alipayPayLogMapper;

    @Autowired
    private PayCenterClient payCenterClient;

    @Autowired
    private PayCenterProperties payCenterProperties;

    @Autowired
    private com.chinasofti.huateng.alipay.paysign.service.impl.PaymentNotifyAdapter paymentNotifyAdapter;

    @Autowired
    private IndustryDetailEnricher industryDetailEnricher;

    @Autowired
    private PayLogBuilder payLogBuilder;

    @Autowired
    private BizDataBuilder bizDataBuilder;

    @Autowired
    private BlacklistClient blacklistClient;

    public AlipayTripRequestPayRespDTO requestPay(AlipayTripRequestPayReqDTO request) {
        AlipayTripRequestPayRespDTO response = new AlipayTripRequestPayRespDTO();
        log.info("接收到支付宝支付申请报文: {}", JSON.toJSONString(request));

        if (request == null || !isValidPayRequest(request)) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "订单号/支付金额/行业类型/订单标题/订单描述/行业详情不能为空");
        }

//        if (request.getRequestSignSeq() == null || request.getRequestSignSeq().trim().isEmpty()) {
//            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "免密场景签约流水号不能为空");
//        }

        AlipaySignInfo signInfo = alipaySignInfoMapper.selectByThirdUserIdAndChannel(request.getThirdUserId(), CHANNEL_ALIPAY);
        if (signInfo == null) {
            throw new BusinessException(FepAppErrorCodeEnum.USER_NOT_SIGNED.getCode(), "用户未签约");
        }

        AlipayPayLog existPayLog = alipayPayLogMapper.selectByOrderNo(request.getOrderNo());
        if (existPayLog != null) {
            log.info("支付订单已存在，直接返回, orderNo={}, payStatus={}", request.getOrderNo(), existPayLog.getPayStatus());
            response.setRetCode(existPayLog.getPayStatus().equals("SUCCESS") ? FepAppErrorCodeEnum.SUCCESS.getCode() : FepAppErrorCodeEnum.FAIL.getCode());
            response.setRetMsg(existPayLog.getResultMsg());
            response.setOrderNo(existPayLog.getOrderNo());
            return response;
        }

        request.setRequestSignSeq(signInfo.getAgreementCode());

        request.setIndustryDetail(industryDetailEnricher.enrich(request.getIndustryDetail(), signInfo.getThirdUserId()));
        log.info("支付宝支付申请,行业详情 enrichment 完成, orderNo={}", request.getOrderNo());

        AlipayPayLog payLog = payLogBuilder.build(request, signInfo);
        alipayPayLogMapper.insert(payLog);
        log.info("支付宝支付申请,支付日志插入完成, orderNo={}, paySeq={}", request.getOrderNo(), payLog.getPaySeq());

        Map<String, Object> bizDataMap = bizDataBuilder.build(request, signInfo, payLog, payCenterProperties);

        log.info("支付宝支付申请,调用支付中心支付接口,请求参数: {}", JSON.toJSONString(bizDataMap));
        PayCenterResponse payCenterResponse = payCenterClient.requestPay(bizDataMap);
        log.info("支付宝支付申请,支付中心响应结果: success={}, msg={}", payCenterResponse != null ? payCenterResponse.getSuccess() : "null", payCenterResponse != null ? payCenterResponse.getMsg() : "null");

        boolean paySuccess = false;
        String tradeNo = "";
        String resultCode = FepAppErrorCodeEnum.SYSTEM_ERROR.getCode();
        String resultMsg = "系统内部错误";

        if (payCenterResponse != null && (payCenterResponse.getCode() != null && payCenterResponse.getCode() == 200 || Boolean.TRUE.equals(payCenterResponse.getSuccess()))) {
            String dataRetCode = payCenterClient.getStringFromData(payCenterResponse, "retCode");
            if (dataRetCode == null) {
                dataRetCode = payCenterClient.getStringFromData(payCenterResponse, "returnCode");
            }
            String dataRetMsg = payCenterClient.getStringFromData(payCenterResponse, "retMsg");
            if (dataRetMsg == null) {
                dataRetMsg = payCenterClient.getStringFromData(payCenterResponse, "returnMsg");
            }

            log.info("支付宝支付申请,支付中心解密后数据: retCode={}, retMsg={}", dataRetCode, dataRetMsg);

            if ("SUCCESS".equals(dataRetCode)) {
                paySuccess = true;
                resultCode = FepAppErrorCodeEnum.SUCCESS.getCode();
                resultMsg = StringUtils.hasText(dataRetMsg) ? dataRetMsg : "支付成功";
                tradeNo = payCenterClient.getStringFromData(payCenterResponse, "channelOrderNo");
            } else {
                resultCode = FepAppErrorCodeEnum.FAIL.getCode();
                resultMsg = StringUtils.hasText(dataRetMsg) ? dataRetMsg : "支付失败";
                addBlackListIfNeeded(signInfo, resultMsg);
            }
        } else if (payCenterResponse != null) {
            resultCode = FepAppErrorCodeEnum.FAIL.getCode();
            resultMsg = payCenterResponse.getMsg() != null ? payCenterResponse.getMsg() : "支付失败";
            addBlackListIfNeeded(signInfo, resultMsg);
        } else {
            resultCode = FepAppErrorCodeEnum.SYSTEM_ERROR.getCode();
            resultMsg = "调用支付中心失败";
        }

        payLog.setTradeNo(tradeNo);
        payLog.setPayStatus(paySuccess ? "SUCCESS" : "FAIL");
        payLog.setResponseBody(JSON.toJSONString(payCenterResponse));
        payLog.setResultCode(resultCode);
        payLog.setResultMsg(resultMsg);
        alipayPayLogMapper.updatePayStatus(payLog.getPaySeq(), payLog.getPayStatus());

        response.setRetCode(resultCode);
        response.setRetMsg(resultMsg);
        response.setOrderNo(request.getOrderNo());
        log.info("支付宝支付申请完成, orderNo={}, tradeNo={}, status={}", request.getOrderNo(), tradeNo, paySuccess ? "SUCCESS" : "FAIL");
        return response;
    }

    private boolean isValidPayRequest(AlipayTripRequestPayReqDTO request) {
        return request.getOrderNo() != null && !request.getOrderNo().trim().isEmpty()
                && request.getAmount() != null
                && request.getIndustryType() != null && !request.getIndustryType().trim().isEmpty()
                && request.getSubject() != null && !request.getSubject().trim().isEmpty()
                && request.getBody() != null && !request.getBody().trim().isEmpty()
                && request.getIndustryDetail() != null && !request.getIndustryDetail().trim().isEmpty();
    }

    private void addBlackListIfNeeded(AlipaySignInfo signInfo, String reason) {
        if (signInfo == null || !StringUtils.hasText(signInfo.getCardId()) || !StringUtils.hasText(signInfo.getThirdUserId())) {
            return;
        }
        try {
            AddBlackListReqDTO blackListRequest = new AddBlackListReqDTO();
            blackListRequest.setCardId(signInfo.getCardId());
            blackListRequest.setThirdUserId(signInfo.getThirdUserId());
            blackListRequest.setCardType(signInfo.getCardType());
            blackListRequest.setReason(StringUtils.hasText(reason) ? reason : "地铁扣款失败");
            log.info("支付宝支付申请失败,添加黑名单, cardId={}, thirdUserId={}, cardType={}, reason={}",
                    blackListRequest.getCardId(), blackListRequest.getThirdUserId(), blackListRequest.getCardType(), blackListRequest.getReason());
            BlackListOperateResult blackListResult = blacklistClient.addBlackList(blackListRequest);
            log.info("支付宝支付申请失败,添加黑名单完成, cardId={}, retCode={}, retMsg={}",
                    blackListRequest.getCardId(), blackListResult != null ? blackListResult.getRetCode() : "null", blackListResult != null ? blackListResult.getRetMsg() : "null");
        } catch (Exception e) {
            log.error("支付宝支付申请失败,添加黑名单异常, cardId={}", signInfo.getCardId(), e);
        }
    }
}
