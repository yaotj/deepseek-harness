package com.chinasofti.huateng.facepay.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 锁死「设备号 → 站码」的解析口径。
 *
 * <p>这条规则有三处需求（心跳落库、BOM 票卡分析同站判定、运营端设备页），
 * 一旦实现分叉就会出现「同一个设备在页面显示的站与判费算的站不是一个」，故逐条钉住。
 */
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

    /** 推不出来就返回 null，让调用方各自决定表示法（心跳留空 / RPC 转空串）。 */
    @Test
    void returnsNullWhenDeviceIdIsMissingOrTooShort() {
        assertNull(F2fDeviceId.stationCode(null));
        assertNull(F2fDeviceId.stationCode(""));
        assertNull(F2fDeviceId.stationCode("062"));
    }

    /** 恰好 4 位是能推导的边界：站码就是它自己（与 TvmResponses 的 {@code <= 4} 判据刻意不同，见类注释）。 */
    @Test
    void fourCharacterDeviceIdIsStillDerivable() {
        assertEquals("0622", F2fDeviceId.stationCode("0622"));
    }

    /** 刻意不 trim：设备号本身就是主键原值，规整会让「行键」与「站码」不同源。 */
    @Test
    void neverTrimsSoStationCodeStaysSameOriginAsDeviceId() {
        assertEquals(" 062", F2fDeviceId.stationCode(" 06220701"));
        assertEquals("0622", F2fDeviceId.stationCode("06220701 "));
    }
}
