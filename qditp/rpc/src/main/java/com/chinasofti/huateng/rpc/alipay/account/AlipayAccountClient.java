package com.chinasofti.huateng.rpc.alipay.account;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.alipay.AlipayUserExportDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付宝出行-账户服务 RPC 客户端。
 */
@Service
public class AlipayAccountClient extends ProxyWebClient {

    public static Logger log = LoggerFactory.getLogger(AlipayAccountClient.class);

    public AlipayAccountClient(@Value("${service.alipayAccount.url:http://alipay-account-server-internal.itp.svc:8080}") String baseUrl,
                               @Value("${service.alipayAccount.openLogger:${service.account.openLogger:true}}") boolean openLogger,
                               WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    /**
     * 支付宝出行-开卡申请。
     */
    public AlipayTripRequestApplicationRespDTO alipayTripRequestApplication(@RequestBody AlipayTripRequestApplicationReqDTO request) {
        String result = postJsonAndGetResponse("/channel/requestApplication", request);
        return JSONUtil.toBean(result, new TypeReference<AlipayTripRequestApplicationRespDTO>() {
        }, true);
    }

    /**
     * 根据 thirdUserId 查询支付宝用户信息。
     */
    public AlipayUserInfoDTO selectByThirdUserId(String thirdUserId) {
        String url = "/channel/queryUserInfo?thirdUserId=" + thirdUserId;
        Map<String, String> params = new HashMap<>();
        String result = getAndGetResponse(url, params);
        return parseUserInfo(result);
    }

    /**
     * 根据逻辑卡号查询支付宝用户信息。
     */
    public AlipayUserInfoDTO selectByCardId(String cardId) {
        String url = "/channel/queryUserInfoByCardId?cardId=" + cardId;
        Map<String, String> params = new HashMap<>();
        String result = getAndGetResponse(url, params);
        return parseUserInfo(result);
    }

    /**
     * 解析用户信息应答。兼容两种返回格式：裸对象 或 {@code {"code":"...","msg":null,"data":{...}}}。
     */
    private AlipayUserInfoDTO parseUserInfo(String result) {
        if (result == null || result.isEmpty()) {
            return null;
        }
        cn.hutool.json.JSONObject wrapper = JSONUtil.parseObj(result);
        Object data = wrapper.get("data");
        String parseTarget = (data instanceof cn.hutool.json.JSONObject) ? ((cn.hutool.json.JSONObject) data).toString() : result;
        AlipayUserInfoDTO dto = JSONUtil.toBean(parseTarget, AlipayUserInfoDTO.class);
        if (dto != null && dto.getCardId() != null && !dto.getCardId().isEmpty()) {
            return dto;
        }
        return null;
    }

    /**
     * 更新用户支付通道信息。
     */
    public boolean updatePaymentChannel(String thirdUserId, String thirdPayId, String reqContractNo) {
        String url = "/channel/updatePaymentChannel?thirdUserId=" + thirdUserId
                + "&thirdPayId=" + thirdPayId
                + "&reqContractNo=" + reqContractNo;
        Map<String, String> params = new HashMap<>();
        String result = getAndGetResponse(url, params);
        if (result == null || result.isEmpty()) {
            return false;
        }
        // 兼容两种返回格式：裸 true/false 或 {"data":true/false}
        result = result.trim();
        if (result.startsWith("{")) {
            cn.hutool.json.JSONObject wrapper = JSONUtil.parseObj(result);
            Object data = wrapper.get("data");
            return data != null && Boolean.parseBoolean(data.toString());
        }
        return Boolean.parseBoolean(result);
    }

    /**
     * 更换手机号。
     */
    public boolean updatePhone(String thirdUserId, String newMsisdn) {
        String url = "/channel/updatePhone?thirdUserId=" + thirdUserId
                + "&newMsisdn=" + newMsisdn;
        Map<String, String> params = new HashMap<>();
        String result = getAndGetResponse(url, params);
        if (result == null || result.isEmpty()) {
            return false;
        }
        // 兼容两种返回格式：裸 true/false 或 {"data":true/false}
        result = result.trim();
        if (result.startsWith("{")) {
            cn.hutool.json.JSONObject wrapper = JSONUtil.parseObj(result);
            Object data = wrapper.get("data");
            return data != null && Boolean.parseBoolean(data.toString());
        }
        return Boolean.parseBoolean(result);
    }

    /**
     * 按并列筛选条件分页拉取支付宝用户（供异步导出使用），条件之间是 AND。
     *
     * <p>空白条件不下发：下游 `/page/user/alipay/search` 要求至少一个非空，全空会被判成参数错误、
     * 本方法随即拿到空 data 并返回空列表。
     */
    public List<AlipayUserExportDTO> searchAlipayUsers(String msisdn, String cardId, String thirdUserId, String reqContractNo) {
        Map<String, String> params = new HashMap<>();
        if (msisdn != null && !msisdn.isBlank()) {
            params.put("msisdn", msisdn.trim());
        }
        if (cardId != null && !cardId.isBlank()) {
            params.put("cardId", cardId.trim());
        }
        if (thirdUserId != null && !thirdUserId.isBlank()) {
            params.put("thirdUserId", thirdUserId.trim());
        }
        if (reqContractNo != null && !reqContractNo.isBlank()) {
            params.put("reqContractNo", reqContractNo.trim());
        }
        int pageSize = 1000;
        int pageNum = 1;
        List<AlipayUserExportDTO> all = new ArrayList<>();
        while (true) {
            params.put("pageNum", String.valueOf(pageNum));
            params.put("pageSize", String.valueOf(pageSize));
            String result = getAndGetResponse("/page/user/alipay/search", params, new HashMap<>());
            JSONObject data = JSONUtil.parseObj(result).getJSONObject("data");
            if (data == null) {
                log.warn("alipay-account /page/user/alipay/search 返回 data 为空，params={}", params);
                break;
            }
            List<AlipayUserExportDTO> page = data.getBeanList("list", AlipayUserExportDTO.class);
            if (page == null || page.isEmpty()) {
                break;
            }
            all.addAll(page);
            if (page.size() < pageSize) {
                break;
            }
            pageNum++;
        }
        return all;
    }
}
