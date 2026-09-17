package com.chinasofti.huateng.gatetxnpay.station;

import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.fare.FareDataGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 按落库前的最终 {@code IN_STATION} / {@code OUT_STATION} 回填两个中文站名列。 */
@Component
public class StationNameBackfiller {

    private static final Logger log = LoggerFactory.getLogger(StationNameBackfiller.class);

    private final FareDataGateway fareDataGateway;

    public StationNameBackfiller(FareDataGateway fareDataGateway) {
        this.fareDataGateway = fareDataGateway;
    }

    /** 按订单当前的进出站编码回填站名，查不到的那一列保持原值。 */
    public void backfill(GateTxnPay order) {
        if (order == null) {
            return;
        }
        Set<String> stationCodes = new LinkedHashSet<>();
        if (StringUtils.hasText(order.getInStation())) {
            stationCodes.add(order.getInStation());
        }
        if (StringUtils.hasText(order.getOutStation())) {
            stationCodes.add(order.getOutStation());
        }
        if (stationCodes.isEmpty()) {
            return;
        }
        Map<String, String> stationNames = fareDataGateway.resolveStationNamesQuietly(stationCodes);
        if (stationNames.isEmpty()) {
            log.warn("站名回填未命中任何站码，保留上游站名, orderNo={}, inStation={}, outStation={}",
                    order.getOrderNo(), order.getInStation(), order.getOutStation());
            return;
        }
        String entryStationName = stationNames.get(order.getInStation());
        if (StringUtils.hasText(entryStationName)) {
            order.setEntryStationName(entryStationName);
        }
        String exitStationName = stationNames.get(order.getOutStation());
        if (StringUtils.hasText(exitStationName)) {
            order.setExitStationName(exitStationName);
        }
        log.info("站名回填完成, orderNo={}, inStation={}, entryStationName={}, outStation={}, exitStationName={}",
                order.getOrderNo(), order.getInStation(), order.getEntryStationName(),
                order.getOutStation(), order.getExitStationName());
    }
}
