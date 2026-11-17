package com.chinasofti.huateng.acc.es.server.service;

import com.chinasofti.huateng.acc.es.server.model.TblTktTaskPlan;
import com.chinasofti.huateng.common.response.ResultVO;

import java.util.List;

public interface ITaskPlanService {

    ResultVO<?> save(TblTktTaskPlan taskPlan);



    ResultVO<?> taskPlansByPage(Integer pageNum, Integer pageSize, TblTktTaskPlan info);


    ResultVO<?> updateByPlanNo(TblTktTaskPlan taskPlan);


    ResultVO<?> deleteByPlanNo(Integer planNo);


    ResultVO<?> approve(TblTktTaskPlan taskPlan);


    ResultVO<?> changeStat(TblTktTaskPlan taskPlan);


    void assignTask(TblTktTaskPlan taskPlan);

    TblTktTaskPlan selectByPlanNo(Integer planNo);

    List<TblTktTaskPlan> queryAvailable();

    int updateByPlanNoSelective(TblTktTaskPlan taskPlan);
}
