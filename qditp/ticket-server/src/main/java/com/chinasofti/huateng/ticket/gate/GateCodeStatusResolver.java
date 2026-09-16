package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.enums.TrxTypeCodeEnum;
import com.chinasofti.huateng.model.ticket.enums.AdviceOptEnum;
import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * IF1A-01 目标状态码解析器 —— 从 {@code trxType} / {@code excessFareType} / {@code adviceOpt}
 * 三个入向字段推导 {@code QRCODE_STATUS.CODE_STATUS} 的下一个取值。
 *
 * <p>2026-09-14 从 {@code GateTicketHandler}（887 行）拆出。拆分判据是**这里是纯函数**：
 * 三个字符串进、一个状态码出，不查库、不调远端、不改入参。原类里它与「查库 / 调 4 个远端 /
 * 组装应答」混在一起，导致 {@code SelfServiceSupplementCodeStatusTest} 只能 {@code new} 出整个
 * 887 行的类来测这一个方法。</p>
 *
 * <p><b>切换已完成</b>：{@code GateTicketHandler} 里那份同形的 {@code resolveCodeStatus}
 * 及其三个私有分支方法已删除，本类是全模块唯一一份，唯一调用点是 {@code GateTxnAssembler}
 * （2026-09-14 全模块 grep 实测：{@code resolveCodeStatus} 只剩本类、{@code GateTxnAssembler}
 * 与 {@code supplement/SupplementStateRules} 那个同名但不同签名的方法）。
 * 本段此前写「本笔只是新增，切换尚未发生」，<b>已过期，NEVER 回退</b>。</p>
 *
 * <p><b>优先级 {@code adviceOpt} &gt; {@code excessFareType} &gt; {@code trxType} 是本类的核心不变量。</b>
 * 前两者互斥（BOM 单边处理只带 adviceOpt，APP 自助补站只带 excessFareType，真实闸机报文两个都不带），
 * 而 {@code trxType} 三种来源都会带，只能作兜底。三段 if 链已于 2026-09-14 改为表驱动，
 * 优先级现在由 {@link #RESOLVE_LEVELS} 的**列表顺序**单点承载，**NEVER 调整该列表的顺序**。</p>
 */
@Component
class GateCodeStatusResolver {

    private static final Logger log = LoggerFactory.getLogger(GateCodeStatusResolver.class);

    /**
     * IF5A-03 建议操作 → 目标状态。**字典的权威在 {@code model} 的 {@link AdviceOptEnum}**，
     * 本处只是查表用的短别名，NEVER 在这里写裸字面量、也 NEVER 在别处复制一份
     * （见 {@code docs/business/ride-code.md}「IF5A 票卡分析与更新」）。
     *
     * <p>真实闸机报文恒不带 {@code adviceOpt}（APP 自助补站用 {@code excessFareType}），
     * 因此这四格只在 BOM 链路命中。<b>{@code 018} 与 {@code 020} 同落 {@code 04 ENTRY}</b>
     * —— 两者都是补进站，区别只在付费区标（018 要 {@code updateType=01}、020 要 {@code 00}），
     * 而付费区判据在 {@code supplement.SupplementStateRules}、不在本表。
     * <b>020 于 2026-09-15 从 {@code UPDATE_FREE(08)} 改到 {@code ENTRY(04)}</b>（用户裁决：
     * 020 是「刷卡没进成」的免费进闸更新），**NEVER 回退成 08** —— 落 08 等于把行程收口，
     * 乘客随后从侧门进付费区再出站时后台没有开环进站记录，票价无从起算。</p>
     */
    private static final Map<String, QRCodeStatusEnum> ADVICE_OPT_TABLE = Map.of(
            AdviceOptEnum.SUPPLEMENT_ENTRY.getCode(), QRCodeStatusEnum.ENTRY,
            AdviceOptEnum.FREE_UPDATE_020.getCode(), QRCodeStatusEnum.ENTRY,
            AdviceOptEnum.FREE_UPDATE.getCode(), QRCodeStatusEnum.UPDATE_FREE,
            AdviceOptEnum.PAID_UPDATE.getCode(), QRCodeStatusEnum.UPDATE_PAY);

    /**
     * IF8A-04 APP 自助补站类型 → 目标状态。取值与 {@code trxType} 同源 ——
     * {@code ExcessFareHandler} 把 {@code upgradeAreaType} 同时塞进 {@code trxType} 与
     * {@code excessFareType} 两个字段，因此本表 <b>MUST 排在 {@link #TRX_TYPE_TABLE} 之前</b>，
     * 否则补站会退化成与真实过闸完全同形的 04 / 05。
     *
     * <p>{@code 81} / {@code 80} 是<b>自助补站专属</b>状态，{@code QRCodeStatusEnum.isOpenLoop()}
     * （含 81）与 {@code isClosedLoop()}（含 80）以及 IF5A 的 {@code SupplementStateRules} 判定
     * 都按它们区分「补站落的站」与「真实过闸落的站」，<b>NEVER 让补站退回 04 / 05</b>：
     * 退回后明细（{@code RESERVE1} 恒 null）与状态两处都无法区分补站与真实过闸。</p>
     *
     * <p>{@code 03}（超时出站）与 {@code 04}（进站失败）<b>按用户 2026-09-14 裁决暂不映射</b>，
     * 故本表<b>只有两格</b>，未命中即回落 {@link #TRX_TYPE_TABLE} 的既有分支（06 / FF）。
     * 要改先定口径，<b>NEVER 顺手补成 80</b>。</p>
     */
    private static final Map<String, QRCodeStatusEnum> EXCESS_FARE_TYPE_TABLE = Map.of(
            TrxTypeCodeEnum.ENTRY.getCode(), QRCodeStatusEnum.SELF_SERVICE_ENTRY,
            TrxTypeCodeEnum.EXIT.getCode(), QRCodeStatusEnum.SELF_SERVICE_EXIT);

    /** 闸机交易类型 → 目标状态。三种来源都会带 {@code trxType}，只能作兜底。 */
    private static final Map<String, QRCodeStatusEnum> TRX_TYPE_TABLE = Map.of(
            TrxTypeCodeEnum.ENTRY.getCode(), QRCodeStatusEnum.ENTRY,
            TrxTypeCodeEnum.EXIT.getCode(), QRCodeStatusEnum.EXIT,
            TrxTypeCodeEnum.EXIT_OVERTIME.getCode(), QRCodeStatusEnum.EXIT_OVERTIME,
            TrxTypeCodeEnum.ENTRY_FAIL.getCode(), QRCodeStatusEnum.ENTRY_FAIL,
            TrxTypeCodeEnum.ABNORMAL.getCode(), QRCodeStatusEnum.ABNORMAL);

    /**
     * 一级解析：从入向报文取一个字段、查一张表。
     *
     * @param fieldName        日志用的字段名
     * @param reader           字段读取器
     * @param table            该字段的映射表
     * @param logUnmappedValue 字段有值但表里没有时是否记一条 INFO。只有 {@code excessFareType}
     *                         为 true —— 它的未映射取值（03 / 04）是**有意留白**、需要留痕；
     *                         {@code adviceOpt} 与 {@code trxType} 的未知值由末尾那条 WARN 兜。
     */
    private record ResolveLevel(String fieldName,
                                Function<GateTxnCodeStatusInput, String> reader,
                                Map<String, QRCodeStatusEnum> table,
                                boolean logUnmappedValue) {
    }

    /** 入向三字段的载体，只为让 {@link ResolveLevel#reader} 能按名取值，不参与业务。 */
    private record GateTxnCodeStatusInput(String trxType, String excessFareType, String adviceOpt) {
    }

    /**
     * <b>列表顺序即优先级，NEVER 调整</b>：{@code adviceOpt} &gt; {@code excessFareType}
     * &gt; {@code trxType}。改成表驱动后，优先级由本列表的顺序单点承载，
     * 不再散落在三个 {@code if} 的书写次序里。
     */
    private static final List<ResolveLevel> RESOLVE_LEVELS = List.of(
            new ResolveLevel("adviceOpt", GateTxnCodeStatusInput::adviceOpt, ADVICE_OPT_TABLE, false),
            new ResolveLevel("excessFareType", GateTxnCodeStatusInput::excessFareType, EXCESS_FARE_TYPE_TABLE, true),
            new ResolveLevel("trxType", GateTxnCodeStatusInput::trxType, TRX_TYPE_TABLE, false));

    /**
     * 状态码解析兜底值。**默认值 MUST 与 {@code application.properties:14} 的
     * {@code ticket.default-code-status=03} 保持一致**：此前注解写 {@code 01}（无交易）、
     * properties 写 {@code 03}（初始化），properties 缺失时兜底语义会从「复位为初始化」
     * 悄悄变成「复位为无交易」。两处不一致时 NEVER 只改一边；
     * {@code ridestatus/TicketRideStatusServiceImpl} 有同名配置项、同款陷阱。
     */
    @Value("${ticket.default-code-status:03}")
    private String defaultCodeStatus;

    /**
     * 根据交易类型解析下一状态码。
     *
     * <pre>
     * adviceOpt=018        -> 04 补进站（付费区）
     * adviceOpt=020        -> 04 免费进闸更新（非付费区，刷卡未进站成功；2026-09-15 起，此前落 08）
     * adviceOpt=005        -> 08 20 分钟内免费更新（补出站）
     * adviceOpt=006        -> 09 20 分钟内付费更新（补出站）
     * excessFareType=01    -> 81 APP 自助补进站
     * excessFareType=02    -> 80 APP 自助补出站
     * trxType=01           -> 04 进站
     * trxType=02           -> 05 正常出站
     * trxType=03           -> 06 超时出站
     * trxType=04           -> FF 进站失败
     * trxType=99           -> 70 异常
     * 其余                 -> defaultCodeStatus
     * </pre>
     *
     * <p>末两行 <b>NEVER 写成都落 FF</b>：{@code QRCodeStatusEnum.ABNORMAL} 的码是 {@code 70}，
     * 只有 {@code ENTRY_FAIL} 才是 {@code FF}。本表此前把 {@code trxType=99} 记成 {@code FF}，
     * 与代码不符，已按 {@code GateCodeStatusResolverTest} 的实测结果改正。</p>
     */
    public String resolveCodeStatus(String trxType, String excessFareType, String adviceOpt) {
        log.debug("IF1A-01 解析状态码, trxType={}, excessFareType={}, adviceOpt={}",
                trxType, excessFareType, adviceOpt);

        GateTxnCodeStatusInput input = new GateTxnCodeStatusInput(trxType, excessFareType, adviceOpt);
        for (ResolveLevel level : RESOLVE_LEVELS) {
            String value = level.reader().apply(input);
            if (!StringUtils.hasText(value)) {
                continue;
            }
            QRCodeStatusEnum status = level.table().get(value);
            if (status != null) {
                // 原实现每格手写一个中文原因（如「补进站」），改表驱动后取 enum 的 desc，
                // 措辞会略有不同（018 现在打「已进站」）。只影响 DEBUG 日志文案，不影响返回值。
                log.debug("IF1A-01 状态码解析结果={}({}), {}={}",
                        status.getCode(), status.getDesc(), level.fieldName(), value);
                return status.getCode();
            }
            if (level.logUnmappedValue()) {
                log.info("IF1A-01 自助补站类型未映射专属状态，回落 trxType 分支, {}={}", level.fieldName(), value);
            }
        }

        String fallback = QRCodeStatusEnum.fromCode(defaultCodeStatus).getCode();
        log.warn("IF1A-01 状态码解析未匹配任何规则, trxType={}, 使用默认值={}", trxType, fallback);
        return fallback;
    }
}
