package com.chinasofti.huateng.facepay.service;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.facepay.domain.F2fDuplicateKey;
import com.chinasofti.huateng.facepay.entity.F2fNotifyTask;
import com.chinasofti.huateng.facepay.mapper.F2fNotifyTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class F2fNotifyService {

    public static final String TYPE_TAKE_TICKET_OK = "TAKE_TICKET_OK";

    public static final String TYPE_TAKE_TICKET_FAIL = "TAKE_TICKET_FAIL";

    public static final String TYPE_REFUND_RESULT = "REFUND_RESULT";

    public static final String TYPE_PAY_RESULT = "PAY_RESULT";

    public static final String TARGET_APP = "APP";

    private static final String STATUS_PENDING = "PENDING";

    private static final int DEFAULT_MAX_RETRY_TIMES = 5;

    private static final Logger log = LoggerFactory.getLogger(F2fNotifyService.class);

    private final F2fNotifyTaskMapper notifyTaskMapper;

    public F2fNotifyService(F2fNotifyTaskMapper notifyTaskMapper) {
        this.notifyTaskMapper = notifyTaskMapper;
    }

    public boolean enqueue(String notifyType, String orderNo, String refundNo, Map<String, Object> payload) {
        log.info("执行 enqueue 方法");
        return enqueue(notifyType, orderNo, refundNo, payload, DEFAULT_MAX_RETRY_TIMES);
    }

    public boolean enqueue(String notifyType, String orderNo, String refundNo,
                           Map<String, Object> payload, int maxRetryTimes) {
        log.info("执行 enqueue 方法,notifyType is {} orderNo is {} refundNo is {} payload is {} maxRetryTimes is {}", notifyType, orderNo, refundNo,
                payload, maxRetryTimes);
        F2fNotifyTask task = new F2fNotifyTask();
        task.setNotifyType(notifyType);
        task.setTarget(TARGET_APP);
        task.setOrderNo(orderNo);
        task.setRefundNo(refundNo);
        task.setNotifyStatus(STATUS_PENDING);
        task.setPayload(payload == null ? null : JSON.toJSONString(payload));
        task.setRetryTimes(0);
        task.setMaxRetryTimes(maxRetryTimes <= 0 ? DEFAULT_MAX_RETRY_TIMES : maxRetryTimes);
        LocalDateTime now = LocalDateTime.now();
        task.setCreateTms(now);
        task.setUpdateTms(now);
        try {
            log.info("开始入库 task is {}", task);
            int i = notifyTaskMapper.insert(task);
            log.info("通知app表入库结束 i is {}",i);
            return true;
        } catch (RuntimeException e) {
            if (!F2fDuplicateKey.isConflict(e)) {
                throw e;
            }
            log.info("通知任务已存在，幂等跳过, notifyType={}, orderNo={}, refundNo={}",
                    notifyType, orderNo, refundNo);
            return false;
        }
    }

    public List<F2fNotifyTask> loadDueTasks(int limit) {
        return notifyTaskMapper.selectDueTasks(LocalDateTime.now(), limit);
    }

    public List<F2fNotifyTask> loadDueTasksByType(String notifyType, int limit) {
        return notifyTaskMapper.selectDueTasksByType(LocalDateTime.now(), notifyType, limit);
    }

    public boolean markSuccess(Long id) {
        return notifyTaskMapper.markSuccess(id, LocalDateTime.now()) > 0;
    }

    public void markFailure(F2fNotifyTask task, String error) {
        int retried = task.getRetryTimes() == null ? 0 : task.getRetryTimes();
        long backoffSeconds = Math.min(600L, 30L * (1L << Math.min(retried, 5)));
        LocalDateTime nextRetry = LocalDateTime.now().plusSeconds(backoffSeconds);
        notifyTaskMapper.markFailure(task.getId(), nextRetry, truncate(error));
        int maxRetry = task.getMaxRetryTimes() == null ? DEFAULT_MAX_RETRY_TIMES : task.getMaxRetryTimes();
        if (retried + 1 >= maxRetry) {
            log.error("通知投递已放弃（GIVEUP），需人工介入, id={}, notifyType={}, orderNo={}, refundNo={}, 已重试={}, 上限={}, error={}",
                    task.getId(), task.getNotifyType(), task.getOrderNo(), task.getRefundNo(),
                    retried + 1, maxRetry, error);
            return;
        }
        log.warn("通知投递失败，{} 秒后重试, id={}, notifyType={}, orderNo={}, error={}",
                backoffSeconds, task.getId(), task.getNotifyType(), task.getOrderNo(), error);
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= 512 ? error : error.substring(0, 512);
    }
}
