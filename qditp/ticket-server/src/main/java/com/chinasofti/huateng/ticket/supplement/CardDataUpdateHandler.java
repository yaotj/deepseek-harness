package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.enums.TrxTypeCodeEnum;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateRespDTO;
import com.chinasofti.huateng.model.ticket.enums.AdviceOptEnum;
import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.gate.AgmRideStatusService;
import com.chinasofti.huateng.ticket.gate.QRCodeStatusStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/** IF5A-03 票卡更新：按 BOM 上送的 {@code adviceOpt} 执行补进站 / 补出站。 */
@Component
class CardDataUpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(CardDataUpdateHandler.class);

    /** {@code optDate} / {@code handleDateTime} 的固定长度 {@code yyyyMMddHHmmss}。 */
    private static final int OPT_DATE_LENGTH = 14;

    /** 车站码固定 4 位。 */
    private static final int STATION_CODE_LENGTH = 4;

    /** QRCODE_STATUS 只读访问。 */
    @Autowired
    private QRCodeStatusStore qrCodeStatusStore;

    @Autowired
    private SupplementUserLookup userLookup;

    /** IF1A-01 检票编排门面，进程内直调。 */
    @Autowired
    private AgmRideStatusService agmRideStatusService;

    @Autowired
    private SupplementStateRules stateRules;

    @Autowired
    private SupplementFareQuery fareQuery;

    @Autowired
    private SupplementGateRequestAssembler gateRequestAssembler;

    @Autowired
    private SupplementRequestLedger requestLedger;

    RequestCardDataUpdateRespDTO handle(RequestCardDataUpdateReqDTO request,
                                       RequestCardDataUpdateRespDTO response) {
        String cardId = request.getCardId();
        String adviceOpt = request.getAdviceOpt();
        String updateType = SupplementCodec.defaultString(
                request.getUpdateType(), SupplementCodec.UPDATE_TYPE_FREE_AREA);
        String requestTransAmount = SupplementCodec.defaultString(
                request.getTransAmount(), SupplementCodec.AMOUNT_ZERO);
        String optDate = request.getOptDate();
        String updateStationCode = request.getUpdateStationCode();

        String paramError = validateRequired(cardId, adviceOpt, updateStationCode, optDate);
        if (paramError != null) {
            log.warn("IF5A-03 入参校验失败[{}], cardId={}, adviceOpt={}, updateStationCode={}, optDate={}",
                    paramError, cardId, adviceOpt, updateStationCode, optDate);
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg(paramError);
            return response;
        }

        QRCodeStatus currentStatus = qrCodeStatusStore.findByCardId(cardId);
        if (currentStatus == null) {
            response.setRetCode(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getCode());
            response.setRetMsg(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getMsg());
            return response;
        }

        String rawCodeStatus = currentStatus.getCodeStatus();
        QRCodeStatusEnum codeStatus = stateRules.resolveCodeStatus(rawCodeStatus);
        if (codeStatus == null) {
            log.warn("IF5A-03 票卡状态不是已登记取值，拒绝, cardId={}, codeStatus={}", cardId, rawCodeStatus);
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("票卡状态不是已登记取值: " + rawCodeStatus);
            return response;
        }

        if (AdviceOptEnum.FREE_UPDATE.matches(adviceOpt)
                && !stateRules.isWithinFreeWindow(currentStatus.getGateInTime())) {
            log.warn("IF5A-03 免费更新超 20 分钟时间窗, cardId={}, codeStatus={}, adviceOpt={}, gateInTime={}",
                    cardId, rawCodeStatus, adviceOpt, currentStatus.getGateInTime());
            response.setRetCode(TicketErrorCodeEnum.CARD_STATUS_CHANGED.getCode());
            response.setRetMsg("免费更新时间窗（20 分钟）已过，请重新执行票卡分析");
            return response;
        }
        if (!stateRules.isUpdateAllowed(codeStatus, adviceOpt, updateType, currentStatus.getGateInTime())) {
            log.warn("IF5A-03 票卡状态不允许此操作, cardId={}, codeStatus={}, adviceOpt={}, updateType={},"
                            + " gateInTime={}",
                    cardId, rawCodeStatus, adviceOpt, updateType, currentStatus.getGateInTime());
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("票卡状态不允许此操作: codeStatus=" + rawCodeStatus + ", adviceOpt=" + adviceOpt);
            return response;
        }

        String trxType = resolveTrxType(adviceOpt);
        if (trxType == null) {
            log.warn("IF5A-03 不支持的操作类型, cardId={}, adviceOpt={}", cardId, adviceOpt);
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("不支持的操作类型: " + adviceOpt);
            return response;
        }

        SupplementUserLookup.Result lookup = userLookup.query(cardId);
        if (!lookup.outcome().isOk()) {
            return fillUserLookupFailure(response, lookup, cardId);
        }
        QueryUserInfoResult userInfo = lookup.userInfo();

        NotifyVerifyResultReqDTO gateRequest = gateRequestAssembler.newBaseRequest(
                currentStatus, cardId, userInfo.getThirdUserId(), trxType, optDate, updateStationCode,
                userInfo.getCardType(), SupplementCodec.defaultString(userInfo.getChannel(), ""));
        gateRequest.setDeviceId(SupplementCodec.defaultString(request.getOperaterId(), ""));
        gateRequest.setAdviceOpt(adviceOpt);
        gateRequest.setReserve1(adviceOpt);

        if (AdviceOptEnum.PAID_UPDATE.matches(adviceOpt)) {
            RequestCardDataUpdateRespDTO fareFailure = fillPaidUpdateAmount(
                    request, response, gateRequest, currentStatus, updateStationCode, requestTransAmount, cardId);
            if (fareFailure != null) {
                return fareFailure;
            }
        }

        return dispatchToGate(gateRequest, currentStatus, adviceOpt, updateType, cardId,
                requestTransAmount, rawCodeStatus, response);
    }

    /**
     * 必填与格式校验。
     *
     * @return null 表示通过，否则为可直接回给 BOM 的原因
     */
    private String validateRequired(String cardId, String adviceOpt, String updateStationCode, String optDate) {
        if (!StringUtils.hasText(cardId) || !StringUtils.hasText(adviceOpt)
                || !StringUtils.hasText(updateStationCode) || !StringUtils.hasText(optDate)) {
            return "必填参数缺失: cardId/adviceOpt/updateStationCode/optDate";
        }
        if (optDate.length() != OPT_DATE_LENGTH || !isAllDigits(optDate)) {
            return "optDate 必须是 " + OPT_DATE_LENGTH + " 位数字(yyyyMMddHHmmss): " + optDate;
        }
        if (updateStationCode.length() != STATION_CODE_LENGTH || !isAlphaNumeric(updateStationCode)) {
            return "updateStationCode 必须是 " + STATION_CODE_LENGTH + " 位字母或数字: " + updateStationCode;
        }
        return null;
    }

    private boolean isAllDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isAlphaNumeric(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isLetterOrDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 建议操作 → 闸机交易方向。
     *
     * @return null 表示不支持的操作类型（含 {@code 000} 无需操作与未登记取值），由调用方拒绝整笔
     */
    private String resolveTrxType(String adviceOpt) {
        AdviceOptEnum opt = AdviceOptEnum.fromCode(adviceOpt);
        if (opt == null) {
            return null;
        }
        if (opt.isSupplementEntry()) {
            return TrxTypeCodeEnum.ENTRY.getCode();
        }
        if (opt.isSupplementExit()) {
            return TrxTypeCodeEnum.EXIT.getCode();
        }
        return null;
    }

    /**
     * 006 付费更新的票价重算与一致性比对。
     *
     * @return null 表示成功（金额已写进 {@code gateRequest}），否则为可直接返回的失败响应
     */
    private RequestCardDataUpdateRespDTO fillPaidUpdateAmount(
            RequestCardDataUpdateReqDTO request, RequestCardDataUpdateRespDTO response,
            NotifyVerifyResultReqDTO gateRequest, QRCodeStatus currentStatus,
            String updateStationCode, String requestTransAmount, String cardId) {

        String entryStation = SupplementCodec.defaultString(
                currentStatus.getGateInStation(), stateRules.unknownStationCode());
        if (!updateStationCode.equals(currentStatus.getLastTxnStation())) {
            log.warn("IF5A-03 付费更新的出站站与 IF5A-01 报价基准不一致, cardId={}, 报价基准lastTxn={},"
                            + " 本次updateStation={}",
                    cardId, currentStatus.getLastTxnStation(), updateStationCode);
        }

        SupplementFareQuery.FareResult fare = fareQuery.query(entryStation, updateStationCode, "IF5A-03");
        if (!fare.isOk()) {
            switch (fare.outcome()) {
                case RpcOutcome.Unreachable unreachable -> {
                    log.error("IF5A-03 票价查询不可达, cardId={}", cardId, unreachable.cause());
                    response.setRetCode(TicketErrorCodeEnum.ACC_COMM_ERROR.getCode());
                    response.setRetMsg("票价服务暂不可用，请稍后重试");
                }
                case RpcOutcome.BizRejected rejected -> {
                    log.warn("IF5A-03 票价查询被拒, cardId={}, retCode={}, retMsg={}",
                            cardId, rejected.retCode(), rejected.retMsg());
                    response.setRetCode(TicketErrorCodeEnum.NO_DATA.getCode());
                    response.setRetMsg("票价查询失败，请前往车站服务台办理");
                }
                case RpcOutcome.Ok ok -> throw new IllegalStateException("Ok 分支不可达: " + ok);
            }
            return response;
        }

        String ticketPrice = fare.ticketPrice();
        if (!isSameAmount(ticketPrice, requestTransAmount)) {
            log.warn("IF5A-03 BOM 上送金额与 ITP 重算不一致，以 ITP 为准, cardId={}, bom={}, itp={}",
                    cardId, requestTransAmount, ticketPrice);
        }
        gateRequest.setTrxAmount(ticketPrice);
        log.info("IF5A-03 付费更新票价已重算, cardId={}, entry={}, exit={}, itp={}, operaterId={}",
                cardId, entryStation, updateStationCode, ticketPrice, request.getOperaterId());
        return null;
    }

    private boolean isSameAmount(String left, String right) {
        try {
            return new BigDecimal(left).compareTo(new BigDecimal(right)) == 0;
        } catch (NumberFormatException e) {
            log.warn("金额格式无法解析，退化为字符串比较, left={}, right={}", left, right);
            return left.equals(right);
        }
    }

    /** 声明幂等 → 下发闸机 → 按结果收口。 */
    private RequestCardDataUpdateRespDTO dispatchToGate(
            NotifyVerifyResultReqDTO gateRequest, QRCodeStatus currentStatus, String adviceOpt,
            String updateType, String cardId, String requestTransAmount, String rawCodeStatus,
            RequestCardDataUpdateRespDTO response) {

        SupplementRequestLedger.Claim claim = requestLedger.claim(
                cardId, currentStatus.getTxnSeq(), adviceOpt, rawCodeStatus,
                gateRequest.getHandleStationCode(), gateRequest.getHandleDateTime(), gateRequest.getTrxAmount());
        if (!claim.acquired()) {
            log.warn("IF5A-03 同一票卡同一交易序号的补站已在处理或已完成, cardId={}, txnSeq={}, adviceOpt={}",
                    cardId, currentStatus.getTxnSeq(), adviceOpt);
            response.setRetCode(TicketErrorCodeEnum.CARD_STATUS_CHANGED.getCode());
            response.setRetMsg(TicketErrorCodeEnum.CARD_STATUS_CHANGED.getMsg());
            return response;
        }

        try {
            NotifyVerifyResultRespDTO gateResponse = agmRideStatusService.notifyVerifyResult(gateRequest);
            String gateRetCode = gateResponse.getRetCode();
            String gateRetMsg = gateResponse.getRetMsg();
            log.info("IF5A-03 进程内检票编排结束, cardId={}, adviceOpt={}, retCode={}, retMsg={}",
                    cardId, adviceOpt, gateRetCode, gateRetMsg);

            if (!SupplementCodec.RET_SUCCESS.equals(gateRetCode)) {
                requestLedger.markRejected(claim, gateRetCode, gateRetMsg);
                response.setRetCode(TicketErrorCodeEnum.AGM_RETURN_STATUS_ABNORMAL1.getCode());
                response.setRetMsg("闸机检票失败: " + gateRetCode);
                return response;
            }
            requestLedger.markSuccess(claim);
        } catch (Exception e) {
            requestLedger.markUnknown(claim, "检票编排异常: " + e.getClass().getSimpleName());
            log.error("IF5A-03 进程内检票编排异常, cardId={}, adviceOpt={}", cardId, adviceOpt, e);
            response.setRetCode(TicketErrorCodeEnum.GATE_COMM_ERROR.getCode());
            response.setRetMsg(TicketErrorCodeEnum.GATE_COMM_ERROR.getMsg());
            return response;
        }

        response.setRetCode(SupplementCodec.RET_SUCCESS);
        response.setRetMsg("成功");
        log.info("IF5A-03 票卡更新完成, cardId={}, adviceOpt={}, updateType={}, codeStatus={}, bom={}, itp={}",
                cardId, adviceOpt, updateType, rawCodeStatus, requestTransAmount, gateRequest.getTrxAmount());
        return response;
    }

    private RequestCardDataUpdateRespDTO fillUserLookupFailure(RequestCardDataUpdateRespDTO response,
                                                              SupplementUserLookup.Result lookup,
                                                              String cardId) {
        switch (lookup.outcome()) {
            case RpcOutcome.Unreachable unreachable -> {
                log.error("IF5A-03 查询用户信息不可达, cardId={}", cardId, unreachable.cause());
                response.setRetCode(TicketErrorCodeEnum.ACC_COMM_ERROR.getCode());
                response.setRetMsg("账户服务暂不可用，请稍后重试");
            }
            case RpcOutcome.BizRejected rejected -> {
                log.warn("IF5A-03 查询用户信息被拒, cardId={}, retCode={}, retMsg={}",
                        cardId, rejected.retCode(), rejected.retMsg());
                response.setRetCode(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getCode());
                response.setRetMsg(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getMsg());
            }
            case RpcOutcome.Ok ok -> throw new IllegalStateException("Ok 分支不可达: " + ok);
        }
        return response;
    }
}
