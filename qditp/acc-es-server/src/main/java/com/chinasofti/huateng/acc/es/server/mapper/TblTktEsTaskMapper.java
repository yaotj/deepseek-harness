package com.chinasofti.huateng.acc.es.server.mapper;

import com.chinasofti.huateng.acc.es.server.model.TblTktEsTask;
import com.chinasofti.huateng.acc.es.server.netty.data.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


@Mapper
public interface TblTktEsTaskMapper {

    int updateTaskAssignStat(@Param("taskNo") Integer taskNo, @Param("assigen") String assigned);

    Integer sumTaskByPlanNo(@Param("planNo")  Integer planNo);

    List<TblTktEsTask> select2(TblTktEsTask task);

    List<TblTktEsTask> queryAllTasks(TblTktEsTask task);

    List<TblTktEsTask> tasksByDateAndEsNodeId(@Param("date") String date, @Param("esCode") String esCode);

    String getTaskTypeByTaskNo(Integer taskNo);

    int deleteByPrimaryKey(Integer taskNo);

    int deleteByPlanNo(Integer planNo);

    int insert(TblTktEsTask record);

    int insertSelective(TblTktEsTask record);

    TblTktEsTask selectByPrimaryKey(Integer taskNo);

    int updateByPrimaryKeySelective(TblTktEsTask record);

    int updateByPrimaryKey(TblTktEsTask record);

    int reportPublishTaskReport(PublishTaskReport publishTaskReport);

    int reportPreAssignTaskReport(PreAssignTaskReport preAssignTaskReport);

    int reportHandCancelTaskReport(HandCancelTaskReport handCancelTaskReport);

    int reportCancelTaskReport(CancelTaskReport cancelTaskReport);

    int reportRecodeTaskReport(RecodeTaskReport recodeTaskReport);

    List<TblTktEsTask> selectThePlanNoTasks(Integer planNo);


    int updateToExecuting(TblTktEsTask tasks);


    List<TblTktEsTask> allCustomTask(TblTktEsTask task);


    int updateTaskToFail(@Param("taskNo") int taskNo);


    int reportCustomTaskReport(CustomTaskReport customTaskReport);
}
