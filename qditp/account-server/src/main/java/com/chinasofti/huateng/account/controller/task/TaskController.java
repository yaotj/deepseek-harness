package com.chinasofti.huateng.account.controller.task;

import com.chinasofti.huateng.account.constant.AccountErrorCodeEnum;
import com.chinasofti.huateng.account.service.PhoneChangeService;

import com.chinasofti.huateng.common.response.CommonResult;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;


@RestController
public class TaskController {
    private static final Logger log = LoggerFactory.getLogger(TaskController.class);


    private final PhoneChangeService phoneChangeService;


    private final ExecutorService compensateExecutor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "phone-sign-sync-compensate");
                thread.setDaemon(true);
                return thread;
            });


    private final AtomicBoolean signSyncRunning = new AtomicBoolean(false);


    public TaskController(PhoneChangeService phoneChangeService) {
        this.phoneChangeService = phoneChangeService;
    }


    @PostMapping("/quartzDemo")
    public CommonResult quartzDemo() {
        log.info("收到由 web-server Quartz 定时任务发起的调用");
        CommonResult response = new CommonResult();
        response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("调用成功");
        return response;
    }


    @PostMapping("/phoneSignSyncCompensate")
    public CommonResult phoneSignSyncCompensate() {
        log.info("收到由 web-server Quartz 定时任务发起的签约展示账号补偿调用");
        CommonResult response = new CommonResult();
        response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
        if (!signSyncRunning.compareAndSet(false, true)) {
            log.info("签约展示账号补偿批次仍在运行，本次触发跳过");
            response.setRetMsg("上一批补偿仍在运行，本次跳过");
            return response;
        }
        try {
            compensateExecutor.execute(() -> {
                try {
                    PhoneChangeService.SignSyncCompensateResult result =
                            phoneChangeService.compensateSignSync();
                    log.info("签约展示账号补偿批次结束: scanned={}, success={}, failed={}",
                            result.scanned(), result.success(), result.failed());
                } catch (Exception e) {
                    log.error("签约展示账号补偿批次异常结束", e);
                } finally {
                    signSyncRunning.set(false);
                }
            });
        } catch (RuntimeException e) {
            signSyncRunning.set(false);
            log.error("签约展示账号补偿批次提交失败，已复位运行标志", e);
            response.setRetMsg("补偿任务提交失败，见 account-server 日志");
            return response;
        }
        response.setRetMsg("补偿已受理，结果见 account-server 日志与 USER_PHONE_CHANGE_LOG");
        return response;
    }


    @PreDestroy
    public void shutdown() {
        compensateExecutor.shutdown();
    }
}
