package com.chinasofti.huateng.dailyticket.service;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.dailyticket.client.DailyTicketAccNotifyClient;
import com.chinasofti.huateng.dailyticket.client.DailyTicketAccNotifyResult;
import com.chinasofti.huateng.dailyticket.config.DailyTicketAccNotifyProperties;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketInstanceMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketInstance;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketActivateReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/** IF8A-67 激活成功后通知 ACC 日票发售。 */
@Service
public class DailyTicketAccActiveNotifyService {

    private static final Logger log = LoggerFactory.getLogger(DailyTicketAccActiveNotifyService.class);

    private static final String NOTICE_PENDING = "PENDING";
    private static final String NOTICE_SUCCESS = "SUCCESS";
    private static final String NOTICE_FAIL = "FAIL";
    private static final String NOTICE_GIVEUP = "GIVEUP";

    private static final String OPERATION_DATE_PATTERN = "yyyyMMdd";
    private static final String TIMESTAMP_PATTERN = "yyyyMMddHHmmss";
    private static final int NOTICE_MSG_MAX_LENGTH = 500;

    private final DailyTicketInstanceMapper instanceMapper;
    private final DailyTicketAccNotifyClient accNotifyClient;
    private final DailyTicketAccNotifyProperties properties;
    private final Executor executor;

    public DailyTicketAccActiveNotifyService(DailyTicketInstanceMapper instanceMapper,
                                             DailyTicketAccNotifyClient accNotifyClient,
                                             DailyTicketAccNotifyProperties properties,
                                             @Qualifier("dailyTicketAccNotifyExecutor") Executor executor) {
        this.instanceMapper = instanceMapper;
        this.accNotifyClient = accNotifyClient;
        this.properties = properties;
        this.executor = executor;
    }

    /**
     * 准备 ACC 发售通知任务字段。调用方在激活事务内随票实例一起落库。
     */
    public void preparePending(DailyTicketInstance ticket, DailyTicketActivateReqDTO request, Date activateTime) {
        ticket.setAccNoticeStatus(NOTICE_PENDING);
        ticket.setAccNoticeTimes(0);
        ticket.setAccNoticeTime(null);
        ticket.setAccNoticeMsg(null);
        ticket.setAccNoticePayload(JSON.toJSONString(buildBizData(ticket, request, activateTime)));
    }

    /**
     * 激活成功后快速异步投递。失败不影响 APP 激活结果，后续由补偿接口重试。
     */
    public void deliverAsync(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return;
        }
        executor.execute(() -> {
            try {
                DailyTicketInstance ticket = instanceMapper.selectByOrderNo(orderNo);
                if (ticket == null) {
                    log.warn("日票ACC发售通知异步投递：票实例不存在 orderNo={}", orderNo);
                    return;
                }
                if (NOTICE_SUCCESS.equals(ticket.getAccNoticeStatus())) {
                    log.info("日票ACC发售通知异步投递：已成功，跳过 orderNo={}", orderNo);
                    return;
                }
                deliverInternal(ticket);
            } catch (RuntimeException e) {
                log.error("日票ACC发售通知异步投递异常 orderNo={}", orderNo, e);
            }
        });
    }

    /**
     * 扫表补偿：把未成功且次数未达上限的发售通知逐条投递给 ACC。
     *
     * @param limit 单批条数上限，非正数按配置默认值
     * @return 本批投递成功条数
     */
    public int deliverPending(int limit) {
        int max = limit <= 0 ? properties.getDefaultLimit() : limit;
        List<DailyTicketInstance> pending =
                instanceMapper.selectPendingAccNotice(properties.getMaxNotifyTimes(), max);
        if (pending == null || pending.isEmpty()) {
            return 0;
        }
        int delivered = 0;
        for (DailyTicketInstance ticket : pending) {
            try {
                if (deliverInternal(ticket)) {
                    delivered++;
                }
            } catch (RuntimeException e) {
                log.error("日票ACC发售通知补偿投递失败（本条跳过，不中断整批） orderNo={}",
                        ticket.getOrderNo(), e);
            }
        }
        log.info("日票ACC发售通知补偿完成 scanned={}, delivered={}", pending.size(), delivered);
        return delivered;
    }

    private Map<String, Object> buildBizData(DailyTicketInstance ticket,
                                             DailyTicketActivateReqDTO request,
                                             Date activateTime) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        Date baseTime = activateTime == null ? new Date() : activateTime;
        bizData.put("operationDate", StringUtils.hasText(request.getOperationDate())
                ? request.getOperationDate() : new SimpleDateFormat(OPERATION_DATE_PATTERN).format(baseTime));
        bizData.put("transType", "01");
        bizData.put("cardType", "04");
        bizData.put("cardSubType", ticket.getAppCardType());
        bizData.put("cardNum", ticket.getCardNum());
        bizData.put("transDate", formatTransDate(request.getTransDate(), baseTime));
        bizData.put("payChannel", ticket.getPayChannel());
        bizData.put("transTimes", 1);
        bizData.put("transAmount", ticket.getTransAmount() == null ? 0 : ticket.getTransAmount());
        bizData.put("discountAmount", ticket.getDiscountAmount() == null ? 0 : ticket.getDiscountAmount());
        bizData.put("period", ticket.getPeriod() == null ? 0 : ticket.getPeriod());
        return bizData;
    }

    private String formatTransDate(Long transDate, Date defaultTime) {
        Date date = transDate == null ? defaultTime : new Date(transDate);
        return new SimpleDateFormat(TIMESTAMP_PATTERN).format(date);
    }

    private boolean deliverInternal(DailyTicketInstance ticket) {
        log.info("日票ACC发售通知准备投递 orderNo={}, url={}, bizData={}",
                ticket.getOrderNo(), properties.getUrl(), ticket.getAccNoticePayload());
        DailyTicketAccNotifyResult result = accNotifyClient.post(ticket.getAccNoticePayload());
        int times = (ticket.getAccNoticeTimes() == null ? 0 : ticket.getAccNoticeTimes()) + 1;
        Date now = new Date();
        if (result.delivered()) {
            instanceMapper.updateAccNoticeStatus(ticket.getOrderNo(), NOTICE_SUCCESS, times, now, null);
            log.info("日票ACC发售通知已被ACC受理 orderNo={}, notifyTimes={}", ticket.getOrderNo(), times);
            return true;
        }
        String status = times >= properties.getMaxNotifyTimes() ? NOTICE_GIVEUP : NOTICE_FAIL;
        instanceMapper.updateAccNoticeStatus(ticket.getOrderNo(), status, times, now,
                abbreviate(result.failureReason()));
        log.warn("日票ACC发售通知未被ACC受理 orderNo={}, notifyTimes={}, notifyStatus={}, reason={}",
                ticket.getOrderNo(), times, status, result.failureReason());
        return false;
    }

    private static String abbreviate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= NOTICE_MSG_MAX_LENGTH ? reason : reason.substring(0, NOTICE_MSG_MAX_LENGTH);
    }
}
