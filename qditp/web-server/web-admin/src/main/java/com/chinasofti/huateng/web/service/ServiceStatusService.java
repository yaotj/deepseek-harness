package com.chinasofti.huateng.web.service;

import com.chinasofti.huateng.common.core.domain.AjaxResult;
import com.chinasofti.huateng.web.domain.ServiceStatusView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** 综管台服务监控：对静态清单中的各微服务逐个探活 {@code /actuator/health} 并聚合。 */
@Service
public class ServiceStatusService {
    private static final Logger log = LoggerFactory.getLogger(ServiceStatusService.class);

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(3);
    private static final DateTimeFormatter CHECKED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 探活并发用虚拟线程池：任务是纯 IO 等待，平台线程池只会徒增调参负担。 */
    private final Executor probeExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(PROBE_TIMEOUT)
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

    private final List<ServiceTarget> targets;

    public ServiceStatusService(
            @Value("${service.account.url:}") String accountUrl,
            @Value("${service.f2f.url:}") String f2fUrl,
            @Value("${service.para.url:}") String paraUrl,
            @Value("${service.paySign.url:}") String paySignUrl,
            @Value("${service.alipay-pay-sign.url:}") String alipayPaySignUrl,
            @Value("${service.blacklist.url:}") String blacklistUrl,
            @Value("${service.cardPool.url:}") String cardPoolUrl,
            @Value("${service.recon.url:}") String reconUrl,
            @Value("${service.gateTxnPay.url:}") String gateTxnPayUrl,
            @Value("${service.ticket.url:}") String ticketUrl,
            @Value("${service.key.url:}") String keyUrl,
            @Value("${service.dailyTicket.url:}") String dailyTicketUrl,
            @Value("${service.industryData.url:}") String industryDataUrl,
            @Value("${service.transQuery.url:}") String transQueryUrl,
            @Value("${service.facePay.url:}") String facePayUrl,
            @Value("${service.fepApp.url:}") String fepAppUrl,
            @Value("${service.fepDev.url:}") String fepDevUrl,
            @Value("${service.fepAcc.url:}") String fepAccUrl,
            @Value("${service.fepAlipay.url:}") String fepAlipayUrl,
            @Value("${service.alipayAccount.url:}") String alipayAccountUrl,
            @Value("${service.accSecurity.url:}") String accSecurityUrl,
            @Value("${service.accEs.url:}") String accEsUrl) {
        List<ServiceTarget> list = new ArrayList<>();
        // 业务核心层
        addTarget(list, "account", "账户服务", accountUrl);
        addTarget(list, "ticket", "票卡服务", ticketUrl);
        addTarget(list, "paySign", "签约服务", paySignUrl);
        addTarget(list, "gateTxnPay", "过闸扣费服务", gateTxnPayUrl);
        addTarget(list, "dailyTicket", "日票服务", dailyTicketUrl);
        addTarget(list, "f2f", "面对面收款服务", f2fUrl);
        addTarget(list, "facePay", "当面付服务", facePayUrl);
        addTarget(list, "recon", "对账服务", reconUrl);
        // 公共能力层
        addTarget(list, "para", "参数服务", paraUrl);
        addTarget(list, "key", "密钥服务", keyUrl);
        addTarget(list, "blacklist", "黑名单服务", blacklistUrl);
        addTarget(list, "cardPool", "卡池服务", cardPoolUrl);
        addTarget(list, "industryData", "行业数据服务", industryDataUrl);
        addTarget(list, "transQuery", "交易查询服务", transQueryUrl);
        // 接入层（FEP）
        addTarget(list, "fepApp", "APP接入服务", fepAppUrl);
        addTarget(list, "fepDev", "闸机接入服务", fepDevUrl);
        addTarget(list, "fepAcc", "ACC前置服务", fepAccUrl);
        addTarget(list, "fepAlipay", "支付宝接入服务", fepAlipayUrl);
        // 支付宝域
        addTarget(list, "alipayAccount", "支付宝账户服务", alipayAccountUrl);
        addTarget(list, "alipay-pay-sign", "支付宝签约服务", alipayPaySignUrl);
        // ACC / 安全层
        addTarget(list, "accSecurity", "加密机服务", accSecurityUrl);
        addTarget(list, "accEs", "ES编码服务", accEsUrl);
        this.targets = List.copyOf(list);
    }

    private void addTarget(List<ServiceTarget> list, String name, String displayName, String baseUrl) {
        list.add(new ServiceTarget(name, displayName, baseUrl));
    }

    /** 并发探活全部目标并聚合返回（list + 汇总计数）。 */
    public AjaxResult list() {
        String checkedAt = LocalDateTime.now().format(CHECKED_AT_FORMAT);
        List<CompletableFuture<ServiceStatusView>> futures = targets.stream()
                .map(target -> CompletableFuture.supplyAsync(() -> probe(target, checkedAt), probeExecutor))
                .toList();
        List<ServiceStatusView> rows = futures.stream().map(CompletableFuture::join).toList();

        long up = rows.stream().filter(row -> "UP".equals(row.getStatus())).count();
        AjaxResult result = AjaxResult.success();
        result.put("list", rows);
        result.put("total", rows.size());
        result.put("upCount", up);
        result.put("downCount", rows.size() - up);
        result.put("checkedAt", checkedAt);
        return result;
    }

    private ServiceStatusView probe(ServiceTarget target, String checkedAt) {
        ServiceStatusView view = new ServiceStatusView();
        view.setServiceName(target.serviceName());
        view.setDisplayName(target.displayName());
        view.setCheckedAt(checkedAt);
        if (target.baseUrl() == null || target.baseUrl().isBlank()) {
            view.setStatus("UNKNOWN");
            view.setMessage("未配置服务地址");
            return view;
        }
        String healthUrl = target.baseUrl().replaceAll("/+$", "") + "/actuator/health";
        view.setHealthUrl(healthUrl);

        long startNanos = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(healthUrl))
                    .timeout(PROBE_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            view.setResponseTimeMs((System.nanoTime() - startNanos) / 1_000_000);
            if (response.statusCode() == 200 && response.body() != null
                    && response.body().contains("\"UP\"")) {
                view.setStatus("UP");
            } else {
                view.setStatus("DOWN");
                view.setMessage("HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            view.setResponseTimeMs((System.nanoTime() - startNanos) / 1_000_000);
            view.setStatus("DOWN");
            view.setMessage(e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
            log.warn("服务探活失败, service={}, url={}", target.serviceName(), healthUrl);
        }
        return view;
    }

    private record ServiceTarget(String serviceName, String displayName, String baseUrl) {
    }
}
