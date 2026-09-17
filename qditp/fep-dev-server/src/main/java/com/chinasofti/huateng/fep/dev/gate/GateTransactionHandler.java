package com.chinasofti.huateng.fep.dev.gate;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.fep.dev.constant.FepDevErrorCodeEnum;
import com.chinasofti.huateng.fep.dev.device.DeviceUserIdCodec;
import com.chinasofti.huateng.fep.dev.model.NotifyVerifyResultDeviceReqDTO;
import com.chinasofti.huateng.model.enums.IssueChannelCodeEnum;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** IF1A-01 闸机检票通知的接入层转发：报文规范化 → 转 ticket-server → 原样透传响应。 */
@Component
public class GateTransactionHandler {
    private static final Logger log = LoggerFactory.getLogger(GateTransactionHandler.class);

    /** 闸机读写器处理成功的 {@code handleResultCode} 取值。 */
    private static final String DEVICE_HANDLE_SUCCESS = "000";

    private final TicketClient ticketClient;
    private final DeviceUserIdCodec deviceUserIdCodec;

    public GateTransactionHandler(TicketClient ticketClient, DeviceUserIdCodec deviceUserIdCodec) {
        this.ticketClient = ticketClient;
        this.deviceUserIdCodec = deviceUserIdCodec;
    }

    /** IF1A-01 闸机检票通知：规范化报文后转 ticket-server，响应原样透传。 */
    public NotifyVerifyResultRespDTO notifyVerifyResult(NotifyVerifyResultDeviceReqDTO request) {
        NotifyVerifyResultRespDTO response = new NotifyVerifyResultRespDTO();
        if (request != null && !DEVICE_HANDLE_SUCCESS.equals(request.getHandleResultCode())) {
            log.warn("IF1A-01 闸机检票通知, 读写器返回非成功状态，跳过业务处理, "
                            + "deviceId={}, handleResultCode={}, cardId={}, trxType={}, handleDateTime={}",
                    request.getDeviceId(), request.getHandleResultCode(),
                    request.getCardId(), request.getTrxType(), request.getHandleDateTime());
            response.setRetCode(FepDevErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg("接收成功");
            return response;
        }
        if (request == null || !StringUtils.hasText(request.getCardId())) {
            response.setRetCode(FepDevErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("cardId不能为空");
            return response;
        }

        NotifyVerifyResultReqDTO ticketRequest = toTicketRequest(request);

        String issueChannelCode = request.getIssueChannelCode();
        ticketRequest.setItpUserId(deviceUserIdCodec.normalize(request.getItpUserId(), issueChannelCode));

        log.info("IF1A-01 调用 ticket-server 闸机检票通知, cardId={}, trxType={}, issueChannelCode={}, alipay={}",
                request.getCardId(), request.getTrxType(), issueChannelCode,
                IssueChannelCodeEnum.isAlipay(issueChannelCode));
        NotifyVerifyResultRespDTO ticketResponse = ticketClient.notifyVerifyResult(ticketRequest);
        log.info("IF1A-01 调用 ticket-server 闸机检票通知, 返回={}", JSON.toJSONString(ticketResponse));
        if (ticketResponse == null) {
            log.warn("IF1A-01 ticket-server 无响应, cardId={}", request.getCardId());
            response.setRetCode(FepDevErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg("闸机检票通知服务异常");
            return response;
        }
        return ticketResponse;
    }

    /** 设备侧入向契约 → ticket-server 对内契约的逐字段搬运。 */
    private NotifyVerifyResultReqDTO toTicketRequest(NotifyVerifyResultDeviceReqDTO device) {
        NotifyVerifyResultReqDTO ticket = new NotifyVerifyResultReqDTO();
        ticket.setDeviceId(device.getDeviceId());
        ticket.setItpUserId(device.getItpUserId());
        ticket.setTrxType(device.getTrxType());
        ticket.setIssueChannelCode(device.getIssueChannelCode());
        ticket.setSignChannelCode(device.getSignChannelCode());
        ticket.setCardId(device.getCardId());
        ticket.setCardType(device.getCardType());
        ticket.setHandleDateTime(device.getHandleDateTime());
        ticket.setHandleStationCode(device.getHandleStationCode());
        ticket.setTrxAmount(device.getTrxAmount());
        ticket.setOvertimeAmount(device.getOvertimeAmount());
        ticket.setLastTicketStatus(device.getLastTicketStatus());
        ticket.setHandleResultCode(device.getHandleResultCode());
        ticket.setLastHandleStationCode(device.getLastHandleStationCode());
        ticket.setLastHandleDateTime(device.getLastHandleDateTime());
        ticket.setTicketTransSeq(device.getTicketTransSeq());
        ticket.setExcessFareType(device.getExcessFareType());
        ticket.setAdviceOpt(device.getAdviceOpt());
        ticket.setReserve1(device.getReserve1());
        ticket.setReserve2(device.getReserve2());
        ticket.setCompanionFlag(device.getCompanionFlag());
        ticket.setPaymentVendor(device.getPaymentVendor());
        ticket.setRequestSignSeq(device.getRequestSignSeq());
        ticket.setChannelType(device.getChannelType());
        return ticket;
    }
}
