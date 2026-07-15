package com.chinasofti.huateng.alipay.account.service;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;

/**
 * 支付宝账户服务接口。
 */
public interface AlipayAccountService {

    /**
     * 支付宝出行-开卡申请。
     *
     * @param request 开卡申请请求参数
     * @return 开卡申请结果
     */
    AlipayTripRequestApplicationRespDTO requestApplication(AlipayTripRequestApplicationReqDTO request);

    /**
     * 根据 thirdUserId 查询支付宝用户信息。
     *
     * @param thirdUserId 第三方用户ID
     * @return 用户信息，未找到返回 null
     */
    AlipayUserInfoDTO selectByThirdUserId(String thirdUserId);

    /**
     * 更新用户支付通道信息。
     *
     * @param thirdUserId 第三方用户ID
     * @param thirdPayId  第三方支付ID
     * @param reqContractNo 签约请求号
     * @return 是否更新成功
     */
    boolean updatePaymentChannel(String thirdUserId, String thirdPayId, String reqContractNo);

    /**
     * 监控卡号池剩余数量，低于阈值时自动申请补充。
     */
    void monitorCardPool();

    /**
     * 请求补充逻辑卡号。
     */
    void requestLogicalCardNo();
}
