package com.chinasofti.huateng.facepay.controller.paycenter;

import com.chinasofti.huateng.facepay.service.supplement.SupplementPayCenterFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 补款单（{@code SP} 前缀）的支付中心结果回调。 */
@RestController
public class SupplementPayNoticeController {

    private static final Logger log = LoggerFactory.getLogger(SupplementPayNoticeController.class);

    private final SupplementPayCenterFlow supplementPayCenterFlow;

    public SupplementPayNoticeController(SupplementPayCenterFlow supplementPayCenterFlow) {
        this.supplementPayCenterFlow = supplementPayCenterFlow;
    }

    @PostMapping({"/ci/facePay/paycenter/payNotice", "/itpbom/ci/bom/supplementPayNotice"})
    public Map<String, String> payNotice(@RequestBody Map<String, Object> body) {
        String orderNo = extractOrderNo(body);
        if (orderNo == null || orderNo.isBlank()) {
            log.warn("补款回调缺少 orderNo / merchantOrderNo, bodyKeys={}", body != null ? body.keySet() : null);
            return Map.of("code", "0", "msg", "success");
        }
        log.info("补款支付中心回调, orderNo={}, status={}, channelOrderNo={}, totalAmount={}, payTime={}",
                orderNo, valueOf(body, "status"), valueOf(body, "channelOrderNo"),
                valueOf(body, "totalAmount"), valueOf(body, "payTime"));
        try {
            supplementPayCenterFlow.handlePayNotice(orderNo);
        } catch (RuntimeException e) {
            log.error("补款回调处理异常, orderNo={}", orderNo, e);
        }
        return Map.of("code", "0", "msg", "success");
    }

    /**
     * 回调正文取值，只用于日志。
     *
     * <p>收口**刻意不看 {@code status}**：`handlePayNotice` 内部以支付中心查询结果为权威，
     * 失败回调同样要收口成失败态。NEVER 改成「status 非 SUCCESS 就直接 return」——那会让失败单永久悬挂。
     */
    private static String valueOf(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? null : String.valueOf(value);
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
