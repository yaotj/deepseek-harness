package com.chinasofti.huateng.collectpay.controller.page;

import com.chinasofti.huateng.collectpay.mapper.TvmTopupOrderMapper;
import com.chinasofti.huateng.collectpay.model.page.TvmTopupOrderPageView;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 运营端 TVM 充值订单查询（对标旧 ITP「TVM充值订单管理」，只读）。 */
@RestController
@RequestMapping("/page/tvm/topup-orders")
public class TvmTopupOrderPageController {
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final TvmTopupOrderMapper tvmTopupOrderMapper;

    public TvmTopupOrderPageController(TvmTopupOrderMapper tvmTopupOrderMapper) {
        this.tvmTopupOrderMapper = tvmTopupOrderMapper;
    }

    /** 分页查询 TVM 充值订单（带最新充值回执态）；要求订单标识或完整下单时间范围以保护数据库。 */
    @GetMapping
    public ResultVO<Map<String, Object>> page(@RequestParam(required = false) String orderNo,
                                              @RequestParam(required = false) String payCenterOrderNo,
                                              @RequestParam(required = false) String deviceId,
                                              @RequestParam(required = false) String ticketLogicNum,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String channel,
                                              @RequestParam(required = false) String topupStatus,
                                              @RequestParam(required = false) String beginTime,
                                              @RequestParam(required = false) String endTime,
                                              @RequestParam(required = false) Integer pageNum,
                                              @RequestParam(required = false) Integer pageSize) {
        String normalizedOrderNo = trimToNull(orderNo);
        String normalizedPayCenterOrderNo = trimToNull(payCenterOrderNo);
        String normalizedTicketLogicNum = trimToNull(ticketLogicNum);
        String normalizedBeginTime = trimToNull(beginTime);
        String normalizedEndTime = trimToNull(endTime);
        if (!hasSearchScope(normalizedOrderNo, normalizedPayCenterOrderNo, normalizedTicketLogicNum,
                normalizedBeginTime, normalizedEndTime)) {
            return ResultMapper.illegalParams("请填写订单号、支付中心订单号、票卡逻辑号，或完整的下单时间范围");
        }

        int currentPage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int currentPageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        String normalizedDeviceId = trimToNull(deviceId);
        String normalizedChannel = trimToNull(channel);
        String normalizedStatus = trimToNull(status);
        String normalizedTopupStatus = trimToNull(topupStatus);
        int offset = (currentPage - 1) * currentPageSize;

        List<TvmTopupOrderPageView> orders = tvmTopupOrderMapper.selectTopupOrderPage(
                normalizedOrderNo, normalizedPayCenterOrderNo, normalizedDeviceId, normalizedTicketLogicNum,
                normalizedStatus, normalizedChannel, normalizedTopupStatus,
                normalizedBeginTime, normalizedEndTime, offset, currentPageSize);
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("list", orders);
        page.put("total", tvmTopupOrderMapper.countTopupOrderPage(
                normalizedOrderNo, normalizedPayCenterOrderNo, normalizedDeviceId, normalizedTicketLogicNum,
                normalizedStatus, normalizedChannel, normalizedTopupStatus,
                normalizedBeginTime, normalizedEndTime));
        return ResultMapper.ok(page);
    }

    private boolean hasSearchScope(String orderNo, String payCenterOrderNo, String ticketLogicNum,
                                   String beginTime, String endTime) {
        return orderNo != null || payCenterOrderNo != null || ticketLogicNum != null
                || (beginTime != null && endTime != null);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
