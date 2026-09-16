package com.chinasofti.huateng.ticket.service.impl;

import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import com.chinasofti.huateng.model.app.*;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseRespDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateRespDTO;
import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.alipay.account.AlipayAccountClient;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * IF5A-01/IF5A-03 票卡分析和更新处理。
 *
 * <p>负责处理票卡分析和更新请求，包括：
 * <ul>
 *   <li>IF5A-01 票卡分析：根据票卡状态返回建议操作</li>
 *   <li>IF5A-03 票卡更新：执行补进站/补出站操作</li>
 * </ul>
 */
@Component
public class CardDataHandler {

    private static final Logger log = LoggerFactory.getLogger(CardDataHandler.class);
    private static final String RET_SUCCESS = "0000";
    private static final DateTimeFormatter BIZ_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Value("${ticket.default-code-status:03}")
    private String defaultCodeStatus;

    @Value("${ticket.default-last-txn-station:FFFF}")
    private String defaultLastTxnStation;

    @Autowired
    private QRCodeStatusMapper qrCodeStatusMapper;

    @Autowired
    private ParaClient paraClient;

    @Autowired
    private AccountClient accountClient;

    @Autowired
    private AlipayAccountClient alipayAccountClient;

    @Autowired
    private FepDevClient fepDevClient;

    /**
     * 处理票卡分析请求（IF5A-01）。
     */
    @Transactional(readOnly = true)
    public RequestCardDataAnalyseRespDTO handleCardDataAnalyse(RequestCardDataAnalyseReqDTO request, RequestCardDataAnalyseRespDTO response) {
        String cardId = request.getCardId();

        // 1. 查询票卡状态
        QRCodeStatus status = qrCodeStatusMapper.selectByCardId(cardId);
        if (status == null) {
            response.setRetCode(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getCode());
            response.setRetMsg(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getMsg());
            return response;
        }

        String gateInStation = defaultString(status.getGateInStation(), defaultLastTxnStation);
        String lastTxnStation = defaultString(status.getLastTxnStation(), defaultLastTxnStation);

        // 2. 查询线路信息
        RequestStationLineInfoResult lastStationInfo = null;
        if (!"FFFF".equalsIgnoreCase(lastTxnStation)) {
            RequestStationLineInfoReqDTO lastStationReq = new RequestStationLineInfoReqDTO();
            lastStationReq.setStationCode(lastTxnStation);
            lastStationInfo = paraClient.requestStationLineInfo(lastStationReq);
        }

        RequestStationLineInfoResult gateInStationInfo = null;
        if (!"FFFF".equalsIgnoreCase(gateInStation)) {
            RequestStationLineInfoReqDTO gateInStationReq = new RequestStationLineInfoReqDTO();
            gateInStationReq.setStationCode(gateInStation);
            gateInStationInfo = paraClient.requestStationLineInfo(gateInStationReq);
        }

        String lastLineCode = lastStationInfo != null && StringUtils.hasText(lastStationInfo.getLineCode())
                ? lastStationInfo.getLineCode() : "";
        String lastStationCode = lastTxnStation;

        // 3. 解析建议操作
        String updateType = defaultString(request.getUpdateType(), "00");
        List<String> adviceOpt = resolveAdviceOpt(
                status.getCodeStatusEnum(), gateInStation, lastTxnStation, updateType,
                status.getGateInTime(), cardId
        );
        response.setAdviceOpt(adviceOpt);

        // 4. 计算付费更新金额（006）
        String transAmount = "0";
        if (adviceOpt.contains("006") || adviceOpt.contains("04")) {
            transAmount = calculatePayAmount(status, lastTxnStation);
        }
        response.setTransAmount(transAmount);

        // 5. 查询用户信息
        String msisdn = defaultString(request.getMsisdn(), "");
        String cardIssueDate = "";
        try {
            queryUserInfo(cardId, request.getProviderId(), response);
        } catch (Exception e) {
            log.warn("IF5A-01 查询用户信息失败, cardId={}", cardId, e);
        }

        response.setRetCode(RET_SUCCESS);
        response.setRetMsg("成功");
        response.setProviderId(defaultString(request.getProviderId(), "99"));
        response.setCardIssueDate(cardIssueDate);
        response.setMsisdn(msisdn);
        response.setCardId(cardId);
        response.setCardStatus(defaultString(status.getCodeStatus(), QRCodeStatusEnum.SJT_ISSUE.getCode()));
        response.setLastLineCode(lastLineCode);
        response.setLastStationCode(lastStationCode);
        response.setLastUpdateDate(defaultString(status.getLastTxnTime(), ""));
        response.setLastTransAmout(status.getTrxAmount() == null ? "0" : String.valueOf(status.getTrxAmount()));
        response.setLastTicketTransSeq(defaultString(status.getTxnSeq(), "0"));
        response.setManagerCode(defaultString(request.getManagerCode(), ""));

        return response;
    }

