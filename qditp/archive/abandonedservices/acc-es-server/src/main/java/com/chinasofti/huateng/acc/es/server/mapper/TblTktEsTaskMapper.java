package com.chinasofti.huateng.acc.es.server.mapper;

import com.chinasofti.huateng.acc.es.server.model.TblTktEsTask;
import com.chinasofti.huateng.acc.es.server.netty.data.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author fc
 * @since 2020-09-02
 */
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

    /**
     * 批量把任务修改为执行中
     * @param tasks
     * @return
     */
    int updateToExecuting(TblTktEsTask tasks);

    /**
     * 查询个性化任务
     * @param task
     * @return
     */
    List<TblTktEsTask> allCustomTask(TblTktEsTask task);

    /**
     * 设置任务的状态为失败
     * @param taskNo
     * @return
     */
    int updateTaskToFail(@Param("taskNo") int taskNo);

    /**
     * 根据个性化任务更新
     * @param customTaskReport
     * @return
     */
    int reportCustomTaskReport(CustomTaskReport customTaskReport);
}
