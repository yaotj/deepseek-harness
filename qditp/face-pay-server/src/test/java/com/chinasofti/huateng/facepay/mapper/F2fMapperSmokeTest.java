package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.FacePayServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MyBatis 绑定与 SQL 可执行性验证。<b>需要真实 Oracle</b>，因此靠 {@code DB_HOST} 环境变量开关，
 * 未设置时整类跳过，不会拖垮无库环境的 {@code mvn test}。
 *
 * <h2>它验证的是编译期与静态检查都看不到的三类错误</h2>
 * <ol>
 *   <li>接口方法名与 XML {@code statement id} 不一致 —— 启动即 {@code BindingException}；</li>
 *   <li>{@code resultMap} 的 {@code type} 写错、{@code jdbcType} 非法 —— 启动即报；</li>
 *   <li>SQL 本身语法或列名错误、以及 <b>Druid WallFilter 因注释判定注入</b> —— 只在真正执行时暴露。
 *       第三类是本项目的高频事故形态（AGENTS.md §5.1），所以这里<b>逐条真的执行一次</b>，
 *       而不是只启动上下文。</li>
 * </ol>
 *
 * <p>全部只读，零写入：新建的 7 张表此刻为空，所有查询都应返回 {@code null} 或空集合。
 * 写路径（{@code insert} / {@code update}）留到 TVM 下单纵向切片时连业务一起验，
 * 那时才有明确的清理边界。</p>
 *
 * <p>运行方式：{@code DB_HOST=host:port DB_DATABASE=.. DB_USERNAME=.. DB_PASSWORD=..
 * mise exec -- mvn test -pl face-pay-server}</p>
 */
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
        assertTrue(orderMapper.selectPaidNotFulfilled(NOW, 10).isEmpty());
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
}
