package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.ticket.enums.AdviceOptEnum;
import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 补站状态机规则：IF5A-01 建议操作（{@link #resolveAdviceOpt}）与 IF5A-03 执行白名单
 * （{@link #isUpdateAllowed}）。
 *
 * <p>两者<b>刻意放在同一个类里</b>：它们是同一张规则表的「建议侧」与「执行侧」，
 * 任何一侧改了另一侧必须看齐（`docs/business/ride-code.md` 里那两张表就是本类的投影）。
 * 拆到两个处理器里过去已经造成过不对称 —— 审查项 X001 就是执行侧漏判 {@code updateType}。</p>
 *
 * <p>规则表与规格的关系：甲方规格<b>没有</b>对应条文，这两张表是实现方自定义的
 * （见 `docs/testing/bom-oneside/02-阻塞项与缺陷候选.md`）。改动前 MUST 先读那份文档。</p>
 */
@Component
class SupplementStateRules {

    private static final Logger log = LoggerFactory.getLogger(SupplementStateRules.class);
    private static final DateTimeFormatter BIZ_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int FREE_UPDATE_WINDOW_MINUTES = 20;

    @Value("${ticket.default-code-status:03}")
    private String defaultCodeStatus;

    @Value("${ticket.default-last-txn-station:FFFF}")
    private String defaultLastTxnStation;

    /** {@link #defaultCodeStatus} 的枚举形态，启动时解析一次。 */
    private QRCodeStatusEnum defaultCodeStatusEnum;

    /**
     * 审查项 C001：<b>启动即校验 {@code ticket.default-code-status}，解析不出枚举直接启动失败。</b>
     *
     * <p>原实现在两处业务分支里写 {@code codeStatus = QRCodeStatusEnum.fromCode(defaultCodeStatus)}
     * 却不判 null，而 {@code fromCode} 是宽松解析、未登记取值返回 null。默认值 {@code 03} 恰好在枚举里，
     * 于是**只要 K8s Deployment 把这个 env 覆盖成枚举外的值，每一笔 IF5A-01 / IF5A-03 都 NPE**，
     * 编译、单测、启动全都发现不了。让它在启动期炸，比让它在第一笔交易上炸好。
     * <b>NEVER 改成「解析不出就默默兜底 03」</b> —— 那等于把配置错误藏起来。</p>
     */
    @PostConstruct
    void validateConfiguredDefaults() {
        this.defaultCodeStatusEnum = QRCodeStatusEnum.fromCode(defaultCodeStatus);
        if (defaultCodeStatusEnum == null) {
            throw new IllegalStateException("ticket.default-code-status 不是已登记的 QRCodeStatusEnum 取值: "
                    + defaultCodeStatus);
        }
        log.info("补站状态机默认值已校验, defaultCodeStatus={}, defaultLastTxnStation={}",
                defaultCodeStatus, defaultLastTxnStation);
    }

    String unknownStationCode() {
        return defaultLastTxnStation;
    }

    boolean isUnknownStation(String stationCode) {
        return SupplementCodec.isUnknownStation(stationCode, defaultLastTxnStation);
    }

    /**
     * 把库内 {@code CODE_STATUS} 解析成枚举。
     *
     * <p>审查项 M003：<b>「列为空」与「列有值但不在枚举里」MUST 区分处置</b>。</p>
     * <ul>
     *   <li>为空 —— 刚开卡、还没过闸的正常新卡，按配置的默认态（{@code 03}）处理；</li>
     *   <li>有值但未登记 —— 脏数据或别处写入了新状态码，返回 {@code null}，
     *       <b>由调用方拒绝本次请求</b>。原实现把它一并提升成 {@code 03} 新卡，
     *       而 {@code 018 补进站} 对 {@code 03} 是放行的，等于「状态越脏越容易补进站」，
     *       与 AGENTS.md §5.2「状态机校验用白名单不用黑名单」直接冲突。</li>
     * </ul>
     */
    QRCodeStatusEnum resolveCodeStatus(String rawCodeStatus) {
        if (!StringUtils.hasText(rawCodeStatus)) {
            return defaultCodeStatusEnum;
        }
        return QRCodeStatusEnum.fromCode(rawCodeStatus);
    }

    /**
     * IF5A-01 解析建议操作列表。
     *
     * @param codeStatus 已由 {@link #resolveCodeStatus} 解析且非 null
     */
    List<String> resolveAdviceOpt(QRCodeStatusEnum codeStatus, String gateInStation, String lastTxnStation,
                                  String updateType, String gateInTime, String cardId) {
        if (codeStatus == null) {
            return AdviceOptEnum.NONE.asSingletonList();
        }
        AdviceContext ctx = new AdviceContext(codeStatus, gateInStation, lastTxnStation,
                updateType, gateInTime, cardId);
        boolean inPaidArea = SupplementCodec.UPDATE_TYPE_PAID_AREA.equals(updateType);
        for (AdviceRule rule : ADVICE_RULES) {
            if (rule.matches().test(codeStatus)) {
                return (inPaidArea ? rule.paidArea() : rule.freeArea()).resolve(this, ctx);
            }
        }
        // 未登记的状态（01 无交易 / 70 异常 / FF 进站失败）落到这里，一律不建议操作。
        return AdviceOptEnum.NONE.asSingletonList();
    }

    /** 建议侧一次判定的全部入参，只为让分支能按名取值，不参与业务。 */
    private record AdviceContext(QRCodeStatusEnum codeStatus, String gateInStation, String lastTxnStation,
                                 String updateType, String gateInTime, String cardId) {
    }

    /** 建议侧一格的结果计算。取 {@code rules} 是因为要用到实例上的配置（站码兜底、时间窗）。 */
    private interface AdviceBranch {
        List<String> resolve(SupplementStateRules rules, AdviceContext ctx);
    }

    /**
     * 建议侧一行规则：状态匹配器 + 付费区结果 + 非付费区结果。
     *
     * <p><b>付费区与非付费区必须各写一格</b>：这两侧的语义完全不同（付费区是「人在闸内、
     * 需要补进站」，非付费区是「人在闸外、需要补出站」），此前散在 if 链里时曾出现过只改一侧。</p>
     */
    private record AdviceRule(String name, Predicate<QRCodeStatusEnum> matches,
                             AdviceBranch paidArea, AdviceBranch freeArea) {
    }

    private static final AdviceBranch NONE = (rules, ctx) -> AdviceOptEnum.NONE.asSingletonList();
    private static final AdviceBranch SUPPLEMENT_ENTRY =
            (rules, ctx) -> AdviceOptEnum.SUPPLEMENT_ENTRY.asSingletonList();

    /**
     * 非付费区 + 卡上没有未完成行程 → 建议 {@code 020} 免费进闸更新（补进站方向）。
     *
     * <p><b>只给「卡上没有未完成行程」的状态</b>（闭环 {@code 02/05/06/80}、新卡 {@code 03}、
     * 已更新 {@code 08/09}）：这三类的共同特征是**库里查不到一次未闭合的进站**，
     * 而乘客人却在闸外找到了 BOM —— 那就是 {@code 020} 的业务场景「刷卡了但没进站成功」。
     * BOM 不会替没问题的乘客发起 IF5A-01，所以「来分析」本身就是异常信号，
     * 这三格此前返 {@code 000} 等于对这个场景永远不给建议。</p>
     *
     * <p><b>NEVER 把本分支挂到开环 {@code 04/81} 上</b>：那两个状态库里**已有一次未闭合的进站**，
     * 该走 {@code 005}/{@code 006} 补出站；给 {@code 020} 等于在已有进站记录上再补一次进站，
     * 会把原进站站与进站时间覆盖成 BOM 的当前站点，**原行程的计费基准就丢了**。</p>
     *
     * <p>本分支给出的状态集合是执行侧 {@code UPDATE_RULES} 里
     * {@code canFreeUpdateAnyRegisteredStatus} 的**真子集**，因此不会出现
     * 「IF5A-01 建议了、IF5A-03 又拒掉」的自相矛盾（2026-09-15 补建议侧时的核对结论）。</p>
     */
    private static final AdviceBranch FREE_ENTRY_UPDATE =
            (rules, ctx) -> AdviceOptEnum.FREE_UPDATE_020.asSingletonList();

    /**
     * IF5A-01 建议侧规则表。**五行互不重叠**（闭环 02/05/06/80、开环 04/81、03、08 与 09、10），
     * 因此顺序不承载语义；但仍按状态码从小到大排，便于与 {@code docs/business/ride-code.md} 的表对读。
     */
    private static final List<AdviceRule> ADVICE_RULES = List.of(
            new AdviceRule("闭环(02/05/06/80)", QRCodeStatusEnum::isClosedLoop,
                    SupplementStateRules::supplementEntryForClosedLoop, FREE_ENTRY_UPDATE),
            new AdviceRule("开环(04/81)", QRCodeStatusEnum::isOpenLoop,
                    NONE, SupplementStateRules::updateForOpenLoopInFreeArea),
            new AdviceRule("03 新卡", QRCodeStatusEnum.SJT_ISSUE::equals,
                    SupplementStateRules::supplementEntryForNewCard, FREE_ENTRY_UPDATE),
            new AdviceRule("08/09 已更新过", status -> QRCodeStatusEnum.UPDATE_FREE.equals(status)
                    || QRCodeStatusEnum.UPDATE_PAY.equals(status),
                    SUPPLEMENT_ENTRY, FREE_ENTRY_UPDATE),
            new AdviceRule("10 入站码更新", QRCodeStatusEnum.UPDATE_ENTRY::equals,
                    SupplementStateRules::supplementEntryWhenEntryStationUnknown,
                    SupplementStateRules::freeUpdateWithinWindowOrNone));

    private static List<String> supplementEntryForClosedLoop(SupplementStateRules rules, AdviceContext ctx) {
        rules.logClosedLoopWarn(ctx.gateInStation(), ctx.lastTxnStation(), ctx.updateType(),
                ctx.cardId(), ctx.codeStatus().getCode());
        return AdviceOptEnum.SUPPLEMENT_ENTRY.asSingletonList();
    }

    private static List<String> supplementEntryForNewCard(SupplementStateRules rules, AdviceContext ctx) {
        log.warn("IF5A-01 新卡在付费区，补进站, gateIn={}, lastTxn={}, updateType={}, cardId={}",
                ctx.gateInStation(), ctx.lastTxnStation(), ctx.updateType(), ctx.cardId());
        return AdviceOptEnum.SUPPLEMENT_ENTRY.asSingletonList();
    }

    private static List<String> updateForOpenLoopInFreeArea(SupplementStateRules rules, AdviceContext ctx) {
        if (rules.isWithinFreeWindow(ctx.gateInTime())) {
            log.info("IF5A-01 开环状态在非付费区且进站未超{}分钟, 建议免费更新, gateInTime={}, codeStatus={}, cardId={}",
                    FREE_UPDATE_WINDOW_MINUTES, ctx.gateInTime(), ctx.codeStatus().getCode(), ctx.cardId());
            return AdviceOptEnum.FREE_UPDATE.asSingletonList();
        }
        return rules.resolvePaidUpdateOrNone(ctx.codeStatus(), ctx.gateInStation(), ctx.lastTxnStation(),
                ctx.cardId());
    }

    /** 10 入站码更新 + 付费区：进站站已知说明这张码的进站信息是完整的，无需再补。 */
    private static List<String> supplementEntryWhenEntryStationUnknown(SupplementStateRules rules,
                                                                      AdviceContext ctx) {
        return rules.isUnknownStation(ctx.gateInStation())
                ? AdviceOptEnum.SUPPLEMENT_ENTRY.asSingletonList()
                : AdviceOptEnum.NONE.asSingletonList();
    }

    /**
     * 10 入站码更新 + 非付费区：只在时间窗内给 {@code 005}，超窗给 {@code 000}。
     *
     * <p><b>NEVER 顺手改成与开环同分支</b>：开环超窗会回落 {@code 006} 付费更新，
     * 这里超窗只给 {@code 000} —— 入站码更新本身尚未确定真实进站站，报不出价。</p>
     */
    private static List<String> freeUpdateWithinWindowOrNone(SupplementStateRules rules, AdviceContext ctx) {
        return rules.isWithinFreeWindow(ctx.gateInTime())
                ? AdviceOptEnum.FREE_UPDATE.asSingletonList()
                : AdviceOptEnum.NONE.asSingletonList();
    }

    /**
     * 审查项 M007：<b>进站站未知时 NEVER 建议 {@code 006} 付费更新。</b>
     *
     * <p>原实现无条件返回 {@code 006}，而报价函数遇到未知进站站会兜底 {@code 0} 元；
     * BOM 拿着 {@code 006} 提交 IF5A-03 时，执行侧要用真实进站站查票价，
     * 进站站还是那个未知值 —— <b>票价查询必然失败、该分支 100% 执行不下去</b>。
     * 与其给 BOM 一个注定走不通的 {@code 006}，不如在建议阶段就返 {@code 000} 并留 WARN 转人工。</p>
     */
    private List<String> resolvePaidUpdateOrNone(QRCodeStatusEnum codeStatus, String gateInStation,
                                                 String lastTxnStation, String cardId) {
        if (isUnknownStation(gateInStation)) {
            log.warn("WARN_STATION_UNKNOWN: 进站站未知，无法为付费更新报价，本次不建议 006, gateIn={}, lastTxn={},"
                            + " codeStatus={}, cardId={}",
                    gateInStation, lastTxnStation, codeStatus.getCode(), cardId);
            return AdviceOptEnum.NONE.asSingletonList();
        }
        if (isUnknownStation(lastTxnStation)) {
            log.warn("WARN_STATION_UNKNOWN: 上次交易站未知，付费更新以本次出站站重算票价, gateIn={}, codeStatus={},"
                            + " cardId={}",
                    gateInStation, codeStatus.getCode(), cardId);
        }
        return AdviceOptEnum.PAID_UPDATE.asSingletonList();
    }

    /**
     * IF5A-03 判断当前状态是否允许执行建议操作（白名单，不是黑名单）。
     *
     * <p>{@code 005} 免费更新<b>额外复核 {@code gateInTime} 的 20 分钟时间窗</b>。IF5A-01 只是「建议」，
     * 白名单不复核时间就等于：BOM 先拿到 005，隔多久再提交 IF5A-03 都能免费出站，
     * 是纯粹的收入损失口子（2026-09-10 补齐）。{@code 006} 不设时间下限 —— 它本来就是超时分支，
     * 加下限只会在临界点误拒真实补出站。</p>
     */
    boolean isUpdateAllowed(QRCodeStatusEnum codeStatus, String adviceOpt,
                            String updateType, String gateInTime) {
        if (codeStatus == null) {
            return false;
        }
        UpdateRule rule = UPDATE_RULES.get(adviceOpt);
        if (rule == null) {
            return false;
        }
        if (!rule.allowedStatus().test(codeStatus) || !rule.requiredArea().matches(updateType)) {
            return false;
        }
        if (rule.checksFreeWindow() && !isWithinFreeWindow(gateInTime)) {
            log.warn("IF5A-03 免费更新已超 {} 分钟时间窗，拒绝, gateInTime={}, codeStatus={}",
                    FREE_UPDATE_WINDOW_MINUTES, gateInTime, codeStatus.getCode());
            return false;
        }
        return true;
    }

    /** 执行侧要求的 {@code updateType}。两个取值都是**精确相等**判定，因此 null / 未知值两者皆不满足。 */
    private enum RequiredArea {
        PAID_AREA, FREE_AREA;

        boolean matches(String updateType) {
            return this == PAID_AREA
                    ? SupplementCodec.UPDATE_TYPE_PAID_AREA.equals(updateType)
                    : SupplementCodec.UPDATE_TYPE_FREE_AREA.equals(updateType);
        }
    }

    /**
     * 执行侧一行规则：状态白名单 + 区域要求 + 是否复核时间窗。
     *
     * @param checksFreeWindow 只有 {@code 005} 为 true —— {@code 006} 本来就是超时分支，
     *                         加时间下限只会在临界点误拒真实补出站；{@code 018} / {@code 020} 是补进站方向，
     *                         「进站早于 20 分钟」不构成拒绝理由。
     */
    private record UpdateRule(Predicate<QRCodeStatusEnum> allowedStatus,
                              RequiredArea requiredArea,
                              boolean checksFreeWindow) {
    }

    /**
     * IF5A-03 执行侧白名单表。**键是建议操作码，未登记的键一律拒绝**（白名单不是黑名单）。
     *
     * <p>三行的区域要求刻意不同，<b>NEVER 抹平</b>：{@code 018} 只在付费区（要求付费区是
     * 审查项 X001，与建议侧对齐，缺了它 BOM 能给从未进站的新码补进站）；
     * {@code 005} / {@code 006} 只在非付费区（放过 {@code updateType} 等于让 BOM 绕过报价路径）。</p>
     *
     * <p><b>{@code 020} 与 {@code 018} 同为补进站方向，区别只在付费区标</b>（用户 2026-09-15 裁决：
     * 020 是「乘客刷卡之后没有进站成功」的免费进闸更新）：{@code 018} 要 {@code updateType=01}（人已在付费区），
     * {@code 020} 要 {@code updateType=00}（人还在非付费区）。状态白名单上 020 最宽 ——
     * {@link #canFreeUpdateAnyRegisteredStatus}（**任何已登记状态，含已出站与新卡**）、且
     * {@code checksFreeWindow=false}，于是 <b>020 在 ITP 侧只剩两条闸口：{@code updateType=00} 与「状态不是脏值」</b>。
     * 而 {@code 005} / {@code 006} 是补出站方向、仍限 {@link #hasEnteredWithoutExit}（已进站未出站）。
     * <b>NEVER 把 020 与 005 合成同一行规则</b>（方向都不同，合了必错）；
     * <b>也 NEVER 把 018 的 {@link #canSupplementEntry} 套给 020</b> —— 那条排除开环 04/81，
     * 而「刷卡没进成」的卡完全可能已是 04。</p>
     *
     * <p>登记 020 的直接原因：BOM 实际上送的就是 020，而本表原先没有这个键 —— 未登记的键一律拒绝，
     * 于是每笔 IF5A-03 都返 8001（2026-09-14 19:58 实测，零数据落库）。</p>
     */
    private static final Map<String, UpdateRule> UPDATE_RULES = Map.of(
            AdviceOptEnum.SUPPLEMENT_ENTRY.getCode(),
            new UpdateRule(SupplementStateRules::canSupplementEntry, RequiredArea.PAID_AREA, false),
            AdviceOptEnum.PAID_UPDATE.getCode(),
            new UpdateRule(SupplementStateRules::hasEnteredWithoutExit, RequiredArea.FREE_AREA, false),
            AdviceOptEnum.FREE_UPDATE.getCode(),
            new UpdateRule(SupplementStateRules::hasEnteredWithoutExit, RequiredArea.FREE_AREA, true),
            AdviceOptEnum.FREE_UPDATE_020.getCode(),
            new UpdateRule(SupplementStateRules::canFreeUpdateAnyRegisteredStatus, RequiredArea.FREE_AREA, false));

    /**
     * 允许补进站（{@code 018}）的前置状态：已收口的行程（闭环）、新卡、已更新过、入站码更新。
     *
     * <p><b>开环状态（04 / 81）不在其内</b>：已进站的码再补一次进站没有业务含义。</p>
     */
    private static boolean canSupplementEntry(QRCodeStatusEnum codeStatus) {
        return codeStatus.isClosedLoop()
                || QRCodeStatusEnum.SJT_ISSUE.equals(codeStatus)
                || QRCodeStatusEnum.UPDATE_FREE.equals(codeStatus)
                || QRCodeStatusEnum.UPDATE_PAY.equals(codeStatus)
                || QRCodeStatusEnum.UPDATE_ENTRY.equals(codeStatus);
    }

    /**
     * 允许补出站（{@code 005} / {@code 006}）的前置状态：开环（已进站未出站）或入站码更新。
     *
     * <p>审查项 C002：原实现在开环判断之后还留了一行 {@code SELF_SERVICE_ENTRY.equals(...)}，
     * 而 81 已被 {@code isOpenLoop()} 吃掉，那行永远为 false。合并成本方法即消除该死代码。</p>
     */
    private static boolean hasEnteredWithoutExit(QRCodeStatusEnum codeStatus) {
        return codeStatus.isOpenLoop() || QRCodeStatusEnum.UPDATE_ENTRY.equals(codeStatus);
    }

    /**
     * {@code 020} 免费更新的状态白名单：<b>任何已登记的 {@link QRCodeStatusEnum} 一律放行</b>，
     * 含**新卡 {@code 03}**（用户 2026-09-14 第三次裁决：020 连新卡也该放行）、
     * 开环 {@code 04/81}、闭环（已出站）{@code 02/05/06/80}、已更新过 {@code 08/09}、
     * 入站码更新 {@code 10}，以及 {@code 01} 无交易 / {@code 70} 异常 / {@code FF} 进站失败。
     *
     * <p><b>这不是「取消白名单」</b>：{@code isUpdateAllowed} 在调用本判据**之前**已经拦掉
     * {@code codeStatus == null} —— 也就是「库里有值但不在枚举里」的脏数据（{@code resolveCodeStatus}
     * 的审查项 M003）。因此 020 的边界仍是「已登记的 13 个取值」这个有限集合，
     * 加上 {@link RequiredArea#FREE_AREA} 这一条硬要求。<b>NEVER 把 null 也放进来</b>。
     *
     * <p>为什么 020 能这么宽、而 {@code 005} / {@code 006} / {@code 018} 不能：005 的 20 分钟窗与 006 的按进站站报价
     * 都以「已进站未出站」为前提，闭环 / 新卡对它们没有意义；018 排除开环（已进站的码再补进站无意义）。
     * 020 是「乘客刷卡没进成，给一次免费进闸」，卡当前处于什么状态都不构成拒绝理由 ——
     * 包括已是开环 {@code 04}（上一次刷卡在后台记成了进站、人却没进去，这正是最典型的场景）。
     * <b>NEVER 把这条判据复用给 005 / 006 / 018</b>。
     *
     * <p><b>剩下的唯一闸口是 BOM 与 {@code updateType}</b>：ITP 侧只拦付费区与脏状态。
     * 这是有意为之的业务口径，改动前 MUST 与业务确认。
     */
    private static boolean canFreeUpdateAnyRegisteredStatus(QRCodeStatusEnum codeStatus) {
        return codeStatus != null;
    }

    /**
     * 判断进站时间是否在免费更新时间窗内。
     *
     * <p><b>MUST 同时判下界。</b>{@code gateInTime} 来自设备上送的 {@code handleDateTime}，
     * 设备时钟超前或脏数据会让差值为负；只判上界的话负数同样算「窗内」，
     * 等于把 005 免费更新无限期放行 —— 正是这个方法要堵的收入口子（2026-09-10 补下界）。</p>
     */
    boolean isWithinFreeWindow(String gateInTime) {
        if (!StringUtils.hasText(gateInTime) || gateInTime.length() < 14) {
            return false;
        }
        try {
            LocalDateTime inTime = LocalDateTime.parse(gateInTime, BIZ_TIME_FORMATTER);
            long minutes = java.time.Duration.between(inTime, LocalDateTime.now()).toMinutes();
            if (minutes < 0) {
                log.warn("进站时间晚于当前时间，按超窗处理, gateInTime={}, minutes={}", gateInTime, minutes);
                return false;
            }
            return minutes <= FREE_UPDATE_WINDOW_MINUTES;
        } catch (Exception e) {
            log.warn("解析进站时间失败, gateInTime={}", gateInTime, e);
            return false;
        }
    }

    private void logClosedLoopWarn(String gateInStation, String lastTxnStation, String updateType,
                                   String cardId, String codeStatus) {
        if (isUnknownStation(lastTxnStation)) {
            log.warn("WARN_STATION_UNKNOWN: 闭环状态codeStatus={}在付费区，补进站, gateIn={}, lastTxn={},"
                            + " updateType={}, cardId={}",
                    codeStatus, gateInStation, lastTxnStation, updateType, cardId);
        }
    }
}
