package com.chinasofti.huateng.rpc.facepay;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;


/**
 * APP 域（IF8A 系列）form 报文的 face-pay 转发客户端。
 *
 * <p>公共参数（providerId / charset / format / timestamp / deviceId / signType / sign / bizData）
 * 原样透传，<b>签名不重算</b>：fep-app 是转发层，不是报文发起方。</p>
 *
 * <p>8 条路径与 face-pay 现有控制器一一对应，其中 7 条在 {@code AppOrderController}
 * （{@code /ci/app}）、{@code /requestActiveTicket} 在 {@code TvmOrderController}
 * （{@code /itptvm/ci/tvm}）—— 两条前缀不同，NEVER 合并成同一个常量。
 * 见 docs/business/app-gateway.md「APP 域网关 Controller 表」。</p>
 *
 * <p>本类只服务 fep-app 的 APP 域网关；对 face-pay 的 JSON 报文转发仍走 {@code FacePayClient}。</p>
 */
@Service
public class FacePayAppClient extends ProxyWebClient {

    private static final String APP_BASE = "/ci/app";

    private static final String TVM_BASE = "/itptvm/ci/tvm";

    public FacePayAppClient(@Value("${service.facePay.url:face-pay-service}") String baseUrl,
                            @Value("${service.facePay.openLogger:true}") boolean openLogger,
                            WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    @Override
    protected boolean isNetworkRetryEnabled() {
        return false;
    }


    /** IF8A-20 请求下单（APP 取票下单）。 */
    public JSONObject requestOrder(Map<String, String> formData) {
        return postAppForm(APP_BASE + "/requestOrder", formData);
    }


    /**
     * IF8A-11 请求支付信息。
     *
     * <p>补款单（订单号 SP 前缀）与非补款单都由 face-pay 的 {@code AppOrderController} 内部分流，
     * fep-app 不判前缀（判在前置层会让网关耦合补款业务）。</p>
     */
    public JSONObject requestPaymentInfo(Map<String, String> formData) {
        return postAppForm(APP_BASE + "/requestPaymentInfo", formData);
    }


    /** IF8A-18 支付结果查询。 */
    public JSONObject requestPayResult(Map<String, String> formData) {
        return postAppForm(APP_BASE + "/requestPayResult", formData);
    }


    /** 请求退款。 */
    public JSONObject requestRefundTicket(Map<String, String> formData) {
        return postAppForm(APP_BASE + "/requestRefundTicket", formData);
    }


    /** 退款结果查询。 */
    public JSONObject requestRefundTicketResult(Map<String, String> formData) {
        return postAppForm(APP_BASE + "/requestRefundTicketResult", formData);
    }


    /** 查询预激活订单列表。 */
    public JSONObject requestPreActiveOrderList(Map<String, String> formData) {
        return postAppForm(APP_BASE + "/requestPreActiveOrderList", formData);
    }


    /** TVM 激活请求（路径在 TVM 前缀下，不是 /ci/app）。 */
    public JSONObject requestActiveTicket(Map<String, String> formData) {
        return postAppForm(TVM_BASE + "/requestActiveTicket", formData);
    }


    /** 接收支付中心退款结果通知。 */
    public JSONObject receiveRefundResult(Map<String, String> formData) {
        return postAppForm(APP_BASE + "/receiveRefundResult", formData);
    }


    private JSONObject postAppForm(String url, Map<String, String> formData) {
        return JSON.parseObject(postFormAndGetResponse(url, formData));
    }
}
