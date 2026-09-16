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
     *
     * <p>与 {@link #hasFailedOrder} 的区别是按 CARD_ID、不带渠道、不带时间下限：
     * {@code BLACKLIST} 表没有渠道字段，判定必须覆盖该卡全部历史欠费。</p>
     *
     * <p>调用方 MUST 先判断 resultCode 再用 hasUnsettled。下游在查询未真正执行时
     * 会把 hasUnsettled 置为 true，**NEVER** 把它当成「已结清」。</p>
     */
    public CardUnsettledQueryRespDTO hasUnsettledOrderByCard(@RequestBody CardUnsettledQueryReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/hasUnsettledOrderByCard", request);
        return JSONUtil.toBean(result, new TypeReference<CardUnsettledQueryRespDTO>() {
        }, true);
    }

    /**
     * 支付结果回调后同步扣费状态（供 pay-sign-server 调用）。
     *
     * <p>调用方 MUST 检查返回的 retCode：非 0000 表示 GATE_TXN_PAY 没收敛，
     * NEVER 因为「没抛异常」就认为对齐了。</p>
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
     *
     * <p><b>这是本 Client 里唯一一个「对端返裸标量、既没有 DTO 也没有 retCode」的方法</b>：
     * gate-txn-pay 侧 {@code /ci/gateTxnPay/app/countTransList} 直接把 int 写进响应体。
     * 这个隐式契约没有编译期保护——对端哪天改成返 JSON、或套一层 {@code CommonResult}，
     * 本端只会在运行时炸。**因此这里 MUST 自己把「对端到底返了什么」带进异常消息**：
     * 裸 {@code Integer.parseInt} 抛出的 {@code NumberFormatException} 只有
     * {@code For input string: "..."}，看不出是包装变了、还是 envoy 返了错误页。
     *
     * <p><b>NEVER 改成 catch 住返 0</b>：那会把「对端契约变了」伪装成「该用户没有交易记录」，
     * 分页总数恒为 0、APP 列表永远只剩第一页，且日志里一条错都没有。
     * 抛出去、由调用方兜成 9001 才是对的。
     */
    public int countTransList(@RequestBody QueryTransListReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/app/countTransList", request);
        return parseCount(result);
    }

    /**
     * 解析 {@code countTransList} 的裸标量响应。
     *
     * <p>只容忍两种无害变形：首尾空白，以及被引号包起来的数字（对端换成 JSON 字符串序列化会这样）。
     * 其余一切形态都抛异常，并**原样带上响应体前 200 字符**。
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
     *
     * <p>统计源表是 {@code GATE_TXN_PAY}——它同时有原价 / 票价 / 超时费 / 实付四个量，
     * 单表即可算全。**NEVER 退回 ticket-server 用 {@code QRCODE_TXN_DETAIL} 自算**：
     * 那张表没有 {@code ORIGINAL_FARE}，只能拿超时费冒充优惠（2026-09-10 修正的语义错位）。</p>
     *
     * <p>调用方 MUST 先判断 retCode 再用 tripData；票种白名单与 {@code cardTypeList}
     * 展开仍在 ticket-server 侧完成后随请求带下来。</p>
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

    // IF8A-26 补款下单已随补款功能迁入 face-pay-server（2026-09-15），
    // 调用方改用 {@link com.chinasofti.huateng.rpc.facepay.FacePayClient#requestPayOrder}。

    // ==================== IF8A-35 APP 用户账务信息 RPC ====================

    /**
     * IF8A-35 查询用户账务信息：未支付订单数 + 扣费失败订单数（供 fep-app-server 调用）。
     *
     * <p>只读，统计范围是 GATE_TXN_PAY 近若干月。调用方 MUST 先判断 retCode 再用两个数量：
     * 查询未执行时两数为 0，**NEVER** 当成「无欠费」。分档口径见
     * {@link RequestUserAccInfoResult} 类注释，与解约/黑名单的「非 SUCCESS 即未结清」不同。</p>
     */
    public RequestUserAccInfoResult requestUserAccInfo(@RequestBody RequestUserAccInfoReqDTO request) {
        String result = postJsonAndGetResponse("/ci/gateTxnPay/app/requestUserAccInfo", request);
        return JSONUtil.toBean(result, new TypeReference<RequestUserAccInfoResult>() {
        }, true);
    }

    // ==================== web-admin Quartz 触发的补偿入口 ====================

    /**
     * 跑一轮离线码金额补偿，供 web-admin 的 {@code gateTxnPayQuartzTask.recoverOfflineFare()} 调用。
     *
     * <p>对端 2.0.73 起才有这个端点（此前是模块内 `@Scheduled`）。**同步跑完一轮才返回**，
     * 因此 `sys_job` 的「禁止并发」才有意义 —— 对端改成异步受理即返回时，禁并发会失效。</p>
     *
     * <p>对端**恒返 `retCode=0000`**：本轮扫表异常属可自愈（下一分钟重入），结论只在 `retMsg` 里。
     * 因此调用方按 `retCode` 判失败时，实际只会在「响应为 null / 网络不可达」时报错 —— 这是有意的，
     * **NEVER 为了让 `sys_job_log` 更「灵敏」而要求对端把可自愈错误返成非 0**。</p>
     *
     * @param headers 附加请求头，Quartz 侧传 {@code QuartzTraceUtils.traceHeaders(traceId)}，
     *                不传就在调度日志里断链（`ProxyWebClient` 不自动注入 trace 头）
     */
    public CommonResult recoverOfflineFare(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/gate-txn-pay/offline-fare/recover",
                Collections.emptyMap(), headers);
        return JSONUtil.toBean(result, new TypeReference<CommonResult>() {
        }, true);
    }

    /**
     * 跑一轮公交换乘推送，供 web-admin 的 {@code gateTxnPayQuartzTask.pushMetroTransfer()} 调用。
     *
     * <p>语义与 {@link #recoverOfflineFare} 完全一致（同步、恒 `0000`、结论在 `retMsg`），
     * **改一个 MUST 看齐另一个**。</p>
     *
     * @param headers 附加请求头，同上
     */
    public CommonResult pushMetroTransfer(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/gate-txn-pay/metro-transfer/push",
                Collections.emptyMap(), headers);
        return JSONUtil.toBean(result, new TypeReference<CommonResult>() {
        }, true);
    }

    /**
     * 补款支付成功后收敛原过闸订单的扣费状态，供 face-pay-server 的补款链路调用（2026-09-16 新增）。
     *
     * <p><b>与上面两个 `/internal/**` 方法语义不同：这条不是补偿批处理、对端也不恒返 `0000`。</b>
     * 调用方 MUST 按 {@code retCode} + {@code converged} + {@code debitStatus} 三者分支
     * （已结清 / 重复支付待退款 / 需人工核对），判据写在
     * {@link GateTxnPayDebitConvergeRespDTO} 的类注释里，<b>NEVER 只看 `retCode`</b>。</p>
     *
     * <p><b>本方法不吞异常</b>：网络不可达时让底层异常原样抛给调用方，等价于
     * {@code RpcOutcome.Unreachable} —— 调用方接住后 MUST 不改本地状态、留补偿任务重入。
     * <b>NEVER 在这里 catch 后返 null 或造一个假的业务码</b>，那会让「该重试」变成「已终态」。
     * 本类其余方法也都是这个约定（返 boolean 的写法已被 §5.2 明令禁止）。</p>
     *
     * <p>本方法**不带 headers 形参**：调用方是业务链路（不是 Quartz），traceId 由
     * `ProxyWebClient` 走 Boot 观测自动带出；将来若这条也要加内部令牌，MUST 新增重载、
     * <b>NEVER 改本方法签名</b>（`rpc` 版本号锁死、被 21 个模块引用）。</p>
     */
    public GateTxnPayDebitConvergeRespDTO convergeDebitStatusForSupplement(
            @RequestBody GateTxnPayDebitConvergeReqDTO request) {
        String result = postJsonAndGetResponse("/internal/gate-txn-pay/debit/converge", request);
        return JSONUtil.toBean(result, new TypeReference<GateTxnPayDebitConvergeRespDTO>() {
        }, true);
    }
}
