package com.chinasofti.huateng.gatetxnpay.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.chinasofti.huateng.model.app.RequestTicketPriceByStationReqDTO;
import com.chinasofti.huateng.model.app.RequestTicketPriceByStationResult;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.ticket.QueryLatestEntryTxnReqDTO;
import com.chinasofti.huateng.model.ticket.QueryLatestEntryTxnResult;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.para.ParaClient;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 钉住离线码出站金额重算的四步顺序与超时费的隔离性。 */
class OfflineFareCalculationTest {

    private static final long TIMEOUT_SECONDS = 1200L;
    private static final int TIMEOUT_FEE_CENTS = 300;
    private static final int TRANSFER_REDUCTION_CENTS = 100;

    private static final String IN_STATION = "0101";
    private static final String OUT_STATION = "0205";
    private static final String ENTRY_TIME = "20260913100000";
    /** 与进站相距 600 秒，小于 1200 秒的超时阈值。 */
    private static final String EXIT_TIME_IN_TIME = "20260913101000";
    /** 与进站相距 3600 秒，超过阈值。 */
    private static final String EXIT_TIME_OVERTIME = "20260913110000";

    private static final int TICKET_PRICE = 400;
    private static final int WALLET_TOTAL_AMT = 5000;

    private final TicketClient ticketClient = mock(TicketClient.class);
    private final ParaClient paraClient = mock(ParaClient.class);
    private final AccountClient accountClient = mock(AccountClient.class);
    private final WalletAppGatewayClient walletAppGatewayClient = mock(WalletAppGatewayClient.class);
    private final OfflineMetroTransferClient offlineMetroTransferClient =
            mock(OfflineMetroTransferClient.class);
    private final DiscountLevelMapper discountLevelMapper = mock(DiscountLevelMapper.class);

    @Test
    void walletOrderAppliesTransferReductionBeforeDiscount() {
        stubEntry();
        stubFare();
        stubWalletAccount();
        stubTransferReduction(true);
        stubDiscountLevel("0.8");

        GateTxnPay order = order(EXIT_TIME_IN_TIME);
        calculate(order, walletRequest());

        assertEquals(IN_STATION, order.getInStation(), "进站站点 MUST 取同序列号首笔进站");
        assertEquals(ENTRY_TIME, order.getInTime());
        assertEquals(TICKET_PRICE, order.getOriginalFare(), "原价 MUST 是 para 返回的票价原值");
        assertEquals(0, order.getOvertimeAmount(), "未超时 MUST 不加收");
        assertEquals("02", order.getTransferFlag(), "命中换乘减免 MUST 置 02");
        assertEquals(240, order.getTrxAmount(),
                "MUST 先减免再打折：(400-100)*0.8=240；先打折再减免会得 220");
        assertEquals(240, order.getExpectedGateAmount());
        assertEquals("SUCCESS", order.getDiscountCalcStatus());
        assertEquals(WALLET_TOTAL_AMT, order.getWalletTotalAmt());
        verify(discountLevelMapper).selectApplicable("01", WALLET_TOTAL_AMT);
    }

    /** 超时费的隔离性：只进 {@code OVERTIME_AMOUNT}，既不参与折扣基数也不并入 {@code TRX_AMOUNT}。 */
    @Test
    void overtimeFeeStaysOutOfDiscountBase() {
        stubEntry();
        stubFare();
        stubWalletAccount();
        stubTransferReduction(true);
        stubDiscountLevel("0.8");

        GateTxnPay order = order(EXIT_TIME_OVERTIME);
        calculate(order, walletRequest());

        assertEquals(TIMEOUT_FEE_CENTS, order.getOvertimeAmount(), "超时 MUST 单列超时费");
        assertEquals(240, order.getTrxAmount(), "超时费 NEVER 进折扣基数，也 NEVER 并进 TRX_AMOUNT");
        assertTrue(order.getDiscountCalcMsg().contains("已加收超时费" + TIMEOUT_FEE_CENTS + "分"),
                "超时时 MUST 在计算说明里留痕，对账与客诉都靠它");
    }

