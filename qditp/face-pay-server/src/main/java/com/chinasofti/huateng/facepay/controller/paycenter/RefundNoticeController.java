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
