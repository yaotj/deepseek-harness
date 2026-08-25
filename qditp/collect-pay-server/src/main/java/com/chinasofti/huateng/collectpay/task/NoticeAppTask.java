package com.chinasofti.huateng.collectpay.task;

import com.chinasofti.huateng.collectpay.common.ItpCommon;
import com.chinasofti.huateng.collectpay.entity.NoticeRefundRecord;
import com.chinasofti.huateng.collectpay.entity.NoticeTakeTicketFailureRecord;
import com.chinasofti.huateng.collectpay.entity.NoticeTakeTicketRecord;
import com.chinasofti.huateng.collectpay.mapper.TvmNoticeAppMapper;
import com.chinasofti.huateng.collectpay.service.AppOrderService;
import com.chinasofti.huateng.collectpay.service.TvmCommonService;
import com.chinasofti.huateng.collectpay.service.TvmOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 当面付汇总task
 */
@Component
@Slf4j
public class NoticeAppTask {

    @Autowired
    private TvmNoticeAppMapper tvmNoticeAppMapper;
    @Autowired
    private TvmOrderService tvmOrderService;
    @Autowired
    private TvmCommonService tvmCommonService;
    @Autowired
    private AppOrderService appOrderService;
    @Autowired
    Environment environment;

    /**
     * 扫码取票接口 通知app出票结果
     */
    @Scheduled(cron = "${doTime.noticeTakeTicketTask}")
    public void noticeTakeTicketTask() {

        log.info("定时任务开始执行 通知app出票结果");

        Map<String, String> condition = new HashMap<>();
        condition.put("status", ItpCommon.NOTICE_SUCCESS);
        condition.put("retryTimes", environment.getProperty("app.retryTimes"));

        List<NoticeTakeTicketRecord> noticeTakeTicketRecordLs = tvmNoticeAppMapper.selectSendFailTakeTicketLs(condition);

        log.info("task noticeTakeTicketRecordLs.size is {}", noticeTakeTicketRecordLs.size());

        for (NoticeTakeTicketRecord record : noticeTakeTicketRecordLs) {
            log.info("原发送记录 taketicket_record is {}", record);
            int retryTimes = Integer.valueOf(record.getRetryTimes()) + 1;
            log.info("开始发送 新的taketicket_retryTimes is {}", retryTimes);
            boolean b = tvmOrderService.sendNoticeAppTakeTicketRecord(record.getOrderNo(), record.getOrderTicketNum(), record.getActualTakeTicketNum(), record.getTakeTickeDate(), String.valueOf(retryTimes));
            log.info("发送结束 b is {}", b);
        }

    }

    /**
     * 扫码取票接口 通知app出票故障结果
     */
    @Scheduled(cron = "${doTime.noticeTakeTicketFailureTask}")
    public void noticeTakeTicketFailureTask() {

        log.info("定时任务开始执行 通知app出票故障结果");
        Map<String, String> condition = new HashMap<>();
        condition.put("status", ItpCommon.NOTICE_SUCCESS);
        condition.put("retryTimes", environment.getProperty("app.retryTimes"));

        List<NoticeTakeTicketFailureRecord> noticeTakeTicketFailureRecordLs = tvmNoticeAppMapper.selectSendFailTakeTicketFailureLs(condition);

        log.info("task noticeTakeTicketFailureRecordLs.size is {}", noticeTakeTicketFailureRecordLs.size());

        for (NoticeTakeTicketFailureRecord record : noticeTakeTicketFailureRecordLs) {
            log.info("app故障通知 原发送记录 taketicket_record is {}", record);
            int retryTimes = Integer.valueOf(record.getRetryTimes()) + 1;
            log.info("开始发送 app故障通知 新的taketicket_retryTimes is {}", retryTimes);
            boolean b = tvmOrderService.sendNoticeAppTakeTicketFailureRecord(record.getOrderNo(), record.getOrderTicketNum(), record.getActualTakeTicketNum(), record.getTakeTickeDate(), record.getTakeTickeDate(),record.getTakeTiketFaultReason(),String.valueOf(retryTimes));
            log.info("app故障通知 发送结束 b is {}", b);
        }

    }

    /**
     * 扫码取票接口 通知app退款结果
     */
    @Scheduled(cron = "${doTime.noticeRefundTask}")
    public void noticeRefundTask() {

        log.info("定时任务开始执行 通知app退款结果");

        Map<String, String> condition = new HashMap<>();
        condition.put("status", ItpCommon.NOTICE_SUCCESS);
        condition.put("retryTimes", environment.getProperty("app.retryTimes"));

        List<NoticeRefundRecord> noticeRefundRecordLs = tvmNoticeAppMapper.selectSendFailRefundLs(condition);

        log.info("task noticeRefundRecordLs.size is {}", noticeRefundRecordLs.size());

        for (NoticeRefundRecord record : noticeRefundRecordLs) {
            log.info("原发送记录 refund_record is {}", record);
            int retryTimes = Integer.valueOf(record.getRetryTimes()) + 1;
            log.info("开始发送 新的refund_retryTimes is {}", retryTimes);
            boolean b = appOrderService.noticeAppRefundResult(record.getOrderNo(), record.getRefundResult(), record.getRefundDate(), record.getRefundAmount(), String.valueOf(retryTimes));
            log.info("发送结束 b is {}", b);
        }

    }

}