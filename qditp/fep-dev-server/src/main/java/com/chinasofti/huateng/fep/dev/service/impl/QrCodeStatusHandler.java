package com.chinasofti.huateng.fep.dev.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.fep.dev.constant.FepDevErrorCodeEnum;
import com.chinasofti.huateng.fep.dev.model.RequestQrCodeStatusReqDTO;
import com.chinasofti.huateng.fep.dev.model.RequestQrCodeStatusRespDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusReqDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusRespDTO;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigInteger;

/**
 * IF1A-04 票卡状态查询处理。
 */
@Component
public class QrCodeStatusHandler {
    private static final Logger log = LoggerFactory.getLogger(QrCodeStatusHandler.class);

    @Autowired
    private TicketClient ticketClient;

    /**
     * IF1A-04 查询票卡状态。
     *
     * @param request 查询票卡状态业务参数
     * @return 查询票卡状态响应
     */
    public RequestQrCodeStatusRespDTO requestQrCodeStatus(RequestQrCodeStatusReqDTO request) {
        RequestQrCodeStatusRespDTO response = new RequestQrCodeStatusRespDTO();
        if (request == null || !StringUtils.hasText(request.getCardId())) {
            response.setRetCode(FepDevErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("cardId不能为空");
            return response;
        }

        QueryStatusReqDTO ticketRequest = new QueryStatusReqDTO();
        ticketRequest.setCardId(request.getCardId());
        ticketRequest.setThirdUserId(normalizeThirdUserId(request.getItpUserId()));

        log.info("IF1A-04 调用 ticket-server 查询票卡状态, 入参={}", JSON.toJSONString(ticketRequest));
        QueryStatusRespDTO ticketResponse = ticketClient.queryQrCodeStatus(ticketRequest);
        if (ticketResponse == null) {
            log.warn("IF1A-04 ticket-server 无响应, cardId={}", request.getCardId());
            response.setRetCode(FepDevErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg("票卡状态查询服务异常");
            return response;
        }
        log.info("IF1A-04 调用 ticket-server 查询票卡状态, 返回={}", JSON.toJSONString(ticketResponse));

        response.setRetCode(ticketResponse.getRetCode());
        response.setRetMsg(ticketResponse.getRetMsg());
        response.setItpUserId(request.getItpUserId());
        response.setCardId(request.getCardId());
        response.setLastTicketStatus(ticketResponse.getStatus());
        response.setLastHandleDateTime(ticketResponse.getLastTxnTime());
        return response;
    }

    /**
     * 将十六进制 itpUserId 转换为十进制字符串，并左侧补零至 8 位。
     * 转换失败时原值返回并记录 warn 日志。
     */
    private String normalizeThirdUserId(String itpUserId) {
        if (!StringUtils.hasText(itpUserId)) {
            return itpUserId;
        }
        try {
            String decimal = new BigInteger(itpUserId.trim(), 16).toString(10);
            return leftPadToEight(decimal);
        } catch (Exception e) {
            log.warn("IF1A-04 itpUserId十六进制转十进制失败, itpUserId={}", itpUserId);
            return itpUserId;
        }
    }

    /**
     * 左侧补零至 8 位，已满足长度或为空时原值返回。
     */
    private String leftPadToEight(String value) {
        if (!StringUtils.hasText(value) || value.length() >= 8) {
            return value;
        }
        return "0".repeat(8 - value.length()) + value;
    }
}
