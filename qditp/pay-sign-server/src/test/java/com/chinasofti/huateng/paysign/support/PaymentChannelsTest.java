package com.chinasofti.huateng.paysign.support;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** 护栏：钱包渠道判定的归一与边界，classify 与 isWallet MUST 同口径。 */
class PaymentChannelsTest {

    @Test
    void walletCodeIsRecognized() {
        assertTrue(PaymentChannels.isWallet("0B"));
    }

    @Test
    void walletCodeCaseInsensitiveAndTrimmed() {
        assertTrue(PaymentChannels.isWallet("  0B  "));
    }

    @Test
    void nonWalletVendorsReturnFalse() {
        assertFalse(PaymentChannels.isWallet("03"));
        assertFalse(PaymentChannels.isWallet("04"));
        assertFalse(PaymentChannels.isWallet("99"));
    }

    @Test
    void nullAndEmptyReturnFalse() {
        assertFalse(PaymentChannels.isWallet(null));
        assertFalse(PaymentChannels.isWallet(""));
        assertFalse(PaymentChannels.isWallet("   "));
    }

    @Test
    void walletCodeRoundTrips() {
        assertTrue(PaymentChannels.isWallet(PaymentChannels.walletCode()));
    }

    /** classify 的钱包分支：带空白也要认出来，且 code() 是归一后的值。 */
    @Test
    void classifyRecognizesWalletAndNormalizesCode() {
        assertInstanceOf(PaymentChannel.Wallet.class, PaymentChannels.classify("0B"));
        PaymentChannel trimmed = PaymentChannels.classify("  0B  ");
        assertInstanceOf(PaymentChannel.Wallet.class, trimmed);
        assertEquals("0B", trimmed.code());
    }

    /** 传统签约渠道归到 Contracted，code() 原样带出。 */
    @Test
    void classifyMapsContractedVendors() {
        assertInstanceOf(PaymentChannel.Contracted.class, PaymentChannels.classify("03"));
        assertEquals("03", PaymentChannels.classify("03").code());
        assertInstanceOf(PaymentChannel.Contracted.class, PaymentChannels.classify("04"));
        assertInstanceOf(PaymentChannel.Contracted.class, PaymentChannels.classify("0C"));
    }

    /** 未知编码与 null / 空白归到 {@code Contracted}，且 NEVER 抛异常。 */
    @Test
    void classifyTreatsUnknownAndBlankAsContracted() {
        assertInstanceOf(PaymentChannel.Contracted.class, PaymentChannels.classify("99"));
        assertInstanceOf(PaymentChannel.Contracted.class, PaymentChannels.classify(null));
        assertInstanceOf(PaymentChannel.Contracted.class, PaymentChannels.classify("   "));
    }

    /** classify 与 isWallet MUST 永远同口径 —— 两者分叉就等于「同一个问题两个答案」。 */
    @Test
    void classifyAgreesWithIsWallet() {
        for (String vendor : new String[] {"0B", "  0B  ", "03", "04", "0C", "99", "", null}) {
            assertEquals(PaymentChannels.isWallet(vendor),
                    PaymentChannels.classify(vendor) instanceof PaymentChannel.Wallet,
                    "classify 与 isWallet 对 vendor=" + vendor + " 给出了相反答案");
        }
    }
}
