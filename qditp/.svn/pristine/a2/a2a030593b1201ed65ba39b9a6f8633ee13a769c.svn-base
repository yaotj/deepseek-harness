package com.chinasofti.huateng.wallet.service.impl;

import com.chinasofti.huateng.wallet.constant.WalletErrorCodeEnum;
import com.chinasofti.huateng.wallet.model.sign.RequestSignInfoReqDTO;
import com.chinasofti.huateng.wallet.model.sign.RequestSignInfoRespDTO;
import com.chinasofti.huateng.wallet.service.WalletSignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class WalletSignServiceImpl implements WalletSignService {
    private static final Logger log = LoggerFactory.getLogger(WalletSignServiceImpl.class);

    @Value("${wallet.sign.alipay.app-id:60000157}")
    private String alipayAppId;
    @Value("${wallet.sign.alipay.merchant-app-id:2015101000413186}")
    private String alipayMerchantAppId;
    @Value("${wallet.sign.wechat.app-id:wx426a3015555a46be}")
    private String wechatAppId;
    @Value("${wallet.sign.wechat.entrust-url:https://api.mch.weixin.qq.com/papay/entrustweb}")
    private String wechatEntrustUrl;

    @Override
    public RequestSignInfoRespDTO requestSignInfo(RequestSignInfoReqDTO request) {
        try {
            log.info("开始处理请求签约请求信息, request={}", request);
            RequestSignInfoRespDTO response = new RequestSignInfoRespDTO();
            String validMsg = validateRequest(request);
            if (validMsg != null) {
                response.setRetCode(WalletErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                return response;
            }
            String payChannelCode = request.getPayChannelCode().trim();
            if (isAlipay(payChannelCode)) {
                response.setRetCode(WalletErrorCodeEnum.SUCCESS.getCode());
                response.setRetMsg(WalletErrorCodeEnum.SUCCESS.getMsg());
                response.setRequestStartSdkInfo(buildAlipaySdkInfo(request));
                return response;
            }
            if (isWechat(payChannelCode)) {
                response.setRetCode(WalletErrorCodeEnum.SUCCESS.getCode());
                response.setRetMsg(WalletErrorCodeEnum.SUCCESS.getMsg());
                response.setRequestStartSdkInfo(buildWechatSdkInfo(request));
                return response;
            }
            response.setRetCode(WalletErrorCodeEnum.FAIL.getCode());
            response.setRetMsg("不支持的支付通道编码");
            return response;
        } catch (Exception e) {
            log.error("处理请求签约请求信息异常", e);
            RequestSignInfoRespDTO response = new RequestSignInfoRespDTO();
            response.setRetCode(WalletErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(WalletErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    /**
     * 校验接口必输参数。
     */
    private String validateRequest(RequestSignInfoReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(request.getDisplayAccount())) {
            return "displayAccount不能为空";
        }
        if (!StringUtils.hasText(request.getPayChannelCode())) {
            return "payChannelCode不能为空";
        }
        if (!StringUtils.hasText(request.getRequestSignSeq())) {
            return "requestSignSeq不能为空";
        }
        if (!StringUtils.hasText(request.getReturnUrl())) {
            return "returnUrl不能为空";
        }
        return null;
    }

    /**
     * 03-支付宝支付。
     */
    private boolean isAlipay(String payChannelCode) {
        return "03".equals(payChannelCode);
    }

    /**
     * 04-微信支付。
     */
    private boolean isWechat(String payChannelCode) {
        return "04".equals(payChannelCode);
    }

    /**
     * 组装支付宝 SDK 拉起参数。
     */
    private String buildAlipaySdkInfo(RequestSignInfoReqDTO request) {
        String signParams = "app_id=" + encode(alipayMerchantAppId)
                + "&third_user_id=" + encode(request.getThirdUserId())
                + "&display_account=" + encode(request.getDisplayAccount())
                + "&request_sign_seq=" + encode(request.getRequestSignSeq())
                + "&return_url=" + encode(request.getReturnUrl());
        if (StringUtils.hasText(request.getAuthCode())) {
            signParams = signParams + "&auth_code=" + encode(request.getAuthCode());
        }
        return "alipays://platformapi/startapp?appId=" + encode(alipayAppId)
                + "&appClearTop=false&startMultApp=YES&sign_params="
                + encode(signParams);
    }

    /**
     * 组装微信签约地址。
     */
    private String buildWechatSdkInfo(RequestSignInfoReqDTO request) {
        String sdkInfo = wechatEntrustUrl
                + "?appid=" + encode(wechatAppId)
                + "&contract_code=" + encode(request.getRequestSignSeq())
                + "&contract_display_account=" + encode(request.getDisplayAccount())
                + "&notify_url=" + encode(request.getReturnUrl())
                + "&request_serial=" + encode(request.getRequestSignSeq())
                + "&third_user_id=" + encode(request.getThirdUserId());
        if (StringUtils.hasText(request.getAuthCode())) {
            sdkInfo = sdkInfo + "&auth_code=" + encode(request.getAuthCode());
        }
        return sdkInfo;
    }

    /**
     * URL 编码。
     */
    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
