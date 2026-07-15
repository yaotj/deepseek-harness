package com.chinasofti.huateng.rpc.para;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.app.RequestLineCodeListReqDTO;
import com.chinasofti.huateng.model.app.RequestLineCodeListResult;
import com.chinasofti.huateng.model.app.RequestLineStationCodeVersionReqDTO;
import com.chinasofti.huateng.model.app.RequestLineStationCodeVersionResult;
import com.chinasofti.huateng.model.app.RequestStationCodeListReqDTO;
import com.chinasofti.huateng.model.app.RequestStationCodeListResult;
import com.chinasofti.huateng.model.app.RequestStationNameReqDTO;
import com.chinasofti.huateng.model.app.RequestStationNameResult;
import com.chinasofti.huateng.model.app.RequestTicketPriceByStationReqDTO;
import com.chinasofti.huateng.model.app.RequestTicketPriceByStationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * para-server RPC 客户端。
 */
@Service
public class ParaClient extends ProxyWebClient {

    public ParaClient(@Value("${service.para.url:http://127.0.0.1:9107}") String baseUrl,
                      @Value("${service.para.openLogger:true}") boolean openLogger,
                      WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    /**
     * IF8A-07 获取线路代码。
     */
    public RequestLineCodeListResult requestLineCodeList(RequestLineCodeListReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestLineCodeList", request);
        return JSONUtil.toBean(result, new TypeReference<RequestLineCodeListResult>() {}, true);
    }

    /**
     * IF8A-08 获取车站代码。
     */
    public RequestStationCodeListResult requestStationCodeList(RequestStationCodeListReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestStationCodeList", request);
        return JSONUtil.toBean(result, new TypeReference<RequestStationCodeListResult>() {}, true);
    }

    /**
     * IF8A-10 计算票价。
     */
    public RequestTicketPriceByStationResult requestTicketPriceByStation(RequestTicketPriceByStationReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestTicketPriceByStation", request);
        return JSONUtil.toBean(result, new TypeReference<RequestTicketPriceByStationResult>() {}, true);
    }

    /**
     * IF8A-17 获取线路站点代码版本。
     */
    public RequestLineStationCodeVersionResult requestLineStationCodeVersion(RequestLineStationCodeVersionReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestLineStationCodeVersion", request);
        return JSONUtil.toBean(result, new TypeReference<RequestLineStationCodeVersionResult>() {}, true);
    }

    /**
     * 根据车站代码查询车站名称。
     */
    public RequestStationNameResult requestStationName(RequestStationNameReqDTO request) {
        String result = postJsonAndGetResponse("/ci/app/requestStationName", request);
        return JSONUtil.toBean(result, new TypeReference<RequestStationNameResult>() {}, true);
    }
}
