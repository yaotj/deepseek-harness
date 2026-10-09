package com.chinasofti.huateng.rpc.pay;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.app.CardUnsettledQueryReqDTO;
import com.chinasofti.huateng.model.app.CardUnsettledQueryRespDTO;
import com.chinasofti.huateng.model.app.QueryTransListReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import com.chinasofti.huateng.model.app.RequestPayFailOrderReqDTO;
import com.chinasofti.huateng.model.app.RequestPayFailOrderResult;
import com.chinasofti.huateng.model.app.RequestUserAccInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestUserAccInfoResult;
import com.chinasofti.huateng.model.pay.GateTxnPayDebitConvergeReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayDebitConvergeRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPaySyncStatusReqDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Collections;
import java.util.Map;

@Service
public class GateTxnPayClient extends ProxyWebClient {
    public GateTxnPayClient(@Value("${service.gateTxnPay.url:gate-txn-pay-service}") String baseUrl,
                            @Value("${service.gateTxnPay.openLogger:true}") boolean openLogger,
                            WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    public GateTxnPayRespDTO requestGateTxnPay(@RequestBody GateTxnPayReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/requestPay", request);
        return JSONUtil.toBean(result, new TypeReference<GateTxnPayRespDTO>() {
        }, true);
    }

    /**
     * 按交易业务键查询 GT 订单号（供 ticket-server 关联查询 tradeOrderNo）。
     */
    public GateTxnPayRespDTO queryOrderByBizKey(@RequestBody GateTxnPayReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/queryOrderByBizKey", request);
        return JSONUtil.toBean(result, new TypeReference<GateTxnPayRespDTO>() {
        }, true);
    }

    /**
     * 查询用户指定支付渠道在指定时间之后是否存在扣费失败订单（供 pay-sign-server 解约流程调用）。
     */
    public GateTxnPayFailedOrderRespDTO hasFailedOrder(@RequestBody GateTxnPayFailedOrderReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/hasFailedOrder", request);
        return JSONUtil.toBean(result, new TypeReference<GateTxnPayFailedOrderRespDTO>() {
        }, true);
    }

    /**
     * 按卡号查询是否仍有未结清扣费订单（供 blacklist-server 盘点黑名单可解除性调用）。
     */
    public CardUnsettledQueryRespDTO hasUnsettledOrderByCard(@RequestBody CardUnsettledQueryReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/hasUnsettledOrderByCard", request);
        return JSONUtil.toBean(result, new TypeReference<CardUnsettledQueryRespDTO>() {
        }, true);
    }

    /**
     * 支付结果回调后同步扣费状态（供 pay-sign-server 调用）。
     */
    public GateTxnPayRespDTO syncDebitStatus(@RequestBody GateTxnPaySyncStatusReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/syncDebitStatus", request);
        return JSONUtil.toBean(result, new TypeReference<GateTxnPayRespDTO>() {
        }, true);
    }

    // ==================== IF8A-05 APP 交易记录列表 RPC ====================

    /**
     * 分页查询进出站交易记录（供 ticket-server 调用）。
     */
    public List<GateTxnPayListDTO> requestTransList(@RequestBody QueryTransListReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/app/requestTransList", request);
        return JSONUtil.toList(result, GateTxnPayListDTO.class);
    }

    /**
     * 统计 IF8A-05 分页查询结果总数（供 ticket-server / trans-query-server 调用）。
     */
    public int countTransList(@RequestBody QueryTransListReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/app/countTransList", request);
        return parseCount(result);
    }

    /**
     * 解析 {@code countTransList} 的裸标量响应。
     */
    private static int parseCount(String rawBody) {
        if (rawBody == null) {
            throw new IllegalStateException("gate-txn-pay countTransList 返回 null，"
                    + "通常是对端不可达或网关未回体；MUST 先确认 service.gateTxnPay.url 与对端存活");
        }
        String trimmed = rawBody.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("gate-txn-pay countTransList 期望裸整数，实际响应体="
                    + (rawBody.length() > 200 ? rawBody.substring(0, 200) + "...(已截断)" : rawBody)
                    + "；对端契约可能已改（套了 CommonResult / 返了错误页），"
                    + "MUST 同批改本方法与 ticket-server、trans-query-server 两个调用点", e);
        }
    }

    // ==================== IF8A-41 APP 账单统计 RPC ====================

    /**
     * IF8A-41 账单统计（供 ticket-server 调用）。
     */
    public RequestTransStatisticsResult requestTransStatistics(@RequestBody RequestTransStatisticsReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/app/requestTransStatistics", request);
        return JSONUtil.toBean(result, new TypeReference<RequestTransStatisticsResult>() {
        }, true);
    }

    // ==================== IF8A-34 APP 订单详情 RPC ====================

    /**
     * 按订单号查询交易记录（供 ticket-server 调用）。
     */
    public GateTxnPayListDTO queryByOrderNo(String orderNo) {
        Map<String, String> request = Collections.singletonMap("orderNo", orderNo);
        String result = postJsonAndGetResponse("/ci/gateTxnPay/app/queryByOrderNo", request);
        return JSONUtil.toBean(result, GateTxnPayListDTO.class, true);
    }

