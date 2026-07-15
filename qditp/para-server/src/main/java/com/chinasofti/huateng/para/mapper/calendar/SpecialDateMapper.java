package com.chinasofti.huateng.para.mapper.calendar;

import com.chinasofti.huateng.para.entity.calendar.SpecialDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SpecialDateMapper {
    int deleteByParaVerNo(@Param("paraVerNo") Long paraVerNo);

    int insertBatch(@Param("list") List<SpecialDate> list);
}
