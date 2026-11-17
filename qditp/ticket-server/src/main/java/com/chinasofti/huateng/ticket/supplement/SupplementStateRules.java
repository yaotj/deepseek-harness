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

@Component
class SupplementStateRules {

    private static final Logger log = LoggerFactory.getLogger(SupplementStateRules.class);
    private static final DateTimeFormatter BIZ_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int FREE_UPDATE_WINDOW_MINUTES = 20;

    @Value("${ticket.default-code-status:03}")
    private String defaultCodeStatus;

    @Value("${ticket.default-last-txn-station:FFFF}")
    private String defaultLastTxnStation;

    private QRCodeStatusEnum defaultCodeStatusEnum;

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

    QRCodeStatusEnum resolveCodeStatus(String rawCodeStatus) {
        if (!StringUtils.hasText(rawCodeStatus)) {
            return defaultCodeStatusEnum;
        }
        return QRCodeStatusEnum.fromCode(rawCodeStatus);
    }


    List<String> resolveAdviceOpt(QRCodeStatusEnum codeStatus, String gateInStation, String lastTxnStation,
                                  String updateType, String gateInTime, String cardId) {
        return resolveAdviceOpt(codeStatus, gateInStation, lastTxnStation, updateType, gateInTime, cardId, null);
    }


    List<String> resolveAdviceOpt(QRCodeStatusEnum codeStatus, String gateInStation, String lastTxnStation,
                                  String updateType, String gateInTime, String cardId, String bomStationCode) {
        if (codeStatus == null) {
            return AdviceOptEnum.NONE.asSingletonList();
        }
        AdviceContext ctx = new AdviceContext(codeStatus, gateInStation, lastTxnStation,
                updateType, gateInTime, cardId, bomStationCode);
        boolean inPaidArea = SupplementCodec.UPDATE_TYPE_PAID_AREA.equals(updateType);
        for (AdviceRule rule : ADVICE_RULES) {
            if (rule.matches().test(codeStatus)) {
                return (inPaidArea ? rule.paidArea() : rule.freeArea()).resolve(this, ctx);
            }
        }
        return AdviceOptEnum.NONE.asSingletonList();
    }

    private enum StationCompare { SAME, DIFFERENT, UNKNOWN }

    private StationCompare compareStations(String gateInStation, String bomStation) {
        if (isUnknownStation(gateInStation) || isUnknownStation(bomStation)) {
            return StationCompare.UNKNOWN;
        }
        return gateInStation.equals(bomStation) ? StationCompare.SAME : StationCompare.DIFFERENT;
    }

    private record AdviceContext(QRCodeStatusEnum codeStatus, String gateInStation, String lastTxnStation,
                                 String updateType, String gateInTime, String cardId, String bomStationCode) {
    }

    private interface AdviceBranch {
        List<String> resolve(SupplementStateRules rules, AdviceContext ctx);
    }

    private record AdviceRule(String name, Predicate<QRCodeStatusEnum> matches,
                             AdviceBranch paidArea, AdviceBranch freeArea) {
    }

