package com.chinasofti.huateng.gatetxnpay.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.entity.MetroTransferPushTask;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/** 钉住「这笔过闸要不要给公交侧推换乘」的五个判定条件与任务字段映射。 */
class MetroTransferPushDecisionTest {

    /** 判定与构建当前所在的类。 */
    private static final Class<?> TARGET = MetroTransferPushTaskProcessor.class;

    /** 五个条件全满足 → 建任务，且六个字段逐一映射到位。 */
    @Test
    void eligibleOrderBuildsTaskWithMappedFields() {
        MetroTransferPushTask task = buildTask(eligible());

        assertNotNull(task, "五条件全满足 MUST 建任务");
        assertEquals("U1", task.getThirdUserId());
        assertEquals("0B", task.getPayChannelType(), "推送用的支付渠道取 PAYMENT_VENDOR");
        assertEquals("02", task.getTransferFlag());
        assertEquals("04", task.getCardType());
    }

    @Test
    void entryTransactionIsNotPushed() {
        GateTxnPay order = eligible();
        order.setTrxType("01");

        assertNull(buildTask(order));
    }

    /** 双段计费出站（{@code trxType=03}）与普通出站（02）同样要推。 */
    @Test
    void bothExitTrxTypesArePushed() {
        GateTxnPay order = eligible();
        order.setTrxType("03");

        assertNotNull(buildTask(order), "03 也是出站，MUST 与 02 同等对待");
    }

    /** `0B` 的大小写与首尾空格都要容忍。 */
    @Test
    void onlyWalletVendorIsPushedAndCaseSpaceTolerant() {
        GateTxnPay alipay = eligible();
        alipay.setPaymentVendor("0A");
        assertNull(buildTask(alipay), "非 0B 渠道 NEVER 推");

        GateTxnPay lowerCase = eligible();
        lowerCase.setPaymentVendor(" 0b ");
        assertNotNull(buildTask(lowerCase), "大小写与空格 MUST 容忍");

        GateTxnPay blank = eligible();
        blank.setPaymentVendor(null);
        assertNull(buildTask(blank), "渠道为空时 NEVER 推");
    }

    /** 蓝牙渠道（{@code channelType=01}）排除。 */
    @Test
    void bluetoothChannelIsExcluded() {
        GateTxnPay order = eligible();
        order.setChannelType("01");

        assertNull(buildTask(order));
    }

    /** 同行票（Y）与第三方票（C）都排除，且大小写不敏感。 */
    @Test
    void companionAndThirdPartyTicketsAreExcluded() {
        GateTxnPay companion = eligible();
        companion.setCompanionFlag("Y");
        assertNull(buildTask(companion), "同行票 NEVER 推");

        GateTxnPay thirdParty = eligible();
        thirdParty.setCompanionFlag("c");
        assertNull(buildTask(thirdParty), "第三方票 NEVER 推，且小写也要认");

        GateTxnPay normal = eligible();
        normal.setCompanionFlag("N");
        assertNotNull(buildTask(normal), "N 是普通票，MUST 推");
    }

    /** {@code OUT_TIME} 是 14 位时，日期取前 8 位、时间取第 9~14 位。 */
    @Test
    void fullOutTimeIsSplitIntoDateAndTime() {
        GateTxnPay order = eligible();
        order.setOutTime("20260101123045");

        MetroTransferPushTask task = buildTask(order);
        assertEquals("20260101", task.getTransDate());
        assertEquals("123045", task.getTransTime());
    }

    /** {@code OUT_TIME} 缺失时日期回落到 {@code TXN_DATE}。 */
    @Test
    void missingOutTimeFallsBackToTxnDate() {
        GateTxnPay order = eligible();
        order.setOutTime(null);

        MetroTransferPushTask task = buildTask(order);
        assertEquals("20260101", task.getTransDate(), "MUST 回落 TXN_DATE");
        assertNull(task.getTransTime(), "拿不到出站时间就留空，NEVER 编一个");
    }

    /** 开关关闭时连任务都不建（2.0.77 起，用户 2026-09-15 要求）。 */
    @Test
    void disabledSwitchBuildsNoTaskAtAll() {
        GateTxnPay order = eligible();

        assertNotNull(buildTask(order, true), "开关开启且五条件满足 MUST 建任务");
        assertNull(buildTask(order, false), "开关关闭时 NEVER 建任务");
    }

    private MetroTransferPushTask buildTask(GateTxnPay order) {
        return buildTask(order, true);
    }

    private MetroTransferPushTask buildTask(GateTxnPay order, boolean enabled) {
        try {
            Method method = TARGET.getDeclaredMethod("buildMetroTransferPushTask", GateTxnPay.class);
            method.setAccessible(true);
            return (MetroTransferPushTask) method.invoke(newTarget(enabled), order);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("反射调用 buildMetroTransferPushTask 失败，方法可能已搬家或改签名", e);
        }
    }

    /** 判定与构建都不读任何协作者，因此协作者全传 null / 零值即可。 */
    private Object newTarget(boolean enabled) {
        return new MetroTransferPushTaskProcessor(null, null, enabled, 0, 0, 0L);
    }

    /** 五个条件全满足的基准订单，各用例只改其中一个字段。 */
    private GateTxnPay eligible() {
        GateTxnPay order = new GateTxnPay();
        order.setOrderNo("GT1");
        order.setTxnDate("20260101");
        order.setThirdUserId("U1");
        order.setCardType("04");
        order.setTrxType("02");
        order.setPaymentVendor("0B");
        order.setChannelType("02");
        order.setCompanionFlag("N");
        order.setTransferFlag("02");
        order.setOutTime("20260101123045");
        return order;
    }
}
