package com.chinasofti.huateng.acc.es.server.service;

import com.chinasofti.huateng.acc.es.server.model.TblTktTaskPlan;
import com.chinasofti.huateng.common.response.ResultVO;

import java.util.List;

public interface ITaskPlanService {
    /**
     * 新增任务计划
     *
     * @param taskPlan
     * @return
     */
    ResultVO<?> save(TblTktTaskPlan taskPlan);


    /**
     * 分页查询任务计划
     *
     * @param info
     * @return
     */
    ResultVO<?> taskPlansByPage(Integer pageNum, Integer pageSize, TblTktTaskPlan info);

    /**
     * 通过taskNo修改计划
     *
     * @param taskPlan
     * @return
     */
    ResultVO<?> updateByPlanNo(TblTktTaskPlan taskPlan);

    /**
     * 通过taskNo对任务计划进行删除
     *
     * @param planNo
     * @return
     */
    ResultVO<?> deleteByPlanNo(Integer planNo);

    /**
     * 审批（发行和预赋值）
     *
     * @param taskPlan
     * @return
     */
    ResultVO<?> approve(TblTktTaskPlan taskPlan);

    /**
     * 同意拒绝（缴销，重编码，注销）
     *
     * @param taskPlan
     * @return
     */
    ResultVO<?> changeStat(TblTktTaskPlan taskPlan);

    /**
     * 拆分任务
     *
     * @param taskPlan
     */
    void assignTask(TblTktTaskPlan taskPlan);

    TblTktTaskPlan selectByPlanNo(Integer planNo);

    List<TblTktTaskPlan> queryAvailable();

    int updateByPlanNoSelective(TblTktTaskPlan taskPlan);
}
