package com.chinasofti.huateng.ticket.service.impl;

import com.chinasofti.huateng.model.app.*;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import com.chinasofti.huateng.rpc.fepDev.FepDevClient;
import com.chinasofti.huateng.rpc.para.ParaClient;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.mapper.QRCodeStatusMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * IF8A-04 自助补站处理。
 *
 * <p>负责处理用户自助补站请求，包括：
 * <ul>
 *   <li>参数校验</li>
 *   <li>状态机判断（允许的补站类型）</li>
 *   <li>票价计算（补出站）</li>
 *   <li>调用闸机接口完成补站</li>
 * </ul>
 */
@Component
public class ExcessFareHandler {

    private static final Logger log = LoggerFactory.getLogger(ExcessFareHandler.class);
    private static final String RET_SUCCESS = "0000";

    @Value("${ticket.default-last-txn-station:FFFF}")
    private String defaultLastTxnStation;

    @Autowired
    private QRCodeStatusMapper qrCodeStatusMapper;

    @Autowired
    private ParaClient paraClient;

    @Autowired
    private FepDevClient fepDevClient;

    /**
     * 处理自助补站请求。
     *
     * @param request  补站请求
     * @param response 响应对象
     */
    public void handleExcessFare(RequestExcessFareReqDTO request, RequestExcessFareResult response) {
        String cardId = request.getCardId();
        String upgradeAreaType = request.getUpgradeAreaType();
        String upgradeStationCode = request.getUpgradeStationCode();
        String upgradeDateTime = request.getUpgradeDateTime();

        // 1. 查询当前票卡状态
        QRCodeStatus currentStatus = qrCodeStatusMapper.selectByCardId(cardId);
        if (currentStatus == null) {
            currentStatus = new QRCodeStatus();
            currentStatus.setCardId(cardId);
            currentStatus.setCreateTime(LocalDateTime.now());
            currentStatus.setUseCount(0);
            currentStatus.setGateInStation(defaultLastTxnStation);
            currentStatus.setLastTxnStation(defaultLastTxnStation);
            currentStatus.setCodeStatus(QRCodeStatusEnum.SJT_ISSUE.getCode());
        }

        String codeStatus = defaultString(currentStatus.getCodeStatus(), QRCodeStatusEnum.SJT_ISSUE.getCode());
        String gateInStation = defaultString(currentStatus.getGateInStation(), defaultLastTxnStation);
        String lastTxnStation = defaultString(currentStatus.getLastTxnStation(), defaultLastTxnStation);

        // 2. 基于 codeStatus 确定允许的补站类型
        QRCodeStatusEnum statusEnum = QRCodeStatusEnum.fromCode(codeStatus);
        if (statusEnum == null) {
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("当前票卡状态不支持补站，codeStatus=" + codeStatus);
            return;
        }

        AllowedTypesResult allowedResult = resolveAllowedTypes(statusEnum, codeStatus);
        String allowedTypes = allowedResult.allowedTypes;
        String friendlyMsg = allowedResult.friendlyMsg;

        if (!allowedTypes.contains(upgradeAreaType)) {
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg(friendlyMsg);
            return;
        }

        // 3. 组装闸机接口参数
        NotifyVerifyResultReqDTO bizData = new NotifyVerifyResultReqDTO();
        bizData.setDeviceId(upgradeStationCode + "36" + "01");
        bizData.setItpUserId(encodeHexThirdUserId(request.getThirdUserId()));
        bizData.setTrxType(upgradeAreaType);
        bizData.setIssueChannelCode(defaultString(currentStatus.getChannel(), "01"));
        bizData.setSignChannelCode("");
        bizData.setCardId(cardId);
        bizData.setCardType(request.getCardType());
        bizData.setHandleDateTime(upgradeDateTime);
        bizData.setHandleStationCode(upgradeStationCode);
        bizData.setOvertimeAmount("0");
        bizData.setLastTicketStatus(defaultString(currentStatus.getCodeStatus(), QRCodeStatusEnum.SJT_ISSUE.getCode()));
        bizData.setHandleResultCode("000");
        bizData.setLastHandleStationCode(currentStatus.getLastTxnStation());
        bizData.setLastHandleDateTime(currentStatus.getLastTxnTime());
        bizData.setTicketTransSeq(currentStatus.getTxnSeq() == null ? "0" : currentStatus.getTxnSeq());
        bizData.setExcessFareType(upgradeAreaType);
        bizData.setReserve1(null);
        bizData.setReserve2(null);

        // 4. 计算票价（仅补出站需要）
        String ticketPrice = null;
        if ("02".equals(upgradeAreaType)) {
            ticketPrice = calculateTicketPrice(
                    defaultString(currentStatus.getGateInStation(), defaultLastTxnStation),
                    upgradeStationCode
            );
            if (ticketPrice == null) {
                response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("票价查询失败，请稍后重试或前往车站服务台办理");
                return;
            }
        }

        bizData.setTrxAmount(ticketPrice != null ? ticketPrice : "0");
        bizData.setOvertimeAmount("0");

        // 5. 调用闸机接口
        try {
            NotifyVerifyResultRespDTO gateResponse = fepDevClient.notifyVerifyResult(bizData);
            if (!RET_SUCCESS.equals(gateResponse.getRetCode())) {
                response.setRetCode(gateResponse.getRetCode());
                response.setRetMsg(gateResponse.getRetMsg());
                return;
            }
            log.info("IF8A-04 补站调用 fep-dev-server 闸机接口成功, cardId={}, trxType={}, station={}",
                    cardId, upgradeAreaType, upgradeStationCode);
        } catch (Exception e) {
            log.error("IF8A-04 补站调用 fep-dev-server 闸机接口异常", e);
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("调用闸机接口异常: " + e.getMessage());
            return;
        }

        response.setRetCode(TicketErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(TicketErrorCodeEnum.SUCCESS.getMsg());
    }

    /**
     * 根据票卡状态解析允许的补站类型。
     */
    private AllowedTypesResult resolveAllowedTypes(QRCodeStatusEnum statusEnum, String codeStatus) {
        switch (statusEnum) {
            case END_TRIP:
            case SJT_ISSUE:
            case EXIT:
            case EXIT_OVERTIME:
            case SELF_SERVICE_EXIT:
            case UPDATE_FREE:
            case UPDATE_PAY:
                return new AllowedTypesResult("01", "码状态正常，无需更新，请正常刷码");
            case ENTRY:
            case SELF_SERVICE_ENTRY:
                return new AllowedTypesResult("02,03,04", "码状态正常，无需更新，请正常刷码");
            case UPDATE_ENTRY:
                return new AllowedTypesResult("01", "码状态正常，无需更新，请正常刷码");
            default:
                return new AllowedTypesResult("", "当前票卡状态不支持补站，codeStatus=" + codeStatus);
        }
    }

    /**
     * 计算票价。
     *
     * @return 票价字符串，失败返回 null
     */
    private String calculateTicketPrice(String entryStationCode, String exitStationCode) {
        if (!StringUtils.hasText(entryStationCode) || !StringUtils.hasText(exitStationCode)) {
            return null;
        }
        try {
            RequestTicketPriceByStationReqDTO fareRequest = new RequestTicketPriceByStationReqDTO();
            fareRequest.setEntryStationCode(entryStationCode);
            fareRequest.setExitStationCode(exitStationCode);
            RequestTicketPriceByStationResult fareResult = paraClient.requestTicketPriceByStation(fareRequest);
            if (fareResult != null && RET_SUCCESS.equals(fareResult.getRetCode())
                    && StringUtils.hasText(fareResult.getTicketPrice())) {
                log.info("IF8A-04 补出站票价查询成功, entry={}, exit={}, ticketPrice={}",
                        entryStationCode, exitStationCode, fareResult.getTicketPrice());
                return fareResult.getTicketPrice();
            } else {
                log.warn("IF8A-04 补出站票价查询失败, entry={}, exit={}, retCode={}, retMsg={}",
                        entryStationCode, exitStationCode,
                        fareResult != null ? fareResult.getRetCode() : "null",
                        fareResult != null ? fareResult.getRetMsg() : "null");
                return null;
            }
        } catch (Exception e) {
            log.error("IF8A-04 补出站票价查询异常, entry={}, exit={}", entryStationCode, exitStationCode, e);
            return null;
        }
    }

    /**
     * 对十六进制 thirdUserId 进行编码（闸机接口要求）。
     */
    private String encodeHexThirdUserId(String decimalThirdUserId) {
        if (!StringUtils.hasText(decimalThirdUserId)) {
            return decimalThirdUserId;
        }
        try {
            long decimalValue = Long.parseLong(decimalThirdUserId);
            return Long.toHexString(decimalValue).toUpperCase();
        } catch (NumberFormatException e) {
            log.warn("thirdUserId 格式异常，无法转为16进制: {}", decimalThirdUserId);
            return decimalThirdUserId;
        }
    }

    private String defaultString(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    /**
     * 允许的补站类型结果。
     */
    private static class AllowedTypesResult {
        final String allowedTypes;
        final String friendlyMsg;

        AllowedTypesResult(String allowedTypes, String friendlyMsg) {
            this.allowedTypes = allowedTypes;
            this.friendlyMsg = friendlyMsg;
        }
    }
}
