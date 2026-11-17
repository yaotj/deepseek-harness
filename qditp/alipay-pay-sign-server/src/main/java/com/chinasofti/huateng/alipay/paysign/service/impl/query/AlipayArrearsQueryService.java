package com.chinasofti.huateng.alipay.paysign.service.impl.query;

import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayLogMapper;
import com.chinasofti.huateng.model.app.CardUnsettledQueryRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AlipayArrearsQueryService {

    private static final Logger log = LoggerFactory.getLogger(AlipayArrearsQueryService.class);

    private static final String RESULT_CODE_SUCCESS = "0000";
    private static final String RESULT_CODE_INVALID_PARAM = "8001";

    private final AlipayPayLogMapper alipayPayLogMapper;

    public AlipayArrearsQueryService(AlipayPayLogMapper alipayPayLogMapper) {
        this.alipayPayLogMapper = alipayPayLogMapper;
    }


    public CardUnsettledQueryRespDTO hasUnsettledOrderByCard(String cardId) {
        CardUnsettledQueryRespDTO response = new CardUnsettledQueryRespDTO();
        if (!StringUtils.hasText(cardId)) {
            log.warn("按卡查询支付宝出行未结清订单参数缺失, cardId={}", cardId);
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
