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

@Component
class GateTxnPayRequestAssembler {
    private static final Logger log = LoggerFactory.getLogger(GateTxnPayRequestAssembler.class);

    private final StationNameResolver stationNameResolver;

    public GateTxnPayRequestAssembler(StationNameResolver stationNameResolver) {
        this.stationNameResolver = stationNameResolver;
    }

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
