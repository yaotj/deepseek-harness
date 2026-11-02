package com.chinasofti.huateng.quartz.task;

import java.io.File;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.web.domain.export.AsyncExportTask;
import com.chinasofti.huateng.web.service.export.IAsyncExportTaskService;
import com.chinasofti.huateng.web.service.export.impl.AsyncExportTaskExecutorService;

/**
 * 异步导出任务调度入口。
 * 每日凌晨 2 点扫描 ASYNC_EXPORT_TASK 的 PENDING 任务，按 task_code 串行执行。
 */
@Component("asyncExportQuartzTask")
public class AsyncExportQuartzTask
{
    private static final Logger log = LoggerFactory.getLogger(AsyncExportQuartzTask.class);

    public static final String EXPORT_DIR = "/home/javaapp/app/export/";

    @Autowired
    private IAsyncExportTaskService asyncExportTaskService;

    @Autowired
    private AsyncExportTaskExecutorService executorService;

    /**
     * Quartz 调用入口，sys_job.invoke_target = asyncExportQuartzTask.execute()
     */
    public void execute()
    {
        QuartzTraceUtils.runWithTrace(this::runPendingTasks);
    }

    private void runPendingTasks(String traceId)
    {
        List<AsyncExportTask> pendingTasks = asyncExportTaskService.selectPendingTasks();
        if (pendingTasks == null || pendingTasks.isEmpty())
        {
            log.info("异步导出调度：当前无 PENDING 任务");
            return;
        }

        log.info("异步导出调度：发现 {} 个 PENDING 任务", pendingTasks.size());
        ensureExportDir();

        for (AsyncExportTask task : pendingTasks)
        {
            executorService.execute(task);
        }
    }

    private void ensureExportDir()
    {
        File dir = new File(EXPORT_DIR);
        if (!dir.exists())
        {
            boolean ok = dir.mkdirs();
            if (!ok)
            {
                log.warn("异步导出目录创建失败或已存在: {}", EXPORT_DIR);
            }
        }
    }
}
