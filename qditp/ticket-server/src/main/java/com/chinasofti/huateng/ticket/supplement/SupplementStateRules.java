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

    /** 非付费区 + 卡上没有未完成行程 → 建议 {@code 020} 免费进闸更新（补进站方向）。 */
    private static final AdviceBranch FREE_ENTRY_UPDATE =
            (rules, ctx) -> AdviceOptEnum.FREE_UPDATE_020.asSingletonList();

    /** IF5A-01 建议侧规则表。 */
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

    /** 10 入站码更新 + 非付费区：只在时间窗内给 {@code 005}，超窗给 {@code 000}。 */
    private static List<String> freeUpdateWithinWindowOrNone(SupplementStateRules rules, AdviceContext ctx) {
        return rules.isWithinFreeWindow(ctx.gateInTime())
                ? AdviceOptEnum.FREE_UPDATE.asSingletonList()
                : AdviceOptEnum.NONE.asSingletonList();
    }

    /** 审查项 M007：进站站未知时 */
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

    /** IF5A-03 判断当前状态是否允许执行建议操作（白名单，不是黑名单）。 */
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
     * 执行侧一行规则：状态白名单 + 区域要求 + 是否复核时间窗。
     *
     * @param checksFreeWindow 只有 {@code 005} 为 true —— {@code 006} 本来就是超时分支，
     */
    private record UpdateRule(Predicate<QRCodeStatusEnum> allowedStatus,
                              RequiredArea requiredArea,
                              boolean checksFreeWindow) {
    }

    /** IF5A-03 执行侧白名单表。 */
    private static final Map<String, UpdateRule> UPDATE_RULES = Map.of(
            AdviceOptEnum.SUPPLEMENT_ENTRY.getCode(),
            new UpdateRule(SupplementStateRules::canSupplementEntry, RequiredArea.PAID_AREA, false),
            AdviceOptEnum.PAID_UPDATE.getCode(),
            new UpdateRule(SupplementStateRules::hasEnteredWithoutExit, RequiredArea.FREE_AREA, false),
            AdviceOptEnum.FREE_UPDATE.getCode(),
            new UpdateRule(SupplementStateRules::hasEnteredWithoutExit, RequiredArea.FREE_AREA, true),
            AdviceOptEnum.FREE_UPDATE_020.getCode(),
            new UpdateRule(SupplementStateRules::canFreeUpdateAnyRegisteredStatus, RequiredArea.FREE_AREA, false));

    /** 允许补进站（{@code 018}）的前置状态：已收口的行程（闭环）、新卡、已更新过、入站码更新。 */
    private static boolean canSupplementEntry(QRCodeStatusEnum codeStatus) {
        return codeStatus.isClosedLoop()
                || QRCodeStatusEnum.SJT_ISSUE.equals(codeStatus)
                || QRCodeStatusEnum.UPDATE_FREE.equals(codeStatus)
                || QRCodeStatusEnum.UPDATE_PAY.equals(codeStatus)
                || QRCodeStatusEnum.UPDATE_ENTRY.equals(codeStatus);
    }

    /** 允许补出站（{@code 005} / {@code 006}）的前置状态：开环（已进站未出站）或入站码更新。 */
    private static boolean hasEnteredWithoutExit(QRCodeStatusEnum codeStatus) {
        return codeStatus.isOpenLoop() || QRCodeStatusEnum.UPDATE_ENTRY.equals(codeStatus);
    }

    /** {@code 020} 免费更新的状态白名单：任何已登记的 {@link QRCodeStatusEnum} 一律放行， 含新卡 {@code 03}（用户 2026-09-14 第三次裁决：020 连新卡也该放行）、 开环 {@code 04/81}、闭环（已出站）{@code 02/05/06/80}、已更新过 {@code 08/09}、 入站码更新 {@code 10}，以及 {@code 01} 无交易 / {@code 70} 异常 / {@code FF} 进站失败。 */
    private static boolean canFreeUpdateAnyRegisteredStatus(QRCodeStatusEnum codeStatus) {
        return codeStatus != null;
    }

    /** 判断进站时间是否在免费更新时间窗内。 */
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
