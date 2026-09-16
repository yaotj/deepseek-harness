package com.chinasofti.huateng.paysign.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.chinasofti.huateng.model.app.ItpCommonRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link AppNotifySigner} 的**加签口径特征测试**（2026-09-16 新增，ADR-D99）。
 *
 * <p><b>为什么必须有</b>：这四个方法从 {@code AppNotifyServiceImpl} 搬出来之前，出向加签是
 * **零测试覆盖**的（全仓 grep `buildItpSign` / `itpSignKey` / `setSign(` 在 test 下零命中）。
 * 那意味着「112 个用例全绿」对这次搬迁**什么都没证明** —— 源串少拼一个字段、排序换个口径、
 * 大小写变一下，测试照样全绿，而对端会开始验签失败。
 *
 * <p><b>期望值是独立算出来的，不是跑本实现取的</b>：按类注释里写明的口径
 * （7 个字段 {@code key=value} 收集 → 字典序排序 → {@code &} 连接 → 末尾 {@code &key=<密钥>}）
 * 在 Java 之外单独算 SHA-1 / MD5 得到。<b>NEVER 用「跑一遍把输出粘进来」的方式更新这两个常量</b> ——
 * 那样测试就退化成「实现等于它自己」，改错了也永远绿。
 *
 * <p>本类刻意只测 {@link AppNotifySigner}（纯函数、零依赖），不起 Spring 上下文。
 *
 * <p><b>本类第一次运行就查出一个既存缺陷，MUST 一并读</b>：独立算出的期望值与实现不一致，
 * 逐个变体反推源串后确认 —— {@code bizData} 那一段用的是 {@code Map} 的**插入顺序**，
 * 而不是字典序，即 {@code JSONWriter.Feature.MapSortField} 在这条调用上**没有生效**。
 * 后果：同一份 bizData 只要构造顺序不同，算出的 {@code sign} 就不同，对端按自己的顺序重建 Map 验签会失败；
 * 而代码里写 {@code MapSortField} 的意图正是要消除这个顺序依赖。
 * <b>本批刻意不修</b>（§5.2 安全红线：改加签 MUST 人工复核 + 与对端重新联调）。
 * 因此下面两个常量钉的是**今天的真实行为**，不是「应该的行为」。
 * 修复那天这两条会变红 —— <b>那时 MUST 把它当成一次有意的契约变更，NEVER 直接把新摘要粘进来了事</b>。
 */
class AppNotifySignerTest {

    private static final String KEY = "TESTKEY";

    /**
     * signType=01 ⇒ SHA-1，期望值由外部独立计算（源串见下）。
     *
     * <p><b>源串里 {@code bizData} 是插入顺序、不是字典序</b>：
     * {@code bizData={"b":"2","a":"1"}&charset=UTF-8&deviceId=ITP-PAY-SIGN&format=json&providerId=06&signType=01&timestamp=20260916101112&key=TESTKEY}
     * —— 也就是说 {@code JSONWriter.Feature.MapSortField} 在这条调用上**没有生效**。详见类注释末尾。</p>
     */
    private static final String EXPECTED_SHA1 = "4abecf5345e6841c3f426529ab14ac84b2f67922";

    /** signType=02 ⇒ MD5，同一源串（signType 段换成 02）。 */
    private static final String EXPECTED_MD5 = "23fb02f1e9dd671c589cd768e61f5b56";

    private ItpCommonRequest<Map<String, String>> request(String signType) {
        ItpCommonRequest<Map<String, String>> request = new ItpCommonRequest<>();
        request.setProviderId("06");
        request.setCharset("UTF-8");
        request.setFormat("json");
        request.setTimestamp("20260916101112");
        request.setDeviceId("ITP-PAY-SIGN");
        request.setSignType(signType);
        // 刻意逆序放入：借此暴露「bizData 的序列化受插入顺序影响」——
        // MapSortField 若真生效，这里换成 a、b 顺序放入应算出同一个 sign。
        Map<String, String> bizData = new LinkedHashMap<>();
        bizData.put("b", "2");
        bizData.put("a", "1");
        request.setBizData(bizData);
        return request;
    }

    @Test
    void signTypeSha1MatchesIndependentlyComputedDigest() {
        assertEquals(EXPECTED_SHA1, AppNotifySigner.buildItpSign(request("01"), KEY));
    }

    @Test
    void signTypeMd5MatchesIndependentlyComputedDigest() {
        assertEquals(EXPECTED_MD5, AppNotifySigner.buildItpSign(request("02"), KEY));
    }

    /** {@code signType=00} 是「免签」，MUST 返回 null 而不是空串或某个摘要。 */
    @Test
    void signTypeZeroZeroMeansNoSignature() {
        assertNull(AppNotifySigner.buildItpSign(request("00"), KEY));
    }

    /** 未约定的 signType MUST 返回 null，NEVER 退化成默认用某个算法。 */
    @Test
    void unknownSignTypeReturnsNull() {
        assertNull(AppNotifySigner.buildItpSign(request("99"), KEY));
    }

    /** 空 signType 同样不签；这条守的是「没配 signType 时不要凭空造一个 sign」。 */
    @Test
    void blankSignTypeMeansNoSignature() {
        assertNull(AppNotifySigner.buildItpSign(request(null), KEY));
    }

    /**
     * 密钥为空时**仍然出签**（源串末尾是 {@code &key=}），这是搬迁前的原样行为。
     *
     * <p>本条不是在赞成它，而是把它钉住：{@code itp.signKey} 默认值就是空串，
     * 若哪天要改成「没密钥就不签 / 直接报错」，MUST 是一次**有意的契约变更**并同步对端，
     * <b>NEVER 顺手改掉让本用例变红再改期望值</b>。</p>
     */
    @Test
    void blankKeyStillProducesSignature() {
        String sign = AppNotifySigner.buildItpSign(request("01"), "");
        assertEquals(40, sign.length());
    }
}
