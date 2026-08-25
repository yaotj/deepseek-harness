package com.chinasofti.huateng.fep.app.service.impl;

import com.chinasofti.huateng.fep.app.service.PhoneChangeAppService;
import com.chinasofti.huateng.model.app.QueryUserInfoReqDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.alipay.account.AlipayAccountClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 手机号更换服务实现。
 */
@Service
public class PhoneChangeAppServiceImpl implements PhoneChangeAppService {

    private static final Logger log = LoggerFactory.getLogger(PhoneChangeAppServiceImpl.class);

    private final AccountClient accountClient;
    private final AlipayAccountClient alipayAccountClient;

    public PhoneChangeAppServiceImpl(AccountClient accountClient, AlipayAccountClient alipayAccountClient) {
        this.accountClient = accountClient;
        this.alipayAccountClient = alipayAccountClient;
    }

    @Override
    public boolean updatePhone(String thirdUserId, String newMsisdn) {
        if (thirdUserId == null || thirdUserId.trim().isEmpty()
                || newMsisdn == null || newMsisdn.trim().isEmpty()) {
            log.warn("更换手机号参数校验失败, thirdUserId={}, newMsisdn={}", thirdUserId, newMsisdn);
            return false;
        }

        try {
            // 优先判断是否为 ITP 用户
            QueryUserInfoReqDTO queryReq = new QueryUserInfoReqDTO();
            queryReq.setThirdUserId(thirdUserId.trim());
            QueryUserInfoResult queryResult = accountClient.queryUserInfo(queryReq);
            if (queryResult != null && "0000".equals(queryResult.getRetCode())) {
                log.info("识别为ITP用户, thirdUserId={}", thirdUserId);
                com.chinasofti.huateng.common.response.CommonResult itpResult = accountClient.updatePhone(thirdUserId.trim(), newMsisdn.trim());
                return "0000".equals(itpResult.getRetCode());
            }

            // 判断是否为支付宝用户
            if (alipayAccountClient.selectByThirdUserId(thirdUserId.trim()) != null) {
                log.info("识别为支付宝用户, thirdUserId={}", thirdUserId);
                return alipayAccountClient.updatePhone(thirdUserId.trim(), newMsisdn.trim());
            }

            log.warn("用户不存在, thirdUserId={}", thirdUserId);
            return false;
        } catch (Exception e) {
            log.error("更换手机号异常, thirdUserId={}", thirdUserId, e);
            return false;
        }
    }
}
