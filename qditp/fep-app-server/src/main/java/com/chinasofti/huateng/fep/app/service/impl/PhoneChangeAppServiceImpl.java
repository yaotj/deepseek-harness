package com.chinasofti.huateng.fep.app.service.impl;

import com.chinasofti.huateng.fep.app.service.PhoneChangeAppService;
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
            // 默认按 ITP 用户处理：account-server 侧 updatePhone 只需 thirdUserId，
            // 内部先 selectActiveByThirdUserId，非 ITP 用户查不到即返回失败，不会误写。
            // NEVER 改回用 queryUserInfo 做归属判断 —— 它强制要求 cardId，此处拿不到，
            // 会导致 ITP 分支永远进不去、恒定落到支付宝分支返回 9999。
            com.chinasofti.huateng.common.response.CommonResult itpResult =
                    accountClient.updatePhone(thirdUserId.trim(), newMsisdn.trim());
            if (itpResult != null && "0000".equals(itpResult.getRetCode())) {
                log.info("识别为ITP用户, 更换手机号成功, thirdUserId={}", thirdUserId);
                return true;
            }
            log.info("ITP侧未处理成功, 尝试支付宝渠道, thirdUserId={}, itpRetCode={}",
                    thirdUserId, itpResult == null ? null : itpResult.getRetCode());

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
