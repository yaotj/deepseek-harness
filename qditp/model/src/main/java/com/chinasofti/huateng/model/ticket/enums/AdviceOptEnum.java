package com.chinasofti.huateng.model.ticket.enums;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * IF5A 建议操作（{@code adviceOpt}）字典。
 *
 * <p>这套取值此前**在三处各写一份**，且互不知晓（2026-09-14 收口）：
 * <ul>
 *   <li>{@code ticket-server/gate/GateTicketHandler} 的三个 {@code ADVICE_OPT_*} 常量</li>
 *   <li>{@code ticket-server/supplement/CardDataHandler} 的约 20 处裸字面量</li>
 *   <li>{@code fep-dev-server/GateTransactionHandler} 的 {@code Set.of("005","006")}</li>
 * </ul>
 * 第三处是**防资金损失的白名单**（BOM 已补出站的交易不再重复扣费），它与前两处对不上即漏扣或重扣，
 * 因此字典 MUST 只有这一份、放在 {@code model} 里由两个模块共用。
 *
 * <p><b>码值 NEVER 改动</b>：000 / 005 / 006 / 018 / 020 是与 BOM、闸机的对外契约，
 * 改一个字符就是协议不兼容。新增取值 MUST 同时确认 {@link #isSupplementExit()} 的归属
 * ——漏归类会让 {@code GateFarePaymentOrchestrator} 的 {@code shouldPay} 走「未知即扣费」分支。
 *
 * <p>与 {@link QRCodeStatusEnum} 的关系：{@code adviceOpt} 是**动作**，{@code codeStatus} 是**状态**，
 * 两者一一对应关系见 {@code gate.GateCodeStatusResolver} 的 {@code ADVICE_OPT_TABLE}
 * （018→ENTRY / 020→ENTRY / 005→UPDATE_FREE / 006→UPDATE_PAY）。NEVER 把两个枚举合并。
 *
 * <p><b>方向分两组，判据是 {@link #isSupplementEntry()} 与 {@link #isSupplementExit()}，两者互斥且不重叠</b>：
 * 补进站组 {@code 018 / 020} 落 {@code TRX_TYPE=01}，补出站组 {@code 005 / 006} 落 {@code TRX_TYPE=02}。
 * <b>020 于 2026-09-15 从补出站组移到补进站组</b>（用户裁决，见 {@link #FREE_UPDATE_020}）；
 * 这不是重命名，是**方向反转 + 退出资金白名单**，NEVER 回退。
 *
 * <p><b>厂家字典与本枚举的 Java 常量名互相错位，读码值、NEVER 读名字。</b>
 * BOM 侧原厂枚举 {@code com.bestone.itp.bom.enumtype.CardAdviceOpt} 是
 * {@code NO_UPDATE(000) / FREE_IN_20(005) / ADD_OUT(006) / ADD_IN(018) / FREE_UPDATE(020)} ——
 * 厂家的 {@code FREE_UPDATE} 是 <b>020</b>，而本枚举的 {@link #FREE_UPDATE} 是 <b>005</b>。
 * 因此 020 在这里叫 {@link #FREE_UPDATE_020}：<b>NEVER 把 005 改名成 FREE_IN_20 再让
 * FREE_UPDATE 指向 020</b> —— 现有 5 处 {@code AdviceOptEnum.FREE_UPDATE} 调用点会照旧编译通过，
 * 但含义从 005 静默变成 020，编译器与单测都拦不住。
 */
public enum AdviceOptEnum {

    /** 000 - 无需操作 */
    NONE("000", "无需操作"),

    /** 005 - 20 分钟内免费更新（补出站，不计票价） */
    FREE_UPDATE("005", "免费更新"),

    /** 006 - 付费更新（补出站，按票价扣费） */
    PAID_UPDATE("006", "付费更新"),

    /** 018 - 补进站 */
    SUPPLEMENT_ENTRY("018", "补进站"),

    /**
     * 020 - 免费更新（厂家字典 {@code CardAdviceOpt.FREE_UPDATE}）。
     *
     * <p><b>方向是补进站，不是补出站</b>（用户 2026-09-15 裁决，原话「020 按照最新描述应该对应补进站方向，
     * 是用户刷卡之后没有进站成功」）。业务场景：乘客在**非付费区**刷了卡、但进站没成功，
     * BOM 给他一次**免费进闸更新**，乘客随后可从侧门进入付费区。因此：
     * <ul>
     *   <li>{@code TRX_TYPE=01}（见 {@link #isSupplementEntry()}），金额 0；</li>
     *   <li>落 {@code CODE_STATUS=04 ENTRY}，<b>进站站取 {@code updateStationCode}、进站时间取 {@code optDate}</b>
     *       —— 这两个值决定乘客真实出站时的票价起算点，写错即算错钱；</li>
     *   <li><b>NEVER 放进 {@link #isSupplementExit()} / {@link #SUPPLEMENT_EXIT_CODES}</b>：那是「BOM 已收过出站费、
     *       闸机跳过扣费」的资金白名单。020 之后乘客还要正常出站扣费，混进去等于整程免费（资损）。</li>
     * </ul>
     *
     * <p><b>2026-09-14 到 2026-09-15 之间它曾被实现成「更宽松版的 005」（补出站 + 落 08 + 进资金白名单），
     * 那版口径已作废、NEVER 回退</b>。作废理由不只是用户改口：`005`/`006` 的前提是「卡已进站未出站」，
     * 而 020 要放行**从未进站的新卡 {@code 03}**（用户 2026-09-14 第三次裁决），
     * 「给没进过站的卡补出站」本身自相矛盾 —— 补进站口径才自洽。
     *
     * <p>与 {@code 018}（同为补进站）的**唯一区别在付费区标**，见 {@code SupplementStateRules.UPDATE_RULES}：
     * {@code 018} 要求 {@code updateType=01}（人已在付费区、卡里没进站记录、出不去），
     * {@code 020} 要求 {@code updateType=00}（人还在非付费区、刷卡没进成）。
     * 状态白名单上 020 最宽：**任何已登记的 {@link QRCodeStatusEnum} 一律放行**，也不复核时间窗；
     * 唯一还拒的是「库内状态是脏值、解析不出枚举」。<b>NEVER 把这条宽判据复用给 005 / 006 / 018。</b>
     *
     * <p>登记它的直接原因：BOM 实际上送的就是 020（2026-09-14 19:58 实测），而枚举里没有，
     * IF5A-03 一路返 8001「票卡状态不允许此操作」、零数据落库。
     */
    FREE_UPDATE_020("020", "免费更新");

    private final String code;
    private final String desc;

    AdviceOptEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 宽松解析：无法识别时返回 null 而不抛异常。
     *
     * <p>上游（BOM / 闸机）可能上送未登记的取值，处置方式 MUST 由调用方决定，NEVER 在这里抛。
     */
    public static AdviceOptEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AdviceOptEnum opt : values()) {
            if (opt.code.equals(code)) {
                return opt;
            }
        }
        return null;
    }

    /** 码值是否等于本枚举，供仍以 String 比较的调用点使用。 */
    public boolean matches(String code) {
        return this.code.equals(code);
    }

    /** 单元素列表，IF5A-01 的 {@code adviceOpt} 响应字段是 List。 */
    public List<String> asSingletonList() {
        return Collections.singletonList(code);
    }

    /**
     * 是否属于「补进站」方向（{@code TRX_TYPE=01}、落 {@code CODE_STATUS=04}）。
     *
     * <p>{@code 018} 是付费区补进站（人在里面、卡里没进站记录、出不去），
     * {@code 020} 是非付费区免费进闸更新（刷卡没进成，见 {@link #FREE_UPDATE_020}）。
     * 两者方向相同、区别只在 {@code updateType}，因此**方向判据合并在这里**，
     * 而**区域判据留在 {@code SupplementStateRules.UPDATE_RULES}**，NEVER 混到一处。
     */
    public boolean isSupplementEntry() {
        return this == SUPPLEMENT_ENTRY || this == FREE_UPDATE_020;
    }

    /**
     * 是否属于「BOM 已完成补出站」。
     *
     * <p><b>这是资金安全判据</b>：命中即表示该乘客的出站费用已在 BOM 侧结清，
     * 出站扣费 MUST 跳过；不命中（含解析不出的未知取值）MUST 照常扣费。
     * 消费方是 {@code ticket-server} 的 {@code GateFarePaymentOrchestrator.shouldPay}。
     *
     * <p><b>只有 005 / 006 —— 020 已于 2026-09-15 移出</b>：020 改成补进站后，
     * 乘客之后还要真实出站并正常扣费，留在这里会让整程免费。
     * <b>NEVER 因为「020 也叫免费更新」就把它加回来</b>：「免费」指的是这次进闸更新不收钱，
     * 不是这一程不收钱。
     */
    public boolean isSupplementExit() {
        return this == FREE_UPDATE || this == PAID_UPDATE;
    }

    /**
     * 「BOM 已完成补出站」的码值集合，供只有字符串在手的调用点直接判定。
     *
     * <p>语义与 {@link #isSupplementExit()} 完全一致，两者 MUST 同步改动。
     */
    public static final Set<String> SUPPLEMENT_EXIT_CODES =
            Set.of(FREE_UPDATE.code, PAID_UPDATE.code);
}
