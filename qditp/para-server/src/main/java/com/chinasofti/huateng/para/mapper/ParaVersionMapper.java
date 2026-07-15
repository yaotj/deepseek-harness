package com.chinasofti.huateng.para.mapper;

import com.chinasofti.huateng.para.entity.ParaVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ParaVersionMapper {
    ParaVersion selectByParaType(@Param("paraType") String paraType);

    int upsert(ParaVersion paraVersion);
}
