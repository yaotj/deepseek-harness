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
import com.chinasofti.huateng.model.ticket.QueryLatestEntryTxnResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 出站扣费的金额计算。 */
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
    /** 查询本次行程的地铁原价并写入 {@code ORIGINAL_FARE}。 */
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

    /** 按进出站查询地铁原价（分），查不到返回 {@code null}。 */
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
    /** 离线码出站金额由服务端重算：出站前最近一笔进站、超时费、换乘减免、钱包折扣。 */
    public void calculateOfflineFare(GateTxnPay order, GateTxnPayReqDTO request) {
        if (!StringUtils.hasText(request.getTicketTransSeq())) {
            throw new IllegalStateException("离线码交易缺少ticketTransSeq");
        }
        if (!StringUtils.hasText(order.getOutTime())) {
            throw new IllegalStateException("离线码交易缺少出站时间");
        }
        // 进站取数口径（2026-09-22 修 C9）：按「同卡 + 进站 + HANDLE_DATE_TIME <= 本次出站时间」取最近一笔。
        // NEVER 退回按 ticketTransSeq 相等配对（gateway.queryFirstEntryTxn）：进站与出站是两笔不同交易，
        // 闸机上送的序列号天然不同（实测进站 0 / 出站 1），相等配对恒命中 0 行、订单永久卡 OFFLINE_FARE_PENDING。
        // NEVER 用 QRCODE_STATUS.GATE_IN_STATION / GATE_IN_TIME：那是会被后续行程覆盖的状态快照，
        // 而本方法也被延迟执行的补偿链路调用，延迟期间该卡再进站一次就会算错钱（比算不出更坏）。
        QueryLatestEntryTxnResult entry = gateway.queryLatestEntryTxnBeforeExit(
                request.getCardId(), order.getOutTime());
        if (entry == null || !RET_SUCCESS.equals(entry.getRetCode())
                || !StringUtils.hasText(entry.getHandleDateTime())
                || !StringUtils.hasText(entry.getHandleStationCode())) {
            throw new IllegalStateException(entry == null ? "出站前进站交易查询失败" : entry.getRetMsg());
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
