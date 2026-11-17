package com.chinasofti.huateng.web.service.export;

import java.util.List;
import com.chinasofti.huateng.web.domain.export.AsyncExportTask;


public interface IAsyncExportTaskService
{

    int insertAsyncExportTask(AsyncExportTask task);


    int updateAsyncExportTask(AsyncExportTask task);


    AsyncExportTask selectAsyncExportTaskById(Long id);


    List<AsyncExportTask> selectAsyncExportTaskList(AsyncExportTask task);


    List<AsyncExportTask> selectPendingTasks();


    AsyncExportTask selectPendingTaskByUniqueKey(AsyncExportTask task);
}
