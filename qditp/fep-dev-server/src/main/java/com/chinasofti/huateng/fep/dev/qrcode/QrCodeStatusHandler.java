package com.chinasofti.huateng.fep.dev.qrcode;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.fep.dev.constant.FepDevErrorCodeEnum;
import com.chinasofti.huateng.fep.dev.device.DeviceUserIdCodec;
import com.chinasofti.huateng.fep.dev.model.RequestQrCodeStatusReqDTO;
import com.chinasofti.huateng.fep.dev.model.RequestQrCodeStatusRespDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusReqDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusRespDTO;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * IF1A-04 票卡状态查询处理。
 *
 * <p>依赖走构造器注入，与本模块其余 handler / assembler 一致：本类可以脱离 Spring 直接 new 出来测。
 * NEVER 改回 {@code @Autowired} 字段注入。</p>
 */
@Component
public class QrCodeStatusHandler {
    private static final Logger log = LoggerFactory.getLogger(QrCodeStatusHandler.class);

    private final TicketClient ticketClient;
    private final DeviceUserIdCodec deviceUserIdCodec;

    public QrCodeStatusHandler(TicketClient ticketClient, DeviceUserIdCodec deviceUserIdCodec) {
        this.ticketClient = ticketClient;
        this.deviceUserIdCodec = deviceUserIdCodec;
    }

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
        // IF1A-04 的报文里没有 issueChannelCode，传 null 即按默认 8 位（ADR-D70）。
        // 改造前这里传的是 alipayTransaction=false，两者行为逐位一致。
        ticketRequest.setThirdUserId(deviceUserIdCodec.normalize(request.getItpUserId(), null));

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
}
