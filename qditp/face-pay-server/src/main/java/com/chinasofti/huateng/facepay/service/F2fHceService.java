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

/**
 * IF5A 票卡分析 / 更新 / HCE 更新结果通知。三个接口都是<b>透传</b>，
 * 分析与更新逻辑在 ticket-server，HCE 数据回写在 account-server。
 *
 * <h2>两处与旧实现的差异</h2>
 * <ul>
 *   <li><b>远端错误码不再原样透给 BOM。</b>旧实现把 ticket-server 的 retCode 直接回吐，
 *       BOM 侧只认 BOM 族码值（0000 / 8999 / 80xx），透传等于给出一个它不认识的码。
 *       这里统一收敛成 {@code 8999} + 远端文案，<b>文案里带上远端码便于排查</b>。</li>
 *   <li><b>状态变更型操作补审计。</b>{@code requestUpdateCardData} 与
 *       {@code notiUpdateHceData} 都会改票卡数据，旧实现前者完全不落库、后者落一条
 *       {@code STATUS} 列被静默丢弃的记录。这里统一落到 {@code F2F_RESULT_REPORT}，
 *       {@code ORDER_NO} 位放 {@code cardId}（本链路没有订单号）。</li>
 * </ul>
 *
 * <p>不带 {@code @Transactional}：三个方法都有 RPC 调用。</p>
 */
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

    /**
     * IF5A-01 票卡分析。只读，不落库。
     *
     * <p><b>下游失败时 MUST 原样透传 ticket-server 的 retCode / retMsg</b>，NEVER 包成 8999。
     * 旧实现是纯转发，BOM 拿到的就是下游码；2026-09-11 新旧双打实测：同一 cardId 旧服务回
     * {@code 8004 未注册用户}，新服务当时回 {@code 8999 票卡分析失败[8004]:未注册用户}，
     * 设备按 8004 做的分支在新服务上永远匹配不到。已改回透传。
     * （「下游异常 / 无应答」两个分支仍回 8999——那是我方兜底、下游根本没给码，
     * 旧实现在这两种情况下的表现无法在测试环境复现，保持现状。）</p>
     */
    public JSONObject requestCardDataAnalyse(RequestCardDataAnalyseReqDTO request) {
        com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseReqDTO remote =
                new com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseReqDTO();
        remote.setProviderId(request.getProviderId());
        remote.setMsisdn(request.getMsisdn());
        remote.setCardId(request.getCardId());
        remote.setUpdateType(request.getUpdateType());

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

    /**
     * IF5A-03 票卡更新。<b>状态变更型</b>，成功与失败都留审计。
     *
     * <p>下游失败同样原样透传 retCode / retMsg，理由见
     * {@link #requestCardDataAnalyse}。</p>
     */
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

    /**
     * IF5A-09 HCE 票卡更新结果通知：先落审计，再把 HCE 数据回写 account-server。
     *
     * <p><b>回写失败仍回成功</b>：通知已经落库，回失败只会让 BOM 无意义重推；
     * 但审计里的 {@code OPT_RESULT} 会记成 FAILED，运营端能查出来。
     * 旧实现这一点是对的，此处保留。</p>
     */
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
     * 落一条审计。<b>{@code ORDER_NO} 位放 {@code cardId}</b>——本链路没有订单号，
     * 而 {@code UK_F2F_REPORT_IDEM} 是 (REPORT_TYPE, ORDER_NO)，因此同一张卡的
     * 重复通知会被挡住。
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
}
