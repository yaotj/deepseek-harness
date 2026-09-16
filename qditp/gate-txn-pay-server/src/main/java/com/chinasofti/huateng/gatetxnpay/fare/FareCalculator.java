package com.chinasofti.huateng.gatetxnpay.fare;

import com.chinasofti.huateng.gatetxnpay.constant.DiscountCalcStatus;
import com.chinasofti.huateng.gatetxnpay.constant.GateTxnPayRetCode;
import com.chinasofti.huateng.gatetxnpay.constant.GateTxnPayFieldCode;
import com.chinasofti.huateng.gatetxnpay.entity.DiscountLevel;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.app.QueryWalletTotalAmtResult;
import com.chinasofti.huateng.model.enums.CardTypeCodeEnum;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.ticket.QueryFirstEntryTxnResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 出站扣费的金额计算。
 *
 * <p>本类只算钱、只改传入的 {@link GateTxnPay} 字段，<b>不落库、不调 pay-sign</b>。
 * 2026-09-14（ADR-D69）把 6 个协作者收口到 {@link FareDataGateway} 一个：此前本类直接注入
 * {@code ParaClient} / {@code AccountClient} / {@code TicketClient} + 两个自建 client + 一个 mapper，
 * 「算票价」的类实际在做跨服务编排。<b>取数一律经 gateway，NEVER 在本类里再注入任何 Client 或 Mapper</b>；
 * 反过来，<b>判定与措辞 MUST 留在本类</b>——gateway 只返回「拿到的东西或 null」。</p>
 *
 * <p><b>本类里有两条口径不同的钱包算价路径，NEVER 擅自合并</b>：
 * <ul>
 *   <li>{@link #calculateOfflineFare} 折扣基数是<b>换乘减免后</b>的票价，
 *       公式 {@code (票价 - 减免) * 折扣率}；</li>
 *   <li>{@link #calculateWalletDiscount} 折扣基数是 {@code 原价 - 1}、<b>不减换乘</b>，
 *       且 {@code TRANSFER_FLAG} 是拿算出来的期望值与闸机上报的 {@code TRX_AMOUNT} 比较反推的。</li>
 * </ul>
 * 那个 {@code -1} 与「减不减换乘」的差异<b>是搬迁前就存在的</b>，是业务规则还是历史遗留尚未裁决，
 * 历次搬迁均逐字保留。要动 MUST 先与业务确认，并同步改 {@code OfflineFareCalculationTest} 的期望值。
 * 两条路径查钱包累计时的报文差异（在线补空 {@code extend1/2}、离线不补）见
 * {@link FareDataGateway#queryWalletTotalAmt}。</p>
 *
 * <p>{@code trimToNull} / {@code truncate} 在本类各留一份私有副本：它们在
 * {@code GateTxnPayServiceImpl} 里另有大量调用点，为此新建工具类违反「NEVER 主动创建工具类」，
 * 三行的重复比一个跨类工具更便宜。</p>
 */
@Component
public class FareCalculator {

    private static final Logger log = LoggerFactory.getLogger(FareCalculator.class);
    private static final String RET_SUCCESS = GateTxnPayRetCode.SUCCESS;

    private final FareDataGateway gateway;
    private final long offlineTimeoutSeconds;
    private final int offlineTimeoutFeeCents;
    private final int transferReductionCents;

    public FareCalculator(
            FareDataGateway gateway,
            @Value("${offline.billing.timeout-seconds:1200}") long offlineTimeoutSeconds,
            @Value("${offline.billing.timeout-fee-cents:300}") int offlineTimeoutFeeCents,
            @Value("${offline.billing.transfer-reduction-cents:100}") int transferReductionCents) {
        this.gateway = gateway;
        this.offlineTimeoutSeconds = offlineTimeoutSeconds;
        this.offlineTimeoutFeeCents = offlineTimeoutFeeCents;
        this.transferReductionCents = transferReductionCents;
    }
    /**
     * 查询本次行程的地铁原价并写入 {@code ORIGINAL_FARE}。
     *
     * <p>该字段是「按进出站算出来的地铁票价」，与本笔是否扣费、走哪个支付渠道都无关，
     * APP 扣费详情靠它算「已省金额」（日票 / 员工票的展示口径是「原价 X，本票抵扣，实付 0」）。
     * 因此 <b>NEVER 把它放进钱包折扣计算的 if 里</b>——那样非 0B 渠道与不参与钱包累计的票种全部拿不到值。</p>
     *
     * <p>本方法 <b>吞掉所有异常只记日志</b>（在 gateway 的 quietly 版本里）：票价查不到属于展示降级，
     * 出站 MUST 放行，NEVER 因为 para-server 不可用而拦住乘客或让扣费流程失败。</p>
     */
    public void fillOriginalFare(GateTxnPay order) {
        if (order.getOriginalFare() != null && order.getOriginalFare() > 0) {
            return;
        }
        Integer originalFare = queryOriginalFare(order.getInStation(), order.getOutStation());
        if (originalFare == null) {
            return;
        }
        order.setOriginalFare(originalFare);
        log.info("地铁原价查询完成, cardId={}, inStation={}, outStation={}, originalFare={}",
                order.getCardId(), order.getInStation(), order.getOutStation(), originalFare);
    }

    /**
     * 按进出站查询地铁原价（分），查不到返回 {@code null}。
     *
     * <p>出站主链路与历史补数接口共用这一处查询，**NEVER 各写一份**——口径分叉后
     * 补数结果与出站落库值会不一致。</p>
     */
    public Integer queryOriginalFare(String inStation, String outStation) {
        return gateway.queryTicketPriceQuietly(inStation, outStation);
    }
    /** 钱包订单在首次落库前生成并固定优惠快照，支付重试不会重新计算。 */
    public void calculateWalletDiscount(GateTxnPay order, GateTxnPayReqDTO request) {
        if (!GateTxnPayFieldCode.isWalletVendor(request.getPaymentVendor())) {
            return;
        }
        order.setTransferFlag("01");
        order.setCumulativeType("02".equals(request.getTrxType()) ? "01" : "02");
        if (!isWalletCumulativeEligible(request)) {
            order.setCumulativeType("03");
        }
        try {
            QueryUserInfoResult user = gateway.queryUserInfo(
                    order.getThirdUserId(), order.getCardId(), order.getCardType());
            if (user == null || !RET_SUCCESS.equals(user.getRetCode())) {
                throw new IllegalStateException("账户信息查询失败");
            }
            order.setPayUserId(trimToNull(user.getThirdPayId()));
            if (!isWalletCumulativeEligible(request)) {
                order.setCumulativeType("03");
                order.setDiscountCalcStatus(DiscountCalcStatus.SKIPPED.code());
                order.setDiscountCalcMsg("同行票、第三方票、员工票或日票不参与钱包累计");
                return;
            }

            // ORIGINAL_FARE 已由 requestPay 里的 fillOriginalFare 提前查好，这里复用，避免同一笔出站
            // 对 para-server 发两次票价查询；仅在提前查询失败（null）时才补查一次。
            int originalFare = order.getOriginalFare() == null ? 0 : order.getOriginalFare();
            if (originalFare <= 0) {
                Integer queried = gateway.queryTicketPrice(order.getInStation(), order.getOutStation());
                if (queried == null) {
                    throw new IllegalStateException("地铁原价查询失败");
                }
                originalFare = queried;
                order.setOriginalFare(originalFare);
            }
            QueryWalletTotalAmtResult totalResult = gateway.queryWalletTotalAmt(
                    order.getThirdUserId(), order.getCardType(), user.getMsisdn(), user.getCardIssueCode(), true);
            if (totalResult == null || !RET_SUCCESS.equals(totalResult.getRetCode()) || totalResult.getTotalAmt() == null
                    || totalResult.getTotalAmt() < 0) {
                throw new IllegalStateException("钱包累计金额查询失败");
            }
            order.setWalletTotalAmt(totalResult.getTotalAmt());

            DiscountLevel level = gateway.selectDiscountLevel("01", totalResult.getTotalAmt());
            if (level == null || level.getLevelDiscount() == null || level.getLevelAmt() == null) {
                throw new IllegalStateException("无有效折扣档位");
            }
            order.setDiscountLevelAmt(level.getLevelAmt());
            order.setDiscountRate(level.getLevelDiscount());
            int expected = BigDecimal.valueOf(originalFare - 1L)
                    .multiply(level.getLevelDiscount())
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValueExact();
            order.setExpectedGateAmount(expected);
            order.setTransferFlag(expected == order.getTrxAmount() ? "02" : "01");
            order.setDiscountCalcStatus(DiscountCalcStatus.SUCCESS.code());
            order.setDiscountCalcMsg("钱包原价、累计金额和折扣档位计算成功");
        } catch (Exception e) {
            order.setTransferFlag("01");
            order.setDiscountCalcStatus(DiscountCalcStatus.FALLBACK.code());
            order.setDiscountCalcMsg(truncate("钱包优惠计算失败，按闸机原始金额扣款：" + e.getMessage(), 500));
            log.warn("钱包优惠计算失败，继续原始金额扣款, orderNo={}", order.getOrderNo(), e);
        }
    }
    /**
     * 离线码出站金额由服务端重算：同序列号首笔进站、超时费、换乘减免、钱包折扣。
     * 超时费始终单独放在 overtimeAmount，不进入折扣基数。
     */
    public void calculateOfflineFare(GateTxnPay order, GateTxnPayReqDTO request) {
        if (!StringUtils.hasText(request.getTicketTransSeq())) {
            throw new IllegalStateException("离线码交易缺少ticketTransSeq");
        }
        QueryFirstEntryTxnResult entry = gateway.queryFirstEntryTxn(
                request.getCardId(), request.getTicketTransSeq());
        if (entry == null || !RET_SUCCESS.equals(entry.getRetCode())
                || !StringUtils.hasText(entry.getHandleDateTime())
                || !StringUtils.hasText(entry.getHandleStationCode())) {
            throw new IllegalStateException(entry == null ? "同序列号进站交易查询失败" : entry.getRetMsg());
        }
        order.setInStation(entry.getHandleStationCode());
        order.setInTime(entry.getHandleDateTime());

        Integer queriedFare = gateway.queryTicketPrice(entry.getHandleStationCode(), order.getOutStation());
        if (queriedFare == null) {
            throw new IllegalStateException("离线码地铁票价查询失败");
        }
        int originalFare = queriedFare;
        order.setOriginalFare(originalFare);

        long rideSeconds = calculateRideSeconds(entry.getHandleDateTime(), order.getOutTime());
        boolean overtime = rideSeconds > offlineTimeoutSeconds;
        int overtimeFee = overtime ? Math.max(0, offlineTimeoutFeeCents) : 0;
        order.setOvertimeAmount(overtimeFee);

        int fareAfterTransfer = originalFare;
        boolean wallet = GateTxnPayFieldCode.isWalletVendor(request.getPaymentVendor());
        if (wallet) {
            order.setTransferFlag("01");
            boolean reduction = gateway.isTransferReduction(order.getThirdUserId(), order.getOutTime(),
                    order.getCardId(), order.getTicketTransSeq());
            if (reduction) {
                fareAfterTransfer = Math.max(0, originalFare - Math.max(0, transferReductionCents));
                order.setTransferFlag("02");
            }
            QueryUserInfoResult user = gateway.queryUserInfo(
                    order.getThirdUserId(), order.getCardId(), order.getCardType());
            if (user == null || !RET_SUCCESS.equals(user.getRetCode())) {
                throw new IllegalStateException("钱包账户信息查询失败");
            }
            order.setPayUserId(trimToNull(user.getThirdPayId()));
            if (isWalletCumulativeEligible(request)) {
                QueryWalletTotalAmtResult totalResult = gateway.queryWalletTotalAmt(
                        order.getThirdUserId(), order.getCardType(), user.getMsisdn(), user.getCardIssueCode(), false);
                if (totalResult == null || !RET_SUCCESS.equals(totalResult.getRetCode())
                        || totalResult.getTotalAmt() == null) {
                    throw new IllegalStateException("钱包累计金额查询失败");
                }
                order.setWalletTotalAmt(totalResult.getTotalAmt());
                DiscountLevel level = gateway.selectDiscountLevel("01", totalResult.getTotalAmt());
                if (level == null || level.getLevelDiscount() == null || level.getLevelAmt() == null) {
                    throw new IllegalStateException("无有效钱包折扣档位");
                }
                order.setDiscountLevelAmt(level.getLevelAmt());
                order.setDiscountRate(level.getLevelDiscount());
                int discountedFare = BigDecimal.valueOf(fareAfterTransfer)
                        .multiply(level.getLevelDiscount()).setScale(0, RoundingMode.HALF_UP).intValueExact();
                order.setExpectedGateAmount(discountedFare);
                order.setDiscountCalcStatus(DiscountCalcStatus.SUCCESS.code());
                order.setDiscountCalcMsg(overtime ? "离线码票价、换乘和钱包折扣计算成功，超时费不参与优惠" : "离线码票价、换乘和钱包折扣计算成功");
                order.setTrxAmount(Math.max(0, discountedFare));
            } else {
                order.setDiscountCalcStatus(DiscountCalcStatus.SKIPPED.code());
                order.setDiscountCalcMsg("当前票卡不参与钱包累计折扣");
                order.setTrxAmount(fareAfterTransfer);
            }
        } else {
            order.setTransferFlag("01");
            order.setDiscountCalcStatus(DiscountCalcStatus.SKIPPED.code());
            order.setDiscountCalcMsg("非钱包渠道不计算换乘和钱包折扣");
            order.setTrxAmount(fareAfterTransfer);
        }
        if (overtime) {
            order.setDiscountCalcMsg(truncate(order.getDiscountCalcMsg() + "；已加收超时费" + overtimeFee + "分", 500));
        }
    }
    private long calculateRideSeconds(String entryTime, String exitTime) {
        try {
            LocalDateTime entry = LocalDateTime.parse(entryTime, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            LocalDateTime exit = LocalDateTime.parse(exitTime, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            long seconds = java.time.Duration.between(entry, exit).getSeconds();
            if (seconds < 0) {
                throw new IllegalStateException("离线码进出站时间顺序无效");
            }
            return seconds;
        } catch (RuntimeException e) {
            if (e instanceof IllegalStateException) throw e;
            throw new IllegalStateException("离线码进出站时间格式无效", e);
        }
    }

    private boolean isWalletCumulativeEligible(GateTxnPayReqDTO request) {
        if (CardTypeCodeEnum.isDailyTicket(request.getCardType()) || CardTypeCodeEnum.isEmployeeCard(request.getCardType())
                || CardTypeCodeEnum.isHceCard(request.getCardType())) {
            return false;
        }
        return !GateTxnPayFieldCode.isCompanionOrThirdParty(request.getCompanionFlag());
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
