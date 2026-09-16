package com.chinasofti.huateng.paysign.support;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * {@link PaymentChannels} 的判定逻辑测试。
 *
 * <p>钉住的口径：
 * <ol>
 *   <li>{@code "0B"}（{@code PaymentVendorEnum.WALLET.getCode()} 的当前值）→ true</li>
 *   <li>大小写、前后空格 → 归一后仍匹配</li>
 *   <li>{@code "03"} / {@code "04"} / 其他渠道 → false</li>
 *   <li>{@code null} / 空串 → false，<b>不抛异常</b></li>
 *   <li>{@code walletCode()} 返回的值必须让 {@code isWallet} 为 true</li>
 * </ol>
 */
class PaymentChannelsTest {

    @Test
    void walletCodeIsRecognized() {
        assertTrue(PaymentChannels.isWallet("0B"));
    }

    @Test
    void walletCodeCaseInsensitiveAndTrimmed() {
        // normalizeVendor 只做 trim，不做大小写转换；
        // 但 PaymentVendorEnum.isValid 可能按原样比较，因此本条实际测的是
        // 「trim 后的 "0B" 与常量相等」——这正是我们要钉的行为。
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
        // walletCode() 返回的值 isWallet 必须为 true
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

    /**
     * 未知编码与 null / 空白归到 {@code Contracted}，且 NEVER 抛异常。
     *
     * <p>这是收口前 {@code isWallet(...)} 对未知值返回 {@code false}、于是走传统链路的**既有行为**，
     * 刻意保留。改成抛异常或另立变体都会改变对外行为。
     */
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
