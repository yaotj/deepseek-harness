package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.app.RequestTicketPriceByStationReqDTO;
import com.chinasofti.huateng.model.app.RequestTicketPriceByStationResult;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import com.chinasofti.huateng.rpc.para.ParaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 补站域的区段票价查询，IF5A-01 预估 / IF5A-03 实扣 / IF8A-04 补出站三处共用同一实现。 */
@Component
class SupplementFareQuery {

    private static final Logger log = LoggerFactory.getLogger(SupplementFareQuery.class);

    @Autowired
    private ParaClient paraClient;

    /**
     * 票价查询结果。
     *
     * @param ticketPrice 票价字符串，仅 {@code outcome} 为 {@code Ok} 时非空
     * @param outcome
     */
    record FareResult(String ticketPrice, RpcOutcome outcome) {

        boolean isOk() {
            return outcome.isOk();
        }

        /** 供「查不到就兜底 0 元」的 IF5A-01 预估路径使用； */
        String priceOrZero() {
            return isOk() ? ticketPrice : SupplementCodec.AMOUNT_ZERO;
        }
    }

    /**
     * 查询进站站到出站站的区段票价。
     *
     * @param logTag 日志前缀，取值如 {@code IF5A-01} / {@code IF5A-03} / {@code IF8A-04}，
     */
    FareResult query(String entryStationCode, String exitStationCode, String logTag) {
        if (!StringUtils.hasText(entryStationCode) || !StringUtils.hasText(exitStationCode)) {
            log.warn("{} 票价查询入参缺失, entry={}, exit={}", logTag, entryStationCode, exitStationCode);
            return new FareResult(null, new RpcOutcome.BizRejected(null, "票价查询入参缺失"));
        }
        try {
            RequestTicketPriceByStationReqDTO fareRequest = new RequestTicketPriceByStationReqDTO();
            fareRequest.setEntryStationCode(entryStationCode);
            fareRequest.setExitStationCode(exitStationCode);
            RequestTicketPriceByStationResult fareResult = paraClient.requestTicketPriceByStation(fareRequest);

            String retCode = fareResult == null ? null : fareResult.getRetCode();
            String retMsg = fareResult == null ? null : fareResult.getRetMsg();
            if (fareResult != null && SupplementCodec.RET_SUCCESS.equals(retCode)
                    && StringUtils.hasText(fareResult.getTicketPrice())) {
                log.info("{} 票价查询成功, entry={}, exit={}, ticketPrice={}",
                        logTag, entryStationCode, exitStationCode, fareResult.getTicketPrice());
                return new FareResult(fareResult.getTicketPrice(), new RpcOutcome.Ok());
            }
            log.warn("{} 票价查询未取到票价, entry={}, exit={}, retCode={}, retMsg={}",
                    logTag, entryStationCode, exitStationCode, retCode, retMsg);
            return new FareResult(null, new RpcOutcome.BizRejected(retCode, retMsg));
        } catch (Exception e) {
            log.error("{} 票价查询异常, entry={}, exit={}", logTag, entryStationCode, exitStationCode, e);
            return new FareResult(null, new RpcOutcome.Unreachable(e));
        }
    }
}
