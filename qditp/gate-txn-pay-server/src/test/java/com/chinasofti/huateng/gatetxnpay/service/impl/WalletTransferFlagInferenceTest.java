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

class WalletTransferFlagInferenceTest {

    private static final int TRANSFER_REDUCTION_CENTS = 100;

    private static final int ORIGINAL_FARE = 400;
    private static final int WALLET_TOTAL_AMT = 5000;

    private static final int EXPECTED_WITH_ONE_CENT_OFF = 319;
    private static final int DISCOUNTED_FULL_FARE = 320;
    private static final int OFFLINE_TRANSFER_FARE = 240;

    private final TicketClient ticketClient = mock(TicketClient.class);
    private final ParaClient paraClient = mock(ParaClient.class);
    private final AccountClient accountClient = mock(AccountClient.class);
    private final WalletAppGatewayClient walletAppGatewayClient = mock(WalletAppGatewayClient.class);
    private final OfflineMetroTransferClient offlineMetroTransferClient =
            mock(OfflineMetroTransferClient.class);
    private final DiscountLevelMapper discountLevelMapper = mock(DiscountLevelMapper.class);

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


    @Test
    void authoritativeTransferSourceIsNeverConsultedOnline() {
        stubWalletAccount();
        stubDiscountLevel("0.8");

        calculate(order(EXPECTED_WITH_ONE_CENT_OFF), walletRequest());

        verifyNoInteractions(offlineMetroTransferClient);
    }


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
