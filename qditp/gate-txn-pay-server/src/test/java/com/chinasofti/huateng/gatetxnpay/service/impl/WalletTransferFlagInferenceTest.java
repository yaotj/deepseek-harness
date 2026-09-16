package com.chinasofti.huateng.gatetxnpay.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.gatetxnpay.entity.DiscountLevel;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.fare.FareCalculator;
import com.chinasofti.huateng.gatetxnpay.fare.FareDataGateway;
import com.chinasofti.huateng.gatetxnpay.mapper.DiscountLevelMapper;
import com.chinasofti.huateng.model.app.QueryUserInfoReqDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.app.QueryWalletTotalAmtReqDTO;
import com.chinasofti.huateng.model.app.QueryWalletTotalAmtResult;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.para.ParaClient;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 钉住在线路径（非离线码）钱包折扣里 {@code TRANSFER_FLAG} 的**反推**判定。
 *
 * <p>{@code calculateWalletDiscount} 不去问「这位乘客到底有没有公交换乘」，而是自己算一个
 * {@code expected = round((原价 - 1) * 折扣率)}，再拿它和闸机上报的 {@code TRX_AMOUNT} 比：
 * 相等就断定「有换乘」置 02，不等就置 01。这个判定有三处 MUST 用断言钉住、NEVER 靠读代码保证：
 * <ul>
 *   <li>减的是**硬编码 1 分**，不是 {@code offline.billing.transfer-reduction-cents}（默认 100 分）；
 *       两条路径对同一个「换乘减免」差 100 倍，见 {@link #offlinePricedTransferTripIsClassifiedAsNoTransfer}；</li>
 *   <li>权威数据源 {@link OfflineMetroTransferClient} 就在同一个类的字段里，在线路径**一次都不调**，
 *       见 {@link #authoritativeTransferSourceIsNeverConsultedOnline}；</li>
 *   <li>算出来的 {@code expected} **不参与扣款**，只写进 {@code EXPECTED_GATE_AMOUNT} 当观测值，
 *       见 {@link #computedExpectedNeverChangesTheChargedAmount}。</li>
 * </ul>
 *
 * <p>本测试**只记录现状、不主张现状正确**。{@code FareCalculator} 类注释已声明那个 {@code -1}
 * 与「减不减换乘」的差异是搬迁前就存在的、是业务规则还是历史遗留尚未裁决。因此这里的断言值
 * 全部是「当前实际会算出什么」，裁决后改口径 MUST 同批改这些期望值并在注释里记下依据，
 * NEVER 把断言放宽成「非空」之类看不出口径的判断 —— 那样就白建这张网了。
 */
class WalletTransferFlagInferenceTest {

    /** 与线下路径同一个配置值，用来证明在线路径**根本不读它**。 */
    private static final int TRANSFER_REDUCTION_CENTS = 100;

    private static final int ORIGINAL_FARE = 400;
    private static final int WALLET_TOTAL_AMT = 5000;

    /** {@code round((400 - 1) * 0.8) = round(319.2) = 319}，闸机报这个数才会被判成有换乘。 */
    private static final int EXPECTED_WITH_ONE_CENT_OFF = 319;
    /** {@code round(400 * 0.8) = 320}，一分不减的折后价。 */
    private static final int DISCOUNTED_FULL_FARE = 320;
    /** {@code round((400 - 100) * 0.8) = 240}，线下路径命中换乘时会算出的价。 */
    private static final int OFFLINE_TRANSFER_FARE = 240;

    private final TicketClient ticketClient = mock(TicketClient.class);
    private final ParaClient paraClient = mock(ParaClient.class);
    private final AccountClient accountClient = mock(AccountClient.class);
    private final WalletAppGatewayClient walletAppGatewayClient = mock(WalletAppGatewayClient.class);
    private final OfflineMetroTransferClient offlineMetroTransferClient =
            mock(OfflineMetroTransferClient.class);
    private final DiscountLevelMapper discountLevelMapper = mock(DiscountLevelMapper.class);

    /** 闸机上报值与自算期望值相等 —— 这是唯一能得出 02 的情形。 */
    @Test
    void gateAmountEqualToComputedExpectedIsInferredAsTransfer() {
        stubWalletAccount();
        stubDiscountLevel("0.8");

        GateTxnPay order = order(EXPECTED_WITH_ONE_CENT_OFF);
        calculate(order, walletRequest());

        assertEquals(EXPECTED_WITH_ONE_CENT_OFF, order.getExpectedGateAmount(),
                "期望值 MUST 是 round((原价-1)*折扣率)，减的是 1 分而非配置的 100 分");
        assertEquals("02", order.getTransferFlag(), "相等即判定有换乘");
        assertEquals("SUCCESS", order.getDiscountCalcStatus());
        assertEquals(WALLET_TOTAL_AMT, order.getWalletTotalAmt());
        assertEquals(new BigDecimal("0.8"), order.getDiscountRate());
    }

    /** 差一分就翻转成 01：一分不减的折后价 320 被判成无换乘。 */
    @Test
    void gateAmountOneCentAwayFallsBackToNoTransfer() {
        stubWalletAccount();
        stubDiscountLevel("0.8");

        GateTxnPay order = order(DISCOUNTED_FULL_FARE);
        calculate(order, walletRequest());

        assertEquals(EXPECTED_WITH_ONE_CENT_OFF, order.getExpectedGateAmount(),
                "期望值只由原价与折扣率决定，NEVER 随闸机上报值变化");
        assertEquals("01", order.getTransferFlag(), "差一分即判定无换乘");
    }

    /**
     * 决定性用例：一笔**真按线下换乘规则定价**的行程（减 100 分再打折 = 240）
     * 在在线路径上被判成「无换乘」。
     *
     * <p>两条路径对同一个业务概念取了相差 100 倍的量级，因此只要减免真的是 1 元，
     * 在线路径的 02 就永远推不出来；反之若真的是 1 分，线下路径每笔多减 99 分。
     * 二者 NEVER 可能同时正确 —— 这就是需要业务裁决的那个点。
     */
    @Test
    void offlinePricedTransferTripIsClassifiedAsNoTransfer() {
        stubWalletAccount();
        stubDiscountLevel("0.8");

        GateTxnPay order = order(OFFLINE_TRANSFER_FARE);
        calculate(order, walletRequest());

        assertEquals("01", order.getTransferFlag(),
                "按 transfer-reduction-cents=100 定价的换乘行程，在线路径判成无换乘");
        assertEquals(EXPECTED_WITH_ONE_CENT_OFF, order.getExpectedGateAmount());
    }

    /**
     * {@code TRANSFER_FLAG} 是「这位乘客有没有从公交换乘过来」这一**事实**，
     * 权威来源是 {@link OfflineMetroTransferClient#isReduction}，且该协作者就在本类字段里。
     * 在线路径一次都不调它，纯靠金额相等去猜。
     */
    @Test
    void authoritativeTransferSourceIsNeverConsultedOnline() {
        stubWalletAccount();
        stubDiscountLevel("0.8");

        calculate(order(EXPECTED_WITH_ONE_CENT_OFF), walletRequest());

        verifyNoInteractions(offlineMetroTransferClient);
    }

    /**
     * 在线路径算出的折后价**不改扣款金额**：{@code TRX_AMOUNT} 与 {@code TOTAL_AMOUNT}
     * 在 {@code buildOrder} 里就按闸机上报值定好了，本方法只往 {@code EXPECTED_GATE_AMOUNT} 写观测值。
     *
     * <p>这与线下路径口径相反 —— 那边 {@code calculateOfflineFare} 会 {@code setTrxAmount}、
     * 算出来的就是真扣的钱。两处 {@code EXPECTED_GATE_AMOUNT} 语义不同，NEVER 合并。
     */
    @Test
    void computedExpectedNeverChangesTheChargedAmount() {
        stubWalletAccount();
        stubDiscountLevel("0.8");

        GateTxnPay order = order(DISCOUNTED_FULL_FARE);
        order.setTotalAmount(DISCOUNTED_FULL_FARE);
        calculate(order, walletRequest());

        assertEquals(DISCOUNTED_FULL_FARE, order.getTrxAmount(), "扣款金额 MUST 保持闸机上报值");
        assertEquals(DISCOUNTED_FULL_FARE, order.getTotalAmount(), "合计金额同样不被折扣计算改写");
        assertEquals(EXPECTED_WITH_ONE_CENT_OFF, order.getExpectedGateAmount(), "折后价只当观测值");
    }

    /** 概率取整是 HALF_UP：{@code 200 * 0.9225 = 184.5} 进位到 185，截断或 HALF_DOWN 会得 184。 */
    @Test
    void probeValueRoundsHalfUp() {
        stubWalletAccount();
        stubDiscountLevel("0.9225");

        GateTxnPay order = order(185);
        order.setOriginalFare(201);
        calculate(order, walletRequest());

        assertEquals(185, order.getExpectedGateAmount(), "(201-1)*0.9225=184.5 MUST 进位到 185");
        assertEquals("02", order.getTransferFlag());
    }

    /**
     * 钱包累计查询失败即整段降级：{@code FALLBACK} + 01，折扣率与期望值全留空。
     *
     * <p>这正是 {@code AFCITPDB} 里全部 8 行钱包订单的实际状态（2026-09-14 实测：
     * {@code DISCOUNT_CALC_STATUS} 无一行 SUCCESS、{@code DISCOUNT_RATE} 46 行全空），
     * 也就是说线上那个 01 是本分支写的，**不是反推出来的**。
     */
    @Test
    void walletQueryFailureFallsBackToNoTransfer() {
        stubWalletAccount();
        QueryWalletTotalAmtResult total = new QueryWalletTotalAmtResult();
        total.setRetCode("9999");
        when(walletAppGatewayClient.queryTotalAmt(any(QueryWalletTotalAmtReqDTO.class))).thenReturn(total);

        GateTxnPay order = order(DISCOUNTED_FULL_FARE);
        calculate(order, walletRequest());

        assertEquals("01", order.getTransferFlag());
        assertEquals("FALLBACK", order.getDiscountCalcStatus());
        assertNull(order.getExpectedGateAmount(), "降级时 MUST 不留半成品期望值");
        assertNull(order.getDiscountRate());
        verifyNoInteractions(discountLevelMapper);
    }

    /** 非钱包渠道整段跳过，连 01 都不写 —— transferFlag 保持入库前的空值。 */
    @Test
    void nonWalletVendorLeavesTransferFlagUntouched() {
        GateTxnPay order = order(DISCOUNTED_FULL_FARE);
        GateTxnPayReqDTO request = walletRequest();
        request.setPaymentVendor("03");
        calculate(order, request);

        assertNull(order.getTransferFlag());
        assertNull(order.getDiscountCalcStatus());
        verifyNoInteractions(accountClient, walletAppGatewayClient, discountLevelMapper,
                offlineMetroTransferClient);
    }

    private void stubWalletAccount() {
        QueryUserInfoResult user = new QueryUserInfoResult();
        user.setRetCode("0000");
        user.setThirdPayId("PAY-U1");
        user.setMsisdn("13800000000");
        user.setCardIssueCode("IC");
        when(accountClient.queryUserInfo(any(QueryUserInfoReqDTO.class))).thenReturn(user);
    }

    private void stubDiscountLevel(String rate) {
        QueryWalletTotalAmtResult total = new QueryWalletTotalAmtResult();
        total.setRetCode("0000");
        total.setTotalAmt(WALLET_TOTAL_AMT);
        when(walletAppGatewayClient.queryTotalAmt(any(QueryWalletTotalAmtReqDTO.class))).thenReturn(total);

        DiscountLevel level = new DiscountLevel();
        level.setLevelAmt(WALLET_TOTAL_AMT);
        level.setLevelDiscount(new BigDecimal(rate));
        when(discountLevelMapper.selectApplicable(anyString(), eq(WALLET_TOTAL_AMT))).thenReturn(level);
    }

    /**
     * {@code ORIGINAL_FARE} 直接写在订单上，对应生产链路里 {@code requestPay} 先调
     * {@code fillOriginalFare} 的既有顺序，因此本测试不需要 stub para。
     *
     * @param gateReportedAmount 闸机上报的 {@code TRX_AMOUNT}，即反推判定的比较对象
     */
    private GateTxnPay order(int gateReportedAmount) {
        GateTxnPay order = new GateTxnPay();
        order.setOrderNo("GT20260913100000000123456");
        order.setThirdUserId("U1");
        order.setCardId("9876543210123456");
        order.setCardType("04");
        order.setTxnDate("20260913");
        order.setOriginalFare(ORIGINAL_FARE);
        order.setTrxAmount(gateReportedAmount);
        return order;
    }

    private GateTxnPayReqDTO walletRequest() {
        GateTxnPayReqDTO request = new GateTxnPayReqDTO();
        request.setCardId("9876543210123456");
        request.setCardType("04");
        request.setTrxType("02");
        request.setPaymentVendor("0B");
        return request;
    }

    private void calculate(GateTxnPay order, GateTxnPayReqDTO request) {
        new FareCalculator(
                new FareDataGateway(ticketClient, paraClient, accountClient, walletAppGatewayClient,
                        discountLevelMapper, offlineMetroTransferClient),
                1200L, 300, TRANSFER_REDUCTION_CENTS)
                .calculateWalletDiscount(order, request);
    }
}
