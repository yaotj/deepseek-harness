package com.chinasofti.huateng.ticket.industry;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.app.CardTypeMapping;
import com.chinasofti.huateng.model.app.IndustryCardDataBuildReqDTO;
import com.chinasofti.huateng.model.app.IndustryCardDataBuildRespDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusRespDTO;
import com.chinasofti.huateng.rpc.industry.IndustryDataClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;


@Component
public class IndustryCardDataAssembler {
    private static final Logger log = LoggerFactory.getLogger(IndustryCardDataAssembler.class);

    private static final String AI_SHAN_DONG_ISSUE_CHANNEL = "01";

    private final IndustryDataClient industryDataClient;

    @Value("${industry.issue-channel-code:01}")
    private String defaultIssueChannelCode;

    public IndustryCardDataAssembler(IndustryDataClient industryDataClient) {
        this.industryDataClient = industryDataClient;
    }


    public IndustryCardDataBuildRespDTO buildAndSign(IndustryDataQuery query,
                                                    QueryStatusRespDTO qrStatus,
                                                    String signChannelCode,
                                                    String cardIssueCode,
                                                    String ticketStatus,
                                                    String txnSeq) {
        IndustryCardDataBuildReqDTO cardDataRequest = new IndustryCardDataBuildReqDTO();
        cardDataRequest.setThirdUserId(query.thirdUserId());
        cardDataRequest.setCardId(query.cardId());
        cardDataRequest.setCardType(query.cardType());
        cardDataRequest.setTicketStatus(ticketStatus);
        cardDataRequest.setLastTxnStation(qrStatus.getLastTxnStation());
        cardDataRequest.setLastTxnTime(qrStatus.getLastTxnTime());
        cardDataRequest.setGateInStation(qrStatus.getGateInStation());
        cardDataRequest.setGateInTime(qrStatus.getGateInTime());
        cardDataRequest.setTxnSeq(txnSeq);
        cardDataRequest.setIssueChannelCode(resolveIssueChannelCode(query.cardType(), cardIssueCode));
        cardDataRequest.setSignChannelCode(signChannelCode);

        log.info("调用industry-data-server生成卡数据, request={}", JSON.toJSONString(cardDataRequest));
        IndustryCardDataBuildRespDTO response = industryDataClient.buildCardData(cardDataRequest);
        log.info("调用industry-data-server生成卡数据完成, response={}", JSON.toJSONString(response));
        return response;
    }


    private String resolveIssueChannelCode(String cardType, String cardIssueCode) {
        if (CardTypeMapping.isAiShanDong(cardType)) {
            return AI_SHAN_DONG_ISSUE_CHANNEL;
        }
        return StringUtils.hasText(cardIssueCode) ? cardIssueCode.trim() : defaultIssueChannelCode;
    }
}
