package com.chinasofti.huateng.para.mapper.network;

import com.chinasofti.huateng.para.entity.network.TsfInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TsfInfoMapper {
    int deleteByParaVerNo(@Param("paraVerNo") Long paraVerNo);

    int insertBatch(@Param("list") List<TsfInfo> list);
}
