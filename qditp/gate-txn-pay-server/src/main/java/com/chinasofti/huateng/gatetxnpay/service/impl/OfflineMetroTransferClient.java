package com.chinasofti.huateng.gatetxnpay.service.impl;

import com.chinasofti.huateng.gatetxnpay.constant.GateTxnPayRetCode;
import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 离线码钱包订单的公交换乘资格查询。
 *
 * <p>对接的是**外部公交卡系统**（不是 ITP 内部服务），因此按 {@code pay-sign-server} 的
 * {@code PayGatewayClient} 的定位留在业务模块里，<b>NEVER 搬进 {@code rpc} 模块</b> ——
 * 那个模块是 ITP 内部服务的 Client 集合，`service.*.url` 与 `@EnableRpcXxx` 都是为内部调用准备的。</p>
 *
 * <p>2026-09-14 从裸 {@code WebClient.Builder#build()} 改为继承 {@link ProxyWebClient}，
 * 换来的是连接池（500 连接 / 1000 排队 / 20s 空闲回收）、3s 连接超时、出入报文日志开关、
 * {@code authorization} 的 MDC 透传与统一的失败日志。**行为刻意保持不变**：
 * 响应为空、非 {@code 0000}、以及任何底层异常，一律抛 {@link IllegalStateException} ——
 * 调用方 {@code FareCalculator} 依赖「抛异常即算不出减免」这个语义，改成返回默认值等于静默少收费。</p>
 *
 * <p><b>报文形态 MUST 是 form-urlencoded + 五个公共参数 + {@code bizData} 外壳，NEVER 回退成 JSON body 平铺四个字段。</b>
 * 2026-09-15 之前本类发的是 {@code postJsonAndGetResponse} + 平铺 {@code thirdUserId}/{@code handleDateTime}/
 * {@code cardId}/{@code ticketTransSeq}，实测对端回 <b>HTTP 200 + {@code {"retCode":"1002","retMsg":"bizData 解析异常"}}</b>
 * ——即**这条查询自接入以来一次都没成功过**。而失败语义是抛 {@code IllegalStateException}，
 * {@code FareCalculator} 据此判「算不出减免」，于是表现为**静默少给优惠、不报错、无告警**，
 * 这类缺陷 MUST 靠真实应答体检出，编译与单测都看不到。
 * 换成本类现在的形态后同一组数据实测回 <b>{@code {"retCode":"0000","retMsg":"成功","isReduction":"01"}}</b>。
 * bizData 的字段清单**就是原来那四个、一个不多一个不少**（无需对端另行提供）。
 * 注意对端这个端点的错误提示比 {@code pushMetroTran} 那个友好：它直接点名 {@code bizData}，
 * 而 {@code pushMetroTran} 回的是 {@code 接收地铁交易数据失败null}（尾部字面量 {@code null} 才是线索），
 * **NEVER 因为两个端点的 retMsg 不同就以为是两类问题** —— 同一个骨架、同一个成因。</p>
 */
@Service
public class OfflineMetroTransferClient extends ProxyWebClient {
    private static final Logger log = LoggerFactory.getLogger(OfflineMetroTransferClient.class);
    private final String url;

    public OfflineMetroTransferClient(
            @Value("${wallet.metro-transfer-check-url:http://172.20.202.10:8885/buscard/busApi/2App/v1/checkMetroTransfer}") String url,
            @Value("${wallet.metro-transfer-check-open-logger:false}") boolean openLogger,
            @Value("${wallet.metro-transfer-check-timeout-ms:3000}") long timeoutMs,
            WebClient.Builder webClientBuilder) {
        super(url, openLogger, webClientBuilder, Duration.ofMillis(timeoutMs));
        this.url = url;
    }

    public boolean isReduction(String thirdUserId, String handleDateTime, String cardId, String ticketTransSeq) {
        try {
            Map<String, Object> bizData = new LinkedHashMap<>();
            bizData.put("thirdUserId", thirdUserId);
            bizData.put("handleDateTime", handleDateTime);
            bizData.put("cardId", cardId);
            bizData.put("ticketTransSeq", ticketTransSeq);
            Map<String, String> request = new LinkedHashMap<>();
            request.put("providerId", "01");
            request.put("charset", "utf-8");
            request.put("format", "json");
            request.put("timestamp", String.valueOf(System.currentTimeMillis()));
            request.put("signType", "00");
            request.put("bizData", JSON.toJSONString(bizData));
            String response = postFormAndGetResponse(url, request, null);
            if (response == null) {
                throw new IllegalStateException("公交换乘查询无响应");
            }
            var responseJson = JSON.parseObject(response);
            String retCode = responseJson.getString("retCode");
            if (!GateTxnPayRetCode.SUCCESS.equals(retCode)) {
                throw new IllegalStateException("公交换乘查询失败：" + response);
            }
            String reduction = responseJson.getString("isReduction");
            log.info("离线码公交换乘查询完成, cardId={}, ticketTransSeq={}, isReduction={}", cardId, ticketTransSeq, reduction);
            return "02".equals(reduction);
        } catch (RuntimeException e) {
            if (e instanceof IllegalStateException) {
                throw e;
            }
            throw new IllegalStateException("公交换乘查询异常", e);
        }
    }
}
