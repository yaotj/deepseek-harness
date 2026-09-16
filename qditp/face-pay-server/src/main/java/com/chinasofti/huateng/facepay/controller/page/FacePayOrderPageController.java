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

/**
 * 运营端当面付订单查询与人工退款。<b>URL 与旧服务一字不改</b>：{@code /page/face-pay/orders}。
 *
 * <p>与设备侧接口不同，本组接口返回 {@link ResultVO}（{@code code/msg/data} 形态），
 * 由管理后台前端解析，<b>不是设备的 retCode 契约</b>。</p>
 *
 * <p><b>必须先有检索范围</b>：订单号、支付中心订单号，或完整的下单时间范围。
 * {@code F2F_ORDER} 是按月分区的核心交易表，无条件全扫会直接影响设备链路。</p>
 *
 * <p>查询结果已升级为强类型 {@link FacePayOrderPageVO}，字段与取值对齐域模型：
 * {@code orderStatus} 存真实枚举值（CREATED / PAID / FULFILLED / REFUNDED 等），
 * 退款状态由独立列承载与主状态正交。前端消费侧 MUST 同步更新映射逻辑，
 * 旧 {@code status="1"} 等 ItpStatusEnum 投影已废弃。</p>
 *
 * <p>⚠️ 本接口<b>没有鉴权</b>，与旧实现一致。它能发起真实退款，
 * 挂在 {@code /page/**} 下靠网络隔离保护；上线前 MUST 确认该路径未对外暴露。</p>
 */
@RestController
@RequestMapping("/page/face-pay/orders")
public class FacePayOrderPageController {

    private static final Logger log = LoggerFactory.getLogger(FacePayOrderPageController.class);

    /**
     * 前端传的时间形态，与旧实现一致。
     *
     * <p>解析留在 Controller、<b>不下沉到 service</b>：这是报文格式问题，格式错就该直接
     * 回「时间格式必须是 yyyy-MM-dd HH:mm:ss」；service 只接已解析好的 {@code LocalDateTime}。
     * 分页归一化与检索范围校验已挪进 {@link F2fOrderQueryService}。</p>
     */
    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final F2fOrderQueryService orderQueryService;

    private final F2fPageRefundService pageRefundService;

    public FacePayOrderPageController(F2fOrderQueryService orderQueryService,
                                      F2fPageRefundService pageRefundService) {
        this.orderQueryService = orderQueryService;
        this.pageRefundService = pageRefundService;
    }

    /**
     * 分页查询。要求订单标识或完整下单时间范围以保护数据库。
     *
     * <p><b>{@code payCenterChannelOrderNo}（渠道订单号）也算一种检索范围</b>——旧
     * {@code /page/face-pay/orders} 就支持这一维，2026-09-11 新旧双打实测旧服务在
     * 无参时回「请填写订单号、支付中心订单号、渠道订单号，或完整的下单时间范围」，
     * 而新服务连这个 {@code @RequestParam} 都没有，运营后台按渠道订单号查不到单。
     * NEVER 再把它从入参里去掉。</p>
     */
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

    /**
     * 运营端发起全额退款。
     *
     * <p><b>退款金额只从订单总额算，不信任页面输入</b>——照搬旧实现的这条约束，
     * 页面只能填退款原因。</p>
     */
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
