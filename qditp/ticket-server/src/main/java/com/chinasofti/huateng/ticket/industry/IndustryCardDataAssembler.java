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

/**
 * 生码入参组装 + 调 industry-data-server 生码，是这条链路**唯一一份**组装逻辑（ADR-D142）。
 *
 * <p>原实现在 `fep-app-server` 里有两份近逐字副本（在线码 `buildCardDataRequest`
 * 与离线码 `buildNoSignalCardData`，13 行 setter 段只差 `ticketStatus` / `txnSeq` 两处取值）。
 * 现在把这两处提成入参，两条链路共用同一个方法 —— <b>NEVER 再为某条链路复制一份</b>。
 *
 * <p>本类刻意**不做**任何业务分流（HCE 短路、日票前置、站内外双码都在
 * {@link IndustryDataOrchestrator} 里）：组装只负责「把已定的值摆进 DTO」。
 */
@Component
public class IndustryCardDataAssembler {
    private static final Logger log = LoggerFactory.getLogger(IndustryCardDataAssembler.class);

    /** 爱山东卡种的发行渠道位固定 01，与 `CARD_ISSUE_CODE` 无关。 */
    private static final String AI_SHAN_DONG_ISSUE_CHANNEL = "01";

    private final IndustryDataClient industryDataClient;

    @Value("${industry.issue-channel-code:01}")
    private String defaultIssueChannelCode;

    public IndustryCardDataAssembler(IndustryDataClient industryDataClient) {
        this.industryDataClient = industryDataClient;
    }

    /**
     * 组装并生码。
     *
     * @param ticketStatus 码体票卡状态位：在线码取码状态表当前值，离线码由调用方按进站 / 出站指定
     * @param txnSeq       码体交易序列号：在线码取当前值，离线码进站码要 +1（见 {@link IndustryDataOrchestrator}）
     */
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

    /**
     * 发行渠道位取值：爱山东强制 {@code 01}，否则用开户回填的 {@code CARD_ISSUE_CODE}，
     * 都没有才回落配置 {@code industry.issue-channel-code}。
     */
    private String resolveIssueChannelCode(String cardType, String cardIssueCode) {
        if (CardTypeMapping.isAiShanDong(cardType)) {
            return AI_SHAN_DONG_ISSUE_CHANNEL;
        }
        return StringUtils.hasText(cardIssueCode) ? cardIssueCode.trim() : defaultIssueChannelCode;
    }
}
