package com.chinasofti.huateng.gatetxnpay.fare;

import com.chinasofti.huateng.gatetxnpay.constant.GateTxnPayRetCode;
import com.chinasofti.huateng.gatetxnpay.entity.DiscountLevel;
import com.chinasofti.huateng.gatetxnpay.mapper.DiscountLevelMapper;
import com.chinasofti.huateng.gatetxnpay.service.impl.OfflineMetroTransferClient;
import com.chinasofti.huateng.gatetxnpay.service.impl.WalletAppGatewayClient;
import com.chinasofti.huateng.model.app.QueryUserInfoReqDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.app.QueryWalletTotalAmtReqDTO;
import com.chinasofti.huateng.model.app.QueryWalletTotalAmtResult;
import com.chinasofti.huateng.model.app.RequestStationNameBatchReqDTO;
import com.chinasofti.huateng.model.app.RequestStationNameBatchResult;
import com.chinasofti.huateng.model.app.RequestStationNameResult;
import com.chinasofti.huateng.model.app.RequestTicketPriceByStationReqDTO;
import com.chinasofti.huateng.model.app.RequestTicketPriceByStationResult;
import com.chinasofti.huateng.model.ticket.QueryFirstEntryTxnReqDTO;
import com.chinasofti.huateng.model.ticket.QueryFirstEntryTxnResult;
import com.chinasofti.huateng.model.ticket.QueryLatestEntryTxnReqDTO;
import com.chinasofti.huateng.model.ticket.QueryLatestEntryTxnResult;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.para.ParaClient;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class FareDataGateway {

    private static final Logger log = LoggerFactory.getLogger(FareDataGateway.class);
    private static final String RET_SUCCESS = GateTxnPayRetCode.SUCCESS;

    private final TicketClient ticketClient;
    private final ParaClient paraClient;
    private final AccountClient accountClient;
    private final WalletAppGatewayClient walletAppGatewayClient;
    private final DiscountLevelMapper discountLevelMapper;
    private final OfflineMetroTransferClient offlineMetroTransferClient;

    public FareDataGateway(
            TicketClient ticketClient,
            ParaClient paraClient,
            AccountClient accountClient,
            WalletAppGatewayClient walletAppGatewayClient,
            DiscountLevelMapper discountLevelMapper,
            OfflineMetroTransferClient offlineMetroTransferClient) {
        this.ticketClient = ticketClient;
        this.paraClient = paraClient;
        this.accountClient = accountClient;
        this.walletAppGatewayClient = walletAppGatewayClient;
        this.discountLevelMapper = discountLevelMapper;
        this.offlineMetroTransferClient = offlineMetroTransferClient;
    }

    public Integer queryTicketPrice(String inStation, String outStation) {
        RequestTicketPriceByStationReqDTO fareRequest = new RequestTicketPriceByStationReqDTO();
        fareRequest.setEntryStationCode(inStation);
        fareRequest.setExitStationCode(outStation);
        RequestTicketPriceByStationResult fareResult = paraClient.requestTicketPriceByStation(fareRequest);
        int originalFare = parseAmount(fareResult == null ? null : fareResult.getTicketPrice());
        if (fareResult == null || !RET_SUCCESS.equals(fareResult.getRetCode()) || originalFare <= 0) {
            return null;
        }
        return originalFare;
    }

    public Integer queryTicketPriceQuietly(String inStation, String outStation) {
        if (!StringUtils.hasText(inStation) || !StringUtils.hasText(outStation)) {
            return null;
        }
        try {
            Integer fare = queryTicketPrice(inStation, outStation);
            if (fare == null) {
                log.warn("地铁原价查询未返回有效票价, inStation={}, outStation={}", inStation, outStation);
            }
            return fare;
        } catch (Exception e) {
            log.warn("地铁原价查询异常, inStation={}, outStation={}", inStation, outStation, e);
            return null;
        }
    }

    public QueryFirstEntryTxnResult queryFirstEntryTxn(String cardId, String ticketTransSeq) {
        QueryFirstEntryTxnReqDTO entryRequest = new QueryFirstEntryTxnReqDTO();
        entryRequest.setCardId(cardId);
        entryRequest.setTicketTransSeq(ticketTransSeq);
        return ticketClient.queryFirstEntryTxn(entryRequest);
    }


    public QueryLatestEntryTxnResult queryLatestEntryTxnBeforeExit(String cardId, String exitHandleDateTime) {
        QueryLatestEntryTxnReqDTO entryRequest = new QueryLatestEntryTxnReqDTO();
        entryRequest.setCardId(cardId);
        entryRequest.setExitHandleDateTime(exitHandleDateTime);
        return ticketClient.queryLatestEntryTxnBeforeExit(entryRequest);
    }

    public QueryUserInfoResult queryUserInfo(String thirdUserId, String cardId, String cardType) {
        QueryUserInfoReqDTO userRequest = new QueryUserInfoReqDTO();
        userRequest.setThirdUserId(thirdUserId);
        userRequest.setCardId(cardId);
        userRequest.setCardType(cardType);
        return accountClient.queryUserInfo(userRequest);
    }


    public QueryWalletTotalAmtResult queryWalletTotalAmt(String thirdUserId, String cardType,
                                                        String msisdn, String cardIssueCode,
                                                        boolean fillEmptyExtend) {
        QueryWalletTotalAmtReqDTO totalRequest = new QueryWalletTotalAmtReqDTO();
        totalRequest.setThirdUserId(thirdUserId);
        totalRequest.setCardType(cardType);
        totalRequest.setMsisdn(msisdn);
        totalRequest.setCardIssueCode(cardIssueCode);
        if (fillEmptyExtend) {
            totalRequest.setExtend1("");
            totalRequest.setExtend2("");
        }
        return walletAppGatewayClient.queryTotalAmt(totalRequest);
    }

    public Map<String, String> resolveStationNamesQuietly(Set<String> stationCodes) {
        Map<String, String> stationNames = new HashMap<>();
        if (stationCodes == null || stationCodes.isEmpty()) {
            return stationNames;
        }
        try {
            RequestStationNameBatchReqDTO nameRequest = new RequestStationNameBatchReqDTO();
            nameRequest.setStationCodes(new ArrayList<>(stationCodes));
            RequestStationNameBatchResult nameResult = paraClient.requestStationNameBatch(nameRequest);
            if (nameResult == null || !RET_SUCCESS.equals(nameResult.getRetCode())
                    || nameResult.getStationNameList() == null) {
                log.warn("站名批量查询未返回有效结果, stationCodes={}, retCode={}", stationCodes,
                        nameResult == null ? null : nameResult.getRetCode());
                return stationNames;
            }
            for (RequestStationNameResult item : nameResult.getStationNameList()) {
                if (item != null && StringUtils.hasText(item.getStationCode())
                        && StringUtils.hasText(item.getStationName())) {
                    stationNames.put(item.getStationCode(), item.getStationName());
                }
            }
        } catch (Exception e) {
            log.warn("站名批量查询异常，本次不回填站名, stationCodes={}", stationCodes, e);
        }
        return stationNames;
    }

    public DiscountLevel selectDiscountLevel(String discountType, int totalAmt) {
        return discountLevelMapper.selectApplicable(discountType, totalAmt);
    }

    public boolean isTransferReduction(String thirdUserId, String handleDateTime,
                                       String cardId, String ticketTransSeq) {
        return offlineMetroTransferClient.isReduction(thirdUserId, handleDateTime, cardId, ticketTransSeq);
    }

    private int parseAmount(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        return Integer.parseInt(value.trim());
    }
}
