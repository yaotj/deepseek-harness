package com.chinasofti.huateng.ticket.ridestatus;

import com.chinasofti.huateng.model.app.MemberItineraryDTO;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import com.chinasofti.huateng.ticket.station.StationNameResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * IF8A-29 行程视图装配器。
 *
 * <p>只做「实体 -> 展示 DTO」的字段搬运与默认值填充，NEVER 在此查库或改状态；
 * 数据获取留在 {@link TicketRideStatusServiceImpl}。与 {@code query} 包的
 * {@code TransRecordAssembler} 同一职责定位。
 *
 * <p>三态分支一律经 {@link Itinerary} 的穷尽 {@code switch}，**NEVER 在本类里
 * 重新写 {@code detail == null} 或 {@code isExitTxn} 判断** —— 那两个判定曾在本类
 * 三个方法里重复五次，改一处漏一处会造成「站名查了 A、字段填了 B」。
 *
 * <p>站名解析统一走 {@link StationNameResolver}，**NEVER 在本包再写一份批量查站名**。
 */
@Component
class MemberItineraryAssembler {

    private static final String PAY_STATUS_DEFAULT = "00";

    private final StationNameResolver stationNameResolver;

    public MemberItineraryAssembler(StationNameResolver stationNameResolver) {
        this.stationNameResolver = stationNameResolver;
    }

    /**
     * 装配用户上次行程视图。
     *
     * @param currentStatus 乘车码当前状态，非空
     * @param latestDetail  最近一次过闸明细，可为空（表示尚无过闸记录）
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
                view.setThisStationCode(status.getLastTxnStation());
                view.setThisStationName(stationNameResolver.resolveNameOrCode(status.getLastTxnStation(), stationNames));
                view.setThisTransTime(status.getLastTxnTime());
                view.setTransSeq(status.getTxnSeq());
            }
            case Itinerary.Entry(QRCodeStatus ignored, QRCodeTxnDetail detail) -> {
                fillCommon(view, detail, stationNames);
                view.setLastStationCode(detail.getLastHandleStationCode());
                view.setLastStationName(stationNameResolver.resolveNameOrCode(detail.getLastHandleStationCode(), stationNames));
                view.setLastTransTime(detail.getLastHandleDateTime());
            }
            case Itinerary.Exit(QRCodeStatus ignored, QRCodeTxnDetail detail) -> {
                fillCommon(view, detail, stationNames);
                view.setLastStationCode(detail.getHandleStationCode());
                view.setLastStationName(stationNameResolver.resolveNameOrCode(detail.getHandleStationCode(), stationNames));
                view.setLastTransTime(detail.getHandleDateTime());
            }
        }
        return view;
    }

    /**
     * 收集本次需要翻译成中文站名的站点编码。
     *
     * <p>出站交易的「上一站」就是本站编码（与 {@code assemble} 的赋值口径一致），
     * 因此只有进站交易才需要额外带上 {@code LAST_HANDLE_STATION_CODE}。
     */
    private Set<String> collectStationCodes(Itinerary itinerary) {
        Set<String> codes = new LinkedHashSet<>();
        switch (itinerary) {
            case Itinerary.NoTxn(QRCodeStatus status) -> addIfPresent(codes, status.getLastTxnStation());
            case Itinerary.Exit(QRCodeStatus ignored, QRCodeTxnDetail detail) ->
                    addIfPresent(codes, detail.getHandleStationCode());
            case Itinerary.Entry(QRCodeStatus ignored, QRCodeTxnDetail detail) -> {
                addIfPresent(codes, detail.getHandleStationCode());
                addIfPresent(codes, detail.getLastHandleStationCode());
            }
        }
        return codes;
    }

    /** 进站与出站共用的本站字段与金额字段。 */
    private void fillCommon(MemberItineraryDTO view, QRCodeTxnDetail detail, Map<String, String> stationNames) {
        view.setThisStationCode(detail.getHandleStationCode());
        view.setThisStationName(stationNameResolver.resolveNameOrCode(detail.getHandleStationCode(), stationNames));
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
