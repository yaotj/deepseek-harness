package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.app.RequestStationLineInfoResult;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseRespDTO;
import com.chinasofti.huateng.model.ticket.enums.AdviceOptEnum;
import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.alipay.account.AlipayAccountClient;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.gate.QRCodeStatusStore;
import com.chinasofti.huateng.ticket.station.StationLineResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/** IF5A-01 票卡分析：按票卡状态给 BOM 返回建议操作与付费更新的预估金额。 */
@Component
class CardDataAnalyseHandler {

    private static final Logger log = LoggerFactory.getLogger(CardDataAnalyseHandler.class);

    /** QRCODE_STATUS 只读访问。 */
    @Autowired
    private QRCodeStatusStore qrCodeStatusStore;

    @Autowired
    private StationLineResolver stationLineResolver;

    @Autowired
    private AccountClient accountClient;

    @Autowired
    private AlipayAccountClient alipayAccountClient;

    @Autowired
    private SupplementStateRules stateRules;

    @Autowired
    private SupplementFareQuery fareQuery;

    RequestCardDataAnalyseRespDTO handle(RequestCardDataAnalyseReqDTO request,
                                        RequestCardDataAnalyseRespDTO response) {
        String cardId = request.getCardId();

        QRCodeStatus status = qrCodeStatusStore.findByCardId(cardId);
        if (status == null) {
            response.setRetCode(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getCode());
            response.setRetMsg(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getMsg());
            return response;
        }

        QRCodeStatusEnum codeStatus = stateRules.resolveCodeStatus(status.getCodeStatus());
        if (codeStatus == null) {
            log.warn("IF5A-01 票卡状态不是已登记取值，拒绝, cardId={}, codeStatus={}", cardId, status.getCodeStatus());
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("票卡状态不是已登记取值: " + status.getCodeStatus());
            return response;
        }

        String unknownStation = stateRules.unknownStationCode();
        String gateInStation = SupplementCodec.defaultString(status.getGateInStation(), unknownStation);
        String lastTxnStation = SupplementCodec.defaultString(status.getLastTxnStation(), unknownStation);

        String lastLineCode = queryLineCode(lastTxnStation, cardId);

        String updateType = SupplementCodec.defaultString(
                request.getUpdateType(), SupplementCodec.UPDATE_TYPE_FREE_AREA);
        List<String> adviceOpt = stateRules.resolveAdviceOpt(
                codeStatus, gateInStation, lastTxnStation, updateType, status.getGateInTime(), cardId);
        response.setAdviceOpt(adviceOpt);

        String transAmount = SupplementCodec.AMOUNT_ZERO;
        if (adviceOpt.contains(AdviceOptEnum.PAID_UPDATE.getCode())) {
            transAmount = estimatePayAmount(gateInStation, lastTxnStation, cardId);
        }
        response.setTransAmount(transAmount);

        response.setMsisdn(SupplementCodec.defaultString(request.getMsisdn(), ""));
        response.setCardIssueDate("");
        try {
            queryUserInfo(cardId, request.getProviderId(), response);
        } catch (Exception e) {
            log.warn("IF5A-01 查询用户信息失败, cardId={}", cardId, e);
        }

        response.setRetCode(SupplementCodec.RET_SUCCESS);
        response.setRetMsg("成功");
        response.setProviderId(SupplementCodec.defaultString(
                request.getProviderId(), SupplementCodec.PROVIDER_ID_DEFAULT));
        response.setCardId(cardId);
        response.setCardStatus(codeStatus.getCode());
        response.setLastLineCode(lastLineCode);
        response.setLastStationCode(lastTxnStation);
        response.setLastUpdateDate(SupplementCodec.defaultString(status.getLastTxnTime(), ""));
        response.setLastTransAmout(status.getTrxAmount() == null
                ? SupplementCodec.AMOUNT_ZERO : String.valueOf(status.getTrxAmount()));
        response.setLastTicketTransSeq(SupplementCodec.defaultString(
                status.getTxnSeq(), SupplementCodec.AMOUNT_ZERO));
        response.setManagerCode(SupplementCodec.defaultString(request.getManagerCode(), ""));

        return response;
    }

    /** 查询车站所属线路号，查不到返回空串。 */
    private String queryLineCode(String stationCode, String cardId) {
        if (stateRules.isUnknownStation(stationCode)) {
            return "";
        }
        RequestStationLineInfoResult lineResult = stationLineResolver.resolveLineInfo(stationCode);
        if (!stationLineResolver.isUsable(lineResult)) {
            log.warn("IF5A-01 线路信息查询未成功，lastLineCode 置空, station={}, retCode={}, cardId={}",
                    stationCode, lineResult == null ? "null" : lineResult.getRetCode(), cardId);
            return "";
        }
        return SupplementCodec.defaultString(lineResult.getLineCode(), "");
    }

    /** 付费更新（{@code 006}）的预估报价。 */
    private String estimatePayAmount(String gateInStation, String lastTxnStation, String cardId) {
        if (stateRules.isUnknownStation(lastTxnStation)) {
            log.warn("IF5A-01 上次交易站未知，付费更新预估按 0 元返回，实扣以 IF5A-03 重算为准, cardId={}", cardId);
            return SupplementCodec.AMOUNT_ZERO;
        }
        return fareQuery.query(gateInStation, lastTxnStation, "IF5A-01").priceOrZero();
    }

    /** 查询用户信息，把 msisdn / cardIssueDate 写进 response。 */
    private void queryUserInfo(String cardId, String providerId, RequestCardDataAnalyseRespDTO response) {
        QueryUserInfoResult cardTypeResult = accountClient.queryCardTypeByCardId(cardId);
        if (cardTypeResult == null || !SupplementCodec.RET_SUCCESS.equals(cardTypeResult.getRetCode())
                || !StringUtils.hasText(cardTypeResult.getThirdUserId())) {
            log.warn("IF5A-01 查询用户卡类型失败, cardId={}", cardId);
            return;
        }

        if (SupplementCodec.PROVIDER_ID_ALIPAY.equals(providerId)) {
            AlipayUserInfoDTO alipayUser = alipayAccountClient.selectByThirdUserId(cardTypeResult.getThirdUserId());
            if (alipayUser != null && StringUtils.hasText(alipayUser.getPhone())) {
                response.setMsisdn(alipayUser.getPhone());
            }
            return;
        }

        if (StringUtils.hasText(cardTypeResult.getMsisdn())) {
            response.setMsisdn(cardTypeResult.getMsisdn());
        }
        if (StringUtils.hasText(cardTypeResult.getRegTms())) {
            response.setCardIssueDate(cardTypeResult.getRegTms());
        }
    }
}
