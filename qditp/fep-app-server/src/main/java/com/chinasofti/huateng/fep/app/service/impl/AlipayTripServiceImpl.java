package com.chinasofti.huateng.fep.app.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.fep.app.service.AlipayTripService;
import com.chinasofti.huateng.model.alipaytrip.*;
import com.chinasofti.huateng.model.app.IndustryCardDataBuildReqDTO;
import com.chinasofti.huateng.model.app.IndustryCardDataBuildRespDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoReqDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.ticket.QueryStatusReqDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusRespDTO;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import com.chinasofti.huateng.rpc.industry.IndustryDataClient;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 支付宝出行专属接口服务默认实现。
 * <p>
 * 通过现有 RPC 客户端调用后端服务。
 * </p>
 */
@Service
public class AlipayTripServiceImpl implements AlipayTripService {
    private static final Logger log = LoggerFactory.getLogger(AlipayTripServiceImpl.class);

    @Autowired
    private PaySignClient paySignClient;

    @Autowired
    private TicketClient ticketClient;

    @Autowired
    private BlacklistClient blacklistClient;

    @Autowired
    private AccountClient accountClient;

    @Autowired
    private IndustryDataClient industryDataClient;

    @Override
    public AlipayTripAddContractRespDTO addContract(AlipayTripAddContractReqDTO request) {
        log.info("支付宝出行-添加签约信息,请求参数：{}", JSON.toJSONString(request));
        AlipayTripAddContractRespDTO response;
        try {
            response = paySignClient.alipayTripAddContract(request);
        } catch (Exception e) {
            log.error("支付宝出行-添加签约信息 异常", e);
            response = new AlipayTripAddContractRespDTO();
            response.setRetCode("9999");
            response.setRetMsg("系统内部错误");
        }
        log.info("支付宝出行-添加签约信息,响应结果：{}", JSON.toJSONString(response));
        return response;
    }

    @Override
    public AlipayTripTerminateContractRespDTO terminateContract(AlipayTripTerminateContractReqDTO request) {
        log.info("支付宝出行-解约登记,请求参数：{}", JSON.toJSONString(request));
        AlipayTripTerminateContractRespDTO response;
        try {
            response = paySignClient.alipayTripTerminateContract(request);
        } catch (Exception e) {
            log.error("支付宝出行-解约登记 异常", e);
            response = new AlipayTripTerminateContractRespDTO();
            response.setRetCode("9999");
            response.setRetMsg("系统内部错误");
        }
        log.info("支付宝出行-解约登记,响应结果：{}", JSON.toJSONString(response));
        return response;
    }

