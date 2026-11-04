package com.chinasofti.huateng.collectpay.controller.page;

import com.chinasofti.huateng.collectpay.mapper.BomNoCashOrderMapper;
import com.chinasofti.huateng.collectpay.model.page.BomOrderPageView;
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

/**
 * 运营端 BOM 非现金订单查询（对标旧 ITP「BOM非现金管理」，只读）。
 *
 * <p>transType 可选：01 购票发售（单程票购票订单口径）/ 22 充值；不传查全部。
 */
@RestController
@RequestMapping("/page/bom/orders")
public class BomOrderPageController {
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final BomNoCashOrderMapper bomNoCashOrderMapper;

    public BomOrderPageController(BomNoCashOrderMapper bomNoCashOrderMapper) {
        this.bomNoCashOrderMapper = bomNoCashOrderMapper;
    }

    /** 分页查询 BOM 非现金订单；要求订单标识或完整下单时间范围以保护数据库。 */
    @GetMapping
    public ResultVO<Map<String, Object>> page(@RequestParam(required = false) String orderNo,
                                              @RequestParam(required = false) String payCenterOrderNo,
                                              @RequestParam(required = false) String deviceId,
                                              @RequestParam(required = false) String cardId,
                                              @RequestParam(required = false) String transType,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String paymentVendor,
                                              @RequestParam(required = false) String beginTime,
                                              @RequestParam(required = false) String endTime,
                                              @RequestParam(required = false) Integer pageNum,
                                              @RequestParam(required = false) Integer pageSize) {
        String normalizedOrderNo = trimToNull(orderNo);
        String normalizedPayCenterOrderNo = trimToNull(payCenterOrderNo);
        String normalizedCardId = trimToNull(cardId);
        String normalizedBeginTime = trimToNull(beginTime);
        String normalizedEndTime = trimToNull(endTime);
        if (!hasSearchScope(normalizedOrderNo, normalizedPayCenterOrderNo, normalizedCardId,
                normalizedBeginTime, normalizedEndTime)) {
            return ResultMapper.illegalParams("请填写订单号、支付中心订单号、卡号，或完整的下单时间范围");
        }

        int currentPage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int currentPageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        String normalizedDeviceId = trimToNull(deviceId);
        String normalizedTransType = trimToNull(transType);
        String normalizedStatus = trimToNull(status);
        String normalizedPaymentVendor = trimToNull(paymentVendor);
        int offset = (currentPage - 1) * currentPageSize;

        List<BomOrderPageView> orders = bomNoCashOrderMapper.selectBomOrderPage(
                normalizedOrderNo, normalizedPayCenterOrderNo, normalizedDeviceId, normalizedCardId,
                normalizedTransType, normalizedStatus, normalizedPaymentVendor,
                normalizedBeginTime, normalizedEndTime, offset, currentPageSize);
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("list", orders);
        page.put("total", bomNoCashOrderMapper.countBomOrderPage(
                normalizedOrderNo, normalizedPayCenterOrderNo, normalizedDeviceId, normalizedCardId,
                normalizedTransType, normalizedStatus, normalizedPaymentVendor,
                normalizedBeginTime, normalizedEndTime));
        return ResultMapper.ok(page);
    }

    private boolean hasSearchScope(String orderNo, String payCenterOrderNo, String cardId,
                                  String beginTime, String endTime) {
        return orderNo != null || payCenterOrderNo != null || cardId != null
                || (beginTime != null && endTime != null);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
