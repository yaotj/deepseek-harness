package com.chinasofti.huateng.gatetxnpay.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.entity.MetroTransferPushTask;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class MetroTransferPushDecisionTest {

    private static final Class<?> TARGET = MetroTransferPushTaskProcessor.class;

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

    @Test
    void bothExitTrxTypesArePushed() {
        GateTxnPay order = eligible();
        order.setTrxType("03");

        assertNotNull(buildTask(order), "03 也是出站，MUST 与 02 同等对待");
    }

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

    @Test
    void bluetoothChannelIsExcluded() {
        GateTxnPay order = eligible();
        order.setChannelType("01");

        assertNull(buildTask(order));
    }

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

    @Test
    void fullOutTimeIsSplitIntoDateAndTime() {
        GateTxnPay order = eligible();
        order.setOutTime("20260101123045");

        MetroTransferPushTask task = buildTask(order);
        assertEquals("20260101", task.getTransDate());
        assertEquals("123045", task.getTransTime());
    }

    @Test
    void missingOutTimeFallsBackToTxnDate() {
        GateTxnPay order = eligible();
        order.setOutTime(null);

        MetroTransferPushTask task = buildTask(order);
        assertEquals("20260101", task.getTransDate(), "MUST 回落 TXN_DATE");
        assertNull(task.getTransTime(), "拿不到出站时间就留空，NEVER 编一个");
    }

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

    private Object newTarget(boolean enabled) {
        return new MetroTransferPushTaskProcessor(null, null, enabled, 0, 0, 0L);
    }

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
