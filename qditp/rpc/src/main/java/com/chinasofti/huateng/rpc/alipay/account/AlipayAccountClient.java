package com.chinasofti.huateng.rpc.alipay.account;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝出行-账户服务 RPC 客户端。
 *
 * <p><b>配置键用嵌套默认值 `service.alipayAccount.url` → `service.account.url`，NEVER 退回只读后者。</b>
 * 历史实现只读 `service.account.url`，而该键在不同模块含义不同：`fep-alipay-server` 把它配成
 * alipay-account 地址（那边注释已说明），但 `ticket-server` 的同名键指向**真 account-server**。
 * 于是 ticket-server 里 {@code CardDataHandler} 按 thirdUserId 查支付宝用户时一直打错目标，
 * 且 ticket-server 早已配好的 `service.alipayAccount.url` 没有任何读取方（2026-09-11 定位）。
 * 现在优先读 `service.alipayAccount.url`；`fep-alipay-server` 没有该键，会回落到它自己的
 * `service.account.url`，**行为不变、不需要改那边的配置**。</p>
 */
@Service
public class AlipayAccountClient extends ProxyWebClient {

    public static Logger log = LoggerFactory.getLogger(AlipayAccountClient.class);

    public AlipayAccountClient(@Value("${service.alipayAccount.url:${service.account.url:http://127.0.0.1:9106}}") String baseUrl,
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
     *
     * <p>给 ticket-server 的过闸链路用：支付宝出行用户只在 `ALIPAY_USER_INFO`，不在
     * `USER_ITP_REG_INFO`，account 侧按 cardId 查必然落空，于是反向推码的签约渠道位
     * 会退化成闸机上送值（票种语义）。见 {@code GateTicketHandler#applyActualCardType}。</p>
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
}
