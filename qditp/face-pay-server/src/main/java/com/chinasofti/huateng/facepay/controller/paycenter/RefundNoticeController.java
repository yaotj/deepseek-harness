package com.chinasofti.huateng.facepay.controller.paycenter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.app.AppRefundNotiResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.app.AppResponses;
import com.chinasofti.huateng.facepay.service.F2fAppRefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 支付中心退款结果回调（契约 §5.2）的**直达入口**。
 *
 * <p>为什么要单开这个类，而不是复用 {@code AppOrderController.receiveRefundResult}：
 * <ul>
 *   <li>那条端点是 {@code @ModelAttribute} 表单绑定，**支付中心发 JSON 时一个字段都取不到**，
 *       会静默变成空对象并回失败码，与日票退款回调 2026-09-20 踩的坑同型；它同时还被 APP 侧
 *       form 报文使用，因此保留原样、NEVER 改它的绑定方式。</li>
 *   <li>路径前缀受入向网关约束：`/ci/app/**` 在 `fep-app-vr` 里没有对应 route，公网打不进来；
 *       而 `/itpbom/`、`/itptvm/` 两条前缀直达 `face-pay-server-svc`，且 rewrite 保留前缀。
 *       因此这里挂的是带前缀的绝对路径别名，形态与 `PAY_CENTER_PAY_NOTICE_URL`
 *       （`/itptvm/ci/tvm/payNotice`）一致。</li>
 * </ul>
 *
 * <p>配套配置：`pay.center.refund-notice-url`（env `PAY_CENTER_REFUND_NOTICE_URL`）MUST 指向本端点，
 * 它会被 {@code PayCenterMessageFactory.buildRefundRequest} 作为 §3.1 必填键 `notifyUrl` 送给支付中心 ——
 * 不送就永远收不到退款回调。
 */
@RestController
public class RefundNoticeController {

    private static final Logger log = LoggerFactory.getLogger(RefundNoticeController.class);

    private final F2fAppRefundService appRefundService;

    public RefundNoticeController(F2fAppRefundService appRefundService) {
        this.appRefundService = appRefundService;
    }

    @PostMapping({"/itpbom/ci/bom/receiveRefundResult", "/itptvm/ci/tvm/receiveRefundResult"})
    public JSONObject receiveRefundResult(@RequestBody String requestBody) {
        log.info("支付中心退款回调原始报文={}", requestBody);
        AppRefundNotiResultReqDTO request = parseCallbackBody(requestBody);
        if (request == null) {
            return AppResponses.fail(AppResponses.CODE_INVALID_PARAM, "非法参数,bizData解析失败");
        }
        if (isBlank(request.getRefundResult())) {
            return AppResponses.fail(AppResponses.CODE_INVALID_PARAM, "非法参数,refundResult不能为空");
        }
        if (isBlank(request.getOutRefundNo()) && isBlank(request.getRefundNo())) {
            return AppResponses.fail(AppResponses.CODE_INVALID_PARAM, "非法参数,退款单号不能为空");
        }
        JSONObject result = appRefundService.receiveRefundResult(request);
        log.info("支付中心退款回调处理结果={}", result);
        return result;
    }

    /**
     * 三种形态都收：{@code bizData} 是对象、{@code bizData} 是 base64 串、整个 body 就是业务体。
     *
     * <p>NEVER 在解析失败时回落成空对象 —— 日票那次就是「null 当成 {@code {}}」把故障静默化，
     * 最后表现为对方不断重推、我方每次返「orderNo 不能为空」。
     */
    private AppRefundNotiResultReqDTO parseCallbackBody(String requestBody) {
        if (requestBody == null || requestBody.isBlank()) {
            return null;
        }
        try {
            JSONObject root = JSON.parseObject(requestBody);
            Object bizData = root.get("bizData");
            if (bizData instanceof JSONObject) {
                return ((JSONObject) bizData).toJavaObject(AppRefundNotiResultReqDTO.class);
            }
            if (bizData instanceof String text && !text.isBlank()) {
                String decoded = new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
                return JSON.parseObject(decoded, AppRefundNotiResultReqDTO.class);
            }
            return root.toJavaObject(AppRefundNotiResultReqDTO.class);
        } catch (RuntimeException e) {
            log.error("支付中心退款回调报文解析失败", e);
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
