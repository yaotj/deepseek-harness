package com.chinasofti.huateng.alipay.account.service.impl;

import com.chinasofti.huateng.alipay.account.entity.AlipayPhoneChangeLog;
import com.chinasofti.huateng.alipay.account.entity.AlipayUserInfo;
import com.chinasofti.huateng.alipay.account.mapper.AlipayPhoneChangeLogMapper;
import com.chinasofti.huateng.alipay.account.mapper.AlipayUserInfoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;


@Service
public class AlipayPhoneChangeService {
    private static final Logger log = LoggerFactory.getLogger(AlipayPhoneChangeService.class);

    private final AlipayUserInfoMapper alipayUserInfoMapper;
    private final AlipayPhoneChangeLogMapper alipayPhoneChangeLogMapper;

    public AlipayPhoneChangeService(AlipayUserInfoMapper alipayUserInfoMapper,
                                   AlipayPhoneChangeLogMapper alipayPhoneChangeLogMapper) {
        this.alipayUserInfoMapper = alipayUserInfoMapper;
        this.alipayPhoneChangeLogMapper = alipayPhoneChangeLogMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean updatePhone(String thirdUserId, String newMsisdn) {
        if (thirdUserId == null || thirdUserId.trim().isEmpty() || newMsisdn == null || newMsisdn.trim().isEmpty()) {
            log.warn("更换手机号参数校验失败, thirdUserId={}, newMsisdn={}", thirdUserId, newMsisdn);
            return false;
        }
        try {
            AlipayUserInfo userInfo = alipayUserInfoMapper.selectByThirdUserId(thirdUserId.trim());
            if (userInfo == null) {
                log.warn("更换手机号未找到有效用户, thirdUserId={}", thirdUserId);
                return false;
            }
            String oldMsisdn = userInfo.getMsisdn();
            if (oldMsisdn != null && oldMsisdn.equals(newMsisdn)) {
                log.info("新旧手机号相同，无需更换, thirdUserId={}, msisdn={}", thirdUserId, newMsisdn);
                return true;
            }
            int updated = alipayUserInfoMapper.updateMsisdnByThirdUserId(thirdUserId.trim(), newMsisdn.trim());
            if (updated == 0) {
                log.warn("更换手机号更新失败, thirdUserId={}", thirdUserId);
                return false;
            }
            AlipayPhoneChangeLog changeLog = new AlipayPhoneChangeLog();
            changeLog.setThirdUserId(thirdUserId.trim());
            changeLog.setOldMsisdn(oldMsisdn);
            changeLog.setNewMsisdn(newMsisdn.trim());
            changeLog.setOperType("CHANGE_PHONE");
            changeLog.setOperTime(LocalDateTime.now());
            changeLog.setOperator("SYSTEM");
            changeLog.setRemark("支付宝用户更换手机号");
            changeLog.setCreateTms(LocalDateTime.now());
            alipayPhoneChangeLogMapper.insert(changeLog);
            log.info("支付宝用户更换手机号成功, thirdUserId={}, oldMsisdn={}, newMsisdn={}",
                    thirdUserId, oldMsisdn, newMsisdn);
            return true;
        } catch (Exception e) {
            log.error("更换手机号异常, thirdUserId={}", thirdUserId, e);
            return false;
        }
    }
}
