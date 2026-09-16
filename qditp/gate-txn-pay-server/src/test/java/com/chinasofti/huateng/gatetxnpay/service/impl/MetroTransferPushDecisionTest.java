package com.chinasofti.huateng.gatetxnpay.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.entity.MetroTransferPushTask;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * 钉住「这笔过闸要不要给公交侧推换乘」的五个判定条件与任务字段映射。
 *
 * <p>建这张网的直接原因：这段逻辑同时被出站首次落单（{@code requestPay}）和离线码金额补偿
 * （{@code recoverSingleOfflineFareOrder}）调用，而**此前一条测试都没有**。它要从
 * {@code GateTxnPayServiceImpl} 搬到 {@link MetroTransferPushTaskProcessor}
 * （该类已持有 {@code MetroTransferPushTask} 的后半生命周期：扫表、推送、落结果），
 * 搬家时若把任一条件写反，**两条链路会同时错**、且错的方向是「少推」或「多推」——
 * 少推乘客拿不到公交换乘优惠、多推给了不该给的（蓝牙 / 同行 / 第三方票），两边都不报错。</p>
 *
 * <p>五个条件缺一不可：出站交易（{@code trxType} 02/03）、钱包渠道（{@code PAYMENT_VENDOR=0B}）、
 * 非蓝牙（{@code CHANNEL_TYPE != 01}）、非同行票（{@code COMPANION_FLAG != Y}）、
 * 非第三方票（{@code != C}）。后三个是**排除**语义，写成 {@code equals} 正好反掉。</p>
 *
 * <p>2.0.77 起在这五个之外还有一道**功能开关** {@code wallet.metro-transfer-enabled}：
 * 关闭时连任务都不建（见 {@link #disabledSwitchBuildsNoTaskAtAll}）。它与上面五个条件性质不同 ——
 * 五个条件是「这笔行程该不该推」，开关是「本功能是否启用」，**NEVER 把两者混成一个判断**。</p>
 *
 * <p>搬家后**只改 {@link #TARGET} 与实例构造这两处**，下面的断言值一行不动 ——
 * 断言不变才是行为没变的证据。</p>
 */
class MetroTransferPushDecisionTest {

    /** 判定与构建当前所在的类。搬家后只改这一行。 */
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

    /** 进站交易（{@code trxType=01}）**NEVER** 推换乘——换乘只在出站结算时才成立。 */
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

    /**
     * 非钱包渠道 **NEVER** 推；`0B` 的大小写与首尾空格都要容忍。
     *
     * <p>渠道值来自闸机上送、经多层转发，实测存在大小写与空格不一致，
     * 因此判定是 {@code equalsIgnoreCase(trimToNull(...))}，**NEVER 退回 `equals`**。</p>
     */
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

    /**
     * {@code OUT_TIME} 缺失时日期回落到 {@code TXN_DATE}。
     *
     * <p>**NEVER** 改成取当日：{@code TXN_DATE} 才是这笔行程的交易日，
     * 与支付域按 {@code (ORDER_NO, TXN_DATE)} 关联的口径一致。</p>
     */
    @Test
    void missingOutTimeFallsBackToTxnDate() {
        GateTxnPay order = eligible();
        order.setOutTime(null);

        MetroTransferPushTask task = buildTask(order);
        assertEquals("20260101", task.getTransDate(), "MUST 回落 TXN_DATE");
        assertNull(task.getTransTime(), "拿不到出站时间就留空，NEVER 编一个");
    }

    /**
     * 开关关闭时**连任务都不建**（2.0.77 起，用户 2026-09-15 要求）。
     *
     * <p>此前 {@code wallet.metro-transfer-enabled} 只拦投递不拦生成，关闭期间行程逐条堆成
     * {@code PENDING}；开关一开，几天前的陈旧行程会一次性涌向公交卡系统，而换乘优惠有时效，
     * 补推过期行程比不推更糟。**NEVER 退回「只在 processReadyTasks 判开关」**。</p>
     *
     * <p>这条与上面全部 {@code assertNotNull} 用例互为对照：同一笔订单，只翻开关就应从「建」变「不建」。</p>
     */
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

    /**
     * 判定与构建都不读任何协作者，因此协作者全传 null / 零值即可。搬家后只改这个方法。
     *
     * <p>{@code enabled} 是**唯一必须传真值的参数**：2.0.77 起它参与「建不建任务」的判定，
     * 传 false 会让上面所有 {@code assertNotNull} 用例全部失败，而失败原因看起来像「判定条件写反了」
     * ——**排查本类用例集体转红 MUST 先看这里传的是什么**。</p>
     */
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
