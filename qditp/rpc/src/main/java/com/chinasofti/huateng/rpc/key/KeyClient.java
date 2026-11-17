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


@Service
public class KeyClient extends ProxyWebClient {

    public KeyClient(@Value("${service.key.url:key-service}") String baseUrl,
                     @Value("${service.key.openLogger:true}") boolean openLogger,
                     WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }


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


    public List<KeyVersionExportDTO> listKeyVersions() {
        String result = getAndGetResponse("/page/key/versions", new java.util.HashMap<>(), new java.util.HashMap<>());
        return JSONUtil.parseObj(result).getBeanList("data", KeyVersionExportDTO.class);
    }
}
