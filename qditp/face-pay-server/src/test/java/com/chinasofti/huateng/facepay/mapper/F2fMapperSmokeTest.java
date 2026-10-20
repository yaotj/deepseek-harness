package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.FacePayServer;
import com.chinasofti.huateng.facepay.api.page.F2fDeviceStatusPageVO;
import com.chinasofti.huateng.facepay.api.page.F2fDeviceStatusSummaryVO;
import com.chinasofti.huateng.facepay.support.F2fChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MyBatis 绑定与 SQL 可执行性验证。 */
@SpringBootTest(classes = FacePayServer.class)
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
class F2fMapperSmokeTest {

    @Autowired
    private F2fOrderMapper orderMapper;

    @Autowired
    private F2fPaymentMapper paymentMapper;

    @Autowired
    private F2fResultReportMapper resultReportMapper;

    @Autowired
    private F2fTicketMapper ticketMapper;

    @Autowired
    private F2fRefundMapper refundMapper;

    @Autowired
    private F2fNotifyTaskMapper notifyTaskMapper;

    @Autowired
    private F2fDeviceStatusMapper deviceStatusMapper;

    private static final String ABSENT = "SMOKE_TEST_NOT_EXIST";

    private static final LocalDateTime NOW = LocalDateTime.now();

    @Test
    void allMappersAreBound() {
        assertNotNull(orderMapper);
        assertNotNull(paymentMapper);
        assertNotNull(resultReportMapper);
        assertNotNull(ticketMapper);
        assertNotNull(refundMapper);
        assertNotNull(notifyTaskMapper);
        assertNotNull(deviceStatusMapper);
    }

    @Test
    void orderQueriesExecute() {
        assertNull(orderMapper.selectByOrderNo(ABSENT));
        assertNull(orderMapper.selectByQrcode(ABSENT, "20260908120000", ABSENT));
        assertTrue(orderMapper.selectByUserAndActivateFlag(ABSENT, "0").isEmpty());
        assertNull(orderMapper.selectLatestByCardId(ABSENT, "02"));
        assertTrue(orderMapper.selectExpiredCandidates(NOW.minusHours(24), NOW, 10).isEmpty());
        assertTrue(orderMapper.selectPaidNotFulfilled(java.util.List.of("01"), NOW.minusDays(7), NOW, 10).isEmpty());
        assertTrue(orderMapper.countStaleExpired(NOW.minusHours(24)) == 0L);
    }

    @Test
    void paymentQueriesExecute() {
        assertNull(paymentMapper.selectLastAttempt(ABSENT));
        assertTrue(paymentMapper.selectByOrderNo(ABSENT).isEmpty());
        assertNull(paymentMapper.selectByPayCenterOrderNo(ABSENT));
        assertNull(paymentMapper.selectMaxAttemptNo(ABSENT));
    }

    @Test
    void resultReportQueriesExecute() {
        assertNull(resultReportMapper.selectByTypeAndOrderNo("TOPUP_OK", ABSENT));
        assertTrue(resultReportMapper.selectAllByOrderNo(ABSENT).isEmpty());
        assertTrue(resultReportMapper.selectByFaultSlipSeq(ABSENT).isEmpty());
        assertTrue(resultReportMapper.selectPendingReports(10, List.of("TOPUP_OK", "TAKE_TICKET"), NOW).isEmpty());
    }

    @Test
    void ticketQueriesExecute() {
        assertTrue(ticketMapper.selectByOrderNo(ABSENT).isEmpty());
        assertNull(ticketMapper.selectByLogicNumAndTransDate(ABSENT, "20260908120000"));
        assertNull(ticketMapper.selectLatestByLogicNum(ABSENT));
    }

    @Test
    void refundQueriesExecute() {
        assertNull(refundMapper.selectByRefundNo(ABSENT));
        assertTrue(refundMapper.selectByOrigOrderNo(ABSENT).isEmpty());
        assertNull(refundMapper.selectByPayCenterRefundNo(ABSENT));
        assertTrue(refundMapper.selectRetryCandidates(List.of("INIT", "PROCESSING"), NOW, 10).isEmpty());
    }

    @Test
    void notifyTaskQueriesExecute() {
        assertNull(notifyTaskMapper.selectById(-1L));
        assertNull(notifyTaskMapper.selectByBizKey("REFUND_RESULT", ABSENT, null));
        assertNull(notifyTaskMapper.selectByBizKey("REFUND_RESULT", ABSENT, ABSENT));
        assertTrue(notifyTaskMapper.selectDueTasks(NOW, 10).isEmpty());
    }

    @Test
    void deviceStatusQueriesExecute() {
        assertNull(deviceStatusMapper.selectByChannelAndDevice("02", ABSENT));
        assertTrue(deviceStatusMapper.selectHeartbeatTimeout(NOW, 10).isEmpty());
        assertTrue(deviceStatusMapper.selectByChannel("02", "1").isEmpty());
    }

    /**
     * 运营端「设备心跳状态」页的三个查询。
     *
     * <p>与上面那条不同，这里只断言「在空库和有数据的库上都成立」的事实（不可能命中的关键字返回空、
     * 汇总恒非空且自洽、取到的行字段都能映射上），因此可以在任何环境执行，不会因为库里恰好有设备而失败。
     */
    @Test
    void devicePageQueriesExecute() {
        F2fDeviceStatusSummaryVO summary = deviceStatusMapper.selectDeviceSummary();
        assertNotNull(summary);
        assertNotNull(summary.getTotal());
        assertEquals(summary.getTotal(), summary.getOnlineCount() + summary.getOfflineCount());

        assertTrue(deviceStatusMapper.selectDevicePage(null, null, ABSENT, 0, 10).isEmpty());
        assertTrue(deviceStatusMapper.selectDevicePage("02", "1", ABSENT, 0, 10).isEmpty());
        assertEquals(0L, deviceStatusMapper.countDevicePage(null, null, ABSENT));

        List<F2fDeviceStatusPageVO> firstPage = deviceStatusMapper.selectDevicePage(null, null, null, 0, 5);
        assertTrue(firstPage.size() <= 5, "单页条数不能超过 limit");
        assertTrue(summary.getTotal() >= firstPage.size(), "汇总总数不会小于单页行数");
        assertEquals(deviceStatusMapper.countDevicePage(null, null, null), summary.getTotal(),
                "无条件过滤时 countDevicePage 应与汇总总数一致");

        String previousFlag = null;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (F2fDeviceStatusPageVO row : firstPage) {
            assertTrue(seen.add(row.getChannel() + '#' + row.getDeviceId()),
                    "同一页出现重复设备，说明 STATION_INFO 的 LEFT JOIN 放大了行数");
            assertTrue(F2fChannel.isSupported(row.getChannel()), "渠道码必须能映射: " + row.getChannel());
            assertNotNull(row.getDeviceId(), "设备号不能为空");
            assertTrue("0".equals(row.getOnlineFlag()) || "1".equals(row.getOnlineFlag()),
                    "在线标志只能是 0/1，取到 " + row.getOnlineFlag() + " 说明列映射错了");
            assertNotNull(row.getLastHeartbeatTms(), "最近心跳时间不能为空");
            assertNotNull(row.getIdleSeconds(), "距上次心跳秒数不能为空");
            assertNotNull(row.getHeartbeatCount(), "累计心跳次数不能为空");
            if (previousFlag != null) {
                assertTrue(previousFlag.compareTo(row.getOnlineFlag()) <= 0,
                        "排序 MUST 是离线优先：0 不能排在 1 之后");
            }
            previousFlag = row.getOnlineFlag();
        }
    }
}
