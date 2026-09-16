package com.chinasofti.huateng.ticket.service.impl;

import com.chinasofti.huateng.model.app.*;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusReqDTO;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusRespDTO;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import com.chinasofti.huateng.ticket.mapper.QRCodeStatusMapper;
import com.chinasofti.huateng.ticket.mapper.QRCodeTxnDetailMapper;
import com.chinasofti.huateng.ticket.service.AppNotifyService;
import com.chinasofti.huateng.ticket.service.TicketRideStatusService;
import com.chinasofti.huateng.rpc.para.ParaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * APP 侧乘车状态服务实现。
 *
 * <p>委托给各 Handler 执行具体业务逻辑：
 * <ul>
 *   <li>{@link ExcessFareHandler} - IF8A-04 自助补站处理</li>
 * </ul>
 * AGM 侧接口请使用 {@link com.chinasofti.huateng.ticket.service.impl.AgmRideStatusServiceImpl}。
 */
@Service
public class TicketRideStatusServiceImpl implements TicketRideStatusService {

    private static final Logger log = LoggerFactory.getLogger(TicketRideStatusServiceImpl.class);
    private static final String RET_SUCCESS = "0000";
    private static final String RET_INVALID_PARAM = "8001";

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
    private ExcessFareHandler excessFareHandler;

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

    /**
     * IF8A-29 查询用户上次行程。
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

    /**
     * IF8A-04 自助补站。
     */
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

        excessFareHandler.handleExcessFare(request, response);
        return response;
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

    // ==================== 私有辅助方法 ====================

    private MemberItineraryDTO buildMemberItinerary(QRCodeStatus currentStatus, QRCodeTxnDetail latestDetail) {
        MemberItineraryDTO itinerary = new MemberItineraryDTO();
        itinerary.setTicketStatus(currentStatus.getCodeStatus());
        itinerary.setPayStatus("00");

        // 收集所有需要查询名称的站点编码
        List<String> stationCodes = new java.util.ArrayList<>();
        if (latestDetail == null) {
            if (StringUtils.hasText(currentStatus.getLastTxnStation())) {
                stationCodes.add(currentStatus.getLastTxnStation());
            }
            itinerary.setThisStationCode(currentStatus.getLastTxnStation());
            itinerary.setThisTransTime(currentStatus.getLastTxnTime());
            itinerary.setTransSeq(currentStatus.getTxnSeq());
        } else {
            stationCodes.add(latestDetail.getHandleStationCode());
            if (isExitTxn(latestDetail.getTrxType())) {
                stationCodes.add(latestDetail.getHandleStationCode());
            } else {
                if (StringUtils.hasText(latestDetail.getLastHandleStationCode())) {
                    stationCodes.add(latestDetail.getLastHandleStationCode());
                }
            }

            itinerary.setThisStationCode(latestDetail.getHandleStationCode());
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
                itinerary.setLastStationCode(latestDetail.getHandleStationCode());
                itinerary.setLastTransTime(latestDetail.getHandleDateTime());
            } else {
                itinerary.setLastStationCode(latestDetail.getLastHandleStationCode());
                itinerary.setLastTransTime(latestDetail.getLastHandleDateTime());
            }
        }

        // 批量查询站点名称
        Map<String, String> stationNameMap = fetchStationNameMap(stationCodes);

        if (latestDetail == null) {
            itinerary.setThisStationName(resolveStationName(currentStatus.getLastTxnStation(), stationNameMap));
        } else {
            itinerary.setThisStationName(resolveStationName(latestDetail.getHandleStationCode(), stationNameMap));
            if (isExitTxn(latestDetail.getTrxType())) {
                itinerary.setLastStationName(resolveStationName(latestDetail.getHandleStationCode(), stationNameMap));
            } else {
                itinerary.setLastStationName(resolveStationName(latestDetail.getLastHandleStationCode(), stationNameMap));
            }
        }
        return itinerary;
    }

    private Map<String, String> fetchStationNameMap(List<String> stationCodes) {
        Map<String, String> map = new HashMap<>();
        if (stationCodes == null || stationCodes.isEmpty()) {
            return map;
        }
        try {
            // 去重
            List<String> uniqueCodes = stationCodes.stream()
                    .filter(StringUtils::hasText)
                    .distinct()
                    .collect(Collectors.toList());
            if (uniqueCodes.isEmpty()) {
                return map;
            }
            RequestStationNameBatchReqDTO batchReq = new RequestStationNameBatchReqDTO();
            batchReq.setStationCodes(uniqueCodes);
            RequestStationNameBatchResult batchResult = paraClient.requestStationNameBatch(batchReq);
            if (batchResult != null && "0000".equals(batchResult.getRetCode())
                    && batchResult.getStationNameList() != null) {
                for (RequestStationNameResult item : batchResult.getStationNameList()) {
                    if (item != null && StringUtils.hasText(item.getStationCode())
                            && StringUtils.hasText(item.getStationName())) {
                        map.put(item.getStationCode(), item.getStationName());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("批量查询车站名称失败", e);
        }
        return map;
    }

    private boolean isExitTxn(String trxType) {
        return com.chinasofti.huateng.model.enums.TrxTypeCodeEnum.isExitTxn(trxType);
    }

    private Integer toInteger(Long value) {
        return value == null ? null : value.intValue();
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private String resolveStationName(String stationCode, Map<String, String> stationNameMap) {
        if (!StringUtils.hasText(stationCode)) {
            return stationCode;
        }
        String name = stationNameMap.get(stationCode);
        return StringUtils.hasText(name) ? name : stationCode;
    }

    private String defaultString(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
