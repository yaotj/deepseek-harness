package com.chinasofti.huateng.web.mapper.export;

import java.util.List;
import com.chinasofti.huateng.web.domain.export.AsyncExportTask;

/**
 * 异步导出任务 Mapper
 */
public interface AsyncExportTaskMapper
{
    /**
     * 新增任务
     */
    int insertAsyncExportTask(AsyncExportTask task);

    /**
     * 更新任务
     */
    int updateAsyncExportTask(AsyncExportTask task);

    /**
     * 按ID查询
     */
    AsyncExportTask selectAsyncExportTaskById(Long id);

    /**
     * 查询任务列表
     */
    List<AsyncExportTask> selectAsyncExportTaskList(AsyncExportTask task);

    /**
     * 查询待执行任务（按创建时间升序，用于串行调度）
     */
    List<AsyncExportTask> selectPendingTasks();

    /**
     * 幂等查询：同用户同条件同编码的 PENDING 任务
     */
    AsyncExportTask selectPendingTaskByUniqueKey(AsyncExportTask task);
}
