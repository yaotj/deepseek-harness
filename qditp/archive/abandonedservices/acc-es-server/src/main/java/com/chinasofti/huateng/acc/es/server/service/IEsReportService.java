package com.chinasofti.huateng.acc.es.server.service;

import com.chinasofti.huateng.acc.es.server.model.TblTktEsReport;
import com.chinasofti.huateng.acc.es.server.model.TblTktEsTask;
import com.chinasofti.huateng.common.response.ResultVO;

public interface IEsReportService {
    /**
     * 分页查询任务报告
     * @param esReport
     * @return
     */
    ResultVO<?> selectPage(Integer pageNum,Integer pageSize,TblTktEsReport esReport);

    ResultVO<?> saveReports(TblTktEsReport reports);

    void analysisFile(String fileName, String taskNo, String taskNum,TblTktEsTask task);

    void analysisCustomFile(String fileName, String taskNo, String taskNum, TblTktEsTask task);
}
