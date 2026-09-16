package com.chinasofti.huateng.gatetxnpay.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.fare.FareDataGateway;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 钉住 {@link StationNameBackfiller} 的三条语义，它们改坏后编译与启动都不报错、
 * 只在「列表站名显示成编码」或「站名被擦成空」时才被发现。
 *
 * <p>不用 Mockito：{@link FareDataGateway} 的取数方法已经是「吞异常返空 Map」的形态，
 * 直接匿名子类覆写那一个方法即可，构造参数传 {@code null} 不会被触达。</p>
 */
class StationNameBackfillerTest {

    /** 两个码都查得到：两列都按查到的中文名覆盖。 */
    @Test
    void backfillBothStationNames() {
        Map<String, String> resolved = new HashMap<>();
        resolved.put("0622", "辛屯");
        resolved.put("0245", "合川路");
        GateTxnPay order = order("0622", "0245", null, null);

        backfillerReturning(resolved).backfill(order);

        assertEquals("辛屯", order.getEntryStationName());
        assertEquals("合川路", order.getExitStationName());
    }

    /**
     * 进站码查不到（占位 {@code FFFF} 不在 {@code TBL_STATION_INFO}）时，
     * 进站名保持原值、**出站名照常回填** —— NEVER 因为进站查不到就整体放弃。
     */
    @Test
    void keepEntryNameWhenEntryCodeUnresolvable() {
        Map<String, String> resolved = new HashMap<>();
        resolved.put("0245", "合川路");
        GateTxnPay order = order("FFFF", "0245", null, null);

        backfillerReturning(resolved).backfill(order);

        assertNull(order.getEntryStationName());
        assertEquals("合川路", order.getExitStationName());
    }

    /**
     * 一个都查不到（para 不可达 / 返非 0000）时，**NEVER 把上游已填对的站名擦成空**。
     * 这条是本类最关键的不变量：覆盖成空比不回填更糟，列表会退回显示编码。
     */
    @Test
    void neverOverwriteExistingNamesWhenNothingResolved() {
        GateTxnPay order = order("0622", "0245", "辛屯", "合川路");

        backfillerReturning(new HashMap<>()).backfill(order);

        assertEquals("辛屯", order.getEntryStationName());
        assertEquals("合川路", order.getExitStationName());
    }

    /** 进出站编码都为空时直接短路，不发 RPC（用抛异常的桩证明没被调用）。 */
    @Test
    void skipWhenBothStationCodesBlank() {
        FareDataGateway exploding = new FareDataGateway(null, null, null, null, null, null) {
            @Override
            public Map<String, String> resolveStationNamesQuietly(Set<String> stationCodes) {
                throw new AssertionError("编码全空时 NEVER 发起站名查询");
            }
        };
        GateTxnPay order = order(null, "", null, null);

        new StationNameBackfiller(exploding).backfill(order);

        assertNull(order.getEntryStationName());
        assertNull(order.getExitStationName());
    }

    /** 进出同站：MUST 能正常回填，且去重后只查一个码。 */
    @Test
    void backfillSameEntryAndExitStation() {
        Map<String, String> resolved = new HashMap<>();
        resolved.put("0622", "辛屯");
        Set<String> asked = new LinkedHashSet<>();
        FareDataGateway recording = new FareDataGateway(null, null, null, null, null, null) {
            @Override
            public Map<String, String> resolveStationNamesQuietly(Set<String> stationCodes) {
                asked.addAll(stationCodes);
                return resolved;
            }
        };
        GateTxnPay order = order("0622", "0622", null, null);

        new StationNameBackfiller(recording).backfill(order);

        assertEquals(1, asked.size());
        assertEquals("辛屯", order.getEntryStationName());
        assertEquals("辛屯", order.getExitStationName());
    }

    private StationNameBackfiller backfillerReturning(Map<String, String> resolved) {
        return new StationNameBackfiller(new FareDataGateway(null, null, null, null, null, null) {
            @Override
            public Map<String, String> resolveStationNamesQuietly(Set<String> stationCodes) {
                return resolved;
            }
        });
    }

    private GateTxnPay order(String inStation, String outStation,
                             String entryStationName, String exitStationName) {
        GateTxnPay order = new GateTxnPay();
        order.setOrderNo("GTTEST0000000000000000001");
        order.setInStation(inStation);
        order.setOutStation(outStation);
        order.setEntryStationName(entryStationName);
        order.setExitStationName(exitStationName);
        return order;
    }
}
