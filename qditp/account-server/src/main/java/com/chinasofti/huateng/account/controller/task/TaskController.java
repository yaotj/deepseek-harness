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

/**
 * web-admin Quartz 定时任务的内部触发端点集合，不对外暴露、不承载 APP 报文。
 *
 * <p>本类的端点一律**不收业务入参**：扫描范围由 account-server 自己决定。这是它们可以暂不鉴权的
 * 唯一前提，**NEVER 给它们加「按流水号 / 按用户」这类外部可控参数**，否则就变成裸暴露的单笔数据
 * 操作接口，必须先有鉴权（AGENTS.md §5.2）。</p>
 */
@RestController
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    /** 第六轮拆分后直接注入实现方，不再经 AccountApplicationService 转发。 */
    private final PhoneChangeService phoneChangeService;

    /**
     * 单线程后台执行器：补偿批次**不能跑在 HTTP 请求线程上**。
     *
     * <p>单批最多扫 {@code SIGN_SYNC_SCAN_LIMIT} 行、逐行发 RPC，下游慢时整批可达数十分钟；
     * 全服务默认开启虚拟线程而 ojdbc8 大量方法是 {@code synchronized}，长阻塞会 pin 住载体线程
     * （AGENTS.md §5.2）。单线程 + {@link #signSyncRunning} 双保险确保同一时刻只有一批在跑，
     * 重复触发不会叠加。</p>
     */
    private final ExecutorService compensateExecutor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "phone-sign-sync-compensate");
                thread.setDaemon(true);
                return thread;
            });

    /** 补偿批次运行中标记，用于拒绝重入触发。 */
    private final AtomicBoolean signSyncRunning = new AtomicBoolean(false);

    /**
     * 构造注入账户应用服务。
     *
     * @param phoneChangeService 账户应用服务
     */
    public TaskController(PhoneChangeService phoneChangeService) {
        this.phoneChangeService = phoneChangeService;
    }

    /**
     * web-server Quartz RPC 联调接口，只记录日志，不处理账户业务数据。
     *
     * @return 固定成功
     */
    @PostMapping("/quartzDemo")
    public CommonResult quartzDemo() {
        log.info("收到由 web-server Quartz 定时任务发起的调用");
        CommonResult response = new CommonResult();
        response.setRetCode(AccountErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("调用成功");
        return response;
    }

    /**
     * 手机号变更后「显示账号同步到支付域」的补偿重推，由 web-admin 的 Quartz 任务触发。
     *
     * <p>不收任何入参：触发的是本服务内部的扫表动作，扫描范围由 account-server 自己决定。
     * 这是本端点可以不鉴权的前提（`docs/architecture/web-server.md` §7.3）——
     * <b>NEVER 给它加「按流水号 / 按用户重推」这类外部可控参数</b>，那会变成裸暴露的
     * 单笔数据操作接口，必须先有鉴权（AGENTS.md §5.2）。</p>
     *
     * <p><b>立即返回「已受理」，批处理交后台单线程执行</b>：详见 {@link #compensateExecutor}。
     * 上一批未跑完时直接返回「进行中」，<b>NEVER 改成同步等待</b>。</p>
     *
     * <p>幂等：重复触发最多多打几次 RPC，状态回写由 CAS 兜住。</p>
     *
     * @return 受理结果；已有批次在跑时返回提示但仍是成功码，避免 Quartz 记失败
     */
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
            // 提交失败（如 @PreDestroy 已 shutdown 后仍有请求进来抛 RejectedExecutionException）时
            // 上面的 finally 永不执行，标志会停在 true，此后本实例的补偿永久停摆且无告警。
            // MUST 在这里复位。
            signSyncRunning.set(false);
            log.error("签约展示账号补偿批次提交失败，已复位运行标志", e);
            response.setRetMsg("补偿任务提交失败，见 account-server 日志");
            return response;
        }
        response.setRetMsg("补偿已受理，结果见 account-server 日志与 USER_PHONE_CHANGE_LOG");
        return response;
    }

    /** 关闭后台执行器，避免容器停止时线程泄漏。 */
    @PreDestroy
    public void shutdown() {
        compensateExecutor.shutdown();
    }
}
