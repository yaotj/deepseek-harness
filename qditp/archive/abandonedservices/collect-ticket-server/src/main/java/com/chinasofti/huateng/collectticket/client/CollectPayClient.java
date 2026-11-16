package com.chinasofti.huateng.collectticket.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.collectticket.model.request.TicketCollectPayNotifyReqDTO;
import com.chinasofti.huateng.collectticket.model.response.TicketCollectPayNotifyRespDTO;
import com.chinasofti.huateng.rpc.route.URLDynamicRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 取票服务调用支付服务的客户端。
 */
@Component
public class CollectPayClient {
    private static final Logger log = LoggerFactory.getLogger(CollectPayClient.class);

    private final URLDynamicRouter urlDynamicRouter;

    public CollectPayClient(URLDynamicRouter urlDynamicRouter) {
        this.urlDynamicRouter = urlDynamicRouter;
    }

    public String requestPay(Object request) {
        try {
            String path = "/collect-pay-server/ci/app/requestPay";
            String response = urlDynamicRouter.postProxy(path, request, null);
            log.info("调用支付服务requestPay响应: {}", response);
            return response;
        } catch (Exception e) {
            log.error("调用支付服务requestPay失败", e);
            return null;
        }
    }

    public JSONObject requestPayForJson(Object request) {
        String response = requestPay(request);
        if (!StringUtils.hasText(response)) {
            return null;
        }
        return JSON.parseObject(response);
    }

    public String payQuery(Object request) {
        try {
            String path = "/collect-pay-server/ci/app/payQuery";
            String response = urlDynamicRouter.postProxy(path, request, null);
            log.info("调用支付服务payQuery响应: {}", response);
            return response;
        } catch (Exception e) {
            log.error("调用支付服务payQuery失败", e);
            return null;
        }
    }

    public String requestRefund(Object request) {
        try {
            String path = "/collect-pay-server/ci/app/requestRefund";
            String response = urlDynamicRouter.postProxy(path, request, null);
            log.info("调用支付服务requestRefund响应: {}", response);
            return response;
        } catch (Exception e) {
            log.error("调用支付服务requestRefund失败", e);
            return null;
        }
    }

    public TicketCollectPayNotifyRespDTO notifyPayResult(TicketCollectPayNotifyReqDTO request) {
        try {
            String path = "/collect-pay-server/ci/app/ticketCollectPayNotify";
            String response = urlDynamicRouter.postProxy(path, request, null);
            log.info("通知支付服务支付结果响应: {}", response);
            if (StringUtils.hasText(response)) {
                return JSON.parseObject(response, TicketCollectPayNotifyRespDTO.class);
            }
            return null;
        } catch (Exception e) {
            log.error("通知支付服务支付结果失败", e);
            return null;
        }
    }
}
