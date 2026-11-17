package com.chinasofti.huateng.dailyticket.service;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.dailyticket.client.DailyTicketAppNotifyClient;
import com.chinasofti.huateng.dailyticket.client.DailyTicketAppNotifyResult;
import com.chinasofti.huateng.dailyticket.config.DailyTicketAppNotifyProperties;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketPayNotifyTaskMapper;
import com.chinasofti.huateng.dailyticket.mapper.TravelTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.DailyTicketPayNotifyTask;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DailyTicketPayResultNotifyService {

    private static final Logger log = LoggerFactory.getLogger(DailyTicketPayResultNotifyService.class);

    private static final int DEFAULT_LIMIT = 100;

    private static final String NOTIFY_PENDING = "PENDING";
    private static final String NOTIFY_SUCCESS = "SUCCESS";
    private static final String NOTIFY_GIVEUP = "GIVEUP";

    private static final String ORDER_TYPE_DAILY_TICKET = "1";
    private static final String ORDER_TYPE_TRAVEL_TICKET = "2";

    private static final String PAY_RESULT_SUCCESS = "SUCCESS";
    private static final String PAY_RESULT_FAIL = "FAIL";

    private static final String DATE_PATTERN = "yyyyMMddHHmmss";
    private static final int NOTIFY_MSG_MAX_LENGTH = 500;

    private final DailyTicketPayNotifyTaskMapper taskMapper;
    private final DailyTicketAppNotifyClient appNotifyClient;
    private final DailyTicketAppNotifyProperties properties;
    private final DailyTicketOrderMapper dailyTicketOrderMapper;
    private final TravelTicketOrderMapper travelOrderMapper;

    public DailyTicketPayResultNotifyService(DailyTicketPayNotifyTaskMapper taskMapper,
                                             DailyTicketAppNotifyClient appNotifyClient,
                                             DailyTicketAppNotifyProperties properties,
                                             DailyTicketOrderMapper dailyTicketOrderMapper,
                                             TravelTicketOrderMapper travelOrderMapper) {
        this.taskMapper = taskMapper;
        this.appNotifyClient = appNotifyClient;
        this.properties = properties;
        this.dailyTicketOrderMapper = dailyTicketOrderMapper;
        this.travelOrderMapper = travelOrderMapper;
    }

    public void enqueueAndDeliver(DailyTicketOrder order, String payResult) {
        if (order == null || !StringUtils.hasText(order.getOrderNo())) {
            return;
        }
        enqueueAndDeliver(buildTask(order.getOrderNo(), ORDER_TYPE_DAILY_TICKET,
                order.getUserId(), order.getTradeNo(), payResult, order.getPayAmount(), order.getPayDate(),
                order.getPayChannelCode()));
    }

    public void enqueueAndDeliver(TravelTicketOrder order, String payResult) {
        if (order == null || !StringUtils.hasText(order.getOrderNo())) {
            return;
        }
        enqueueAndDeliver(buildTask(order.getOrderNo(), ORDER_TYPE_TRAVEL_TICKET,
                order.getUserId(), order.getTradeNo(), payResult, order.getPayAmount(), order.getPayDate(),
                order.getPayChannelCode()));
    }


    public int deliverPending(int limit) {
        int max = limit <= 0 ? DEFAULT_LIMIT : limit;
        List<DailyTicketPayNotifyTask> pending =
                taskMapper.selectPendingNotify(properties.getMaxNotifyTimes(), max);
        if (pending == null || pending.isEmpty()) {
            return 0;
        }
        int delivered = 0;
        for (DailyTicketPayNotifyTask task : pending) {
            try {
                if (deliverInternal(task)) {
                    delivered++;
                }
            } catch (RuntimeException e) {
                log.error("日票支付结果通知投递失败（本条跳过，不中断整批） orderNo={}", task.getOrderNo(), e);
            }
        }
        log.info("日票支付结果通知本批投递完成 scanned={}, delivered={}", pending.size(), delivered);
        return delivered;
    }

    private void enqueueAndDeliver(DailyTicketPayNotifyTask task) {
        try {
            int inserted = taskMapper.insertIfAbsent(task);
            if (inserted == 0) {
                log.info("日票支付结果通知已入队，跳过重复入队 orderNo={}, orderType={}, payResult={}",
                        task.getOrderNo(), task.getOrderType(), task.getPayResult());
                return;
            }
            deliverInternal(task);
        } catch (RuntimeException e) {
            log.error("日票支付结果通知快速路径异常（已尽量入队，等扫表补偿） orderNo={}", task.getOrderNo(), e);
        }
    }

    private DailyTicketPayNotifyTask buildTask(String orderNo, String orderType, String userId, String tradeNo,
                                               String payResult, Integer payAmount, Date payDate, String payChannel) {
        Date now = new Date();
        DailyTicketPayNotifyTask task = new DailyTicketPayNotifyTask();
        task.setId(UUID.randomUUID().toString().replace("-", ""));
        task.setOrderNo(orderNo);
        task.setOrderType(orderType);
        task.setPayResult(normalizePayResult(payResult));
        task.setPayload(JSON.toJSONString(buildBizData(orderNo, orderType, userId, tradeNo,
                task.getPayResult(), payAmount, payDate, payChannel)));
        task.setNotifyStatus(NOTIFY_PENDING);
        task.setNotifyTimes(0);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        return task;
    }

    private Map<String, Object> buildBizData(String orderNo, String orderType, String userId, String tradeNo,
                                             String payResult, Integer payAmount, Date payDate, String payChannel) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        putIfText(bizData, "userId", userId);
        bizData.put("orderNo", orderNo);
        putIfText(bizData, "tradeNo", tradeNo);
        bizData.put("payResult", payResult);
        putIfText(bizData, "payChannel", payChannel);
        if (payAmount != null) {
            bizData.put("payAmount", String.valueOf(payAmount));
        }
        if (payDate != null) {
            bizData.put("payDate", new SimpleDateFormat(DATE_PATTERN).format(payDate));
        }
        bizData.put("voucher", "");
        bizData.put("orderType", orderType);
        return bizData;
    }

    private boolean deliverInternal(DailyTicketPayNotifyTask task) {
        String payload = enrichPayChannelIfAbsent(task);
        log.info("日票支付结果通知准备投递 orderNo={}, url={}, bizData={}",
                task.getOrderNo(), properties.getPayResultUrl(), payload);
        DailyTicketAppNotifyResult result =
                appNotifyClient.postMultipart(properties.getPayResultUrl(), payload);
        int times = (task.getNotifyTimes() == null ? 0 : task.getNotifyTimes()) + 1;
        Date now = new Date();
        if (result.delivered()) {
            taskMapper.updateNotifyStatus(task.getOrderNo(), NOTIFY_SUCCESS, times, now, null);
            log.info("日票支付结果通知已被对端受理 orderNo={}, notifyTimes={}", task.getOrderNo(), times);
            return true;
        }
        String status = times >= properties.getMaxNotifyTimes() ? NOTIFY_GIVEUP : NOTIFY_PENDING;
        taskMapper.updateNotifyStatus(task.getOrderNo(), status, times, now, abbreviate(result.failureReason()));
        log.warn("日票支付结果通知未被受理 orderNo={}, notifyTimes={}, notifyStatus={}, reason={}",
                task.getOrderNo(), times, status, result.failureReason());
        return false;
    }


    private String enrichPayChannelIfAbsent(DailyTicketPayNotifyTask task) {
        String payload = task.getPayload();
        try {
            com.alibaba.fastjson2.JSONObject json = JSON.parseObject(payload);
            if (json == null || StringUtils.hasText(json.getString("payChannel"))) {
                return payload;
            }
            String payChannel = lookupPayChannel(task.getOrderNo(), task.getOrderType());
            if (!StringUtils.hasText(payChannel)) {
                return payload;
            }
            json.put("payChannel", payChannel);
            return json.toJSONString();
        } catch (RuntimeException e) {
            log.warn("日票支付结果通知 payload 补 payChannel 失败，按原报文投递 orderNo={}", task.getOrderNo(), e);
            return payload;
        }
    }

    private String lookupPayChannel(String orderNo, String orderType) {
        if (ORDER_TYPE_TRAVEL_TICKET.equals(orderType)) {
            TravelTicketOrder order = travelOrderMapper.selectByOrderNo(orderNo);
            return order == null ? null : order.getPayChannelCode();
        }
        DailyTicketOrder order = dailyTicketOrderMapper.selectByOrderNo(orderNo);
        return order == null ? null : order.getPayChannelCode();
    }

    private String normalizePayResult(String payResult) {
        return "success".equalsIgnoreCase(payResult) || PAY_RESULT_SUCCESS.equalsIgnoreCase(payResult)
                ? PAY_RESULT_SUCCESS : PAY_RESULT_FAIL;
    }

    private void putIfText(Map<String, Object> map, String key, String value) {
        if (StringUtils.hasText(value)) {
            map.put(key, value);
        }
    }

    private static String abbreviate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= NOTIFY_MSG_MAX_LENGTH ? reason : reason.substring(0, NOTIFY_MSG_MAX_LENGTH);
    }
}
