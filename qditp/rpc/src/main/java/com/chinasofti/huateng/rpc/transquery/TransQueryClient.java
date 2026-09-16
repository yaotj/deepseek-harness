package com.chinasofti.huateng.rpc.transquery;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.app.RequestTransDetailReqDTO;
import com.chinasofti.huateng.model.app.RequestTransDetailResult;
import com.chinasofti.huateng.model.app.RequestTransListReqDTO;
import com.chinasofti.huateng.model.app.RequestTransListResult;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * trans-query-server（交易查询服务，9113）客户端。
 *
 * <p>这三个方法与 {@link com.chinasofti.huateng.rpc.ticket.TicketClient} 的同名方法
 * <b>路径、请求 DTO、应答 DTO 全部逐字相同</b>，唯一差别是 baseUrl 指向新服务。
 * 之所以另起一个 Client 而不是改 TicketClient 的 baseUrl：ticket-server 上还有乘车码状态机、
 * 自助补站等一批接口没迁走，改 baseUrl 会把它们一起指错。</p>
 *
 * <p><b>ticket-server 上那三个同路径端点仍在、未删</b>（过渡期双活），因此这里改完只是把
 * fep-app-server 的流量切到新服务，回滚只需把调用点换回 ticketClient。
 * 两边同时在跑期间 MUST 保证 {@code app.trans.*} 五个键取值一致，否则同一笔订单在
 * 新旧链路会返回不同商户号。</p>
 *
 * <p>支付宝行程（{@code findTravelList} / {@code findTravelDetail}）与日票乘车记录
 * <b>故意不在这里</b>：它们还没迁进 trans-query-server（阻塞在支付宝 pay-sign 新表），
 * 仍 MUST 走 TicketClient。</p>
 */
@Service
public class TransQueryClient extends ProxyWebClient {

    public static Logger log = LoggerFactory.getLogger(TransQueryClient.class);

    public TransQueryClient(@Value("${service.transQuery.url:trans-query-service}") String baseUrl,
                            @Value("${service.transQuery.openLogger:true}") boolean openLogger,
                            WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    @Override
    protected Duration getResponseTimeout() {
        return Duration.ofSeconds(10);
    }

    /** IF8A-05 请求查询交易记录。 */
    public RequestTransListResult requestTransList(@RequestBody RequestTransListReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestTransList", request);
        return JSONUtil.toBean(result, new TypeReference<RequestTransListResult>() {
        }, true);
    }

    /** IF8A-41 查询账单统计。 */
    public RequestTransStatisticsResult requestTransStatistics(@RequestBody RequestTransStatisticsReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestTransStatistics", request);
        return JSONUtil.toBean(result, new TypeReference<RequestTransStatisticsResult>() {
        }, true);
    }

    /** IF8A-34 获取订单详情。 */
    public RequestTransDetailResult requestTransDetail(@RequestBody RequestTransDetailReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestTransDetail", request);
        return JSONUtil.toBean(result, new TypeReference<RequestTransDetailResult>() {
        }, true);
    }
}
