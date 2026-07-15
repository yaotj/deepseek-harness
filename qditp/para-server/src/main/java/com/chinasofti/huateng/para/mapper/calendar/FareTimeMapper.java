package com.chinasofti.huateng.para.mapper.calendar;

import com.chinasofti.huateng.para.entity.calendar.FareTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FareTimeMapper {
    int deleteByParaVerNo(@Param("paraVerNo") Long paraVerNo);

    int insertBatch(@Param("list") List<FareTime> list);
}