    /**
     * 处理票卡更新请求（IF5A-03）。
     */
    @Transactional(rollbackFor = Exception.class)
    public RequestCardDataUpdateRespDTO handleCardDataUpdate(RequestCardDataUpdateReqDTO request, RequestCardDataUpdateRespDTO response) {
        String cardId = request.getCardId();
        String adviceOpt = request.getAdviceOpt();
        String updateType = defaultString(request.getUpdateType(), "00");
        String transAmount = defaultString(request.getTransAmount(), "0");
        String optDate = request.getOptDate();
        String updateStationCode = request.getUpdateStationCode();

        // 1. 查询当前票卡状态
        QRCodeStatus currentStatus = qrCodeStatusMapper.selectByCardId(cardId);
        if (currentStatus == null) {
            response.setRetCode(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getCode());
            response.setRetMsg(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getMsg());
            return response;
        }

        String codeStatus = currentStatus.getCodeStatus();

        // 2. 记录状态快照（幂等性校验）
        String snapshotCodeStatus = currentStatus.getCodeStatus();
        String snapshotTxnSeq = currentStatus.getTxnSeq();
        log.info("IF5A-03 记录状态快照, cardId={}, codeStatus={}, txnSeq={}, updateTime={}",
                cardId, snapshotCodeStatus, snapshotTxnSeq, currentStatus.getUpdateTime());

        // 3. 校验状态是否允许操作
        if (!isUpdateAllowed(currentStatus.getCodeStatusEnum(), adviceOpt, updateType)) {
            log.warn("IF5A-03 票卡状态不允许此操作, cardId={}, codeStatus={}, adviceOpt={}, updateType={}",
                    cardId, codeStatus, adviceOpt, updateType);
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("票卡状态不允许此操作: codeStatus=" + codeStatus + ", adviceOpt=" + adviceOpt);
            return response;
        }

        // 4. 确定交易类型
        String trxType = resolveTrxType(adviceOpt);
        if (trxType == null) {
            log.warn("IF5A-03 不支持的操作类型, cardId={}, adviceOpt={}", cardId, adviceOpt);
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("不支持的操作类型: " + adviceOpt);
            return response;
        }

        // 5. 查询用户信息
        QueryUserInfoResult userInfo = null;
        String signChannelCode = "";
        String cardType = "";
        String itpUserId = "";
        try {
            userInfo = queryUserInfoForUpdate(cardId);
            signChannelCode = userInfo.getChannel();
            cardType = userInfo.getCardType();
            itpUserId = userInfo.getThirdUserId();
        } catch (Exception e) {
            log.warn("IF5A-03 查询用户信息失败, cardId={}", cardId, e);
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("查询用户信息异常");
            return response;
        }

        // 6. 构建闸机检票请求
        NotifyVerifyResultReqDTO gateRequest = buildGateRequest(
                request, currentStatus, trxType, optDate, updateStationCode,
                signChannelCode, cardType, itpUserId
        );

        // 7. 计算票价（006付费更新）
        String ticketPrice = null;
        if ("006".equals(adviceOpt)) {
            ticketPrice = calculateTicketPrice(
                    defaultString(currentStatus.getGateInStation(), defaultLastTxnStation),
                    updateStationCode
            );
            if (ticketPrice == null) {
                response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("票价查询失败，请稍后重试或前往车站服务台办理");
                return response;
            }
        }
        gateRequest.setTrxAmount(ticketPrice != null ? ticketPrice : "0");

        // 8. 幂等性校验
        QRCodeStatus checkStatus = qrCodeStatusMapper.selectByCardId(cardId);
        if (checkStatus == null) {
            log.warn("IF5A-03 幂等性校验：票卡状态不存在, cardId={}", cardId);
            response.setRetCode(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getCode());
            response.setRetMsg("票卡状态不存在");
            return response;
        }
        if (!snapshotCodeStatus.equals(checkStatus.getCodeStatus())) {
            log.warn("IF5A-03 幂等性校验：codeStatus 已变更, cardId={}, old={}, new={}",
                    cardId, snapshotCodeStatus, checkStatus.getCodeStatus());
            response.setRetCode(RET_SUCCESS);
            response.setRetMsg("票卡状态已变更，请刷新后重试");
            return response;
        }
        if (!defaultString(snapshotTxnSeq, "0").equals(defaultString(checkStatus.getTxnSeq(), "0"))) {
            log.warn("IF5A-03 幂等性校验：txnSeq 已变更, cardId={}, old={}, new={}",
                    cardId, snapshotTxnSeq, checkStatus.getTxnSeq());
            response.setRetCode(RET_SUCCESS);
            response.setRetMsg("票卡状态已变更，请刷新后重试");
            return response;
        }

        log.info("IF5A-03 幂等性校验通过, cardId={}, codeStatus={}, txnSeq={}",
                cardId, checkStatus.getCodeStatus(), checkStatus.getTxnSeq());

        // 9. 调用闸机接口
        try {
            NotifyVerifyResultRespDTO gateResponse = fepDevClient.notifyVerifyResult(gateRequest);
            log.info("IF5A-03 调用闸机检票接口结束, cardId={}, adviceOpt={}, gateResponse={}",
                    cardId, adviceOpt, gateResponse);

            if (gateResponse == null || !RET_SUCCESS.equals(gateResponse.getRetCode())) {
                log.warn("IF5A-03 闸机检票接口调用失败, cardId={}, adviceOpt={}, gateResponse={}",
                        cardId, adviceOpt, gateResponse);
                response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("闸机检票接口调用失败: " + (gateResponse != null ? gateResponse.getRetMsg() : "无响应"));
                return response;
            }
        } catch (Exception e) {
            log.error("IF5A-03 闸机检票接口调用异常, cardId={}, adviceOpt={}", cardId, adviceOpt, e);
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("闸机检票接口调用异常: " + e.getMessage());
            return response;
        }

        response.setRetCode(RET_SUCCESS);
        response.setRetMsg("成功");
        log.info("IF5A-03 票卡更新完成, cardId={}, adviceOpt={}, updateType={}, codeStatus={}, transAmount={}",
                cardId, adviceOpt, updateType, codeStatus, transAmount);

        return response;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 解析建议操作列表。
     */
    public List<String> resolveAdviceOpt(QRCodeStatusEnum codeStatus, String gateInStation, String lastTxnStation,
                                          String updateType, String gateInTime, String cardId) {
        if (codeStatus == null) {
            codeStatus = QRCodeStatusEnum.fromCode(defaultCodeStatus);
        }

        boolean lastTxnIsFFFF = "FFFF".equalsIgnoreCase(lastTxnStation);

        // 闭环状态：02/05/06/80
        if (codeStatus.isClosedLoop()) {
            if ("01".equals(updateType)) {
                logClosedLoopWarn(gateInStation, lastTxnStation, updateType, cardId, codeStatus.getCode());
                return Collections.singletonList("018");
            }
            return Collections.singletonList("000");
        }

        // 开环状态：04/81
        if (codeStatus.isOpenLoop()) {
            if ("01".equals(updateType)) {
                return Collections.singletonList("000");
            }
            if (lastTxnIsFFFF) {
                log.warn("WARN_STATION_FFFF: 开环状态在非付费区且lastStationCode=FFFF, 强制按最低票价计费, gateIn={}, codeStatus={}, cardId={}",
                        gateInStation, codeStatus.getCode(), cardId);
            }
            return Collections.singletonList("006");
        }

        // 新卡状态：03
        if (QRCodeStatusEnum.SJT_ISSUE.equals(codeStatus)) {
            if ("01".equals(updateType)) {
                log.warn("WARN_STATION_FFFF: 新卡在付费区，补进站, gateIn={}, lastTxn={}, updateType={}, cardId={}",
                        gateInStation, lastTxnStation, updateType, cardId);
                return Collections.singletonList("018");
            }
            return Collections.singletonList("000");
        }

        // 更新状态：08/09
        if (QRCodeStatusEnum.UPDATE_FREE.equals(codeStatus) || QRCodeStatusEnum.UPDATE_PAY.equals(codeStatus)) {
            if ("01".equals(updateType)) {
                return Collections.singletonList("018");
            }
            return Collections.singletonList("000");
        }

        // 乘车码状态：10
        if (QRCodeStatusEnum.UPDATE_ENTRY.equals(codeStatus)) {
            if ("01".equals(updateType)) {
                boolean gateInEmpty = defaultLastTxnStation.equalsIgnoreCase(gateInStation);
                if (gateInEmpty) {
                    return Collections.singletonList("018");
                }
                return Collections.singletonList("000");
            }
            if (isWithin20Minutes(gateInTime)) {
                return Collections.singletonList("005");
            }
            return Collections.singletonList("000");
        }

        // 其他未知状态，兜底
        return Collections.singletonList("000");
    }

    /**
     * 判断当前状态是否允许执行建议操作。
     */
    public boolean isUpdateAllowed(QRCodeStatusEnum codeStatus, String adviceOpt, String updateType) {
        if (codeStatus == null) {
            codeStatus = QRCodeStatusEnum.fromCode(defaultCodeStatus);
        }

        // 018 补进站
        if ("018".equals(adviceOpt)) {
            if (codeStatus.isClosedLoop()
                    || QRCodeStatusEnum.UPDATE_FREE.equals(codeStatus)
                    || QRCodeStatusEnum.UPDATE_PAY.equals(codeStatus)) {
                return "01".equals(updateType);
            }
            if (QRCodeStatusEnum.SJT_ISSUE.equals(codeStatus)) {
                return true;
            }
            if (QRCodeStatusEnum.UPDATE_ENTRY.equals(codeStatus)) {
                return "01".equals(updateType);
            }
            return false;
        }

        // 006 补出站/付费更新
        if ("006".equals(adviceOpt)) {
            if (codeStatus.isOpenLoop()) {
                return "00".equals(updateType);
            }
            if (QRCodeStatusEnum.UPDATE_ENTRY.equals(codeStatus)) {
                return "00".equals(updateType);
            }
            return QRCodeStatusEnum.SELF_SERVICE_ENTRY.equals(codeStatus);
        }

        // 005 免费更新
        if ("005".equals(adviceOpt)) {
            return QRCodeStatusEnum.UPDATE_ENTRY.equals(codeStatus);
        }

        return false;
    }

    /**
     * 根据 adviceOpt 解析交易类型。
     */
    private String resolveTrxType(String adviceOpt) {
        if ("018".equals(adviceOpt)) {
            return com.chinasofti.huateng.model.enums.TrxTypeCodeEnum.ENTRY.getCode();
        } else if ("005".equals(adviceOpt) || "006".equals(adviceOpt)) {
            return com.chinasofti.huateng.model.enums.TrxTypeCodeEnum.EXIT.getCode();
        }
        return null;
    }

    /**
     * 计算付费更新金额。
     */
    private String calculatePayAmount(QRCodeStatus status, String lastTxnStation) {
        boolean lastTxnIsFFFF = "FFFF".equalsIgnoreCase(lastTxnStation);
        if (lastTxnIsFFFF) {
            log.warn("WARN_STATION_FFFF: 历史缺出站且lastStationCode=FFFF, 强制按最低票价计费, cardId={}", status.getCardId());
            return "0";
        }
        if (StringUtils.hasText(status.getGateInStation()) && StringUtils.hasText(status.getLastTxnStation())
                && !defaultLastTxnStation.equals(status.getGateInStation())
                && !defaultLastTxnStation.equals(status.getLastTxnStation())) {
            try {
                RequestTicketPriceByStationReqDTO fareReq = new RequestTicketPriceByStationReqDTO();
                fareReq.setEntryStationCode(status.getGateInStation());
                fareReq.setExitStationCode(status.getLastTxnStation());
                RequestTicketPriceByStationResult fareResult = paraClient.requestTicketPriceByStation(fareReq);
                if (fareResult != null && RET_SUCCESS.equals(fareResult.getRetCode())
                        && StringUtils.hasText(fareResult.getTicketPrice())) {
                    return fareResult.getTicketPrice();
                }
            } catch (Exception e) {
                log.warn("IF5A-01 查询票价失败, gateIn={}, lastTxn={}",
                        status.getGateInStation(), status.getLastTxnStation(), e);
            }
        }
        return "0";
    }

    /**
     * 查询用户信息（用于票卡分析）。
     */
    private void queryUserInfo(String cardId, String providerId, RequestCardDataAnalyseRespDTO response) {
        String msisdn = defaultString(response.getMsisdn(), "");
        String cardIssueDate = "";

        QueryUserInfoResult cardTypeResult = accountClient.queryCardTypeByCardId(cardId);
        if (cardTypeResult == null || !RET_SUCCESS.equals(cardTypeResult.getRetCode())
                || !StringUtils.hasText(cardTypeResult.getThirdUserId())) {
            log.warn("IF5A-01 查询用户卡类型失败, cardId={}", cardId);
            return;
        }

        String thirdUserId = cardTypeResult.getThirdUserId();

        if ("07".equals(providerId)) {
            // 支付宝发行方
            AlipayUserInfoDTO alipayUser = alipayAccountClient.selectByThirdUserId(thirdUserId);
            if (alipayUser != null) {
                msisdn = alipayUser.getPhone();
            }
        } else {
            // 其他发行方
            QueryUserInfoReqDTO userInfoReq = new QueryUserInfoReqDTO();
            userInfoReq.setThirdUserId(thirdUserId);
            userInfoReq.setCardId(cardId);
            userInfoReq.setCardType(cardTypeResult.getCardType());
            QueryUserInfoResult detailInfo = accountClient.queryUserInfo(userInfoReq);
            if (detailInfo != null) {
                msisdn = detailInfo.getMsisdn();
                cardIssueDate = detailInfo.getRegTms();
            }
        }

        response.setMsisdn(msisdn);
        response.setCardIssueDate(cardIssueDate);
    }

    /**
     * 查询用户信息（用于票卡更新）。
     */
    private QueryUserInfoResult queryUserInfoForUpdate(String cardId) {
        QueryUserInfoResult cardTypeResult = accountClient.queryCardTypeByCardId(cardId);
        if (cardTypeResult == null) {
            log.warn("IF5A-03 查询用户信息无响应, cardId={}", cardId);
            throw new RuntimeException("查询用户信息无响应");
        }
        if (!RET_SUCCESS.equals(cardTypeResult.getRetCode())) {
            String accountRetCode = cardTypeResult.getRetCode();
            log.warn("IF5A-03 查询用户信息失败, cardId={}, retCode={}", cardId, accountRetCode);
            throw new RuntimeException("查询用户信息失败: " + accountRetCode);
        }
        if (!StringUtils.hasText(cardTypeResult.getThirdUserId())) {
            log.warn("IF5A-03 未注册用户, cardId={}", cardId);
            throw new RuntimeException("未注册用户");
        }

        QueryUserInfoReqDTO userInfoReq = new QueryUserInfoReqDTO();
        userInfoReq.setThirdUserId(cardTypeResult.getThirdUserId());
        userInfoReq.setCardType(cardTypeResult.getCardType());
        userInfoReq.setCardId(cardId);
        QueryUserInfoResult userInfo = accountClient.queryUserInfo(userInfoReq);
        if (userInfo == null || !RET_SUCCESS.equals(userInfo.getRetCode())) {
            log.warn("IF5A-03 查询用户详细信息失败, cardId={}", cardId);
            throw new RuntimeException("查询用户详细信息失败");
        }
        if (!StringUtils.hasText(userInfo.getThirdUserId())) {
            throw new RuntimeException("未注册用户");
        }
        return userInfo;
    }

    /**
     * 构建闸机检票请求。
     */
    private NotifyVerifyResultReqDTO buildGateRequest(
            RequestCardDataUpdateReqDTO request, QRCodeStatus currentStatus,
            String trxType, String optDate, String updateStationCode,
            String signChannelCode, String cardType, String itpUserId) {

        NotifyVerifyResultReqDTO gateRequest = new NotifyVerifyResultReqDTO();
        gateRequest.setDeviceId(defaultString(request.getOperaterId(), ""));
        gateRequest.setItpUserId(encodeHexThirdUserId(itpUserId));
        gateRequest.setTrxType(trxType);
        gateRequest.setIssueChannelCode(defaultString(currentStatus.getChannel(), "01"));
        gateRequest.setSignChannelCode(signChannelCode);
        gateRequest.setCardId(request.getCardId());
        gateRequest.setCardType(cardType);
        gateRequest.setHandleDateTime(optDate);
        gateRequest.setHandleStationCode(updateStationCode);
        gateRequest.setOvertimeAmount("0");
        gateRequest.setLastTicketStatus(defaultString(currentStatus.getCodeStatus(), QRCodeStatusEnum.SJT_ISSUE.getCode()));
        gateRequest.setHandleResultCode("000");
        gateRequest.setLastHandleStationCode(currentStatus.getLastTxnStation());
        gateRequest.setLastHandleDateTime(currentStatus.getLastTxnTime());
        gateRequest.setTicketTransSeq(currentStatus.getTxnSeq() == null ? "0" : currentStatus.getTxnSeq());
        gateRequest.setAdviceOpt(request.getAdviceOpt());
        gateRequest.setReserve1(null);
        gateRequest.setReserve2(null);
        return gateRequest;
    }

    /**
     * 计算票价。
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
                log.info("IF5A-03 付费更新票价查询成功, entry={}, exit={}, ticketPrice={}",
                        entryStationCode, exitStationCode, fareResult.getTicketPrice());
                return fareResult.getTicketPrice();
            } else {
                log.warn("IF5A-03 付费更新票价查询失败, entry={}, exit={}, retCode={}, retMsg={}",
                        entryStationCode, exitStationCode,
                        fareResult != null ? fareResult.getRetCode() : "null",
                        fareResult != null ? fareResult.getRetMsg() : "null");
                return null;
            }
        } catch (Exception e) {
            log.error("IF5A-03 付费更新票价查询异常, entry={}, exit={}", entryStationCode, exitStationCode, e);
            return null;
        }
    }

    /**
     * 判断进站时间是否在 20 分钟内。
     */
    private boolean isWithin20Minutes(String gateInTime) {
        if (!StringUtils.hasText(gateInTime) || gateInTime.length() < 14) {
            return false;
        }
        try {
            LocalDateTime inTime = LocalDateTime.parse(gateInTime, BIZ_TIME_FORMATTER);
            return java.time.Duration.between(inTime, LocalDateTime.now()).toMinutes() <= 20;
        } catch (Exception e) {
            log.warn("解析进站时间失败, gateInTime={}", gateInTime, e);
            return false;
        }
    }

    /**
     * 打印闭环状态警告日志。
     */
    private void logClosedLoopWarn(String gateInStation, String lastTxnStation, String updateType, String cardId, String codeStatus) {
        if ("FFFF".equalsIgnoreCase(lastTxnStation)) {
            log.warn("WARN_STATION_FFFF: 闭环状态codeStatus={}在付费区，补进站, gateIn={}, lastTxn={}, updateType={}, cardId={}",
                    codeStatus, gateInStation, lastTxnStation, updateType, cardId);
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
}
