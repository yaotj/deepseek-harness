package com.chinasofti.huateng.alipay.paysign.service.impl.support;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayCenterMsgLog;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayCenterMsgLogMapper;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;


@Component
public class AlipayPayCenterMsgLogWriter {

    private static final Logger log = LoggerFactory.getLogger(AlipayPayCenterMsgLogWriter.class);

    private static final int RET_MSG_MAX = 500;
    private static final int REMARK_MAX = 500;

    private final AlipayPayCenterMsgLogMapper alipayPayCenterMsgLogMapper;

    AlipayPayCenterMsgLogWriter(AlipayPayCenterMsgLogMapper alipayPayCenterMsgLogMapper) {
        this.alipayPayCenterMsgLogMapper = alipayPayCenterMsgLogMapper;
    }


    public void record(String orderNo, String txnDate, String apiName, String requestNo,
                       Map<String, Object> bizData, PayCenterReply reply, long elapsedMs, String remark) {
        try {
            AlipayPayCenterMsgLog record = new AlipayPayCenterMsgLog();
            record.setOrderNo(orderNo);
            record.setTxnDate(txnDate);
            record.setApiName(apiName);
            record.setRequestNo(requestNo);
            record.setElapsedMs(elapsedMs);
            record.setRequestBody(bizData == null ? null : JSON.toJSONString(bizData));
            record.setRemark(truncate(remark, REMARK_MAX));
            if (bizData != null) {
                Object scene = bizData.get("scene");
                Object industryType = bizData.get("industryType");
                Object ipAddress = bizData.get("ipAddress");
                record.setScene(scene == null ? null : scene.toString());
                record.setIndustryType(industryType == null ? null : industryType.toString());
                record.setIpAddress(ipAddress == null ? null : ipAddress.toString());
            }
            fillReply(record, reply);
            record.setCreateTime(LocalDateTime.now());
            alipayPayCenterMsgLogMapper.insert(record);
        } catch (Exception e) {
            log.warn("支付中心出网报文留痕失败，本次业务继续, orderNo={}, apiName={}", orderNo, apiName, e);
        }
    }


    private void fillReply(AlipayPayCenterMsgLog record, PayCenterReply reply) {
        if (reply == null) {
            return;
        }
        record.setResponseBody(reply.rawBody());
        switch (reply) {
            case PayCenterReply.Accepted accepted -> {
                record.setRetCode(accepted.retCode());
                record.setRetMsg(truncate(accepted.retMsg(), RET_MSG_MAX));
            }
            case PayCenterReply.Rejected rejected -> record.setRetMsg(truncate(rejected.msg(), RET_MSG_MAX));
            case PayCenterReply.NoAnswer noAnswer -> record.setRetMsg("支付中心无响应");
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
