package com.chinasofti.huateng.para.mapper.fare;

import com.chinasofti.huateng.para.entity.fare.BaseFare;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BaseFareMapper {
    int deleteByParaVerNo(@Param("paraVerNo") Long paraVerNo);

    int insertBatch(@Param("list") List<BaseFare> list);
}
