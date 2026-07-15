package com.chinasofti.huateng.ticket.mapper;

import com.chinasofti.huateng.ticket.entity.StationInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StationInfoMapper {
    StationInfo selectByStationCode(@Param("stationCode") String stationCode);
}
