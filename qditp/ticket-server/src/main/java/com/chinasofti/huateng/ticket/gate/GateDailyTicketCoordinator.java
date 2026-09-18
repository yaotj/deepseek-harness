package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoResult;
import com.chinasofti.huateng.model.enums.CardTypeCodeEnum;
import com.chinasofti.huateng.model.enums.TrxTypeCodeEnum;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.rpc.dailyticket.DailyTicketClient;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import com.chinasofti.huateng.ticket.notify.AppNotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/** IF1A-01 日票协同器 —— 与 {@code daily-ticket-server} 的三次交互（进站校验 / 出站扣次 / 票号查询） 以及两个恒有值字段（{@code countingFlag} / {@code countingTimes}）的推导。 */
@Component
class GateDailyTicketCoordinator {

    private static final Logger log = LoggerFactory.getLogger(GateDailyTicketCoordinator.class);
    private static final String RET_SUCCESS = "0000";
    /** 一趟行程消耗的日票次数，恒为 1。 */
    private static final int DAILY_TICKET_TIMES_PER_TRIP = 1;
    /** 非日票卡种的 countingTimes。 */
    private static final int COUNTING_TIMES_NOT_DAILY_TICKET = 0;
    /** 日票（记期票 0445~0447 + 计次票 0448）的 countingFlag。 */
    private static final String COUNTING_FLAG_DAILY_TICKET = "Y";
    /** 非日票卡种的 countingFlag。 */
    private static final String COUNTING_FLAG_NOT_DAILY_TICKET = "N";

    @Autowired
    private DailyTicketClient dailyTicketClient;

    @Autowired
    private AppNotifyService appNotifyService;

    /** 远端调用的技术失败（RPC 抛异常 / 响应解析为 null）处置口径。 */
    private enum FailurePolicy {
        /** 失败即抛。 */
        PROPAGATE,
        /** 失败即放行 + ERROR 留证据。 */
        PASS_THROUGH("已放行出站，次数未扣减需人工核对"),
        /** 失败即降级 + ERROR 留证据。 */
        DEGRADE("仅本字段降级，其余字段照常");

        private final String description;

        FailurePolicy() {
            this(null);
        }

        FailurePolicy(String description) {
            this.description = description;
        }

        /** 供日志说明「失败后做了什么」。 */
        String description() {
            return description;
        }
    }

    /** 一次远端调用的结果。 */
    private record RemoteOutcome<T extends DailyTicketBaseResult>(T payload, boolean accepted) {
        boolean technicalFailure() {
            return payload == null;
        }
    }

    /** 三次交互共用的调用骨架：发起 → 技术失败按 {@code policy} 处置 → 判 {@code retCode}。 */
    private <T extends DailyTicketBaseResult> RemoteOutcome<T> invoke(
            String step, String logContext, FailurePolicy policy, Supplier<T> remoteCall) {
        T payload;
        try {
            payload = remoteCall.get();
        } catch (Exception e) {
            if (policy == FailurePolicy.PROPAGATE) {
                log.error("IF1A-01 {}调用异常, {}", step, logContext, e);
                throw new RuntimeException(step + "服务不可用, " + logContext, e);
            }
            log.error("IF1A-01 {}调用异常，{}, {}", step, policy.description(), logContext, e);
            return new RemoteOutcome<>(null, false);
        }
        if (payload == null) {
            if (policy == FailurePolicy.PROPAGATE) {
                log.error("IF1A-01 {}响应解析为空, {}", step, logContext);
                throw new RuntimeException(step + "服务不可用, " + logContext);
            }
            log.error("IF1A-01 {}响应解析为空，{}, {}", step, policy.description(), logContext);
            return new RemoteOutcome<>(null, false);
        }
        return new RemoteOutcome<>(payload, RET_SUCCESS.equals(payload.getRetCode()));
    }

