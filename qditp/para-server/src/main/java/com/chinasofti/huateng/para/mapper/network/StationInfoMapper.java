package com.chinasofti.huateng.para.mapper.network;

import com.chinasofti.huateng.para.entity.network.StationInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StationInfoMapper {
    int deleteByParaVerNo(@Param("paraVerNo") Long paraVerNo);

    int insertBatch(@Param("list") List<StationInfo> list);

    List<StationInfo> selectByParaVerNo(@Param("paraVerNo") Long paraVerNo);

    List<StationInfo> selectByParaVerNoAndCondition(
            @Param("paraVerNo") Long paraVerNo,
            @Param("ownerLineId") String ownerLineId,
            @Param("stationCode") String stationCode,
            @Param("stationNm") String stationNm,
            @Param("stationENm") String stationENm);
}
