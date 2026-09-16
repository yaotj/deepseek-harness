package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IF8A-04 APP 自助补站落 80 / 81 的判据。
 *
 * <p>钉住三件事：①{@code excessFareType=01/02} 分别落 81 / 80，<b>不再退化成与真实过闸同形的
 * 04 / 05</b>；②{@code adviceOpt}（BOM 单边处理）优先级高于 {@code excessFareType}；
 * ③{@code excessFareType=03/04} 按用户 2026-09-14 裁决暂不映射，仍回落 trxType 落 06 / FF。</p>
 */
class SelfServiceSupplementCodeStatusTest {

    private final GateCodeStatusResolver handler = new GateCodeStatusResolver();

    @Test
    void selfServiceEntryLands81() {
        assertEquals(QRCodeStatusEnum.SELF_SERVICE_ENTRY.getCode(),
                handler.resolveCodeStatus("01", "01", null));
    }

    @Test
    void selfServiceExitLands80() {
        assertEquals(QRCodeStatusEnum.SELF_SERVICE_EXIT.getCode(),
                handler.resolveCodeStatus("02", "02", null));
    }

    @Test
    void adviceOptWinsOverExcessFareType() {
        // BOM 单边处理与 APP 自助补站互斥，同时出现时以 adviceOpt 为准（006 付费更新 -> 09）
        assertEquals(QRCodeStatusEnum.UPDATE_PAY.getCode(),
                handler.resolveCodeStatus("02", "02", "006"));
    }

    @Test
    void undecidedExcessFareTypesFallBackToTrxType() {
        assertEquals(QRCodeStatusEnum.EXIT_OVERTIME.getCode(),
                handler.resolveCodeStatus("03", "03", null));
        assertEquals(QRCodeStatusEnum.ENTRY_FAIL.getCode(),
                handler.resolveCodeStatus("04", "04", null));
    }

    @Test
    void realGateTxnStillLands04And05() {
        // 真实闸机报文不带 excessFareType，口径必须一字不动
        assertEquals(QRCodeStatusEnum.ENTRY.getCode(), handler.resolveCodeStatus("01", null, null));
        assertEquals(QRCodeStatusEnum.EXIT.getCode(), handler.resolveCodeStatus("02", null, null));
        assertEquals(QRCodeStatusEnum.ENTRY.getCode(), handler.resolveCodeStatus("01", "", null));
    }

    @Test
    void whitelistCoversNewlyReachableEdges() {
        // 补进站的前置状态来自 ExcessFareHandler.resolveAllowedTypes 里允许 "01" 的那些状态
        assertTrue(QRCodeStatusEnum.EXIT.canTransitTo(QRCodeStatusEnum.SELF_SERVICE_ENTRY));
        assertTrue(QRCodeStatusEnum.EXIT_OVERTIME.canTransitTo(QRCodeStatusEnum.SELF_SERVICE_ENTRY));
        assertTrue(QRCodeStatusEnum.END_TRIP.canTransitTo(QRCodeStatusEnum.SELF_SERVICE_ENTRY));
        assertTrue(QRCodeStatusEnum.UPDATE_FREE.canTransitTo(QRCodeStatusEnum.SELF_SERVICE_ENTRY));
        assertTrue(QRCodeStatusEnum.UPDATE_PAY.canTransitTo(QRCodeStatusEnum.SELF_SERVICE_ENTRY));
        assertTrue(QRCodeStatusEnum.SELF_SERVICE_EXIT.canTransitTo(QRCodeStatusEnum.SELF_SERVICE_ENTRY));
        assertTrue(QRCodeStatusEnum.SJT_ISSUE.canTransitTo(QRCodeStatusEnum.SELF_SERVICE_ENTRY));
        // 81 状态下允许补出站 / 超时出站 / 进站失败
        assertTrue(QRCodeStatusEnum.SELF_SERVICE_ENTRY.canTransitTo(QRCodeStatusEnum.SELF_SERVICE_EXIT));
        assertTrue(QRCodeStatusEnum.SELF_SERVICE_ENTRY.canTransitTo(QRCodeStatusEnum.EXIT_OVERTIME));
        assertTrue(QRCodeStatusEnum.SELF_SERVICE_ENTRY.canTransitTo(QRCodeStatusEnum.ENTRY_FAIL));
        // 04 已进站补出站落 80，本来就在白名单里
        assertTrue(QRCodeStatusEnum.ENTRY.canTransitTo(QRCodeStatusEnum.SELF_SERVICE_EXIT));
    }
}
