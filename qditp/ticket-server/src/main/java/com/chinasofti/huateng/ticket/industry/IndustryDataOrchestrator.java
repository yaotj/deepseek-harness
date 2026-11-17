package com.chinasofti.huateng.ticket.industry;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.app.CardTypeMapping;
import com.chinasofti.huateng.model.app.IndustryCardDataBuildRespDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoReqDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.app.RequestIndustryDataReqDTO;
import com.chinasofti.huateng.model.app.RequestIndustryDataResult;
import com.chinasofti.huateng.model.app.RequestNoSignalDataReqDTO;
import com.chinasofti.huateng.model.app.RequestNoSignalDataResult;
import com.chinasofti.huateng.model.enums.CardTypeCodeEnum;
import com.chinasofti.huateng.model.ticket.QueryStatusReqDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusRespDTO;
import com.chinasofti.huateng.model.utils.SignChannelUtils;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.dailyticket.DailyTicketClient;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import com.chinasofti.huateng.ticket.gate.AgmRideStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigInteger;


@Service
public class IndustryDataOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(IndustryDataOrchestrator.class);

    private static final String RET_SUCCESS = "0000";
    private static final String STATUS_IN_STATION = "04";
    private static final String DEFAULT_ENTRY_STATUS = "03";
    private static final String NO_SIGNAL_SIGN_CHANNEL = "17";

    private final AccountClient accountClient;
    private final DailyTicketClient dailyTicketClient;
    private final AgmRideStatusService agmRideStatusService;
    private final IndustryCardDataAssembler cardDataAssembler;

    public IndustryDataOrchestrator(AccountClient accountClient,
                                   DailyTicketClient dailyTicketClient,
                                   AgmRideStatusService agmRideStatusService,
                                   IndustryCardDataAssembler cardDataAssembler) {
        this.accountClient = accountClient;
        this.dailyTicketClient = dailyTicketClient;
        this.agmRideStatusService = agmRideStatusService;
        this.cardDataAssembler = cardDataAssembler;
    }

    public RequestIndustryDataResult requestIndustryData(RequestIndustryDataReqDTO request) {
        RequestIndustryDataResult response = new RequestIndustryDataResult();
        response.setSignType("00");
        response.setSign("");
        try {
            log.info("开始处理IF8A-03请求行业数据, request={}", JSON.toJSONString(request));
            if (request == null) {
                return fillInvalidParam(response, "请求报文不能为空");
            }
            String validMsg = IndustryDataQuery.validate(
                    request.getThirdUserId(), request.getCardId(), request.getCardType());
            if (validMsg != null) {
                log.warn("IF8A-03参数校验失败, request={}, msg={}", JSON.toJSONString(request), validMsg);
                return fillInvalidParam(response, validMsg);
            }
            IndustryDataQuery query = IndustryDataQuery.from(request);

            QueryUserInfoResult userInfo = queryUserInfo(query);
            if (userInfo == null) {
                log.warn("IF8A-03查询用户信息返回null, query={}", query);
                response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
                response.setRetMsg("account-server调用失败，返回为空");
                return response;
            }
            if (!RET_SUCCESS.equals(userInfo.getRetCode())) {
                response.setRetCode(userInfo.getRetCode());
                response.setRetMsg(userInfo.getRetMsg());
                return response;
            }


        if (CardTypeCodeEnum.isHceCard(userInfo.getCardType())) {
            String hce = userInfo.getHceData();
            if (!StringUtils.hasText(hce) || hce.trim().length() < 64 || !hce.trim().matches("[0-9A-Fa-f]+")) {
                log.warn("IF8A-03 HCE卡数据缺失或格式非法, cardId={}, cardType={}, hceDataLength={}",
                        userInfo.getCardId(), userInfo.getCardType(),
                        hce == null ? 0 : hce.trim().length());
                response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
                response.setRetMsg("HCE卡数据不存在");
                return response;
            }
                response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
                response.setRetMsg("成功");
                response.setCardData(userInfo.getHceData());
                log.info("IF8A-03 HCE卡直接返回缓存卡数据, thirdUserId={}, cardId={}, cardType={}",
                        userInfo.getThirdUserId(), userInfo.getCardId(), userInfo.getCardType());
                return response;
            }

            String signChannelCode = SignChannelUtils.resolve(userInfo.getChannel());
            if (!StringUtils.hasText(signChannelCode)) {
                log.warn("IF8A-03用户签约渠道为空, userInfo={}", JSON.toJSONString(userInfo));
                response.setRetCode("8001");
                response.setRetMsg("用户签约渠道不能为空");
                return response;
            }

            RpcOutcome.BizRejected dailyTicketReject =
                    checkDailyTicketAvailability(query, userInfo.getCardType());
            if (dailyTicketReject != null) {
                response.setRetCode("8004");
                response.setRetMsg(dailyTicketReject.retMsg());
                return response;
            }

            QueryStatusRespDTO qrStatus = queryTicketStatus(query);
            if (qrStatus == null) {
                log.warn("IF8A-03查询乘车码状态返回null, query={}", query);
                response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
                response.setRetMsg("查询乘车码状态失败，返回为空");
                return response;
            }
            if (!RET_SUCCESS.equals(qrStatus.getRetCode())) {
                response.setRetCode(qrStatus.getRetCode());
                response.setRetMsg(qrStatus.getRetMsg());
                return response;
            }

            IndustryCardDataBuildRespDTO cardDataResp = cardDataAssembler.buildAndSign(
                    query, qrStatus, signChannelCode, userInfo.getCardIssueCode(),
                    qrStatus.getStatus(), qrStatus.getTxnSeq());
            if (cardDataResp == null || !RET_SUCCESS.equals(cardDataResp.getRetCode())) {
                response.setRetCode(cardDataResp == null ? "9999" : cardDataResp.getRetCode());
                response.setRetMsg(cardDataResp == null
                        ? "industry-data-server生成卡数据失败" : cardDataResp.getRetMsg());
                return response;
            }

            response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg("成功");
            response.setCardData(cardDataResp.getCardData());
            log.info("IF8A-03请求行业数据成功, thirdUserId={}, cardId={}, cardType={}, cardData={}",
                    query.thirdUserId(), query.cardId(), query.cardType(), cardDataResp.getCardData());
            return response;
        } catch (Exception e) {
            log.error("处理IF8A-03请求行业数据异常, request={}", JSON.toJSONString(request), e);
            response.setRetCode("9999");
            response.setRetMsg("系统内部错误");
            return response;
        }
    }

    public RequestNoSignalDataResult requestNoSignalData(RequestNoSignalDataReqDTO request) {
        RequestNoSignalDataResult response = new RequestNoSignalDataResult();
        try {
            log.info("开始处理IF8D_03获取离线码数据, request={}", JSON.toJSONString(request));
            if (request == null) {
                response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("请求报文不能为空");
                return response;
            }
            String validMsg = IndustryDataQuery.validate(
                    request.getThirdUserId(), request.getCardId(), request.getCardType());
            if (validMsg != null) {
                log.warn("IF8D_03参数校验失败, request={}, msg={}", JSON.toJSONString(request), validMsg);
                response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                return response;
            }
            IndustryDataQuery query = IndustryDataQuery.from(request);

            QueryUserInfoResult userInfo = queryUserInfo(query);
            if (userInfo == null) {
                log.warn("IF8D_03查询用户信息返回null, query={}", query);
                response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
                response.setRetMsg("account-server调用失败，返回为空");
                return response;
            }
            if (!RET_SUCCESS.equals(userInfo.getRetCode())) {
                response.setRetCode(userInfo.getRetCode());
                response.setRetMsg(userInfo.getRetMsg());
                return response;
            }

            QueryStatusRespDTO qrStatus = queryTicketStatus(query);
            if (qrStatus == null) {
                log.warn("IF8D_03查询乘车码状态返回null, query={}", query);
                response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
                response.setRetMsg("查询乘车码状态失败，返回为空");
                return response;
            }
            if (!RET_SUCCESS.equals(qrStatus.getRetCode())) {
                response.setRetCode(qrStatus.getRetCode());
                response.setRetMsg(qrStatus.getRetMsg());
                return response;
            }

            response.setChannel(userInfo.getChannel());
            String cardIssueCode = userInfo.getCardIssueCode();
            if (STATUS_IN_STATION.equals(qrStatus.getStatus())) {

                IndustryCardDataBuildRespDTO exitDataResp = cardDataAssembler.buildAndSign(
                        query, qrStatus, NO_SIGNAL_SIGN_CHANNEL, cardIssueCode,
                        STATUS_IN_STATION, qrStatus.getTxnSeq());
                if (!isCardDataSuccess(exitDataResp)) {
                    return fillNoSignalError(response, exitDataResp);
                }
                response.setExitData(exitDataResp.getCardData());
            } else {

                String nextTxnSeq = nextTxnSeq(qrStatus.getTxnSeq());
                IndustryCardDataBuildRespDTO entryDataResp = cardDataAssembler.buildAndSign(
                        query, qrStatus, NO_SIGNAL_SIGN_CHANNEL, cardIssueCode,
                        firstNonBlank(qrStatus.getStatus(), DEFAULT_ENTRY_STATUS), nextTxnSeq);
                if (!isCardDataSuccess(entryDataResp)) {
                    return fillNoSignalError(response, entryDataResp);
                }
                IndustryCardDataBuildRespDTO exitDataResp = cardDataAssembler.buildAndSign(
                        query, qrStatus, NO_SIGNAL_SIGN_CHANNEL, cardIssueCode,
                        STATUS_IN_STATION, nextTxnSeq);
                if (!isCardDataSuccess(exitDataResp)) {
                    return fillNoSignalError(response, exitDataResp);
                }
                response.setEntryData(entryDataResp.getCardData());
                response.setExitData(exitDataResp.getCardData());
            }

            response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg("成功");
            log.info("IF8D_03获取离线码数据成功, thirdUserId={}, cardId={}, cardType={}, response={}",
                    query.thirdUserId(), query.cardId(), query.cardType(), JSON.toJSONString(response));
            return response;
        } catch (Exception e) {
            log.error("处理IF8D_03获取离线码数据异常, request={}", JSON.toJSONString(request), e);
            response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
            response.setRetMsg("系统内部错误");
            return response;
        }
    }

    private RequestIndustryDataResult fillInvalidParam(RequestIndustryDataResult response, String msg) {
        response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
        response.setRetMsg(msg);
        return response;
    }

    private QueryUserInfoResult queryUserInfo(IndustryDataQuery query) {
        QueryUserInfoReqDTO userInfoReq = new QueryUserInfoReqDTO();
        userInfoReq.setThirdUserId(query.thirdUserId());
        userInfoReq.setCardId(query.cardId());
        userInfoReq.setCardType(query.cardType());
        QueryUserInfoResult userInfo = accountClient.queryUserInfo(userInfoReq);
        log.info("查询用户信息结果, request={}, response={}",
                JSON.toJSONString(userInfoReq), JSON.toJSONString(userInfo));
        return userInfo;
    }


    private QueryStatusRespDTO queryTicketStatus(IndustryDataQuery query) {
        QueryStatusReqDTO qrReq = new QueryStatusReqDTO();
        qrReq.setThirdUserId(query.thirdUserId());
        qrReq.setCardId(query.cardId());
        QueryStatusRespDTO qrStatus = agmRideStatusService.queryQrCodeStatus(qrReq);
        log.info("查询乘车码状态结果, request={}, response={}",
                JSON.toJSONString(qrReq), JSON.toJSONString(qrStatus));
        return qrStatus;
    }


    private RpcOutcome.BizRejected checkDailyTicketAvailability(IndustryDataQuery query, String accountCardType) {
        String issueCardType = CardTypeMapping.toIssueCardType(accountCardType);
        if (!CardTypeCodeEnum.isDailyTicket(issueCardType)) {
            return null;
        }
        RpcOutcome availability = dailyTicketClient.checkRideAvailability(query.cardId());
        switch (availability) {
            case RpcOutcome.Ok ok -> {
                log.info("IF8A-03 日票可用性校验通过, cardId={}, cardType={}", query.cardId(), issueCardType);
                return null;
            }
            case RpcOutcome.BizRejected rejected -> {
                log.warn("IF8A-03 日票不可用，拒发乘车码, cardId={}, cardType={}, retCode={}, retMsg={}",
                        query.cardId(), issueCardType, rejected.retCode(), rejected.retMsg());
                return rejected;
            }
            case RpcOutcome.Unreachable(var cause) -> {
                log.warn("IF8A-03 日票可用性校验不可达，降级放行, cardId={}", query.cardId(), cause);
                return null;
            }
        }
    }

    private boolean isCardDataSuccess(IndustryCardDataBuildRespDTO response) {
        return response != null
                && RET_SUCCESS.equals(response.getRetCode())
                && StringUtils.hasText(response.getCardData());
    }

    private RequestNoSignalDataResult fillNoSignalError(RequestNoSignalDataResult response,
                                                       IndustryCardDataBuildRespDTO cardDataResp) {
        response.setRetCode(cardDataResp == null
                ? FepAppErrorCodeEnum.FAIL.getCode() : cardDataResp.getRetCode());
        response.setRetMsg(cardDataResp == null
                ? "industry-data-server生成离线码数据失败" : cardDataResp.getRetMsg());
        return response;
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

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        return StringUtils.hasText(second) ? second.trim() : null;
    }
}
