package com.chinasofti.huateng.facepay.service;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.bom.BomResponses;
import com.chinasofti.huateng.facepay.api.device.bom.RequestQrCodeStatusReqDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusReqDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusRespDTO;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class F2fQrCodeStatusService {

    private static final Logger log = LoggerFactory.getLogger(F2fQrCodeStatusService.class);

    private static final String REMOTE_SUCCESS = "0000";

    private final TicketClient ticketClient;

    public F2fQrCodeStatusService(TicketClient ticketClient) {
        this.ticketClient = ticketClient;
    }

    public JSONObject requestQrCodeStatus(RequestQrCodeStatusReqDTO request) {
        QueryStatusReqDTO remote = new QueryStatusReqDTO();
        remote.setCardId(request.getCardId());
        remote.setThirdUserId(request.getItpUserId());

        QueryStatusRespDTO response;
        try {
            response = ticketClient.queryQrCodeStatus(remote);
        } catch (RuntimeException e) {
            log.error("票卡状态查询 调 ticket-server 异常, cardId={}", request.getCardId(), e);
            return BomResponses.qrCodeStatusFail(BomResponses.CODE_FAIL, "票卡状态查询失败:下游异常");
        }
        if (response == null) {
            log.warn("票卡状态查询 下游无应答, cardId={}", request.getCardId());
            return BomResponses.qrCodeStatusFail(BomResponses.CODE_FAIL, "票卡状态查询失败");
        }
        if (!REMOTE_SUCCESS.equals(response.getRetCode())) {
            log.warn("票卡状态查询 下游返回失败, cardId={}, retCode={}, retMsg={}",
                    request.getCardId(), response.getRetCode(), response.getRetMsg());
            return BomResponses.qrCodeStatusFail(response.getRetCode(), response.getRetMsg());
        }
        return BomResponses.qrCodeStatus(request.getItpUserId(), response.getCardId(),
                response.getStatus(), response.getLastTxnTime());
    }
}
