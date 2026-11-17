package com.chinasofti.huateng.acc.es.server.service;

import com.chinasofti.huateng.acc.es.server.model.TblTktEsAssign;
import com.chinasofti.huateng.acc.es.server.model.TblTktEsTask;
import com.chinasofti.huateng.acc.es.server.netty.data.EsTaskReport;
import com.chinasofti.huateng.common.response.ResultVO;

import java.util.List;

public interface IEsTaskService {


    ResultVO<?> save(TblTktEsTask esTask);


    ResultVO<?> selectByTaskNo(Integer taskNo);


    ResultVO<?> updateTask(TblTktEsTask esTask);


    ResultVO<?> deleteById(Integer taskNo, String esCode);


    ResultVO<?> selectTasksByPage(Integer pageNum, Integer pageSize, TblTktEsTask task);

    int changeAssignStat(Integer taskNo, String assigned);


    ResultVO<?> save2(TblTktEsTask esTask);


    ResultVO<?> delete2(Integer taskNo, String esCode);


    ResultVO<?> update2(TblTktEsTask task);


    ResultVO<?> page2(Integer pageNum, Integer pageSize, TblTktEsTask task);


    List<TblTktEsTask> tasksByNodeIdAndDate(String esNodeId, String date);


    String getTaskType(Integer taskNo);


    boolean report(EsTaskReport report);


    ResultVO<?> getCustomTasks(Integer pageNum, Integer pageSize, TblTktEsTask task);



    ResultVO<?> saveCustom(TblTktEsTask task);


    void customFile(TblTktEsAssign esAssign);
}
