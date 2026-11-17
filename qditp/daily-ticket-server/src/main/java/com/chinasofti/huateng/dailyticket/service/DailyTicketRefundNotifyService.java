package com.chinasofti.huateng.dailyticket.service;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.dailyticket.client.DailyTicketAppNotifyClient;
import com.chinasofti.huateng.dailyticket.client.DailyTicketAppNotifyResult;
import com.chinasofti.huateng.dailyticket.config.DailyTicketAppNotifyProperties;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketRefundMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketRefund;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DailyTicketRefundNotifyService {

    private static final Logger log = LoggerFactory.getLogger(DailyTicketRefundNotifyService.class);

    private static final int DEFAULT_LIMIT = 100;

    private static final String NOTIFY_PENDING = "PENDING";
    private static final String NOTIFY_SUCCESS = "SUCCESS";
    private static final String NOTIFY_GIVEUP = "GIVEUP";

    private static final String REFUND_STATUS_REFUNDED = "REFUNDED";
    private static final String REFUND_STATUS_FAILED = "FAILED";

    private static final String ORDER_TYPE_DAILY_TICKET = "1";

    private static final int NOTIFY_MSG_MAX_LENGTH = 500;

    private static final String DATE_PATTERN = "yyyyMMddHHmmss";

    private final DailyTicketRefundMapper refundMapper;
    private final DailyTicketAppNotifyClient appNotifyClient;
    private final DailyTicketAppNotifyProperties properties;

    public DailyTicketRefundNotifyService(DailyTicketRefundMapper refundMapper,
                                          DailyTicketAppNotifyClient appNotifyClient,
                                          DailyTicketAppNotifyProperties properties) {
        this.refundMapper = refundMapper;
        this.appNotifyClient = appNotifyClient;
        this.properties = properties;
    }


    public int deliverPending(int limit) {
        int max = limit <= 0 ? DEFAULT_LIMIT : limit;
        List<DailyTicketRefund> pending =
                refundMapper.selectPendingNotify(properties.getMaxNotifyTimes(), max);
        if (pending == null || pending.isEmpty()) {
            return 0;
        }
        int delivered = 0;
        for (DailyTicketRefund refund : pending) {
            try {
                if (deliverInternal(refund)) {
                    delivered++;
                }
            } catch (RuntimeException e) {
                log.error("日票退款结果通知投递失败（本条跳过，不中断整批） orderNo={}", refund.getOrderNo(), e);
            }
        }
        log.info("日票退款结果通知本批投递完成 scanned={}, delivered={}", pending.size(), delivered);
        return delivered;
    }

    public void deliverOne(String orderNo) {
        try {
            DailyTicketRefund refund = refundMapper.selectByOrderNo(orderNo);
            if (refund == null) {
                log.warn("日票退款结果通知快速路径：退款单不存在 orderNo={}", orderNo);
                return;
            }
            if (!NOTIFY_PENDING.equals(refund.getNotifyStatus())) {
                log.info("日票退款结果通知快速路径：非待发状态，跳过 orderNo={}, notifyStatus={}",
                        orderNo, refund.getNotifyStatus());
                return;
            }
            deliverInternal(refund);
        } catch (RuntimeException e) {
            log.error("日票退款结果通知快速路径异常（已落库为 PENDING，等扫表补偿） orderNo={}", orderNo, e);
        }
    }


    private boolean deliverInternal(DailyTicketRefund refund) {
        String payload = JSON.toJSONString(buildBizData(refund));
        log.info("日票退款结果通知准备投递 orderNo={}, url={}, bizData={}",
                refund.getOrderNo(), properties.getRefundResultUrl(), payload);
        DailyTicketAppNotifyResult result = appNotifyClient.post(properties.getRefundResultUrl(), payload);
        int times = (refund.getNotifyTimes() == null ? 0 : refund.getNotifyTimes()) + 1;
        Date now = new Date();
        if (result.delivered()) {
            refundMapper.updateNotifyStatus(refund.getOrderNo(), NOTIFY_SUCCESS, times, now, null);
            log.info("日票退款结果通知已被对端受理 orderNo={}, notifyTimes={}", refund.getOrderNo(), times);
            return true;
        }
        String status = times >= properties.getMaxNotifyTimes() ? NOTIFY_GIVEUP : NOTIFY_PENDING;
        refundMapper.updateNotifyStatus(refund.getOrderNo(), status, times, now,
                abbreviate(result.failureReason()));
        log.warn("日票退款结果通知未被受理 orderNo={}, notifyTimes={}, notifyStatus={}, reason={}",
                refund.getOrderNo(), times, status, result.failureReason());
        return false;
    }

    private Map<String, Object> buildBizData(DailyTicketRefund refund) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("orderNo", refund.getOrderNo());
        bizData.put("orderType", StringUtils.hasText(refund.getOrderType())
                ? refund.getOrderType() : ORDER_TYPE_DAILY_TICKET);
        bizData.put("refundType", refund.getRefundType());
        bizData.put("refundResult", toRefundResult(refund.getRefundStatus()));
        bizData.put("refundResultDesc", toRefundResultDesc(refund.getRefundStatus()));
        bizData.put("refundDate", refund.getRefundDate() == null ? ""
                : new SimpleDateFormat(DATE_PATTERN).format(refund.getRefundDate()));
        bizData.put("refundAmount", String.valueOf(refund.getRefundAmount() == null ? 0 : refund.getRefundAmount()));
        return bizData;
    }

    private String toRefundResult(String refundStatus) {
        if (REFUND_STATUS_REFUNDED.equals(refundStatus)) {
            return "SUCCESS";
        }
        if (REFUND_STATUS_FAILED.equals(refundStatus)) {
            return "FAIL";
        }
        return "PROCESSING";
    }

    private String toRefundResultDesc(String refundStatus) {
        if (REFUND_STATUS_REFUNDED.equals(refundStatus)) {
            return "退款成功";
        }
        if (REFUND_STATUS_FAILED.equals(refundStatus)) {
            return "退款失败";
        }
        return "退款处理中";
    }

    private static String abbreviate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= NOTIFY_MSG_MAX_LENGTH ? reason : reason.substring(0, NOTIFY_MSG_MAX_LENGTH);
    }
}
