package com.chinasofti.huateng.alipay.paysign.service.impl.sign;

import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;


@Component
class SignRepository {

    private static final Logger log = LoggerFactory.getLogger(SignRepository.class);

    @Autowired
    private AlipaySignInfoMapper alipaySignInfoMapper;

    AlipaySignInfo findActiveSign(String thirdUserId) {
        return alipaySignInfoMapper.selectByThirdUserIdAndChannel(thirdUserId, SignCommand.CHANNEL_ALIPAY);
    }


    SignOutcome save(AlipaySignInfo signRow) {
        String thirdUserId = signRow.getThirdUserId();
        if (alipaySignInfoMapper.selectAnyByThirdUserIdAndChannel(thirdUserId, SignCommand.CHANNEL_ALIPAY) != null) {
            if (alipaySignInfoMapper.reactivateSign(signRow) == 0) {
                return onWriteConflict(signRow);
            }
            return new SignOutcome.Reactivated(signRow);
        }
        try {
            alipaySignInfoMapper.insert(signRow);
        } catch (RuntimeException e) {
            if (!isUniqueConflict(e)) {
                throw e;
            }
            return onWriteConflict(signRow);
        }
        return new SignOutcome.Created(signRow);
    }


    private SignOutcome onWriteConflict(AlipaySignInfo signRow) {
        log.warn("签约落库与并发请求冲突，判为重复请求, thirdUserId={}, agreementCode={}",
                signRow.getThirdUserId(), signRow.getAgreementCode());
        AlipaySignInfo committed = findActiveSign(signRow.getThirdUserId());
        if (committed == null) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "签约冲突且回查不到生效签约，请重试");
        }
        return new SignOutcome.AlreadySigned(committed);
    }


    private boolean isUniqueConflict(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof DuplicateKeyException || t instanceof DataIntegrityViolationException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