    private static final AdviceBranch NONE = (rules, ctx) -> AdviceOptEnum.NONE.asSingletonList();
    private static final AdviceBranch SUPPLEMENT_ENTRY =
            (rules, ctx) -> AdviceOptEnum.SUPPLEMENT_ENTRY.asSingletonList();

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
        StationCompare cmp = rules.compareStations(ctx.gateInStation(), ctx.bomStationCode());
        if (cmp == StationCompare.DIFFERENT) {
            if (rules.isUnknownStation(ctx.gateInStation())) {
                return AdviceOptEnum.FREE_UPDATE_020.asSingletonList();
            }
            log.info("IF5A-01 开环状态跨站补站({}≠{}), 建议付费更新, gateInTime={}, codeStatus={}, cardId={}",
                    ctx.gateInStation(), ctx.bomStationCode(), ctx.gateInTime(),
                    ctx.codeStatus().getCode(), ctx.cardId());
            return List.of(AdviceOptEnum.PAID_UPDATE.getCode(), AdviceOptEnum.FREE_UPDATE_020.getCode());
        }
        if (rules.isWithinFreeWindow(ctx.gateInTime())) {
            log.info("IF5A-01 开环状态在非付费区且进站未超{}分钟, 建议免费更新, gateInTime={}, codeStatus={}, cardId={}",
                    FREE_UPDATE_WINDOW_MINUTES, ctx.gateInTime(), ctx.codeStatus().getCode(), ctx.cardId());
            return AdviceOptEnum.FREE_UPDATE.asSingletonList();
        }
        return rules.resolveOverWindowAdvice(ctx.codeStatus(), ctx.gateInStation(), ctx.lastTxnStation(),
                ctx.gateInTime(), ctx.cardId());
    }

    private static List<String> supplementEntryWhenEntryStationUnknown(SupplementStateRules rules,
                                                                      AdviceContext ctx) {
        return rules.isUnknownStation(ctx.gateInStation())
                ? AdviceOptEnum.SUPPLEMENT_ENTRY.asSingletonList()
                : AdviceOptEnum.NONE.asSingletonList();
    }

    private static List<String> freeUpdateWithinWindowOrNone(SupplementStateRules rules, AdviceContext ctx) {
        return rules.isWithinFreeWindow(ctx.gateInTime())
                ? AdviceOptEnum.FREE_UPDATE.asSingletonList()
                : AdviceOptEnum.NONE.asSingletonList();
    }


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
        return List.of(AdviceOptEnum.PAID_UPDATE.getCode(), AdviceOptEnum.FREE_UPDATE_020.getCode());
    }


    boolean isUpdateAllowed(QRCodeStatusEnum codeStatus, String adviceOpt,
                            String updateType, String gateInTime) {
        return isUpdateAllowed(codeStatus, adviceOpt, updateType, gateInTime, null, null);
    }

    boolean isUpdateAllowed(QRCodeStatusEnum codeStatus, String adviceOpt,
                           String updateType, String gateInTime,
                           String gateInStation, String updateStationCode) {
        return checkUpdate(codeStatus, adviceOpt, updateType, gateInTime,
                gateInStation, updateStationCode) == UpdateRejection.NONE;
    }


    UpdateRejection checkUpdate(QRCodeStatusEnum codeStatus, String adviceOpt,
                                String updateType, String gateInTime) {
        return checkUpdate(codeStatus, adviceOpt, updateType, gateInTime, null, null);
    }


    UpdateRejection checkUpdate(QRCodeStatusEnum codeStatus, String adviceOpt,
                               String updateType, String gateInTime,
                               String gateInStation, String updateStationCode) {
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
        return rule.freeWindow().check(this, gateInTime, codeStatus, gateInStation, updateStationCode);
    }

    enum UpdateRejection {
        NONE, STATE_NOT_ALLOWED, FREE_WINDOW_EXPIRED, FREE_WINDOW_NOT_EXPIRED, CROSS_STATION_NOT_FREE
    }

    private enum RequiredArea {
        PAID_AREA, FREE_AREA;

        boolean matches(String updateType) {
            return this == PAID_AREA
                    ? SupplementCodec.UPDATE_TYPE_PAID_AREA.equals(updateType)
                    : SupplementCodec.UPDATE_TYPE_FREE_AREA.equals(updateType);
        }
    }


    private enum FreeWindow {
        IGNORED, REQUIRE_WITHIN, REQUIRE_EXPIRED;

        UpdateRejection check(SupplementStateRules rules, String gateInTime, QRCodeStatusEnum codeStatus,
                              String gateInStation, String updateStationCode) {
            if (this == IGNORED) {
                return UpdateRejection.NONE;
            }
            StationCompare cmp = rules.compareStations(gateInStation, updateStationCode);
            if (this == REQUIRE_WITHIN) {
                if (!rules.isWithinFreeWindow(gateInTime)) {
                    log.warn("IF5A-03 免费更新已超 {} 分钟时间窗，拒绝, gateInTime={}, codeStatus={}",
                            FREE_UPDATE_WINDOW_MINUTES, gateInTime, codeStatus.getCode());
                    return UpdateRejection.FREE_WINDOW_EXPIRED;
                }
                if (cmp == StationCompare.DIFFERENT) {
                    log.warn("IF5A-03 跨站补站禁止走免费更新(005)，应走付费更新(006), gateIn={}, update={}, codeStatus={}",
                            gateInStation, updateStationCode, codeStatus.getCode());
                    return UpdateRejection.CROSS_STATION_NOT_FREE;
                }
                return UpdateRejection.NONE;
            }
            if (cmp == StationCompare.SAME) {
                if (rules.isWithinFreeWindow(gateInTime)) {
                    log.warn("IF5A-03 同站且在 20 分钟窗内应走免费更新(005)，拒绝付费更新(006), gateIn={}, codeStatus={}",
                            gateInStation, codeStatus.getCode());
                    return UpdateRejection.FREE_WINDOW_NOT_EXPIRED;
                }
                return UpdateRejection.NONE;
            }
            if (cmp == StationCompare.DIFFERENT) {
                return UpdateRejection.NONE;
            }
            if (!rules.isFreeWindowExpired(gateInTime)) {
                log.warn("IF5A-03 付费更新站码未知且无法确认已超窗，拒绝收费, gateInTime={}, codeStatus={}",
                        gateInTime, codeStatus.getCode());
                return UpdateRejection.FREE_WINDOW_NOT_EXPIRED;
            }
            return UpdateRejection.NONE;
        }
    }

    private record UpdateRule(Predicate<QRCodeStatusEnum> allowedStatus,
                              RequiredArea requiredArea,
                              FreeWindow freeWindow) {
    }

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


    private static boolean canSupplementEntry(QRCodeStatusEnum codeStatus) {
        return codeStatus.isClosedLoop()
                || QRCodeStatusEnum.SJT_ISSUE.equals(codeStatus)
                || QRCodeStatusEnum.UPDATE_FREE.equals(codeStatus)
                || QRCodeStatusEnum.UPDATE_PAY.equals(codeStatus)
                || QRCodeStatusEnum.UPDATE_ENTRY.equals(codeStatus);
    }

    private static boolean hasEnteredWithoutExit(QRCodeStatusEnum codeStatus) {
        return codeStatus.isOpenLoop() || QRCodeStatusEnum.UPDATE_ENTRY.equals(codeStatus);
    }

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