    /**
     * 日票进站校验：{@code SIGN_CHANNEL_CODE ∈ {12,13,14,15}} 且本笔是进站交易时才拦截。
     *
     * @return true=允许继续检票流程；
     */
    public boolean checkEntryAllowed(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO response,
                                     String resolvedCardType) {
        if (!TrxTypeCodeEnum.isEntryTxn(request.getTrxType())
                || !CardTypeCodeEnum.isDailyTicket(resolvedCardType)) {
            return true;
        }
        String logContext = "cardId=" + request.getCardId() + ", signChannelCode=" + request.getSignChannelCode();
        RemoteOutcome<DailyTicketBaseResult> outcome = invoke("日票进站校验", logContext,
                FailurePolicy.PROPAGATE, () -> dailyTicketClient.entryCheck(request.getCardId()));
        if (!outcome.accepted()) {
            log.warn("IF1A-01 日票进站校验拒绝, {}, retMsg={}", logContext, outcome.payload().getRetMsg());
            response.setRetCode(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getCode());
            response.setRetMsg(outcome.payload().getRetMsg());
            return false;
        }
        log.info("IF1A-01 日票进站校验通过, cardId={}, signChannelCode={}",
                request.getCardId(), request.getSignChannelCode());
        return true;
    }

    /** 日票出站处理：出站时扣减计次票次数、标记已使用（仅出站 trxType 触发）。 */
    public void markUsedOnExit(NotifyVerifyResultReqDTO request, String resolvedCardType) {
        if (!TrxTypeCodeEnum.EXIT.getCode().equals(request.getTrxType())
                || !CardTypeCodeEnum.isDailyTicket(resolvedCardType)) {
            return;
        }
        RemoteOutcome<DailyTicketBaseResult> outcome = invoke("日票出站标记", "cardId=" + request.getCardId(),
                FailurePolicy.PASS_THROUGH,
                () -> dailyTicketClient.markUsed(
                        request.getCardId(), null,
                        null,
                        request.getLastHandleStationCode(),
                        request.getHandleStationCode()));
        if (outcome.technicalFailure()) {
            return;
        }
        if (!outcome.accepted()) {
            log.error("IF1A-01 日票出站标记被拒，{}, cardId={}, retCode={}, retMsg={}",
                    FailurePolicy.PASS_THROUGH.description(), request.getCardId(),
                    outcome.payload().getRetCode(), outcome.payload().getRetMsg());
            return;
        }
        log.info("IF1A-01 日票出站处理完成, cardId={}", request.getCardId());
        appNotifyService.notifyCountingTicketTimes(request, DAILY_TICKET_TIMES_PER_TRIP);
    }

    /** 解析 {@code countingTimes}（本次行程消耗次数），恒有值、 */
    public int resolveCountingTimes(String resolvedCardType) {
        return CardTypeCodeEnum.isDailyTicket(resolvedCardType)
                ? DAILY_TICKET_TIMES_PER_TRIP : COUNTING_TIMES_NOT_DAILY_TICKET;
    }

    /** 解析 {@code countingFlag}（计次标志），恒有值、 */
    public String resolveCountingFlag(String resolvedCardType) {
        return CardTypeCodeEnum.isDailyTicket(resolvedCardType)
                ? COUNTING_FLAG_DAILY_TICKET : COUNTING_FLAG_NOT_DAILY_TICKET;
    }

    /** 查询日票票号并写入 {@code response.ticketCode}（仅日票卡种调用）。 */
    public void applyDailyTicketFields(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO response) {
        QueryDailyTicketInfoReqDTO queryReq = new QueryDailyTicketInfoReqDTO();
        queryReq.setCardId(request.getCardId());
        RemoteOutcome<QueryDailyTicketInfoResult> outcome = invoke("查询日票信息",
                "cardId=" + request.getCardId(), FailurePolicy.DEGRADE,
                () -> dailyTicketClient.queryDailyTicketInfo(queryReq));
        if (outcome.technicalFailure()) {
            return;
        }
        if (!outcome.accepted()) {
            log.warn("IF1A-01 查询日票信息失败, cardId={}, retCode={}",
                    request.getCardId(), outcome.payload().getRetCode());
            return;
        }
        QueryDailyTicketInfoResult info = outcome.payload();
        response.setTicketCode(info.getTicketCode());
        log.info("IF1A-01 日票信息解析完成, cardId={}, ticketCode={}, countingTimes={}, countingFlag={}, 剩余次数(仅日志)={}",
                request.getCardId(), info.getTicketCode(), response.getCountingTimes(),
                response.getCountingFlag(), info.getActualTimes());
    }
}
