package com.chinasofti.huateng.alipay.paysign.service.impl.payment;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayCenterMsgLog;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayCenterMsgLogMapper;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 我方 → 支付中心的出网报文留痕，一次调用一行，写进 {@code ALIPAY_PAY_CENTER_MSG_LOG}。
 *
 * <p><b>为什么单独一个类</b>：{@code requestPay} / {@code payQuery} / {@code requestRefund} /
 * {@code refundQuery} 四个出网接口都要写同一张表、同一套字段，抄四份必然长歪。
 *
 * <p><b>写入时机 MUST 是「拿到应答（或异常）之后写一行」</b>，NEVER 拆成出网前后各写一次 ——
 * 那样一次调用会变两行，破坏本表「一次调用一行」的语义。调用抛异常拿不到应答时，
 * {@code retCode} 留空、成因记进 {@code remark}，报文照样落。
 *
 * <p><b>落库失败只记 WARN、NEVER 打断业务链路</b>：留痕失败不该让一笔本可成功的支付失败。
 *
 * <p><b>为什么是 public</b>：扣费申请的新落点在 {@code service.impl.pay} 包，跨包注不进包私有类。
 * 放开可见性是刻意的、也是最小改法 —— 另一个选项是把留痕逻辑再抄一份，那必然长歪。
 * <b>本类仍只做留痕、NEVER 在里面判成没成</b>（业务判定属于调用点）。
 */
@Component
public class AlipayPayCenterMsgLogWriter {

    private static final Logger log = LoggerFactory.getLogger(AlipayPayCenterMsgLogWriter.class);

    private static final int RET_MSG_MAX = 500;
    private static final int REMARK_MAX = 500;

    private final AlipayPayCenterMsgLogMapper alipayPayCenterMsgLogMapper;

    AlipayPayCenterMsgLogWriter(AlipayPayCenterMsgLogMapper alipayPayCenterMsgLogMapper) {
        this.alipayPayCenterMsgLogMapper = alipayPayCenterMsgLogMapper;
    }

    /**
     * 记一次出网调用。
     *
     * @param apiName   requestPay / payQuery / requestRefund / refundQuery
     * @param requestNo 退款类接口填退款单号，支付类传 null
     * @param reply     支付中心应答；调用本身抛异常时传 null
     * @param remark    异常摘要或人工说明，无则传 null
     */
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

    /**
     * 三个分支都只取「对方说了什么」，NEVER 在这里判成没成 —— 业务判定属于调用点，
     * 混进留痕会让同一份事实出现两套解释。
     */
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
