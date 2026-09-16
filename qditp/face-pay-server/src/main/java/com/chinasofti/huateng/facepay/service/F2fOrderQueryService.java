package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.api.page.FacePayOrderPageVO;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 运营端当面付订单的<b>只读</b>查询。本类不做任何写操作，也不组装响应壳。
 *
 * <p>抽出本类的原因：{@code FacePayOrderPageController} 原先直接注入
 * {@code F2fOrderMapper} 并在 Controller 里拼 11 个入参、算 offset、判检索范围
 * —— 违反「Controller 只做参数校验与路由」（AGENTS.md §3.3）。
 *
 * <p><b>本类返回 {@link PageOutcome} 而不是 {@code ResultVO}</b>：运营端那组接口的
 * 响应壳是 {@code code/msg/data}、由 {@code ResultMapper} 组装，属 Controller 职责；
 * 与 {@link F2fPayCenterFlow} 返回判定结果、由调用方各自组壳是同一个模式。
 * 好处是调用方 {@code switch} 上少写一个分支直接编译失败。
 *
 * <p><b>检索范围必填这条规则 MUST 留在本类</b>（不是 Controller）：{@code F2F_ORDER}
 * 是按月分区的核心交易表，无条件全扫会直接影响设备链路。规则本身与旧服务逐字一致：
 * 订单号、支付中心订单号、渠道订单号，三者任一，或完整的下单时间范围。
 */
@Service
public class F2fOrderQueryService {

    private static final Logger log = LoggerFactory.getLogger(F2fOrderQueryService.class);

    private static final int DEFAULT_PAGE_SIZE = 10;

    private static final int MAX_PAGE_SIZE = 100;

    private final F2fOrderMapper orderMapper;

    public F2fOrderQueryService(F2fOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }
    /**
     * 分页查询入参。<b>11 个检索维度逐个保留，NEVER 精简</b>。
     *
     * <p>其中 {@code payCenterChannelOrderNo}（渠道订单号）是 2026-09-11 新旧双打补回来的
     * 一维：旧 {@code /page/face-pay/orders} 一直支持它，而重写后的新服务连这个入参都没有，
     * 运营后台按渠道订单号查不到单。NEVER 再把它去掉。</p>
     *
     * <p>{@code pageNum} / {@code pageSize} 允许传 null，在 {@link #page} 内归一化。</p>
     */
    public record OrderPageQuery(String orderNo,
                                 String payCenterOrderNo,
                                 String payCenterChannelOrderNo,
                                 String deviceId,
                                 String channel,
                                 String bizType,
                                 String orderStatus,
                                 LocalDateTime beginTms,
                                 LocalDateTime endTms,
                                 Integer pageNum,
                                 Integer pageSize) {

        /** 三个订单标识任一有值，或下单时间范围完整，才算给了检索范围。 */
        private boolean hasSearchScope() {
            return trimToNull(orderNo) != null
                    || trimToNull(payCenterOrderNo) != null
                    || trimToNull(payCenterChannelOrderNo) != null
                    || (beginTms != null && endTms != null);
        }
    }

    /** 查询判定结果，调用方 MUST 穷尽分支后各自组响应壳。 */
    public sealed interface PageOutcome {

        /** 查询成功。{@code total} 是符合条件的总行数，不是本页行数。 */
        record Ok(List<FacePayOrderPageVO> list, long total) implements PageOutcome {
        }

        /**
         * 入参不满足检索范围要求，<b>没有查库</b>。
         *
         * @param reason 直接回给运营端的原因，措辞与旧服务逐字一致
         */
        record Rejected(String reason) implements PageOutcome {
        }
    }

    /**
     * 分页查询。<b>只读，无事务</b>。
     *
     * <p>入参里的时间已由调用方按 {@code yyyy-MM-dd HH:mm:ss} 解析完（解析失败属报文格式
     * 问题、由 Controller 直接拒绝），本方法只做业务层面的范围校验。</p>
     */
    public PageOutcome page(OrderPageQuery query) {
        if (!query.hasSearchScope()) {
            return new PageOutcome.Rejected("请填写订单号、支付中心订单号、渠道订单号，或完整的下单时间范围");
        }
        if (query.beginTms() != null && query.endTms() != null
                && query.beginTms().isAfter(query.endTms())) {
            return new PageOutcome.Rejected("下单时间范围的开始时间不能晚于结束时间");
        }

        int currentPage = query.pageNum() == null || query.pageNum() < 1 ? 1 : query.pageNum();
        int currentPageSize = query.pageSize() == null || query.pageSize() < 1
                ? DEFAULT_PAGE_SIZE : Math.min(query.pageSize(), MAX_PAGE_SIZE);
        int offset = (currentPage - 1) * currentPageSize;

        String orderNo = trimToNull(query.orderNo());
        String payCenterOrderNo = trimToNull(query.payCenterOrderNo());
        String payCenterChannelOrderNo = trimToNull(query.payCenterChannelOrderNo());
        String deviceId = trimToNull(query.deviceId());
        String channel = trimToNull(query.channel());
        String bizType = trimToNull(query.bizType());
        String orderStatus = trimToNull(query.orderStatus());

        List<FacePayOrderPageVO> list = orderMapper.selectPageView(orderNo, payCenterOrderNo,
                payCenterChannelOrderNo, deviceId, channel, bizType, orderStatus,
                query.beginTms(), query.endTms(), offset, currentPageSize);
        long total = orderMapper.countPage(orderNo, payCenterOrderNo, payCenterChannelOrderNo,
                deviceId, channel, bizType, orderStatus, query.beginTms(), query.endTms());
        log.info("运营端当面付订单查询完成, pageNum={}, pageSize={}, size={}, total={}",
                currentPage, currentPageSize, list == null ? 0 : list.size(), total);
        return new PageOutcome.Ok(list, total);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
