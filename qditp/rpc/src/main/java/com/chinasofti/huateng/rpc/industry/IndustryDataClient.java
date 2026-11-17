package com.chinasofti.huateng.rpc.industry;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.app.IndustryCardDataBuildReqDTO;
import com.chinasofti.huateng.model.app.IndustryCardDataBuildRespDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;


@Service
public class IndustryDataClient extends ProxyWebClient {

    public IndustryDataClient(@Value("${service.industryData.url:industry-data-service}") String baseUrl,
                              @Value("${service.industryData.openLogger:true}") boolean openLogger,
                              WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }


    public IndustryCardDataBuildRespDTO buildCardData(@RequestBody IndustryCardDataBuildReqDTO request) {
        String result = postJsonAndGetResponse("/ci/industry/buildCardData", request);
        return JSONUtil.toBean(result, new TypeReference<IndustryCardDataBuildRespDTO>() {
        }, true);
    }
}
