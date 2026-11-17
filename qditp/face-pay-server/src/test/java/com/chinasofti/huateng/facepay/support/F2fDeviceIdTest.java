package com.chinasofti.huateng.facepay.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


class F2fDeviceIdTest {

    @Test
    void takesLeadingFourCharactersAsStationCode() {
        assertEquals("0622", F2fDeviceId.stationCode("06220701"));
        assertEquals("0622", F2fDeviceId.stationCode("06220801"));
        assertEquals("0220", F2fDeviceId.stationCode("02201101"));
        assertEquals("0244", F2fDeviceId.stationCode("02441101"));
        assertEquals("0352", F2fDeviceId.stationCode("03520801"));
        assertEquals("1128", F2fDeviceId.stationCode("11280801"));
    }

    @Test
    void returnsNullWhenDeviceIdIsMissingOrTooShort() {
        assertNull(F2fDeviceId.stationCode(null));
        assertNull(F2fDeviceId.stationCode(""));
        assertNull(F2fDeviceId.stationCode("062"));
    }

    @Test
    void fourCharacterDeviceIdIsStillDerivable() {
        assertEquals("0622", F2fDeviceId.stationCode("0622"));
    }

    @Test
    void neverTrimsSoStationCodeStaysSameOriginAsDeviceId() {
        assertEquals(" 062", F2fDeviceId.stationCode(" 06220701"));
        assertEquals("0622", F2fDeviceId.stationCode("06220701 "));
    }
}
