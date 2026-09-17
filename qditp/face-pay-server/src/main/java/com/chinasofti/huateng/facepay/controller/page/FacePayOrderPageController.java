package com.chinasofti.huateng.facepay.controller.page;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.facepay.api.page.FacePayOrderPageVO;
import com.chinasofti.huateng.facepay.api.page.FacePayRefundRequest;
import com.chinasofti.huateng.facepay.service.F2fOrderQueryService;
import com.chinasofti.huateng.facepay.service.F2fPageRefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/** 运营端当面付订单查询与人工退款。 */
@RestController
@RequestMapping("/page/face-pay/orders")
public class FacePayOrderPageController {

    private static final Logger log = LoggerFactory.getLogger(FacePayOrderPageController.class);

    /** 前端传的时间形态，与旧实现一致。 */
    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final F2fOrderQueryService orderQueryService;

    private final F2fPageRefundService pageRefundService;

    public FacePayOrderPageController(F2fOrderQueryService orderQueryService,
                                      F2fPageRefundService pageRefundService) {
        this.orderQueryService = orderQueryService;
        this.pageRefundService = pageRefundService;
    }

    /** 分页查询。 */
    @GetMapping
    public ResultVO<Map<String, Object>> page(@RequestParam(required = false) String orderNo,
                                              @RequestParam(required = false) String payCenterOrderNo,
                                              @RequestParam(required = false) String payCenterChannelOrderNo,
                                              @RequestParam(required = false) String deviceId,
                                              @RequestParam(required = false) String channel,
                                              @RequestParam(required = false) String bizType,
                                              @RequestParam(required = false) String orderStatus,
                                              @RequestParam(required = false) String beginTime,
                                              @RequestParam(required = false) String endTime,
                                              @RequestParam(required = false) Integer pageNum,
                                              @RequestParam(required = false) Integer pageSize) {
        LocalDateTime begin;
        LocalDateTime end;
        try {
            begin = parse(beginTime);
            end = parse(endTime);
        } catch (DateTimeParseException e) {
            return ResultMapper.illegalParams("时间格式必须是 yyyy-MM-dd HH:mm:ss");
        }
        F2fOrderQueryService.OrderPageQuery query = new F2fOrderQueryService.OrderPageQuery(
                orderNo, payCenterOrderNo, payCenterChannelOrderNo, deviceId, channel, bizType,
                orderStatus, begin, end, pageNum, pageSize);
        return switch (orderQueryService.page(query)) {
            case F2fOrderQueryService.PageOutcome.Ok ok -> {
                Map<String, Object> page = new LinkedHashMap<>();
                page.put("list", ok.list());
                page.put("total", ok.total());
                yield ResultMapper.ok(page);
            }
            case F2fOrderQueryService.PageOutcome.Rejected rejected ->
                    ResultMapper.illegalParams(rejected.reason());
        };
    }

    /** 运营端发起全额退款。 */
    @PostMapping("/{orderNo}/refund")
    public ResultVO<Map<String, Object>> refund(@PathVariable String orderNo,
                                                @RequestBody(required = false) FacePayRefundRequest request) {
        if (orderNo == null || orderNo.isBlank()) {
            return ResultMapper.illegalParams("订单号不能为空");
        }
        String reason = request == null || request.getRefundReason() == null
                || request.getRefundReason().isBlank()
                ? "运营人工退款" : request.getRefundReason().trim();
        String operator = request == null ? null : trimToNull(request.getOperatorId());
        log.info("运营端发起当面付退款, orderNo={}, operatorId={}", orderNo.trim(), operator);
        return pageRefundService.refundWholeOrder(orderNo.trim(), reason, operator);
    }

    private static LocalDateTime parse(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : LocalDateTime.parse(normalized, INPUT_FORMATTER);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
