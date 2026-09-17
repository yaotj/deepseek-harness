package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.api.page.FacePayOrderPageVO;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 运营端当面付订单的只读查询。 */
@Service
public class F2fOrderQueryService {

    private static final Logger log = LoggerFactory.getLogger(F2fOrderQueryService.class);

    private static final int DEFAULT_PAGE_SIZE = 10;

    private static final int MAX_PAGE_SIZE = 100;

    private final F2fOrderMapper orderMapper;

    public F2fOrderQueryService(F2fOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }
    /** 分页查询入参。 */
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

        /** 查询成功。 */
        record Ok(List<FacePayOrderPageVO> list, long total) implements PageOutcome {
        }

        /**
         * 入参不满足检索范围要求，没有查库。
         *
         * @param reason 直接回给运营端的原因，措辞与旧服务逐字一致
         */
        record Rejected(String reason) implements PageOutcome {
        }
    }

    /** 分页查询。 */
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
