package com.chinasofti.huateng.recon.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;


@Mapper
public interface ReconStationMapper {

    List<Map<String, Object>> selectStationLineCodes();
}
