package com.chinasofti.huateng.rpc.blacklist;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripReceiveBlackListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripReceiveBlackListRespDTO;
import com.chinasofti.huateng.model.app.AddBlackListReqDTO;
import com.chinasofti.huateng.model.app.BlackListOperateResult;
import com.chinasofti.huateng.model.app.BlacklistReleaseInspectRespDTO;
import com.chinasofti.huateng.model.app.QueryBlackListReqDTO;
import com.chinasofti.huateng.model.app.QueryBlackListResult;
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
     *
     * @param baseUrl 黑名单服务地址
     * @param openLogger 是否开启请求日志
     * @param webClientBuilder WebClient 构建器
     */
    public BlacklistClient(@Value("${service.blacklist.url:blacklist-service}") String baseUrl,
                           @Value("${service.blacklist.openLogger:true}") boolean openLogger,
                           WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    /**
     * 请求 blacklist-server 查询黑名单状态。
     *
     * @param request 查询黑名单请求参数
     * @return 查询黑名单结果
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
     *
     * @param request 新增黑名单请求参数
     * @return 黑名单操作结果
     */
    public BlackListOperateResult addBlackList(@RequestBody AddBlackListReqDTO request) {
        String result = postJsonAndGetResponse("/addBlackList", request);
        return JSONUtil.toBean(result, BlackListOperateResult.class, true);
    }

    /**
     * 盘点黑名单记录的欠费结清情况（供 web-server 的 Quartz 任务调用）。
     *
     * <p><b>只读，下游 NEVER 删除任何黑名单记录。</b>本轮只输出明细供人工核对：
     * {@code BLACKLIST} 没有拉黑类型字段，「欠费结清」不等于「可以解除」（挂失补卡类同样会结清）。</p>
     *
     * <p>无业务入参，批量范围由下游配置控制。但 body **MUST NOT 传 null**：
     * {@code ProxyWebClient.postJsonAndGetResponse} 无条件调 {@code bodyValue(requestBody)}，
     * Spring 的 {@code BodyInserters.fromValue} 断言非 null，传 null 会抛
     * {@code IllegalArgumentException: 'body' must not be null}，请求根本发不出去。
     * 传空 Map 序列化成 {@code {}}。</p>
     *
     * <p>调用方 MUST 检查返回的 resultCode，返回 null 说明 HTTP 层就没通。</p>
     *
     * @param headers 透传的请求头，用于把调度侧 traceId 带到下游日志
     */
    public BlacklistReleaseInspectRespDTO inspectReleasable(Map<String, String> headers) {
        String result = postJsonAndGetResponse("/internal/blacklist/inspectReleasable", new HashMap<>(), headers);
        return JSONUtil.toBean(result, new TypeReference<BlacklistReleaseInspectRespDTO>() {
        }, true);
    }
}
