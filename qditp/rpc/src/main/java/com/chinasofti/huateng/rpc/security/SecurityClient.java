package com.chinasofti.huateng.rpc.security;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.app.RequestSignInsDataReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInsDataRespDTO;
import com.chinasofti.huateng.model.security.RequestExportUserPriKeyReqDTO;
import com.chinasofti.huateng.model.security.RequestExportUserPriKeyRespDTO;
import com.chinasofti.huateng.model.security.RequestDpkReqDTO;
import com.chinasofti.huateng.model.security.RequestDpkRespDTO;
import com.chinasofti.huateng.model.security.RequestHceCardDataReqDTO;
import com.chinasofti.huateng.model.security.RequestHceCardDataRespDTO;
import com.chinasofti.huateng.model.security.RequestSignPubkeyReqDTO;
import com.chinasofti.huateng.model.security.RequestSignPubkeyRespDTO;
import com.chinasofti.huateng.model.security.RequestUserSm2KeyReqDTO;
import com.chinasofti.huateng.model.security.RequestUserSm2KeyRespDTO;
import com.chinasofti.huateng.model.security.SecurityBaseRespDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 安全服务RPC客户端。
 */
@Service
public class SecurityClient extends ProxyWebClient {
    public SecurityClient(@Value("${service.security.url:security-service}") String baseUrl,
                          @Value("${service.security.openLogger:true}") boolean openLogger,
                          WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    public RequestSignInsDataRespDTO requestSignInsData(@RequestBody RequestSignInsDataReqDTO request) {
        String result = postJsonAndGetResponse("/ci/itp/requestSignInsData", request);
        JSONObject resultObj = JSONUtil.parseObj(result);
        RequestSignInsDataRespDTO response = new RequestSignInsDataRespDTO();
        response.setRetCode(resultObj.getStr("code"));
        response.setRetMsg(resultObj.getStr("msg"));
        JSONObject dataObj = resultObj.getJSONObject("data");
        if (dataObj != null) {
            response.setIndustryDataSign(dataObj.getStr("industryDataSign"));
        }
        if (response.getRetCode() == null) {
            response.setRetCode(ResultVO.ERROR_CODE);
            response.setRetMsg(ResultVO.ERROR_MSG);
        }
        return response;
    }

    /**
     * 请求生成用户SM2密钥对。
     * @param request 请求参数。
     * @return 用户SM2密钥对。
     */
    public RequestUserSm2KeyRespDTO requestUserSm2Key(@RequestBody RequestUserSm2KeyReqDTO request) {
        String result = postJsonAndGetResponse("/ci/itp/requestUserSm2Key", request);
        JSONObject dataObj = parseDataObject(result);
        RequestUserSm2KeyRespDTO response = buildBaseResponse(result, new RequestUserSm2KeyRespDTO());
        if (dataObj != null) {
            response.setPrivateKey(dataObj.getStr("privateKey"));
            response.setPublicKey(dataObj.getStr("publicKey"));
            response.setSm2KeyPair(dataObj.getStr("sm2KeyPair"));
        }
        return response;
    }

    /**
     * 请求签名用户公钥。
     * @param request 请求参数。
     * @return 签名用户公钥结果。
     */
    public RequestSignPubkeyRespDTO requestSignPubkey(@RequestBody RequestSignPubkeyReqDTO request) {
        String result = postJsonAndGetResponse("/ci/itp/requestSignPubkey", request);
        JSONObject dataObj = parseDataObject(result);
        RequestSignPubkeyRespDTO response = buildBaseResponse(result, new RequestSignPubkeyRespDTO());
        if (dataObj != null) {
            response.setSignData(dataObj.getStr("signData"));
        }
        return response;
    }

    /**
     * 请求导出用户私钥。
     * @param request 请求参数。
     * @return 导出用户私钥结果。
     */
    public RequestExportUserPriKeyRespDTO requestExportUserPriKey(@RequestBody RequestExportUserPriKeyReqDTO request) {
        String result = postJsonAndGetResponse("/ci/itp/requestExportUserPriKey", request);
        JSONObject dataObj = parseDataObject(result);
        RequestExportUserPriKeyRespDTO response = buildBaseResponse(result, new RequestExportUserPriKeyRespDTO());
        if (dataObj != null) {
            response.setUserPrivateKeyByKes(dataObj.getStr("userPrivateKeyByKes"));
        }
        return response;
    }

    /**
     * 请求导出 HCE 应用子密钥（DPK）。
     * @param request HCE 逻辑卡号。
     * @return ACC KEK 加密的 DPK。
     */
    public RequestDpkRespDTO requestDpk(@RequestBody RequestDpkReqDTO request) {
        String result = postJsonAndGetResponse("/ci/itp/requestDPK", request);
        JSONObject dataObj = parseDataObject(result);
        RequestDpkRespDTO response = buildBaseResponse(result, new RequestDpkRespDTO());
        if (dataObj != null) {
            response.setDpkByKek(dataObj.getStr("dpkByKek"));
        }
        return response;
    }

    /**
     * 调用安全服务发售 HCE 卡数据。
     * @param request HCE 卡数据请求参数。
     * @return 包含安全服务返回码、HCE 卡数据和逻辑卡号的响应。
     */
    public RequestHceCardDataRespDTO requestHceCardData(@RequestBody RequestHceCardDataReqDTO request) {
        String result = postJsonAndGetResponse("/ci/itp/requestHceCardData", request);
        JSONObject dataObj = parseDataObject(result);
        RequestHceCardDataRespDTO response = buildBaseResponse(result, new RequestHceCardDataRespDTO());
        if (dataObj != null) {
            response.setHceData(dataObj.getStr("hceData"));
            response.setLogicNum(dataObj.getStr("logicNum"));
        }
        return response;
    }

    private <T extends SecurityBaseRespDTO> T buildBaseResponse(String result, T response) {
        JSONObject resultObj = JSONUtil.parseObj(result);
        response.setRetCode(resultObj.getStr("code"));
        response.setRetMsg(resultObj.getStr("msg"));
        if (response.getRetCode() == null) {
            response.setRetCode(ResultVO.ERROR_CODE);
            response.setRetMsg(ResultVO.ERROR_MSG);
        }
        return response;
    }

    private JSONObject parseDataObject(String result) {
        JSONObject resultObj = JSONUtil.parseObj(result);
        return resultObj.getJSONObject("data");
    }
}
