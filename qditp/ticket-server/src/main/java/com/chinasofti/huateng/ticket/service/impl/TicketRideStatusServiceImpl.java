package com.chinasofti.huateng.ticket.service.impl;

import com.chinasofti.huateng.model.app.*;
import com.chinasofti.huateng.model.ticket.QueryStatusReqDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusRespDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusReqDTO;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusRespDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseRespDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateRespDTO;
import com.chinasofti.huateng.rpc.fepDev.FepDevClient;
import com.chinasofti.huateng.rpc.para.ParaClient;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.alipay.account.AlipayAccountClient;
import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import com.chinasofti.huateng.model.app.UpdateHceDataReqDTO;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import com.chinasofti.huateng.ticket.mapper.QRCodeStatusMapper;
import com.chinasofti.huateng.ticket.mapper.QRCodeTxnDetailMapper;
import com.chinasofti.huateng.ticket.service.AppNotifyService;
import com.chinasofti.huateng.ticket.service.TicketRideStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.math.BigInteger;
import java.util.List;

@Service
public class TicketRideStatusServiceImpl implements TicketRideStatusService {
    private static final Logger log = LoggerFactory.getLogger(TicketRideStatusServiceImpl.class);
    private static final String RET_SUCCESS = "0000";
    private static final String RET_INVALID_PARAM = "8001";
    private static final DateTimeFormatter BIZ_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Value("${ticket.default-channel:01}")
    private String defaultChannel;

    @Value("${ticket.default-code-status:03}")
    private String defaultCodeStatus;

    @Value("${ticket.default-gate-status:00}")
    private String defaultGateStatus;

    @Value("${ticket.default-last-txn-station:FFFF}")
    private String defaultLastTxnStation;

    @Value("${ticket.default-gate-in-station:FFFF}")
    private String defaultGateInStation;

    @Autowired
    private QRCodeStatusMapper qrCodeStatusMapper;

    @Autowired
    private QRCodeTxnDetailMapper qrCodeTxnDetailMapper;

    @Autowired
    private ParaClient paraClient;

    @Autowired
    private AppNotifyService appNotifyService;

    @Autowired
    private AccountClient accountClient;

    @Autowired
    private AlipayAccountClient alipayAccountClient;

    @Autowired
    private com.chinasofti.huateng.rpc.industry.IndustryDataClient industryDataClient;

    @Autowired
    private com.chinasofti.huateng.rpc.pay.GateTxnPayClient gateTxnPayClient;

    @Autowired
    private FepDevClient fepDevClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisterRideStatusRespDTO registerRideStatus(RegisterRideStatusReqDTO request) {
        RegisterRideStatusRespDTO response = new RegisterRideStatusRespDTO();
        if (request == null || !StringUtils.hasText(request.getCardId())) {
            response.setRetCode(RET_INVALID_PARAM);
            response.setRetMsg("cardId不能为空");
            return response;
        }

        LocalDateTime now = LocalDateTime.now();
        QRCodeStatus qrCodeStatus = qrCodeStatusMapper.selectByCardId(request.getCardId());
        if (qrCodeStatus == null) {
            qrCodeStatus = new QRCodeStatus();
            qrCodeStatus.setCardId(request.getCardId());
            qrCodeStatus.setCreateTime(now);
            qrCodeStatus.setUseCount(0);
        }

        qrCodeStatus.setChannel(defaultString(request.getChannel(), defaultChannel));
        qrCodeStatus.setCodeStatus(defaultCodeStatus);
        qrCodeStatus.setLastTxnTime("00000000000000");
        qrCodeStatus.setLastTxnStation(defaultLastTxnStation);
        qrCodeStatus.setTxnSeq("0");
        qrCodeStatus.setGateStatus(defaultGateStatus);
        qrCodeStatus.setGateInStation(defaultGateInStation);
        qrCodeStatus.setGateInTime("00000000000000");
        qrCodeStatus.setUpdateTime(now);
        qrCodeStatusMapper.upsert(qrCodeStatus);

        response.setRetCode(RET_SUCCESS);
        response.setRetMsg("成功");
        response.setCardId(qrCodeStatus.getCardId());
        response.setItpUserId(request.getThirdUserId());
        response.setCardStatus(qrCodeStatus.getCodeStatus());
        return response;
    }

    @Override
    public QueryStatusRespDTO queryQrCodeStatus(QueryStatusReqDTO request) {
        QueryStatusRespDTO response = new QueryStatusRespDTO();
        if (request == null || !StringUtils.hasText(request.getCardId())) {
            response.setRetCode(RET_INVALID_PARAM);
            response.setRetMsg("cardId不能为空");
            return response;
        }

        QRCodeStatus qrCodeStatus = qrCodeStatusMapper.selectByCardId(request.getCardId());
        if (qrCodeStatus == null) {
            response.setRetCode(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getCode());
            response.setRetMsg(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getMsg());
            response.setCardId(request.getCardId());
            response.setThirdUserId(request.getThirdUserId());
            return response;
        }

        response.setThirdUserId(request.getThirdUserId());
        response.setGateInTime(qrCodeStatus.getGateInTime());
        response.setGateInStation(qrCodeStatus.getGateInStation());
        response.setStatus(qrCodeStatus.getCodeStatus());
        response.setCardId(qrCodeStatus.getCardId());
        response.setLastTxnTime(qrCodeStatus.getLastTxnTime());
        response.setLastTxnStation(qrCodeStatus.getLastTxnStation());
        response.setTxnSeq(qrCodeStatus.getTxnSeq());

        response.setRetCode(RET_SUCCESS);
        response.setRetMsg("成功");


        return response;
    }

