package com.chinasofti.huateng.alipay.paysign.service.impl.payment;

import com.chinasofti.huateng.alipay.paysign.service.impl.support.BizDataBuilder;
import com.chinasofti.huateng.alipay.paysign.service.impl.support.IndustryDetailEnricher;

import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.alipay.paysign.config.PayCenterProperties;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.port.BlacklistPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
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
    private PayCenterPort payCenterPort;

    @Autowired
    private PayCenterProperties payCenterProperties;

    @Autowired
    private com.chinasofti.huateng.alipay.paysign.service.impl.notify.PaymentNotifyAdapter paymentNotifyAdapter;

    @Autowired
    private IndustryDetailEnricher industryDetailEnricher;

    @Autowired
    private BizDataBuilder bizDataBuilder;

    @Autowired
    private BlacklistPort blacklistPort;

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
        PayCenterReply reply = payCenterPort.requestPay(bizDataMap);
        log.info("支付宝支付申请,支付中心响应结果: success={}, msg={}", reply.success(), reply.msg());

        boolean paySuccess = false;
        String tradeNo = "";
        String resultCode = FepAppErrorCodeEnum.SYSTEM_ERROR.getCode();
        String resultMsg = "系统内部错误";

        switch (reply) {
            case PayCenterReply.Accepted accepted -> {
                log.info("支付宝支付申请,支付中心解密后数据: retCode={}, retMsg={}", accepted.retCode(), accepted.retMsg());
                if ("SUCCESS".equals(accepted.retCode())) {
                    paySuccess = true;
                    resultCode = FepAppErrorCodeEnum.SUCCESS.getCode();
                    resultMsg = StringUtils.hasText(accepted.retMsg()) ? accepted.retMsg() : "支付成功";
                    tradeNo = accepted.field("channelOrderNo");
                } else {
                    resultCode = FepAppErrorCodeEnum.FAIL.getCode();
                    resultMsg = StringUtils.hasText(accepted.retMsg()) ? accepted.retMsg() : "支付失败";
                    addBlackListIfNeeded(signInfo, resultMsg);
                }
            }
            case PayCenterReply.Rejected rejected -> {
                resultCode = FepAppErrorCodeEnum.FAIL.getCode();
                resultMsg = rejected.msg() != null ? rejected.msg() : "支付失败";
                log.error("支付宝支付申请未拿到业务应答，按传输层失败处理、NEVER 加黑名单, orderNo={}, code={}, msg={}, success={}",
                        request.getOrderNo(), rejected.code(), rejected.msg(), rejected.success());
            }
            case PayCenterReply.NoAnswer noAnswer -> {
                resultCode = FepAppErrorCodeEnum.SYSTEM_ERROR.getCode();
                resultMsg = "调用支付中心失败";
                log.error("支付宝支付申请调用支付中心无响应，按传输层失败处理、NEVER 加黑名单, orderNo={}", request.getOrderNo());
            }
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

    /**
     * 扣款失败后把该卡加入黑名单。
     *
     * <p>三态判读收口在 {@link BlacklistPort} 的 adapter（ADR-D131）。本方法的处置**逐字保留现状**：
     * 三种结果都只打日志、不落库、不重试 —— 这意味着**加黑失败即永久丢失，该卡仍可过闸**。
     * 要补偿得先有载体表（本模块目前没有），列进批次 5 的待办，NEVER 在这里加静默重试。</p>
     */
    private void addBlackListIfNeeded(AlipaySignInfo signInfo, String reason) {
        if (signInfo == null || !StringUtils.hasText(signInfo.getCardId()) || !StringUtils.hasText(signInfo.getThirdUserId())) {
            return;
        }
        String cardId = signInfo.getCardId();
        String thirdUserId = signInfo.getThirdUserId();
        String blackReason = StringUtils.hasText(reason) ? reason : "地铁扣款失败";
        log.info("支付宝支付申请失败,添加黑名单, cardId={}, thirdUserId={}, cardType={}, reason={}",
                cardId, thirdUserId, signInfo.getCardType(), blackReason);
        RpcOutcome outcome = blacklistPort.addBlackList(cardId, thirdUserId, signInfo.getCardType(), blackReason);
        switch (outcome) {
            case RpcOutcome.Ok ok -> log.info("支付宝支付申请失败,添加黑名单完成, cardId={}", cardId);
            case RpcOutcome.BizRejected rejected -> log.error(
                    "添加黑名单被业务拒绝，重试无意义、该卡仍可过闸、MUST 人工核对, cardId={}, thirdUserId={}, retCode={}, retMsg={}",
                    cardId, thirdUserId, rejected.retCode(), rejected.retMsg());
            case RpcOutcome.Unreachable unreachable -> log.error(
                    "添加黑名单未获答复，该卡仍可过闸、MUST 人工核对, cardId={}, thirdUserId={}, cause={}",
                    cardId, thirdUserId, unreachable.cause().getClass().getSimpleName(), unreachable.cause());
        }
    }
}
