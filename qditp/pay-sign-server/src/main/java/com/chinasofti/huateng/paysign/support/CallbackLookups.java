package com.chinasofti.huateng.paysign.support;

import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.mapper.PaySignRequestMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * 两条回调链路共用的「从流水表补字段」查询（2026-09-17，ADR-D120）。
 *
 * <p>拆分 {@code CallbackDomainServiceImpl} 时，{@code resolveThirdUserId} 是签约与解约两条链路
 * <b>唯一共用</b>的私有方法。复制成两份会让「补不到就返 null」这条口径分叉，
 * 所以收进本类、mapper 作参数传入（与 {@code TerminationStatusTransition.classify} 同形）。
 *
 * <p><b>NEVER 让本类持有 mapper 字段</b>：它是无状态查询帮手，不是 Spring Bean。
 */
public final class CallbackLookups {

    private static final Logger log = LoggerFactory.getLogger(CallbackLookups.class);

    private CallbackLookups() {
    }

    /** 支付平台回调可能不带 thirdUserId，尝试从流水表补充。 */
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
