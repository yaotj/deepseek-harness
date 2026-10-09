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

/** IF5A 票卡分析 / 更新 / HCE 更新结果通知。本类刻意不带 {@code @Transactional}（链路里有支付中心调用），NEVER 加。 */
@Service
public class F2fHceService {

    private static final Logger log = LoggerFactory.getLogger(F2fHceService.class);

    /** 下游成功码。 */
    private static final String REMOTE_SUCCESS = "0000";

    /** 票卡更新审计上报类型，复用 BOM 业务结果通道。 */
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

    /** IF5A-01 票卡分析。 */
    public JSONObject requestCardDataAnalyse(RequestCardDataAnalyseReqDTO request) {
        com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseReqDTO remote =
                new com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseReqDTO();
        remote.setProviderId(request.getProviderId());
        remote.setMsisdn(request.getMsisdn());
        remote.setCardId(request.getCardId());
        remote.setUpdateType(request.getUpdateType());
        // BOM 设备报文不含站码，但 deviceId 前 4 位即其所属站码（与 TVM 同口径，已用 STATION_INFO 核验：
        // 0622=辛屯 / 0352=双山 / 1128=世博园 / 0220=国际邮轮港）。IF5A-01 同站/跨站判定依赖此值。
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

    /** IF5A-03 票卡更新。 */
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

        RequestCardDataUpdateRespDTO response;
        try {
            response = ticketClient.requestUpdateCardData(remote);
        } catch (RuntimeException e) {
            log.error("票卡更新 调 ticket-server 异常, cardId={}", request.getCardId(), e);
            audit(request.getCardId(), request.getDeviceId(), "FAILED", "下游异常", request.toString());
            return BomResponses.cardDataUpdateFail(BomResponses.CODE_FAIL, "票卡更新失败:下游异常");
        }
        if (response == null) {
            audit(request.getCardId(), request.getDeviceId(), "FAILED", "下游无应答", request.toString());
            return BomResponses.cardDataUpdateFail(BomResponses.CODE_FAIL, "票卡更新失败");
        }
        if (!REMOTE_SUCCESS.equals(response.getRetCode())) {
            log.warn("票卡更新 下游返回失败, cardId={}, retCode={}", request.getCardId(), response.getRetCode());
            audit(request.getCardId(), request.getDeviceId(), "FAILED",
                    response.getRetCode() + ":" + response.getRetMsg(), request.toString());
            return BomResponses.cardDataUpdateFail(response.getRetCode(), response.getRetMsg());
        }
        audit(request.getCardId(), request.getDeviceId(), "SUCCESS", "票卡更新成功", request.toString());
        return BomResponses.cardDataUpdate(response.getCardData());
    }

    /** IF5A-09 HCE 票卡更新结果通知：先落审计，再把 HCE 数据回写 account-server。 */
    public JSONObject receiveHceUpdateResult(NotiUpdateHceDataReqDTO request) {
        boolean firstReport = audit(request.getCardId(), request.getDeviceId(),
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
                reportMapper.updateOptResult(REPORT_HCE, request.getCardId(), "FAILED", reason);
            } else {
                log.info("HCE 数据回写成功, cardId={}", request.getCardId());
            }
        } catch (RuntimeException e) {
            log.error("HCE 数据回写异常, cardId={}", request.getCardId(), e);
            reportMapper.updateOptResult(REPORT_HCE, request.getCardId(), "FAILED", "回写异常");
        }
        return BomResponses.success();
    }

    /**
     * 落一条审计。
     *
     * @return true 表示首次落库；false 表示撞唯一索引
     */
    private boolean audit(String cardId, String deviceId, String optResult, String desc, String rawBody) {
        F2fResultReport report = new F2fResultReport();
        report.setReportType(REPORT_HCE);
        report.setOrderNo(cardId);
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

    /**
     * 由设备号推导所属站码：取前 4 位。与 TVM 侧 {@code TvmResponses} 的口径一致，且已用
     * {@code STATION_INFO} 核验（BOM 设备号形如 {@code 06220801} → 站码 {@code 0622}=辛屯）。
     * 设备号不足 4 位时返回空串（由下游按未知站处理）。
     */
    private static String deriveStationCode(String deviceId) {
        if (deviceId == null || deviceId.length() < 4) {
            return "";
        }
        return deviceId.substring(0, 4);
    }
}
