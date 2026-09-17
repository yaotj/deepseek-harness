package com.chinasofti.huateng.alipay.paysign.service;

import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayLogMapper;
import com.chinasofti.huateng.model.app.CardUnsettledQueryRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 支付宝出行欠费只读查询。 */
@Service
public class AlipayArrearsQueryService {

    private static final Logger log = LoggerFactory.getLogger(AlipayArrearsQueryService.class);

    private static final String RESULT_CODE_SUCCESS = "0000";
    private static final String RESULT_CODE_INVALID_PARAM = "8001";

    private final AlipayPayLogMapper alipayPayLogMapper;

    public AlipayArrearsQueryService(AlipayPayLogMapper alipayPayLogMapper) {
        this.alipayPayLogMapper = alipayPayLogMapper;
    }

    /**
     * 查询该卡在支付宝出行链路下是否仍有未结清订单。
     * @param cardId 卡号
     * @return 查询结果，参数缺失时 resultCode 非 0000 且 hasUnsettled 固定为 true
     */
    public CardUnsettledQueryRespDTO hasUnsettledOrderByCard(String cardId) {
        CardUnsettledQueryRespDTO response = new CardUnsettledQueryRespDTO();
        if (!StringUtils.hasText(cardId)) {
            log.warn("按卡查询支付宝出行未结清订单参数缺失, cardId={}", cardId);
            // 查询未执行时 NEVER 返回 false：调用方会把 false 当成「已结清」，
            response.setResultCode(RESULT_CODE_INVALID_PARAM);
            response.setResultMsg("按卡查询支付宝出行未结清订单参数缺失");
            response.setHasUnsettled(true);
            return response;
        }

        int count = alipayPayLogMapper.countUnsettledByCardId(cardId.trim());
        response.setResultCode(RESULT_CODE_SUCCESS);
        response.setResultMsg("成功");
        response.setHasUnsettled(count > 0);
        log.info("按卡查询支付宝出行未结清订单完成, cardId={}, count={}", cardId, count);
        return response;
    }
}
