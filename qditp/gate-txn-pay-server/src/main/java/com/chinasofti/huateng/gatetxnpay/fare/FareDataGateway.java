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

/** 算价所需的取数收口：票价、进站交易、账户、钱包累计、折扣档位、换乘减免。 */
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

    /** 查地铁票价（分），非 {@code 0000} 或 {@code <= 0} 返 {@code null}，异常照原样抛出。 */
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

    /** 同上，但吞掉所有异常只记日志。 */
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

    /** 查同序列号首笔进站交易，返回原始应答（含 retCode / retMsg），判定留给调用方。 */
    public QueryFirstEntryTxnResult queryFirstEntryTxn(String cardId, String ticketTransSeq) {
        QueryFirstEntryTxnReqDTO entryRequest = new QueryFirstEntryTxnReqDTO();
        entryRequest.setCardId(cardId);
        entryRequest.setTicketTransSeq(ticketTransSeq);
        return ticketClient.queryFirstEntryTxn(entryRequest);
    }

    /**
     * 查「同卡 + 早于或等于本次出站时间的最近一笔进站交易」，返回原始应答，判定留给调用方。
     *
     * <p>离线码出站算价的现行取数口径。<b>NEVER 退回上面那条按 {@code ticketTransSeq} 相等配对的方法</b> ——
     * 进站与出站是同一张卡的两笔不同交易，闸机上送的序列号天然不同（2026-09-22 实测进站 0 / 出站 1），
     * 相等配对恒命中 0 行，订单永久卡在 {@code OFFLINE_FARE_PENDING}。
     * <b>也 NEVER 改用 {@code QRCODE_STATUS.GATE_IN_STATION} / {@code GATE_IN_TIME}</b>：
     * 那是会被下一趟行程覆盖的状态快照，而补偿是延迟执行的，延迟期间该卡再进站就会算错钱。
     * 上面那条方法保留未删（可能仍有别的调用方），两者并存、只增不改。
     */
    public QueryLatestEntryTxnResult queryLatestEntryTxnBeforeExit(String cardId, String exitHandleDateTime) {
        QueryLatestEntryTxnReqDTO entryRequest = new QueryLatestEntryTxnReqDTO();
        entryRequest.setCardId(cardId);
        entryRequest.setExitHandleDateTime(exitHandleDateTime);
        return ticketClient.queryLatestEntryTxnBeforeExit(entryRequest);
    }

    /** 查账户信息，返回原始应答，判定留给调用方（两条路径的失败措辞不同）。 */
    public QueryUserInfoResult queryUserInfo(String thirdUserId, String cardId, String cardType) {
        QueryUserInfoReqDTO userRequest = new QueryUserInfoReqDTO();
        userRequest.setThirdUserId(thirdUserId);
        userRequest.setCardId(cardId);
        userRequest.setCardType(cardType);
        return accountClient.queryUserInfo(userRequest);
    }

    /**
     * 查钱包累计金额。
     *
     * @param fillEmptyExtend 在线钱包路径会额外把 {@code extend1} / {@code extend2} 置空串。
     */
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

    /** 批量查站点编码对应的中文站名，吞掉所有异常与非 {@code 0000}，只返回查到的那些。 */
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

    /** 按累计金额取折扣档位，取不到返 {@code null}。 */
    public DiscountLevel selectDiscountLevel(String discountType, int totalAmt) {
        return discountLevelMapper.selectApplicable(discountType, totalAmt);
    }

    /** 离线码钱包订单查公交换乘减免资格。 */
    public boolean isTransferReduction(String thirdUserId, String handleDateTime,
                                       String cardId, String ticketTransSeq) {
        return offlineMetroTransferClient.isReduction(thirdUserId, handleDateTime, cardId, ticketTransSeq);
    }

    /** 金额字段按分保存，空值按 0 处理。 */
    private int parseAmount(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        return Integer.parseInt(value.trim());
    }
}
