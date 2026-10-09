package com.chinasofti.huateng.rpc.blacklist;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripReceiveBlackListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripReceiveBlackListRespDTO;
import com.chinasofti.huateng.model.app.AddBlackListReqDTO;
import com.chinasofti.huateng.model.app.BlackListOperateResult;
import com.chinasofti.huateng.model.app.BlacklistAutoReleaseRespDTO;
import com.chinasofti.huateng.model.app.BlacklistReleaseInspectRespDTO;
import com.chinasofti.huateng.model.app.QueryBlackListReqDTO;
import com.chinasofti.huateng.model.app.QueryBlackListResult;
import com.chinasofti.huateng.model.domain.OutboxScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

/**
 * 黑名单服务 RPC 客户端。
 */
@Service
public class BlacklistClient extends ProxyWebClient {
    /**
     * 创建黑名单服务 RPC 客户端。
     * @param baseUrl 黑名单服务地址。
     * @param openLogger 是否开启请求日志。
     * @param webClientBuilder WebClient 构建器。
     */
    public BlacklistClient(@Value("${service.blacklist.url:blacklist-service}") String baseUrl,
                           @Value("${service.blacklist.openLogger:true}") boolean openLogger,
                           WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    /**
     * 请求 blacklist-server 查询黑名单状态。
     * @param request 查询黑名单请求参数。
     * @return 查询黑名单结果。
     */
    public QueryBlackListResult queryBlackList(@RequestBody QueryBlackListReqDTO request) {
        String result = postJsonAndGetResponse("/queryBlackList", request);
        return JSONUtil.toBean(result, new TypeReference<QueryBlackListResult>() {
        }, true);
    }

    /**
     * 支付宝出行-黑名单状态变更通知。
     */
    public AlipayTripReceiveBlackListRespDTO alipayTripReceiveBlackList(@RequestBody AlipayTripReceiveBlackListReqDTO request) {
        QueryBlackListReqDTO appRequest = new QueryBlackListReqDTO();
        appRequest.setCardId(request.getCardId());
        QueryBlackListResult appResult = queryBlackList(appRequest);
        AlipayTripReceiveBlackListRespDTO response = new AlipayTripReceiveBlackListRespDTO();
        response.setRetCode(appResult.getRetCode());
        response.setRetMsg(appResult.getRetMsg());
        return response;
    }

    /**
     * 新增黑名单。
     * @param request 新增黑名单请求参数。
     * @return 黑名单操作结果。
     */
    public BlackListOperateResult addBlackList(@RequestBody AddBlackListReqDTO request) {
        String result = postJsonAndGetResponse("/addBlackList", request);
        return JSONUtil.toBean(result, BlackListOperateResult.class, true);
    }

    /**
     * 盘点黑名单记录的欠费结清情况（供 web-server 的 Quartz 任务调用）。
     * @param headers 透传的请求头，用于把调度侧 traceId 带到下游日志。
     */
    public BlacklistReleaseInspectRespDTO inspectReleasable(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/blacklist/inspectReleasable", new HashMap<>(), headers);
        return JSONUtil.toBean(result, new TypeReference<BlacklistReleaseInspectRespDTO>() {
        }, true);
    }

    /**
     * 自动解除黑名单（供 web-server 的 Quartz 任务调用）。
     *
     * <p><b>这是本 Client 里唯一会改黑名单本体的方法</b>：扫 {@code BLACK_CAUSE='01'}（欠费）且生效中的行，
     * 按 {@code CHANNEL_CODE} 查对应欠费源，已结清即走 {@code deleteBlackList} 的两阶段解除。
     * 判据收紧到欠费类是刻意的 —— 挂失补卡即便欠费清了也 NEVER 自动放行。
     *
     * @param limit 单轮上限；{@code null} 或非正数时由服务端取配置缺省值。
     * @param headers 透传的请求头，用于把调度侧 traceId 带到下游日志。
     */
    public BlacklistAutoReleaseRespDTO autoRelease(Integer limit, Map<String, String> headers) {
        String url = limit == null ? "/internal/blacklist/auto-release"
                : "/internal/blacklist/auto-release?limit=" + limit;
        String result = postJsonAndGetResponse(url, new HashMap<>(), headers);
        return JSONUtil.toBean(result, new TypeReference<BlacklistAutoReleaseRespDTO>() {
        }, true);
    }

    /**
     * 补推加黑方向的渠道同步（供 web-server 的 Quartz 任务调用）。
     *
     * <p>这是加黑通知的<b>唯一兜底</b>：blacklist-server 侧 afterCommit 那条快速路径是进程内非持久的，
     * JVM 崩溃即丢。<b>NEVER 改成让 blacklist-server 自己 {@code @Scheduled}</b>（该模块刻意没有）。
     *
     * @param limit 单轮扫描上限；{@code null} 或非正数时由服务端取缺省值 200。
     * @param headers 透传的请求头，用于把调度侧 traceId 带到下游日志。
     */
    public OutboxScan.Result compensateChannelSyncAdd(Integer limit, Map<String, String> headers) {
        return postOutboxScan("/internal/blacklist/channel-sync/compensate-add", limit, headers);
    }

    /**
     * 补推解黑方向的渠道同步（供 web-server 的 Quartz 任务调用）。
     *
     * <p>解除是两阶段的：通知推成功前那行仍留在 {@code BLACKLIST}（{@code STATUS='RELEASING'}、判黑仍命中），
     * 因此<b>这条补偿不补就意味着「运营已点解除、用户却一直过不了闸」永久存在</b>，比加黑方向更要紧。
     *
     * @param limit 单轮扫描上限；{@code null} 或非正数时由服务端取缺省值 200。
     * @param headers 透传的请求头，用于把调度侧 traceId 带到下游日志。
     */
    public OutboxScan.Result compensateChannelSyncRelease(Integer limit, Map<String, String> headers) {
        return postOutboxScan("/internal/blacklist/channel-sync/compensate-release", limit, headers);
    }

    /**
     * 调补偿端点并把应答读成 {@link OutboxScan.Result}。
     *
     * <p>{@code limit} 在服务端是 {@code @RequestParam}、不在 JSON body 里，所以拼进 query string；
     * 传 {@code null} 时整个参数都不带，让服务端用它自己的缺省值。
     *
     * <p><b>这里逐字段取值、NEVER 换成 {@code JSONUtil.toBean(result, OutboxScan.Result.class)}</b> ——
     * {@code Result} 是 record（无空构造、无 setter），Hutool 的 bean 填充拿不到值，
     * 会静默返回一个三项全 0 的对象：编译能过、调用也不报错，但补偿任务从此永远看不到失败数。
     */
    private OutboxScan.Result postOutboxScan(String path, Integer limit, Map<String, String> headers) {
        String url = limit == null ? path : path + "?limit=" + limit;
        String result = postJsonAndGetResponse(url, new HashMap<>(), headers);
        JSONObject json = JSONUtil.parseObj(result);
        return new OutboxScan.Result(json.getInt("scanned", 0), json.getInt("success", 0),
                json.getInt("failed", 0));
    }
}
