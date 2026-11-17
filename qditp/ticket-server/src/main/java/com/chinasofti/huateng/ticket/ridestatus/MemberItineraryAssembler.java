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
                view.setLastStationCode(normalizeStationCode(detail.getLastHandleStationCode()));
                view.setLastStationName(normalizeStationName(detail.getLastHandleStationCode(), stationNames));
                view.setLastTransTime(detail.getLastHandleDateTime());
            }
        }
        return view;
    }


    private String normalizeStationCode(String stationCode) {
        return SupplementCodec.isUnknownStation(stationCode, defaultLastTxnStation) ? "" : stationCode;
    }

    private String normalizeStationName(String stationCode, Map<String, String> stationNames) {
        return SupplementCodec.isUnknownStation(stationCode, defaultLastTxnStation)
                ? "" : stationNameResolver.resolveNameOrCode(stationCode, stationNames);
    }

    private Set<String> collectStationCodes(Itinerary itinerary) {
        Set<String> codes = new LinkedHashSet<>();
        switch (itinerary) {
            case Itinerary.NoTxn(QRCodeStatus status) -> addIfPresent(codes, status.getLastTxnStation());
            case Itinerary.Exit(QRCodeStatus ignored, QRCodeTxnDetail detail) -> {
                addIfPresent(codes, detail.getHandleStationCode());
                addIfPresent(codes, detail.getLastHandleStationCode());
            }
            case Itinerary.Entry(QRCodeStatus ignored, QRCodeTxnDetail detail) -> {
                addIfPresent(codes, detail.getHandleStationCode());
                addIfPresent(codes, detail.getLastHandleStationCode());
            }
        }
        return codes;
    }

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