    @Override
    public AlipayTripRequestApplicationRespDTO requestApplication(AlipayTripRequestApplicationReqDTO request) {
        log.info("支付宝出行-开卡申请,请求参数：{}", JSON.toJSONString(request));
        AlipayTripRequestApplicationRespDTO response;
        try {
            response = accountClient.alipayTripRequestApplication(request);
        } catch (Exception e) {
            log.error("支付宝出行-开卡申请 异常", e);
            response = new AlipayTripRequestApplicationRespDTO();
            response.setRetCode("9999");
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

            QueryUserInfoResult userInfo = queryUserInfo(request);
            if (userInfo == null) {
                response.setRetCode("9999");
                response.setRetMsg("account-server调用失败，返回为空");
                log.warn("支付宝出行-获取行业数据查询用户信息返回null, request={}", JSON.toJSONString(request));
                return response;
            }
            if (!"0000".equals(userInfo.getRetCode())) {
                response.setRetCode(userInfo.getRetCode());
                response.setRetMsg(userInfo.getRetMsg());
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

    private QueryUserInfoResult queryUserInfo(AlipayTripRequestIndustryDataReqDTO request) {
        QueryUserInfoReqDTO userInfoReq = new QueryUserInfoReqDTO();
        userInfoReq.setThirdUserId(request.getThirdUserId().trim());
        userInfoReq.setCardId(request.getCardId().trim());
        userInfoReq.setCardType(request.getCardType().trim());
        QueryUserInfoResult userInfo = accountClient.queryUserInfo(userInfoReq);
        log.info("支付宝出行-获取行业数据查询用户信息结果, request={}, response={}", JSON.toJSONString(userInfoReq), JSON.toJSONString(userInfo));
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
        cardDataRequest.setTxnSeq(qrStatus.getTxnSeq());
        cardDataRequest.setIssueChannelCode("07");
        cardDataRequest.setSignChannelCode(signChannelCode);
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

    @Override
    public AlipayTripFindTravelListRespDTO findTravelList(AlipayTripFindTravelListReqDTO request) {
        log.info("支付宝出行-查询乘车记录,请求参数：{}", JSON.toJSONString(request));
        AlipayTripFindTravelListRespDTO response;
        try {
            response = ticketClient.alipayTripFindTravelList(request);
        } catch (Exception e) {
            log.error("支付宝出行-查询乘车记录 异常", e);
            response = new AlipayTripFindTravelListRespDTO();
            response.setRetCode("9999");
            response.setRetMsg("系统内部错误");
        }
        log.info("支付宝出行-查询乘车记录,响应结果：{}", JSON.toJSONString(response));
        return response;
    }

    @Override
    public AlipayTripFindTravelDetailRespDTO findTravelDetail(AlipayTripFindTravelDetailReqDTO request) {
        log.info("支付宝出行-查询乘车记录详情,请求参数：{}", JSON.toJSONString(request));
        AlipayTripFindTravelDetailRespDTO response;
        try {
            response = ticketClient.alipayTripFindTravelDetail(request);
        } catch (Exception e) {
            log.error("支付宝出行-查询乘车记录详情 异常", e);
            response = new AlipayTripFindTravelDetailRespDTO();
            response.setRetCode("9999");
            response.setRetMsg("系统内部错误");
        }
        log.info("支付宝出行-查询乘车记录详情,响应结果：{}", JSON.toJSONString(response));
        return response;
    }

    @Override
    public AlipayTripCloseResultRespDTO closeResultForAlipay(AlipayTripCloseResultReqDTO request) {
        log.info("支付宝出行-业务关闭结果通知,请求参数：{}", JSON.toJSONString(request));
        AlipayTripCloseResultRespDTO response = new AlipayTripCloseResultRespDTO();
        try {
            // TODO: 业务关闭结果通知需后续对接 pay-sign-server 解约结果处理
            response.setRetCode("0000");
            response.setRetMsg("成功");
        } catch (Exception e) {
            log.error("支付宝出行-业务关闭结果通知 异常", e);
            response.setRetCode("9999");
            response.setRetMsg("系统内部错误");
        }
        log.info("支付宝出行-业务关闭结果通知,响应结果：{}", JSON.toJSONString(response));
        return response;
    }

    @Override
    public AlipayTripPushTransDataRespDTO pushTransData(AlipayTripPushTransDataReqDTO request) {
        log.info("支付宝出行-行程数据推送,请求参数：{}", JSON.toJSONString(request));
        AlipayTripPushTransDataRespDTO response = new AlipayTripPushTransDataRespDTO();
        try {
            // TODO: 行程数据推送需后续对接 ticket-server / online-server
            response.setRetCode("0000");
            response.setRetMsg("成功");
        } catch (Exception e) {
            log.error("支付宝出行-行程数据推送 异常", e);
            response = new AlipayTripPushTransDataRespDTO();
            response.setRetCode("9999");
            response.setRetMsg("系统内部错误");
        }
        log.info("支付宝出行-行程数据推送,响应结果：{}", JSON.toJSONString(response));
        return response;
    }

    @Override
    public AlipayTripReceiveCardDataRespDTO receiveCardDataFromItp(AlipayTripReceiveCardDataReqDTO request) {
        log.info("支付宝出行-行业数据推送,请求参数：{}", JSON.toJSONString(request));
        AlipayTripReceiveCardDataRespDTO response = new AlipayTripReceiveCardDataRespDTO();
        try {
            // TODO: 行业数据推送需后续对接 account-server / security-server
            response.setRetCode("0000");
            response.setRetMsg("成功");
        } catch (Exception e) {
            log.error("支付宝出行-行业数据推送 异常", e);
            response = new AlipayTripReceiveCardDataRespDTO();
            response.setRetCode("9999");
            response.setRetMsg("系统内部错误");
        }
        log.info("支付宝出行-行业数据推送,响应结果：{}", JSON.toJSONString(response));
        return response;
    }

    @Override
    public AlipayTripReceiveBlackListRespDTO receiveBlackListFromItp(AlipayTripReceiveBlackListReqDTO request) {
        log.info("支付宝出行-黑名单状态变更通知,请求参数：{}", JSON.toJSONString(request));
        AlipayTripReceiveBlackListRespDTO response;
        try {
            response = blacklistClient.alipayTripReceiveBlackList(request);
        } catch (Exception e) {
            log.error("支付宝出行-黑名单状态变更通知 异常", e);
            response = new AlipayTripReceiveBlackListRespDTO();
            response.setRetCode("9999");
            response.setRetMsg("系统内部错误");
        }
        log.info("支付宝出行-黑名单状态变更通知,响应结果：{}", JSON.toJSONString(response));
        return response;
    }
}
