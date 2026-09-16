package com.chinasofti.huateng.facepay.controller.paycenter;

import com.chinasofti.huateng.facepay.service.supplement.SupplementPayCenterFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 补款单（{@code SP} 前缀）的支付中心结果回调。
 *
 * <p><b>类级 {@code @RequestMapping} 已刻意去掉</b>：两条路径不共享前缀，MUST 各写全路径。
 * <ul>
 *   <li>{@code /ci/facePay/paycenter/payNotice} —— 原有内网路径，<b>NEVER 删</b>；</li>
 *   <li>{@code /itpbom/ci/bom/supplementPayNotice} —— 对外别名，复用 {@code fep-app-vr} 已有的
 *       {@code /itpbom/} 前缀（{@code rewrite.uri: /itpbom/}，路径原样保留），因此
 *       <b>不需要改网关</b>。支付中心的 {@code notifyUrl} 送的就是它（ADR-D103）。</li>
 * </ul>
 * 两条走同一个方法体，NEVER 复制一份实现。</p>
 */
@RestController
public class SupplementPayNoticeController {

    private static final Logger log = LoggerFactory.getLogger(SupplementPayNoticeController.class);

    private final SupplementPayCenterFlow supplementPayCenterFlow;

    public SupplementPayNoticeController(SupplementPayCenterFlow supplementPayCenterFlow) {
        this.supplementPayCenterFlow = supplementPayCenterFlow;
    }

    @PostMapping({"/ci/facePay/paycenter/payNotice", "/itpbom/ci/bom/supplementPayNotice"})
    public Map<String, String> payNotice(@RequestBody Map<String, Object> body) {
        log.debug("补款支付中心回调, bodyKeys={}", body != null ? body.keySet() : null);
        String orderNo = extractOrderNo(body);
        if (orderNo == null || orderNo.isBlank()) {
            log.warn("补款回调缺少 orderNo / merchantOrderNo, bodyKeys={}", body != null ? body.keySet() : null);
            return Map.of("code", "0", "msg", "success");
        }
        try {
            supplementPayCenterFlow.handlePayNotice(orderNo);
        } catch (RuntimeException e) {
            log.error("补款回调处理异常, orderNo={}", orderNo, e);
        }
        return Map.of("code", "0", "msg", "success");
    }

    private static String extractOrderNo(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        Object orderNo = body.get("orderNo");
        if (orderNo != null) {
            return String.valueOf(orderNo);
        }
        Object merchantOrderNo = body.get("merchantOrderNo");
        if (merchantOrderNo != null) {
            return String.valueOf(merchantOrderNo);
        }
        return null;
    }
}
