package com.chinasofti.huateng.web.service.export.executor;

import com.chinasofti.huateng.web.domain.export.AsyncExportTask;

/**
 * 异步导出执行器接口。
 * 每个 task_code 对应一个 Spring Bean，Bean 名称须等于 task_code。
 */
public interface IAsyncExportExecutor
{
    /**
     * 执行导出任务，须自行完成：
     * 1. 按 task.paramJson 查询全量数据；
     * 2. 生成 xlsx 文件到 task.filePath；
     * 3. 更新 task.fileSize / rowCount 等字段（调用方负责 status/finish_time）。
     *
     * @param task 任务实体
     * @throws Exception 任何异常由调度层捕获并标记 FAILED
     */
    void execute(AsyncExportTask task) throws Exception;
}
