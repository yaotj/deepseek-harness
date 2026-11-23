package com.chinasofti.huateng.facepay.controller.paycenter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.service.supplement.SupplementPayCenterFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

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
        // status/channelOrderNo 等业务字段同在 bizData 里，日志从解析结果取，NEVER 只读顶层（会恒为 null）。
        JSONObject biz = parseBizData(body);
        log.info("补款支付中心回调, orderNo={}, status={}, channelOrderNo={}, totalAmount={}, payTime={}",
                orderNo, valueOf(biz, "status"), valueOf(biz, "channelOrderNo"),
                valueOf(biz, "totalAmount"), valueOf(biz, "payTime"));
        try {
            supplementPayCenterFlow.handlePayNotice(orderNo);
        } catch (RuntimeException e) {
            log.error("补款回调处理异常, orderNo={}", orderNo, e);
        }
        return Map.of("code", "0", "msg", "success");
    }

    private static String valueOf(JSONObject obj, String key) {
        Object value = obj == null ? null : obj.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 提取支付中心回调里的「补款单号」（SP 前缀，即我方预下单时上送的 merchantOrderNo）。
     *
     * <p>支付中心真实回调报文把业务字段包在 bizData 里（Base64 编码的 JSON），顶层只有
     * apiVersion / bizData / charset / merchantNo / sign / signType，没有 orderNo：
     * <pre>
     * {"apiVersion":"1.0","bizData":"eyJ...","charset":"UTF-8","merchantNo":"...","sign":"...","signType":"RSA2"}
     * // bizData 解码后：
     * {"merchantOrderNo":"SP...126","orderNo":"2940...","status":"SUCCESS",...}
     * </pre>
     * 旧实现只读顶层 orderNo / merchantOrderNo，导致回调恒被吞、状态只能靠 5 分钟兜底任务收敛（延迟数分钟）。
     *
     * <p><b>NEVER 用 bizData 里的 orderNo 当补款单号</b>：那是支付中心单号（2940...），
     * {@code handlePayNotice} 要用它查 SUPPLEMENT_ORDER 与回查支付中心（buildQueryRequest 把它当 merchantOrderNo 上送），
     * 传错会查不到补款单。补款单号只认 merchantOrderNo。
     *
     * <p>解析优先级：顶层 merchantOrderNo / orderNo（兼容早期扁平报文）→ bizData.merchantOrderNo。
     */
    static String extractOrderNo(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        String top = firstNonBlank(body.get("merchantOrderNo"), body.get("orderNo"));
        if (top != null) {
            return top;
        }
        JSONObject biz = parseBizData(body);
        return biz == null ? null : firstNonBlank(biz.get("merchantOrderNo"));
    }

    /** 把回调里的 bizData（Base64 或 JSON 明文，或已被解析成 Map）统一解析成 JSONObject；失败返 null。 */
    private static JSONObject parseBizData(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        Object bizData = body.get("bizData");
        if (bizData instanceof Map<?, ?> map) {
            return new JSONObject(map);
        }
        if (bizData instanceof String text && !text.isBlank()) {
            String json = decodeBizData(text);
            if (json == null) {
                return null;
            }
            try {
                return JSON.parseObject(json);
            } catch (RuntimeException e) {
                log.warn("补款回调 bizData 不是合法 JSON, bizData={}", text, e);
                return null;
            }
        }
        return null;
    }

    /** bizData 可能是 Base64，也可能是 JSON 明文；与 RefundNoticeController.parseCallbackBody 同范式。 */
    private static String decodeBizData(String text) {
        String raw = text.trim();
        if (raw.startsWith("{")) {
            return raw;
        }
        try {
            return new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            log.warn("补款回调 bizData Base64 解码失败", e);
            return null;
        }
    }

    private static String firstNonBlank(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }
}
