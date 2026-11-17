package com.chinasofti.huateng.accsimulator.mapper;

import com.chinasofti.huateng.accsimulator.entity.AccSimulationHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


@Mapper
public interface AccSimulationHistoryMapper {


    void insert(AccSimulationHistory history);


    List<AccSimulationHistory> selectPage(@Param("operation") String operation);


    int deleteAll();
}
