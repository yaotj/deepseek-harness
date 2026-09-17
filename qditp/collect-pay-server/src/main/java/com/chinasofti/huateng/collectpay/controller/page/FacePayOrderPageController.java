package com.chinasofti.huateng.collectpay.controller.page;

import com.chinasofti.huateng.collectpay.mapper.TvmOrderMapper;
import com.chinasofti.huateng.collectpay.entity.TvmPayOrder;
import com.chinasofti.huateng.collectpay.model.page.FacePayOrderPageView;
import com.chinasofti.huateng.collectpay.model.request.tvm.RequestRefundReqDTO;
import com.chinasofti.huateng.collectpay.service.TvmOrderPreService;
import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 运营端 TVM 当面付订单查询。 */
@RestController
@RequestMapping("/page/face-pay/orders")
public class FacePayOrderPageController {
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final TvmOrderMapper tvmOrderMapper;
    private final TvmOrderPreService tvmOrderPreService;

    public FacePayOrderPageController(TvmOrderMapper tvmOrderMapper, TvmOrderPreService tvmOrderPreService) {
        this.tvmOrderMapper = tvmOrderMapper;
        this.tvmOrderPreService = tvmOrderPreService;
    }

    /** 分页查询 TVM 当面付订单；要求订单标识或完整下单时间范围以保护数据库。 */
    @GetMapping
    public ResultVO<Map<String, Object>> page(@RequestParam(required = false) String orderNo,
                                              @RequestParam(required = false) String payCenterOrderNo,
                                              @RequestParam(required = false) String payCenterChannelOrderNo,
                                              @RequestParam(required = false) String deviceId,
                                              @RequestParam(required = false) String channel,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String ticketType,
                                              @RequestParam(required = false) String beginTime,
                                              @RequestParam(required = false) String endTime,
                                              @RequestParam(required = false) Integer pageNum,
                                              @RequestParam(required = false) Integer pageSize) {
        String normalizedOrderNo = trimToNull(orderNo);
        String normalizedPayCenterOrderNo = trimToNull(payCenterOrderNo);
        String normalizedChannelOrderNo = trimToNull(payCenterChannelOrderNo);
        String normalizedBeginTime = trimToNull(beginTime);
        String normalizedEndTime = trimToNull(endTime);
        if (!hasSearchScope(normalizedOrderNo, normalizedPayCenterOrderNo, normalizedChannelOrderNo, normalizedBeginTime, normalizedEndTime)) {
            return ResultMapper.illegalParams("请填写订单号、支付中心订单号、渠道订单号，或完整的下单时间范围");
        }

        int currentPage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int currentPageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        String normalizedDeviceId = trimToNull(deviceId);
        String normalizedChannel = trimToNull(channel);
        String normalizedStatus = trimToNull(status);
        String normalizedTicketType = trimToNull(ticketType);
        int offset = (currentPage - 1) * currentPageSize;

        List<FacePayOrderPageView> orders = tvmOrderMapper.selectFacePayOrderPage(
                normalizedOrderNo, normalizedPayCenterOrderNo, normalizedChannelOrderNo, normalizedDeviceId,
                normalizedChannel, normalizedStatus, normalizedTicketType,
                normalizedBeginTime, normalizedEndTime, offset, currentPageSize);
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("list", orders);
        page.put("total", tvmOrderMapper.countFacePayOrderPage(
                normalizedOrderNo, normalizedPayCenterOrderNo, normalizedChannelOrderNo, normalizedDeviceId,
                normalizedChannel, normalizedStatus, normalizedTicketType,
                normalizedBeginTime, normalizedEndTime));
        return ResultMapper.ok(page);
    }

    /** 运营端发起 TVM 当面付全额退款。 */
    @PostMapping("/{orderNo}/refund")
    public ResultVO<JSONObject> requestRefund(@PathVariable String orderNo,
                                              @RequestBody(required = false) FacePayRefundRequest request) {
        if (!StringUtils.hasText(orderNo)) {
            return ResultMapper.illegalParams("订单号不能为空");
        }
        TvmPayOrder order = tvmOrderMapper.selectByOrderNo(orderNo.trim());
        if (order == null) {
            return ResultMapper.error("未找到对应的当面付订单");
        }
        if (!"1".equals(order.getStatus())) {
            return ResultMapper.error("仅支付成功的订单可以退款");
        }
        if (StringUtils.hasText(order.getRsv2())) {
            return ResultMapper.error("该订单已发起退款，退款单号：" + order.getRsv2());
        }
        int refundAmount;
        try {
            refundAmount = order.calculateTotalPrice().intValueExact();
        } catch (ArithmeticException exception) {
            return ResultMapper.error("订单金额格式异常，不能退款");
        }
        if (refundAmount <= 0) {
            return ResultMapper.error("订单金额无效，不能退款");
        }

        RequestRefundReqDTO refundRequest = new RequestRefundReqDTO();
        refundRequest.setOrderNo(order.getOrderNo());
        refundRequest.setRefundAmt(String.valueOf(refundAmount));
        refundRequest.setRefundReason(request == null || !StringUtils.hasText(request.getRefundReason())
                ? "运营人工退款" : request.getRefundReason().trim());
        JSONObject result = tvmOrderPreService.requestRefund(refundRequest);
        String retCode = result == null ? null : result.getString("retCode");
        return "0000".equals(retCode) ? ResultMapper.ok(result)
                : ResultMapper.error(result == null ? "退款服务未返回结果" : result.getString("retMsg"));
    }

    private boolean hasSearchScope(String orderNo, String payCenterOrderNo, String channelOrderNo, String beginTime, String endTime) {
        return orderNo != null || payCenterOrderNo != null || channelOrderNo != null || (beginTime != null && endTime != null);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
