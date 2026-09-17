package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.app.RequestExcessFareReqDTO;
import com.chinasofti.huateng.model.app.RequestExcessFareResult;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.gate.AgmRideStatusService;
import com.chinasofti.huateng.ticket.gate.QRCodeStatusStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

/** IF8A-04 自助补站处理。 */
@Component
class ExcessFareHandler {

    private static final Logger log = LoggerFactory.getLogger(ExcessFareHandler.class);

    /** 补出站，需要算票价。 */
    private static final String UPGRADE_TYPE_EXIT = "02";

    /** QRCODE_STATUS 只读访问。 */
    @Autowired
    private QRCodeStatusStore qrCodeStatusStore;

    /** IF1A-01 检票编排门面，进程内直调。 */
    @Autowired
    private AgmRideStatusService agmRideStatusService;

    @Autowired
    private SupplementStateRules stateRules;

    @Autowired
    private SupplementFareQuery fareQuery;

    @Autowired
    private SupplementGateRequestAssembler gateRequestAssembler;

    /**
     * 处理自助补站请求。
     *
     * @param request 补站请求
     * @param response 响应对象
     */
    void handleExcessFare(RequestExcessFareReqDTO request, RequestExcessFareResult response) {
        String cardId = request.getCardId();
        String upgradeAreaType = request.getUpgradeAreaType();
        String upgradeStationCode = request.getUpgradeStationCode();
        String upgradeDateTime = request.getUpgradeDateTime();
        String unknownStation = stateRules.unknownStationCode();

        QRCodeStatus currentStatus = qrCodeStatusStore.findByCardId(cardId);
        if (currentStatus == null) {
            currentStatus = new QRCodeStatus();
            currentStatus.setCardId(cardId);
            currentStatus.setCreateTime(LocalDateTime.now());
            currentStatus.setUseCount(0);
            currentStatus.setGateInStation(unknownStation);
            currentStatus.setLastTxnStation(unknownStation);
            currentStatus.setCodeStatus(QRCodeStatusEnum.SJT_ISSUE.getCode());
        }

        String codeStatus = SupplementCodec.defaultString(
                currentStatus.getCodeStatus(), QRCodeStatusEnum.SJT_ISSUE.getCode());
        QRCodeStatusEnum statusEnum = QRCodeStatusEnum.fromCode(codeStatus);
        if (statusEnum == null) {
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("当前票卡状态不支持补站，codeStatus=" + codeStatus);
            return;
        }

        AllowedTypesResult allowedResult = resolveAllowedTypes(statusEnum, codeStatus);
        if (!allowedResult.allowedTypes.contains(upgradeAreaType)) {
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg(allowedResult.friendlyMsg);
            return;
        }

        NotifyVerifyResultReqDTO bizData = gateRequestAssembler.newBaseRequest(
                currentStatus, cardId, request.getThirdUserId(), upgradeAreaType,
                upgradeDateTime, upgradeStationCode, request.getCardType(), "");
        bizData.setDeviceId(upgradeStationCode + "36" + "01");
        bizData.setExcessFareType(upgradeAreaType);

        if (UPGRADE_TYPE_EXIT.equals(upgradeAreaType)) {
            String entryStation = SupplementCodec.defaultString(
                    currentStatus.getGateInStation(), unknownStation);
            SupplementFareQuery.FareResult fare = fareQuery.query(entryStation, upgradeStationCode, "IF8A-04");
            if (!fare.isOk()) {
                response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("票价查询失败，请稍后重试或前往车站服务台办理");
                return;
            }
            bizData.setTrxAmount(fare.ticketPrice());
        }

        try {
            NotifyVerifyResultRespDTO gateResponse = agmRideStatusService.notifyVerifyResult(bizData);
            if (!SupplementCodec.RET_SUCCESS.equals(gateResponse.getRetCode())) {
                response.setRetCode(gateResponse.getRetCode());
                response.setRetMsg(gateResponse.getRetMsg());
                return;
            }
            log.info("IF8A-04 补站进程内完成检票编排, cardId={}, trxType={}, station={}",
                    cardId, upgradeAreaType, upgradeStationCode);
        } catch (Exception e) {
            log.error("IF8A-04 补站检票编排异常, cardId={}, trxType={}", cardId, upgradeAreaType, e);
            response.setRetCode(TicketErrorCodeEnum.GATE_COMM_ERROR.getCode());
            response.setRetMsg(TicketErrorCodeEnum.GATE_COMM_ERROR.getMsg());
            return;
        }

        response.setRetCode(TicketErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(TicketErrorCodeEnum.SUCCESS.getMsg());
    }

    /** 根据票卡状态解析允许的补站类型。 */
    private AllowedTypesResult resolveAllowedTypes(QRCodeStatusEnum statusEnum, String codeStatus) {
        switch (statusEnum) {
            case END_TRIP:
            case SJT_ISSUE:
            case EXIT:
            case EXIT_OVERTIME:
            case SELF_SERVICE_EXIT:
            case UPDATE_FREE:
            case UPDATE_PAY:
                return new AllowedTypesResult(Set.of("01"), "码状态正常，无需更新，请正常刷码");
            case ENTRY:
            case SELF_SERVICE_ENTRY:
                return new AllowedTypesResult(Set.of("02", "03", "04"), "码状态正常，无需更新，请正常刷码");
            case UPDATE_ENTRY:
                return new AllowedTypesResult(Set.of("01"), "码状态正常，无需更新，请正常刷码");
            default:
                return new AllowedTypesResult(Set.of(), "当前票卡状态不支持补站，codeStatus=" + codeStatus);
        }
    }

    /** 允许的补站类型结果。 */
    private record AllowedTypesResult(Set<String> allowedTypes, String friendlyMsg) {
    }
}
