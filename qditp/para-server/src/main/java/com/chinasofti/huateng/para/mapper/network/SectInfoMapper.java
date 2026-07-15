package com.chinasofti.huateng.para.mapper.network;

import com.chinasofti.huateng.para.entity.network.SectInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SectInfoMapper {
    int deleteByParaVerNo(@Param("paraVerNo") Long paraVerNo);

    int insertBatch(@Param("list") List<SectInfo> list);
}
