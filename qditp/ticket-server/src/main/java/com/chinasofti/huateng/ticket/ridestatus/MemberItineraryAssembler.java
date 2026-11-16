package com.chinasofti.huateng.ticket.ridestatus;

import com.chinasofti.huateng.model.app.MemberItineraryDTO;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import com.chinasofti.huateng.ticket.station.StationNameResolver;
import com.chinasofti.huateng.ticket.supplement.SupplementCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** IF8A-29 行程视图装配器。 */
@Component
class MemberItineraryAssembler {

    private static final String PAY_STATUS_DEFAULT = "00";

    private final StationNameResolver stationNameResolver;
    private final String defaultLastTxnStation;

    public MemberItineraryAssembler(StationNameResolver stationNameResolver,
                                    @Value("${ticket.default-last-txn-station:FFFF}") String defaultLastTxnStation) {
        this.stationNameResolver = stationNameResolver;
        this.defaultLastTxnStation = defaultLastTxnStation;
    }

    /**
     * 装配用户上次行程视图。
     *
     * @param currentStatus 乘车码当前状态，非空
     * @param latestDetail 最近一次过闸明细，可为空（表示尚无过闸记录）
     * @return 行程视图
     */
    public MemberItineraryDTO assemble(QRCodeStatus currentStatus, QRCodeTxnDetail latestDetail) {
        Itinerary itinerary = Itinerary.of(currentStatus, latestDetail);
        Map<String, String> stationNames = stationNameResolver.resolveStationNames(collectStationCodes(itinerary));

        MemberItineraryDTO view = new MemberItineraryDTO();
        view.setTicketStatus(currentStatus.getCodeStatus());
        view.setPayStatus(PAY_STATUS_DEFAULT);

        switch (itinerary) {
            case Itinerary.NoTxn(QRCodeStatus status) -> {
                view.setThisStationCode(normalizeStationCode(status.getLastTxnStation()));
                view.setThisStationName(normalizeStationName(status.getLastTxnStation(), stationNames));
                view.setThisTransTime(status.getLastTxnTime());
                view.setTransSeq(status.getTxnSeq());
            }
            case Itinerary.Entry(QRCodeStatus ignored, QRCodeTxnDetail detail) -> {
                fillCommon(view, detail, stationNames);
                view.setLastStationCode(normalizeStationCode(detail.getLastHandleStationCode()));
                view.setLastStationName(normalizeStationName(detail.getLastHandleStationCode(), stationNames));
                view.setLastTransTime(detail.getLastHandleDateTime());
            }
            case Itinerary.Exit(QRCodeStatus ignored, QRCodeTxnDetail detail) -> {
                fillCommon(view, detail, stationNames);
                // 出站明细自带「上一笔（进站）」站码与时间（GateTicketHandler 出站时把 currentStatus.LAST_TXN_STATION
                // 写入 lastHandleStationCode），直接取用即拼出真实进站站，与 Entry 分支对称。
                // NEVER 按 TICKET_TRANS_SEQ 相等配对进出站——实测进站 seq=4 / 出站 seq=5，相等恒命中 0 行（mapper Javadoc 不可回退口径）。
                view.setLastStationCode(normalizeStationCode(detail.getLastHandleStationCode()));
                view.setLastStationName(normalizeStationName(detail.getLastHandleStationCode(), stationNames));
                view.setLastTransTime(detail.getLastHandleDateTime());
            }
        }
        return view;
    }

    /**
     * 未知站哨兵（含配置值）归一为空串，NEVER 把 `FFFF` 当站点编码吐给 APP（2026-11-16 业主裁决推翻 D166）。
     *
     * <p>判据统一走 {@link SupplementCodec#isUnknownStation}，避免裸写 `"FFFF"` 造成配置改一半漏判（审查项 C004）。
     */
    private String normalizeStationCode(String stationCode) {
        return SupplementCodec.isUnknownStation(stationCode, defaultLastTxnStation) ? "" : stationCode;
    }

    /** 站名同口径归一：哨兵返回空串，避免 `stationNameResolver` 查不到站名时把 `FFFF` 原样回填。 */
    private String normalizeStationName(String stationCode, Map<String, String> stationNames) {
        return SupplementCodec.isUnknownStation(stationCode, defaultLastTxnStation)
                ? "" : stationNameResolver.resolveNameOrCode(stationCode, stationNames);
    }

    /** 收集本次需要翻译成中文站名的站点编码。 */
    private Set<String> collectStationCodes(Itinerary itinerary) {
        Set<String> codes = new LinkedHashSet<>();
        switch (itinerary) {
            case Itinerary.NoTxn(QRCodeStatus status) -> addIfPresent(codes, status.getLastTxnStation());
            case Itinerary.Exit(QRCodeStatus ignored, QRCodeTxnDetail detail) -> {
                addIfPresent(codes, detail.getHandleStationCode());
                // 与 Entry 对称：Exit 的 last 取明细自带的进站站，需一并批量翻译站名。
                addIfPresent(codes, detail.getLastHandleStationCode());
            }
            case Itinerary.Entry(QRCodeStatus ignored, QRCodeTxnDetail detail) -> {
                addIfPresent(codes, detail.getHandleStationCode());
                addIfPresent(codes, detail.getLastHandleStationCode());
            }
        }
        return codes;
    }

    /** 进站与出站共用的本站字段与金额字段。 */
    private void fillCommon(MemberItineraryDTO view, QRCodeTxnDetail detail, Map<String, String> stationNames) {
        view.setThisStationCode(normalizeStationCode(detail.getHandleStationCode()));
        view.setThisStationName(normalizeStationName(detail.getHandleStationCode(), stationNames));
        view.setThisTransTime(detail.getHandleDateTime());
        view.setTransSeq(detail.getTicketTransSeq());
        view.setTransValue(toInteger(detail.getTrxAmount()));
        view.setOvertimeTransValue(toInteger(detail.getOvertimeAmount()));
        view.setPayChannel(detail.getSignChannelCode());
        view.setOriTicketAmt(toInteger(detail.getTrxAmount()));
        view.setDebitAmt(toInteger(defaultLong(detail.getTrxAmount()) + defaultLong(detail.getOvertimeAmount())));
        view.setOrderExpType(0);
        view.setDiscountInfo("");
        view.setCarbonDiscount(0);
    }

    private void addIfPresent(Set<String> codes, String stationCode) {
        if (StringUtils.hasText(stationCode)) {
            codes.add(stationCode);
        }
    }

    private Integer toInteger(Long value) {
        return value == null ? null : value.intValue();
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }
}