    /** 非钱包渠道整段跳过换乘与折扣，金额就是票价原值。 */
    @Test
    void nonWalletOrderSkipsTransferAndDiscount() {
        stubEntry();
        stubFare();

        GateTxnPay order = order(EXIT_TIME_IN_TIME);
        GateTxnPayReqDTO request = walletRequest();
        request.setPaymentVendor("01");
        calculate(order, request);

        assertEquals("01", order.getTransferFlag());
        assertEquals("SKIPPED", order.getDiscountCalcStatus());
        assertEquals(TICKET_PRICE, order.getTrxAmount(), "非钱包渠道 MUST 按票价原值扣款");
        assertNull(order.getExpectedGateAmount());
        verifyNoInteractions(offlineMetroTransferClient, accountClient, walletAppGatewayClient,
                discountLevelMapper);
    }

    /** 同行票不参与钱包累计折扣，但换乘减免仍然生效 —— 两件事共用一个 wallet 分支，容易被一起跳过。 */
    @Test
    void companionOrderKeepsTransferReductionButSkipsDiscount() {
        stubEntry();
        stubFare();
        stubWalletAccount();
        stubTransferReduction(true);

        GateTxnPay order = order(EXIT_TIME_IN_TIME);
        GateTxnPayReqDTO request = walletRequest();
        request.setCompanionFlag("Y");
        calculate(order, request);

        assertEquals("02", order.getTransferFlag(), "同行票的换乘减免 MUST 照常判定");
        assertEquals(300, order.getTrxAmount(), "MUST 是减免后的 400-100=300，不再打折");
        assertEquals("SKIPPED", order.getDiscountCalcStatus());
        verify(walletAppGatewayClient, never()).queryTotalAmt(any());
        verify(discountLevelMapper, never()).selectApplicable(anyString(), any());
    }

    /** 未命中换乘减免时 transferFlag 保持 01，金额不减。 */
    @Test
    void walletOrderWithoutReductionKeepsFullFareAsDiscountBase() {
        stubEntry();
        stubFare();
        stubWalletAccount();
        stubTransferReduction(false);
        stubDiscountLevel("0.8");

        GateTxnPay order = order(EXIT_TIME_IN_TIME);
        calculate(order, walletRequest());

        assertEquals("01", order.getTransferFlag());
        assertEquals(320, order.getTrxAmount(), "未减免时基数是全额票价：400*0.8=320");
    }

