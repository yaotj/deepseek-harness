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

/**
 * 算价所需的**取数**收口：票价、进站交易、账户、钱包累计、折扣档位、换乘减免。
 *
 * <p>2026-09-14 从 {@link FareCalculator} 拆出（ADR-D69）。拆的理由不是行数，而是
 * {@code FareCalculator} 注入了 6 个协作者、其中 5 个是**跨服务 / 跨系统调用** ——
 * 一个「算钱」的类实际在做编排。现在编排在本类、算钱在那一类，
 * {@code FareCalculator} 只依赖本类 + 三个 {@code @Value}。</p>
 *
 * <p><b>本类只取数、只做「空 / retCode / 明显非法值」这一层判定，NEVER 写业务判据</b>：
 * 「查不到票价算不算失败」「非 0B 渠道要不要查钱包」这类结论属于算价规则，MUST 留在
 * {@code FareCalculator}。因此本类的返回值一律是「拿到的东西或 null」，
 * <b>NEVER 在这里抛业务异常</b> —— 两条算价路径对同一个查询失败的措辞与后果都不同
 * （一条降级、一条中断），在这里抛就把那个差异抹平了。</p>
 */
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

    /**
     * 查地铁票价（分），非 {@code 0000} 或 {@code <= 0} 返 {@code null}，**异常照原样抛出**。
     *
     * <p>给「查不到就中断本笔」的路径用（钱包补查、离线码重算）。</p>
     */
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

    /**
     * 同上，但**吞掉所有异常只记日志**。
     *
     * <p>给「查不到只是展示降级、出站 MUST 放行」的路径用（{@code ORIGINAL_FARE} 填充与历史补数）。
     * <b>NEVER 把这两个方法合成一个</b> —— 吞不吞异常正是那两条路径唯一的区别，
     * 合一之后必有一条被改错，且编译与单测都发现不了。</p>
     */
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
     * @param fillEmptyExtend 在线钱包路径会额外把 {@code extend1} / {@code extend2} 置空串，
     *                        离线码路径不置。**这个差异是搬迁前就存在的、影响出向报文**，
     *                        因此原样保留成一个开关，<b>NEVER 图省事统一成一种</b> ——
     *                        改它等于改对外报文，MUST 先与对端确认。
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

    /**
     * 批量查站点编码对应的中文站名，**吞掉所有异常与非 {@code 0000}，只返回查到的那些**。
     *
     * <p>给站名回填用（{@link com.chinasofti.huateng.gatetxnpay.station.StationNameBackfiller}）。
     * 站名只影响展示，<b>NEVER 因为查不到站名而中断出站扣费</b> —— 所以本方法与
     * {@link #queryTicketPriceQuietly} 同属「静默降级」那一类，返回空 Map 即「一个都没查到」。</p>
     *
     * <p>查不到某个码时该键**不出现在返回值里**（而不是映射到 null）：回填方据此决定「保留原值」，
     * <b>NEVER 改成把查不到的码映射成空串</b> —— 那会把上游已填对的站名覆盖成空。
     * 实测能查不到的真实取值是占位站码 {@code FFFF}（{@code ticket.default-last-txn-station}），
     * 它不在 {@code TBL_STATION_INFO} 里。</p>
     */
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

    /**
     * 离线码钱包订单查公交换乘减免资格。
     *
     * <p>对端不可达或答非 {@code 0000} 时**照原样抛 {@code IllegalStateException}**
     * （由 {@code OfflineMetroTransferClient} 抛出，本方法不接）：换乘资格影响实收金额，
     * <b>NEVER 降级成 false</b> —— 那是静默少收换乘减免、属资损方向。</p>
     */
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
