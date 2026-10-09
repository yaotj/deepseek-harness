package com.chinasofti.huateng.dailyticket.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketRefundMapper;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundOrderQuery;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundOrderView;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundQuery;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundView;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住从 {@code DailyTicketServiceImpl} 拆出来的三个只读查询的行为：
 * 分页参数钳制（1 / 10 兜底、上限 100）、null 入参兜底、空主单号拒绝、mapper 结果原样透传。
 *
 * <p>钳制值在 mapper 的 answer 里经 {@link PageHelper#getLocalPage()} 读取真实生效的 Page，
 * 而不是断言私有方法，因此拆分前后的等价性是按「实际下发给分页插件的参数」比对的。
 */
class DailyTicketRefundQueryServiceTest {

    private DailyTicketOrderMapper orderMapper;
    private DailyTicketRefundMapper refundMapper;
    private DailyTicketRefundQueryService service;

    @BeforeEach
    void setUp() {
        orderMapper = mock(DailyTicketOrderMapper.class);
        refundMapper = mock(DailyTicketRefundMapper.class);
        service = new DailyTicketRefundQueryService(orderMapper, refundMapper);
    }

    @AfterEach
    void tearDown() {
        PageHelper.clearPage();
    }

    @Test
    void pageRefundOrders_nullQuery_usesDefaultPageAndEmptyQuery() {
        AtomicReference<Page<Object>> effective = new AtomicReference<>();
        ArgumentCaptor<DailyTicketRefundOrderQuery> captor =
                ArgumentCaptor.forClass(DailyTicketRefundOrderQuery.class);
        when(orderMapper.selectRefundOrders(captor.capture())).thenAnswer(invocation -> {
            effective.set(PageHelper.getLocalPage());
            return new ArrayList<DailyTicketRefundOrderView>();
        });

        ResultVO<?> result = service.pageRefundOrders(null);

        assertEquals(ResultVO.SUCCESS_CODE, result.getCode());
        assertNotNull(result.getData());
        assertNotNull(captor.getValue(), "null 入参 MUST 兜底成空查询对象，不能把 null 传给 mapper");
        assertEquals(1, effective.get().getPageNum());
        assertEquals(10, effective.get().getPageSize());
    }

    @Test
    void pageRefundOrders_outOfRangePage_isClamped() {
        AtomicReference<Page<Object>> effective = new AtomicReference<>();
        when(orderMapper.selectRefundOrders(any())).thenAnswer(invocation -> {
            effective.set(PageHelper.getLocalPage());
            return new ArrayList<DailyTicketRefundOrderView>();
        });
        DailyTicketRefundOrderQuery query = new DailyTicketRefundOrderQuery();
        query.setPageNum(0);
        query.setPageSize(999);

        service.pageRefundOrders(query);

        assertEquals(1, effective.get().getPageNum());
        assertEquals(100, effective.get().getPageSize());
    }

    @Test
    void pageRefundRecords_keepsInRangePageAsIs() {
        AtomicReference<Page<Object>> effective = new AtomicReference<>();
        when(refundMapper.selectRefunds(any())).thenAnswer(invocation -> {
            effective.set(PageHelper.getLocalPage());
            return new ArrayList<DailyTicketRefundView>();
        });
        DailyTicketRefundQuery query = new DailyTicketRefundQuery();
        query.setPageNum(3);
        query.setPageSize(20);

        ResultVO<?> result = service.pageRefundRecords(query);

        assertEquals(ResultVO.SUCCESS_CODE, result.getCode());
        assertEquals(3, effective.get().getPageNum());
        assertEquals(20, effective.get().getPageSize());
    }

    @Test
    void listTravelSubRefundOrders_blankParentOrderNo_rejectedWithoutQuery() {
        ResultVO<List<DailyTicketRefundOrderView>> result = service.listTravelSubRefundOrders("  ");

        assertEquals(ResultVO.ILLEGAL_PARAMS_CODE, result.getCode());
        assertEquals("旅游票主单号不能为空", result.getMsg());
        verify(orderMapper, never()).selectTravelSubRefundOrders(any());
    }

    @Test
    void listTravelSubRefundOrders_returnsMapperResultAsIs() {
        List<DailyTicketRefundOrderView> rows = new ArrayList<>();
        rows.add(new DailyTicketRefundOrderView());
        when(orderMapper.selectTravelSubRefundOrders(eq("0T202609200001"))).thenReturn(rows);

        ResultVO<List<DailyTicketRefundOrderView>> result = service.listTravelSubRefundOrders("0T202609200001");

        assertEquals(ResultVO.SUCCESS_CODE, result.getCode());
        assertSame(rows, result.getData());
    }
}