    /**
     * IF1A-01 闸机检票通知。
     *
     * <p>ticket-server 先查询当前卡状态，用当前状态填充交易明细中的上次交易字段；
     * 再根据本次交易更新卡状态并写入交易明细；入库成功后异步推送 APP。</p>
     *
     * @param request 闸机检票通知业务参数
     * @return 处理结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotifyVerifyResultRespDTO notifyVerifyResult(NotifyVerifyResultReqDTO request) {
        NotifyVerifyResultRespDTO response = new NotifyVerifyResultRespDTO();
        if (request == null || !StringUtils.hasText(request.getCardId())) {
            response.setRetCode(RET_INVALID_PARAM);
            response.setRetMsg("cardId不能为空");
            return response;
        }
        if (!StringUtils.hasText(request.getHandleDateTime()) || request.getHandleDateTime().length() < 8) {
            response.setRetCode(RET_INVALID_PARAM);
            response.setRetMsg("handleDateTime不能为空且长度不能小于8");
            return response;
        }
        if (!StringUtils.hasText(request.getTrxType())) {
            response.setRetCode(RET_INVALID_PARAM);
            response.setRetMsg("trxType不能为空");
            return response;
        }

        QRCodeStatus currentStatus = qrCodeStatusMapper.selectByCardId(request.getCardId());
        if (currentStatus == null) {
            response.setRetCode(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getCode());
            response.setRetMsg(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getMsg());
            return response;
        }

        log.info("IF1A-01 ticket-server 收到闸机检票通知, 请求参数={}", com.alibaba.fastjson.JSON.toJSONString(request));

        applyActualCardType(request);
        fillLastTicketFields(request, currentStatus);

        QRCodeTxnDetail detail = buildTxnDetail(request);
        log.info("IF1A-01 闸机交易明细入库, detail={}", com.alibaba.fastjson.JSON.toJSONString(detail));
        try {
            qrCodeTxnDetailMapper.insert(detail);
        } catch (DuplicateKeyException e) {
            log.info("IF1A-01 闸机交易明细重复上送, cardId={}, trxType={}, handleDateTime={}, ticketTransSeq={}, deviceId={}",
                    request.getCardId(), request.getTrxType(), request.getHandleDateTime(),
                    request.getTicketTransSeq(), request.getDeviceId());
            // 前次账户服务回写失败时，利用闸机重传再次同步最新 HCE 卡数据。
            updateHceDataFromGateTransaction(request);
            response.setRetCode(RET_SUCCESS);
            response.setRetMsg("成功");
            return response;
        }

        QRCodeStatus nextStatus = buildNextStatus(request, currentStatus);
        log.info("IF1A-01 更新二维码状态, status={}", com.alibaba.fastjson.JSON.toJSONString(nextStatus));
        qrCodeStatusMapper.upsert(nextStatus);

        updateHceDataFromGateTransaction(request);

        String issueChannelCode = request.getIssueChannelCode();
        if (!isHceCard(request.getCardType())) {
            appNotifyService.notifyVerifyResult(request, nextStatus);
        } else {
            log.info("IF1A-01 HCE卡不推送行业数据, cardId={}, cardType={}", request.getCardId(), request.getCardType());
        }

        // 支付宝渠道：行业数据推送完成后，推进行程数据
        if ("07".equals(issueChannelCode)) {
            appNotifyService.pushAlipayTripData(request, nextStatus, detail);
        }

        response.setRetCode(RET_SUCCESS);
        response.setRetMsg("成功");
        return response;
    }

    /**
     * IF8A-29 查询用户上次行程。
     *
     * <p>该接口只查询刷码后已经落库的状态和交易明细，不修改票卡状态。</p>
     */
    @Override
    public QueryUserItineraryResult queryUserItinerary(QueryUserItineraryReqDTO request) {
        QueryUserItineraryResult response = new QueryUserItineraryResult();
        if (request == null || !StringUtils.hasText(request.getCardNum())) {
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("cardNum不能为空");
            return response;
        }

        QRCodeStatus currentStatus = qrCodeStatusMapper.selectByCardId(request.getCardNum());
        if (currentStatus == null) {
            response.setRetCode(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getCode());
            response.setRetMsg(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getMsg());
            return response;
        }

        QRCodeTxnDetail latestDetail = qrCodeTxnDetailMapper.selectLatestByCardId(request.getCardNum());
        MemberItineraryDTO itinerary = buildMemberItinerary(currentStatus, latestDetail);
        response.setMemberItinerary(itinerary);
        response.setRetCode(RET_SUCCESS);
        response.setRetMsg("成功");
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequestExcessFareResult requestExcessFare(RequestExcessFareReqDTO request) {
        RequestExcessFareResult response = new RequestExcessFareResult();
        if (request == null || !StringUtils.hasText(request.getCardId())
                || !StringUtils.hasText(request.getUpgradeAreaType())
                || !StringUtils.hasText(request.getUpgradeStationCode())
                || !StringUtils.hasText(request.getUpgradeDateTime())) {
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("cardId/upgradeAreaType/upgradeStationCode/upgradeDateTime不能为空");
            return response;
        }

        /*
         * IF8A-04 自助补站状态机（基于 CODE_STATUS）：
         * 1. 按 cardId 查询现有二维码票卡状态；
         * 2. 根据 codeStatus 判断允许的补站类型：
         *    - 02（结束行程）：允许 01（补进站，可能是跟随进站）
         *    - 03（新卡/初始）：允许 01（补进站）
         *    - 04（已进站）：允许 02/03/04（补出站，可能是跟随出站）
         *    - 05（已出站）：允许 01（补进站）
         *    - 06（超时出站）：允许 01（补进站）
         *    - 08/09/10（20分钟更新/入站码更新）：提示用户到服务台处理
         *    - 80（用户自助补出站）：允许 01（补进站）
         *    - 81（用户自助补进站）：允许 02/03/04（补出站）
         * 3. 无需 FFFFF 校验；
         * 4. 更新交易时间、站点和进出站信息；
         * 5. 通过 upsert 持久化。
         */
        QRCodeStatus currentStatus = qrCodeStatusMapper.selectByCardId(request.getCardId());
        if (currentStatus == null) {
            currentStatus = new QRCodeStatus();
            currentStatus.setCardId(request.getCardId());
            currentStatus.setCreateTime(LocalDateTime.now());
            currentStatus.setUseCount(0);
            currentStatus.setGateInStation(defaultLastTxnStation);
            currentStatus.setLastTxnStation(defaultLastTxnStation);
            currentStatus.setCodeStatus(QRCodeStatusEnum.SJT_ISSUE.getCode());
        }

        String codeStatus = defaultString(currentStatus.getCodeStatus(), QRCodeStatusEnum.SJT_ISSUE.getCode());
        String gateInStation = defaultString(currentStatus.getGateInStation(), defaultLastTxnStation);
        String lastTxnStation = defaultString(currentStatus.getLastTxnStation(), defaultLastTxnStation);
        String upgradeAreaType = request.getUpgradeAreaType();

        // 基于 codeStatus 确定允许的补站类型
        String allowedTypes;
        String friendlyMsg;
        QRCodeStatusEnum statusEnum = QRCodeStatusEnum.fromCode(codeStatus);
        if (statusEnum == null) {
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("当前票卡状态不支持补站，codeStatus=" + codeStatus);
            return response;
        }
        switch (statusEnum) {
            case END_TRIP:
                // 结束行程：允许补进站，不允许补出站
                allowedTypes = "01";
                friendlyMsg = "码状态正常，无需更新，请正常刷码";
                break;
            case SJT_ISSUE:
                // 新卡/初始状态：允许补进站，不允许补出站
                allowedTypes = "01";
                friendlyMsg = "码状态正常，无需更新，请正常刷码";
                break;
            case ENTRY:
                // 已进站：允许补出站，不允许补进站
                allowedTypes = "02,03,04";
                friendlyMsg = "码状态正常，无需更新，请正常刷码";
                break;
            case EXIT:
                // 已出站：允许补进站，不允许补出站
                allowedTypes = "01";
                friendlyMsg = "码状态正常，无需更新，请正常刷码";
                break;
            case EXIT_OVERTIME:
                // 超时出站：允许补进站，不允许补出站
                allowedTypes = "01";
                friendlyMsg = "码状态正常，无需更新，请正常刷码";
                break;
            case SELF_SERVICE_EXIT:
                // 用户自助补出站：允许补进站，不允许补出站
                allowedTypes = "01";
                friendlyMsg = "码状态正常，无需更新，请正常刷码";
                break;
            case SELF_SERVICE_ENTRY:
                // 用户自助补进站：允许补出站，不允许补进站
                allowedTypes = "02,03,04";
                friendlyMsg = "码状态正常，无需更新，请正常刷码";
                break;
            case UPDATE_FREE:
                // 20分钟免费更新：允许补进站，不允许补出站
                allowedTypes = "01";
                friendlyMsg = "码状态正常，无需更新，请正常刷码";
                break;
            case UPDATE_PAY:
                // 20分钟付费更新：允许补进站，不允许补出站
                allowedTypes = "01";
                friendlyMsg = "码状态正常，无需更新，请正常刷码";
                break;
            case UPDATE_ENTRY:
                // 入站码更新：码状态正常，无需更新
                response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("码状态正常，无需更新，请正常刷码");
                return response;
            default:
                response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("当前票卡状态不支持补站，codeStatus=" + codeStatus);
                return response;
        }

        if (!allowedTypes.contains(upgradeAreaType)) {
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg(friendlyMsg);
            return response;
        }

        // 业务规则判断通过后，组装闸机接口参数
        NotifyVerifyResultReqDTO bizData = new NotifyVerifyResultReqDTO();
        bizData.setDeviceId(request.getUpgradeStationCode() + "36" + "01");
        bizData.setItpUserId(encodeHexThirdUserId(request.getThirdUserId()));
        bizData.setTrxType(upgradeAreaType);
        bizData.setIssueChannelCode(defaultString(currentStatus.getChannel(), "01"));
        bizData.setSignChannelCode("");
        bizData.setCardId(request.getCardId());
        bizData.setCardType(request.getCardType());
        bizData.setHandleDateTime(request.getUpgradeDateTime());
        bizData.setHandleStationCode(request.getUpgradeStationCode());
        bizData.setOvertimeAmount("0");
        bizData.setLastTicketStatus(defaultString(currentStatus.getCodeStatus(), QRCodeStatusEnum.SJT_ISSUE.getCode()));
        bizData.setHandleResultCode("000");
        bizData.setLastHandleStationCode(currentStatus.getLastTxnStation());
        bizData.setLastHandleDateTime(currentStatus.getLastTxnTime());
        bizData.setTicketTransSeq(currentStatus.getTxnSeq() == null ? "0" : currentStatus.getTxnSeq());
        bizData.setExcessFareType(upgradeAreaType);
        bizData.setReserve1(null);
        bizData.setReserve2(null);

        // 只有补出站（02）才计算票价和收费，补进站（01）不涉及。
        // 票价查询和扣费必须在调用闸机接口之前，因为闸机会更新票卡状态。
        String ticketPrice = null;
        if ("02".equals(upgradeAreaType)) {
            String entryStationCode = defaultString(currentStatus.getGateInStation(), defaultLastTxnStation);
            String exitStationCode = request.getUpgradeStationCode();
            if (StringUtils.hasText(entryStationCode) && StringUtils.hasText(exitStationCode)) {
                try {
                    RequestTicketPriceByStationReqDTO fareRequest = new RequestTicketPriceByStationReqDTO();
                    fareRequest.setEntryStationCode(entryStationCode);
                    fareRequest.setExitStationCode(exitStationCode);
                    RequestTicketPriceByStationResult fareResult = paraClient.requestTicketPriceByStation(fareRequest);
                    if (fareResult != null && RET_SUCCESS.equals(fareResult.getRetCode())
                            && StringUtils.hasText(fareResult.getTicketPrice())) {
                        ticketPrice = fareResult.getTicketPrice();
                        log.info("IF8A-04 补出站票价查询成功, entry={}, exit={}, ticketPrice={}",
                                entryStationCode, exitStationCode, ticketPrice);
                    } else {
                        log.warn("IF8A-04 补出站票价查询失败, entry={}, exit={}, retCode={}, retMsg={}",
                                entryStationCode, exitStationCode,
                                fareResult != null ? fareResult.getRetCode() : "null",
                                fareResult != null ? fareResult.getRetMsg() : "null");
                        response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
                        response.setRetMsg("票价查询失败，请稍后重试或前往车站服务台办理");
                        return response;
                    }
                } catch (Exception e) {
                    log.error("IF8A-04 补出站票价查询异常, entry={}, exit={}", entryStationCode, exitStationCode, e);
                    response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
                    response.setRetMsg("票价查询异常，请稍后重试或前往车站服务台办理");
                    return response;
                }
            }
        }

        // 调用闸机接口时传入实际票价
        bizData.setTrxAmount(ticketPrice != null ? ticketPrice : "0");
        bizData.setOvertimeAmount("0");
        try {
            NotifyVerifyResultRespDTO gateResponse = fepDevClient.notifyVerifyResult(bizData);
            if (!RET_SUCCESS.equals(gateResponse.getRetCode())) {
                response.setRetCode(gateResponse.getRetCode());
                response.setRetMsg(gateResponse.getRetMsg());
                return response;
            }
            log.info("IF8A-04 补站调用 fep-dev-server 闸机接口成功, cardId={}, trxType={}, station={}",
                    request.getCardId(), upgradeAreaType, request.getUpgradeStationCode());
        } catch (Exception e) {
            log.error("IF8A-04 补站调用 fep-dev-server 闸机接口异常", e);
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("调用闸机接口异常: " + e.getMessage());
            return response;
        }

        response.setRetCode(TicketErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(TicketErrorCodeEnum.SUCCESS.getMsg());
        return response;
    }

    private MemberItineraryDTO buildMemberItinerary(QRCodeStatus currentStatus, QRCodeTxnDetail latestDetail) {
        MemberItineraryDTO itinerary = new MemberItineraryDTO();
        itinerary.setTicketStatus(currentStatus.getCodeStatus());
        itinerary.setPayStatus("00");

        if (latestDetail == null) {
            itinerary.setThisStationCode(currentStatus.getLastTxnStation());
            itinerary.setThisStationName(resolveStationName(currentStatus.getLastTxnStation()));
            itinerary.setThisTransTime(currentStatus.getLastTxnTime());
            itinerary.setTransSeq(currentStatus.getTxnSeq());
            return itinerary;
        }

        itinerary.setThisStationCode(latestDetail.getHandleStationCode());
        itinerary.setThisStationName(resolveStationName(latestDetail.getHandleStationCode()));
        itinerary.setThisTransTime(latestDetail.getHandleDateTime());
        itinerary.setTransSeq(latestDetail.getTicketTransSeq());
        itinerary.setTransValue(toInteger(latestDetail.getTrxAmount()));
        itinerary.setOvertimeTransValue(toInteger(latestDetail.getOvertimeAmount()));
        itinerary.setPayChannel(latestDetail.getSignChannelCode());
        itinerary.setOriTicketAmt(toInteger(latestDetail.getTrxAmount()));
        itinerary.setDebitAmt(toInteger(defaultLong(latestDetail.getTrxAmount()) + defaultLong(latestDetail.getOvertimeAmount())));
        itinerary.setOrderExpType(0);
        itinerary.setDiscountInfo("");
        itinerary.setCarbonDiscount(0);

        if (isExitTxn(latestDetail.getTrxType())) {
            itinerary.setLastStationCode(latestDetail.getLastHandleStationCode());
            itinerary.setLastStationName(resolveStationName(latestDetail.getLastHandleStationCode()));
            itinerary.setLastTransTime(latestDetail.getLastHandleDateTime());
        }
        return itinerary;
    }

    private String resolveStationName(String stationCode) {
        if (!StringUtils.hasText(stationCode)) {
            return stationCode;
        }
        RequestStationNameReqDTO request = new RequestStationNameReqDTO();
        request.setStationCode(stationCode);
        RequestStationNameResult result;
        try {
            result = paraClient.requestStationName(request);
        } catch (Exception e) {
            log.warn("调用参数服务查询车站名称失败，按车站代码返回, stationCode={}", stationCode, e);
            return stationCode;
        }
        if (result == null || !RET_SUCCESS.equals(result.getRetCode()) || !StringUtils.hasText(result.getStationName())) {
            return stationCode;
        }
        return result.getStationName();
    }

    private boolean isExitTxn(String trxType) {
        return "02".equals(trxType) || "03".equals(trxType);
    }

    private Integer toInteger(Long value) {
        return value == null ? null : value.intValue();
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private void fillLastTicketFields(NotifyVerifyResultReqDTO request, QRCodeStatus currentStatus) {
        request.setLastTicketStatus(currentStatus.getCodeStatus());
        request.setLastHandleStationCode(currentStatus.getLastTxnStation());
        if (!StringUtils.hasText(request.getLastHandleDateTime())) {
            request.setLastHandleDateTime(currentStatus.getLastTxnTime());
        }
    }

    /**
     * HCE 卡闸机交易完成后，将 IF1A-01 reserve1 的 64 字节卡数据回写到账户服务。
     * 回写失败不影响已完成的交易明细入库和票卡状态更新，闸机重试可再次触发回写。
     */
    private void updateHceDataFromGateTransaction(NotifyVerifyResultReqDTO request) {
        if (!isHceCard(request.getCardType()) || !StringUtils.hasText(request.getReserve1())) {
            return;
        }
        try {
            UpdateHceDataReqDTO updateRequest = new UpdateHceDataReqDTO();
            updateRequest.setCardId(request.getCardId());
            updateRequest.setHceData(request.getReserve1().trim());
            accountClient.updateHceData(updateRequest);
        } catch (Exception e) {
            log.error("IF1A-01 回写HCE卡数据失败, cardId={}", request.getCardId(), e);
        }
    }

    /**
     * {@code 0442} 为 HCE 卡，{@code 0443} 为新版 HCE 卡。
     */
    private boolean isHceCard(String cardType) {
        return StringUtils.hasText(cardType)
                && ("0442".equals(cardType.trim()) || "0443".equals(cardType.trim()));
    }

    /**
     * 闸机行业数据中的 cardType 对日票、员工票均为 0441，不能直接作为交易真实卡种入库。
     */
    private void applyActualCardType(NotifyVerifyResultReqDTO request) {
        try {
            QueryUserInfoResult cardTypeResult = accountClient.queryCardTypeByCardId(request.getCardId());
            if (cardTypeResult == null || !RET_SUCCESS.equals(cardTypeResult.getRetCode())
                    || !StringUtils.hasText(cardTypeResult.getCardType())) {
                log.warn("IF1A-01 查询真实卡类型失败，保留闸机上送卡类型, cardId={}, retCode={}, retMsg={}",
                        request.getCardId(),
                        cardTypeResult == null ? null : cardTypeResult.getRetCode(),
                        cardTypeResult == null ? null : cardTypeResult.getRetMsg());
                return;
            }

            String actualCardType = cardTypeResult.getCardType().trim();
            request.setCardType(actualCardType);
            if (isEmployeeCard(actualCardType)) {
                // 员工票免费乘车，交易记录与后续推送均不得保留闸机计算的费用。
                request.setTrxAmount("0");
                request.setOvertimeAmount("0");
                log.info("IF1A-01 员工票免扣费, cardId={}, cardType={}", request.getCardId(), actualCardType);
            }
        } catch (Exception e) {
            log.warn("IF1A-01 查询真实卡类型异常，保留闸机上送卡类型, cardId={}", request.getCardId(), e);
        }
    }

    private boolean isEmployeeCard(String cardType) {
        return StringUtils.hasText(cardType)
                && ("11".equals(cardType.trim()) || "0444".equals(cardType.trim()));
    }

    private QRCodeTxnDetail buildTxnDetail(NotifyVerifyResultReqDTO request) {
        QRCodeTxnDetail detail = new QRCodeTxnDetail();
        detail.setDeviceId(request.getDeviceId());
        detail.setItpUserId(request.getItpUserId());
        detail.setTrxType(request.getTrxType());
        detail.setIssueChannelCode(request.getIssueChannelCode());
        detail.setSignChannelCode(request.getSignChannelCode());
        detail.setCardId(request.getCardId());
        detail.setCardType(request.getCardType());
        detail.setHandleDateTime(request.getHandleDateTime());
        detail.setTxnDate(request.getHandleDateTime().substring(0, 8));
        detail.setHandleStationCode(request.getHandleStationCode());
        detail.setTrxAmount(parseAmount(request.getTrxAmount()));
        detail.setOvertimeAmount(parseAmount(request.getOvertimeAmount()));
        detail.setLastTicketStatus(request.getLastTicketStatus());
        detail.setHandleResultCode(request.getHandleResultCode());
        detail.setLastHandleStationCode(request.getLastHandleStationCode());
        detail.setLastHandleDateTime(request.getLastHandleDateTime());
        detail.setTicketTransSeq(request.getTicketTransSeq());
        detail.setReserve1(request.getReserve1());
        detail.setReserve2(request.getReserve2());
        detail.setCreateTime(LocalDateTime.now());
        return detail;
    }

    @Override
    public String queryEntryDevice(String cardId) {
        if (!StringUtils.hasText(cardId)) {
            return null;
        }
        try {
            QRCodeTxnDetail entryDetail = qrCodeTxnDetailMapper.selectLatestEntryByCardId(cardId);
            return entryDetail != null ? entryDetail.getDeviceId() : null;
        } catch (Exception e) {
            log.error("查询进站设备异常, cardId={}", cardId, e);
            return null;
        }
    }

    private QRCodeStatus buildNextStatus(NotifyVerifyResultReqDTO request, QRCodeStatus currentStatus) {
        QRCodeStatus nextStatus = new QRCodeStatus();
        nextStatus.setCardId(request.getCardId());
        nextStatus.setUseCount(currentStatus.getUseCount() == null ? 1 : currentStatus.getUseCount() + 1);
        nextStatus.setChannel(defaultString(request.getIssueChannelCode(), currentStatus.getChannel()));
        nextStatus.setCodeStatus(resolveCodeStatus(request.getTrxType(), request.getExcessFareType()));
        nextStatus.setLastTxnTime(request.getHandleDateTime());
        nextStatus.setLastTxnStation(request.getHandleStationCode());
        if ("01".equals(request.getTrxType())) {
            nextStatus.setTxnSeq(currentStatus.getTxnSeq());
        } else {
            nextStatus.setTxnSeq(incrementTxnSeq(currentStatus.getTxnSeq()));
        }
        nextStatus.setCreateTime(currentStatus.getCreateTime());
        nextStatus.setUpdateTime(LocalDateTime.now());
        nextStatus.setGateStatus(request.getTrxType());

        if ("01".equals(request.getTrxType())) {
            nextStatus.setGateInTime(request.getHandleDateTime());
            nextStatus.setGateInStation(request.getHandleStationCode());
        } else {
            nextStatus.setGateInTime(currentStatus.getGateInTime());
            nextStatus.setGateInStation(currentStatus.getGateInStation());
        }

        // 出站时记录实际交易金额
        if (!"01".equals(request.getTrxType())) {
            nextStatus.setTrxAmount(parseAmount(request.getTrxAmount()));
        } else {
            nextStatus.setTrxAmount(currentStatus.getTrxAmount());
        }
        return nextStatus;
    }

    private String incrementTxnSeq(String txnSeq) {
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

    private String resolveCodeStatus(String trxType, String excessFareType) {
        // 补进站：codeStatus = 81（用户自助补进站）
        if ("01".equals(excessFareType)) {
            return QRCodeStatusEnum.SELF_SERVICE_ENTRY.getCode();
        }
        // 补出站：codeStatus = 80（用户自助补出站）
        if ("02".equals(excessFareType)) {
            return QRCodeStatusEnum.SELF_SERVICE_EXIT.getCode();
        }
        // 真实检票：按原有逻辑
        if ("01".equals(trxType)) {
            return QRCodeStatusEnum.ENTRY.getCode();
        }
        if ("02".equals(trxType)) {
            return QRCodeStatusEnum.EXIT.getCode();
        }
        if ("03".equals(trxType)) {
            return QRCodeStatusEnum.EXIT_OVERTIME.getCode();
        }
        return QRCodeStatusEnum.fromCode(defaultCodeStatus).getCode();
    }

    private QRCodeStatusEnum resolveBomCodeStatus(String adviceOpt) {
        if ("018".equals(adviceOpt)) {
            return QRCodeStatusEnum.UPDATE_ENTRY;
        }
        if ("005".equals(adviceOpt)) {
            return QRCodeStatusEnum.UPDATE_FREE;
        }
        if ("006".equals(adviceOpt)) {
            return QRCodeStatusEnum.UPDATE_PAY;
        }
        return QRCodeStatusEnum.fromCode(defaultCodeStatus);
    }

    private Long parseAmount(String amount) {
        if (!StringUtils.hasText(amount)) {
            return null;
        }
        return Long.valueOf(amount);
    }

    private String formatBizTime(LocalDateTime dateTime) {
        return dateTime.format(BIZ_TIME_FORMATTER);
    }

    private String defaultString(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    /**
     * 将十进制 thirdUserId 转为16进制字符串（闸机接口要求）
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

    @Override
    public RequestCardDataAnalyseRespDTO requestCardDataAnalyse(RequestCardDataAnalyseReqDTO request) {
        RequestCardDataAnalyseRespDTO response = new RequestCardDataAnalyseRespDTO();
        if (request == null || !StringUtils.hasText(request.getCardId())) {
            response.setRetCode(RET_INVALID_PARAM);
            response.setRetMsg("cardId不能为空");
            return response;
        }

        String cardId = request.getCardId();
        QRCodeStatus status = qrCodeStatusMapper.selectByCardId(cardId);
        if (status == null) {
            response.setRetCode(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getCode());
            response.setRetMsg(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getMsg());
            return response;
        }

        String gateInStation = defaultString(status.getGateInStation(), defaultLastTxnStation);
        String lastTxnStation = defaultString(status.getLastTxnStation(), defaultLastTxnStation);

        // FFFFF 是默认车站编码，无需查询线路信息
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

        // 付费区标：00 非付费区，01 付费区
        String updateType = defaultString(request.getUpdateType(), "00");
        List<String> adviceOpt = resolveAdviceOpt(status.getCodeStatusEnum(), gateInStation, lastTxnStation, updateType, status.getGateInTime(), cardId);
        response.setAdviceOpt(adviceOpt);

        // 20分付费更新金额：仅当建议操作包含付费更新类型时查询票价
        String transAmount = "0";
        if (adviceOpt.contains("006") || adviceOpt.contains("04")) {
            // FFFF 前置校验：若 lastStationCode=FFFF，无法计算实际票价，强制按线网最低票价扣费
            boolean lastTxnIsFFFF = "FFFF".equalsIgnoreCase(status.getLastTxnStation());
            if (lastTxnIsFFFF) {
                log.warn("WARN_STATION_FFFF: 历史缺出站且lastStationCode=FFFF, 强制按最低票价计费, cardId={}", cardId);
                transAmount = "0"; // 线网最低票价，待确认具体值
            } else if (StringUtils.hasText(status.getGateInStation()) && StringUtils.hasText(status.getLastTxnStation())
                    && !defaultLastTxnStation.equals(status.getGateInStation())
                    && !defaultLastTxnStation.equals(status.getLastTxnStation())) {
                try {
                    RequestTicketPriceByStationReqDTO fareReq = new RequestTicketPriceByStationReqDTO();
                    fareReq.setEntryStationCode(status.getGateInStation());
                    fareReq.setExitStationCode(status.getLastTxnStation());
                    RequestTicketPriceByStationResult fareResult = paraClient.requestTicketPriceByStation(fareReq);
                    if (fareResult != null && RET_SUCCESS.equals(fareResult.getRetCode())
                            && StringUtils.hasText(fareResult.getTicketPrice())) {
                        transAmount = fareResult.getTicketPrice();
                    }
                } catch (Exception e) {
                    log.warn("IF5A-01 查询票价失败, gateIn={}, lastTxn={}",
                            status.getGateInStation(), status.getLastTxnStation(), e);
                }
            }
        }
        response.setTransAmount(transAmount);

        // 查询用户信息，获取 msisdn 和 注册时间（作为 cardIssueDate）
        String msisdn = defaultString(request.getMsisdn(), "");
        String cardIssueDate = "";
        try {
            String providerId = request.getProviderId();
            if ("07".equals(providerId)) {
                // 支付宝发行方，查询 alipay-account 用户信息
                QueryUserInfoResult userInfo = accountClient.queryCardTypeByCardId(cardId);
                if (userInfo != null && RET_SUCCESS.equals(userInfo.getRetCode()) && StringUtils.hasText(userInfo.getThirdUserId())) {
                    AlipayUserInfoDTO alipayUser = alipayAccountClient.selectByThirdUserId(userInfo.getThirdUserId());
                    if (alipayUser != null) {
                        msisdn = alipayUser.getPhone();
                        // 支付宝用户暂无注册时间，留空
                    }
                }
            } else {
                // 其他发行方，查询 account 用户信息
                QueryUserInfoResult userInfo = accountClient.queryCardTypeByCardId(cardId);
                if (userInfo != null && RET_SUCCESS.equals(userInfo.getRetCode()) && StringUtils.hasText(userInfo.getThirdUserId())) {
                    com.chinasofti.huateng.model.app.QueryUserInfoReqDTO userInfoReq = new com.chinasofti.huateng.model.app.QueryUserInfoReqDTO();
                    userInfoReq.setThirdUserId(userInfo.getThirdUserId());
                    userInfoReq.setCardId(userInfo.getCardId());
                    userInfoReq.setCardType(userInfo.getCardType());
                    QueryUserInfoResult detailInfo = accountClient.queryUserInfo(userInfoReq);
                    if (detailInfo != null) {
                        msisdn = detailInfo.getMsisdn();
                        cardIssueDate = detailInfo.getRegTms();
                    }
                }
            }
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
     * 根据进出站状态和付费区标解析建议操作类型。
     *
     * <p>adviceOpt 值：
     * <ul>
     *   <li>000：无需更新</li>
     *   <li>018：补进站（无法出站）</li>
     *   <li>006：补出站（最低票价，无法进站）</li>
     *   <li>005：20分免费进站更新（无法进站）</li>
     * </ul>
     * </p>
     *
     * <p>FFFF 处理规范：
     * <ul>
     *   <li>所有涉及 lastStationCode 的判断，必须先执行 FFFF 校验</li>
     *   <li>FFFF 校验忽略大小写</li>
     *   <li>FFFF 场景需增加 WARN_STATION_FFFF 日志标记</li>
     * </ul>
     * </p>
     */
    private List<String> resolveAdviceOpt(QRCodeStatusEnum codeStatus, String gateInStation, String lastTxnStation,
                                          String updateType, String gateInTime, String cardId) {
        if (codeStatus == null) {
            codeStatus = QRCodeStatusEnum.fromCode(defaultCodeStatus);
        }

        boolean gateInEmpty = defaultLastTxnStation.equalsIgnoreCase(gateInStation);
        boolean lastTxnEmpty = defaultLastTxnStation.equalsIgnoreCase(lastTxnStation);
        boolean lastTxnIsFFFF = "FFFF".equalsIgnoreCase(lastTxnStation);
        boolean gateInIsFFFF = "FFFF".equalsIgnoreCase(gateInStation);

        // 闭环状态：02/05/06/80
        if (codeStatus.isClosedLoop()) {
            if ("01".equals(updateType)) {
                logClosedLoopWarn(gateInStation, lastTxnStation, updateType, cardId, codeStatus.getCode());
                return java.util.Collections.singletonList("018");
            }
            return java.util.Collections.singletonList("000");
        }

        // 开环状态：04/81
        if (codeStatus.isOpenLoop()) {
            if ("01".equals(updateType)) {
                return java.util.Collections.singletonList("000");
            }
            if (lastTxnIsFFFF) {
                log.warn("WARN_STATION_FFFF: 开环状态在非付费区且lastStationCode=FFFF, 强制按最低票价计费, gateIn={}, codeStatus={}, cardId={}",
                        gateInStation, codeStatus.getCode(), cardId);
            }
            return java.util.Collections.singletonList("006");
        }

        // 新卡状态：03
        if (QRCodeStatusEnum.SJT_ISSUE.equals(codeStatus)) {
            if ("01".equals(updateType)) {
                log.warn("WARN_STATION_FFFF: 新卡在付费区，补进站, gateIn={}, lastTxn={}, updateType={}, cardId={}",
                        gateInStation, lastTxnStation, updateType, cardId);
                return java.util.Collections.singletonList("018");
            }
            return java.util.Collections.singletonList("000");
        }

        // 更新状态：08/09
        if (QRCodeStatusEnum.UPDATE_FREE.equals(codeStatus) || QRCodeStatusEnum.UPDATE_PAY.equals(codeStatus)) {
            if (isWithin20Minutes(gateInTime)) {
                return java.util.Collections.singletonList("005");
            }
            return java.util.Collections.singletonList("006");
        }

        // 乘车码状态：10
        if (QRCodeStatusEnum.UPDATE_ENTRY.equals(codeStatus)) {
            if ("01".equals(updateType)) {
                if (gateInEmpty) {
                    return java.util.Collections.singletonList("018");
                }
                return java.util.Collections.singletonList("000");
            }
            if (lastTxnIsFFFF) {
                log.warn("WARN_STATION_FFFF: 乘车码状态在非付费区且lastStationCode=FFFF, 强制按最低票价计费, cardId={}", cardId);
            }
            return java.util.Collections.singletonList("006");
        }

        // 其他未知状态，兜底
        return java.util.Collections.singletonList("000");
    }

    private void logClosedLoopWarn(String gateInStation, String lastTxnStation, String updateType, String cardId, String codeStatus) {
        if ("FFFF".equalsIgnoreCase(lastTxnStation)) {
            log.warn("WARN_STATION_FFFF: 闭环状态codeStatus={}在付费区，补进站, gateIn={}, lastTxn={}, updateType={}, cardId={}",
                    codeStatus, gateInStation, lastTxnStation, updateType, cardId);
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
     * 判断当前 CODE_STATUS 是否允许执行建议操作。
     *
     * <p>校验规则基于 AFC 系统 BOM 票卡状态更新矩阵（v6.0 终版）：
     * <ul>
     *   <li>018 补进站：闭环状态(02/05/06/80)仅在付费区允许；新卡(03)/乘车码(10)允许</li>
     *   <li>006 补出站：开环状态(04/81)仅在非付费区允许；更新状态(08/09)/自助补进站(81)允许</li>
     *   <li>005 免费更新：仅更新状态(08/09)允许</li>
     * </ul>
     * </p>
     */
    private boolean isUpdateAllowed(QRCodeStatusEnum codeStatus, String adviceOpt, String updateType) {
        if (codeStatus == null) {
            codeStatus = QRCodeStatusEnum.fromCode(defaultCodeStatus);
        }

        // 018 补进站
        if ("018".equals(adviceOpt)) {
            // 闭环状态只在付费区允许 018
            if (codeStatus.isClosedLoop()) {
                return "01".equals(updateType);
            }
            // 新卡允许
            if (QRCodeStatusEnum.SJT_ISSUE.equals(codeStatus)) {
                return true;
            }
            // 乘车码仅在付费区允许 018
            if (QRCodeStatusEnum.UPDATE_ENTRY.equals(codeStatus)) {
                return "01".equals(updateType);
            }
            return false;
        }

        // 006 补出站
        if ("006".equals(adviceOpt)) {
            // 开环状态只在非付费区允许 006
            if (codeStatus.isOpenLoop()) {
                return "00".equals(updateType);
            }
            // 更新状态/自助补进站允许
            return QRCodeStatusEnum.UPDATE_FREE.equals(codeStatus)
                    || QRCodeStatusEnum.UPDATE_PAY.equals(codeStatus)
                    || QRCodeStatusEnum.SELF_SERVICE_ENTRY.equals(codeStatus);
        }

        // 005 免费更新
        if ("005".equals(adviceOpt)) {
            return QRCodeStatusEnum.UPDATE_FREE.equals(codeStatus)
                    || QRCodeStatusEnum.UPDATE_PAY.equals(codeStatus);
        }

        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequestCardDataUpdateRespDTO requestCardDataUpdate(RequestCardDataUpdateReqDTO request) {
        RequestCardDataUpdateRespDTO response = new RequestCardDataUpdateRespDTO();

        // ==================== 参数提取 ====================
        String cardId = request.getCardId();
        String adviceOpt = request.getAdviceOpt();
        String updateType = defaultString(request.getUpdateType(), "00");
        String transAmount = defaultString(request.getTransAmount(), "0");
        String optDate = request.getOptDate();
        String updateStationCode = request.getUpdateStationCode();
        String signChannelCode = "";
        String cardType = "";
        String itpUserId = "";

        // ==================== 参数校验 ====================
        if (request == null || !StringUtils.hasText(cardId)) {
            log.warn("IF5A-03 参数校验失败, cardId为空");
            response.setRetCode(RET_SUCCESS);
            response.setRetMsg("cardId不能为空");
            return response;
        }
        if (!StringUtils.hasText(adviceOpt)) {
            log.warn("IF5A-03 参数校验失败, adviceOpt为空, cardId={}", cardId);
            response.setRetCode(RET_SUCCESS);
            response.setRetMsg("adviceOpt不能为空");
            return response;
        }
        if (!StringUtils.hasText(updateStationCode)) {
            log.warn("IF5A-03 参数校验失败, updateStationCode为空, cardId={}", cardId);
            response.setRetCode(RET_SUCCESS);
            response.setRetMsg("updateStationCode不能为空");
            return response;
        }
        if (!StringUtils.hasText(optDate)) {
            log.warn("IF5A-03 参数校验失败, optDate为空, cardId={}", cardId);
            response.setRetCode(RET_SUCCESS);
            response.setRetMsg("optDate不能为空");
            return response;
        }

        log.info("IF5A-03 请求票卡更新开始, cardId={}, adviceOpt={}, updateType={}, transAmount={}, updateStationCode={}, optDate={}",
                cardId, adviceOpt, updateType, transAmount, updateStationCode, optDate);

        // ==================== 查询当前票卡状态 ====================
        QRCodeStatus currentStatus = qrCodeStatusMapper.selectByCardId(cardId);
        if (currentStatus == null) {
            log.warn("IF5A-03 票卡状态不存在, cardId={}", cardId);
            response.setRetCode(RET_SUCCESS);
            response.setRetMsg(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getMsg());
            return response;
        }

        String codeStatus = currentStatus.getCodeStatus();
        QRCodeStatusEnum codeStatusEnum = currentStatus.getCodeStatusEnum();
        String gateInStation = currentStatus.getGateInStation();
        String lastTxnStation = currentStatus.getLastTxnStation();
        String gateInTime = currentStatus.getGateInTime();

        log.info("IF5A-03 当前票卡状态, cardId={}, codeStatus={}, gateInStation={}, lastTxnStation={}, gateInTime={}",
                cardId, codeStatus, gateInStation, lastTxnStation, gateInTime);

        // ==================== 校验状态是否允许操作 ====================
        if (!isUpdateAllowed(codeStatusEnum, adviceOpt, updateType)) {
            log.warn("IF5A-03 票卡状态不允许此操作, cardId={}, codeStatus={}, adviceOpt={}, updateType={}",
                    cardId, codeStatus, adviceOpt, updateType);
            response.setRetCode(RET_SUCCESS);
            response.setRetMsg("票卡状态不允许此操作: codeStatus=" + codeStatus + ", adviceOpt=" + adviceOpt);
            return response;
        }

        log.info("IF5A-03 状态校验通过, cardId={}, codeStatus={}, adviceOpt={}, updateType={}",
                cardId, codeStatus, adviceOpt, updateType);

        // ==================== 确定交易类型 ====================
        String trxType;
        if ("018".equals(adviceOpt)) {
            // 补进站 → trxType=01
            trxType = "01";
            log.info("IF5A-03 执行补进站, cardId={}, 当前codeStatus={}, 进站站点={}, 进站时间={}",
                    cardId, codeStatus, updateStationCode, optDate);
        } else if ("005".equals(adviceOpt)) {
            // 005免费更新 → trxType=02
            trxType = "02";
            log.info("IF5A-03 执行免费更新, cardId={}, 当前codeStatus={}, 出站站点={}, 出站时间={}, 票价={}",
                    cardId, codeStatus, updateStationCode, optDate, currentStatus.getTrxAmount());
        } else if ("006".equals(adviceOpt)) {
            // 006付费更新 → trxType=02
            trxType = "02";
            log.info("IF5A-03 执行付费更新, cardId={}, 当前codeStatus={}, 出站站点={}, 出站时间={}, 票价={}",
                    cardId, codeStatus, updateStationCode, optDate, transAmount);
        } else {
            log.warn("IF5A-03 不支持的操作类型, cardId={}, adviceOpt={}", cardId, adviceOpt);
            response.setRetCode(RET_SUCCESS);
            response.setRetMsg("不支持的操作类型: " + adviceOpt);
            return response;
        }

        // ==================== 恢复查询用户信息 ====================
        // 006/018/005 都需要查询用户信息，构建闸机检票接口参数
        log.info("IF5A-03 查询用户信息开始, cardId={}, adviceOpt={}", cardId, adviceOpt);
        QueryUserInfoResult userInfo = null;
        try {
            QueryUserInfoResult cardTypeResult = accountClient.queryCardTypeByCardId(cardId);
            if (cardTypeResult == null) {
                log.warn("IF5A-03 查询用户信息无响应, cardId={}", cardId);
                response.setRetCode(RET_SUCCESS);
                response.setRetMsg("查询用户信息无响应");
                return response;
            }
            if (!RET_SUCCESS.equals(cardTypeResult.getRetCode())) {
                String accountRetCode = cardTypeResult.getRetCode();
                log.warn("IF5A-03 查询用户信息失败-请求参数验证失败, cardId={}, retCode={}", cardId, accountRetCode);
                switch (accountRetCode) {
                    case "8001" -> {
                        response.setRetCode(RET_SUCCESS);
                        response.setRetMsg("请求参数验证失败");
                    }
                    case "8004" -> {
                        response.setRetCode(RET_SUCCESS);
                        response.setRetMsg("未注册用户");
                    }
                    case "8007" -> {
                        response.setRetCode(RET_SUCCESS);
                        response.setRetMsg("合作伙伴验证失败");
                    }
                    case "8008" -> {
                        response.setRetCode(RET_SUCCESS);
                        response.setRetMsg("用户状态为解约审核中");
                    }
                    case "8006" -> {
                        response.setRetCode(RET_SUCCESS);
                        response.setRetMsg("用户卡号与请求参数不一致");
                    }
                    case null, default -> {
                        response.setRetCode(RET_SUCCESS);
                        response.setRetMsg("查询用户信息失败: " + cardTypeResult.getRetMsg());
                    }
                }
                return response;
            }
            if (!StringUtils.hasText(cardTypeResult.getThirdUserId())) {
                log.warn("IF5A-03 未注册用户, cardId={}, thirdUserId为空", cardId);
                response.setRetCode(RET_SUCCESS);
                response.setRetMsg("未注册用户");
                return response;
            }

            QueryUserInfoReqDTO userInfoReq = new QueryUserInfoReqDTO();
            userInfoReq.setThirdUserId(cardTypeResult.getThirdUserId());
            userInfoReq.setCardType(cardTypeResult.getCardType());
            userInfoReq.setCardId(cardId);
            userInfo = accountClient.queryUserInfo(userInfoReq);
            if (userInfo == null) {
                log.warn("IF5A-03 查询用户信息无响应, cardId={}", cardId);
                response.setRetCode(RET_SUCCESS);
                response.setRetMsg("查询用户信息无响应");
                return response;
            }
            if (!RET_SUCCESS.equals(userInfo.getRetCode())) {
                String accountRetCode = userInfo.getRetCode();
                switch (accountRetCode) {
                    case "8001" -> {
                        log.warn("IF5A-03 查询用户信息失败-请求参数验证失败, cardId={}, retCode={}", cardId, accountRetCode);
                        response.setRetCode(RET_SUCCESS);
                        response.setRetMsg(userInfo.getRetMsg());
                    }
                    case "8004" -> {
                        log.warn("IF5A-03 查询用户信息失败-未注册用户, cardId={}, retCode={}", cardId, accountRetCode);
                        response.setRetCode(RET_SUCCESS);
                        response.setRetMsg(userInfo.getRetMsg());
                    }
                    case "8007" -> {
                        log.warn("IF5A-03 查询用户信息失败-合作伙伴验证失败, cardId={}, retCode={}", cardId, accountRetCode);
                        response.setRetCode(RET_SUCCESS);
                        response.setRetMsg(userInfo.getRetMsg());
                    }
                    case "8008" -> {
                        log.warn("IF5A-03 查询用户信息失败-用户状态为解约审核中, cardId={}, retCode={}", cardId, accountRetCode);
                        response.setRetCode(RET_SUCCESS);
                        response.setRetMsg(userInfo.getRetMsg());
                    }
                    case "8006" -> {
                        log.warn("IF5A-03 查询用户信息失败-用户卡号与请求参数不一致, cardId={}, retCode={}", cardId, accountRetCode);
                        response.setRetCode(RET_SUCCESS);
                        response.setRetMsg(userInfo.getRetMsg());
                    }
                    case null, default -> {
                        log.warn("IF5A-03 查询用户信息失败, cardId={}, retCode={}, retMsg={}", cardId, accountRetCode, userInfo.getRetMsg());
                        response.setRetCode(RET_SUCCESS);
                        response.setRetMsg("查询用户信息失败: " + userInfo.getRetMsg());
                    }
                }
                return response;
            }
            if (!StringUtils.hasText(userInfo.getThirdUserId())) {
                response.setRetCode(RET_SUCCESS);
                response.setRetMsg("未注册用户");
                return response;
            }
            signChannelCode = userInfo.getChannel();
            cardType = userInfo.getCardType();
            itpUserId = userInfo.getThirdUserId();
        } catch (Exception e) {
            log.warn("IF5A-03 查询用户信息失败, cardId={}", cardId, e);
            response.setRetCode(RET_SUCCESS);
            response.setRetMsg("查询用户信息异常");
            return response;
        }

        log.info("IF5A-03 用户信息查询成功, cardId={}, thirdUserId={}, cardType={}, signChannelCode={}",
                cardId, itpUserId, cardType, signChannelCode);

        // ==================== 构建闸机检票请求并调用 ====================
        // 构建闸机检票请求
        NotifyVerifyResultReqDTO gateRequest =
                new NotifyVerifyResultReqDTO();
        gateRequest.setDeviceId(defaultString(request.getOperaterId(), ""));
        gateRequest.setItpUserId(encodeHexThirdUserId(itpUserId));
        gateRequest.setTrxType(trxType);
        gateRequest.setIssueChannelCode(defaultString(currentStatus.getChannel(), "01"));
        gateRequest.setSignChannelCode("");
        gateRequest.setCardId(cardId);
        gateRequest.setCardType(cardType);
        gateRequest.setHandleDateTime(optDate);
        gateRequest.setHandleStationCode(updateStationCode);
        gateRequest.setOvertimeAmount("0");
        gateRequest.setLastTicketStatus(defaultString(currentStatus.getCodeStatus(), QRCodeStatusEnum.SJT_ISSUE.getCode()));
        gateRequest.setHandleResultCode("000");
        gateRequest.setLastHandleStationCode(currentStatus.getLastTxnStation());
        gateRequest.setLastHandleDateTime(currentStatus.getLastTxnTime());
        gateRequest.setTicketTransSeq(currentStatus.getTxnSeq() == null ? "0" : currentStatus.getTxnSeq());
        gateRequest.setAdviceOpt(adviceOpt);
        gateRequest.setReserve1(null);
        gateRequest.setReserve2(null);
        gateRequest.setAdviceOpt(adviceOpt);

        log.info("IF5A-03 调用闸机检票接口开始, cardId={}, adviceOpt={}, trxType={}, transAmount={}, excessFareType={}",
                cardId, adviceOpt, trxType, gateRequest.getTrxAmount(), gateRequest.getExcessFareType());

        try {
            NotifyVerifyResultRespDTO gateResponse =
                    fepDevClient.notifyVerifyResult(gateRequest);

            log.info("IF5A-03 调用闸机检票接口结束, cardId={}, adviceOpt={}, gateResponse={}",
                    cardId, adviceOpt, gateResponse);

            if (gateResponse == null || !RET_SUCCESS.equals(gateResponse.getRetCode())) {
                log.warn("IF5A-03 闸机检票接口调用失败, cardId={}, adviceOpt={}, gateResponse={}",
                        cardId, adviceOpt, gateResponse);
                response.setRetCode(RET_SUCCESS);
                response.setRetMsg("闸机检票接口调用失败: " + (gateResponse != null ? gateResponse.getRetMsg() : "无响应"));
                return response;
            }

            log.info("IF5A-03 闸机检票接口调用成功, cardId={}, adviceOpt={}", cardId, adviceOpt);
        } catch (Exception e) {
            log.error("IF5A-03 闸机检票接口调用异常, cardId={}, adviceOpt={}", cardId, adviceOpt, e);
            response.setRetCode(RET_SUCCESS);
            response.setRetMsg("闸机检票接口调用异常: " + e.getMessage());
            return response;
        }

        response.setRetCode(RET_SUCCESS);
        response.setRetMsg("成功");

        log.info("IF5A-03 票卡更新完成, cardId={}, adviceOpt={}, updateType={}, codeStatus={}, transAmount={}, retCode={}",
                cardId, adviceOpt, updateType, codeStatus, transAmount,
                response.getRetCode());

        return response;
    }
}
