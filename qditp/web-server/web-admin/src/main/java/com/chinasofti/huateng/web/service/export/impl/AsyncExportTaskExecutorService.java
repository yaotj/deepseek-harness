package com.chinasofti.huateng.web.service.export.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chinasofti.huateng.web.domain.export.AsyncExportTask;
import com.chinasofti.huateng.web.service.export.IAsyncExportTaskService;
import com.chinasofti.huateng.web.service.export.executor.IAsyncExportExecutor;

/**
 * 异步导出任务单条执行器（独立 Bean，确保 @Transactional 代理生效）。
 */
@Service
public class AsyncExportTaskExecutorService
{
    private static final Logger log = LoggerFactory.getLogger(AsyncExportTaskExecutorService.class);

    public static final String EXPORT_DIR = "/home/javaapp/app/export/";

    @Autowired
    private IAsyncExportTaskService asyncExportTaskService;

    @Autowired
    private Map<String, IAsyncExportExecutor> exportExecutors;

    @Transactional(rollbackFor = Exception.class)
    public void execute(AsyncExportTask task)
    {
        Long taskId = task.getId();
        String taskCode = task.getTaskCode();
        log.info("异步导出任务开始执行, taskId={}, taskCode={}", taskId, taskCode);

        String filePath = buildFilePath(task);
        task.setFilePath(filePath);
        AsyncExportTask pathUpdate = new AsyncExportTask();
        pathUpdate.setId(taskId);
        pathUpdate.setFilePath(filePath);
        pathUpdate.setUpdateTime(new Date());
        asyncExportTaskService.updateAsyncExportTask(pathUpdate);

        markRunning(task);

        IAsyncExportExecutor executor = exportExecutors.get(taskCode);
        if (executor == null)
        {
            markFailed(task, "未找到任务编码对应的执行器: " + taskCode);
            return;
        }

        try
        {
            executor.execute(task);
            markSuccess(task);
            log.info("异步导出任务执行成功, taskId={}, taskCode={}", taskId, taskCode);
        }
        catch (Exception e)
        {
            log.error("异步导出任务执行失败, taskId={}, taskCode={}", taskId, taskCode, e);
            String msg = e.getMessage();
            if (msg == null)
            {
                msg = e.getClass().getName();
            }
            if (msg.length() > 1900)
            {
                msg = msg.substring(0, 1900);
            }
            markFailed(task, msg);
        }
    }

    private String buildFilePath(AsyncExportTask task)
    {
        String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        return EXPORT_DIR + task.getTaskCode() + "_" + task.getId() + "_" + ts + ".xlsx";
    }

    private void markRunning(AsyncExportTask task)
    {
        AsyncExportTask update = new AsyncExportTask();
        update.setId(task.getId());
        update.setStatus("RUNNING");
        update.setExecuteTime(new Date());
        update.setUpdateTime(new Date());
        asyncExportTaskService.updateAsyncExportTask(update);
    }

    private void markSuccess(AsyncExportTask task)
    {
        AsyncExportTask update = new AsyncExportTask();
        update.setId(task.getId());
        update.setStatus("SUCCESS");
        update.setFilePath(task.getFilePath());
        update.setFileSize(task.getFileSize());
        update.setRowCount(task.getRowCount());
        update.setFinishTime(new Date());
        update.setUpdateTime(new Date());
        asyncExportTaskService.updateAsyncExportTask(update);
    }

    private void markFailed(AsyncExportTask task, String errorMsg)
    {
        AsyncExportTask update = new AsyncExportTask();
        update.setId(task.getId());
        update.setStatus("FAILED");
        update.setErrorMsg(errorMsg);
        update.setFinishTime(new Date());
        update.setUpdateTime(new Date());
        Integer retry = task.getRetryCount();
        update.setRetryCount(retry == null ? 1 : retry + 1);
        asyncExportTaskService.updateAsyncExportTask(update);
    }
}