    /** 出站早于进站是脏数据。 */
    @Test
    void reversedRideTimeIsRejected() {
        stubEntry();
        stubFare();

        GateTxnPay order = order("20260913095900");
        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> calculate(order, walletRequest()));
        assertTrue(e.getMessage().contains("离线码进出站时间顺序无效"), e.getMessage());
    }

    /** 缺 ticketTransSeq 时无法定位首笔进站。 */
    @Test
    void missingTicketTransSeqIsRejected() {
        GateTxnPay order = order(EXIT_TIME_IN_TIME);
        GateTxnPayReqDTO request = walletRequest();
        request.setTicketTransSeq(null);
        assertThrows(IllegalStateException.class, () -> calculate(order, request));
        verifyNoInteractions(ticketClient);
    }

    /** 订单号规则：{@code GT} + 17 位时间戳 + 卡号后 6 位。 */
    @Test
    void orderNoKeepsPrefixTimestampAndCardSuffix() {
        GateTxnPayReqDTO request = walletRequest();
        request.setCardId("9876543210123456");
        String orderNo = invokeOnService("buildOrderNo", new Class<?>[] {GateTxnPayReqDTO.class}, request);
        assertTrue(orderNo.matches("GT\\d{17}123456"), "实际订单号=" + orderNo);

        request.setCardId("123");
        assertTrue(this.<String>invokeOnService("buildOrderNo", new Class<?>[] {GateTxnPayReqDTO.class}, request)
                        .matches("GT\\d{17}123"),
                "卡号不足 6 位时 MUST 原样拼接，NEVER 抛越界");
    }

    private void stubEntry() {
        QueryLatestEntryTxnResult entry = new QueryLatestEntryTxnResult();
        entry.setRetCode("0000");
        entry.setHandleDateTime(ENTRY_TIME);
        entry.setHandleStationCode(IN_STATION);
        when(ticketClient.queryLatestEntryTxnBeforeExit(any(QueryLatestEntryTxnReqDTO.class))).thenReturn(entry);
    }

    private void stubFare() {
        RequestTicketPriceByStationResult fare = new RequestTicketPriceByStationResult();
        fare.setRetCode("0000");
        fare.setTicketPrice(String.valueOf(TICKET_PRICE));
        when(paraClient.requestTicketPriceByStation(any(RequestTicketPriceByStationReqDTO.class)))
                .thenReturn(fare);
    }

    private void stubWalletAccount() {
        QueryUserInfoResult user = new QueryUserInfoResult();
        user.setRetCode("0000");
        user.setThirdPayId("PAY-U1");
        user.setMsisdn("13800000000");
        user.setCardIssueCode("IC");
        when(accountClient.queryUserInfo(any(QueryUserInfoReqDTO.class))).thenReturn(user);
    }

    private void stubTransferReduction(boolean reduction) {
        when(offlineMetroTransferClient.isReduction(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(reduction);
    }

    private void stubDiscountLevel(String rate) {
        QueryWalletTotalAmtResult total = new QueryWalletTotalAmtResult();
        total.setRetCode("0000");
        total.setTotalAmt(WALLET_TOTAL_AMT);
        when(walletAppGatewayClient.queryTotalAmt(any(QueryWalletTotalAmtReqDTO.class)))
                .thenReturn(total);

        DiscountLevel level = new DiscountLevel();
        level.setLevelAmt(WALLET_TOTAL_AMT);
        level.setLevelDiscount(new BigDecimal(rate));
        when(discountLevelMapper.selectApplicable(anyString(), eq(WALLET_TOTAL_AMT)))
                .thenReturn(level);
    }

    private GateTxnPay order(String outTime) {
        GateTxnPay order = new GateTxnPay();
        order.setOrderNo("GT20260913100000000123456");
        order.setThirdUserId("U1");
        order.setCardId("9876543210123456");
        order.setCardType("04");
        order.setTicketTransSeq("1");
        order.setOutStation(OUT_STATION);
        order.setOutTime(outTime);
        order.setTxnDate("20260913");
        return order;
    }

    private GateTxnPayReqDTO walletRequest() {
        GateTxnPayReqDTO request = new GateTxnPayReqDTO();
        request.setCardId("9876543210123456");
        request.setCardType("04");
        request.setTicketTransSeq("1");
        request.setTrxType("02");
        request.setOfflineFlag("Y");
        request.setPaymentVendor("0B");
        return request;
    }

    private void calculate(GateTxnPay order, GateTxnPayReqDTO request) {
        calculator().calculateOfflineFare(order, request);
    }

    /** 只装配算价用得到的六个协作者。 */
    private FareCalculator calculator() {
        return new FareCalculator(
                new FareDataGateway(ticketClient, paraClient, accountClient, walletAppGatewayClient,
                        discountLevelMapper, offlineMetroTransferClient),
                TIMEOUT_SECONDS, TIMEOUT_FEE_CENTS, TRANSFER_REDUCTION_CENTS);
    }

    /** {@code buildOrderNo} 仍留在 {@code GateTxnPayServiceImpl}（订单号是订单聚合的身份，不属算价），因此这里仍需反射。 */
    @SuppressWarnings("unchecked")
    private <T> T invokeOnService(String name, Class<?>[] signature, Object... args) {
        try {
            Method method = GateTxnPayServiceImpl.class.getDeclaredMethod(name, signature);
            method.setAccessible(true);
            GateTxnPayServiceImpl service = new GateTxnPayServiceImpl(
                    null, null, null, null, null, null, null, null);
            return (T) method.invoke(service, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("反射调用 " + name + " 失败，方法签名可能已变更", e);
        }
    }
}
