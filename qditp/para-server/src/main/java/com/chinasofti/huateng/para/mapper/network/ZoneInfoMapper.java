package com.chinasofti.huateng.para.mapper.network;

import com.chinasofti.huateng.para.entity.network.ZoneInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ZoneInfoMapper {
    int deleteByParaVerNo(@Param("paraVerNo") Long paraVerNo);

    int insertBatch(@Param("list") List<ZoneInfo> list);
}
