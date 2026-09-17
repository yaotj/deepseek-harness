package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.ticket.station.StationNameResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 出站扣费入参 {@link GateTxnPayReqDTO} 的组装：设备报文字段映射 + ticket-server 响应透传 + 中文站名回填。 */
@Component
class GateTxnPayRequestAssembler {
    private static final Logger log = LoggerFactory.getLogger(GateTxnPayRequestAssembler.class);

    private final StationNameResolver stationNameResolver;

    public GateTxnPayRequestAssembler(StationNameResolver stationNameResolver) {
        this.stationNameResolver = stationNameResolver;
    }
    /**
     * 按设备报文 + ticket-server 响应组装扣费入参，并回填进出站中文站名。
     *
     * @param industryDetail 支付宝出行的 21 键行业明细，仅 issueChannelCode=07 有值；
     */
    public GateTxnPayReqDTO assemble(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO ticketResponse,
                                    String industryDetail) {
        GateTxnPayReqDTO payRequest = GateTxnPayReqDTO.fromVerifyResult(request);
        payRequest.setIndustryDetail(industryDetail);
        applyTicketResponse(payRequest, request, ticketResponse);
        fillStationNames(payRequest, request.getLastHandleStationCode(), request.getHandleStationCode());
        log.info("IF1A-01 扣费请求关键字段, cardId={}, ticketStatus={}, orderExpType={}, offlineFlag={}, entryStationName={}, exitStationName={}",
                request.getCardId(), payRequest.getTicketStatus(), payRequest.getOrderExpType(),
                payRequest.getOfflineFlag(), payRequest.getEntryStationName(), payRequest.getExitStationName());
        return payRequest;
    }
    /** 透传 ticket-server 返回的票卡状态、订单异常类型、离线码标识、日票相关字段与签约信息。 */
    private void applyTicketResponse(GateTxnPayReqDTO payRequest, NotifyVerifyResultReqDTO request,
                                    NotifyVerifyResultRespDTO ticketResponse) {
        if (ticketResponse == null) {
            log.warn("IF1A-01 ticketResponse 为null，"
                            + "ticketStatus/orderExpType/offlineFlag/payChannelCode/requestSignSeq 将为null, cardId={}",
                    request.getCardId());
            return;
        }
        payRequest.setTicketStatus(ticketResponse.getTicketStatus());
        payRequest.setOrderExpType(ticketResponse.getOrderExpType());
        payRequest.setOfflineFlag(ticketResponse.getOfflineFlag());
        payRequest.setTicketCode(ticketResponse.getTicketCode());
        payRequest.setCountingTimes(ticketResponse.getCountingTimes());
        payRequest.setCountingFlag(ticketResponse.getCountingFlag());
        payRequest.setAttributableParty(ticketResponse.getAttributableParty());
        payRequest.setReceivingParty(ticketResponse.getReceivingParty());
        payRequest.setPayChannelCode(ticketResponse.getPayChannelCode());
        if (StringUtils.hasText(ticketResponse.getPayChannelCode())) {
            payRequest.setPaymentVendor(ticketResponse.getPayChannelCode());
        }
        if (StringUtils.hasText(ticketResponse.getRequestSignSeq())) {
            payRequest.setRequestSignSeq(ticketResponse.getRequestSignSeq());
        }
        if (StringUtils.hasText(ticketResponse.getPayUserId())) {
            payRequest.setPayUserId(ticketResponse.getPayUserId());
        }
        log.info("IF1A-01 透传 ticketResponse 字段到 payRequest, cardId={}, "
                        + "ticketStatus={}, orderExpType={}, offlineFlag={}, "
                        + "companionFlag={}, ticketCode={}, countingTimes={}, "
                        + "countingFlag={}, attributableParty={}, receivingParty={}, "
                        + "payChannelCode={}, requestSignSeq={}",
                request.getCardId(), ticketResponse.getTicketStatus(),
                ticketResponse.getOrderExpType(), ticketResponse.getOfflineFlag(),
                ticketResponse.getCompanionFlag(), ticketResponse.getTicketCode(),
                ticketResponse.getCountingTimes(), ticketResponse.getCountingFlag(),
                ticketResponse.getAttributableParty(), ticketResponse.getReceivingParty(),
                ticketResponse.getPayChannelCode(),
                ticketResponse.getRequestSignSeq());
    }
    /** 根据进出站编码查询中文站名并写入扣费请求。 */
    private void fillStationNames(GateTxnPayReqDTO payRequest, String entryStationCode, String exitStationCode) {
        Set<String> stationCodes = new LinkedHashSet<>();
        if (StringUtils.hasText(entryStationCode)) {
            stationCodes.add(entryStationCode);
        }
        if (StringUtils.hasText(exitStationCode)) {
            stationCodes.add(exitStationCode);
        }
        if (stationCodes.isEmpty()) {
            return;
        }
        Map<String, String> stationNames = stationNameResolver.resolveStationNames(stationCodes);
        String entryName = stationNames.get(entryStationCode);
        if (StringUtils.hasText(entryName)) {
            payRequest.setEntryStationName(entryName);
        }
        String exitName = stationNames.get(exitStationCode);
        if (StringUtils.hasText(exitName)) {
            payRequest.setExitStationName(exitName);
        }
    }
}
