package com.chinasofti.huateng.web.service.export.executor;

import com.chinasofti.huateng.web.domain.export.AsyncExportTask;


public interface IAsyncExportExecutor
{

    void execute(AsyncExportTask task) throws Exception;
}
