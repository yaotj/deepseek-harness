package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** IF8A-04 APP 自助补站落 80 / 81 的判据。 */
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
        assertEquals(QRCodeStatusEnum.ENTRY.getCode(), handler.resolveCodeStatus("01", null, null));
        assertEquals(QRCodeStatusEnum.EXIT.getCode(), handler.resolveCodeStatus("02", null, null));
        assertEquals(QRCodeStatusEnum.ENTRY.getCode(), handler.resolveCodeStatus("01", "", null));
    }

    @Test
    void whitelistCoversNewlyReachableEdges() {
        assertTrue(QRCodeStatusEnum.EXIT.canTransitTo(QRCodeStatusEnum.SELF_SERVICE_ENTRY));
        assertTrue(QRCodeStatusEnum.EXIT_OVERTIME.canTransitTo(QRCodeStatusEnum.SELF_SERVICE_ENTRY));
        assertTrue(QRCodeStatusEnum.END_TRIP.canTransitTo(QRCodeStatusEnum.SELF_SERVICE_ENTRY));
        assertTrue(QRCodeStatusEnum.UPDATE_FREE.canTransitTo(QRCodeStatusEnum.SELF_SERVICE_ENTRY));
        assertTrue(QRCodeStatusEnum.UPDATE_PAY.canTransitTo(QRCodeStatusEnum.SELF_SERVICE_ENTRY));
        assertTrue(QRCodeStatusEnum.SELF_SERVICE_EXIT.canTransitTo(QRCodeStatusEnum.SELF_SERVICE_ENTRY));
        assertTrue(QRCodeStatusEnum.SJT_ISSUE.canTransitTo(QRCodeStatusEnum.SELF_SERVICE_ENTRY));
        assertTrue(QRCodeStatusEnum.SELF_SERVICE_ENTRY.canTransitTo(QRCodeStatusEnum.SELF_SERVICE_EXIT));
        assertTrue(QRCodeStatusEnum.SELF_SERVICE_ENTRY.canTransitTo(QRCodeStatusEnum.EXIT_OVERTIME));
        assertTrue(QRCodeStatusEnum.SELF_SERVICE_ENTRY.canTransitTo(QRCodeStatusEnum.ENTRY_FAIL));
        assertTrue(QRCodeStatusEnum.ENTRY.canTransitTo(QRCodeStatusEnum.SELF_SERVICE_EXIT));
    }
}
