package com.chinasofti.huateng.paysign.support;

import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.mapper.PaySignRequestMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;


public final class CallbackLookups {

    private static final Logger log = LoggerFactory.getLogger(CallbackLookups.class);

    private CallbackLookups() {
    }

    public static String resolveThirdUserId(PaySignRequestMapper paySignRequestMapper,
                                            String requestSignSeq, String thirdUserId) {
        if (StringUtils.hasText(thirdUserId)) {
            return thirdUserId;
        }
        if (!StringUtils.hasText(requestSignSeq)) {
            return null;
        }
        try {
            PaySignRequest record = paySignRequestMapper.selectByRequestSignSeq(requestSignSeq);
            if (record != null && StringUtils.hasText(record.getThirdUserId())) {
                log.info("从流水表补充 thirdUserId, requestSignSeq={}, thirdUserId={}", requestSignSeq, record.getThirdUserId());
                return record.getThirdUserId();
            }
        } catch (Exception e) {
            log.warn("查询流水表补充 thirdUserId 异常, requestSignSeq={}", requestSignSeq, e);
        }
        return null;
    }
}
