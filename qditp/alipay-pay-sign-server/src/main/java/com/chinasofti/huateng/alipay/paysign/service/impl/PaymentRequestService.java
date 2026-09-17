package com.chinasofti.huateng.alipay.paysign.service.impl;

import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
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
    private PayCenterClient payCenterClient;

    @Autowired
    private PayCenterProperties payCenterProperties;

    @Autowired
    private com.chinasofti.huateng.alipay.paysign.service.impl.PaymentNotifyAdapter paymentNotifyAdapter;

    @Autowired
    private IndustryDetailEnricher industryDetailEnricher;

    @Autowired
    private BizDataBuilder bizDataBuilder;

    @Autowired
    private BlacklistClient blacklistClient;

    /** 支付宝出行扣费申请。 */
    public AlipayTripRequestPayRespDTO requestPay(AlipayTripRequestPayReqDTO request) {
        AlipayTripRequestPayRespDTO response = new AlipayTripRequestPayRespDTO();
        log.info("接收到支付宝支付申请报文: {}", JSON.toJSONString(request));

        if (request == null || !isValidPayRequest(request)) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "订单号/支付金额/行业类型/订单标题/订单描述/行业详情不能为空");
        }

//        if (request.getRequestSignSeq() == null || request.getRequestSignSeq().trim().isEmpty()) {

        AlipaySignInfo signInfo = alipaySignInfoMapper.selectByThirdUserIdAndChannel(request.getThirdUserId(), CHANNEL_ALIPAY);
        if (signInfo == null) {
            throw new BusinessException(FepAppErrorCodeEnum.USER_NOT_SIGNED.getCode(), "用户未签约");
        }

        request.setRequestSignSeq(signInfo.getAgreementCode());

        request.setIndustryDetail(industryDetailEnricher.enrich(request.getIndustryDetail(), signInfo.getThirdUserId()));
        log.info("支付宝支付申请,行业详情 enrichment 完成, orderNo={}", request.getOrderNo());

        Map<String, Object> bizDataMap = bizDataBuilder.build(request, signInfo, payCenterProperties);

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
            log.error("支付宝支付申请未拿到业务应答，按传输层失败处理、NEVER 加黑名单, orderNo={}, code={}, msg={}, success={}",
                    request.getOrderNo(), payCenterResponse.getCode(), payCenterResponse.getMsg(), payCenterResponse.getSuccess());
        } else {
            resultCode = FepAppErrorCodeEnum.SYSTEM_ERROR.getCode();
            resultMsg = "调用支付中心失败";
            log.error("支付宝支付申请调用支付中心无响应，按传输层失败处理、NEVER 加黑名单, orderNo={}", request.getOrderNo());
        }

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

    /** 扣款失败后把该卡加入黑名单。 */
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
            if (blackListResult == null || !FepAppErrorCodeEnum.SUCCESS.getCode().equals(blackListResult.getRetCode())) {
                log.error("添加黑名单未成功，该卡仍可过闸、MUST 人工核对, cardId={}, thirdUserId={}, retCode={}, retMsg={}",
                        blackListRequest.getCardId(), blackListRequest.getThirdUserId(),
                        blackListResult != null ? blackListResult.getRetCode() : "null",
                        blackListResult != null ? blackListResult.getRetMsg() : "null");
                return;
            }
            log.info("支付宝支付申请失败,添加黑名单完成, cardId={}, retCode={}, retMsg={}",
                    blackListRequest.getCardId(), blackListResult.getRetCode(), blackListResult.getRetMsg());
        } catch (Exception e) {
            log.error("支付宝支付申请失败,添加黑名单异常, cardId={}", signInfo.getCardId(), e);
        }
    }
}
