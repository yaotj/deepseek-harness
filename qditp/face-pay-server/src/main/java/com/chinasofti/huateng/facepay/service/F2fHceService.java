package com.chinasofti.huateng.facepay.service;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.bom.BomResponses;
import com.chinasofti.huateng.facepay.api.device.bom.NotiUpdateHceDataReqDTO;
import com.chinasofti.huateng.facepay.api.device.bom.RequestCardDataAnalyseReqDTO;
import com.chinasofti.huateng.facepay.api.device.bom.RequestCardDataUpdateReqDTO;
import com.chinasofti.huateng.facepay.domain.F2fDuplicateKey;
import com.chinasofti.huateng.facepay.entity.F2fResultReport;
import com.chinasofti.huateng.facepay.mapper.F2fResultReportMapper;
import com.chinasofti.huateng.facepay.support.F2fChannel;
import com.chinasofti.huateng.facepay.support.F2fDeviceId;
import com.chinasofti.huateng.model.app.UpdateHceDataReqDTO;
import com.chinasofti.huateng.model.app.UpdateHceDataResult;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseRespDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateRespDTO;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class F2fHceService {

    private static final Logger log = LoggerFactory.getLogger(F2fHceService.class);

    private static final String REMOTE_SUCCESS = "0000";

    private static final String REPORT_HCE = "BOM_BIZ_RESULT";

    private final TicketClient ticketClient;

    private final AccountClient accountClient;

    private final F2fResultReportMapper reportMapper;

    public F2fHceService(TicketClient ticketClient, AccountClient accountClient,
                        F2fResultReportMapper reportMapper) {
        this.ticketClient = ticketClient;
        this.accountClient = accountClient;
        this.reportMapper = reportMapper;
    }

    public JSONObject requestCardDataAnalyse(RequestCardDataAnalyseReqDTO request) {
        com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseReqDTO remote =
                new com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseReqDTO();
        remote.setProviderId(request.getProviderId());
        remote.setMsisdn(request.getMsisdn());
        remote.setCardId(request.getCardId());
        remote.setUpdateType(request.getUpdateType());
        remote.setBomStationCode(deriveStationCode(request.getDeviceId()));

        RequestCardDataAnalyseRespDTO response;
        try {
            response = ticketClient.requestCardDataAnalyse(remote);
        } catch (RuntimeException e) {
            log.error("票卡分析 调 ticket-server 异常, cardId={}", request.getCardId(), e);
            return BomResponses.cardDataAnalyseFail(BomResponses.CODE_FAIL, "票卡分析失败:下游异常");
        }
        if (response == null) {
            log.warn("票卡分析 下游无应答, cardId={}", request.getCardId());
            return BomResponses.cardDataAnalyseFail(BomResponses.CODE_FAIL, "票卡分析失败");
        }
        if (!REMOTE_SUCCESS.equals(response.getRetCode())) {
            log.warn("票卡分析 下游返回失败, cardId={}, retCode={}, retMsg={}",
                    request.getCardId(), response.getRetCode(), response.getRetMsg());
            return BomResponses.cardDataAnalyseFail(response.getRetCode(), response.getRetMsg());
        }
        return BomResponses.cardDataAnalyse(response.getProviderId(), response.getCardIssueDate(),
                response.getMsisdn(), response.getCardId(), response.getCardStatus(),
                response.getLastLineCode(), response.getLastStationCode(), response.getLastUpdateDate(),
                response.getLastTransAmout(), response.getLastTicketTransSeq(),
                response.getAdviceOpt(), response.getManagerCode(), response.getTransAmount());
    }

    public JSONObject requestUpdateCardData(RequestCardDataUpdateReqDTO request) {
        com.chinasofti.huateng.model.ticket.RequestCardDataUpdateReqDTO remote =
                new com.chinasofti.huateng.model.ticket.RequestCardDataUpdateReqDTO();
        remote.setCardId(request.getCardId());
        remote.setUpdateType(request.getUpdateType());
        remote.setAdviceOpt(request.getAdviceOpt());
        remote.setOperaterId(request.getOperaterId());
        remote.setUpdateStationCode(request.getUpdateStationCode());
        remote.setOptDate(request.getOptDate());
        remote.setTransAmount(request.getTransAmount());

        String orderNo = auditOrderNo(FLOW_UPDATE, request.getCardId(), request.getOptDate());
        RequestCardDataUpdateRespDTO response;
        try {
            response = ticketClient.requestUpdateCardData(remote);
        } catch (RuntimeException e) {
            log.error("票卡更新 调 ticket-server 异常, cardId={}", request.getCardId(), e);
            audit(orderNo, request.getDeviceId(), "FAILED", "下游异常", request.toString());
            return BomResponses.cardDataUpdateFail(BomResponses.CODE_FAIL, "票卡更新失败:下游异常");
        }
        if (response == null) {
            audit(orderNo, request.getDeviceId(), "FAILED", "下游无应答", request.toString());
            return BomResponses.cardDataUpdateFail(BomResponses.CODE_FAIL, "票卡更新失败");
        }
        if (!REMOTE_SUCCESS.equals(response.getRetCode())) {
            log.warn("票卡更新 下游返回失败, cardId={}, retCode={}", request.getCardId(), response.getRetCode());
            audit(orderNo, request.getDeviceId(), "FAILED",
                    response.getRetCode() + ":" + response.getRetMsg(), request.toString());
            return BomResponses.cardDataUpdateFail(response.getRetCode(), response.getRetMsg());
        }
        audit(orderNo, request.getDeviceId(), "SUCCESS", "票卡更新成功", request.toString());
        return BomResponses.cardDataUpdate(response.getCardData());
    }

    public JSONObject receiveHceUpdateResult(NotiUpdateHceDataReqDTO request) {
        String orderNo = auditOrderNo(FLOW_HCE_NOTI, request.getCardId(), request.getTimestamp());
        boolean firstReport = audit(orderNo, request.getDeviceId(),
                "SUCCESS", "HCE更新通知已受理", request.getHceData());
        if (!firstReport) {
            log.info("HCE 更新通知重复到达，幂等返回成功, cardId={}", request.getCardId());
            return BomResponses.success();
        }
        UpdateHceDataReqDTO remote = new UpdateHceDataReqDTO();
        remote.setCardId(request.getCardId());
        remote.setHceData(request.getHceData());
        try {
            UpdateHceDataResult result = accountClient.updateHceData(remote);
            if (result == null || !REMOTE_SUCCESS.equals(result.getRetCode())) {
                String reason = result == null ? "下游无应答"
                        : result.getRetCode() + ":" + result.getRetMsg();
                log.warn("HCE 数据回写失败, cardId={}, reason={}", request.getCardId(), reason);
                reportMapper.updateOptResult(REPORT_HCE, orderNo, "FAILED", reason);
            } else {
                log.info("HCE 数据回写成功, cardId={}", request.getCardId());
            }
        } catch (RuntimeException e) {
            log.error("HCE 数据回写异常, cardId={}", request.getCardId(), e);
            reportMapper.updateOptResult(REPORT_HCE, orderNo, "FAILED", "回写异常");
        }
        return BomResponses.success();
    }

    private boolean audit(String orderNo, String deviceId, String optResult, String desc, String rawBody) {
        F2fResultReport report = new F2fResultReport();
        report.setReportType(REPORT_HCE);
        report.setOrderNo(orderNo);
        report.setChannel(F2fChannel.BOM);
        report.setDeviceId(deviceId);
        report.setOptResult(optResult);
        report.setOptResultDesc(desc);
        report.setProcessed("1");
        report.setRawBody(rawBody);
        report.setReceiveTms(LocalDateTime.now());
        report.setCreateTms(LocalDateTime.now());
        try {
            reportMapper.insert(report);
            return true;
        } catch (RuntimeException e) {
            if (!F2fDuplicateKey.isConflict(e)) {
                throw e;
            }
            return false;
        }
    }

    private static final String FLOW_UPDATE = "U";

    private static final String FLOW_HCE_NOTI = "N";

    private static String auditOrderNo(String flow, String cardId, String eventKey) {
        String key = eventKey == null || eventKey.isBlank() ? String.valueOf(System.currentTimeMillis()) : eventKey;
        return cardId + ":" + flow + key;
    }

    private static String deriveStationCode(String deviceId) {
        String stationCode = F2fDeviceId.stationCode(deviceId);
        return stationCode == null ? "" : stationCode;
    }
}
