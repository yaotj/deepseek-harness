package com.chinasofti.huateng.fep.alipay.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestIndustryDataReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestIndustryDataRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import com.chinasofti.huateng.model.app.IndustryCardDataBuildReqDTO;
import com.chinasofti.huateng.model.app.IndustryCardDataBuildRespDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusReqDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusRespDTO;
import com.chinasofti.huateng.fep.alipay.service.AlipayApplicationService;
import com.chinasofti.huateng.rpc.alipay.account.AlipayAccountClient;
import com.chinasofti.huateng.rpc.industry.IndustryDataClient;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

/**
 * 支付宝出行开卡/行业数据服务实现。
 */
@Service
public class AlipayApplicationServiceImpl implements AlipayApplicationService {
    private static final Logger log = LoggerFactory.getLogger(AlipayApplicationServiceImpl.class);

    private final AlipayAccountClient alipayAccountClient;
    private final TicketClient ticketClient;
    private final IndustryDataClient industryDataClient;

    @Autowired
    public AlipayApplicationServiceImpl(AlipayAccountClient alipayAccountClient,
                                        TicketClient ticketClient,
                                        IndustryDataClient industryDataClient) {
        this.alipayAccountClient = alipayAccountClient;
        this.ticketClient = ticketClient;
        this.industryDataClient = industryDataClient;
    }

    @Override
    public AlipayTripRequestApplicationRespDTO requestApplication(AlipayTripRequestApplicationReqDTO request) {
        log.info("支付宝出行-开卡申请,请求参数：{}", JSON.toJSONString(request));
        AlipayTripRequestApplicationRespDTO response = new AlipayTripRequestApplicationRespDTO();
        if (request == null || !StringUtils.hasText(request.getThirdUserId()) || !StringUtils.hasText(request.getCardType())) {
            response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("无效的参数");
            log.warn("支付宝出行-开卡申请,参数校验失败");
            return response;
        }
        try {
            response = alipayAccountClient.alipayTripRequestApplication(request);
            if (response == null) {
                response = new AlipayTripRequestApplicationRespDTO();
                response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
                response.setRetMsg("系统内部错误");
                log.error("支付宝出行-开卡申请,alipay-account-server 返回空响应");
                return response;
            }
        } catch (Exception e) {
            log.error("支付宝出行-开卡申请 异常", e);
            response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
            response.setRetMsg("系统内部错误");
        }
        log.info("支付宝出行-开卡申请,响应结果：{}", JSON.toJSONString(response));
        return response;
    }

    @Override
    public AlipayTripRequestIndustryDataRespDTO requestIndustryData(AlipayTripRequestIndustryDataReqDTO request) {
        log.info("支付宝出行-获取行业数据,请求参数：{}", JSON.toJSONString(request));
        AlipayTripRequestIndustryDataRespDTO response = new AlipayTripRequestIndustryDataRespDTO();
        response.setSignType("00");
        response.setSign("");
        try {
            String validMsg = validateRequest(request);
            if (validMsg != null) {
                response.setRetCode("1001");
                response.setRetMsg(validMsg);
                log.warn("支付宝出行-获取行业数据参数校验失败, request={}, msg={}", JSON.toJSONString(request), validMsg);
                return response;
            }

            AlipayUserInfoDTO userInfo = queryUserInfo(request);
            if (userInfo == null) {
                response.setRetCode("9999");
                response.setRetMsg("用户未开户");
                log.warn("支付宝出行-获取行业数据用户未开户, request={}", JSON.toJSONString(request));
                return response;
            }
            if (userInfo.getChannel() == null) {
                response.setRetCode("8001");
                response.setRetMsg("用户签约渠道不能为空");
                log.warn("支付宝出行-获取行业数据用户签约渠道为空, userInfo={}", JSON.toJSONString(userInfo));
                return response;
            }
            if (!request.getCardId().equals(userInfo.getCardId())) {
                response.setRetCode("8002");
                response.setRetMsg("卡号不匹配");
                log.warn("支付宝出行-获取行业数据卡号不匹配, requestCardId={}, accountCardId={}", request.getCardId(), userInfo.getCardId());
                return response;
            }

            String signChannelCode = resolveSignChannelCode(userInfo.getChannel());
            if (!StringUtils.hasText(signChannelCode)) {
                response.setRetCode("8001");
                response.setRetMsg("用户签约渠道不能为空");
                log.warn("支付宝出行-获取行业数据用户签约渠道为空, userInfo={}", JSON.toJSONString(userInfo));
                return response;
            }

            QueryStatusRespDTO qrStatus = queryTicketStatus(request);
            if (qrStatus == null) {
                response.setRetCode("9999");
                response.setRetMsg("ticket-server调用失败，返回为空");
                log.warn("支付宝出行-获取行业数据查询二维码状态返回null, request={}", JSON.toJSONString(request));
                return response;
            }
            if (!"0000".equals(qrStatus.getRetCode())) {
                response.setRetCode(qrStatus.getRetCode());
                response.setRetMsg(qrStatus.getRetMsg());
                return response;
            }

            IndustryCardDataBuildReqDTO cardDataRequest = buildCardDataRequest(request, qrStatus, signChannelCode);
            log.info("支付宝出行-获取行业数据调用industry-data-server生成卡数据, request={}", JSON.toJSONString(cardDataRequest));
            IndustryCardDataBuildRespDTO cardDataResp = industryDataClient.buildCardData(cardDataRequest);
            log.info("支付宝出行-获取行业数据调用industry-data-server生成卡数据完成, response={}", JSON.toJSONString(cardDataResp));
            if (cardDataResp == null || !"0000".equals(cardDataResp.getRetCode())) {
                response.setRetCode(cardDataResp == null ? "9999" : cardDataResp.getRetCode());
                response.setRetMsg(cardDataResp == null ? "industry-data-server生成卡数据失败" : cardDataResp.getRetMsg());
                return response;
            }

            response.setRetCode("0000");
            response.setRetMsg("成功");
            response.setCardData(cardDataResp.getCardData());
            log.info("支付宝出行-获取行业数据成功, thirdUserId={}, cardId={}, cardType={}, cardData={}",
                    request.getThirdUserId(), request.getCardId(), request.getCardType(), cardDataResp.getCardData());
            return response;
        } catch (Exception e) {
            log.error("支付宝出行-获取行业数据异常, request={}", JSON.toJSONString(request), e);
            response.setRetCode("9999");
            response.setRetMsg("系统内部错误");
            return response;
        }
    }

