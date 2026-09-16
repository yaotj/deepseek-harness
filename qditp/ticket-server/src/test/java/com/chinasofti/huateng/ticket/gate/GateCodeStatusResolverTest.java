package com.chinasofti.huateng.ticket.gate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code GateCodeStatusResolver} 的**特征测试**（characterization test）：
 * 在把三段 if 链改成表驱动之前，先把当前的输入 → 输出逐条钉死，改错立刻变红。
 *
 * <p>与同包 {@code SelfServiceSupplementCodeStatusTest} 的分工：那个类只钉 IF8A-04
 * 自助补站落 80 / 81 的**业务判据**，本类钉的是**全量映射表 + 三级优先级 + 兜底**。
 * 有重叠是有意的（80 / 81 两格），<b>NEVER</b> 为了去重删掉任何一边。</p>
 *
 * <p>本类记录两处「文档与代码不一致、代码为准」的实测结果：
 * ①类 Javadoc 曾写「本笔只是新增，切换尚未发生」，实际 {@code GateTxnAssembler} 已是唯一调用点、
 * {@code GateTicketHandler} 里的原副本已删除；②类 Javadoc 的映射表曾写 {@code trxType=99 -> FF}，
 * 实际 {@code QRCodeStatusEnum.ABNORMAL} 的码是 <b>70</b>，只有 {@code ENTRY_FAIL} 才是 {@code FF}。
 * 两处已同批改正。</p>
 */
class GateCodeStatusResolverTest {

    private GateCodeStatusResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new GateCodeStatusResolver();
        // defaultCodeStatus 是 @Value 注入的，纯单测里没有 Spring 上下文，MUST 自己塞值。
        // 取 03 是为了与 application.properties:14 的 ticket.default-code-status 一致。
        ReflectionTestUtils.setField(resolver, "defaultCodeStatus", "03");
    }

    /**
     * 全量映射表。空串列用 {@code ''} 表示，@CsvSource 会给出空字符串（而非 null），
     * 二者在 {@code StringUtils.hasText} 下等价，故不再单列 null 行。
     */
    @ParameterizedTest(name = "trxType={0}, excessFareType={1}, adviceOpt={2} -> {3}")
    @CsvSource(nullValues = "NULL", value = {
            // adviceOpt 命中（BOM 单边处理），优先级最高
            "NULL, NULL, 018, 04",
            "NULL, NULL, 020, 04",
            "NULL, NULL, 005, 08",
            "NULL, NULL, 006, 09",
            // excessFareType 命中（IF8A-04 APP 自助补站）
            "NULL, 01,   NULL, 81",
            "NULL, 02,   NULL, 80",
            // trxType 兜底（真实闸机报文只带这一个字段）
            "01,   NULL, NULL, 04",
            "02,   NULL, NULL, 05",
            "03,   NULL, NULL, 06",
            "04,   NULL, NULL, FF",
            "99,   NULL, NULL, 70",
    })
    void 映射表逐格钉死(String trxType, String excessFareType, String adviceOpt, String expected) {
        assertEquals(expected, resolver.resolveCodeStatus(trxType, excessFareType, adviceOpt));
    }

    @ParameterizedTest(name = "trxType={0}, excessFareType={1}, adviceOpt={2} -> {3}")
    @CsvSource(nullValues = "NULL", value = {
            // adviceOpt 压过其余两级：006 付费更新胜出，不落 02 的 05、也不落 excessFareType 的 80
            "02, 02, 006, 09",
            "01, 01, 018, 04",
            // excessFareType 压过 trxType：ExcessFareHandler 把同一个值塞进两个字段，
            // 若顺序颠倒，补站会退化成与真实过闸同形的 04 / 05
            "01, 01, NULL, 81",
            "02, 02, NULL, 80",
    })
    void 优先级_adviceOpt大于excessFareType大于trxType(String trxType, String excessFareType,
                                                     String adviceOpt, String expected) {
        assertEquals(expected, resolver.resolveCodeStatus(trxType, excessFareType, adviceOpt));
    }

    @ParameterizedTest(name = "trxType={0}, excessFareType={1}, adviceOpt={2} -> {3}")
    @CsvSource(nullValues = "NULL", value = {
            // 空串与纯空白等同于「未上送」，逐级回落
            "01, '',   '',   04",
            "01, '  ', '  ', 04",
            // adviceOpt 上送了但不在字典内（000 无需操作）：不算命中，继续回落
            "02, NULL, 000, 05",
            // excessFareType=03/04 按 2026-09-14 裁决暂不映射，回落 trxType 落 06 / FF
            "03, 03,   NULL, 06",
            "04, 04,   NULL, FF",
    })
    void 未命中即逐级回落(String trxType, String excessFareType, String adviceOpt, String expected) {
        assertEquals(expected, resolver.resolveCodeStatus(trxType, excessFareType, adviceOpt));
    }

    @Test
    void 三级全不命中时落配置的兜底值() {
        assertEquals("03", resolver.resolveCodeStatus("88", null, null));
        assertEquals("03", resolver.resolveCodeStatus(null, null, null));
    }

    /**
     * 钉住一处**已知地雷**：兜底分支写的是 {@code fromCode(defaultCodeStatus).getCode()}，
     * 而 {@code fromCode} 对未登记码返回 null，于是配错 {@code ticket.default-code-status}
     * 会在过闸热路径上抛 NPE，而不是退化成某个安全默认值。
     *
     * <p>本测试只**记录**现状，不主张它合理；要改 MUST 先定「配错时该落哪个状态」的口径，
     * <b>NEVER</b> 顺手改成静默兜 03 —— 那会让配置错误永久静默。</p>
     */
    @Test
    void 兜底值配错时热路径抛NPE_属已知地雷(){
        ReflectionTestUtils.setField(resolver, "defaultCodeStatus", "ZZ");
        assertThrows(NullPointerException.class, () -> resolver.resolveCodeStatus("88", null, null));
    }
}
