package com.chinasofti.huateng.rpc.recon;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.recon.ReconExportReqDTO;
import com.chinasofti.huateng.model.recon.ReconExportRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * recon-server 向各源服务下发抽取指令的客户端。
 */
@Service
public class ReconExportClient extends ProxyWebClient {

    private static final Logger log = LoggerFactory.getLogger(ReconExportClient.class);

    private static final String EXPORT_PATH = "/internal/recon/export";

    /**
     * 让源服务侧的 INFO 日志进 VictoriaLogs 的开关头，取值固定 {@code 1}。
     */
    private static final String VLOGS_CAPTURE_HEADER = "X-Vlogs-Capture";

    /**
     * 构造抽取指令下发客户端。
     * @param openLogger 是否打印报文日志，指令报文很小，默认开启。
     * @param webClientBuilder Spring 注入的 WebClient 构造器。
     */
    public ReconExportClient(@Value("${service.recon.openLogger:true}") boolean openLogger,
                             WebClient.Builder webClientBuilder) {
        super("", openLogger, webClientBuilder);
    }

    /**
     * 源服务收到指令后要现场起 Oracle 抽取，握手与首次响应可能被慢 SQL 拖住。
     */
    @Override
    protected Duration getResponseTimeout() {
        return Duration.ofMinutes(5);
    }

    /**
     * 向一个源服务下发抽取指令。
     * @param sourceBaseUrl 源服务基础地址。
     * @param request 抽取指令。
     * @return 受理结果；地址非法、远端不可达或响应无法解析时返回 {@code accepted=false} 并带。
     */
    public ReconExportRespDTO dispatch(String sourceBaseUrl, ReconExportReqDTO request) {
        String url;
        try {
            url = normalize(sourceBaseUrl) + EXPORT_PATH;
        } catch (RuntimeException ex) {
            log.error("对账抽取指令的源服务地址非法 sourceBaseUrl={}, batchId={}, msg={}",
                    sourceBaseUrl, request.getBatchId(), ex.getMessage());
            return ReconExportRespDTO.rejected("源服务地址非法: " + ex.getMessage());
        }
        String response;
        try {
            Map<String, String> headers = new HashMap<>(2);
            headers.put(VLOGS_CAPTURE_HEADER, "1");
            response = postJsonAndGetResponse(url, request, headers);
        } catch (RuntimeException ex) {
            log.error("下发对账抽取指令失败 url={}, batchId={}, msg={}", url, request.getBatchId(), ex.getMessage());
            return ReconExportRespDTO.rejected("调用源服务异常: " + ex.getMessage());
        }
        if (response == null || response.isBlank()) {
            return ReconExportRespDTO.rejected("源服务无响应");
        }
        try {
            ReconExportRespDTO parsed = JSON.parseObject(response, ReconExportRespDTO.class);
            return parsed == null ? ReconExportRespDTO.rejected("源服务响应为空对象: " + response) : parsed;
        } catch (RuntimeException ex) {
            return ReconExportRespDTO.rejected("源服务响应无法解析: " + response);
        }
    }

    /**
     * 归一化源服务基础地址：去掉尾部 {@code /} 并强制要求带 {@code http://} / {@code https://} 前缀。
     */
    private String normalize(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("源服务地址未配置");
        }
        String trimmed = baseUrl.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new IllegalArgumentException("源服务地址必须是带 http:// 或 https:// 的绝对地址: " + trimmed);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
