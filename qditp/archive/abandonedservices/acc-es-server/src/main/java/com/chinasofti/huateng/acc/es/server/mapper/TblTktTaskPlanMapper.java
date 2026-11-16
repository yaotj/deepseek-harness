package com.chinasofti.huateng.acc.es.server.mapper;


import com.chinasofti.huateng.acc.es.server.model.TblTktTaskPlan;
import org.apache.ibatis.annotations.Mapper;

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
public interface TblTktTaskPlanMapper{

    int approve(TblTktTaskPlan taskPlan);

    int changeStat(TblTktTaskPlan taskPlan);

    int assignTask(TblTktTaskPlan taskPlan);

    int deleteByPrimaryKey(Integer planNo);

    int insert(TblTktTaskPlan record);

    int insertSelective(TblTktTaskPlan record);

    TblTktTaskPlan selectByPrimaryKey(Integer planNo);

    int updateByPrimaryKeySelective(TblTktTaskPlan record);

    int updateByPrimaryKey(TblTktTaskPlan record);

    List<TblTktTaskPlan> queryAll(TblTktTaskPlan plan);

    List<TblTktTaskPlan> queryAllAvailable();
}
