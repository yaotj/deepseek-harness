package com.chinasofti.huateng.rpc.key;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.app.RequestKeyListReqDTO;
import com.chinasofti.huateng.model.app.RequestKeyListResult;
import com.chinasofti.huateng.model.agm.RequestAgmSynKeyListReqDTO;
import com.chinasofti.huateng.model.agm.RequestAgmSynKeyListResult;
import com.chinasofti.huateng.model.key.KeyVersionExportDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * 密钥服务RPC客户端。
 */
@Service
public class KeyClient extends ProxyWebClient {
    /**
     * 创建密钥服务RPC客户端。
     * @param baseUrl 密钥服务地址。
     * @param openLogger 是否开启请求日志。
     * @param webClientBuilder WebClient构建器。
     */
    public KeyClient(@Value("${service.key.url:key-service}") String baseUrl,
                     @Value("${service.key.openLogger:true}") boolean openLogger,
                     WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    /**
     * 请求key-server同步用户密钥。
     * @param request 请求同步密钥参数。
     * @return 请求同步密钥结果。
     */
    public RequestKeyListResult requestKeyList(@RequestBody RequestKeyListReqDTO request) {
        String result = postJsonAndGetResponse("/requestKeyList", request);
        return JSONUtil.toBean(result, new TypeReference<RequestKeyListResult>() {
        }, true);
    }

    public RequestAgmSynKeyListResult requestAgmSynKeyList(@RequestBody RequestAgmSynKeyListReqDTO request) {
        String result = postJsonAndGetResponse("/requestAgmSynKeyList", request);
        return JSONUtil.toBean(result, new TypeReference<RequestAgmSynKeyListResult>() {
        }, true);
    }

    /**
     * 查询综管台密钥版本列表（供异步导出使用）。
     * @return 密钥版本展示列表
     */
    public List<KeyVersionExportDTO> listKeyVersions() {
        String result = getAndGetResponse("/page/key/versions", new java.util.HashMap<>(), new java.util.HashMap<>());
        // key-server 返回 ResultVO<List<KeyVersionView>>，取 data 字段
        return JSONUtil.parseObj(result).getBeanList("data", KeyVersionExportDTO.class);
    }
}
