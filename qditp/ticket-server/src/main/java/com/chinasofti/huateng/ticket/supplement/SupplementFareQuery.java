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

@Component
class SupplementFareQuery {

    private static final Logger log = LoggerFactory.getLogger(SupplementFareQuery.class);

    @Autowired
    private ParaClient paraClient;


    record FareResult(String ticketPrice, RpcOutcome outcome) {

        boolean isOk() {
            return outcome.isOk();
        }

        String priceOrZero() {
            return isOk() ? ticketPrice : SupplementCodec.AMOUNT_ZERO;
        }
    }


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
