package com.chinasofti.huateng.para.mapper.network;

import com.chinasofti.huateng.para.entity.network.ZoneDtl;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ZoneDtlMapper {
    int deleteByParaVerNo(@Param("paraVerNo") Long paraVerNo);

    int insertBatch(@Param("list") List<ZoneDtl> list);
}
