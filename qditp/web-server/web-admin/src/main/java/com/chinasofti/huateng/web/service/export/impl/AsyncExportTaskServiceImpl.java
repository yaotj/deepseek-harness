package com.chinasofti.huateng.web.service.export.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.chinasofti.huateng.web.mapper.export.AsyncExportTaskMapper;
import com.chinasofti.huateng.web.domain.export.AsyncExportTask;
import com.chinasofti.huateng.web.service.export.IAsyncExportTaskService;

/**
 * 异步导出任务 Service 实现
 */
@Service
public class AsyncExportTaskServiceImpl implements IAsyncExportTaskService
{
    @Autowired
    private AsyncExportTaskMapper asyncExportTaskMapper;

    @Override
    public int insertAsyncExportTask(AsyncExportTask task)
    {
        return asyncExportTaskMapper.insertAsyncExportTask(task);
    }

    @Override
    public int updateAsyncExportTask(AsyncExportTask task)
    {
        return asyncExportTaskMapper.updateAsyncExportTask(task);
    }

    @Override
    public AsyncExportTask selectAsyncExportTaskById(Long id)
    {
        return asyncExportTaskMapper.selectAsyncExportTaskById(id);
    }

    @Override
    public List<AsyncExportTask> selectAsyncExportTaskList(AsyncExportTask task)
    {
        return asyncExportTaskMapper.selectAsyncExportTaskList(task);
    }

    @Override
    public List<AsyncExportTask> selectPendingTasks()
    {
        return asyncExportTaskMapper.selectPendingTasks();
    }

    @Override
    public AsyncExportTask selectPendingTaskByUniqueKey(AsyncExportTask task)
    {
        return asyncExportTaskMapper.selectPendingTaskByUniqueKey(task);
    }
}
