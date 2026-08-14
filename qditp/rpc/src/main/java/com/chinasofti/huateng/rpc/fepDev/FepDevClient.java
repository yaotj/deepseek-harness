package com.chinasofti.huateng.rpc.fepDev;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.time.Duration;

/**
 * fep-dev-server RPC Client。
 *
 * <p>通过 HTTP/FormData 调用 fep-dev-server 的 AGM 设备接口。</p>
 */
@Service
public class FepDevClient extends ProxyWebClient {

    public static Logger log = LoggerFactory.getLogger(FepDevClient.class);

    public FepDevClient(@Value("${service.fepDev.url:http://127.0.0.1:9104}") String baseUrl,
                        @Value("${service.fepDev.openLogger:true}") boolean openLogger,
                        WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    @Override
    protected Duration getResponseTimeout() {
        return Duration.ofSeconds(30);
    }

    /**
     * IF1A-01 闸机检票通知（FormData 调用 fep-dev-server）。
     *
     * @param bizData 业务参数
     * @return 闸机检票通知处理结果
     */
    public NotifyVerifyResultRespDTO notifyVerifyResult(@RequestBody NotifyVerifyResultReqDTO bizData) {
        Map<String, String> formData = new java.util.HashMap<>();
        formData.put("providerId", "01");
        formData.put("charset", "UTF-8");
        formData.put("format", "json");
        formData.put("timestamp", new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date()));
        formData.put("deviceId", bizData.getDeviceId());
        formData.put("signType", "00");
        formData.put("sign", "");
        formData.put("bizData", JSONUtil.toJsonStr(bizData));

        String result = postFormAndGetResponse("/ci/agm/notiVerifyResult", formData, null);
        return JSONUtil.toBean(result, new TypeReference<NotifyVerifyResultRespDTO>() {
        }, true);
    }
}