    private AlipayUserInfoDTO queryUserInfo(AlipayTripRequestIndustryDataReqDTO request) {
        AlipayUserInfoDTO userInfo = alipayAccountClient.selectByThirdUserId(request.getThirdUserId().trim());
        log.info("支付宝出行-获取行业数据查询用户信息结果, thirdUserId={}, response={}", request.getThirdUserId().trim(), JSON.toJSONString(userInfo));
        return userInfo;
    }

    private QueryStatusRespDTO queryTicketStatus(AlipayTripRequestIndustryDataReqDTO request) {
        QueryStatusReqDTO qrReq = new QueryStatusReqDTO();
        qrReq.setThirdUserId(request.getThirdUserId().trim());
        qrReq.setCardId(request.getCardId().trim());
        QueryStatusRespDTO qrStatus = ticketClient.queryQrCodeStatus(qrReq);
        log.info("支付宝出行-获取行业数据查询二维码状态结果, request={}, response={}", JSON.toJSONString(qrReq), JSON.toJSONString(qrStatus));
        return qrStatus;
    }

    private String resolveSignChannelCode(String channel) {
        if (!StringUtils.hasText(channel)) {
            return null;
        }
        String normalized = channel.trim();
        return normalized.length() >= 2 ? normalized.substring(0, 2) : normalized;
    }

    private String nextTxnSeq(String txnSeq) {
        if (!StringUtils.hasText(txnSeq)) {
            return "1";
        }
        try {
            return new BigInteger(txnSeq.trim()).add(BigInteger.ONE).toString();
        } catch (NumberFormatException e) {
            log.warn("交易序列号不是数字，使用1作为下一序列号, txnSeq={}", txnSeq);
            return "1";
        }
    }

    private IndustryCardDataBuildReqDTO buildCardDataRequest(AlipayTripRequestIndustryDataReqDTO request,
                                                             QueryStatusRespDTO qrStatus,
                                                             String signChannelCode) {
        IndustryCardDataBuildReqDTO cardDataRequest = new IndustryCardDataBuildReqDTO();
        cardDataRequest.setThirdUserId(request.getThirdUserId().trim());
        cardDataRequest.setCardId(request.getCardId().trim());
        cardDataRequest.setCardType(request.getCardType().trim());
        cardDataRequest.setTicketStatus(qrStatus.getStatus());
        cardDataRequest.setLastTxnStation(qrStatus.getLastTxnStation());
        cardDataRequest.setLastTxnTime(qrStatus.getLastTxnTime());
        cardDataRequest.setGateInStation(qrStatus.getGateInStation());
        cardDataRequest.setGateInTime(qrStatus.getGateInTime());
        cardDataRequest.setTxnSeq(nextTxnSeq(qrStatus.getTxnSeq()));
        cardDataRequest.setIssueChannelCode("07");
        cardDataRequest.setSignChannelCode("07");
        return cardDataRequest;
    }

    private String validateRequest(AlipayTripRequestIndustryDataReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(request.getCardId())) {
            return "cardId不能为空";
        }
        if (!StringUtils.hasText(request.getCardType())) {
            return "cardType不能为空";
        }
        return null;
    }
}