    // ==================== IF8A-26 APP 在线补款下单 RPC ====================

    // 调用方改用 {@link com.chinasofti.huateng.rpc.facepay.FacePayClient#requestPayOrder}。

    // ==================== IF8A-35 APP 用户账务信息 RPC ====================

    /**
     * IF8A-35 查询用户账务信息：未支付订单数 + 扣费失败订单数（供 fep-app-server 调用）。
     */
    public RequestUserAccInfoResult requestUserAccInfo(@RequestBody RequestUserAccInfoReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/app/requestUserAccInfo", request);
        return JSONUtil.toBean(result, new TypeReference<RequestUserAccInfoResult>() {
        }, true);
    }

    // ==================== 用户主动发起免密失败订单重试扣费（APP requestPayFailOrder） ====================

    /**
     * 用户主动发起免密失败订单重试扣费：把指定 thirdUserId（可选按 cardNums 收窄）下
     * {@code DEBIT_STATUS IN ('INIT','RETRY','FAIL')} 的过闸扣费单重新发起免密扣款。
     * 供 fep-app-server 的 {@code GateTxnPayController#requestPayFailOrder} 调用。
     */
    public RequestPayFailOrderResult requestPayFailOrder(@RequestBody RequestPayFailOrderReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/app/requestPayFailOrder", request);
        return JSONUtil.toBean(result, new TypeReference<RequestPayFailOrderResult>() {
        }, true);
    }

    // ==================== web-admin Quartz 触发的补偿入口 ====================

    /**
     * 跑一轮离线码金额补偿，供 web-admin 的 {@code gateTxnPayQuartzTask.recoverOfflineFare()} 调用。
     * @param headers 附加请求头，Quartz 侧传 {@code QuartzTraceUtils.traceHeaders(traceId)}
     */
    public CommonResult recoverOfflineFare(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/gate-txn-pay/offline-fare/recover",
                Collections.emptyMap(), headers);
        return JSONUtil.toBean(result, new TypeReference<CommonResult>() {
        }, true);
    }

    /**
     * 跑一轮公交换乘推送，供 web-admin 的 {@code gateTxnPayQuartzTask.pushMetroTransfer()} 调用。
     * @param headers 附加请求头，同上。
     */
    public CommonResult pushMetroTransfer(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/gate-txn-pay/metro-transfer/push",
                Collections.emptyMap(), headers);
        return JSONUtil.toBean(result, new TypeReference<CommonResult>() {
        }, true);
    }

    /**
     * 跑一轮**非支付宝渠道**的行程扣费批量重试，供 web-admin 的
     * {@code gateTxnPayQuartzTask.retryDefaultChannelDebits()}（{@code sys_job} 220）调用。
     * @param headers 附加请求头，同上。
     */
    public CommonResult retryDefaultChannelDebits(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/gate-txn-pay/debit/retry/default",
                Collections.emptyMap(), headers);
        return JSONUtil.toBean(result, new TypeReference<CommonResult>() {
        }, true);
    }

    /**
     * 跑一轮**支付宝出行渠道**的行程扣费批量重试，供 web-admin 的
     * {@code gateTxnPayQuartzTask.retryAlipayChannelDebits()}（{@code sys_job} 255）调用。
     * <p>与上一个方法**NEVER 合并**：两类单子的扣费出口不同（支付宝那支走 alipay-pay-sign、不经支付中心）。
     * @param headers 附加请求头，同上。
     */
    public CommonResult retryAlipayChannelDebits(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/gate-txn-pay/debit/retry/alipay",
                Collections.emptyMap(), headers);
        return JSONUtil.toBean(result, new TypeReference<CommonResult>() {
        }, true);
    }

    /**
     * 跑一轮**近 N 分钟未扣费订单**的重试，供 web-admin 的
     * {@code gateTxnPayQuartzTask.retryRecentUnpaidDebits()}（{@code sys_job} 345「补站扣费周期查询更新」，2026-09-21 编号先后为 135 → 265 → 235 → 345）调用。
     * <p>与上面两个方法**并行、NEVER 合并**：本条不分渠道、多捞 {@code INIT}、每分钟一轮且不写记账列，
     * 判据见 gate-txn-pay-server 的 {@code DebitRetryProcessor#retryRecentUnpaidDebits()} 与 ADR-D154。
     * @param headers 附加请求头，同上。
     */
    public CommonResult retryRecentUnpaidDebits(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/gate-txn-pay/debit/retry/recent",
                Collections.emptyMap(), headers);
        return JSONUtil.toBean(result, new TypeReference<CommonResult>() {
        }, true);
    }

    /**
     * 补款支付成功后收敛原过闸订单的扣费状态，供 face-pay-server 的补款链路调用。
     */
    public GateTxnPayDebitConvergeRespDTO convergeDebitStatusForSupplement(
            @RequestBody GateTxnPayDebitConvergeReqDTO request) {
        String result = postJsonAndGetResponse("/internal/gate-txn-pay/debit/converge", request);
        return JSONUtil.toBean(result, new TypeReference<GateTxnPayDebitConvergeRespDTO>() {
        }, true);
    }
}
