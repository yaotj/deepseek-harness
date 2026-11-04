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

    /** IF5A-09 HCE 票卡更新结果通知：先落审计，再把 HCE 数据回写 account-server。 */
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

    /**
     * 落一条审计。
     *
     * @param orderNo 幂等键，{@code cardId + ":" + 流前缀 + 事件键}（见 {@link #auditOrderNo}）
     * @return true 表示首次落库；false 表示撞唯一索引（同一事件重复到达）
     */
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

    /** IF5A-03 票卡更新审计流前缀。 */
    private static final String FLOW_UPDATE = "U";

    /** IF5A-09 HCE 更新结果通知审计流前缀。 */
    private static final String FLOW_HCE_NOTI = "N";

    /**
     * 审计幂等键 = {@code cardId + ":" + 流前缀 + 事件键}（ADR-D173）。
     *
     * <p>IF5A-03 事件键取 {@code optDate}（BOM 操作时间，控制器强制非空）、IF5A-09 取报文
     * {@code timestamp}：同一事件重试（设备断网重传）键相同 → 撞 {@code UK_F2F_REPORT_IDEM}
     * 走幂等；同卡不同事件键不同 → 各落各的行，NEVER 再退回「每卡一行」的老键（老键导致同卡
     * 第二次更新的审计静默丢失、HCE 通知被误判重复而跳过回写）。流前缀隔离两条链路，防止
     * 更新与紧随其后的通知在同一秒（事件键相同）互相挤占。
     *
     * <p>{@code eventKey} 缺失时退化为毫秒时间戳一次性键：保证审计行必落（留痕优先于幂等），
     * 该情形下重试不再去重，调用方 MUST 保证事件键非空（IF5A-03 已由控制器强制）。
     */
    private static String auditOrderNo(String flow, String cardId, String eventKey) {
        String key = eventKey == null || eventKey.isBlank() ? String.valueOf(System.currentTimeMillis()) : eventKey;
        return cardId + ":" + flow + key;
    }

    /**
     * 由设备号推导所属站码，供 {@code bomStationCode} 使用。
     *
     * <p>解析口径的唯一真源是 {@link F2fDeviceId#stationCode(String)}（取前 4 位，规则见 ADR-D157）。
     * 本方法只负责把「推不出来」表示成该 RPC 字段约定的空串（下游按未知站处理），
     * NEVER 在此重写解析规则、也 NEVER 改回 {@code TvmResponses} 那种 {@code <= 4} 判据。
     */
    private static String deriveStationCode(String deviceId) {
        String stationCode = F2fDeviceId.stationCode(deviceId);
        return stationCode == null ? "" : stationCode;
    }
}
