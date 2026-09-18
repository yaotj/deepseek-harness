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

/**
 * 行业数据（IF8A-03 在线码 / IF8D-03 离线码）的**编排宿主**（ADR-D142）。
 *
 * <p><b>为什么在 ticket-server</b>：这条链路要「查账户 → 查乘车码状态 → 日票前置 → 生码」，
 * 其中**乘车码状态就是本模块自己的聚合**（{@link AgmRideStatusService}，本地调用），
 * 其余三个都是单向出网（account / daily-ticket / industry-data，三个 `service.*.url` 与
 * `@EnableRpc*` 本模块**早就有**）。放在这里**零双向 RPC**；
 * 反过来若把编排放 industry-data-server，它就得回头查 ticket-server 的码状态，
 * 而 ticket-server 的 {@code IndustryDataNotifier} 又要调它生码 —— 形成 A→B→A，
 * 违反 `docs/domain` 那条「双向 RPC 即边界画错」判据。<b>NEVER 迁到 industry-data-server。</b>
 *
 * <p><b>为什么不留在 fep-app-server</b>：那是报文接入层，同模块另外 6 个 Service 都是单行转发
 * （中位数 89 行、`if` 数 0~5），而这条编排 425 行 / 4 个下游 Client / 31 个 `if`、
 * 还按卡种与渠道分流，属分层越位。现在 `fep-app-server` 侧只剩一行转发。
 *
 * <p><b>本类刻意只做编排</b>：入参翻译在 {@link IndustryDataQuery}、码体组装在
 * {@link IndustryCardDataAssembler}。两个 public 方法共用同一套私有方法，
 * <b>NEVER 再按链路复制成对重载</b>（原实现有 4 组逐字副本，这是本次重构的主要动因）。
 */
@Service
public class IndustryDataOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(IndustryDataOrchestrator.class);

    private static final String RET_SUCCESS = "0000";
    /** 码状态「站内」：已进站未出站。 */
    private static final String STATUS_IN_STATION = "04";
    /** 进站码在码状态缺失时的兜底票卡状态。 */
    private static final String DEFAULT_ENTRY_STATUS = "03";
    /** 离线码的签约渠道位固定 17，与账户 `CHANNEL` 无关。 */
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

    /** IF8A-03 请求行业数据（在线码）。 */
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

            /*
             * HCE 卡数据由开户与闸机交易维护，不参与码体生成与签名，直接返缓存值、短路整条生码。
             */
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

    /** IF8D-03 获取离线码数据。 */
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
                /*
                 * 已在站内：只需要出站码，交易序列号沿用当前值（出站与进站是同一笔交易）。
                 */
                IndustryCardDataBuildRespDTO exitDataResp = cardDataAssembler.buildAndSign(
                        query, qrStatus, NO_SIGNAL_SIGN_CHANNEL, cardIssueCode,
                        STATUS_IN_STATION, qrStatus.getTxnSeq());
                if (!isCardDataSuccess(exitDataResp)) {
                    return fillNoSignalError(response, exitDataResp);
                }
                response.setExitData(exitDataResp.getCardData());
            } else {
                /*
                 * 站外：进站码与出站码一次发两张，**两张共用同一个 +1 后的序列号** ——
                 * 它们属于「即将发生的同一笔行程」，NEVER 给出站码再 +1。
                 */
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

    /**
     * 乘车码状态**本地查**（迁进 ticket-server 的直接收益：这一跳从 RPC 变成进程内调用）。
     */
    private QueryStatusRespDTO queryTicketStatus(IndustryDataQuery query) {
        QueryStatusReqDTO qrReq = new QueryStatusReqDTO();
        qrReq.setThirdUserId(query.thirdUserId());
        qrReq.setCardId(query.cardId());
        QueryStatusRespDTO qrStatus = agmRideStatusService.queryQrCodeStatus(qrReq);
        log.info("查询乘车码状态结果, request={}, response={}",
                JSON.toJSONString(qrReq), JSON.toJSONString(qrStatus));
        return qrStatus;
    }

    /**
     * 日票族拉码前置校验（提前反馈，不是护栏）。三条口径 NEVER 改：
     * ① 只对日票族卡种生效，后付费链路一行不动；
     * ② {@code Unreachable} 降级放行 —— 闸机侧 {@code GateDailyTicketCoordinator.checkEntryAllowed}
     *    还有一道权威校验；
     * ③ 这是提前反馈、不是护栏，NEVER 据此撤掉闸机那道校验。
     *
     * @return 需要拒发时返回那条 {@code BizRejected}（<b>NEVER 改成返回 retMsg 字符串</b>——
     *         对端不带文案时会退化成 null，与「放行」撞语义），放行返回 null
     */
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
