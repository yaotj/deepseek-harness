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

/** 补站状态机规则：IF5A-01 建议操作（{@link #resolveAdviceOpt}）与 IF5A-03 执行白名单 （{@link #isUpdateAllowed}）。 */
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

    /** 审查项 C001：启动即校验 {@code ticket.default-code-status}，解析不出枚举直接启动失败。 */
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

    /** 把库内 {@code CODE_STATUS} 解析成枚举。 */
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
        return AdviceOptEnum.NONE.asSingletonList();
    }

    /** 建议侧一次判定的全部入参，只为让分支能按名取值，不参与业务。 */
    private record AdviceContext(QRCodeStatusEnum codeStatus, String gateInStation, String lastTxnStation,
                                 String updateType, String gateInTime, String cardId) {
    }

    /** 建议侧一格的结果计算。 */
    private interface AdviceBranch {
        List<String> resolve(SupplementStateRules rules, AdviceContext ctx);
    }

    /** 建议侧一行规则：状态匹配器 + 付费区结果 + 非付费区结果。 */
    private record AdviceRule(String name, Predicate<QRCodeStatusEnum> matches,
                             AdviceBranch paidArea, AdviceBranch freeArea) {
    }

    private static final AdviceBranch NONE = (rules, ctx) -> AdviceOptEnum.NONE.asSingletonList();
    private static final AdviceBranch SUPPLEMENT_ENTRY =
            (rules, ctx) -> AdviceOptEnum.SUPPLEMENT_ENTRY.asSingletonList();

    /** IF5A-01 建议侧规则表。 */
    private static final List<AdviceRule> ADVICE_RULES = List.of(
            new AdviceRule("闭环(02/05/06/80)", QRCodeStatusEnum::isClosedLoop,
                    SupplementStateRules::supplementEntryForClosedLoop, NONE),
            new AdviceRule("开环(04/81)", QRCodeStatusEnum::isOpenLoop,
                    NONE, SupplementStateRules::updateForOpenLoopInFreeArea),
            new AdviceRule("03 新卡", QRCodeStatusEnum.SJT_ISSUE::equals,
                    SupplementStateRules::supplementEntryForNewCard, NONE),
            new AdviceRule("08/09 已更新过", status -> QRCodeStatusEnum.UPDATE_FREE.equals(status)
                    || QRCodeStatusEnum.UPDATE_PAY.equals(status),
                    SUPPLEMENT_ENTRY, NONE),
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
        return rules.resolveOverWindowAdvice(ctx.codeStatus(), ctx.gateInStation(), ctx.lastTxnStation(),
                ctx.gateInTime(), ctx.cardId());
    }

    /** 10 入站码更新 + 付费区：进站站已知说明这张码的进站信息是完整的，无需再补。 */
    private static List<String> supplementEntryWhenEntryStationUnknown(SupplementStateRules rules,
                                                                      AdviceContext ctx) {
        return rules.isUnknownStation(ctx.gateInStation())
                ? AdviceOptEnum.SUPPLEMENT_ENTRY.asSingletonList()
                : AdviceOptEnum.NONE.asSingletonList();
    }

    /** 10 入站码更新 + 非付费区：只在时间窗内给 {@code 005}，超窗给 {@code 000}。 */
    private static List<String> freeUpdateWithinWindowOrNone(SupplementStateRules rules, AdviceContext ctx) {
        return rules.isWithinFreeWindow(ctx.gateInTime())
                ? AdviceOptEnum.FREE_UPDATE.asSingletonList()
                : AdviceOptEnum.NONE.asSingletonList();
    }

    /**
     * 开环 + 非付费区 + 不在 20 分钟窗内：先给无时间窗限制的 {@code 020} 免费更新，再按能否收费追加 {@code 006}。
     *
     * <p>审查项 M007：进站站未知时报不出价，那个 {@code 006} 必然执行不下去，本次只给 {@code 020}。
     *
     * <p>同理，**进站时间缺失或解析不出时也只给 {@code 020}**（2026-09-18 端到端实测补，ADR-D136）：
     * 执行侧的 {@code 006} 以 {@link #isFreeWindowExpired} 为前置（「免费不扣费」裁决），证明不了超窗一律拒，
     * 因此这里若照旧给出 {@code 006}，BOM 会显示一个「点下去必然返 8305」的付费选项。
     * **建议侧 MUST 与执行侧同口径，NEVER 建议一个自己都不会放行的操作。**
     */
    private List<String> resolveOverWindowAdvice(QRCodeStatusEnum codeStatus, String gateInStation,
                                                 String lastTxnStation, String gateInTime, String cardId) {
        if (isUnknownStation(gateInStation)) {
            log.warn("WARN_STATION_UNKNOWN: 进站站未知，无法为付费更新报价，本次只建议 020, gateIn={}, lastTxn={},"
                            + " codeStatus={}, cardId={}",
                    gateInStation, lastTxnStation, codeStatus.getCode(), cardId);
            return AdviceOptEnum.FREE_UPDATE_020.asSingletonList();
        }
        if (!isFreeWindowExpired(gateInTime)) {
            log.warn("IF5A-01 进站时间缺失或解析不出，证明不了超窗，本次只建议 020, gateInTime={}, codeStatus={},"
                            + " cardId={}",
                    gateInTime, codeStatus.getCode(), cardId);
            return AdviceOptEnum.FREE_UPDATE_020.asSingletonList();
        }
        if (isUnknownStation(lastTxnStation)) {
            log.warn("WARN_STATION_UNKNOWN: 上次交易站未知，付费更新以本次出站站重算票价, gateIn={}, codeStatus={},"
                            + " cardId={}",
                    gateInStation, codeStatus.getCode(), cardId);
        }
        return List.of(AdviceOptEnum.FREE_UPDATE_020.getCode(), AdviceOptEnum.PAID_UPDATE.getCode());
    }

    /**
     * IF5A-03 判断当前状态是否允许执行建议操作（白名单，不是黑名单）。
     *
     * <p>只给出「放行 / 不放行」，拒绝原因用 {@link #checkUpdate} 取。
     */
    boolean isUpdateAllowed(QRCodeStatusEnum codeStatus, String adviceOpt,
                            String updateType, String gateInTime) {
        return checkUpdate(codeStatus, adviceOpt, updateType, gateInTime) == UpdateRejection.NONE;
    }

    /**
     * IF5A-03 执行侧校验，**按「状态 + 区域」在前、「时间窗」在后**的顺序给出拒绝原因。
     *
     * <p>顺序是本方法存在的唯一理由（2026-09-18 端到端实测，ADR-D136）：调用方原先把时间窗检查写在
     * 白名单之前，于是「{@code 03} 新卡直传 {@code 006}」「闭环 {@code 02} 直传 {@code 005}」这类
     * **状态本来就不允许**的请求，会先被时间窗分支截走、返 8305「时间窗已过 / 未确认超窗」——
     * 现场据此以为「只是来晚了、重新分析一次就行」，而实际重试多少次都不会通过。
     * **NEVER 把时间窗判定挪回状态白名单之前。**
     */
    UpdateRejection checkUpdate(QRCodeStatusEnum codeStatus, String adviceOpt,
                                String updateType, String gateInTime) {
        if (codeStatus == null) {
            return UpdateRejection.STATE_NOT_ALLOWED;
        }
        UpdateRule rule = UPDATE_RULES.get(adviceOpt);
        if (rule == null) {
            return UpdateRejection.STATE_NOT_ALLOWED;
        }
        if (!rule.allowedStatus().test(codeStatus) || !rule.requiredArea().matches(updateType)) {
            return UpdateRejection.STATE_NOT_ALLOWED;
        }
        return rule.freeWindow().check(this, gateInTime, codeStatus);
    }

    /** IF5A-03 执行侧的拒绝原因，{@link #NONE} 表示放行。 */
    enum UpdateRejection {
        NONE, STATE_NOT_ALLOWED, FREE_WINDOW_EXPIRED, FREE_WINDOW_NOT_EXPIRED
    }

    /** 执行侧要求的 {@code updateType}。 */
    private enum RequiredArea {
        PAID_AREA, FREE_AREA;

        boolean matches(String updateType) {
            return this == PAID_AREA
                    ? SupplementCodec.UPDATE_TYPE_PAID_AREA.equals(updateType)
                    : SupplementCodec.UPDATE_TYPE_FREE_AREA.equals(updateType);
        }
    }

    /**
     * 执行侧对 20 分钟免费更新时间窗的要求。
     *
     * <p>{@code 006} 是 {@link #REQUIRE_EXPIRED} 而不是 {@link #IGNORED}：**窗内免费、NEVER 收费**
     * （用户 2026-09-18 裁决，ADR-D136）。窗内该走 {@code 005}，此时放行 {@code 006} 等于把本可免费的
     * 更新收成付费 —— 2026-09-18 实测过这条口子（IF5A-01 明明给的是 {@code 005}，直传 {@code 006} 照样成功
     * 并按票价入账）。{@code 020} 按定义就是「没有时间窗限制的免费更新」，{@code 018} 是补进站方向，两者
     * 与窗无关。
     */
    private enum FreeWindow {
        IGNORED, REQUIRE_WITHIN, REQUIRE_EXPIRED;

        UpdateRejection check(SupplementStateRules rules, String gateInTime, QRCodeStatusEnum codeStatus) {
            if (this == IGNORED) {
                return UpdateRejection.NONE;
            }
            if (this == REQUIRE_WITHIN && !rules.isWithinFreeWindow(gateInTime)) {
                log.warn("IF5A-03 免费更新已超 {} 分钟时间窗，拒绝, gateInTime={}, codeStatus={}",
                        FREE_UPDATE_WINDOW_MINUTES, gateInTime, codeStatus.getCode());
                return UpdateRejection.FREE_WINDOW_EXPIRED;
            }
            if (this == REQUIRE_EXPIRED && !rules.isFreeWindowExpired(gateInTime)) {
                log.warn("IF5A-03 付费更新要求已确认超出免费时间窗（时间缺失或窗内均拒绝）, gateInTime={}, codeStatus={}",
                        gateInTime, codeStatus.getCode());
                return UpdateRejection.FREE_WINDOW_NOT_EXPIRED;
            }
            return UpdateRejection.NONE;
        }
    }

    /** 执行侧一行规则：状态白名单 + 区域要求 + 时间窗要求。 */
    private record UpdateRule(Predicate<QRCodeStatusEnum> allowedStatus,
                              RequiredArea requiredArea,
                              FreeWindow freeWindow) {
    }

    /** IF5A-03 执行侧白名单表。 */
    private static final Map<String, UpdateRule> UPDATE_RULES = Map.of(
            AdviceOptEnum.SUPPLEMENT_ENTRY.getCode(),
            new UpdateRule(SupplementStateRules::canSupplementEntry,
                    RequiredArea.PAID_AREA, FreeWindow.IGNORED),
            AdviceOptEnum.PAID_UPDATE.getCode(),
            new UpdateRule(SupplementStateRules::hasEnteredWithoutExit,
                    RequiredArea.FREE_AREA, FreeWindow.REQUIRE_EXPIRED),
            AdviceOptEnum.FREE_UPDATE.getCode(),
            new UpdateRule(SupplementStateRules::hasEnteredWithoutExit,
                    RequiredArea.FREE_AREA, FreeWindow.REQUIRE_WITHIN),
            AdviceOptEnum.FREE_UPDATE_020.getCode(),
            new UpdateRule(SupplementStateRules::hasEnteredWithoutExit,
                    RequiredArea.FREE_AREA, FreeWindow.IGNORED));


    /** 允许补进站（{@code 018}）的前置状态：已收口的行程（闭环）、新卡、已更新过、入站码更新。 */
    private static boolean canSupplementEntry(QRCodeStatusEnum codeStatus) {
        return codeStatus.isClosedLoop()
                || QRCodeStatusEnum.SJT_ISSUE.equals(codeStatus)
                || QRCodeStatusEnum.UPDATE_FREE.equals(codeStatus)
                || QRCodeStatusEnum.UPDATE_PAY.equals(codeStatus)
                || QRCodeStatusEnum.UPDATE_ENTRY.equals(codeStatus);
    }

    /** 允许补出站（{@code 005} / {@code 006} / {@code 020}）的前置状态：开环（已进站未出站）或入站码更新。 */
    private static boolean hasEnteredWithoutExit(QRCodeStatusEnum codeStatus) {
        return codeStatus.isOpenLoop() || QRCodeStatusEnum.UPDATE_ENTRY.equals(codeStatus);
    }

    /** 判断进站时间是否在免费更新时间窗内。 */
    boolean isWithinFreeWindow(String gateInTime) {
        LocalDateTime inTime = parseBizTime(gateInTime);
        if (inTime == null) {
            return false;
        }
        long minutes = java.time.Duration.between(inTime, LocalDateTime.now()).toMinutes();
        if (minutes < 0) {
            log.warn("进站时间晚于当前时间，按超窗处理, gateInTime={}, minutes={}", gateInTime, minutes);
            return false;
        }
        return minutes <= FREE_UPDATE_WINDOW_MINUTES;
    }

    /**
     * 判断是否**已确认**超出免费更新时间窗。
     *
     * <p>与 {@code !isWithinFreeWindow(...)} 不等价：进站时间缺失或解析不出时那个式子得到 true，
     * 而本方法 MUST 返回 false —— {@code 006} 付费更新以本方法为前置，**证明不了超窗就 NEVER 收费**
     * （用户 2026-09-18「免费不扣费」裁决，ADR-D136）。这类卡仍可走 {@code 020} 免费更新，
     * 对乘客无损。
     */
    boolean isFreeWindowExpired(String gateInTime) {
        return parseBizTime(gateInTime) != null && !isWithinFreeWindow(gateInTime);
    }

    private LocalDateTime parseBizTime(String bizTime) {
        if (!StringUtils.hasText(bizTime) || bizTime.length() < 14) {
            return null;
        }
        try {
            return LocalDateTime.parse(bizTime, BIZ_TIME_FORMATTER);
        } catch (Exception e) {
            log.warn("解析进站时间失败, gateInTime={}", bizTime, e);
            return null;
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
