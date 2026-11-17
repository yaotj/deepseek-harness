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

@Component
class GateDailyTicketCoordinator {

    private static final Logger log = LoggerFactory.getLogger(GateDailyTicketCoordinator.class);
    private static final String RET_SUCCESS = "0000";
    private static final int DAILY_TICKET_TIMES_PER_TRIP = 1;
    private static final int COUNTING_TIMES_NOT_DAILY_TICKET = 0;
    private static final String COUNTING_FLAG_DAILY_TICKET = "Y";
    private static final String COUNTING_FLAG_NOT_DAILY_TICKET = "N";

    @Autowired
    private DailyTicketClient dailyTicketClient;

    @Autowired
    private AppNotifyService appNotifyService;

    private enum FailurePolicy {
        PROPAGATE,
        PASS_THROUGH("已放行出站，次数未扣减需人工核对"),
        DEGRADE("仅本字段降级，其余字段照常");

        private final String description;

        FailurePolicy() {
            this(null);
        }

        FailurePolicy(String description) {
            this.description = description;
        }

        String description() {
            return description;
        }
    }

    private record RemoteOutcome<T extends DailyTicketBaseResult>(T payload, boolean accepted) {
        boolean technicalFailure() {
            return payload == null;
        }
    }

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


    public void markUsedOnExit(NotifyVerifyResultReqDTO request, String resolvedCardType) {
        if (!TrxTypeCodeEnum.isExitTxn(request.getTrxType())
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

    public int resolveCountingTimes(String resolvedCardType) {
        return CardTypeCodeEnum.isDailyTicket(resolvedCardType)
                ? DAILY_TICKET_TIMES_PER_TRIP : COUNTING_TIMES_NOT_DAILY_TICKET;
    }

    public String resolveCountingFlag(String resolvedCardType) {
        return CardTypeCodeEnum.isDailyTicket(resolvedCardType)
                ? COUNTING_FLAG_DAILY_TICKET : COUNTING_FLAG_NOT_DAILY_TICKET;
    }

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
